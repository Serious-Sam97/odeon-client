package dev.odeon.android.tv.telas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.odeon.android.dados.ObraDaLista
import dev.odeon.android.tv.ui.BarraDeAndamento
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Focavel
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.saidaPraEsquerda
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.serie.ModeloDaSerie

/// Os episódios de uma temporada, em **lista** — não em grade.
///
/// ## ⚠️ Lista, e é o ponto do desenho
///
/// Uma grade de quadros responde «que imagens existem». Um episódio não se
/// escolhe pela imagem: escolhe-se pelo **número** e pelo que já aconteceu com
/// ele. A fileira horizontal — número, quadro, título, `S01E04 · 43min`, visto
/// ou onde parou — põe as quatro coisas na mesma linha de leitura.
///
/// ⚠️ **Falta a sinopse por episódio**, e é falta do servidor, não desenho.
/// `ObraDaLista` traz título, código, duração, `position_seconds` e `finished`,
/// mas não `overview`. Pedido no `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`; enquanto não vier,
/// a linha não é desenhada (§18 — não se inventa a sinopse a partir do título).
@Composable
fun TelaDaTemporadaDaTv(
    modelo: ModeloDaSerie,
    numeroDaTemporada: Int,
    aoTocar: (episodioId: String) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
    saidaEsquerda: FocusRequester? = null,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val temporada = estado.temporada(numeroDaTemporada)
    val primeiro = remember { FocusRequester() }

    /// A pilha é `temporada ▸ série ▸ biblioteca`, e cada degrau trata a própria
    /// tecla. Ver a regra na `AtividadeDaTv`.
    BackHandler { aoVoltar() }

    /// ⚠️ Abre **no episódio em que se parou**, e não no primeiro da lista.
    /// Numa temporada de 22, começar sempre no piloto é fazer a pessoa descer
    /// vinte fileiras toda vez.
    val indiceDoFoco = remember(temporada) {
        val eps = temporada?.episodios ?: emptyList()
        eps.indexOfFirst { (it.ondeParou ?: 0.0) > 0.0 && it.finished != true }
            .takeIf { it >= 0 }
            ?: eps.indexOfFirst { it.finished != true }.takeIf { it >= 0 }
            ?: 0
    }

    LaunchedEffect(temporada) {
        if (temporada != null) repeat(6) {
            runCatching { primeiro.requestFocus() }
            kotlinx.coroutines.delay(70)
        }
    }

    if (temporada == null) {
        Recado(
            titulo = "esta temporada não está aqui",
            detalhe = estado.erro ?: "os episódios dela não chegaram",
            modifier = modifier,
        ) { BotaoDaSala("voltar à série", aoVoltar, principal = true) }
        return
    }

    Box(modifier.fillMaxSize().background(Cores.fundo)) {
        PanoDeFundo(modelo.arte(temporada.arte), forte = false)

        LazyColumn(
            contentPadding = PaddingValues(
                start = Sala.overscanH,
                end = Sala.overscanH,
                top = Sala.overscanV,
                bottom = Sala.overscanV,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Column(Modifier.padding(bottom = 18.dp)) {
                    /// De onde se veio — e o `‹` diz que o ◀ volta pra lá.
                    BotaoDaSala("‹ ${estado.titulo}", aoVoltar)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        text = temporada.rotulo,
                        style = MaterialTheme.typography.displaySmall,
                        color = Cores.texto,
                    )
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = buildList {
                            add("${temporada.quantos} episódio" + if (temporada.quantos > 1) "s" else "")
                            if (temporada.vistos > 0) {
                                add("${temporada.vistos} visto" + if (temporada.vistos > 1) "s" else "")
                            }
                        }.joinToString("  ·  "),
                        style = MaterialTheme.typography.labelLarge,
                        color = Cores.textoApagado,
                    )
                }
            }

            itemsIndexed(temporada.episodios, key = { _, e -> e.id }) { i, ep ->
                FileiraDoEpisodio(
                    episodio = ep,
                    arte = modelo.arte(ep.arte),
                    aoEscolher = { aoTocar(ep.id) },
                    saidaEsquerda = saidaEsquerda,
                    modifier = if (i == indiceDoFoco) Modifier.focusRequester(primeiro) else Modifier,
                )
            }
        }
    }
}

@Composable
private fun FileiraDoEpisodio(
    episodio: ObraDaLista,
    arte: String?,
    aoEscolher: () -> Unit,
    saidaEsquerda: FocusRequester?,
    modifier: Modifier = Modifier,
) {
    val visto = episodio.finished == true
    val andado = andadoDoEpisodio(episodio)
    val forma = RoundedCornerShape(14.dp)

    Focavel(
        aoEscolher = aoEscolher,
        forma = forma,
        escalar = false,
        anel = false,
        modifier = modifier.fillMaxWidth().saidaPraEsquerda(saidaEsquerda),
    ) { focado ->
        Row(
            Modifier
                .fillMaxWidth()
                .background(if (focado) Cores.fundoElevado else Color.Transparent, forma)
                .then(
                    if (focado) Modifier.border(3.dp, Cores.destaqueQuente, forma) else Modifier,
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            /// ## ⚠️ O quadro da lista é **menor** que o `Sala.quadroL` — 18/08/2026
            ///
            /// Com os 240×135dp da fileira de cartões, a TCL mostrou **duas**
            /// linhas na tela inteira: o dp da sala é desenhado a 2× em 1920, e
            /// cada fileira passava de 300px de altura.
            ///
            /// 150×84dp dão cinco linhas — que é o que faz disto um índice, e
            /// não um folheado. A proporção 16:9 fica, porque ela é o still.
            Box(Modifier.size(width = 150.dp, height = 84.dp).clip(RoundedCornerShape(8.dp))) {
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
                /// ⚠️ Um **ou** outro: quem terminou não parou no meio, e uma
                /// barra cheia embaixo do ✓ diria duas vezes a mesma coisa.
                if (visto) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.45f)),
                    )
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.8f)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("✓", style = MaterialTheme.typography.labelLarge, color = Cores.destaque)
                    }
                } else if (andado > 0f) {
                    BarraDeAndamento(andado, Modifier.align(Alignment.BottomStart))
                }
            }

            Spacer(Modifier.width(24.dp))

            /// O número em serifa, como o letreiro — ele é o que identifica.
            episodio.episodio?.let { n ->
                Text(
                    text = "$n",
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (focado) Cores.destaque else Cores.destaqueApagado,
                )
                Spacer(Modifier.width(20.dp))
            }

            Column(Modifier.fillMaxWidth(0.72f)) {
                Text(
                    text = episodio.title,
                    style = MaterialTheme.typography.titleLarge,
                    /// Visto apaga o título — é a marca que se lê a três metros.
                    color = if (visto) Cores.textoApagado else Cores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = subtitulo(episodio, andado),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (andado > 0f && !visto) Cores.destaque else Cores.textoApagado,
                )
                /// ⚠️ A sinopse chegou em 18/08/2026 — era «falta do servidor,
                /// não desenho», e deixou de ser. Ela responde «qual era esse
                /// mesmo?» sem abrir a ficha, que é o ponto da lista.
                ///
                /// ⚠️ Metade dos episódios não tem, e aí a linha **não existe**
                /// — em vez de um vão reservado pra ela.
                episodio.overview?.takeIf { it.isNotBlank() }?.let { sinopse ->
                    Spacer(Modifier.height(6.dp))
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
}

/// `S01E04 · 43min`, e `· faltam 21min` quando há onde voltar.
///
/// ⚠️ Cada pedaço só entra se existir. Duração nula não vira `0min` (§18): o
/// acervo tem episódios sem arquivo casado, e eles não têm duração nenhuma.
private fun subtitulo(ep: ObraDaLista, andado: Float): String = buildList {
    ep.codigo?.let { add(it) }
    ep.duracaoEmSegundos?.takeIf { it > 0 }?.let { add("${(it / 60).toInt()}min") }
    if (andado > 0f && ep.finished != true) {
        val total = ep.duracaoEmSegundos ?: 0.0
        val faltam = ((total - (ep.ondeParou ?: 0.0)) / 60).toInt()
        if (faltam > 0) add("faltam ${faltam}min")
    }
}.joinToString("  ·  ")

private fun andadoDoEpisodio(ep: ObraDaLista): Float {
    val onde = ep.ondeParou ?: return 0f
    val total = ep.duracaoEmSegundos?.takeIf { it > 0 } ?: return 0f
    return (onde / total).toFloat().coerceIn(0f, 1f)
}
