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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.RotuloDeSecao
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
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val grade = rememberLazyGridState()

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

        if (estado.carregando) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = Cores.destaque)
            }
            return@Column
        }

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
                    quantos = estado.itens.size,
                    total = estado.total,
                )
            }

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
            if (estado.paraContinuar.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    FileiraParaContinuar(
                        itens = estado.paraContinuar,
                        arte = modelo::arte,
                        aoTocar = { aoAbrirObra(it.id) },
                    )
                }
            }

            items(estado.itens, key = { it.id }) { item ->
                Cartaz(
                    item = item,
                    capa = modelo.capa(item),
                    aoTocar = { aoAbrirObra(item.id) },
                )
            }
        }
    }
}

@Composable
private fun Cabecalho(
    quantos: Int,
    total: Int?,
) {
    /// Sem padding lateral próprio: dentro da grade quem alinha é o
    /// `contentPadding` de 16.dp dela, e somar os dois afastaria o título dos
    /// cartazes que ele encabeça. O espaço até a primeira fileira também já vem
    /// de graça, do `verticalArrangement`.
    Column {
        Text(
            text = "biblioteca",
            style = MaterialTheme.typography.headlineSmall,
            color = Cores.texto,
        )
        /// A contagem só aparece quando existe.
        ///
        /// §24: linha vazia **some**, não vira "—". E enquanto o total é nulo,
        /// escrever "0 de 0" seria afirmar que o acervo está vazio.
        if (total != null) {
            Text(
                text = "$quantos de $total",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
            )
        }

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
                            .background(Cores.destaque),
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
private fun Cartaz(item: ItemDaBiblioteca, capa: String?, aoTocar: () -> Unit) {
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
                            .background(Cores.destaque),
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
