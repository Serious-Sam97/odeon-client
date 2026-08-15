import Foundation
import Observation

/// O que o app guarda sobre um arquivo baixado, além dos bytes.
///
/// ## ⚠️ Ela é gravada **junto** com o filme, e é isso que a torna útil
///
/// Título, arte e duração vêm do catálogo, que só existe com rede. Uma tela de
/// baixados que precisasse do servidor pra saber o nome do que está no disco
/// seria o contrário do que ela existe pra fazer — e o `Downloads.kt` do Android
/// escreve exatamente isso: «o arquivo tem que se desenhar sem rede, e um caminho
/// de arte que só existisse no catálogo faria a tela offline mostrar retângulo
/// cinza».
struct FichaDoBaixado: Codable, Sendable, Identifiable {
    let obraId: String
    let arquivoId: String
    let titulo: String
    let poster: String?
    /// ⚠️ A arte **deitada**, e não o pôster esticado. O cartão desta tela é uma
    /// faixa larga — proporção de backdrop. Um pôster é 2:3, e enfiá-lo ali corta
    /// cabeça ou pé de quem está na arte.
    let backdrop: String?
    let duracaoEmSegundos: Double?
    let ano: Int?

    /// A extensão do arquivo — `mp4`, `mov`, `m4v`.
    ///
    /// ## ⚠️ Ela **não é enfeite de nome**: sem ela o filme não abre
    ///
    /// Custou uma captura descobrir. O arquivo foi baixado inteiro (2,23 GB, a
    /// tela mostrou o tamanho certo), e ao tocar veio
    /// **`AVFoundationErrorDomain -11828: não é possível abrir`**. Ele estava
    /// gravado como `{id}.filme`, e o AVFoundation escolhe o demuxer de um arquivo
    /// local **pela extensão** — não olha os bytes, não fareja o `ftyp`. Um mp4
    /// perfeito com o nome errado é, pra ele, um formato desconhecido.
    ///
    /// ⚠️ E ela vem do `filename` do servidor, não de um palpite: o que decide é o
    /// arquivo que existe lá. `mp4` só quando não há extensão nenhuma no nome — e
    /// aí é o chute menos ruim, porque só `direct_play` chega aqui e o iPhone
    /// declara `mp4`, `mov` e `m4v`.
    let extensao: String

    var id: String { arquivoId }

    static func extensaoDe(_ filename: String) -> String {
        let e = (filename as NSString).pathExtension.lowercased()
        return e.isEmpty ? "mp4" : e
    }
}

/// Um baixado, do jeito que a tela precisa dele.
struct Baixado: Sendable, Identifiable {
    let ficha: FichaDoBaixado
    /// De 0 a 1. `nil` enquanto o servidor não disse o tamanho — e aí a barra
    /// **não desenha**, em vez de fingir 0% (§18).
    let fracao: Double?
    let bytes: Int64
    let bytesTotais: Int64?
    let pronto: Bool
    let falhou: Bool

    var id: String { ficha.arquivoId }
    var baixando: Bool { !pronto && !falhou }
}

/// A prateleira de arquivos no aparelho.
///
/// ## ⚠️ O que **não** dá pra baixar, e por que a regra é do iOS e não do produto
///
/// No Android baixa-se qualquer coisa: o ExoPlayer abre matroska, e **55% do
/// acervo é matroska**. O AVFoundation não abre. Um `.mkv` de 2 GB no iPhone é
/// disco gasto num arquivo que não vai abrir — o §53 na versão mais cara
/// possível, porque o custo não é um erro na tela, é o armazenamento da pessoa.
///
/// Então só desce o que o `/api/playback/…/plan` chama de **`direct_play`**. Não
/// é uma lista de extensões escrita aqui: é o mesmo servidor que decide o que
/// toca, respondendo a mesma pergunta. Medido em 15/08/2026 numa amostra de 38
/// arquivos: `direct_play` 55,3%, `direct_stream` 42,1%, `transcode` 2,6%.
///
/// ⚠️ E `direct_stream` **não** é baixável disfarçado. Ele é remux ao vivo: os
/// bytes que o iPhone conseguiria tocar não existem em arquivo nenhum — eles
/// nascem quando alguém pede. Baixar o original seria baixar o mkv de novo.
///
/// ## ⚠️ Onde os bytes moram, e os três detalhes que não são detalhe
///
/// | | |
/// |---|---|
/// | **Application Support**, não Caches | o iOS **apaga** Caches quando o disco aperta, e um filme que a pessoa baixou de propósito não é cache |
/// | fora do backup do iCloud | 2 GB por filme subindo pro iCloud da pessoa é conta dela sendo gasta sem ela pedir |
/// | índice ao lado dos bytes | uma segunda fonte de verdade divergiria no dia em que o iOS apagasse o arquivo por dentro |
@Observable
@MainActor
final class Baixados: NSObject {

    private(set) var itens: [Baixado] = []

    /// ⚠️ Tudo daqui pra baixo é `@ObservationIgnored`, e não é economia: o
    /// `@Observable` reescreve cada propriedade guardada num par de acessores, e
    /// isso é **incompatível com `lazy`** — a `sessao` não compilava. O que a tela
    /// observa é a `itens`, uma só, montada de propósito. Estado interno que
    /// dispara redesenho é redesenho que ninguém pediu.
    @ObservationIgnored private let odeon: RepositorioOdeon
    @ObservationIgnored private var fichas: [String: FichaDoBaixado] = [:]
    @ObservationIgnored private var emCurso: [String: URLSessionDownloadTask] = [:]
    @ObservationIgnored private var progresso: [String: (Int64, Int64?)] = [:]
    @ObservationIgnored private var falhados: Set<String> = []
    /// Quem já teve o token renovado nesta sessão do app. **Uma vez por filme.**
    ///
    /// ⚠️ Sem a trava isto vira laço, e o laço é caro: cada renovação
    /// **aposenta o token de mídia dos outros aparelhos da casa**. Um download que
    /// tentasse pra sempre derrubaria o filme de quem está na sala. É a mesma
    /// trava do `jaRenovou` do player, pela mesma razão.
    @ObservationIgnored private var jaRenovou: Set<String> = []

    /// ⚠️ Sessão **de fundo**, e não `.shared`. Um filme de 2 GB no servidor de
    /// casa leva minutos; com `.shared`, sair do app pro WhatsApp mataria o
    /// download no meio. É a diferença entre «baixados» e «baixados se você não
    /// tocar no telefone».
    ///
    /// ⚠️ E a retomada é de graça **porque foi medida**: o `/api/stream` responde
    /// `206` com `Accept-Ranges: bytes` e `Last-Modified` (15/08/2026), que é o
    /// que o `URLSession` precisa pra continuar de onde parou em vez de recomeçar.
    /// Sem essa resposta, uma queda de Wi-Fi custaria os 2 GB de novo — e aí a
    /// tela teria que avisar, em vez de fingir que retoma.
    @ObservationIgnored private lazy var sessao: URLSession = {
        let config = URLSessionConfiguration.background(withIdentifier: "dev.odeon.ios.baixados")
        config.isDiscretionary = false
        config.sessionSendsLaunchEvents = true
        return URLSession(configuration: config, delegate: self, delegateQueue: nil)
    }()

    init(odeon: RepositorioOdeon) {
        self.odeon = odeon
        super.init()
        carregarIndice()
        Task { await recolherOQueJaEstava() }
    }

    // MARK: - Onde as coisas moram

    private static var pasta: URL {
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask)[0]
        let pasta = base.appending(path: "baixados", directoryHint: .isDirectory)
        try? FileManager.default.createDirectory(at: pasta, withIntermediateDirectories: true)
        return pasta
    }

    private static var indice: URL { pasta.appending(path: "indice.json") }

    /// O arquivo no disco.
    ///
    /// ⚠️ O **nome** é o id do arquivo, e não a URL: a URL carrega o token de
    /// mídia, que muda a cada renovação — o mesmo filme viraria dois downloads. O
    /// `Downloads.kt` do Android registra o mesmo tropeço, resolvido com o mesmo
    /// `customCacheKey`.
    ///
    /// ⚠️ E a **extensão** é a do servidor, porque sem ela o AVFoundation não abre
    /// o arquivo — ver `FichaDoBaixado.extensao`.
    static func arquivoNoDisco(_ ficha: FichaDoBaixado) -> URL {
        pasta.appending(path: ficha.arquivoId + "." + ficha.extensao)
    }

    // MARK: - A prateleira

    func baixar(ficha: FichaDoBaixado) async throws {
        guard fichas[ficha.arquivoId] == nil else { return }

        /// ⚠️ O plano vem **antes** dos bytes, e é a trava do §53: se este arquivo
        /// não é `direct_play`, o download seria disco gasto num filme que o
        /// iPhone não abre. Perguntar custa uma requisição; descobrir depois custa
        /// 2 GB.
        let plano = try await odeon.plano(arquivo: ficha.arquivoId)
        guard plano.eDireto, let direta = plano.urlDireta,
              let url = try await odeon.urlDeMidia(direta) else {
            throw FalhaDoOdeon.recado("este filme não é guardável no iPhone — ele precisa do servidor pra tocar")
        }

        fichas[ficha.arquivoId] = ficha
        falhados.remove(ficha.arquivoId)
        gravarIndice()

        let tarefa = sessao.downloadTask(with: url)
        tarefa.taskDescription = ficha.arquivoId
        emCurso[ficha.arquivoId] = tarefa
        tarefa.resume()
        refazerLista()
    }

    func apagar(_ arquivoId: String) {
        let ficha = fichas[arquivoId]
        emCurso[arquivoId]?.cancel()
        emCurso[arquivoId] = nil
        progresso[arquivoId] = nil
        falhados.remove(arquivoId)
        fichas[arquivoId] = nil
        if let ficha { try? FileManager.default.removeItem(at: Self.arquivoNoDisco(ficha)) }
        gravarIndice()
        refazerLista()
    }

    /// Quanto o app está ocupando. ⚠️ Somado **do disco**, e não do índice: o
    /// índice diz o que deveria estar lá, e o disco diz o que está. Numa tela de
    /// armazenamento, a diferença entre as duas é o assunto.
    var bytesNoDisco: Int64 {
        itens.reduce(0) { $0 + $1.bytes }
    }

    // MARK: - O índice

    private func carregarIndice() {
        guard let dados = try? Data(contentsOf: Self.indice),
              let lista = try? JSONDecoder().decode([FichaDoBaixado].self, from: dados) else { return }
        fichas = Dictionary(uniqueKeysWithValues: lista.map { ($0.arquivoId, $0) })
    }

    private func gravarIndice() {
        guard let dados = try? JSONEncoder().encode(Array(fichas.values)) else { return }
        try? dados.write(to: Self.indice, options: .atomic)
    }

    /// Reconecta com o que a sessão de fundo terminou enquanto o app estava
    /// fechado — o motivo de a sessão existir.
    private func recolherOQueJaEstava() async {
        let tarefas = await sessao.allTasks
        for t in tarefas {
            guard let id = t.taskDescription, let d = t as? URLSessionDownloadTask else { continue }
            emCurso[id] = d
        }
        refazerLista()
    }

    fileprivate func anotarProgresso(_ id: String, _ feito: Int64, _ total: Int64) {
        progresso[id] = (feito, total > 0 ? total : nil)
        refazerLista()
    }

    /// ## ⚠️ O defeito que isto conserta esteve na tela, e era invisível
    ///
    /// O `URLSessionDownloadTask` **não sabe o que é erro**. Um `401` chega no
    /// `didFinishDownloadingTo` exatamente como um `200`: com um arquivo
    /// temporário na mão, «pronto». Visto em 15/08/2026 — o filme «baixou», o
    /// cartão apareceu, e ao tocar veio `AVFoundationErrorDomain -11829`. No
    /// disco havia **54 bytes**:
    ///
    /// ```
    /// {"error":"credenciais inválidas ou sessão expirada"}
    /// ```
    ///
    /// Um recado de erro gravado com nome de filme. E o mais caro: **nada na tela
    /// era falso** — o cartão dizia o que o disco tinha. O defeito só apareceu no
    /// player, três telas depois de onde nasceu.
    ///
    /// ⚠️ Por isso o status é conferido **antes** de o arquivo virar filme. E não
    /// é lista de códigos que dão certo: é o contrário — o que não é `2xx` não é
    /// filme, seja qual for o número.
    fileprivate func guardar(_ id: String, de temporario: URL, status: Int) {
        guard let ficha = fichas[id] else { return }

        guard (200 ..< 300).contains(status) else {
            try? FileManager.default.removeItem(at: temporario)
            emCurso[id] = nil
            progresso[id] = nil

            /// ⚠️ 401 aqui quase sempre é **token de mídia vencido**, não sessão
            /// perdida: ele dura pouco, e o servidor apaga o dos outros clientes a
            /// cada emissão. Renovar e pedir de novo — uma vez — é o mesmo remédio
            /// que o player já usa; sem ele, guardar um filme falharia sempre que
            /// alguém tivesse aberto o app na TV.
            if status == 401, !jaRenovou.contains(id) {
                jaRenovou.insert(id)
                Task { await tentarDeNovo(ficha) }
                return
            }
            falhados.insert(id)
            refazerLista()
            return
        }

        let destino = Self.arquivoNoDisco(ficha)
        try? FileManager.default.removeItem(at: destino)
        do {
            try FileManager.default.moveItem(at: temporario, to: destino)
            /// ⚠️ Fora do backup. Sem esta linha, cada filme baixado sobe pro
            /// iCloud da pessoa — 2 GB de uma conta que ela paga, gastos por uma
            /// decisão que ela não tomou. E a Apple rejeita app que faz isso.
            var url = destino
            var valores = URLResourceValues()
            valores.isExcludedFromBackup = true
            try? url.setResourceValues(valores)
        } catch {
            falhados.insert(id)
        }
        emCurso[id] = nil
        progresso[id] = nil
        refazerLista()
    }

    private func tentarDeNovo(_ ficha: FichaDoBaixado) async {
        guard let url = try? await odeon.renovarTokenDeMidia(),
              !url.isEmpty else { falhados.insert(ficha.arquivoId); refazerLista(); return }
        fichas[ficha.arquivoId] = nil
        try? await baixar(ficha: ficha)
    }

    fileprivate func anotarFalha(_ id: String) {
        emCurso[id] = nil
        progresso[id] = nil
        falhados.insert(id)
        refazerLista()
    }

    private func refazerLista() {
        let fm = FileManager.default
        itens = fichas.values
            .map { ficha in
                let id = ficha.arquivoId
                let noDisco = Self.arquivoNoDisco(ficha)
                let tamanho = (try? fm.attributesOfItem(atPath: noDisco.path)[.size] as? Int64) ?? nil
                let pronto = tamanho != nil
                let (feito, total) = progresso[id] ?? (0, nil)
                return Baixado(
                    ficha: ficha,
                    /// ⚠️ `nil` enquanto o servidor não disse o tamanho — e aí a
                    /// barra **não desenha**. Barra em 0% que não anda é
                    /// indistinguível de barra travada.
                    fracao: pronto ? 1 : total.map { Double(feito) / Double($0) },
                    bytes: pronto ? (tamanho ?? 0) : feito,
                    bytesTotais: total,
                    pronto: pronto,
                    falhou: !pronto && falhados.contains(id),
                )
            }
            .sorted { $0.ficha.titulo < $1.ficha.titulo }
    }
}

/// ⚠️ O delegate é **separado do observável**, e `@unchecked Sendable` de
/// propósito: o `URLSession` chama de uma fila própria, e o `Baixados` é
/// `@MainActor`. Marcar a classe inteira como delegate faria o compilador do
/// Swift 6 pedir `nonisolated` em cada método — e aí o estado teria dois donos.
/// Assim há uma porta só: tudo entra pelo `Task { @MainActor }`.
extension Baixados: URLSessionDownloadDelegate {

    nonisolated func urlSession(
        _ session: URLSession, downloadTask: URLSessionDownloadTask,
        didWriteData _: Int64, totalBytesWritten: Int64, totalBytesExpectedToWrite: Int64,
    ) {
        guard let id = downloadTask.taskDescription else { return }
        Task { @MainActor in anotarProgresso(id, totalBytesWritten, totalBytesExpectedToWrite) }
    }

    nonisolated func urlSession(
        _ session: URLSession, downloadTask: URLSessionDownloadTask,
        didFinishDownloadingTo location: URL,
    ) {
        guard let id = downloadTask.taskDescription else { return }
        /// ⚠️ O arquivo temporário morre **quando este método retorna** — e este
        /// método não é `async`. Mover pra um lugar nosso aqui, síncrono, é a
        /// única forma; passar a URL pro `MainActor` num `Task` entregaria um
        /// caminho que já não existe.
        let abrigo = FileManager.default.temporaryDirectory.appending(path: id + ".mudando")
        try? FileManager.default.removeItem(at: abrigo)
        try? FileManager.default.moveItem(at: location, to: abrigo)
        let status = (downloadTask.response as? HTTPURLResponse)?.statusCode ?? 0
        Task { @MainActor in guardar(id, de: abrigo, status: status) }
    }

    nonisolated func urlSession(
        _ session: URLSession, task: URLSessionTask, didCompleteWithError error: Error?,
    ) {
        guard let id = task.taskDescription, error != nil else { return }
        /// Cancelar é o «apagar» da tela, e ele já limpou tudo — anotar falha aqui
        /// faria a linha reaparecer dizendo que deu errado.
        if (error as? URLError)?.code == .cancelled { return }
        Task { @MainActor in anotarFalha(id) }
    }
}

extension Int64 {
    /// `2229618268` → `2,1 GB`.
    var emTamanho: String {
        let f = ByteCountFormatter()
        f.countStyle = .file
        return f.string(fromByteCount: self)
    }
}
