package dev.odeon.android.dados

import android.os.Build
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import retrofit2.HttpException
import java.io.IOException

/// Quem sabe conversar com o Odeon.
///
/// Uma classe só, e é de propósito: a fase 1 tem cinco rotas. Uma camada de
/// `UseCase` por rota seria arquitetura pra um problema que ainda não existe —
/// e o projeto tem régua contra isso ("dividir **quando houver alvo**", §4 da
/// espec).
class RepositorioOdeon(private val cofre: Cofre) {

    private val cliente: OkHttpClient = Rede.cliente(cofre)

    /// O contrato em uso. Trocado quando o servidor muda.
    @Volatile
    private var api: OdeonApi? = null

    @Volatile
    var base: String? = null
        private set

    /// O OkHttp, exposto pra quem mais precisar dele.
    ///
    /// Hoje é o Coil (pôsteres). Na fase 2 é o Media3. Ver `Rede`.
    fun clienteHttp(): OkHttpClient = cliente

    /// Retoma o que estava guardado. Chamado uma vez, no arranque.
    ///
    /// Devolve `true` quando há servidor **e** sessão — ou seja, quando dá pra
    /// abrir direto na biblioteca em vez de pedir login de novo.
    suspend fun retomar(): Boolean {
        cofre.aquecer()
        val guardado = cofre.servidorAgora() ?: return false
        api = Rede.api(guardado, cliente)
        base = guardado
        return cofre.sessaoEmMemoria != null
    }

    /// Descobre em qual endereço o Odeon atende.
    ///
    /// ## Ele pergunta antes de mandar senha
    ///
    /// Tenta `/api/auth/status` em cada candidato — https primeiro, http depois.
    /// É a única rota que responde sem sessão, e ela serve de duas maneiras:
    /// confirma que ali **tem** um Odeon, e diz se ele ainda precisa de
    /// configuração inicial.
    ///
    /// Mandar `username` e `password` pra um endereço não confirmado seria
    /// entregar a senha da casa pra qualquer coisa que atenda naquela porta.
    suspend fun procurarServidor(digitado: String): ResultadoDaProcura = withContext(Dispatchers.IO) {
        val candidatos = EnderecoDoServidor.candidatos(digitado)
        if (candidatos.isEmpty()) return@withContext ResultadoDaProcura.EnderecoInvalido

        var ultimaFalha: Throwable? = null

        for (url in candidatos) {
            val tentativa = Rede.api(url, cliente)
            try {
                val status = tentativa.status()
                api = tentativa
                base = url
                cofre.guardarServidor(url)
                return@withContext ResultadoDaProcura.Achou(url, status.precisaConfigurar)
            } catch (e: IOException) {
                // Não respondeu neste esquema/porta. Segue pro próximo candidato
                // — é exatamente o caso que a lista existe pra cobrir.
                ultimaFalha = e
            } catch (e: HttpException) {
                // Respondeu, mas não como um Odeon. Também não serve, e é uma
                // falha diferente de "não respondeu": vale guardar a última.
                ultimaFalha = e
            }
        }

        ResultadoDaProcura.NaoRespondeu(candidatos, ultimaFalha)
    }

    suspend fun entrar(usuario: String, senha: String): Usuario = withContext(Dispatchers.IO) {
        val api = api ?: error("entrar() sem servidor — procurarServidor() vem antes")

        val resposta = api.entrar(
            Credenciais(
                username = usuario,
                password = senha,
                deviceLabel = nomeDoAparelho(),
            ),
        )
        cofre.guardarSessao(resposta.token)
        resposta.user
    }

    suspend fun quemSouEu(): Usuario = withContext(Dispatchers.IO) {
        exigirApi().quemSouEu()
    }

    suspend fun biblioteca(pulando: Int = 0, limite: Int = PAGINA, busca: String? = null): List<ItemDaBiblioteca> =
        withContext(Dispatchers.IO) {
            exigirApi().biblioteca(limite = limite, pulando = pulando, busca = busca?.takeIf { it.isNotBlank() })
        }

    /// Garante que existe token de mídia — **sem pedir um novo se já houver**.
    ///
    /// ⚠️ É a regra do §43 em código: emitir aposenta o anterior. Um app que
    /// renova por precaução derruba o próprio player no meio do filme, e na fase
    /// 4 derruba o Chromecast sem o celular perceber.
    ///
    /// Falhar aqui não é fatal: sem token de mídia os pôsteres não carregam, mas
    /// a API continua de pé e a biblioteca ainda lista.
    suspend fun garantirTokenDeMidia() = withContext(Dispatchers.IO) {
        if (cofre.midiaEmMemoria != null) return@withContext
        runCatching { exigirApi().tokenDeMidia() }
            .onSuccess { cofre.guardarMidia(it.token) }
    }

    /// A URL de um pôster, com o token de mídia pendurado.
    ///
    /// `null` quando a obra não tem pôster — e são **4.794** assim hoje, contra
    /// 2.096 identificadas. Quem chama **tem** que tratar o nulo desenhando
    /// outra coisa, não um espaço com cara de imagem quebrada (§18).
    fun urlDoPoster(caminho: String?): String? {
        val caminho = caminho ?: return null
        val base = base ?: return null
        val token = cofre.midiaEmMemoria ?: return null
        return "$base/artwork/$caminho?token=$token"
    }

    suspend fun sair() = cofre.esquecerSessao()

    private fun exigirApi(): OdeonApi =
        api ?: error("chamada de API sem servidor — retomar() ou procurarServidor() vem antes")

    /// O rótulo que aparece na tela de aparelhos do admin.
    ///
    /// `Build.MODEL` sozinho vira "SM-A536B", que não diz nada. Com o fabricante
    /// na frente vira "samsung SM-A536B" — feio, mas identificável, que é o que
    /// a tela precisa.
    private fun nomeDoAparelho(): String {
        val fabricante = Build.MANUFACTURER.orEmpty()
        val modelo = Build.MODEL.orEmpty()
        return when {
            modelo.startsWith(fabricante, ignoreCase = true) -> modelo
            fabricante.isBlank() -> modelo.ifBlank { "Android" }
            else -> "$fabricante $modelo".trim()
        }
    }

    companion object {
        /// Quantas linhas por página.
        ///
        /// 60 e não 500: o servidor devolve o `total` em toda linha, então a
        /// contagem não custa uma segunda requisição — e uma primeira tela que
        /// espera 500 pôsteres é uma primeira tela lenta.
        const val PAGINA = 60
    }
}

/// O que a procura por servidor pode devolver.
///
/// Um `sealed` e não um `String?` de erro: cada caso desenha uma frase diferente
/// na tela, e o §8b não perdoa um clique que não faz nada.
sealed interface ResultadoDaProcura {
    data class Achou(val url: String, val precisaConfigurar: Boolean) : ResultadoDaProcura
    data object EnderecoInvalido : ResultadoDaProcura
    data class NaoRespondeu(val tentados: List<String>, val causa: Throwable?) : ResultadoDaProcura
}
