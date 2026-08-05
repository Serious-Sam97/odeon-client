package dev.odeon.android.ui.locadora

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.odeon.android.dados.CaixaExposta
import dev.odeon.android.dados.Fita
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.corDeHex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt

/// O palco: a caixa **na mão**, fora da estante.
///
/// ## As fases, e elas são as da web
///
/// `na-mão → abrindo → mídia → (fita)`. A web tem uma a mais, `voando`, que é a
/// caixa saindo da prateleira até o centro; aqui o palco entra com um
/// escurecimento e a caixa cresce, que é a mesma ideia sem medir retângulo de
/// origem numa lista que rola.
///
/// ## O que o dedo faz em cada uma
///
/// | gesto | efeito |
/// |---|---|
/// | arrastar | gira a caixa em dois eixos — ±42°, e volta ao repouso ao soltar |
/// | tocar na **abertura** (a metade direita, oposta à lombada) | abre a caixa e entrega a mídia |
/// | tocar na mídia | DVD toca; VHS de outra pessoa passa pela fita |
/// | tocar no fundo | guarda a caixa e fecha o palco |
///
/// A abertura ser a metade **direita** não é escolha de layout: a dobradiça de
/// uma caixa de fita fica do lado da lombada, e a lombada está à esquerda. Abrir
/// pelo lado errado seria abrir pela dobradiça.
@Composable
fun Palco(
    caixa: CaixaExposta,
    arte: String?,
    /// A fita, quando já se sabe onde ela parou. `null` enquanto carrega — e aí
    /// a mídia sai como disco, que é o caso mais comum do acervo.
    fita: Fita?,
    /// A obra inteira, pro **verso** da caixa. `null` enquanto não chegou.
    obra: dev.odeon.android.dados.ObraDetalhada?,
    arteDe: (String?) -> String?,
    rebobinando: Boolean,
    aoFechar: () -> Unit,
    /// Tocar **o filme**, direto. Não é a ficha — ver o botão da `Contracapa`.
    aoAssistir: () -> Unit,
    /// Abrir o menu do disco. Só existe em DVD: «a fita não tem menu, tem
    /// rebobinar».
    aoAbrirOMenu: () -> Unit,
    aoRebobinar: () -> Unit,
) {
    var aberta by remember { mutableStateOf(false) }
    var naFita by remember { mutableStateOf(false) }
    val haptico = LocalHapticFeedback.current
    val cor = corDeHex(caixa.corDominante) ?: Cores.destaque

    /// A tampa abre até 118°: passa dos 90° o bastante pra ficar claro que ela
    /// está aberta, e para antes de encostar na lombada do outro lado.
    val abertura by animateFloatAsState(
        targetValue = if (aberta) 118f else 0f,
        animationSpec = tween(520),
        label = "abrindo a caixa",
    )

    /// A mídia sai **depois** que a tampa passa da metade. Sair junto seria a
    /// mídia atravessando a própria tampa.
    val saida by animateFloatAsState(
        targetValue = if (aberta) 1f else 0f,
        animationSpec = tween(durationMillis = 420, delayMillis = if (aberta) 260 else 0),
        label = "mídia saindo",
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.86f))
            /// O fundo fecha o palco. Sem indicação visual: é o gesto que todo
            /// mundo já tenta primeiro, e escrever «toque fora para fechar» seria
            /// explicar o óbvio ocupando a tela.
            .clickable(onClick = aoFechar),
        contentAlignment = Alignment.Center,
    ) {
        if (naFita && fita != null) {
            TelaDaFita(
                fita = fita,
                titulo = caixa.titulo,
                cor = cor,
                rebobinando = rebobinando,
                aoRebobinar = aoRebobinar,
                aoDeixarPraDepois = { naFita = false },
            )
            return@Box
        }

        /// ## O tamanho da caixa sai da **altura da tela**, e foi a paisagem
        /// que mandou
        ///
        /// Em pé sobra altura, e os 285dp da caixa deixam espaço pro título e
        /// pra dica embaixo. Deitado a tela tem 1080px de altura inteira — e o
        /// screenshot mostrou a caixa empurrando o título pra fora: «Tetris»
        /// cortado ao meio, e a dica de abrir sumida.
        ///
        /// ## E ela cresceu — «aumente o tamanho»
        ///
        /// Os 190dp da primeira versão vinham do nada: era o tamanho que cabia
        /// sem pensar. Numa tela de 405dp de largura, isso é **menos da metade**
        /// da tela pra o objeto que é o assunto inteiro dela.
        ///
        /// Agora a conta olha **as duas dimensões**: 72% da altura ou 88% da
        /// largura, o que vier menor. Em pé manda a altura; deitado manda a
        /// largura, e é o que impede a caixa de virar um cartão de visita no meio
        /// de uma tela larga.
        ///
        /// O teto fixo de 285dp saiu junto: com o verso legível, uma caixa maior
        /// é uma caixa que dá pra ler.
        androidx.compose.foundation.layout.BoxWithConstraints(
            contentAlignment = Alignment.Center,
        ) {
        val proporcao = 285f / 190f
        val alturaDaCaixa = minOf(maxHeight * 0.72f, maxWidth * 0.88f * proporcao)
        val larguraDaCaixa = alturaDaCaixa / proporcao
        val espessuraDaCaixa = alturaDaCaixa * (52f / 285f)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            /// O vão acompanha a caixa: numa tela baixa ele encolhe junto, senão
            /// os 40dp somados de respiro comem o que a caixa acabou de ceder.
            verticalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                CaixaEm3D(
                    largura = larguraDaCaixa,
                    altura = alturaDaCaixa,
                    espessura = espessuraDaCaixa,
                    giravel = true,
                    abertura = abertura,
                    /// A metade direita abre; a esquerda é a dobradiça, e tocar
                    /// nela não faz nada de propósito — um gesto que funciona nos
                    /// dois lados não ensina de que lado a caixa abre.
                    aoTocarNaAbertura = {
                        if (!aberta) {
                            haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                            aberta = true
                        }
                    },
                ) { lado, luz ->
                    FaceDaCaixa(
                        lado = lado,
                        luz = luz,
                        titulo = caixa.titulo,
                        arte = arte,
                        cor = corDeHex(caixa.corDominante),
                        /// O verso, com o que a foto do dono mostra: sinopse,
                        /// cena, ficha técnica, código de barras e o botão que
                        /// **toca o filme**.
                        verso = {
                            Contracapa(
                                titulo = caixa.titulo,
                                obra = obra,
                                ehVhs = fita?.vhs == true,
                                cor = cor,
                                arte = arteDe,
                                aoAssistir = if (obra?.files?.isNotEmpty() == true) aoAssistir else null,
                            )
                        },
                    )
                }

                /// A mídia, saindo por trás da tampa.
                if (saida > 0.01f) {
                    Box(
                        Modifier
                            .graphicsLayer {
                                /// Ela desliza pra direita e cresce um pouco —
                                /// o mesmo movimento de tirar um disco de uma
                                /// caixa, que sai pelo lado da abertura. O
                                /// deslocamento acompanha a caixa: numa caixa
                                /// menor, a mídia sai menos longe.
                                translationX = larguraDaCaixa.toPx() * 0.79f * saida
                                alpha = saida
                                scaleX = 0.86f + 0.14f * saida
                                scaleY = 0.86f + 0.14f * saida
                            }
                            .clickable {
                                haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                /// ## Os três caminhos da mídia, e nenhum leva à ficha
                                ///
                                /// Era o quarto pedido do dono — «clicar em play
                                /// tem que cair no filme, não nos detalhes».
                                ///
                                /// | o que está na mão | pra onde vai |
                                /// |---|---|
                                /// | fita que **outra pessoa** deixou no meio | a tela da fita, e o rebobinar é obrigatório |
                                /// | fita rebobinada, ou sua | **o filme** |
                                /// | disco | **o menu do disco** — que é o que um disco tem, e cujo `Tocar` também cai no filme |
                                when {
                                    fita?.precisaRebobinar == true -> naFita = true
                                    fita?.vhs == true -> aoAssistir()
                                    else -> aoAbrirOMenu()
                                }
                            },
                    ) {
                        val tamanhoDaMidia = larguraDaCaixa * 0.88f
                        if (fita?.vhs == true) {
                            FitaVHS(largura = tamanhoDaMidia, cor = cor, andado = fita.andado)
                        } else {
                            Disco(tamanho = tamanhoDaMidia, cor = cor, pose = Pose())
                        }
                    }
                }
            }

            Text(
                text = caixa.titulo,
                style = MaterialTheme.typography.titleMedium,
                color = Cores.texto,
                textAlign = TextAlign.Center,
            )

            /// A dica só existe **antes** de abrir, e some pra sempre depois.
            ///
            /// §24 na forma mais literal: uma instrução que já foi seguida não
            /// tem o que dizer. E ela é necessária uma vez porque «tocar na
            /// aresta» não é gesto que alguém adivinhe — a web resolve isso com
            /// o cursor mudando de forma, e aqui não há cursor.
            Text(
                text = when {
                    aberta && fita?.precisaRebobinar == true -> "toque na fita"
                    aberta -> "toque no disco para assistir"
                    /// A dica ensina **os dois gestos**, e o giro vem primeiro
                    /// porque é o que mostra que a caixa tem verso.
                    else -> "arraste pra girar · toque na abertura pra abrir"
                },
                style = Tipo.pilula,
                color = Cores.textoApagado,
                textAlign = TextAlign.Center,
            )
        }
        }
    }
}

/// A tela da fita — **o atrito que é a ideia** (§6 da referência).
///
/// ## Rebobinar é obrigatório, e não há «dar play daqui»
///
/// Quando a fita foi **outra pessoa** que deixou no meio, o filme não começa.
/// Aparece o carretel, o ponteiro, o nome de quem deixou assim — e dois botões,
/// dos quais só um leva ao filme.
///
/// A web é explícita: «não há "dar play daqui"». É a única fricção deliberada do
/// produto inteiro, e ela existe porque é o que transforma um acervo compartilhado
/// numa locadora — a fita é um objeto só, e o que a pessoa anterior fez com ele
/// chega até você.
///
/// ## A espera é o conteúdo do gesto
///
/// A duração é proporcional ao que a fita andou — **1 segundo a cada 12 minutos**,
/// entre 2,5s e 10s —, a velocidade **cai** com o que falta, e o rolo da esquerda
/// emagrece enquanto o da direita engorda.
///
/// ⚠️ Em `prefers-reduced-motion` os discos ficam parados **e a espera
/// continua**: é a web outra vez, e o motivo é que tirar a espera tiraria o
/// gesto. O que enjoa é o giro, não o tempo.
@Composable
private fun TelaDaFita(
    fita: Fita,
    titulo: String,
    cor: Color,
    rebobinando: Boolean,
    aoRebobinar: () -> Unit,
    aoDeixarPraDepois: () -> Unit,
) {
    val haptico = LocalHapticFeedback.current

    /// Quanto a fita ainda está andada. Anima de onde estava até zero enquanto
    /// rebobina.
    val andado by animateFloatAsState(
        targetValue = if (rebobinando) 0f else fita.andado,
        animationSpec = tween(duracaoDoRebobinar(fita)),
        label = "rebobinando a fita",
    )

    /// As voltas dos carretéis. Elas não são uma animação de valor: são um
    /// contador que anda **mais devagar conforme a fita esvazia**, que é o que um
    /// motor de verdade faz — o rolo que recebe engorda, e cada volta puxa mais
    /// fita.
    var voltas by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(rebobinando) {
        if (!rebobinando) return@LaunchedEffect
        while (true) {
            delay(16)
            voltas += 0.04f + 0.10f * andado
        }
    }

    /// O tranco do fim de curso — a batida quando a fita chega ao começo.
    LaunchedEffect(rebobinando, andado) {
        if (rebobinando && andado <= 0.001f) {
            haptico.performHapticFeedback(HapticFeedbackType.LongPress)
        }
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(24.dp),
    ) {
        FitaVHS(largura = 260.dp, cor = cor, andado = andado, voltas = voltas)

        Text(
            text = fita.ponteiro,
            style = MaterialTheme.typography.headlineSmall,
            color = Cores.destaque,
        )

        Text(
            text = titulo,
            style = MaterialTheme.typography.titleMedium,
            color = Cores.texto,
            textAlign = TextAlign.Center,
        )

        /// Quem deixou assim. **Dizer o nome é o item inteiro** — uma fita no
        /// minuto 47 sem dono é um defeito; com dono, é uma pessoa que assistiu
        /// antes de você.
        fita.deixadaPor?.let { quem ->
            Text(
                text = "$quem deixou assim",
                style = Tipo.pilula,
                color = Cores.textoApagado,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (rebobinando) {
            Text(
                text = "rebobinando…",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.destaque,
            )
        } else {
            TextButton(onClick = {
                haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                aoRebobinar()
            }) {
                Text("⟲ rebobinar", color = Cores.destaque)
            }
            /// A saída fica a dois centímetros do "não", e é a régua da escassez
            /// (§6): quem não quer esperar volta pro palco sem precisar procurar.
            TextButton(onClick = aoDeixarPraDepois) {
                Text("deixa pra depois", color = Cores.textoApagado)
            }
        }
    }
}

/// Quanto tempo o rebobinar leva, em milissegundos.
///
/// **1 segundo a cada 12 minutos de fita**, com piso de 2,5s e teto de 10s — os
/// três números são da web. O piso existe pra um rebobinar de trinta segundos
/// ainda ser um gesto, e o teto pra um filme de três horas não virar castigo.
internal fun duracaoDoRebobinar(fita: Fita): Int {
    val minutos = fita.posicaoEmSegundos / 60.0
    val segundos = (minutos / 12.0).coerceIn(2.5, 10.0)
    return (segundos * 1000).roundToInt()
}
