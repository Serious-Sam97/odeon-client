package dev.odeon.android.ui.obra

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.ArquivoDeMidia
import dev.odeon.android.dados.ObraDetalhada
import dev.odeon.android.dados.PlanoDeReproducao
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDaObra(
    val carregando: Boolean = true,
    val obra: ObraDetalhada? = null,
    val arquivo: ArquivoDeMidia? = null,
    val plano: PlanoDeReproducao? = null,
    val planoCarregando: Boolean = false,
    val erro: String? = null,
    /// O que aconteceu na locadora agora — "pegando…", ou o resultado.
    /// `null` quando não há nada a dizer.
    val recadoDaLocadora: String? = null,
    val pegando: Boolean = false,
) {
    /// Só há o que tocar se houver arquivo. Uma obra identificada sem arquivo
    /// existe no acervo — é linha de catálogo sem mídia — e não toca.
    val temComoTocar: Boolean get() = arquivo != null
}

/// A ficha da obra.
///
/// ## Ela NÃO pergunta a locadora, e o servidor mudou pra isso valer
///
/// Vale contar inteiro, porque a ordem dos fatos é a lição.
///
/// Esta tela **nasceu** perguntando `/api/locadora/liberadas` antes de desenhar
/// o play, que era o que a §10 do `CONTINUAR-ANDROID.md` e a §6 da espec
/// mandavam — "inclusive pro admin". Rodando em 04/08/2026 o efeito apareceu:
/// `exige` voltou `true`, `sam` não tinha empréstimo de nada, e o play sumiu do
/// acervo inteiro.
///
/// A guarda saiu por decisão do dono, e aí o **servidor** negou: `GET
/// /api/playback/{arquivo}/plan` devolveu **403**, num `GET` que nem chega a
/// servir byte. Ou seja, o documento descrevia a máquina corretamente, e tirar a
/// checagem do cliente só trocou "play escondido" por "play que leva a 403" —
/// o §53 ao contrário.
///
/// O conserto foi do lado de lá: a exigência passou a ser **regra da locadora**,
/// e a biblioteca virou modo livre. Está no §71 do `DESIGN.md`. Medido depois da
/// mudança, mesma obra e mesma conta, sem empréstimo e com a escassez ligada:
/// antes `false`, agora `true`.
///
/// Por isso a pergunta some daqui inteira, e não vira "pergunta e ignora": uma
/// requisição por ficha aberta pra um dado que nenhuma linha desta tela lê é
/// rede gasta em silêncio. `RepositorioOdeon.liberadas()` continua existindo e
/// continua verdadeiro — ele é da **fase 5**, onde a regra passou a morar.
///
/// ## Ela pergunta duas coisas, em fila curta
///
/// A obra primeiro; o plano depende do arquivo, que vem dentro dela.
class ModeloDaObra(
    private val odeon: RepositorioOdeon,
    private val obraId: String,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaObra())
    val estado: StateFlow<EstadoDaObra> = _estado.asStateFlow()

    init {
        carregar()
    }

    private fun carregar() {
        viewModelScope.launch {
            _estado.update { it.copy(carregando = true, erro = null) }
            try {
                val obra = odeon.obra(obraId)

                /// O primeiro arquivo é o escolhido de partida, e os outros
                /// ficam à mão na tela. Não há critério melhor aqui: a ordem é a
                /// do servidor, e inventar uma preferência ("o maior", "o de
                /// mais altura") seria o app escolhendo dublagem por tamanho de
                /// arquivo.
                val arquivo = obra.files.firstOrNull()

                _estado.update {
                    it.copy(carregando = false, obra = obra, arquivo = arquivo)
                }

                if (arquivo != null) pedirPlano(arquivo)
            } catch (e: Exception) {
                _estado.update {
                    it.copy(carregando = false, erro = e.message ?: "não deu pra abrir a ficha")
                }
            }
        }
    }

    fun escolherArquivo(arquivo: ArquivoDeMidia) {
        if (_estado.value.arquivo?.id == arquivo.id) return
        _estado.update { it.copy(arquivo = arquivo, plano = null) }
        pedirPlano(arquivo)
    }

    /// O plano é o selo, e ele é pedido **antes** de tocar de propósito.
    ///
    /// A decisão foi tomada: o selo aparece na ficha e no player. Na ficha ele
    /// responde "vai transcodificar?" antes de a pessoa gastar o toque — e num
    /// servidor que atende três pessoas da mesma casa, saber disso antes vale.
    ///
    /// Falhar aqui **não** derruba a ficha: sem plano o selo some (§24, linha
    /// vazia some), e o play continua de pé — quem decide de verdade como tocar
    /// é a tela do player, que pergunta de novo.
    private fun pedirPlano(arquivo: ArquivoDeMidia) {
        viewModelScope.launch {
            _estado.update { it.copy(planoCarregando = true) }
            val plano = runCatching { odeon.plano(arquivo.id) }.getOrNull()
            _estado.update {
                /// A corrida: se alguém trocou de versão enquanto este plano
                /// voava, ele já não é o plano da versão escolhida. Descartar é
                /// o certo — mostrar o selo do outro arquivo seria mentir com
                /// cara de metadado (§18).
                if (it.arquivo?.id != arquivo.id) it.copy(planoCarregando = false)
                else it.copy(plano = plano, planoCarregando = false)
            }
        }
    }

    /// A URL do pôster, com o token de mídia pendurado. Igual à da grade — o
    /// mesmo `RepositorioOdeon.urlDoPoster`, pra não haver duas montagens da
    /// mesma URL divergindo no dia em que o token mudar de nome.
    fun capa(caminho: String?): String? = odeon.urlDoPoster(caminho)

    /// Pegar a fita desta obra. **Escreve no acervo.**
    ///
    /// ## Ela não tenta de novo sozinha, e o botão tranca
    ///
    /// Um `POST` que cria empréstimo com retentativa automática é como duas
    /// fitas saem da estante por um toque só. O `pegando` fecha o botão
    /// enquanto a requisição está no ar, e falha vira frase — não segunda
    /// tentativa.
    ///
    /// O retorno traz o prazo em dias, e ele é dito na tela: quem pega precisa
    /// saber até quando, e é o mesmo número que viaja com o download quando a
    /// locadora ganhar o botão de baixar (§4).
    fun pegarAFita() {
        if (_estado.value.pegando) return
        _estado.update { it.copy(pegando = true, recadoDaLocadora = null) }
        viewModelScope.launch {
            val recado = runCatching { odeon.alugar(obraId) }.fold(
                onSuccess = { "fita pega — vence em ${it.venceEmDias} dias" },
                onFailure = { it.message ?: "não deu pra pegar a fita" },
            )
            _estado.update { it.copy(pegando = false, recadoDaLocadora = recado) }
        }
    }

    fun tentarDeNovo() = carregar()

    /// Relê a ficha **só se ela já tinha sido lida**.
    ///
    /// ## Por que ela precisa reler ao voltar do filme
    ///
    /// O `ViewModel` fica em cache pela `key` da obra, então voltar do player cai
    /// na mesma instância — com o `position_seconds` de antes de assistir. Visto
    /// rodando: assistiu 28s, voltou, e o botão continuava dizendo **"assistir"**
    /// em vez de "continuar". A marca tinha subido; a tela é que estava velha.
    ///
    /// A guarda de "já carregou" evita a requisição dobrada na primeira entrada,
    /// onde o `init` já está buscando.
    fun relerSeJaTem() {
        if (_estado.value.obra != null) carregar()
    }
}
