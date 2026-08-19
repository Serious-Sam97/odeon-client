package dev.odeon.android.ui

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.Text
import dev.odeon.android.R

/// O saguão — o que o app mostra **antes de saber se há sessão**.
///
/// ## ⚠️ O nome não é enfeite: `Chegada` já existe, e a colisão derrubou o app
///
/// Esta peça nasceu chamada `Chegada.kt`, no pacote `dev.odeon.android.ui` — e o
/// `:cenario` **já tem** um `Chegada.kt` no mesmo pacote, com o modificador
/// `chega` das caixas caindo na prateleira. Dois arquivos de mesmo nome no mesmo
/// pacote viram **a mesma classe JVM** (`ChegadaKt`), e o APK saiu com duas: uma
/// no `classes7.dex` e outra no `classes17.dex`.
///
/// O compilador não reclamou — são módulos diferentes. Quem reclamou foi o
/// aparelho, em tempo de execução:
///
/// ```
/// NoSuchMethodError: No static method Chegada(Landroidx/compose/runtime/Composer;I)V
///   in class Ldev/odeon/android/ui/ChegadaKt;
/// ```
///
/// ⚠️ E o sintoma **mentia**: parecia dex velho de instalação incremental, e eu
/// gastei um `clean` e uma instalação direta do APK atrás disso. O `.class`
/// compilado tinha a assinatura certa o tempo todo; era o `dexdump` do APK que
/// mostrava as duas classes.
///
/// ## ⚠️ O que estava aqui era um risquinho no meio do preto
///
/// Medido no emulador em 16/08/2026: da abertura até a biblioteca aparecer
/// passam-se cerca de dez segundos — 4,3s só de arranque do processo (build de
/// depuração, emulador), e o resto entre `retomar()` e a carga do acervo.
///
/// Nesses segundos o app era um `CircularProgressIndicator` dourado num vão
/// preto. Sem título, sem marca, sem forma: a **primeira tela** de um app que se
/// vende como uma sala de cinema era indistinguível da tela de carregamento de
/// qualquer coisa.
///
/// E era pior que feio: o sistema tinha acabado de desenhar o carretel na
/// splash, e o app trocava esse carretel por um risco. A identidade aparecia e
/// **sumia** justamente no ponto em que não havia mais nada pra olhar.
///
/// ## Por que não é um esqueleto, como na biblioteca
///
/// Um esqueleto promete uma forma — «vêm cartazes, deste tamanho, aqui». Aqui
/// não se sabe **qual tela vem**: a resposta do cofre decide entre a biblioteca
/// e o login, e desenhar molduras de cartaz pra quem vai cair no login seria
/// prometer um acervo a quem ainda não entrou (§18).
///
/// O que se pode afirmar sem mentir é **de quem é o app**. Então é a marca, e
/// ela respira — devagar, porque é espera e não carregamento de barra.
@Composable
fun Saguao() {
    val pulso = rememberInfiniteTransition(label = "respiro do saguão")
    /// ⚠️ 1400ms e de 0,45 a 1,0 — **lento e sem apagar de todo**. Um pisca-pisca
    /// rápido lê como alerta; a marca sumindo por completo lê como falha de
    /// desenho. O que se quer é o sinal mínimo de que a tela está viva, que é o
    /// único trabalho que sobrou pro indicador que saiu daqui.
    val brilho by pulso.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400), RepeatMode.Reverse),
        label = "brilho",
    )

    /// ⚠️ **Sem altura fixa**, e é conserto de defeito visto: a primeira versão
    /// travava a coluna em 160dp com um carretel de 140dp dentro, e a palavra
    /// «odeon» era espremida pra fora do quadro. Na tela sobrava só o carretel —
    /// exatamente a splash de novo, sem o que esta peça tinha a acrescentar.
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically),
    ) {
        /// O mesmo carretel da splash, e é o ponto: a marca **continua** em vez
        /// de ser substituída. Quem olha não vê duas telas, vê uma que ficou.
        Image(
            painter = painterResource(R.drawable.ic_launcher_foreground),
            contentDescription = null,
            colorFilter = ColorFilter.tint(Cores.destaque),
            modifier = Modifier.size(120.dp).alpha(brilho),
        )
        Text(
            text = "odeon",
            fontFamily = Serifada,
            fontSize = 22.sp,
            color = Cores.destaqueApagado,
            textAlign = TextAlign.Center,
        )
    }
}
