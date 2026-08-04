package dev.odeon.android.ui.locadora

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Emprestada
import dev.odeon.android.ui.Cores

/// A locadora.
///
/// ## Ela é 2D, e isso é decisão da espec — não limitação encontrada
///
/// A estante da web é **CSS 3D**: `perspective`, `preserve-3d`, `translateZ`. A
/// §3 diz que o Compose não tem equivalente — `graphicsLayer` faz rotação e
/// câmera, mas não compõe uma hierarquia com filhos em profundidade — e propõe
/// o substituto: **prateleira 2D com a arte das caixas, e o girar da caixa
/// virando um `flip` de duas faces**.
///
/// O que se perde é a profundidade da cena, não a metáfora: a caixa continua
/// sendo objeto, continua tendo frente e verso, e continua saindo da estante
/// quando alguém a leva.
@Composable
fun TelaDaLocadora(modelo: ModeloDaLocadora, aoVoltar: () -> Unit) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    if (estado.carregando && estado.prateleira == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cores.destaque)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        TextButton(onClick = aoVoltar, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("‹ biblioteca", color = Cores.destaque)
        }

        Text("locadora", style = MaterialTheme.typography.headlineSmall, color = Cores.texto)

        estado.erro?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Cores.perigo)
        }

        /// As regras da casa, ditas pelo servidor e não por constante daqui.
        estado.prateleira?.opcoes?.let { opcoes ->
            Text(
                text = regras(opcoes.escassez, opcoes.limitePorPessoa, opcoes.prazoEmDias, estado.prateleira!!.possoPegar),
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
            )
        }

        if (estado.minhas.isNotEmpty()) {
            Secao("comigo") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(estado.minhas, key = { it.id }) { fita ->
                        Caixa(
                            fita = fita,
                            arte = modelo.arte(fita.poster),
                            /// Devolver só existe nas minhas. Nas dos outros o
                            /// gesto seria mexer no empréstimo de alguém — e o
                            /// §11 é explícito sobre isso.
                            aoDevolver = { modelo.devolver(fita.id) },
                            devolvendo = estado.devolvendo == fita.id,
                        )
                    }
                }
            }
        }

        if (estado.dosOutros.isNotEmpty()) {
            Secao("na mão de alguém") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(estado.dosOutros, key = { it.id }) { fita ->
                        Caixa(fita = fita, arte = modelo.arte(fita.poster), aoDevolver = null, devolvendo = false)
                    }
                }
            }
        }

        /// Vazio de verdade tem frase, e não silêncio.
        ///
        /// Aqui o §24 **não** vale: uma locadora sem nenhuma caixa fora é um
        /// estado normal e informativo — "está tudo na estante" é notícia. Uma
        /// tela em branco, não.
        if (estado.minhas.isEmpty() && estado.dosOutros.isEmpty() && estado.erro == null) {
            Text(
                text = "nenhuma caixa fora da estante",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.textoApagado,
            )
        }
    }
}

@Composable
private fun Secao(titulo: String, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = Cores.texto)
        conteudo()
    }
}

/// Uma caixa, com frente e verso.
///
/// ## O `flip` é o que sobrou da estante 3D, e basta
///
/// `graphicsLayer` com `rotationY` e `cameraDistance` gira **um** elemento em
/// perspectiva — é o que o Compose dá, e é exatamente o que a §3 propôs no lugar
/// da cena 3D. A frente é a arte; o verso é o que está escrito na etiqueta:
/// quem levou, quando vence, e o botão de devolver quando é minha.
///
/// A meia-volta troca o que se desenha: passados 90°, a face de trás vira a da
/// frente, e sem a troca o verso apareceria espelhado.
@Composable
private fun Caixa(fita: Emprestada, arte: String?, aoDevolver: (() -> Unit)?, devolvendo: Boolean) {
    var virada by remember { mutableStateOf(false) }
    val giro by animateFloatAsState(
        targetValue = if (virada) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "giro da caixa",
    )

    Column(
        modifier = Modifier.width(140.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .graphicsLayer {
                    rotationY = giro
                    /// Sem isto a rotação é ortográfica e a caixa parece
                    /// achatar em vez de girar. O número é distância de câmera
                    /// em múltiplos da densidade — 12 dá perspectiva sem a
                    /// deformação de grande-angular.
                    cameraDistance = 12f * density
                }
                .clip(RoundedCornerShape(6.dp))
                .background(Cores.fundoElevado)
                .clickable { virada = !virada },
            contentAlignment = Alignment.Center,
        ) {
            if (giro <= 90f) {
                if (arte != null) {
                    AsyncImage(
                        model = arte,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = fita.titulo,
                        style = MaterialTheme.typography.labelMedium,
                        color = Cores.texto,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(8.dp),
                    )
                }
            } else {
                /// O verso é desenhado espelhado de volta: sem este segundo
                /// `rotationY`, o texto da etiqueta sairia invertido.
                Column(
                    modifier = Modifier
                        .graphicsLayer { rotationY = 180f }
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = fita.titulo,
                        style = MaterialTheme.typography.labelMedium,
                        color = Cores.texto,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "com ${fita.quemNome}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cores.textoApagado,
                    )
                    fita.pedidoPorNome?.let {
                        Text(
                            text = "$it pediu de volta",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cores.destaque,
                        )
                    }
                    if (aoDevolver != null) {
                        TextButton(onClick = aoDevolver, enabled = !devolvendo) {
                            Text(if (devolvendo) "devolvendo…" else "devolver", color = Cores.destaque)
                        }
                    }
                }
            }
        }

        Text(
            text = fita.titulo,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/// A frase das regras, montada com o que o servidor mandou.
///
/// Ela existe porque a escassez muda o significado da tela inteira: ligada, uma
/// caixa na mão de alguém é uma caixa que **você não pode pegar**; desligada, é
/// só informação. Dizer qual dos dois está valendo evita a tela mentir por
/// omissão.
private fun regras(escassez: Boolean, limite: Int, prazoDias: Int, possoPegar: Int): String = buildString {
    append(if (escassez) "escassez ligada: uma cópia por caixa" else "escassez desligada: ninguém barra ninguém")
    if (limite > 0) append(" · limite de $limite por pessoa")
    if (prazoDias > 0) append(" · prazo de $prazoDias dias")
    if (escassez) append(" · você ainda pode pegar $possoPegar")
}
