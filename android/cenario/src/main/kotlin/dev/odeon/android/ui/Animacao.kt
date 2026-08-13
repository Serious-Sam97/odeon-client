package dev.odeon.android.ui

import android.provider.Settings
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/// A preferência de movimento do sistema, em fração.
///
/// `1f` é animação normal, `0f` é **desligada**. O padrão na ausência de valor é
/// `1f` porque falhar pro lado de ligado aqui seria o erro errado: quem desligou
/// animação nos ajustes pediu explicitamente.
///
/// ## ⚠️ Ela existe pros `delay`, e **não** pros `tween`
///
/// É a distinção que fez este arquivo nascer, e é fácil de errar nos dois
/// sentidos:
///
/// | | escala do sistema |
/// |---|---|
/// | `tween(300)` dentro de `animateTo` | **já é aplicada sozinha** — o Compose lê o `MotionDurationScale` do contexto, que no Android sai deste mesmo ajuste |
/// | `delay(300)` numa corrotina | **não é** — é espera de corrotina, não animação |
///
/// Multiplicar a duração de um `tween` por esta escala aplica o desconto **duas
/// vezes**: com o sistema em 0,5 a animação sairia em 25% do tempo. E deixar um
/// `delay` sem ela faz a coreografia inteira parar de respeitar a preferência —
/// as partes que se movem obedecem, e as pausas entre elas não.
///
/// Coreografia com pausas — como a [dev.odeon.android.ui.player.CortinaDeAbertura] —
/// precisa das duas coisas: `tween` cru e `delay` multiplicado.
///
/// E vale checar o **zero** antes de tudo: com a escala em 0 os `tween` terminam
/// no primeiro quadro, mas os `delay` continuariam esperando o tempo cheio. O
/// jeito certo é não entrar na coreografia.
///
/// ## Por que não é lida do `Inclinacao` nem da `Marquise`
///
/// A `Marquise` ganha isso de graça — `rememberInfiniteTransition` já consulta o
/// `MotionDurationScale`. O `Inclinacao` lê o ajuste à mão dentro de um
/// `DisposableEffect`, e por um motivo que não é o daqui: ele decide **registrar
/// ou não um sensor**, e essa decisão tem ciclo de vida próprio. Unificar os
/// dois faria a leitura de uma preferência de desenho carregar o registro de um
/// acelerômetro junto.
@Composable
fun escalaDeAnimacao(): Float {
    val contexto = LocalContext.current
    /// Lida uma vez por composição. Mudar a preferência é ir aos ajustes do
    /// sistema e voltar — e voltar recompõe.
    return remember(contexto) {
        runCatching {
            Settings.Global.getFloat(
                contexto.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f)
    }
}
