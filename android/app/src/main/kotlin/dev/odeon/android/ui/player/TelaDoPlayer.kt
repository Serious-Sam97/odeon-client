package dev.odeon.android.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import androidx.media3.ui.PlayerView
import com.google.common.util.concurrent.MoreExecutors
import dev.odeon.android.dados.FolhaDeSprites
import dev.odeon.android.midia.ServicoDeMidia
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.ManterATelaAcesa
import kotlinx.coroutines.delay

/// Assistir.
///
/// ## Esta tela conhece `Player`, e agora nem sequer o player
///
/// A decisão da espec era escrever a UI contra a interface `Player`, nunca
/// contra `ExoPlayer`, porque o `media3-cast` entrega um `CastPlayer` que
/// implementa a mesma interface (§4c).
///
/// Com a sessão de mídia, o que chega aqui é um **`MediaController`** — um
/// controle remoto pro player que vive no serviço. Ele também é um `Player`, e
/// nada nesta tela mudou por causa disso: a timeline, o arrasto, o preview e o
/// selo continuam iguais.
///
/// Isso é a prova que a fase 4 ia cobrar. Se trocar o `ExoPlayer` por um objeto
/// que nem sequer é o player não quebrou nada, trocar por um `CastPlayer` não
/// vai quebrar também.
@Composable
fun TelaDoPlayer(
    modelo: ModeloDoPlayer,
    ondeParou: Double,
    aoVoltar: () -> Unit,
    /// O arquivo **acabou**. Só interessa a quem veio de um canal: a TV já tinha
    /// isto, e é o que separa «tocar um arquivo» de «ficar num canal».
    aoAcabar: () -> Unit = {},
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    /// ## ⚠️ As duas pontas do ciclo de vida da sessão moram aqui
    ///
    /// E moram aqui porque **o modelo vive mais que esta tela**: o
    /// `viewModel(key = "player:…")` do `AppOdeon` é do escopo da atividade. Sem
    /// estas duas linhas, voltar e entrar de novo reaproveita a sessão de HLS
    /// anterior — que continuou sendo escrita — e o ExoPlayer cai na borda viva
    /// dela, dez minutos à frente. Ver `ModeloDoPlayer.encerrar`, onde a medida
    /// está.
    ///
    /// `ondeParou` chega **fresco da ficha** a cada entrada, e é ela quem aplica
    /// o `ondeContinuar` — o que o modelo lembrava tem a idade da primeira vez.
    DisposableEffect(Unit) {
        modelo.garantirPreparado(ondeParou)
        onDispose { modelo.encerrar() }
    }

    ModoDeSala()

    /// ## ⚠️ A cortina mora aqui, e não no [Reprodutor]
    ///
    /// > «pq no família de aluguel quando eu clico avanço na timeline o mesmo
    /// > abre as cortinas novamente? no guns akimbo e em outros não aparece as
    /// > cortinas, só avança»
    ///
    /// O comentário dela sempre disse a intenção certa — «acontece uma vez só na
    /// vida desta tela» — mas o estado morava no `Reprodutor`, que é a vida da
    /// **fonte**, não da tela. A distinção não custava nada enquanto a fonte
    /// nunca trocava no meio de uma visita.
    ///
    /// Ela passou a trocar: salto pra fora da sessão de HLS refaz plano e sessão
    /// (ver `saltarPara`), a `url` fica nula por um instante, o `Reprodutor` sai
    /// de cena e volta — e levava o `remember` junto. Daí a cortina reabrir a
    /// cada avanço, e **só** em filme transcodificado: em `direct_play` o salto é
    /// um `seekTo` e nada é remontado. É a mesma raiz do «só no Família de
    /// Aluguel».
    ///
    /// Aqui em cima ela sobrevive à troca de sessão e morre junto com a visita,
    /// que é exatamente o tempo de vida que a cortina sempre quis ter.
    var cortinaAberta by remember { mutableStateOf(false) }

    /// ⚠️ **A legenda escolhida sobe pelo mesmo motivo, e é pior que a cortina.**
    ///
    /// Medido em 06/08/2026: com `PT-BR FULL` no ar, um salto pra fora da sessão
    /// devolvia o menu com **`sem legenda`** marcado. Não era só o rótulo — a
    /// faixa some de verdade, porque o `MediaItem` novo traz outros
    /// `TrackGroup`s e o `TrackSelectionOverride` antigo não casa com nenhum.
    ///
    /// Cortina que reabre é feio; legenda que cai sozinha é a pessoa perdendo
    /// uma escolha sem ninguém dizer nada. Guardada aqui, ela é **reaplicada**
    /// quando as faixas da fonte nova aparecem — ver o `onTracksChanged` do
    /// ouvinte no [Reprodutor].
    var legendaEscolhida by remember { mutableStateOf<String?>(null) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        when {
            estado.preparando -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cores.destaque)
            }

            estado.erro != null || estado.url == null -> Column(
                modifier = Modifier.fillMaxSize().padding(24.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = estado.erro ?: "não deu pra preparar a reprodução",
                    color = Cores.perigo,
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                )
                TextButton(onClick = modelo::tentarDeNovo) { Text("tentar de novo") }
                TextButton(onClick = aoVoltar) { Text("voltar") }
            }

            else -> Reprodutor(
                modelo = modelo,
                estado = estado,
                cortinaAberta = cortinaAberta,
                aoAbrirCortina = { cortinaAberta = true },
                legendaEscolhida = legendaEscolhida,
                aoEscolherLegenda = { legendaEscolhida = it },
                aoVoltar = aoVoltar,
                aoAcabar = aoAcabar,
            )
        }
    }
}

/// Pula pra um instante do **filme**, decidindo se a sessão em vigor alcança.
///
/// ## ⚠️ A decisão que faltava: fora da sessão não é salto, é sessão nova
///
/// Em `direct_play` isto é sempre um `seekTo` — o arquivo inteiro está no
/// aparelho, e todo instante existe.
///
/// Em HLS não. O `ffmpeg` escreve a playlist do começo ao fim, e ela só contém o
/// trecho entre o `start` da sessão e o ponto a que a transcodificação chegou.
/// Pedir fora disso não dá erro: o ExoPlayer **espera**. Medido em 06/08/2026,
/// saltando de `47:20` pra `1:32:52`: `BUFFERING`, e ficou. Neste emulador
/// destravou sozinho depois de ~15s, quando a transcodificação alcançou — o que
/// é sorte, e não desenho. Num salto maior ou num servidor ocupado a espera é de
/// minutos, e do lado de quem assiste isso é travar.
///
/// ⚠️ **E o mesmo vale pra trás.** Rebobinar antes do início da sessão parava no
/// começo dela, porque aqueles segmentos nunca foram gerados. As duas pontas são
/// o mesmo caso, e agora têm a mesma resposta.
///
/// ## Como se sabe o que a sessão tem
///
/// `Player.duration`, justamente o número que o `duracaoConhecidaMs` existe pra
/// **não** usar na timeline: durante a transcodificação ele reporta só o que já
/// foi escrito, e cresce. Inútil como denominador, exato como "até onde dá".
///
/// A margem de 10s é o último segmento, que pode estar pela metade — mirar na
/// borda viva é pedir pra esperar de novo.
///
/// ⚠️ Duração desconhecida (`TIME_UNSET`, ou zero antes do primeiro segmento)
/// cai no `seekTo` de sempre. Sem saber até onde a sessão vai, refazer a sessão a
/// cada toque seria trocar uma espera por um ffmpeg novo — e o caso comum, o
/// salto curto, funciona.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun saltarPara(
    alvoNoFilmeMs: Long,
    player: Player?,
    estado: EstadoDoPlayer,
    modelo: ModeloDoPlayer,
) {
    val p = player ?: return
    val alvo = alvoNoFilmeMs.coerceAtLeast(0)
    val naSessao = tempoDeSessao(alvo, estado.deslocamentoMs)
    val gerado = p.duration.takeIf { it > 0 }

    val antesDoComeco = alvo < estado.deslocamentoMs
    val depoisDoFim = gerado != null && naSessao > gerado - 10_000

    android.util.Log.i(
        "odeon-salto",
        "alvo=${alvo}ms desloc=${estado.deslocamentoMs}ms hls=${estado.eHls} " +
            "antes=$antesDoComeco depois=$depoisDoFim gerado=$gerado",
    )

    /// ## ⚠️ **Antes do começo reabre, com ou sem `eHls`** — 18/08/2026
    ///
    /// O `eHls` guardava as duas condições, e não devia guardar a primeira:
    /// `antesDoComeco` **só pode ser verdade com `deslocamentoMs > 0`**, e
    /// deslocamento maior que zero é, por definição, uma sessão que começou
    /// adiante — não existe direct play com deslocamento. Ou seja: a guarda não
    /// protegia nada e podia **impedir** o conserto se o `eHls` chegasse falso
    /// por qualquer motivo.
    ///
    /// O sintoma era exatamente esse: arrastar a tira até a esquerda e o vídeo
    /// não se mexer, porque `seekTo(tempoDeSessao(0, desloc))` é `seekTo(0)` —
    /// o segundo zero **da sessão**, que é o minuto onde ela começou.
    if (antesDoComeco || (estado.eHls && depoisDoFim)) {
        modelo.reabrirEm(alvo)
    } else {
        p.seekTo(naSessao)
    }
    /// ⚠️ **O salto reancora o relógio na hora.** Quem arrastou pediu um ponto;
    /// esperar o `currentPosition` concordar deixaria o número parado no lugar
    /// antigo por até dois segundos — que é justamente o intervalo em que a
    /// pessoa olha pra ver se o toque pegou. Ver o laço da posição.
    modelo.anotarPosicao(alvo)
}

/// Apaga as luzes da casa enquanto o filme está na tela.
///
/// ## O relógio e a bateria não são parte do filme
///
/// Até 06/08/2026 o player era a única tela do app que usava a tela inteira — e
/// mesmo assim tinha, no alto, a hora, o sinal, o wi-fi e a bateria do sistema
/// desenhados por cima da imagem. Está nas fotos de ontem: `11:40` e três ícones
/// sobre o rosto de quem está no quadro.
///
/// Não é detalhe de acabamento. O app inteiro é a metáfora de uma sala de
/// cinema, e a barra de status é a coisa mais oposta a isso que existe num
/// celular: ela é o mundo lá fora pedindo atenção, no exato lugar onde a sala
/// deveria estar escura.
///
/// ## `BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`, e não esconder e pronto
///
/// Escondida pra sempre seria prender quem quer ver a hora ou responder uma
/// notificação. Com este comportamento a barra volta com um arrasto da borda e
/// some sozinha depois — o mesmo gesto que todo player de vídeo do sistema usa,
/// então não há nada novo pra aprender.
///
/// ⚠️ **O `onDispose` devolve as barras**, e não é higiene: sem ele, sair do
/// filme deixaria o app inteiro sem relógio e sem bateria, porque a janela é uma
/// só e a configuração é dela, não desta tela.
///
/// ## E devolve a orientação, pelo mesmo motivo
///
/// O botão de girar do [CabecalhoDoPlayer] **trava** a atividade num eixo — é o
/// que faz ele servir pra quem está deitado na cama com a rotação automática
/// desligada. Só que `requestedOrientation` é da atividade, e a atividade é uma
/// só: sem devolver aqui, a biblioteca ficaria deitada porque alguém quis ver um
/// filme deitado, e a única saída seria fechar o app.
///
/// `UNSPECIFIED` e não `SENSOR`: devolver ao **não pedido** deixa valer o que o
/// manifesto e o sistema decidem, inclusive a trava de rotação da pessoa. Pedir
/// sensor seria trocar uma imposição por outra.
@Composable
private fun ModoDeSala() {
    val contexto = LocalContext.current
    DisposableEffect(contexto) {
        val atividade = contexto.acharAtividade()
        val janela = atividade?.window
        val controlador = janela?.let {
            androidx.core.view.WindowCompat.getInsetsController(it, it.decorView)
        }
        controlador?.systemBarsBehavior =
            androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controlador?.hide(androidx.core.view.WindowInsetsCompat.Type.systemBars())
        onDispose {
            controlador?.show(androidx.core.view.WindowInsetsCompat.Type.systemBars())
            /// ## ⚠️ **O comportamento volta junto** — relatado pelo dono, 18/08/2026
            ///
            /// > «tem hora que eu volto pra página inicial e o menu de baixo fica
            /// > mega pra baixo, como se tivesse entrado em um modo full do nada»
            ///
            /// O `show()` devolvia as barras e **deixava o `systemBarsBehavior`
            /// em `TRANSIENT_BARS_BY_SWIPE`** — que é o modo cheio: as barras
            /// voltam a aparecer, mas **por cima** do conteúdo em vez de ocupar
            /// espaço. A janela continua medindo como tela cheia, e a barra de
            /// abas do app vai parar debaixo da barra do sistema.
            ///
            /// ⚠️ No tablet é onde dói: a barra de navegação é maior, e o menu
            /// do app some atrás dela.
            ///
            /// A regra que fica: **quem muda um modo da janela restaura os dois
            /// lados dele** — o que está visível e como ele se comporta.
            controlador?.systemBarsBehavior =
                androidx.core.view.WindowInsetsControllerCompat.BEHAVIOR_DEFAULT
            atividade?.requestedOrientation =
                android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun Reprodutor(
    modelo: ModeloDoPlayer,
    estado: EstadoDoPlayer,
    /// Vem de cima porque precisa sobreviver à troca de sessão — ver
    /// `TelaDoPlayer`.
    cortinaAberta: Boolean,
    aoAbrirCortina: () -> Unit,
    /// Também de cima, e reaplicada a cada fonte nova — ver `TelaDoPlayer`.
    legendaEscolhida: String?,
    aoEscolherLegenda: (String?) -> Unit,
    aoVoltar: () -> Unit,
    /// O arquivo acabou — repassado de cima; ver a folha no `TelaDoPlayer`.
    aoAcabar: () -> Unit,
) {
    val contexto = LocalContext.current
    val app = contexto.applicationContext as dev.odeon.android.OdeonApp

    /// ## Aqui está a aposta da fase 2 sendo cobrada
    ///
    /// `cast.player` é um `CastPlayer`, e `controleLocal` é um `MediaController`.
    /// Os dois implementam `Player`, e o resto desta tela — timeline, arrasto,
    /// preview, legendas, gestos — não sabe qual dos dois recebeu.
    ///
    /// Mandar pra TV virou **trocar a instância**, que é literalmente o que a
    /// §4c prometeu que aconteceria se a UI fosse escrita contra a interface. Se
    /// isto tivesse sido escrito contra `ExoPlayer`, esta linha seria uma
    /// reescrita da tela inteira.
    val cast = lembrarCast(app.odeon.base)
    val controleLocal = lembrarControle(
        url = estado.url!!,
        arquivoId = estado.arquivoId,
        eHls = estado.eHls,
        comecarEm = estado.comecarEm,
        legendas = estado.legendas,
        titulo = estado.titulo,
        capaUrl = estado.capaUrl,
    )
    val player = cast.player ?: controleLocal

    /// Mudar de aparelho refaz o plano: os codecs declarados passam a ser os da
    /// TV. Ver `ModeloDoPlayer.mudouParaCast` e `PerfilDeCast`.
    LaunchedEffect(cast.conectado) { modelo.mudouParaCast(cast.conectado) }

    /// **A perseguição** — o outro aparelho mexeu nesta obra.
    ///
    /// A tolerância de 5s e o porquê dela estão no `ModeloDoPlayer`. Aqui é só o
    /// pulo, e ele acontece em `offset + currentTime` como tudo neste arquivo:
    /// a posição que o servidor conhece é a da **obra**, e o `<video>` só sabe
    /// da sessão que está tocando (ver o cabeçalho).
    val perseguir by modelo.perseguir.collectAsStateWithLifecycle()
    LaunchedEffect(perseguir) {
        val alvoEmSegundos = perseguir ?: return@LaunchedEffect
        val p = player ?: return@LaunchedEffect
        val agoraEmSegundos = tempoDeFilme(p.currentPosition, estado.deslocamentoMs) / 1000.0
        if (kotlin.math.abs(alvoEmSegundos - agoraEmSegundos) > 5.0) {
            /// ## ⚠️ **Arrastar a tira não conseguia voltar ao zero** — 18/08/2026
            ///
            /// > «tô tentando o piloto de Abbott e não consigo arrastar a linha
            /// > do tempo pro zero»
            ///
            /// Havia **dois caminhos de busca** nesta tela, e só um sabia de HLS.
            /// O `saltarPara` (setas e capítulos) já tratava o caso: alvo antes
            /// do `deslocamentoMs` **reabre a sessão** naquele ponto, porque uma
            /// sessão de transcodificação começa onde foi pedida e não tem nada
            /// atrás disso.
            ///
            /// O arrasto da tira chamava `seekTo` direto — e o `tempoDeSessao`
            /// tem `coerceAtLeast(0)`, que **cala**: pedir o minuto 0 de um filme
            /// cuja sessão começou aos 12 vira «segundo 0 da sessão», que é o
            /// minuto 12 do filme. O dedo ia até a esquerda e o vídeo não se
            /// mexia.
            ///
            /// ⚠️ Só morde com `deslocamentoMs > 0` — ou seja, **continuar** algo
            /// por HLS. Quem abre do começo nunca viu, e é por isso que sobreviveu
            /// até alguém arrastar pra trás num episódio retomado.
            saltarPara((alvoEmSegundos * 1000).toLong(), p, estado, modelo)
        }
        modelo.jaPerseguiu()
    }

    /// A posição, lida em relógio de tela e não por evento.
    ///
    /// `Player` não emite "andou um segundo" — ele emite mudança de estado. Pra
    /// desenhar uma timeline que anda, alguém tem que perguntar; 200ms é o que
    /// faz o traço parecer contínuo sem acordar a composição sessenta vezes por
    /// segundo.
    /// ⚠️ **`posicao` é tempo de filme, não tempo de sessão** — e a conversão
    /// acontece aqui, na única linha que lê o player. Tudo abaixo (o relógio, o
    /// «faltam», a fração da tira, o segundo da miniatura) fala a mesma língua
    /// que a `duracao`, que sempre foi a do arquivo. Ver `tempoDeFilme`.
    var posicao by remember { mutableLongStateOf(0L) }
    var duracao by remember { mutableLongStateOf(0L) }
    var tocando by remember { mutableStateOf(true) }

    /// O player está esperando dado. Ver o rodinho lá embaixo.
    var enchendo by remember { mutableStateOf(false) }

    /// ⚠️ O celular tem o mesmo defeito, e ninguém tinha notado porque ali o
    /// tempo de tela do sistema é curto e a pessoa costuma estar com o aparelho
    /// na mão. Num filme longo apoiado na mesa, dorme igual.
    ManterATelaAcesa(tocando)


    /// ## ⚠️ O contador **saltava** em vez de andar — 18/08/2026
    ///
    /// > «o número que conta quanto tempo tá correndo fica pulando de 00:20 pra
    /// > 1:10, aleatório»
    ///
    /// A causa não é a leitura de 200ms: é o que se lê. Numa sessão de
    /// transcodificação a playlist **ainda está crescendo** — sem `ENDLIST`, o
    /// ExoPlayer trata a janela como móvel e **reancora** a cada atualização.
    /// O `currentPosition` então não anda: ele se recoloca, pra frente e pra
    /// trás, conforme a janela muda debaixo dele.
    ///
    /// Medido junto: `gerado=744034` num episódio de `21:57` — o player conhecia
    /// pouco mais da metade do que existe.
    ///
    /// ## O relógio passa a andar sozinho, e só **confere** com o player
    ///
    /// Entre duas leituras, o tempo que passou é `elapsedRealtime` — que não
    /// depende de janela nenhuma. O player vira **árbitro**: quando a posição
    /// dele está perto da nossa (até 2,5 s), reancoramos nela; quando está
    /// longe, é a janela se remexendo e a nossa contagem continua.
    ///
    /// ⚠️ **Salto e pausa reancoram sempre.** Quem arrastou pediu um ponto, e o
    /// relógio tem de obedecer na hora — desconfiar aí seria ignorar a pessoa.
    ///
    /// ⚠️ Isto é **remendo**, e está escrito como tal: a cura é a playlist VOD,
    /// que está parada por decisão conjunta em `PEDIDOS-AO-SERVIDOR.md`. O que
    /// isto compra é um número que não mente enquanto ela não vem.
    var ancora by remember { mutableLongStateOf(-1L) }
    var instanteDaAncora by remember { mutableLongStateOf(0L) }

    /// O ponto que um salto pediu. O laço da posição consome e zera — é o único
    /// caminho que tem o direito de reancorar o relógio.
    var pedidoDeAncora by remember { mutableStateOf<Long?>(null) }

    LaunchedEffect(player) {
        while (true) {
            val doPlayer = tempoDeFilme(player?.currentPosition ?: 0L, estado.deslocamentoMs)
            val agora = android.os.SystemClock.elapsedRealtime()
            val andando = player?.isPlaying ?: false
            /// ⚠️ **Pausar NÃO reancora** — 18/08/2026, segunda tentativa.
            ///
            /// > «quando eu pauso mesmo em 00:17 o contador volta pra 2:27»
            ///
            /// A primeira versão disto mandava a pausa adotar a posição do
            /// player. Era incoerente com a própria premissa: se o
            /// `currentPosition` é confiável, o remendo inteiro não precisava
            /// existir; se não é, **pausar não o torna confiável**. Pausado, o
            /// número **congela onde está** — que é o que a palavra pausa quer
            /// dizer.
            ///
            /// Quem reancora é o **salto**, e só ele: ali a pessoa pediu um
            /// ponto, e obedecer é o certo. Ver `pedidoDeAncora`.
            pedidoDeAncora?.let { pedido ->
                ancora = pedido
                instanteDaAncora = agora
                pedidoDeAncora = null
            }
            when {
                ancora < 0L -> {
                    ancora = doPlayer
                    instanteDaAncora = agora
                    posicao = doPlayer
                }
                !andando -> posicao = ancora
                else -> {
                    val nossa = ancora + (agora - instanteDaAncora)
                    posicao = if (kotlin.math.abs(doPlayer - nossa) <= 2_500L) doPlayer else nossa
                    ancora = posicao
                    instanteDaAncora = agora
                }
            }
            /// A posição anotada no modelo é o que o `voltarPraFicha` leva como
            /// dica — ver `ModeloDoPlayer.ultimaPosicaoNoFilmeMs`.
            modelo.anotarPosicao(posicao)
            /// A conhecida ganha da do player, e o porquê está inteiro em
            /// `EstadoDoPlayer.duracaoConhecidaMs`: em HLS de transcodificação
            /// o player só enxerga os segmentos já gerados, e a linha do tempo
            /// desenhada contra esse número anda pra trás.
            duracao = estado.duracaoConhecidaMs.takeIf { it > 0 }
                ?: player?.duration?.takeIf { it > 0 } ?: 0L
            tocando = andando
            /// ⚠️ `STATE_BUFFERING` **e não «não está tocando»**: pausado também
            /// não toca, e um rodinho sobre uma pausa diria que o app está
            /// fazendo algo quando quem parou foi a pessoa.
            enchendo = player?.playbackState ==
                androidx.media3.common.Player.STATE_BUFFERING
            delay(200)
        }
    }

    /// O batimento do progresso.
    ///
    /// 10s é escolha: a marca serve pra continuar de onde parou, e perder até
    /// dez segundos de filme é imperceptível. Mais frequente seria escrever no
    /// Postgres de casa a cada respiração, por um ganho que ninguém enxerga.
    LaunchedEffect(player) {
        while (true) {
            delay(10_000)
            val p = player ?: continue
            if (p.isPlaying) modelo.marcar(p.currentPosition, p.duration, "progress")
        }
    }

    /// ## ⚠️ O fim do arquivo, pra quem está num canal
    ///
    /// A TV escuta isto desde o redesenho dela; o celular não escutava, e por
    /// isso um canal aqui **acabava numa tela parada** quando o filme terminava.
    ///
    /// Quem decide o que fazer é a raiz, que sabe se havia canal — ver o
    /// `aoAcabar` no `AppOdeon`. Esta tela só avisa: ela não sabe de grade.
    LaunchedEffect(player) {
        val p = player ?: return@LaunchedEffect
        val ouvinte = object : androidx.media3.common.Player.Listener {
            override fun onPlaybackStateChanged(estadoDoPlayer: Int) {
                if (estadoDoPlayer == androidx.media3.common.Player.STATE_ENDED) aoAcabar()
            }
        }
        p.addListener(ouvinte)
        try {
            kotlinx.coroutines.awaitCancellation()
        } finally {
            p.removeListener(ouvinte)
        }
    }

    /// ## ⚠️ O único lugar do app que escuta o player falhar
    ///
    /// Antes de 06/08/2026 não havia `Player.Listener` em lugar nenhum, e o
    /// efeito está medido no `ModeloDoPlayer.falhouTocando`: a reprodução morria,
    /// o play parava de funcionar, e a tela continuava desenhando um relógio que
    /// andava.
    ///
    /// ## Uma tentativa calada, e só então a tela de erro
    ///
    /// `prepare()` é a recuperação que o Media3 documenta, e para uma piscada de
    /// rede ela devolve o filme sem que ninguém veja nada além de um engasgo. Ir
    /// direto pra tela de erro nesse caso seria trocar um segundo de buffer por
    /// uma tela vermelha — pior do que o problema.
    ///
    /// ⚠️ **Mas só uma.** Se o segundo erro chegar, `prepare()` não é a resposta:
    /// a sessão de HLS provavelmente morreu, e insistir nela é um laço que gasta
    /// bateria mostrando a mesma tela preta. Aí sobe pro `falhouTocando`, que
    /// refaz **plano e sessão** no ponto onde parou.
    ///
    /// O contador zera quando o filme volta a tocar — senão o primeiro erro da
    /// sessão gastaria a única tentativa de todas as horas seguintes.
    var jaTentouLevantar by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val ouvinte = object : Player.Listener {
            override fun onPlayerError(erro: androidx.media3.common.PlaybackException) {
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

            /// ## ⚠️ As escolhas de faixa são reaplicadas aqui, e só aqui
            ///
            /// `onTracksChanged` é o único momento em que dá pra fazer isso: um
            /// `TrackSelectionOverride` aponta pra um `TrackGroup` **daquela**
            /// fonte, e a fonte acabou de trocar. Tentar antes — logo depois do
            /// `prepare`, num `LaunchedEffect` — não acha grupo nenhum e falha em
            /// silêncio, que é o pior jeito de falhar.
            ///
            /// A legenda vem do estado guardado acima da troca de sessão. O áudio
            /// só precisa disto em `direct_play`: em HLS a playlist já vem com a
            /// faixa pedida e não há o que escolher.
            override fun onTracksChanged(faixas: androidx.media3.common.Tracks) {
                legendaEscolhida?.let { escolherLegenda(p, it) }
                val audio = estado.faixaDeAudioEmUso
                if (!estado.eHls && audio != null && audio > 0) escolherAudio(p, audio)
            }
        }
        p.addListener(ouvinte)
        onDispose { p.removeListener(ouvinte) }
    }

    /// ## ⚠️ Em `direct_play`, a faixa escolhida precisa ser reaplicada aqui
    ///
    /// Pedir a faixa 1 num arquivo direto refaz o plano, e o plano devolve a
    /// **mesma** URL — é o mesmo arquivo, com as duas faixas dentro. O player
    /// recarrega e escolhe a primeira por conta própria, então sem isto o menu
    /// mudaria o rótulo e não mudaria uma nota do que se ouve.
    ///
    /// Em HLS não faz nada, e é de propósito: lá a playlist tem uma faixa só, o
    /// `getOrNull(indice)` não acha grupo nenhum e a função devolve sem tocar em
    /// nada. Uma condição a menos pra alguém errar depois.
    LaunchedEffect(player, estado.eHls, estado.faixaDeAudioEmUso) {
        val indice = estado.faixaDeAudioEmUso ?: return@LaunchedEffect
        if (!estado.eHls && indice > 0) escolherAudio(player, indice)
    }

    /// Se o filme está indo pra **TV** neste instante.
    ///
    /// Lido por `rememberUpdatedState` porque quem o consulta é o `onDispose`
    /// abaixo, que roda muito depois de o efeito ter sido criado — capturar o
    /// valor de então diria "não está na TV" pra quem mandou pra sala no meio do
    /// filme.
    val estaNaTv by androidx.compose.runtime.rememberUpdatedState(cast.conectado)

    /// A marca do fim — e, desde 06/08/2026, **o fim do filme também**.
    ///
    /// Sair da tela é o momento em que a pessoa parou, e é o número que ela vai
    /// encontrar amanhã. As periódicas acima existem pro caso de o app morrer
    /// sem passar por aqui.
    ///
    /// ## ⚠️ E aqui o filme para de tocar, que é o que faltava
    ///
    /// O `ServicoDeMidia` foi escrito pra o player **sobreviver** à tela — é o
    /// que faz a janelinha e os controles da tela de bloqueio existirem. Só que
    /// "a tela saiu de cena" virou sinônimo de "continue tocando", e o efeito
    /// era o que o dono viu: sai-se do filme com o `voltar`, volta-se pra ficha,
    /// e o filme segue tocando por baixo do app inteiro.
    ///
    /// A distinção que faltava é **por que** a tela saiu:
    ///
    /// | | |
    /// |---|---|
    /// | janelinha, ou o app foi pro fundo | a tela **continua composta** — este bloco não roda, e o filme segue |
    /// | `voltar`, ou o botão do sistema | a tela é destruída — e aqui o filme acaba |
    ///
    /// ⚠️ **Girar o aparelho não passa por aqui**, e é o `configChanges` do
    /// manifesto que garante: sem ele a atividade seria recriada a cada giro, e
    /// este `onDispose` mataria o filme toda vez que alguém deitasse o celular.
    ///
    /// ⚠️ **E quem está na TV não para.** Mandar pro Chromecast é justamente
    /// dizer "não é mais este aparelho que toca" — desligar a sala porque alguém
    /// fechou a tela do celular seria o oposto do que a §4c promete.
    ///
    /// `stop` antes de `clearMediaItems` porque é o `stop` que solta o
    /// decodificador de hardware — o mesmo vazamento que o `ServicoDeMidia`
    /// documenta em `onTaskRemoved`, e que aparece só no **próximo** filme.
    DisposableEffect(player) {
        onDispose {
            val p = player ?: return@onDispose
            /// Antes de parar: parado, `currentPosition` deixa de valer.
            modelo.marcar(p.currentPosition, p.duration, "abandon")
            if (!estaNaTv) {
                p.stop()
                p.clearMediaItems()
            }
        }
    }

    /// O que deu errado ao tentar a janelinha. Fica na tela porque o contrário
    /// disso é um botão que não faz nada — ver `acharAtividade`.
    var falhaDaJanelinha by remember { mutableStateOf<String?>(null) }

    /// A fração de passo de volume que sobra entre um evento de arrasto e o
    /// próximo. `-1` = "nenhum gesto em curso". Ver `mudarVolume`.
    ///
    /// Um `FloatArray` de um elemento, e não um `mutableFloatStateOf`, porque
    /// isto **não** é estado de tela: mudá-lo não redesenha nada, e usar estado
    /// do Compose faria uma recomposição por evento de dedo.
    val acumuladorDeVolume = remember { floatArrayOf(-1f) }

    /// Se estamos **dentro** da janelinha.
    ///
    /// ## Dentro dela não existe controle nosso
    ///
    /// Visto rodando: a janelinha entrou com a barra de "−10s / pausar / +30s"
    /// junto, encolhida numa janela de poucos centímetros. Nada ali é alcançável
    /// com o dedo, e o que sobra é tapar o filme com enfeite.
    ///
    /// Quem controla o PiP é o **sistema**, com os botões dele — e ele já sabe
    /// pausar porque a sessão de mídia existe (ver `ServicoDeMidia`). Ou seja, o
    /// certo aqui é sumir e deixar a plataforma trabalhar.
    var emJanelinha by remember { mutableStateOf(false) }
    DisposableEffect(contexto) {
        val atividade = contexto.acharAtividade() as? androidx.activity.ComponentActivity
        val ouvinte = androidx.core.util.Consumer<androidx.core.app.PictureInPictureModeChangedInfo> {
            emJanelinha = it.isInPictureInPictureMode
        }
        atividade?.addOnPictureInPictureModeChangedListener(ouvinte)
        onDispose { atividade?.removeOnPictureInPictureModeChangedListener(ouvinte) }
    }

    /// ## A cortina de abertura
    ///
    /// Ela vive **aqui**, e não dentro dos `Controles`: o cromo é uma camada que
    /// aparece e some a cada toque, e a cortina acontece uma vez só na vida
    /// desta tela. Pendurá-la no cromo a faria renascer a cada toque.
    ///
    /// `pronto` é o player ter chegado a `STATE_READY` — o sinal de que há um
    /// primeiro quadro atrás do pano. É ele que faz a cortina **cortar** em vez
    /// de cumprir a coreografia inteira.

    /// Quem mais está neste filme agora. Vem do barramento, e o eco do próprio
    /// aparelho já foi descartado antes — ver `Barramento`.
    val naSala by modelo.naSala.collectAsStateWithLifecycle()

    Box(Modifier.fillMaxSize()) {
        Superficie(player)

        /// O grão, sobre o filme inteiro e o tempo todo.
        ///
        /// ⚠️ Ele fica **fora** do `if (emJanelinha)`: na janelinha o quadro tem
        /// 200dp, e grão desenhado nessa escala vira chuvisco. Ver a checagem
        /// logo abaixo — a camada é emitida antes, mas a janelinha sai da função
        /// antes dela.
        if (!emJanelinha) {
            dev.odeon.android.ui.Grao.Camada(Modifier.fillMaxSize())
        }

        if (emJanelinha) return@Box

        /// ⚠️ **O cromo não nasce enquanto a cortina está no ar.**
        ///
        /// A primeira versão desenhava os dois, e a foto mostrou o resultado:
        /// título, selo do plano, `legendas · janelinha · voltar`, a timeline e
        /// «faltam 1:37:48» flutuando por cima de um pano vermelho fechado. O
        /// cromo estava anunciando um filme que ainda não tinha começado.
        ///
        /// A cortina tem o próprio toque-pra-pular, então nada fica inalcançável
        /// no intervalo.
        if (!cortinaAberta) {
            CortinaDeAbertura(
                titulo = estado.titulo,
                pronto = player?.playbackState == androidx.media3.common.Player.STATE_READY,
                aoTerminar = aoAbrirCortina,
            )
        }

        if (!cortinaAberta) return@Box

        /// ## ⚠️ O rodinho de espera — 18/08/2026
        ///
        /// > «quando tu fica esperando o buffer ou algo assim, adiciona um
        /// > indicador»
        ///
        /// Numa sessão de transcodificação o player espera de verdade: os
        /// segmentos são produzidos enquanto se assiste, e **cada salto refaz a
        /// sessão**. Sem marca nenhuma, esperar e travar são a mesma imagem — um
        /// quadro parado.
        ///
        /// ⚠️ Ele mora **acima da cortina de abertura e abaixo do cromo**: a
        /// cortina já diz «está começando» com o nome do filme, e repetir um
        /// rodinho por cima dela seria dizer duas vezes. Depois que ela abre,
        /// quem espera é isto.
        ///
        /// ⚠️ E não escurece o vídeo: o que está na tela continua sendo o filme.
        /// Um véu por cima faria a espera parecer erro.
        if (enchendo) {
            androidx.compose.material3.CircularProgressIndicator(
                color = Cores.destaqueQuente,
                strokeWidth = 3.dp,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(46.dp),
            )
        }

        Controles(
            player = player,
            naSala = naSala,
            arteDaCena = { modelo.arteDaCena(it) },
            cast = cast,
            posicao = posicao,
            duracao = duracao,
            tocando = tocando,
            estado = estado,
            falhaDaJanelinha = falhaDaJanelinha,
            aoVoltar = aoVoltar,
            legendaEscolhida = legendaEscolhida,
            aoEscolherLegenda = aoEscolherLegenda,
            aoTrocarAudio = modelo::trocarFaixaDeAudio,
            aoSaltar = { alvo ->
                pedidoDeAncora = alvo
                saltarPara(alvo, player, estado, modelo)
            },
            aoEntrarNaJanelinha = { falhaDaJanelinha = entrarNaJanelinha(contexto) },
            aoMudarBrilho = { mudarBrilho(contexto, it) },
            aoMudarVolume = { mudarVolume(contexto, it, acumuladorDeVolume) },
            aoTerminarGesto = { acumuladorDeVolume[0] = -1f },
        )
    }
}

/// Conecta no serviço e devolve o controle remoto.
///
/// `null` enquanto a conexão não completa — ela é assíncrona, e desenhar a
/// timeline de um player que ainda não existe é o caminho curto pro traço
/// piscando em zero antes de saltar pro lugar certo.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun lembrarControle(
    url: String,
    arquivoId: String,
    eHls: Boolean,
    comecarEm: Long,
    legendas: List<LegendaOferecida>,
    titulo: String,
    capaUrl: String?,
): Player? {
    val contexto = LocalContext.current
    var controle by remember(url) { mutableStateOf<MediaController?>(null) }

    DisposableEffect(url) {
        val token = SessionToken(
            contexto,
            android.content.ComponentName(contexto, ServicoDeMidia::class.java),
        )
        val futuro = MediaController.Builder(contexto, token).buildAsync()

        futuro.addListener({
            val c = futuro.get()
            /// O tipo vai explícito porque a URL do Odeon carrega `?token=` e
            /// nem sempre termina em `.m3u8`. Sem ele o `DefaultMediaSourceFactory`
            /// tenta adivinhar pela extensão, erra, e trata a playlist como se
            /// fosse um arquivo de vídeo.
            val item = MediaItem.Builder()
                .setUri(url)
                /// Os metadados que a **notificação** e o controle do carro leem
                /// — R9.
                ///
                /// ## Sem isto a sessão sobe muda, e era o que acontecia
                ///
                /// O `MediaItem` não declarava `MediaMetadata` nenhuma, então a
                /// notificação de mídia mostrava o que o sistema conseguisse
                /// deduzir da URL — que aqui é `/api/stream/{id}?token=…`, ou
                /// seja, nada. Título e arte estavam na tela do app e em lugar
                /// nenhum fora dele.
                ///
                /// O `MediaSession` do Media3 lê os metadados **do item que está
                /// tocando**, e é por isso que o lugar certo é aqui e não no
                /// `ServicoDeMidia`: o serviço não sabe qual obra é.
                ///
                /// ⚠️ `setArtworkUri` e não `setArtworkData`: passar bytes
                /// obrigaria o app a baixar e decodificar o pôster **de novo**,
                /// numa segunda cópia, só pra entregar ao sistema. Com a URI
                /// quem busca é o carregador de mídia do Android, e ela é a mesma
                /// URL que o Coil já tem em cache.
                ///
                /// A capa é nula em 8.598 das 17.930 obras, e aí a notificação
                /// sobe **sem arte** em vez de com um quadrado vazio — §24 fora
                /// do app.
                .setMediaMetadata(
                    androidx.media3.common.MediaMetadata.Builder()
                        .setTitle(titulo)
                        .setArtworkUri(capaUrl?.let(android.net.Uri::parse))
                        .build(),
                )
                /// A **mesma** chave que o download usou.
                ///
                /// Sem ela o cache indexa pela URL — e a URL carrega o token de
                /// mídia, que muda quando ele é reemitido (§43). O filme baixado
                /// ontem não seria encontrado hoje, e o app rebaixaria 4 GB por
                /// causa de um parâmetro de query. Ver `Baixados.baixar`.
                .setCustomCacheKey(arquivoId)
                .apply { if (eHls) setMimeType(MimeTypes.APPLICATION_M3U8) }
                /// As legendas entram como faixas **de fora**, ao lado do vídeo.
                ///
                /// É assim porque o servidor as serve numa rota própria, já
                /// convertidas em WebVTT — a espec chama isso de «legenda como
                /// faixa WebVTT nativa». O player não precisa saber extrair nada
                /// do contêiner, e a mesma faixa serve pro arquivo direto e pro
                /// HLS sem mudar uma linha.
                .setSubtitleConfigurations(
                    legendas.map { legenda ->
                        MediaItem.SubtitleConfiguration
                            .Builder(android.net.Uri.parse(legenda.url))
                            .setMimeType(MimeTypes.TEXT_VTT)
                            .setLanguage(legenda.idioma)
                            .setLabel(legenda.rotulo)
                            .build()
                    },
                )
                .build()

            /// ## ⚠️ A posição inicial vai **dentro** do `setMediaItem`
            ///
            /// Era `setMediaItem(item)`, depois `seekTo(comecarEm)`, depois
            /// `prepare()` — três comandos. E o que está do outro lado não é o
            /// player: é um `MediaController`, um controle remoto pra um player
            /// que vive noutro processo. Cada chamada é uma mensagem, e
            /// `setMediaItem` de um argumento **zera a posição** por definição.
            /// Se ele chegar depois do `seekTo`, apaga o salto.
            ///
            /// **Medido em 06/08/2026**, *Armas em Jogo* em `direct_play`, marca
            /// em `58:06` e botão dizendo `continuar`: o player abriu em **`0:09`**
            /// e, na segunda tentativa, em **`0:10`**.
            ///
            /// ⚠️ **E não parava na tela.** A marca de progresso é escrita a cada
            /// 10s de reprodução — então abrir o filme *apagava* de onde a pessoa
            /// tinha parado. Numa base com três pessoas de verdade, é o defeito
            /// mais caro que este arquivo já teve: os outros mostravam errado,
            /// este **perdia**.
            ///
            /// `setMediaItem(item, startPositionMs)` é um comando só, e a posição
            /// viaja junto com o item. Não há duas mensagens pra chegarem fora de
            /// ordem.
            ///
            /// ## Por que ninguém tinha notado
            ///
            /// Em HLS quem retoma é o **servidor**: a sessão é aberta com
            /// `start=N` (ver `ModeloDoPlayer.preparar`), e o player abre no zero
            /// dela. Aí `comecarEm` vale zero de propósito e este caminho nunca é
            /// exercitado. Metade do acervo vem por HLS — inclusive todo filme
            /// com áudio ac3, que é o que este cliente não toca. Só `direct_play`
            /// caía aqui.
            c.setMediaItem(item, comecarEm)
            c.prepare()
            c.playWhenReady = true
            controle = c
        }, MoreExecutors.directExecutor())

        onDispose {
            /// Solta o **controle**, não o player. O player é do serviço, e é o
            /// serviço que decide morrer quando ninguém mais o segura — que é o
            /// que mantém o filme tocando quando esta tela sai de cena.
            MediaController.releaseFuture(futuro)
            controle = null
        }
    }

    return controle
}

/// Os controles, e eles são nossos.
///
/// Os do Media3 vinham de graça e serviram pra provar que o filme tocava. Mas a
/// espec (§4) separa as duas liberdades: **como os bytes viram imagem** é do
/// Media3; **como o player parece** é sempre nosso. Timeline, selo do modo,
/// preview de seek e gestos são a metade que não se terceiriza.
@Composable
private fun Controles(
    player: Player?,
    /// Quem mais está neste filme agora — ver `ModeloDoPlayer.naSala`.
    naSala: Map<String, NaSala>,
    /// Monta a URL de uma imagem de cena, pras células da tira.
    arteDaCena: (String) -> String?,
    posicao: Long,
    duracao: Long,
    tocando: Boolean,
    estado: EstadoDoPlayer,
    cast: EstadoDoCast,
    falhaDaJanelinha: String?,
    /// A legenda no ar, e quem a troca. Vem de cima porque precisa sobreviver à
    /// troca de sessão — ver `TelaDoPlayer`.
    legendaEscolhida: String?,
    aoEscolherLegenda: (String?) -> Unit,
    aoVoltar: () -> Unit,
    /// Trocar de faixa de áudio: o índice pedido e onde o filme está, em tempo
    /// de filme. Ver `ModeloDoPlayer.trocarFaixaDeAudio`.
    aoTrocarAudio: (indice: Int, posicaoDoFilmeMs: Long) -> Unit,
    /// Pular pra um instante do **filme**. Quem recebe decide se o alvo cabe na
    /// sessão em vigor ou se ela precisa ser refeita — ver `saltarPara`.
    aoSaltar: (alvoNoFilmeMs: Long) -> Unit,
    aoEntrarNaJanelinha: () -> Unit,
    aoMudarBrilho: (Float) -> Unit,
    aoMudarVolume: (Float) -> Unit,
    aoTerminarGesto: () -> Unit,
) {
    /// Os controles somem sozinhos, e voltam ao toque.
    ///
    /// Um filme com barra permanente por cima é um filme com menos filme. Três
    /// segundos é o que dá pra achar o que se quer sem virar decoração.
    var visiveis by remember { mutableStateOf(true) }

    /// ## Paisagem: a altura é o recurso escasso
    ///
    /// Deitado, a tela tem ~411dp de altura — e o cromo de baixo estava
    /// empilhado em **três fileiras** (a tira, o transporte, os tempos), o que
    /// o dono descreveu como «indo até a metade da tela».
    ///
    /// A mesma régua que o `EsqueletoComAbas` usa pra virar trilho: em altura
    /// espremida a tira encolhe, o disco de play diminui, e os **tempos entram
    /// na fileira do transporte** em vez de ocuparem uma própria. Três fileiras
    /// viram duas.
    val espremido = !androidx.compose.material3.adaptive.currentWindowAdaptiveInfo()
        .windowSizeClass
        .isHeightAtLeastBreakpoint(androidx.window.core.layout.WindowSizeClass.HEIGHT_DP_MEDIUM_LOWER_BOUND)
    var arrastando by remember { mutableStateOf(false) }
    var posicaoDoArrasto by remember { mutableFloatStateOf(0f) }
    var menuDeLegendas by remember { mutableStateOf(false) }
    var menuDeAudio by remember { mutableStateOf(false) }

    /// ## ⚠️ Os saltos se somam antes de virar um `seekTo` só
    ///
    /// > «os saltos de 10s não acumulam»
    ///
    /// **Medido em 06/08/2026:** trinta toques seguidos no `−10s` andaram **seis
    /// segundos**. A causa é a leitura: a `posicao` vem de um relógio de 200ms
    /// (ver o laço acima), então dois toques dentro da mesma batida calculam do
    /// **mesmo** número e pedem o **mesmo** ponto — trinta pedidos idênticos são
    /// um pedido. E em HLS cada `seekTo` ainda está bufferizando quando o próximo
    /// chega, o que engole o resto.
    ///
    /// O conserto é o que todo player faz: o toque não pula, ele **soma**. O
    /// número cresce na tela na hora — cinco toques mostram `−50s` imediatamente —
    /// e um único `seekTo` sai quando a mão para.
    ///
    /// ⚠️ **A base é capturada no primeiro toque**, e não relida a cada um: com o
    /// filme correndo durante a martelada, somar contra a posição de agora daria
    /// `−50s` a partir de um lugar que já andou dois segundos. A conta que a
    /// pessoa fez foi a partir de onde ela estava quando começou.
    var saltoPendente by remember { mutableLongStateOf(0L) }
    var baseDoSalto by remember { mutableLongStateOf(0L) }

    /// 320ms sem toque novo é o que separa «ele está martelando» de «ele acabou».
    /// Menos que isso volta a partir os saltos em dois; muito mais e o filme
    /// demora a obedecer a um toque único, que é o caso comum.
    LaunchedEffect(saltoPendente) {
        if (saltoPendente == 0L) return@LaunchedEffect
        delay(320)
        aoSaltar(baseDoSalto + saltoPendente)
        saltoPendente = 0L
    }

    /// O que o cromo desenha: o arrasto manda, depois o salto pendente, e só
    /// então a posição real. É uma leitura só, usada pelo relógio, pelo «faltam» e
    /// pela janela da tira — três lugares que **precisam** concordar, e que já
    /// divergiram uma vez nesta tela.
    val posicaoMostrada = when {
        arrastando -> (posicaoDoArrasto * duracao).toLong()
        saltoPendente != 0L -> (baseDoSalto + saltoPendente).coerceIn(0L, duracao)
        else -> posicao
    }

    /// Qual legenda está no ar. `null` é «sem legenda», que é o estado inicial do
    /// player — e é o que acende ou apaga o `cc`.

    /// O menu aberto também segura os controles. Um menu que se fecha sozinho
    /// no meio da leitura é pior que não ter menu.
    LaunchedEffect(visiveis, tocando, arrastando, menuDeLegendas, menuDeAudio) {
        if (visiveis && tocando && !arrastando && !menuDeLegendas && !menuDeAudio) {
            delay(3_000)
            visiveis = false
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures(onTap = { visiveis = !visiveis })
            }
            /// Os gestos verticais: brilho na metade esquerda, volume na
            /// direita.
            ///
            /// ## Por que essa divisão, e não outra
            ///
            /// É a convenção que todo player de celular usa, e convenção aqui
            /// vale mais que originalidade: ninguém lê manual de player. O lado
            /// é decidido no **começo** do gesto e não muda no meio — senão
            /// arrastar em diagonal trocaria de função na metade do caminho.
            ///
            /// Eles ficam **fora** do `if (visiveis)`: ajustar volume não devia
            /// exigir acordar a barra de controles primeiro.
            .pointerInput(Unit) {
                var naEsquerda = true
                detectVerticalDragGestures(
                    onDragStart = { toque -> naEsquerda = toque.x < size.width / 2 },
                    onDragEnd = aoTerminarGesto,
                    onDragCancel = aoTerminarGesto,
                ) { mudanca, deslocamento ->
                    mudanca.consume()
                    /// Sinal invertido: arrastar **pra cima** aumenta, e na tela
                    /// o eixo y cresce pra baixo.
                    val passo = -deslocamento / size.height
                    if (naEsquerda) aoMudarBrilho(passo) else aoMudarVolume(passo)
                }
            },
    ) {
        if (!visiveis) return@Box

        /// ## ⚠️ As duas lavagens saíram, e no lugar entrou **profundidade**
        ///
        /// Elas eram duas faixas de degradê, uma em cada ponta, postas na sétima
        /// rodada quando a paisagem mostrou o cromo ilegível sobre um assoalho
        /// claro. Consertaram o sintoma e cobraram um preço: escureciam o filme
        /// nas duas pontas **o tempo todo em que o cromo estava aberto**.
        ///
        /// O conserto de raiz é físico, e não pictórico: quando o cromo aparece,
        /// **o filme sai de foco** e os controles ficam no plano nítido. Você não
        /// está olhando através de uma tarja — está olhando o vidro em vez da
        /// tela. E nada precisa ser escurecido pra texto branco ser legível
        /// sobre um borrão escuro.
        ///
        /// ⚠️ **O borrão pede API 31**, e o `minSdk` daqui é 26. Abaixo disso o
        /// `Modifier.blur` **não falha: ele não faz nada** — e um cromo branco
        /// sobre um filme nítido seria ilegível justamente nos aparelhos mais
        /// velhos. Por isso o véu uniforme continua existindo por baixo, fraco:
        /// nos aparelhos novos ele é o toque de contraste que o borrão não dá;
        /// nos velhos, ele é o conserto inteiro.
        ///
        /// O véu é **uniforme** e não degradê de propósito: degradê tem borda, e
        /// borda dentro do quadro foi o defeito que a barra do facho levou três
        /// rodadas pra perder.
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.28f)),
        )

        /// ## O cabeçalho, refeito em 06/08/2026
        ///
        /// Eram **quatro blocos** empilhados aqui — título, selo do plano, as
        /// palavras `janelinha`/`voltar`, e noventa caracteres explicando o
        /// Cast —, cada um com um tamanho e nenhuma margem em comum. Viraram uma
        /// fileira só. O porquê de cada troca está no [CabecalhoDoPlayer]; o
        /// resumo é que a ação mais usada da tela (`voltar`) era a palavra mais
        /// apagada dela, e o dado que ninguém decide no meio do filme (o plano)
        /// era o segundo elemento mais gritante.
        CabecalhoDoPlayer(
            titulo = estado.titulo,
            canalNome = estado.canalNome,
            plano = estado.plano?.let { plano ->
                when (plano.mode) {
                    "direct_play" -> "direto"
                    "direct_stream" -> "remux"
                    "transcode" -> "transcodificando"
                    else -> plano.mode
                }
            },
            planoEDireto = estado.plano?.eDireto == true,
            /// Só durante um cast: fora dele, dizer "neste aparelho" seria
            /// responder uma pergunta que ninguém fez.
            aparelhoDoPlano = if (estado.paraCast) (cast.aparelho ?: "TV") else null,
            impedimentoDoCast = cast.impedimento,
            falhaDaJanelinha = falhaDaJanelinha,
            aoVoltar = aoVoltar,
            aoEntrarNaJanelinha = aoEntrarNaJanelinha,
            modifier = Modifier.align(Alignment.TopStart),
        )

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                /// ⚠️ **A tarja preta saiu daqui.**
                ///
                /// Havia um `Color.Black` a 55% por trás desta coluna inteira, e
                /// ele ficou redundante quando o véu uniforme passou a cobrir a
                /// tela toda: duas camadas escurecendo o mesmo lugar, e a de
                /// baixo com borda visível. Em paisagem o resultado era o que o
                /// dono viu — «uma barra preta indo até a metade da tela».
                ///
                /// O véu sozinho já dá contraste ao cromo, e sem aresta.
                .padding(
                    horizontal = 16.dp,
                    vertical = if (espremido) 6.dp else 12.dp,
                ),
            verticalArrangement = Arrangement.spacedBy(if (espremido) 4.dp else 8.dp),
        ) {
            /// ## Os menus de faixa, abertos acima da tira
            ///
            /// ⚠️ **Um por vez.** Abrir o áudio fecha a legenda e vice-versa: são
            /// duas listas do mesmo tamanho no mesmo canto, e duas abertas ao
            /// mesmo tempo cobririam a tira inteira — que é a peça que diz onde
            /// se está.
            if (menuDeLegendas || menuDeAudio) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    if (menuDeLegendas) {
                        MenuDeFaixas(
                            titulo = "legenda",
                            /// «sem legenda» é item da lista, e não um botão à
                            /// parte: desligar é uma escolha entre as outras, e
                            /// não uma operação de outra natureza.
                            itens = listOf("sem legenda" to (legendaEscolhida == null)) +
                                estado.legendas.map { it.rotulo to (it.rotulo == legendaEscolhida) },
                            aoEscolher = { indice ->
                                val rotulo = if (indice == 0) null else estado.legendas[indice - 1].rotulo
                                escolherLegenda(player, rotulo)
                                aoEscolherLegenda(rotulo)
                                menuDeLegendas = false
                            },
                        )
                    }
                    if (menuDeAudio) {
                        MenuDeFaixas(
                            titulo = "áudio",
                            itens = estado.faixasDeAudio.map {
                                rotuloDaFaixa(it) to (it.index == estado.faixaDeAudioEmUso)
                            },
                            aoEscolher = { escolhida ->
                                /// A posição em **tempo de filme**, que é o que o
                                /// modelo não tem: lá dentro só existe a posição
                                /// da sessão, e ela zera assim que a sessão é
                                /// trocada. Ver `trocarFaixaDeAudio`.
                                aoTrocarAudio(estado.faixasDeAudio[escolhida].index, posicao)
                                menuDeAudio = false
                            },
                        )
                    }
                }
            }

            /// A miniatura do ponto pra onde o dedo está indo.
            ///
            /// Ela só aparece durante o arrasto, e some quando o dedo levanta —
            /// e some **inteira** quando não há folha de sprites, em vez de virar
            /// um retângulo cinza dizendo "sem preview" (§24).
            ///
            /// ⚠️ As duas cópias locais não são estilo — são **exigência do
            /// compilador**, e foram o único preço que a extração do `:core`
            /// cobrou no `:app` inteiro (12/08/2026):
            ///
            ///     Smart cast to 'FolhaDeSprites' is impossible, because
            ///     'folha' is a public API property declared in different module
            ///
            /// Enquanto `EstadoDoPlayer` morava neste módulo, o `!= null` do
            /// `if` bastava pra `estado.folha` entrar em `Miniatura` já
            /// estreitado. Agora ele mora no `:core`, e o Kotlin recusa: outro
            /// módulo pode ser recompilado sozinho, e uma `val` de lá pode virar
            /// uma `val` com getter — que é livre pra devolver `null` na segunda
            /// leitura.
            ///
            /// Ler uma vez pra uma local resolve porque a local **é** deste
            /// módulo. Foi só aqui, e em nenhum outro dos 60 arquivos: sinal de
            /// que a fronteira já estava no lugar certo antes de virar um
            /// `:core`.
            val folha = estado.folha
            val urlDaFolha = estado.urlDaFolha
            if (arrastando && folha != null && urlDaFolha != null) {
                Miniatura(
                    folha = folha,
                    url = urlDaFolha,
                    segundo = (posicaoDoArrasto * (duracao / 1000.0)).toInt(),
                )
            } else if (arrastando && estado.erroDaFolha != null) {
                /// Só quando a pergunta **falhou**. Filme sem sprite gerado não
                /// escreve nada — é ausência normal, e o §24 manda a linha vazia
                /// sumir. Já uma falha calada é o §8b, e foi assim que a web
                /// perdeu o preview do acervo inteiro sem ninguém notar.
                Text(
                    text = "sem preview: ${estado.erroDaFolha}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cores.textoApagado,
                )
            }

            /// ## ⚠️ No canal **não há tira, nem transporte** — ver `EstadoDoPlayer.aoVivo`
            ///
            /// Uma tira de miniaturas é um mapa do filme pra escolher onde
            /// entrar, e numa transmissão não se escolhe: ela já está onde está.
            /// Desenhá-la convida a um gesto que só afasta a pessoa do que está
            /// no ar.
            if (!estado.aoVivo) {
            Tira(
                fracao = if (duracao > 0) posicaoMostrada.toFloat() / duracao else 0f,
                folha = estado.folha,
                urlDaFolha = estado.urlDaFolha,
                cenas = estado.cenas,
                arteDaCena = arteDaCena,
                emPaisagem = espremido,
                naSala = naSala,
                /// O detente da R8: um tique a cada **10 minutos de filme**
                /// arrastados. Ver o comentário em `Linha`.
                duracaoMs = duracao,
                aoComecarArrasto = { arrastando = true; visiveis = true },
                aoArrastar = { posicaoDoArrasto = it },
                aoSoltar = {
                    arrastando = false
                    /// A fração é da película inteira, então `fração × duração` é
                    /// tempo de **filme** — e o player só entende tempo de
                    /// sessão. Sem esta conversão, tocar em 20% da tira de um
                    /// filme retomado em 1h19 pedia o minuto 28 **da sessão**, e
                    /// levava pra 1h47 do filme. Ver `tempoDeSessao`.
                    /// ## ⚠️ **Arrastar até a ponta esquerda quer dizer ZERO** — 18/08/2026
                    ///
                    /// > «eu movo pra 00, aí aparece 00:20»
                    ///
                    /// Medido: arrastando o dedo até a borda, a fração que chega
                    /// aqui é **0,0095** — e não 0. A tira tem padding, o polegar
                    /// tem raio, e o menor valor alcançável nunca é o começo.
                    /// Num episódio de 22 minutos isso são 12 segundos; num
                    /// filme de duas horas, mais de um minuto.
                    ///
                    /// ⚠️ O laço é **em tempo, não em fração**: 2% de 22 minutos
                    /// são 26 segundos e 2% de duas horas são dois minutos e
                    /// meio. Quem arrasta pro começo quer o começo nos dois
                    /// casos, e um limiar em fração daria uma régua diferente
                    /// para cada filme.
                    ///
                    /// Quinze segundos é o que separa «fui pro início» de «quis
                    /// mesmo os primeiros vinte segundos» — e ninguém mira os
                    /// primeiros quinze segundos arrastando uma tira.
                    if (duracao > 0) {
                        val pedido = (posicaoDoArrasto * duracao).toLong()
                        aoSaltar(if (pedido < 15_000) 0L else pedido)
                    }
                },
            )

            /// ## ⚠️ O transporte, **centrado** e com alvo de gente
            ///
            /// Ele era `−10s · pausar · +30s` em `TextButton`, amontoado no
            /// canto inferior esquerdo — e a foto de uma tela deitada de 2.400px
            /// mostrou o que isso é: três palavrinhas num canto de uma tela
            /// enorme, com o meio vazio.
            ///
            /// Agora vai no centro, que é onde o polegar de quem segura o
            /// aparelho com as duas mãos alcança nas duas orientações. E o play
            /// é um disco de 56dp: ele é o único controle que se usa sem olhar,
            /// e era o mesmo tamanho de texto que os outros dois.
            ///
            /// ⚠️ **Os saltos continuam sendo texto**, e é decisão: `−10s` e
            /// `+30s` dizem **quanto** saltam. Trocá-los por setas com um número
            /// dentro seria desenhar o que a palavra já diz, e este app não tem
            /// jogo de ícones próprio — os cinco que existem são das abas.
            }

            /// ⚠️ O transporte inteiro — voltar 10s, pausar, adiantar 30s — sai no
            /// canal. Os três são gestos sobre um tempo que **não é seu**: a
            /// grade segue correndo, e pausar uma transmissão não a pausa, só
            /// afasta você dela.
            if (!estado.aoVivo) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(bottom = if (espremido) 0.dp else 2.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                /// Em paisagem o tempo decorrido entra **aqui**, à esquerda do
                /// transporte, e não numa fileira própria.
                Box(Modifier.weight(1f)) {
                    if (espremido) {
                        Text(
                            text = relogio(
                                posicaoMostrada,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = Cores.textoApagado,
                        )
                    }
                }
                /// Os dois saltos são **relativos**, e por isso eram os únicos
                /// controles que já acertavam antes desta rodada: somar 30s à
                /// posição da sessão e à do filme dá o mesmo pulo. Agora que a
                /// `posicao` virou tempo de filme, eles precisam da volta —
                /// senão passam a errar por exatamente o deslocamento.
                BotaoDeSalto(segundos = 10, paraTras = true) {
                    if (saltoPendente == 0L) baseDoSalto = posicao
                    saltoPendente -= 10_000
                }
                Box(Modifier.padding(horizontal = if (espremido) 12.dp else 18.dp)) {
                    BotaoDeTocar(tocando = tocando, compacto = espremido) {
                        if (tocando) player?.pause() else player?.play()
                    }
                }
                BotaoDeSalto(segundos = 30, paraTras = false) {
                    if (saltoPendente == 0L) baseDoSalto = posicao
                    saltoPendente += 30_000
                }
                /// ## ⚠️ As faixas ficam na ponta, e o transporte segue no meio
                ///
                /// A conta é de peso: os dois lados desta fileira têm
                /// `weight(1f)`, então o `−10 · play · +30` continua centrado na
                /// tela **mesmo com os ícones só de um lado**. Pendurá-los sem
                /// peso empurraria o play pra esquerda, e ele é o único controle
                /// que se usa sem olhar.
                ///
                /// Deitado o «faltam» divide esta ponta com eles; em pé ele mora
                /// na fileira de baixo e a ponta é só das faixas.
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (espremido && duracao > 0) {
                        val agora = posicaoMostrada
                        Text(
                            text = "faltam ${relogio(duracao - agora)}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cores.textoApagado,
                            modifier = Modifier.padding(end = 4.dp),
                        )
                    }
                    /// Os dois só nascem quando há o que escolher — §53. Um `cc`
                    /// apagado num filme sem faixa nenhuma, ou um alto-falante
                    /// num filme de áudio único, é oferecer o que a validação vai
                    /// negar.
                    if (estado.legendas.isNotEmpty()) {
                        BotaoDeLegenda(ligado = legendaEscolhida != null) {
                            menuDeAudio = false
                            menuDeLegendas = !menuDeLegendas
                        }
                    }
                    /// ⚠️ A lista vem do **plano**, e não do player. Perguntar ao
                    /// player responde «uma» em toda transcodificação, porque ele
                    /// só enxerga o que está na playlist — foi assim que o dual
                    /// audio de *Família de Aluguel* sumiu na medida de
                    /// 06/08/2026. Ver `dados.FaixaDeAudio`.
                    if (estado.faixasDeAudio.size > 1) {
                        BotaoDeAudio {
                            menuDeLegendas = false
                            menuDeAudio = !menuDeAudio
                        }
                    }
                }
            }
            }

            if (espremido) return@Column

            /// ⚠️ Os tempos também saem no canal: «faltam 1h29» é a promessa de
            /// um fim, e uma transmissão não tem o seu — quem diz quanto falta do
            /// **programa** é o herói da tela do ao vivo, que é onde essa conta
            /// pertence.
            if (estado.aoVivo) return@Column

            /// Os tempos ladeando a tira: onde você está, e **quanto falta**.
            ///
            /// ⚠️ «faltam 1h29» e não «0:35 / 1:37:48». A fração obriga quem lê
            /// a fazer a subtração, e o que a pessoa quer saber é se dá tempo. É
            /// também a palavra que a ficha e a fileira de continuar já usam — o
            /// player era o único lugar do app falando em fração.
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val agora = posicaoMostrada
                Text(
                    text = relogio(agora),
                    style = MaterialTheme.typography.labelSmall,
                    color = Cores.textoApagado,
                )
                if (duracao > 0) {
                    Text(
                        text = "faltam ${relogio(duracao - agora)}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cores.textoApagado,
                    )
                }
            }
        }
    }
}

/// A linha do tempo.
///
/// Desenhada à mão e não com o `Slider` do Material: o `Slider` traz bolinha,
/// ondulação e faixa de toque próprios, e nenhum deles combina com uma barra
/// fina por cima de um filme. E o arrasto aqui precisa avisar **quando começa**,
/// pra a miniatura nascer junto — coisa que o `Slider` não conta.
/// ## O detente háptico — R8
///
/// > «Um tique a cada 10 minutos de filme arrastados — a timeline passa a ter
/// > textura.»
///
/// O que ele conserta é concreto: arrastar uma timeline de 2h22 numa tela de
/// 1080px significa que **cada pixel vale 8 segundos**, e o dedo cobre uns 40.
/// Sem retorno, procurar um ponto é olhar o relógio e corrigir; com o tique, a
/// mão conta os passos.
///
/// ## 10 minutos é do documento, mas a régua é outra
///
/// Num filme de 2h22 dá 14 tiques ao longo da tela — um a cada ~77px, ou ~7 por
/// segundo num arrasto de velocidade normal. É denso, e é o ponto: a timeline
/// tem que **parecer** uma superfície com sulcos, não um botão que confirma.
///
/// ⚠️ **Num episódio de 22 minutos daria dois tiques**, o que não é textura, é
/// enfeite. Por isso o passo tem piso: o menor entre 10 minutos e um vinte avos
/// da duração, o que garante ~20 tiques na tela inteira em qualquer duração.
///
/// ## O tique é o seco
///
/// `TextHandleMove`, o mesmo de virar a caixa da locadora — e pelo mesmo motivo
/// da escala que a R5 montou: arrastar não escreve nada, e o `LongPress` está
/// reservado pros gestos que mudam o acervo. Um seek que batesse como uma
/// devolução ensinaria a mão errado.
@Composable
private fun Linha(
    fracao: Float,
    duracaoMs: Long,
    aoComecarArrasto: () -> Unit,
    aoArrastar: (Float) -> Unit,
    aoSoltar: () -> Unit,
) {
    var largura by remember { mutableFloatStateOf(1f) }
    val haptico = LocalHapticFeedback.current

    /// Em que "casa" o arrasto estava no evento anterior. `Int.MIN_VALUE` é o
    /// "ainda não começou" — sem ele, o primeiro toque cairia na casa 0 e o
    /// primeiro tique só sairia ao **sair** dela, o que soa como atraso.
    var casaAnterior by remember { mutableIntStateOf(Int.MIN_VALUE) }

    /// Quantas casas a timeline inteira tem.
    ///
    /// Sem duração conhecida não há detente nenhum — e isso acontece de verdade:
    /// em HLS de transcodificação a duração só chega depois do primeiro plano.
    /// Um detente calculado sobre duração zero daria divisão por zero ou um
    /// tique por pixel.
    val casas = if (duracaoMs > 0) {
        val passoMs = minOf(10 * 60 * 1000L, duracaoMs / 20)
        (duracaoMs / passoMs.coerceAtLeast(1L)).toInt().coerceIn(1, 200)
    } else {
        0
    }

    /// Dá o tique se o arrasto mudou de casa. Devolve a fração intacta.
    fun tiquear(f: Float): Float {
        if (casas > 0) {
            val casa = (f * casas).toInt()
            if (casa != casaAnterior) {
                if (casaAnterior != Int.MIN_VALUE) {
                    haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                }
                casaAnterior = casa
            }
        }
        return f
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            /// 24dp de alvo pra uma barra de 3dp: o traço é fino porque é
            /// bonito, mas dedo não tem 3dp. A área invisível em volta é o que
            /// faz o arrasto pegar de primeira.
            .height(24.dp)
            .pointerInput(Unit) {
                largura = size.width.toFloat()
                detectHorizontalDragGestures(
                    onDragStart = { toque: Offset ->
                        aoComecarArrasto()
                        /// A casa é zerada aqui e não no fim: cada arrasto novo
                        /// começa sem memória do anterior, senão pegar a
                        /// timeline no mesmo ponto de onde se soltou não daria
                        /// tique nenhum ao andar o primeiro passo.
                        casaAnterior = Int.MIN_VALUE
                        aoArrastar(tiquear((toque.x / largura).coerceIn(0f, 1f)))
                    },
                    onDragEnd = { aoSoltar() },
                    onHorizontalDrag = { mudanca, _ ->
                        aoArrastar(tiquear((mudanca.position.x / largura).coerceIn(0f, 1f)))
                    },
                )
            },
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(2.dp))
                .background(Cores.linha),
        )
        Box(
            Modifier.fillMaxWidth(fracao.coerceIn(0f, 1f)).height(3.dp)
                .clip(RoundedCornerShape(2.dp)).background(Cores.destaque),
        )
    }
}

/// Um quadro recortado da folha de sprites.
///
/// ## Uma imagem, e nenhuma requisição por segundo arrastado
///
/// O servidor gera uma grade de miniaturas espaçadas de `intervaloSegundos`.
/// Baixar a folha uma vez e recortar o quadro certo é o que faz o arrasto ser
/// instantâneo — pedir uma miniatura por posição seria uma requisição a cada
/// pixel que o dedo anda.
///
/// O recorte é feito por deslocamento: a imagem inteira entra numa caixa do
/// tamanho de **um** quadro, escalada pelo número de colunas e linhas, e
/// empurrada pra que o quadro desejado caia na janela.
@Composable
private fun Miniatura(folha: FolhaDeSprites, url: String, segundo: Int) {
    val indice = (segundo / folha.intervaloSegundos)
        .toInt()
        .coerceIn(0, (folha.quantosQuadros - 1).coerceAtLeast(0))
    val coluna = indice % folha.columns
    val linha = indice / folha.columns

    Box(
        modifier = Modifier
            .size(width = 160.dp, height = (160f * folha.alturaDaMiniatura / folha.larguraDaMiniatura).dp)
            .clip(RoundedCornerShape(4.dp))
            .background(Cores.fundoElevado),
    ) {
        coil3.compose.AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                /// A folha inteira é `columns` x `rows` vezes maior que a janela,
                /// e o deslocamento empurra a grade até o quadro certo aparecer.
                .layout { measurable, constraints ->
                    val larguraTotal = constraints.maxWidth * folha.columns
                    val alturaTotal = constraints.maxHeight * folha.rows
                    val posto = measurable.measure(
                        Constraints.fixed(larguraTotal, alturaTotal),
                    )
                    layout(constraints.maxWidth, constraints.maxHeight) {
                        posto.place(
                            x = -coluna * constraints.maxWidth,
                            y = -linha * constraints.maxHeight,
                        )
                    }
                },
        )
    }
}

/// A superfície de vídeo.
///
/// `AndroidView` com `PlayerView` porque quem desenha pixel de vídeo é um
/// `SurfaceView` do sistema, e Compose não tem equivalente. Os controles dele
/// ficam **desligados** — os nossos estão logo acima, e dois conjuntos de
/// controles disputando o mesmo toque é pior que nenhum.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun Superficie(player: Player?) {
    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { contexto ->
            PlayerView(contexto).apply {
                useController = false
                setBackgroundColor(android.graphics.Color.BLACK)
            }
        },
        update = { vista ->
            vista.player = player

            /// ## ⚠️ O borrão foi tentado aqui, e **não funciona** — 05/08/2026
            ///
            /// O «foco» do desenho aprovado era: com o cromo aberto, o filme sai
            /// de foco e os controles ficam no plano nítido. Duas tentativas, e
            /// as duas morrem no mesmo lugar.
            ///
            /// **`Modifier.blur` do Compose não alcança o vídeo.** Ele age na
            /// camada de composição, e quem desenha pixel de filme é o
            /// `SurfaceView` do `PlayerView` — que o sistema compõe **fora** da
            /// janela do app. O borrão pegaria tudo em volta do vídeo e deixaria
            /// o vídeo nítido: o contrário do desejado.
            ///
            /// **`View.setRenderEffect` também não.** Ele existe desde a API 31 e
            /// foi o que ficou escrito aqui por uma rodada. **Medido no aparelho,
            /// com API 37: o código roda e o filme continua nítido.** Pelo mesmo
            /// motivo — o efeito é da `View`, e a superfície do vídeo é composta
            /// pelo SurfaceFlinger num plano separado.
            ///
            /// ## O que faria funcionar, e por que não foi feito
            ///
            /// Trocar o `surface_type` do `PlayerView` pra `texture_view`. Um
            /// `TextureView` compõe **dentro** da hierarquia e aceita efeito.
            ///
            /// O preço é real e é de vídeo: some a camada de overlay de
            /// hardware, cada quadro passa a ser copiado pra uma textura, e num
            /// HEVC 4K em Direct Play — que é metade do que este acervo tem — a
            /// conta aparece em bateria e em quadros perdidos. Gastar isso por um
            /// efeito de cromo que aparece três segundos por vez não se paga.
            ///
            /// **É decisão do dono**, e está anotada como tal no
            /// `PARIDADE-ANDROID.md`. Enquanto isso o véu uniforme dos
            /// `Controles` faz o trabalho de legibilidade sozinho — e ele nunca
            /// foi plano B: mesmo com o borrão ele continuaria existindo pros
            /// aparelhos abaixo da API 31.
        },
    )
}

/// A janelinha.
///
/// A espec fixou `minSdk` 26 por causa dela (§4), e o `aspectRatio` não é
/// detalhe: sem ele o sistema escolhe um formato próprio e o filme entra na
/// janela com tarja. 16:9 é o que a imensa maioria do acervo é — o valor exato
/// por arquivo entra quando o player souber o tamanho do vídeo.
///
/// Devolve a frase do erro, ou `null` quando entrou. Ver `acharAtividade` pro
/// defeito que fez isto precisar devolver alguma coisa.
private fun entrarNaJanelinha(contexto: android.content.Context): String? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "PiP exige Android 8"

    val atividade = contexto.acharAtividade()
        ?: return "não achei a Activity"

    val parametros = PictureInPictureParams.Builder()
        .setAspectRatio(Rational(16, 9))
        .build()

    return runCatching { atividade.enterPictureInPictureMode(parametros) }
        .fold(
            onSuccess = { if (it) null else "o sistema recusou" },
            onFailure = { it.message ?: "falhou" },
        )
}

/// O brilho, e ele é **só desta janela**.
///
/// `screenBrightness` no `LayoutParams` muda a tela enquanto este app está na
/// frente e volta ao normal quando ele sai. O outro caminho — escrever em
/// `Settings.System.SCREEN_BRIGHTNESS` — mudaria o brilho **do aparelho**, exige
/// `WRITE_SETTINGS` (que é permissão de tela cheia de aviso) e deixaria a pessoa
/// com o celular escuro depois do filme. Um player não tem esse direito.
private fun mudarBrilho(contexto: android.content.Context, passo: Float) {
    val janela = contexto.acharAtividade()?.window ?: return
    val atributos = janela.attributes
    /// `< 0` é o valor "herda do sistema", e é como a janela nasce. Na primeira
    /// mexida ele vira um número — partindo da metade, porque não dá pra ler o
    /// brilho atual do sistema sem permissão.
    val atual = atributos.screenBrightness.takeIf { it >= 0f } ?: 0.5f
    atributos.screenBrightness = (atual + passo).coerceIn(0.01f, 1f)
    janela.attributes = atributos
}

/// O volume, no fluxo de música — que é onde o Media3 toca.
///
/// ## Ele precisa de um acumulador, e a primeira versão não tinha
///
/// O gesto chega em dezenas de eventos pequenos: cada um vale uns 0,004 da
/// altura da tela, ou **0,06 de um passo** de volume. A primeira versão fazia
/// `(atual + passo * maximo).toInt()`, que truncava os 0,06 a cada evento — o
/// alvo saía sempre igual ao atual, e o volume **nunca mexia**.
///
/// Medido: arrastando a tela inteira de baixo pra cima, 5 de 15 antes e 5 de 15
/// depois. Compila, não crasha, e não faz nada — o §8b em forma de aritmética.
///
/// Com o acumulador guardando a fração entre um evento e outro, o gesto inteiro
/// soma o que tem que somar.
private fun mudarVolume(
    contexto: android.content.Context,
    passo: Float,
    acumulador: FloatArray,
) {
    val audio = contexto.getSystemService(android.content.Context.AUDIO_SERVICE)
        as? android.media.AudioManager ?: return

    val maximo = audio.getStreamMaxVolume(android.media.AudioManager.STREAM_MUSIC)
    val atual = audio.getStreamVolume(android.media.AudioManager.STREAM_MUSIC)

    /// `< 0` marca "gesto novo": começa do volume que está valendo agora.
    if (acumulador[0] < 0f) acumulador[0] = atual.toFloat()
    acumulador[0] = (acumulador[0] + passo * maximo).coerceIn(0f, maximo.toFloat())

    val alvo = acumulador[0].toInt()
    if (alvo == atual) return

    /// `FLAG_SHOW_UI` de propósito: a régua de volume do sistema é a resposta
    /// visual do gesto. Sem ela o dedo sobe e nada aparece — e a pessoa não sabe
    /// se mexeu no volume, no brilho, ou em nada (§8b).
    audio.setStreamVolume(
        android.media.AudioManager.STREAM_MUSIC,
        alvo,
        android.media.AudioManager.FLAG_SHOW_UI,
    )
}

/// Liga uma faixa de legenda, ou desliga todas.
///
/// ## Por override de faixa, e não por idioma preferido
///
/// `setPreferredTextLanguage` seria uma linha, e erra no caso que mais aparece
/// neste acervo: **duas faixas do mesmo idioma** — a cheia e a forçada, ou a de
/// tradução e a de comentários. Pelo idioma, o player escolhe uma das duas e
/// quem pediu a outra não tem como dizer qual.
///
/// O `rotulo` é a chave porque é o que o servidor manda pronto e é o que a
/// pessoa acabou de tocar na lista — ou seja, o mesmo texto dos dois lados.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

/// Acha a Activity subindo a corrente de `ContextWrapper`.
///
/// ## Um `as? Activity` não basta, e o sintoma foi um botão morto
///
/// `LocalContext.current` **não é** a Activity: o Compose entrega o contexto
/// temático, que é um `ContextWrapper` em volta dela. A primeira versão fazia
/// `contexto as? Activity`, o cast dava `null`, a função voltava calada, e o
/// botão "janelinha" não fazia nada — o §8b em pessoa, e num botão que a espec
/// tratou como razão pra fixar o `minSdk` inteiro.
internal tailrec fun android.content.Context.acharAtividade(): Activity? = when (this) {
    is Activity -> this
    is android.content.ContextWrapper -> baseContext.acharAtividade()
    else -> null
}

private fun relogio(ms: Long): String {
    if (ms <= 0) return "0:00"
    val total = ms / 1000
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
