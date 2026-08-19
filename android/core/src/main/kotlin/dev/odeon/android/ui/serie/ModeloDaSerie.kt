package dev.odeon.android.ui.serie

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dev.odeon.android.dados.ColecaoComFilhos
import dev.odeon.android.dados.Filtros
import dev.odeon.android.dados.ObraDaLista
import dev.odeon.android.dados.RepositorioOdeon
import dev.odeon.android.dados.fraseDaFalha
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/// Uma temporada, montada aqui.
///
/// ## ⚠️ Ela **não existe no servidor** — 18/08/2026
///
/// Não há rota de temporada nem entidade de temporada: o que existe é o
/// `season_number` em cada obra e o `season_count` na série. Tudo abaixo é
/// agrupamento feito no cliente, e é por isso que [arte] tem reserva.
///
/// O pedido de temporadas com pôster próprio do TMDB está no
/// `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`. Quando ele chegar, [arte] passa a vir de lá e
/// **só esta linha muda** — a tela não sabe de onde a imagem veio.
data class TemporadaDaSerie(
    val numero: Int,
    val episodios: List<ObraDaLista>,
    /// O pôster da temporada.
    ///
    /// ## ⚠️ Desde 18/08/2026 ele vem **do servidor** — 461 de 473
    ///
    /// O `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10` foi atendido: as temporadas ganharam pôster
    /// próprio do TMDB, e `GET /api/collections/{id}` os devolve nos `children`.
    ///
    /// ⚠️ **A reserva ficou**, e não por preguiça: 12 temporadas não têm pôster
    /// lá — a de especiais, na maioria — e pra elas o `still` do primeiro
    /// episódio continua sendo a única imagem que pertence àquela temporada e
    /// não à série.
    val arte: String?,
    /// O nome próprio da temporada, quando existe (26 das 473).
    ///
    /// ⚠️ `null` é o caso comum, e aí o rótulo é `Temporada N`. O servidor **não
    /// grava** o «Temporada 3» que o TMDB devolve traduzido — seria trocar a
    /// string pela idêntica —, então um nome aqui é sempre um nome de verdade.
    val nome: String? = null,
    /// A sinopse da temporada (232 das 473).
    val sinopse: String? = null,
) {
    val quantos: Int get() = episodios.size
    val vistos: Int get() = episodios.count { it.finished == true }

    /// ⚠️ `null` quando nada foi visto — e não `0f`. A tela **não desenha** a
    /// barra nesse caso (§24): uma barra vazia afirma «começou» sobre uma
    /// temporada em que ninguém tocou.
    val andado: Float? get() = (vistos.toFloat() / quantos).takeIf { quantos > 0 && vistos > 0 }

    /// `Temporada 3`, `Especiais`, `Sem temporada`.
    ///
    /// ## ⚠️ A temporada **0** é «Especiais», e é convenção do meio
    ///
    /// Não é um número faltando: episódios especiais, piloto não exibido e
    /// natalinos entram como temporada 0 no TMDB e em todo servidor de mídia.
    /// Escrever «Temporada 0» seria o app inventando uma temporada que ninguém
    /// chama assim.
    ///
    /// ⚠️ E `-1` é **sem temporada**, que é outra coisa ainda: episódio que o
    /// identificador não casou. Ele fica visível, num grupo próprio — sumir da
    /// série seria pior, e enfiá-lo na 1 seria afirmar uma temporada que
    /// ninguém informou (§18).
    ///
    /// Os três casos vieram da web, que já os tinha e os mediu no acervo desta
    /// casa. Ver `porTemporada`, em `web/src/App.tsx`.
    val rotulo: String get() = nome ?: when (numero) {
        SEM_TEMPORADA -> "Sem temporada"
        0 -> "Especiais"
        else -> "Temporada $numero"
    }

    companion object {
        /// A chave de quem não tem `season_number`. Negativa pra ordenar **antes**
        /// da 0 e da 1 sem disputar número com nenhuma temporada de verdade.
        const val SEM_TEMPORADA = -1
    }
}

/// Onde a pessoa parou dentro da série, se parou.
data class OndeParouNaSerie(
    val episodio: ObraDaLista,
    /// `true` quando o episódio foi **começado**; `false` quando ele é apenas o
    /// próximo a assistir. Separa «continuar» de «começar» no botão.
    val comecado: Boolean,
)

data class EstadoDaSerie(
    val carregando: Boolean = true,
    val erro: String? = null,
    val titulo: String = "",
    val ano: Int? = null,
    /// A sinopse da série — 115 das 120 têm. ⚠️ `null` **não** vira texto de
    /// enchimento: a tela simplesmente não desenha o parágrafo (§24).
    val sinopse: String? = null,
    val temporadas: List<TemporadaDaSerie> = emptyList(),
    val ondeParou: OndeParouNaSerie? = null,
    /// A arte larga do topo.
    ///
    /// ⚠️ Reserva: o `ItemDaBiblioteca` traz `poster`, mas **não** traz backdrop
    /// nem sinopse da série. Enquanto o §9 não chega, o pano de fundo é o
    /// `backdrop ?: still` do primeiro episódio — quadro da própria obra, e não
    /// uma capa esticada.
    val panoDeFundo: String? = null,
) {
    val quantosEpisodios: Int get() = temporadas.sumOf { it.quantos }
    val quantosVistos: Int get() = temporadas.sumOf { it.vistos }
    val vazio: Boolean get() = !carregando && erro == null && temporadas.isEmpty()

    fun temporada(numero: Int): TemporadaDaSerie? = temporadas.firstOrNull { it.numero == numero }
}

/// A ficha de uma série: as temporadas dela, e onde a pessoa parou.
///
/// ## ⚠️ Carrega a série **inteira** antes de mostrar, e é obrigatório
///
/// Agrupar por temporada exige ter todos os episódios: com meia lista, a
/// «Temporada 5» apareceria com 2 episódios porque os outros 11 estão na página
/// seguinte — e um número errado é pior que um número ausente.
///
/// São 60 por página; o `Arrested Development`, a maior série da casa, são 84 —
/// duas voltas. O teto de [MAXIMO_DE_VOLTAS] existe pra que um `collection` que
/// o servidor nunca termine não gire pra sempre.
class ModeloDaSerie(
    private val odeon: RepositorioOdeon,
    private val serieId: String,
    tituloInicial: String,
) : ViewModel() {

    private val _estado = MutableStateFlow(EstadoDaSerie(titulo = tituloInicial))
    val estado: StateFlow<EstadoDaSerie> = _estado.asStateFlow()

    init { carregar() }

    fun carregar() {
        _estado.update { it.copy(carregando = true, erro = null) }
        viewModelScope.launch {
            try {
                val todos = buildList {
                    var volta = 0
                    while (volta < MAXIMO_DE_VOLTAS) {
                        /// ⚠️ O `limite` vai **escrito**, e não herdado do
                        /// padrão do repositório: a saída do laço compara
                        /// `pagina.size < PAGINA`, e se os dois números
                        /// deixassem de bater um dia, o laço pararia na
                        /// primeira página ou giraria doze vezes à toa.
                        val pagina = odeon.obras(
                            pulando = size,
                            limite = PAGINA,
                            filtros = Filtros(colecao = serieId),
                        )
                        addAll(pagina)
                        if (pagina.size < PAGINA) break
                        volta++
                    }
                }
                /// ⚠️ A coleção é pedida **em paralelo** com os episódios e
                /// **não bloqueia**: ela enriquece (pôster de temporada,
                /// sinopse, backdrop da série), e a tela abre sem ela. Uma série
                /// que o servidor não conheça continua abrindo como abria.
                val colecao = odeon.colecao(serieId)
                _estado.update { antes ->
                    antes.copy(carregando = false, erro = null)
                        .comOsEpisodios(todos)
                        .comAColecao(colecao)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _estado.update {
                    it.copy(carregando = false, erro = fraseDaFalha(e, "a série não abriu"))
                }
            }
        }
    }

    fun arte(caminho: String?): String? = odeon.urlDaArte(caminho)

    /// Põe por cima o que o servidor sabe da série e das temporadas.
    ///
    /// ## ⚠️ Campo a campo, e sempre com reserva
    ///
    /// Não é «se veio a coleção, use a coleção»: é cada campo caindo pro que
    /// havia antes quando o de lá é nulo. 12 temporadas não têm pôster, 5 séries
    /// não têm sinopse, 2 não têm backdrop — e nenhuma delas pode piorar por
    /// causa disso.
    ///
    /// ⚠️ A junção é pelo **`position`**, que numa temporada é o número dela. Se
    /// o servidor mandar uma temporada que a listagem de episódios não tem, ela
    /// é ignorada: a tela mostra o que **existe em arquivo**, e não o que o TMDB
    /// diz que deveria existir (§18).
    private fun EstadoDaSerie.comAColecao(colecao: ColecaoComFilhos?): EstadoDaSerie {
        if (colecao == null) return this
        val porNumero = colecao.children.associateBy { it.position }
        return copy(
            titulo = colecao.collection.title.ifBlank { titulo },
            ano = colecao.collection.year ?: ano,
            sinopse = colecao.collection.overview?.takeIf { it.isNotBlank() } ?: sinopse,
            panoDeFundo = colecao.collection.backdrop ?: panoDeFundo,
            temporadas = temporadas.map { t ->
                val doServidor = porNumero[t.numero] ?: return@map t
                t.copy(
                    arte = doServidor.poster ?: t.arte,
                    nome = doServidor.title?.takeIf { it.isNotBlank() },
                    sinopse = doServidor.overview?.takeIf { it.isNotBlank() },
                )
            },
        )
    }

    private fun EstadoDaSerie.comOsEpisodios(todos: List<ObraDaLista>): EstadoDaSerie {
        val temporadas = agruparEmTemporadas(todos)
        val primeiro = temporadas.firstOrNull()?.episodios?.firstOrNull()
        return copy(
            temporadas = temporadas,
            ondeParou = ondeParar(temporadas),
            ano = todos.firstNotNullOfOrNull { it.year },
            panoDeFundo = primeiro?.let { it.backdrop ?: it.still ?: it.poster },
        )
    }

    private companion object {
        const val PAGINA = 60
        const val MAXIMO_DE_VOLTAS = 12
    }
}

/// Os episódios de uma série, virados em temporadas.
///
/// ⚠️ **Episódio sem temporada ganha grupo próprio** (`SEM_TEMPORADA`), e não
/// cai na 1. A primeira versão disto dobrava os nulos na temporada 1 — o que
/// afirma uma temporada que o servidor não informou. A web já fazia certo e já
/// tinha medido: «das 8.410 obras dentro de uma temporada, **nenhuma** está sem
/// `season_number`». Ou seja, o caso é raro — e é justamente por ser raro que
/// dobrá-lo passaria despercebido.
///
/// ⚠️ Dentro da temporada, **sem número vai pro fim**. Ordenar por `null` como
/// se fosse zero poria um episódio não identificado antes do piloto.
fun agruparEmTemporadas(todos: List<ObraDaLista>): List<TemporadaDaSerie> =
    todos.groupBy { it.temporada ?: TemporadaDaSerie.SEM_TEMPORADA }
        .toSortedMap()
        .map { (numero, eps) ->
            val ordenados = eps.sortedBy { it.episodio ?: Int.MAX_VALUE }
            TemporadaDaSerie(
                numero = numero,
                episodios = ordenados,
                arte = ordenados.firstNotNullOfOrNull { it.arte },
            )
        }

/// Onde a pessoa parou — o que o botão principal da ficha vai oferecer.
///
/// ## O começado ganha do próximo
///
/// Um episódio **começado** é onde a pessoa estava; o primeiro não visto é só
/// onde ela chegaria. Havendo os dois, o começado vence: voltar pro meio do que
/// se estava assistindo é o que se espera de um «continuar».
///
/// ⚠️ Série inteira vista **não** fica sem botão — volta o primeiro episódio,
/// como «começar». Um `null` aqui apagaria a única ação da tela justamente de
/// quem mais gostou dela.
fun ondeParar(temporadas: List<TemporadaDaSerie>): OndeParouNaSerie? {
    val emOrdem = temporadas.flatMap { it.episodios }
    val comecado = emOrdem.firstOrNull { (it.ondeParou ?: 0.0) > 0.0 && it.finished != true }
    if (comecado != null) return OndeParouNaSerie(comecado, comecado = true)
    val proximo = emOrdem.firstOrNull { it.finished != true }
    if (proximo != null) return OndeParouNaSerie(proximo, comecado = false)
    return emOrdem.firstOrNull()?.let { OndeParouNaSerie(it, comecado = false) }
}
