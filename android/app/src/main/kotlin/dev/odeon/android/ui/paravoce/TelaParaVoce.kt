package dev.odeon.android.ui.paravoce

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Recomendacao
import dev.odeon.android.ui.Cores

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
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            listOf<Pair<String, Int?>>(
                "qualquer" to null,
                "até 90min" to 90,
                "até 2h" to 120,
            ).forEach { (rotulo, minutos) ->
                val ativo = estado.minutos == minutos
                TextButton(onClick = { modelo.filtrar(minutos) }) {
                    Text(rotulo, color = if (ativo) Cores.destaque else Cores.textoApagado)
                }
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

        estado.itens.forEach { item ->
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
