package dev.odeon.android.ui.login

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.RepositorioOdeon
import dev.odeon.android.dados.ResultadoDaProcura
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/// O estado da tela de login.
///
/// Um objeto só, imutável, e não seis `mutableStateOf` soltos. A diferença
/// aparece no `ocupado`: com campos independentes é fácil deixar um botão
/// habilitado durante a chamada, e aí um toque duplo manda dois logins.
data class EstadoDoLogin(
    val servidor: String = "",
    val usuario: String = "",
    val senha: String = "",
    val servidorConfirmado: String? = null,
    val ocupado: Boolean = false,
    /// A frase de erro, já pronta pra tela.
    ///
    /// O §8b manda: um clique que não faz nada é pior que um erro visível. Toda
    /// falha deste fluxo termina aqui, com texto que diz o que houve.
    val erro: String? = null,
)

class ModeloDeLogin(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDoLogin())
    val estado: StateFlow<EstadoDoLogin> = _estado.asStateFlow()

    /// Emitido quando a sessão nasce. Quem ouve troca de tela.
    private val _entrou = MutableStateFlow(false)
    val entrou: StateFlow<Boolean> = _entrou.asStateFlow()

    fun mudouServidor(valor: String) = _estado.update {
        // Mudar o endereço invalida a confirmação anterior — senão a tela
        // continuaria dizendo "achei" sobre um servidor que não é mais o
        // digitado, que é o §18 por outro caminho.
        it.copy(servidor = valor, servidorConfirmado = null, erro = null)
    }

    fun mudouUsuario(valor: String) = _estado.update { it.copy(usuario = valor, erro = null) }

    fun mudouSenha(valor: String) = _estado.update { it.copy(senha = valor, erro = null) }

    /// Procura o servidor. É o que o botão faz quando ainda não há um confirmado.
    fun procurar() {
        val digitado = _estado.value.servidor
        _estado.update { it.copy(ocupado = true, erro = null) }

        viewModelScope.launch {
            when (val r = odeon.procurarServidor(digitado)) {
                is ResultadoDaProcura.Achou -> _estado.update {
                    it.copy(
                        ocupado = false,
                        servidorConfirmado = r.url,
                        erro = if (r.precisaConfigurar) {
                            "este Odeon ainda não tem administrador — configure pela web primeiro"
                        } else {
                            null
                        },
                    )
                }

                ResultadoDaProcura.EnderecoInvalido -> _estado.update {
                    it.copy(ocupado = false, erro = "endereço vazio ou sem host")
                }

                is ResultadoDaProcura.NaoRespondeu -> _estado.update {
                    // Dizer O QUE foi tentado, e não só "falhou": quem digitou
                    // `rog` precisa saber que o app foi em `https://rog:8443` e
                    // `http://rog:8080` antes de desconfiar da própria rede.
                    it.copy(
                        ocupado = false,
                        erro = "não respondeu em " + r.tentados.joinToString(" nem "),
                    )
                }
            }
        }
    }

    fun entrar() {
        val agora = _estado.value
        if (agora.usuario.isBlank() || agora.senha.isEmpty()) {
            _estado.update { it.copy(erro = "usuário e senha") }
            return
        }

        _estado.update { it.copy(ocupado = true, erro = null) }

        viewModelScope.launch {
            try {
                odeon.entrar(agora.usuario, agora.senha)
                odeon.garantirTokenDeMidia()
                _entrou.value = true
            } catch (e: HttpException) {
                _estado.update {
                    it.copy(
                        ocupado = false,
                        // O servidor devolve a MESMA resposta pros três casos
                        // (não existe / sem senha / senha errada), de propósito:
                        // distinguir entregaria a lista de usuários válidos. A
                        // tela não inventa uma distinção que o servidor recusou.
                        erro = if (e.code() == 401) {
                            "usuário ou senha não conferem"
                        } else {
                            "o servidor respondeu ${e.code()}"
                        },
                    )
                }
            } catch (e: IOException) {
                _estado.update { it.copy(ocupado = false, erro = "sem resposta do servidor") }
            }
        }
    }
}
