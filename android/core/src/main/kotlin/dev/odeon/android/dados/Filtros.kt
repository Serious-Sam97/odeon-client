package dev.odeon.android.dados

/// O filtro composto da biblioteca — o `Filters` do `web/src/api.ts:905`.
///
/// ## Um objeto só, e não oito parâmetros soltos
///
/// Porque eles **viajam juntos**: trocar de ordem mantém as tags, entrar numa
/// série mantém a ordem, e limpar tira quase tudo menos duas coisas. Com oito
/// campos soltos no `ViewModel`, cada uma dessas regras vira uma linha que
/// alguém esquece de escrever — e o sintoma é o filtro que some sozinho.
///
/// ## Os nomes dos parâmetros são os do servidor, e a tradução mora num lugar só
///
/// `tag_mode`, `min_minutes`, `year_from`. Eles saem daqui pro Retrofit como
/// vieram, e a tela nunca monta uma query — é o mesmo arranjo da web, cujo
/// `queryString` é a única função que conhece os nomes.
data class Filtros(
    val busca: String = "",
    /// `movie`, `episode`, `standup`… Vem do guia (§7 da referência), que filtra
    /// por gênero **só em filmes**.
    val tipo: String? = null,
    /// Cada uma é `namespace:valor` — `genre:Terror`. A mesma string que vai pro
    /// servidor, sem tradução no meio: a tela mostra `Terror`, o filtro carrega
    /// `genre:Terror`, e o `namespace` é o que agrupa os chips.
    val etiquetas: List<String> = emptyList(),
    /// `all` exige todas, `any` aceita qualquer uma. O padrão é `all` porque
    /// somar duas tags quase sempre quer dizer «as duas».
    val modoDasEtiquetas: String = "all",
    val anoDe: Int? = null,
    val anoAte: Int? = null,
    val minutosDe: Int? = null,
    val minutosAte: Int? = null,
    /// A série em que se entrou. Com ela, a fonte deixa de ser `/api/library`
    /// (agrupada) e passa a ser `/api/works` (plana) — ver `ModeloDaBiblioteca`.
    val colecao: String? = null,
    /// `confirmed` · `auto` · `needs_review` · `unmatched` · `ignored`.
    ///
    /// ⚠️ `ignored` é o **único** jeito de ver as 1.234 obras descartadas: elas
    /// ficam fora da biblioteca por padrão, de propósito.
    val estado: String? = null,
    val pessoa: String? = null,
    /// `featured` · `title` · `year` · `added` · `duration` · `random`.
    val ordem: String = "featured",
) {
    /// As tags como o servidor as quer: separadas por vírgula.
    val etiquetasEmTexto: String? get() = etiquetas.takeIf { it.isNotEmpty() }?.joinToString(",")

    /// O `tag_mode` só é mandado quando há tag — mandar o modo de uma lista
    /// vazia é dizer ao servidor como combinar nada.
    val modoParaMandar: String? get() = modoDasEtiquetas.takeIf { etiquetas.isNotEmpty() }

    /// Quantos filtros estão ligados. É o número da pílula do botão `filtros`, e
    /// **não conta a busca nem a ordem**: as duas têm controle próprio na tela, e
    /// contá-las faria a pílula dizer 1 com a tela recém-aberta.
    val quantosLigados: Int
        get() = etiquetas.size +
            listOfNotNull(estado, tipo, pessoa).size +
            (if (minutosDe != null || minutosAte != null) 1 else 0) +
            (if (anoDe != null || anoAte != null) 1 else 0)

    val algumLigado: Boolean get() = quantosLigados > 0

    /// Limpar.
    ///
    /// ⚠️ **A busca, a ordem e a coleção sobrevivem**, e é regra da web escrita
    /// no `FilterBar.tsx`: «limpar filtro não deve tirar você de dentro da
    /// série». As três não são filtro de acervo — são onde você está e como está
    /// olhando.
    fun limpo(): Filtros = Filtros(busca = busca, ordem = ordem, colecao = colecao)

    /// Liga ou desliga uma etiqueta.
    fun comEtiqueta(chave: String): Filtros = copy(
        etiquetas = if (chave in etiquetas) etiquetas - chave else etiquetas + chave,
    )

    /// A faixa de duração, ligando e desligando no mesmo toque — tocar na que já
    /// está ligada desliga, que é como a web faz e como um chip deve se
    /// comportar.
    fun comDuracao(de: Int?, ate: Int?): Filtros =
        if (minutosDe == de && minutosAte == ate) {
            copy(minutosDe = null, minutosAte = null)
        } else {
            copy(minutosDe = de, minutosAte = ate)
        }

    fun comEstado(qual: String): Filtros =
        copy(estado = if (estado == qual) null else qual)
}

/// As faixas de duração, com o rótulo que a web usa.
///
/// Os cortes (40 e 90 minutos) não são arredondamento bonito: 40 separa episódio
/// de filme, e 90 separa filme de filme longo. Ver `FilterBar.tsx`.
val DURACOES: List<Triple<String, Int?, Int?>> = listOf(
    Triple("curto (até 40min)", null, 40),
    Triple("médio (40–90min)", 40, 90),
    Triple("longo (90min+)", 90, null),
)

/// Os estados da identificação, em português.
val ESTADOS_DE_IDENTIFICACAO: List<Pair<String, String>> = listOf(
    "confirmed" to "confirmadas",
    "auto" to "automáticas",
    "needs_review" to "em dúvida",
    "unmatched" to "sem match",
    "ignored" to "ignoradas",
)

/// As ordens. `featured` é o padrão, e o comentário da web diz por quê:
/// ordenar por título põe `001 - Draw My Life As a Gamer` na frente, porque
/// número vem antes de letra.
val ORDENS: List<Pair<String, String>> = listOf(
    "featured" to "em destaque",
    "title" to "título",
    "year" to "ano",
    "added" to "adicionado",
    "duration" to "duração",
    "random" to "aleatório",
)
