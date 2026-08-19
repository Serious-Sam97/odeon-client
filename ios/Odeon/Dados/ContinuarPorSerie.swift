import Foundation

/// A fileira de «continuar», com **uma linha por série**.
///
/// ## ⚠️ Três episódios da mesma série eram três cartões — 18/08/2026
///
/// A fileira vem do servidor por **obra**, e episódio é obra. Quem parou no meio
/// de três episódios de `Arcane` recebia três cartões, os três escritos
/// «Arcane» — porque todo cliente desenha `tituloDaSerie ?? title`. Lado a lado,
/// eles não dizem três coisas: dizem a mesma coisa três vezes.
///
/// ## A regra: o primeiro de cada série sobrevive
///
/// A rota devolve por **recência**, e é o que faz «o primeiro» ser a resposta
/// certa: o episódio mais recente daquela série é onde a pessoa estava.
///
/// ⚠️ **Filme não é tocado** — sem `tituloDaSerie` não há o que agrupar.
///
/// ⚠️ A chave é o **título**: `ItemPraContinuar` não traz id de coleção. Mesma
/// fraqueza declarada do Android, e mesmo conserto no dia em que o servidor
/// mandar o id. Ver `colapsarPorSerie` no `:core`.
func colapsarPorSerie(_ itens: [ItemPraContinuar]) -> [ItemPraContinuar] {
    var jaVistas = Set<String>()
    return itens.filter { item in
        guard let serie = item.tituloDaSerie else { return true }
        return jaVistas.insert(serie).inserted
    }
}
