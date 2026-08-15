import Foundation
import Testing
@testable import Odeon

/// A sonda do token de mídia — **fala com o servidor de verdade**.
///
/// ## ⚠️ A arte sumiu do app inteiro, e eu não sei por quê
///
/// Visto na tela em 15/08/2026: herói vazio, cartões de «continuar» vazios, as
/// caixas da locadora só com a cor dominante, e **um** cartaz aparecendo na
/// grade. O `conferirTokenDeMidia` que eu escrevi pra isso não pegou o caso.
///
/// Há três explicações, e elas pedem consertos opostos:
///
/// | | o conserto |
/// |---|---|
/// | não há token guardado | `garantirTokenDeMidia` está falhando em pedir |
/// | há token e ele morreu | a renovação não está sendo disparada |
/// | há token, ele vale, e a URL é que está errada | não é token nenhum |
///
/// Chutar entre as três custa o mesmo que imprimir as três.
struct SondaDoToken {

    private var odeon: RepositorioOdeon { RepositorioOdeon(cofre: Cofre()) }

    @Test("existe token? ele vale? a arte abre?")
    func oQueEstaAcontecendo() async throws {
        let cofre = Cofre()
        guard cofre.sessao != nil, let base = cofre.servidor else {
            print("⚠️ sem sessão no simulador"); return
        }
        print("── antes ──")
        print("   servidor:       \(base)")
        print("   token guardado: \(cofre.tokenDeMidia.map { String($0.prefix(12)) + "…" } ?? "NENHUM")")

        /// 1. O que `garantirTokenDeMidia` devolve hoje.
        let token = try? await odeon.garantirTokenDeMidia()
        print("   garantir():     \(token.map { String($0.prefix(12)) + "…" } ?? "FALHOU")")

        /// 2. Uma arte que a tela pediria.
        guard let item = try await odeon.biblioteca(limite: 8).first(where: { $0.poster != nil }),
              let caminho = item.poster else {
            print("⚠️ nada com pôster"); return
        }
        guard let url = odeon.urlDaArte(caminho) else {
            print("   urlDaArte:      NIL  ← sem token, a URL nem é montada")
            return
        }
        let (dados, resposta) = try await URLSession.shared.data(from: url)
        let status = (resposta as? HTTPURLResponse)?.statusCode ?? -1
        print("   arte «\(item.title)»: \(status) · \(dados.count) bytes")

        /// 3. Se falhou, um token novo resolve? É a pergunta que separa «morreu»
        ///    de «a rota mudou».
        if status != 200 {
            let novo = try? await odeon.renovarTokenDeMidia()
            print("── depois de renovar ──")
            print("   token novo:     \(novo.map { String($0.prefix(12)) + "…" } ?? "FALHOU")")
            if let url2 = odeon.urlDaArte(caminho) {
                let (d2, r2) = try await URLSession.shared.data(from: url2)
                print("   arte de novo:   \((r2 as? HTTPURLResponse)?.statusCode ?? -1) · \(d2.count) bytes")
            }
        }
    }
}
