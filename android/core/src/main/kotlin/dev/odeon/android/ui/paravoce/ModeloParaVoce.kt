package dev.odeon.android.ui.paravoce

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.Recomendacao
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoParaVoce(
    val carregando: Boolean = true,
    val itens: List<Recomendacao> = emptyList(),
    val aindaNaoTeConhece: Boolean = false,
    /// O corte de tempo escolhido. `null` = sem corte.
    val minutos: Int? = null,
    val erro: String? = null,
)

/// "Para você".
class ModeloParaVoce(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoParaVoce())
    val estado: StateFlow<EstadoParaVoce> = _estado.asStateFlow()

    init {
        carregar(null)
    }

    /// Troca o corte de tempo e repergunta.
    ///
    /// Repergunta ao servidor em vez de filtrar a lista que já veio: quem sabe
    /// **quais** 24 recomendar dentro de 90 minutos é o servidor, com o perfil
    /// inteiro na mão. Filtrar aqui devolveria as sobras de uma lista pensada
    /// pra outro tempo — e uma tela de curadoria que mostra sobras deixa de ser
    /// curadoria.
    fun filtrar(minutos: Int?) {
        if (_estado.value.minutos == minutos) return
        carregar(minutos)
    }

    private fun carregar(minutos: Int?) {
        _estado.update { it.copy(carregando = true, erro = null, minutos = minutos) }
        viewModelScope.launch {
            try {
                val resposta = odeon.paraVoce(minutos)
                _estado.update {
                    it.copy(
                        carregando = false,
                        /// ⚠️ **Cartão sem motivo não entra.**
                        ///
                        /// Esta tela existe pra dizer *por quê*; um item sem
                        /// `reasons` seria catálogo no meio da curadoria, e o
                        /// §18 manda omitir o que não se sabe em vez de exibir
                        /// vazio.
                        itens = resposta.items.filter { r -> r.porque != null },
                        aindaNaoTeConhece = resposta.aindaNaoTeConhece,
                    )
                }
            } catch (e: Exception) {
                _estado.update {
                    it.copy(carregando = false, erro = e.message ?: "não deu pra montar as recomendações")
                }
            }
        }
    }

    fun arte(item: Recomendacao): String? = odeon.urlDaArte(item.poster ?: item.backdrop)
}
