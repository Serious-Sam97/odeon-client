package dev.odeon.android.ui.aovivo

import android.view.ViewGroup
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Tipo

/// A **transmissão** de um canal, no celular.
///
/// ## ⚠️ Ela existe porque o celular estava desistindo — 18/08/2026
///
/// > «mas man, o Carpenedo tá passando agora»
///
/// E estava mesmo. O ao vivo entrega **duas** coisas: o stream do canal, que o
/// ErsatzTV transmite sempre, e o **casamento** entre o programa da grade e uma
/// obra do acervo. O player de filme do celular só sabia abrir pelo segundo — e
/// sem casamento ele parava, mostrando um recado, diante de um canal que estava
/// no ar.
///
/// A TV nunca fez isso: sem obra, ela sintoniza. Esta tela é o mesmo caminho,
/// no telefone.
///
/// ⚠️ **A tabela que estava aqui envelheceu no mesmo dia, e some por isso.** Ela
/// dizia «casado abre o player de filme · não casado abre isto», que era verdade
/// por algumas horas do dia 18. A regra que ficou é outra e está escrita no
/// `TelaAoVivo.abrir`: quem escolhe o caminho é o **tipo de canal**, não o
/// casamento. Canal de fora — o que tem `programaId` — vem sempre pra cá, casado
/// ou não; canal do Odeon abre o arquivo, porque nele não existe transmissão.
///
/// Deixar a tabela velha aqui seria o defeito de ontem repetido: um comentário
/// que descreve o app de anteontem é pior que nenhum, porque é lido com confiança.
///
/// ⚠️ **Sem barra é honesto, e não uma falta.** Uma barra de progresso sobre uma
/// transmissão prometeria um fim que ninguém sabe — o app não conhece o arquivo,
/// só o que está saindo agora.
///
/// ⚠️ `UnstableApi` porque `setShutterBackgroundColor` e as fontes de dados do
/// Media3 ainda são experimentais — a mesma anotação que a tela irmã da TV já
/// carregava. Sem ela o `:app:lintDebug` aborta, e estava assim desde que este
/// arquivo nasceu.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TelaDoCanal(
    modelo: ModeloAoVivo,
    canalId: String,
    nome: String,
    aoSair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    var url by remember(canalId) { mutableStateOf<String?>(null) }
    var erro by remember(canalId) { mutableStateOf<String?>(null) }

    /// Quantas vezes esta tela já pediu o canal. Mudar de valor **re-sintoniza**.
    ///
    /// ⚠️ Ele existe porque «tentar de novo» aqui não é `prepare()`. A TV chama
    /// `prepare()` no mesmo player, e nela isso basta pro caso dela; no ao vivo o
    /// que costuma ter morrido é a **sessão do servidor** — e preparar de novo um
    /// player apontado pra uma playlist que não existe mais falha na hora, de
    /// novo, sem sair do lugar. Pedir o canal outra vez cobre os dois casos: se a
    /// sessão caiu, nasce outra; se foi a rede, a mesma chamada refaz o caminho.
    var tentativa by remember(canalId) { mutableIntStateOf(0) }

    LaunchedEffect(canalId, tentativa) {
        /// ⚠️ Limpar **antes** de pedir: sem isto o recado da tentativa anterior
        /// fica na tela enquanto a nova está a caminho, e quem tocou «tentar de
        /// novo» não vê diferença nenhuma acontecer.
        erro = null
        url = null
        val aberto = modelo.sintonizar(canalId)
        val playlist = aberto?.let { modelo.playlist(it.urlDaPlaylist) }
        if (playlist == null) erro = "o servidor não abriu este canal." else url = playlist
    }

    BackHandler { aoSair() }

    /// ## ⚠️ A cortina também abre aqui — 18/08/2026
    ///
    /// > «por que alguns iniciam com a cortina abrindo e outro com um loading
    /// > somente? o canal 1 e o Tela Amarela iniciam com loading, fica tela
    /// > preta»
    ///
    /// Porque eram **duas telas**: programa casado ia pro player de filme, que
    /// tem cortina; programa não casado vinha pra cá, que só tinha rodinho. A
    /// diferença que vazava pra tela era o **casamento** — contabilidade
    /// interna decidindo como a casa se apresenta.
    ///
    /// Sintonizar um canal é o mesmo gesto de começar um filme: a sala escurece
    /// e o pano abre. Que o app conheça ou não o arquivo por trás não muda o
    /// gesto.
    var cortinaAberta by remember(canalId) { mutableStateOf(false) }

    val cabecalhos = remember { modelo.cabecalhos() }
    val player = remember(url) {
        url?.let {
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
                    prepare()
                    playWhenReady = true
                }
        }
    }

    /// ⚠️ O player morre com a tela. Sem isto, sair do canal deixaria a sessão
    /// tocando em silêncio e segurando a transcodificação no servidor.
    DisposableEffect(player) { onDispose { player?.release() } }

    /// A transmissão caiu depois de ter começado.
    ///
    /// ## ⚠️ Sem isto a tela ficava muda, e preta — 19/08/2026
    ///
    /// O `erro` acima só cobre **não conseguir abrir**. Uma transmissão que cai
    /// depois — a fonte sai do ar, a rede vai embora, o servidor derruba a sessão
    /// — não passava por lugar nenhum: o player parava e a tela seguia preta, com
    /// «NO AR» escrito em cima, dizendo o contrário do que estava acontecendo.
    ///
    /// A TV já ouvia isso desde 17/08; o celular não. É o mesmo defeito de sempre
    /// desta casa — **a tela afirmando o que não sabe** (§18).
    var falhou by remember(player) { mutableStateOf(false) }
    DisposableEffect(player) {
        val p = player ?: return@DisposableEffect onDispose { }
        val ouvinte = object : androidx.media3.common.Player.Listener {
            override fun onPlayerError(e: androidx.media3.common.PlaybackException) {
                android.util.Log.w("Odeon", "canal $canalId caiu: ${e.errorCodeName}")
                falhou = true
            }
        }
        p.addListener(ouvinte)
        onDispose { p.removeListener(ouvinte) }
    }

    /// As duas maneiras de não ter canal, juntas — é o que a tela desenha igual.
    val naoDeu = erro != null || falhou

    Box(modifier.fillMaxSize().background(Color.Black)) {
        /// ## ⚠️ A superfície nasce **com a tela**, e não quando o player chega
        ///
        /// > «alguns canais abrem mega rápido e outros ficam com tela preta pra
        /// > sempre; esses estão com áudio rodando normal, e quando tu clica em
        /// > voltar a imagem aparece por 1 segundo»
        ///
        /// Medido no emulador em 19/08/2026: **17 de 17 canais de fora pretos**,
        /// todos com o vídeo decodificando (h264, quadro 1920×1080) e o áudio
        /// saindo. O `PlayerView` era criado, o player era anexado, e mesmo assim
        /// o `dumpsys SurfaceFlinger` não tinha camada de `SurfaceView` nenhuma —
        /// o codec desenhava numa superfície-fantasma (`unnamed`), que é onde o
        /// ExoPlayer joga quadro quando não há tela.
        ///
        /// A causa é a ordem, e ela dependia de uma corrida:
        ///
        /// | ordem | superfície | tela |
        /// |---|---|---|
        /// | `url` chega **antes** de a cortina terminar | válida, 1080×607 (mediu o 16:9) | imagem |
        /// | cortina termina **antes** de a `url` chegar | **inválida**, 1080×2400 (nunca mediu) | preta pra sempre |
        ///
        /// ⚠️ **O `return@Box` da cortina pulava a própria superfície.** Quando o
        /// `PlayerView` era enfiado numa tela que já tinha sido desenhada sem
        /// ele, o `SurfaceView` nascia sem superfície e não ganhava uma depois.
        ///
        /// O `TelaDoPlayer` já fazia certo e por isso nunca teve o defeito: lá a
        /// `Superficie(player)` é a **primeira** coisa do `Box`, composta sempre,
        /// e o `return@Box` da cortina só pula o **cromo**. Aqui é a mesma forma.
        ///
        /// ⚠️ E o player entra nulo até a `url` chegar — de propósito. O que não
        /// pode faltar é a superfície; o player ela recebe quando houver.
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

        if (!cortinaAberta) {
            dev.odeon.android.ui.player.CortinaDeAbertura(
                titulo = nome,
                pronto = url != null,
                aoTerminar = { cortinaAberta = true },
            )
            /// ⚠️ O erro atravessa a cortina: se o canal não abriu, esperar o
            /// pano terminar pra dizer isso seria segurar a má notícia.
            if (!naoDeu) return@Box
        }

        /// ## O recado, e ele é o mesmo do resto do app
        ///
        /// ⚠️ Antes daqui o erro era **uma linha vermelha no meio da tela preta**,
        /// sem saída: quem lia «o servidor não abriu este canal» só tinha o gesto
        /// de voltar do sistema, e nada dizia se insistir adiantava. O `Recado` já
        /// existia — a série e a temporada o usam — e é o que dá as duas saídas
        /// que faltavam, com o nome da ação escrito.
        ///
        /// ⚠️ **Os dois títulos são diferentes porque as duas coisas são
        /// diferentes**: «não abriu» é o canal que nunca começou; «parou» é o que
        /// estava no ar e caiu. Dizer a mesma frase nos dois esconderia de quem
        /// assiste a única parte que ele consegue julgar — se era pra estar
        /// funcionando ou não.
        if (naoDeu) {
            dev.odeon.android.ui.Recado(
                titulo = if (falhou) "a transmissão parou" else "o canal não abriu",
                /// ⚠️ Sem código de erro na frase, pela mesma razão da TV: num
                /// canal de fora a causa quase sempre está do outro lado, e um
                /// número aqui daria a impressão de que há o que consertar deste
                /// lado. O código vai pro `logcat`, que é de quem conserta.
                detalhe = if (falhou) {
                    "o canal saiu do ar, a fonte parou de responder, ou a rede caiu."
                } else {
                    "o servidor não entregou a transmissão deste canal."
                },
                aoTentar = { falhou = false; tentativa++ },
                aoVoltar = aoSair,
            )
            return@Box
        }

        /// §15 e o pedido do dono: enquanto o canal não vem, **algo gira**.
        if (url == null) {
            CircularProgressIndicator(
                color = Cores.destaqueQuente,
                strokeWidth = 3.dp,
                modifier = Modifier.align(Alignment.Center).size(46.dp),
            )
        }

        /// O cromo do canal: sair, o ponto vermelho e o nome. **Não há
        /// transporte** — pausar não pausa a transmissão, e voltar 10s só afasta
        /// do que está no ar. É a mesma decisão da TV.
        Row(
            Modifier.align(Alignment.TopStart).padding(4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextButton(onClick = aoSair) { Text("‹ voltar", color = Cores.destaque) }
            Spacer(Modifier.width(6.dp))
            Box(Modifier.size(8.dp).clip(CircleShape).background(Cores.perigo))
            Spacer(Modifier.width(8.dp))
            Text("NO AR", style = Tipo.rotulo, color = Cores.perigo)
            Spacer(Modifier.width(10.dp))
            Text(nome, style = Tipo.rotulo, color = Cores.textoApagado, maxLines = 1)
        }
    }
}
