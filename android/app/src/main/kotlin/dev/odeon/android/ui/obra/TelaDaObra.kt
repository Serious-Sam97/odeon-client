package dev.odeon.android.ui.obra

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.ArquivoDeMidia
import dev.odeon.android.dados.PlanoDeReproducao
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.MolduraDoCartaz
import dev.odeon.android.ui.inclinacao
import dev.odeon.android.ui.PilulaDeEtiqueta
import dev.odeon.android.ui.corDeHex

/// A ficha da obra — a terceira tela do app, e a porta do player.
///
/// ## Pela biblioteca é livre, e a escassez fica na locadora
///
/// Esta tela já teve o funil do §66 dentro dela, e ele trancava o acervo
/// inteiro. O alcance da regra foi corrigido pelo dono: **a escassez vale no
/// modo locadora; pela biblioteca se assiste direto.** O porquê e o que os
/// documentos ainda dizem estão em `ModeloDaObra`.
///
/// O `@OptIn` é do `FlowRow`, usado pelas etiquetas: experimental de assinatura,
/// estável de comportamento, e o único jeito de ter `flex-wrap` sem escrever
/// medição de linha à mão.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaDaObra(
    modelo: ModeloDaObra,
    aoVoltar: () -> Unit,
    aoTocar: (arquivoId: String, titulo: String, ondeParou: Double, duracao: Double?, capa: String?) -> Unit,
    aoBaixar: (arquivoId: String) -> Unit = {},
    /// A outra ponta da transição compartilhada — ver `MolduraDoCartaz`.
    moldura: MolduraDoCartaz = MolduraDoCartaz.Nenhuma,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    if (estado.carregando) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cores.destaque)
        }
        return
    }

    val obra = estado.obra
    if (obra == null) {
        Column(
            modifier = Modifier.fillMaxSize().padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = estado.erro ?: "não deu pra abrir a ficha",
                color = Cores.perigo,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.bodyMedium,
            )
            TextButton(onClick = modelo::tentarDeNovo) { Text("tentar de novo") }
            TextButton(onClick = aoVoltar) { Text("voltar") }
        }
        return
    }

    /// A paralaxe da R8: a arte se move dentro da moldura conforme o aparelho
    /// inclina. Ver `Inclinacao.kt` — inclusive o porquê de ela sumir sozinha
    /// pra quem desligou animação no sistema.
    val tilt by inclinacao()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        /// O backdrop, **borda a borda** — R8.
        ///
        /// ## Ele sobe até debaixo da barra de status, e o texto não
        ///
        /// É a única forma de "borda a borda" que vale a pena: arte encostando
        /// no topo do vidro, e **conteúdo** respeitando as áreas seguras. Fazer
        /// o contrário — texto sob o relógio — é borda a borda que ninguém
        /// pediu.
        ///
        /// Por isso o `safeDrawingPadding` saiu do `AppOdeon` só pra esta tela:
        /// lá ele empurrava a tela inteira, backdrop incluído, e uma arte que
        /// começa 60dp abaixo do topo é uma arte com uma tarja preta em cima.
        ///
        /// ## Sem backdrop, isto não desenha nada
        ///
        /// §24. Uma faixa de 220dp vazia no topo de metade das fichas seria pior
        /// que não ter faixa — e é metade mesmo: 8.598 das 17.930 obras não têm
        /// arte nenhuma.
        val backdrop = modelo.capa(obra.artwork["backdrop"])
        if (backdrop != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(220.dp)
                    /// ⚠️ **`clipToBounds` é obrigatório aqui**, e o screenshot
                    /// mostrou por quê: a arte é desenhada 4% maior pra a
                    /// paralaxe ter folga, e sem recorte ela **vaza** os 220dp
                    /// da faixa — aparecia uma tira de pôster solta abaixo do
                    /// degradê, com uma aresta dura no meio da tela.
                    .clipToBounds(),
            ) {
                AsyncImage(
                    model = backdrop,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .fillMaxSize()
                        /// A paralaxe: 6dp de deslocamento sobre uma arte 4%
                        /// maior que a moldura. A folga é o que evita a borda
                        /// aparecer quando a arte anda — sem ela, inclinar
                        /// mostraria o fundo num dos lados.
                        .graphicsLayer {
                            translationX = tilt.x * 6.dp.toPx()
                            translationY = tilt.y * 6.dp.toPx()
                            scaleX = 1.04f
                            scaleY = 1.04f
                        },
                )
                /// A lavagem que dá chão ao que vem embaixo, e escurece a arte
                /// sob a barra de status pros ícones do sistema continuarem
                /// legíveis — que é a parte de "borda a borda" que costuma ser
                /// esquecida.
                /// A lavagem, e os três pontos dela têm motivo.
                ///
                /// | | por quê |
                /// |---|---|
                /// | topo a 55% | escurece a arte **sob a barra de status**, pros ícones do sistema continuarem legíveis — a parte de "borda a borda" que costuma ser esquecida |
                /// | meio a 25% | o screenshot mostrou o problema: com o meio **transparente**, um backdrop claro (a neve do 007) vira uma faixa branca gritando no meio da tela escura |
                /// | base opaca | é o que faz a faixa **acabar** em vez de ser cortada |
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Cores.fundo.copy(alpha = 0.55f),
                            0.45f to Cores.fundo.copy(alpha = 0.25f),
                            1f to Cores.fundo,
                        ),
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                /// ⚠️ **O topo sai do inset quando há backdrop**, e o screenshot
                /// mostrou o defeito: com o `safeDrawing` inteiro, o conteúdo
                /// ganhava a altura da barra de status **de novo**, embaixo de
                /// uma faixa que já tinha passado por baixo dela. O resultado era
                /// um vão de ~40dp entre a arte e o "‹ biblioteca".
                ///
                /// Sem backdrop o topo volta pro inset, porque aí não há nada
                /// desenhado sob a barra de status pra abrir espaço.
                .windowInsetsPadding(
                    if (backdrop != null) {
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        )
                    } else {
                        WindowInsets.safeDrawing
                    },
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
        TextButton(onClick = aoVoltar, contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
            Text("‹ biblioteca", color = Cores.destaque)
        }

        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            /// O pôster em 2:3, a mesma proporção da grade — a ficha tem que
            /// parecer a continuação do cartão em que se tocou, não outra tela.
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .aspectRatio(2f / 3f)
                    .then(moldura.de(obra.id))
                    .clip(RoundedCornerShape(6.dp)),
            ) {
                val poster = modelo.capa(obra.artwork["poster"])
                if (poster != null) {
                    AsyncImage(
                        model = poster,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }

            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = obra.title,
                    style = MaterialTheme.typography.headlineSmall,
                    color = Cores.texto,
                )
                /// §24: cada linha só existe se tiver o que dizer. Nada de "—".
                obra.tituloOriginal?.takeIf { it != obra.title }?.let {
                    Text(it, style = MaterialTheme.typography.bodySmall, color = Cores.textoApagado)
                }
                obra.year?.let {
                    Text("$it", style = MaterialTheme.typography.bodyMedium, color = Cores.textoApagado)
                }
                obra.duracaoEmSegundos?.let {
                    Text(duracao(it), style = MaterialTheme.typography.bodySmall, color = Cores.textoApagado)
                }
            }
        }

        Selo(plano = estado.plano, carregando = estado.planoCarregando)

        obra.overview?.takeIf { it.isNotBlank() }?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Cores.texto)
        }

        /// As etiquetas — R3.
        ///
        /// ## Elas vêm do servidor desde sempre, e o app descartava
        ///
        /// `tags` está no `WorkDetail` da web e o `ObraDetalhada` não a
        /// declarava: o comentário do modelo dizia que campo sem tela que o leia
        /// é contrato que ninguém confere, e estava certo — até esta tela
        /// existir.
        ///
        /// ⚠️ **Sem etiqueta, nada é desenhado** — nem rótulo de seção, nem
        /// "nenhuma". §24: linha vazia some. A web escreve "nenhuma" aqui, e é
        /// escolha dela: lá a seção é editável por administrador e o vazio é
        /// convite pra preencher. Aqui não há edição, então o vazio não é
        /// convite pra nada.
        if (obra.tags.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                obra.tags.forEach { etiqueta ->
                    PilulaDeEtiqueta(
                        namespace = etiqueta.namespace,
                        valor = etiqueta.value,
                        cor = corDeHex(etiqueta.color),
                    )
                }
            }
        }

        Reproduzir(
            estado = estado,
            /// A duração sai do **arquivo** e só cai pra da obra se ele não
            /// trouxer: a do arquivo é o que o probe mediu naquele rip; a da
            /// obra é o tempo de execução do catálogo, que pode divergir de um
            /// corte pro outro.
            aoTocar = { arquivo ->
                aoTocar(
                    arquivo.id,
                    obra.title,
                    obra.ondeParou,
                    arquivo.duracaoEmSegundos ?: obra.duracaoEmSegundos,
                    /// A capa viaja daqui pro controle de mídia — R9.
                    ///
                    /// A ficha é o único lugar da pilha que **tem** a arte da
                    /// obra: o player recebe um arquivo, e arquivo não sabe de
                    /// pôster. Sem este parâmetro, a notificação teria que
                    /// reperguntar a ficha só pra saber que imagem desenhar.
                    modelo.capa(obra.artwork["poster"]),
                )
            },
        )

        /// Baixar.
        ///
        /// Fica **abaixo** do assistir e mais discreto: baixar é a exceção, e
        /// quem abriu a ficha quase sempre veio pra ver agora. Dar o mesmo peso
        /// aos dois faria a tela perguntar uma coisa que já estava respondida.
        if (estado.temComoTocar) {
            TextButton(
                onClick = { estado.arquivo?.let { aoBaixar(it.id) } },
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text("baixar pra ver sem rede", color = Cores.textoApagado)
            }
        }

        /// Pegar a fita.
        ///
        /// ## Ela existe mesmo com a biblioteca livre, e o motivo é a locadora
        ///
        /// Desde o §71 não é preciso empréstimo pra assistir. Pegar a fita virou
        /// gesto **da locadora** — tirar a caixa da estante, com prazo e com
        /// escassez, que é a parte de jogo do produto. Quem pega quer a fita, não
        /// a permissão.
        /// O toque de pegar a fita — R5, e é a primeira vez que o app usa o
        /// corpo do aparelho.
        ///
        /// `LongPress` é o mais encorpado dos dois tipos que o Compose expõe, e
        /// é o certo aqui: pegar uma fita **escreve no acervo de três pessoas**
        /// — com a escassez ligada, a caixa sai da estante de todo mundo. A mão
        /// deve sentir que isto não é o mesmo que virar uma caixa pra ler o
        /// verso, que leva o tique seco (`TextHandleMove`, em `TelaDaLocadora`).
        val haptico = LocalHapticFeedback.current
        TextButton(
            onClick = {
                haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                modelo.pegarAFita()
            },
            enabled = !estado.pegando,
            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
        ) {
            Text(
                text = if (estado.pegando) "pegando…" else "pegar a fita na locadora",
                color = Cores.textoApagado,
            )
        }

        estado.recadoDaLocadora?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Cores.destaque)
        }

        Versoes(
            arquivos = obra.files,
            escolhido = estado.arquivo,
            aoEscolher = modelo::escolherArquivo,
        )
        }
    }
}

/// O selo do modo, e o porquê dele.
///
/// A decisão foi de mostrá-lo **aqui e no player**. Aqui ele responde antes do
/// toque: num servidor de casa que atende três pessoas, saber que aquele arquivo
/// vai fazer o servidor re-encodar é informação que muda a escolha da versão.
///
/// O `reasons` vem escrito pelo servidor e vai para a tela como veio. Reescrever
/// aqui seria a terceira redação da mesma frase — a web já resistiu à mesma
/// tentação no `label` das legendas.
@Composable
private fun Selo(plano: PlanoDeReproducao?, carregando: Boolean) {
    /// Enquanto não sei, não escrevo nada. §24 outra vez: o vazio some, não vira
    /// "carregando…" piscando em cima de uma tela que já tem o que mostrar.
    if (plano == null) {
        if (carregando) return
        return
    }

    val rotulo = when (plano.mode) {
        "direct_play" -> "direto"
        "direct_stream" -> "remux"
        "transcode" -> "transcodificando"
        else -> plano.mode
    }

    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(6.dp))
            .background(Cores.fundoElevado)
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(
            text = rotulo,
            style = MaterialTheme.typography.labelLarge,
            color = if (plano.eDireto) Cores.certo else Cores.destaque,
        )
        plano.reasons.forEach {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Cores.textoApagado)
        }
    }
}

/// O play — ou o silêncio dele.
///
/// ## Sem arquivo não há botão, e a tela diz isso
///
/// Sobrou **um** caso de esconder, e ele não é da locadora: obra sem arquivo no
/// acervo. Ela existe — é linha de catálogo identificada cuja mídia não está
/// aqui — e não toca de jeito nenhum.
///
/// Esconder e calar seria o §8b, o toque que não acontece sem ninguém entender
/// por quê. Então some o botão e fica a frase, que é o §53 e o §8b concordando:
/// não oferecer o que vai ser negado, e não negar em silêncio.
@Composable
private fun Reproduzir(estado: EstadoDaObra, aoTocar: (ArquivoDeMidia) -> Unit) {
    val arquivo = estado.arquivo

    when {
        !estado.temComoTocar -> Text(
            text = "sem arquivo no acervo",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.textoApagado,
        )

        else -> Button(
            onClick = { arquivo?.let(aoTocar) },
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (estado.obra?.ondeParou?.let { it > 1 } == true) "continuar" else "assistir")
        }
    }
}

/// As versões do mesmo filme.
///
/// ## Elas aparecem todas, e a decisão é essa
///
/// A grade já mostra `007 Contra Goldfinger (1964)` duas vezes, e a razão pode
/// ser dublagem ou legenda diferente — que é exatamente o tipo de coisa que se
/// escolhe na hora de assistir, não algo que o app deva decidir sozinho.
///
/// Some quando há uma só: uma lista de um item é ruído, e o §24 manda a linha
/// vazia sumir.
@Composable
private fun Versoes(
    arquivos: List<ArquivoDeMidia>,
    escolhido: ArquivoDeMidia?,
    aoEscolher: (ArquivoDeMidia) -> Unit,
) {
    if (arquivos.size < 2) return

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("versões", style = MaterialTheme.typography.labelLarge, color = Cores.textoApagado)

        arquivos.forEach { arquivo ->
            val eEste = arquivo.id == escolhido?.id
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (eEste) Cores.fundoElevado else Cores.fundoAfundado)
                    .clickable { aoEscolher(arquivo) }
                    .padding(10.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = arquivo.filename,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (eEste) Cores.texto else Cores.textoApagado,
                )
                /// A segunda linha é montada só com o que existe, e some inteira
                /// quando não existe nada — o mesmo corolário do §24 que a grade
                /// já segue na linha do ano.
                ficha(arquivo)?.let {
                    Text(it, style = MaterialTheme.typography.labelSmall, color = Cores.textoApagado)
                }
            }
        }
    }
}

/// O tamanho do arquivo, e por que ele mora **aqui** e não na grade.
///
/// A R4 do redesenho pedia `1969 · 816p · 2h22 · 2,3 GB` no cartaz da grade, e
/// foi o que a primeira versão fez. O screenshot mostrou a linha truncando num
/// cartaz de 108dp — `1969 · 816p · 2h22 · …` —, e a reticência era o defeito:
/// ela promete um dado que nenhum gesto daquela tela alcança.
///
/// Esta é a tela certa pra ele de qualquer forma. O tamanho não ajuda a escolher
/// o que assistir; ele importa antes de **baixar**, e o botão de baixar está a
/// dois dedos daqui.
///
/// Vírgula decimal porque o app é em português. **Base 1000 e não 1024**, que é
/// a mesma escolha da web: este número é o que a pessoa compara com o espaço
/// livre que o Android mostra, e o Android usa base 1000 desde o Oreo. Com 1024
/// o mesmo arquivo apareceria como "2,1 GB" aqui e "2,3 GB" nos ajustes — e o
/// §18 vale também pra unidade.
private fun tamanho(bytes: Long): String {
    val gb = bytes / 1_000_000_000.0
    if (gb >= 1) return "%.1f GB".format(gb).replace('.', ',')
    val mb = bytes / 1_000_000.0
    return "${mb.toLong()} MB"
}

private fun ficha(a: ArquivoDeMidia): String? = listOfNotNull(
    a.height?.let { "${it}p" },
    a.tamanhoEmBytes?.let { tamanho(it) },
    a.codecDeVideo,
    a.codecDeAudio,
    a.canaisDeAudio?.let { "${it}ch" },
    a.idiomasDeLegenda.takeIf { it.isNotEmpty() }?.joinToString("/"),
).joinToString(" · ").takeIf { it.isNotBlank() }

private fun duracao(segundos: Double): String {
    val total = segundos.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m}min"
}
