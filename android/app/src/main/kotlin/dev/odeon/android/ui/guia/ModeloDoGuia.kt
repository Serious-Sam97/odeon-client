package dev.odeon.android.ui.guia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.GuiaDeEixos
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDoGuia(
    val carregando: Boolean = true,
    val eixos: GuiaDeEixos = GuiaDeEixos(),
)

class ModeloDoGuia(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDoGuia())
    val estado: StateFlow<EstadoDoGuia> = _estado.asStateFlow()

    init { carregar() }

    private fun carregar() {
        viewModelScope.launch {
            _estado.update { it.copy(carregando = true) }
            val eixos = odeon.guia()
            _estado.update { it.copy(carregando = false, eixos = eixos) }
        }
    }

    fun arte(caminho: String?): String? = odeon.urlDoPoster(caminho)
}
