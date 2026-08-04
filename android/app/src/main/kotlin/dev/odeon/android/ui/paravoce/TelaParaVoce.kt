package dev.odeon.android.ui.paravoce

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Recomendacao
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.LampadasDaMarquise
import dev.odeon.android.ui.PilulaDeFiltro
import dev.odeon.android.ui.Tipo

/// Para você.
///
/// ## É a tese do projeto numa tela só
///
/// O README do Odeon abre com ela: «não é um catálogo de arquivos, é uma
/// **biblioteca que te conhece**». A §5 põe esta tela por último de propósito —
/// ela só faz sentido depois que houver o que conhecer, e o que ela conhece vem
/// do progresso que a fase 2 passou a gravar.
///
/// ## O motivo é o produto, não o enfeite
///
/// Uma lista ordenada por `score` é indistinguível de "os mais recentes". O que
/// separa as duas é a frase — *por que este filme, pra mim, agora* — e ela vem
/// pronta do servidor, que é quem tem o perfil e os vetores.
///
/// Por isso um cartão **sem motivo** aqui é um cartão que não deveria existir:
/// ele viraria catálogo no meio da curadoria.
/// O "‹ biblioteca" saiu quando esta tela virou aba. Ver `TelaDaLocadora`.
///
/// O `@OptIn` é do `FlowRow`, que continua experimental de assinatura mas é
/// estável de comportamento há vários releases — e é o único jeito de ter
/// `flex-wrap` sem escrever medição de linha à mão.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaParaVoce(
    modelo: ModeloParaVoce,
    aoAbrirObra: (String) -> Unit,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("para você", style = MaterialTheme.typography.headlineSmall, color = Cores.texto)

        /// "Tenho 90 minutos" — o filtro que faz esta tela ser de celular.
        ///
        /// A pergunta que se faz com o telefone na mão não é "o que existe", é
        /// "o que cabe agora". O servidor já aceita `minutes`; a tela só precisa
        /// oferecer os cortes que alguém realmente usa.
        ///
        /// ## R3: eram três botões, viraram as seis pílulas da web
        ///
        /// Os três eram `TextButton` soltos — "qualquer", "até 90min", "até 2h" —
        /// e o único sinal de qual valia era a **cor da letra**. A `.chip.on` da
        /// web muda borda, fundo e letra de uma vez, e é o que faz o corte
        /// escolhido ser lido de relance em vez de procurado.
        ///
        /// ⚠️ Os seis cortes **não** são escolha minha: são o `TIME_OPTIONS` do
        /// `ForYou.tsx:13`, copiado. A primeira versão desta lista inventou um
        /// "1h30" que não existe lá e perdeu o "15 min" que existe — e inventar
        /// corte é decidir sozinho que 90 minutos é uma pergunta que alguém faz.
        /// O "15 min" é o que responde "tenho um episódio de tempo", e some se
        /// ninguém copiar a lista de onde ela mora.
        ///
        /// O rótulo "tenho" é o `.filter-label` (`styles.css:1164`): 11px, caixa
        /// alta, `letter-spacing: 0.1em`. Sem ele, "45 min" sozinho é ambíguo —
        /// pode ser duração do filme ou tempo de quem assiste.
        ///
        /// `FlowRow` e não rolagem lateral: os seis não cabem nos 411dp de um
        /// celular, e rolagem lateral **esconde** cortes atrás de um gesto que
        /// ninguém sabe que existe (§8b). A web resolve com `flex-wrap` nos
        /// `.chips`, e o `FlowRow` é isso mesmo.
        Text(
            text = "tenho".uppercase(),
            style = Tipo.rotulo.copy(letterSpacing = 0.1.em),
            color = Cores.textoApagado,
        )
        FlowRow(
            /// 6dp, que é o `gap` do `.chips`. O vertical fica em zero porque o
            /// `minimumInteractiveComponentSize` das pílulas já reserva 48dp de
            /// altura por linha — somar espaçamento aqui abriria um vão que a
            /// web não tem, e foi o que a primeira versão fez.
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            listOf<Pair<String, Int?>>(
                "qualquer tempo" to null,
                "15 min" to 15,
                "30 min" to 30,
                "45 min" to 45,
                "1h" to 60,
                "2h" to 120,
            ).forEach { (rotulo, minutos) ->
                PilulaDeFiltro(
                    texto = rotulo,
                    selecionada = estado.minutos == minutos,
                    aoTocar = { modelo.filtrar(minutos) },
                )
            }
        }

        if (estado.carregando) {
            Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cores.destaque)
            }
            return@Column
        }

        estado.erro?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Cores.perigo)
        }

        /// "Ainda não te conheço" **não** é "não recomendo nada".
        ///
        /// Sem esta distinção, uma lista fraca parece algoritmo ruim. Com ela,
        /// vira convite — e é honesto: o servidor está dizendo que ainda não tem
        /// dado suficiente, não que o acervo não tem nada pra essa pessoa.
        if (estado.aindaNaoTeConhece) {
            Text(
                text = "ainda estou te conhecendo — assista mais um pouco e isto aqui melhora",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
            )
        }

        /// O primeiro é **herói**, e o resto é fila — a mesma divisão do
        /// `ForYou.tsx` (`heroi = items[0]`).
        ///
        /// Sem ela, "para você" é uma lista ordenada por `score`, e uma lista
        /// ordenada por score é indistinguível de "os mais recentes" — que é o
        /// que o comentário lá em cima diz que esta tela não pode ser. O herói é
        /// a tela **escolhendo**, em vez de listar.
        estado.itens.firstOrNull()?.let { primeiro ->
            CartaoHeroi(
                item = primeiro,
                arte = modelo.arte(primeiro),
                aoTocar = { aoAbrirObra(primeiro.id) },
            )
        }

        estado.itens.drop(1).forEach { item ->
            Cartao(
                item = item,
                arte = modelo.arte(item),
                aoTocar = { aoAbrirObra(item.id) },
            )
        }

        if (estado.itens.isEmpty() && estado.erro == null) {
            Text(
                text = "nada pra recomendar com esse tempo",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.textoApagado,
            )
        }
    }
}

/// O herói — o primeiro da lista, desenhado como cartaz de marquise.
///
/// ## As lâmpadas são o item da R6, e o porquê está em `Marquise.kt`
///
/// Resumo: a R6 pedia perfuração de película «como na web», a folha não tem
/// perfuração nenhuma, e o que o herói do `ForYou.tsx` tem é a `.bulbs` — as
/// lâmpadas da marquise, com a luz correndo. É substituição medida, e vetável.
///
/// ## A arte entra escura, e é a folha que diz quanto
///
/// `.hero-art` (`styles.css:2075`) aplica `brightness(0.32) saturate(0.85)` e
/// põe por cima a `.hero-wash`, três gradientes empilhados. O motivo é o §18 por
/// outro caminho: sobre a arte crua, o título e o **motivo** ficariam ilegíveis
/// em metade dos pôsteres — e o motivo é a coisa que esta tela veio dizer.
///
/// Aqui a arte vai a 32% de brilho por `ColorFilter`, e a lavagem é um gradiente
/// vertical da cor de fundo. Não são os três da web: os outros dois dependem da
/// `--accent-work`, que é a cor dominante da obra, e o `Recomendacao` a traz —
/// mas empilhar três camadas sobre a arte num cartão que rola é onde o enfeite
/// começa a custar quadro. Fica um, que é o que sustenta o texto.
@Composable
private fun CartaoHeroi(item: Recomendacao, arte: String?, aoTocar: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Cores.fundoElevado)
            .clickable(onClick = aoTocar),
    ) {
        if (arte != null) {
            AsyncImage(
                model = arte,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                /// `brightness(0.32)` da `.hero-art`, traduzido: cada canal
                /// multiplicado por 0,32. O `saturate(0.85)` fica de fora — ele
                /// pede outra matriz, e o ganho não paga a linha.
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            0.32f, 0f, 0f, 0f, 0f,
                            0f, 0.32f, 0f, 0f, 0f,
                            0f, 0f, 0.32f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                ),
            )
        }

        /// A lavagem: o fundo sobe da base e some no meio, pra o texto ter chão.
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        0.45f to Cores.fundo.copy(alpha = 0.55f),
                        1f to Cores.fundo.copy(alpha = 0.92f),
                    ),
                ),
        )

        /// A marquise, no topo — o mesmo lugar da web (`top: 0`).
        LampadasDaMarquise(Modifier.align(Alignment.TopCenter))

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            /// `headlineSmall` é o slot serifado da R1, e o `.hero-title` da web
            /// também é `--font-display`. É o único lugar do "para você" onde o
            /// título é letreiro, e é o que separa o herói da fila.
            Text(
                text = item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Cores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.porque?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.destaque,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/// Um cartão largo, com o pôster à esquerda e o **motivo** ao lado.
///
/// Deitado e não em grade: numa grade o motivo não caberia, e sem o motivo esta
/// tela seria a biblioteca com outra ordem. A forma segue o que ela tem de
/// diferente.
@Composable
private fun Cartao(item: Recomendacao, arte: String?, aoTocar: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Cores.fundoElevado)
            .clickable(onClick = aoTocar)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier.width(72.dp).aspectRatio(2f / 3f).clip(RoundedCornerShape(4.dp))
                .background(Cores.fundoAfundado),
        ) {
            if (arte != null) {
                AsyncImage(
                    model = arte,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            item.year?.let {
                Text("$it", style = MaterialTheme.typography.labelSmall, color = Cores.textoApagado)
            }
            /// O motivo, em destaque — é o que esta tela veio dizer.
            item.porque?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.destaque,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
