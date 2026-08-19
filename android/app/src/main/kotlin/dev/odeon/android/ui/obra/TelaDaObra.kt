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
import androidx.compose.foundation.layout.offset
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
import androidx.compose.ui.draw.shadow
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
    /// Onde o filme está segundo o player que acabou de fechar, em segundos.
    /// `null` quando se chegou aqui navegando — e aí vale o que o servidor diz.
    ///
    /// ## ⚠️ Ela ganha da releitura, e é esse o ponto
    ///
    /// Sair do player dispara a marca de `abandon` e a releitura desta ficha
    /// **ao mesmo tempo**. Quando a leitura chega antes da escrita, o
    /// `obra.ondeParou` relido é o de **antes** da sessão que acabou de
    /// acontecer — e o botão dizia `assistir` pra quem tinha acabado de ver
    /// meia hora. A dica é o que o app viu com os próprios olhos 200ms atrás;
    /// não há releitura mais fresca que isso.
    dicaDeOndeParou: Double? = null,
    aoVoltar: () -> Unit,
    /// Como se chama o lugar de onde se veio — «biblioteca», «Temporada 1»…
    /// Ver o rótulo do botão lá embaixo.
    voltaPara: String = "biblioteca",
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
            /// ⚠️ O rótulo diz **pra onde volta**, e não «biblioteca» sempre.
            /// Vindo de uma temporada, o botão levava à temporada e escrevia
            /// «biblioteca»: o texto contando uma história e o toque outra —
            /// visto no emulador em 18/08/2026.
            Text("‹ $voltaPara", color = Cores.destaque)
        }

        /// ## A fachada: a marquise, e o varal pendurado nela
        ///
        /// Era pôster à esquerda e quatro linhas de metadado à direita. Virou o
        /// letreiro de lâmpadas com as fotos de cena penduradas embaixo — o
        /// porquê inteiro está no [Marquise], e o resumo é que esta era a única
        /// tela do app que não sabia que o app é um cinema.
        ///
        /// ⚠️ **O selo do plano entra dentro do letreiro**, e não solto abaixo
        /// dele: estado é coisa da fachada, e é onde a lâmpada do player já mora.
        Marquise(
            titulo = obra.title,
            /// §24 aplicado **antes** de montar a linha: o que não existe não
            /// deixa um `·` solto pra trás.
            linhaDeBaixo = listOfNotNull(
                obra.tituloOriginal?.takeIf { it != obra.title },
                obra.year?.toString(),
                obra.duracaoEmSegundos?.let { duracao(it) },
            ).joinToString(" · "),
            plano = { Selo(plano = estado.plano, carregando = estado.planoCarregando) },
        )

        /// ⚠️ **Sem margem entre a marquise e o varal**, e é o ponto do desenho:
        /// o fio nasce nos cantos de baixo do letreiro. Um respiro aqui soltaria
        /// a corda no ar e as duas metáforas voltariam a ser duas.
        Varal(
            cenas = estado.cenas,
            urlDaCena = { cena -> modelo.capa(cena.imagem) },
            /// ⚠️ **Não é uma rota nova** — é o mesmo `aoTocar` da ficha, com o
            /// segundo da cena no lugar da posição salva. O player não precisa
            /// saber que veio de uma foto pendurada: pra ele é «abra este arquivo
            /// neste ponto», que é o que ele já faz.
            aoTocarNaCena = { cena ->
                estado.arquivo?.let { arquivo ->
                    aoTocar(
                        arquivo.id,
                        obra.title,
                        cena.segundos,
                        arquivo.duracaoEmSegundos ?: obra.duracaoEmSegundos,
                        modelo.capa(obra.artwork["poster"]),
                    )
                }
            },
            /// ⚠️ `offset` e não `padding` negativo — ver a nota gêmea no
            /// `Marquise`. Padding negativo é `IllegalArgumentException` em tempo
            /// de execução, e só a tela denuncia.
            modifier = Modifier.offset(y = (-12).dp),
        )

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
                        rotulo = etiqueta.rotulo,
                        valor = etiqueta.value,
                        cor = corDeHex(etiqueta.color),
                    )
                }
            }
        }

        Reproduzir(
            estado = estado,
            dicaDeOndeParou = dicaDeOndeParou,
            /// A duração sai do **arquivo** e só cai pra da obra se ele não
            /// trouxer: a do arquivo é o que o probe mediu naquele rip; a da
            /// obra é o tempo de execução do catálogo, que pode divergir de um
            /// corte pro outro.
            aoTocar = { arquivo ->
                aoTocar(
                    arquivo.id,
                    obra.title,
                    /// ⚠️ **Não é `obra.ondeParou` cru** — filme terminado
                    /// recomeça do zero, e as três condições são da web. Ver
                    /// `dados.ondeContinuar`.
                    dev.odeon.android.dados.ondeContinuar(
                        ondeParou = dicaDeOndeParou ?: obra.ondeParou,
                        duracaoEmSegundos = arquivo.duracaoEmSegundos ?: obra.duracaoEmSegundos,
                        finished = obra.finished,
                    ),
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

        /// ## Os dois canhotos, lado a lado
        ///
        /// > «Você esqueceu de atualizar esses dois tb»
        ///
        /// E estava certo: a marquise, o varal e o bilhete entraram, e estas duas
        /// continuaram dois `TextButton` de cinza apagado, empilhados, sem nada em
        /// volta — a tela velha sobrevivendo no rodapé. Viraram talões arrancados
        /// do bilhete, com o porquê inteiro no [Canhoto].
        ///
        /// ⚠️ **`weight(1f)` nos dois**, e não largura fixa: `pegar a fita na
        /// locadora` tem quase o dobro dos caracteres de `baixar…`, e duas
        /// larguras diferentes lado a lado leriam como hierarquia diferente. Elas
        /// têm o mesmo peso — a hierarquia que importa é entre elas e o bilhete.
        ///
        /// O toque de pegar a fita continua sendo `LongPress`, e o motivo é o de
        /// sempre: pegar uma fita **escreve no acervo de três pessoas** — com a
        /// escassez ligada, a caixa sai da estante de todo mundo. A mão deve
        /// sentir que isto não é o mesmo que virar uma caixa pra ler o verso.
        val haptico = LocalHapticFeedback.current
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            /// Baixar só existe quando há arquivo — §53, e a mesma condição de
            /// antes. Sem ele o canhoto não nasce, e o da locadora fica sozinho
            /// na fileira em vez de dividir espaço com um botão morto.
            if (estado.temComoTocar) {
                Canhoto(
                    rotulo = "baixar pra ver sem rede",
                    glifo = { desenharBaixar() },
                    habilitado = true,
                    aoTocar = { estado.arquivo?.let { aoBaixar(it.id) } },
                    modifier = Modifier.weight(1f),
                )
            }
            Canhoto(
                rotulo = if (estado.pegando) "pegando…" else "pegar a fita na locadora",
                glifo = {
                    desenharFita(if (estado.pegando) Cores.destaqueApagado else Cores.destaque)
                },
                habilitado = !estado.pegando,
                aoTocar = {
                    haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                    modelo.pegarAFita()
                },
                modifier = Modifier.weight(1f),
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
private fun Reproduzir(
    estado: EstadoDaObra,
    /// Ver o parâmetro homônimo da [TelaDaObra] — o rótulo do botão sai da mesma
    /// conta que decide a posição, então a dica precisa chegar até aqui.
    dicaDeOndeParou: Double?,
    aoTocar: (ArquivoDeMidia) -> Unit,
) {
    val arquivo = estado.arquivo

    when {
        !estado.temComoTocar -> Text(
            text = "sem arquivo no acervo",
            style = MaterialTheme.typography.bodySmall,
            color = Cores.textoApagado,
        )

        /// O botão principal do app virou **bilhete** — ver [Bilhete].
        ///
        /// Ele já tinha sido aceso uma vez (o halo dourado saindo dele), e
        /// continuava sendo um retângulo arredondado. O ingresso dá forma ao
        /// gesto e abre lugar pro «de onde parou» sem inventar uma linha de
        /// metadado ao lado dele.
        else -> {
            /// ⚠️ O rótulo sai da **mesma** função que decide a posição, e não de
            /// uma condição parecida escrita ao lado. Prometer «continuar» e
            /// começar do zero é o §8b visto do outro lado — e duas regras que
            /// deveriam concordar divergem no dia em que alguém mexer numa só.
            val obra = estado.obra
            val de = obra?.let {
                dev.odeon.android.dados.ondeContinuar(
                    ondeParou = dicaDeOndeParou ?: it.ondeParou,
                    duracaoEmSegundos = arquivo?.duracaoEmSegundos ?: it.duracaoEmSegundos,
                    finished = it.finished,
                )
            } ?: 0.0

            Bilhete(
                chamada = if (de > 0) "continuar · ${relogioCurto(de)}" else "assistir",
                sobrelinha = if (de > 0) "SESSÃO · DE ONDE PAROU" else "SESSÃO · DO COMEÇO",
                aoTocar = { arquivo?.let(aoTocar) },
            )
        }
    }
}

/// `1:04:51`, ou `4:51` num filme curto. É a mesma leitura do relógio do player,
/// e a razão de a hora sumir quando é zero é a mesma: um `0:04:51` faz quem lê
/// procurar a hora que não existe.
private fun relogioCurto(segundos: Double): String {
    val total = segundos.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    /// `Locale.ROOT`: o relógio é o mesmo em qualquer idioma, e um locale de
    /// dígitos próprios escreveria a hora em outro alfabeto.
    val ptBr = java.util.Locale.ROOT
    return if (h > 0) {
        String.format(ptBr, "%d:%02d:%02d", h, m, s)
    } else {
        String.format(ptBr, "%d:%02d", m, s)
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
