package dev.odeon.android.ui

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp

/// As coisas **caem** no lugar em vez de aparecerem — leva 3.
///
/// ## É o `caixa-cai` da web, e ela responde "o que chegou?"
///
/// A folha anima cada caixa entrando na prateleira, escalonada, e o motivo está
/// no comentário dela: a estante «nasce com a madeira desenhada e as caixas caem
/// uma a uma». Sem isso a fileira inteira pisca do nada — e piscar do nada é o
/// que uma lista faz, não o que uma prateleira faz.
///
/// Os números saem do `@keyframes caixa-cai` (`styles.css:4229`):
///
/// | | web | aqui |
/// |---|---|---|
/// | duração | 0.42s | 420ms |
/// | de onde | `translate: 0 -26px` | −26dp |
/// | o repique | passa de 0 e volta (70% em +4px) | a curva faz o mesmo |
/// | atraso entre irmãs | `var(--atraso)` | 40ms × índice |
///
/// ## ⚠️ Onde ela NÃO entra, e por quê
///
/// **Na grade de 8.316.** A web também não põe: o `caixa-cai` é do
/// `.fileira .caixa` — as fileiras da locadora —, não da biblioteca. Numa lista
/// preguiçosa os itens são recompostos ao entrarem na tela, então a animação
/// dispararia a cada rolagem, e o que é chegada vira tremor.
///
/// ## O custo, e como ele fica em zero depois
///
/// Enquanto anima há uma camada por item. **Terminada a animação o modificador
/// devolve `this` inteiro** — sem `graphicsLayer`, sem camada, sem nada. É a
/// mesma forma do afundar do cartaz: gratuito no estado de repouso, por
/// construção.
///
/// E ela some sozinha pra quem desligou animação no sistema: `animateFloatAsState`
/// lê o `MotionDurationScale`, então a duração vai a zero e o item nasce no
/// lugar.
@Composable
fun Modifier.chega(indice: Int = 0): Modifier {
    var chegou by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { chegou = true }

    /// A curva do `cubic-bezier(0.2, 0.8, 0.3, 1)` da folha: sai rápido, passa
    /// um pouco do lugar e assenta. É ela que dá o repique sem precisar de um
    /// quadro-chave a mais.
    val curva = CubicBezierEasing(0.2f, 0.8f, 0.3f, 1f)
    val atraso = indice * 40

    val queda by animateFloatAsState(
        targetValue = if (chegou) 0f else -26f,
        animationSpec = tween(420, delayMillis = atraso, easing = curva),
        label = "queda da chegada",
    )
    val opacidade by animateFloatAsState(
        targetValue = if (chegou) 1f else 0f,
        animationSpec = tween(300, delayMillis = atraso, easing = curva),
        label = "opacidade da chegada",
    )

    return if (queda == 0f && opacidade == 1f) {
        this
    } else {
        this.graphicsLayer {
            translationY = queda.dp.toPx()
            alpha = opacidade
        }
    }
}
