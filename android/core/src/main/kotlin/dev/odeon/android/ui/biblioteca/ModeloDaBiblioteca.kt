package dev.odeon.android.ui.biblioteca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.EspacoDeEtiqueta
import dev.odeon.android.dados.EtiquetaDoAcervo
import dev.odeon.android.dados.Filtros
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.dados.ItemPraContinuar
import dev.odeon.android.dados.ObraDaLista
import dev.odeon.android.dados.RepositorioOdeon
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import retrofit2.HttpException
import java.io.IOException

/// A série em que se entrou.
///
/// Ela é guardada inteira, e não só o id, por causa de **duas** coisas que a
/// grade já tinha na mão e a listagem plana não devolve: o título, pra escrever
/// o chip «Dentro de», e o `work_count`, que é o denominador de «12 de 62».
/// Sem isto, entrar numa série custaria uma segunda consulta pra saber o nome
/// do lugar onde já se está.
data class SerieAberta(
    val id: String,
    val titulo: String,
    val quantosEpisodios: Int,
)

data class EstadoDaBiblioteca(
    val itens: List<ItemDaBiblioteca> = emptyList(),
    /// Os episódios, quando se está **dentro** de uma série. Fora dela, vazia —
    /// e a tela desenha `itens`.
    val episodios: List<ObraDaLista> = emptyList(),
    val serie: SerieAberta? = null,
    /// A fileira de "continuar de onde parou". Vazia é o estado normal de quem
    /// não começou nada — e aí ela **não aparece**, em vez de aparecer vazia
    /// com um título por cima (§24).
    val paraContinuar: List<ItemPraContinuar> = emptyList(),
    /// O total do filtro atual, que vem repetido em toda linha do servidor.
    ///
    /// `null` enquanto nada chegou — e a tela **não escreve "0"** nesse caso.
    /// Zero é uma afirmação ("não há nada"), e o app ainda não sabe disso.
    val total: Int? = null,
    val filtros: Filtros = Filtros(),
    /// O catálogo de etiquetas, buscado na primeira abertura do painel.
    val etiquetas: List<EtiquetaDoAcervo> = emptyList(),
    val espacos: List<EspacoDeEtiqueta> = emptyList(),
    val painelAberto: Boolean = false,
    val carregando: Boolean = false,
    val carregandoMais: Boolean = false,
    val erro: String? = null,
) {
    val dentroDaSerie: Boolean get() = serie != null

    val quantosNaTela: Int get() = if (dentroDaSerie) episodios.size else itens.size

    val temMais: Boolean get() = total != null && quantosNaTela < total

    /// Filtrou (ou buscou) e não veio nada. É diferente de "a biblioteca está
    /// vazia", e a tela precisa saber qual das duas pra escrever a frase certa.
    val vazioComFiltro: Boolean
        get() = !carregando && quantosNaTela == 0 && erro == null &&
            (filtros.busca.isNotBlank() || filtros.algumLigado)

    /// As etiquetas agrupadas pelo espaço delas, na ordem do servidor.
    ///
    /// ⚠️ Espaço sem etiqueta **não vira grupo vazio** (§24), e etiqueta de um
    /// espaço que o servidor não declarou também não some: ela cai num grupo com
    /// o próprio `namespace` de rótulo. Descartá-la seria o app decidir que uma
    /// tag do acervo não existe porque a tabela de rótulos está incompleta.
    val etiquetasPorEspaco: List<Pair<EspacoDeEtiqueta, List<EtiquetaDoAcervo>>>
        get() {
            val porEspaco = etiquetas.filter { it.quantasObras > 0 }.groupBy { it.namespace }
            val declarados = espacos.sortedBy { it.position }
            val faltando = porEspaco.keys - declarados.map { it.namespace }.toSet()
            val todos = declarados + faltando.sorted().map { EspacoDeEtiqueta(it, it) }
            return todos.mapNotNull { espaco ->
                porEspaco[espaco.namespace]
                    ?.sortedByDescending { it.quantasObras }
                    ?.let { espaco to it }
            }
        }
}

class ModeloDaBiblioteca(private val odeon: RepositorioOdeon) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaBiblioteca())
    val estado: StateFlow<EstadoDaBiblioteca> = _estado.asStateFlow()

    /// O trabalho em curso da busca, pra cancelar quando vier outra letra.
    private var trabalhoDaBusca: Job? = null

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
        recarregarParaContinuar()
    }

    /// A fileira de "continuar", numa corrotina separada da grade.
    ///
    /// Separada de propósito: as duas não dependem uma da outra, e a grade é o
    /// que a pessoa veio ver. Esperar a fileira pra desenhar o acervo somaria
    /// dois tempos de rede na primeira tela do app.
    fun recarregarParaContinuar() {
        viewModelScope.launch {
            val fileira = odeon.paraContinuar()
            _estado.update { it.copy(paraContinuar = fileira) }
        }
    }

    /// O que se digitou na busca.
    ///
    /// ## Os 250ms, e o que eles evitam
    ///
    /// A web usa o mesmo número (§1.6: «`filters` dispara `refresh` com debounce
    /// de 250ms»), e o motivo aqui é maior: o servidor de casa atende três
    /// pessoas e ainda roda o Postgres, o ffmpeg e a identificação. Digitar
    /// «goldfinger» sem espera são **onze** consultas sobre 17.930 obras pra
    /// mostrar o resultado de uma.
    ///
    /// ## ⚠️ A grade **não** é apagada enquanto a nova resposta não chega
    ///
    /// Nada de `carregando = true` aqui. Trocar a grade por um rodinho a cada
    /// letra faria a tela piscar onze vezes na mesma palavra — e o que estava na
    /// tela continua sendo verdade sobre a busca anterior até a próxima chegar.
    /// É a mesma escolha do `recarregarParaContinuar`: quem já tem o que mostrar
    /// mostra, e troca quando tiver melhor.
    ///
    /// O `cancel` do trabalho anterior é o que garante a ordem: sem ele, duas
    /// respostas em voo poderiam chegar trocadas e a grade terminaria mostrando
    /// o resultado de «goldfing» com «goldfinger» escrito no campo.
    fun mudouBusca(texto: String) {
        if (_estado.value.filtros.busca == texto) return
        _estado.update { it.copy(filtros = it.filtros.copy(busca = texto)) }
        depoisDaEspera()
    }

    /// Troca o filtro e relê **na hora**.
    ///
    /// Sem espera, ao contrário da busca: tocar num chip é um gesto inteiro, e
    /// não uma letra no meio de uma palavra. Esperar 250ms depois de um toque é
    /// a tela parecendo lenta sem economizar consulta nenhuma.
    fun mudouFiltros(novos: Filtros) {
        if (_estado.value.filtros == novos) return
        _estado.update { it.copy(filtros = novos, carregando = true, erro = null) }
        trabalhoDaBusca?.cancel()
        trabalhoDaBusca = viewModelScope.launch { buscar(pulando = 0, primeira = true) }
    }

    private fun depoisDaEspera() {
        trabalhoDaBusca?.cancel()
        trabalhoDaBusca = viewModelScope.launch {
            delay(ESPERA_DA_BUSCA)
            buscar(pulando = 0, primeira = true)
        }
    }

    /// Abre ou fecha o painel de filtros, e busca o catálogo **na primeira vez**.
    ///
    /// As etiquetas são centenas de linhas e mudam quando a identificação roda —
    /// ou seja, quase nunca, do ponto de vista de quem está olhando a tela.
    /// Pedi-las no arranque atrasaria a grade por um painel que talvez ninguém
    /// abra; pedi-las a cada abertura seria repetir a mesma resposta.
    fun alternarPainel() {
        val abrindo = !_estado.value.painelAberto
        _estado.update { it.copy(painelAberto = abrindo) }

        if (abrindo && _estado.value.etiquetas.isEmpty()) {
            viewModelScope.launch {
                val (etiquetas, espacos) = odeon.etiquetasDoAcervo()
                _estado.update { it.copy(etiquetas = etiquetas, espacos = espacos) }
            }
        }
    }

    /// Entra na série — e a fonte da lista muda com ela.
    ///
    /// ## Por que isto não é "abrir a ficha da série"
    ///
    /// A ficha responde «o que é esta obra». Aqui a pergunta é «quais
    /// episódios», e a resposta é uma lista — que é o que a web faz desde
    /// sempre: o cartão de série **vira filtro de coleção**, e não outra tela.
    ///
    /// ⚠️ A busca é zerada ao entrar. Quem procurou «breaking» e tocou na série
    /// não quer os episódios cujo título contenha «breaking» — quer os
    /// episódios. Manter o texto filtraria a série por dentro em silêncio, e o
    /// resultado (dois episódios de 62) pareceria uma série incompleta.
    fun entrarNaSerie(item: ItemDaBiblioteca) {
        _estado.update {
            it.copy(
                serie = SerieAberta(item.id, item.title, item.quantasObras),
                filtros = it.filtros.copy(colecao = item.id, busca = ""),
                episodios = emptyList(),
                total = item.quantasObras.takeIf { n -> n > 0 },
                carregando = true,
                erro = null,
            )
        }
        trabalhoDaBusca?.cancel()
        trabalhoDaBusca = viewModelScope.launch { buscar(pulando = 0, primeira = true) }
    }

    /// Sai da série, voltando pro acervo agrupado.
    fun sairDaSerie() {
        if (_estado.value.serie == null) return
        _estado.update {
            it.copy(
                serie = null,
                filtros = it.filtros.copy(colecao = null),
                episodios = emptyList(),
                total = null,
                carregando = true,
                erro = null,
            )
        }
        trabalhoDaBusca?.cancel()
        trabalhoDaBusca = viewModelScope.launch { buscar(pulando = 0, primeira = true) }
    }

    /// Carrega a próxima página. Ignorado quando já está carregando ou acabou.
    fun maisUmaPagina() {
        val agora = _estado.value
        if (agora.carregando || agora.carregandoMais || !agora.temMais) return

        _estado.update { it.copy(carregandoMais = true) }
        viewModelScope.launch { buscar(pulando = agora.quantosNaTela, primeira = false) }
    }

    private suspend fun buscar(pulando: Int, primeira: Boolean) {
        val filtros = _estado.value.filtros
        try {
            if (filtros.colecao != null) {
                val pagina = odeon.obras(pulando = pulando, filtros = filtros)
                _estado.update { antes ->
                    antes.copy(
                        episodios = if (primeira) pagina else antes.episodios + pagina,
                        /// ⚠️ O total **não** sai desta resposta: `/api/works`
                        /// não devolve `count(*) OVER ()`. Ele veio do
                        /// `work_count` da série, no `entrarNaSerie` — e se a
                        /// série tinha zero lá, o que se tem agora é o que
                        /// chegou.
                        total = antes.total ?: (pulando + pagina.size),
                        carregando = false,
                        carregandoMais = false,
                        erro = null,
                    )
                }
                return
            }

            /// A busca e o filtro vão junto **em toda página**, e não só na
            /// primeira: sem eles, rolar até o fim de «bond» pediria a página 2
            /// do acervo inteiro e emendaria 60 filmes quaisquer embaixo do
            /// resultado.
            val pagina = odeon.biblioteca(pulando = pulando, filtros = filtros)
            _estado.update { antes ->
                antes.copy(
                    itens = if (primeira) pagina else antes.itens + pagina,
                    // O total vem de toda linha; uma página vazia não traz
                    // nenhuma, e aí o que vale é o tamanho do que já se tem.
                    total = pagina.firstOrNull()?.total
                        ?: (if (primeira) 0 else antes.total ?: 0),
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

    /// A arte do episódio — `still` primeiro. Ver `ObraDaLista.arte`.
    fun arte(item: ObraDaLista): String? = odeon.urlDaArte(item.arte)

    /// A arte da fileira de continuar — `still`, `backdrop` ou `poster`, nessa
    /// ordem. Ver `ItemPraContinuar.arte`.
    fun arte(item: ItemPraContinuar): String? = odeon.urlDaArte(item.arte)

    private companion object {
        /// O mesmo número da web (§1.6). Ele é curto o bastante pra a busca
        /// parecer imediata e longo o bastante pra uma palavra inteira valer uma
        /// consulta, e não onze.
        const val ESPERA_DA_BUSCA = 250L
    }
}
