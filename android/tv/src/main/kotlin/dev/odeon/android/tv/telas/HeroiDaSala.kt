package dev.odeon.android.tv.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.odeon.android.dados.ItemPraContinuar
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.TipoDaSala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.corDeHex

/// O herói da biblioteca — «a tela **é** o herói» (§5.1).
///
/// ## Por que ele é maior aqui do que no celular
///
/// > «No celular o herói é um cartão de 16:9 dentro de uma coluna; numa TV, a
/// > tela **já tem** o tamanho e a proporção daquele cartão.»
///
/// Ou seja: no celular o herói é um objeto **dentro** da tela; aqui ele é a
/// primeira dobra da tela. Não é o mesmo desenho maior — é o mesmo desenho
/// ocupando o papel que ele sempre quis ocupar.
///
/// ## ⚠️ Ele **rola com o resto**, e chegou a encolher — 13/08/2026
///
/// A §5.1 pedia que ele encolhesse: «descer leva às fileiras; o herói **encolhe**
/// e vira um cabeçalho fino, liberando a tela — é o gesto que uma TV faz melhor
/// que um celular.» Foi implementado assim, e o dono viu na sala e cortou:
///
/// > «que teto o que, ao rolar tudo vai sumindo normal, ignora esse teto feio
/// > aí, deixa como tava»
///
/// ⚠️ **A doc perde para o aparelho.** Um cabeçalho que se agarra ao topo é uma
/// ideia boa no papel e um estorvo a três metros — ele fica no caminho de uma
/// grade que a pessoa está atravessando com a seta. O herói continua sendo a
/// primeira dobra; ele só não insiste em ficar.
@Composable
fun HeroiDaSala(
    item: ItemPraContinuar,
    arte: String?,
    modifier: Modifier = Modifier,
) {
    val corDaObra = corDeHex(item.corDominante) ?: Cores.destaque
    val titulo = item.tituloDaSerie ?: item.title

    Box(
        modifier
            .fillMaxWidth()
            .height(ALTURA_DO_HEROI)
            .clip(RoundedCornerShape(bottomStart = 10.dp, bottomEnd = 10.dp))
            .background(Cores.fundoElevado),
    ) {
        if (arte != null) {
            AsyncImage(
                model = arte,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                /// ⚠️ `TopCenter` e não `Center`: encolhendo, o que se quer
                /// manter é a **cabeça** do quadro. Cortar pelo meio decapita
                /// quem estiver em pé na cena, que é quase todo mundo num pôster
                /// deitado.
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }

        /// O véu. Ele é vertical e não radial porque o texto mora no pé — e a
        /// cor da obra entra por baixo, fraca, que é a §4b da espec: «a cor da
        /// tela sai do pôster».
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            Color.Transparent,
                            Cores.fundo.copy(alpha = 0.55f),
                            Cores.fundo.copy(alpha = 0.92f),
                        ),
                    ),
                ),
        )
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(corDaObra.copy(alpha = 0.16f), Color.Transparent),
                    ),
                ),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(horizontal = Sala.overscanH, vertical = 22.dp)
            ,
        ) {
            /// ⚠️ Serifada — é letreiro, não item de lista. A §2.5 separa os três
            /// papéis da serifa, e este é o segundo: «letreiro — o herói, o tema
            /// da capa da revista».
            Text(
                text = titulo,
                style = MaterialTheme.typography.displayMedium,
                color = Cores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                /// ⚠️ **«faltam X», nunca «continuar de X».** A §2.6 põe isso na
                /// primeira linha da tabela da voz da casa, e marca que o `:tv`
                /// errava as três frases. Esta é a primeira a ser corrigida.
                faltamDoItem(item)?.let { falta ->
                    Text(
                        text = falta,
                        style = MaterialTheme.typography.titleMedium,
                        color = Cores.destaque,
                    )
                    Spacer(Modifier.width(16.dp))
                }
                item.temporada?.let { t ->
                    Text(
                        text = "T$t" + (item.episodio?.let { "E$it" } ?: ""),
                        style = TipoDaSala.rotulo,
                        color = Cores.textoApagado,
                    )
                }
            }

            Spacer(Modifier.height(14.dp))

            /// A barra de progresso. Larga porque a tela é larga — e ela é a
            /// única coisa do herói que diz **quanto** falta em vez de dizer em
            /// palavras.
            Box(
                Modifier
                    .width(560.dp)
                    .height(5.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(Cores.linha),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((item.fracaoVista ?: 0f).coerceIn(0f, 1f))
                        .fillMaxSize()
                        .background(Cores.destaque),
                )
            }
        }
    }
}

/// A altura do herói.
///
/// ⚠️ **245dp**, e o número saiu da tela: a sala tem 540dp, e a «primeira dobra»
/// da §5.1 tem que deixar a primeira fileira de cartazes espiando por baixo —
/// uma dobra que ocupa a tela inteira não convida a descer.
///
/// Eram 306 antes de a escala geral cair 20% (ver `Sala.cartazL`); este desceu
/// junto, senão o herói ficaria desproporcional aos cartazes que encolheram.
private val ALTURA_DO_HEROI = 245.dp

/// `faltam 80min` — a voz da casa, e a conta é a mesma do celular.
///
/// ⚠️ `null` quando não dá pra saber: sem posição, sem duração, ou com menos de
/// um minuto restando. O §18 vale aqui — «faltam 0min» seria afirmar que o filme
/// acabou.
private fun faltamDoItem(item: ItemPraContinuar): String? {
    val onde = item.ondeParou ?: return null
    val total = item.duracaoEmSegundos?.takeIf { it > onde } ?: return null
    val minutos = ((total - onde) / 60).toInt()
    return if (minutos > 0) "faltam ${minutos}min" else null
}
