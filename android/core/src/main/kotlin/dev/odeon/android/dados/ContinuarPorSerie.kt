package dev.odeon.android.dados

/// A fileira de «continuar», com **uma linha por série**.
///
/// ## ⚠️ Três episódios da mesma série eram três cartões — 18/08/2026
///
/// A fileira vem do servidor por **obra**, e episódio é obra. Quem parou no meio
/// de três episódios de `Arcane` recebia três cartões, os três escritos
/// «Arcane» — porque todo cliente desenha `tituloDaSerie ?: title`. Lado a lado,
/// eles não dizem três coisas: dizem a mesma coisa três vezes, e empurram pra
/// fora da fileira as obras que **de fato** são outras.
///
/// Medido na fileira do rudney: 29 itens.
///
/// ## A regra: o primeiro de cada série sobrevive
///
/// A rota devolve por **recência**, e é o que faz «o primeiro» ser a resposta
/// certa: o episódio mais recente daquela série é onde a pessoa estava. Manter o
/// de menor `season/episode` daria o começo da série a quem está no fim dela.
///
/// ⚠️ **Filme não é tocado.** Sem `tituloDaSerie` não há o que agrupar, e dois
/// filmes com o mesmo nome são dois filmes — o `id` é o que os separa.
///
/// ## ⚠️ A chave é o **título**, e não um id de série
///
/// E é uma fraqueza conhecida, não um descuido: `ItemPraContinuar` não traz
/// `collection_id`. Duas séries homônimas no acervo colapsariam numa só.
///
/// Ele tem exatamente uma ocorrência aqui — `A Pantera cor-de-rosa` (1978) e
/// `A Pantera Cor-de-Rosa` (1993) — e elas **não** colidem, porque a comparação
/// é sensível a caixa e os títulos diferem nela. É sorte, e por isso está
/// escrito: se um dia o servidor mandar `collection_id` na fileira, a chave
/// troca aqui e em lugar nenhum mais.
fun colapsarPorSerie(itens: List<ItemPraContinuar>): List<ItemPraContinuar> {
    val jaVistas = mutableSetOf<String>()
    return itens.filter { item ->
        val serie = item.tituloDaSerie ?: return@filter true
        jaVistas.add(serie)
    }
}
