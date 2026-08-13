package dev.odeon.android.ui.locadora

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.odeon.android.ui.Texto
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.odeon.android.dados.Prateleira
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Serifada
import androidx.compose.ui.layout.layout

/// A cenografia da locadora — «A loja da esquina, 21h».
///
/// ## De onde este arquivo vem
///
/// O dono pediu «o maior feel possível de locadora, aquela nostalgia», olhou
/// vinte conceitos e escolheu um: **as prateleiras ficam** («com o celular não
/// tem como fugir das prateleiras, que eu até acho ok»), e o resto da tela vira
/// matéria — madeira, papel, luz. O mock aprovado tem três peças além da
/// estante, e são as três que moram aqui:
///
/// | | |
/// |---|---|
/// | a **arandela** | a luz quente que acende o título no topo |
/// | as **etiquetas penduradas** | as duas contagens da porta, em papel por barbante |
/// | a **nota do caixa** | o resumo impresso no fim da rolagem, com serrilha e carimbo |
///
/// A quarta peça — a plaquinha de papel da estante — vive na `TelaDaLocadora`,
/// colada na madeira que ela nomeia.
///
/// ## A régua de sempre
///
/// Papel é `Cores.papel` e tinta é tinta de papel: as mesmas do bilhete da
/// ficha e do rótulo da fita. A cenografia inteira usa material que o app já
/// tem — é o que faz a locadora parecer do mesmo prédio que o cinema.

/// A arandela: a meia-cúpula de latão e o facho que ela joga na parede.
///
/// É a luz da casa (`luzNoLado` supõe uma lâmpada de cima; o palco ganhou o
/// facho quente em 06/08) chegando ao topo da loja. O título embaixo dela não
/// tem luz própria — quem brilha é a lâmpada, e o texto está **na** luz.
@Composable
fun Arandela(modifier: Modifier = Modifier) {
    Canvas(modifier.fillMaxWidth().height(46.dp)) {
        val cx = size.width / 2f
        /// O facho, primeiro — a cúpula é desenhada por cima da nascente dele.
        ///
        /// ⚠️ **Em círculo, e não em retângulo.** A primeira versão pintava um
        /// `drawRect` com o pincel radial, e a foto mostrou uma **tarja**: o
        /// gradiente morria fora do canvas e o recorte virava duas arestas retas
        /// atravessando o topo. O círculo é do tamanho da própria luz, e o que
        /// não é luz não é pintado.
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(
                    Cores.destaqueQuente.copy(alpha = 0.28f),
                    Cores.destaqueQuente.copy(alpha = 0.07f),
                    Color.Transparent,
                ),
                center = Offset(cx, 30.dp.toPx()),
                radius = size.width * 0.38f,
            ),
            radius = size.width * 0.38f,
            center = Offset(cx, 30.dp.toPx()),
        )
        /// A meia-cúpula: um arco de latão com a boca pra baixo.
        drawArc(
            brush = Brush.verticalGradient(
                listOf(Color(0xFF8A6A3A), Color(0xFF5A4326)),
            ),
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = true,
            topLeft = Offset(cx - 56.dp.toPx(), 4.dp.toPx()),
            size = Size(112.dp.toPx(), 52.dp.toPx()),
        )
        /// O fio de luz na boca da cúpula — é ele que diz «acesa».
        drawLine(
            color = Cores.destaqueQuente.copy(alpha = 0.85f),
            start = Offset(cx - 52.dp.toPx(), 30.dp.toPx()),
            end = Offset(cx + 52.dp.toPx(), 30.dp.toPx()),
            strokeWidth = 2.5.dp.toPx(),
        )
    }
}

/// Uma etiqueta de papel pendurada por barbante — as contagens da porta.
///
/// O barbante e o nó são desenhados; o papel é um retângulo levemente torto.
/// ⚠️ O torto é **fixo por etiqueta** e vem de fora: ângulo sorteado mudaria a
/// cada recomposição e a etiqueta tremeria pendurada — a mesma regra do varal.
@Composable
fun EtiquetaPendurada(
    numero: String,
    rotulo: String,
    angulo: Float,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        /// O barbante: um traço fino descendo, com o nó na ponta.
        Canvas(Modifier.width(20.dp).height(16.dp)) {
            drawLine(
                color = Color(0xFFCDB98A).copy(alpha = 0.7f),
                start = Offset(size.width / 2f, 0f),
                end = Offset(size.width / 2f, size.height),
                strokeWidth = 1.5.dp.toPx(),
            )
        }
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
            modifier = Modifier
                .graphicsLayer { rotationZ = angulo }
                .background(Cores.papel, RoundedCornerShape(2.dp))
                .drawBehind {
                    /// O ilhós do barbante: o furinho de metal no topo do papel.
                    drawCircle(
                        color = Color(0xFF3A2C18),
                        radius = 3.dp.toPx(),
                        center = Offset(size.width / 2f, 0f),
                    )
                    drawCircle(
                        color = Cores.papel,
                        radius = 3.dp.toPx(),
                        center = Offset(size.width / 2f, 0f),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(1.5.dp.toPx()),
                    )
                }
                .padding(horizontal = 12.dp, vertical = 7.dp),
        ) {
            Texto(
                text = numero,
                style = TextStyle(
                    fontFamily = Serifada,
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cores.tintaDoBilhete,
                ),
            )
            Texto(
                text = rotulo,
                style = TextStyle(
                    fontFamily = FontFamily.Cursive,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Cores.tintaDoPapel,
                ),
                modifier = Modifier.padding(bottom = 1.dp),
            )
        }
    }
}

/// A nota do caixa — o resumo da loja, impresso, no fim da rolagem.
///
/// ## Por que ela mora no fim
///
/// O resumo de quem-está-com-o-quê ocupava o topo, **antes** de a pessoa ver a
/// loja. No desenho aprovado ele vira o fechamento: você anda pelas estantes e,
/// na saída, o caixa te entrega a notinha — acervo, as pessoas, seu limite, o
/// prazo da casa. É o mesmo dado de antes com a ordem de uma visita de verdade.
///
/// ## O que ela imprime, e tudo é dado do servidor
///
/// | linha | de onde vem |
/// |---|---|
/// | `NO ACERVO` | `loja.no_acervo` — some se o servidor não mandou (§24) |
/// | as pessoas | `prateleira.pessoas`, com as **três contagens** dos chips que ela substitui: fora, `✕` zoadas, `⟲` rebobinadas — «um placar que só conta o defeito faz de todo mundo réu» continua valendo no papel |
/// | `VOCÊ PODE PEGAR` | `posso_pegar` — e no limite a frase vem com a saída junto (§8b) |
/// | `PRAZO DA CASA` | `opcoes.prazo_dias` — a regra que saiu da tela em 05/08 volta a ser dita, agora onde regra se imprime: no rodapé da nota |
///
/// O carimbo `VOLTE SEMPRE` é o único enfeite — e é enfeite honesto: não se
/// parece com dado nenhum (§18), como o código de barras do verso.
@Composable
fun NotaDoCaixa(
    prateleira: Prateleira,
    noAcervo: Int,
    modifier: Modifier = Modifier,
) {
    val mono = TextStyle(
        fontFamily = FontFamily.Monospace,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
        color = Cores.tintaDoPapel,
    )

    Column(
        modifier = modifier
            .width(300.dp)
            .drawBehind {
                /// O papel, com a **serrilha** embaixo: os dentes são recortes
                /// do fundo, como os furos do bilhete — pintados por cima, eles
                /// ficariam da cor errada no dia em que o fundo mudar.
                drawRect(Cores.papel, size = Size(size.width, size.height - 5.dp.toPx()))
                val dente = 10.dp.toPx()
                var x = 0f
                while (x < size.width) {
                    drawCircle(
                        color = Cores.papel,
                        radius = dente / 2f,
                        center = Offset(x + dente / 2f, size.height - 5.dp.toPx()),
                    )
                    x += dente * 1.6f
                }
                /// ⚠️ O clipe de metal foi desenhado e **saiu**: um retângulo
                /// arredondado mordendo o topo lia como um O caído, não como
                /// clipe — a foto reprovou. Papel preso por nada é como recibo
                /// chega na mão mesmo.
            }
            .padding(horizontal = 18.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Texto(
            text = "LOCADORA ODEON",
            style = mono.copy(fontSize = 16.sp, letterSpacing = 0.14.em),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )
        Texto(
            text = "— acervo da casa —",
            style = mono.copy(fontSize = 8.5.sp, fontWeight = FontWeight.Normal),
            color = Cores.tintaDoPapel.copy(alpha = 0.7f),
            modifier = Modifier.align(Alignment.CenterHorizontally),
        )

        Tracejado()

        if (noAcervo > 0) {
            LinhaDaNota("NO ACERVO", "$noAcervo", mono)
        }
        prateleira.opcoes.prazoEmDias.takeIf { it > 0 }?.let {
            LinhaDaNota("PRAZO DA CASA", "$it DIAS", mono)
        }

        /// As pessoas — só quem tem fita ou fama, a mesma régua dos chips que
        /// esta nota substitui (§24: quem não tem nada não é notícia).
        val gente = prateleira.pessoas.filter { it.temOQueDizer }
        if (gente.isNotEmpty()) {
            Tracejado()
            gente.forEach { pessoa ->
                LinhaDaNota(
                    esquerda = pessoa.nome,
                    direita = listOfNotNull(
                        pessoa.naMao.takeIf { it > 0 }?.let { "$it fora" },
                        pessoa.noMeio.takeIf { it > 0 }?.let { "$it no meio" },
                        pessoa.zoadas.takeIf { it > 0 }?.let { "✕$it" },
                        pessoa.rebobinou.takeIf { it > 0 }?.let { "⟲$it" },
                    ).joinToString(" · "),
                    estilo = mono,
                )
            }
        }

        Tracejado()

        if (prateleira.possoPegar > 0) {
            LinhaDaNota("VOCÊ PODE PEGAR", "+${prateleira.possoPegar}", mono)
        } else {
            LinhaDaNota("VOCÊ ESTÁ NO LIMITE", "", mono)
            Texto(
                text = "devolva uma pra pegar outra",
                style = mono.copy(fontSize = 9.sp, fontWeight = FontWeight.Normal),
                color = Cores.tintaDoPapel.copy(alpha = 0.8f),
            )
        }

        Spacer(Modifier.height(6.dp))

        /// O carimbo, torto como todo carimbo.
        Box(
            modifier = Modifier
                .align(Alignment.End)
                .padding(end = 4.dp, bottom = 6.dp)
                .graphicsLayer { rotationZ = -8f }
                .border(
                    2.5.dp,
                    Color(0xFFB22C2C).copy(alpha = 0.75f),
                    RoundedCornerShape(5.dp),
                )
                .padding(horizontal = 10.dp, vertical = 4.dp),
        ) {
            Texto(
                text = "VOLTE SEMPRE",
                style = mono.copy(fontSize = 11.sp, letterSpacing = 0.08.em),
                color = Color(0xFFB22C2C).copy(alpha = 0.8f),
            )
        }
    }
}

/// Uma linha da nota: rótulo à esquerda, valor à direita, como recibo imprime.
@Composable
private fun LinhaDaNota(esquerda: String, direita: String, estilo: TextStyle) {
    Row(Modifier.fillMaxWidth()) {
        Texto(text = esquerda, style = estilo, modifier = Modifier.weight(1f))
        Texto(text = direita, style = estilo)
    }
}

/// O separador tracejado do recibo.
@Composable
private fun Tracejado() {
    Canvas(Modifier.fillMaxWidth().height(2.dp).padding(vertical = 0.5.dp)) {
        drawLine(
            color = Color(0xFFB8AC8A),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5.dp.toPx(),
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6.dp.toPx(), 4.dp.toPx())),
        )
    }
}

/// A plaquinha de papel da estante — o gênero escrito à mão, preso com fita.
///
/// A cor do papel cicla por estante numa paleta fixa de papelaria: sorteá-la
/// mudaria a cor a cada recomposição, e amarrá-la ao gênero criaria um código
/// de cores que ninguém combinou (§18 — cor decorativa não pode parecer dado).
@Composable
fun PlaquinhaDaEstante(nome: String, indice: Int, modifier: Modifier = Modifier) {
    val papeis = listOf(
        Color(0xFFF2DD7C),
        Color(0xFFA9C8E8),
        Color(0xFFE8B4C0),
        Color(0xFFBCDF96),
        Color(0xFFE8C89A),
    )
    Box(modifier = modifier.graphicsLayer { rotationZ = -2f }) {
        Texto(
            text = nome,
            style = TextStyle(
                fontFamily = FontFamily.Cursive,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF3A2F1A),
            ),
            modifier = Modifier
                .background(papeis[indice % papeis.size], RoundedCornerShape(1.dp))
                .drawBehind {
                    /// A fita crepe, translúcida, prendendo o papel na madeira.
                    drawRect(
                        color = Color(0xFFEBE4CD).copy(alpha = 0.55f),
                        topLeft = Offset(size.width * 0.38f, -5.dp.toPx()),
                        size = Size(size.width * 0.24f, 10.dp.toPx()),
                    )
                }
                .padding(horizontal = 14.dp, vertical = 5.dp),
        )
    }
}

/// A etiqueta de preço na tábua — o `7 DIAS` colorido das locadoras.
///
/// O número é o **prazo real da casa** (`opcoes.prazo_dias`); a cor cicla por
/// estante, tinta e não dado. Sem prazo do servidor, sem etiqueta (§24).
@Composable
fun EtiquetaDePrazo(prazoEmDias: Int, indice: Int, modifier: Modifier = Modifier) {
    val tintas = listOf(
        Color(0xFFEEC84A),
        Color(0xFF5AA6D8),
        Color(0xFFE0798F),
        Color(0xFF8FC47A),
        Color(0xFFE8A05A),
    )
    Texto(
        text = "$prazoEmDias DIAS",
        /// Era `Tipo.rotulo.copy(...)`, e do rótulo ele só herdava o peso —
        /// que já vinha sobrescrito aqui. O `Tipo` ficou no `:app` (§3.3).
        style = TextStyle(
            fontWeight = FontWeight.Bold,
            fontSize = 8.5.sp,
            letterSpacing = 0.08.em,
        ),
        color = Color(0xFF241C08),
        modifier = modifier
            .background(tintas[indice % tintas.size])
            .padding(horizontal = 5.dp, vertical = 1.dp),
    )
}

/// A madeira da prateleira.
@Composable
/// ⚠️ Ela era `private` na tela da locadora do celular e virou pública no
/// `:cenario` na T3 — a estante da sala precisa da mesma tábua.
///
/// Uma prateleira de madeira não é do celular nem da TV: é do **lugar**. Foi o
/// mesmo argumento que trouxe a arandela e as plaquinhas pra este arquivo.
fun Tabua() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            /// Sangra 16dp de cada lado pra cancelar o padding da tela. Uma
            /// prateleira que respeita a margem do texto é uma prateleira que
            /// acaba no ar.
            ///
            /// ⚠️ **`padding` negativo não existe em Compose**, e a primeira
            /// versão disto tentou: `padding(horizontal = (-16).dp)`. Compilou,
            /// passou no lint, e o app **caiu ao abrir a locadora** com
            /// `IllegalArgumentException: Padding must be non-negative`. Mais
            /// uma para a lista: compilar e passar no lint não é ter visto.
            ///
            /// O jeito certo é medir com folga e colocar deslocado. O `layout`
            /// pede ao filho uma largura maior que a que recebeu e depois o
            /// posiciona 16dp à esquerda, mantendo a **altura** reservada igual
            /// — assim a coluna acima não se mexe.
            .layout { medivel, restricoes ->
                val folga = 16.dp.roundToPx() * 2
                val largura = restricoes.maxWidth + folga
                val posto = medivel.measure(
                    restricoes.copy(minWidth = largura, maxWidth = largura),
                )
                layout(restricoes.maxWidth, posto.height) {
                    posto.place(-folga / 2, 0)
                }
            }
            .height(6.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2A2119), Color(0xFF150F0A)),
                ),
            ),
    ) {
        /// A luz da loja batendo na tábua: uma linha quente no topo dela.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Cores.destaque.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}
