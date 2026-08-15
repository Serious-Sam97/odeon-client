import Foundation
import Testing
@testable import Odeon

/// Acha um arquivo com **duas ou mais faixas de áudio**.
///
/// O servidor mediu 3.469 no acervo, e o par recorrente é `ac3:por | aac:eng` —
/// que é justamente o caso em que o dual audio sumia no Android antes de as faixas
/// passarem a vir do plano.
@Suite(.serialized)
struct SondaDeDualAudio {
    @Test func acharDualAudio() async throws {
        let cofre = Cofre()
        guard cofre.sessao != nil else { print("⚠️ sem sessão"); return }
        let odeon = RepositorioOdeon(cofre: cofre)

        for salto in [0, 800, 1600, 2400, 3200, 4000] {
            let pagina = try await odeon.biblioteca(pulando: salto, limite: 10)
            for item in pagina where !item.eSerie {
                guard let arquivo = item.arquivoId, !arquivo.isEmpty else { continue }
                guard let plano = try? await odeon.plano(arquivo: arquivo) else { continue }
                if plano.faixasDeAudio.count > 1 {
                    print("ACHADO obra=\(item.id)")
                    print("   título: \(item.title)")
                    print("   modo=\(plano.mode)  faixas=\(plano.faixasDeAudio.count)")
                    for f in plano.faixasDeAudio {
                        print("      [\(f.index)] label=«\(f.label)» lang=\(f.language ?? "-") codec=\(f.codec) canais=\(f.channels ?? 0)")
                    }
                    return
                }
            }
        }
        print("⚠️ não achei nenhum com 2+ faixas na amostra")
    }
}
