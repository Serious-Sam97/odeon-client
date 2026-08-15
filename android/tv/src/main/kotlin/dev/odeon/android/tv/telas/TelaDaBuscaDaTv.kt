package dev.odeon.android.tv.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dev.odeon.android.tv.ui.EscolhaDeVersaoDaSala
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.tv.ui.Cartaz
import dev.odeon.android.tv.ui.Focavel
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.TipoDaSala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.busca.ModeloDaBusca

/// As teclas, em seis colunas.
///
/// ## ⚠️ Por que a ordem é o alfabeto e não QWERTY
///
/// Um teclado de computador é ordenado por **onde os dedos moram**, e num D-pad
/// não há dedos: há um cursor que anda casa a casa. Nessa condição o que a
/// pessoa faz não é digitar, é **procurar a letra** — e procurar num QWERTY
/// significa lembrar de cor onde fica cada uma, enquanto no alfabeto a busca é
/// dedutiva: o `m` está no meio, o `z` no fim.
///
/// Seis colunas porque 26 letras em seis dão quatro fileiras e meia, e a mão
/// atravessa no máximo cinco casas pra chegar em qualquer letra.
private val TECLAS: List<Char> = ('a'..'z').toList() + ('0'..'9').toList()

private const val COLUNAS_DO_TECLADO = 6

/// A busca do Odeon, dentro do Odeon.
///
/// ## ⚠️ O que esta tela substituiu
///
/// O `BUSCAR` do trilho abria a busca **do sistema**. Na TCL do dono isso caía
/// no assistente da Google: «o buscar está ativando o Gemini, wtf». O
/// assistente não sabe o que existe neste acervo, e o botão levava pra fora do
/// app — o pior destino possível pra um botão de dentro.
///
/// ## Por que teclado na tela e não campo de texto com o teclado do sistema
///
/// A §5.1 era categórica: «não há campo de texto nesta tela, digitar com D-pad é
/// soletrar». Ela estava certa sobre o custo e errada sobre a saída, porque a
/// alternativa que ela propunha (a busca do sistema) não está sob nosso
/// controle — e quando o aparelho decide que busca é assistente, não há o que
/// fazer.
///
/// ⚠️ Um `TextField` chamaria o **IME do aparelho**, que é a mesma aposta com
/// outra roupa: quem desenha aquele teclado é a fabricante, ele aparece por cima
/// da tela e nesta TCL é o mesmo teto do Google. Um teclado desenhado por nós é
/// mais trabalho e é a única forma de a tela ser inteira nossa.
///
/// ## Por que ela busca sozinha, sem botão de «procurar»
///
/// Um botão de confirmar obrigaria a atravessar o teclado inteiro até ele depois
/// de cada correção. Como o modelo espera 200ms e cancela o pedido anterior, a
/// lista simplesmente **acompanha** o que está escrito — e quem digitou o
/// bastante já vê o resultado antes de terminar de soletrar.
@Composable
fun TelaDaBuscaDaTv(
    modelo: ModeloDaBusca,
    aoAbrirObra: (String) -> Unit,
    saidaEsquerda: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    /// ⚠️ O foco entra no `a`, e não nos resultados: ao chegar aqui não há
    /// resultado nenhum, e uma tela de busca que abre com o foco no vazio faz a
    /// pessoa procurar onde escrever.
    val primeiraTecla = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { primeiraTecla.requestFocus() } }

    /// O item cuja escolha de versão está aberta.
    ///
    /// ## ⚠️ Ela precisa existir **aqui também**, e a TV é que denunciou
    ///
    /// Esta tela busca pelo mesmo `/api/library` da grade (`ModeloDaBusca` chama
    /// `odeon.biblioteca`), então ela passou a receber as entradas **agrupadas**
    /// assim que o servidor mudou, em 14/08/2026. Sem esta modal ela continuaria
    /// abrindo `aoAbrirObra(item.id)` direto — e a segunda versão do filme ficaria
    /// **inalcançável pela busca**, que é pior do que era antes do agrupamento.
    ///
    /// Foi achado ao preparar a conferência na TV, e não ao escrever o código: a
    /// grade da biblioteca funcionava, e a busca é a tela por onde se chega no 007
    /// de verdade — ninguém rola 8.273 cartazes.
    var escolhendoVersao by remember { mutableStateOf<ItemDaBiblioteca?>(null) }

    Row(modifier.fillMaxSize()) {
        Column(
            Modifier
                .width(300.dp)
                .fillMaxHeight()
                .padding(start = Sala.overscanH, top = Sala.overscanV, bottom = Sala.overscanV),
        ) {
            LinhaDoQueFoiEscrito(estado.texto)
            Spacer(Modifier.height(20.dp))

            TECLAS.chunked(COLUNAS_DO_TECLADO).forEachIndexed { fileira, letras ->
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    letras.forEachIndexed { coluna, letra ->
                        Tecla(
                            rotulo = letra.toString(),
                            /// ⚠️ Só a **primeira coluna** entrega o foco ao
                            /// trilho no ◀. Nas outras, ◀ é «uma letra pra
                            /// esquerda» — que é o que a pessoa quer ao andar
                            /// dentro do teclado.
                            saidaEsquerda = if (coluna == 0) saidaEsquerda else null,
                            modifier = if (fileira == 0 && coluna == 0) {
                                Modifier.focusRequester(primeiraTecla)
                            } else {
                                Modifier
                            },
                        ) { modelo.digitou(letra) }
                    }
                }
                Spacer(Modifier.height(6.dp))
            }

            Spacer(Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Tecla("espaço", largura = 104.dp, saidaEsquerda = saidaEsquerda) {
                    modelo.digitou(' ')
                }
                Tecla("apagar", largura = 90.dp) { modelo.apagou() }
                Tecla("limpar", largura = 90.dp) { modelo.limpou() }
            }
        }

        Box(Modifier.fillMaxSize()) {
            when {
                estado.curtoDemais -> Aviso(
                    /// ⚠️ A frase diz **o que falta**, não o que faltou. «Digite
                    /// pelo menos duas letras» é acionável; «busca inválida» é
                    /// uma reclamação. §8b.
                    "escreva pelo menos duas letras",
                    "as obras aparecem aqui conforme você soletra.",
                )

                estado.erro != null -> Aviso("a busca não respondeu", estado.erro)

                estado.vazio -> Aviso(
                    "nada com «${estado.texto.trim()}»",
                    "o acervo não tem nenhum título parecido.",
                )

                else -> Resultados(
                    itens = estado.itens,
                    capa = modelo::capa,
                    /// ⚠️ Filme com mais de uma versão **pergunta qual** antes de
                    /// abrir, igual à grade. Ver `escolhendoVersao` acima pro que
                    /// acontecia sem isto.
                    aoEscolherItem = { item ->
                        if (item.temEscolhaDeVersao) escolhendoVersao = item
                        else aoAbrirObra(item.id)
                    },
                )
            }

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

            /// ⚠️ O aviso de que está procurando fica **numa quina**, e não no
            /// lugar da lista: trocar a lista inteira por «procurando…» a cada
            /// letra faria a tela piscar do começo ao fim de uma palavra. A lista
            /// anterior continua no lugar até a nova chegar.
            if (estado.procurando) {
                Text(
                    text = "procurando…",
                    style = MaterialTheme.typography.labelMedium,
                    color = Cores.textoApagado,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(horizontal = Sala.overscanH, vertical = Sala.overscanV),
                )
            }
        }
    }
}

/// O que já foi escrito, com um traço no fim fazendo de cursor.
@Composable
private fun LinhaDoQueFoiEscrito(texto: String) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(52.dp)
            .background(Cores.fundoElevado, RoundedCornerShape(10.dp))
            .border(1.dp, Cores.destaqueApagado.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Text(
            /// ⚠️ O `_` no fim é o cursor, e ele existe porque **não há campo de
            /// texto**: sem ele, uma caixa com uma palavra dentro parece um
            /// rótulo, e nada na tela diz que aquilo está sendo escrito agora.
            text = if (texto.isEmpty()) "o que você procura?" else "$texto▁",
            style = MaterialTheme.typography.titleMedium.copy(fontSize = 18.sp),
            color = if (texto.isEmpty()) Cores.textoApagado else Cores.texto,
            maxLines = 1,
        )
    }
}

@Composable
private fun Tecla(
    rotulo: String,
    modifier: Modifier = Modifier,
    largura: androidx.compose.ui.unit.Dp = 44.dp,
    saidaEsquerda: FocusRequester? = null,
    aoEscolher: () -> Unit,
) {
    val forma = RoundedCornerShape(8.dp)
    Focavel(
        aoEscolher = aoEscolher,
        forma = forma,
        modifier = modifier.then(
            /// Só a primeira coluna manda o ◀ pro trilho; nas outras ele é «uma
            /// letra pra trás», pela busca direcional normal.
            if (saidaEsquerda != null) {
                Modifier.focusProperties { left = saidaEsquerda }
            } else {
                Modifier
            },
        ),
    ) { focado ->
        Box(
            Modifier
                .width(largura)
                .height(40.dp)
                .background(if (focado) Cores.destaqueQuente else Cores.fundoElevado, forma),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = rotulo,
                style = MaterialTheme.typography.labelLarge,
                color = if (focado) Cores.fundoAfundado else Cores.texto,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun Resultados(
    itens: List<ItemDaBiblioteca>,
    capa: (ItemDaBiblioteca) -> String?,
    /// ⚠️ Entrega o **item**, e não o id: quem decide o que fazer com ele é a
    /// tela, porque um filme com mais de uma versão pergunta antes de abrir. Com
    /// `(String) -> Unit` esta lista não teria como saber que há escolha.
    aoEscolherItem: (ItemDaBiblioteca) -> Unit,
) {
    if (itens.isEmpty()) return
    LazyVerticalGrid(
        columns = GridCells.Adaptive(Sala.cartazL + Sala.vaoEntreCartazes),
        contentPadding = PaddingValues(
            start = 24.dp,
            end = Sala.overscanH,
            top = Sala.overscanV,
            bottom = Sala.overscanV,
        ),
        horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
        verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
        modifier = Modifier.fillMaxSize(),
    ) {
        itemsIndexed(itens, key = { _, i -> i.id }) { _, item ->
            Cartaz(
                titulo = item.title,
                arte = capa(item),
                cor = item.corDominante,
                detalhe = item.year?.toString(),
                aoEscolher = { aoEscolherItem(item) },
            )
        }
    }
}

/// ⚠️ Não é o `Recado` das outras telas de propósito: aquele centraliza na tela
/// inteira e traz botões. Aqui a mensagem divide o espaço com o teclado e não
/// tem ação nenhuma pra oferecer — a ação é continuar escrevendo, à esquerda.
@Composable
private fun Aviso(titulo: String, detalhe: String?) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(titulo, style = TipoDaSala.rotulo, color = Cores.destaque)
        if (detalhe != null) {
            Spacer(Modifier.height(10.dp))
            Text(
                detalhe,
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.textoApagado,
            )
        }
    }
}
