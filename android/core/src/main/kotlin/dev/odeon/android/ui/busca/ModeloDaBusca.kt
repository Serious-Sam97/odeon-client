package dev.odeon.android.ui.busca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.Filtros
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/// Quantas letras antes de valer a pena perguntar ao servidor.
///
/// ⚠️ Duas, e não uma. Com uma letra a resposta é «metade do acervo», que não
/// ajuda ninguém e ainda custa a viagem inteira. Com duas já há forma de palavra.
private const val LETRAS_MINIMAS = 2

/// ⚠️ A espera é **mais curta que a do celular** (350ms), e de propósito.
///
/// No celular a pessoa digita rápido e a espera existe pra não perguntar a cada
/// tecla. Aqui cada letra custa uma travessia de D-pad — quem escreve «matrix»
/// gasta segundos entre uma tecla e outra, e uma espera longa depois de tudo
/// isso só adiciona atraso a um gesto que já foi lento.
private const val ESPERA_MS = 200L

data class EstadoDaBusca(
    val texto: String = "",
    val itens: List<ItemDaBiblioteca> = emptyList(),
    val procurando: Boolean = false,
    val erro: String? = null,
    /// Se já houve uma resposta para o texto atual.
    ///
    /// ⚠️ Existe pra distinguir «não achei nada» de «ainda não perguntei» — sem
    /// isso a tela mostraria «nada encontrado» no instante entre a última letra
    /// e a resposta, dizendo que não existe o que talvez exista.
    val respondeu: Boolean = false,
) {
    val curtoDemais: Boolean get() = texto.trim().length < LETRAS_MINIMAS
    val vazio: Boolean get() = respondeu && !procurando && itens.isEmpty() && !curtoDemais
}

/// A busca do Odeon, que é **nossa**.
///
/// ## ⚠️ Por que ela existe, se o botão já abria uma busca
///
/// O item `BUSCAR` do trilho disparava a busca **do sistema** — `GLOBAL_SEARCH`,
/// com `ACTION_ASSIST` de reserva. A ideia era honesta: digitar com D-pad é
/// soletrar, e a busca por voz da TV é melhor que soletrar.
///
/// Na TCL isso não deu numa busca. Deu no assistente da Google — «o buscar está
/// ativando o Gemini, wtf» —, que não sabe o que existe neste acervo e leva
/// quem apertou pra fora do Odeon. Um botão dentro do nosso app que entrega a
/// pessoa a outro app é pior que um botão que não existe.
///
/// ⚠️ **E não dá pra consertar do nosso lado**: o que o `GLOBAL_SEARCH` abre é
/// decisão do aparelho, e nesta TCL a decisão é o assistente. A única saída sob
/// nosso controle é ter a tela aqui dentro.
///
/// ## Por que um modelo separado e não o [dev.odeon.android.ui.biblioteca.ModeloDaBiblioteca]
///
/// A biblioteca **já tem** um campo `busca` nos filtros, e reusá-la seria menos
/// código. Mas os filtros da biblioteca são um estado que a pessoa montou —
/// etiquetas, ano, ordem — e escrever na busca dali significaria que sair da
/// busca deixa o acervo filtrado por um texto que ninguém pediu, ou que entrar
/// na busca apaga um filtro que alguém montou. Duas telas, dois estados.
class ModeloDaBusca(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaBusca())
    val estado: StateFlow<EstadoDaBusca> = _estado.asStateFlow()

    /// ⚠️ O trabalho em curso, guardado pra ser **cancelado** na letra seguinte.
    /// Sem isso, respostas de textos diferentes chegam fora de ordem e a última a
    /// aterrissar vence — e a última a aterrissar não é a última que se pediu.
    private var trabalho: Job? = null

    fun digitou(letra: Char) = trocar(_estado.value.texto + letra)

    fun apagou() = trocar(_estado.value.texto.dropLast(1))

    fun limpou() = trocar("")

    private fun trocar(texto: String) {
        if (texto == _estado.value.texto) return
        _estado.update { it.copy(texto = texto, respondeu = false) }
        trabalho?.cancel()

        if (texto.trim().length < LETRAS_MINIMAS) {
            /// ⚠️ Apagar até uma letra **limpa a lista**. Deixar os resultados de
            /// «matrix» na tela enquanto se lê «ma» diria que aquilo responde ao
            /// que está escrito, e o próximo passo de quem apaga é digitar outra
            /// coisa.
            _estado.update { it.copy(itens = emptyList(), procurando = false, erro = null) }
            return
        }

        trabalho = viewModelScope.launch {
            delay(ESPERA_MS)
            _estado.update { it.copy(procurando = true, erro = null) }
            runCatching { odeon.biblioteca(filtros = Filtros(busca = texto.trim())) }
                .onSuccess { achados ->
                    _estado.update {
                        it.copy(itens = achados, procurando = false, respondeu = true)
                    }
                }
                .onFailure { erro ->
                    _estado.update {
                        it.copy(
                            procurando = false,
                            respondeu = true,
                            erro = erro.message ?: "a busca não respondeu",
                        )
                    }
                }
        }
    }

    fun capa(item: ItemDaBiblioteca): String? = odeon.urlDoPoster(item.poster)
}
