package dev.odeon.android.ui.guia

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.GuiaDeEixos
import dev.odeon.android.dados.RepositorioOdeon
import dev.odeon.android.dados.Revista
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDoGuia(
    val carregando: Boolean = true,
    val eixos: GuiaDeEixos = GuiaDeEixos(),
    /// A capa da semana.
    ///
    /// `null` é o estado normal de quem não tem capa — servidor sem a rota,
    /// revista fora do ar, semana ainda não sorteada. A tela começa nos eixos e
    /// não diz nada sobre isso: uma capa que não existe não vira aviso.
    val revista: Revista? = null,
)

class ModeloDoGuia(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDoGuia())
    val estado: StateFlow<EstadoDoGuia> = _estado.asStateFlow()

    init { carregar() }

    /// As duas rotas, em paralelo, **e falhando separado**.
    ///
    /// É o arranjo do `ModeloDaLocadora`, pelo mesmo motivo: são duas coisas
    /// diferentes (a capa e o índice), nenhuma depende da outra, e em série a
    /// tela esperaria a soma das duas viagens pela tailnet. Cada uma trata a
    /// própria falha lá no repositório — revista fora do ar não apaga os eixos.
    private fun carregar() {
        viewModelScope.launch {
            _estado.update { it.copy(carregando = true) }
            val eixos = async { odeon.guia() }
            val revista = async { odeon.revista() }
            _estado.update {
                it.copy(carregando = false, eixos = eixos.await(), revista = revista.await())
            }
        }
    }

    fun arte(caminho: String?): String? = odeon.urlDoPoster(caminho)
}
