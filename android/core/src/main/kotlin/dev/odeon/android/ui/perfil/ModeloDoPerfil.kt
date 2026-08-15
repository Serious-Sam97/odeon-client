package dev.odeon.android.ui.perfil

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.Perfil
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDoPerfil(
    val carregando: Boolean = false,
    val perfil: Perfil? = null,
    /// `true` depois que a primeira tentativa terminou, tenha dado certo ou
    /// não. É o que impede a insígnia de repetir a chamada a cada troca de aba
    /// quando o servidor respondeu erro — ver `carregarSePreciso`.
    val jaTentou: Boolean = false,
)

/// O modelo do "eu": a insígnia do canto **e** a tela do perfil.
///
/// ## Um modelo pros dois, e é o que evita duas chamadas
///
/// A insígnia precisa de quatro campos (rosto, nível, fatia, moldura) e a tela
/// precisa da resposta inteira — e as duas coisas saem de `GET /api/perfil`. Com
/// dois modelos, abrir o perfil pediria de novo o que o canto da tela já tinha
/// há dez minutos.
///
/// É a mesma decisão da web, e o comentário dela diz o resto: «uma requisição,
/// na montagem: o número muda devagar e a barra não é lugar de ficar
/// perguntando».
///
/// ⚠️ Por isso ele **vive no `AppOdeon`**, e não dentro da gaveta: quem sobrevive
/// à troca de aba é ele, e a gaveta é redesenhada a cada uma.
class ModeloDoPerfil(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDoPerfil())
    val estado: StateFlow<EstadoDoPerfil> = _estado.asStateFlow()

    /// Virou `true` quando a sessão acabou de ser esquecida. Quem ouve troca de
    /// tela — o modelo não navega, pelo mesmo motivo que o `ModeloDeLogin` não
    /// navega: quem sabe onde ficam as telas é o `AppOdeon`.
    private val _saiu = MutableStateFlow(false)
    val saiu: StateFlow<Boolean> = _saiu.asStateFlow()

    /// Carrega **uma vez**, e só se ainda não tentou.
    ///
    /// Sem o `jaTentou`, uma resposta de erro deixaria o `perfil` nulo e a
    /// insígnia repetiria a chamada em toda troca de aba — cinco abas viram
    /// cinco requisições pra um dado que muda devagar, e nenhuma delas
    /// consertaria nada, porque o que falhou foi o servidor.
    fun carregarSePreciso() {
        val agora = _estado.value
        if (agora.jaTentou || agora.carregando) return
        carregar()
    }

    /// Relê agora, custe o que custar. É o que a tela do perfil oferece quando a
    /// primeira tentativa falhou — ali houve uma pergunta explícita, e §8b pede
    /// um caminho de volta.
    fun carregar() {
        _estado.update { it.copy(carregando = true) }
        viewModelScope.launch {
            val vindo = odeon.perfil()
            _estado.update { it.copy(carregando = false, perfil = vindo, jaTentou = true) }
        }
    }

    /// Sair.
    ///
    /// ⚠️ **Esquecer o perfil faz parte de sair.** Sem isso, entrar com outra
    /// conta no mesmo aparelho mostraria o rosto e o nível de quem saiu, até
    /// alguém abrir o perfil e reparar — que é o §18 na sua forma mais
    /// constrangedora: a tela afirmando ser outra pessoa.
    fun sair() {
        viewModelScope.launch {
            odeon.sair()
            _estado.value = EstadoDoPerfil()
            _saiu.value = true
        }
    }

    /// Consumido por quem já trocou de tela — senão qualquer recomposição
    /// mandaria a pessoa de volta pro login logo depois de ela entrar de novo.
    fun jaSaiu() {
        _saiu.value = false
    }

    fun arte(caminho: String?): String? = odeon.urlDoPoster(caminho)
}
