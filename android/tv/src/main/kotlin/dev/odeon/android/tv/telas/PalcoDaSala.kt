package dev.odeon.android.tv.telas

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.dados.CaixaExposta
import dev.odeon.android.dados.ObraDetalhada
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.TipoDaSala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.corDeHex
import dev.odeon.android.ui.locadora.CaixaEm3D
import dev.odeon.android.ui.locadora.FaceDaCaixa
import dev.odeon.android.ui.locadora.Pose
import dev.odeon.android.ui.locadora.Contracapa
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import dev.odeon.android.tv.ui.BotaoDaSala

/// A caixa na mão — só que na sala não há mão.
///
/// ## A encenação da §5.2, traduzida
///
/// O documento descreve cinco passos, e os cinco estão aqui:
///
/// ```
/// 1. o resto da tela escurece
/// 2. a caixa voa pro centro e cresce
/// 3. ◀ ▶ giram a caixa
/// 4. OK abre
/// 5. voltar devolve à estante
/// ```
///
/// ⚠️ **A caixa é a mesma do celular**, do `:cenario` — não uma versão de TV. O
/// que muda é como se aponta pra ela, e é literalmente o que a §8 previu:
///
/// | do celular | por quê | o que entra |
/// |---|---|---|
/// | arrasto pra girar | não há dedo | `◀ ▶` giram |
/// | háptico com dois pesos | não há mão | nada — a caixa girando **é** a resposta |
/// | paralaxe por acelerômetro | a TV não se inclina | nada, e tudo bem |
///
/// ## ⚠️ Por que isto existe separado do `Palco` do `:cenario`
///
/// O `Palco` de lá é a encenação **do celular**: ele monta o arrasto, o toque na
/// abertura, o háptico e a tela da fita. Nenhum desses gestos existe aqui, e o
/// que sobraria dele depois de tirá-los é a `CaixaEm3D` — que é justamente o que
/// esta tela chama direto.
///
/// A peça atravessou; a encenação não. É a mesma linha da §1: «o objeto se copia,
/// a encenação se traduz.»
///
/// ## ⚠️ O custo, medido antes de escrever isto
///
/// A §10.1 mediu **oito** caixas numa prateleira rolável: 200ms por quadro,
/// contra 42 do cartaz plano. Foi por isso que a estante voltou ao cartaz e a
/// caixa ficou reservada pra cá.
///
/// Aqui é **uma**, parada, sem lista rolando atrás. O número está na §10.9.
@Composable
fun PalcoDaSala(
    caixa: CaixaExposta,
    arte: String?,
    obra: ObraDetalhada?,
    ehVhs: Boolean,
    aoAssistir: () -> Unit,
    aoVerFicha: () -> Unit,
    aoFechar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    /// A pose que o D-pad controla. É o `poseControlada` que a `CaixaEm3D` já
    /// aceita de fora — o comentário dela diz «quando alguém quer controlá-la», e
    /// este é o alguém.
    var pose by remember { mutableStateOf(Pose()) }

    /// ## ⚠️ `OK` abre a caixa — e antes não abria nada
    ///
    /// > «apertar ok na visão 3d não abre a capa e nem tem como eu pegar
    /// > emprestado ou assistir o filme»
    ///
    /// A versão anterior mandava o `OK` direto pro `abrirOMenu` do modelo — que
    /// põe o menu de disco **no estado** e conta com alguém desenhando. No
    /// celular quem desenha é o `MenuDeDVD`; no `:tv` **ninguém desenhava**.
    /// Resultado: a tecla acendia um estado invisível e a caixa ficava parada.
    ///
    /// É o §8b na forma clássica — um toque que não responde —, e ele passou
    /// porque eu liguei a chamada sem seguir o que ela acende até a tela.
    ///
    /// Agora o `OK` faz o que a §5.2 diz que ele faz: **abre a caixa**. E a caixa
    /// aberta entrega o que se pode fazer com ela.
    var aberta by remember { mutableStateOf(false) }

    /// A abertura anima como no celular — 520ms, e o mesmo destino de 118. O
    /// comentário de lá explica o número: é onde a tampa para antes de encostar
    /// na lombada do outro lado.
    val abertura by animateFloatAsState(
        targetValue = if (aberta) 118f else 0f,
        animationSpec = tween(520),
        label = "a caixa abrindo",
    )

    /// ⚠️ A pose **anima** entre um aperto e o outro em vez de saltar.
    ///
    /// No celular o dedo dá continuidade ao giro: a caixa acompanha a mão. Aqui
    /// cada seta é um degrau, e sem animação a caixa pularia de ângulo em ângulo
    /// como um menu — que é o oposto de um objeto sendo virado.
    val giroY by animateFloatAsState(pose.giroY, tween(180), label = "giro da caixa")
    val giroX by animateFloatAsState(pose.giroX, tween(180), label = "inclinação da caixa")

    val foco = remember { FocusRequester() }
    val focoDaAcao = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { foco.requestFocus() } }

    /// «voltar devolve à estante» — o passo 5 da §5.2. E ele é `BackHandler` e
    /// não uma tecla tratada abaixo porque a pilha de voltar deste app é
    /// explícita: são quatro, e estão contadas no `AtividadeDaTv`.
    /// ## ⚠️ `voltar` desfaz **um** passo, e não dois
    ///
    /// Com a caixa aberta ele fecha a caixa; com ela fechada, guarda e devolve à
    /// estante. Antes era só o segundo, e na TCL o primeiro `voltar` parecia não
    /// fazer nada — porque não fazia: a caixa continuava aberta e o palco
    /// continuava lá.
    ///
    /// Desfazer dois passos de uma tecla é o que faz alguém perder o lugar sem
    /// entender por quê. É a mesma pilha explícita que o `AtividadeDaTv` mantém
    /// pro resto do app, uma camada abaixo.
    BackHandler {
        if (aberta) aberta = false else aoFechar()
    }

    Box(
        modifier
            .fillMaxSize()
            /// ⚠️ **Escurece, e não desfoca.**
            ///
            /// A §5.2 pede «escurece e desfoca». O desfoque ficou de fora de
            /// propósito, e é decisão de custo: `Modifier.blur` numa tela de
            /// 1920×1080 é um passe de render inteiro por quadro, e a §10.1
            /// acabou de mostrar que esta TV não tem folga de GPU sobrando.
            ///
            /// O escuro sozinho já separa o palco da loja, que é o trabalho.
            /// Quando houver número dizendo que o desfoque cabe, ele entra.
            .background(Color.Black.copy(alpha = 0.86f))
            .onKeyEvent { evento ->
                if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (evento.key) {
                    /// ⚠️ **12° por aperto**, e o número sai de uma conta: o
                    /// `Pose.TETO` é 42°, então quatro apertos levam de uma ponta
                    /// à outra do curso. Menos que isso vira um cursor de ângulo;
                    /// mais, e a caixa vira de costas antes de a pessoa ver a
                    /// lombada.
                    /// ⚠️ **`livre = true`, e sem ele não se via o verso** —
                    /// relatado pelo dono: «não consigo ver a parte de trás da
                    /// capa 3d».
                    ///
                    /// O `Pose.somando` trava em ±42° por padrão (`TETO`), que é
                    /// o curso de um **arrasto de dedo**: no celular a caixa
                    /// acompanha a mão e volta, e passar disso com o polegar é
                    /// desconfortável. Numa TV a seta não tem esse limite físico,
                    /// e travar em 42° faz a caixa ter um lado só.
                    ///
                    /// Com `livre`, o `daVolta` dá a volta inteira: quatro setas
                    /// mostram a lombada, quinze mostram a contracapa. É a §5.2
                    /// pedindo isso desde sempre — «a contracapa: o verso, quando
                    /// a caixa abre» é uma das oito peças da §0.1, e ela estava
                    /// desenhada e inalcançável.
                    Key.DirectionLeft -> { pose = pose.somando(-12f, 0f, livre = true); true }
                    Key.DirectionRight -> { pose = pose.somando(12f, 0f, livre = true); true }
                    /// A inclinação continua travada: ela é o «olhar de cima»,
                    /// não uma volta, e girar no eixo X passa a mostrar o fundo
                    /// da caixa, que não tem nada.
                    Key.DirectionUp -> { pose = pose.somando(0f, -8f); true }
                    Key.DirectionDown -> { pose = pose.somando(0f, 8f); true }
                    Key.DirectionCenter, Key.Enter -> { aberta = !aberta; true }
                    /// ⚠️ **`voltar` também é tratado aqui, e não só no
                    /// `BackHandler`** — medido na TCL: um dos dois apertos era
                    /// engolido, e o palco ficava aberto com a caixa fechada.
                    ///
                    /// Não descobri **quem** engolia, e é por isso que a saída é
                    /// esta e não um conserto do culpado: o `onKeyEvent` mora no
                    /// nó focado e sobe daí, então ele vê a tecla antes de
                    /// qualquer despachante do sistema. Os dois caminhos levam ao
                    /// mesmo lugar, e o `BackHandler` continua pra quem chegar
                    /// pelo gesto do sistema em vez da tecla.
                    ///
                    /// Redundância de propósito, com o porquê escrito — que é
                    /// diferente de redundância por descuido.
                    Key.Back -> {
                        if (aberta) aberta = false else aoFechar()
                        true
                    }
                    else -> false
                }
            }
            .focusRequester(foco)
            .focusable(),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            /// ⚠️ **380dp, e a primeira versão pôs 520 — a caixa saiu da tela.**
            ///
            /// A §5.2 convida ao exagero, e com razão: «Numa TV isso é melhor que
            /// no celular: a caixa pode ser enorme, e a lombada — que no celular
            /// tem 40dp — passa a ser legível de verdade.» Eu li «enorme» e pus
            /// 520dp. Na TCL a caixa foi cortada em cima **e** embaixo, e levou o
            /// título e a dica pra fora junto.
            ///
            /// O teto não é gosto, é conta. A tela tem 540dp de altura:
            ///
            /// ```
            /// 540  a sala
            /// -54  overscan (27 em cima, 27 embaixo)
            /// -82  título, dica e os dois respiros
            /// ----
            /// 404  o que sobra pra caixa
            /// ```
            ///
            /// 380 deixa 24dp de folga, que é o que impede a caixa de encostar no
            /// título quando o giro a faz ocupar mais altura.
            ///
            /// As três medidas saem do mesmo fator — `380/144 = 2,64` — pelo mesmo
            /// motivo da prateleira: escalar só a largura afinaria a lombada, que
            /// é justamente a peça que se quer ler.
            CaixaEm3D(
                largura = if (ehVhs) 209.dp else 269.dp,
                altura = 380.dp,
                espessura = if (ehVhs) 50.dp else 29.dp,
                poseControlada = Pose(giroY = giroY, giroX = giroX),
                abertura = abertura,
            ) { lado, luz, poseDoQuadro ->
                FaceDaCaixa(
                    lado = lado,
                    luz = luz,
                    pose = poseDoQuadro,
                    titulo = caixa.titulo,
                    arte = arte,
                    cor = corDeHex(caixa.corDominante),
                    ehVhs = ehVhs,
                    ano = caixa.ano,
                    id = caixa.id,
                    temporadas = if (caixa.serie) caixa.temporadas else 0,
                    /// ⚠️ **O verso é a `Contracapa`**, e é o que faz girar valer
                    /// a pena: sinopse, ficha técnica, código de barras e o selo
                    /// da casa, impressos na face de trás.
                    ///
                    /// Sem esta linha a caixa girava e mostrava um retângulo
                    /// escuro — que era metade do «não consigo ver a parte de
                    /// trás»: a outra metade era o giro travado.
                    verso = {
                        Contracapa(
                            titulo = caixa.titulo,
                            obra = obra,
                            ehVhs = ehVhs,
                            cor = corDeHex(caixa.corDominante) ?: Cores.destaque,
                            arte = { arte },
                            /// ⚠️ `null` de propósito: o botão ▸ ASSISTIR da
                            /// contracapa é **de dedo**, e um botão que o D-pad
                            /// não alcança impresso numa capa é pior que botão
                            /// nenhum. Quem oferece o assistir aqui é a fileira
                            /// de ações abaixo da caixa, que é focável.
                            aoAssistir = null,
                        )
                    },
                )
            }

            Spacer(Modifier.height(28.dp))

            Text(
                text = caixa.titulo,
                style = MaterialTheme.typography.headlineSmall,
                color = Cores.texto,
            )

            Spacer(Modifier.height(10.dp))

            /// A dica do celular, traduzida — a §5.2 escreve ela assim, e é a
            /// mesma frase de lá com os gestos trocados:
            ///
            /// > «A dica embaixo, na voz do celular traduzida: `◀ ▶ girar · OK
            /// > abrir`.»
            ///
            /// ⚠️ Esta **fica**, ao contrário da do rolo do player. A diferença é
            /// que a caixa não ensina sozinha que gira: parada, ela é um pôster
            /// grosso. A do rolo saiu porque a lente andando já dizia tudo.
            /// ## ⚠️ A caixa aberta entrega o que fazer com ela
            ///
            /// > «nem tem como eu pegar emprestado ou assistir o filme»
            ///
            /// Fechada, a caixa é um objeto pra olhar e girar. Aberta, ela é uma
            /// caixa **com o disco na mão** — e é aí que as ações fazem sentido,
            /// não antes. É o mesmo desenho do celular: lá o disco só aparece
            /// depois de a tampa abrir, e tocar nele é que toca o filme.
            ///
            /// ⚠️ Elas são `BotaoDaSala` e não os botões impressos da contracapa:
            /// o `▸ ASSISTIR` daquela face é de dedo, e um botão que o D-pad não
            /// alcança impresso numa capa é pior que botão nenhum.
            if (aberta) {
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BotaoDaSala(
                        rotulo = "assistir",
                        principal = true,
                        modifier = Modifier.focusRequester(focoDaAcao),
                        aoEscolher = aoAssistir,
                    )
                    BotaoDaSala("ver a ficha", aoVerFicha)
                    BotaoDaSala("guardar", aoFechar)
                }
                /// O foco desce pras ações assim que a caixa abre — senão a
                /// pessoa abre a caixa e continua com o foco no objeto, sem
                /// nada dizendo que há o que apertar.
                LaunchedEffect(Unit) { runCatching { focoDaAcao.requestFocus() } }
            }

            /// ⚠️ A dica encolhe o respiro quando a fileira de ações existe:
            /// com os dois vãos cheios ela raspava a borda de baixo da TCL, e
            /// texto encostado na borda é o que o overscan existe pra evitar.
            Spacer(Modifier.height(if (aberta) 4.dp else 10.dp))

            Text(
                text = if (aberta) "◀ ▶ GIRAR · OK FECHAR" else "◀ ▶ GIRAR · OK ABRIR",
                style = TipoDaSala.rotulo,
                color = Cores.textoApagado,
                modifier = Modifier.padding(horizontal = Sala.overscanH),
            )
        }
    }
}
