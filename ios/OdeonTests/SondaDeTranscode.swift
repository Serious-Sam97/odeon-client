import Foundation
import Testing
@testable import Odeon

/// A sonda do `transcode` — **fala com o servidor de verdade**.
///
/// ## ⚠️ O terceiro modo é o único que nunca foi visto
///
/// `direct_play` e `direct_stream` foram conferidos na tela, com filme rodando.
/// O `transcode` é **17,2% do acervo** pelo perfil do iOS e nenhum filme aberto
/// nesta história caiu nele — os 007 do topo da grade são todos mp4 ou remux.
///
/// Escrever «o player funciona» sem ter visto este ramo seria afirmar sobre um
/// terço do caminho que ninguém andou.
///
/// ## ⚠️ E o que a sonda achou fecha um círculo
///
/// Os três `transcode` da amostra dão o mesmo motivo: **«o cliente não toca áudio
/// em mp3»**. É a decisão do `ProvaDeMp3` voltando pelo outro lado — mp3 toca em
/// `.mov` e é invisível em `.mp4`, o acervo tem 64 `.mp4` com mp3, e por isso o
/// codec ficou fora da lista declarada. O servidor lê essa lista e recodifica.
///
/// ⚠️ E o vídeo sai `copy`: **só o áudio é recodificado**. «Transcode» aqui é
/// mais barato do que o nome sugere — o que o servidor gasta é um encoder de
/// áudio, não um de vídeo.
///
/// Esta sonda só **acha** um: ela varre a biblioteca perguntando o plano até
/// achar `transcode`, e imprime o título pra a busca da tela encontrar. Quem
/// confere é o olho.
struct SondaDeTranscode {

    private var odeon: RepositorioOdeon { RepositorioOdeon(cofre: Cofre()) }

    private func temSessao() -> Bool {
        let cofre = Cofre()
        return cofre.sessao != nil && cofre.servidor != nil
    }

    @Test("ache um filme que o iPhone só abre transcodificando")
    func acheUmTranscode() async throws {
        guard temSessao() else {
            print("⚠️ sem sessão no simulador — entre no app e rode de novo")
            return
        }
        _ = try? await odeon.garantirTokenDeMidia()

        var achados = 0
        var pulando = 0

        /// ⚠️ Varre de **página em página** e para no terceiro achado. Cada plano é
        /// uma consulta no servidor de casa, que também transcodifica — varrer as
        /// 8.273 entradas pra achar o que a terceira responde seria cobrar do
        /// servidor por teimosia.
        while achados < 3, pulando < 400 {
            let pagina = try await odeon.biblioteca(pulando: pulando, limite: 50)
            if pagina.isEmpty { break }
            pulando += pagina.count

            for item in pagina {
                guard let arquivo = item.arquivoId, !arquivo.isEmpty else { continue }
                guard let plano = try? await odeon.plano(arquivo: arquivo) else { continue }
                guard plano.mode == "transcode" else { continue }
                achados += 1
                print("── \(item.title) (\(item.year.map(String.init) ?? "?"))")
                print("   motivo: \(plano.reasons.joined(separator: ", "))")
                print("   vídeo: \(plano.video)  áudio: \(plano.audio)  altura: \(plano.alturaAlvo.map(String.init) ?? "—")")
                if achados >= 3 { break }
            }
        }
        if achados == 0 { print("⚠️ nenhum transcode nas primeiras \(pulando) entradas") }
    }
}
