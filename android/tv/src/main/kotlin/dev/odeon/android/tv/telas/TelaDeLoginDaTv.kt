package dev.odeon.android.tv.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.CampoDaSala
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.ui.Serifada
import dev.odeon.android.tv.ui.TipoDaSala
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.login.ModeloDeLogin

/// A porta da sala.
///
/// ## Ela é de duas colunas, e não do formulário centralizado do celular
///
/// Numa TV, um formulário centralizado empilha campo-campo-campo-botão numa
/// coluna estreita no meio de uma tela de 1920 de largura, e sobra tela vazia
/// dos dois lados. A da esquerda é a marca e o que explicar; a da direita são os
/// campos.
///
/// ## ⚠️ O que esta tela errou, visto na TCL em 12/08/2026
///
/// O comentário aqui dizia que os campos ficavam «na metade de cima, onde o
/// teclado não alcança». **Era falso**, e a primeira foto do app rodando mostrou
/// os dois defeitos que ele escondia:
///
/// | | o que se viu |
/// |---|---|
/// | o teclado | o IME da TCL ocupa **a metade de baixo** — de 310dp pra baixo, numa tela de 540dp. O campo de senha ficava atrás dele |
/// | o letreiro | a coluna da esquerda tinha ~184dp, e `displayMedium` são 56sp. «o acervo da casa» quebrou **no meio da palavra**: `o acerv / o da / casa` |
///
/// Os dois são a mesma lição: nenhum deles aparece num build verde, e nenhum
/// aparece numa medida feita de cabeça. A régua do projeto — «vistas rodando em
/// aparelho, tela a tela» — existe por causa disto.
///
/// ## O conserto, e por que não é «empurrar tudo pra cima»
///
/// Chutar medidas até caber é o conserto que quebra na próxima TV, porque o IME
/// **não tem altura fixa**: ele muda com o fabricante, com o idioma e com haver
/// sugestão de texto ou não.
///
/// O que funciona é deixar o sistema dizer: `imePadding()` encolhe a coluna até
/// a borda de cima do teclado, e `verticalScroll` faz o Compose trazer o campo
/// focado pra dentro sozinho. Assim o que está focado está visível **por
/// construção**, e não porque a conta bateu nesta TV.
///
/// ⚠️ Isso só funciona com `WindowCompat.setDecorFitsSystemWindows(window, false)`
/// e `adjustResize` no manifesto — sem os dois, o `imePadding` mede zero e a
/// tela volta a ficar exatamente como estava, calada. Ver `AtividadeDaTv`.
@Composable
fun TelaDeLoginDaTv(modelo: ModeloDeLogin) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val primeiroCampo = remember { FocusRequester() }
    val campoDoUsuario = remember { FocusRequester() }

    /// ⚠️ Sem foco inicial explícito, o D-pad não tem de onde partir e **a
    /// primeira seta do controle não faz nada**. É o clássico de app de TV mal
    /// feito, e numa tela de login é fatal: a pessoa aperta, nada acontece, e
    /// conclui que o app travou.
    ///
    /// O `runCatching` está aí porque `requestFocus` lança se o nó ainda não
    /// entrou na composição — e numa TV lenta isso acontece.
    LaunchedEffect(Unit) { runCatching { primeiroCampo.requestFocus() } }

    /// ## Achou o servidor? Então o próximo passo é o usuário — e o foco vai
    /// junto
    ///
    /// Visto na TCL em 12/08/2026, com o servidor de verdade: a procura deu
    /// certo, a dica escreveu «achei em https://odeon-api.serious-sam.dev», e o
    /// foco **continuou no campo do endereço**. Num celular isso não seria nada:
    /// o dedo vai pro campo seguinte. Aqui custa fechar o teclado, descer, e o
    /// teclado abrir de novo — três apertos pra ir ao único lugar que faz
    /// sentido depois de achar o servidor.
    ///
    /// A chave é o `servidorConfirmado`, e não um `Unit`: assim o salto acontece
    /// **na transição** de nulo pra achado, e não a cada recomposição. Quem
    /// voltar ao endereço pra corrigi-lo não é arrastado de volta pra baixo.
    LaunchedEffect(estado.servidorConfirmado) {
        if (estado.servidorConfirmado != null) {
            runCatching { campoDoUsuario.requestFocus() }
        }
    }

    Row(
        Modifier
            .fillMaxSize()
            .background(Cores.fundo)
            .padding(horizontal = Sala.overscanH, vertical = Sala.overscanV),
    ) {
        /// ⚠️ Largura **fixa**, e não `weight(1f)`.
        ///
        /// Com `weight` ela ficou com o que sobrou depois dos 620dp do
        /// formulário — 184dp —, e foi isso que quebrou «acervo» ao meio. Aqui a
        /// coluna do letreiro é que manda: 360dp é o que cabe «o acervo» em
        /// 44sp de serifada, medido na TCL.
        Column(
            Modifier.width(360.dp).fillMaxHeight().padding(end = 40.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("◉", color = Cores.destaque, style = MaterialTheme.typography.headlineMedium)
                Spacer(Modifier.width(16.dp))
                Text(
                    text = "ODEON",
                    style = TipoDaSala.rotulo.copy(fontSize = 22.sp),
                    color = Cores.texto,
                )
            }
            Spacer(Modifier.height(24.dp))
            /// As quebras são **escritas à mão**, e não deixadas pro layout.
            ///
            /// Um letreiro que se reparte sozinho é um letreiro que muda de
            /// forma quando a fonte, o idioma ou a TV mudam — e a foto de
            /// 12/08 mostrou o que ele faz quando não cabe: parte no meio da
            /// palavra. Com `\n`, ou cabe como está escrito, ou o defeito é
            /// visível na hora.
            Text(
                text = "o acervo\nda casa,\nna sala",
                style = MaterialTheme.typography.headlineLarge.copy(fontFamily = Serifada),
                color = Cores.texto,
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "Basta o host: «rog» ou o IP. Ele tenta https na 8443 " +
                    "e cai pra http na 8080.",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.textoApagado,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = "Fica salvo neste aparelho. Digita-se uma vez.",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.destaqueApagado,
            )
        }

        /// ⚠️ `imePadding()` **antes** do `verticalScroll`, e a ordem importa.
        ///
        /// Assim o padding encolhe o contêiner que rola, e o que rola é o que
        /// sobrou acima do teclado. Na ordem inversa, a coluna continuaria do
        /// tamanho da tela e o `bringIntoView` traria o campo focado pra debaixo
        /// do IME — que é o defeito de origem com mais um passo.
        Column(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .imePadding()
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.Center,
        ) {
            CampoDaSala(
                rotulo = "servidor",
                valor = estado.servidor,
                aoMudar = modelo::mudouServidor,
                dica = estado.servidorConfirmado?.let { "achei em $it" },
                focoInicial = primeiroCampo,
                aoConcluir = { if (estado.servidorConfirmado == null) modelo.procurar() },
            )
            Spacer(Modifier.height(22.dp))
            CampoDaSala(
                rotulo = "usuário",
                valor = estado.usuario,
                aoMudar = modelo::mudouUsuario,
                focoInicial = campoDoUsuario,
            )
            Spacer(Modifier.height(22.dp))
            CampoDaSala(
                rotulo = "senha",
                valor = estado.senha,
                aoMudar = modelo::mudouSenha,
                senha = true,
                acaoDoTeclado = ImeAction.Go,
                /// A segunda porta pro login, e a que quem digita rápido usa.
                /// O `ModeloDeLogin` protege esta entrada com a guarda de campo
                /// vazio — o comentário dele explica que ela não é código morto
                /// justamente por causa desta linha.
                aoConcluir = modelo::entrar,
            )

            if (estado.erro != null) {
                Spacer(Modifier.height(20.dp))
                Box(
                    Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.horizontalGradient(
                                listOf(Cores.perigo.copy(alpha = 0.18f), Color.Transparent),
                            ),
                        )
                        .padding(16.dp),
                ) {
                    Text(
                        text = estado.erro!!,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cores.perigo,
                    )
                }
            }

            Spacer(Modifier.height(28.dp))

            /// ## Um botão só, que muda de trabalho
            ///
            /// Antes de haver servidor confirmado ele **procura**; depois, ele
            /// entra. Dois botões lado a lado seriam dois destinos de D-pad pra
            /// uma decisão que o app já sabe tomar — e o errado dos dois estaria
            /// sempre a um aperto de distância.
            val procurando = estado.servidorConfirmado == null
            BotaoDaSala(
                rotulo = when {
                    estado.ocupado -> "…"
                    procurando -> "procurar o servidor"
                    else -> "entrar"
                },
                principal = true,
                habilitado = !estado.ocupado && estado.servidor.isNotBlank(),
                aoEscolher = { if (procurando) modelo.procurar() else modelo.entrar() },
            )
        }
    }
}
