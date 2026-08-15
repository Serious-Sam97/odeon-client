package dev.odeon.android.tv.ui

import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

/// Rolar uma tela de **leitura** com o controle.
///
/// ## ⚠️ Por que isto precisou existir
///
/// Numa TV o foco é quem rola: a `LazyColumn` só anda quando o foco entra num
/// item que está fora da vista. Isso funciona na biblioteca, onde todo item é um
/// cartaz focável — e falha inteiro numa tela que é **texto**.
///
/// O sintoma foi visto duas vezes, e o dono relatou a primeira com estas
/// palavras: «ir pra baixo faz aparecer o menu lateral». Sem nada focável
/// abaixo, o ▼ procura vizinho, não acha, e o único focável em qualquer direção é
/// o trilho à esquerda. A pessoa não está errando o botão — a tela é que não tem
/// para onde mandá-la.
///
/// No ao vivo o conserto foi tornar cada faixa da grade focável, porque lá cada
/// faixa **é** uma coisa. Aqui não: uma lista de oitenta conquistas não tem
/// oitenta destinos, tem um texto comprido. Então quem ganha foco é a coluna
/// inteira, e as setas rolam a página — que é como se lê uma página.
///
/// ⚠️ **Nas pontas o evento não é consumido.** No topo, ▲ tem de escapar pra
/// quem está acima (na tela do perfil, o botão de sair); no fim, ▼ tem de
/// escapar também. Consumir sempre prenderia o foco aqui dentro para sempre — um
/// poço, e o pior tipo, porque parece que o controle parou de funcionar.
@Composable
fun Modifier.rolavelComOControle(estado: LazyListState): Modifier {
    val escopo = rememberCoroutineScope()
    val densidade = LocalDensity.current

    /// Um terço de tela por aperto. Menos que isso obriga a marretar o botão numa
    /// lista longa; mais que isso perde o fio — o olho não acha onde parou.
    val passo = with(densidade) { PASSO_DA_ROLAGEM.toPx() }

    return this
        /// ⚠️ `onKeyEvent` **antes** de `focusable`, e é a mesma ordem que o
        /// player e o ao vivo já usam nesta casa: escrita ao contrário ela
        /// simplesmente não roda.
        .onKeyEvent { evento ->
            if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
            val sentido = when (evento.key) {
                Key.DirectionDown -> 1f
                Key.DirectionUp -> -1f
                else -> return@onKeyEvent false
            }

            val noTopo = !estado.canScrollBackward
            val noFim = !estado.canScrollForward
            if ((sentido < 0 && noTopo) || (sentido > 0 && noFim)) return@onKeyEvent false

            escopo.launch { estado.animateScrollBy(sentido * passo) }
            true
        }
        .focusable()
}

private val PASSO_DA_ROLAGEM = 260.dp
