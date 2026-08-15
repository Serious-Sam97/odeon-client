import Foundation
import Testing
@testable import Odeon

/// Abre uma sessão e **imprime a URL da playlist**, pra a análise seguir fora do
/// app — com `ffprobe`, que é a ferramenta certa pra olhar dentro de um TS.
@Suite(.serialized)
struct SondaDeSegmento {
    @Test func urlDaSessao() async throws {
        let cofre = Cofre()
        guard cofre.sessao != nil else { print("⚠️ sem sessão"); return }
        let odeon = RepositorioOdeon(cofre: cofre)
        let s = try await odeon.abrirSessao(arquivo: "a2274591-541d-4e83-bbe3-6f1b35b6cc6a", comecandoEm: 0)
        guard let url = try await odeon.urlDeMidia(s.urlDaPlaylist) else { return }
        print("SESSAO=\(s.id)")
        print("PLAYLIST=\(url.absoluteString)")
        /// Espera o ffmpeg produzir alguma coisa antes de a análise começar.
        try? await Task.sleep(for: .seconds(6))
    }
}
