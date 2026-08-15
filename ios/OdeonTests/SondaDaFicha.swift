import Foundation
import Testing
@testable import Odeon

/// A sonda da ficha — **fala com o servidor de verdade**.
///
/// ## ⚠️ A marquise subiu e as fotos ficaram pretas
///
/// Visto em 15/08/2026: o fotograma de fundo e as três Polaroids do varal
/// desenharam a moldura e **não a imagem**. O `urlDaArte` monta sempre
/// `/artwork/{caminho}?token=…`, e isso vale pro pôster da grade — a pergunta é
/// se vale pro `backdrop` da obra e pro `imagem` de uma cena.
///
/// Chutar entre «a chave do dicionário é outra» e «o caminho não é de `/artwork`»
/// custa o mesmo que imprimir os dois.
struct SondaDaFicha {

    private var odeon: RepositorioOdeon { RepositorioOdeon(cofre: Cofre()) }

    private func temSessao() -> Bool {
        let cofre = Cofre()
        return cofre.sessao != nil && cofre.servidor != nil
    }

    @Test("que caminhos a obra e as cenas mandam, e eles abrem?")
    func queCaminhos() async throws {
        guard temSessao() else {
            print("⚠️ sem sessão no simulador — entre no app e rode de novo")
            return
        }
        _ = try? await odeon.garantirTokenDeMidia()

        guard let item = try await odeon.biblioteca(limite: 12).first(where: { $0.poster != nil })
        else { print("⚠️ nada com pôster na amostra"); return }
        let obra = try await odeon.obra(item.id)

        print("── «\(obra.title)» ──")
        print("   artwork: \(obra.artwork)")
        print("   tags:    \(obra.tags.map { "\($0.namespace):\($0.value)" })")

        let cenas = (try? await odeon.cenas(obra: obra.id)) ?? []
        print("   cenas:   \(cenas.count)")
        if let c = cenas.first { print("   1ª cena: segundos=\(c.segundos) imagem=«\(c.imagem)»") }

        /// E o teste que decide: as URLs montadas **abrem**?
        for (nome, caminho) in [("backdrop", obra.artwork["backdrop"]),
                                ("poster", obra.artwork["poster"]),
                                ("cena", cenas.first?.imagem)] {
            guard let caminho, let url = odeon.urlDaArte(caminho) else {
                print("   \(nome): sem caminho"); continue
            }
            let (dados, resposta) = try await URLSession.shared.data(from: url)
            let status = (resposta as? HTTPURLResponse)?.statusCode ?? -1
            print("   \(nome): \(status) · \(dados.count) bytes · \(url.path)")
        }
    }
}
