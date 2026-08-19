package dev.odeon.android.tv.player

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.net.toUri
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.google.common.util.concurrent.MoreExecutors
import dev.odeon.android.tv.midia.ServicoDeMidiaDaTv
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Pilula
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.ManterATelaAcesa
import dev.odeon.android.ui.player.ModeloDoPlayer
import dev.odeon.android.ui.player.escolherAudio
import dev.odeon.android.ui.player.escolherLegenda
import dev.odeon.android.ui.player.frasePraFalha
import dev.odeon.android.ui.player.rotuloDaFaixa
import dev.odeon.android.ui.player.tempoDeFilme
import dev.odeon.android.ui.player.tempoDeSessao
import kotlinx.coroutines.delay
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import dev.odeon.android.ui.player.CortinaDeAbertura
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.clip
import dev.odeon.android.ui.player.Tira
import dev.odeon.android.ui.player.BotaoDeSalto
import dev.odeon.android.ui.player.BotaoDeTocar
import androidx.compose.runtime.mutableIntStateOf
import dev.odeon.android.tv.ui.Focavel
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.heightIn
import dev.odeon.android.tv.ui.TipoDaSala
import androidx.compose.ui.focus.onFocusChanged

/// O player da sala.
///
/// ## O controle remoto é a tela inteira, e é a diferença que reescreve tudo
///
/// No celular, cada botão do player é um alvo de toque num lugar da tela. Aqui
/// não há alvos: há **um** foco, e cinco teclas. A gramática que todo app de TV
/// converge — e que este segue — é:
///
/// | tecla | com o cromo escondido | com o cromo aberto |
/// |---|---|---|
/// | ▶❚❚ / centro | mostra o cromo **e** pausa | age no que está focado |
/// | ◀ ▶ | pula 10s, sem abrir o cromo | anda no que está focado |
/// | ▲ ▼ | abre o cromo | anda entre transporte e faixas |
/// | voltar | sai do filme | fecha o cromo |
///
/// ⚠️ A linha das setas ◀ ▶ é a mais importante e a mais fácil de errar: pular
/// 10s **sem** acender a interface é o que faz um player de TV parecer rápido.
/// Se cada toque na seta acendesse a barra inteira por cima do filme, procurar
/// uma cena seria assistir à própria interface.
///
/// ## Ele reusa o `ModeloDoPlayer` inteiro — 914 linhas do `:core`
///
/// Nada do que é difícil aqui foi reescrito: o plano de reprodução, a sessão de
/// HLS e o `deslocamentoMs` que ela obriga, a marca de progresso, a troca de
/// faixa que refaz a sessão, o «perseguir» de outro aparelho, a recuperação de
/// erro. Tudo isso é do modelo, e ele não sabe que está numa TV.
///
/// O que este arquivo escreve é o que muda: como se aponta, e o que se vê.
@Composable
fun TelaDoPlayerDaTv(
    modelo: ModeloDoPlayer,
    ondeParou: Double,
    /// A legenda já escolhida ao abrir. Só a bancada de medição preenche isto —
    /// quem assiste escolhe na modal, e a escolha começa vazia como sempre.
    legendaInicial: String? = null,
    aoSair: () -> Unit,
    /// ⚠️ **O arquivo acabou** — e até este parâmetro existir, acabar não era um
    /// acontecimento nesta tela: era a ausência de qualquer um.
    ///
    /// Visto na TCL, com o Batman de um canal: o filme chega ao fim, a tela fica
    /// **preta**, o cronômetro marca `faltam 0:00` e o botão vira play. Nada
    /// mais. Da poltrona isso é indistinguível de um app travado, e foi assim
    /// que o dono descreveu — «o app morreu». Não morreu: terminou, e não tinha
    /// para onde ir.
    ///
    /// Pior que a tela preta: apertar play ali **recomeça o mesmo filme do
    /// zero**, que é a outra metade do relato — «mesmo filme não mudou».
    aoAcabar: () -> Unit = {},
    /// ⚠️ **A tela voltou pra frente** — com a posição do filme onde ela parou.
    ///
    /// Existe por causa de uma medida: num canal, o `A Hora do Rush` entrou em
    /// 43s, tocou **sete segundos**, e a TV dormiu. Quarenta minutos depois ela
    /// acordou em 0:50 — porque a reprodução pausa quando o painel apaga e
    /// continua de onde parou.
    ///
    /// «Continuar de onde parou» é certo pra um filme do acervo e errado pra um
    /// canal: ao vivo que acorda quarenta minutos atrás não é ao vivo. Quem
    /// decide o que fazer com isso é quem sabe se há um canal atrás — daí a
    /// posição subir em vez de a regra descer.
    aoVoltarAoFrente: (posicaoDoFilmeMs: Long) -> Unit = {},
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    DisposableEffect(Unit) {
        modelo.garantirPreparado(ondeParou)
        onDispose { modelo.encerrar() }
    }

    val player = lembrarControle(
        url = estado.url,
        arquivoId = estado.arquivoId,
        eHls = estado.eHls,
        comecarEm = estado.comecarEm,
        legendas = estado.legendas,
        titulo = estado.titulo,
        capaUrl = estado.capaUrl,
    )

    if (estado.erro != null) {
        Recado(
            titulo = "o filme não abriu",
            detalhe = estado.erro,
            modifier = Modifier.background(Cores.fundo),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BotaoDaSala("tentar de novo", modelo::tentarDeNovo, principal = true)
                BotaoDaSala("voltar", aoSair)
            }
        }
        return
    }

    /// ## A cortina abre a sessão — T2
    ///
    /// A §6.1 chama isto de «a peça de maior retorno do documento», e o motivo é
    /// que numa TV, no escuro, com a tela inteira, é a coisa mais próxima de uma
    /// sala de cinema que este produto vai chegar.
    ///
    /// ⚠️ Ela **veste uma espera que já existe** — plano, URL, buffer até o
    /// primeiro quadro — que até agora mostrava preto. Não soma tempo; ocupa o
    /// que já se perdia. E ela tem um **piso** de duração, descoberto no celular:
    /// em Direct Play local o `READY` chega tão rápido que cortar ali fazia o
    /// dono dizer, duas vezes, que as luzes não existiam. O `Cortina.kt` já
    /// resolve os dois, e é por isso que ela veio inteira do `:cenario`.
    var cortinaAberta by rememberSaveable { mutableStateOf(false) }

    /// ⚠️ **`ON_RESUME`, e não um `LaunchedEffect`.**
    ///
    /// A composição não é recriada quando a TV dorme e acorda — foi o que o
    /// aparelho mostrou: `onde` é um `remember` simples, o processo seguiu vivo o
    /// tempo todo, e nada em Compose voltou a rodar. O único aviso de «a pessoa
    /// está de volta» vem do ciclo de vida.
    ///
    /// ## ⚠️ Só vale o `ON_RESUME` que vem **depois** de um `ON_PAUSE`
    ///
    /// `addObserver` despacha na hora os eventos até o estado atual: registrar o
    /// observador com a tela já em pé dispara um `ON_RESUME` sintético
    /// imediatamente. Sem a trava isso vira laço — cada re-sintonia recria o
    /// player, o player re-registra o observador, o observador dispara, e
    /// re-sintoniza de novo.
    ///
    /// Foi visto acontecer: **três reconstruções** numa única volta ao app, e as
    /// duas primeiras lendo `pos=0s` porque o player recém-criado ainda não tinha
    /// posição. Com `pos=0` a folga de dois minutos nunca protegeria ninguém —
    /// qualquer volta pareceria um atraso enorme.
    ///
    /// Com a trava, o disparo sintético não conta (não houve pausa antes dele) e
    /// a posição lida é a real, do player que estava tocando.
    val donoDoCiclo = androidx.lifecycle.compose.LocalLifecycleOwner.current
    var esteveNoFundo by remember { mutableStateOf(false) }
    DisposableEffect(donoDoCiclo, player) {
        val observador = androidx.lifecycle.LifecycleEventObserver { _, evento ->
            when (evento) {
                androidx.lifecycle.Lifecycle.Event.ON_PAUSE -> esteveNoFundo = true
                androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                    val p = player
                    if (esteveNoFundo && p != null) {
                        esteveNoFundo = false
                        aoVoltarAoFrente(tempoDeFilme(p.currentPosition, estado.deslocamentoMs))
                    }
                }
                else -> Unit
            }
        }
        donoDoCiclo.lifecycle.addObserver(observador)
        onDispose { donoDoCiclo.lifecycle.removeObserver(observador) }
    }

    /// ⚠️ **O fim do arquivo é ouvido aqui, e não no `Cromo`.**
    ///
    /// O `Cromo` é a barra de baixo — ele some sozinho depois de alguns segundos
    /// parado, e some inteiro quando o menu de faixas abre. Um ouvinte que vive
    /// junto dele deixaria de existir exatamente na situação mais comum de todas:
    /// alguém assistindo sem tocar no controle até o filme acabar.
    /// ⚠️ `estaTocando` mora **aqui**, e não no `Cromo`. O cromo é a barra de
    /// baixo, que some sozinha depois de alguns segundos parado — e a tela tem de
    /// continuar acesa exatamente quando ninguém está tocando no controle, que é
    /// o caso em que o sistema mais quer dormir.
    var estaTocando by remember { mutableStateOf(false) }
    ManterATelaAcesa(estaTocando)

    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        estaTocando = p.isPlaying
        val ouvinte = object : Player.Listener {
            override fun onPlaybackStateChanged(estadoDoPlayer: Int) {
                if (estadoDoPlayer == Player.STATE_ENDED) aoAcabar()
            }

            override fun onIsPlayingChanged(tocando: Boolean) {
                estaTocando = tocando
            }
        }
        p.addListener(ouvinte)
        onDispose { p.removeListener(ouvinte) }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (player != null) {
            Superficie(player)
        }

        /// ⚠️ **O cromo não nasce enquanto o pano está no ar**, e é lição do
        /// celular: desenhar os dois pôs título, relógio e «faltam 1:37:48»
        /// flutuando por cima de uma cortina fechada — o cromo anunciando um
        /// filme que ainda não tinha começado.
        ///
        /// Nada fica inalcançável no intervalo: a cortina tem o próprio
        /// pula-tudo, e aqui ele responde ao D-pad além do toque.
        if (!cortinaAberta) {
            CortinaDeAbertura(
                titulo = estado.titulo,
                pronto = player?.playbackState == Player.STATE_READY,
                aoTerminar = { cortinaAberta = true },
            )
            return@Box
        }

        Cromo(
            modelo = modelo,
            player = player,
            legendaInicial = legendaInicial,
            aoSair = aoSair,
        )
    }
}

/// A superfície de vídeo.
///
/// ⚠️ **`PlayerView` com os controles do Media3 desligados.** Quem desenha pixel
/// de vídeo é um `SurfaceView` do sistema, e disso o Compose não tem
/// equivalente — daí o `AndroidView`. Mas os controles que vêm de fábrica são de
/// toque: eles roubam o foco do D-pad e desenham uma barra que não é a desta
/// casa. `useController = false` os apaga e deixa só a superfície.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun Superficie(player: Player) {
    AndroidView(
        factory = { contexto ->
            androidx.media3.ui.PlayerView(contexto).apply {
                useController = false
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT,
                )
                /// Preto e não a cor da casa: o que sobra do vídeo numa tela
                /// 16:9 é a barra de um filme em 2.39:1, e barra de cinema é
                /// preta. O dourado ali seria uma moldura.
                setBackgroundColor(android.graphics.Color.BLACK)
                setShutterBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { it.player = player },
        modifier = Modifier.fillMaxSize(),
    )
}

/// O cromo: relógio, transporte e faixas — tudo que some quando ninguém mexe.
@Composable
private fun Cromo(
    modelo: ModeloDoPlayer,
    player: Player?,
    /// Ver o parâmetro de mesmo nome em [TelaDoPlayerDaTv]: a escolha mora aqui
    /// dentro, então é aqui que ela precisa começar preenchida.
    legendaInicial: String?,
    aoSair: () -> Unit,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val foco = remember { FocusRequester() }

    var aberto by remember { mutableStateOf(true) }
    var menuDeFaixas by remember { mutableStateOf(false) }
    var legendaEscolhida by remember { mutableStateOf<String?>(legendaInicial) }
    var posicao by remember { mutableLongStateOf(0L) }
    var duracao by remember { mutableLongStateOf(0L) }
    var tocando by remember { mutableStateOf(true) }

    /// ## A viagem pelo rolo — pedido do dono
    ///
    /// > «quando eu vejo os botões de play e pause eu posso usar o dpad pra cima
    /// > e ir na timeline, usando o dpad dela me fazendo de um jeito rápido
    /// > viajar no rolo do filme até onde eu quiser pra frente ou trás, daí se eu
    /// > apertar o ok do controle no dpad o filme vai pro ponto que escolhi»
    ///
    /// ⚠️ **`null` quer dizer «não estou viajando»**, e isso não é economia de
    /// variável: é o que separa duas perguntas que a tela precisa responder ao
    /// mesmo tempo — *onde o filme está* (`posicao`) e *onde eu estou olhando*
    /// (`alvoDaViagem`). Com um número só, sair da viagem sem confirmar não teria
    /// pra onde voltar.
    ///
    /// É a mesma ideia que o `Trilho` usa pra separar foco de destino: percorrer
    /// não é escolher.
    var alvoDaViagem by remember { mutableStateOf<Long?>(null) }


    /// O relógio da tela. `Player` não emite «andou um segundo» — ele emite
    /// mudança de estado —, então alguém tem que perguntar.
    ///
    /// ⚠️ **`posicao` é tempo de filme, não tempo de sessão**, e a conversão
    /// acontece aqui, na única linha que lê o player. Em HLS a sessão é aberta
    /// com `start=N`, então o segundo zero do player é o segundo N do filme;
    /// desenhar a barra contra o número cru mostraria o filme sempre no começo.
    LaunchedEffect(player) {
        while (true) {
            posicao = tempoDeFilme(player?.currentPosition ?: 0L, estado.deslocamentoMs)
            modelo.anotarPosicao(posicao)
            /// A conhecida ganha da do player: em HLS de transcodificação o
            /// player só enxerga os segmentos já gerados, e a linha do tempo
            /// desenhada contra esse número **anda pra trás**.
            duracao = estado.duracaoConhecidaMs.takeIf { it > 0 }
                ?: player?.duration?.takeIf { it > 0 } ?: 0L
            tocando = player?.isPlaying ?: false
            delay(200)
        }
    }

    /// O batimento do progresso — 10s, o mesmo do celular. Perder até dez
    /// segundos de filme é imperceptível; escrever no Postgres de casa a cada
    /// respiração não é.
    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            val p = player ?: continue
            if (p.isPlaying) modelo.marcar(p.currentPosition, p.duration, "progress")
        }
    }

    /// O cromo se apaga sozinho. 4s é o que dá pra ler o relógio e decidir, sem
    /// deixar uma barra por cima do filme enquanto ninguém pediu.
    ///
    /// ⚠️ Ele **não** se apaga com o filme pausado: pausado, a interface é a
    /// única coisa que responde, e escondê-la deixaria a pessoa diante de um
    /// quadro parado sem saber o que apertar.
    /// ⚠️ `alvoDaViagem` entra na conta: o cromo apagando no meio de uma viagem
    /// levaria embora a própria película que se está navegando.
    LaunchedEffect(aberto, tocando, menuDeFaixas, alvoDaViagem) {
        if (aberto && tocando && !menuDeFaixas && alvoDaViagem == null) {
            delay(4_000)
            aberto = false
        }
    }

    LaunchedEffect(aberto) {
        if (aberto) runCatching { foco.requestFocus() }
    }

    /// ## ⚠️ As faixas são reaplicadas em `onTracksChanged`, e só ali
    ///
    /// Um `TrackSelectionOverride` aponta pra um `TrackGroup` **daquela** fonte,
    /// e a fonte acabou de trocar. Tentar antes — logo depois do `prepare`, num
    /// `LaunchedEffect` — não acha grupo nenhum e falha em silêncio, que é o
    /// pior jeito de falhar. É a lição que o `:app` já pagou.
    var jaTentouLevantar by remember { mutableStateOf(false) }
    val legendaAgora by rememberUpdatedState(legendaEscolhida)
    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val ouvinte = object : Player.Listener {
            override fun onPlayerError(erro: androidx.media3.common.PlaybackException) {
                /// Uma tentativa calada primeiro: `prepare()` é a recuperação
                /// que o Media3 documenta, e pra uma piscada de rede ela devolve
                /// o filme sem que ninguém veja mais que um engasgo. **Mas só
                /// uma** — insistir numa sessão de HLS morta é um laço que
                /// mostra tela preta.
                if (!jaTentouLevantar) {
                    jaTentouLevantar = true
                    p.prepare()
                    return
                }
                modelo.falhouTocando(
                    mensagem = frasePraFalha(erro, eHls = estado.eHls),
                    posicaoDoFilmeMs = tempoDeFilme(p.currentPosition, estado.deslocamentoMs),
                )
            }

            override fun onPlaybackStateChanged(estadoDoPlayer: Int) {
                if (estadoDoPlayer == Player.STATE_READY) jaTentouLevantar = false
            }

            override fun onTracksChanged(faixas: androidx.media3.common.Tracks) {
                legendaAgora?.let { escolherLegenda(p, it) }
                val audio = estado.faixaDeAudioEmUso
                if (!estado.eHls && audio != null && audio > 0) escolherAudio(p, audio)
            }
        }
        p.addListener(ouvinte)
        onDispose { p.removeListener(ouvinte) }
    }

    /// A marca do fim, e o fim do filme.
    ///
    /// Numa TV não há janelinha nem «o app foi pro fundo» que valha: sair desta
    /// tela é parar de assistir. O `stop` antes do `clearMediaItems` é o que
    /// solta o **decodificador de hardware** — e numa TCL ele é um só, então
    /// quem o segura impede o próximo filme de abrir.
    DisposableEffect(player) {
        onDispose {
            val p = player ?: return@onDispose
            modelo.marcar(p.currentPosition, p.duration, "abandon")
            p.stop()
            p.clearMediaItems()
        }
    }

    /// Perseguir o outro aparelho — o que o barramento existe pra fazer.
    /// «Parou na TV, continua no ônibus» vale ao contrário também, e é aqui que
    /// a sala descobre que o celular andou com o mesmo filme.
    val perseguir by modelo.perseguir.collectAsStateWithLifecycle()
    LaunchedEffect(perseguir) {
        val alvo = perseguir ?: return@LaunchedEffect
        val p = player ?: return@LaunchedEffect
        p.seekTo(tempoDeSessao((alvo * 1000).toLong(), estado.deslocamentoMs))
        modelo.jaPerseguiu()
    }

    BackHandler(enabled = true) {
        when {
            menuDeFaixas -> menuDeFaixas = false
            aberto -> aberto = false
            else -> aoSair()
        }
    }

    /// ## A película aparece ao buscar, e some sozinha — pedido do dono
    ///
    /// > «dentro do filme ao usar o dpad do controle para esquerda ou direita a
    /// > timeline de film roll deve aparecer para você acompanhar»
    ///
    /// ⚠️ E ela aparece **sozinha**, sem o resto do cromo. É a diferença que faz
    /// a regra antiga continuar valendo: «◀ ▶ correm a película sem acender o
    /// cromo» (§6.2) queria dizer «sem tapar o filme com botões», não «sem
    /// mostrar onde você está». Mostrar a película **é** acompanhar.
    var espiando by remember { mutableStateOf(false) }

    /// ⚠️ **O contador existe porque a primeira versão nunca apagava a
    /// película** — visto na TCL.
    ///
    /// Ela media o tempo com `LaunchedEffect(espiando, posicao)`, e `posicao`
    /// muda a cada 200ms enquanto o filme corre. O efeito reiniciava antes de
    /// completar os 2s, então a espiada durava **para sempre** — e o «sem acender
    /// o cromo» virava «com uma película permanente por cima do filme».
    ///
    /// Contando saltos em vez de posições, o relógio só reinicia quando a pessoa
    /// realmente aperta a seta de novo. Que é o que «2s a partir do último
    /// salto» sempre quis dizer.
    var saltos by remember { mutableIntStateOf(0) }
    LaunchedEffect(saltos) {
        if (saltos > 0) {
            espiando = true
            delay(2_000)
            espiando = false
        }
    }

    fun pular(segundos: Long) {
        saltos++
        val p = player ?: return
        val alvo = (posicao + segundos * 1000).coerceAtLeast(0L)
        /// ⚠️ Em HLS, pular pra fora do que a sessão cobre não é um `seekTo` —
        /// é uma sessão nova. O modelo sabe disso; a tela só pergunta.
        val antesDoComeco = alvo < estado.deslocamentoMs
        if (estado.eHls && antesDoComeco) {
            modelo.reabrirEm(alvo)
        } else {
            p.seekTo(tempoDeSessao(alvo, estado.deslocamentoMs))
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            /// ## A caixa inteira escuta o D-pad, e é o que faz o filme
            /// responder com o cromo apagado
            ///
            /// `onKeyEvent` **antes** de `focusable` na cadeia: eventos de tecla
            /// sobem do nó focado pra fora, então este bloco precisa ser o pai.
            /// Escrito na ordem inversa — como quase todo exemplo escreve — ele
            /// simplesmente não roda.
            .onKeyEvent { evento ->
                if (evento.type != KeyEventType.KeyUp) return@onKeyEvent false
                when (evento.key) {
                    /// ## ⚠️ ◀▶ só buscam com o cromo **apagado** — defeito
                    /// relatado pelo dono
                    ///
                    /// > «só de ficar em cima do botão de voltar 10s ou avançar
                    /// > 30s o filme já muda sem eu clicar»
                    ///
                    /// E era isto: as duas linhas devolviam `true`
                    /// **incondicionalmente**. Com o cromo aberto, andar entre os
                    /// botões é ◀▶ — então cada passo do foco buscava dez
                    /// segundos, e o botão nem precisava ser apertado.
                    ///
                    /// Pior: como o evento era consumido, o foco **também** não
                    /// andava. O que parecia «o filme muda sozinho» era, na
                    /// verdade, a fileira de botões inteira sequestrada.
                    ///
                    /// Devolvendo `false` com o cromo aberto, o D-pad volta a ser
                    /// do foco, e a busca por seta continua existindo onde ela
                    /// foi desenhada pra existir: sobre o filme, sem interface —
                    /// «a regra mais importante do player de TV».
                    Key.DirectionLeft -> if (aberto) false else { pular(-10); true }
                    Key.DirectionRight -> if (aberto) false else { pular(10); true }
                    Key.MediaRewind -> { pular(-30); true }
                    Key.MediaFastForward -> { pular(30); true }
                    Key.MediaPlay -> { player?.play(); aberto = true; true }
                    Key.MediaPause -> { player?.pause(); aberto = true; true }
                    Key.MediaPlayPause, Key.DirectionCenter, Key.Enter -> {
                        if (!aberto) {
                            aberto = true
                            true
                        } else {
                            false
                        }
                    }
                    Key.DirectionUp, Key.DirectionDown -> {
                        if (!aberto) { aberto = true; true } else false
                    }
                    else -> false
                }
            }
            .focusable(),
    ) {
        /// ## A película sozinha, quando se busca com o cromo apagado
        ///
        /// Ver o comentário do `espiando`. Ela mora no mesmo lugar em que moraria
        /// dentro do cromo, e por isso a transição de uma pra outra não pula: se
        /// a pessoa apertar `OK` no meio da busca, o cromo nasce em volta de uma
        /// película que já está na tela.
        /// ⚠️ **Nunca num canal**: espiar é olhar adiante no rolo, e num canal não
        /// há rolo — ver a película escondida logo abaixo.
        AnimatedVisibility(
            visible = espiando && !aberto && !estado.aoVivo,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(horizontal = Sala.overscanH)
                .padding(bottom = Sala.overscanV + 40.dp),
        ) {
            PeliculaDaSala(
                posicao = posicao,
                duracao = duracao,
                folha = estado.folha,
                urlDaFolha = estado.urlDaFolha,
                cenas = estado.cenas,
                arteDaCena = { modelo.arteDaCena(it) },
            )
        }

        AnimatedVisibility(
            visible = aberto,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.verticalGradient(
                            listOf(Color.Transparent, Color.Black.copy(alpha = 0.92f)),
                        ),
                    )
                    .padding(horizontal = Sala.overscanH)
                    /// ## ⚠️ **No canal a coluna sobe 60dp, e é o `SAIR`** — 17/08/2026
                    ///
                    /// Visto na TCL: tirada a película, o cromo do canal ficou com
                    /// uma linha só — e ela desceu até o rodapé, **em cima** do
                    /// `SAIR`, que mora ancorado no `BottomStart` da tela inteira e
                    /// não dentro desta coluna. Lia-se `NO AR` e `SAIR` sobrepostos
                    /// no mesmo canto.
                    ///
                    /// Os 60dp são o botão (44 de altura mínima) mais um respiro. É
                    /// a conta do próprio `BotaoDeSair` — não um número escolhido
                    /// até parecer certo.
                    ///
                    /// ⚠️ Só no canal: no filme a coluna é alta e o `SAIR` fica
                    /// naturalmente abaixo dela, que é onde o dono pediu que ficasse.
                    .padding(
                        top = Sala.overscanV,
                        bottom = Sala.overscanV + if (estado.aoVivo) 60.dp else 0.dp,
                    ),
            ) {

                Row(verticalAlignment = Alignment.CenterVertically) {
                    /// ## ⚠️ O plano virou um **ponto**, e não é economia de espaço
                    ///
                    /// A §6.3: «o plano vira **ponto colorido** ao lado do título,
                    /// não pílula com texto». Era uma pílula escrita
                    /// «transcodificando» na fileira dos botões.
                    ///
                    /// O argumento é de hierarquia. `direto` × `transcodificando`
                    /// é **diagnóstico**: interessa quando o filme engasga, e não
                    /// enquanto ele corre. Uma pílula com texto no meio dos
                    /// botões pede leitura toda vez que o cromo aparece, pra
                    /// entregar uma informação que quase sempre é «está tudo
                    /// bem». Um ponto verde diz isso sem cobrar leitura, e um
                    /// ponto dourado chama quando muda.
                    ///
                    /// É a mesma linha do celular, que já usa o ponto no
                    /// cabeçalho do player.
                    /// ## ⚠️ Num canal a lâmpada do plano **dá lugar ao ponto
                    /// vermelho** — 17/08/2026
                    ///
                    /// `direto × transcodificando` é uma conta sobre o arquivo, e
                    /// quem sintonizou não escolheu arquivo nenhum. O que importa
                    /// ali é que isto está **no ar** — e de qual canal.
                    ///
                    /// É a mesma troca que o celular já faz no `Cabecalho`, com as
                    /// mesmas palavras. Ver `EstadoDoPlayer.canalNome`.
                    val canal = estado.canalNome
                    if (canal != null) {
                        Box(Modifier.size(12.dp).background(Cores.perigo, CircleShape))
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "NO AR",
                            style = MaterialTheme.typography.labelLarge,
                            color = Cores.perigo,
                        )
                        Text(
                            text = canal,
                            style = MaterialTheme.typography.labelLarge,
                            color = Cores.textoApagado,
                            maxLines = 1,
                            modifier = Modifier.padding(start = 14.dp, end = 14.dp),
                        )
                    } else estado.plano?.let { plano ->
                        Box(
                            Modifier
                                .size(12.dp)
                                .background(
                                    if (plano.eDireto) Cores.certo else Cores.destaque,
                                    CircleShape,
                                )
                                .semantics {
                                    contentDescription =
                                        if (plano.eDireto) "direto" else "transcodificando"
                                },
                        )
                        Spacer(Modifier.width(12.dp))
                    }
                    Text(
                        text = estado.titulo,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Cores.texto,
                    )
                }
                Spacer(Modifier.height(10.dp))

                /// ## ⚠️ **No canal não há película** — 17/08/2026
                ///
                /// > «no ao vivo somente apareça esse player ao vivo sem mostrar
                /// > timeline pausar etc»
                ///
                /// O primeiro conserto tirou os três botões de transporte e parou
                /// ali; a tira de quadros, o `1:21:21` e o `faltam 7:05` ficaram —
                /// e vistos na TCL eram justamente a metade que mais parece um
                /// player de filme. A `PeliculaDaSala` desenha os três juntos, então
                /// escondê-la leva os três de uma vez.
                ///
                /// O argumento é o mesmo do transporte: **o tempo não é seu**. Uma
                /// barra que anda até o fim promete um fim que não existe num canal,
                /// e «faltam 7:05» é o resto do *arquivo* — não do que está no ar.
                ///
                /// ⚠️ O que sobra no cromo é `● NO AR · canal`, o título do programa
                /// e o `SAIR`. O `SAIR` fica: sem ele, sintonizado num canal, não há
                /// como voltar sem o controle da TV.
                if (!estado.aoVivo) {
                PeliculaDaSala(
                    /// ⚠️ **Enquanto se viaja, a película mostra o alvo e não o
                    /// filme.** É o ponto inteiro: a lente vai pra onde a pessoa
                    /// está olhando, os quadros em volta são os de lá, e o
                    /// relógio conta o tempo **de destino**. Sem isso a viagem
                    /// seria às cegas — setas mexendo num número invisível.
                    posicao = alvoDaViagem ?: posicao,
                    duracao = duracao,
                    folha = estado.folha,
                    urlDaFolha = estado.urlDaFolha,
                    cenas = estado.cenas,
                    arteDaCena = { modelo.arteDaCena(it) },
                    aoEntrarNaViagem = { alvoDaViagem = posicao },
                    aoSairDaViagem = { alvoDaViagem = null },
                    /// Um **quadro** por seta, e não um punhado de segundos.
                    ///
                    /// A `Tira` desenha até 40 quadros ao longo do filme, então
                    /// `duração/40` é exatamente a distância de um quadro pro
                    /// vizinho. Andar de quadro em quadro é o que faz a viagem
                    /// ser *no rolo* e não num relógio: cada aperto move a lente
                    /// pra a imagem seguinte, que é a coisa que se está
                    /// procurando.
                    ///
                    /// > «Você não arrasta até um tempo — arrasta até uma
                    /// > **imagem**.» (§2.3)
                    ///
                    /// Num filme de 2h22 isso dá ~3min30 por aperto: atravessa o
                    /// rolo inteiro em 40, que é o «de um jeito rápido» pedido.
                    aoViajar = { passos ->
                        val salto = (duracao / 40).coerceAtLeast(5_000L)
                        alvoDaViagem = ((alvoDaViagem ?: posicao) + passos * salto)
                            .coerceIn(0L, duracao)
                    },
                    navegavel = true,
                    aoConfirmar = {
                        alvoDaViagem?.let { destino ->
                            val p = player
                            if (p != null) {
                                p.seekTo(tempoDeSessao(destino, estado.deslocamentoMs))
                                posicao = destino
                            }
                        }
                        alvoDaViagem = null
                        /// Confirmar devolve o foco ao transporte: a viagem
                        /// acabou, e o que se quer em seguida é play, pausa ou
                        /// mais um salto.
                        runCatching { foco.requestFocus() }
                    },
                )
                }
                /// ⚠️ **Sem vão aqui, e é de propósito** — «desce um pouco a
                /// timeline film roll junto com o nome para mais próximo do play
                /// button».
                ///
                /// O que separava os dois não era este `Spacer` de 18dp: era o
                /// **halo** do `BotaoDeTocar`. Ele tem 124dp e o disco tem 60,
                /// então há 32dp de halo transparente acima do disco antes de
                /// qualquer pixel aceso. Somando os 18, o nome e a película
                /// flutuavam a meio palmo do botão.
                ///
                /// O halo fica — é a luz da peça. O vão escrito é que sai.
                Spacer(Modifier.height(2.dp))

                /// ## ⚠️ A fileira foi refeita a pedido do dono — «feios demais»
                ///
                /// Eram cinco pílulas de texto em fila à esquerda: `pausar`,
                /// `‹ 10s`, `10s ›`, `cc`, `sair`. Quatro coisas mudaram, e as
                /// quatro foram pedidas:
                ///
                /// | | |
                /// |---|---|
                /// | **centralizado** | a fileira era ancorada à esquerda e agora mora no meio da tela, que é onde os olhos já estão |
                /// | **o disco clássico** | `pausar`/`continuar` escrito virou o ▶/⏸ desenhado — o `BotaoDeTocar` do celular, com o disco dourado e o halo que a §6.3 pede |
                /// | **o 10 à esquerda** | «coloque 10s pra esquerda do botão play pause». É a ordem do celular, e ela é espacial: voltar fica do lado de onde o filme veio |
                /// | **o `sair` no canto** | «longe dos botões do player normal» — ver o `Modifier.align` lá embaixo |
                ///
                /// ⚠️ Os dois botões de salto e o disco vêm do `:cenario`, e são
                /// **os mesmos objetos do celular** — não uma segunda versão
                /// deles. É a §3 sendo cobrada: o `Botoes.kt` tinha 254 linhas e
                /// zero `material3`, e «atravessa de graça».
                /// ## ⚠️ **No canal não há transporte** — 17/08/2026
                ///
                /// O dono relatou dois players no ao vivo, e eram mesmo dois: o
                /// `TelaDoCanalAoVivoDaTv`, mínimo, servia o canal **sem obra**, e
                /// este servia o canal **com** obra — com voltar 10s, pausar e
                /// adiantar 30s. Qual aparecia dependia do programa que estava no
                /// ar naquele minuto, e daí o «de vez em quando».
                ///
                /// Agora quem manda é de **onde se entrou**. E os três somem
                /// porque são gestos sobre um tempo que não é seu: a grade segue
                /// correndo, pausar não pausa a transmissão, e voltar 10s só
                /// afasta você do que está no ar.
                if (!estado.aoVivo) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    /// ## ⚠️ Cada um dentro de um `Focavel` — os três não eram
                    /// selecionáveis
                    ///
                    /// > «os botões voltar 10s e avançar 30s não são
                    /// > selecionáveis no player»
                    ///
                    /// E a causa é a fronteira entre os dois aparelhos, na forma
                    /// mais literal: o `BotaoDeSalto` e o `BotaoDeTocar` vêm do
                    /// `:cenario` e usam `Modifier.clickable`, que é **dedo**. Um
                    /// nó `clickable` não é um alvo de D-pad; ele nem entra na
                    /// busca de foco.
                    ///
                    /// Enquanto eles eram `BotaoDaSala`, o `Focavel` vinha junto
                    /// e ninguém precisou pensar nisso. Trocar o desenho trocou o
                    /// **input** junto, calado — e o play/pause continuou
                    /// respondendo porque o `OK` é tratado pelo `onKeyEvent` da
                    /// tela inteira, o que escondeu metade do defeito.
                    ///
                    /// ⚠️ É a §8 em código: «háptico com dois pesos → não há mão»
                    /// vale pro toque inteiro. A peça atravessa; o jeito de
                    /// apontar pra ela não.
                    ///
                    /// O `Focavel` desenha o anel de foco por fora e chama o
                    /// mesmo `aoEscolher`. O `clickable` de dentro fica inerte
                    /// numa TV, e não atrapalha.
                    Focavel(aoEscolher = { pular(-10) }, forma = CircleShape) {
                        BotaoDeSalto(segundos = 10, paraTras = true) { pular(-10) }
                    }
                    Spacer(Modifier.width(26.dp))
                    /// ⚠️ **O anel deste é desenhado aqui, e não pelo `Focavel`**
                    /// — «esse círculo do select do play tá muito grande».
                    ///
                    /// O `BotaoDeTocar` é um disco de 60dp dentro de um halo de
                    /// 124dp. O anel do `Focavel` abraça o conteúdo, e o conteúdo
                    /// é o halo: saía um círculo com o **dobro** do diâmetro do
                    /// disco, boiando longe da coisa que ele deveria marcar.
                    ///
                    /// Encolher o halo não serve — ele é a peça, e é ele que faz
                    /// o botão ter luz. Recortar também não: o halo sumiria. O
                    /// que sobra é desenhar o anel em volta do **disco**, e é o
                    /// que estas linhas fazem, com o `anel = false` desligando o
                    /// de fora.
                    ///
                    /// 72dp = os 60 do disco mais 6 de respiro de cada lado. Se o
                    /// disco mudar de tamanho lá, este número mente — e é por
                    /// isso que ele está escrito com a conta ao lado.
                    Focavel(
                        aoEscolher = {
                            val p = player ?: return@Focavel
                            if (p.isPlaying) p.pause() else p.play()
                        },
                        forma = CircleShape,
                        anel = false,
                        modifier = Modifier.focusRequester(foco),
                    ) { focado ->
                        Box(contentAlignment = Alignment.Center) {
                            BotaoDeTocar(tocando = tocando) {
                                val p = player ?: return@BotaoDeTocar
                                if (p.isPlaying) p.pause() else p.play()
                            }
                            if (focado) {
                                Box(
                                    Modifier
                                        .size(72.dp)
                                        .border(3.dp, Cores.destaqueQuente, CircleShape),
                                )
                            }
                        }
                    }
                    Spacer(Modifier.width(26.dp))
                    Focavel(aoEscolher = { pular(30) }, forma = CircleShape) {
                        BotaoDeSalto(segundos = 30, paraTras = false) { pular(30) }
                    }

                    /// ⚠️ Os dois menus só nascem quando há o que escolher — é o
                    /// §53, a mesma régua do celular: «com uma faixa só, o botão
                    /// não nasce». Eles ficam **depois** do transporte porque são
                    /// ajuste de sessão, não comando de filme.
                }
                }
            }
        }

        /// ## O `sair` no canto, e longe — pedido do dono
        ///
        /// > «o botão sair deve ficar no canto da esquerda longe dos botões do
        /// > player normal»
        ///
        /// E o argumento é bom: `sair` não é comando de filme, é comando de
        /// **sessão**. No meio do transporte ele fica a uma seta de distância do
        /// `30s ↻` — e a única tecla que se aperta às cegas num controle de TV é
        /// justamente a seta.
        ///
        /// ⚠️ Ele respeita o overscan como todo texto de borda (`Sala.overscanH`),
        /// e mora no **rodapé** esquerdo, no mesmo eixo do relógio, pra não
        /// cobrir filme na altura dos olhos.
        if (aberto) {
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(horizontal = Sala.overscanH, vertical = Sala.overscanV),
            ) {
                BotaoDeSair(aoSair)
            }

        /// ## ⚠️ O `cc` e o `áudio` saíram da fileira de transporte
        ///
        /// Relatado pelo dono, olhando a TV: «o botão de play não está alinhado
        /// com o centro, ele tá mais pra esquerda».
        ///
        /// E estava, por aritmética. A fileira tinha **quatro** itens — `10`,
        /// `play`, `30` e `cc` — centralizados como grupo. Um grupo de quatro
        /// centrado não põe o segundo no meio: o `cc` pendurado à direita empurra
        /// tudo, e o disco de play cai à esquerda do centro real da tela.
        ///
        /// Somar um espaçador invisível do outro lado consertaria o pixel e
        /// deixaria a causa de pé — bastaria alguém acrescentar um quinto botão
        /// pra quebrar de novo.
        ///
        /// O que conserta é a **regra**: a fileira do meio tem só o transporte, e
        /// é simétrica por construção — `10 · play · 30`, o play no eixo. O que
        /// não é transporte mora nas quinas, como o `sair` já morava. Agora as
        /// duas quinas de baixo estão ocupadas, e a tela ficou equilibrada de
        /// propósito e não por acaso.
        if (aberto && (estado.legendas.isNotEmpty() || estado.faixasDeAudio.size > 1)) {
            /// ⚠️ **Um botão só**, e o rótulo diz o que há.
            ///
            /// Eram dois, um por assunto, porque cada um abria a sua listinha.
            /// Agora os dois abrem a mesma modal — e dois botões que fazem a
            /// mesma coisa fazem a pessoa escolher antes de escolher, além de
            /// dizerem, por existirem separados, que a outra escolha está noutro
            /// lugar. Justamente o que o dono não quis.
            ///
            /// O rótulo não é fixo porque a tela não é sempre a mesma: num
            /// filme dublado sem legenda, «legendas» seria mentira, e `cc` seria
            /// mentira e sigla.
            Row(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(horizontal = Sala.overscanH, vertical = Sala.overscanV),
            ) {
                val temLegenda = estado.legendas.isNotEmpty()
                val temAudio = estado.faixasDeAudio.size > 1
                BotaoDaSala(
                    when {
                        temLegenda && temAudio -> "legendas e áudio"
                        temLegenda -> "legendas"
                        else -> "áudio"
                    },
                    { menuDeFaixas = true },
                )
            }
        }

        }

        /// Quem mais está no mesmo filme agora. Numa TV é notícia útil: é o
        /// aviso de que alguém no quarto ao lado está na mesma cena.
        val naSala by modelo.naSala.collectAsStateWithLifecycle()
        if (aberto && naSala.isNotEmpty()) {
            Row(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = Sala.overscanH, vertical = Sala.overscanV),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                naSala.values.forEach { quem -> Pilula("${quem.nome} está aqui") }
            }
        }

        /// ## ⚠️ O menu de faixas virou **modal no meio da tela**
        ///
        /// Ele era desenhado **dentro da coluna do cromo**, acima da fileira de
        /// transporte — e o resultado, na foto do dono, foi uma listinha espremida
        /// na quina inferior esquerda, por cima do filme. «Abre a opção lá em
        /// cima, wtf.»
        ///
        /// Escolher legenda é uma decisão que **para o filme**: a pessoa deixa de
        /// assistir e vai mexer numa preferência. Uma tira de opções encostada na
        /// borda trata isso como se fosse mais um botão do transporte. Uma modal
        /// centrada trata como o que é — uma pausa deliberada.
        ///
        /// ⚠️ E as duas escolhas moram **juntas**, como o dono pediu: «mesmo com
        /// escolha de áudio nos casos de dual áudio». Quem abre pra trocar a
        /// legenda de um filme dublado quase sempre quer mexer no áudio também, e
        /// obrigar a fechar e reabrir por outro botão é fazer a pessoa pagar duas
        /// vezes pela mesma pausa.
        if (menuDeFaixas) {
            MenuDeFaixasDaSala(
                estado = estado,
                legendaEscolhida = legendaEscolhida,
                aoEscolherLegenda = { rotulo ->
                    legendaEscolhida = rotulo
                    escolherLegenda(player, rotulo)
                    menuDeFaixas = false
                },
                aoEscolherAudio = { indice ->
                    modelo.trocarFaixaDeAudio(indice, posicao)
                    menuDeFaixas = false
                },
            )
        }
    }
}

/// O menu de faixas, deitado.
///
/// No celular ele é uma coluna que rola — foi o conserto de um filme com 16
/// legendas, em que a lista ia da borda de cima à de baixo e o que passava disso
/// era inalcançável. Aqui ele é uma **fileira**, e o problema não se repete:
/// numa `LazyRow` a décima sexta faixa está a quinze setas de distância, mas
/// está alcançável, e o foco a traz pra tela sozinho.
/// ⚠️ `exit` do `focusProperties` ainda é experimental no Compose, e por isso a
/// anotação — que aqui é declaração, não silêncio. É a única forma de dizer «o
/// foco não sai daqui pelas setas»; imitá-la à mão significaria interceptar as
/// quatro direções e adivinhar quando já se está na borda, que é o mesmo bug
/// escrito por nós em vez de pela biblioteca.
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun MenuDeFaixasDaSala(
    estado: dev.odeon.android.ui.player.EstadoDoPlayer,
    legendaEscolhida: String?,
    aoEscolherLegenda: (String?) -> Unit,
    aoEscolherAudio: (Int) -> Unit,
) {
    /// ⚠️ Sem `BackHandler` próprio: quem atende o ◀ é a cadeia única do player
    /// (`menuDeFaixas -> aberto -> sair`). Um segundo aqui venceria o de lá por
    /// ser composto depois, e passaria a haver dois lugares que decidem o que o
    /// ◀ faz — o tipo de duplicata que fica certa até alguém mexer num só.
    ///
    /// ⚠️ O foco **entra** na modal, e não sai dela pelas setas.
    ///
    /// A fileira de transporte continua composta atrás do véu, e continua
    /// focável. Sem pedir o foco, o ▼ da pessoa moveria o play lá embaixo com a
    /// modal aberta na cara dela — o controle mexendo numa coisa que ela não
    /// está olhando. E sem `exit = Cancel`, sair pela borda de baixo cairia de
    /// volta no transporte em vez de não fazer nada.
    ///
    /// A saída é o ◀, que o `BackHandler` acima atende. É a única, e é a que a
    /// pessoa já usa pra fechar tudo nesta casa.
    val primeiro = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primeiro.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            Modifier
                .focusGroup()
                .focusProperties { exit = { FocusRequester.Cancel } }
                .background(Cores.fundoAfundado, RoundedCornerShape(14.dp))
                .border(1.dp, Cores.destaqueApagado.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(horizontal = 52.dp, vertical = 40.dp),
            horizontalArrangement = Arrangement.spacedBy(56.dp),
        ) {
            /// ⚠️ **Uma coluna por assunto**, e o áudio só aparece quando há
            /// escolha. Um cabeçalho «ÁUDIO» com uma faixa só embaixo é uma
            /// pergunta que não tem resposta alternativa — o §24 aplicado a um
            /// menu.
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "LEGENDAS",
                    style = dev.odeon.android.tv.ui.TipoDaSala.rotulo,
                    color = Cores.destaque,
                )
                Spacer(Modifier.height(10.dp))
                ItemDeFaixa(
                    rotulo = "sem legenda",
                    escolhido = legendaEscolhida == null,
                    modifier = Modifier.focusRequester(primeiro),
                ) { aoEscolherLegenda(null) }
                estado.legendas.forEach { legenda ->
                    ItemDeFaixa(legenda.rotulo, legendaEscolhida == legenda.rotulo) {
                        aoEscolherLegenda(legenda.rotulo)
                    }
                }
            }

            if (estado.faixasDeAudio.size > 1) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "ÁUDIO",
                        style = dev.odeon.android.tv.ui.TipoDaSala.rotulo,
                        color = Cores.destaque,
                    )
                    Spacer(Modifier.height(10.dp))
                    estado.faixasDeAudio.forEach { faixa ->
                        /// ⚠️ O rótulo sai do `rotuloDaFaixa` do `:core` — o mesmo
                        /// do celular. Ele é quem sabe que `und` conta como
                        /// ausente, e escrevê-lo de novo aqui seria a quarta
                        /// redação da frase.
                        ///
                        /// ⚠️ E a escolha vai por `faixa.index`, **não** pela
                        /// posição na lista: o índice é o da faixa dentro do
                        /// arquivo, e a lista pode não estar na mesma ordem.
                        ItemDeFaixa(
                            rotulo = rotuloDaFaixa(faixa),
                            escolhido = estado.faixaDeAudioEmUso == faixa.index,
                        ) { aoEscolherAudio(faixa.index) }
                    }
                }
            }
        }
    }
}


@Composable
private fun ItemDeFaixa(
    rotulo: String,
    escolhido: Boolean,
    modifier: Modifier = Modifier,
    aoEscolher: () -> Unit,
) {
    dev.odeon.android.tv.ui.Focavel(
        aoEscolher = aoEscolher,
        forma = RoundedCornerShape(8.dp),
        modifier = modifier,
    ) { focado ->
        Row(
            Modifier
                .width(280.dp)
                .background(
                    when {
                        focado -> Cores.destaqueQuente
                        escolhido -> Cores.fundoElevado
                        else -> Color.Transparent
                    },
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 18.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            /// ⚠️ A marca ocupa lugar **sempre**, escolhida ou não. Se ela só
            /// existisse na linha em uso, todas as outras andariam pra esquerda
            /// e a coluna ficaria desalinhada — e o olho leria isso como se a
            /// linha marcada fosse de outro tipo.
            Text(
                text = if (escolhido) "✓" else " ",
                style = MaterialTheme.typography.labelLarge,
                color = if (focado) Cores.fundoAfundado else Cores.destaque,
            )
            Spacer(Modifier.width(12.dp))
            Text(
                text = rotulo,
                style = MaterialTheme.typography.labelLarge,
                color = when {
                    focado -> Cores.fundoAfundado
                    escolhido -> Cores.texto
                    else -> Cores.textoApagado
                },
            )
        }
    }
}

/// A linha do tempo — o traço, o percorrido e os dois relógios.
///
/// Desenhada à mão pelo mesmo motivo da barrinha do cartaz, e por mais um: numa
/// TV ela **não é arrastável**. Não há dedo. Quem anda no filme são as setas, e
/// uma barra que parece arrastável e não é seria uma promessa quebrada.
@Composable
private fun PeliculaDaSala(
    posicao: Long,
    duracao: Long,
    folha: dev.odeon.android.dados.FolhaDeSprites?,
    urlDaFolha: String?,
    cenas: List<dev.odeon.android.dados.Cena>,
    arteDaCena: (String) -> String?,
    /// ⚠️ Os cinco abaixo são opcionais porque esta mesma peça desenha em **dois
    /// lugares**: dentro do cromo, onde se viaja, e sozinha na espiada de dois
    /// segundos ao saltar com as setas. Lá não há foco nem viagem — é só uma
    /// olhada.
    ///
    /// ⚠️ Houve um sexto, `viajando`, e ele **saiu**: existia só pra desenhar a
    /// moldura e a legenda de ajuda que o dono cortou. Parâmetro que sobrevive
    /// ao próprio uso vira alavanca que não liga em nada, e a próxima pessoa
    /// gasta uma tarde procurando o que ele faz.
    aoEntrarNaViagem: () -> Unit = {},
    aoSairDaViagem: () -> Unit = {},
    aoViajar: (Int) -> Unit = {},
    aoConfirmar: () -> Unit = {},
    navegavel: Boolean = false,
) {
    val fracao = if (duracao > 0) (posicao.toFloat() / duracao).coerceIn(0f, 1f) else 0f

    /// ## ⚠️ A película é um alvo de D-pad, e o teclado dela é próprio
    ///
    /// `▲` a partir do transporte cai aqui — é o que a busca de foco do Compose
    /// faz sozinha assim que existe um nó focável acima. O que **não** sai de
    /// graça é o resto: `◀▶` aqui não podem andar entre botões nem saltar dez
    /// segundos; elas correm o rolo.
    ///
    /// O `onKeyEvent` fica no nó focado, e não na tela: eventos sobem do foco pra
    /// fora, então este consome `◀▶` e `OK` **antes** do bloco da tela inteira —
    /// aquele que trata as setas com o cromo apagado. Os dois não brigam porque
    /// nunca estão os dois em jogo.
    ///
    /// ⚠️ `▼` e `voltar` **não** são consumidos de propósito: sair da película
    /// sem confirmar é desistir da viagem, e o `onFocusChanged` abaixo cuida
    /// disso. Consumi-los seria prender a pessoa numa timeline.
    val aoTeclado = Modifier
        .onFocusChanged { estadoDoFoco ->
            if (estadoDoFoco.isFocused) aoEntrarNaViagem() else aoSairDaViagem()
        }
        .onKeyEvent { evento ->
            if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
            when (evento.key) {
                Key.DirectionLeft -> { aoViajar(-1); true }
                Key.DirectionRight -> { aoViajar(1); true }
                Key.DirectionCenter, Key.Enter -> { aoConfirmar(); true }
                else -> false
            }
        }
        .focusable()

    /// ## ⚠️ Sem moldura e sem legenda de ajuda, e é decisão do dono
    ///
    /// A primeira versão desta viagem punha um anel dourado em volta da película
    /// inteira e trocava o «faltam 45:49» por um `OK PRA IR ATÉ AQUI`. Os dois
    /// saíram no mesmo pedido:
    ///
    /// > «não precisa do contorno amarelo na timeline quando tu vai controlar
    /// > ela (…) pode tirar o ok para ir até aqui — essas duas coisas são
    /// > intuitivas por si só, deixe somente a funcionalidade»
    ///
    /// E ele está certo pelo argumento que este projeto já usa em outro lugar: a
    /// lente **já** se move quando as setas andam, e os quadros em volta trocam
    /// junto. O objeto está dizendo o que está acontecendo. O anel e a frase
    /// diziam a mesma coisa uma terceira e uma quarta vez.
    ///
    /// É a §24 aplicada com mais rigor do que eu tinha aplicado: instrução que a
    /// própria coisa já ensina não é instrução, é ruído. O `Palco` do celular faz
    /// igual — a dica de arrastar **some pra sempre** depois de obedecida.
    Column(if (navegavel) aoTeclado else Modifier) {
        /// ## ⚠️ A lente **anda**, e a §6.2 queria ela parada — 12/08/2026
        ///
        /// O documento pede a tradução criativa: «no celular você arrasta a
        /// película até a cena. Numa TV não há arrasto — então a lente fica
        /// parada no centro e a película corre por baixo».
        ///
        /// **Tentei, e não saiu com a peça como ela é.** O plano era não tocar na
        /// `Tira`: ela não recebe `modifier` e usa `fillMaxWidth()` por dentro,
        /// então bastaria pô-la num pai três vezes mais largo que a tela e
        /// deslocar esse pai — a lente que ela desenha em `fracao` cairia no
        /// centro por construção.
        ///
        /// Duas medições na TCL derrubaram as duas versões disso:
        ///
        /// | | o que a foto mostrou |
        /// |---|---|
        /// | com `Modifier.width` | a lente **240px à esquerda** do centro. `width` é preferência e continua submetida à restrição do pai: pedir 3× dentro de 1× devolve 1×, calado |
        /// | com `requiredWidth` | a película encolheu pra 45% da tela e a lente **sumiu** |
        ///
        /// A segunda eu não terminei de diagnosticar, e é por isso que ela está
        /// escrita aqui em vez de num comentário afirmando causa: a `Tira` tem
        /// `BoxWithConstraints` aninhado, e o que ela mede de largura deixa de
        /// ser o que a janela mostra assim que o pai fura a restrição.
        ///
        /// ## O que ficou, e por que não é derrota
        ///
        /// A **primeira** linha da §6.2 — «A `Tira` inteira, **maior**» — está
        /// entregue: a película ocupa a largura da sala, com as cenas de verdade,
        /// as perfurações, o já visto em cor cheia contra o que vem, e a lente
        /// correndo sobre ela. É a mesma película do celular, do tamanho da TV.
        ///
        /// O que falta é a lente ficar parada. Isso é encenação, não objeto — e
        /// fazer sair provavelmente pede um modo dentro da própria `Tira` (uma
        /// película mais larga que a janela, com deslocamento próprio), que é
        /// mudar a peça. A T0 disse que as peças se movem e não se reescrevem, e
        /// abrir essa exceção no meio da T2, sem o dono ter visto o que já
        /// existe, seria decidir sozinho a coisa mais cara do documento.
        ///
        /// ⚠️ Está anotado na §10.4 do `docs/REDESENHO-TV.md` como pendência
        /// **medida**, e não como esquecimento.
        Tira(
            fracao = fracao,
            duracaoMs = duracao,
            folha = folha,
            urlDaFolha = urlDaFolha,
            cenas = cenas,
            arteDaCena = arteDaCena,
            /// Uma TV é **sempre** paisagem, e a `Tira` já tem as medidas desse
            /// caso: quadros mais largos e uma janela de 70dp em vez de 40.
            emPaisagem = true,
            /// ⚠️ Os três são exigidos pela assinatura e **não fazem nada aqui**,
            /// de propósito: são o arrasto do dedo, e a §8 já disse que ele não
            /// atravessa. Quem move o filme na sala são as setas, que já
            /// funcionam **sem acender o cromo** — a regra mais importante do
            /// player de TV, e ela é anterior a esta leva.
            aoComecarArrasto = {},
            aoArrastar = {},
            aoSoltar = {},
        )
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = relogio(posicao),
                style = MaterialTheme.typography.labelLarge,
                color = Cores.texto,
            )
            /// ⚠️ O «faltam» **continua** durante a viagem, e não vira
            /// instrução.
            ///
            /// Eu tinha trocado por um `OK PRA IR ATÉ AQUI` e o dono cortou. O
            /// número é melhor: ele conta o que sobra **do ponto que se está
            /// escolhendo**, que é exatamente a pergunta de quem está viajando
            /// pelo rolo — «se eu for pra cá, quanto ainda tem?».
            if (duracao > 0) {
                Text(
                    text = "faltam ${relogio(duracao - posicao)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Cores.textoApagado,
                )
            }
        }
    }
}

/// A barra fina de antes da T2 — sem película, sem cenas.
///
/// ⚠️ Ela **fica**, e não é código morto: a §6.2 manda «sem folha de sprites → as
/// 12 cenas; sem elas → barra fina. **Nunca** inventar retângulo colorido (§18)».
/// A `Tira` já cai na barra sozinha quando não há duração, e esta é a rede de
/// baixo dela.
@Suppress("UnusedPrivateMember")
@Composable
private fun LinhaDoTempo(posicao: Long, duracao: Long) {
    val fracao = if (duracao > 0) (posicao.toFloat() / duracao).coerceIn(0f, 1f) else 0f
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .background(Cores.linha, RoundedCornerShape(3.dp)),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(fracao)
                    .height(6.dp)
                    .background(Cores.destaque, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.height(10.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = relogio(posicao),
                style = MaterialTheme.typography.labelLarge,
                color = Cores.texto,
            )
            /// ⚠️ «faltam» só quando se sabe a duração. Em HLS de transcodificação
            /// o servidor às vezes ainda não sabe — e escrever «faltam 0min»
            /// seria afirmar que o filme acabou.
            if (duracao > 0) {
                Text(
                    text = "faltam ${relogio(duracao - posicao)}",
                    style = MaterialTheme.typography.labelLarge,
                    color = Cores.textoApagado,
                )
            }
        }
    }
}

/// `1:23:45` · `23:45`. A hora só aparece quando existe.
private fun relogio(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/// O `MediaController` — um controle remoto pro player que vive no serviço.
///
/// ## Ele é a montagem do `:app`, com uma diferença e nenhuma novidade
///
/// Cada linha aqui carrega uma lição já paga do lado do celular, e vale repetir
/// as duas maiores porque errar qualquer uma delas é silencioso:
///
///  - **o tipo vai explícito** quando é HLS. A URL do Odeon carrega `?token=` e
///    nem sempre termina em `.m3u8`; sem o `setMimeType`, o
///    `DefaultMediaSourceFactory` adivinha pela extensão, erra, e trata a
///    playlist como se fosse um arquivo de vídeo.
///
///  - ⚠️ **a posição inicial vai dentro do `setMediaItem`**. Do outro lado não
///    há um player, há um controle remoto pra outro processo, e cada chamada é
///    uma mensagem. `setMediaItem` de um argumento **zera a posição** por
///    definição — se ele chegar depois do `seekTo`, apaga o salto. Medido no
///    celular em 06/08/2026: o filme abria em `0:09` com a marca em `58:06`, e
///    a marca de progresso então **apagava** de onde a pessoa tinha parado.
///
/// A diferença é o `setCustomCacheKey`, que não vem: ele existe pra o cache
/// achar no disco o que o download escreveu, e a TV não baixa (ver `OdeonTv`).
///
/// ⚠️ O `@OptIn` não é sobre o Media3 em geral — é sobre **o nosso serviço**: o
/// `ServicoDeMidiaDaTv` é anotado `@UnstableApi` (ele estende `MediaSessionService`,
/// que ainda está abaixo da fronteira estável), e só de nomear a classe no
/// `ComponentName` o lint cobra a marca. É o mesmo motivo pelo qual o
/// `ServicoDeMidia` do `:app` também a carrega.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun lembrarControle(
    url: String?,
    arquivoId: String,
    eHls: Boolean,
    comecarEm: Long,
    legendas: List<dev.odeon.android.ui.player.LegendaOferecida>,
    titulo: String,
    capaUrl: String?,
): Player? {
    val contexto = LocalContext.current
    var controle by remember(url) { mutableStateOf<MediaController?>(null) }

    DisposableEffect(url) {
        if (url == null) return@DisposableEffect onDispose { }

        val token = SessionToken(
            contexto,
            android.content.ComponentName(contexto, ServicoDeMidiaDaTv::class.java),
        )
        val futuro = MediaController.Builder(contexto, token).buildAsync()

        futuro.addListener({
            val c = futuro.get()
            val item = MediaItem.Builder()
                .setUri(url)
                /// Os metadados que o «o que está tocando» da **home da TV** lê.
                /// Sem eles a sessão sobe muda, e o sistema mostra o que
                /// conseguir deduzir da URL — que aqui é
                /// `/api/stream/{id}?token=…`, ou seja nada.
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(titulo)
                        .setArtworkUri(capaUrl?.let(android.net.Uri::parse))
                        .build(),
                )
                .apply { if (eHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                /// As legendas entram como faixas **de fora**, ao lado do vídeo:
                /// o servidor já as serve convertidas em WebVTT, então a mesma
                /// faixa serve pro arquivo direto e pro HLS sem mudar uma linha.
                .setSubtitleConfigurations(
                    legendas.map { legenda ->
                        MediaItem.SubtitleConfiguration
                            .Builder(legenda.url.toUri())
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(legenda.idioma)
                            .setLabel(legenda.rotulo)
                            .build()
                    },
                )
                .build()

            c.setMediaItem(item, comecarEm)
            c.prepare()
            c.playWhenReady = true
            controle = c
        }, MoreExecutors.directExecutor())

        onDispose {
            MediaController.releaseFuture(futuro)
            controle = null
        }
    }

    return controle
}


/// O `sair` do player — e ele não é um `BotaoDaSala` de propósito.
///
/// ## Por que ele merecia desenho próprio
///
/// «melhore esse botão de sair.» A pílula genérica dizia a coisa errada de três
/// jeitos:
///
/// | | |
/// |---|---|
/// | **peso** | um retângulo sólido igual ao `cc` e ao `áudio`, competindo com o transporte pela atenção num canto onde ninguém precisa dele |
/// | **direção** | `sair` é uma palavra sem lugar. Voltar pra onde? O chevron responde, e é o mesmo `‹` que a ficha e o menu de disco já usam |
/// | **estado** | ele parecia apertável o tempo todo; agora só acende quando o foco chega |
///
/// ## O desenho
///
/// Fantasma: só contorno até o foco chegar, e aí ele **enche** — que é a régua
/// da §2.8 da casa, «estado é preenchimento, não cor», a mesma das pílulas de
/// filtro. Sem foco, o chevron e a palavra ficam em `textoApagado` sobre nada.
///
/// ⚠️ O alvo continua do tamanho de antes, com o respiro por dentro: encolher o
/// desenho não é encolher o que se acerta com o controle. É a mesma linha do
/// `BotaoDeTexto` do `Palco`, e pelo mesmo motivo — botão que encolhe sem
/// ninguém pedir é regressão que passa por build verde.
@Composable
private fun BotaoDeSair(aoSair: () -> Unit) {
    val forma = RoundedCornerShape(24.dp)
    Focavel(aoEscolher = aoSair, forma = forma, anel = false) { focado ->
        Row(
            Modifier
                .background(if (focado) Cores.destaqueQuente else Color.Transparent, forma)
                .then(
                    if (focado) {
                        Modifier
                    } else {
                        Modifier.border(1.dp, Cores.linha, forma)
                    },
                )
                .heightIn(min = 44.dp)
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "‹",
                style = MaterialTheme.typography.titleLarge,
                color = if (focado) Cores.fundoAfundado else Cores.textoApagado,
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = "SAIR",
                style = TipoDaSala.rotulo,
                color = if (focado) Cores.fundoAfundado else Cores.textoApagado,
            )
        }
    }
}
