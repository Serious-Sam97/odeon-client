package dev.odeon.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp

/// O dourado como **luz**, e não como tinta.
///
/// ## O diagnóstico que este arquivo existe pra consertar
///
/// Medido em 04/08/2026, comparando `ui/` com `web/src/styles.css`:
///
/// | uso | app | web |
/// |---|---|---|
/// | `destaque` / `--accent` | 26 | **210** |
/// | `destaqueApagado` / `--accent-dim` | **2** | **107** |
/// | `destaqueQuente` / `--accent-hot` | 2 | 19 |
/// | dourado como **luz** — sombra, halo, gradiente | **0** | **19** |
///
/// A última linha é a coisa toda. O app não tinha **um único** brilho, halo ou
/// gradiente de dourado — ele pintava com a cor, chapada, e mais nada.
///
/// E o `Tema.kt` já chamava os três tons de «o filamento aceso», «o topo da luz»
/// e «o filamento apagado» desde a fase 1. Os nomes descreviam uma sala acesa
/// que o código nunca desenhou.
///
/// ## A gramática, em três estados
///
/// Uma cor numa sala escura tem três estados, e o app só usava o do meio:
///
/// | | quem é | onde |
/// |---|---|---|
/// | **apagado** — `destaqueApagado` | o filamento frio | contorno, régua, borda que não pede nada |
/// | **aceso** — `destaque` | o corpo da luz | o que está ligado agora |
/// | **quente** — `destaqueQuente` | onde a luz bate | o topo de um gradiente, a beirada iluminada |
///
/// **Nenhum valor novo entra na paleta.** O que entra é gradiente, sombra
/// colorida e alfa — que é como as mesmas três cores passam a ter serventia.
object Luz {

    /// A barra que vai do filamento frio ao topo da luz.
    ///
    /// Substitui o dourado chapado das barras de progresso. A leitura muda: uma
    /// barra chapada é uma medida; uma barra que esquenta na ponta é uma coisa
    /// **acesa até ali**.
    val filamento = Brush.horizontalGradient(
        listOf(Cores.destaqueApagado, Cores.destaque, Cores.destaqueQuente),
    )

    /// O que um botão aceso tem por baixo.
    ///
    /// Radial vindo da **base** (`0.5f, 1.2f`), não do centro: luz de sala vem
    /// de baixo ou de trás, e um halo centrado lê como botão de sistema
    /// operacional. O 1.2 põe o foco fora do elemento, então o que se vê é a
    /// borda de cima do halo — a parte que parece derramar.
    val porBaixo = Brush.radialGradient(
        colors = listOf(Cores.destaque.copy(alpha = 0.26f), Color.Transparent),
        center = androidx.compose.ui.geometry.Offset(0.5f, 1.2f),
        radius = 1.4f,
    )
}

/// O contorno de uma coisa que pega luz.
///
/// 1dp de dourado a 22% — quase nada, e é o ponto. Sobre `#0A0A0C`, um cartaz
/// sem contorno flutua: não há aresta, e a arte parece adesivo colado no preto.
/// Com ele, o objeto tem borda, e borda é o que separa "imagem" de "coisa".
///
/// ⚠️ **Sem sombra aqui, e é decisão de custo.** A versão com `shadow` colorido
/// ficou pro herói e pra fileira de continuar, que têm poucos itens. A grade tem
/// 8.316, e sombra é a peça cara — cada uma é uma camada de composição. A
/// proposta já dizia que este seria o primeiro item a sair se a rolagem caísse;
/// ele entrou pela metade de propósito, na metade barata.
fun Modifier.pegaLuz(forma: Shape, forca: Float = 0.22f): Modifier =
    this.border(1.dp, Cores.destaque.copy(alpha = forca), forma)

/// A coisa **acesa**: contorno mais o halo que ela joga em volta.
///
/// Para o que é único na tela — o herói, o cartão de destaque, a caixa que está
/// na mão. Nunca para lista longa: ver o aviso do [pegaLuz].
///
/// `spotColor` e `ambientColor` são de **API 28**, e o `minSdk` é 26. Nos dois
/// níveis abaixo a sombra sai preta em vez de dourada — degrada pra sombra
/// comum, que continua dando profundidade. É o padrão da espec (§4): o moderno
/// entra atrás de uma checagem e ninguém fica de fora.
fun Modifier.acesa(forma: Shape, elevacao: Int = 16): Modifier =
    this
        .shadow(
            elevation = elevacao.dp,
            shape = forma,
            clip = false,
            spotColor = Cores.destaque,
            ambientColor = Cores.destaque,
        )
        .border(1.dp, Cores.destaque.copy(alpha = 0.30f), forma)

/// O fundo de um botão ou pílula que está ligada.
///
/// Some quando `ligado` é falso — e some pra `Modifier` vazio, não pra um fundo
/// transparente: uma camada invisível ainda mede e ainda desenha.
fun Modifier.acendePorBaixo(ligado: Boolean): Modifier =
    if (ligado) this.background(Luz.porBaixo) else this
