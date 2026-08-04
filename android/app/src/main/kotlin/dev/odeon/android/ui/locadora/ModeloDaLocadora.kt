package dev.odeon.android.ui.locadora

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.Emprestada
import dev.odeon.android.dados.Prateleira
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDaLocadora(
    val carregando: Boolean = true,
    val prateleira: Prateleira? = null,
    val erro: String? = null,
    /// O empréstimo que está sendo devolvido agora, pra o botão não aceitar
    /// dois toques. Devolver duas vezes é mexer no acervo de alguém duas vezes.
    val devolvendo: Int? = null,
) {
    /// As minhas, separadas das dos outros.
    ///
    /// A prateleira mistura tudo de propósito — quem te barra pode ser qualquer
    /// morador, e ver isso é parte da ideia. Mas a tela precisa da separação:
    /// nas minhas dá pra devolver, nas dos outros só dá pra pedir.
    val minhas: List<Emprestada> get() = prateleira?.emprestadas.orEmpty().filter { it.meu }
    val dosOutros: List<Emprestada> get() = prateleira?.emprestadas.orEmpty().filterNot { it.meu }
}

/// A locadora.
///
/// ## Ela é a única tela do app que **escreve** no acervo de todo mundo
///
/// O §11 do `CONTINUAR-ANDROID.md` avisa: não há ambiente de teste, o servidor é
/// o que três pessoas usam, e um empréstimo criado por engano fica no perfil de
/// alguém. Por isso:
///
///   - ler é livre (`prateleira`)
///   - **pegar e devolver pedem confirmação na tela**, e nenhum dos dois é
///     disparado por navegação, montagem ou retentativa automática
///
/// Nada aqui tenta de novo sozinho. Uma retentativa silenciosa num `POST` que
/// cria empréstimo é como duas fitas saem da estante por um toque só.
class ModeloDaLocadora(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaLocadora())
    val estado: StateFlow<EstadoDaLocadora> = _estado.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            _estado.update { it.copy(carregando = true, erro = null) }
            try {
                val prateleira = odeon.prateleira()
                _estado.update { it.copy(carregando = false, prateleira = prateleira) }
            } catch (e: Exception) {
                _estado.update {
                    it.copy(carregando = false, erro = e.message ?: "não deu pra abrir a locadora")
                }
            }
        }
    }

    /// Devolve uma fita. **Escreve.**
    ///
    /// Só chamada por toque, e nunca duas vezes ao mesmo tempo — o `devolvendo`
    /// tranca o botão enquanto a requisição está no ar.
    fun devolver(emprestimoId: Int) {
        if (_estado.value.devolvendo != null) return
        _estado.update { it.copy(devolvendo = emprestimoId) }
        viewModelScope.launch {
            runCatching { odeon.devolver(emprestimoId) }
                .onFailure { e ->
                    _estado.update { it.copy(erro = e.message ?: "a devolução não completou") }
                }
            _estado.update { it.copy(devolvendo = null) }
            carregar()
        }
    }

    fun arte(caminho: String?): String? = odeon.urlDaArte(caminho)
}
