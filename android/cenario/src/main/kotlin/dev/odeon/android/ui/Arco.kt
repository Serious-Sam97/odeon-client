package dev.odeon.android.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.unit.dp
import kotlin.math.abs

/// A lâmpada de arco: a piscada, o cone, a poeira e a lente.
///
/// ## De onde este arquivo veio, e por que ele não é uma peça nova
///
/// Ele saiu do `ui/Facho.kt` do `:app` na T0 do `docs/REDESENHO-TV.md` (§3.2), e
/// **nenhum número aqui foi recalculado**. Os dez quadros-chave, as sete paradas
/// do radial, os dois primos do espalhamento, o `2.6f` do raio e o `19x13` do
/// passo da poeira estão copiados dígito por dígito. A §2.1 daquele documento
/// chama esses dez números de «o som da casa», e um deles trocado é outro som.
///
/// O que ficou pra trás foi a **barra**: `BarraDoFacho` continua no `:app`, com
/// os `ALTURA_DA_*`, o inset do gesto e a fileira de destinos. Aquilo é a barra
/// de baixo de um celular; isto é uma lâmpada.
///
/// A divisão é o achado da §2.1 virando código: o app tem **um** sistema de luz,
/// visto de três ângulos — a barra de destinos, as lâmpadas da marquise, e a
/// lente correndo sobre a película. Não são três efeitos parecidos; é um
/// projetor. Enquanto tudo morava dentro da barra de navegação, esse era um
/// argumento escrito num comentário. Agora é onde o código mora.
///
/// ## ⚠️ A geometria daqui ainda é a do celular, e isso é de propósito
///
/// A T0 **move**; ela não generaliza. Duas coisas nas funções abaixo supõem uma
/// luz que nasce **embaixo** e sobe:
///
/// | | |
/// |---|---|
/// | [desenhaOCone] | ⬅️ **não supõe nada** — o radial é isotrópico, e o que faz ele parecer um cone é o centro cair fora da área visível. Um feixe horizontal é o mesmo desenho com outro centro |
/// | [desenhaAPoeira] | supõe: a queda de alfa mede distância horizontal ao eixo **e** altura a partir da base |
/// | [desenhaALente] | a oval é deitada, 26x5dp — a proporção de uma lente vista de frente numa barra horizontal |
///
/// A §4.2 quer o feixe abrindo **pra direita** a partir da borda esquerda. O
/// cone atravessa de graça; a poeira e a lente vão precisar do outro eixo. Fazer
/// isso agora seria inventar uma API pra um chamador que ainda não existe — e a
/// T1 vai saber a forma certa depois de ter desenhado a cabine, não antes.

/// Os dez quadros de uma lâmpada de arco firmando.
///
/// Os tempos não são aleatórios — é a curva de uma lâmpada de arco: apaga quase
/// tudo, dá um pico **acima** do normal, cai, e assenta.
///
/// ## A curva de uma lenta não é a de uma curta esticada
///
/// Esticar seis quadros de 300ms para 1200ms daria um pulso lento e regular —
/// que lê como respiração, não como acendimento. Lâmpada de arco **oscila**, e a
/// oscilação **decai**: cada repique chega mais perto de 1 que o anterior, até
/// assentar. A amplitude cai de 0,35 para 0,03 — `0,92 · 0,35 · 0,78 · 0,15 ·
/// 0,60 · 0,08 · 0,32 · 0,03`.
///
/// ⚠️ **O primeiro pico passa de 1 de propósito** — é o estouro do arco, e é ele
/// que faz parecer que a luz **nasceu** em vez de aparecer.
const val DURACAO_DO_ARCO = 1200

/// O brilho de uma lâmpada firmando o arco, reacendendo a cada troca de [chave].
///
/// ## Ela é longa, e o `:app` tinha argumentado o contrário
///
/// A primeira versão durava **300ms**, e o comentário dizia: «piscada longa em
/// barra de navegação deixa de ser cinema e vira defeito de renderização — a
/// pessoa acha que a tela travou». O dono pediu bem mais lenta, e olhando de
/// novo o receio estava exagerado.
///
/// O motivo é que **nada da interface apaga**: a piscada multiplica só a luz —
/// o cone, a poeira e a lente. O que está escolhido continua em
/// `destaqueQuente` o tempo todo, e o conteúdo troca na hora. O que demora é a
/// lâmpada firmando, não o app respondendo.
///
/// Se durasse o mesmo tempo **e** apagasse o rótulo, aí o receio valeria.
///
/// ⚠️ Ela some sozinha pra quem desligou animação: `Animatable` lê o
/// `MotionDurationScale`, que no Android vem do `ANIMATOR_DURATION_SCALE`. Com a
/// preferência em zero o facho salta pro lugar aceso, sem pulsar. É o mesmo
/// mecanismo que o [escalaDeAnimacao] usa explicitamente, e aqui sai de graça.
@Composable
fun brilhoDoArco(chave: Any?): Float {
    val brilho = remember { Animatable(1f) }
    LaunchedEffect(chave) {
        brilho.snapTo(0.12f)
        brilho.animateTo(
            targetValue = 1f,
            animationSpec = keyframes {
                durationMillis = DURACAO_DO_ARCO
                0.08f at 0
                1.35f at 90
                0.22f at 200
                1.15f at 320
                0.40f at 450
                1.08f at 600
                0.68f at 780
                1.03f at 950
                0.90f at 1080
                1f at 1200
            },
        )
    }
    return brilho.value
}

/// O cone: o radial que abre a partir da lente.
///
/// ⚠️ **Sete paradas e raio grande, contra o banding.**
///
/// A primeira versão tinha três paradas e raio de 1,45×. O screenshot mostrou
/// exatamente o risco que a proposta previu: uma **aresta vertical** no lado
/// esquerdo do cone. Não era o desenho — era a queda de alfa cruzando o degrau
/// de 8 bits num fundo quase preto, e o olho lê isso como borda.
///
/// Duas medidas juntas: mais paradas fazem a queda ser gradual em vez de linear
/// por trecho, e o raio maior joga o fim do degradê **pra fora** da área
/// desenhada — a aresta continua existindo, só que onde não há pixel pra
/// mostrá-la. A poeira ajuda de brinde: ruído sobre degradê é o remédio clássico
/// de banding.
///
/// ⚠️ Ele pinta a área **inteira** do `DrawScope` (`drawRect` sem tamanho). O
/// recorte é de quem chama — e no celular quem chama deixa `ALTURA_DA_LUZ` de
/// folga justamente pra o cone terminar sozinho em vez de ser cortado numa
/// aresta reta. Ver `ALTURA_DA_LUZ` no `Facho.kt` do `:app`.
fun DrawScope.desenhaOCone(
    centro: Offset,
    raio: Float,
    forca: Float,
    cor: Color = Cores.destaque,
) {
    drawRect(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.00f to cor.copy(alpha = 0.42f * forca),
                0.18f to cor.copy(alpha = 0.30f * forca),
                0.34f to cor.copy(alpha = 0.19f * forca),
                0.50f to cor.copy(alpha = 0.11f * forca),
                0.66f to cor.copy(alpha = 0.055f * forca),
                0.82f to cor.copy(alpha = 0.02f * forca),
                1.00f to Color.Transparent,
            ),
            center = centro,
            radius = raio,
        ),
    )
}

/// A poeira suspensa. O alfa cai com a distância ao eixo **e** com a altura —
/// pó no ar só brilha onde a luz passa. É o que faz a luz ter **ar** dentro.
///
/// Ela não é elemento: são círculos desenhados no mesmo `Canvas`. Nenhum nó
/// entra na árvore.
///
/// ⚠️ **Cada grão sai do lugar, e o screenshot é que exigiu.** A primeira versão
/// punha os pontos numa grade de 19×13 exatos, e o resultado lia como **trama**,
/// não como pó: o olho acha a repetição antes de achar a luz.
///
/// O deslocamento vem de uma função de espalhamento sobre as coordenadas —
/// determinística, sem `Random`. Isso importa por dois motivos: sorteio por
/// quadro faria a poeira **cintilar** (que é chuvisco de TV, não poeira em
/// suspensão), e sorteio por composição mudaria o desenho a cada recomposição,
/// tirando o screenshot de comparação.
///
/// ⚠️ [alcance] é a distância horizontal em que o grão zera. No celular é a
/// largura de uma aba, e é o que faz a poeira pertencer ao destino escolhido em
/// vez de cobrir a barra toda.
fun DrawScope.desenhaAPoeira(
    eixo: Float,
    raio: Float,
    forca: Float,
    alcance: Float,
    cor: Color = Cores.destaqueQuente,
    /// ⚠️ **De onde a luz vem.** Na barra do celular ela nasce embaixo e sobe;
    /// na cabine da TV ela nasce à esquerda e abre pra direita (§4.2).
    ///
    /// A T0 deixou este parâmetro **de fora** de propósito, com o argumento de
    /// que «fazer isso agora seria inventar uma API pra um chamador que ainda
    /// não existe — e a T1 vai saber a forma certa depois de ter desenhado a
    /// cabine, não antes».
    ///
    /// A cabine existe, e a forma certa é esta: os dois eixos trocam de papel e
    /// mais nada. O que era distância horizontal ao eixo vira distância
    /// vertical; o que era altura a partir da base vira avanço a partir da
    /// borda esquerda. Nenhum número mudou.
    ///
    /// O [eixo] segue sendo a coordenada **perpendicular** ao feixe: `x` da
    /// lente quando a luz sobe, `y` da lente quando ela corre pro lado.
    deitado: Boolean = false,
) {
    val passoX = 19.dp.toPx()
    val passoY = 13.dp.toPx()

    /// ## ⚠️ O laço varre **onde o facho chega**, e não a tela inteira
    ///
    /// Ele percorria toda a área de desenho: numa tela de 1920×1080 são ~2050
    /// iterações por quadro, e a esmagadora maioria delas termina em «alfa
    /// pequeno demais, não desenha». Trabalho feito pra ser jogado fora, sessenta
    /// vezes por segundo, enquanto a lâmpada pisca.
    ///
    /// No celular isso nunca doeu porque a caixa tem 143dp de altura. Na TV a
    /// caixa é a sala — e foi o dono quem sentiu: «abrir e fechar o menu,
    /// movimentar ele tá meio travado».
    ///
    /// ⚠️ A janela é derivada do **próprio raio**, não de um número novo: fora
    /// dele `altura` já daria zero. Recortar aqui não muda um pixel do que se vê;
    /// muda quantas contas se faz pra não desenhar nada.
    val alcanceY = if (deitado) raio else size.height
    val deY = if (deitado) (eixo - alcanceY).coerceAtLeast(0f) else 0f
    val ateY = if (deitado) (eixo + alcanceY).coerceAtMost(size.height) else size.height
    val ateX = if (deitado) minOf(size.width, raio) else size.width

    var linha = (deY / passoY).toInt()
    var y = passoY / 2 + linha * passoY
    while (y < ateY) {
        var coluna = 0
        var x = passoX / 2
        while (x < ateX) {
            /// Espalhamento barato: dois primos, o resto vira fração.
            val semente = (coluna * 73856093) xor (linha * 19349663)
            val rx = ((semente shr 3) and 0xFF) / 255f - 0.5f
            val ry = ((semente shr 11) and 0xFF) / 255f - 0.5f
            val px = x + rx * passoX * 0.85f
            val py = y + ry * passoY * 0.85f

            /// A distância **perpendicular** ao feixe — é ela que faz o grão
            /// pertencer ao destino escolhido em vez de cobrir a barra toda.
            val dist = (if (deitado) abs(py - eixo) else abs(px - eixo)) / alcance
            /// E o **avanço** ao longo do feixe, medido pelo raio e não pela
            /// caixa: com a caixa inteira, os grãos da ponta ficariam com alfa
            /// alto **fora** do cone, e pó brilhando onde não há luz é sujeira
            /// na lente, não poeira em suspensão.
            val altura = if (deitado) {
                (1f - px / raio).coerceIn(0f, 1f)
            } else {
                (1f - (py - (size.height - raio)) / raio).coerceIn(0f, 1f)
            }
            /// O tamanho também varia: grão de pó não tem calibre.
            val calibre = 0.6f + (((semente shr 19) and 0x3F) / 63f) * 0.7f
            val alfa = ((1f - dist) * altura * 0.5f * forca).coerceIn(0f, 1f)
            if (alfa > 0.02f) {
                drawCircle(
                    color = cor,
                    radius = calibre.dp.toPx(),
                    center = Offset(px, py),
                    alpha = alfa,
                )
            }
            x += passoX
            coluna++
        }
        y += passoY
        linha++
    }
}

/// A lente: o ponto quente de onde a luz sai.
///
/// Sem ela o facho é um degradê, não um facho — e é a mesma lente que a §2.1
/// encontrou nos três lugares: aqui na barra, nas lâmpadas da marquise, e
/// correndo sobre a película do player, «fazendo a única coisa que uma lente de
/// projetor faz».
///
/// Deitada, 26x5dp: é a proporção de uma lente vista de frente.
fun DrawScope.desenhaALente(
    centro: Offset,
    forca: Float,
    cor: Color = Cores.destaqueQuente,
    largura: Float = 26.dp.toPx(),
    altura: Float = 5.dp.toPx(),
) {
    drawOval(
        color = cor,
        topLeft = Offset(centro.x - largura / 2, centro.y - altura / 2),
        size = Size(largura, altura),
        alpha = forca.coerceAtMost(1f),
    )
}
