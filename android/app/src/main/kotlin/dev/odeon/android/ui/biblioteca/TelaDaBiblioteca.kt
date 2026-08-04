package dev.odeon.android.ui.biblioteca

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    Column(modifier = Modifier.clickable(onClick = aoTocar)) {
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
        }

        Text(
            text = item.title,
            style = MaterialTheme.typography.bodySmall,
            color = Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )

        /// A segunda linha: ou quantos episódios, ou o ano. Nunca as duas, e
        /// nunca um traço quando não há nenhuma.
        segundaLinha(item)?.let { texto ->
            Text(
                text = texto,
                style = MaterialTheme.typography.labelSmall,
                color = Cores.textoApagado,
                maxLines = 1,
            )
        }
    }
}

private fun segundaLinha(item: ItemDaBiblioteca): String? = when {
    item.eSerie && item.quantasObras > 0 -> "${item.quantasObras} episódios"
    item.year != null -> item.year.toString()
    else -> null
}

/// A cor que o servidor extraiu do pôster, se extraiu.
///
/// **9.332 obras já têm** `dominant_color`, e isso não custa requisição nenhuma:
/// vem na mesma linha da listagem. É o que faz a grade parecer o acervo mesmo
/// antes de a primeira imagem chegar.
///
/// Formato `#RRGGBB`. Qualquer coisa fora disso vira `null` em vez de estourar —
/// uma cor inválida não pode derrubar a biblioteca.
private fun corDaObra(hex: String?): Color? {
    val limpo = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    val valor = limpo.toLongOrNull(16) ?: return null
    return Color(valor or 0xFF000000L)
}

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
