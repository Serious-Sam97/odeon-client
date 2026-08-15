import Foundation
import Testing
@testable import Odeon

/// A sonda da contagem — **fala com o servidor de verdade**.
///
/// ## ⚠️ O número que não bateu, e por que ele merece medida
///
/// Na primeira captura da ponte guia → biblioteca, a pílula do guia prometia
/// **Drama 228** e a grade entregou **216**. Doze filmes de diferença.
///
/// Um filtro que promete um número e entrega outro é a família do §8b: visível,
/// e mentindo baixinho. E há duas explicações plausíveis e opostas — ou o
/// `kind=movie` está cortando algo que o guia conta, ou o **agrupamento de
/// versões** de 14/08/2026 fez a biblioteca contar grupos onde o guia conta
/// obras. A segunda seria interessante: significaria que a mudança de ontem
/// desalinhou os dois números em **todos os quatro clientes**, não só neste.
///
/// Chutar entre as duas custaria o mesmo que medir. Então mediu-se, em
/// 15/08/2026, e a resposta é **exata**:
///
/// ```
/// guia:                       Drama = 228
/// tags+kind=movie (a ponte):  216
/// só tags, sem kind:          252
/// entradas na grade:          216
/// delas, grupos de versão:    11
/// rips a mais que entradas:   12
/// → 216 + 12 = 228
/// ```
///
/// ⚠️ **É o agrupamento de versões**, e não o `kind`. O guia conta **rips**; a
/// biblioteca conta **grupos**. Onze filmes de Drama têm segunda versão (um deles
/// tem três), e são exatamente os doze que faltam.
///
/// Isso quer dizer que a mudança de 14/08/2026 desalinhou os dois números em
/// **todos os quatro clientes** — TV, celular, web e iOS —, porque quem mudou foi
/// o servidor. O conserto é de lá e é de um lugar só; está escrito no
/// `PEDIDOS-AO-SERVIDOR.md`. Aqui fica a medida, que é o que torna o pedido uma
/// afirmação e não uma suspeita.
struct SondaDaContagem {

    private var odeon: RepositorioOdeon { RepositorioOdeon(cofre: Cofre()) }

    private func temSessao() -> Bool {
        let cofre = Cofre()
        return cofre.sessao != nil && cofre.servidor != nil
    }

    @Test("por que o guia diz 228 e a biblioteca diz 216")
    func porQueOsNumerosDiferem() async throws {
        guard temSessao() else {
            print("⚠️ sem sessão no simulador — entre no app e rode de novo")
            return
        }
        _ = try? await odeon.garantirTokenDeMidia()

        let eixos = try await odeon.guia()
        guard let drama = eixos.generos.first(where: { $0.rotulo == "Drama" }) else {
            print("⚠️ o eixo «Drama» não veio — nada a comparar")
            return
        }
        print("guia:                       \(drama.rotulo) = \(drama.obras)  (chave \(drama.chave))")

        /// 1. O que a ponte manda hoje: etiqueta + `kind=movie`.
        let comoAPonteManda = try await odeon.biblioteca(limite: 1, filtros: Filtros.doEixo(drama))
        print("tags+kind=movie (a ponte):  \(comoAPonteManda.first?.total ?? -1)")

        /// 2. A mesma etiqueta **sem** o `kind`. Se este for 228, o corte é o
        ///    `kind=movie` — e aí a pílula do guia estaria contando episódio.
        let semTipo = try await odeon.biblioteca(
            limite: 1, filtros: Filtros(etiqueta: drama.chave),
        )
        print("só tags, sem kind:          \(semTipo.first?.total ?? -1)")

        /// 3. E a conta que decide entre as duas explicações: **quantas das
        ///    entradas filtradas são grupo de versão**. Se for exatamente a
        ///    diferença, o guia conta rips e a biblioteca conta grupos.
        var todas: [ItemDaBiblioteca] = []
        while true {
            let pagina = try await odeon.biblioteca(
                pulando: todas.count, limite: 200, filtros: Filtros.doEixo(drama),
            )
            if pagina.isEmpty { break }
            todas += pagina
            if let t = pagina.first?.total, todas.count >= t { break }
        }
        let grupos = todas.filter(\.temEscolhaDeVersao)
        let ripsAMais = grupos.reduce(0) { $0 + $1.versoesEscolhiveis.count - 1 }
        print("entradas na grade:          \(todas.count)")
        print("delas, grupos de versão:    \(grupos.count)")
        print("rips a mais que entradas:   \(ripsAMais)")
        print("→ \(todas.count) + \(ripsAMais) = \(todas.count + ripsAMais), e o guia diz \(drama.obras)")
    }
}
