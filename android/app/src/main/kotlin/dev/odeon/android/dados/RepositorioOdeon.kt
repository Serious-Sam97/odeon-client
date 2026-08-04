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

    // ----------------------------------------------------------------- fase 2

    suspend fun obra(id: String): ObraDetalhada = withContext(Dispatchers.IO) {
        exigirApi().obra(id)
    }

    /// O que este usuário tem emprestado. **É da fase 5**, e não do player.
    ///
    /// ⚠️ Não chame isto antes de desenhar o play. A regra mudou no servidor
    /// (§71 do `DESIGN.md`): a exigência de empréstimo vale **dentro da
    /// locadora**, e a biblioteca é modo livre. A tela da obra chegou a
    /// perguntar, e o efeito foi esconder o play do acervo inteiro — a história
    /// está em `ui/obra/ModeloDaObra.kt`.
    ///
    /// ⚠️ Falhar aqui **não** pode virar "nada liberado". Uma tela que nasce
    /// dizendo "pegar na locadora" pra tudo e conserta meio segundo depois mente
    /// duas vezes em vez de uma. Sem resposta, segue sem trancar nada.
    suspend fun liberadas(): Liberadas = withContext(Dispatchers.IO) {
        runCatching { exigirApi().liberadas() }.getOrDefault(Liberadas(exige = false))
    }

    /// O plano de reprodução.
    ///
    /// ⚠️ `paraCast` troca **o sujeito da pergunta**. A rota recebe os codecs
    /// "do cliente", e o §M6 do servidor assume que quem pergunta é quem toca —
    /// com Cast isso deixa de valer: quem toca é o Chromecast.
    ///
    /// Mandar o perfil do celular durante um cast pediria Direct Play de um HEVC
    /// que a TV não abre, e o defeito apareceria como tela preta na sala. Ver
    /// `PerfilDeCast`, e a §4c da espec — que registra que isto **não custa
    /// backend**, porque a rota já aceita a lista como parâmetro.
    suspend fun plano(arquivoId: String, paraCast: Boolean = false): PlanoDeReproducao = withContext(Dispatchers.IO) {
        /// As capacidades vão pro log porque **elas decidem o modo**, e o modo
        /// mudou entre duas execuções do mesmo arquivo sem ninguém mexer em
        /// nada: uma vez `direto`, outra `transcodificando — o cliente não toca
        /// áudio em ac3`. Sem saber o que foi enviado, os dois lados parecem
        /// certos e o desacordo não tem onde ser investigado.
        android.util.Log.i(
            "Odeon",
            "plano($arquivoId) capacidades: v=${CapacidadesDoAparelho.codecsDeVideo} " +
                "a=${CapacidadesDoAparelho.codecsDeAudio} c=${CapacidadesDoAparelho.containers}",
        )
        exigirApi().plano(
            arquivoId = arquivoId,
            containers = if (paraCast) PerfilDeCast.CONTAINERS else CapacidadesDoAparelho.containers,
            codecsDeVideo = if (paraCast) PerfilDeCast.VIDEO else CapacidadesDoAparelho.codecsDeVideo,
            codecsDeAudio = if (paraCast) PerfilDeCast.AUDIO else CapacidadesDoAparelho.codecsDeAudio,
        )
    }

    suspend fun abrirSessao(
        arquivoId: String,
        comecandoEm: Int,
        paraCast: Boolean = false,
    ): SessaoDeTranscodificacao =
        withContext(Dispatchers.IO) {
            exigirApi().abrirSessao(
                arquivoId = arquivoId,
                containers = if (paraCast) PerfilDeCast.CONTAINERS else CapacidadesDoAparelho.containers,
                codecsDeVideo = if (paraCast) PerfilDeCast.VIDEO else CapacidadesDoAparelho.codecsDeVideo,
                codecsDeAudio = if (paraCast) PerfilDeCast.AUDIO else CapacidadesDoAparelho.codecsDeAudio,
                comecandoEm = comecandoEm,
            )
        }

    // ----------------------------------------------------------------- fase 7

    suspend fun paraVoce(minutos: Int? = null): ParaVoce = withContext(Dispatchers.IO) {
        exigirApi().paraVoce(minutos = minutos)
    }

    // ----------------------------------------------------------------- fase 5

    suspend fun prateleira(): Prateleira = withContext(Dispatchers.IO) {
        exigirApi().prateleira()
    }

    /// Pegar a fita. **Escreve em produção** — ver `OdeonApi.alugar`.
    suspend fun alugar(obraId: String): RespostaDoAluguel = withContext(Dispatchers.IO) {
        exigirApi().alugar(AlvoDaCaixa(obraId))
    }

    /// Devolver. Também escreve.
    suspend fun devolver(emprestimoId: Int) = withContext(Dispatchers.IO) {
        exigirApi().devolver(emprestimoId)
        Unit
    }

    /// De onde continuar.
    ///
    /// Falha vira lista vazia, e é escolha: a fileira de "continuar" fica
    /// **acima** da biblioteca, e derrubar a tela inteira porque essa consulta
    /// não voltou seria trocar o acervo por um erro. Sem ela, a grade aparece
    /// igual — a fileira é que some.
    suspend fun paraContinuar(): List<ItemPraContinuar> = withContext(Dispatchers.IO) {
        runCatching { exigirApi().paraContinuar() }.getOrDefault(emptyList())
    }

    /// A URL de uma arte qualquer (`still`, `backdrop` ou `poster`), com o token.
    ///
    /// É a mesma montagem do `urlDoPoster` — as três moram em `/artwork/`. Existe
    /// separada só porque o nome "pôster" mentiria sobre as outras duas.
    fun urlDaArte(caminho: String?): String? = urlDoPoster(caminho)

    /// Encerra a sessão de HLS. Ver `OdeonApi.encerrarSessao`.
    ///
    /// Falha em silêncio de propósito: isto roda quando a tela já está indo
    /// embora, e não há mais onde mostrar erro. O servidor tem um reaper pro
    /// caso de a chamada não chegar — ele é a rede de segurança, não o plano.
    suspend fun encerrarSessao(sessaoId: String) = withContext(Dispatchers.IO) {
        runCatching { exigirApi().encerrarSessao(sessaoId) }
            .onFailure { android.util.Log.w("Odeon", "sessão $sessaoId não encerrou: $it") }
        Unit
    }

    /// Marca onde parou.
    ///
    /// ## Falhar aqui é **silencioso de propósito**, e é a exceção ao §8b
    ///
    /// A regra da casa diz que errar calado é o defeito. Aqui vale o contrário,
    /// e o motivo é o que esta chamada é: ela roda a cada poucos segundos com o
    /// filme na tela. Uma tarja vermelha por cima do vídeo porque uma marca de
    /// progresso não subiu interromperia justamente o que ela existe pra
    /// preservar — e a próxima marca, segundos depois, conserta sozinha.
    ///
    /// O que **não** é silencioso é a marca do fim: quando o player para, a
    /// última chamada é a que decide se dá pra continuar amanhã.
    suspend fun marcarProgresso(
        obraId: String,
        posicaoEmSegundos: Double,
        duracaoEmSegundos: Double?,
        arquivoId: String,
        tipo: String,
    ): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            exigirApi().marcarProgresso(
                obraId = obraId,
                marca = MarcaDeProgresso(
                    posicaoEmSegundos = posicaoEmSegundos,
                    duracaoEmSegundos = duracaoEmSegundos,
                    arquivoId = arquivoId,
                    tipo = tipo,
                    aparelhoId = cofre.aparelhoEmMemoria,
                ),
            )
        }.onFailure {
            /// Silencioso na tela, **não** no log.
            ///
            /// A tela cala porque uma tarja vermelha por cima do filme por causa
            /// de uma marca perdida é pior que a marca perdida. Mas calar nos dois
            /// lugares é como um progresso que nunca sobe vira "o servidor não
            /// guarda isso" na cabeça de quem for investigar.
            android.util.Log.w("Odeon", "progresso não subiu ($tipo, ${posicaoEmSegundos}s): $it")
        }.isSuccess
    }

    /// A folha de sprites, ou `null` quando ainda não foi gerada.
    ///
    /// ⚠️ **Só o 404 vira `null`.** Qualquer outro código sobe como erro, e isso
    /// não é rigor gratuito: a web já perdeu os sprites de todo o acervo por
    /// tratar "não deu certo" como "não existe" — um 401 virou "sem preview",
    /// calado, e os sprites que estavam no banco nunca apareceram.
    suspend fun folhaDeSprites(arquivoId: String): FolhaDeSprites? = withContext(Dispatchers.IO) {
        try {
            exigirApi().folhaDeSprites(arquivoId)
        } catch (e: HttpException) {
            if (e.code() == 404) null else throw e
        }
    }

    /// Uma URL de mídia com o token pendurado.
    ///
    /// Serve pro vídeo direto e pra playlist de HLS, e as duas vêm do servidor
    /// como caminho relativo. É o mesmo mecanismo do pôster — e o mesmo motivo:
    /// o player não manda header, então o token viaja na query (§43).
    ///
    /// ⚠️ O token **não se renova aqui**. Emitir um novo aposenta o anterior, e
    /// o anterior pode estar dentro de um player tocando neste segundo.
    fun urlDeMidia(caminhoOuUrl: String?): String? {
        val caminho = caminhoOuUrl ?: return null
        val base = base ?: return null
        val token = cofre.midiaEmMemoria ?: return null
        val completa = if (caminho.startsWith("http")) caminho else "$base${caminho.ensurePrefix()}"
        val separador = if ('?' in completa) "&" else "?"
        return "$completa${separador}token=$token"
    }

    private fun String.ensurePrefix(): String = if (startsWith("/")) this else "/$this"

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
