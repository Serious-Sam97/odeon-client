package dev.odeon.android.ui.perfil

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
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

    Box(modifier = modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
        Insignia(
            nome = nome,
            rosto = rosto,
            nivel = perfil?.progresso?.nivel,
            fatia = perfil?.fatiaDoNivel ?: 0f,
            cor = corDeHex(perfil?.moldura) ?: Cores.destaque,
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

        DropdownMenu(
            expanded = aberta,
            onDismissRequest = { aberta = false },
            containerColor = Cores.fundoElevado,
        ) {
            DropdownMenuItem(
                text = { Text("perfil", color = Cores.texto, style = Tipo.pilula) },
                onClick = {
                    aberta = false
                    aoAbrirPerfil()
                },
            )
            DropdownMenuItem(
                /// `sair` em texto apagado, e não em vermelho: sair não é
                /// destrutivo — o servidor fica guardado, e voltar custa a senha.
                /// Pintar de perigo o que se desfaz em dez segundos é gastar a
                /// cor que a `zona de risco` da web reserva pra apagar arquivo.
                text = { Text("sair", color = Cores.textoApagado, style = Tipo.pilula) },
                onClick = {
                    aberta = false
                    aoSair()
                },
            )
        }
    }
}
