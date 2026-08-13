package dev.odeon.android.tv.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Acontecimento
import dev.odeon.android.tv.ui.FileiraFantasma
import dev.odeon.android.tv.ui.Focavel
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.mural.ModeloDoMural
import dev.odeon.android.ui.fazQuantoTempo

/// O mural — o que a casa andou fazendo.
///
/// ## Uma coluna, e não fileiras
///
/// É a segunda tela deste app que não é uma fileira horizontal, e por um motivo
/// oposto ao da biblioteca. Lá era volume; aqui é **ordem**: um feed é uma
/// sequência no tempo, e tempo se lê de cima pra baixo. Uma fileira horizontal
/// de acontecimentos faria "hoje" e "semana passada" ficarem lado a lado, do
/// mesmo tamanho.
///
/// ## ⚠️ O que não tem frase não desenha
///
/// O `Acontecimento.frase` devolve `null` quando o tipo é desconhecido, e o
/// comentário dele é regra: «melhor uma linha a menos que uma linha que diz
/// "alguém fez algo com alguma coisa"». Um servidor mais novo que este app manda
/// tipos que ele não conhece, e a resposta certa é o silêncio.
@Composable
fun TelaDoMuralDaTv(
    modelo: ModeloDoMural,
    aoAbrirObra: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val linhas = estado.mural.acontecimentos.filter { it.frase != null }

    when {
        estado.carregando && linhas.isEmpty() ->
            Column(modifier.fillMaxSize().padding(top = Sala.overscanV)) {
                FileiraFantasma(deitado = true)
            }

        linhas.isEmpty() -> Recado(
            titulo = "o mural está vazio",
            detalhe = "ele enche com o que você e os seus fizerem: terminar um filme, " +
                "pegar uma fita, devolver.",
            modifier = modifier,
        )

        else -> LazyColumn(
            contentPadding = PaddingValues(
                horizontal = Sala.overscanH,
                vertical = Sala.overscanV,
            ),
            modifier = modifier.fillMaxSize(),
        ) {
            item {
                Column {
                    Text(
                        text = "mural",
                        style = MaterialTheme.typography.headlineLarge,
                        color = Cores.texto,
                    )
                    Spacer(Modifier.height(8.dp))
                    /// ⚠️ «3 de 7 vozes» é do §8b, e não é enfeite. O comentário
                    /// do modelo explica: «um mural com um nome só não é uma
                    /// conversa — e a tela diz isso em vez de parecer completa».
                    if (estado.mural.pessoas > 0) {
                        Text(
                            text = "${estado.mural.vozes} de ${estado.mural.pessoas} " +
                                if (estado.mural.vozes == 1) "voz" else "vozes",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cores.textoApagado,
                        )
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            items(linhas) { linha ->
                LinhaDoMural(
                    linha = linha,
                    arte = modelo.arte(linha.poster),
                    aoEscolher = { linha.obraId?.let(aoAbrirObra) },
                )
            }
        }
    }
}

@Composable
private fun LinhaDoMural(
    linha: Acontecimento,
    arte: String?,
    aoEscolher: () -> Unit,
) {
    val forma = RoundedCornerShape(10.dp)
    Focavel(
        aoEscolher = aoEscolher,
        forma = forma,
        /// Uma linha sem obra não leva a lugar nenhum, e por isso o D-pad passa
        /// por cima dela. É o §53: não oferecer o que não vai responder.
        escolhivel = linha.obraId != null,
        modifier = Modifier.padding(vertical = 6.dp),
    ) { focado ->
        Row(
            Modifier
                .background(if (focado) Cores.fundoElevado else Cores.fundo, forma)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .size(width = 72.dp, height = 108.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Cores.fundoElevado),
            ) {
                if (arte != null) {
                    AsyncImage(
                        model = arte,
                        contentDescription = linha.titulo,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (linha.meu) "você" else linha.quem,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (linha.meu) Cores.destaque else Cores.texto,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = linha.frase.orEmpty(),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Cores.textoApagado,
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = linha.titulo,
                    style = MaterialTheme.typography.headlineMedium,
                    color = Cores.texto,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (linha.detalhe != null) {
                    Spacer(Modifier.height(6.dp))
                    /// O `detalhe` vem **pronto** do servidor, e é conteúdo — uma
                    /// nota, um recado de quem devolveu. Diferente da `frase`,
                    /// que é desenho e se monta aqui.
                    Text(
                        text = linha.detalhe!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cores.textoApagado,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(20.dp))
            /// `fazQuantoTempo` vem do `:core`, e é o `quando()` do
            /// `Mural.tsx` da web portado — não uma segunda redação dele. Duas
            /// telas do mesmo produto dizendo «há 3 dias» e «3 dias atrás» sobre
            /// o mesmo acontecimento é o defeito que ninguém testa e todo mundo
            /// vê.
            Text(
                text = fazQuantoTempo(linha.quando).orEmpty(),
                style = MaterialTheme.typography.labelMedium,
                color = Cores.textoApagado,
            )
        }
    }
}
