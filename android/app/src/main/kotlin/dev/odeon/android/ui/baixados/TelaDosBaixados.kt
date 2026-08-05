package dev.odeon.android.ui.baixados

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Baixado
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.duracaoCompacta
import dev.odeon.android.ui.tamanhoCompacto

/// Os baixados.
///
/// ## O que ela era, e o que a foto mostrou — 05/08/2026
///
/// Uma lista de retângulos com título, uma frase de estado e um botão `apagar`.
/// Nenhuma imagem, e a queixa do dono foi direta. Mas o defeito maior não era
/// aparência: **não havia como assistir**. A tela recebia só o modelo, e a única
/// ação de um filme de 2 GB baixado pra ver sem rede era apagá-lo.
///
/// Cinco coisas chegavam no modelo e nenhuma era desenhada:
///
/// | | |
/// |---|---|
/// | `poster` / `backdrop` | numa tela de filmes, nenhuma arte |
/// | `bytes` | uma tela de armazenamento que não dizia quanto ocupava |
/// | `duracaoEmSegundos` | — |
/// | `origem` | e é ela que decide se o arquivo vence |
/// | **tocar** | não existia callback nenhum |
///
/// ## A conta que decidiu o tamanho do cartão
///
/// Área útil do aparelho: 914dp menos a barra de status, a barra do facho (54) e
/// o inset do gesto (25) = **~810dp**. Título 40 + cabeçalho 40 + respiros deixam
/// ~730 pros cartões, e **cinco** cabem sem rolar com a faixa da arte em 100dp.
/// Cinco é o número que importa: quem baixa filme tem meia dúzia, não duzentos.
///
/// ## O que ela mostra além do progresso
///
/// **Se a fita venceu.** É a única tela do app onde isso aparece, porque é a
/// única onde o arquivo existe sem o servidor por perto — e um filme que não
/// toca precisa dizer por quê antes de alguém achar que quebrou.
///
/// E vencido **não some do disco** (§4): «se você pegar a fita de novo, volta a
/// tocar sem baixar de novo — e um filme de 4 GB rebaixado por causa de um
/// empréstimo que voltou é o tipo de coisa que faz alguém desligar o offline».
/// Quem apaga é quem toca no botão.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TelaDosBaixados(
    modelo: ModeloDosBaixados,
    /// Tocar o arquivo **do disco**. Sem isto a tela volta a ser um inventário.
    ///
    /// O `ondeParou` chega resolvido pelo modelo — ver `ModeloDosBaixados.tocar`,
    /// e o defeito que ele conserta.
    aoTocar: (item: Baixado, ondeParou: Double) -> Unit = { _, _ -> },
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(start = 16.dp, end = 16.dp, top = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("baixados", style = MaterialTheme.typography.headlineSmall, color = Cores.texto)

        if (estado.itens.isEmpty()) {
            /// Vazio com frase, e não em branco: "não há nada baixado" é
            /// informação; uma tela muda é dúvida.
            Text(
                text = "nada baixado ainda",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.textoApagado,
            )
            return@Column
        }

        Cabecalho(quantos = estado.itens.size, bytes = estado.bytesNoAparelho)

        estado.itens.forEach { item ->
            Cartao(
                modelo = modelo,
                item = item,
                venceu = modelo.venceu(item),
                prazo = modelo.prazoEmPalavra(item),
                aoTocar = { modelo.tocar(item) { onde -> aoTocar(item, onde) } },
                aoApagar = { modelo.apagar(item.id) },
            )
        }

        /// O respiro do fim, e ele não é enfeite: a barra do facho **flutua por
        /// cima** do conteúdo (ver `AppOdeon`), e sem isto o último cartão fica
        /// com a ação embaixo dos rótulos das abas.
        Box(Modifier.height(dev.odeon.android.ui.ALTURA_DA_FILEIRA))
    }
}

/// `5 filmes · 9,6 GB no aparelho`.
///
/// ## É a pergunta que faz alguém abrir esta tela
///
/// Ninguém entra em «baixados» pra admirar a lista: entra pra assistir alguma
/// coisa, ou pra decidir o que apagar. A segunda precisa do número, e ele nunca
/// esteve na tela — `bytes` chegava em cada item e ninguém somava.
///
/// Os dois números saem na **serifa dourada das placas**, a mesma tinta que a
/// porta da locadora passou a usar hoje. As duas telas contam estoque; contar
/// estoque neste app tem uma tipografia.
@Composable
private fun Cabecalho(quantos: Int, bytes: Long) {
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        val numero = MaterialTheme.typography.headlineSmall.copy(fontSize = 20.sp)
        val palavra = MaterialTheme.typography.bodySmall

        Text("$quantos", style = numero, color = Cores.destaque)
        Text(
            /// Concordância, como no resto do app: um filme não é «1 filmes».
            text = if (quantos == 1) "filme ·" else "filmes ·",
            style = palavra,
            color = Cores.textoApagado,
            modifier = Modifier.padding(bottom = 2.dp),
        )
        Text(tamanhoCompacto(bytes), style = numero, color = Cores.destaque)
        Text(
            text = "no aparelho",
            style = palavra,
            color = Cores.textoApagado,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
}

/// Um cartão: a arte deitada, o título por cima, e a linha de ação embaixo.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun Cartao(
    modelo: ModeloDosBaixados,
    item: Baixado,
    venceu: Boolean,
    prazo: Pair<String, Int>?,
    aoTocar: () -> Unit,
    aoApagar: () -> Unit,
) {
    /// ⚠️ **Apagar pede o segundo toque, e é a mesma regra do devolver.**
    ///
    /// A locadora não devolve fita num toque só, e aqui o estrago é maior: o
    /// empréstimo se refaz em dois segundos, 2 GB voltam por download inteiro.
    /// O primeiro toque troca o rótulo pra «apagar mesmo?»; o segundo apaga.
    ///
    /// Ele **não** é um `AlertDialog` de propósito — uma caixa modal pra
    /// confirmar o apagamento de um arquivo é cerimônia que a web não faz e que
    /// interrompe a tela inteira por uma decisão de um item.
    var confirmando by remember(item.id) { mutableStateOf(false) }

    val podeTocar = item.pronto && !venceu

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Cores.fundoElevado),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(100.dp)
                /// §53: o cartão só é clicável quando há o que tocar. Quem está
                /// baixando ainda não tem filme, e quem venceu não tem permissão
                /// — nos dois casos o toque não vira nada, então ele não existe.
                .then(if (podeTocar) Modifier.clickable(onClick = aoTocar) else Modifier),
        ) {
            /// A arte deitada. O `backdrop` primeiro, o pôster como reserva —
            /// downloads gravados antes de 05/08/2026 não têm backdrop, e um
            /// pôster 2:3 esticado nesta faixa é melhor que um vão.
            val arte = modelo.arte(item.ficha.backdrop ?: item.ficha.poster)
            if (arte != null) {
                AsyncImage(
                    model = arte,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            /// ## O véu, e ele faz dois trabalhos
            ///
            /// Embaixo ele dá chão ao título — arte clara com texto branco em
            /// cima é o defeito que a ficha e o menu de disco já cobraram duas
            /// vezes neste projeto.
            ///
            /// E **em quem venceu ele cobre tudo**: a arte apaga porque o filme
            /// não toca. É a mesma gramática do cartaz visto na grade — o que
            /// está fora de alcance não brilha.
            Box(
                Modifier.fillMaxSize().background(
                    if (venceu) {
                        Brush.verticalGradient(
                            listOf(Cores.fundo.copy(alpha = 0.78f), Cores.fundo.copy(alpha = 0.88f)),
                        )
                    } else {
                        Brush.verticalGradient(
                            /// ⚠️ **O topo também escurece, e foi a foto que
                            /// mandou.**
                            ///
                            /// A primeira versão só tinha o véu de baixo. O
                            /// cartaz de *007: A Serviço Secreto* tem uma tarja
                            /// branca no alto da arte, e ela batia direto na
                            /// borda de cima do cartão — uma linha branca de
                            /// ponta a ponta, que lia como defeito de recorte e
                            /// não como parte do desenho do pôster.
                            ///
                            /// É a terceira vez que este projeto tropeça no mesmo
                            /// lugar: cromo claro encostando em borda já custou o
                            /// fundo do menu de disco e o cromo do player em
                            /// paisagem. A arte do acervo tem 8.316 origens
                            /// diferentes e **nenhuma garantia de margem**.
                            ///
                            /// 0,22 é o suficiente pra tirar o choque sem
                            /// apagar a arte — e ele dá chão ao selo do prazo,
                            /// que mora justamente neste canto.
                            0.00f to Cores.fundo.copy(alpha = 0.22f),
                            0.30f to Color.Transparent,
                            1.00f to Cores.fundo.copy(alpha = 0.92f),
                        )
                    },
                ),
            )

            Text(
                text = item.ficha.titulo,
                style = MaterialTheme.typography.bodyMedium,
                color = if (venceu) Cores.textoApagado else Cores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 12.dp, end = 12.dp, bottom = 9.dp),
            )

            /// O selo do prazo, no canto de cima.
            ///
            /// ⚠️ **Só existe em quem veio da locadora**, e a ausência é a
            /// informação: download de biblioteca não vence, e um selo dizendo
            /// «sem prazo» ocuparia espaço pra dizer que não há o que dizer.
            /// Vermelho a dois dias, como a cinta da caixa na locadora.
            val selo = when {
                venceu -> "venceu" to Cores.perigo
                prazo != null -> prazo.first to if (prazo.second <= 2) Cores.perigo else Cores.textoApagado
                else -> null
            }
            selo?.let { (texto, cor) ->
                Text(
                    text = texto,
                    style = Tipo.pilula.copy(fontSize = 11.sp),
                    color = cor,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                        .clip(CircleShape)
                        .background(Cores.fundo.copy(alpha = 0.72f))
                        .padding(horizontal = 9.dp, vertical = 3.dp),
                )
            }

            /// A barra de quem está baixando corre **no pé da arte**, e não numa
            /// linha própria: é a mesma borda de onde a barra de progresso do
            /// cartaz da grade sai, e economiza a altura de uma linha inteira.
            if (item.baixando) {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.45f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth((item.porcentagem / 100f).coerceIn(0f, 1f))
                            .height(3.dp)
                            .background(Cores.destaque),
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (podeTocar) {
                Text(
                    text = "▸ assistir",
                    style = Tipo.pilula,
                    color = Cores.destaque,
                    modifier = Modifier
                        .clip(CircleShape)
                        .clickable(onClick = aoTocar)
                        .padding(horizontal = 11.dp, vertical = 4.dp),
                )
            }

            Text(
                text = linhaDeEstado(item, venceu),
                style = Tipo.pilula.copy(fontSize = 11.sp),
                color = if (venceu || item.falhou) Cores.perigo else Cores.textoApagado,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )

            /// `apagar`, e o segundo toque.
            ///
            /// Em texto apagado, e não em vermelho **até virar pergunta**: o
            /// vermelho é a cor do que já é perigo, não do que ainda é opção.
            Text(
                text = if (confirmando) "apagar mesmo?" else "apagar",
                style = Tipo.pilula.copy(fontSize = 11.sp),
                color = if (confirmando) Cores.perigo else Cores.textoApagado,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable { if (confirmando) aoApagar() else confirmando = true }
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            )
        }
    }
}

/// A linha de estado, sem o que já está dito em outro lugar.
///
/// ⚠️ O prazo **saiu daqui** e virou selo sobre a arte: escrito nos dois lugares,
/// um deles ficaria velho primeiro — é a mesma razão que tirou o «você ainda pode
/// pegar N» da linha de regras da locadora.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun linhaDeEstado(item: Baixado, venceu: Boolean): String {
    val ficha = item.ficha
    return when {
        /// Vencido, a linha é o **caminho de saída** e não o diagnóstico. O selo
        /// já disse «venceu»; o §8b manda o não vir com a porta junto.
        venceu -> "pegue de novo na locadora e ela volta a tocar"
        item.falhou -> "falhou — toque em apagar e baixe de novo"
        /// Baixando é o único estado com **denominador**: `1,2 de 1,9 GB` diz
        /// quanto falta de um jeito que a porcentagem sozinha não diz — 64% de
        /// um arquivo cujo tamanho ninguém sabe não ajuda a decidir esperar.
        item.baixando -> buildString {
            append("baixando ${item.porcentagem.toInt()}%")
            val total = totalEstimado(item)
            if (total != null) append(" · ${tamanhoCompacto(item.bytes)} de ${tamanhoCompacto(total)}")
        }
        else -> listOfNotNull(
            ficha.duracaoEmSegundos?.takeIf { it > 0 }?.let { duracaoCompacta(it) },
            tamanhoCompacto(item.bytes).takeIf { item.bytes > 0 },
        ).joinToString(" · ")
    }
}

/// Quanto o arquivo vai ocupar quando terminar.
///
/// O Media3 dá o baixado e a porcentagem, não o total — então ele é **estimado**,
/// e a estimativa só vale quando já desceu o bastante pra ela não ser ruído.
/// Abaixo de 1%, `bytes / 0,004` devolve números que mudam a cada segundo, e um
/// tamanho que oscila na tela é pior que um tamanho ausente (§18).
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal fun totalEstimado(item: Baixado): Long? {
    if (item.porcentagem < 1f || item.bytes <= 0) return null
    return (item.bytes / (item.porcentagem / 100.0)).toLong()
}
