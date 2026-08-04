package dev.odeon.android.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import dev.odeon.android.OdeonApp
import dev.odeon.android.dados.RepositorioOdeon
import dev.odeon.android.ui.biblioteca.ModeloDaBiblioteca
import dev.odeon.android.ui.biblioteca.TelaDaBiblioteca
import dev.odeon.android.ui.login.ModeloDeLogin
import dev.odeon.android.ui.login.TelaDeLogin

/// Onde o app está.
///
/// ## Não há biblioteca de navegação, e é escolha
///
/// São **dois destinos**, e a transição entre eles é de mão única: entrou, não
/// volta pro login pelo botão voltar — voltar pra tela de login depois de entrar
/// é o tipo de "volta" que ninguém pede.
///
/// `navigation-compose` resolve pilha, argumento tipado e deep link. Nada disso
/// existe aqui hoje. Ele entra quando houver a terceira tela e uma pilha de
/// verdade — a ficha da obra, na fase 2 —, e aí entra resolvendo problema
/// medido.
private sealed interface Onde {
    data object Decidindo : Onde
    data object Login : Onde
    data object Biblioteca : Onde
}

@Composable
fun AppOdeon() {
    val app = LocalContext.current.applicationContext as OdeonApp
    var onde: Onde by remember { mutableStateOf(Onde.Decidindo) }

    /// O arranque: havia servidor e sessão guardados?
    ///
    /// Enquanto isso não se resolve, a tela fica no `Decidindo`. Mostrar o login
    /// por um instante e depois trocar pra biblioteca seria piscar uma pergunta
    /// já respondida.
    LaunchedEffect(Unit) {
        onde = if (app.odeon.retomar()) Onde.Biblioteca else Onde.Login
    }

    TemaOdeon {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
        ) {
            when (onde) {
                Onde.Decidindo -> Box(
                    Modifier.fillMaxSize().safeDrawingPadding(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(color = Cores.destaque)
                }

                Onde.Login -> {
                    val modelo: ModeloDeLogin = viewModel(factory = fabrica(app.odeon))
                    val entrou by modelo.entrou.collectAsStateWithLifecycle()
                    LaunchedEffect(entrou) { if (entrou) onde = Onde.Biblioteca }
                    TelaDeLogin(modelo)
                }

                Onde.Biblioteca -> {
                    val modelo: ModeloDaBiblioteca = viewModel(factory = fabrica(app.odeon))
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
                        TelaDaBiblioteca(modelo)
                    }
                }
            }
        }
    }
}

/// A fábrica dos dois modelos.
///
/// Escrita à mão porque o grafo tem **um nó** — o repositório. Um framework de
/// injeção aqui seria configuração para resolver o que um parâmetro resolve.
private fun fabrica(odeon: RepositorioOdeon) = viewModelFactory {
    initializer { ModeloDeLogin(odeon) }
    initializer { ModeloDaBiblioteca(odeon) }
}
