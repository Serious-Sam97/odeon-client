import Foundation
import Observation

/// Uma temporada, montada aqui.
///
/// ## ⚠️ Ela **não existe no servidor**
///
/// Não há rota nem entidade de temporada: o que existe é o `season_number` em
/// cada obra. Tudo abaixo é agrupamento feito no cliente, e é por isso que
/// [arte] tem reserva — o pedido de pôster de temporada do TMDB está no
/// `android/docs/PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`.
///
/// Espelha `TemporadaDaSerie` do Android, regra por regra. As duas foram
/// escritas duas vezes de propósito: são plataformas diferentes, e um pacote
/// compartilhado entre Kotlin e Swift custaria mais que as quarenta linhas.
struct TemporadaDaSerie: Identifiable, Sendable {
    /// A chave de quem não tem `season_number`. Negativa pra ordenar **antes** da
    /// 0 e da 1 sem disputar número com temporada de verdade.
    static let semTemporada = -1

    let numero: Int
    let episodios: [ObraDaLista]
    /// Hoje o `still` do **primeiro episódio** — a única imagem do acervo que
    /// pertence àquela temporada e não à série.
    var arte: String?
    /// O nome próprio da temporada, quando existe (26 das 473). ⚠️ `nil` é o
    /// caso comum, e aí o rótulo é `Temporada N`. O servidor **não grava** o
    /// «Temporada 3» que o TMDB devolve traduzido — então um nome aqui é sempre
    /// um nome de verdade.
    var nome: String?
    /// A sinopse da temporada (232 das 473).
    var sinopse: String?

    var id: Int { numero }
    var quantos: Int { episodios.count }
    var vistos: Int { episodios.filter { $0.finished == true }.count }

    /// ⚠️ `nil` quando nada foi visto — e não `0`. A tela **não desenha** a barra
    /// nesse caso: uma barra vazia afirma «começou» sobre o que ninguém tocou.
    var andado: Double? {
        guard quantos > 0, vistos > 0 else { return nil }
        return Double(vistos) / Double(quantos)
    }

    /// `Temporada 3`, `Especiais`, `Sem temporada`.
    ///
    /// ⚠️ A temporada **0** é «Especiais», e é convenção do meio — especiais,
    /// piloto não exibido e natalinos entram como 0 no TMDB. Escrever
    /// «Temporada 0» seria inventar uma temporada que ninguém chama assim.
    var rotulo: String {
        if let nome, !nome.isEmpty { return nome }
        switch numero {
        case Self.semTemporada: return "Sem temporada"
        case 0: return "Especiais"
        default: return "Temporada \(numero)"
        }
    }
}

/// Onde a pessoa parou dentro da série, se parou.
struct OndeParouNaSerie: Sendable {
    let episodio: ObraDaLista
    /// `true` quando o episódio foi **começado**; `false` quando ele é apenas o
    /// próximo. Separa «continuar» de «começar» no botão.
    let comecado: Bool
}

/// Os episódios virados em temporadas.
///
/// ⚠️ **Sem temporada ganha grupo próprio**, e não cai na 1: dobrá-lo afirmaria
/// uma temporada que o servidor não informou. Dentro da temporada, **sem número
/// vai pro fim** — ordenar `nil` como zero poria um não identificado antes do
/// piloto.
func agruparEmTemporadas(_ todos: [ObraDaLista]) -> [TemporadaDaSerie] {
    Dictionary(grouping: todos) { $0.temporada ?? TemporadaDaSerie.semTemporada }
        .sorted { $0.key < $1.key }
        .map { numero, eps in
            let ordenados = eps.sorted { ($0.episodio ?? .max) < ($1.episodio ?? .max) }
            return TemporadaDaSerie(
                numero: numero,
                episodios: ordenados,
                arte: ordenados.compactMap(\.arte).first,
            )
        }
}

/// Onde parar — o que o botão principal da ficha vai oferecer.
///
/// O **começado** ganha do próximo: ele é onde a pessoa estava, e o primeiro não
/// visto é só onde ela chegaria.
///
/// ⚠️ Série inteira vista **não** fica sem botão — volta o primeiro episódio,
/// como «começar». Um `nil` apagaria a única ação da tela justamente de quem
/// mais gostou dela.
func ondeParar(_ temporadas: [TemporadaDaSerie]) -> OndeParouNaSerie? {
    let emOrdem = temporadas.flatMap(\.episodios)
    if let comecado = emOrdem.first(where: { ($0.ondeParou ?? 0) > 0 && $0.finished != true }) {
        return OndeParouNaSerie(episodio: comecado, comecado: true)
    }
    if let proximo = emOrdem.first(where: { $0.finished != true }) {
        return OndeParouNaSerie(episodio: proximo, comecado: false)
    }
    return emOrdem.first.map { OndeParouNaSerie(episodio: $0, comecado: false) }
}

/// A ficha de uma série: as temporadas dela, e onde a pessoa parou.
///
/// ## ⚠️ Carrega a série **inteira** antes de mostrar
///
/// Agrupar por temporada exige ter todos os episódios: com meia lista, a
/// «Temporada 5» apareceria com 2 episódios porque os outros 11 estão na página
/// seguinte — e um número errado é pior que um número ausente.
@MainActor
@Observable
final class ModeloDaSerie {
    private let odeon: RepositorioOdeon
    private let serieId: String

    let titulo: String
    private(set) var carregando = true
    private(set) var erro: String?
    private(set) var temporadas: [TemporadaDaSerie] = []
    private(set) var ondeParou: OndeParouNaSerie?
    private(set) var ano: Int?
    private(set) var panoDeFundo: String?
    /// A sinopse da série — 115 das 120 têm. ⚠️ `nil` não vira texto de
    /// enchimento: a tela não desenha o parágrafo (§24).
    private(set) var sinopse: String?
    private(set) var tituloDoServidor: String?

    var quantosEpisodios: Int { temporadas.reduce(0) { $0 + $1.quantos } }
    var quantosVistos: Int { temporadas.reduce(0) { $0 + $1.vistos } }
    var vazio: Bool { !carregando && erro == nil && temporadas.isEmpty }

    func temporada(_ numero: Int) -> TemporadaDaSerie? {
        temporadas.first { $0.numero == numero }
    }

    init(odeon: RepositorioOdeon, serieId: String, titulo: String) {
        self.odeon = odeon
        self.serieId = serieId
        self.titulo = titulo
    }

    func carregar() async {
        carregando = true
        erro = nil
        do {
            var todos: [ObraDaLista] = []
            var volta = 0
            /// ⚠️ O teto existe pra que um `collection` que o servidor nunca
            /// termine não gire pra sempre. 12 × 60 = 720 episódios.
            while volta < 12 {
                let pagina = try await odeon.obras(colecao: serieId, pulando: todos.count)
                todos.append(contentsOf: pagina)
                if pagina.count < 60 { break }
                volta += 1
            }
            temporadas = agruparEmTemporadas(todos)
            ondeParou = ondeParar(temporadas)
            ano = todos.compactMap(\.year).first
            let primeiro = temporadas.first?.episodios.first
            panoDeFundo = primeiro?.backdrop ?? primeiro?.still ?? primeiro?.poster

            /// ⚠️ A coleção **enriquece e não bloqueia**: pôster de temporada,
            /// sinopse e backdrop da série. Campo a campo, sempre com reserva —
            /// 12 temporadas não têm pôster e 5 séries não têm sinopse, e
            /// nenhuma delas pode piorar por causa disso.
            if let colecao = await odeon.colecao(serieId) {
                sinopse = colecao.collection.overview?.isEmpty == false
                    ? colecao.collection.overview : sinopse
                tituloDoServidor = colecao.collection.title.isEmpty ? nil : colecao.collection.title
                panoDeFundo = colecao.collection.backdrop ?? panoDeFundo
                if let ano = colecao.collection.year { self.ano = ano }
                let porNumero = Dictionary(
                    colecao.children.compactMap { c in c.position.map { ($0, c) } },
                    uniquingKeysWith: { a, _ in a },
                )
                temporadas = temporadas.map { t in
                    guard let doServidor = porNumero[t.numero] else { return t }
                    var novo = t
                    novo.arte = doServidor.poster ?? t.arte
                    novo.nome = doServidor.title.isEmpty ? nil : doServidor.title
                    novo.sinopse = doServidor.overview?.isEmpty == false ? doServidor.overview : nil
                    return novo
                }
            }
            carregando = false
        } catch {
            /// A mesma fala da ficha: o `FalhaDoOdeon` já traduz 401, HTTP e
            /// rede; o texto genérico só entra quando não é nenhum dos três.
            erro = (error as? FalhaDoOdeon)?.errorDescription ?? "a série não abriu"
            carregando = false
        }
    }

    func arte(_ caminho: String?) -> URL? { odeon.urlDaArte(caminho) }
}
