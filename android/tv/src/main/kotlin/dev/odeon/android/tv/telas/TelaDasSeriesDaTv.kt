package dev.odeon.android.tv.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.dados.ItemPraContinuar
import dev.odeon.android.tv.ui.Cartaz
import dev.odeon.android.tv.ui.FileiraFantasma
import dev.odeon.android.tv.ui.Quadro
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.biblioteca.ModeloDaBiblioteca

/// As séries, na sala — o destino que a trilha ganhou em 18/08/2026.
///
/// ## ⚠️ Aba, e não prateleira
///
/// A TV chegou a ter uma fileira de pílulas (`tudo · série · filme · anime`) no
/// topo da biblioteca. O dono olhou o mesmo desenho no celular e disse o que
/// estava errado: **a separação parecia um filtro**. Duas bibliotecas separadas
/// é o que ele tinha proposto no primeiro dia, e é o que ficou.
///
/// ## A organização é **onde você está**
///
/// | | |
/// |---|---|
/// | **na metade** | as começadas, em quadro deitado, com `S01E04 · faltam 21min` |
/// | **todas as séries** | o resto, em cartaz, com `6 temporadas` ou `63 episódios` |
///
/// Alfabético serve pra quem já sabe o nome, e pra isso existe a busca da
/// trilha. Às nove da noite a pergunta é «onde eu parei».
@Composable
fun TelaDasSeriesDaTv(
    modelo: ModeloDaBiblioteca,
    aoAbrirSerie: (id: String, titulo: String) -> Unit,
    aoAbrirObra: (String) -> Unit,
    modifier: Modifier = Modifier,
    saidaEsquerda: FocusRequester? = null,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    /// ⚠️ Só as séries da fileira de continuar — ela já vem colapsada por série
    /// (ver `colapsarPorSerie`), então cada linha aqui é uma série e não um
    /// episódio. Filme começado é assunto da outra aba.
    val naMetade = estado.paraContinuar.filter { it.tituloDaSerie != null }
    val comecadas = naMetade.mapNotNull { it.tituloDaSerie }.toSet()

    if (estado.carregando && estado.itens.isEmpty()) {
        Box(modifier.fillMaxSize().background(Cores.fundo).padding(top = Sala.overscanV)) {
            /// §15: quadros vazios, e nunca a palavra «carregando».
            FileiraFantasma(quantos = 6)
        }
        return
    }

    if (estado.itens.isEmpty() && naMetade.isEmpty()) {
        Recado(
            titulo = "nenhuma série no acervo",
            detalhe = estado.erro,
            modifier = modifier,
        )
        return
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Cores.fundo)) {
        val colunas = maxOf(
            1,
            ((maxWidth + Sala.vaoEntreCartazes) / (Sala.cartazL + Sala.vaoEntreCartazes)).toInt(),
        )
        LazyVerticalGrid(
            columns = GridCells.Fixed(colunas),
            contentPadding = PaddingValues(
                start = Sala.overscanH,
                end = Sala.overscanH,
                top = Sala.overscanV,
                bottom = Sala.overscanV,
            ),
            horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
            verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
            modifier = Modifier.fillMaxSize(),
        ) {
            if (naMetade.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    RotuloDeSecao("na metade", numero = naMetade.size)
                }
                item(span = { GridItemSpan(maxLineSpan) }) {
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes)) {
                        items(naMetade, key = { it.id }) { item ->
                            Quadro(
                                titulo = item.tituloDaSerie ?: item.title,
                                arte = modelo.arte(item),
                                detalhe = linhaDoEpisodio(item),
                                andado = andadoDoQueComecou(item),
                                aoEscolher = { aoAbrirObra(item.id) },
                                saidaEsquerda = saidaEsquerda,
                            )
                        }
                    }
                }
                item(span = { GridItemSpan(maxLineSpan) }) { Box(Modifier.height(18.dp)) }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                RotuloDeSecao(
                    if (naMetade.isEmpty()) "todas as séries" else "o resto",
                    /// ⚠️ O total do servidor **menos** as que já estão acima — e
                    /// não o total cru, que contaria duas vezes.
                    numero = estado.total?.let { (it - comecadas.size).coerceAtLeast(0) },
                )
            }

            itemsIndexed(
                estado.itens.filter { it.title !in comecadas },
                key = { _, i -> i.id },
            ) { indice, item ->
                Cartaz(
                    titulo = item.title,
                    arte = modelo.capa(item),
                    cor = item.corDominante,
                    detalhe = quantoTem(item),
                    /// ⚠️ Numa série, «andado» é quantos episódios acabaram — e
                    /// não a posição dentro de um arquivo.
                    andado = if (item.quantasObras > 0) {
                        item.quantasVistas.toFloat() / item.quantasObras
                    } else {
                        0f
                    },
                    saidaEsquerda = if (indice % colunas == 0) saidaEsquerda else null,
                    aoEscolher = {
                        if (item.eSerie) aoAbrirSerie(item.id, item.title) else aoAbrirObra(item.id)
                    },
                )
            }
        }
    }
}

/// ⚠️ **Um número só.** `6 temporadas · 63 ep` não cabe sob um cartaz — medido no
/// celular, onde saía `4 temporadas ·` com o resto cortado. Temporadas quando há
/// mais de uma, episódios quando há uma só: «1 temporada» não informa nada.
private fun quantoTem(item: ItemDaBiblioteca): String = when {
    item.quantasTemporadas > 1 -> "${item.quantasTemporadas} temporadas"
    item.quantasObras > 0 -> "${item.quantasObras} episódio" + if (item.quantasObras > 1) "s" else ""
    else -> item.year?.toString().orEmpty()
}

/// `S01E04 · faltam 21min`.
private fun linhaDoEpisodio(item: ItemPraContinuar): String = buildList<String> {
    val t = item.temporada
    val e = item.episodio
    if (t != null && e != null) add("S%02dE%02d".format(t, e)) else if (e != null) add("ep $e")
    val total = item.duracaoEmSegundos ?: 0.0
    val faltam = ((total - (item.ondeParou ?: 0.0)) / 60).toInt()
    if (total > 0 && faltam > 0) add("faltam ${faltam}min")
}.joinToString(" · ")

private fun andadoDoQueComecou(item: ItemPraContinuar): Float {
    val onde = item.ondeParou ?: return 0f
    val total = item.duracaoEmSegundos?.takeIf { it > 0 } ?: return 0f
    return (onde / total).toFloat().coerceIn(0f, 1f)
}
