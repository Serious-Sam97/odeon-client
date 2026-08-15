import AVFoundation
import Foundation
import Testing
@testable import Odeon

/// A prova de que o arquivo **toca** — e não de que alguém disse que toca.
///
/// ## ⚠️ Por que decodificar um quadro, e não perguntar `isPlayable`
///
/// `isPlayable` é a opinião do AVFoundation antes de tentar, e ela é otimista:
/// contêiner reconhecido costuma bastar pra ela dizer sim. O que decide é **sair
/// imagem** — e `AVAssetImageGenerator` obriga o decodificador a trabalhar de
/// verdade.
///
/// É a mesma régua do resto do projeto, aplicada onde ela cabe num teste: o
/// equivalente de «ver na tela» é «saiu quadro».
///
/// ## O que está em jogo: **2.265 arquivos**
///
/// O acervo tem 14,7% de avi, quase todos `mpeg4` + `mp3`. Depois que o cliente
/// passou a declarar `avi`, `mpeg4` e `mp3`, o servidor os classifica como
/// `direct_play` — arquivo original, zero ffmpeg.
///
/// ⚠️ Mas o servidor decide **acreditando na lista do cliente**. Se o AVPlayer não
/// abrir de fato, a declaração piorou a situação: antes transcodificava e
/// funcionava; agora vai o original e não toca. É o segundo sentido em que «a
/// lista fixa mente», e é por isso que esta sonda existe.
///
/// ⚠️ **`.serialized` não é zelo.** Vários destes testes renovam o token de mídia,
/// e emitir um **aposenta o anterior** — rodando em paralelo, cada um mata o token
/// do vizinho e todos falham por um motivo que não é o que se está medindo. A
/// bancada criando o defeito que ela mede é o §27 do Android de novo.
@Suite(.serialized)
struct SondaDeReproducao {

    private var odeon: RepositorioOdeon { RepositorioOdeon(cofre: Cofre()) }

    /// Um avi/mpeg4 achado pela `SondaDoAcervo`.
    private let aviDoAcervo = "a9deb328-f018-4362-9451-a6f688ceb528"
    /// O 007 em inglês — mp4/aac, o `direct_play` que já funciona em toda parte.
    /// Serve de controle: se **ele** não decodificar, o defeito é da sonda.
    private let mp4DeControle = "2531ac55-1f33-4252-a1d8-c4e878fbb757"

    @Test("sai quadro de um avi/mpeg4 do acervo?")
    func aviDecodifica() async throws {
        try await tentarDecodificar(arquivo: aviDoAcervo, nome: "avi/mpeg4+mp3")
    }

    @Test("controle: sai quadro do mp4/aac que já toca em tudo")
    func mp4Decodifica() async throws {
        try await tentarDecodificar(arquivo: mp4DeControle, nome: "mp4/aac (controle)")
    }

    /// ⚠️ **A prova que falta: o `mp3` foi declarado pelo mesmo oráculo que
    /// mentiu sobre o `mpeg4`**, e 576 arquivos do acervo dependem dele.
    ///
    /// O avi que falhou também tinha mp3, mas ali o fracasso é atribuível ao
    /// vídeo. Pra isolar o áudio é preciso um arquivo cujo **vídeo já se sabe que
    /// decodifica** — mov/mp4 com h264 — e cujo áudio seja mp3. O servidor contou
    /// 63 desses em mov.
    @Test("mp3 num contêiner que já funciona: o áudio decodifica?")
    func mp3Decodifica() async throws {
        let cofre = Cofre()
        guard cofre.sessao != nil else {
            print("⚠️ sem sessão no simulador")
            return
        }

        var achado: (arquivo: String, container: String, video: String)?
        busca: for salto in [0, 1500, 3000, 4500, 6000, 7500] {
            let pagina = try await odeon.biblioteca(pulando: salto, limite: 12)
            for item in pagina where !item.eSerie {
                guard let obra = try? await odeon.obra(item.id) else { continue }
                for a in obra.files {
                    let cont = (a.container ?? "").lowercased()
                    let ehCascaBoa = cont.contains("mov") || cont.contains("mp4")
                    if ehCascaBoa, a.codecDeAudio == "mp3", a.codecDeVideo == "h264" {
                        achado = (a.id, cont, a.codecDeVideo ?? "?")
                        break busca
                    }
                }
            }
        }

        guard let achado else {
            print("⚠️ não achei mov/mp4 + h264 + mp3 na amostra — o mp3 continua NÃO PROVADO")
            return
        }
        print("── achado: \(achado.container) + \(achado.video) + mp3")
        try await tentarDecodificar(arquivo: achado.arquivo, nome: "mp3 em \(achado.container)")
    }

    private func tentarDecodificar(arquivo: String, nome: String) async throws {
        let cofre = Cofre()
        guard cofre.sessao != nil, let base = cofre.servidor else {
            print("⚠️ sem sessão no simulador — entre no app e rode de novo")
            return
        }

        let plano = try await odeon.plano(arquivo: arquivo)
        print("── \(nome): mode=\(plano.mode) video=\(plano.video) audio=\(plano.audio)")

        guard let caminho = plano.urlDireta else {
            print("   sem url direta (mode=\(plano.mode)) — esta sonda só cobre direct_play")
            return
        }

        /// ⚠️ O `direct_url` pode vir relativo. E ele precisa do **token de
        /// mídia** — o de sessão não abre bytes.
        let token = try await odeon.garantirTokenDeMidia()
        let absoluto = caminho.hasPrefix("http") ? caminho : base + caminho
        guard var partes = URLComponents(string: absoluto) else {
            print("   url impossível de montar: \(caminho)")
            return
        }
        var itens = partes.queryItems ?? []
        if !itens.contains(where: { $0.name == "token" }) {
            itens.append(URLQueryItem(name: "token", value: token))
        }
        partes.queryItems = itens
        guard let url = partes.url else { return }
        print("   url: \(url.absoluteString.prefix(90))…")

        let asset = AVURLAsset(url: url)

        let tocavel = (try? await asset.load(.isPlayable)) ?? false
        let faixas = (try? await asset.loadTracks(withMediaType: .video)) ?? []
        let duracao = (try? await asset.load(.duration)) ?? .zero
        print("   isPlayable=\(tocavel)  faixas de vídeo=\(faixas.count)  duração=\(Int(duracao.seconds))s")

        if let faixa = faixas.first {
            let tam = (try? await faixa.load(.naturalSize)) ?? .zero
            let fps = (try? await faixa.load(.nominalFrameRate)) ?? 0
            print("   \(Int(tam.width))x\(Int(tam.height)) a \(fps) fps")
        }

        /// ⚠️ **A prova.** Tudo acima é o AVFoundation descrevendo o arquivo;
        /// só aqui o decodificador é obrigado a produzir pixel.
        let gerador = AVAssetImageGenerator(asset: asset)
        gerador.appliesPreferredTrackTransform = true
        gerador.requestedTimeToleranceBefore = CMTime(seconds: 2, preferredTimescale: 600)
        gerador.requestedTimeToleranceAfter = CMTime(seconds: 2, preferredTimescale: 600)

        do {
            let (imagem, quando) = try await gerador.image(at: CMTime(seconds: 60, preferredTimescale: 600))
            print("   ✅ QUADRO DECODIFICADO: \(imagem.width)x\(imagem.height) em \(Int(quando.seconds))s")
        } catch {
            print("   ❌ NÃO SAIU QUADRO: \(error.localizedDescription)")
        }
    }
}

/// A prova do caminho HLS — **53,3% do acervo depende dele**.
extension SondaDeReproducao {

    /// O 007 pt-BR: mkv/ac3, que o servidor classifica como `direct_stream`.
    private var mkvDeControle: String { "a2274591-541d-4e83-bbe3-6f1b35b6cc6a" }

    @Test("direct_stream: a playlist carimba o token, e o AVPlayer toca?")
    func hlsToca() async throws {
        let cofre = Cofre()
        guard cofre.sessao != nil else { print("⚠️ sem sessão"); return }

        let plano = try await odeon.plano(arquivo: mkvDeControle)
        print("── plano: mode=\(plano.mode) video=\(plano.video) audio=\(plano.audio)")
        guard !plano.eDireto else {
            print("   virou direct_play — esta sonda é do caminho HLS")
            return
        }

        let sessao = try await odeon.abrirSessao(arquivo: mkvDeControle, comecandoEm: 1500)
        defer { Task { await odeon.encerrarSessao(sessao.id) } }
        guard let url = try await odeon.urlDeMidia(sessao.urlDaPlaylist) else {
            Issue.record("não deu pra montar a URL da playlist"); return
        }

        /// ⚠️ Primeiro a pergunta do servidor: as linhas de segmento saem com
        /// token? Sem isso, o AVPlayer toma 401 e o sintoma é tela preta.
        let (dados, _) = try await URLSession.shared.data(from: url)
        let texto = String(data: dados, encoding: .utf8) ?? ""
        let linhasDeSegmento = texto.split(separator: "\n").filter { $0.hasSuffix(".ts") || $0.contains(".ts?") }
        let carimbadas = linhasDeSegmento.filter { $0.contains("token=") }
        print("   segmentos na playlist: \(linhasDeSegmento.count), com token: \(carimbadas.count)")
        if let primeira = linhasDeSegmento.first { print("   1ª linha: \(primeira.prefix(60))…") }

        /// ⚠️ **A pergunta que separa as duas hipóteses.**
        ///
        /// O `URLSession.shared` acima baixou a playlist com 200. O AVPlayer, na
        /// mesma URL e com o mesmo token, toma 401. A diferença candidata é o
        /// **cookie**: o `.shared` guarda o cookie de sessão desde o login, e o
        /// AVPlayer não compartilha esse armazenamento.
        ///
        /// Uma sessão efêmera não leva cookie nenhum — é o que o AVPlayer é.
        let efemera = URLSession(configuration: .ephemeral)
        let (dEf, rEf) = (try? await efemera.data(from: url)) ?? (Data(), URLResponse())
        let httpEf = (rEf as? HTTPURLResponse)?.statusCode ?? -1
        print("   playlist SEM COOKIE (como o AVPlayer): http=\(httpEf) bytes=\(dEf.count)")

        if let primeiroSeg = linhasDeSegmento.first {
            let segURL = url.deletingLastPathComponent().appendingPathComponent(String(primeiroSeg))
            let (dS, rS) = (try? await efemera.data(from: segURL)) ?? (Data(), URLResponse())
            print("   segmento SEM COOKIE: http=\((rS as? HTTPURLResponse)?.statusCode ?? -1) bytes=\(dS.count)")
        }

        /// ⚠️ E agora a prova de verdade: **o AVPlayer carrega mídia?** Playlist
        /// bonita e zero byte carregado foi exatamente o estado anterior.
        let item = AVPlayerItem(url: url)
        let player = AVPlayer(playerItem: item)
        player.play()

        var carregado = 0.0
        var status = -1
        for _ in 0 ..< 30 {
            try? await Task.sleep(for: .milliseconds(500))
            status = item.status.rawValue
            carregado = item.loadedTimeRanges.first?.timeRangeValue.duration.seconds ?? 0
            if carregado > 1 { break }
            if item.status == .failed { break }
        }
        player.pause()

        if let erro = item.error as NSError? {
            print("   ❌ falhou: \(erro.domain) \(erro.code) \(erro.localizedDescription)")
            for log in item.errorLog()?.events ?? [] {
                print("      log: \(log.errorStatusCode) uri=\(log.uri ?? "")")
            }
        }
        print("   status=\(status) carregado=\(String(format: "%.1f", carregado))s")
        if carregado > 1 {
            print("   ✅ HLS CARREGOU \(String(format: "%.1f", carregado))s de mídia")
        } else {
            print("   ❌ HLS NÃO CARREGOU MÍDIA")
        }
        #expect(carregado > 1, "o caminho HLS é 53% do acervo — se ele não carrega, o iOS não serve")
    }
}

/// A hipótese do token cadáver.
extension SondaDeReproducao {
    @Test func hlsComEsemSalto() async throws {
        let comecarEm = 0
        let cofre = Cofre()
        guard cofre.sessao != nil else { print("⚠️ sem sessão"); return }

        let antigo = cofre.tokenDeMidia ?? "(nenhum)"
        let novo = try await odeon.renovarTokenDeMidia()
        print("── token trocado: \(antigo.prefix(10))… → \(novo.prefix(10))…  (igual? \(antigo == novo))")

        print("════ start=\(comecarEm) ════")
        let sessao = try await odeon.abrirSessao(arquivo: "a2274591-541d-4e83-bbe3-6f1b35b6cc6a", comecandoEm: comecarEm)
        defer { Task { await odeon.encerrarSessao(sessao.id) } }
        guard let url = try await odeon.urlDeMidia(sessao.urlDaPlaylist) else { return }

        let efemera = URLSession(configuration: .ephemeral)
        let (d, r) = (try? await efemera.data(from: url)) ?? (Data(), URLResponse())
        print("   playlist SEM COOKIE, token novo: http=\((r as? HTTPURLResponse)?.statusCode ?? -1) bytes=\(d.count)")

        let item = AVPlayerItem(url: url)
        let player = AVPlayer(playerItem: item)
        player.play()
        var carregado = 0.0
        for volta in 0 ..< 48 {
            try? await Task.sleep(for: .milliseconds(500))
            carregado = item.loadedTimeRanges.first?.timeRangeValue.duration.seconds ?? 0
            if volta % 12 == 0 {
                let d = item.duration.seconds
                print("   t+\(volta / 2)s status=\(item.status.rawValue) carregado=\(String(format: "%.1f", carregado))s"
                    + " dur=\(d.isFinite ? String(format: "%.0f", d) : "indef")"
                    + " vazio=\(item.isPlaybackBufferEmpty) segura=\(item.isPlaybackLikelyToKeepUp)")
            }
            if carregado > 1 || item.status == .failed { break }
        }
        player.pause()
        if let e = item.error as NSError? { print("   ❌ \(e.code) \(e.localizedDescription)") }

        /// ⚠️ O log de **acesso** diz o que o AVPlayer chegou a pedir. «Não
        /// carrega» com zero evento é diferente de «pediu e não gostou» — e as
        /// duas causas não se parecem em nada.
        let eventos = item.accessLog()?.events ?? []
        print("   eventos de acesso: \(eventos.count)")
        for e in eventos.prefix(3) {
            print("      uri=\(e.uri ?? "?") bytes=\(e.numberOfBytesTransferred) stalls=\(e.numberOfStalls) taxa=\(e.observedBitrate)")
        }
        for e in (item.errorLog()?.events ?? []).prefix(3) {
            print("      ERRO \(e.errorStatusCode) \(e.errorComment ?? "") uri=\(e.uri ?? "?")")
        }
        print(carregado > 1 ? "   ✅ HLS CARREGOU \(String(format: "%.1f", carregado))s" : "   ❌ HLS NÃO CARREGOU")
        #expect(carregado > 1)
    }
}
