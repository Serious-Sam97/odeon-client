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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.dados.ItemPraContinuar
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.biblioteca.ModeloDaBiblioteca

/// A aba das séries.
///
/// ## ⚠️ Por que ela é uma aba, e não uma prateleira — 18/08/2026
///
/// A primeira tentativa foi uma fileira de pílulas no topo da biblioteca:
/// `tudo · série · filme · anime`. O dono olhou e disse o que estava errado —
/// **a separação parecia um filtro**. Era uma fileira de chips idêntica à de
/// `filtros ▾` logo abaixo dela, e a mais importante das duas com a cara da
/// menos importante.
///
/// Vieram mais duas tentativas, e as duas eram decoração em vez de conserto:
/// caixa de coleção em 3D (o mesmo objeto da locadora, onde ele significa
/// **escassez** — repeti-lo aqui gastaria esse significado) e estante de
/// lombadas (que obrigava a **ler de lado**, e eu cheguei a elogiar isso como
/// se fosse virtude).
///
/// O que ficou é o que ele tinha proposto no primeiro dia: **duas bibliotecas
/// separadas**, como o Jellyfin faz. Sem chip, sem 3D, sem texto girado.
///
/// ## A organização é **onde você está**, e não o alfabeto
///
/// | | |
/// |---|---|
/// | **na metade** | as séries começadas, em quadro largo, com `S01E04 · faltam 21min` |
/// | **não começadas** | o resto, em pôster, com `6 temporadas · 63 ep` |
///
/// Alfabético serve pra quem já sabe o nome — e pra isso existe a busca, logo
/// acima. Às nove da noite a pergunta é «onde eu parei».
@Composable
fun TelaDasSeries(
    modelo: ModeloDaBiblioteca,
    aoAbrirSerie: (id: String, titulo: String) -> Unit,
    /// ⚠️ Leva o **par da série** junto quando o episódio veio de «na metade»:
    /// é o que faz o «voltar» dele saber que há uma ficha de série no caminho.
    aoAbrirObra: (id: String, serie: Pair<String, String>?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    /// ⚠️ **Só as séries** da fileira de continuar. Ela já vem colapsada por
    /// série (ver `colapsarPorSerie`), então cada linha aqui é uma série, e não
    /// um episódio. Filme começado não entra: ele é assunto da outra aba.
    val naMetade = estado.paraContinuar.filter { it.tituloDaSerie != null }
    val comecadas = naMetade.mapNotNull { it.tituloDaSerie }.toSet()

    /// ⚠️ **O padding é o mesmo da grade dos filmes** — 16dp nas laterais.
    /// A primeira versão não tinha nenhum: os pôsteres encostavam na borda e os
    /// rótulos das seções também. «Ta feio», e estava.
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier.fillMaxSize().background(Cores.fundo),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 28.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        /// ⚠️ **O cabeçalho e a busca faltavam inteiros.** A aba abria direto no
        /// `NA METADE`, sem dizer onde se estava nem oferecer busca — a aba dos
        /// filmes tem os dois, e o desenho aprovado também.
        item(span = { GridItemSpan(maxLineSpan) }) {
            Column(Modifier.padding(top = 8.dp)) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "séries",
                        style = MaterialTheme.typography.displaySmall,
                        color = Cores.texto,
                    )
                    estado.total?.let { total ->
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "$total",
                            style = MaterialTheme.typography.titleMedium,
                            color = Cores.destaque,
                            modifier = Modifier.padding(bottom = 6.dp),
                        )
                    }
                }
                Spacer(Modifier.height(12.dp))
                CampoDeBuscaDasSeries(
                    valor = estado.filtros.busca,
                    aoMudar = modelo::mudouBusca,
                )
                Spacer(Modifier.height(6.dp))
            }
        }
        if (naMetade.isNotEmpty()) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                Column {
                    RotuloDeSecao("na metade", numero = naMetade.size)
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        items(naMetade, key = { it.id }) { item ->
                            NaMetade(
                                item = item,
                                arte = modelo.arte(item),
                                /// ⚠️ O par vai **nulo** aqui: a fileira de
                                /// continuar não traz o id da coleção, só o
                                /// título. Sem id não dá pra montar a ficha da
                                /// série, e afirmar um id que não se tem é pior
                                /// que voltar um degrau a menos.
                                aoTocar = { aoAbrirObra(item.id, null) },
                            )
                        }
                    }
                }
            }
        }

        item(span = { GridItemSpan(maxLineSpan) }) {
            RotuloDeSecao(
                if (naMetade.isEmpty()) "todas as séries" else "não começadas",
                /// ⚠️ O total do servidor **menos** as que já estão acima — e não
                /// o total cru, que contaria duas vezes o que a tela já mostrou.
                numero = estado.total?.let { (it - comecadas.size).coerceAtLeast(0) },
            )
        }

        items(
            estado.itens.filter { it.title !in comecadas },
            key = { it.id },
        ) { item ->
            CartazDaSerie(
                item = item,
                arte = modelo.capa(item),
                aoTocar = {
                    if (item.eSerie) {
                        aoAbrirSerie(item.id, item.title)
                    } else {
                        aoAbrirObra(item.id, null)
                    }
                },
            )
        }
    }
}

@Composable
private fun NaMetade(item: ItemPraContinuar, arte: String?, aoTocar: () -> Unit) {
    Column(
        Modifier.width(268.dp).clip(RoundedCornerShape(12.dp)).clickable(onClick = aoTocar),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(RoundedCornerShape(12.dp))) {
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
            Box(
                Modifier.fillMaxSize().background(
                    Brush.verticalGradient(0.5f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.7f)),
                ),
            )
            andado(item)?.let { BarraDeAndado(it, Modifier.align(Alignment.BottomStart)) }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = item.tituloDaSerie ?: item.title,
            style = MaterialTheme.typography.titleSmall,
            color = Cores.texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = linhaDoEpisodio(item),
            style = Tipo.rotulo,
            color = Cores.destaque,
            maxLines = 1,
        )
    }
}

@Composable
private fun CartazDaSerie(item: ItemDaBiblioteca, arte: String?, aoTocar: () -> Unit) {
    Column(Modifier.clickable(onClick = aoTocar)) {
        Box(Modifier.fillMaxWidth().aspectRatio(2f / 3f).clip(RoundedCornerShape(10.dp))) {
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
            /// ⚠️ A barra aqui conta **episódios terminados**, não posição num
            /// arquivo. Numa série, «andado» é quantos acabaram.
            if (item.quantasVistas > 0 && item.quantasObras > 0) {
                BarraDeAndado(
                    item.quantasVistas.toFloat() / item.quantasObras,
                    Modifier.align(Alignment.BottomStart),
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(
            text = item.title,
            style = MaterialTheme.typography.titleSmall,
            color = Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = quantoTem(item),
            style = Tipo.rotulo,
            color = Cores.textoApagado,
            maxLines = 1,
        )
        if (item.quantasVistas > 0) {
            Text("${item.quantasVistas} vistos", style = Tipo.rotulo, color = Cores.destaque)
        }
    }
}

/// ## ⚠️ **Um número só**, e não `6 temporadas · 63 ep` — visto no emulador
///
/// A linha de dois números não coube: num cartaz de um terço da tela ela saía
/// `4 temporadas ·` com o resto cortado — pior que um número, porque o `·`
/// promete um segundo que nunca chega.
///
/// A regra: **temporadas quando há mais de uma, episódios quando há uma só**.
/// «1 temporada» não informa nada (toda série tem pelo menos uma); o que
/// distingue uma série de uma temporada é quantos episódios ela tem.
private fun quantoTem(item: ItemDaBiblioteca): String = when {
    item.quantasTemporadas > 1 -> "${item.quantasTemporadas} temporadas"
    item.quantasObras > 0 -> "${item.quantasObras} episódio" + if (item.quantasObras > 1) "s" else ""
    else -> item.year?.toString().orEmpty()
}

/// `S01E04 · faltam 21min`.
private fun linhaDoEpisodio(item: ItemPraContinuar): String = buildList<String> {
    /// ⚠️ `S01E04` só quando os **dois** números existem: meio código não
    /// identifica episódio nenhum (§18).
    val t = item.temporada
    val e = item.episodio
    if (t != null && e != null) add("S%02dE%02d".format(t, e)) else if (e != null) add("ep $e")
    val total = item.duracaoEmSegundos ?: 0.0
    val faltam = ((total - (item.ondeParou ?: 0.0)) / 60).toInt()
    if (total > 0 && faltam > 0) add("faltam ${faltam}min")
}.joinToString(" · ")

private fun andado(item: ItemPraContinuar): Float? {
    val onde = item.ondeParou ?: return null
    val total = item.duracaoEmSegundos?.takeIf { it > 0 } ?: return null
    return (onde / total).toFloat().coerceIn(0f, 1f)
}

/// O campo de busca da aba — o mesmo desenho do da biblioteca.
@Composable
private fun CampoDeBuscaDasSeries(valor: String, aoMudar: (String) -> Unit) {
    androidx.compose.foundation.text.BasicTextField(
        value = valor,
        onValueChange = aoMudar,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Cores.texto),
        cursorBrush = androidx.compose.ui.graphics.SolidColor(Cores.destaque),
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Cores.fundoElevado)
            .padding(horizontal = 18.dp, vertical = 14.dp),
        decorationBox = { campo ->
            if (valor.isEmpty()) {
                Text(
                    "buscar nas séries…",
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cores.textoApagado,
                )
            }
            campo()
        },
    )
}
