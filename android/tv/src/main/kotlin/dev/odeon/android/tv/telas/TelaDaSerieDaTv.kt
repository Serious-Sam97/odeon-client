package dev.odeon.android.tv.telas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.FileiraFantasma
import dev.odeon.android.tv.ui.Cartaz
import dev.odeon.android.tv.ui.Quadro
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.saidaPraEsquerda
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.serie.ModeloDaSerie
import dev.odeon.android.ui.serie.TemporadaDaSerie

/// A ficha de uma série — o desenho aprovado pelo dono em 18/08/2026.
///
/// ## ⚠️ Ela substitui a grade plana de 84 episódios
///
/// Até ontem, entrar numa série trocava a biblioteca por uma parede com todos os
/// episódios de todas as temporadas em fila contínua: o `S03E13` colado no
/// `S04E01`, sem fronteira e sem jeito de pular pra uma temporada. Ver
/// `docs/SERIES.md` pro que foi medido na TCL.
///
/// A tela responde três perguntas, nesta ordem — e é a ordem que decide o
/// desenho:
///
/// | | |
/// |---|---|
/// | **onde eu parei?** | o botão principal, com o episódio escrito nele. Zero apertos |
/// | **o que é isto?** | letreiro, sinopse e a contagem |
/// | **onde está o resto?** | a fileira de temporadas, cada uma com arte própria |
///
/// ⚠️ A arte de cada temporada **não vem do servidor ainda** — é o `still` do
/// primeiro episódio dela. Ver `TemporadaDaSerie.arte` e o
/// `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`, que pede o pôster de temporada do TMDB. Quando
/// ele chegar, esta tela não muda uma linha.
@Composable
fun TelaDaSerieDaTv(
    modelo: ModeloDaSerie,
    aoAbrirTemporada: (numero: Int) -> Unit,
    /// ⚠️ Leva o **segundo** junto, e não só o id. O `Onde.Ficha` toca a partir
    /// do que recebe: mandar `0.0` no «continuar» faria o botão que diz
    /// «continuar» começar do zero — que é exatamente o que o botão ao lado
    /// existe pra fazer.
    aoTocar: (episodioId: String, em: Double) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    saidaEsquerda: FocusRequester? = null,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val principal = remember { FocusRequester() }

    /// ⚠️ Sem esta linha, «voltar» na ficha da série **sai do app** — é a regra
    /// escrita na `AtividadeDaTv`: quem não liga um `BackHandler` está dizendo
    /// que ali voltar é sair. Foi assim que a ficha da obra quebrou em agosto.
    BackHandler { aoVoltar() }

    /// ⚠️ Pede o foco **até o nó existir** — a lista chegar e o botão estar
    /// composto são dois momentos. É a mesma lição que a biblioteca pagou;
    /// ver `TelaDaBibliotecaDaTv.insista`.
    LaunchedEffect(estado.carregando) {
        if (!estado.carregando) repeat(6) {
            runCatching { principal.requestFocus() }
            kotlinx.coroutines.delay(70)
        }
    }

    when {
        estado.erro != null -> Recado(
            titulo = "a série não abriu",
            detalhe = estado.erro,
            modifier = modifier,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BotaoDaSala("tentar de novo", modelo::carregar, principal = true)
                BotaoDaSala("voltar", aoVoltar)
            }
        }

        estado.carregando -> Column(
            modifier.fillMaxSize().padding(top = Sala.overscanV),
            verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
        ) {
            /// §15: quadros vazios, não a palavra «carregando».
            FileiraFantasma(quantos = 3, deitado = true)
        }

        estado.vazio -> Recado(
            titulo = "esta série está sem episódios",
            detalhe = "o acervo tem a série, mas nenhum arquivo casou com ela",
            modifier = modifier,
        ) { BotaoDaSala("voltar", aoVoltar, principal = true) }

        else -> Box(modifier.fillMaxSize().background(Cores.fundo)) {
            PanoDeFundo(modelo.arte(estado.panoDeFundo))

            Column(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = Sala.overscanH, vertical = Sala.overscanV),
            ) {
                Text(
                    text = estado.titulo,
                    style = MaterialTheme.typography.displayMedium,
                    color = Cores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth(0.62f),
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    text = contagem(estado.temporadas.size, estado.quantosEpisodios, estado.quantosVistos, estado.ano),
                    style = MaterialTheme.typography.labelLarge,
                    color = Cores.textoApagado,
                )

                /// ⚠️ A sinopse da série chegou em 18/08/2026 — 115 das 120
                /// têm. As 5 que não têm **não ganham parágrafo nenhum** (§24),
                /// e a fileira de temporadas sobe no lugar.
                estado.sinopse?.let { sinopse ->
                    Spacer(Modifier.height(18.dp))
                    Text(
                        text = sinopse,
                        style = MaterialTheme.typography.bodyLarge,
                        color = Cores.textoApagado,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth(0.52f),
                    )
                }

                Spacer(Modifier.height(34.dp))

                /// ⚠️ **«continuar» × «começar»**, e a diferença é o que já
                /// aconteceu. Oferecer «continuar» a quem nunca abriu a série é
                /// prometer uma posição que não existe (§53).
                estado.ondeParou?.let { onde ->
                    val cod = onde.episodio.codigo?.let { "$it · " } ?: ""
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        BotaoDaSala(
                            rotulo = if (onde.comecado) {
                                "▸ continuar  $cod${onde.episodio.title}"
                            } else {
                                "▸ começar  $cod${onde.episodio.title}"
                            },
                            aoEscolher = {
                                aoTocar(onde.episodio.id, onde.episodio.ondeParou ?: 0.0)
                            },
                            principal = true,
                            modifier = Modifier
                                .focusRequester(principal)
                                .saidaPraEsquerda(saidaEsquerda),
                        )
                        /// ⚠️ «do começo» **só existe havendo meio**. Ao lado de
                        /// um «começar», ele seria o mesmo botão duas vezes — e
                        /// §24: o que não tem o que dizer desaparece.
                        if (onde.comecado) {
                            BotaoDaSala("do começo", { aoTocar(onde.episodio.id, 0.0) })
                        }
                    }
                }

                Spacer(Modifier.height(44.dp))
                RotuloDeSecao("temporadas", numero = estado.temporadas.size)

                LazyRow(horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes)) {
                    itemsIndexed(estado.temporadas, key = { _, t -> t.numero }) { i, t ->
                        CartaoDaTemporada(
                            temporada = t,
                            arte = modelo.arte(t.arte),
                            aoEscolher = { aoAbrirTemporada(t.numero) },
                            saidaEsquerda = if (i == 0) saidaEsquerda else null,
                        )
                    }
                }
            }
        }
    }
}

/// `2021 · 2 temporadas · 18 episódios · 3 vistos`.
///
/// ⚠️ Cada pedaço só entra se tiver o que dizer (§24). Uma série sem nada visto
/// não escreve «0 vistos» — ela simplesmente não fala do assunto.
private fun contagem(temporadas: Int, episodios: Int, vistos: Int, ano: Int?): String = buildList {
    ano?.let { add(it.toString()) }
    if (temporadas > 0) add("$temporadas temporada" + if (temporadas > 1) "s" else "")
    if (episodios > 0) add("$episodios episódio" + if (episodios > 1) "s" else "")
    if (vistos > 0) add("$vistos visto" + if (vistos > 1) "s" else "")
}.joinToString("  ·  ")

@Composable
private fun CartaoDaTemporada(
    temporada: TemporadaDaSerie,
    arte: String?,
    aoEscolher: () -> Unit,
    saidaEsquerda: FocusRequester?,
) {
    /// ## ⚠️ `Cartaz` e não `Quadro` — 18/08/2026
    ///
    /// A fileira era deitada porque a única imagem era o `still` do primeiro
    /// episódio. Desde hoje o servidor manda o **pôster da temporada** do TMDB,
    /// e pôster é 2:3: num quadro 16:9 a `Temporada 1` do Arcane virava um olho.
    /// **A moldura segue a imagem.**
    Cartaz(
        titulo = temporada.rotulo,
        arte = arte,
        largura = Sala.cartazLdaEstante,
        altura = Sala.cartazAdaEstante,
        aoEscolher = aoEscolher,
        /// ⚠️ `andado` é a fração de episódios **vistos**, e não a posição
        /// dentro de um arquivo: numa temporada, «andado» é quantos acabaram.
        andado = temporada.andado ?: 0f,
        detalhe = buildList {
            add("${temporada.quantos} episódio" + if (temporada.quantos > 1) "s" else "")
            if (temporada.vistos > 0) add("${temporada.vistos} visto" + if (temporada.vistos > 1) "s" else "")
        }.joinToString("  ·  "),
        saidaEsquerda = saidaEsquerda,
    )
}

/// A arte larga do topo, com os dois véus da ficha da obra — o horizontal segura
/// a coluna de texto, o vertical segura a base.
///
/// ## ⚠️ [forte] existe porque as duas telas querem coisas opostas — 18/08/2026
///
/// Na ficha da série a arte **é** o assunto: ela apresenta a obra a quem talvez
/// nunca a tenha visto. Na temporada o assunto é a **lista**, e ali o mesmo pano
/// de fundo — visto na TCL — virou um rosto de dois metros atrás de nove linhas
/// de texto, competindo com cada uma delas.
///
/// Não é o mesmo desenho com opacidades diferentes por gosto: é a mesma peça
/// respondendo a duas perguntas diferentes.
@Composable
internal fun PanoDeFundo(url: String?, forte: Boolean = true) {
    if (url != null) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = androidx.compose.ui.layout.ContentScale.Crop,
            modifier = Modifier.fillMaxSize(),
        )
    }
    Box(
        Modifier.fillMaxSize().background(
            Brush.horizontalGradient(
                0f to Cores.fundo.copy(alpha = 0.97f),
                (if (forte) 0.38f else 0.30f) to Cores.fundo.copy(alpha = 0.95f),
                (if (forte) 0.72f else 0.98f) to Color.Transparent,
                1f to Color.Transparent,
            ),
        ),
    )
    Box(
        Modifier.fillMaxSize().background(
            Brush.verticalGradient(
                (if (forte) 0.42f else 0.18f) to Color.Transparent,
                1f to Cores.fundo.copy(alpha = 0.96f),
            ),
        ),
    )
    /// ⚠️ Na lista, uma gaze por cima de tudo. Os dois véus acima são
    /// direcionais e deixam um canto aceso; numa lista **não há canto vazio** —
    /// as fileiras atravessam a tela inteira.
    if (!forte) {
        Box(Modifier.fillMaxSize().background(Cores.fundo.copy(alpha = 0.72f)))
    }
}
