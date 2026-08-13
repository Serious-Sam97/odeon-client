package dev.odeon.android.ui

import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit

/// O `Text` deste módulo, e ele existe por causa de uma dependência que não pode
/// entrar aqui.
///
/// ## O problema
///
/// A régua do `:cenario` é `foundation` + `ui` + `animation-core`, e o motivo
/// está no `build.gradle.kts`: `androidx.tv.material3` e
/// `androidx.compose.material3` exportam `Text` com o mesmo nome, e um módulo
/// compartilhado que escolhesse um dos dois só compilaria de um lado.
///
/// Só que as peças daqui **escrevem**: a advertência da contracapa, o «DVD» do
/// selo, o título na lombada, o nome do filme na cortina. Vinte e uma chamadas,
/// medidas na §3 do `docs/REDESENHO-TV.md`.
///
/// ## Por que um invólucro, e não vinte e uma reescritas
///
/// O `BasicText` do `foundation` é exatamente o que o `Text` do Material chama
/// por baixo depois de resolver cor e estilo do tema — mas a assinatura dele é
/// outra: ele não aceita `color`, `textAlign` nem `fontSize` soltos, só o
/// `style` inteiro.
///
/// Trocar `Text` por `BasicText` nas vinte e uma chamadas obrigaria a dobrar
/// cada `color =` e cada `textAlign =` pra dentro de um `TextStyle` à mão. Isso
/// é reescrever vinte e uma peças de desenho pra pagar uma mudança de módulo —
/// e o §0.1 do redesenho é explícito: «Nenhuma delas é pra reescrever.»
///
/// Com este arquivo, a mudança em cada chamada foi **uma letra**: `Text` virou
/// `Texto`. O diff mostra a mudança de módulo, e não uma refatoração de texto
/// escondida dentro dela.
///
/// ## ⚠️ Ele reproduz o `Text` do Material, e eu errei o modelo dele três vezes
///
/// O `Text` faz duas coisas que o `BasicText` não faz, e **elas não são a mesma
/// coisa** — foi confundir as duas que custou o dia:
///
/// | | |
/// |---|---|
/// | o `LocalTextStyle` do tema | é o **valor padrão** do parâmetro `style`. Quem passa `style = X` não o vê: X vale inteiro, com os campos em branco em branco mesmo |
/// | os campos **soltos** (`fontSize`, `fontWeight`, `lineHeight`, `letterSpacing`, `textAlign`) | são fundidos **por cima** do `style`, com `merge` |
///
/// As três tentativas erradas, e o que cada uma mostrou na tela:
///
/// | # | o que eu supus | o aparelho |
/// |---|---|---|
/// | 1 | base `TextStyle.Default`, sem campos soltos | a lombada da caixa saiu diferente numa faixa de 24px por toda a altura do objeto |
/// | 2 | «o `Text` funde a chamada por cima do local» → reconstruí o `bodyLarge` à mão | o selo do nível fechou em 0 e **a caixa do palco subiu 13px** (411.010 px). Duas telas discordando é a assinatura de um modelo errado |
/// | 3 | local emprestado pelo hospedeiro, fundido por baixo | palco 0, selo ainda fora — porque as chamadas da `Insignia` tinham sido reescritas pra passar `style` e deixaram de herdar |
///
/// ⚠️ E houve um quarto erro, que não era de modelo e sim de execução: os quatro
/// campos soltos foram **declarados na assinatura e esquecidos no corpo**. O
/// `Bold` do algarismo do selo simplesmente não chegava, e a foto mostrou um «3»
/// mais fino que o original. Um `assert` numa substituição de texto teria pego;
/// a régua da casa pegou primeiro.
///
/// ## O que vale agora, medido
///
/// | tela | pixels diferentes do original |
/// |---|---|
/// | palco — a caixa, a lombada, a dica | **0** |
/// | perfil — a insígnia, o selo, o placar | **0** |
/// | locadora | 3.448, dos quais **3.414 são o selo mostrando `3` em vez de `2`** — o nível subiu durante a sessão. Os 34 restantes são grão solto |
///
/// ## Por que o local, e não uma reconstituição
///
/// Quem **tem** o tema é o hospedeiro: o `:app` tem o Material de celular, o
/// `:tv` o de TV. Cada um empresta o seu `LocalTextStyle` e este módulo o usa
/// como padrão, sem saber o que tem dentro — que é o ponto.
///
/// Adivinhar o que o `Typography` do Material põe em cada campo é arqueologia de
/// biblioteca de terceiro, e o resultado dela é um número que parece medido e
/// não é. A tentativa 2 acima é exatamente isso acontecendo.
///
/// ⚠️ Quem não fornecer nada cai em `TextStyle.Default`. É o padrão certo pra um
/// módulo que não pode depender de tema: erra visivelmente, em vez de errar por
/// 13 pixels.
///
/// O `:app` fornece no `TemaOdeon`; o `:tv` no `TemaDaSala`.

val LocalLetraDoHospedeiro = compositionLocalOf { TextStyle.Default }


@Composable
fun Texto(
    text: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    textAlign: TextAlign? = null,
    maxLines: Int = Int.MAX_VALUE,
    minLines: Int = 1,
    overflow: TextOverflow = TextOverflow.Clip,
    softWrap: Boolean = true,
    /// ⚠️ **Os quatro campos soltos existem pela mesma razão que o `Text` do
    /// Material os tem**, e tirá-los custou 356 pixels.
    ///
    /// A primeira versão deste invólucro não os tinha, e as duas chamadas da
    /// `Insignia` — as únicas que passavam corpo e peso **sem** passar `style` —
    /// tiveram de ser reescritas pra dobrar tudo num `TextStyle`. Com isso elas
    /// deixaram de herdar do tema, e o algarismo do selo do nível saiu 1px fora
    /// do lugar.
    ///
    /// Com eles, a fusão acontece aqui — `style.merge(os soltos)`, na mesma
    /// ordem do Material — e as duas chamadas voltaram a ser o que eram, com
    /// `Text` trocado por `Texto` e mais nada.
    fontSize: TextUnit = TextUnit.Unspecified,
    fontWeight: FontWeight? = null,
    lineHeight: TextUnit = TextUnit.Unspecified,
    letterSpacing: TextUnit = TextUnit.Unspecified,
    style: TextStyle = LocalLetraDoHospedeiro.current,
) {
    BasicText(
        text = text,
        modifier = modifier,
        /// ⚠️ **Sem `merge`, e essa é a correção que fechou em zero.**
        ///
        /// Eu tinha modelado o `Text` do Material errado duas vezes seguidas,
        /// sempre supondo que ele funde o estilo da chamada **por cima** do
        /// `LocalTextStyle`. Ele não funde: o `LocalTextStyle` é o **valor
        /// padrão** do parâmetro `style`. Quem passa `style = X` explicitamente
        /// não vê o local nenhum — X vale inteiro, com os campos em branco em
        /// branco mesmo.
        ///
        /// A diferença aparece justamente nas peças daqui, que quase sempre
        /// passam `style` explícito: fundir dava a elas uma entrelinha de 24sp
        /// que o `Text` nunca tinha dado, e a caixa do palco subia 13px.
        ///
        /// Agora o local é o padrão do parâmetro, como no Material. As duas
        /// chamadas da `Insignia`, que não passam `style`, herdam; todas as
        /// outras, que passam, não.
        /// A mesma fusão que o `Text` do Material faz, e na mesma ordem: os
        /// campos soltos da chamada por cima do [style] — que, quando ninguém
        /// passa um, é o do hospedeiro.
        ///
        /// `merge` ignora campo não especificado, e é isso que deixa os quatro
        /// soltos terem padrão «em branco» sem apagar nada de quem passou um
        /// `style` inteiro.
        style = style.merge(
            TextStyle(
                color = color,
                fontSize = fontSize,
                fontWeight = fontWeight,
                lineHeight = lineHeight,
                letterSpacing = letterSpacing,
                textAlign = textAlign ?: TextAlign.Unspecified,
            ),
        ),
        overflow = overflow,
        softWrap = softWrap,
        maxLines = maxLines,
        minLines = minLines,
    )
}
