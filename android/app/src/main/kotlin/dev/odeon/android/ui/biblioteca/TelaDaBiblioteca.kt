package dev.odeon.android.ui.biblioteca

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import dev.odeon.android.dados.EtiquetaDoAcervo
import dev.odeon.android.ui.PilulaDeFiltro
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.dados.ObraDaLista
import dev.odeon.android.dados.VersaoDaObra
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.duracaoCompacta
import dev.odeon.android.ui.idiomasEmPortugues
import dev.odeon.android.ui.tamanhoCompacto
import dev.odeon.android.ui.MolduraDoCartaz
import dev.odeon.android.ui.LampadasDaMarquise
import dev.odeon.android.ui.Luz
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.pegaLuz
import dev.odeon.android.ui.corDeHex
import kotlin.math.max
import kotlin.math.min
import dev.odeon.android.dados.ItemPraContinuar
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.layout.width

/// A biblioteca.
///
/// ## Ela lista séries, não episódios
///
/// A fonte é `/api/library`, que agrupa. `/api/works` devolveria os 14.657
/// episódios do acervo como cartões iguais — e a web já concluiu o que isso é:
/// «listagem de arquivo e não biblioteca».
@Composable
fun TelaDaBiblioteca(
    modelo: ModeloDaBiblioteca,
    aoAbrirObra: (String) -> Unit = {},
    /// ⚠️ Série **sai desta tela** — 18/08/2026. Ela era um modo daqui
    /// (`entrarNaSerie` trocava a grade no lugar) e virou destino próprio, com
    /// ficha e temporadas. Ver `docs/SERIES.md`.
    /// ⚠️ Continua existindo porque a busca e a locadora ainda trazem série pra
    /// cá — e uma série que caia nesta grade deve abrir a ficha dela, não a
    /// ficha de obra (que dá 404).
    aoAbrirSerie: (id: String, titulo: String) -> Unit = { _, _ -> },
    /// ⚠️ A aba dos **filmes** liga isto, e hoje ele só muda **as palavras** —
    /// o título e o texto da busca.
    ///
    /// Ele já foi um corte na tela: `itens.filter { !it.eSerie }`, porque a API
    /// não sabia dizer «tudo menos série». Com o `?tags_not=` de 18/08/2026 quem
    /// tira é o servidor, e o corte saiu daqui — junto com o custo dele, que era
    /// o cabeçalho contar um conjunto e a grade mostrar outro.
    escondendoSeries: Boolean = false,
    aoAbrirBaixados: () -> Unit = {},
    /// Quantos filmes estão no aparelho — a pastilha acesa da fileira de
    /// filtros. Vem do `AppOdeon`, que já segura o `Baixados`: dar ao modelo da
    /// biblioteca uma dependência de download só pra contar seria acoplar a
    /// grade ao Media3 por um número.
    quantosBaixados: Int = 0,
    /// Como marcar o pôster pra transição compartilhada — ver `MolduraDoCartaz`.
    moldura: MolduraDoCartaz = MolduraDoCartaz.Nenhuma,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    /// ## ⚠️ **Série não aparece no «continuar» dos filmes** — 18/08/2026
    ///
    /// O dono viu: o episódio de `Arcane` que ele tinha começado abria o **herói
    /// da aba dos filmes**. A grade já não trazia série (o servidor tira, com
    /// `?tags_not=`), mas a fileira de continuar vem de outra rota e trazia.
    ///
    /// Cada aba fala do que ela guarda: série começada é assunto da aba das
    /// séries, que tem uma seção só pra isso (`na metade`).
    val paraContinuar = remember(estado.paraContinuar, escondendoSeries) {
        if (escondendoSeries) {
            estado.paraContinuar.filter { it.tituloDaSerie == null }
        } else {
            estado.paraContinuar
        }
    }
    val grade = rememberLazyGridState()

    /// O item cuja escolha de versão está aberta. `null` é a folha fechada.
    /// Ver `EscolhaDeVersao`, no fim deste arquivo.
    var escolhendoVersao by remember { mutableStateOf<ItemDaBiblioteca?>(null) }

    /// Quando pedir a próxima página.
    ///
    /// `derivedStateOf` porque a rolagem muda o índice a cada quadro, e sem ele
    /// esta condição recomporia a tela inteira sessenta vezes por segundo.
    val chegouNoFim by remember {
        derivedStateOf {
            val ultimoVisivel = grade.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            ultimoVisivel >= grade.layoutInfo.totalItemsCount - 6
        }
    }

    androidx.compose.runtime.LaunchedEffect(chegouNoFim, estado.temMais) {
        if (chegouNoFim && estado.temMais) modelo.maisUmaPagina()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        /// O erro fica **fora** da grade, e é o único que fica.
        ///
        /// Ele é o oposto do cabeçalho: rolar não pode fazê-lo sumir, senão a
        /// página que falhou some junto e sobra uma biblioteca que só parece ter
        /// acabado — o §8b outra vez. Quando não há erro isto não emite nada e
        /// não ocupa altura nenhuma, então a grade recebe a tela inteira.
        estado.erro?.let { frase ->
            Text(
                text = frase,
                color = Cores.perigo,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        /// ## ⚠️ Carregando, a tela é **a própria tela** — não um risquinho
        ///
        /// Medido no emulador em 16/08/2026: da abertura até a grade aparecer
        /// passam-se **cerca de dez segundos**, e nesses dez segundos a porta de
        /// entrada do app era um vão preto com um risco dourado girando no meio.
        /// Nem título, nem busca, nem forma — nada que dissesse sequer *qual* app
        /// tinha aberto.
        ///
        /// A regra da casa é a §15, e ela já está aplicada na locadora e na grade
        /// de capítulos: **moldura vazia em vez de «carregando»**. Uma moldura diz
        /// duas coisas que um indicador não diz — o que vem (cartazes, neste
        /// tamanho, nesta quantidade) e onde vai estar. O indicador só diz
        /// «espere», que a pessoa já sabia.
        ///
        /// ⚠️ O cabeçalho é o mesmo, com os mesmos callbacks — **não é um
        /// desenho de mentira**. A contagem se omite sozinha enquanto o total é
        /// nulo (§24, ver `Cabecalho`), e a busca já funciona: digitar aqui vale
        /// pra carga que está vindo, em vez de ser um campo morto esperando o fim
        /// de uma espera.
        if (estado.carregando) {
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 108.dp),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Cabecalho(
                        titulo = if (escondendoSeries) "filmes" else "biblioteca",
                        ondeProcura = if (escondendoSeries) "nos filmes" else "na biblioteca",
                        quantos = estado.quantosNaTela,
                        total = estado.total,
                        busca = estado.filtros.busca,
                        aoBuscar = modelo::mudouBusca,
                        serie = estado.serie,
                        aoSairDaSerie = modelo::sairDaSerie,
                    )
                }
                /// Doze é o que cobre a tela de um celular sem passar muito: o
                /// bastante pra a grade ter forma, e não tanto que a chegada dos
                /// cartazes de verdade pareça uma segunda tela.
                items(12) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .aspectRatio(2f / 3f)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Cores.fundoElevado),
                    )
                }
            }
            return@Column
        }

        /// ## A barra que condensa fica **por cima** da grade
        ///
        /// O cabeçalho continua sendo item da grade e sobe junto com a primeira
        /// fileira — a decisão de 04/08/2026 sobre os 180px continua valendo.
        /// O que muda é que, depois que ele sai de vista, uma barra fina toma o
        /// lugar dele **flutuando**, e não empurrando a grade pra baixo.
        ///
        /// É o «condensar por rolagem» que o §1.1 do `PARIDADE` lista como coisa
        /// que a web faz e o app não. E o que ela mantém é a **busca**, por
        /// pedido do dono: o comentário do `CampoDeBusca` dizia que «quem vai
        /// buscar volta ao topo, que é um gesto só» — verdade, e um gesto a mais
        /// do que não precisar voltar.
        Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            state = grade,
            /// `Adaptive` e não um número fixo de colunas: o mesmo código serve
            /// celular em pé, celular deitado e tablet, e o cartaz mantém a
            /// largura em que ele é legível em vez de esticar.
            columns = GridCells.Adaptive(minSize = 108.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            modifier = Modifier.fillMaxSize(),
        ) {
            /// O cabeçalho é um item da grade, ocupando a linha inteira — e não
            /// uma faixa fixa por cima dela.
            ///
            /// ## Por que ele deixou de ser fixo
            ///
            /// Fixo, ele custava **180px dos 2400** em pé, e os mesmos 180px de
            /// **1080** deitado — 17% da tela, medido no emulador em
            /// 04/08/2026, com o resultado de sobrar **uma fileira e meia** de
            /// cartaz num celular na horizontal.
            ///
            /// E o preço não era só altura: a grade rolava **por baixo** dele e
            /// era cortada na borda, então a linha de cima aparecia como títulos
            /// soltos sem pôster em cima. Parecia defeito de carregamento.
            ///
            /// Como item, ele sobe junto com a primeira fileira e devolve a tela
            /// inteira pros cartazes — que é o que a pessoa veio ver.
            ///
            /// **O que se perde:** a contagem sai de vista depois da primeira
            /// rolada. Ela é contexto, não comando — quem rola já está olhando o
            /// acervo, e volta ao topo pra reler.
            item(span = { GridItemSpan(maxLineSpan) }) {
                Cabecalho(
                    titulo = if (escondendoSeries) "filmes" else "biblioteca",
                    ondeProcura = if (escondendoSeries) "nos filmes" else "na biblioteca",
                    quantos = estado.quantosNaTela,
                    total = estado.total,
                    busca = estado.filtros.busca,
                    aoBuscar = modelo::mudouBusca,
                    serie = estado.serie,
                    aoSairDaSerie = modelo::sairDaSerie,
                )
            }

            /// A barra de filtros — e ela **não existe dentro da série**.
            ///
            /// Lá dentro a lista é a numeração de um enredo: filtrar 62
            /// episódios por gênero devolveria os mesmos 62, e por duração
            /// devolveria um recorte que ninguém pediu. O que serve ali é sair,
            /// e o chip «Dentro de» já faz isso.
            /// ## ⚠️ A frase do vazio vai **junto da barra**, e foi preciso medir
            ///
            /// Ela era um `item` próprio da grade, logo depois deste — e **nunca
            /// aparecia**. Buscar «zzzqqq» dava «0 de 0» e um vão preto, que é
            /// exatamente o defeito que ela existe pra não ter: «uma grade em
            /// branco depois de digitar parece defeito».
            ///
            /// Medido em 17/08/2026, com dois `Log` temporários:
            ///
            /// | pergunta | resposta |
            /// |---|---|
            /// | a condição liga? | `vazioComFiltro=true`, `naTela=0`, `erro=null` |
            /// | o `item` é registrado? | sim, o bloco roda |
            /// | o item **compõe**? | **nunca**, zero vezes |
            ///
            /// A grade recebia o item e não o punha na tela. Fora dela a frase
            /// aparece, mas **acima do cabeçalho** — a resposta antes da pergunta.
            /// Dentro deste item, que comprovadamente compõe, ela cai onde
            /// pertence: colada nos chips, no lugar em que os resultados
            /// começariam.
            ///
            /// ⚠️ A causa dentro da `LazyVerticalGrid` ficou **sem explicação**.
            /// Está anotado como tal em vez de virar uma teoria bonita: o que se
            /// sabe é que o item era registrado e não compunha, e que daqui ele
            /// compõe.
            if (!estado.dentroDaSerie) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                  Column {
                    /// ⚠️ **A fileira de prateleiras saiu daqui** — 18/08/2026.
                    /// Ela era `tudo · série · filme · anime` e parecia o que
                    /// não era: uma segunda barra de filtros. Séries virou aba;
                    /// esta tela é a dos **filmes**. Ver `TelaDasSeries`.
                    BarraDeFiltros(
                        filtros = estado.filtros,
                        etiquetasPorEspaco = estado.etiquetasPorEspaco,
                        aberto = estado.painelAberto,
                        aoAlternarPainel = modelo::alternarPainel,
                        aoMudar = modelo::mudouFiltros,
                        quantosBaixados = quantosBaixados,
                        aoAbrirBaixados = aoAbrirBaixados,
                    )

                    if (estado.vazioComFiltro) {
                        Text(
                            text = if (estado.filtros.busca.isNotBlank()) {
                                "nada com «${estado.filtros.busca}» no acervo"
                            } else {
                                "nada no acervo com esses filtros"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cores.textoApagado,
                            modifier = Modifier.padding(vertical = 24.dp),
                        )
                    }
                  }
                }
            }

            /// Filtrou (ou buscou) e não veio nada.
            ///
            /// ⚠️ Aqui o §24 **não** vale, e é o mesmo caso da locadora sem
            /// caixa fora: uma grade em branco depois de digitar parece defeito
            /// — o campo tem texto, a tela não tem nada, e nada explica a
            /// ligação. A frase repete o que foi pedido porque é ela que fecha
            /// a pergunta.

            /// A fileira de "continuar de onde parou", **acima** do acervo.
            ///
            /// ## Por que aqui, e por que rolando junto
            ///
            /// É a tese da §5: «você parou na TV e continua no ônibus». Quem
            /// abre o app com um filme pela metade quase sempre veio por causa
            /// dele — então ele fica antes dos 8.316, e não escondido numa aba.
            ///
            /// Mas rola junto com a grade, como o cabeçalho: fixá-la custaria
            /// mais um terço da tela em paisagem, que é o defeito que o
            /// cabeçalho fixo já tinha causado uma vez.
            ///
            /// E some inteira quando não há nada — sem título órfão, sem "nada
            /// por aqui" (§24).
            /// O herói da chegada — leva 2 do segundo redesenho.
            ///
            /// ## A tela mais visitada era a mais plana
            ///
            /// Toda sessão começa aqui, e aqui havia menos desenho que em
            /// qualquer outra tela: cabeçalho, uma fileira e 8.316 retângulos do
            /// mesmo tamanho. O "para você" — a aba menos visitada — ganhou
            /// herói na leva 4 do primeiro redesenho e virou a tela mais bonita
            /// do app. Esta não tinha.
            ///
            /// ## Ele é o que se estava assistindo, e não uma recomendação
            ///
            /// É a diferença entre as duas telas. O herói do "para você"
            /// responde *o que assistir*; este responde *onde você parou* — que
            /// é a pergunta de quem abre o app com um filme pela metade, e a
            /// §5 da espec chama de «você parou na TV e continua no ônibus».
            ///
            /// ⚠️ **Sem nada pela metade, não há herói** (§24). A tela volta a
            /// abrir na grade, sem faixa vazia e sem "nada por aqui".
            /// ⚠️ **Buscando, o herói e a fileira somem** — e foi o screenshot
            /// que mandou.
            ///
            /// Com «goldfinger» no campo, a tela mostrava: o herói de 16:9 do
            /// que se estava assistindo, a fileira de continuar com quatro
            /// cartazes, e só então os **2 de 2** resultados — abaixo da dobra.
            /// Ou seja, a resposta à pergunta feita ficava atrás de duas coisas
            /// que ninguém perguntou.
            ///
            /// Os dois são contexto de **chegada** («onde você parou»), e quem
            /// digita já sabe o que quer. Some enquanto durar a busca, e volta
            /// inteiro quando o campo esvazia.
            ///
            /// E somem **dentro da série** pelo mesmo motivo: quem entrou em
            /// *Breaking Bad* está olhando a lista de episódios, e um herói de
            /// outro filme no topo dela é a tela mudando de assunto sozinha.
            val buscando = estado.filtros.busca.isNotBlank() ||
                estado.filtros.algumLigado ||
                estado.dentroDaSerie

            if (!buscando && paraContinuar.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    HeroiDaChegada(
                        item = paraContinuar.first(),
                        arte = modelo.arte(paraContinuar.first()),
                        aoTocar = { aoAbrirObra(paraContinuar.first().id) },
                    )
                }
            }

            if (!buscando && paraContinuar.size > 1) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FileiraParaContinuar(
                        /// O primeiro virou herói, então a fileira mostra o
                        /// resto. Repeti-lo seria a mesma obra duas vezes na
                        /// mesma tela, a 30dp de distância.
                        itens = paraContinuar.drop(1),
                        arte = modelo::arte,
                        aoTocar = { aoAbrirObra(it.id) },
                    )
                }
            }

            if (estado.dentroDaSerie) {
                /// A lista quebra **por temporada** — §4 da referência.
                ///
                /// `Especiais` pra a temporada 0 (é o que a numeração do TMDB
                /// usa) e `Sem temporada` pra o episódio que a identificação
                /// ainda não numerou. Nenhum dos dois é caso raro num acervo
                /// com 3.350 obras esperando revisão.
                porTemporada(estado.episodios).forEach { (rotulo, doGrupo) ->
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        /// `TEMPORADA 2 · 3 VISTOS ——— 10`.
                        ///
                        /// O «vistos» some quando é zero (§24): uma temporada
                        /// que ninguém começou não precisa anunciar que ninguém
                        /// a começou, e o número da direita já diz o tamanho.
                        val vistos = doGrupo.count { it.finished == true }
                        RotuloDeSecao(
                            texto = if (vistos > 0) "$rotulo · $vistos vistos" else rotulo,
                            numero = doGrupo.size,
                        )
                    }
                    items(doGrupo, key = { it.id }) { episodio ->
                        CartaoDeEpisodio(
                            item = episodio,
                            arte = modelo.arte(episodio),
                            aoTocar = { aoAbrirObra(episodio.id) },
                        )
                    }
                }
            } else {
                items(estado.itens, key = { it.id }) { item ->
                    Cartaz(
                        item = item,
                        capa = modelo.capa(item),
                        /// ⚠️ **Série não abre ficha, série abre a série.**
                        ///
                        /// É a regra da web (§4): o cartão agrupado «vira filtro
                        /// de coleção». A ficha de uma série responde «o que é
                        /// esta obra», e quem toca numa capa de *Breaking Bad*
                        /// está perguntando outra coisa — quais episódios há, e
                        /// quais já viu.
                        ///
                        /// ⚠️ E há um terceiro caso desde 14/08/2026: filme com
                        /// mais de uma versão no acervo **pergunta qual** antes
                        /// de abrir a ficha. Ver `EscolhaDeVersao`.
                        aoTocar = {
                            when {
                                item.eSerie -> aoAbrirSerie(item.id, item.title)
                                item.temEscolhaDeVersao -> escolhendoVersao = item
                                else -> aoAbrirObra(item.id)
                            }
                        },
                        moldura = moldura,
                    )
                }
            }
        }

        /// ## Ela aparece quando o cabeçalho sai de vista
        ///
        /// `firstVisibleItemIndex > 0` — o cabeçalho é o item 0, e o teste é
        /// «ele já subiu?». Dentro de um `derivedStateOf` porque a rolagem muda
        /// o índice a cada quadro, e sem ele a tela recomporia sessenta vezes
        /// por segundo pra responder um booleano que muda uma vez.
        ///
        /// ⚠️ **Não aparece dentro da série.** Lá o cabeçalho é o nome da série e
        /// o chip de saída, e não há filtro nenhum — uma barra fixa com busca e
        /// `filtros ▾` ofereceria dois controles que aquela lista não tem (§53).
        val condensado by remember {
            derivedStateOf { grade.firstVisibleItemIndex > 0 }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = condensado && !estado.dentroDaSerie,
            enter = androidx.compose.animation.fadeIn() +
                androidx.compose.animation.slideInVertically { -it },
            exit = androidx.compose.animation.fadeOut() +
                androidx.compose.animation.slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            BarraCondensada(
                ondeProcura = if (escondendoSeries) "nos filmes" else "na biblioteca",
                busca = estado.filtros.busca,
                aoBuscar = modelo::mudouBusca,
                filtrosLigados = estado.filtros.quantosLigados,
                aoAbrirFiltros = modelo::alternarPainel,
                quantosBaixados = quantosBaixados,
                aoAbrirBaixados = aoAbrirBaixados,
            )
        }
        }

        escolhendoVersao?.let { aberto ->
            EscolhaDeVersao(
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

/// A escolha de versão, quando o mesmo filme está no acervo mais de uma vez.
///
/// ## Por que ela existe
///
/// O dono baixou alguns filmes **duas vezes** — um em pt-BR e outro em inglês —
/// porque não achou dual audio, e até 14/08/2026 os dois ocupavam cartões
/// separados na grade. Agora o servidor os agrupa (`ItemDaBiblioteca.versoes`), e
/// esta folha é onde a escolha acontece. O pedido inteiro está no §2.1 do
/// `docs/PEDIDOS-AO-SERVIDOR.md`.
///
/// ⚠️ **Ela escolhe uma obra, e não um arquivo.** Cada versão tem id, progresso e
/// ficha próprios; o toque abre a ficha daquela obra, com o botão de assistir de
/// sempre. Nada é fundido — fundir apagaria o progresso de uma das duas.
///
/// ## Por que uma folha, se o `TelaDosBaixados` recusa caixa modal
///
/// Aquela recusa é sobre **confirmação**: «uma caixa modal pra confirmar o
/// apagamento de um arquivo é cerimônia que a web não faz». Esta não confirma
/// nada — ela **oferece uma escolha** que não existe em nenhum outro lugar da
/// tela, e escolha precisa de superfície. A folha é a forma do Android pra isso, e
/// ela sai com o gesto de arrastar pra baixo, sem botão de cancelar.
///
/// ⚠️ E o rótulo pode não existir: o 007 em inglês deste acervo não declara idioma
/// na faixa de áudio, então sai como «versão 2» — a queda posicional do
/// `rotuloDaFaixa`. Quem distingue as duas ali é o «parou em».
@OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
@Composable
private fun EscolhaDeVersao(
    item: ItemDaBiblioteca,
    aoFechar: () -> Unit,
    aoEscolher: (VersaoDaObra) -> Unit,
) {
    val versoes = item.versoesEscolhiveis
    androidx.compose.material3.ModalBottomSheet(
        onDismissRequest = aoFechar,
        containerColor = Cores.fundoElevado,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = Cores.texto,
            )
            Text(
                text = "${versoes.size} versões no acervo",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
            )
            Column(
                Modifier.padding(top = 18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                versoes.forEachIndexed { indice, versao ->
                    LinhaDeVersao(versao = versao, posicao = indice) { aoEscolher(versao) }
                }
            }
        }
    }
}

/// Uma linha da folha: o nome, o que se sabe do arquivo, e onde se parou.
@Composable
private fun LinhaDeVersao(
    versao: VersaoDaObra,
    posicao: Int,
    aoTocar: () -> Unit,
) {
    /// ⚠️ Queda **posicional** quando o arquivo não declara idioma — a mesma do
    /// `rotuloDaFaixa` («faixa 1»). Escrever «Inglês» num arquivo que não diz que
    /// é inglês seria inventar metadado.
    val nome = idiomasEmPortugues(versao.idiomasDeAudio) ?: "versão ${posicao + 1}"

    /// `818p · 2,3 GB` — item por item; a linha some inteira se não houver nenhum.
    val tecnico = buildList {
        versao.height?.let { add("${it}p") }
        versao.tamanhoEmBytes?.let { add(tamanhoCompacto(it)) }
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")

    /// ⚠️ Filme terminado **não** tem «parou em»: a posição de quem viu até o fim
    /// é o fim, e escrevê-la é a mesma mentira que o `ondeContinuar` conserta na
    /// ficha. O piso de 5s é o dele — abaixo disso foi toque acidental.
    val parou = versao.ondeParou
        ?.takeIf { it > 5 && !versao.finished }
        ?.let { "parou em ${duracaoCompacta(it)}" }

    Row(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(11.dp))
            .background(Cores.fundoAfundado)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = nome,
                style = MaterialTheme.typography.bodyLarge,
                color = Cores.texto,
            )
            if (parou != null) {
                Text(
                    text = parou,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.textoApagado,
                )
            }
        }
        if (tecnico != null) {
            Text(
                text = tecnico,
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
            )
        }
    }
}

/// A barra fina que substitui o cabeçalho depois da primeira rolada.
///
/// ## O que ela mantém, e por quê
///
/// | | |
/// |---|---|
/// | **a busca** | inteira, e foi pedido do dono: buscar sem voltar ao topo |
/// | **`filtros`** | com a pílula do número — filtrar é o gesto de quem está folheando |
/// | **`⤓ N`** | só o ícone e o número: «no aparelho» por extenso deixaria a busca com menos da metade da largura |
///
/// O que **não** sobe é o título e a contagem: os dois são contexto de chegada.
/// Quem já rolou sabe que está na biblioteca, e `60 de 8.316` só muda quando se
/// filtra — momento em que o painel se abre e a contagem volta a ser lida no topo.
///
/// ## O fundo é sólido, e não translúcido
///
/// Ela flutua sobre cartazes que passam por baixo. Um fundo com alfa deixaria
/// pôster aparecendo atrás do texto do campo — o mesmo defeito que o menu de
/// disco cobrou («Tocar disputando espaço com uma camiseta vermelha»). A borda de
/// baixo é o que separa a barra da grade sem ela precisar de sombra.
@Composable
private fun BarraCondensada(
    busca: String,
    aoBuscar: (String) -> Unit,
    filtrosLigados: Int,
    aoAbrirFiltros: () -> Unit,
    quantosBaixados: Int,
    aoAbrirBaixados: () -> Unit,
    /// Ver `CampoDeBusca.ondeProcura`.
    ondeProcura: String = "na biblioteca",
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Cores.fundo)
            /// ⚠️ **68dp reservados à direita, e não 16 — a insígnia mora ali.**
            ///
            /// A `GavetaDoEu` é desenhada pelo `AppOdeon` no canto de cima à
            /// direita, **por cima de toda tela**, e a primeira versão desta
            /// barra ignorou isso: a foto mostrou a pastilha `⤓ 1` desaparecendo
            /// atrás do rosto.
            ///
            /// É a pendência que o `PARIDADE` já tinha anotado por outro
            /// sintoma — «a insígnia do canto rouba o toque nos 48dp do canto
            /// superior direito». Aqui ela cobriu o desenho, não só o toque, e o
            /// conserto é o mesmo dos dois lados: **quem desenha no topo recua**.
            ///
            /// 68 = os 48 da insígnia + os 12 de respiro dela + 8 de folga.
            .padding(start = 16.dp, end = 68.dp, top = 8.dp, bottom = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            CampoDeBusca(valor = busca, aoMudar = aoBuscar, compacto = true, ondeProcura = ondeProcura)
        }

        Text(
            text = if (filtrosLigados > 0) "filtros $filtrosLigados" else "filtros ▾",
            style = Tipo.pilula,
            color = if (filtrosLigados > 0) Cores.destaque else Cores.texto,
            maxLines = 1,
            modifier = Modifier
                .clip(RoundedCornerShape(percent = 50))
                .background(Cores.fundoElevado)
                .clickable(onClick = aoAbrirFiltros)
                .padding(horizontal = 12.dp, vertical = 7.dp),
        )

        /// A mesma pastilha acesa da fileira de baixo, encolhida ao ícone e ao
        /// número — e com a mesma regra: sem download, ela não nasce (§24).
        if (quantosBaixados > 0) {
            Text(
                text = "↓ $quantosBaixados",
                style = Tipo.pilula,
                color = Cores.fundo,
                maxLines = 1,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Cores.destaque)
                    .clickable(onClick = aoAbrirBaixados)
                    .padding(horizontal = 11.dp, vertical = 7.dp),
            )
        }
    }
}

/// O cartão de um episódio — e ele é **16:9**, não 2:3.
///
/// ## O `still` é a razão de este cartão existir
///
/// O comentário da web é a especificação: «usa o `still` 16:9 do episódio, não o
/// pôster da série — com o pôster, 21 episódios eram 21 cópias da mesma
/// imagem». Uma grade de capas idênticas não é uma lista de episódios, é um
/// papel de parede.
///
/// Sem `still`, cai no backdrop e depois no pôster — e sem nenhum dos três,
/// mostra o código sobre a cor da obra, que é o que o cartaz da grade já faz
/// pelas 8.598 obras sem arte.
@Composable
private fun CartaoDeEpisodio(item: ObraDaLista, arte: String?, aoTocar: () -> Unit) {
    val cor = corDeHex(item.corDominante)

    Column(
        modifier = Modifier.clickable(onClick = aoTocar),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(cor?.copy(alpha = 0.35f) ?: Cores.fundoElevado),
        ) {
            if (arte != null) {
                AsyncImage(
                    model = arte,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }

            /// O código no canto, sobre uma lavagem — sem ela, um `S02E05`
            /// branco sobre um quadro claro some, e é justamente no quadro que
            /// ele precisa ser lido.
            item.codigo?.let { codigo ->
                Text(
                    text = codigo,
                    style = Tipo.pilula,
                    color = Cores.texto,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(4.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Cores.fundo.copy(alpha = 0.72f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }

            /// A barra de progresso, igual à do cartaz. Terminado **não** desenha
            /// barra cheia: quem terminou não está no meio de nada.
            val fracao = item.ondeParou
                ?.takeIf { it > 30 && item.finished != true }
                ?.let { onde ->
                    item.duracaoEmSegundos?.takeIf { it > 0 }?.let { (onde / it).toFloat() }
                }
                ?.coerceIn(0f, 1f)

            if (fracao != null) {
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Cores.fundo.copy(alpha = 0.6f)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fracao)
                            .height(3.dp)
                            .background(Cores.destaque),
                    )
                }
            }
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            /// Visto fica apagado — a marca é a mesma da capa da revista do
            /// guia: o que já se viu recua, em vez de ganhar um selo a mais.
            color = if (item.finished == true) Cores.textoApagado else Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/// Os episódios agrupados por temporada, na ordem em que se assiste.
///
/// ⚠️ **`Sem temporada` vai pro fim**, e não pro começo. Ele é onde caem os
/// episódios que a identificação não numerou — e pôr o não identificado antes da
/// primeira temporada faria a série parecer começar por um monte de arquivo
/// solto.
internal fun porTemporada(episodios: List<ObraDaLista>): List<Pair<String, List<ObraDaLista>>> =
    episodios
        .groupBy { it.temporada }
        .toList()
        .sortedWith(compareBy(nullsLast<Int>()) { it.first })
        .map { (temporada, doGrupo) ->
            val rotulo = when (temporada) {
                null -> "sem temporada"
                0 -> "especiais"
                else -> "temporada $temporada"
            }
            rotulo to doGrupo.sortedWith(compareBy(nullsLast<Int>()) { it.episodio })
        }

@Composable
private fun Cabecalho(
    quantos: Int,
    total: Int?,
    busca: String,
    aoBuscar: (String) -> Unit,
    serie: SerieAberta?,
    aoSairDaSerie: () -> Unit,
    /// Ver `CampoDeBusca.ondeProcura`.
    ondeProcura: String = "na biblioteca",
    /// ⚠️ **«filmes», e não «biblioteca»** — 18/08/2026. As séries viraram aba
    /// própria, e um título que diz «biblioteca» numa tela que não tem série
    /// promete as duas coisas. Ver `TelaDasSeries`.
    titulo: String = "biblioteca",
) {
    /// Sem padding lateral próprio: dentro da grade quem alinha é o
    /// `contentPadding` de 16.dp dela, e somar os dois afastaria o título dos
    /// cartazes que ele encabeça. O espaço até a primeira fileira também já vem
    /// de graça, do `verticalArrangement`.
    Column {
        /// ## O título e a contagem dividem a linha — 05/08/2026
        ///
        /// Eram duas linhas, e a de baixo era `60 de 8316` em cinza de 12sp: o
        /// mesmo peso de um rodapé. Hoje a porta da locadora e o cabeçalho dos
        /// baixados deram **serifa dourada** a esta classe de número, e a
        /// biblioteca — que é a tela que mais conta acervo — era a única onde ele
        /// era nota de pé de página.
        ///
        /// Juntá-los numa linha não é só economia de 42px: `biblioteca 60 de
        /// 8.316` é **uma frase**, e era isso que as duas linhas estavam
        /// separando.
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                /// Dentro da série, o título **é a série**. Manter «biblioteca»
                /// com os episódios de *Breaking Bad* embaixo faria a tela mentir
                /// sobre onde se está — e o chip logo abaixo é o caminho de volta.
                text = serie?.titulo ?: titulo,
                style = MaterialTheme.typography.headlineSmall,
                color = Cores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                /// ⚠️ `weight(1f, fill = false)` e não `weight(1f)`: com um título
                /// de série curto, o `fill` empurraria a contagem pro fim da
                /// linha, longe da palavra que ela conta.
                modifier = Modifier.weight(1f, fill = false),
            )
            /// A contagem só aparece quando existe.
            ///
            /// §24: linha vazia **some**, não vira "—". E enquanto o total é
            /// nulo, escrever "0 de 0" seria afirmar que o acervo está vazio.
            if (total != null) {
                val numero = MaterialTheme.typography.headlineSmall.copy(fontSize = 17.sp)
                Text("$quantos", style = numero, color = Cores.destaque)
                Text(
                    text = "de",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.textoApagado,
                    modifier = Modifier.padding(bottom = 2.dp),
                )
                /// ⚠️ **Com ponto de milhar**, que a versão anterior não tinha:
                /// `8316` obriga a contar os algarismos pra saber a ordem de
                /// grandeza. É a mesma razão que fez a temporada `1 3` virar `13`
                /// numa rodada anterior — número é quantidade, e quantidade se lê
                /// de relance ou não se lê.
                Text(
                    text = "%,d".format(total).replace(',', '.'),
                    style = numero,
                    color = Cores.destaque,
                )
            }
        }

        /// O chip «Dentro de» — o caminho de volta, e o único que existe aqui.
        ///
        /// Ele é da web (§4), e o ✕ é o gesto inteiro: sair da série é desfazer
        /// um filtro, não navegar pra trás. O botão físico de voltar continua
        /// levando à aba anterior, que é outra coisa — e é por isso que este
        /// chip precisa existir mesmo com ele.
        if (serie != null) {
            Text(
                text = "dentro de ${serie.titulo}  ✕",
                style = Tipo.pilula,
                color = Cores.destaque,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Cores.fundoElevado)
                    .clickable(onClick = aoSairDaSerie)
                    .padding(horizontal = 12.dp, vertical = 6.dp),
            )
            return@Column
        }

        /// A busca fica **colada na contagem**, e não depois do atalho.
        ///
        /// As duas respondem a mesma coisa — *o que estou vendo desta grade* —,
        /// e o screenshot mostrou o custo de separá-las: com o `no aparelho ›`
        /// no meio, o link ficava boiando entre dois pedaços do mesmo assunto.
        CampoDeBusca(valor = busca, aoMudar = aoBuscar, ondeProcura = ondeProcura)

        /// ## Os três links saíram daqui
        ///
        /// Eles eram `locadora ›`, `baixados ›` e `para você ›`, e o comentário
        /// que estava neste lugar defendia o arranjo assim: «uma barra de abas
        /// com dois itens gasta altura permanente pra oferecer uma escolha que
        /// quase sempre já está feita».
        ///
        /// O argumento era bom e envelheceu. Ele foi escrito quando havia dois
        /// destinos; a v1 terminou com quatro, que é exatamente a faixa em que o
        /// Material põe barra de navegação. E o defeito de verdade não era a
        /// altura: era que os três só existiam **de dentro desta tela** — ir dos
        /// baixados pra locadora passava pela biblioteca no meio.
        ///
        /// Agora eles estão no `EsqueletoComAbas` do `AppOdeon`, que vira trilho
        /// lateral em paisagem e em tablet — o que responde à objeção de altura
        /// justamente onde ela doía.
        ///
        /// ## E um deles voltou pra cá: **baixados**
        ///
        /// Com mural e guia entrando seriam seis abas, e a seis cada uma fica
        /// com 68,5dp — «biblioteca» ocupa 61dp a 12sp, ou seja não cabe com o
        /// respiro. Medido em 04/08/2026.
        ///
        /// O corte foi em baixados, e não é só aritmética: ele nunca foi um
        /// **lugar**, é um **estado** do acervo — «o que está no aparelho». E é
        /// aqui que alguém procura um filme, baixado ou não.
        ///
        /// Fica como atalho no cabeçalho porque a tela continua existindo e é
        /// boa: ela mostra o que baixou, o quanto falta e o que venceu. O que
        /// mudou é que ela deixou de disputar um dos cinco lugares permanentes
        /// com o mural e o guia, que são lugares de verdade.
        ///
        /// ## ⚠️ E o atalho saiu daqui — 05/08/2026
        ///
        /// Era um `TextButton` com «no aparelho ›» numa linha só dele, e a
        /// queixa do dono foi exata: «tão simples e escondido que ninguém vai
        /// ver». Ele é a **única porta** pra tela de baixados, e parecia uma
        /// legenda.
        ///
        /// Faltava-lhe o que todo o resto do app tem: **um número**. «no
        /// aparelho ›» é uma palavra; «⤓ 1 no aparelho» é um lugar com coisa
        /// dentro.
        ///
        /// Ele foi pra fileira dos filtros, e o motivo não é onde sobrava
        /// espaço — **`no aparelho` é um filtro do acervo**. «Me mostre o que
        /// está aqui» é o mesmo gesto que `filtros ▾` e `em destaque ▾`:
        /// estreitar 8.316 entradas. Ele estava fora da fileira onde mora o seu
        /// próprio tipo de ação, e é o que este comentário previa três linhas
        /// acima («o próximo passo óbvio é ele virar filtro») — meio caminho
        /// andado: ele ainda abre tela em vez de filtrar a grade.
        ///
        /// O ganho é duplo: mais visível **e** uma linha a menos, porque ele
        /// entra numa fileira que já existia. Ver `BarraDeFiltros`.
    }
}

/// O campo de busca.
///
/// ## Ele rola junto, e é a mesma decisão medida do cabeçalho
///
/// A web põe a busca na barra de cima, que é fixa e condensa. Aqui ela é a
/// última linha do cabeçalho — que é **item da grade** desde que a versão fixa
/// custou 180px de 1080 em paisagem, uma fileira e meia de cartaz.
///
/// O que se perde é a busca sair de vista depois da primeira rolada. É o mesmo
/// preço que a contagem paga, e pelo mesmo motivo: quem já rolou está olhando o
/// acervo, e quem vai buscar volta ao topo — que é um gesto só, e o gesto que
/// todo mundo já faz.
///
/// ## `BasicTextField`, e não o `TextField` do Material
///
/// Pelo mesmo motivo que o `NavigationSuiteScaffold` saiu: o campo do Material
/// traz um esquema de cores inteiro atrás — fundo do contêiner, indicador,
/// cursor, rótulo flutuante —, e cada um deles que não estiver definido no
/// `EsquemaEscuro` cai no padrão de fábrica. Foi assim que a cápsula da barra
/// virou lilás. Aqui são quatro cores da casa e nada mais.
@Composable
private fun CampoDeBusca(
    valor: String,
    aoMudar: (String) -> Unit,
    compacto: Boolean = false,
    /// ## ⚠️ O texto diz **onde** se procura — 18/08/2026
    ///
    /// A busca sempre respeitou a prateleira (os dois viajam no mesmo pedido);
    /// o que faltava era **dizer isso**. Numa biblioteca de 8.333 entradas, quem
    /// digita `arcane` e recebe um resultado precisa saber se procurou em tudo
    /// ou só nas séries — foi o que me confundiu ao medir o acervo hoje.
    ondeProcura: String = "na biblioteca",
) {
    val foco = LocalFocusManager.current

    BasicTextField(
        value = valor,
        onValueChange = aoMudar,
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyMedium.copy(color = Cores.texto),
        cursorBrush = SolidColor(Cores.destaque),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        /// Buscar já aconteceu — o debounce cuidou disso enquanto se digitava.
        /// O que a tecla faz é **guardar o teclado**, que é o que sobrou pra ela
        /// fazer e o que a pessoa quer nesse momento: ver a grade.
        keyboardActions = KeyboardActions(onSearch = { foco.clearFocus() }),
        modifier = Modifier.fillMaxWidth().padding(top = if (compacto) 0.dp else 4.dp),
        decorationBox = { campo ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Cores.fundoElevado)
                    /// ⚠️ **Dois respiros, e o compacto é o da barra que
                    /// condensa.** Não é gosto: com 10dp em cima e embaixo a
                    /// barra fixa passa de 52dp, e aí ela come mais tela do que
                    /// o cabeçalho inteiro devolveu ao rolar.
                    .padding(horizontal = 14.dp, vertical = if (compacto) 7.dp else 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f)) {
                    /// A frase é a da web, letra por letra. Duas telas do mesmo
                    /// produto pedindo a mesma coisa com palavras diferentes é
                    /// como um produto parece dois.
                    if (valor.isEmpty()) {
                        Text(
                            text = "buscar $ondeProcura…",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Cores.textoApagado,
                        )
                    }
                    campo()
                }

                /// O ✕ só nasce quando há o que limpar (§24), e limpar devolve o
                /// foco ao campo — quem apaga a busca quase sempre vai digitar
                /// outra, não fechar o teclado.
                if (valor.isNotEmpty()) {
                    Text(
                        text = "✕",
                        style = Tipo.pilula,
                        color = Cores.textoApagado,
                        modifier = Modifier
                            .clickable { aoMudar("") }
                            .padding(start = 8.dp),
                    )
                }
            }
        },
    )
}

/// O herói da biblioteca: o que você deixou pela metade, em tamanho de cartaz.
///
/// ## A cor da obra tinge a tela — e é a tese da §4b sendo cobrada
///
/// «A interface se tinge com a obra que você está olhando», diz a espec, e
/// **9.332 obras já têm `dominant_color` extraída** pelo servidor. Até agora o
/// app usava esse dado pra uma coisa só: preencher o fundo de cartão sem pôster.
/// Ou seja, a cor da obra só aparecia quando **não havia** obra pra ver.
///
/// Aqui ela entra onde faz sentido: na lavagem por baixo do texto. A tela de
/// chegada muda de temperatura conforme o filme que está em cima dela.
///
/// Nulo é normal — 8.598 obras não têm arte, logo não têm cor extraída dela — e
/// aí a lavagem cai no dourado da casa. Nunca numa cor sorteada.
@Composable
private fun HeroiDaChegada(item: ItemPraContinuar, arte: String?, aoTocar: () -> Unit) {
    val corDaObra = corDeHex(item.corDominante) ?: Cores.destaque

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(16f / 9f)
            .clip(RoundedCornerShape(8.dp))
            .background(Cores.fundoElevado)
            .clickable(onClick = aoTocar),
    ) {
        if (arte != null) {
            AsyncImage(
                model = arte,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                /// `brightness(0.32)` da `.hero-art` da web, o mesmo do herói do
                /// "para você". Sobre a arte crua o texto some em metade dos
                /// pôsteres — e o que está escrito aqui é o quanto falta, que é
                /// a razão desta faixa existir.
                colorFilter = ColorFilter.colorMatrix(
                    ColorMatrix(
                        floatArrayOf(
                            0.32f, 0f, 0f, 0f, 0f,
                            0f, 0.32f, 0f, 0f, 0f,
                            0f, 0f, 0.32f, 0f, 0f,
                            0f, 0f, 0f, 1f, 0f,
                        ),
                    ),
                ),
            )
        }

        /// Duas camadas: a **cor da obra** vindo do canto de baixo, e o fundo da
        /// casa subindo da base pra dar chão ao texto. Nessa ordem — a cor tinge
        /// a arte, o fundo sustenta a letra.
        /// ⚠️ **42%, e o screenshot é que definiu o número.**
        ///
        /// A 30% a faixa saía **cinza**: `brightness(0.32)` sobre um pôster de
        /// neve branca tira a cor junto com a luz, e uma lavagem fraca não
        /// repõe. A web usa `42%` na `.hero-wash` (`styles.css:2086`) e é
        /// exatamente por isso — o número dela não era estético, era corretivo.
        ///
        /// O centro fica **fora** da faixa (`y = 1.1`), então o que se vê é o
        /// topo do halo subindo da base. Centrado, ele viraria uma bola de luz
        /// no meio da arte.
        Box(
            Modifier.fillMaxSize().background(
                Brush.radialGradient(
                    colors = listOf(corDaObra.copy(alpha = 0.42f), Color.Transparent),
                    center = androidx.compose.ui.geometry.Offset(0.10f, 1.1f),
                    radius = 1.15f,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    0.45f to Cores.fundo.copy(alpha = 0.5f),
                    1f to Cores.fundo.copy(alpha = 0.94f),
                ),
            ),
        )

        /// As lâmpadas da marquise, o mesmo composable do "para você".
        LampadasDaMarquise(Modifier.align(Alignment.TopCenter))

        Column(
            modifier = Modifier.align(Alignment.BottomStart).padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = item.tituloDaSerie ?: item.title,
                style = MaterialTheme.typography.headlineSmall,
                color = Cores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            segundaLinhaDeContinuar(item)?.let {
                Text(it, style = MaterialTheme.typography.bodySmall, color = Cores.destaque)
            }
        }

        /// A barra do quanto falta, colada na base do herói — a mesma conta e o
        /// mesmo desenho da fileira e da grade, pra não haver três progressos
        /// diferentes na mesma tela.
        item.fracaoVista?.let { fracao ->
            Box(
                Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp)
                    .background(Cores.fundoAfundado),
            ) {
                Box(Modifier.fillMaxWidth(fracao).height(3.dp).background(Luz.filamento))
            }
        }
    }
}

/// A fileira de "continuar de onde parou".
///
/// ## Ela leva à ficha, e não direto ao filme
///
/// A decisão de "toque no cartaz leva à tela de detalhe" já estava tomada, e
/// esta fileira segue a mesma — inclusive porque a ficha é onde se escolhe a
/// versão quando a obra tem mais de um arquivo, e o botão de lá já diz
/// **continuar** com o segundo certo.
///
/// ⚠️ Vale registrar a tensão: o argumento contrário é bom. "Continuar" é
/// literalmente o gesto de voltar pro filme, e um toque a mais aqui custa
/// justamente na tela onde a pressa é maior. Se o dono preferir ir direto ao
/// player, é trocar o `aoTocar` — uma linha.
@Composable
private fun FileiraParaContinuar(
    itens: List<ItemPraContinuar>,
    arte: (ItemPraContinuar) -> String?,
    aoTocar: (ItemPraContinuar) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        /// O número é `itens.size`, e ele é honesto: a fileira mostra tudo que
        /// tem, sem paginação. Não é o caso da grade, cujo "60 de 17.498" é
        /// outra conversa e continua no cabeçalho.
        RotuloDeSecao(texto = "continuar", numero = itens.size)

        LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(itens, key = { it.id }) { item ->
                CartaoDeContinuar(item = item, arte = arte(item), aoTocar = { aoTocar(item) })
            }
        }
    }
}

/// Um cartão da fileira — largo, e não 2:3.
///
/// A grade usa proporção de cartaz porque lá o que identifica é a capa. Aqui o
/// que identifica é **o quadro onde parou**, e quadro de filme é largo. 16:9
/// também é o que `still` e `backdrop` são; forçá-los em 2:3 cortaria metade.
@Composable
private fun CartaoDeContinuar(item: ItemPraContinuar, arte: String?, aoTocar: () -> Unit) {
    val fundo = corDaObra(item.corDominante) ?: Cores.fundoElevado

    Column(
        modifier = Modifier.width(200.dp).clickable(onClick = aoTocar),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(16f / 9f)
                .clip(RoundedCornerShape(6.dp))
                .background(fundo),
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
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    /// A mesma escolha por contraste da grade — a cor da obra
                    /// pode ser clara, e texto claro sobre ela some.
                    color = corDoTitulo(fundo),
                    textAlign = TextAlign.Center,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }

            /// A barrinha do quanto já passou, colada na base do quadro.
            ///
            /// Ela é o que diferencia esta fileira de qualquer outra lista de
            /// filmes: sem ela, "continuar" é só uma seleção sem explicação.
            /// Some quando não dá pra calcular, em vez de aparecer zerada.
            item.fracaoVista?.let { fracao ->
                /// 4dp e trilho **opaco**, e os dois números têm motivo.
                ///
                /// A primeira versão era 3dp com o trilho a 60% de alfa. Ela
                /// desenhava certo — medido, 6% de preenchimento pra um "faltam
                /// 133min" de 142min — e mesmo assim quase não se via: o amarelo
                /// do destaque contra um pôster claro dá cerca de 1,9:1, e o
                /// trilho transparente deixava a imagem atravessar.
                ///
                /// O trilho escuro é o que emoldura a barra contra qualquer arte,
                /// clara ou escura. É o mesmo problema do título sobre a cor da
                /// obra, resolvido do mesmo jeito: não confiar que o fundo
                /// colabore.
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Cores.fundoAfundado),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fracao)
                            .height(4.dp)
                            /// Acesa em vez de chapada: o filamento vai do frio
                            /// à ponta quente. Uma barra chapada é uma medida;
                            /// uma que esquenta na ponta é uma coisa **acesa até
                            /// ali**, que é o que progresso é.
                            .background(Luz.filamento),
                    )
                }
            }
        }

        Text(
            /// Numa série, o que identifica é o nome dela — o título do episódio
            /// sozinho ("Piloto") não diz de que série é.
            text = item.tituloDaSerie ?: item.title,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.texto,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )

        segundaLinhaDeContinuar(item)?.let {
            Text(it, style = MaterialTheme.typography.labelSmall, color = Cores.textoApagado)
        }
    }
}

/// A segunda linha do cartão: o episódio, ou o quanto falta.
///
/// Nunca as duas — o cartão tem 200dp e a linha é uma. E nunca um traço quando
/// não há nenhuma (§24).
private fun segundaLinhaDeContinuar(item: ItemPraContinuar): String? {
    val episodio = if (item.temporada != null && item.episodio != null) {
        "T%d E%d".format(item.temporada, item.episodio)
    } else {
        null
    }

    val falta = item.ondeParou?.let { onde ->
        item.duracaoEmSegundos?.takeIf { it > onde }?.let { total ->
            val minutos = ((total - onde) / 60).toInt()
            if (minutos > 0) "faltam ${minutos}min" else null
        }
    }

    return listOfNotNull(episodio, falta).joinToString(" · ").takeIf { it.isNotBlank() }
}

/// Um cartão da grade.
///
/// ## Quando não há pôster, ele não finge que há
///
/// **8.598 obras de 17.930 não têm pôster** — 48% do acervo, medido no banco em
/// 04/08/2026. Ou seja, capa faltando não é a exceção: é quase metade da grade.
/// Um retângulo cinza com ícone de imagem quebrada diria "falhou ao carregar",
/// que é mentira — não há o que carregar.
///
/// O que aparece no lugar é o título, sobre a cor da obra quando o servidor a
/// extraiu. É o §18: quando o dado não existe, a tela mostra o que existe.
@Composable
private fun Cartaz(
    item: ItemDaBiblioteca,
    capa: String?,
    aoTocar: () -> Unit,
    moldura: MolduraDoCartaz = MolduraDoCartaz.Nenhuma,
) {
    val fundoDoCartaz = corDaObra(item.corDominante) ?: Cores.fundoElevado

    /// O clicável é a **coluna inteira**, não só o pôster.
    ///
    /// O título e o ano ficam abaixo da imagem e são parte do mesmo cartão aos
    /// olhos de quem toca. Um alvo que cobre só a arte transforma o toque no
    /// texto num toque que não faz nada — §8b, na versão em que a pessoa acha
    /// que o app travou.
    /// O afundar ao toque — R4.
    ///
    /// ## Ele responde uma pergunta, que é a regra 5 do redesenho
    ///
    /// A pergunta é *o dedo pegou este?*. Numa grade de cartazes colados, com
    /// 12dp entre um e outro, o toque acerta o vizinho mais do que parece — e
    /// hoje o único retorno era a tela **inteira** trocar meio segundo depois.
    ///
    /// 0,96 e não 0,90: o cartaz encolhe o suficiente pra separar do vizinho e
    /// não o bastante pra parecer que saiu do lugar. `spring` e não `tween`
    /// porque soltar antes de o toque virar navegação tem que voltar sem
    /// esperar duração nenhuma.
    ///
    /// ⚠️ **Isto é multiplicado por tudo que está na tela**, e a grade tem 8.316
    /// entradas. O `animateFloatAsState` só anima o cartaz **pressionado** —
    /// os outros ficam em 1f e não recompõem, porque `graphicsLayer` com lambda
    /// muda a camada sem passar pela fase de composição.
    val interacoes = remember { MutableInteractionSource() }
    val pressionado by interacoes.collectIsPressedAsState()
    val escala by animateFloatAsState(
        targetValue = if (pressionado) 0.96f else 1f,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "afundar do cartaz",
    )

    Column(
        modifier = Modifier
            /// A camada só existe **enquanto o cartaz está encolhido**.
            ///
            /// Com `escala == 1f` — o estado de todo cartaz durante uma rolagem,
            /// porque ninguém está com o dedo em nenhum — o `then` devolve
            /// `Modifier` vazio e **nenhuma camada de composição é criada**. Só o
            /// cartaz pressionado ganha uma, por ~300ms.
            ///
            /// Isto é construção, não medida: um `graphicsLayer` incondicional
            /// alocaria uma camada por cartaz visível o tempo todo, e esta forma
            /// não aloca nenhuma quando ninguém está tocando.
            ///
            /// ## ⚠️ E o número que a R4 pede **não foi obtido**
            ///
            /// A régua da R4 é «se a rolagem sair de 60fps no emulador, o enfeite
            /// sai». Medido com `dumpsys gfxinfo` em 04/08/2026, seis arrastos
            /// iguais sobre conteúdo já carregado, com a tela conferida no fim
            /// pra garantir que a rolagem aconteceu mesmo:
            ///
            /// | | quadros | perdidos | 90º percentil |
            /// |---|---|---|---|
            /// | camada só ao toque | 151 | 43,0% | 81ms |
            /// | sem camada nenhuma | 87 | 66,7% | 85ms |
            ///
            /// A versão **sem** o enfeite saiu pior, o que não pode ser verdade.
            /// E 87 contra 151 quadros pro mesmo gesto diz o que está
            /// acontecendo: **a variância entre execuções é maior que a diferença
            /// entre as versões.** O emulador não segura 60fps nesta grade nem
            /// com nem sem enfeite — mediana de 32ms e 36ms, ou seja ~30fps nos
            /// dois casos.
            ///
            /// Ou seja: **a régua da R4 não é aplicável neste ambiente.** Ela
            /// precisa de aparelho de verdade, ou de `androidx.benchmark`, que
            /// roda a mesma rolagem N vezes e devolve intervalo de confiança em
            /// vez de uma amostra.
            ///
            /// Uma medição anterior chegou a dizer «67,7% contra 6,1%», e estava
            /// contaminada: um dos arrastos virou toque e abriu a ficha, então
            /// metade da amostra foi tirada de uma tela parada. Fica registrado
            /// porque um número errado num comentário sobrevive mais que um
            /// número errado numa conversa.
            .then(
                if (escala == 1f) {
                    Modifier
                } else {
                    Modifier.graphicsLayer { scaleX = escala; scaleY = escala }
                },
            )
            .clickable(
                interactionSource = interacoes,
                /// Sem ondulação: ela desenharia um círculo claro por cima da
                /// arte do pôster, e o que se quer é o objeto se mexer — não uma
                /// tinta em cima dele.
                indication = null,
                onClick = aoTocar,
            ),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                /// 2:3 é a proporção de cartaz de cinema, e é a que o servidor
                /// baixa. Qualquer outra recortaria o rosto de alguém.
                .aspectRatio(2f / 3f)
                /// A moldura entra **antes** do `clip`: é o pôster que viaja pra
                /// ficha, e ele tem que levar o próprio recorte junto. Depois do
                /// clip, o elemento compartilhado seria o retângulo sem cantos.
                .then(moldura.de(item.id))
                /// O cartaz pega luz — leva 1 do segundo redesenho.
                ///
                /// Sobre `#0A0A0C` um cartaz sem contorno **flutua**: não há
                /// aresta, e a arte lê como adesivo colado no preto. 1dp de
                /// dourado a 22% dá a borda, e borda é o que separa "imagem" de
                /// "coisa". Sem sombra aqui — ver `Luz.pegaLuz`, que explica por
                /// que a parte cara ficou de fora da grade de 8.316.
                .pegaLuz(RoundedCornerShape(6.dp))
                .clip(RoundedCornerShape(6.dp))
                .background(fundoDoCartaz),
            contentAlignment = Alignment.Center,
        ) {
            if (capa != null) {
                AsyncImage(
                    model = capa,
                    /// Nulo de propósito: o título está escrito logo abaixo, em
                    /// texto de verdade. Repeti-lo aqui faria o leitor de tela
                    /// dizer o nome do filme duas vezes seguidas.
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = item.title,
                    style = MaterialTheme.typography.labelMedium,
                    /// **Não** `Cores.texto` fixo — ver `corDoTitulo` abaixo.
                    color = corDoTitulo(fundoDoCartaz),
                    textAlign = TextAlign.Center,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(8.dp),
                )
            }

            /// A barra do quanto já passou — **dentro** do pôster, na base.
            ///
            /// ## Ela já existia na fileira de "continuar", e faltava aqui
            ///
            /// É a R4, e o desenho é o mesmo da `FileiraParaContinuar` de
            /// propósito: dois retângulos, 4dp, trilho opaco. O comentário de lá
            /// carrega a medida que decidiu os dois números — o amarelo do
            /// destaque contra um pôster claro dá ~1,9:1, e um trilho
            /// transparente deixava a arte atravessar a barra.
            ///
            /// ⚠️ **Só aparece com progresso de verdade.** Não é `0f` quando
            /// nunca se assistiu: é nada. Uma barra zerada em 8.316 cartazes
            /// diria que o acervo inteiro foi começado, que é o §18 — e uma
            /// barra que não é progresso não pode parecer barra de progresso.
            fracaoVista(item)?.let { fracao ->
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(4.dp)
                        .background(Cores.fundoAfundado),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(fracao)
                            .height(4.dp)
                            /// Acesa em vez de chapada: o filamento vai do frio
                            /// à ponta quente. Uma barra chapada é uma medida;
                            /// uma que esquenta na ponta é uma coisa **acesa até
                            /// ali**, que é o que progresso é.
                            .background(Luz.filamento),
                    )
                }
            }
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        /// A linha de metadados — R4: `1969 · 816p · 2h22 · 2,3 GB`.
        ///
        /// Substituiu a "segunda linha", que dizia **ou** o ano **ou** quantos
        /// episódios. O motivo de ela ter sido uma coisa só era o dado: a fase 1
        /// mapeou `year` e `work_count` e mais nada. `height` e `size_bytes`
        /// sempre vieram na mesma resposta e eram descartados — ver `Modelos`.
        ///
        /// ⚠️ **Ela monta item por item e omite o que falta** (§18/§24). Não há
        /// "—", não há "desconhecido", e a linha inteira some quando nada existe.
        /// Isso não é caso raro: 8.598 das 17.930 entradas não têm arquivo
        /// casado, então quase metade da grade mostra só o ano.
        linhaDeMetadados(item)?.let { texto ->
            Text(
                text = texto,
                style = MaterialTheme.typography.labelSmall,
                color = Cores.textoApagado,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/// `1969 · 816p · 2h22` — e cada pedaço só entra se existir.
///
/// ## A ordem não é arbitrária
///
/// Ela vai do que identifica pro que é detalhe técnico: o ano diz *qual* filme
/// (é o que separa as duas versões de "Cassino Royale"), e a resolução e a
/// duração dizem *que cópia é esta*. Quem lê de relance lê os dois primeiros.
///
/// ## ⚠️ O tamanho saiu, e foi o screenshot que mandou
///
/// A R4 pede `1969 · 816p · 2h22 · 2,3 GB`, e foi o que a primeira versão
/// escreveu. No aparelho ela apareceu assim:
///
/// ```
/// 1969 · 816p · 2h22 · …
/// ```
///
/// O cartaz tem 108dp de largura mínima, e os quatro campos não cabem em
/// `labelSmall`. A reticência é o defeito: ela **promete** um dado a mais, e
/// não há gesto nenhum nesta tela que o mostre.
///
/// Cortar o tamanho e não outro é o que a própria ordem acima já dizia — ele é
/// o único dos quatro que não ajuda a escolher o que assistir. Ele importa
/// antes de **baixar**, e baixar acontece na ficha, que é onde ele está.
///
/// ## A série troca a resolução por quantos episódios
///
/// Porque uma série não tem uma resolução: tem uma por arquivo, e a entrada da
/// grade é a série inteira. Dizer "816p" ali seria afirmar sobre 9 episódios o
/// que foi medido num — §18.
private fun linhaDeMetadados(item: ItemDaBiblioteca): String? = listOfNotNull(
    item.year?.toString(),
    if (item.eSerie) {
        item.quantasObras.takeIf { it > 0 }?.let { "$it episódios" }
    } else {
        item.height?.let { "${it}p" }
    },
    item.duracaoEmSegundos?.takeIf { it > 0 }?.let { duracaoCurta(it) },
).joinToString(" · ").takeIf { it.isNotBlank() }

/// `2h22`, ou `48min` quando não chega a uma hora.
///
/// É a mesma conta do `duracao()` da ficha. Não foi extraída pra um lugar só
/// porque são quatro linhas e as duas telas podem querer formatos diferentes —
/// se uma terceira precisar, aí vale.
private fun duracaoCurta(segundos: Double): String {
    val total = segundos.toLong()
    val h = total / 3600
    val m = (total % 3600) / 60
    return if (h > 0) "${h}h${m.toString().padStart(2, '0')}" else "${m}min"
}

/// O quanto já se assistiu, entre 0 e 1 — ou `null` quando não dá pra dizer.
///
/// ## Os três nulos são casos diferentes, e todos viram "não desenha"
///
/// | | por quê |
/// |---|---|
/// | sem `position_seconds` | nunca foi começado |
/// | sem `duration_seconds` | não há de que fração tirar |
/// | fração < 1% | começou e desistiu nos primeiros segundos |
///
/// O último corte é o que evita uma barra de um pixel em cartaz que alguém abriu
/// por engano. E `coerceAtMost(1f)` porque `position` pode passar da duração
/// quando o probe mediu um pouco a menos que o arquivo.
private fun fracaoVista(item: ItemDaBiblioteca): Float? {
    val onde = item.ondeParou ?: return null
    val total = item.duracaoEmSegundos?.takeIf { it > 0 } ?: return null
    val fracao = (onde / total).toFloat()
    return fracao.takeIf { it >= 0.01f }?.coerceAtMost(1f)
}

/// A cor que o servidor extraiu do pôster, se extraiu.
///
/// O parser mora em `ui/Cor.kt` desde a R3, porque a ficha passou a precisar da
/// mesma conta pras etiquetas. O nome local fica: aqui a cor é **da obra**, e é
/// isso que o cartaz quer dizer ao se tingir com ela.
private fun corDaObra(hex: String?): Color? = corDeHex(hex)

/// A cor do título do cartão sem pôster, decidida pela cor da obra.
///
/// ## O defeito que isto conserta, e como ele apareceu
///
/// O título ia em `Cores.texto` (`#ECEEF4`, quase branco) **sempre** — o que
/// funciona sobre `fundoElevado` e falha sobre metade do acervo. A cor de trás
/// não é da paleta: é a `dominant_color` que o servidor extraiu do pôster, e
/// pôster claro dá cor clara.
///
/// Só se vê rodando, e foi assim que apareceu: rolando a grade depressa, os
/// cartões aparecem tingidos antes de a imagem chegar. Seis, medidos no
/// emulador em 04/08/2026, contra `#ECEEF4`:
///
/// | cor da obra | antes | depois |
/// |---|---|---|
/// | `#F0F0F0` | **1,02:1** | 17,36:1 |
/// | `#B0D0D0` | **1,42:1** | 12,04:1 |
/// | `#D09070` | **2,29:1** | 7,45:1 |
/// | `#D07090` | **2,83:1** | 6,03:1 |
/// | `#109030` | **3,58:1** | 4,76:1 |
/// | `#1010B0` | 10,51:1 | 10,51:1 (não muda) |
///
/// Cinco das seis reprovavam no piso de 4,5:1 da WCAG AA, e em `#F0F0F0` o
/// título ficava **invisível**. Com a escolha, a pior passa a ser 4,76:1.
///
/// ## Por que comparar, e não cortar num limiar
///
/// O caminho comum é `if (luminancia > 0,5) escuro else claro`. Ele erra perto
/// da linha, onde as duas opções são ruins e o limiar decide sozinho qual. Aqui
/// as duas candidatas são conhecidas — são duas —, então dá pra medir o
/// contraste com cada uma e ficar com a maior. Custa duas contas por cartão e
/// não tem número mágico pra alguém ajustar depois no escuro.
///
/// A escura é `Cores.fundo` e não preto puro: é a mesma tinta do resto do app,
/// e sobre ela o preto absoluto seria a única cor da tela que não pertence à
/// paleta.
private fun corDoTitulo(fundo: Color): Color =
    if (contraste(fundo, Cores.texto) >= contraste(fundo, Cores.fundo)) {
        Cores.texto
    } else {
        Cores.fundo
    }

/// A razão de contraste da WCAG entre duas cores, de 1:1 a 21:1.
///
/// `luminance()` é do próprio Compose e já é a luminância relativa da norma —
/// com a correção de gama dentro, que é a parte que quem escreve à mão esquece.
private fun contraste(a: Color, b: Color): Float {
    val la = a.luminance()
    val lb = b.luminance()
    return (max(la, lb) + 0.05f) / (min(la, lb) + 0.05f)
}


