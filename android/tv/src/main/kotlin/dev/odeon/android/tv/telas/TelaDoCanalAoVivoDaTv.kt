package dev.odeon.android.tv.telas

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.TipoDaSala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.ManterATelaAcesa
import dev.odeon.android.ui.aovivo.ModeloAoVivo

/// Um canal **de fora**, tocando a playlist que o servidor abriu.
///
/// ## ⚠️ Por que esta tela existe separada do player
///
/// O `TelaDoPlayerDaTv` é a tela de assistir um **filme**: ela negocia plano de
/// transcodificação, marca posição, monta a tira de miniaturas, escolhe legenda e
/// faixa de áudio, e sabe voltar dez segundos. Nada disso existe aqui.
///
/// Um canal de M3U externo não tem obra, não tem arquivo, não tem duração e não
/// tem onde parar — ele já está no meio quando você chega e continua depois que
/// você sai. Enfiar isso no `ModeloDoPlayer` significaria costurar «e se não
/// houver obra» em cada um daqueles caminhos, e cada costura dessas é um lugar
/// onde o filme normal pode quebrar depois.
///
/// ⚠️ **Sem barra de progresso, e é o §24.** Uma barra precisa de um fim; uma
/// transmissão não tem. Desenhar a barra vazia diria «isto está no começo», que é
/// falso, e desenhá-la cheia diria «acabou», que é pior.
/// ⚠️ `UnstableApi` porque `setShutterBackgroundColor` ainda é experimental no
/// media3 — a mesma anotação que o `TelaDoPlayerDaTv` carrega, e pelo mesmo
/// motivo. O lint desta casa tem `abortOnError`, então isto é declaração, não
/// silêncio: a API pode mudar, e quando mudar o compilador avisa.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TelaDoCanalAoVivoDaTv(
    canalId: String,
    nome: String,
    modelo: ModeloAoVivo,
    aoSair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    var url by remember(canalId) { mutableStateOf<String?>(null) }
    var erro by remember(canalId) { mutableStateOf<String?>(null) }

    LaunchedEffect(canalId) {
        val aberto = modelo.sintonizar(canalId)
        val playlist = aberto?.let { modelo.playlist(it.urlDaPlaylist) }
        if (playlist == null) {
            erro = "o servidor não abriu este canal."
        } else {
            url = playlist
        }
    }

    BackHandler { aoSair() }

    if (erro != null) {
        Recado(titulo = "não deu pra sintonizar", detalhe = erro, modifier = modifier) {
            BotaoDaSala("voltar", aoSair, principal = true)
        }
        return
    }

    /// ⚠️ O player nasce e morre **com esta tela**, e não numa sessão de mídia.
    ///
    /// Uma sessão serve pra continuar tocando com o app no fundo e pra os
    /// controles do sistema — coisas que fazem sentido num filme. Num canal, sair
    /// da tela é desligar a TV do canal, e é isso que o `release` faz.
    val cabecalhos = remember { modelo.cabecalhos() }
    val player = remember(url) {
        url?.let {
            /// ⚠️ **O `Bearer` vai na fonte de dados, não na URL.**
            ///
            /// A regra está escrita na web (`hls.ts`) e custou um 401 aqui pra
            /// ser lembrada: o `ffmpeg` escreve os segmentos com nome relativo
            /// (`seg00000.ts`), e resolução relativa **descarta a query string**.
            /// Com o token só na URL, a playlist até carrega e o primeiro
            /// segmento vai nu.
            ///
            /// `setDefaultRequestProperties` vale pra todo pedido desta fonte —
            /// playlist e segmento —, que é exatamente o papel do `xhrSetup` do
            /// hls.js do outro lado.
            val fonte = androidx.media3.datasource.DefaultHttpDataSource.Factory()
                .setDefaultRequestProperties(cabecalhos)
                .setAllowCrossProtocolRedirects(true)

            ExoPlayer.Builder(contexto)
                .setMediaSourceFactory(
                    androidx.media3.exoplayer.source.DefaultMediaSourceFactory(fonte),
                )
                .build()
                .apply {
                    setMediaItem(MediaItem.fromUri(it))
                    playWhenReady = true
                    prepare()
                }
        }
    }
    DisposableEffect(player) {
        onDispose { player?.release() }
    }

    /// ⚠️ Num canal a tela tem de ficar acesa pelo mesmo motivo do filme — e com
    /// mais razão: uma transmissão não tem pausa, então ninguém vai tocar no
    /// controle até querer sair.
    var estaTocando by remember(player) { mutableStateOf(false) }
    ManterATelaAcesa(estaTocando)

    var falhou by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val ouvinte = object : Player.Listener {
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                falhou = true
            }

            override fun onIsPlayingChanged(tocando: Boolean) {
                estaTocando = tocando
            }
        }
        p.addListener(ouvinte)
        onDispose { p.removeListener(ouvinte) }
    }

    if (falhou) {
        Recado(
            titulo = "a transmissão parou",
            /// ⚠️ Sem código de erro na frase: num canal externo a causa quase
            /// sempre está do outro lado da internet, e um número aqui só daria
            /// a impressão de que há o que consertar deste lado.
            detalhe = "o canal saiu do ar ou a fonte parou de responder.",
            modifier = modifier,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BotaoDaSala("tentar de novo", { falhou = false; player?.prepare() }, principal = true)
                BotaoDaSala("voltar", aoSair)
            }
        }
        return
    }

    Box(modifier.fillMaxSize().background(Color.Black)) {
        /// ## ⚠️ A superfície nasce **com a tela**, e não quando o player chega
        ///
        /// Vinha daqui um `if (player != null)` em volta deste `AndroidView`, e é
        /// a mesma forma que produziu no celular o defeito medido em 19/08/2026:
        /// **17 de 17 canais de fora pretos**, com o vídeo decodificando e o áudio
        /// tocando. Um `PlayerView` inserido numa tela que já foi desenhada sem
        /// ele nasce com `SurfaceView` **sem superfície** — e não ganha uma depois.
        /// O player segue tocando pra uma superfície-fantasma, e a sala vê preto.
        ///
        /// A folha do `TelaDoCanal` do `:app` tem a medição inteira, com o
        /// `dumpsys SurfaceFlinger` sem camada nenhuma e os dois tamanhos que
        /// separam os casos (1080×607 quando mediu o vídeo, 1080×2400 quando
        /// nunca mediu). Aqui a superfície passa a ser sempre a primeira coisa do
        /// `Box`, e o player entra nela quando houver.
        ///
        /// ⚠️ **Não foi exercitado nesta tela.** A TCL estava desligada em
        /// 19/08/2026, e o que existe é o defeito medido no celular mais a leitura
        /// de que o código daqui tinha a mesma forma. Quando a TV voltar, o teste
        /// é o mesmo: entrar num canal de fora e ver se aparece imagem — se vier
        /// som sem imagem, o conserto não pegou.
        AndroidView(
            factory = { ctx ->
                androidx.media3.ui.PlayerView(ctx).apply {
                    useController = false
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    setBackgroundColor(android.graphics.Color.BLACK)
                    setShutterBackgroundColor(android.graphics.Color.BLACK)
                }
            },
            update = { it.player = player },
            modifier = Modifier.fillMaxSize(),
        )

        /// A tarja do canal — aparece na entrada e **some depois de 6 segundos**.
        ///
        /// ## ⚠️ Ela ficava pra sempre, e a decisão foi trocada — 17/08/2026
        ///
        /// A folha anterior defendia que ela nunca sumisse: «a tarja é a única
        /// coisa que diz em que canal você está — e num canal sem guia, quem não
        /// vê o nome não sabe onde caiu». O argumento é bom pra quem **acabou de
        /// chegar**, e só pra esse.
        ///
        /// O dono relatou o custo do resto do tempo: «hj esse player do ao vivo
        /// nao some». Uma tarja permanente sobre uma transmissão que dura horas
        /// deixa de informar e passa a tapar — e a faixa preta de baixo de um
        /// 16:9 não é garantida: num conteúdo 4:3 ou 2.39:1 ela cai sobre a
        /// imagem.
        ///
        /// Seis segundos atendem os dois: quem chega lê o canal, e quem ficou
        /// vê o filme. ⚠️ E ela **volta a qualquer toque ou tecla**, que é o que
        /// separa «sumiu» de «foi embora»: a informação continua a um gesto de
        /// distância.
        var tarjaVisivel by androidx.compose.runtime.remember {
            androidx.compose.runtime.mutableStateOf(true)
        }
        androidx.compose.runtime.LaunchedEffect(tarjaVisivel) {
            if (tarjaVisivel) {
                kotlinx.coroutines.delay(6_000)
                tarjaVisivel = false
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = tarjaVisivel,
            enter = androidx.compose.animation.fadeIn(),
            exit = androidx.compose.animation.fadeOut(),
            modifier = Modifier.align(Alignment.BottomStart),
        ) {
        Column(
            Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        0f to Color.Transparent,
                        1f to Color.Black.copy(alpha = 0.85f),
                    ),
                )
                .padding(horizontal = Sala.overscanH, vertical = 24.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Cores.perigo),
                )
                Spacer(Modifier.width(10.dp))
                Text("NO AR", style = TipoDaSala.rotulo, color = Cores.perigo)
            }
            Spacer(Modifier.height(8.dp))
            Text(
                text = nome,
                style = MaterialTheme.typography.headlineMedium,
                color = Cores.texto,
            )
            Spacer(Modifier.height(6.dp))
            /// ⚠️ A dica de sair fica escrita porque **não há mais nada pra
            /// fazer nesta tela**. Sem transporte, sem faixas e sem barra, o
            /// controle inteiro não faz nada — e uma tela que ignora todos os
            /// botões precisa dizer qual é o que funciona.
            Text(
                text = "◀ voltar pra sintonia",
                style = TipoDaSala.rotulo,
                color = Cores.textoApagado,
            )
        }
        }
    }
}
