package dev.odeon.android.dados

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request

/// O barramento — **uma conexão SSE pro app inteiro** (§62).
///
/// ## Ele era a maior falta do app, e era de fase 3
///
/// A `PARIDADE-ANDROID.md` §1.3 registrava: «nenhuma linha do app abre
/// `/api/events`». O item 10 do checklist é fase 3, e as fases 4 a 7 tinham sido
/// feitas por cima dele.
///
/// O que ele custava, medido pelo que o app já tem: **a sincronia entre
/// aparelhos**. Parar um filme na TV e o celular continuar dizendo que ele está
/// no minuto zero — que é justamente a tese da §5 da espec («você parou na TV e
/// continua no ônibus») acontecendo pela metade.
///
/// ## Uma conexão, e não uma por tela
///
/// A web aprendeu isso e escreveu no §62; aqui o motivo é maior. Um SSE por tela
/// seriam cinco conexões abertas contra um servidor de casa que já roda o
/// Postgres, o ffmpeg e a identificação — e cinco reconexões cada vez que o wifi
/// pisca. Por isso ele mora no `OdeonApp`, ao lado do OkHttp e do cofre: o que
/// dura o app inteiro.
///
/// ## O token vai na query, e é o de mídia
///
/// `EventSource` não manda header — a web não tem escolha, e o servidor aceita
/// `?token=` por causa disso. O app **poderia** mandar header, e mesmo assim
/// manda na query: é o mesmo endereço que a web usa, e o servidor não precisa
/// aprender um segundo jeito de autenticar a mesma rota.
///
/// ⚠️ Sem token de mídia não há barramento — e isso é normal no arranque, antes
/// de `garantirTokenDeMidia`. Quem chama `ligar` de novo depois disso reconecta.
class Barramento(
    private val cofre: Cofre,
    private val cliente: OkHttpClient,
) {

    private val _eventos = MutableSharedFlow<EventoDoServidor>(extraBufferCapacity = 32)

    /// O que chegou do servidor. Quem quiser, ouve — e ninguém precisa saber que
    /// há uma conexão só por trás.
    val eventos: SharedFlow<EventoDoServidor> = _eventos.asSharedFlow()

    private val json = Json { ignoreUnknownKeys = true }

    @Volatile
    private var ligado = false

    /// Liga o barramento. Chamar duas vezes não abre duas conexões.
    fun ligar(escopo: CoroutineScope, base: String) {
        if (ligado) return
        ligado = true
        escopo.launch { manterLigado(base) }
    }

    /// O laço de reconexão.
    ///
    /// **Cinco tentativas**, como a web. O limite existe pra o app não ficar
    /// batendo num servidor que está fora — quem volta do zero é a próxima
    /// abertura da tela, que é quando alguém está olhando e quer o dado fresco.
    ///
    /// A espera cresce (1s, 2s, 4s…) porque a causa mais comum de queda é o wifi
    /// trocando de rede, e nesse caso a segunda tentativa falha tão rápido quanto
    /// a primeira.
    private suspend fun manterLigado(base: String) {
        var tentativa = 0
        while (tentativa < MAXIMO_DE_TENTATIVAS) {
            val token = cofre.midiaEmMemoria
            if (token == null) {
                /// Sem token não dá pra conectar, e isso não é falha: é o
                /// arranque. Espera e tenta de novo, **sem gastar tentativa** —
                /// senão o app queimaria as cinco antes de fazer login.
                delay(2_000)
                continue
            }

            val caiu = escutar("$base/api/events?token=$token")
            if (!caiu) return

            tentativa++
            delay(1_000L shl (tentativa - 1).coerceAtMost(4))
        }
        android.util.Log.w("Odeon", "barramento desistiu depois de $MAXIMO_DE_TENTATIVAS tentativas")
    }

    /// Abre a conexão e lê até ela cair. Devolve `true` se vale reconectar.
    private suspend fun escutar(url: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val pedido = Request.Builder().url(url).header("Accept", "text/event-stream").build()
            cliente.newCall(pedido).execute().use { resposta ->
                if (!resposta.isSuccessful) {
                    android.util.Log.w("Odeon", "barramento recusado: ${resposta.code}")
                    /// 401 é token vencido, e reconectar com o mesmo token daria
                    /// 401 de novo. Os outros códigos valem uma segunda chance.
                    return@withContext resposta.code != 401
                }

                val corpo = resposta.body ?: return@withContext true
                val fonte = corpo.source()

                var tipo: String? = null
                val dados = StringBuilder()

                while (!fonte.exhausted()) {
                    val linha = fonte.readUtf8LineStrict()
                    when {
                        /// Linha em branco fecha o evento. É o formato do SSE, e
                        /// é o único ponto em que a mensagem está inteira.
                        linha.isBlank() -> {
                            despachar(tipo, dados.toString())
                            tipo = null
                            dados.clear()
                        }
                        linha.startsWith("event:") -> tipo = linha.removePrefix("event:").trim()
                        linha.startsWith("data:") -> dados.append(linha.removePrefix("data:").trim())
                        /// `:` sozinho é comentário — o servidor manda como
                        /// batida de vida, e ignorar é o comportamento certo.
                        else -> Unit
                    }
                }
            }
            true
        } catch (e: Exception) {
            android.util.Log.w("Odeon", "barramento caiu: $e")
            true
        }
    }

    /// Monta o evento e joga no fluxo — **descartando o próprio eco**.
    ///
    /// ## O eco é o motivo de o `device_id` existir
    ///
    /// Todo aparelho que manda progresso recebe o próprio progresso de volta pelo
    /// barramento. Sem descartar, o player receberia «alguém está no minuto 12»
    /// meio segundo depois de ele mesmo dizer isso — e como a posição já andou,
    /// ele saltaria pra trás. O sintoma seria um filme que gagueja sozinho.
    private suspend fun despachar(tipo: String?, dados: String) {
        if (tipo == null || dados.isBlank()) return

        val objeto = runCatching { json.parseToJsonElement(dados).jsonObject }.getOrNull() ?: return
        fun texto(chave: String) = objeto[chave]?.jsonPrimitive?.contentOrNull
        fun numero(chave: String) = objeto[chave]?.jsonPrimitive?.contentOrNull?.toDoubleOrNull()

        val deQuemVeio = texto("device_id")
        if (deQuemVeio != null && deQuemVeio == cofre.aparelhoEmMemoria) return

        val evento = when (tipo) {
            "progress" -> EventoDoServidor.Progresso(
                obraId = texto("work_id") ?: return,
                posicaoEmSegundos = numero("position_seconds") ?: 0.0,
            )
            "locadora" -> EventoDoServidor.NaLocadora(
                oQue = texto("o_que") ?: "",
                titulo = texto("titulo"),
                quem = texto("quem_nome"),
            )
            /// Os outros cinco tipos do §1.4 da referência não têm tela que os
            /// ouça ainda — mural, junto, ao vivo e as faixas de progresso do
            /// servidor. Eles passam como `Outro` em vez de sumirem: quem for
            /// escrever essas telas vai querer saber que o barramento já os
            /// entrega, e um `when` que engole tipo desconhecido é como um evento
            /// novo do servidor nunca aparece.
            else -> EventoDoServidor.Outro(tipo)
        }

        _eventos.emit(evento)
    }

    private companion object {
        const val MAXIMO_DE_TENTATIVAS = 5
    }
}

/// O que o servidor manda pelo barramento.
sealed interface EventoDoServidor {
    /// Alguém — **noutro aparelho** — mexeu no progresso de uma obra.
    data class Progresso(val obraId: String, val posicaoEmSegundos: Double) : EventoDoServidor

    /// A locadora mudou: `pegou` · `devolveu` · `pediu` · `venceu`.
    data class NaLocadora(val oQue: String, val titulo: String?, val quem: String?) : EventoDoServidor {
        /// A frase pronta, montada no cliente pelo mesmo motivo do mural: só o
        /// cliente sabe o que cabe na tela dele. `null` num tipo que este app não
        /// conhece — e aí nada é mostrado, em vez de «fulano unknown X» (§18).
        val recado: String?
            get() {
                val quem = quem ?: return null
                val titulo = titulo ?: return null
                return when (oQue) {
                    "pegou" -> "$quem pegou $titulo"
                    "devolveu" -> "$quem devolveu $titulo"
                    "pediu" -> "$quem pediu $titulo de volta"
                    "venceu" -> "$titulo venceu na mão de $quem"
                    else -> null
                }
            }
    }

    /// Um tipo que ainda não tem tela. Ver o comentário do `despachar`.
    data class Outro(val tipo: String) : EventoDoServidor
}

private val kotlinx.serialization.json.JsonPrimitive.contentOrNull: String?
    get() = if (this is kotlinx.serialization.json.JsonNull) null else content
