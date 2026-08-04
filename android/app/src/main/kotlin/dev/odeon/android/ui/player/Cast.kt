package dev.odeon.android.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.media3.cast.CastPlayer
import androidx.media3.common.Player
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener

/// O estado do Cast, do ponto de vista da tela.
data class EstadoDoCast(
    /// O `CastPlayer`, quando há sessão. **É um `Player`** — e é isso que faz a
    /// troca ser troca de instância e não reescrita de tela.
    val player: Player? = null,
    /// O nome do aparelho, pra tela poder dizer "na TV da sala" em vez de "na
    /// TV". Nulo quando não há sessão.
    val aparelho: String? = null,
    /// Por que o Cast não está disponível. Nulo quando está.
    val impedimento: String? = null,
) {
    val conectado: Boolean get() = player != null
}

/// Liga no Cast e devolve o estado dele.
///
/// ## Ele nunca pode derrubar o resto do app
///
/// O `CastContext.getSharedInstance` lança quando o Google Play Services está
/// velho, ausente ou desligado — e isso é comum em emulador e em aparelho sem
/// Play. As fases 1 a 3 estão **verificadas rodando**, e uma dependência nova da
/// fase 4 não tem o direito de quebrá-las.
///
/// Por isso tudo aqui é embrulhado: falhar vira `impedimento`, que a tela mostra
/// como uma linha discreta, e o player local continua tocando.
///
/// ## E ele checa o endereço antes de se oferecer
///
/// Quem busca o vídeo é o Chromecast, e ele não entra na tailnet. Com o servidor
/// em `100.x`, mandar pra TV seria mandar um endereço que ela não alcança — tela
/// preta sem explicação, num aparelho onde nem há onde explicar. Ver
/// `dados/Cast.kt`.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun lembrarCast(baseDoServidor: String?): EstadoDoCast {
    val contexto = LocalContext.current
    var estado by remember { mutableStateOf(EstadoDoCast()) }

    DisposableEffect(baseDoServidor) {
        if (!dev.odeon.android.dados.EnderecoParaCast.alcancavelPelaTv(baseDoServidor)) {
            /// A frase diz **o motivo e onde se resolve**, não só "não dá" — é a
            /// mesma régua do `acesso::negado()` do servidor.
            estado = EstadoDoCast(
                impedimento = "o servidor está na tailnet, e a TV não entra nela — " +
                    "o Cast só funciona com o Odeon na rede de casa",
            )
            return@DisposableEffect onDispose { }
        }

        val castContext = runCatching { CastContext.getSharedInstance(contexto) }.getOrNull()
        if (castContext == null) {
            estado = EstadoDoCast(impedimento = "o Google Play Services não respondeu")
            return@DisposableEffect onDispose { }
        }

        fun sincronizar(sessao: CastSession?) {
            estado = if (sessao != null && sessao.isConnected) {
                EstadoDoCast(
                    player = runCatching { CastPlayer(castContext) }.getOrNull(),
                    aparelho = sessao.castDevice?.friendlyName,
                )
            } else {
                EstadoDoCast()
            }
        }

        val ouvinte = object : SessionManagerListener<CastSession> {
            override fun onSessionStarted(sessao: CastSession, id: String) = sincronizar(sessao)
            override fun onSessionResumed(sessao: CastSession, retomou: Boolean) = sincronizar(sessao)
            override fun onSessionEnded(sessao: CastSession, erro: Int) = sincronizar(null)
            override fun onSessionSuspended(sessao: CastSession, motivo: Int) = sincronizar(null)
            override fun onSessionStarting(sessao: CastSession) = Unit
            override fun onSessionStartFailed(sessao: CastSession, erro: Int) = sincronizar(null)
            override fun onSessionEnding(sessao: CastSession) = Unit
            override fun onSessionResuming(sessao: CastSession, id: String) = Unit
            override fun onSessionResumeFailed(sessao: CastSession, erro: Int) = sincronizar(null)
        }

        val gerente = castContext.sessionManager
        gerente.addSessionManagerListener(ouvinte, CastSession::class.java)
        sincronizar(gerente.currentCastSession)

        onDispose {
            gerente.removeSessionManagerListener(ouvinte, CastSession::class.java)
            /// O `CastPlayer` é solto, **a sessão não**. Sair da tela do player
            /// não devia desligar a TV — quem desliga é quem tocou no botão.
            estado.player?.release()
        }
    }

    return estado
}
