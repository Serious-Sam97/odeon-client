package dev.odeon.android.ui.biblioteca

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.EspacoDeEtiqueta
import dev.odeon.android.dados.EtiquetaDoAcervo
import dev.odeon.android.dados.Filtros
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.dados.ItemPraContinuar
import dev.odeon.android.dados.ObraDaLista
import dev.odeon.android.dados.FolhaDeSprites
import dev.odeon.android.dados.PlanoDeReproducao
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
    /// ## As prateleiras — `filmes` e `séries` — 18/08/2026
    ///
    /// Medido no acervo desta casa: **120 séries** contra ~8.200 filmes na
    /// listagem agrupada. Uma série é **1 cartão em 69** — elas não estão
    /// misturadas, estão afogadas. Ver `docs/SERIES.md §11`.
    ///
    /// ⚠️ Elas vêm do espaço `format` que o **servidor** declara, e não de uma
    /// lista escrita aqui: se ele acrescentar um formato amanhã, ele aparece.
    val prateleiras: List<EtiquetaDoAcervo> = emptyList(),
    /// Quantas **entradas** cada prateleira tem — agrupadas, como a grade mostra.
    ///
    /// ⚠️ Não é a `quantasObras` da etiqueta. A etiqueta `format:série` conta
    /// **8.475 obras** (episódios); a grade mostra **120 entradas**. Pôr 8.475
    /// ao lado de uma grade de 120 é o mesmo erro do `18 de 1` — ver
    /// `PEDIDOS-AO-SERVIDOR.md, «já entregue» 9`. Por isso cada número é **perguntado** ao
    /// servidor, com uma consulta de uma linha só.
    val quantasPorPrateleira: Map<String, Int> = emptyMap(),

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
        /// ⚠️ As etiquetas passaram a ser buscadas **na abertura**, e não só na
        /// primeira vez que alguém abre o painel: sem elas não há prateleira, e
        /// a prateleira é a primeira coisa da tela. Corre em paralelo com a
        /// grade — quem chega vê o acervo sem esperar por isto.
        viewModelScope.launch { carregarPrateleiras() }
    }

    /// As duas prateleiras e quantas entradas cada uma tem.
    private suspend fun carregarPrateleiras() {
        val (etiquetas, espacos) = odeon.etiquetasDoAcervo()
        val formatos = etiquetas
            .filter { it.namespace == ESPACO_DO_FORMATO && it.quantasObras > 0 }
            .sortedByDescending { it.quantasObras }
        if (formatos.isEmpty()) return
        _estado.update { it.copy(prateleiras = formatos, etiquetas = etiquetas, espacos = espacos) }

        /// ## ⚠️ As sondas de contagem **saíram** — 18/08/2026
        ///
        /// Eram quatro consultas de uma linha, uma por prateleira, pra escrever
        /// `série 5143` dentro da pílula. As pílulas viraram abas e os números
        /// saíram delas — mas as consultas ficaram, e o `sóSéries` esperava por
        /// elas antes de filtrar.
        ///
        /// Medido no emulador: **8 segundos** entre abrir a aba das séries e ela
        /// deixar de mostrar o acervo inteiro. Uma aba que mostra a coisa errada
        /// por oito segundos não está carregando — está mentindo, e depois se
        /// corrige.
        ///
        /// O que sobrou aqui é só a lista de formatos, que é o que a aba precisa
        /// pra saber o que excluir. A contagem de cada aba vem do `total` da
        /// própria consulta dela.
    }

    /// A biblioteca **sem** as séries — a aba dos filmes.
    ///
    /// ## ⚠️ Ela **tira**, e é o que o `?tags_not=` comprou · 18/08/2026
    ///
    /// Fixar `format:filme` daria 981 e deixaria de fora as **2.182** entradas
    /// que o scanner não classifica. Tirar as séries dá **3.187** — os filmes
    /// identificados, os clipes, os animes e as não classificadas, que é o que
    /// uma biblioteca de filmes pode prometer honestamente.
    ///
    /// ⚠️ **O anime entra na exclusão, e não é detalhe.** O servidor mediu:
    /// `tags_not=format:série` sozinho deixa passar o `Beyblade` — 43 episódios
    /// que carregam `format:anime` e **não** `format:série`. Uma série de 43
    /// episódios na aba dos filmes é o defeito que esta aba existe pra não ter.
    ///
    /// ⚠️ E agora o `total` fala do **mesmo conjunto** que a grade: o cabeçalho
    /// e a paginação voltam a fechar. O corte na tela sai junto.
    fun semSéries() {
        viewModelScope.launch {
            if (_estado.value.prateleiras.isEmpty()) carregarPrateleiras()
            val fora = _estado.value.prateleiras
                .filter { it.value.startsWith("série") || it.value.startsWith("anime") }
                .map { it.chave }
            if (fora.isEmpty()) return@launch
            _estado.update {
                it.copy(filtros = it.filtros.copy(excluindo = fora), carregando = true)
            }
            trabalhoDaBusca?.cancel()
            trabalhoDaBusca = viewModelScope.launch { buscar(pulando = 0, primeira = true) }
        }
    }

    /// Fixa este modelo na prateleira das **séries**, e é o que faz a aba de
    /// séries existir sem uma segunda cópia da biblioteca.
    ///
    /// ## ⚠️ Ela espera as prateleiras chegarem
    ///
    /// A chave (`format:série`) vem do servidor, e o `carregarPrateleiras` corre
    /// em paralelo com a primeira página. Fixar «a etiqueta cujo valor começa
    /// com série» **antes** dela chegar fixaria em nada — e a aba abriria com o
    /// acervo inteiro por um piscar, que é pior do que abrir vazia.
    fun sóSéries() {
        viewModelScope.launch {
            if (_estado.value.prateleiras.isEmpty()) carregarPrateleiras()
            val serie = _estado.value.prateleiras.firstOrNull { it.value.startsWith("série") }
            if (serie == null) return@launch
            escolherPrateleira(serie.chave)
        }
    }

    /// Trocar de prateleira. `null` é «tudo».
    ///
    /// ⚠️ Zera a **paginação** e a busca, mas mantém os outros filtros: quem
    /// filtrou por «Comédia» e trocou pra séries quer comédias em série.
    fun escolherPrateleira(chave: String?) {
        if (_estado.value.filtros.prateleira == chave) return
        _estado.update {
            it.copy(filtros = it.filtros.copy(prateleira = chave), carregando = true, erro = null)
        }
        trabalhoDaBusca?.cancel()
        trabalhoDaBusca = viewModelScope.launch { buscar(pulando = 0, primeira = true) }
    }

    fun primeiraPagina() {
        _estado.update { it.copy(carregando = true, erro = null) }
        /// ## ⚠️ Ela **entra no `trabalhoDaBusca`** — visto no emulador, 18/08/2026
        ///
        /// Ela lançava uma corrotina solta. Ninguém conseguia cancelá-la, e a
        /// aba das séries mostrou o defeito: `sóSéries()` fixava a prateleira,
        /// disparava a consulta filtrada, e **esta** — que já estava no ar —
        /// chegava depois e sobrescrevia com o acervo inteiro. A tela dizia
        /// «TODAS AS SÉRIES 8333» com uma grade de 007.
        ///
        /// ⚠️ O sintoma é de corrida, então ele **some sozinho** num servidor
        /// rápido e volta num lento. Quem mandou nunca é quem chega por último.
        trabalhoDaBusca?.cancel()
        trabalhoDaBusca = viewModelScope.launch {
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
    /// Abre uma série **vinda de outra tela** — hoje, a caixa da locadora.
    ///
    /// ## ⚠️ Ela existe porque a caixa de série era um beco sem saída
    ///
    /// Medido em 17/08/2026: tocar no disco de «The White Lotus» na locadora dava
    /// 404 no menu, caía pra ficha como previsto, e a **ficha também dava 404** —
    /// sobrando uma tela de erro cujo «tentar de novo» não podia funcionar nunca.
    ///
    /// A causa é de contrato, e é simples de dizer: o id de uma caixa de série
    /// **não é um id de obra**. Ele é um id de *coleção* — funciona em
    /// `/api/works?colecao=…`, que é exatamente o que o [entrarNaSerie] usa. O
    /// destino existia o tempo todo; faltava a locadora saber mandar pra ele.
    ///
    /// ⚠️ **Sem contagem, e tudo bem.** A `CaixaExposta` não traz quantos
    /// episódios a série tem, e o `total` nulo cai na regra que o `buscar` já
    /// tinha escrito: «se a série tinha zero lá, o que se tem agora é o que
    /// chegou». O cabeçalho mostra o título da série e a contagem aparece da
    /// primeira página — em vez de o app afirmar um número que não recebeu.
    fun abrirSerieDeFora(id: String, titulo: String) {
        _estado.update {
            it.copy(
                serie = SerieAberta(id, titulo, 0),
                filtros = it.filtros.copy(colecao = id, busca = ""),
                episodios = emptyList(),
                total = null,
                carregando = true,
                erro = null,
            )
        }
        trabalhoDaBusca?.cancel()
        trabalhoDaBusca = viewModelScope.launch { buscar(pulando = 0, primeira = true) }
    }

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
            /// ⚠️ As frases saíram daqui e viraram `fraseDaFalha`, no `:core` —
            /// esta classificação estava certa e era a **única** do app; o resto
            /// das telas mostrava `e.message`, que sem rede é inglês de DNS.
            ///
            /// ## ⚠️ `CancellationException` **passa reto**, e eu já errei isto aqui
            ///
            /// A primeira versão desta mudança trocou os dois `catch` específicos
            /// por um `catch (e: Exception)` — que também pega o cancelamento. E
            /// esta tela cancela o tempo todo: cada tecla digitada na busca
            /// derruba a requisição anterior.
            ///
            /// O efeito é pior que um erro à toa: o cancelamento virava `erro`, e
            /// `erro != null` desliga o `vazioComFiltro` — ou seja, a frase «nada
            /// com «x» no acervo» **parava de aparecer**, e uma busca sem
            /// resultado voltava a ser uma grade em branco sem explicação, que é
            /// exatamente o defeito que aquela frase existe pra não ter.
            ///
            /// Cancelamento não é falha: é a corrotina sendo desfeita de
            /// propósito, e engoli-lo quebra a concorrência estruturada inteira.
            /// Ele sobe.
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (e: Exception) {
            falhou(dev.odeon.android.dados.fraseDaFalha(e, "a biblioteca não abriu"))
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

    /// A folha de sprites de um arquivo — os quadros que o rolo do player usa.
    ///
    /// ⚠️ Ela existe pro preview de seek, e é por isso que serve aqui: são
    /// **quadros do próprio filme**, já gerados, já servidos, já cacheados. Um
    /// herói que troca de cena não precisou de nada novo do servidor.
    suspend fun folha(arquivoId: String): FolhaDeSprites? = odeon.folhaDeSprites(arquivoId)

    /// A URL da folha, absoluta e com token — a mesma `urlDeMidia` de sempre.
    fun urlDaFolha(caminho: String): String? = odeon.urlDeMidia("/scrub/$caminho")

    /// O plano de reprodução de um arquivo — só pra saber se ele **toca direto**.
    ///
    /// ⚠️ A prévia do herói usa isto como **porteiro**, não como negociação: se o
    /// filme precisar de transcodificação, não há prévia. Abrir uma sessão de
    /// `ffmpeg` no servidor pra enfeitar um fundo é caro do lado errado — a
    /// máquina da casa passaria a trabalhar porque alguém está *olhando* a
    /// biblioteca, sem ter pedido nada.
    suspend fun planoDoArquivo(arquivoId: String): PlanoDeReproducao? =
        runCatching { odeon.plano(arquivoId) }.getOrNull()

    /// A URL de mídia, absoluta e com token.
    fun urlDeMidia(caminho: String?): String? = odeon.urlDeMidia(caminho)

    /// Ver `RepositorioOdeon.cabecalhosDeMidia`. **O `?token=` da URL não basta
    /// pro ExoPlayer** — foi 401 nos canais ao vivo e foi 401 aqui, pelo mesmo
    /// motivo e com uma semana de diferença.
    fun cabecalhosDeMidia(): Map<String, String> = odeon.cabecalhosDeMidia()

    private companion object {
        /// O mesmo número da web (§1.6). Ele é curto o bastante pra a busca
        /// parecer imediata e longo o bastante pra uma palavra inteira valer uma
        /// consulta, e não onze.
        const val ESPERA_DA_BUSCA = 250L

        /// O espaço de etiqueta que separa filme de série.
        ///
        /// ⚠️ O nome vem do servidor (`/api/tag-namespaces`), e é o mesmo que o
        /// painel de filtros já rotula como «FORMATO» — ver a tabela de rótulos
        /// em `Modelos.kt`. A constante existe pra a prateleira e o painel não
        /// discordarem sobre o que é formato.
        const val ESPACO_DO_FORMATO = "format"
    }
}
