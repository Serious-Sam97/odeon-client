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
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.shape.RoundedCornerShape
import dev.odeon.android.tv.ui.Focavel
import androidx.compose.foundation.background
import dev.odeon.android.tv.ui.saidaPraEsquerda
import dev.odeon.android.dados.EtiquetaDoAcervo
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.dados.ObraDaLista
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Cartaz
import dev.odeon.android.tv.ui.FileiraFantasma
import dev.odeon.android.tv.ui.Quadro
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.biblioteca.ModeloDaBiblioteca
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.odeon.android.tv.ui.EscolhaDeVersaoDaSala

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
    /// ⚠️ Série **sai desta tela** — 18/08/2026. Ela era um modo daqui
    /// (`entrarNaSerie` trocava a grade no lugar) e agora é destino próprio, com
    /// ficha e temporadas. Ver `Onde.Serie` e `docs/SERIES.md`.
    aoAbrirSerie: (id: String, titulo: String) -> Unit,
    aoTocar: (ItemDaBiblioteca) -> Unit,
    /// ⚠️ A biblioteca da TV é a dos **filmes**: muda o título e liga o
    /// `semSéries()`. Quem tira as séries é o servidor (`?tags_not=`).
    escondendoSeries: Boolean = false,
    modifier: Modifier = Modifier,
    /// O trilho. Todo item da **primeira coluna** manda a seta ◀ pra cá — ver
    /// `saidaPraEsquerda` em `ui/Pecas.kt` pro defeito que isso conserta.
    saidaEsquerda: FocusRequester? = null,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    LaunchedEffect(escondendoSeries) { if (escondendoSeries) modelo.semSéries() }
    val grade = rememberLazyGridState()
    val primeiroItem = remember { FocusRequester() }

    /// ## ⚠️ A série abria **no meio da temporada 3** — visto na TCL, 17/08/2026
    ///
    /// A biblioteca e a lista de episódios são a **mesma** `LazyVerticalGrid`, e
    /// portanto o mesmo `rememberLazyGridState`. Trocar o conteúdo não mexe na
    /// rolagem: eu havia descido 16 fileiras no acervo, entrei no `Arrested
    /// Development` e caí na fileira 16 **dele** — em `S03E11`, com o cabeçalho
    /// e o nome da série fora da tela.
    ///
    /// É pior do que parece porque o cabeçalho da série mora **dentro** da
    /// grade: quem cai no meio não tem, na tela, nada que diga onde está nem
    /// como sair.
    ///
    /// ⚠️ **Sair devolve a posição de antes**, e não o topo. A pessoa não pediu
    /// pra voltar ao começo do acervo: ela pediu pra sair da série, e o lugar de
    /// onde ela veio é onde estava a série que ela escolheu. Mandar pro topo
    /// trocaria um lugar arbitrário por outro.
    var ondeEstavaOAcervo by remember { mutableStateOf(0 to 0) }
    val serieAberta = estado.serie?.id
    LaunchedEffect(serieAberta) {
        if (serieAberta != null) {
            ondeEstavaOAcervo = grade.firstVisibleItemIndex to grade.firstVisibleItemScrollOffset
            grade.scrollToItem(0)
        } else {
            val (indice, deslocamento) = ondeEstavaOAcervo
            grade.scrollToItem(indice, deslocamento)
        }
    }

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

    /// ⚠️ **O `BackHandler` da série saiu daqui** — 18/08/2026. Ele existia
    /// porque «dentro de uma série» era um estado desta tela; agora é destino
    /// próprio (`Onde.Serie`), e quem trata a tecla é a tela de lá.

    /// O item cuja escolha de versão está aberta. `null` é a modal fechada.
    ///
    /// ⚠️ Guarda o **item**, e não o id: as versões já vieram dentro dele, na
    /// mesma resposta da grade. Guardar só o id obrigaria a procurá-lo de volta
    /// na lista a cada recomposição — e a lista cresce quando outra página chega.
    var escolhendoVersao by remember { mutableStateOf<ItemDaBiblioteca?>(null) }

    val temFoco by remember {
        derivedStateOf { estado.itens.isNotEmpty() || estado.episodios.isNotEmpty() }
    }
    LaunchedEffect(temFoco) {
        if (temFoco) primeiroItem.insista()
    }

    /// ## ⚠️ Entrar numa série **não** redisparava o efeito acima — 17/08/2026
    ///
    /// `temFoco` é «há itens **ou** episódios». Entrando numa série ele já era
    /// `true` por causa dos itens e **continua** `true` por causa dos episódios:
    /// nunca muda, e um `LaunchedEffect` que não vê a chave mudar não roda de
    /// novo. Só que o cartão que tinha o foco — a série escolhida — sumiu da
    /// composição no meio do caminho.
    ///
    /// Foco sem dono sobe: ia parar no trilho, que **abre quando tem foco** e
    /// fica por cima dos episódios. A série abria com o menu na frente dela.
    ///
    /// ⚠️ Espera a primeira página chegar antes de pedir o foco. Pedir na hora
    /// da troca é pedir a um nó que ainda não existe — o mesmo jeito calado de
    /// falhar que este arquivo já pagou duas vezes hoje.
    LaunchedEffect(serieAberta) {
        if (serieAberta == null) return@LaunchedEffect
        snapshotFlow { estado.episodios.isNotEmpty() }.first { it }
        primeiroItem.insista()
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
            /// ## ⚠️ Dentro de uma série o cartão é **deitado** — 17/08/2026
            ///
            /// Um episódio não é um cartaz. A capa de `Arrested Development` é a
            /// mesma nos 84, então ela não distingue nada; o que distingue é o
            /// **quadro** — e o `ObraDaLista.arte` já prefere o `still`, que é
            /// 16:9. Espremê-lo num cartaz de 2:3 jogava fora ~45% da largura no
            /// corte central: visto na TCL, o `S01E03` do `Arrested` virou uma
            /// roda de bicicleta.
            ///
            /// A peça certa já existia e já dizia isto na própria folha: «quem
            /// parou no meio já sabe **que** filme é, e quer saber **onde
            /// estava**. Uma capa não diz isso; um quadro diz.» Faltava a grade
            /// de episódios usá-la.
            ///
            /// ⚠️ Sete colunas viram três, e **é a intenção**: 84 miniaturas
            /// ilegíveis não são um índice, são um mosaico.
            val larguraDoCartao = if (estado.dentroDaSerie) Sala.quadroL else Sala.cartazL
            val colunas = maxOf(
                1,
                ((maxWidth + Sala.vaoEntreCartazes) / (larguraDoCartao + Sala.vaoEntreCartazes))
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
                    titulo = estado.serie?.titulo
                        ?: if (escondendoSeries) "filmes" else "biblioteca",
                    /// ## ⚠️ **`18 de 1`** — o total só entra se ele se sustentar
                    ///
                    /// Visto na TCL: `Arcane` aberta pela busca escreveu `18 de
                    /// 0`, e depois `18 de 1`. Os dois números vieram do
                    /// servidor: o `work_count` de `/api/library?q=` devolve **1**
                    /// pra essa série, enquanto o da grade devolve **84** pro
                    /// `Arrested Development` — que é o certo.
                    ///
                    /// Não dá pra escolher qual dos dois é o bom daqui. O que dá
                    /// pra fazer é **não afirmar o que não se sustenta**: um
                    /// total menor do que a quantidade já carregada é falso por
                    /// construção, e aí a tela conta só o que tem na mão.
                    ///
                    /// É o §18 na forma mais literal — omitir o número em vez de
                    /// inventar um. Ver `PEDIDOS-AO-SERVIDOR.md` §8.
                    conta = estado.serie?.let { serie ->
                        val quantos = estado.episodios.size
                        if (serie.quantosEpisodios >= quantos && quantos > 0) {
                            "$quantos de ${serie.quantosEpisodios}"
                        } else {
                            quantos.takeIf { n -> n > 0 }?.let { n -> "$n episódios" }
                        }
                    },
                    /// Só a biblioteca ganha as contagens douradas: dentro de uma
                    /// série o `conta` acima já diz «3 de 21», e ali o total é o
                    /// da temporada, não do acervo.
                    carregadas = if (estado.dentroDaSerie) null else estado.itens.size,
                    total = if (estado.dentroDaSerie) null else estado.total,
                    aoVoltar = if (estado.dentroDaSerie) modelo::sairDaSerie else null,
                )
            }

            /// ⚠️ **A fileira de prateleiras saiu daqui** — 18/08/2026. Ela
            /// era `tudo · série · filme · anime` e parecia o que não era: uma
            /// segunda barra de filtros. Séries virou destino da trilha; esta
            /// tela é a dos **filmes**. Ver `TelaDasSeriesDaTv`.
            if (estado.dentroDaSerie) {
                itemsIndexed(estado.episodios, key = { _, e -> e.id }) { indice, episodio ->
                    Quadro(
                        titulo = episodio.title,
                        arte = modelo.arte(episodio),
                        cor = episodio.corDominante,
                        detalhe = episodio.codigo,
                        /// ⚠️ Os dois campos **já chegavam** e eram jogados fora:
                        /// a grade de filmes passava `andado`, a de episódios
                        /// não. Numa série é onde eles mais valem — «até onde eu
                        /// fui» é a pergunta que se faz diante de 84 cartões.
                        andado = andadoDoEpisodio(episodio),
                        visto = episodio.finished == true,
                        saidaEsquerda = if (indice % colunas == 0) saidaEsquerda else null,
                        /// ## ⚠️ O `primeiroItem` **não estava amarrado aqui** —
                        /// 17/08/2026
                        ///
                        /// O `temFoco` acima acende com `episodios.isNotEmpty()`
                        /// e chama `primeiroItem.requestFocus()`, mas o
                        /// `FocusRequester` só era pendurado no ramo dos filmes.
                        /// Dentro de uma série ele apontava pro vazio, o
                        /// `runCatching` engolia, e o foco ficava onde estivesse —
                        /// **no trilho**, que fica aberto por cima dos episódios.
                        ///
                        /// Só apareceu agora porque até hoje só se entrava numa
                        /// série pela própria grade, onde o foco já estava no
                        /// conteúdo. Vindo da busca, a tela troca inteira — e a
                        /// série abria com o menu na frente dela.
                        ///
                        /// ⚠️ É o mesmo defeito do ◀ do teclado da busca, na
                        /// mesma semana: **um requester sem nó falha calado**.
                        modifier = if (indice == 0) {
                            Modifier.focusRequester(primeiroItem)
                        } else {
                            Modifier
                        },
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
                        ///
                        /// ⚠️ E desde 14/08/2026 há um terceiro caso no meio:
                        /// filme com mais de uma versão **pergunta antes de
                        /// abrir**. Uma versão só continua caindo direto na
                        /// ficha, como sempre — ver `temEscolhaDeVersao`.
                        aoEscolher = {
                            when {
                                item.eSerie -> aoAbrirSerie(item.id, item.title)
                                item.temEscolhaDeVersao -> escolhendoVersao = item
                                else -> aoTocar(item)
                            }
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

        /// ⚠️ **Depois da grade e dentro do mesmo `Box`**, pra desenhar por cima.
        /// Fosse irmã do `BoxWithConstraints`, ela dividiria a tela com a grade
        /// em vez de cobri-la.
        escolhendoVersao?.let { aberto ->
            EscolhaDeVersaoDaSala(
                item = aberto,
                aoFechar = { escolhendoVersao = null },
                aoEscolher = { versao ->
                    escolhendoVersao = null
                    aoAbrirObra(versao.id)
                },
            )
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

/// A barrinha de um **episódio**. Mesma conta do `andadoDoItem`, noutro tipo:
/// `ObraDaLista` traz `position_seconds` e `duration_seconds` como a
/// `ItemDaBiblioteca`, e é o servidor que já sabe onde a pessoa parou.
///
/// ⚠️ Zero quando não há duração — dividir por ela daria uma barra cheia num
/// episódio nunca aberto, que é a mentira mais cara desta tela.
private fun andadoDoEpisodio(episodio: ObraDaLista): Float {
    val onde = episodio.ondeParou ?: return 0f
    val total = episodio.duracaoEmSegundos?.takeIf { it > 0 } ?: return 0f
    return (onde / total).toFloat().coerceIn(0f, 1f)
}

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

/// Pede o foco **até o nó existir**.
///
/// ## ⚠️ `isNotEmpty()` não quer dizer «já está na tela»
///
/// A lista chegar e o item estar **composto e anexado** são dois momentos, e o
/// segundo é o que um `FocusRequester` precisa. Pedir no primeiro é o mesmo jeito
/// calado de falhar que este arquivo já pagou três vezes esta semana: o
/// `requestFocus` lança, alguém engole com `runCatching`, e o foco fica onde
/// estava — no trilho, que **abre quando tem foco** e cobre a tela inteira.
///
/// Medido na TCL: entrando numa série pela busca, a tela nascia com o menu por
/// cima dos episódios. Uma tentativa só nunca pegava; com as tentativas, pega na
/// segunda ou terceira.
///
/// ## ⚠️ E **não** dá pra sair no primeiro «deu certo»
///
/// Foi a primeira tentativa de conserto, e ela não funcionou: `requestFocus()`
/// num nó ainda não anexado **não lança** — ele volta normal e não faz nada. O
/// `runCatching(...).isSuccess` dava `true` na primeira volta, o laço parava, e o
/// foco continuava no trilho. Medido na TCL: a tela abriu com o menu por cima
/// exatamente igual a antes do conserto.
///
/// Então ele pede as N vezes, sem perguntar. Quando o item aparece, um dos
/// pedidos pega; os anteriores não custam nada.
///
/// ⚠️ Seis tentativas de 70ms é ~0,4s de teto, e ele **termina**. É curto de
/// propósito: enquanto o laço roda, ele ganha de quem mexer no controle — e
/// meio segundo é o que dura a troca de tela, não o que dura a paciência de
/// alguém.
private suspend fun FocusRequester.insista(vezes: Int = 6, pausa: Long = 70) {
    repeat(vezes) {
        runCatching { requestFocus() }
        delay(pausa)
    }
}
