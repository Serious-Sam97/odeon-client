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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.ObraDaLista
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Recado
import dev.odeon.android.ui.Tipo

/// Os episódios de uma temporada, no celular.
///
/// ⚠️ **Lista, como na sala** — e aqui a razão é ainda mais direta: numa largura
/// de celular, uma grade de quadros 16:9 caberia em duas colunas de miniatura
/// ilegível, ou numa coluna de cartões gigantes. A fileira horizontal põe quadro,
/// número, título e estado na mesma linha de leitura, e cabem seis na tela.
///
/// ⚠️ Falta a sinopse por episódio, e é falta do servidor — `ObraDaLista` não
/// traz `overview`. Ver `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`.
@Composable
fun TelaDaTemporada(
    modelo: ModeloDaSerie,
    numeroDaTemporada: Int,
    aoTocar: (episodioId: String) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val temporada = estado.temporada(numeroDaTemporada)

    if (temporada == null) {
        Recado(
            titulo = "esta temporada não está aqui",
            detalhe = estado.erro ?: "os episódios dela não chegaram",
            aoTentar = modelo::carregar,
            aoVoltar = aoVoltar,
            modifier = modifier,
        )
        return
    }

    LazyColumn(
        modifier.fillMaxSize().background(Cores.fundo),
        contentPadding = PaddingValues(bottom = 28.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        item {
            Column(Modifier.padding(start = 8.dp, end = 20.dp, top = 8.dp, bottom = 14.dp)) {
                TextButton(onClick = aoVoltar) {
                    Text("‹ ${estado.titulo}", color = Cores.destaque, maxLines = 1)
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = temporada.rotulo,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Cores.texto,
                    modifier = Modifier.padding(start = 12.dp),
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    text = buildList {
                        add("${temporada.quantos} episódio" + if (temporada.quantos > 1) "s" else "")
                        if (temporada.vistos > 0) {
                            add("${temporada.vistos} visto" + if (temporada.vistos > 1) "s" else "")
                        }
                    }.joinToString("  ·  "),
                    style = Tipo.rotulo,
                    color = Cores.textoApagado,
                    modifier = Modifier.padding(start = 12.dp),
                )
            }
        }

        items(temporada.episodios, key = { it.id }) { ep ->
            FileiraDoEpisodio(
                episodio = ep,
                arte = modelo.arte(ep.arte),
                aoTocar = { aoTocar(ep.id) },
            )
        }
    }
}

@Composable
private fun FileiraDoEpisodio(
    episodio: ObraDaLista,
    arte: String?,
    aoTocar: () -> Unit,
) {
    val visto = episodio.finished == true
    val andado = andadoDoEpisodio(episodio)

    Row(
        Modifier
            .fillMaxWidth()
            /// ⚠️ 64dp de altura mínima e a fileira **inteira** é o alvo — a
            /// régua de toque da casa. Um quadro de 112dp já passa disso, mas a
            /// mínima protege o caso sem arte.
            .heightIn(min = 64.dp)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .width(128.dp)
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(8.dp)),
        ) {
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
            /// Um **ou** outro — quem terminou não parou no meio.
            if (visto) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.45f)))
                MarcaDeVisto(Modifier.align(Alignment.TopEnd).padding(4.dp))
            } else if (andado > 0f) {
                BarraDeAndado(andado, Modifier.align(Alignment.BottomStart))
            }
        }

        Spacer(Modifier.width(14.dp))

        Column(Modifier.fillMaxWidth()) {
            Row(verticalAlignment = Alignment.Top) {
                episodio.episodio?.let { n ->
                    Text(
                        text = "$n",
                        style = MaterialTheme.typography.titleMedium,
                        color = Cores.destaqueApagado,
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    text = episodio.title,
                    style = MaterialTheme.typography.titleSmall,
                    /// Visto apaga o título — é a marca que se lê de relance.
                    color = if (visto) Cores.textoApagado else Cores.texto,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = subtitulo(episodio, andado),
                style = Tipo.rotulo,
                color = if (andado > 0f && !visto) Cores.destaque else Cores.textoApagado,
            )
            /// ⚠️ A sinopse do episódio — chegou em 18/08/2026. Metade não tem, e
            /// aí a linha **não existe** (§24).
            episodio.overview?.takeIf { it.isNotBlank() }?.let { sinopse ->
                Spacer(Modifier.height(4.dp))
                Text(
                    text = sinopse,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.textoApagado,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

/// `S01E04 · 43min`, e `· faltam 21min` quando há onde voltar.
///
/// ⚠️ Cada pedaço só entra se existir — duração nula não vira `0min` (§18).
private fun subtitulo(ep: ObraDaLista, andado: Float): String = buildList {
    ep.codigo?.let { add(it) }
    ep.duracaoEmSegundos?.takeIf { it > 0 }?.let { add("${(it / 60).toInt()}min") }
    if (andado > 0f && ep.finished != true) {
        val faltam = (((ep.duracaoEmSegundos ?: 0.0) - (ep.ondeParou ?: 0.0)) / 60).toInt()
        if (faltam > 0) add("faltam ${faltam}min")
    }
}.joinToString("  ·  ")

private fun andadoDoEpisodio(ep: ObraDaLista): Float {
    val onde = ep.ondeParou ?: return 0f
    val total = ep.duracaoEmSegundos?.takeIf { it > 0 } ?: return 0f
    return (onde / total).toFloat().coerceIn(0f, 1f)
}
