package dev.odeon.android.ui.perfil

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.odeon.android.dados.Perfil
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Insignia
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.corDeHex

/// A gaveta "eu", no canto de cima à direita.
///
/// ## Ela é a única coisa do app que fica por cima de toda aba
///
/// E é assim na web também (§1.2): navegação de um lado, **o que não é acervo**
/// do outro. O app não tem barra de cima — cada tela escreve o próprio título —,
/// então a gaveta entra como o canto oposto ao título, que é onde a web a põe e
/// onde o polegar de quem segura o celular com a direita alcança.
///
/// ## O que ela abre, e por que só duas linhas
///
/// `perfil` e `sair`. A web tem as mesmas duas, e o cadeado do https ao lado —
/// que aqui não faz sentido: o app não é servido por uma página, e o esquema já
/// foi escolhido na tela de entrada.
///
/// ## Sem perfil carregado, a insígnia continua desenhando
///
/// O anel fica a zero, o selo mostra `·` e o miolo é um disco liso — ver
/// `Insignia`. Não é estado de erro: é o que se sabe até a resposta chegar.
/// Esconder a gaveta enquanto isso faria o **sair** aparecer e desaparecer
/// sozinho, que é pior do que um número que ainda não chegou.
@Composable
fun GavetaDoEu(
    /// O nome de quem entrou — o que a marca desenhada usa de semente. Em branco
    /// enquanto o perfil não chegou, e aí não há marca nenhuma.
    nome: String,
    perfil: Perfil?,
    rosto: String?,
    aoAbrirPerfil: () -> Unit,
    aoSair: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var aberta by remember { mutableStateOf(false) }

    /// A cor da pessoa, com o dourado da casa como piso — nunca uma cor
    /// sorteada (§18). É a mesma que a insígnia usa, lida uma vez e passada às
    /// duas, pra o anel e a borda do painel não poderem discordar.
    val tinta = corDeHex(perfil?.moldura) ?: Cores.destaque

    Box(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Insignia(
            nome = nome,
            rosto = rosto,
            nivel = perfil?.progresso?.nivel,
            fatia = perfil?.fatiaDoNivel ?: 0f,
            cor = tinta,
            /// ⚠️ **Sem `clip` aqui**, e foi o screenshot que mandou tirar.
            ///
            /// A primeira versão recortava a insígnia num círculo pra o toque
            /// ficar redondo. O selo do nível mora no canto de baixo à direita
            /// e **encosta na borda** — o recorte circular comia justamente o
            /// pedaço onde o número está, e o que aparecia na tela era uma foice
            /// dourada com meio algarismo dentro.
            ///
            /// O toque continua funcionando na área quadrada dos 36dp, que é
            /// maior que o alvo mínimo do círculo e não custa nada: não há nada
            /// ao lado pra clicar por engano.
            /// 48dp, e não os 36 da primeira versão.
            ///
            /// A web desenha a insígnia com 38px numa barra que tem o nome da
            /// pessoa escrito ao lado — aqui não há nome, não há barra e não há
            /// mais nada no canto: o rosto **é** a gaveta inteira, e ele tinha
            /// que ser lido de relance a um braço de distância. A foto de 36dp
            /// mostrava um rosto do tamanho de uma unha, e o selo do nível vinha
            /// junto, encolhido na mesma proporção.
            ///
            /// 48dp é também o alvo de toque mínimo do Material, que a versão
            /// anterior só alcançava por causa do `padding` em volta.
            tamanho = 48.dp,
            modifier = Modifier.clickable { aberta = true },
        )

        /// ## ⚠️ O `DropdownMenuItem` saiu — 05/08/2026
        ///
        /// A queixa foi «esse menu que aparece está feio demais», e a foto dava
        /// razão: dois rótulos soltos num retângulo escuro, com um vão enorme
        /// entre eles e nada em volta.
        ///
        /// Nada daquilo era escolha desta tela. O `DropdownMenuItem` do Material
        /// tem **48dp de altura mínima** mais 12dp de respiro vertical próprio, e
        /// o `DropdownMenu` vem sem borda: dois itens viravam 120dp de caixa pra
        /// 24dp de texto. É o mesmo diagnóstico que tirou a `NavigationBar` daqui
        /// (ver `BarraDoFacho`) — o menu era a segunda peça do app pintada por
        /// outra pessoa.
        ///
        /// O que entrou no lugar:
        ///
        /// | | |
        /// |---|---|
        /// | **o cabeçalho** | nome, nível e a fatia — a gaveta passa a dizer **de quem** ela é |
        /// | **a moldura tinge** | borda e barra saem da cor que a pessoa escolheu, como no resto do app |
        /// | **as linhas** | 38dp, e não 48+12 |
        /// | **o `›`** | só no `perfil`, porque só ele leva a algum lugar |
        DropdownMenu(
            expanded = aberta,
            onDismissRequest = { aberta = false },
            containerColor = Cores.fundoElevado,
            shape = RoundedCornerShape(14.dp),
            /// A borda é a **moldura da pessoa**, apagada. É o §10 da referência
            /// («a moldura tinge a tela») aplicado ao menor lugar possível — e
            /// sem ela o painel é um retângulo escuro sobre um fundo escuro, que
            /// era metade do problema da foto.
            border = BorderStroke(1.dp, tinta.copy(alpha = 0.30f)),
        ) {
            Column(Modifier.width(196.dp)) {
                /// ## O cabeçalho, e por que ele **não** repete o rosto
                ///
                /// A insígnia está desenhada a 48dp logo acima, ainda na tela: o
                /// painel abre colado nela. Repetir a cara aqui seria mostrar
                /// duas vezes a mesma coisa a 8dp de distância.
                ///
                /// O que a insígnia **não** consegue dizer é o nome — ela é um
                /// disco de 48dp. Na web o nome fica escrito ao lado dela na
                /// barra de cima; aqui não há barra, então é o painel que o diz.
                ///
                /// §24: sem perfil carregado o cabeçalho inteiro não nasce, e a
                /// gaveta volta a ser as duas linhas — que continuam funcionando,
                /// e é por isso que a gaveta não espera o perfil pra existir.
                perfil?.let { p ->
                    Column(
                        Modifier.padding(start = 14.dp, end = 14.dp, top = 12.dp, bottom = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            text = p.nome.ifBlank { nome },
                            style = Tipo.pilula,
                            color = Cores.texto,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            /// O denominador vem junto, como em toda contagem
                            /// deste app — «12 conquistas» sem o total é o
                            /// «Biblioteca 300» de novo.
                            text = buildString {
                                append("nível ${p.progresso.nivel}")
                                if (p.progresso.total > 0) {
                                    /// ⚠️ **Com a palavra «conquistas» junto.** A
                                    /// primeira versão escrevia «nível 2 · 6 de
                                    /// 80», e a foto mostrou o que o código não
                                    /// denuncia: ao lado de «nível 2», um `6 de
                                    /// 80` sem substantivo se lê como se fosse
                                    /// sobre o nível. Número sem o nome da coisa
                                    /// que ele conta é o §18 na forma mais barata
                                    /// de cometer.
                                    append(" · ${p.progresso.desbloqueadas} de ${p.progresso.total} conquistas")
                                }
                            },
                            style = Tipo.pilula.copy(fontSize = 11.sp),
                            color = Cores.textoApagado,
                            maxLines = 1,
                        )

                        /// A fatia do nível — **o mesmo número do anel da
                        /// insígnia**, na forma que um anel de 48dp não consegue
                        /// dar: aqui dá pra ver que falta pouco ou que falta
                        /// tudo. Sai de `Perfil.fatiaDoNivel`, então as duas
                        /// nunca discordam.
                        Box(
                            Modifier
                                .padding(top = 3.dp)
                                .fillMaxWidth()
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Cores.linha),
                        ) {
                            Box(
                                Modifier
                                    .fillMaxWidth(perfil.fatiaDoNivel.coerceIn(0f, 1f))
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(tinta),
                            )
                        }
                    }

                    HorizontalDivider(thickness = 1.dp, color = Cores.linha)
                }

                LinhaDaGaveta(
                    rotulo = "perfil",
                    cor = Cores.texto,
                    /// O `›` é a mesma marca de «isto leva a outra tela» que a
                    /// biblioteca usa no «no aparelho ›» e o balcão no «mais N
                    /// antes ›». Ele **não** vai no `sair`, e a assimetria é a
                    /// informação: sair não abre nada, fecha.
                    leva = true,
                    aoTocar = {
                        aberta = false
                        aoAbrirPerfil()
                    },
                )

                /// `sair` em texto apagado, e não em vermelho: sair não é
                /// destrutivo — o servidor fica guardado, e voltar custa a senha.
                /// Pintar de perigo o que se desfaz em dez segundos é gastar a
                /// cor que a `zona de risco` da web reserva pra apagar arquivo.
                LinhaDaGaveta(
                    rotulo = "sair",
                    cor = Cores.textoApagado,
                    leva = false,
                    aoTocar = {
                        aberta = false
                        aoSair()
                    },
                )
            }
        }
    }
}

/// Uma linha da gaveta.
///
/// **38dp**, contra os 48 de altura mínima mais 12 de respiro que o
/// `DropdownMenuItem` impunha. O alvo de toque mínimo do Material são 48dp e este
/// é menor de propósito: a regra existe pra alvos isolados no meio de uma tela, e
/// aqui a linha ocupa a **largura inteira** de um painel que só tem duas — errar
/// o toque significa acertar a outra, não acertar o nada.
@Composable
private fun LinhaDaGaveta(
    rotulo: String,
    cor: Color,
    leva: Boolean,
    aoTocar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(38.dp)
            .clickable(onClick = aoTocar)
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rotulo, style = Tipo.pilula, color = cor, modifier = Modifier.weight(1f))
        if (leva) {
            Text("›", style = Tipo.pilula, color = Cores.textoApagado.copy(alpha = 0.55f))
        }
    }
}
