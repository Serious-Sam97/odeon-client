package dev.odeon.android.ui.player

import android.app.Activity
import android.app.PictureInPictureParams
import android.os.Build
import android.util.Rational
import androidx.compose.foundation.background
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
import androidx.compose.ui.unit.dp
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
fun TelaDoPlayer(modelo: ModeloDoPlayer, aoVoltar: () -> Unit) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

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

            else -> Reprodutor(modelo = modelo, estado = estado, aoVoltar = aoVoltar)
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun Reprodutor(modelo: ModeloDoPlayer, estado: EstadoDoPlayer, aoVoltar: () -> Unit) {
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
    )
    val player = cast.player ?: controleLocal

    /// Mudar de aparelho refaz o plano: os codecs declarados passam a ser os da
    /// TV. Ver `ModeloDoPlayer.mudouParaCast` e `PerfilDeCast`.
    LaunchedEffect(cast.conectado) { modelo.mudouParaCast(cast.conectado) }

    /// A posição, lida em relógio de tela e não por evento.
    ///
    /// `Player` não emite "andou um segundo" — ele emite mudança de estado. Pra
    /// desenhar uma timeline que anda, alguém tem que perguntar; 200ms é o que
    /// faz o traço parecer contínuo sem acordar a composição sessenta vezes por
    /// segundo.
    var posicao by remember { mutableLongStateOf(0L) }
    var duracao by remember { mutableLongStateOf(0L) }
    var tocando by remember { mutableStateOf(true) }

    LaunchedEffect(player) {
        while (true) {
            posicao = player?.currentPosition ?: 0L
            /// A conhecida ganha da do player, e o porquê está inteiro em
            /// `EstadoDoPlayer.duracaoConhecidaMs`: em HLS de transcodificação
            /// o player só enxerga os segmentos já gerados, e a linha do tempo
            /// desenhada contra esse número anda pra trás.
            duracao = estado.duracaoConhecidaMs.takeIf { it > 0 }
                ?: player?.duration?.takeIf { it > 0 } ?: 0L
            tocando = player?.isPlaying ?: false
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

    /// A marca do fim — e esta é a que importa de verdade.
    ///
    /// Sair da tela é o momento em que a pessoa parou, e é o número que ela vai
    /// encontrar amanhã. As periódicas acima existem pro caso de o app morrer
    /// sem passar por aqui.
    DisposableEffect(player) {
        onDispose {
            val p = player ?: return@onDispose
            modelo.marcar(p.currentPosition, p.duration, "abandon")
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

    Box(Modifier.fillMaxSize()) {
        Superficie(player)

        if (emJanelinha) return@Box

        Controles(
            player = player,
            cast = cast,
            posicao = posicao,
            duracao = duracao,
            tocando = tocando,
            estado = estado,
            falhaDaJanelinha = falhaDaJanelinha,
            aoVoltar = aoVoltar,
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

            c.setMediaItem(item)
            /// Só salta quando há pra onde saltar. Em HLS o `comecarEm` vem
            /// zerado de propósito — a sessão já começou no ponto pedido, e
            /// somar de novo saltaria o dobro. Ver `ModeloDoPlayer`.
            if (comecarEm > 0) c.seekTo(comecarEm)
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
    posicao: Long,
    duracao: Long,
    tocando: Boolean,
    estado: EstadoDoPlayer,
    cast: EstadoDoCast,
    falhaDaJanelinha: String?,
    aoVoltar: () -> Unit,
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
    var arrastando by remember { mutableStateOf(false) }
    var posicaoDoArrasto by remember { mutableFloatStateOf(0f) }
    var menuDeLegendas by remember { mutableStateOf(false) }

    /// O menu aberto também segura os controles. Um menu que se fecha sozinho
    /// no meio da leitura é pior que não ter menu.
    LaunchedEffect(visiveis, tocando, arrastando, menuDeLegendas) {
        if (visiveis && tocando && !arrastando && !menuDeLegendas) {
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

        /// O selo do modo — decidido pra aparecer **nos dois** lugares, aqui e
        /// na ficha. Aqui ele responde a pergunta que só nasce com o filme na
        /// tela: "por que está ruim?". Sem ele, transcodificação a 720p parece
        /// defeito de rede.
        estado.plano?.let { plano ->
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(Cores.fundoAfundado.copy(alpha = 0.75f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text(
                    text = when (plano.mode) {
                        "direct_play" -> "direto"
                        "direct_stream" -> "remux"
                        "transcode" -> "transcodificando"
                        else -> plano.mode
                    },
                    style = MaterialTheme.typography.labelSmall,
                    color = if (plano.eDireto) Cores.certo else Cores.destaque,
                )
                /// De **qual aparelho** o selo está falando.
                ///
                /// Durante um cast, `direto` e `transcodificando` são sobre a
                /// TV, não sobre este celular — a §4c manda dizer isso, senão a
                /// tela afirma sobre um aparelho uma coisa que é de outro, que é
                /// o §18 por outro caminho.
                if (estado.paraCast) {
                    Text(
                        text = cast.aparelho?.let { "na $it" } ?: "na TV",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cores.textoApagado,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.align(Alignment.TopEnd).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            /// O botão de legenda só existe quando há legenda. Um "CC" apagado
            /// num filme sem faixa nenhuma é o §53 outra vez — oferecer o que a
            /// validação vai negar.
            if (estado.legendas.isNotEmpty()) {
                TextButton(onClick = { menuDeLegendas = !menuDeLegendas }) {
                    Text("legendas", color = Cores.texto)
                }
            }
            TextButton(onClick = aoEntrarNaJanelinha) { Text("janelinha", color = Cores.texto) }
            TextButton(onClick = aoVoltar) { Text("voltar", color = Cores.texto) }
        }

        if (menuDeLegendas) {
            Column(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 56.dp, end = 12.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(Cores.fundoAfundado.copy(alpha = 0.95f))
                    .padding(4.dp),
            ) {
                TextButton(onClick = {
                    escolherLegenda(player, null)
                    menuDeLegendas = false
                }) {
                    Text("sem legenda", color = Cores.textoApagado)
                }
                estado.legendas.forEach { legenda ->
                    TextButton(onClick = {
                        escolherLegenda(player, legenda.rotulo)
                        menuDeLegendas = false
                    }) {
                        Text(legenda.rotulo, color = Cores.texto)
                    }
                }
            }
        }

        /// Por que não há Cast, quando não há.
        ///
        /// Ela aparece **no lugar do botão**, e não como botão apagado: o §53
        /// diz que o produto não oferece o que a validação vai negar, e o §8b
        /// diz que negar calado é o defeito. A frase resolve os dois — e diz
        /// **onde se resolve**, que é a régua do `acesso::negado()` do servidor.
        cast.impedimento?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.labelSmall,
                color = Cores.textoApagado,
                /// `end` também, e não só `start`: sem ele a frase encosta na
                /// borda direita e some meia palavra. Visto no emulador — o
                /// texto terminava em "com o" e o resto ficava fora da tela.
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, end = 16.dp, bottom = 96.dp),
            )
        }

        falhaDaJanelinha?.let {
            Text(
                text = "janelinha: $it",
                style = MaterialTheme.typography.labelSmall,
                color = Cores.perigo,
                modifier = Modifier.align(Alignment.TopCenter).padding(top = 56.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            /// A miniatura do ponto pra onde o dedo está indo.
            ///
            /// Ela só aparece durante o arrasto, e some quando o dedo levanta —
            /// e some **inteira** quando não há folha de sprites, em vez de virar
            /// um retângulo cinza dizendo "sem preview" (§24).
            if (arrastando && estado.folha != null && estado.urlDaFolha != null) {
                Miniatura(
                    folha = estado.folha,
                    url = estado.urlDaFolha,
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

            Linha(
                fracao = if (arrastando) posicaoDoArrasto
                else if (duracao > 0) posicao.toFloat() / duracao else 0f,
                /// O detente da R8: um tique a cada **10 minutos de filme**
                /// arrastados. Ver o comentário em `Linha`.
                duracaoMs = duracao,
                aoComecarArrasto = { arrastando = true; visiveis = true },
                aoArrastar = { posicaoDoArrasto = it },
                aoSoltar = {
                    arrastando = false
                    if (duracao > 0) player?.seekTo((posicaoDoArrasto * duracao).toLong())
                },
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = { player?.seekTo((posicao - 10_000).coerceAtLeast(0)) }) {
                    Text("−10s", color = Cores.texto)
                }
                TextButton(onClick = {
                    if (tocando) player?.pause() else player?.play()
                }) {
                    Text(if (tocando) "pausar" else "tocar", color = Cores.destaque)
                }
                TextButton(onClick = { player?.seekTo(posicao + 30_000) }) {
                    Text("+30s", color = Cores.texto)
                }
                Text(
                    text = "${relogio(if (arrastando) (posicaoDoArrasto * duracao).toLong() else posicao)} / ${relogio(duracao)}",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cores.textoApagado,
                )
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
        update = { it.player = player },
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
private fun escolherLegenda(player: Player?, rotulo: String?) {
    val p = player ?: return

    if (rotulo == null) {
        p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
            .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, true)
            .clearOverridesOfType(androidx.media3.common.C.TRACK_TYPE_TEXT)
            .build()
        return
    }

    val grupo = p.currentTracks.groups
        .firstOrNull { g ->
            g.type == androidx.media3.common.C.TRACK_TYPE_TEXT &&
                (0 until g.length).any { g.getTrackFormat(it).label == rotulo }
        } ?: return

    val faixa = (0 until grupo.length).first { grupo.getTrackFormat(it).label == rotulo }

    p.trackSelectionParameters = p.trackSelectionParameters.buildUpon()
        .setTrackTypeDisabled(androidx.media3.common.C.TRACK_TYPE_TEXT, false)
        .setOverrideForType(
            androidx.media3.common.TrackSelectionOverride(grupo.mediaTrackGroup, faixa),
        )
        .build()
}

/// Acha a Activity subindo a corrente de `ContextWrapper`.
///
/// ## Um `as? Activity` não basta, e o sintoma foi um botão morto
///
/// `LocalContext.current` **não é** a Activity: o Compose entrega o contexto
/// temático, que é um `ContextWrapper` em volta dela. A primeira versão fazia
/// `contexto as? Activity`, o cast dava `null`, a função voltava calada, e o
/// botão "janelinha" não fazia nada — o §8b em pessoa, e num botão que a espec
/// tratou como razão pra fixar o `minSdk` inteiro.
private tailrec fun android.content.Context.acharAtividade(): Activity? = when (this) {
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
