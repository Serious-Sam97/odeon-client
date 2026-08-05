package dev.odeon.android.ui.locadora

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.layout
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Emprestada
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import dev.odeon.android.dados.CaixaExposta
import dev.odeon.android.dados.EstanteExposta
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.corDeHex
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.chega

/// A locadora.
///
/// ## Ela é 2D, e isso é decisão da espec — não limitação encontrada
///
/// A estante da web é **CSS 3D**: `perspective`, `preserve-3d`, `translateZ`. A
/// §3 diz que o Compose não tem equivalente — `graphicsLayer` faz rotação e
/// câmera, mas não compõe uma hierarquia com filhos em profundidade — e propõe
/// o substituto: **prateleira 2D com a arte das caixas, e o girar da caixa
/// virando um `flip` de duas faces**.
///
/// O que se perde é a profundidade da cena, não a metáfora: a caixa continua
/// sendo objeto, continua tendo frente e verso, e continua saindo da estante
/// quando alguém a leva.
/// ## O "‹ biblioteca" saiu daqui, e não foi esquecimento
///
/// A locadora virou **aba**, e um destino de primeiro nível não tem pra onde
/// voltar: ele é raiz tanto quanto a biblioteca. O botão que estava aqui vinha
/// de quando esta tela só era alcançável por um link no cabeçalho da grade.
///
/// O botão físico de voltar continua levando à biblioteca — quem trata disso é o
/// `BackHandler` no `AppOdeon`, que é onde a navegação mora.
@Composable
fun TelaDaLocadora(modelo: ModeloDaLocadora, aoAbrirObra: (String) -> Unit = {}) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    if (estado.carregando && estado.prateleira == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cores.destaque)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("locadora", style = MaterialTheme.typography.headlineSmall, color = Cores.texto)

        estado.erro?.let {
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Cores.perigo)
        }

        /// As regras da casa, ditas pelo servidor e não por constante daqui.
        estado.prateleira?.opcoes?.let { opcoes ->
            Text(
                text = regras(opcoes.escassez, opcoes.limitePorPessoa, opcoes.prazoEmDias, estado.prateleira!!.possoPegar),
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
            )
        }

        if (estado.minhas.isNotEmpty()) {
            Secao("comigo", quantos = estado.minhas.size) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(estado.minhas, key = { _, f -> f.id }) { i, fita ->
                        Caixa(
                            indice = i,
                            fita = fita,
                            arte = modelo.arte(fita.poster),
                            /// Devolver só existe nas minhas. Nas dos outros o
                            /// gesto seria mexer no empréstimo de alguém — e o
                            /// §11 é explícito sobre isso.
                            aoDevolver = { modelo.devolver(fita.id) },
                            devolvendo = estado.devolvendo == fita.id,
                        )
                    }
                }
            }
        }

        if (estado.dosOutros.isNotEmpty()) {
            Secao("na mão de alguém", quantos = estado.dosOutros.size) {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    itemsIndexed(estado.dosOutros, key = { _, f -> f.id }) { i, fita ->
                        Caixa(
                            indice = i,
                            fita = fita,
                            arte = modelo.arte(fita.poster),
                            aoDevolver = null,
                            devolvendo = false,
                        )
                    }
                }
            }
        }

        /// Vazio de verdade tem frase, e não silêncio.
        ///
        /// Aqui o §24 **não** vale: uma locadora sem nenhuma caixa fora é um
        /// estado normal e informativo — "está tudo na estante" é notícia. Uma
        /// tela em branco, não.
        if (estado.minhas.isEmpty() && estado.dosOutros.isEmpty() && estado.erro == null) {
            /// ⚠️ As tábuas vazias só entram quando **não há vitrine**.
            ///
            /// Elas nasceram pra consertar um vazio: a tela sem empréstimo era
            /// 1.400 pixels de preto com uma frase. Com a vitrine desenhada
            /// abaixo, esse vazio não existe mais — e duas prateleiras vazias
            /// empurrando a loja pra baixo passam de conserto a estorvo.
            ///
            /// É a mesma decisão que o próprio conserto tomou, invertida: o que
            /// muda é o que está em volta.
            EstanteVazia(comTabuas = estado.loja?.estantes.isNullOrEmpty())
        }

        /// ## A vitrine — e ela é a locadora
        ///
        /// Até aqui esta tela mostrava só o que **saiu** da estante: os seus
        /// empréstimos e os dos outros. O acervo exposto — a loja em si — nunca
        /// foi pedido, e `/api/locadora/estantes` existe desde antes deste app.
        ///
        /// É o mesmo padrão de `height`, `size_bytes` e `tags`: o servidor já
        /// dava e o cliente não pegava. A diferença é o tamanho — não era um
        /// campo, era metade de uma tela.
        estado.loja?.estantes?.forEach { estante ->
            Estante(estante = estante, arte = modelo::arte, aoAbrir = aoAbrirObra)
        }

        /// Quando a vitrine vira.
        ///
        /// O comentário da web diz o que este campo carrega: «é o que torna a
        /// rotação **promessa, não sorteio**». Uma seleção que muda sem data
        /// anunciada é aleatoriedade; com data, é programação — e é a diferença
        /// entre um acervo embaralhado e uma locadora que troca a vitrine na
        /// segunda.
        estado.loja?.viraEm?.let { quando ->
            Text(
                text = "a vitrine vira em $quando",
                style = MaterialTheme.typography.labelSmall,
                color = Cores.textoApagado,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}

/// Uma seção da locadora: rótulo, as caixas, e **a tábua** por baixo.
///
/// ## A tábua é a leva 2, e ela conserta o que a leva 3 deixou pela metade
///
/// A caixa ganhou lombada, pose e verniz — e ficou de pé **no nada**. Não havia
/// prateleira: as caixas flutuavam sobre o preto, o que desfaz metade do
/// trabalho de fazê-las parecerem objetos. Objeto que não repousa em nada é
/// desenho, não coisa.
///
/// A web sabe disso e o comentário dela diz por quê:
///
/// > «A tábua encosta na base das caixas. Com folga embaixo o conjunto lê como
/// > cartão, não como objeto.»
///
/// Por isso a madeira encosta — sem vão, sem margem. E ela **sangra pras
/// laterais** (os −16dp) porque prateleira de loja não acaba onde acaba a
/// fileira: ela atravessa a parede.
///
/// ## O halo é a luz da loja batendo na madeira
///
/// Um dourado difuso subindo da tábua, a 55% e desfocado. Sem ele a madeira é
/// uma tarja marrom; com ele, ela é uma superfície **iluminada** — e é o mesmo
/// argumento do arquivo `Luz.kt`, aplicado a um lugar em vez de a um objeto.
/// Uma estante da vitrine: placa, caixas e tábua.
///
/// ## A placa é serifada e **acesa**, e isso é da folha
///
/// `.placa span` (`styles.css:4105`): 24px em `--font-display`, `--accent`, com
/// `text-shadow: 0 0 24px` a 42%. Ou seja — o nome da estante não é um rótulo,
/// é um **letreiro aceso** pendurado sobre a prateleira. É o dourado como luz na
/// forma mais literal que a web tem.
///
/// Compose não tem `text-shadow` colorido difuso como o CSS, então o halo vem
/// pelo `shadow` do `TextStyle`: mesma ideia, e o borrão de 24px vira `blurRadius`.
///
/// ## «16 de 113» é a promessa da vitrine
///
/// `total` é quantas caixas a estante tem **no acervo**, não quantas estão à
/// vista. O comentário da web insiste nisso, e é o que impede a placa de mentir:
/// a vitrine é uma amostra que gira, não o estoque.
@Composable
private fun Estante(
    estante: EstanteExposta,
    arte: (String?) -> String?,
    aoAbrir: (String) -> Unit,
) {
    if (estante.caixas.isEmpty()) return

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = estante.nome,
                style = MaterialTheme.typography.headlineSmall.copy(
                    fontSize = 24.sp,
                    letterSpacing = 0.05.em,
                    shadow = Shadow(
                        color = Cores.destaque.copy(alpha = 0.42f),
                        blurRadius = 24f,
                    ),
                ),
                color = Cores.destaque,
            )
            Text(
                text = "${estante.caixas.size} de ${estante.total}",
                style = Tipo.rotulo.copy(letterSpacing = 0.14.em),
                color = Cores.destaqueApagado,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            itemsIndexed(estante.caixas, key = { _, c -> c.id }) { i, caixa ->
                CaixaNaEstante(
                    caixa = caixa,
                    arte = arte(caixa.poster),
                    indice = i,
                    aoAbrir = { aoAbrir(caixa.id) },
                )
            }
        }

        Tabua()
    }
}

/// Uma caixa **na** estante — de pé, na mesma pose das que saíram.
///
/// Ela não vira: o verso de uma caixa emprestada tem quem levou e o prazo, e uma
/// que está na estante não tem nada disso pra mostrar. Tocar abre a ficha, que é
/// onde se pega a fita.
@Composable
private fun CaixaNaEstante(
    caixa: CaixaExposta,
    arte: String?,
    indice: Int,
    aoAbrir: () -> Unit,
) {
    val espessura = 26.dp
    Column(
        modifier = Modifier
            .chega(indice)
            .width(96.dp + espessura)
            .clickable(onClick = aoAbrir),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(96.dp + espessura)
                .aspectRatio((96f + 26f) / 144f),
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(espessura)
                    .fillMaxHeight()
                    .graphicsLayer {
                        rotationY = 68f
                        transformOrigin = TransformOrigin(1f, 0.5f)
                        cameraDistance = 12f * density
                    }
                    .background(
                        Brush.horizontalGradient(
                            listOf(Cores.fundoAfundado, Cores.fundoElevado, Cores.fundoAfundado),
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = caixa.titulo,
                    style = MaterialTheme.typography.labelSmall,
                    color = Cores.textoApagado,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.requiredWidth(130.dp).graphicsLayer { rotationZ = 90f },
                )
            }

            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .width(96.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        rotationY = -22f
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        cameraDistance = 12f * density
                    }
                    .clip(RoundedCornerShape(4.dp))
                    .background(corDeHex(caixa.corDominante) ?: Cores.fundoElevado),
                contentAlignment = Alignment.Center,
            ) {
                if (arte != null) {
                    AsyncImage(
                        model = arte,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                } else {
                    Text(
                        text = caixa.titulo,
                        style = MaterialTheme.typography.labelSmall,
                        color = Cores.texto,
                        textAlign = TextAlign.Center,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(6.dp),
                    )
                }
                /// O verniz, o mesmo das caixas emprestadas.
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.linearGradient(
                            0.00f to Color.White.copy(alpha = 0.24f),
                            0.14f to Color.White.copy(alpha = 0.05f),
                            0.32f to Color.Transparent,
                            0.74f to Color.Transparent,
                            1.00f to Color.White.copy(alpha = 0.10f),
                        ),
                    ),
                )
            }
        }
    }
}

@Composable
private fun Secao(titulo: String, quantos: Int? = null, conteudo: @Composable () -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        RotuloDeSecao(texto = titulo, numero = quantos)
        conteudo()
        Tabua()
    }
}

/// A loja com nada emprestado — e **ela continua sendo uma loja**.
///
/// ## A tela dizia uma coisa e desenhava outra
///
/// A frase é «nenhuma caixa fora da estante», que é uma afirmação **positiva**:
/// está tudo guardado. E o desenho era um retângulo preto de 1.400 pixels com
/// uma linha cinza no topo. Copy e desenho se contradiziam: um dizia "está tudo
/// no lugar", o outro dizia "não há nada aqui".
///
/// Duas tábuas vazias com a frase entre elas resolvem isso sem inventar dado: é
/// a **mobília da loja**, que existe independentemente de haver caixa fora. O
/// que se vê é uma prateleira de empréstimo vazia, que é exatamente o que é.
///
/// ⚠️ **Não desenho caixas cheias**, e a tentação existia — uma estante lotada
/// leria muito melhor. Seria §18 na forma mais direta: o app não sabe o que está
/// na estante. Esta tela só conhece o que **saiu** dela; o acervo inteiro é da
/// biblioteca. Desenhar caixas aqui seria afirmar um estoque que ninguém contou.
@Composable
private fun EstanteVazia(comTabuas: Boolean) {
    if (!comTabuas) {
        Text(
            text = "nenhuma caixa fora da estante",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.textoApagado,
        )
        return
    }

    Column(
        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        /// A altura da fileira que **estaria** aqui, vazia. Sem ela as duas
        /// tábuas ficariam coladas e leriam como duas linhas, não como estante.
        Box(Modifier.fillMaxWidth().height(96.dp))
        Tabua()
        Text(
            text = "nenhuma caixa fora da estante",
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.textoApagado,
            modifier = Modifier.padding(vertical = 20.dp),
        )
        Box(Modifier.fillMaxWidth().height(96.dp))
        Tabua()
    }
}

/// A madeira da prateleira.
@Composable
private fun Tabua() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            /// Sangra 16dp de cada lado pra cancelar o padding da tela. Uma
            /// prateleira que respeita a margem do texto é uma prateleira que
            /// acaba no ar.
            ///
            /// ⚠️ **`padding` negativo não existe em Compose**, e a primeira
            /// versão disto tentou: `padding(horizontal = (-16).dp)`. Compilou,
            /// passou no lint, e o app **caiu ao abrir a locadora** com
            /// `IllegalArgumentException: Padding must be non-negative`. Mais
            /// uma para a lista: compilar e passar no lint não é ter visto.
            ///
            /// O jeito certo é medir com folga e colocar deslocado. O `layout`
            /// pede ao filho uma largura maior que a que recebeu e depois o
            /// posiciona 16dp à esquerda, mantendo a **altura** reservada igual
            /// — assim a coluna acima não se mexe.
            .layout { medivel, restricoes ->
                val folga = 16.dp.roundToPx() * 2
                val largura = restricoes.maxWidth + folga
                val posto = medivel.measure(
                    restricoes.copy(minWidth = largura, maxWidth = largura),
                )
                layout(restricoes.maxWidth, posto.height) {
                    posto.place(-folga / 2, 0)
                }
            }
            .height(6.dp)
            .background(
                Brush.verticalGradient(
                    listOf(Color(0xFF2A2119), Color(0xFF150F0A)),
                ),
            ),
    ) {
        /// A luz da loja batendo na tábua: uma linha quente no topo dela.
        Box(
            Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(
                            Color.Transparent,
                            Cores.destaque.copy(alpha = 0.35f),
                            Color.Transparent,
                        ),
                    ),
                ),
        )
    }
}

/// Uma caixa de fita, **de pé e em três quartos** — R5.
///
/// ## Ela não fica de frente, e isso é o item inteiro
///
/// A web nunca desenha a caixa chapada: ela repousa em `rotateX(3deg)
/// rotateY(22deg)` (`styles.css:4256`) e é essa pose que mostra a **lombada**.
/// Um retângulo de frente é uma capa; um retângulo girado com uma faixa escura
/// na lateral é uma caixa numa prateleira. A §1.3 do redesenho chama isso de «a
/// coisa que o Odeon é e o app ainda não».
///
/// As medidas saem da folha, não do olho:
///
/// | | web (`.caixa.vhs`) | aqui |
/// |---|---|---|
/// | largura × altura | 104 × 200 | 140dp × 210dp (2:3) |
/// | **espessura** | **28px** — 27% da largura | 38dp, a mesma proporção |
/// | pose | `rotateY(22deg)` | 22° |
///
/// ## O que **não** dá pra copiar, e o substituto
///
/// A web compõe as cinco faces em `preserve-3d`, num espaço 3D compartilhado. O
/// Compose não tem isso — a §3 da espec já registrava —, então cada face aqui é
/// uma camada com transformação própria, e as duas são **encostadas por conta**:
/// a lombada é posicionada à esquerda da capa e girada sobre a própria aresta
/// direita, de modo que as arestas coincidam na pose escolhida.
///
/// É aproximação, e ela tem um limite honesto: como as camadas não dividem o
/// mesmo ponto de fuga, a junta só fecha **na pose de repouso**. Por isso a pose
/// é fixa e não acompanha o dedo — animar o ângulo abriria a junta no meio do
/// caminho, e uma caixa com fresta é pior que uma caixa chapada.
///
/// ## O verniz
///
/// A faixa diagonal clara sobre a capa é o `.brilho` (`:4385`), e o comentário
/// da folha diz o que ela faz: «é o que faz o olho ler objeto em vez de
/// imagem». Custa um gradiente e é o item de melhor retorno desta fase.
///
/// ⚠️ Ela é decoração e **não pode parecer dado** (§18). Por isso é branco a
/// 24% sobre a arte, na diagonal, e não uma faixa colorida na horizontal — que
/// é a forma que a barra de progresso tem em toda outra tela deste app.
@Composable
private fun Caixa(
    fita: Emprestada,
    arte: String?,
    aoDevolver: (() -> Unit)?,
    devolvendo: Boolean,
    indice: Int = 0,
) {
    var virada by remember { mutableStateOf(false) }
    val giro by animateFloatAsState(
        targetValue = if (virada) 180f else 0f,
        animationSpec = tween(durationMillis = 400),
        label = "giro da caixa",
    )
    val haptico = LocalHapticFeedback.current

    /// A espessura da caixa, na proporção da web: 28 de 104 é 27%.
    val espessura = 38.dp

    /// ## Arrastar pra baixo devolve a fita — R8
    ///
    /// É o gesto que fecha a metáfora: a caixa **volta pra estante**, e a mão
    /// faz o movimento em vez de procurar um botão.
    ///
    /// ### ⚠️ Ele só existe nas minhas
    ///
    /// `aoDevolver` é nulo nas caixas dos outros, e aí nem o detector é montado.
    /// Um arrasto que devolvesse a fita de alguém seria o §11 na pior versão —
    /// e "sem querer" não é desculpa quando o dado é de outra pessoa.
    ///
    /// ### O limite é alto de propósito, e a tela avisa antes
    ///
    /// 96dp é quase metade da altura da caixa. Um limite curto transformaria
    /// qualquer rolagem mal-agarrada numa devolução — e devolver **escreve no
    /// acervo de três pessoas**, então o gesto tem que ser deliberado.
    ///
    /// E ele não é secreto: a caixa acompanha o dedo, e passado o limite aparece
    /// a frase "solte pra devolver". Gesto escondido que faz coisa grave é o §8b
    /// ao contrário — errar em silêncio é ruim, mas acertar em silêncio numa
    /// ação destrutiva é pior.
    ///
    /// ### ✅ Visto rodando, e o primeiro teste enganou
    ///
    /// Verificado em 04/08/2026 de ponta a ponta: a caixa desce com o dedo,
    /// desbota, o rótulo vira "solte pra devolver", e soltar devolve — a fita
    /// some da prateleira e o limite volta a subir. Feito duas vezes.
    ///
    /// ⚠️ **A primeira tentativa disse que não funcionava, e estava errada.** Um
    /// `adb input swipe` de 900ms não dispara nada; com 1600ms dispara sempre. O
    /// arrasto precisa vencer o `touchSlop` **e** andar 96dp, e rápido demais os
    /// dois chegam numa rajada de eventos que o detector lê como um só. É limite
    /// da ferramenta de teste, não do gesto — e vale anotar porque o sintoma é
    /// idêntico ao de um gesto quebrado.
    ///
    /// ### O que ele custa, e vale dizer
    ///
    /// A tela rola na vertical, e um arrasto vertical que começa **na caixa**
    /// passa a ser dela. Ou seja: não dá pra rolar a página agarrando uma caixa
    /// minha — tem que agarrar o resto da tela. É o preço do gesto, e é o mesmo
    /// que qualquer app de lista com deslizar-pra-apagar paga.
    var arrasto by remember { mutableFloatStateOf(0f) }
    val limiteDeDevolucao = with(LocalDensity.current) { 96.dp.toPx() }
    val passouDoLimite = arrasto > limiteDeDevolucao
    val descida by animateFloatAsState(
        targetValue = arrasto,
        animationSpec = spring(),
        label = "descida da caixa",
    )

    Column(
        /// A caixa **cai** na prateleira ao chegar, escalonada — ver `Chegada`.
        modifier = Modifier.chega(indice).width(140.dp + espessura),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .width(140.dp + espessura)
                .aspectRatio((140f + 38f) / 210f)
                /// A caixa desce com o dedo e desbota — o desbotar é o que diz
                /// "isto está saindo daqui" sem escrever nada.
                .graphicsLayer {
                    translationY = descida
                    alpha = 1f - (descida / (limiteDeDevolucao * 2f)).coerceIn(0f, 0.45f)
                }
                .then(
                    if (aoDevolver == null || devolvendo) {
                        Modifier
                    } else {
                        Modifier.pointerInput(fita.id) {
                            detectVerticalDragGestures(
                                onDragEnd = {
                                    if (arrasto > limiteDeDevolucao) {
                                        /// A batida da R5 no fim do gesto —
                                        /// a mesma de tocar em "devolver".
                                        haptico.performHapticFeedback(
                                            HapticFeedbackType.LongPress,
                                        )
                                        aoDevolver()
                                    }
                                    arrasto = 0f
                                },
                                onDragCancel = { arrasto = 0f },
                                onVerticalDrag = { _, delta ->
                                    /// Só pra baixo: arrastar pra cima não
                                    /// desdevolve nada, então não move nada.
                                    arrasto = (arrasto + delta).coerceAtLeast(0f)
                                },
                            )
                        }
                    },
                )
                /// ⚠️ **O giro é do objeto inteiro, e mora aqui — não na capa.**
                ///
                /// A primeira versão punha o `giro` na `rotationY` da capa, que
                /// já girava −22° sobre a **aresta esquerda** pra encostar na
                /// lombada. O screenshot mostrou o resultado: virar a caixa a
                /// espremia numa tira escura de dois dedos. Girar 158° em torno
                /// da aresta esquerda não vira o objeto — **joga ele pra fora**,
                /// como uma porta abrindo.
                ///
                /// Aqui a origem é o centro (o padrão), então a caixa gira sobre
                /// o próprio eixo. A pose de −22° da capa fica **dentro** desta
                /// camada e é somada a ela, que é o que o `preserve-3d` da web
                /// faz de graça e o Compose faz por aninhamento.
                .graphicsLayer {
                    rotationY = giro
                    cameraDistance = 12f * density
                }
                .clickable {
                    /// Girar a caixa é mexer num objeto, e a mão avisa.
                    ///
                    /// `TextHandleMove` é o mais **seco** dos dois tipos que o
                    /// Compose expõe — um tique, não uma batida. Virar a caixa é
                    /// gesto leve; a batida fica pra devolver, que é o que muda
                    /// o acervo de todo mundo.
                    haptico.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    virada = !virada
                },
        ) {
            if (giro <= 90f) {
                /// A lombada.
                ///
                /// Girada sobre a **própria aresta direita** (`TransformOrigin(1f,
                /// .5f)`) pra encostar na aresta esquerda da capa. O ângulo é
                /// 90° − 22° = 68°: a capa está a 22° do plano da tela, e a
                /// lombada é perpendicular a ela.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .width(espessura)
                        .fillMaxHeight()
                        .graphicsLayer {
                            rotationY = 68f
                            transformOrigin = TransformOrigin(1f, 0.5f)
                            cameraDistance = 12f * density
                        }
                        .background(
                            /// Escurece do meio pras bordas: é a curvatura do
                            /// plástico pegando luz de cima. Chapada, a lombada
                            /// lê como um retângulo colado ao lado da capa.
                            Brush.horizontalGradient(
                                listOf(
                                    Cores.fundoAfundado,
                                    Cores.fundoElevado,
                                    Cores.fundoAfundado,
                                ),
                            ),
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    /// O título na vertical, como em toda lombada de fita.
                    ///
                    /// `rotationZ` e não uma fonte vertical: o Compose não tem
                    /// `writing-mode`. O texto é medido deitado e girado depois,
                    /// então a largura dele vira a altura da lombada.
                    ///
                    /// ⚠️ `requiredWidth` e **não** `width`, e o screenshot é que
                    /// denunciou: com `width(190.dp)` o título saía `007 C…`.
                    /// `graphicsLayer` gira o que já foi desenhado e não mexe em
                    /// medição — então o texto era medido dentro dos 38dp da
                    /// lombada, cortava ali, e só depois girava. O `required` é o
                    /// que ignora a restrição do pai; sem ele toda lombada
                    /// mostraria três letras.
                    Text(
                        text = fita.titulo,
                        style = MaterialTheme.typography.labelSmall,
                        color = Cores.textoApagado,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .requiredWidth(190.dp)
                            .graphicsLayer { rotationZ = 90f },
                    )
                }

                /// A capa, girada 22° sobre a aresta esquerda — o mesmo eixo em
                /// que a lombada encosta.
                Box(
                    modifier = Modifier
                        .align(Alignment.CenterEnd)
                        .width(140.dp)
                        .fillMaxHeight()
                        .graphicsLayer {
                            rotationY = -22f
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            /// Sem isto a rotação é ortográfica e a caixa parece
                            /// achatar em vez de girar. O número é distância de
                            /// câmera em múltiplos da densidade — 12 dá
                            /// perspectiva sem a deformação de grande-angular.
                            cameraDistance = 12f * density
                        }
                        .clip(RoundedCornerShape(4.dp))
                        .background(Cores.fundoElevado),
                    contentAlignment = Alignment.Center,
                ) {
                    if (arte != null) {
                        AsyncImage(
                            model = arte,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = fita.titulo,
                            style = MaterialTheme.typography.labelMedium,
                            color = Cores.texto,
                            textAlign = TextAlign.Center,
                            maxLines = 4,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(8.dp),
                        )
                    }

                    /// O verniz — o `.brilho` da web (`styles.css:4385`). Só
                    /// sobre a frente: o verso de uma caixa de fita é papel, não
                    /// plástico.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .background(
                                Brush.linearGradient(
                                    0.00f to Color.White.copy(alpha = 0.24f),
                                    0.14f to Color.White.copy(alpha = 0.05f),
                                    0.32f to Color.Transparent,
                                    0.74f to Color.Transparent,
                                    1.00f to Color.White.copy(alpha = 0.10f),
                                ),
                            ),
                    )
                }
            } else {
                /// O verso — a etiqueta.
                ///
                /// Ele ocupa a caixa inteira, lombada incluída: virada, a caixa
                /// mostra o papel de trás e não há mais lateral pra ver deste
                /// lado. E leva `rotationY = 180f` pra desespelhar — sem isso o
                /// texto sairia invertido, porque o pai já girou meia-volta.
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { rotationY = 180f }
                        .clip(RoundedCornerShape(4.dp))
                        .background(Cores.fundoElevado),
                ) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Text(
                            text = fita.titulo,
                            style = MaterialTheme.typography.labelMedium,
                            color = Cores.texto,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = "com ${fita.quemNome}",
                            style = MaterialTheme.typography.labelSmall,
                            color = Cores.textoApagado,
                        )
                        fita.pedidoPorNome?.let {
                            Text(
                                text = "$it pediu de volta",
                                style = MaterialTheme.typography.labelSmall,
                                color = Cores.destaque,
                            )
                        }
                        if (aoDevolver != null) {
                            TextButton(
                                onClick = {
                                    /// A batida de devolver.
                                    ///
                                    /// `LongPress` é o mais **encorpado** dos
                                    /// dois tipos que o Compose expõe —
                                    /// devolver escreve no acervo de três
                                    /// pessoas, e a mão sente a diferença entre
                                    /// isto e virar a caixa, que leva o tique
                                    /// seco.
                                    haptico.performHapticFeedback(HapticFeedbackType.LongPress)
                                    aoDevolver()
                                },
                                enabled = !devolvendo,
                                contentPadding = PaddingValues(0.dp),
                            ) {
                                Text(
                                    text = if (devolvendo) "devolvendo…" else "devolver",
                                    color = Cores.destaque,
                                )
                            }
                        }
                    }
                }
            }
        }

        /// A frase só aparece depois do limite, e é o aviso antes do fato.
        if (passouDoLimite) {
            Text(
                text = "solte pra devolver",
                style = MaterialTheme.typography.labelSmall,
                color = Cores.destaque,
                maxLines = 1,
            )
        } else {
            Text(
                text = fita.titulo,
                style = MaterialTheme.typography.bodySmall,
                color = Cores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/// A frase das regras, montada com o que o servidor mandou.
///
/// Ela existe porque a escassez muda o significado da tela inteira: ligada, uma
/// caixa na mão de alguém é uma caixa que **você não pode pegar**; desligada, é
/// só informação. Dizer qual dos dois está valendo evita a tela mentir por
/// omissão.
private fun regras(escassez: Boolean, limite: Int, prazoDias: Int, possoPegar: Int): String = buildString {
    append(if (escassez) "escassez ligada: uma cópia por caixa" else "escassez desligada: ninguém barra ninguém")
    if (limite > 0) append(" · limite de $limite por pessoa")
    if (prazoDias > 0) append(" · prazo de $prazoDias dias")
    if (escassez) append(" · você ainda pode pegar $possoPegar")
}
