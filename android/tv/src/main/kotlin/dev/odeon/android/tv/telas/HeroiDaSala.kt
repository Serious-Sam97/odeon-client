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
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.runtime.produceState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.layout.layout
import dev.odeon.android.dados.FolhaDeSprites
import kotlinx.coroutines.delay
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import dev.odeon.android.dados.PlanoDeReproducao
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
    /// De onde vêm as cenas. `null` mantém o herói na arte estática, e é o
    /// padrão: quem não passa não muda de comportamento.
    folhaDoFilme: (suspend (String) -> FolhaDeSprites?)? = null,
    urlDaFolha: (String) -> String? = { null },
    /// O plano do arquivo — a prévia em vídeo só existe se ele **tocar direto**.
    planoDoArquivo: (suspend (String) -> PlanoDeReproducao?)? = null,
    urlDeMidia: (String?) -> String? = { null },
    cabecalhosDeMidia: () -> Map<String, String> = { emptyMap() },
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
        /// ## ⚠️ O herói passa cenas do filme, e elas já existiam
        ///
        /// A folha de sprites é gerada pro **preview de seek** do player — é ela
        /// que desenha o rolo de miniaturas. São quadros do próprio filme, já
        /// servidos e já cacheados: um herói que troca de cena não pediu nada
        /// novo ao servidor.
        ///
        /// ⚠️ **Só cenas que você já viu.** Os quadros saem do trecho entre o
        /// começo e `ondeParou` — nunca depois. Um herói de «continuar
        /// assistindo» que mostrasse o terceiro ato seria um spoiler entregue por
        /// quem devia estar te convidando a voltar.
        val folha by produceState<FolhaDeSprites?>(null, item.arquivoId) {
            val arquivo = item.arquivoId
            value = if (arquivo == null || folhaDoFilme == null) null else folhaDoFilme(arquivo)
            /// ⚠️ **Este log fica**, e é sobre um silêncio.
            ///
            /// Quando não há folha o herói não quebra: ele fica na arte estática,
            /// exatamente como antes. É o §24 funcionando, e é também o pior tipo
            /// de defeito pra diagnosticar depois — «não mudou nada» não diz se a
            /// causa foi arquivo sem id, filme mal começado, ou folha inexistente.
            ///
            /// A folha vem de um trabalho em lote (`POST /api/scrub`, na página
            /// do Servidor), não sob demanda. Um filme que nunca entrou nesse
            /// lote responde 404 — e 404 aqui quer dizer «ainda não foi gerada»,
            /// não «deu erro».
            android.util.Log.i(
                "odeon-heroi",
                "arquivo=$arquivo parou=${item.ondeParou} " +
                    "folha=${value?.quantosQuadros?.let { "$it quadros" } ?: "não gerada"}",
            )
        }
        val cenas = remember(folha, item.ondeParou) { cenasJaVistas(folha, item.ondeParou) }
        val urlDaTira = remember(folha) { folha?.let { urlDaFolha(it.path) } }


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

        /// ## ⚠️ A prévia em vídeo, e o decodificador que é **um só**
        ///
        /// O pedido do dono foi «um videozinho rodando de uma cena aleatória», e
        /// a peça mais perigosa desta casa está exatamente aqui. O comentário do
        /// player já dizia, antes de esta prévia existir:
        ///
        /// > «solta o **decodificador de hardware** — e numa TCL ele é um só,
        /// > então quem o segura impede o próximo filme de abrir.»
        ///
        /// Um fundo decorativo que segura o decodificador é um fundo que impede
        /// alguém de assistir. Por isso a prévia é cercada por quatro condições,
        /// e nenhuma delas é enfeite:
        ///
        /// | | |
        /// |---|---|
        /// | **só toca direto** | transcodificar pra enfeitar um fundo põe a máquina da casa a trabalhar porque alguém está *olhando* a tela, sem ter pedido nada |
        /// | **só depois de 3s parado** | passar pela biblioteca a caminho de outra tela não deve acender decodificador nenhum |
        /// | **solta no `onDispose` e no `ON_STOP`** | as duas, porque a ordem entre desmontar a biblioteca e montar o player não é garantida |
        /// | **sem som** | é fundo. Fundo que fala interrompe |
        val ondeComecar = remember(item.arquivoId, item.ondeParou) {
            umaCenaJaVista(item.ondeParou)
        }
        val plano by produceState<PlanoDeReproducao?>(null, item.arquivoId) {
            val arquivo = item.arquivoId
            if (arquivo == null || planoDoArquivo == null) return@produceState
            /// A folga de 3s: quem passa reto não paga decodificador.
            delay(3_000)
            val obtido = planoDoArquivo(arquivo)
            android.util.Log.i(
                "odeon-heroi",
                "plano de $arquivo: modo=${obtido?.mode ?: "nenhum"} " +
                    "direta=${obtido?.urlDireta != null} comecarEm=$ondeComecar",
            )
            value = obtido.takeIf { it?.eDireto == true }
        }

        val urlDaPrevia = remember(plano) { plano?.let { urlDeMidia(it.urlDireta) } }

        if (urlDaPrevia != null && ondeComecar != null) {
            PreviaDoHeroi(
                url = urlDaPrevia,
                comecarEm = ondeComecar,
                cabecalhos = cabecalhosDeMidia(),
                modifier = Modifier.fillMaxSize(),
            )
        }

        /// ⚠️ A arte estática fica **por baixo** e nunca sai: enquanto a folha
        /// carrega, e nos primeiros milissegundos de cada troca, é ela que
        /// preenche. O herói nunca pisca preto.
        val aFolha = folha
        if (aFolha != null && urlDaTira != null && cenas.isNotEmpty()) {
            var qual by remember(cenas) { mutableIntStateOf(0) }
            LaunchedEffect(cenas) {
                /// ⚠️ Seis segundos, e a troca é longa de propósito. Este é o
                /// fundo de uma tela que se navega: cena que muda depressa
                /// disputa com os cartazes, e o §4.2 é explícito — «ele não pode
                /// competir com um pôster».
                while (true) {
                    delay(6_000)
                    qual = (qual + 1) % cenas.size
                }
            }
            Crossfade(
                targetState = cenas[qual],
                animationSpec = tween(1_200),
                label = "cena do herói",
            ) { segundo ->
                QuadroDaFolha(aFolha, urlDaTira, segundo, Modifier.fillMaxSize())
            }
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


/// Os segundos de onde tirar cena — **só do trecho já assistido**.
///
/// ⚠️ Começa em 8% do visto e para em 96% dele, e as duas pontas têm motivo: o
/// começo é logotipo de estúdio e crédito de abertura, e o fim é a fronteira do
/// spoiler — o quadro seguinte é o que a pessoa ainda não viu.
///
/// Devolve vazio quando não há trecho suficiente: menos de dois minutos vistos
/// não dão cena nenhuma, e aí o herói fica na arte estática. Inventar quadro de
/// onde não há é exatamente o que a regra proíbe (§24).
internal fun cenasJaVistas(folha: FolhaDeSprites?, ondeParou: Double?): List<Int> {
    if (folha == null || folha.quantosQuadros <= 0) return emptyList()
    val visto = (ondeParou ?: 0.0).toInt()
    if (visto < 120) return emptyList()

    val comeco = (visto * 0.08).toInt()
    val fim = (visto * 0.96).toInt()
    if (fim <= comeco) return emptyList()

    /// ⚠️ Cinco cenas, espaçadas por igual. Mais vira apresentação de slides;
    /// menos, e o laço fica óbvio na segunda volta.
    val quantas = 5
    return (0 until quantas).map { comeco + (fim - comeco) * it / (quantas - 1) }
}

/// Um quadro da folha, recortado por **posicionamento** e não por bitmap.
///
/// ⚠️ A técnica é a mesma do rolo do celular: mede a imagem em `colunas × linhas`
/// o tamanho da caixa e a empurra pra que a célula certa caia na janela. Nada é
/// decodificado duas vezes, e a folha inteira é um `AsyncImage` só — que o Coil
/// já tem em cache, porque é o mesmo arquivo do preview de seek.
@Composable
private fun QuadroDaFolha(
    folha: FolhaDeSprites,
    url: String,
    segundo: Int,
    modifier: Modifier = Modifier,
) {
    val indice = (segundo / folha.intervaloSegundos)
        .toInt()
        .coerceIn(0, (folha.quantosQuadros - 1).coerceAtLeast(0))
    val coluna = indice % folha.columns
    val linha = indice / folha.columns

    Box(modifier) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.FillBounds,
            modifier = Modifier
                .fillMaxSize()
                .layout { medivel, restricoes ->
                    val larguraTotal = restricoes.maxWidth * folha.columns
                    val alturaTotal = restricoes.maxHeight * folha.rows
                    val posto = medivel.measure(
                        androidx.compose.ui.unit.Constraints.fixed(larguraTotal, alturaTotal),
                    )
                    layout(restricoes.maxWidth, restricoes.maxHeight) {
                        posto.place(
                            x = -coluna * restricoes.maxWidth,
                            y = -linha * restricoes.maxHeight,
                        )
                    }
                },
        )
    }
}


/// O «videozinho» do herói: uma cena, sem som, em laço curto.
///
/// ⚠️ **Este player nasce e morre com o herói, e isso é a regra de segurança.**
/// Ver o quadro de condições no [HeroiDaSala]: numa TCL o decodificador de
/// hardware é um só, e um fundo decorativo que o segura impede um filme de abrir.
///
/// ⚠️ Ele também solta no `ON_STOP`, e não só no `onDispose`. A ordem entre
/// desmontar a biblioteca e montar a tela do filme não é garantida pelo Compose;
/// o ciclo de vida chega antes e é o que fecha a janela em que os dois
/// existiriam ao mesmo tempo.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun PreviaDoHeroi(
    url: String,
    comecarEm: Int,
    cabecalhos: Map<String, String>,
    modifier: Modifier = Modifier,
) {
    val contexto = LocalContext.current
    val player = remember(url, comecarEm) {
        /// ⚠️ **O `Bearer` na fonte, e é a segunda vez que este projeto aprende
        /// isso.** A prévia deu `401` com a URL que já leva `?token=` — o mesmo
        /// erro, o mesmo código e a mesma causa dos canais de fora: as rotas de
        /// mídia querem o cabeçalho, e o token na query não vale por ele.
        ///
        /// Da primeira vez a resposta estava escrita no `web/src/hls.ts` e eu não
        /// procurei. Da segunda estava escrita no `RepositorioOdeon` desta casa,
        /// por mim, três dias antes.
        val fonte = androidx.media3.datasource.DefaultHttpDataSource.Factory()
            .setDefaultRequestProperties(cabecalhos)
            .setAllowCrossProtocolRedirects(true)

        ExoPlayer.Builder(contexto)
            .setMediaSourceFactory(
                androidx.media3.exoplayer.source.DefaultMediaSourceFactory(fonte),
            )
            .build()
            .apply {
            setMediaItem(MediaItem.fromUri(url), comecarEm * 1_000L)
            /// ⚠️ Sem som, e não é «volume zero por educação»: é fundo. Um fundo
            /// que fala interrompe quem está escolhendo o que ver.
            volume = 0f
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            prepare()
        }
    }

    /// ⚠️ **A prévia tem hora pra acabar**, e é uma trava de segurança, não de
    /// estética.
    ///
    /// Numa TV que fica ligada na biblioteca a tarde inteira, um decodificador
    /// preso por um fundo decorativo é um decodificador preso por horas. Depois
    /// de 45s ela solta e a arte estática reaparece — que é como a tela era antes
    /// desta prévia existir, então não se perde nada.
    LaunchedEffect(player) {
        delay(45_000)
        player.stop()
        player.clearMediaItems()
    }

    val dono = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(dono, player) {
        val observador = androidx.lifecycle.LifecycleEventObserver { _, evento ->
            /// ⚠️ `ON_PAUSE`, e não `ON_STOP`: ele chega **antes**, no instante
            /// em que outra tela toma a frente. Entre os dois há uma janela em
            /// que a prévia e o player do filme existiriam juntos — e nesta TV
            /// existir junto é um deles não abrir.
            if (evento == androidx.lifecycle.Lifecycle.Event.ON_PAUSE) {
                player.stop()
                player.clearMediaItems()
            }
        }
        dono.lifecycle.addObserver(observador)
        onDispose {
            dono.lifecycle.removeObserver(observador)
            player.stop()
            player.clearMediaItems()
            player.release()
        }
    }

    AndroidView(
        factory = { ctx ->
            androidx.media3.ui.PlayerView(ctx).apply {
                useController = false
                setBackgroundColor(android.graphics.Color.TRANSPARENT)
                setShutterBackgroundColor(android.graphics.Color.TRANSPARENT)
                layoutParams = android.view.ViewGroup.LayoutParams(
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                    android.view.ViewGroup.LayoutParams.MATCH_PARENT,
                )
                resizeMode = androidx.media3.ui.AspectRatioFrameLayout.RESIZE_MODE_ZOOM
            }
        },
        update = { it.player = player },
        modifier = modifier,
    )
}


/// Um segundo qualquer do trecho **já assistido** — o ponto onde a prévia entra.
///
/// ⚠️ A mesma regra de spoiler dos quadros parados, e pelo mesmo motivo: a prévia
/// é um convite pra voltar ao filme, e um convite não entrega o final. Sorteia
/// entre 8% e 96% do que já foi visto — fora a abertura, aquém da fronteira.
///
/// `null` quando não há trecho suficiente, e aí não há prévia: menos de dois
/// minutos vistos é abertura, não é cena.
internal fun umaCenaJaVista(ondeParou: Double?, sorteio: (Int) -> Int = { (0 until it).random() }): Int? {
    val visto = (ondeParou ?: 0.0).toInt()
    if (visto < 120) return null
    val comeco = (visto * 0.08).toInt()
    val fim = (visto * 0.96).toInt()
    if (fim <= comeco) return null
    return comeco + sorteio(fim - comeco)
}
