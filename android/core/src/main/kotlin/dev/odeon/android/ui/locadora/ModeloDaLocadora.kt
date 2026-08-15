package dev.odeon.android.ui.locadora

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.CaixaExposta
import dev.odeon.android.dados.Emprestada
import dev.odeon.android.dados.Fita
import dev.odeon.android.dados.Loja
import dev.odeon.android.dados.Prateleira
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class EstadoDaLocadora(
    val carregando: Boolean = true,
    val prateleira: Prateleira? = null,
    /// A vitrine — as estantes com as caixas expostas.
    ///
    /// Separada da `prateleira` porque são **duas rotas e duas coisas**: a
    /// prateleira é o que saiu da estante, a loja é o que está nela. Uma pode
    /// falhar sem a outra: ver `RepositorioOdeon.estantes`.
    val loja: Loja? = null,
    val erro: String? = null,
    /// O empréstimo que está sendo devolvido agora, pra o botão não aceitar
    /// dois toques. Devolver duas vezes é mexer no acervo de alguém duas vezes.
    val devolvendo: Int? = null,
    /// A caixa que está **na mão** — o palco. `null` é a loja normal.
    val naMao: CaixaExposta? = null,
    /// Onde a fita dessa caixa parou. `null` enquanto a rota não respondeu, e a
    /// mídia sai como disco até saber — que é o caso da maior parte do acervo.
    val fita: Fita? = null,
    val rebobinando: Boolean = false,
    /// O empréstimo que está sendo pedido de volta agora, pra o botão não
    /// aceitar dois toques — dois pedidos são dois recados pra mesma pessoa.
    val pedindo: Int? = null,
    /// O recado ao vivo do barramento — `fulano pegou X`. Some sozinho em 6s,
    /// como na web (§6): é notícia, não estado.
    val recado: String? = null,
    /// O menu do disco aberto. `null` é a caixa fechada ou a fita, que não tem
    /// menu — «a fita não tem menu, tem rebobinar».
    val menu: dev.odeon.android.dados.MenuDoDisco? = null,
    val cenas: List<dev.odeon.android.dados.Cena> = emptyList(),
    /// A obra da caixa que está na mão — é ela que enche **o verso**: sinopse,
    /// cena, ficha técnica e o arquivo que vai tocar.
    ///
    /// `null` enquanto não chegou, e aí o verso mostra o que a caixa já sabia (o
    /// título) em vez de um retângulo vazio.
    val obraNaMao: dev.odeon.android.dados.ObraDetalhada? = null,
) {
    /// As minhas, separadas das dos outros.
    ///
    /// A prateleira mistura tudo de propósito — quem te barra pode ser qualquer
    /// morador, e ver isso é parte da ideia. Mas a tela precisa da separação:
    /// nas minhas dá pra devolver, nas dos outros só dá pra pedir.
    /// Esta obra é fita ou disco?
    ///
    /// O corte vem do servidor (`ultimo_ano_vhs`) e **não é uma constante
    /// daqui**: é o mesmo número que decide se a caixa rebobina, e tê-lo em dois
    /// lugares é como os dois passariam a discordar.
    ///
    /// Sem ano, é disco. É o §18: na dúvida, o app não afirma que uma obra é de
    /// uma era que ele não sabe qual é — e disco é o caso mais comum do acervo.
    fun ehVhs(ano: Int?): Boolean {
        val corte = prateleira?.ultimoAnoVhs ?: return false
        return corte > 0 && ano != null && ano <= corte
    }

    val minhas: List<Emprestada> get() = prateleira?.emprestadas.orEmpty().filter { it.meu }
    val dosOutros: List<Emprestada> get() = prateleira?.emprestadas.orEmpty().filterNot { it.meu }

    /// **A vitrine menos o que está alugado** — e o vão que a caixa deixou fica
    /// aberto.
    ///
    /// ## O buraco é a escassez sendo vista antes de ser lida
    ///
    /// Até aqui esta tela desenhava `loja.estantes` cru: a caixa que alguém
    /// levou continuava de pé na prateleira, com a arte inteira, indistinguível
    /// de uma que dá pra pegar. O comentário da web (`Locadora.tsx:302`) é o que
    /// manda aqui, e ele explica o que se perde:
    ///
    /// > «se alguém aluga, a caixa some da prateleira e volta quando devolve» —
    /// > e **o buraco não é preenchido**: «puxar uma caixa nova do acervo pra
    /// > tapar o vão faria a loja ter 40 sempre, e aí levar uma fita não custaria
    /// > nada a ninguém».
    ///
    /// Uma loja não deixa a caixa vazia exposta. A fileira fica mais curta, e é
    /// a fileira mais curta que faz a escassez ser **vista** — a linha da porta
    /// da loja só a nomeia depois.
    ///
    /// ## Some quando **tranca** ou quando é **sua**, e só nesses dois casos
    ///
    /// São os dois em que a caixa não está ao seu alcance. Com a escassez
    /// desligada o empréstimo de outra pessoa **não** é exclusivo, e a caixa
    /// continua exposta de propósito: sumir com ela seria encenar uma disputa
    /// que a opção do servidor acabou de desligar.
    ///
    /// ⚠️ É o primeiro uso de `Emprestada.exclusivo` no app. O campo estava
    /// modelado e nunca lido — o mesmo padrão do §8 do `PARIDADE-ANDROID.md`,
    /// desta vez num campo que não desenhava nada e sim **escondia**.
    ///
    /// Estante que ficou sem nada não vira placa (§24) — a mesma regra que o
    /// servidor já aplica ao sortear.
    val expostas: List<dev.odeon.android.dados.EstanteExposta>
        get() {
            val estantes = loja?.estantes ?: return emptyList()
            val fora = prateleira?.emprestadas.orEmpty().associateBy { it.caixaId }
            return estantes
                .map { estante ->
                    estante.copy(
                        caixas = estante.caixas.filterNot { caixa ->
                            val emprestimo = fora[caixa.id]
                            emprestimo != null && (emprestimo.exclusivo || emprestimo.meu)
                        },
                    )
                }
                .filter { it.caixas.isNotEmpty() }
        }

    /// Quantas caixas estão **à vista agora**, depois do buraco.
    val naPrateleira: Int get() = expostas.sumOf { it.caixas.size }

    /// Quantas a semana **sorteou**, antes de alguém levar alguma.
    ///
    /// A diferença pra [naPrateleira] é o buraco, e é ela que a porta da loja
    /// conta. Sai das estantes cruas de propósito: se saísse das expostas, as
    /// duas seriam sempre iguais e o `N fora` nunca nasceria.
    val sorteadas: Int get() = loja?.estantes.orEmpty().sumOf { it.caixas.size }
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
class ModeloDaLocadora(
    private val odeon: RepositorioOdeon,
    /// O barramento, quando há. `null` nos testes e em qualquer montagem que não
    /// queira uma conexão — a tela funciona sem ele, só não fica ao vivo.
    private val barramento: dev.odeon.android.dados.Barramento? = null,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaLocadora())
    val estado: StateFlow<EstadoDaLocadora> = _estado.asStateFlow()

    init {
        carregar()
    }

    fun carregar() {
        viewModelScope.launch {
            _estado.update { it.copy(carregando = true, erro = null) }
            try {
                /// As duas em paralelo: elas não dependem uma da outra, e em
                /// série a tela esperaria a soma das duas viagens pela tailnet.
                val prateleira = async { odeon.prateleira() }
                val loja = async { odeon.estantes() }
                _estado.update {
                    it.copy(
                        carregando = false,
                        prateleira = prateleira.await(),
                        loja = loja.await(),
                    )
                }
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
    /// Tira a caixa da estante e põe no palco.
    ///
    /// ⚠️ **Não empresta nada.** Pegar na mão é olhar o objeto — girar, abrir,
    /// ver o que tem dentro. O empréstimo continua sendo um `POST` explícito, na
    /// ficha, e é o §11 em código: nenhum gesto de exploração pode escrever no
    /// acervo de três pessoas.
    ///
    /// A fita é perguntada **junto**, e não quando a caixa abre: quem abre quer
    /// ver a mídia na hora, e uma viagem de rede no meio da animação faria o
    /// disco aparecer depois da tampa.
    fun pegarNaMao(caixa: CaixaExposta) {
        pegarNaMaoInterno(caixa)
    }

    /// A fita que **já é sua** também vai pra mão — 07/08/2026.
    ///
    /// > «Os filmes que estão comigo eu não consigo iniciar pela locadora — ao
    /// > clicar só mostra a opção de devolver e nem abre o 3D.»
    ///
    /// A `Emprestada` carrega tudo que o palco precisa (id da caixa, título,
    /// arte, cor, ano), então tocar numa fita do «comigo» abre o mesmo palco da
    /// vitrine — e de lá o caminho pro filme já existe: fita rebobinada toca,
    /// disco abre o menu. Devolver continua sendo o arrasto pra baixo no cartão.
    fun pegarNaMao(fita: Emprestada) {
        pegarNaMaoInterno(
            CaixaExposta(
                id = fita.caixaId,
                serie = fita.serie,
                titulo = fita.titulo,
                ano = fita.ano,
                poster = fita.poster,
                corDominante = fita.corDominante,
            ),
        )
    }

    private fun pegarNaMaoInterno(caixa: CaixaExposta) {
        _estado.update { it.copy(naMao = caixa, fita = null, obraNaMao = null) }
        /// A obra inteira, pro verso. Em paralelo com a fita: são duas rotas
        /// independentes, e a caixa já pode ser girada enquanto elas chegam.
        viewModelScope.launch {
            val obra = runCatching { odeon.obra(caixa.id) }.getOrNull()
            _estado.update { if (it.naMao?.id == caixa.id) it.copy(obraNaMao = obra) else it }
        }
        viewModelScope.launch {
            val fita = odeon.fita(caixa.id)
            /// Só aplica se a caixa ainda for a mesma: quem fechou e abriu outra
            /// no meio do caminho não pode receber a fita da anterior.
            _estado.update { if (it.naMao?.id == caixa.id) it.copy(fita = fita) else it }
        }
    }

    fun guardar() = _estado.update {
        it.copy(naMao = null, fita = null, obraNaMao = null, rebobinando = false)
    }

    /// Rebobinar. **Escreve**, e é o único gesto de escrita que o palco tem.
    ///
    /// A animação da tela e a chamada correm juntas de propósito: o servidor
    /// responde em milissegundos e a fita leva segundos, então esperar a resposta
    /// pra começar a girar deixaria o carretel parado justamente no tempo em que
    /// a pessoa está olhando pra ele. Se a chamada falhar, a fita volta a estar
    /// andada na próxima abertura — que é a verdade, e aparece sozinha.
    fun rebobinar() {
        val caixa = _estado.value.naMao ?: return
        _estado.update { it.copy(rebobinando = true) }
        viewModelScope.launch {
            runCatching { odeon.rebobinar(caixa.id) }
            _estado.update {
                it.copy(rebobinando = false, fita = it.fita?.copy(posicaoEmSegundos = 0.0))
            }
        }
    }

    /// Pedir de volta uma fita que está com outra pessoa.
    ///
    /// ⚠️ **Não encurta o prazo de ninguém** (§6 da referência) — dar a um
    /// morador poder sobre o prazo do outro transformaria a locadora em disputa.
    /// O que ela faz é pôr um recado na caixa de quem está com ela, e é por isso
    /// que não pede confirmação: o efeito é um aviso, não uma perda.
    fun pedirDeVolta(emprestimoId: Int) {
        _estado.update { it.copy(pedindo = emprestimoId) }
        viewModelScope.launch {
            runCatching { odeon.pedirDeVolta(emprestimoId) }
            _estado.update { it.copy(pedindo = null) }
            carregar()
        }
    }

    /// O barramento em ação — a única tela do app que fica **ao vivo**.
    ///
    /// ## Por que aqui, e por que só aqui
    ///
    /// A locadora é a tela cujo conteúdo **outra pessoa muda**: alguém pega uma
    /// caixa e ela sai da estante de todo mundo. Sem o barramento, a prateleira
    /// só conta a verdade de quando foi aberta — e o §6 da referência chama o
    /// recado de parte da loja, não de enfeite.
    ///
    /// As outras telas mudam por conta de quem está olhando; esta muda sozinha.
    ///
    /// ⚠️ O recado some em **6 segundos**, o número da web. Ele é notícia: uma
    /// linha que fica pra sempre vira estado, e um estado que diz «fulano pegou
    /// X» é falso dez minutos depois.
    init {
        ouvirOBarramento()
    }

    private fun ouvirOBarramento() {
        val barramento = barramento ?: return
        viewModelScope.launch {
            barramento.eventos.collect { evento ->
                if (evento !is dev.odeon.android.dados.EventoDoServidor.NaLocadora) return@collect
                /// Recarrega **sempre** — a prateleira mudou, tenha ou não frase
                /// pra mostrar. O recado é o aviso; a recarga é o conteúdo.
                carregar()
                val recado = evento.recado ?: return@collect
                _estado.update { it.copy(recado = recado) }
                delay(6_000)
                _estado.update { if (it.recado == recado) it.copy(recado = null) else it }
            }
        }
    }

    /// Abre o menu do disco.
    ///
    /// ## As duas rotas em paralelo, e falhando separado
    ///
    /// O menu abre com o que a primeira devolve; as cenas chegam depois e caem
    /// na grade. É o arranjo do guia e da locadora, pelo mesmo motivo: são doze
    /// imagens, e esperar por elas pra abrir o menu faria o disco demorar mais
    /// que a caixa.
    ///
    /// ⚠️ **Sem menu, não há menu** — e quem chamou trata isso abrindo a ficha,
    /// que é o caminho que sempre existiu. Um menu vazio seria pior que nenhum.
    fun abrirOMenu(obraId: String, seNaoDer: () -> Unit) {
        viewModelScope.launch {
            val menu = odeon.menuDoDisco(obraId)
            if (menu == null) {
                seNaoDer()
                return@launch
            }
            _estado.update { it.copy(menu = menu) }
            val cenas = odeon.cenasDoDisco(obraId)
            _estado.update { if (it.menu?.obraId == obraId) it.copy(cenas = cenas) else it }
        }
    }

    fun fecharOMenu() = _estado.update { it.copy(menu = null, cenas = emptyList()) }

}
