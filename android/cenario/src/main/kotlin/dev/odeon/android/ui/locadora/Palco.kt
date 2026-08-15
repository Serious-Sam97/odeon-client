package dev.odeon.android.ui.locadora

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import dev.odeon.android.ui.Texto
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.odeon.android.dados.CaixaExposta
import dev.odeon.android.dados.Fita
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.corDeHex
import kotlinx.coroutines.delay
import kotlin.math.roundToInt
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.heightIn

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
/// | arrastar | gira a caixa **livre** — a inércia morre por atrito e ela fica onde parou (07/08) |
/// | tocar na **abertura** (a metade direita, oposta à lombada) | abre a caixa e entrega a mídia |
/// | tocar na mídia | DVD toca; VHS de outra pessoa passa pela fita |
/// | tocar no fundo | guarda a caixa e fecha o palco |
///
/// A abertura ser a metade **direita** não é escolha de layout: a dobradiça de
/// uma caixa de fita fica do lado da lombada, e a lombada está à esquerda. Abrir
/// pelo lado errado seria abrir pela dobradiça.
/// Os corpos de texto que o palco usa **em volta** do objeto.
///
/// ## Por que isto é parâmetro, e a `Contracapa` não tem nada parecido
///
/// A divisa que a T0 usou pra decidir o que vira parâmetro é uma só: **o texto
/// está impresso no objeto, ou está na interface em volta dele?**
///
/// | | exemplo | e então |
/// |---|---|---|
/// | impresso no objeto | a advertência do verso, o «DVD» do selo, o ano na lombada, o nome do filme no pano da cortina | **fica literal** — ele escala com o objeto, e o objeto já sabe de que tamanho é |
/// | interface em volta | o título sob a caixa, a dica de gesto, o «rebobinando…», os botões | **vira parâmetro** — ele escala com o aparelho |
///
/// A advertência de uma capa de VHS tem corpo de bula porque capas de VHS têm
/// bula em corpo de bula, a trinta centímetros ou a três metros. Já a dica
/// `arraste pra girar` é instrução de uso do aparelho — e na sala ela nem diz a
/// mesma coisa: vira `◀ ▶ girar · OK abrir` (§5.2), num corpo que se lê a três
/// metros.
///
/// ⚠️ Sem valor padrão de propósito. Um padrão aqui seria o corpo do celular, e
/// o `:tv` herdaria 16sp sem ninguém perceber — que é exatamente o defeito que o
/// `TipoDaSala` do `:tv` já descreve: «11sp a três metros não é um rótulo
/// discreto, é um rótulo ilegível». Sem padrão, quem chama é obrigado a
/// escolher.
data class LetraDoPalco(
    /// O título da obra, embaixo da caixa.
    val titulo: TextStyle,
    /// A linha de instrução — o único texto do palco que **some pra sempre**
    /// depois de obedecido (§24).
    val dica: TextStyle,
    /// O contador da fita, em destaque. É o único em corpo de letreiro.
    val ponteiro: TextStyle,
    /// «rebobinando…» — o aviso enquanto a fita anda.
    val aviso: TextStyle,
    /// «⟲ rebobinar» e «deixa pra depois».
    val botao: TextStyle,
)

@Composable
fun Palco(
    caixa: CaixaExposta,
    arte: String?,
    /// A fita, quando já se sabe onde ela parou. `null` enquanto carrega — e aí
    /// a mídia sai como disco, que é o caso mais comum do acervo.
    fita: Fita?,
    /// A obra inteira, pro **verso** da caixa. `null` enquanto não chegou.
    obra: dev.odeon.android.dados.ObraDetalhada?,
    /// Fita ou disco, pelo `ultimo_ano_vhs` — decide o **formato do estojo**
    /// antes mesmo de a `fita` chegar do servidor. Quando ela chega, a palavra
    /// final é dela.
    ehVhs: Boolean,
    arteDe: (String?) -> String?,
    rebobinando: Boolean,
    aoFechar: () -> Unit,
    /// Tocar **o filme**, direto. Não é a ficha — ver o botão da `Contracapa`.
    aoAssistir: () -> Unit,
    /// Abrir o menu do disco. Só existe em DVD: «a fita não tem menu, tem
    /// rebobinar».
    aoAbrirOMenu: () -> Unit,
    aoRebobinar: () -> Unit,
    /// Ver [LetraDoPalco] — o que é do aparelho, e não do objeto.
    letra: LetraDoPalco,
) {
    var aberta by remember { mutableStateOf(false) }
    var naFita by remember { mutableStateOf(false) }

    /// ## A mídia **roda enquanto carrega** — 07/08/2026
    ///
    /// > «quando tu clicar para iniciar um filme no modelo 3d deixa o cd rodar
    /// > para mostrar que está carregando»
    ///
    /// Tocar no disco pede o menu ao servidor e tocar na fita abre o player: os
    /// dois são viagem de rede, e até agora o objeto ficava **parado** no
    /// intervalo — o §8b na forma clássica, um toque que não responde. Agora ele
    /// gira, que é o que um aparelho de verdade faz quando engatou a mídia.
    ///
    /// ⚠️ Não precisa de reset: quando o menu abre ou o player entra, o palco
    /// **sai de composição** (ver `PalcoPorCima`, que retorna antes com o menu na
    /// mão), e o estado morre junto. Voltar é sempre voltar com a mídia parada.
    var carregando by remember { mutableStateOf(false) }

    /// As voltas do carretel enquanto carrega. É contador e não animação de
    /// valor: um `tween` teria fim, e carregar não tem duração conhecida — a
    /// mesma razão do rebobinar, e o mesmo passo de 16ms.
    var voltasDoCarretel by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(carregando) {
        if (!carregando) return@LaunchedEffect
        while (true) {
            delay(16)
            voltasDoCarretel += 0.05f
        }
    }
    val haptico = LocalHapticFeedback.current
    val cor = corDeHex(caixa.corDominante) ?: Cores.destaque

    /// A tampa abre até 118°: passa dos 90° o bastante pra ficar claro que ela
    /// está aberta, e para antes de encostar na lombada do outro lado.
    ///
    /// ## O `Animatable` é herança da tampa arrastável, que existiu e saiu
    ///
    /// A tampa chegou a seguir o dedo (por isso o ângulo virou `Animatable`,
    /// que aceita dono trocando no meio) e o dono cortou em 07/08: «abrir
    /// somente apertando na lateral». O `Animatable` ficou — não custa nada e
    /// poupa a volta se a ideia renascer.
    val aberturaAnim = remember { Animatable(0f) }
    LaunchedEffect(aberta) {
        aberturaAnim.animateTo(if (aberta) 118f else 0f, tween(520))
    }
    val abertura = aberturaAnim.value

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
            /// ## O fundo fecha **em degraus** — 07/08/2026
            ///
            /// > «quando eu clicar fora não me deve mandar de volta à locadora,
            /// > mas sim fechar a capa; aí se eu clicar novamente ele guarda.»
            ///
            /// O toque fora desfaz o último passo, como a mão faria: quem está
            /// na tela da fita volta pro palco; quem está com a caixa aberta a
            /// fecha (a mídia entra junto, pelo mesmo `saida`); e só com a caixa
            /// já fechada o segundo toque devolve ela pra estante. Sem indicação
            /// visual — é o gesto que todo mundo tenta primeiro.
            .clickable {
                when {
                    naFita -> naFita = false
                    aberta -> aberta = false
                    else -> aoFechar()
                }
            },
        contentAlignment = Alignment.Center,
    ) {
        if (naFita && fita != null) {
            TelaDaFita(
                letra = letra,
                fita = fita,
                titulo = caixa.titulo,
                cor = cor,
                rebobinando = rebobinando,
                aoRebobinar = aoRebobinar,
                aoDeixarPraDepois = { naFita = false },
            )
            return@Box
        }

        /// A luz da loja: um facho quente e fraco vindo de cima, atrás da caixa.
        ///
        /// Sem ele o palco é um objeto boiando em preto absoluto — e a sombra de
        /// contato que a `CaixaEm3D` ganhou não tem de onde vir. O facho dá a
        /// fonte: é a mesma lâmpada que o `luzNoLado` já supõe («de cima e da
        /// frente») aparecendo no cenário, fraca de propósito pra não competir
        /// com a capa.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(
                brush = Brush.radialGradient(
                    listOf(Cores.destaqueQuente.copy(alpha = 0.09f), Color.Transparent),
                    center = Offset(size.width / 2f, size.height * 0.26f),
                    radius = size.minDimension * 0.85f,
                ),
            )
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
        /// ## E a proporção agora é a do objeto, não a do pôster
        ///
        /// Os 285/190 antigos eram 2:3 — proporção de **cartaz**, não de
        /// estojo. Um keep case de DVD mede 135×190×14mm e um estojo de VHS
        /// 103×187×25mm, e é essa diferença que faz a fita ser reconhecida
        /// antes de qualquer rótulo: mais estreita, quase o dobro da grossura.
        val proporcao = if (ehVhs) 187f / 103f else 190f / 135f
        val alturaDaCaixa = minOf(maxHeight * 0.72f, maxWidth * 0.88f * proporcao)
        val larguraDaCaixa = alturaDaCaixa / proporcao
        val espessuraDaCaixa = larguraDaCaixa * (if (ehVhs) 0.243f else 0.104f)
        /// Capturada aqui porque o `maxWidth` é do `BoxWithConstraints`, e o
        /// trajeto da mídia é medido dentro de um `Box` comum — onde o nome já
        /// não alcança o escopo certo sem receptor explícito.
        val larguraDoPalco = maxWidth

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
                    /// A fita manda quando já chegou; sem ela, o corte por ano.
                    interiorDeDisco = !(fita?.vhs ?: ehVhs),
                    /// A mesma conta do casco da `FaceDaCaixa` — papelão tingido
                    /// na fita, plástico preto no disco. Duplicada aqui porque a
                    /// geometria desenha o lábio e a arte desenha as faces, e
                    /// nenhuma das duas é dona da outra.
                    corDoCasco = if (fita?.vhs ?: ehVhs) {
                        androidx.compose.ui.graphics.lerp(
                            Color.Black,
                            corDeHex(caixa.corDominante) ?: Cores.fundoElevado,
                            0.30f,
                        )
                    } else {
                        Color(0xFF101014)
                    },
                    /// A metade direita abre; a esquerda é a dobradiça, e tocar
                    /// nela não faz nada de propósito — um gesto que funciona nos
                    /// dois lados não ensina de que lado a caixa abre.
                    aoTocarNaAbertura = {
                        if (!aberta) {
                            haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                            aberta = true
                        }
                    },
                    /// ⚠️ **A tampa arrastável existiu e saiu** — 07/08/2026. O
                    /// arrasto que começava na beirada da abertura puxava a
                    /// tampa junto com o dedo; o dono cortou: «abrir a capa
                    /// somente apertando na lateral — girar o item eu posso
                    /// livremente». Com o giro livre, qualquer arrasto é giro, e
                    /// abrir é só o toque de sempre.
                ) { lado, luz, pose ->
                    FaceDaCaixa(
                        lado = lado,
                        luz = luz,
                        pose = pose,
                        titulo = caixa.titulo,
                        arte = arte,
                        cor = corDeHex(caixa.corDominante),
                        ehVhs = ehVhs,
                        ano = caixa.ano,
                        id = caixa.id,
                        /// O verso, com o que a foto do dono mostra: sinopse,
                        /// cena, ficha técnica, código de barras e o botão que
                        /// **toca o filme**.
                        verso = {
                            Contracapa(
                                titulo = caixa.titulo,
                                obra = obra,
                                /// A fita tem a palavra final; sem ela, o corte
                                /// por ano — o rótulo e o estojo nunca discordam.
                                ehVhs = fita?.vhs ?: ehVhs,
                                cor = cor,
                                arte = arteDe,
                                aoAssistir = if (obra?.files?.isNotEmpty() == true) aoAssistir else null,
                            )
                        },
                    )
                }

                /// ## A mídia sai **por cima do interior aberto** — segunda versão
                ///
                /// A primeira desta rodada a pôs **atrás** da caixa, pra ela
                /// emergir pela borda — e a foto reprovou duas vezes: no estojo
                /// de VHS, agora estreito, o trajeto de 0,79 largura empurrava a
                /// fita pra fora da tela; e com a caixa de costas ela saía
                /// escondida atrás da própria tampa. Na frente, com o forro que a
                /// `CaixaEm3D` ganhou, a leitura de «saiu de dentro» vem do
                /// interior escuro visível embaixo dela — e o toque alcança a
                /// mídia inteira, não só a metade que escapou do estojo.
                if (saida > 0.01f) {
                    /// O trajeto é o de sempre (0,79 da largura), **contido pela
                    /// tela**: a mídia para com uma margem antes da borda, em vez
                    /// de confiar que caixa e tela têm sempre a mesma proporção —
                    /// era exatamente essa confiança que cortava a fita ao meio.
                    val tamanhoDaMidia =
                        if (fita?.vhs == true) alturaDaCaixa * 0.58f else larguraDaCaixa * 0.88f
                    val deslocamentoDaMidia = minOf(
                        larguraDaCaixa * 0.79f,
                        larguraDoPalco / 2 - tamanhoDaMidia / 2 - 10.dp,
                    )
                    Box(
                        Modifier
                            .graphicsLayer {
                                translationX = deslocamentoDaMidia.toPx() * saida
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
                                    /// A tela da fita é local — não há o que
                                    /// carregar, e girar aqui seria encenar
                                    /// espera que não existe.
                                    fita?.precisaRebobinar == true -> naFita = true
                                    fita?.vhs == true -> {
                                        carregando = true
                                        aoAssistir()
                                    }
                                    else -> {
                                        carregando = true
                                        aoAbrirOMenu()
                                    }
                                }
                            },
                    ) {
                        /// O disco se mede pela **largura** do estojo (12cm num
                        /// case de 13,5 = 0,88); a fita, pela **altura** — o
                        /// cassete é deitado, e ancorá-lo na largura de um
                        /// estojo que agora é estreito o deixaria anão.
                        if (fita?.vhs == true) {
                            FitaVHS(
                                largura = tamanhoDaMidia,
                                cor = cor,
                                andado = fita.andado,
                                /// Os carretéis giram enquanto o filme carrega.
                                voltas = voltasDoCarretel,
                                titulo = caixa.titulo,
                            )
                        } else {
                            Disco(
                                tamanho = tamanhoDaMidia,
                                cor = cor,
                                pose = Pose(),
                                /// O disco gira enquanto o menu vem do servidor.
                                girando = carregando,
                                /// A mesma arte da capa, impressa no rótulo — a
                                /// referência do dono são os discos da Disney.
                                arte = arte,
                            )
                        }
                    }
                }
            }

            Texto(
                text = caixa.titulo,
                style = letra.titulo,
                color = Cores.texto,
                textAlign = TextAlign.Center,
            )

            /// A dica só existe **antes** de abrir, e some pra sempre depois.
            ///
            /// §24 na forma mais literal: uma instrução que já foi seguida não
            /// tem o que dizer. E ela é necessária uma vez porque «tocar na
            /// aresta» não é gesto que alguém adivinhe — a web resolve isso com
            /// o cursor mudando de forma, e aqui não há cursor.
            Texto(
                text = when {
                    /// Enquanto carrega, a dica **diz** o que a mídia girando
                    /// mostra. Duas linguagens pro mesmo fato, e é de propósito:
                    /// quem não reparar no giro lê, e quem não ler vê girar.
                    carregando -> "carregando…"
                    aberta && fita?.precisaRebobinar == true -> "toque na fita"
                    /// ⚠️ A dica nomeia **o que está na mão** — «toque no disco»
                    /// com uma fita exposta era o §18 em uma linha, e passou
                    /// despercebido enquanto a caixa era igual pros dois.
                    aberta && fita?.vhs == true -> "toque na fita para assistir"
                    aberta -> "toque no disco para assistir"
                    /// A dica ensina **os dois gestos**, e o giro vem primeiro
                    /// porque é o que mostra que a caixa tem verso.
                    else -> "arraste pra girar · toque na abertura pra abrir"
                },
                style = letra.dica,
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
    letra: LetraDoPalco,
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
        FitaVHS(largura = 260.dp, cor = cor, andado = andado, voltas = voltas, titulo = titulo)

        Texto(
            text = fita.ponteiro,
            style = letra.ponteiro,
            color = Cores.destaque,
        )

        Texto(
            text = titulo,
            style = letra.titulo,
            color = Cores.texto,
            textAlign = TextAlign.Center,
        )

        /// Quem deixou assim. **Dizer o nome é o item inteiro** — uma fita no
        /// minuto 47 sem dono é um defeito; com dono, é uma pessoa que assistiu
        /// antes de você.
        fita.deixadaPor?.let { quem ->
            Texto(
                text = "$quem deixou assim",
                style = letra.dica,
                color = Cores.textoApagado,
            )
        }

        Spacer(Modifier.height(8.dp))

        if (rebobinando) {
            Texto(
                text = "rebobinando…",
                style = letra.aviso,
                color = Cores.destaque,
            )
        } else {
            /// ⚠️ **Eram dois `TextButton` do Material**, e viraram os
            /// `BotaoDeTexto` daqui na T0 — não há Material neste módulo, e o
            /// motivo está no `Texto.kt`.
            ///
            /// As medidas do botão (altura mínima de 40dp, 12dp de respiro nos
            /// lados) foram copiadas do que o `TextButton` aplicava, e estão
            /// escritas no [BotaoDeTexto]. O alvo de toque não encolheu.
            BotaoDeTexto(
                texto = "⟲ rebobinar",
                cor = Cores.destaque,
                estilo = letra.botao,
                aoTocar = {
                    haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                    aoRebobinar()
                },
            )
            /// A saída fica a dois centímetros do "não", e é a régua da escassez
            /// (§6): quem não quer esperar volta pro palco sem precisar procurar.
            BotaoDeTexto(
                texto = "deixa pra depois",
                cor = Cores.textoApagado,
                estilo = letra.botao,
                aoTocar = aoDeixarPraDepois,
            )
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

/// O `TextButton` deste módulo — ver o `Texto.kt` pro porquê de ele existir.
///
/// ⚠️ Os dois números não são escolha: são o que o `TextButton` do Material
/// aplicava nestas duas chamadas antes da mudança de módulo. `ButtonDefaults`
/// dá 12dp de respiro horizontal, e a altura mínima de alvo de toque é 40dp em
/// ambos os Material. Copiados pra o alvo não encolher — botão que encolhe sem
/// ninguém pedir é o tipo de regressão que passa por um build verde.
@Composable
private fun BotaoDeTexto(
    texto: String,
    cor: Color,
    estilo: TextStyle,
    aoTocar: () -> Unit,
) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = aoTocar)
            .heightIn(min = 40.dp)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Texto(text = texto, color = cor, style = estilo)
    }
}
