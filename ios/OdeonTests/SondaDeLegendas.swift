import AVFoundation
import Foundation
import Testing
@testable import Odeon

/// O que o AVPlayer **enxerga** de legenda em cada caminho.
///
/// ## ⚠️ Medir antes de desenhar menu
///
/// O plano traz `subtitles` — o que o **arquivo** tem. Isso não é o mesmo que o
/// que o **player** consegue selecionar, e os dois caminhos são mundos diferentes:
///
/// | | como a legenda chegaria |
/// |---|---|
/// | `direct_play` | dentro do arquivo, se o AVPlayer souber lê-la |
/// | `direct_stream` / `transcode` | como *rendition* na playlist HLS, se o servidor a puser lá |
///
/// No Android o app anexa as legendas como `SubtitleConfiguration` no `MediaItem`
/// — e a §26.7 registra que anexar **todas** foi suspeito de custar quadro. Aqui o
/// mecanismo é outro (`AVMediaSelectionGroup`), então a pergunta é do zero.
@Suite(.serialized)
struct SondaDeLegendas {

    private var odeon: RepositorioOdeon { RepositorioOdeon(cofre: Cofre()) }

    @Test(arguments: [
        ("007 inglês — direct_play", "2531ac55-1f33-4252-a1d8-c4e878fbb757"),
        ("007 pt-BR — direct_stream", "a2274591-541d-4e83-bbe3-6f1b35b6cc6a"),
    ])
    func oQueOPlayerEnxerga(_ alvo: (nome: String, arquivo: String)) async throws {
        let cofre = Cofre()
        guard cofre.sessao != nil else { print("⚠️ sem sessão"); return }

        print("\n════ \(alvo.nome) ════")
        let plano = try await odeon.plano(arquivo: alvo.arquivo)
        print("plano: mode=\(plano.mode)  legendas no ARQUIVO=\(plano.subtitles.count)")
        for s in plano.subtitles.prefix(4) {
            print("   [\(s.index)] «\(s.label)» lang=\(s.language ?? "-") codec=\(s.codec) origem=\(s.origem) texto=\(s.baseadaEmTexto) forçada=\(s.forced)")
        }

        let url: URL?
        var sessaoId: String?
        if plano.eDireto, let d = plano.urlDireta {
            url = try await odeon.urlDeMidia(d)
        } else {
            let s = try await odeon.abrirSessao(arquivo: alvo.arquivo, comecandoEm: 0)
            sessaoId = s.id
            url = try await odeon.urlDeMidia(s.urlDaPlaylist)
        }
        defer { if let sessaoId { Task { await odeon.encerrarSessao(sessaoId) } } }
        guard let url else { return }

        let asset = AVURLAsset(url: url)

        /// ⚠️ **A pergunta que decide o menu**: o `AVMediaSelectionGroup` é o
        /// único jeito de o AVPlayer oferecer legenda. Se ele vier vazio, não
        /// adianta ter dez legendas no arquivo — a tela não teria o que oferecer.
        for caracteristica in [AVMediaCharacteristic.legible, .audible] {
            let grupo = try? await asset.loadMediaSelectionGroup(for: caracteristica)
            let opcoes = grupo?.options ?? []
            print("   grupo \(caracteristica.rawValue): \(opcoes.count) opção(ões)")
            for o in opcoes.prefix(5) {
                let idioma = o.locale?.identifier ?? o.extendedLanguageTag ?? "-"
                print("      • \(o.displayName)  [\(idioma)]  tipo=\(o.mediaType.rawValue)")
            }
        }
    }
}

/// O que a rota de legendas devolve — formato importa: o AVFoundation não lê
/// `.srt`, e o navegador não lê tampouco (a web usa `<track>`, que exige VTT).
extension SondaDeLegendas {
    @Test func oQueARotaDevolve() async throws {
        let cofre = Cofre()
        guard cofre.sessao != nil, let base = cofre.servidor else { print("⚠️ sem sessão"); return }
        let odeon = RepositorioOdeon(cofre: cofre)
        let arquivo = "2531ac55-1f33-4252-a1d8-c4e878fbb757"
        let token = try await odeon.garantirTokenDeMidia()

        for indice in [-1, -2] {
            guard let url = URL(string: "\(base)/api/media/\(arquivo)/subtitles/\(indice)?token=\(token)") else { continue }
            let (dados, resp) = (try? await URLSession.shared.data(from: url)) ?? (Data(), URLResponse())
            let http = (resp as? HTTPURLResponse)?.statusCode ?? -1
            let tipo = (resp as? HTTPURLResponse)?.value(forHTTPHeaderField: "Content-Type") ?? "?"
            print("── índice \(indice): http=\(http) tipo=\(tipo) bytes=\(dados.count)")
            let texto = String(data: dados.prefix(320), encoding: .utf8) ?? "(não é texto)"
            for linha in texto.split(separator: "\n").prefix(8) { print("   | \(linha)") }
        }
    }
}

/// A legenda casa com o relógio do filme?
extension SondaDeLegendas {
    @Test func aLegendaCasaComOTempo() async throws {
        let cofre = Cofre()
        guard cofre.sessao != nil else { print("⚠️ sem sessão"); return }
        let odeon = RepositorioOdeon(cofre: cofre)
        let arquivo = "2531ac55-1f33-4252-a1d8-c4e878fbb757"

        let falas = try await odeon.legenda(arquivo: arquivo, indice: -2)
        print("── falas lidas: \(falas.count)")
        guard let primeira = falas.first, let ultima = falas.last else {
            Issue.record("nenhuma fala"); return
        }
        print("   primeira: \(primeira.de)s → \(primeira.ate)s  «\(primeira.texto.prefix(40))»")
        print("   última:   \(ultima.de)s → \(ultima.ate)s")
        print("   o filme dura ~8538s; a última fala está em \(Int(ultima.ate))s")

        /// O ponto onde o player estava quando a tela ficou sem legenda.
        for t in [4980.0, 4985.0, 5000.0, 5100.0, 60.0, 3600.0] {
            let f = Legenda.falaEm(t, falas)
            print("   em \(Int(t))s: \(f.map { "«\($0.texto.prefix(34))»" } ?? "— nada —")")
        }
    }
}
