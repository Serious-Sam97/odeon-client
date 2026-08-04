package dev.odeon.android.ui.login

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.odeon.android.ui.Cores

/// Entrar.
///
/// ## Ela é uma tela só, em dois tempos
///
/// Primeiro o endereço, depois a conta. Não são duas telas porque quem já usou
/// o app uma vez não passa mais pelo primeiro tempo — o endereço fica guardado.
///
/// E o primeiro tempo existe porque **o app não deduz o servidor**. A web deduz
/// da própria página; aqui não há página. O que há é alguém digitando o nome da
/// máquina de casa.
@Composable
fun TelaDeLogin(modelo: ModeloDeLogin) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val achouServidor = estado.servidorConfirmado != null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .safeDrawingPadding()
            /// Sem isto o teclado cobre o campo de senha, que é o último da
            /// coluna — e a pessoa digita às cegas.
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 32.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = "Odeon",
            style = MaterialTheme.typography.displaySmall,
            color = Cores.destaque,
        )

        Column(
            modifier = Modifier
                .padding(top = 32.dp)
                .fillMaxWidth()
                /// Num tablet, um campo de texto de 900px de largura é ilegível.
                .widthIn(max = 420.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedTextField(
                value = estado.servidor,
                onValueChange = modelo::mudouServidor,
                label = { Text("servidor") },
                /// O exemplo é literal de propósito: mostra que **só o host
                /// basta**, que é a coisa que a tela mais precisa comunicar.
                placeholder = { Text("rog, ou 192.168.0.10") },
                singleLine = true,
                enabled = !estado.ocupado,
                keyboardOptions = KeyboardOptions(
                    /// `Uri` e não `Text`: dá o teclado com ponto e barra à mão,
                    /// e — o que mais importa — **sem autocorreção**, que
                    /// transformaria `rog` em outra palavra.
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = { modelo.procurar() }),
                modifier = Modifier.fillMaxWidth(),
            )

            /// Os campos de conta só aparecem depois que o servidor respondeu.
            ///
            /// É o §53: **o produto não oferece o que a validação vai negar.**
            /// Um formulário de senha antes de saber se ali tem um Odeon é um
            /// formulário que promete um login impossível.
            if (achouServidor) {
                Text(
                    text = estado.servidorConfirmado.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.textoApagado,
                )

                OutlinedTextField(
                    value = estado.usuario,
                    onValueChange = modelo::mudouUsuario,
                    label = { Text("usuário") },
                    singleLine = true,
                    enabled = !estado.ocupado,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = estado.senha,
                    onValueChange = modelo::mudouSenha,
                    label = { Text("senha") },
                    singleLine = true,
                    enabled = !estado.ocupado,
                    visualTransformation = PasswordVisualTransformation(),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Password,
                        imeAction = ImeAction.Go,
                    ),
                    keyboardActions = KeyboardActions(onGo = { modelo.entrar() }),
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            /// O erro fica **acima** do botão, e some quando alguém digita.
            ///
            /// Abaixo, o teclado o esconderia justamente no momento em que a
            /// pessoa vai tentar de novo.
            estado.erro?.let { frase ->
                Text(
                    text = frase,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.perigo,
                )
            }

            Button(
                onClick = { if (achouServidor) modelo.entrar() else modelo.procurar() },
                /// Desabilitado enquanto ocupado — é o que impede o toque duplo
                /// de mandar dois logins.
                enabled = !estado.ocupado && estado.servidor.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (estado.ocupado) {
                    CircularProgressIndicator(
                        modifier = Modifier.padding(end = 8.dp),
                        strokeWidth = 2.dp,
                    )
                }
                Text(if (achouServidor) "entrar" else "procurar")
            }
        }
    }
}
