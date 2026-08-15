package dev.odeon.android.tv.telas

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Cartaz
import dev.odeon.android.tv.ui.FileiraFantasma
import dev.odeon.android.tv.ui.Pilula
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.odeon.android.tv.ui.Focavel
import androidx.compose.ui.unit.sp
import dev.odeon.android.ui.LampadasDaMarquise
import dev.odeon.android.ui.Serifada
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.paravoce.ModeloParaVoce

/// «Para você» — o que o acervo acha que você quer ver.
///
/// ## ⚠️ O motivo é tão visível quanto o cartaz
///
/// «A recomendação com motivo é a tese do projeto numa tela só» (§5 da espec).
/// Se o motivo estiver numa lista separada, a tese exige dois olhares — e aí não
/// está numa tela só. Ele mora colado no cartaz.
@Composable
fun TelaParaVoceDaTv(
    modelo: ModeloParaVoce,
    aoAbrirObra: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    /// ⚠️ Sem `LaunchedEffect` de carga: o `ModeloParaVoce` carrega no `init`.
    /// Chamar de novo aqui refaria o pedido a cada volta à tela.

    when {
        estado.carregando && estado.itens.isEmpty() -> Column(
            modifier.fillMaxSize().padding(top = Sala.overscanV),
            verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
        ) { FileiraFantasma() }

        estado.erro != null -> Recado(
            titulo = "a recomendação não veio",
            detalhe = estado.erro,
            modifier = modifier,
        ) { BotaoDaSala("tentar de novo", { modelo.filtrar(estado.minutos) }, principal = true) }

        estado.itens.isEmpty() -> Recado(
            titulo = "ainda não sei o que te oferecer",
            detalhe = "assista e avalie alguma coisa — é daí que sai a recomendação.",
            modifier = modifier,
        )

        else -> LazyColumn(
            contentPadding = PaddingValues(vertical = Sala.overscanV),
            verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
            modifier = modifier.fillMaxSize(),
        ) {
            item {
                /// ## ⚠️ As lâmpadas da marquise, como na biblioteca
                ///
                /// É a R6 do celular trazida pra cá, e ela não é enfeite: é o que
                /// diz «isto aqui é a entrada do cinema, não uma lista de
                /// arquivos». A biblioteca já as acende; esta tela é a outra
                /// porta da casa e merecia as mesmas luzes.
                Box(Modifier.fillMaxWidth()) {
                    LampadasDaMarquise(Modifier.align(Alignment.TopCenter))
                    Column(
                        Modifier.padding(
                            horizontal = Sala.overscanH,
                            vertical = 26.dp,
                        ),
                    ) {
                        Text(
                            text = "para você",
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = Serifada,
                                fontSize = 48.sp,
                                color = Cores.texto,
                            ),
                        )
                        Spacer(Modifier.height(20.dp))
                        /// ⚠️ **O filtro fica no alto.**
                        ///
                        /// Numa TV o que está embaixo custa apertos, e «tenho uma
                        /// hora e meia» é a pergunta real de quem senta às onze da
                        /// noite. Pergunta que se faz primeiro se responde
                        /// primeiro.
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            TEMPOS.forEach { (rotulo, minutos) ->
                                PilulaDeTempo(
                                    rotulo = rotulo,
                                    ligada = estado.minutos == minutos,
                                    aoEscolher = { modelo.filtrar(minutos) },
                                )
                            }
                        }
                    }
                }
            }

            item {
                Column {
                    RotuloDeSecao(
                        texto = estado.minutos?.let { "até $it minutos" } ?: "o que eu acho",
                        modifier = Modifier.padding(horizontal = Sala.overscanH),
                    )
                    androidx.compose.foundation.lazy.LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
                        contentPadding = PaddingValues(horizontal = Sala.overscanH),
                    ) {
                        items(estado.itens.size) { indice ->
                            val item = estado.itens[indice]
                            /// ⚠️ **O motivo vai junto do cartaz.**
                            ///
                            /// Ele morava numa seção «por que estes» lá embaixo,
                            /// com os quatro primeiros títulos repetidos e o
                            /// motivo ao lado. Isso obriga a pessoa a casar duas
                            /// listas de cabeça: ver o cartaz aqui, procurar o
                            /// nome ali, ler o motivo.
                            ///
                            /// «A recomendação com motivo é a tese do projeto
                            /// numa tela só» (§5 da espec) — e uma tese que exige
                            /// dois olhares pra ser lida não está numa tela só. O
                            /// motivo é **do cartaz**, então mora nele.
                            ///
                            /// ⚠️ O ano cede o lugar. Dos dois, o motivo é o que
                            /// esta tela existe pra dizer; o ano está na ficha, a
                            /// um `OK` de distância.
                            Cartaz(
                                titulo = item.title,
                                arte = modelo.arte(item),
                                detalhe = item.porque ?: item.year?.toString(),
                                linhasDoDetalhe = 3,
                                aoEscolher = { aoAbrirObra(item.id) },
                            )
                        }
                    }
                }
            }

            /// ## O «porquê» de cada recomendação, e por que ele é uma seção
            ///
            /// A web mostra o motivo dentro do cartão. Aqui não cabe: um cartaz
            /// de 200dp com uma frase de motivo por baixo vira uma parede de
            /// texto pequeno, que é o oposto de 10 pés.
            ///
            /// O que a sala faz é o que o `reasons` permite — a primeira razão
            /// vira uma linha só, embaixo, pra os primeiros itens. É menos
            /// informação que a web de propósito: numa TV, a alternativa a
            /// «menos» não é «mais», é «ilegível».
        }
    }
}

/// «Tenho uma hora e meia» — o filtro que faz esta tela.
///
/// ⚠️ Os três valores são os da web, e o `null` é o primeiro de propósito: quem
/// abre esta tela sem pressa não deveria ter de desmarcar nada. Escolher um corte
/// é decidir sozinho que noventa minutos é a pergunta que alguém está fazendo.
private val TEMPOS = listOf<Pair<String, Int?>>(
    "qualquer" to null,
    "até 90min" to 90,
    "até 2h" to 120,
)

/// Uma pílula de tempo, focável — porque ela **faz** alguma coisa.
///
/// ⚠️ Diferente das pílulas de gênero da ficha, que são etiqueta e não levam a
/// lugar nenhum (§53). Aqui apertar troca a lista inteira, então ela é botão, e
/// botão numa TV tem de poder receber foco.
@Composable
private fun PilulaDeTempo(rotulo: String, ligada: Boolean, aoEscolher: () -> Unit) {
    Focavel(
        aoEscolher = aoEscolher,
        forma = RoundedCornerShape(50),
        escalar = false,
        anel = false,
        modifier = Modifier.padding(2.dp),
    ) { focado ->
        Pilula(
            texto = rotulo,
            cor = if (ligada || focado) Cores.destaque else Cores.linha,
            tinta = if (ligada || focado) Cores.destaque else Cores.textoApagado,
        )
    }
}
