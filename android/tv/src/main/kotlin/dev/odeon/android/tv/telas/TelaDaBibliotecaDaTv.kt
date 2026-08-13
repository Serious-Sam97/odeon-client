package dev.odeon.android.tv.telas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Cartaz
import dev.odeon.android.tv.ui.FileiraFantasma
import dev.odeon.android.tv.ui.Quadro
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.biblioteca.ModeloDaBiblioteca
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import androidx.compose.runtime.derivedStateOf

/// A biblioteca, na sala.
///
/// ## Grade e não fileira, e é a única tela deste app que faz isso
///
/// Todas as outras telas da sala são fileiras horizontais, que é a forma padrão
/// de TV. Esta não, e o motivo é a **contagem**: são 17.930 obras. Numa fileira
/// horizontal, chegar na milésima custa mil apertos de seta pra direita, sem
/// nenhum atalho — o D-pad não tem "página abaixo" que a `LazyRow` entenda.
///
/// Numa grade de cinco colunas, a mesma milésima está a duzentos apertos pra
/// baixo, e segurar a seta rola de verdade. Continua sendo muito; a resposta pra
/// «muito» é a busca do sistema (ver `busca/ProvedorDeBusca.kt`), não uma
/// fileira mais comprida.
///
/// A fileira de «continuar assistindo» fica por cima, deitada, e essa **é** uma
/// fileira — ela tem cinco itens, não dez mil.
@Composable
fun TelaDaBibliotecaDaTv(
    modelo: ModeloDaBiblioteca,
    aoAbrirObra: (String) -> Unit,
    aoTocar: (ItemDaBiblioteca) -> Unit,
    modifier: Modifier = Modifier,
    /// O trilho. Todo item da **primeira coluna** manda a seta ◀ pra cá — ver
    /// `saidaPraEsquerda` em `ui/Pecas.kt` pro defeito que isso conserta.
    saidaEsquerda: FocusRequester? = null,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val grade = rememberLazyGridState()
    val primeiroItem = remember { FocusRequester() }

    /// ## A paginação escuta a rolagem, e não o último item desenhado
    ///
    /// A forma comum — «quando o último item compuser, peça mais» — dispara
    /// várias vezes seguidas numa TV, porque a grade compõe uma fileira inteira
    /// de uma vez e o D-pad a atravessa em milissegundos.
    ///
    /// O `snapshotFlow` com `debounce` pergunta uma vez só por parada de
    /// rolagem. O `distinctUntilChanged` cobre o resto: parar duas vezes no
    /// mesmo lugar não pede duas páginas.
    LaunchedEffect(grade) {
        snapshotFlow { grade.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0 }
            .debounce(120)
            .distinctUntilChanged()
            .filter { ultimo -> ultimo >= estado.quantosNaTela - 10 }
            .collect { modelo.maisUmaPagina() }
    }

    /// Dentro de uma série, «voltar» sai da série — e **não** do app.
    ///
    /// Ele é o mais interno dos três `BackHandler` desta pilha, e por isso ganha:
    /// o Compose entrega a tecla ao mais recente que estiver ligado. A ordem
    /// resultante é a que se espera de fora — episódios ▸ biblioteca ▸ aba
    /// biblioteca ▸ sair.
    BackHandler(enabled = estado.dentroDaSerie) { modelo.sairDaSerie() }

    val temFoco by remember {
        derivedStateOf { estado.itens.isNotEmpty() || estado.episodios.isNotEmpty() }
    }
    LaunchedEffect(temFoco) {
        if (temFoco) runCatching { primeiroItem.requestFocus() }
    }

    when {
        estado.carregando && estado.quantosNaTela == 0 -> Column(
            modifier.fillMaxSize().padding(top = Sala.overscanV),
            verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
        ) {
            FileiraFantasma(deitado = true)
            FileiraFantasma()
        }

        estado.erro != null && estado.quantosNaTela == 0 -> Recado(
            titulo = "não consegui falar com o servidor",
            detalhe = estado.erro,
            modifier = modifier,
        ) {
            BotaoDaSala("tentar de novo", { modelo.primeiraPagina() }, principal = true)
        }

        estado.vazioComFiltro -> Recado(
            titulo = "nada com esse filtro",
            detalhe = "a busca do controle procura no acervo inteiro — segure o microfone e fale o título.",
            modifier = modifier,
        )

        else -> BoxWithConstraints(modifier.fillMaxSize()) {
            /// ## As colunas são contadas, e o número medido desmentiu o
            /// comentário — 12/08/2026
            ///
            /// Aqui estava escrito «cinco colunas em 1080p». Na TCL saíram
            /// **três**, e a conta explica: a tela útil é 960dp, o trilho fechado
            /// come 96 e o overscan mais 96, sobram 768 — que a 220dp por cartaz
            /// (200 + 20 de vão) dá 3,5.
            ///
            /// Três colunas pra 8.316 obras é rolagem demais, então o cartaz
            /// encolheu pra 160dp: `768 / 180 = 4,2`, ou seja **quatro e um
            /// pedaço**. O pedaço é de propósito — uma fileira que termina exata
            /// na borda parece lista completa, e ninguém tenta ir pra direita.
            ///
            /// ⚠️ E agora é `Fixed` e não `Adaptive`, o que parece um passo atrás
            /// e não é: o `Adaptive` não diz quantas colunas escolheu, e sem esse
            /// número não dá pra saber **qual item está na primeira coluna** —
            /// que é exatamente quem precisa do desvio pro trilho.
            val colunas = maxOf(
                1,
                ((maxWidth + Sala.vaoEntreCartazes) / (Sala.cartazL + Sala.vaoEntreCartazes))
                    .toInt(),
            )

            /// ## ⚠️ A marquise é o **teto da tela** — §5.1
        ///
        /// > «**A marquise vira o teto da tela.** Uma fileira de lâmpadas
        /// > douradas ao longo da borda superior, com o mesmo brilho e a mesma
        /// > piscada do trilho. É a terceira aparição do projetor, e a que
        /// > transforma a TV numa **sala** em vez de uma grade.»
        ///
        /// ⚠️ Ela mora **fora** da `LazyVerticalGrid`, colada na borda de cima —
        /// e não como primeiro item dela. Um teto que rola pra fora com o
        /// conteúdo não é teto, é cabeçalho; e a §5.1 pediu teto.
        ///
        /// ⚠️ E ela **não** respeita o `overscanV`, de propósito. O comentário do
        /// `Sala.overscanH` já dizia a régua: «a margem é do conteúdo, não da
        /// tela. Fundo, arte de fundo e o facho vão borda a borda; o que respeita
        /// o overscan é texto, cartaz e qualquer coisa que se possa perder». Uma
        /// fileira de lâmpadas na moldura da TV é exatamente o que uma marquise
        /// de verdade faz.
        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = grade,
            columns = GridCells.Fixed(colunas),
            contentPadding = PaddingValues(
                start = Sala.overscanH,
                end = Sala.overscanH,
                top = Sala.overscanV,
                bottom = Sala.overscanV,
            ),
            horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
            verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
            modifier = Modifier.fillMaxSize(),
        ) {
            /// ## ⚠️ O herói **rola com o resto**, e o teto saiu — 13/08/2026
            ///
            /// > «que teto o que, ao rolar tudo vai sumindo normal, ignora esse
            /// > teto feio aí, deixa como tava»
            ///
            /// Duas coisas saíram no mesmo pedido, e as duas eram minhas:
            ///
            /// | | |
            /// |---|---|
            /// | **a marquise fixa** | a fileira de lâmpadas no topo, que não rolava. A §5.1 a chamava de «o teto da tela»; na sala ela virou uma faixa que fica no caminho |
            /// | **o herói que encolhia** | ele parava no topo virando cabeçalho fino. Agora é item de grade e sai com o resto |
            ///
            /// ⚠️ A §5.1 pedia os dois — «a marquise vira o teto da tela» e «o
            /// herói encolhe e vira um cabeçalho fino, liberando a tela». Eu
            /// implementei os dois como escrito, e o dono viu na sala e cortou.
            /// **A doc perde para o aparelho**, e é a régua da casa: uma ideia
            /// boa no papel que incomoda a três metros é uma ideia ruim.
            ///
            /// O herói **fica** — ele continua sendo a primeira dobra, com a arte
            /// grande, o `faltam` e a barra. O que saiu foi ele se agarrar ao
            /// topo.
            if (estado.paraContinuar.isNotEmpty() && !estado.dentroDaSerie) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    val primeiro = estado.paraContinuar.first()
                    HeroiDaSala(
                        item = primeiro,
                        arte = modelo.arte(primeiro),
                        folhaDoFilme = modelo::folha,
                        urlDaFolha = modelo::urlDaFolha,
                        planoDoArquivo = modelo::planoDoArquivo,
                        urlDeMidia = modelo::urlDeMidia,
                        cabecalhosDeMidia = modelo::cabecalhosDeMidia,
                        modifier = Modifier.padding(bottom = 10.dp),
                    )
                }

                item(span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        RotuloDeSecao("continuar", numero = estado.paraContinuar.size)
                        Row(horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes)) {
                            /// Cinco, e não a lista inteira. A fileira é um
                            /// atalho pro que está pela metade; passar disso é
                            /// uma segunda biblioteca por cima da primeira.
                            estado.paraContinuar.take(5).forEachIndexed { indice, item ->
                                Quadro(
                                    saidaEsquerda = if (indice == 0) saidaEsquerda else null,
                                    titulo = item.tituloDaSerie ?: item.title,
                                    arte = modelo.arte(item),
                                    cor = item.corDominante,
                                    andado = item.fracaoVista ?: 0f,
                                    detalhe = item.temporada?.let { t ->
                                        "T$t" + (item.episodio?.let { "E$it" } ?: "")
                                    },
                                    aoEscolher = { aoAbrirObra(item.id) },
                                )
                            }
                        }
                    }
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }) {
                Cabecalho(
                    titulo = estado.serie?.titulo ?: "biblioteca",
                    conta = estado.serie
                        ?.let { "${estado.episodios.size} de ${it.quantosEpisodios}" },
                    /// Só a biblioteca ganha as contagens douradas: dentro de uma
                    /// série o `conta` acima já diz «3 de 21», e ali o total é o
                    /// da temporada, não do acervo.
                    carregadas = if (estado.dentroDaSerie) null else estado.itens.size,
                    total = if (estado.dentroDaSerie) null else estado.total,
                    aoVoltar = if (estado.dentroDaSerie) modelo::sairDaSerie else null,
                )
            }

            if (estado.dentroDaSerie) {
                itemsIndexed(estado.episodios, key = { _, e -> e.id }) { indice, episodio ->
                    Cartaz(
                        titulo = episodio.title,
                        arte = modelo.arte(episodio),
                        detalhe = episodio.codigo,
                        saidaEsquerda = if (indice % colunas == 0) saidaEsquerda else null,
                        aoEscolher = { aoAbrirObra(episodio.id) },
                    )
                }
            } else {
                itemsIndexed(estado.itens, key = { _, i -> i.id }) { indice, item ->
                    Cartaz(
                        titulo = item.title,
                        arte = modelo.capa(item),
                        cor = item.corDominante,
                        detalhe = detalheDoItem(item),
                        andado = andadoDoItem(item),
                        saidaEsquerda = if (indice % colunas == 0) saidaEsquerda else null,
                        modifier = if (indice == 0) {
                            Modifier.focusRequester(primeiroItem)
                        } else {
                            Modifier
                        },
                        /// Série entra; obra solta abre a ficha. É o mesmo
                        /// desdobramento do celular — o `ModeloDaBiblioteca` já
                        /// sabe fazer os dois.
                        aoEscolher = {
                            if (item.eSerie) modelo.entrarNaSerie(item) else aoTocar(item)
                        },
                    )
                }
            }

            if (estado.carregandoMais) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(Modifier.fillMaxWidth().height(60.dp), Alignment.Center) {
                        Text(
                            "carregando mais…",
                            style = MaterialTheme.typography.labelMedium,
                            color = Cores.textoApagado,
                        )
                    }
                }
            }
        }
    }
        }
    }
}

@Composable
private fun Cabecalho(
    titulo: String,
    conta: String?,
    /// Quantas obras já chegaram, e quantas existem. As duas juntas viram
    /// `60 de 8.316` — ver o corpo.
    carregadas: Int? = null,
    total: Int? = null,
    aoVoltar: (() -> Unit)?,
) {
    Column {
        if (aoVoltar != null) {
            BotaoDaSala("‹ sair da série", aoVoltar)
            Spacer(Modifier.height(18.dp))
        }
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.headlineLarge,
                color = Cores.texto,
            )
            /// ## ⚠️ `60 de 8.316` — **o carregado é dourado, o total é apagado**
            ///
            /// A §2.7 da doc, e ela não é enfeite tipográfico: as duas metades
            /// dizem coisas diferentes. O primeiro número é **o que está aqui
            /// agora**, e por isso ele acende; o segundo é o tamanho do acervo,
            /// que é contexto e não conquista.
            ///
            /// O `:tv` escrevia `8316 obras` — sem separador, sem a divisão, e
            /// sem dizer que a grade tem só as primeiras sessenta. Três
            /// informações perdidas numa string só.
            ///
            /// ⚠️ **O separador é o de português, e não o do aparelho.**
            ///
            /// A primeira versão usava `"%,d".format(n)`, que pega o `Locale` da
            /// TV — e o comentário aqui dizia que isso era «o certo pra quem está
            /// lendo». Na TCL, configurada em inglês, saiu **`8,316`**.
            ///
            /// Está errado, e de um jeito que muda o sentido: em português a
            /// vírgula é separador **decimal**. `8,316` não se lê «oito mil», se
            /// lê «oito vírgula três». O acervo inteiro virou um número menor que
            /// nove.
            ///
            /// E a régua da casa já tinha resposta: este app é escrito em
            /// português do começo ao fim — os nomes das funções, os rótulos, a
            /// voz. Um número que muda de forma conforme o idioma do aparelho é
            /// a única coisa da tela que não seria. `pt-BR` explícito.
            if (carregadas != null && total != null) {
                Spacer(Modifier.width(20.dp))
                Row(
                    verticalAlignment = Alignment.Bottom,
                    modifier = Modifier.padding(bottom = 6.dp),
                ) {
                    Text(
                        text = comMilhar(carregadas),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Cores.destaque,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "de",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cores.textoApagado,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = comMilhar(total),
                        style = MaterialTheme.typography.headlineSmall,
                        color = Cores.textoApagado,
                    )
                }
            } else if (conta != null) {
                Spacer(Modifier.width(20.dp))
                Text(
                    text = conta,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.textoApagado,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
        }
        Spacer(Modifier.height(10.dp))
    }
}

/// `1969 · 4 temporadas` — a linha de metadados da R4, na medida da sala.
///
/// Item por item, e não a linha inteira: **8.598 obras não têm arquivo casado**,
/// e omitir o que falta é o §24. Uma linha que escreve «— · —» é pior que uma
/// linha mais curta.
private fun detalheDoItem(item: ItemDaBiblioteca): String? = buildList {
    item.year?.let { add(it.toString()) }
    when {
        item.eSerie && item.quantasTemporadas > 0 ->
            add("${item.quantasTemporadas} temporada" + if (item.quantasTemporadas > 1) "s" else "")
        item.eSerie && item.quantasObras > 0 -> add("${item.quantasObras} episódios")
        item.height != null -> add("${item.height}p")
    }
}.takeIf { it.isNotEmpty() }?.joinToString(" · ")

/// A barrinha do cartaz. Só há dado pra ela numa obra solta que foi começada.
private fun andadoDoItem(item: ItemDaBiblioteca): Float {
    val onde = item.ondeParou ?: return 0f
    val total = item.duracaoEmSegundos?.takeIf { it > 0 } ?: return 0f
    return (onde / total).toFloat().coerceIn(0f, 1f)
}


/// `8316` → `8.316`. O separador é o de **português**, sempre.
///
/// ⚠️ `Locale.forLanguageTag("pt-BR")` explícito e não o do aparelho — ver o
/// comentário longo no `Cabecalho`. O defeito que isto conserta apareceu na TCL
/// como `8,316`, que em português é oito vírgula três.
private fun comMilhar(n: Int): String =
    String.format(java.util.Locale.forLanguageTag("pt-BR"), "%,d", n)
