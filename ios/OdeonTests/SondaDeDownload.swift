import Foundation
import Testing
@testable import Odeon

/// A sonda do download — **fala com o servidor de verdade**.
///
/// ## ⚠️ Duas perguntas que decidem a tela inteira dos baixados
///
/// **1. Quanto do acervo dá pra baixar?** No Android, tudo: o ExoPlayer abre
/// matroska, e 55% do acervo é matroska. O AVFoundation **não abre**. Baixar um
/// mkv de 2 GB no iPhone seria gastar o disco da pessoa num arquivo que não abre
/// — o §53 na versão mais cara possível. Então só desce o que o plano diz
/// `direct_play`, e a pergunta é qual fatia é essa.
///
/// **2. O `/api/stream` aceita `Range`?** Sem isso não há retomada: o
/// `URLSession` de fundo só sabe continuar de onde parou se o servidor responder
/// `206` a um pedido parcial. Se não aceitar, «pausar» na tela seria um botão que
/// joga fora 800 MB — e aí a tela **não deve oferecer pausa**.
///
/// Nenhuma das duas se responde lendo código. Mede-se.
struct SondaDeDownload {

    private var odeon: RepositorioOdeon { RepositorioOdeon(cofre: Cofre()) }

    private func temSessao() -> Bool {
        let cofre = Cofre()
        return cofre.sessao != nil && cofre.servidor != nil
    }

    @Test("que fatia do acervo o iPhone consegue guardar, e o servidor aceita Range?")
    func aFatiaEORange() async throws {
        guard temSessao() else {
            print("⚠️ sem sessão no simulador — entre no app e rode de novo")
            return
        }
        _ = try? await odeon.garantirTokenDeMidia()

        /// Uma amostra da biblioteca, e o plano de cada um. ⚠️ 40 e não 400: cada
        /// plano é uma consulta no servidor de casa, que também transcodifica.
        let amostra = try await odeon.biblioteca(limite: 40)
        var diretos: [(String, String)] = []
        var porModo: [String: Int] = [:]

        for item in amostra {
            guard let arquivo = item.arquivoId, !arquivo.isEmpty else { continue }
            guard let plano = try? await odeon.plano(arquivo: arquivo) else { continue }
            porModo[plano.mode, default: 0] += 1
            if plano.eDireto, let url = plano.urlDireta { diretos.append((item.title, url)) }
        }

        let total = porModo.values.reduce(0, +)
        print("── planos de \(total) arquivos da amostra ──")
        for (modo, quantos) in porModo.sorted(by: { $0.value > $1.value }) {
            print(String(format: "  %-16@ %3d  (%.1f%%)", modo as NSString, quantos,
                         Double(quantos) / Double(max(total, 1)) * 100))
        }
        print("→ baixáveis no iPhone: \(diretos.count) de \(total)")

        /// A segunda pergunta, contra um arquivo direto de verdade.
        guard let (titulo, caminho) = diretos.first,
              let url = try await odeon.urlDeMidia(caminho) else {
            print("⚠️ nenhum direct_play na amostra — não dá pra testar o Range")
            return
        }

        var pedido = URLRequest(url: url)
        pedido.setValue("bytes=1000000-1000999", forHTTPHeaderField: "Range")
        let (dados, resposta) = try await URLSession.shared.data(for: pedido)
        let http = resposta as? HTTPURLResponse
        print("── Range em «\(titulo)» ──")
        print("  status:         \(http?.statusCode ?? -1)  (206 = aceita retomada)")
        print("  bytes vindos:   \(dados.count)  (1000 = mandou só o pedaço)")
        print("  Content-Range:  \(http?.value(forHTTPHeaderField: "Content-Range") ?? "—")")
        print("  Accept-Ranges:  \(http?.value(forHTTPHeaderField: "Accept-Ranges") ?? "—")")
        print("  ETag:           \(http?.value(forHTTPHeaderField: "ETag") ?? "—")")
        print("  Last-Modified:  \(http?.value(forHTTPHeaderField: "Last-Modified") ?? "—")")
    }
}
