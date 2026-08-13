package dev.odeon.android.tv

import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.compose.viewModel

/// Como um `Modelo*` do `:core` nasce numa tela da sala.
///
/// ## Por que não `remember { ModeloDaBiblioteca(odeon) }`
///
/// Porque `remember` morre com a composição, e um `ViewModel` não deve. Numa TV
/// isso é mais visível que num celular: trocar de aba e voltar é o gesto mais
/// comum do controle, e com `remember` cada volta refaria a chamada — o que numa
/// biblioteca de 17.930 obras é uma página inteira baixada de novo, e o foco
/// voltando pro começo da fileira.
///
/// O `viewModel()` guarda a instância no `ViewModelStore` da Activity, que
/// sobrevive à troca de aba e à recomposição. O `key` é o que dá uma instância
/// **por obra** onde isso importa — duas fichas abertas em sequência não podem
/// dividir o mesmo `ModeloDaObra`, senão a segunda abre com os dados da
/// primeira.
///
/// ## O `@Suppress` é a fronteira do `ViewModelProvider.Factory`
///
/// A interface é de antes dos genéricos reificados e devolve `T` a partir de uma
/// `Class<T>`. Não há como escrever isto sem o elenco não-verificado; o que dá
/// pra fazer é isolá-lo **num lugar só**, que é este, em vez de repeti-lo em
/// cada uma das oito telas.
@Composable
inline fun <reified M : ViewModel> lembrarModelo(
    chave: String? = null,
    crossinline criar: () -> M,
): M = viewModel(
    key = chave,
    factory = object : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = criar() as T
    },
)
