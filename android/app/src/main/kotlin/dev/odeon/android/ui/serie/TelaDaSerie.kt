package dev.odeon.android.ui.serie

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Recado
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.Tipo

/// A ficha de uma série, no celular — o mesmo desenho da TV, na largura da mão.
///
/// ## ⚠️ O que muda do desenho da sala, e por quê
///
/// | | |
/// |---|---|
/// | o pano de fundo é **um bloco 16:9 no topo**, não a tela toda | num celular em pé, arte de fundo atrás de texto vira textura: a coluna é estreita e o texto atravessa a imagem inteira |
/// | o botão principal é **largura cheia** | é o alvo mais tocado da tela, e a régua da casa é 48dp de altura mínima |
/// | as temporadas rolam **na horizontal** | vertical empurraria a primeira ação pra fora da dobra numa série de 5 |
///
/// O resto é igual, de propósito: mesmas palavras, mesma ordem, mesma decisão
/// sobre o que aparece. Ver `docs/SERIES.md` e `ModeloDaSerie`.
@Composable
fun TelaDaSerie(
    modelo: ModeloDaSerie,
    aoAbrirTemporada: (numero: Int) -> Unit,
    aoTocar: (episodioId: String, em: Double) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    when {
        estado.erro != null -> Recado(
            titulo = "a série não abriu",
            detalhe = estado.erro,
            aoTentar = modelo::carregar,
            aoVoltar = aoVoltar,
            modifier = modifier,
        )

        estado.carregando -> EsqueletoDaSerie(modifier)

        estado.vazio -> Recado(
            titulo = "esta série está sem episódios",
            detalhe = "o acervo tem a série, mas nenhum arquivo casou com ela",
            aoTentar = null,
            aoVoltar = aoVoltar,
            modifier = modifier,
        )

        else -> LazyColumn(
            modifier.fillMaxSize().background(Cores.fundo),
            contentPadding = PaddingValues(bottom = 28.dp),
        ) {
            item {
                Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f)) {
                    modelo.arte(estado.panoDeFundo)?.let {
                        AsyncImage(
                            model = it,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    /// ⚠️ O véu vertical existe pro título **encostar** na arte
                    /// sem flutuar sobre ela: o letreiro nasce na base do bloco,
                    /// e sem o degradê ele cairia sobre um céu claro.
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0.35f to Color.Transparent,
                                1f to Cores.fundo,
                            ),
                        ),
                    )

                    /// ## ⚠️ E um véu **no topo**, pro «voltar» — 19/08/2026
                    ///
                    /// Medido no emulador, n'`A Feiticeira`: o dourado da casa
                    /// (#e0b062, luz 181) sobre o céu claro do backdrop (luz 204
                    /// onde o texto cai) dá **1,33:1**. O mínimo legível pra
                    /// texto é 4,5:1 — ou seja, o botão estava na tela e não dava
                    /// pra ler.
                    ///
                    /// O véu de baixo já existia pelo mesmo motivo, e cobria o
                    /// **letreiro**; ninguém tinha olhado pro canto de cima.
                    ///
                    /// ⚠️ **Duas tentativas antes desta, e as duas medidas.**
                    /// Com 22% do bloco o contraste foi de 1,33 pra 1,47 — nada,
                    /// porque o botão não mora no topo do bloco e sim **abaixo da
                    /// barra de status** (`bounds=[32,100][146,153]`), onde um
                    /// degradê curto já virou transparente. Subindo pra 88% de
                    /// preto em 42% do bloco deu 3,43:1 — passa o limiar de texto
                    /// **grande** (3:1) e não o de texto normal (4,5:1), e ainda
                    /// custava um canto de arte quase preto.
                    ///
                    /// Então o véu voltou a ser discreto e quem ficou legível foi
                    /// o **botão**, com fundo próprio logo abaixo. Escurecer a
                    /// obra inteira pra salvar 114 pixels de texto era pagar caro
                    /// no lugar errado.
                    Box(
                        Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                0f to Color.Black.copy(alpha = 0.45f),
                                0.30f to Color.Transparent,
                            ),
                        ),
                    )
                    /// ⚠️ **`statusBarsPadding` aqui, e não no `Box`** — visto no
                    /// emulador em 18/08/2026: o bloco de arte é borda a borda de
                    /// propósito (ele sobe até debaixo do relógio, como a ficha),
                    /// e sem isto o «voltar» nascia **em cima do relógio**.
                    ///
                    /// O padding é do botão, então a arte continua sangrando e só
                    /// o alvo de toque desce.
                    /// ⚠️ **O botão carrega o próprio fundo**, e é isso que o faz
                    /// legível sobre qualquer arte: medido n'`A Feiticeira`, o
                    /// dourado sobre o céu claro do backdrop dava **1,33:1** — o
                    /// mínimo pra texto é 4,5:1. Não é caso raro: metade das
                    /// séries deste acervo tem backdrop claro.
                    ///
                    /// A pílula é a mesma forma que o resto do app usa pra pousar
                    /// cromo sobre imagem, e custa só a área do próprio rótulo.
                    TextButton(
                        onClick = aoVoltar,
                        modifier = Modifier
                            .align(Alignment.TopStart)
                            .statusBarsPadding()
                            .padding(start = 8.dp, top = 4.dp)
                            .background(
                                /// ⚠️ 0,70 e não 0,55: medido, a 0,55 o contraste
                                /// parou em **4,08:1**, e o mínimo pra texto é
                                /// 4,5. Meia dúzia de pontos de alfa custa nada e
                                /// é a diferença entre passar e quase passar.
                                Color.Black.copy(alpha = 0.70f),
                                RoundedCornerShape(50),
                            ),
                    ) {
                        Text("‹ voltar", color = Cores.destaque)
                    }
                }
            }

            item {
                Column(Modifier.padding(horizontal = 20.dp)) {
                    Text(
                        text = estado.titulo,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Cores.texto,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = contagem(
                            estado.temporadas.size,
                            estado.quantosEpisodios,
                            estado.quantosVistos,
                            estado.ano,
                        ),
                        style = Tipo.rotulo,
                        color = Cores.textoApagado,
                    )
                    /// ⚠️ A sinopse da série — 115 das 120 têm. Quem não tem não
                    /// ganha parágrafo nenhum (§24).
                    estado.sinopse?.let { sinopse ->
                        Spacer(Modifier.height(14.dp))
                        Text(
                            text = sinopse,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cores.textoApagado,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }

                    Spacer(Modifier.height(20.dp))

                    estado.ondeParou?.let { onde ->
                        val cod = onde.episodio.codigo?.let { "$it · " } ?: ""
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .heightIn(min = 52.dp)
                                .clip(RoundedCornerShape(26.dp))
                                .background(Cores.destaqueQuente)
                                .clickable {
                                    aoTocar(onde.episodio.id, onde.episodio.ondeParou ?: 0.0)
                                }
                                .padding(horizontal = 22.dp, vertical = 14.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = (if (onde.comecado) "▸  continuar  " else "▸  começar  ") +
                                    "$cod${onde.episodio.title}",
                                style = MaterialTheme.typography.titleSmall,
                                color = Cores.fundoAfundado,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        /// §24: «do começo» só havendo meio.
                        if (onde.comecado) {
                            TextButton(onClick = { aoTocar(onde.episodio.id, 0.0) }) {
                                Text("do começo", color = Cores.destaque)
                            }
                        }
                    }

                    RotuloDeSecao("temporadas", numero = estado.temporadas.size)
                }
            }

            item {
                LazyRow(
                    contentPadding = PaddingValues(horizontal = 20.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    items(estado.temporadas, key = { it.numero }) { t ->
                        CartaoDaTemporada(
                            temporada = t,
                            arte = modelo.arte(t.arte),
                            aoTocar = { aoAbrirTemporada(t.numero) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CartaoDaTemporada(
    temporada: TemporadaDaSerie,
    arte: String?,
    aoTocar: () -> Unit,
) {
    /// ## ⚠️ O cartão de temporada virou **retrato** — 18/08/2026
    ///
    /// Ele era 16:9 porque a única imagem que havia era o `still` do primeiro
    /// episódio. Desde hoje o servidor manda o **pôster da temporada** do TMDB
    /// (461 de 473), e pôster é 2:3 — enfiá-lo num quadro deitado cortava a arte
    /// inteira: a `Temporada 1` do Arcane virou um olho.
    ///
    /// É a mesma lição do episódio, ao contrário: **a moldura segue a imagem**,
    /// e não o contrário.
    ///
    /// ⚠️ As 12 sem pôster caem no `still` do primeiro episódio, e aí é um 16:9
    /// dentro de um 2:3 — cortado nas laterais, que é o corte menos destrutivo
    /// dos dois.
    Column(
        Modifier
            .width(150.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = aoTocar),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(12.dp))) {
            if (arte != null) {
                AsyncImage(
                    model = arte,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(Modifier.fillMaxSize().background(Cores.fundoElevado))
            }
            temporada.andado?.let { BarraDeAndado(it, Modifier.align(Alignment.BottomStart)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(temporada.rotulo, style = MaterialTheme.typography.titleSmall, color = Cores.texto)
        Text(
            text = buildList {
                add("${temporada.quantos} episódio" + if (temporada.quantos > 1) "s" else "")
                if (temporada.vistos > 0) {
                    add("${temporada.vistos} visto" + if (temporada.vistos > 1) "s" else "")
                }
            }.joinToString("  ·  "),
            style = Tipo.rotulo,
            color = Cores.textoApagado,
        )
    }
}

/// `2021 · 2 temporadas · 18 episódios · 3 vistos` — cada pedaço só entra se
/// tiver o que dizer (§24).
internal fun contagem(temporadas: Int, episodios: Int, vistos: Int, ano: Int?): String = buildList {
    ano?.let { add(it.toString()) }
    if (temporadas > 0) add("$temporadas temporada" + if (temporadas > 1) "s" else "")
    if (episodios > 0) add("$episodios episódio" + if (episodios > 1) "s" else "")
    if (vistos > 0) add("$vistos visto" + if (vistos > 1) "s" else "")
}.joinToString("  ·  ")

@Composable
internal fun BarraDeAndado(fracao: Float, modifier: Modifier = Modifier) {
    Box(modifier.fillMaxWidth().height(4.dp).background(Color.Black.copy(alpha = 0.55f))) {
        Box(
            Modifier
                .fillMaxWidth(fracao.coerceIn(0f, 1f))
                .height(4.dp)
                .background(Cores.destaque),
        )
    }
}

@Composable
internal fun MarcaDeVisto(modifier: Modifier = Modifier) {
    Box(
        modifier.size(26.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.78f)),
        contentAlignment = Alignment.Center,
    ) {
        Text("✓", style = Tipo.rotulo, color = Cores.destaque)
    }
}

@Composable
private fun EsqueletoDaSerie(modifier: Modifier = Modifier) {
    /// §15: quadros vazios, e nunca a palavra «carregando».
    Column(modifier.fillMaxSize().background(Cores.fundo)) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(Cores.fundoElevado))
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            repeat(2) {
                Box(
                    Modifier
                        .width(230.dp)
                        .aspectRatio(16f / 9f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(Cores.fundoElevado),
                )
            }
        }
    }
}
