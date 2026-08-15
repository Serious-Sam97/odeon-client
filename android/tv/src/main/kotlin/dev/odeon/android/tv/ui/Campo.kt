package dev.odeon.android.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.ui.Cores

/// Um campo de texto na sala.
///
/// ## ⚠️ Digitar numa TV é ruim, e nenhum código conserta isso
///
/// Vale dizer de frente, porque a tentação é achar que há um truque. Não há: o
/// teclado de uma Google TV é uma grade de letras que se percorre com setas, e
/// escrever uma senha de doze caracteres custa uns oitenta apertos. Todo app de
/// TV do mundo tem esse mesmo problema, inclusive os grandes — é por isso que
/// eles empurram o login pro celular com um código de pareamento.
///
/// O que **dá** pra fazer é o que está feito aqui, e é honesto listar:
///
///  - campos grandes, com foco óbvio, pra não errar de campo com o D-pad
///  - o endereço do servidor **junto** do login, porque numa TV "não conecta" é
///    quase sempre o IP errado, e mandar a pessoa procurar isso noutra tela é
///    fazê-la digitar duas vezes
///  - `ImeAction.Go` na senha, pra entrar direto do teclado sem ter de navegar
///    até o botão — que é justamente o caminho que o `ModeloDeLogin` já protege
///    com a guarda de campo vazio
///  - a sessão fica **salva por aparelho**: digita-se uma vez, e só de novo se
///    alguém sair
///
/// O caminho de verdade — entrar pelo celular e a TV herdar — é uma rota nova no
/// servidor (um código curto, de vida curta, trocado por sessão). Está anotado
/// em `docs/PEDIDOS-AO-SERVIDOR.md`; não dá pra fazer só deste lado.
///
/// ## Por que `BasicTextField`, e não um `TextField`
///
/// Porque não há: `androidx.tv.material3` não tem campo de texto, e o
/// `OutlinedTextField` do Material de celular **não está no classpath** deste
/// módulo, de propósito (ver `tv/build.gradle.kts`). O `BasicTextField` vem do
/// `foundation`, que é comum aos dois, e a moldura é desenhada aqui — o que de
/// todo jeito seria necessário, porque a do Material tem o rótulo flutuante e as
/// medidas do toque.
@Composable
fun CampoDaSala(
    rotulo: String,
    valor: String,
    aoMudar: (String) -> Unit,
    modifier: Modifier = Modifier,
    /// A dica embaixo — o que se espera ali. Numa TV ela vale mais que num
    /// celular: quem errou o formato vai gastar oitenta apertos pra corrigir.
    dica: String? = null,
    senha: Boolean = false,
    acaoDoTeclado: ImeAction = ImeAction.Next,
    aoConcluir: () -> Unit = {},
    focoInicial: FocusRequester? = null,
) {
    var focado by remember { mutableStateOf(false) }
    val forma = RoundedCornerShape(8.dp)
    val gerente = LocalFocusManager.current

    Column(modifier) {
        Text(
            text = rotulo.uppercase(),
            style = TipoDaSala.rotulo,
            color = if (focado) Cores.destaqueQuente else Cores.textoApagado,
        )
        Spacer(Modifier.height(8.dp))

        Box(
            Modifier
                .fillMaxWidth()
                .background(Cores.fundoElevado, forma)
                .border(
                    width = if (focado) 3.dp else 1.dp,
                    color = if (focado) Cores.destaqueQuente else Cores.linha,
                    shape = forma,
                ),
        ) {
            BasicTextField(
                value = valor,
                onValueChange = aoMudar,
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.merge(color = Cores.texto),
                /// O cursor é dourado porque é o único movimento na tela
                /// enquanto se digita, e numa TV o cursor padrão (branco fino) a
                /// três metros some.
                cursorBrush = androidx.compose.ui.graphics.SolidColor(Cores.destaqueQuente),
                visualTransformation = if (senha) {
                    PasswordVisualTransformation()
                } else {
                    VisualTransformation.None
                },
                keyboardOptions = KeyboardOptions(
                    /// `KeyboardType.Uri` no endereço não é capricho: ele põe o
                    /// `.` e o `/` na primeira tela do teclado da TV, em vez de
                    /// atrás de um "mais símbolos" que custa mais seis apertos.
                    keyboardType = if (senha) KeyboardType.Password else KeyboardType.Text,
                    imeAction = acaoDoTeclado,
                ),
                /// ## ⚠️ O ⏎ do teclado **anda de campo**, e é a única saída com
                /// o IME aberto
                ///
                /// Medido na TCL: com o teclado no ar, `mInputShown=true`, ele
                /// consome as setas antes de o app vê-las — o D-pad passa a
                /// andar entre as **letras**, não entre os campos. É correto e é
                /// o que todo app de TV faz.
                ///
                /// A consequência é que o `onNext` deixa de ser conveniência e
                /// vira o caminho. Antes ele chamava só o `aoConcluir`, que no
                /// campo de usuário é `{}` — ou seja, apertar ⏎ ali não fazia
                /// **nada**, e o único jeito de chegar na senha era fechar o
                /// teclado com «voltar» e descer. Ninguém descobre isso sozinho.
                ///
                /// Agora ele faz as duas coisas, e a ordem importa: primeiro o
                /// `aoConcluir` (no campo do servidor é ele que dispara a
                /// procura), depois o salto. Assim ⏎ no servidor procura **e**
                /// desce; ⏎ no usuário desce; ⏎ na senha entra — esse último
                /// pelo `onGo`, que não desce porque não há pra onde.
                keyboardActions = KeyboardActions(
                    onGo = { aoConcluir() },
                    onDone = { aoConcluir() },
                    onNext = {
                        aoConcluir()
                        gerente.moveFocus(FocusDirection.Down)
                    },
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 18.dp, vertical = 16.dp)
                    .onFocusChanged { focado = it.isFocused }
                    /// ## ⚠️ Sem isto o D-pad **não sai do primeiro campo**
                    ///
                    /// Medido na TCL em 12/08/2026, com `uiautomator`: ▼, ▼ e ▲
                    /// deixavam o foco parado no mesmo retângulo
                    /// (`[852,237][1788,333]`, o campo do servidor). Usuário,
                    /// senha e o botão eram **inalcançáveis** pelo controle — a
                    /// tela de login não dava pra usar.
                    ///
                    /// A causa é do `BasicTextField`: um campo de texto consome
                    /// as setas verticais pra mover o cursor dentro do texto, e
                    /// consome mesmo sendo `singleLine`, onde não há pra onde
                    /// mover. O evento morre ali e a busca de foco nunca roda.
                    ///
                    /// Num celular isso nunca apareceu porque **não há setas**:
                    /// o dedo escolhe o campo. É o defeito mais caro deste
                    /// módulo até agora, e é invisível pra `assembleDebug`,
                    /// pro lint e pros 155 testes.
                    ///
                    /// ⚠️ `onPreviewKeyEvent` e **não** `onKeyEvent`: o preview
                    /// desce de fora pra dentro, então ele vê a tecla antes do
                    /// campo. Com `onKeyEvent`, que sobe de dentro pra fora, o
                    /// campo já teria consumido e este bloco nunca rodaria.
                    ///
                    /// ◀ ▶ ficam de fora de propósito: ali o cursor **tem** o
                    /// que fazer, e roubá-las impediria de corrigir uma letra no
                    /// meio de um endereço.
                    .onPreviewKeyEvent { evento ->
                        if (evento.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                        when (evento.key) {
                            Key.DirectionDown -> {
                                gerente.moveFocus(FocusDirection.Down)
                                true
                            }
                            Key.DirectionUp -> {
                                gerente.moveFocus(FocusDirection.Up)
                                true
                            }
                            else -> false
                        }
                    }
                    .then(
                        if (focoInicial != null) Modifier.focusRequester(focoInicial) else Modifier,
                    ),
            )
        }

        if (dica != null) {
            Spacer(Modifier.height(6.dp))
            Text(
                text = dica,
                style = MaterialTheme.typography.labelMedium,
                color = Cores.textoApagado,
            )
        }
    }
}

/// Um texto com uma parte em dourado — pro «não respondeu em https://rog:8443».
///
/// O endereço tem que se destacar da frase: ele é o que a pessoa vai conferir, e
/// numa parede de texto cinza a 3 m ninguém acha uma URL.
@Composable
fun FraseComDestaque(
    frase: String,
    destaque: String?,
    modifier: Modifier = Modifier,
) {
    val texto = androidx.compose.ui.text.buildAnnotatedString {
        val corte = destaque?.let { frase.indexOf(it) } ?: -1
        if (destaque == null || corte < 0) {
            append(frase)
        } else {
            append(frase.substring(0, corte))
            withStyle(SpanStyle(color = Cores.destaque)) { append(destaque) }
            append(frase.substring(corte + destaque.length))
        }
    }
    Text(
        text = texto,
        style = MaterialTheme.typography.bodyMedium,
        color = Cores.textoApagado,
        modifier = modifier,
    )
}
