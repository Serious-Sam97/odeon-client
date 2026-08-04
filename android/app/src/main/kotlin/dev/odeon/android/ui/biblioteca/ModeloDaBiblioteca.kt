package dev.odeon.android.ui.biblioteca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

data class EstadoDaBiblioteca(
    val itens: List<ItemDaBiblioteca> = emptyList(),
    /// O total do filtro atual, que vem repetido em toda linha do servidor.
    ///
    /// `null` enquanto nada chegou — e a tela **não escreve "0"** nesse caso.
    /// Zero é uma afirmação ("não há nada"), e o app ainda não sabe disso.
    val total: Int? = null,
    val carregando: Boolean = false,
    val carregandoMais: Boolean = false,
    val erro: String? = null,
) {
    val temMais: Boolean get() = total != null && itens.size < total
}

class ModeloDaBiblioteca(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaBiblioteca())
    val estado: StateFlow<EstadoDaBiblioteca> = _estado.asStateFlow()

    init {
        primeiraPagina()
    }

    fun primeiraPagina() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            // O token de mídia antes da primeira página: sem ele o `urlDoPoster`
            // devolve nulo e a tela desenha a grade inteira sem capa nenhuma.
            odeon.garantirTokenDeMidia()
            buscar(pulando = 0, primeira = true)
        }
    }

    /// Carrega a próxima página. Ignorado quando já está carregando ou acabou.
    fun maisUmaPagina() {
        val agora = _estado.value
        if (agora.carregando || agora.carregandoMais || !agora.temMais) return

        _estado.update { it.copy(carregandoMais = true) }
        viewModelScope.launch { buscar(pulando = agora.itens.size, primeira = false) }
    }

    private suspend fun buscar(pulando: Int, primeira: Boolean) {
        try {
            val pagina = odeon.biblioteca(pulando = pulando)
            _estado.update { antes ->
                antes.copy(
                    itens = if (primeira) pagina else antes.itens + pagina,
                    // O total vem de toda linha; uma página vazia não traz
                    // nenhuma, e aí o que vale é o tamanho do que já se tem.
                    total = pagina.firstOrNull()?.total ?: antes.total ?: 0,
                    carregando = false,
                    carregandoMais = false,
                    erro = null,
                )
            }
        } catch (e: HttpException) {
            falhou(
                if (e.code() == 401) "a sessão expirou — entre de novo" else "o servidor respondeu ${e.code()}",
            )
        } catch (e: IOException) {
            falhou("sem resposta do servidor")
        }
    }

    private fun falhou(frase: String) = _estado.update {
        it.copy(carregando = false, carregandoMais = false, erro = frase)
    }

    /// A URL da capa, já com o token de mídia. `null` quando a obra não tem.
    fun capa(item: ItemDaBiblioteca): String? = odeon.urlDoPoster(item.poster)
}
