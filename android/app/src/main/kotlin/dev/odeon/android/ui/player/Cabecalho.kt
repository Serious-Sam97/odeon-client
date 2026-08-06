package dev.odeon.android.ui.player

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.odeon.android.ui.Cores
import kotlinx.coroutines.delay

/// O cabeçalho do player, refeito em 06/08/2026.
///
/// ## O que estava errado, e o dono resumiu em três palavras
///
/// > «me dê um redesign da parte de cima do Player, o que temos hj ta mt feio»
///
/// A foto de 06/08/2026 mostra o diagnóstico inteiro: **quatro blocos empilhados
/// à esquerda**, cada um com um tamanho e uma cor, sem uma margem em comum.
///
/// | | o que era |
/// |---|---|
/// | 1 | o título serifado, truncado em «de Sua …» |
/// | 2 | `janelinha` e `voltar`, duas palavras cruas na mesma linha do título |
/// | 3 | a pílula âmbar `transcodificando`, num retângulo cinza |
/// | 4 | noventa caracteres de cinza explicando por que não há Cast |
///
/// Três defeitos de fundo, e nenhum deles é gosto:
///
/// **O `voltar` era a palavra mais apagada da tela.** É a ação mais usada de um
/// player e estava escrita em 13sp, sem alvo próprio, no canto oposto ao polegar
/// de quem segura o aparelho. Virou galo (`‹`) na borda de entrada, com 44dp de
/// alvo — a convenção que ninguém precisa aprender.
///
/// **O dado menos importante era o segundo mais gritante.** `transcodificando`
/// diz respeito a uma decisão que já foi tomada pelo servidor e que ninguém muda
/// no meio do filme. Virou **lâmpada**: âmbar transcodificando, verde direto, e
/// tocar diz a palavra. As cores são as mesmas de antes e as mesmas da ficha —
/// mudou a forma, não a língua. E a forma já existe no app: é a lâmpada da
/// marquise da [CortinaDeAbertura].
///
/// **A frase do Cast era a coisa mais larga da tela.** Uma explicação que ninguém
/// pediu, no texto de menor contraste, permanentemente. Deitado ela caía sobre
/// madeira clara e sumia — está na foto. Agora o ícone de cast nasce **riscado**,
/// e a frase é a **resposta ao toque**.
///
/// ⚠️ **E isso não afrouxa o §53 nem o §8b**, que era a pergunta certa a fazer:
/// o §53 manda não oferecer o que a validação vai negar, e um ícone riscado não
/// oferece — ele nega de cara. O §8b manda não negar calado, e o toque responde
/// com a frase inteira, incluindo **onde** se resolve. O que saiu foi só a
/// permanência.
///
/// ## O véu, que não existia
///
/// O cromo de baixo tem o véu uniforme de 28% do [Controles]; o de cima não tinha
/// nada. Funcionava por acidente: em pé um filme 2.35:1 deixa tarja preta no alto,
/// e o texto caía nela. Deitado, não — a foto de 06/08/2026 tem a frase do Cast
/// sobre um teto de madeira clara, ilegível.
///
/// Aqui entra um degradê **de cima pra baixo**, e não um véu uniforme: no alto
/// ele é o cabeçalho, embaixo ele acaba. Degradê tem borda, e borda dentro do
/// quadro foi o defeito que a barra do facho levou três rodadas pra perder — mas
/// esta borda é a de um degradê que termina em transparente, que é a única que
/// não se vê.
///
/// ## Os ícones são desenhados, e é a mesma conta do [BotaoDeTocar]
///
/// O app não tem jogo de ícones: os cinco das abas são vetores escritos à mão, e
/// o §15 chama isso de «zero bytes». Trazer uma biblioteca por três formas — um
/// galo, dois retângulos e dois arcos — inverteria a conta que este projeto faz
/// em todo lugar.
///
/// ⚠️ **A legenda saiu daqui em 06/08/2026**, a pedido do dono, e foi pro rodapé
/// junto com o áudio — ver [BotaoDeLegenda]. O que sobrou no alto é navegação
/// (voltar, janelinha) e estado (a lâmpada, o cast). Escolher língua é outra
/// família de decisão, e ela mora ao lado do transporte.
@Composable
internal fun CabecalhoDoPlayer(
    titulo: String,
    /// A palavra do plano: `direto`, `remux`, `transcodificando`. `null` enquanto
    /// o plano não chegou — e aí a lâmpada não nasce, em vez de nascer apagada
    /// fingindo que já se sabe.
    plano: String?,
    planoEDireto: Boolean,
    /// De qual aparelho o plano fala. Durante um cast é a TV, e não este celular
    /// — a §4c manda dizer, senão a tela afirma sobre um aparelho o que é de
    /// outro (§18 por outro caminho).
    aparelhoDoPlano: String?,
    /// Por que não dá pra mandar pra TV, quando não dá.
    ///
    /// ## ⚠️ E é ele, e só ele, que faz o ícone de cast existir
    ///
    /// A tentação era desenhar o cast sempre: riscado quando impedido, aceso
    /// quando não. Mas **não há o que o aceso faria**. O `EstadoDoCast` apenas
    /// *observa* uma sessão iniciada por fora — quem escolhe o aparelho é o
    /// seletor do sistema, e ele não está montado nesta tela. Um ícone aceso
    /// seria um clique que não faz nada, que é o §8b em pessoa.
    ///
    /// Então o ícone nasce **só quando há impedimento**, riscado, e o toque
    /// devolve a frase. É o mesmo que a tela já dizia — deixou de dizer o tempo
    /// todo.
    ///
    /// Quando o Cast de verdade entrar (fase 4), o botão que **age** vem junto
    /// com o seletor, e aí as duas metades existem.
    impedimentoDoCast: String?,
    /// O que deu errado ao tentar a janelinha, quando deu.
    falhaDaJanelinha: String?,
    aoVoltar: () -> Unit,
    aoEntrarNaJanelinha: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /// A linha de resposta: uma frase por vez, e ela some sozinha.
    ///
    /// É o que substitui os dois blocos permanentes. Quatro segundos porque a
    /// frase do Cast tem noventa caracteres — três, que é o tempo do cromo, não
    /// dá pra terminar de ler.
    var recado by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(recado) {
        if (recado != null) {
            delay(4_000)
            recado = null
        }
    }

    /// A falha da janelinha entra na mesma linha, e **empurra** o recado: ela é
    /// resposta a um toque que acabou de acontecer, e o §8b diz que ela não pode
    /// esperar a vez.
    val linha = falhaDaJanelinha?.let { "janelinha: $it" } ?: recado

    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    listOf(Color.Black.copy(alpha = 0.62f), Color.Transparent),
                ),
            )
            .padding(start = 4.dp, end = 4.dp, top = 2.dp, bottom = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            AlvoDeToque(rotulo = "voltar", aoTocar = aoVoltar) { desenharGalo() }

            plano?.let { palavra ->
                Lampada(
                    direto = planoEDireto,
                    aoTocar = {
                        recado = aparelhoDoPlano?.let { "$palavra — na $it" } ?: palavra
                    },
                )
            }

            Text(
                text = titulo,
                style = MaterialTheme.typography.headlineSmall.copy(fontSize = 16.sp),
                color = Cores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 6.dp, end = 4.dp),
            )

            AlvoDeToque(rotulo = "janelinha", aoTocar = aoEntrarNaJanelinha) { desenharJanelinha() }
            impedimentoDoCast?.let { frase ->
                AlvoDeToque(
                    rotulo = "por que não dá pra mandar pra TV",
                    aoTocar = { recado = frase },
                ) { desenharCast(riscado = true) }
            }
        }

        linha?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = if (falhaDaJanelinha != null) Cores.perigo else Cores.textoApagado,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 2.dp),
            )
        }
    }
}

/// A lâmpada do plano.
///
/// Sete dp de vidro com um halo em volta — o derrame é o que separa uma lâmpada
/// de um ponto colorido, e é a mesma conta dos bulbos da marquise.
@Composable
private fun Lampada(direto: Boolean, aoTocar: () -> Unit) {
    val cor = if (direto) Cores.certo else Cores.destaque
    AlvoDeToque(rotulo = "plano de reprodução", aoTocar = aoTocar, tamanho = 32.dp) {
        val centro = Offset(size.width / 2f, size.height / 2f)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cor.copy(alpha = 0.55f), Color.Transparent),
                center = centro,
                radius = size.minDimension / 2f,
            ),
            radius = size.minDimension / 2f,
            center = centro,
        )
        drawCircle(color = cor, radius = 3.5.dp.toPx(), center = centro)
    }
}

/// O alvo de toque, e ele é o mesmo pros quatro ícones.
///
/// 44dp é o mínimo que a mão alcança sem mirar, e o ícone dentro tem 22 — a folga
/// existe pro dedo, não pro desenho. `indication = null`: o realce retangular
/// padrão do Material dentro de um filme é um retângulo cinza piscando sobre a
/// imagem, e a resposta ao toque aqui é o que o botão **faz**.
@Composable
internal fun AlvoDeToque(
    rotulo: String,
    aoTocar: () -> Unit,
    tamanho: androidx.compose.ui.unit.Dp = 44.dp,
    desenho: DrawScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .size(tamanho)
            .clip(CircleShape)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClickLabel = rotulo,
                onClick = aoTocar,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(if (tamanho > 40.dp) 22.dp else tamanho), onDraw = desenho)
    }
}

/// O galo de voltar: duas retas e nada mais.
///
/// ⚠️ Ponta **arredondada**, e é o que o diferencia de uma seta de navegador: o
/// resto do cromo é curvo (o disco de play, os arcos dos saltos, a lente da
/// tira), e um `<` de canto vivo aqui destoaria de tudo.
private fun DrawScope.desenharGalo() {
    val traco = 2.dp.toPx()
    val caminho = Path().apply {
        moveTo(size.width * 0.62f, size.height * 0.22f)
        lineTo(size.width * 0.34f, size.height * 0.5f)
        lineTo(size.width * 0.62f, size.height * 0.78f)
    }
    drawPath(caminho, Cores.texto, style = Stroke(width = traco, cap = StrokeCap.Round))
}

/// A janelinha: a tela grande em contorno, a pequena preenchida no canto.
///
/// O canto é o **inferior direito** porque é onde o Android põe a janela de PiP
/// por padrão — o ícone diz pra onde o filme vai, e não só que ele vai encolher.
private fun DrawScope.desenharJanelinha() {
    val traco = 1.8.dp.toPx()
    drawRoundRect(
        color = Cores.texto,
        topLeft = Offset(traco / 2, size.height * 0.18f),
        size = Size(size.width - traco, size.height * 0.64f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        style = Stroke(width = traco),
    )
    drawRoundRect(
        color = Cores.texto,
        topLeft = Offset(size.width * 0.46f, size.height * 0.44f),
        size = Size(size.width * 0.44f, size.height * 0.3f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(1.dp.toPx()),
    )
}

/// O cast: a tela, e as ondas saindo do canto de baixo.
///
/// Riscado quando não dá — e a barra é desenhada **por cima de tudo**, na
/// diagonal, que é como qualquer pessoa lê «isto está desligado».
private fun DrawScope.desenharCast(riscado: Boolean) {
    val cor = if (riscado) Cores.textoApagado else Cores.texto
    val traco = 1.8.dp.toPx()

    /// ⚠️ **A tela fica no alto e à direita, e as ondas embaixo e à esquerda.**
    ///
    /// A primeira versão pôs o retângulo ocupando 80% da largura a partir de
    /// 0,18 — e as ondas, com raio de até 0,52, entravam **por dentro** dele. A
    /// foto ampliada mostrou o resultado: um rabisco. As duas metades do glifo
    /// precisam de território separado, e é o que estes números garantem: a tela
    /// começa em 0,34 da largura e para em 0,62 da altura; a maior onda alcança
    /// 0,52 da largura a partir do canto, e não chega lá.
    val canto = Offset(size.width * 0.08f, size.height * 0.90f)

    fun tela(cor: Color, largura: Float) = drawRoundRect(
        color = cor,
        topLeft = Offset(size.width * 0.34f, size.height * 0.10f),
        size = Size(size.width * 0.62f, size.height * 0.52f),
        cornerRadius = androidx.compose.ui.geometry.CornerRadius(2.dp.toPx()),
        style = Stroke(width = largura),
    )

    fun ondas(cor: Color, largura: Float) {
        /// ⚠️ **Sem a bolinha do canto**, que todo glifo de cast tem e que aqui
        /// não sobrevive: com 1,6dp de raio, ela fica exatamente onde a barra do
        /// riscado nasce, e a foto ampliada mostrou o que resta dela — nada.
        /// Um ponto que some não é um ponto discreto, é um ponto ausente.
        ///
        /// Duas ondas e não três: em 22dp, três arcos concêntricos ficam a menos
        /// de 2dp um do outro e viram uma mancha. Duas leem como sinal.
        listOf(0.30f, 0.52f).forEach { raio ->
            drawArc(
                color = cor,
                startAngle = -90f,
                sweepAngle = 90f,
                useCenter = false,
                topLeft = Offset(canto.x - size.width * raio, canto.y - size.width * raio),
                size = Size(size.width * raio * 2, size.width * raio * 2),
                style = Stroke(width = largura, cap = StrokeCap.Round),
            )
        }
    }

    if (riscado) {
        /// ## O vão por baixo da barra
        ///
        /// Riscar por cima de um glifo com arcos, no mesmo tom e na mesma
        /// espessura, dá o que a foto ampliada mostrou: nada se distingue de
        /// nada. O conserto é o que todo jogo de ícones usa pro estado "off" —
        /// **desenhar o glifo, abrir um sulco escuro na diagonal, e só então
        /// passar a barra dentro do sulco**. A barra deixa de disputar com as
        /// linhas e passa a cortá-las.
        ///
        /// O sulco é o preto do cinema (`Cores.fundo`), e não transparente:
        /// transparente deixaria o filme aparecer no meio do ícone.
        ondas(cor, traco)
        tela(cor, traco)
        /// ⚠️ **O vão é 2,2× a barra, e não 3,2×.**
        ///
        /// A primeira medida abriu um sulco de quase 6dp num ícone de 22 — a
        /// foto ampliada mostrou a barra dominando o glifo inteiro e as ondas
        /// reduzidas a três tiquinhos. O vão precisa separar, não apagar: 2,2×
        /// deixa meio ponto de folga de cada lado, que é o bastante pra a barra
        /// não encostar nas linhas que ela corta.
        ///
        /// E ela nasce e morre **dentro** do ícone (0,10 a 0,90), não nas
        /// quinas: uma diagonal que toca os cantos vira moldura, e o que se quer
        /// é um corte.
        val de = Offset(size.width * 0.10f, size.height * 0.90f)
        val ate = Offset(size.width * 0.90f, size.height * 0.10f)
        drawLine(Cores.fundo, de, ate, strokeWidth = traco * 2.2f, cap = StrokeCap.Round)
        drawLine(cor, de, ate, strokeWidth = traco, cap = StrokeCap.Round)
    } else {
        ondas(cor, traco)
        tela(cor, traco)
    }
}
