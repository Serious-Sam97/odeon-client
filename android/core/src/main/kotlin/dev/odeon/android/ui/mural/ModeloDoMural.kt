package dev.odeon.android.ui.mural

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.Mural
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDoMural(
    val carregando: Boolean = true,
    val mural: Mural = Mural(),
)

/// O mural. Uma rota, um estado.
///
/// Não há `erro` no estado, e é decisão: `RepositorioOdeon.mural()` já devolve
/// mural vazio quando falha, e as duas situações — «a rede caiu» e «ninguém fez
/// nada esta semana» — desenham a mesma coisa aqui, porque nos dois casos não há
/// o que mostrar.
///
/// ⚠️ Isso seria §8b se houvesse ação a tomar. Não há: o mural é só leitura
/// nesta versão. Se um dia ele ganhar «escrever post», a distinção volta a
/// importar — publicar num mural que na verdade está offline é perder o texto.
class ModeloDoMural(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDoMural())
    val estado: StateFlow<EstadoDoMural> = _estado.asStateFlow()

    init { carregar() }

    private fun carregar() {
        viewModelScope.launch {
            _estado.update { it.copy(carregando = true) }
            val mural = odeon.mural()
            _estado.update { it.copy(carregando = false, mural = mural) }
        }
    }

    fun arte(caminho: String?): String? = odeon.urlDoPoster(caminho)
}
