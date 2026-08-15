package dev.odeon.android.tv.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.corDeHex

/// As peças que toda tela da sala usa.

/// O rótulo dentro de um botão. Separado porque o `BotaoDaSala` mora em
/// `Foco.kt`, que é sobre foco, e não sobre tipografia.
@Composable
internal fun RotuloDoBotao(rotulo: String, tinta: Color) {
    /// ## ⚠️ Mais ar, e versalete
    ///
    /// «Os botões estão simples e feios», e a causa era medida: `26×14` de folga
    /// num botão que a três metros tem o tamanho de uma unha. Botão de TV não é
    /// botão de dedo — ele é lido de longe e apertado de longe, e precisa de
    /// corpo pra parecer um alvo.
    ///
    /// O versalete com espaçamento é a mesma voz dos rótulos desta casa. Um botão
    /// escrito em caixa baixa apertada some ao lado de um título em serifada de
    /// 62sp; escrito assim, ele **responde** ao título em vez de sumir.
    Text(
        text = rotulo,
        /// ⚠️ O espaçamento do rótulo cai pela metade num botão: `0.28em` é ar
        /// pra um versalete solto, e dentro de uma pílula ele estica a palavra até
        /// ela expulsar a vizinha da fileira — na TCL o `voltar` sumiu por isso.
        /// ⚠️ **16sp fixo, e não `titleMedium`.**
        ///
        /// O `titleMedium` do tema de TV é grande de propósito — ele é para
        /// títulos —, e empilhar `32×20` de folga em cima dele fez o botão virar
        /// um bloco: na foto do dono, o `▸ continuar de 1h14` ocupava mais altura
        /// que o título do filme, e a fileira quebrou em três linhas.
        ///
        /// Um botão não é um título. Ele precisa ser **legível** a três metros, e
        /// 16sp com folga curta já é — o que ele não pode é competir com o nome do
        /// filme, que é a coisa que a tela existe pra dizer.
        style = MaterialTheme.typography.titleMedium.copy(fontSize = 16.sp),
        color = tinta,
        maxLines = 1,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 9.dp),
    )
}

/// O versalete que encabeça uma fileira, com a régua em degradê ao lado.
///
/// Copiado do `.strip h2` da web, e do `RotuloDeSecao` do celular — mesma forma,
/// tamanho da sala (ver `TipoDaSala.rotulo`). A régua existe porque um rótulo
/// curto sobre uma fileira larga fica órfão no canto; ela liga o texto ao que
/// ele nomeia.
///
/// A caixa alta é aplicada **aqui**, e não no `TextStyle`: `TextStyle` não tem
/// `text-transform`, e a regra da casa é que o texto se escreva em minúscula no
/// código.
@Composable
fun RotuloDeSecao(texto: String, modifier: Modifier = Modifier, numero: Int? = null) {
    Row(
        modifier.fillMaxWidth().padding(bottom = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = texto.uppercase(),
            style = TipoDaSala.rotulo,
            color = Cores.destaque,
        )
        Spacer(Modifier.width(14.dp))
        Box(
            Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Cores.destaqueApagado, Color.Transparent),
                    ),
                ),
        )
        /// ⚠️ **A contagem na ponta direita da régua** — §2.7: «o rótulo de seção
        /// carrega a contagem na ponta direita da régua (`CONTINUAR ──── 15`)».
        ///
        /// Ela vai depois do `weight(1f)`, então a régua **encolhe** pra caber o
        /// número em vez de o número empurrar a régua pra fora. E é `Int?` porque
        /// nem toda seção tem contagem: a §2.7 é explícita — «a biblioteca tem, o
        /// guia não».
        if (numero != null) {
            Spacer(Modifier.width(14.dp))
            Text(
                text = "$numero",
                style = MaterialTheme.typography.labelLarge,
                color = Cores.textoApagado,
            )
        }
    }
}

/// Uma fileira nomeada, que rola na horizontal.
///
/// ## O `contentPadding` é o overscan, e ele não pode virar `padding`
///
/// A diferença importa e é fácil de errar. Com `Modifier.padding`, a `LazyRow`
/// inteira encolhe: os cartazes param a 48dp da borda **e a rolagem também**, o
/// que faz o primeiro item nunca chegar à esquerda da tela. Com
/// `contentPadding`, a lista ocupa a largura toda e o **conteúdo** é que começa
/// afastado — a fileira corre borda a borda, como as da Netflix.
@Composable
fun Fileira(
    titulo: String,
    modifier: Modifier = Modifier,
    conteudo: androidx.compose.foundation.lazy.LazyListScope.() -> Unit,
) {
    Column(modifier) {
        RotuloDeSecao(titulo, Modifier.padding(horizontal = Sala.overscanH))
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
            contentPadding = PaddingValues(horizontal = Sala.overscanH),
            content = conteudo,
        )
    }
}

/// Um cartaz em pé — a capa de uma obra.
///
/// ## O que ele desenha quando não há pôster, e por que isso importa tanto
///
/// **4.794 obras do acervo não têm pôster**, contra 2.096 que têm. Ou seja: o
/// caminho sem imagem não é o caso de erro, é o caso **comum**. Um cartaz que
/// desenha um retângulo cinza quando falta arte deixaria a maior parte da
/// biblioteca cinza.
///
/// O que ele faz em vez disso é o que a web e o celular já fazem: pinta o fundo
/// com a `dominant_color` da obra — que vem na mesma linha da listagem, sem
/// custar requisição nenhuma — e escreve o título por cima. Fica um objeto com
/// identidade, e a grade parece o acervo antes de a primeira imagem chegar.
@Composable
fun Cartaz(
    titulo: String,
    /// A largura, pra quem mede diferente. A locadora mede — ver
    /// `Sala.cartazLdaEstante`.
    largura: androidx.compose.ui.unit.Dp = Sala.cartazL,
    altura: androidx.compose.ui.unit.Dp = Sala.cartazA,
    arte: String?,
    aoEscolher: () -> Unit,
    modifier: Modifier = Modifier,
    /// A `dominant_color` da obra, em `#RRGGBB`. Ver `corDeHex` no `:core`.
    cor: String? = null,
    /// De 0 a 1. Quando > 0, a barrinha aparece **dentro** do cartaz — é a R4 do
    /// celular, e vale igual aqui: o progresso é do objeto, não uma legenda
    /// embaixo dele.
    andado: Float = 0f,
    /// A linha debaixo do título — ano, temporada, o que a tela souber.
    detalhe: String? = null,
    /// Quantas linhas o detalhe pode ocupar.
    ///
    /// ⚠️ **Uma por padrão, e é de propósito.** Na biblioteca e na locadora o
    /// detalhe é um dado curto — `1998 · 湯山邦彦` —, e deixá-lo crescer faria
    /// cartazes vizinhos terem alturas diferentes conforme o tamanho do nome do
    /// diretor. Fileira de cartaz é grade, e grade quer altura igual.
    ///
    /// No «para você» o detalhe é o **motivo**, que é o assunto daquela tela, e
    /// lá ele pede três. Quem sabe qual é o caso é quem chama.
    linhasDoDetalhe: Int = 1,
    escolhivel: Boolean = true,
    /// Pra onde a seta ◀ vai quando este é o item mais à esquerda da fileira.
    ///
    /// ⚠️ Sem isto a fileira **enrola**: medido na TCL em 12/08/2026, ◀ no
    /// primeiro cartão levava pro **último** da mesma fileira, e de lá não saía
    /// mais. Ver o comentário do `saidaEsquerda` no `Sala.kt`.
    saidaEsquerda: FocusRequester? = null,
) {
    /// ## ⚠️ Quem cresce é o cartão inteiro, e não só a arte
    ///
    /// Visto na TCL em 12/08/2026: com a escala aplicada só ao `Focavel` da
    /// arte, o pôster focado crescia 12% **por cima do próprio título** — os
    /// 300dp de altura ganhavam 36, e metade disso descia em cima das duas
    /// linhas de texto logo abaixo. O título do item focado, que é justamente o
    /// que se quer ler, era o único que ficava ilegível.
    ///
    /// Aumentar o `Spacer` resolveria pelo lado errado: deixaria um vão morto de
    /// 20dp debaixo de **todo** cartaz pra acomodar o estado de um só.
    ///
    /// Escalando a `Column`, arte e título crescem juntos e a distância entre os
    /// dois cresce junto também. E o cartão continua passando por cima dos
    /// **vizinhos**, que é o que o `Sala.ESCALA_DO_FOCO` documenta e quer.
    var focado by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(
        targetValue = if (focado) Sala.ESCALA_DO_FOCO else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "escala do cartaz",
    )

    Column(modifier.width(largura).scale(escala)) {
        Focavel(
            aoEscolher = aoEscolher,
            escolhivel = escolhivel,
            escalar = false,
            aoFocar = { focado = it },
            modifier = Modifier
                .size(width = largura, height = altura)
                .saidaPraEsquerda(saidaEsquerda),
        ) { focado ->
            FundoDoCartaz(titulo = titulo, arte = arte, cor = cor)

            if (andado > 0f) {
                BarraDeAndamento(andado, Modifier.align(Alignment.BottomStart))
            }

            /// O escurecimento de quem **não** está focado.
            ///
            /// Ele é o terceiro sinal de foco (ver `Foco.kt`), e é o que faz uma
            /// fileira de dez cartazes ter um único item aceso em vez de dez
            /// competindo. Sem ele, a escala e a borda disputam atenção com a
            /// arte de dez filmes ao mesmo tempo.
            if (!focado) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        if (detalhe != null) {
            /// ⚠️ **Duas linhas, e o corte é reticências.**
            ///
            /// `maxLines = 1` servia enquanto o detalhe era `1998 · 湯山邦彦` —
            /// um dado curto que cabe. No «para você» ele passou a ser o
            /// **motivo** da recomendação, e numa linha só `você costuma ver
            /// ficção científica` virava `você costuma` na TCL.
            ///
            /// Motivo picado é pior que motivo ausente: `você costuma` não diz
            /// nada, e ainda ocupa o lugar de quem diria. Duas linhas cabem no
            /// vão entre um cartaz e o de baixo, e as reticências avisam quando
            /// mesmo assim faltou.
            Text(
                text = detalhe,
                style = MaterialTheme.typography.labelMedium,
                color = Cores.textoApagado,
                maxLines = linhasDoDetalhe,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/// Um cartão deitado — um quadro do filme, 16:9.
///
/// É o formato do "continuar assistindo" e do mural, e a escolha do formato é a
/// pergunta que o cartão responde: quem parou no meio já sabe **que** filme é, e
/// quer saber **onde estava**. Uma capa não diz isso; um quadro diz.
@Composable
fun Quadro(
    titulo: String,
    arte: String?,
    aoEscolher: () -> Unit,
    modifier: Modifier = Modifier,
    cor: String? = null,
    andado: Float = 0f,
    detalhe: String? = null,
    /// Ver `Cartaz.saidaEsquerda`.
    saidaEsquerda: FocusRequester? = null,
) {
    /// Mesma conta do `Cartaz` acima, e pelo mesmo motivo.
    var focado by remember { mutableStateOf(false) }
    val escala by animateFloatAsState(
        targetValue = if (focado) Sala.ESCALA_DO_FOCO else 1f,
        animationSpec = tween(durationMillis = 120),
        label = "escala do quadro",
    )

    Column(modifier.width(Sala.quadroL).scale(escala)) {
        Focavel(
            aoEscolher = aoEscolher,
            escalar = false,
            aoFocar = { focado = it },
            modifier = Modifier
                .size(width = Sala.quadroL, height = Sala.quadroA)
                .saidaPraEsquerda(saidaEsquerda),
        ) { focado ->
            FundoDoCartaz(titulo = titulo, arte = arte, cor = cor)

            /// O véu de baixo pra cima, pro título nunca cair sobre uma parte
            /// clara do quadro. Um still de filme não tem lugar previsível pra
            /// texto — o céu de uma cena de dia é branco.
            Box(
                Modifier
                    .fillMaxSize()
                    .background(
                        Brush.verticalGradient(
                            0.45f to Color.Transparent,
                            1f to Color.Black.copy(alpha = 0.85f),
                        ),
                    ),
            )

            if (andado > 0f) {
                BarraDeAndamento(andado, Modifier.align(Alignment.BottomStart))
            }
            if (!focado) {
                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.35f)))
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            text = titulo,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (detalhe != null) {
            Text(
                text = detalhe,
                style = MaterialTheme.typography.labelMedium,
                color = Cores.textoApagado,
                maxLines = 1,
                overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
            )
        }
    }
}

/// O miolo de um cartaz: a arte, ou o que se desenha na falta dela.
@Composable
private fun FundoDoCartaz(titulo: String, arte: String?, cor: String?) {
    val tinta = corDeHex(cor) ?: Cores.fundoElevado
    Box(Modifier.fillMaxSize().background(tinta)) {
        if (arte != null) {
            AsyncImage(
                model = arte,
                contentDescription = titulo,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        } else {
            /// Sem arte, o título **é** o cartaz. Alinhado embaixo porque é onde
            /// ele estaria numa capa de verdade, e porque deixa o alto da cor
            /// respirar.
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodySmall,
                color = Cores.texto,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            )
        }
    }
}

/// A barrinha de quanto já se viu, dentro do cartaz.
///
/// Desenhada à mão e não com um `LinearProgressIndicator`: o do Material tem
/// cantos, espaço em volta e uma animação de trilha que aqui atrapalha — o que
/// se quer é uma régua colada na base da arte, como a de um player.
@Composable
private fun BarraDeAndamento(andado: Float, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(5.dp)
            .background(Color.Black.copy(alpha = 0.6f)),
    ) {
        Box(
            Modifier
                .fillMaxWidth(andado.coerceIn(0f, 1f))
                .height(5.dp)
                .background(Cores.destaque),
        )
    }
}

/// Uma pílula — etiqueta, filtro, selo de modo.
///
/// A borda é que carrega a cor; o fundo nunca. É a regra que o `Cor.kt` do
/// `:core` já escrevia sobre a `color` das etiquetas, e ela vale aqui pelo mesmo
/// motivo: uma pílula de fundo colorido compete com o cartaz ao lado.
@Composable
fun Pilula(
    texto: String,
    modifier: Modifier = Modifier,
    cor: Color = Cores.destaqueApagado,
    tinta: Color = Cores.textoApagado,
) {
    Text(
        text = texto,
        style = TipoDaSala.pilula,
        color = tinta,
        maxLines = 1,
        modifier = modifier
            .clip(CircleShape)
            .border(1.dp, cor, CircleShape)
            .padding(horizontal = 16.dp, vertical = 7.dp),
    )
}

/// O que se desenha enquanto não há nada ainda.
///
/// ⚠️ **Não é um spinner centralizado**, e é decisão. Numa TV, um giro no meio
/// da tela é a coisa mais visível do cômodo e não informa nada. O que informa é
/// o esqueleto da fileira que vai chegar: a pessoa já vê **onde** as coisas vão
/// aparecer, e o foco tem pra onde ir assim que aparecerem.
@Composable
fun FileiraFantasma(quantos: Int = 6, deitado: Boolean = false) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
        modifier = Modifier.padding(horizontal = Sala.overscanH),
    ) {
        repeat(quantos) {
            Box(
                Modifier
                    .size(
                        width = if (deitado) Sala.quadroL else Sala.cartazL,
                        height = if (deitado) Sala.quadroA else Sala.cartazA,
                    )
                    .clip(RoundedCornerShape(10.dp))
                    .background(Cores.fundoElevado),
            )
        }
    }
}

/// Uma frase no meio da tela — erro, vazio, "nada aqui ainda".
///
/// Ela **não** é um `Toast` nem um diálogo: numa TV não há como descartar um
/// diálogo sem um botão focado, e um `Toast` desaparece antes de alguém a três
/// metros terminar de ler.
@Composable
fun Recado(
    titulo: String,
    modifier: Modifier = Modifier,
    detalhe: String? = null,
    abaixo: @Composable () -> Unit = {},
) {
    Column(
        modifier
            .fillMaxSize()
            .padding(horizontal = Sala.overscanH, vertical = Sala.overscanV),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = titulo,
            style = MaterialTheme.typography.headlineMedium,
            color = Cores.texto,
        )
        if (detalhe != null) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = detalhe,
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.textoApagado,
            )
        }
        Spacer(Modifier.height(26.dp))
        abaixo()
    }
}

/// Manda a seta ◀ deste item pra um destino explícito, em vez de deixar a busca
/// de foco decidir.
///
/// ## ⚠️ Ela existe porque a busca de foco **enrola na borda**
///
/// Medido na TCL em 12/08/2026, com `uiautomator`, numa fileira de três cartões:
///
/// | tecla | de onde | pra onde |
/// |---|---|---|
/// | ▶ | cartão 1 | cartão 2 — certo |
/// | ◀ | cartão 2 | cartão 1 — certo |
/// | ◀ | **cartão 1** | **cartão 3** — enrolou, e de lá não saía mais |
///
/// Ou seja: a navegação dentro da fileira está certa; o defeito é só na borda,
/// onde o foco devia **sair** e em vez disso dá a volta. Com o trilho à
/// esquerda, isso deixava o menu — e cinco das seis telas — inalcançáveis pelo
/// controle.
///
/// Duas tentativas falharam antes desta, e ficam registradas porque parecem
/// certas: `focusProperties { exit }` num `focusGroup()` em volta do conteúdo
/// (nunca é consultado — a busca acha o item enrolado **dentro** do grupo, então
/// nunca chega a sair), e `focusProperties { left }` no mesmo contêiner
/// (`focusProperties` não desce pros filhos: não quebrou nada, e também não
/// consertou nada).
///
/// O que funciona é pôr o desvio **no item** onde a enrolada acontece — o mais à
/// esquerda de cada fileira. Ali ele curto-circuita a busca inteira.
private fun Modifier.saidaPraEsquerda(destino: FocusRequester?): Modifier =
    if (destino == null) this else this.focusProperties { left = destino }
