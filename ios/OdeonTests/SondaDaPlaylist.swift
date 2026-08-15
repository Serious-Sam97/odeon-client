import Foundation
import Testing
@testable import Odeon

/// A sonda da playlist — **fala com o servidor de verdade**.
///
/// ## ⚠️ O defeito: a barra do player diz «4:44 AM» num filme de 2006
///
/// Visto na tela em 15/08/2026, tocando «007: Cassino Royale» em `transcode`. A
/// barra do AVKit não mostrava `11:03 / 2:24:00`: mostrava **um horário de
/// relógio** e um marcador de borda ao vivo, do jeito que um player mostra uma
/// transmissão. O diagnóstico do `vigiarFalha` já dizia o mesmo em outra língua:
/// `duracao=indefinida`.
///
/// Um filme cuja barra não sabe onde termina não dá pra arrastar até o minuto 90.
/// E isto **não é do transcode**: se for o que se suspeita, vale pro
/// `direct_stream` também — os dois modos que somam **~70% do acervo** pelo perfil
/// do iOS.
///
/// A suspeita tem nome: falta `#EXT-X-PLAYLIST-TYPE:VOD` (e `#EXT-X-ENDLIST`) na
/// playlist. Sem eles o cliente **é obrigado** a tratar a lista como ao vivo — é o
/// que a RFC 8216 manda — e a janela de busca vira só os segmentos à vista.
///
/// ⚠️ Suspeita não é conclusão. Esta sonda **lê a playlist** e imprime as tags.
struct SondaDaPlaylist {

    private var odeon: RepositorioOdeon { RepositorioOdeon(cofre: Cofre()) }

    private func temSessao() -> Bool {
        let cofre = Cofre()
        return cofre.sessao != nil && cofre.servidor != nil
    }

    @Test("a playlist diz que é um filme, ou diz que é uma transmissão?")
    func aPlaylistDizOQue() async throws {
        guard temSessao() else {
            print("⚠️ sem sessão no simulador — entre no app e rode de novo")
            return
        }
        _ = try? await odeon.garantirTokenDeMidia()

        /// Pega o primeiro arquivo que não seja direct_play — é o que gera HLS.
        var alvo: (String, String)?
        for item in try await odeon.biblioteca(limite: 30) {
            guard let arquivo = item.arquivoId, !arquivo.isEmpty,
                  let plano = try? await odeon.plano(arquivo: arquivo),
                  !plano.eDireto else { continue }
            alvo = (item.title, arquivo)
            break
        }
        guard let (titulo, arquivo) = alvo else {
            print("⚠️ nenhum arquivo de HLS na amostra")
            return
        }

        let sessao = try await odeon.abrirSessao(arquivo: arquivo, comecandoEm: 0, faixaDeAudio: nil)
        defer { Task { await odeon.encerrarSessao(sessao.id) } }
        guard let url = try await odeon.urlDeMidia(sessao.urlDaPlaylist) else { return }

        let (dados, _) = try await URLSession.shared.data(from: url)
        let texto = String(decoding: dados, as: UTF8.self)
        let linhas = texto.split(separator: "\n").map(String.init)

        print("── playlist de «\(titulo)» ──")
        /// Só as tags, e não os 900 segmentos: o que decide o comportamento é o
        /// cabeçalho.
        for linha in linhas.prefix(12) { print("   \(linha)") }
        print("   … (\(linhas.count) linhas no total)")

        let temVOD = texto.contains("#EXT-X-PLAYLIST-TYPE:VOD")
        let temFim = texto.contains("#EXT-X-ENDLIST")
        print("── o veredito ──")
        print("   #EXT-X-PLAYLIST-TYPE:VOD  \(temVOD ? "presente" : "AUSENTE")")
        print("   #EXT-X-ENDLIST            \(temFim ? "presente" : "AUSENTE")")
        print("   → \(temVOD && temFim ? "é um filme" : "o cliente é obrigado a tratar como transmissão")")
    }
}
