package dev.odeon.android.ui.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.ui.Cores

/// A biblioteca.
///
/// ## Ela lista séries, não episódios
///
/// A fonte é `/api/library`, que agrupa. `/api/works` devolveria os 14.657
/// episódios do acervo como cartões iguais — e a web já concluiu o que isso é:
/// «listagem de arquivo e não biblioteca».
@Composable
fun TelaDaBiblioteca(modelo: ModeloDaBiblioteca) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val grade = rememberLazyGridState()

    /// Quando pedir a próxima página.
    ///
    /// `derivedStateOf` porque a rolagem muda o índice a cada quadro, e sem ele
    /// esta condição recomporia a tela inteira sessenta vezes por segundo.
    val chegouNoFim by remember {
        derivedStateOf {
            val ultimoVisivel = grade.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            ultimoVisivel >= grade.layoutInfo.totalItemsCount - 6
        }
    }

    androidx.compose.runtime.LaunchedEffect(chegouNoFim, estado.temMais) {
        if (chegouNoFim && estado.temMais) modelo.maisUmaPagina()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Cabecalho(quantos = estado.itens.size, total = estado.total)

        estado.erro?.let { frase ->
            Text(
                text = frase,
                color = Cores.perigo,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (estado.carregando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cores.destaque)
            }
            return@Column
        }

        LazyVerticalGrid(
            state = grade,
            /// `Adaptive` e não um número fixo de colunas: o mesmo código serve
            /// celular em pé, celular deitado e tablet, e o cartaz mantém a
            /// largura em que ele é legível em vez de esticar.
            columns = GridCells.Adaptive(minSize = 108.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            items(estado.itens, key = { it.id }) { item ->
                Cartaz(item = item, capa = modelo.capa(item))
            }
        }
    }
}

@Composable
private fun Cabecalho(quantos: Int, total: Int?) {
    Column(modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 4.dp)) {
        Text(
            text = "biblioteca",
            style = MaterialTheme.typography.headlineSmall,
            color = Cores.texto,
        )
        /// A contagem só aparece quando existe.
        ///
        /// §24: linha vazia **some**, não vira "—". E enquanto o total é nulo,
        /// escrever "0 de 0" seria afirmar que o acervo está vazio.
        if (total != null) {
            Text(
                text = "$quantos de $total",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
            )
        }
    }
}

/// Um cartão da grade.
///
/// ## Quando não há pôster, ele não finge que há
///
/// **8.598 obras de 17.930 não têm pôster** — 48% do acervo, medido no banco em
/// 04/08/2026. Ou seja, capa faltando não é a exceção: é quase metade da grade.
/// Um retângulo cinza com ícone de imagem quebrada diria "falhou ao carregar",
/// que é mentira — não há o que carregar.
///
/// O que aparece no lugar é o título, sobre a cor da obra quando o servidor a
/// extraiu. É o §18: quando o dado não existe, a tela mostra o que existe.
@Composable
private fun Cartaz(item: ItemDaBiblioteca, capa: String?) {
    val cor = corDaObra(item.corDominante)

    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                /// 2:3 é a proporção de cartaz de cinema, e é a que o servidor
                /// baixa. Qualquer outra recortaria o rosto de alguém.
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(cor ?: Cores.fundoElevado),
            contentAlignment = Alignment.Center,
        ) {
            if (capa != null) {
                AsyncImage(
                    model = capa,
                    /// Nulo de propósito: o título está escrito logo abaixo, em
                    /// texto de verdade. Repeti-lo aqui faria o leitor de tela
                    /// dizer o nome do filme duas vezes seguidas.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Cores.texto,
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        /// A segunda linha: ou quantos episódios, ou o ano. Nunca as duas, e
        /// nunca um traço quando não há nenhuma.
        segundaLinha(item)?.let { texto ->
            Text(
                text = texto,
                style = MaterialTheme.typography.labelSmall,
                color = Cores.textoApagado,
                maxLines = 1,
            )
        }
    }
}

private fun segundaLinha(item: ItemDaBiblioteca): String? = when {
    item.eSerie && item.quantasObras > 0 -> "${item.quantasObras} episódios"
    item.year != null -> item.year.toString()
    else -> null
}

/// A cor que o servidor extraiu do pôster, se extraiu.
///
/// **9.332 obras já têm** `dominant_color`, e isso não custa requisição nenhuma:
/// vem na mesma linha da listagem. É o que faz a grade parecer o acervo mesmo
/// antes de a primeira imagem chegar.
///
/// Formato `#RRGGBB`. Qualquer coisa fora disso vira `null` em vez de estourar —
/// uma cor inválida não pode derrubar a biblioteca.
private fun corDaObra(hex: String?): Color? {
    val limpo = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    val valor = limpo.toLongOrNull(16) ?: return null
    return Color(valor or 0xFF000000L)
}
