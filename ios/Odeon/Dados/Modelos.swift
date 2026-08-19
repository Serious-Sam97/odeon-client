import Foundation

/// O contrato com o servidor, escrito à mão.
///
/// ## ⚠️ É a **terceira** cópia, e foi escolha — não acidente
///
/// A primeira é `web/src/api.ts`; a segunda é `android/core/…/dados/Modelos.kt`,
/// com 1.423 linhas comentadas. Não há tipo compartilhado, não há código gerado e
/// não há import cruzado — a espec registrou isso como «a dívida que a separação
/// dos repositórios comprou», e o `PLANO.md` §1 registra que a aumentamos de duas
/// para três de olhos abertos.
///
/// **Quem mexer no contrato mexe nos três.** O terceiro é o que ninguém lembra.
///
/// ## ⚠️ Todo campo opcional é opcional aqui também
///
/// Os `?` abaixo não são cautela: são o que o servidor devolve. Um `poster` nulo
/// é uma obra sem arte baixada — e são **8.598 de 17.930**, ou seja 48% do
/// acervo. Declarar não-nulo faria a biblioteca falhar em quase metade do que
/// lista.
///
/// E o §18 manda o corolário: quando o dado não existe, a tela **omite**. Não
/// inventa, não escreve «—».
///
/// ## ⚠️ O `Codable` do Swift é mais rígido que o `kotlinx-serialization`
///
/// Lá, um campo com valor padrão e chave ausente **funciona**; aqui, chave
/// ausente **lança**, mesmo com valor padrão declarado. Isso importa porque o
/// servidor omite chave de propósito — `versions` some quando o filme tem uma
/// versão só, que é o caso de 8.230 das 8.273 entradas.
///
/// Sem tolerância explícita, o app quebraria exatamente no caso normal. Por isso
/// existe o [ou] abaixo, e por isso os `init(from:)` escritos à mão: eles são a
/// tradução do que o Kotlin ganhava de graça.
///
/// ## O que ainda **não** está mapeado
///
/// Locadora, mural, guia, perfil, para-você, ao vivo e baixados. Eles entram na
/// F6 do `PLANO.md`. Declarar contrato que nenhuma tela lê é contrato que ninguém
/// confere — é a mesma régua que o `ObraDetalhada` do Android aplicou às `tags`
/// até haver tela que as desenhasse.
enum Contrato {}

// MARK: - Tolerância

extension KeyedDecodingContainer {
    /// Lê a chave, e cai no padrão quando ela **não veio** ou veio nula.
    ///
    /// ⚠️ Ele engole o erro de tipo também, e isso é deliberado: um campo que
    /// mudou de `Int` pra `String` no servidor derruba a tela inteira se lançar,
    /// e aqui vira «o valor padrão» — que é degradar em vez de morrer. A mesma
    /// escolha do `label` da faixa de áudio no Android, cujo comentário diz que
    /// campo obrigatório «transformaria uma renomeação de JSON em filme que não
    /// abre».
    func ou<T: Decodable>(_ chave: Key, _ padrao: T) -> T {
        ((try? decodeIfPresent(T.self, forKey: chave)) ?? nil) ?? padrao
    }

    /// Lê a chave e devolve `nil` sem lançar.
    func talvez<T: Decodable>(_ chave: Key) -> T? {
        (try? decodeIfPresent(T.self, forKey: chave)) ?? nil
    }
}

// MARK: - Autenticação

/// `POST /api/auth/login`
struct Credenciais: Encodable, Sendable {
    let username: String
    let password: String
    /// O nome do aparelho, e ele importa mais do que parece: a tela de aparelhos
    /// do admin lista as sessões por este rótulo, e sessão sem rótulo aparece
    /// anônima.
    let deviceLabel: String?

    enum CodingKeys: String, CodingKey {
        case username, password
        case deviceLabel = "device_label"
    }
}

/// A resposta do login. O `token` é o de **sessão**: 90 dias, `Bearer`.
struct RespostaDeLogin: Decodable, Sendable {
    let token: String
    let user: Usuario
}

struct Usuario: Decodable, Sendable, Identifiable {
    let id: String
    let username: String
    let displayName: String
    /// `"admin"` ou `"user"`.
    ///
    /// ⚠️ Não vira booleano aqui: o servidor manda o papel, e reduzir a «é
    /// admin?» perderia o dia em que houver um terceiro.
    let role: String
    let ativo: Bool

    var eAdmin: Bool { role == "admin" }

    enum CodingKeys: String, CodingKey {
        case id, username, role
        case displayName = "display_name"
        case ativo = "is_active"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        username = c.ou(.username, "")
        displayName = c.ou(.displayName, "")
        role = c.ou(.role, "user")
        ativo = c.ou(.ativo, true)
    }
}

/// `GET /api/auth/status` — a única rota que responde **sem sessão**.
///
/// Ela serve pra duas coisas, e a segunda é a que a tela de login usa: descobrir
/// se aquele endereço **é** um Odeon antes de mandar senha pra ele.
struct StatusDoServidor: Decodable, Sendable {
    let precisaConfigurar: Bool

    enum CodingKeys: String, CodingKey {
        case precisaConfigurar = "needs_setup"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        precisaConfigurar = c.ou(.precisaConfigurar, false)
    }
}

/// `POST /api/auth/media-token` — o token curto que **só abre bytes**.
struct TokenDeMidia: Decodable, Sendable {
    let token: String
}

// MARK: - A biblioteca

/// `GET /api/library` — uma linha por **série** ou obra avulsa.
///
/// ## Não é `/api/works`, e a diferença é a tela inteira
///
/// `/api/works` devolve obra por obra: os 14.657 episódios do acervo viram 14.657
/// cartões iguais — «é listagem de arquivo e não biblioteca». `/api/library`
/// agrupa: uma série é uma linha.
struct ItemDaBiblioteca: Decodable, Sendable, Identifiable {
    let id: String
    let eSerie: Bool
    let title: String
    let year: Int?
    /// Caminho relativo, servido em `/artwork/…`. Nulo enquanto não identificado.
    let poster: String?
    /// A cor extraída do pôster pelo servidor. É o que deixa a interface se
    /// tingir com a obra sem custar requisição nenhuma.
    let corDominante: String?
    let quantasObras: Int
    let quantasTemporadas: Int
    let quantasVistas: Int
    let arquivoId: String?
    let duracaoEmSegundos: Double?
    let height: Int?
    let tamanhoEmBytes: Int64?
    let kind: String?
    let estadoDaIdentificacao: String?
    let ondeParou: Double?

    /// As versões deste filme — os rips que o servidor agrupou numa entrada só.
    ///
    /// ⚠️ **Vazia no caso normal**, e isso é o contrato: o servidor **omite a
    /// chave** quando há uma versão só, que é o caso de 8.230 das 8.273 entradas.
    /// São **43 grupos** no acervo inteiro (medido em 14/08/2026).
    ///
    /// ⚠️ A chave do agrupamento é a **identificação** (`external_ids->>'tmdb'`),
    /// nunca título+ano. Só filme, e só o que já foi identificado.
    let versoes: [VersaoDaObra]

    /// Repetido em toda linha: o total de entradas do filtro atual.
    ///
    /// ⚠️ **Conta grupos**, e não rips — a grade escreve «carregadas / total» e a
    /// paginação para em `quantosNaTela < total`.
    let total: Int

    /// As versões entre as quais dá pra escolher de verdade.
    ///
    /// ⚠️ Versão sem `id` é descartada: sem ele a escolha não tem pra onde mandar.
    var versoesEscolhiveis: [VersaoDaObra] { versoes.filter { !$0.id.isEmpty } }

    /// ⚠️ **Uma versão só nunca abre escolha.** Menu com uma opção é pergunta sem
    /// resposta alternativa — o §24 aplicado a um menu.
    var temEscolhaDeVersao: Bool { versoesEscolhiveis.count > 1 }

    enum CodingKeys: String, CodingKey {
        case id, title, year, poster, kind, total, height, versions
        case eSerie = "is_series"
        case corDominante = "dominant_color"
        case quantasObras = "work_count"
        case quantasTemporadas = "season_count"
        case quantasVistas = "finished_count"
        case arquivoId = "media_file_id"
        case duracaoEmSegundos = "duration_seconds"
        case tamanhoEmBytes = "size_bytes"
        case estadoDaIdentificacao = "match_state"
        case ondeParou = "position_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = c.ou(.title, "")
        eSerie = c.ou(.eSerie, false)
        year = c.talvez(.year)
        poster = c.talvez(.poster)
        corDominante = c.talvez(.corDominante)
        quantasObras = c.ou(.quantasObras, 0)
        quantasTemporadas = c.ou(.quantasTemporadas, 0)
        quantasVistas = c.ou(.quantasVistas, 0)
        arquivoId = c.talvez(.arquivoId)
        duracaoEmSegundos = c.talvez(.duracaoEmSegundos)
        height = c.talvez(.height)
        tamanhoEmBytes = c.talvez(.tamanhoEmBytes)
        kind = c.talvez(.kind)
        estadoDaIdentificacao = c.talvez(.estadoDaIdentificacao)
        ondeParou = c.talvez(.ondeParou)
        versoes = c.ou(.versions, [])
        total = c.ou(.total, 0)
    }
}

/// Uma versão de um filme — um dos rips que o servidor agrupou.
///
/// ## ⚠️ Ela **não** é um arquivo, é uma **obra**
///
/// Id próprio, progresso próprio, ficha própria. Confundi-la com o arquivo é o
/// caminho mais curto pra **fundir** o que só devia ser **agrupado** — e fundir
/// apagaria o `position_seconds` de uma das duas, que é a objeção que segurou o
/// pedido de 04/08 a 14/08/2026.
/// Uma obra da listagem **plana** — um episódio, dentro de uma série.
///
/// ## ⚠️ Ela não é a `ItemDaBiblioteca`, e a diferença é o agrupamento
///
/// `/api/library` devolve o acervo **agrupado**: uma série é uma entrada só, com
/// `season_count` e `work_count`. `/api/works?collection=…` devolve o mesmo
/// acervo **plano** — cada episódio uma linha, com `season_number` e
/// `episode_number`. É essa a listagem que a ficha da série e a visão de
/// temporada leem.
///
/// Espelha a `ObraDaLista` do Android, campo a campo. Ver
/// `core/.../dados/Modelos.kt`.
struct ObraDaLista: Decodable, Sendable, Identifiable {
    let id: String
    let title: String
    let year: Int?
    let temporada: Int?
    let episodio: Int?
    let corDominante: String?
    let poster: String?
    let backdrop: String?
    let still: String?
    let tituloDaSerie: String?
    /// A sinopse do episódio — chegou em 18/08/2026, em 7.628 dos 14.844.
    /// ⚠️ Quem não tem manda nulo, e aí a linha não é desenhada (§18).
    let overview: String?
    let arquivoId: String?
    let duracaoEmSegundos: Double?
    let ondeParou: Double?
    let finished: Bool?

    /// A arte do cartão, da mais específica pra menos.
    ///
    /// ⚠️ O `still` **ganha**, e é o desenho inteiro da lista de episódios: um
    /// quadro do próprio episódio distingue os nove de uma temporada; o pôster
    /// da série é o mesmo nos nove.
    var arte: String? { still ?? backdrop ?? poster }

    /// `S01E04`. ⚠️ `nil` quando falta um dos dois — meio código é pior que
    /// nenhum, e um `S01E` sozinho não diz que episódio é.
    var codigo: String? {
        if let temporada, let episodio {
            String(format: "S%02dE%02d", temporada, episodio)
        } else if let episodio {
            "ep \(episodio)"
        } else {
            nil
        }
    }

    enum CodingKeys: String, CodingKey {
        case id, title, year, poster, backdrop, still, finished, overview
        case temporada = "season_number"
        case episodio = "episode_number"
        case corDominante = "dominant_color"
        case tituloDaSerie = "series_title"
        case arquivoId = "media_file_id"
        case duracaoEmSegundos = "duration_seconds"
        case ondeParou = "position_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        year = try c.decodeIfPresent(Int.self, forKey: .year)
        temporada = try c.decodeIfPresent(Int.self, forKey: .temporada)
        episodio = try c.decodeIfPresent(Int.self, forKey: .episodio)
        corDominante = try c.decodeIfPresent(String.self, forKey: .corDominante)
        poster = try c.decodeIfPresent(String.self, forKey: .poster)
        backdrop = try c.decodeIfPresent(String.self, forKey: .backdrop)
        still = try c.decodeIfPresent(String.self, forKey: .still)
        tituloDaSerie = try c.decodeIfPresent(String.self, forKey: .tituloDaSerie)
        overview = try c.decodeIfPresent(String.self, forKey: .overview)
        arquivoId = try c.decodeIfPresent(String.self, forKey: .arquivoId)
        duracaoEmSegundos = try c.decodeIfPresent(Double.self, forKey: .duracaoEmSegundos)
        ondeParou = try c.decodeIfPresent(Double.self, forKey: .ondeParou)
        finished = try c.decodeIfPresent(Bool.self, forKey: .finished)
    }
}

struct VersaoDaObra: Decodable, Sendable, Identifiable {
    /// O id da **obra**, e é por ele que a ficha abre.
    ///
    /// ⚠️ Padrão vazio de propósito: campo obrigatório transformaria uma
    /// renomeação de JSON em **biblioteca que não carrega**. Sem id, a versão cai
    /// fora de `versoesEscolhiveis` e o cartão volta a abrir a obra representante
    /// — degrada, não morre.
    let id: String
    let arquivoId: String?
    let height: Int?
    let tamanhoEmBytes: Int64?
    let duracaoEmSegundos: Double?

    /// Os idiomas do áudio, em ISO 639 — `["por"]`.
    ///
    /// ⚠️ **Vem vazia quando o arquivo não declara**, e é o caso do 007 em inglês
    /// deste acervo: a faixa aac dele não traz idioma. O servidor **recusa**
    /// mandar `und` (*undetermined*), e a recusa é a certa — escrever «und» como
    /// idioma faria a tela oferecer «und» como escolha.
    ///
    /// ⚠️ A consequência é de produto: a escolha consegue escrever «Português» e
    /// **não** consegue escrever «Inglês». Quem conserta é o acervo.
    let idiomasDeAudio: [String]

    /// Onde **este** usuário parou nesta versão.
    ///
    /// ⚠️ É o campo que salva a escolha. Com um lado sem idioma, «parou em 1h22»
    /// é o que de fato distingue as duas — sem ele seria `818p` contra `816p`.
    let ondeParou: Double?
    let finished: Bool

    enum CodingKeys: String, CodingKey {
        case id, height, finished
        case arquivoId = "media_file_id"
        case tamanhoEmBytes = "size_bytes"
        case duracaoEmSegundos = "duration_seconds"
        case idiomasDeAudio = "audio_langs"
        case ondeParou = "position_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        arquivoId = c.talvez(.arquivoId)
        height = c.talvez(.height)
        tamanhoEmBytes = c.talvez(.tamanhoEmBytes)
        duracaoEmSegundos = c.talvez(.duracaoEmSegundos)
        idiomasDeAudio = c.ou(.idiomasDeAudio, [])
        ondeParou = c.talvez(.ondeParou)
        finished = c.ou(.finished, false)
    }
}

// MARK: - Continuar

/// `GET /api/continue` — de onde continuar.
///
/// ## A arte preferida aqui **não** é o pôster
///
/// `still` é o quadro daquele episódio, `backdrop` é a arte larga, `poster` é a
/// vertical da grade. Numa fileira de «continuar», a mais específica ganha: quem
/// parou no meio de um episódio reconhece o quadro dele antes da capa da série.
struct ItemPraContinuar: Decodable, Sendable, Identifiable {
    let id: String
    let title: String
    let year: Int?
    let tituloDaSerie: String?
    let temporada: Int?
    let episodio: Int?
    let poster: String?
    let backdrop: String?
    let still: String?
    let corDominante: String?
    let arquivoId: String?
    let duracaoEmSegundos: Double?
    let ondeParou: Double?
    let finished: Bool?

    /// A arte da fileira, da mais específica pra menos. `nil` quando não há
    /// nenhuma — e aí o cartão mostra o título sobre a cor da obra.
    var arte: String? { still ?? backdrop ?? poster }

    /// Quanto do filme já passou, de 0 a 1. `nil` quando não dá pra saber — e aí
    /// a barrinha **não aparece**, em vez de aparecer zerada (§24).
    /// «faltam 143min». `nil` quando não dá pra calcular.
    ///
    /// ## ⚠️ Quanto **falta**, e não quanto já foi
    ///
    /// É a frase do Android, e a escolha é o que a tela é para: quem abre o app
    /// às onze da noite decide pelo que sobra, não pelo que passou. «Parou em
    /// 11min» é história; «faltam 143min» é a pergunta respondida.
    var quantoFalta: String? {
        guard let duracao = duracaoEmSegundos, duracao > 0, let onde = ondeParou else { return nil }
        let restam = Int((duracao - onde) / 60)
        /// Abaixo de um minuto não sobra nada que valha uma frase — e «faltam
        /// 0min» seria o mesmo defeito do «continuar de 0min» da ficha.
        guard restam >= 1 else { return nil }
        return "faltam \(restam)min"
    }

    var fracaoVista: Double? {
        guard let ondeParou, let total = duracaoEmSegundos, total > 0 else { return nil }
        return min(max(ondeParou / total, 0), 1)
    }

    enum CodingKeys: String, CodingKey {
        case id, title, year, poster, backdrop, still, finished
        case tituloDaSerie = "series_title"
        case temporada = "season_number"
        case episodio = "episode_number"
        case corDominante = "dominant_color"
        case arquivoId = "media_file_id"
        case duracaoEmSegundos = "duration_seconds"
        case ondeParou = "position_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = c.ou(.title, "")
        year = c.talvez(.year)
        tituloDaSerie = c.talvez(.tituloDaSerie)
        temporada = c.talvez(.temporada)
        episodio = c.talvez(.episodio)
        poster = c.talvez(.poster)
        backdrop = c.talvez(.backdrop)
        still = c.talvez(.still)
        corDominante = c.talvez(.corDominante)
        arquivoId = c.talvez(.arquivoId)
        duracaoEmSegundos = c.talvez(.duracaoEmSegundos)
        ondeParou = c.talvez(.ondeParou)
        finished = c.talvez(.finished)
    }
}

// MARK: - De onde continuar, a conta

/// De onde continuar — ou **zero**, quando não há de onde.
///
/// ## ⚠️ O defeito que ela conserta deixava o filme impossível de reabrir
///
/// > «tentei começar o família de aluguel que tu tinha terminado, o filme abre no
/// > final dele e dps aparece essa mensagem»
///
/// `position_seconds` de um filme visto até o fim **é** o fim, e o app retomava
/// lá. Em HLS isso era impeditivo: a sessão nascia com quase nada pela frente.
///
/// ## As três condições
///
/// | | por quê |
/// |---|---|
/// | `> 5` | **decisão do dono**: «a pessoa pode assistir um teco e voltar, isso já deve salvar o progresso dela» |
/// | `!finished` | o veredito é do **servidor** |
/// | `restam > 60` | pega o filme parado a 99% que o servidor ainda não marcou |
///
/// ⚠️ **Duração `0` conta como ausente.** Ela chega zerada em arquivo sem probe
/// guardada, e sem essa leitura a conta `0 − 3401 ≤ 60` é verdadeira — mandando
/// pro começo **todo** filme não medido.
func ondeContinuar(ondeParou: Double, duracaoEmSegundos: Double?, finished: Bool) -> Double {
    if finished { return 0 }
    if ondeParou <= 5 { return 0 }
    guard let total = duracaoEmSegundos, total > 0 else { return ondeParou }
    if total - ondeParou <= 60 { return 0 }
    return ondeParou
}

// MARK: - A ficha da obra

/// Um arquivo por trás da obra — `MediaFileSummary` na web.
///
/// ## Uma obra pode ter mais de um, e a ficha mostra todos
///
/// Podem ser dublagens diferentes, legendagens diferentes ou duas qualidades — e
/// esconder um deles seria o app decidir sozinho que um arquivo do acervo não
/// existe, que é o §18 ao contrário.
///
/// ⚠️ **Não confundir com [VersaoDaObra]**: aquilo é uma **obra** inteira (id,
/// progresso e ficha próprios) que o servidor agrupou com esta; isto é um arquivo
/// **dentro** de uma obra.
struct ArquivoDeMidia: Decodable, Sendable, Identifiable {
    let id: String
    let filename: String
    let tamanhoEmBytes: Int64?
    /// ⚠️ É por ele que se descobre o que o iOS vai fazer com o acervo:
    /// `matroska` é 55% dos arquivos e o iPhone não abre Matroska, então esses
    /// viram remux. `avi` é 14,7% e é a pergunta ainda aberta.
    let container: String?
    let duracaoEmSegundos: Double?
    let codecDeVideo: String?
    let width: Int?
    let height: Int?
    let codecDeAudio: String?
    let canaisDeAudio: Int?
    let idiomasDeLegenda: [String]

    enum CodingKeys: String, CodingKey {
        case id, filename, container, width, height
        case tamanhoEmBytes = "size_bytes"
        case duracaoEmSegundos = "duration_seconds"
        case codecDeVideo = "video_codec"
        case codecDeAudio = "audio_codec"
        case canaisDeAudio = "audio_channels"
        case idiomasDeLegenda = "subtitle_langs"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        filename = c.ou(.filename, "")
        tamanhoEmBytes = c.talvez(.tamanhoEmBytes)
        container = c.talvez(.container)
        duracaoEmSegundos = c.talvez(.duracaoEmSegundos)
        codecDeVideo = c.talvez(.codecDeVideo)
        width = c.talvez(.width)
        height = c.talvez(.height)
        codecDeAudio = c.talvez(.codecDeAudio)
        canaisDeAudio = c.talvez(.canaisDeAudio)
        idiomasDeLegenda = c.ou(.idiomasDeLegenda, [])
    }
}

/// `GET /api/works/{id}` — a ficha.
struct ObraDetalhada: Decodable, Sendable, Identifiable {
    let id: String
    let kind: String
    let title: String
    let year: Int?
    let overview: String?
    let duracaoEmSegundos: Double?
    let corDominante: String?
    /// `{poster, backdrop, still}` — caminhos servidos em `/artwork/`.
    let artwork: [String: String]
    /// «Thunderball». ⚠️ Ele vai na marquise **junto** do ano e da duração, e não
    /// some quando é igual ao título: uma marquise que às vezes tem três itens e
    /// às vezes dois muda de altura entre um filme e o seguinte. Some só quando
    /// **não existe** (§24).
    let tituloOriginal: String?
    /// `namespace:valor` — `genre:Ação`, `country:Reino Unido`.
    let tags: [EtiquetaDaObra]
    let files: [ArquivoDeMidia]
    /// Onde **este** usuário parou, em segundos. `0` se nunca começou.
    let ondeParou: Double
    let finished: Bool

    enum CodingKeys: String, CodingKey {
        case id, kind, title, year, overview, artwork, files, finished, tags
        case tituloOriginal = "original_title"
        case duracaoEmSegundos = "runtime_seconds"
        case corDominante = "dominant_color"
        case ondeParou = "position_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        kind = c.ou(.kind, "")
        title = c.ou(.title, "")
        year = c.talvez(.year)
        overview = c.talvez(.overview)
        duracaoEmSegundos = c.talvez(.duracaoEmSegundos)
        corDominante = c.talvez(.corDominante)
        artwork = c.ou(.artwork, [:])
        tituloOriginal = c.talvez(.tituloOriginal)
        tags = c.ou(.tags, [])
        files = c.ou(.files, [])
        ondeParou = c.ou(.ondeParou, 0)
        finished = c.ou(.finished, false)
    }
}

// MARK: - O plano de reprodução

/// Uma faixa de áudio que o **arquivo** tem — e não necessariamente a playlist.
///
/// ## ⚠️ Ela existe porque perguntar ao player sempre responde «uma»
///
/// Em transcodificação o ffmpeg do servidor põe **uma** faixa na playlist, então
/// o dual audio sumia exatamente nos arquivos que o têm. A lista verdadeira vem
/// do plano, tirada da probe do arquivo.
struct FaixaDeAudio: Decodable, Sendable {
    /// Relativo ao **áudio**: é o `N` de `-map 0:a:N`.
    let index: Int
    let codec: String
    let language: String?
    let title: String?
    let channels: Int?
    /// ⚠️ Vazio como padrão de propósito: se o servidor renomear este campo, o
    /// plano continua sendo lido e a tela cai no rótulo posicional. Campo
    /// obrigatório transformaria uma renomeação de JSON em filme que não abre.
    let label: String

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        index = c.ou(.index, 0)
        codec = c.ou(.codec, "")
        language = c.talvez(.language)
        title = c.talvez(.title)
        channels = c.talvez(.channels)
        label = c.ou(.label, "")
    }

    enum CodingKeys: String, CodingKey {
        case index, codec, language, title, channels, label
    }
}

struct FaixaDeLegenda: Decodable, Sendable {
    let index: Int
    let origem: String
    let codec: String
    let language: String?
    let forced: Bool
    let baseadaEmTexto: Bool
    /// Vem **pronto do servidor** — montar «Português (forçada)» aqui seria a
    /// quarta redação da mesma frase.
    let label: String

    enum CodingKeys: String, CodingKey {
        case index, origem, codec, language, forced, label
        case baseadaEmTexto = "text_based"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        index = c.ou(.index, 0)
        origem = c.ou(.origem, "")
        codec = c.ou(.codec, "")
        language = c.talvez(.language)
        forced = c.ou(.forced, false)
        baseadaEmTexto = c.ou(.baseadaEmTexto, true)
        label = c.ou(.label, "")
    }
}

/// `GET /api/playback/{arquivo}/plan` — como **este** aparelho vai receber o filme.
///
/// ## O `mode` é o selo, e o `reasons` é o porquê
///
/// | | |
/// |---|---|
/// | `direct_play` | o arquivo como está — **zero ffmpeg** |
/// | `direct_stream` | remuxa o contêiner sem re-encodar. ⚠️ Acende ffmpeg em `-c copy` e uma sessão HLS |
/// | `transcode` | re-encoda. O único que custa CPU de verdade |
///
/// ⚠️ **No iOS o `direct_stream` é o caso comum**, não a exceção: o servidor mediu
/// **53,3% do acervo** caindo nele com o perfil deste cliente, contra 29,4% de
/// `direct_play`. É a diferença de não abrir Matroska — no Android esses mesmos
/// arquivos não abrem processo nenhum no servidor.
struct PlanoDeReproducao: Decodable, Sendable {
    let mode: String
    let video: String
    let audio: String
    let alturaAlvo: Int?
    let reasons: [String]
    /// Preenchida só em `direct_play`. Nos outros dois o vídeo vem por HLS, e
    /// quem abre a sessão é o `POST …/session`.
    let urlDireta: String?
    let subtitles: [FaixaDeLegenda]
    let faixasDeAudio: [FaixaDeAudio]
    /// **Qual** faixa este plano está falando. Sem pedido, o servidor usa a 0.
    let faixaDeAudio: Int?
    /// A duração do arquivo, **medida pelo servidor** — 17/08/2026.
    ///
    /// ## ⚠️ É a resposta ao pedido da playlist `VOD`, e é melhor que ele
    ///
    /// O pedido era declarar a playlist `VOD` pra a barra nascer certa. A
    /// resposta recusou com medida: declarar `VOD` exige saber o tamanho de cada
    /// segmento antes de produzi-lo, e no caminho `video=copy` isso são os
    /// keyframes da fonte — **1m33s por arquivo** contra 25s de espera da
    /// playlist. Metade dos filmes ficaria certa e metade errada, sem ninguém
    /// saber qual.
    ///
    /// Veio o número no lugar. É o suficiente: quem desenha a barra e o «faltam»
    /// é o cliente, e o que faltava era só o denominador.
    let duracaoEmSegundos: Double?

    var eDireto: Bool { mode == "direct_play" }

    enum CodingKeys: String, CodingKey {
        case mode, video, audio, reasons, subtitles
        case alturaAlvo = "target_height"
        case urlDireta = "direct_url"
        case faixasDeAudio = "audio_tracks"
        case faixaDeAudio = "audio_track"
        case duracaoEmSegundos = "duration_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        mode = c.ou(.mode, "")
        video = c.ou(.video, "")
        audio = c.ou(.audio, "")
        alturaAlvo = c.talvez(.alturaAlvo)
        reasons = c.ou(.reasons, [])
        urlDireta = c.talvez(.urlDireta)
        subtitles = c.ou(.subtitles, [])
        faixasDeAudio = c.ou(.faixasDeAudio, [])
        faixaDeAudio = c.talvez(.faixaDeAudio)
        duracaoEmSegundos = c.talvez(.duracaoEmSegundos)
    }
}

/// Uma etiqueta **do acervo** — `GET /api/tags`.
///
/// ⚠️ Ela não é a `EtiquetaDaObra`: aquela é o que um filme tem, esta é o que o
/// acervo oferece, com quantas obras cada uma alcança. É o que enche o painel de
/// filtros.
struct EtiquetaDoAcervo: Decodable, Sendable, Identifiable {
    let id: String
    let namespace: String
    let value: String
    let quantasObras: Int

    /// `genre:Terror` — a chave que viaja pro servidor e identifica o chip.
    var chave: String { "\(namespace):\(value)" }

    enum CodingKeys: String, CodingKey {
        case id, namespace, value
        case quantasObras = "work_count"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        namespace = c.ou(.namespace, "")
        value = c.ou(.value, "")
        quantasObras = c.ou(.quantasObras, 0)
    }
}

/// O grupo de etiquetas — `GET /api/tag-namespaces`.
///
/// ⚠️ É dele que sai o rótulo «Gênero» e a **ordem** dos grupos. Sem ele a tela
/// traduziria `genre` por conta própria, e a lista de namespaces existiria em
/// dois lugares.
struct EspacoDeEtiqueta: Decodable, Sendable, Identifiable {
    let namespace: String
    let rotulo: String
    let posicao: Int

    var id: String { namespace }

    enum CodingKeys: String, CodingKey {
        case namespace
        case rotulo = "label"
        case posicao = "position"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        namespace = c.ou(.namespace, "")
        rotulo = c.ou(.rotulo, "")
        posicao = c.ou(.posicao, 0)
    }
}

/// Uma etiqueta da obra: `genre` · `Ação`.
///
/// ⚠️ O **namespace fica na tela**, apagado, ao lado do valor em negrito. Não é
/// enfeite de banco de dados vazando: é o que faz «Reino Unido» ser lido como
/// país e não como distribuidora, e «Ação» como gênero e não como formato. Uma
/// pílula que só dissesse o valor obrigaria a adivinhar de que eixo ela é.
struct EtiquetaDaObra: Decodable, Sendable, Identifiable {
    let id: String
    let namespace: String
    let value: String

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        namespace = c.ou(.namespace, "")
        value = c.ou(.value, "")
    }

    enum CodingKeys: String, CodingKey { case id, namespace, value }

    /// O qualificador em português — `country` vira «país».
    ///
    /// ## ⚠️ A tela mostrava **chave de banco**, e o Android já tinha consertado
    ///
    /// Visto na ficha de «007 Contra GoldenEye» em 17/08/2026, aqui: `country
    /// Reino Unido`, `format filme`, `genre Ação`, `lang inglês`. O mesmo defeito
    /// que o Android corrigiu em 16/08 — e que ficou de pé neste cliente porque o
    /// conserto de lá não atravessou.
    ///
    /// O desenho está certo e vem da web: o qualificador apagado diz *de que
    /// tipo* é a etiqueta. O que ele assumia é que o namespace chegava em
    /// português (`genero/Crime`, `pais/Estados Unidos`), como chegava quando
    /// aquilo foi escrito. Não chega mais.
    ///
    /// Traduzir código em nome é **desenho**, e mora no cliente pela mesma regra
    /// do `nomeDoIdioma`. ⚠️ Namespace desconhecido devolve `nil` e a pílula
    /// **omite o qualificador** em vez de imprimir a chave: o valor sozinho
    /// continua legível, e é o pior caso aceitável (§18).
    ///
    /// ⚠️ A tabela é a mesma do Android, entrada por entrada. Divergir aqui
    /// significaria a mesma etiqueta lida de dois jeitos em dois aparelhos da
    /// mesma casa.
    var rotulo: String? {
        switch namespace.lowercased() {
        case "country", "pais", "país": "país"
        case "genre", "genero", "gênero": "gênero"
        case "format", "formato", "tipo": "formato"
        case "lang", "language", "idioma": "idioma"
        case "decade", "decada", "década": "década"
        case "director", "diretor": "direção"
        case "studio", "estudio", "estúdio": "estúdio"
        case "collection", "saga": "saga"
        default: nil
        }
    }
}

/// Um fotograma da obra — `GET /api/works/{obra}/cenas`.
///
/// ## ⚠️ Ela **custa**, e por isso não vem junto da ficha
///
/// São doze extrações de ffmpeg no servidor de casa, ~4s na primeira vez. O
/// Android pede em separado e deixa a ficha desenhar sem esperar; aqui é igual —
/// o varal aparece quando chega, e a ficha nunca fica parada por causa dele.
struct Cena: Decodable, Sendable, Identifiable {
    let segundos: Double
    let imagem: String
    /// `capitulo` quando o **disco** disse onde a cena começa, `regular` quando
    /// foi o relógio que dividiu. ⚠️ Muda a legenda, não o desenho: é a diferença
    /// entre «nos cortes do disco» e «divididos pelo relógio».
    let origem: String

    var id: String { imagem }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        segundos = c.ou(.segundos, 0)
        imagem = c.ou(.imagem, "")
        origem = c.ou(.origem, "regular")
    }

    enum CodingKeys: String, CodingKey { case segundos, imagem, origem }
}

/// `POST /api/playback/{arquivo}/session` — a sessão HLS, quando não é direto.
struct SessaoDeTranscodificacao: Decodable, Sendable {
    let id: String
    let mode: String
    let reasons: [String]
    let urlDaPlaylist: String
    /// A duração — ver a folha do mesmo campo em `PlanoDeReproducao`.
    ///
    /// ⚠️ Ela vem **nas duas** respostas de propósito: o caminho direto não abre
    /// sessão, e uma sessão retomada pode não passar pelo plano.
    let duracaoEmSegundos: Double?

    enum CodingKeys: String, CodingKey {
        case id, mode, reasons
        case urlDaPlaylist = "playlist_url"
        case duracaoEmSegundos = "duration_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        mode = c.ou(.mode, "")
        reasons = c.ou(.reasons, [])
        urlDaPlaylist = c.ou(.urlDaPlaylist, "")
        duracaoEmSegundos = c.talvez(.duracaoEmSegundos)
    }
}

// MARK: - Para você

/// Uma recomendação, com o **porquê** dela.
///
/// ## ⚠️ O `reasons` é a metade que importa
///
/// Um `score` sozinho é uma lista ordenada por um número que ninguém vê — e uma
/// lista assim é indistinguível de «os mais recentes». O que separa este produto
/// de um catálogo é a frase: *por que este filme, pra mim, agora*.
///
/// Ela vem **pronta do servidor**, que é quem tem o perfil, os vetores e o
/// histórico. Reescrevê-la aqui seria adivinhar o motivo de uma conta feita do
/// outro lado — e é a mesma regra do `label` das faixas e do `reasons` do plano.
struct Recomendacao: Decodable, Sendable, Identifiable {
    let id: String
    let title: String
    let year: Int?
    let poster: String?
    let backdrop: String?
    let corDominante: String?
    let arquivoId: String?
    let duracaoEmSegundos: Double?
    let score: Double
    let reasons: [String]

    /// ⚠️ **Só a primeira.** A web mostra uma por cartão pelo mesmo motivo: três
    /// frases de justificativa viram parágrafo, e ninguém lê parágrafo escolhendo
    /// filme.
    var porque: String? { reasons.first }

    enum CodingKeys: String, CodingKey {
        case id, title, year, poster, backdrop, score, reasons
        case corDominante = "dominant_color"
        case arquivoId = "media_file_id"
        case duracaoEmSegundos = "duration_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = c.ou(.title, "")
        year = c.talvez(.year)
        poster = c.talvez(.poster)
        backdrop = c.talvez(.backdrop)
        corDominante = c.talvez(.corDominante)
        arquivoId = c.talvez(.arquivoId)
        duracaoEmSegundos = c.talvez(.duracaoEmSegundos)
        score = c.ou(.score, 0)
        reasons = c.ou(.reasons, [])
    }
}

/// O perfil de gosto, medido pelo servidor.
struct PerfilDeGosto: Decodable, Sendable {
    let obrasTocadas: Int
    let finished: Int
    let temVetor: Bool

    enum CodingKeys: String, CodingKey {
        case finished
        case obrasTocadas = "works_touched"
        case temVetor = "has_taste_vector"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        obrasTocadas = c.ou(.obrasTocadas, 0)
        finished = c.ou(.finished, 0)
        temVetor = c.ou(.temVetor, false)
    }
}

/// `GET /api/curation/for-you`
///
/// ## ⚠️ `cold_start` é a diferença entre duas frases muito diferentes
///
/// «Não recomendo nada» e «ainda não te conheço» parecem a mesma tela vazia e não
/// são: **uma lista fraca sem aviso parece um algoritmo ruim; com o aviso, é um
/// convite a assistir mais.** A tela precisa dizer qual dos dois — é o §8b
/// aplicado a uma ausência.
struct ParaVoce: Decodable, Sendable {
    let profile: PerfilDeGosto?
    let items: [Recomendacao]
    let aindaNaoTeConhece: Bool

    enum CodingKeys: String, CodingKey {
        case profile, items
        case aindaNaoTeConhece = "cold_start"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        profile = c.talvez(.profile)
        items = c.ou(.items, [])
        aindaNaoTeConhece = c.ou(.aindaNaoTeConhece, false)
    }
}

// MARK: - A locadora

/// Uma caixa **exposta na vitrine** — uma fita que está na estante, à mostra.
struct CaixaExposta: Decodable, Sendable, Identifiable {
    let id: String
    let serie: Bool
    let titulo: String
    let ano: Int?
    let poster: String?
    let corDominante: String?
    let temporadas: Int
    let arquivoId: String?
    let ondeParou: Double?
    let estante: Int

    enum CodingKeys: String, CodingKey {
        case id, serie, titulo, ano, poster, temporadas, estante
        case corDominante = "dominant_color"
        case arquivoId = "media_file_id"
        case ondeParou = "position_seconds"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        serie = c.ou(.serie, false)
        titulo = c.ou(.titulo, "")
        ano = c.talvez(.ano)
        poster = c.talvez(.poster)
        corDominante = c.talvez(.corDominante)
        temporadas = c.ou(.temporadas, 0)
        arquivoId = c.talvez(.arquivoId)
        ondeParou = c.talvez(.ondeParou)
        estante = c.ou(.estante, 0)
    }
}

/// Uma estante, com nome e placa.
struct EstanteExposta: Decodable, Sendable, Identifiable {
    let nome: String
    /// ⚠️ Quantas caixas esta estante tem **no acervo**, e não quantas estão à
    /// vista. É o que faz a placa dizer «16 de 113» em vez de «16» — e a
    /// diferença é a promessa: **a vitrine é uma amostra, não o estoque**.
    let total: Int
    let caixas: [CaixaExposta]

    var id: String { nome }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        nome = c.ou(.nome, "")
        total = c.ou(.total, 0)
        caixas = c.ou(.caixas, [])
    }

    enum CodingKeys: String, CodingKey { case nome, total, caixas }
}

/// `GET /api/locadora/estantes` — a **loja**, e ela é uma vitrine que gira.
///
/// ## ⚠️ `vira_em` é o que torna a vitrine promessa, e não sorteio
///
/// O comentário da web é a explicação inteira: «quando a vitrine vira. É o que
/// torna a rotação **promessa, não sorteio**». Uma seleção que muda sem data
/// anunciada é aleatoriedade; com data, é programação — e é a diferença entre um
/// acervo embaralhado e uma locadora que troca a vitrine na segunda.
struct Loja: Decodable, Sendable {
    let estantes: [EstanteExposta]
    let noAcervo: Int
    let semanaDe: String?
    let viraEm: String?

    enum CodingKeys: String, CodingKey {
        case estantes
        case noAcervo = "no_acervo"
        case semanaDe = "semana_de"
        case viraEm = "vira_em"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        estantes = c.ou(.estantes, [])
        noAcervo = c.ou(.noAcervo, 0)
        semanaDe = c.talvez(.semanaDe)
        viraEm = c.talvez(.viraEm)
    }
}

// MARK: - O mural

/// Uma coisa que aconteceu na casa.
///
/// ## ⚠️ `tipo` é lista fechada, e o que a tela não sabe **some**
///
/// A regra vem da web e vale igual aqui: «`terminou` | `pegou` | `devolveu` |
/// `pediu` | `avaliou`. Lista fechada: um tipo que a tela não sabe dizer não vira
/// linha muda, **some**.»
///
/// É o §18 aplicado a um feed: melhor uma linha a menos que uma linha dizendo
/// «alguém fez algo com alguma coisa».
struct Acontecimento: Decodable, Sendable, Identifiable {
    let tipo: String
    let quando: String
    let quem: String
    let quemId: String
    let meu: Bool
    let titulo: String
    let obraId: String?
    let poster: String?
    let detalhe: String?

    var id: String { "\(quemId)-\(quando)-\(titulo)" }

    /// A frase, montada **aqui** porque ela é desenho e não dado.
    ///
    /// ⚠️ O servidor manda o verbo em código (`pegou`) e os sujeitos; a frase em
    /// português é da tela. Fazer o servidor mandar texto pronto amarraria o
    /// idioma da API ao idioma do cliente — e o `detalhe` já vem pronto porque
    /// aquele **é** conteúdo (uma nota, um recado).
    ///
    /// `nil` quando o tipo é desconhecido, e aí **a linha não desenha**.
    var frase: String? {
        switch tipo {
        case "terminou": "terminou"
        case "pegou": "pegou a fita de"
        case "devolveu": "devolveu"
        case "pediu": "pediu de volta"
        case "avaliou": "avaliou"
        default: nil
        }
    }

    enum CodingKeys: String, CodingKey {
        case tipo, quando, quem, meu, titulo, poster, detalhe
        case quemId = "quem_id"
        case obraId = "obra_id"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        tipo = c.ou(.tipo, "")
        quando = c.ou(.quando, "")
        quem = c.ou(.quem, "")
        quemId = c.ou(.quemId, "")
        meu = c.ou(.meu, false)
        titulo = c.ou(.titulo, "")
        obraId = c.talvez(.obraId)
        poster = c.talvez(.poster)
        detalhe = c.talvez(.detalhe)
    }
}

/// `GET /api/feed` — o que aconteceu na casa.
struct Mural: Decodable, Sendable {
    let acontecimentos: [Acontecimento]
    /// ⚠️ Quantas pessoas apareceram no mural — e ele é **desenhado**, não
    /// enfeite: «um mural com um nome só não é uma conversa, e a tela diz isso em
    /// vez de parecer completa».
    let vozes: Int
    /// Quantas poderiam aparecer: você mais os seus.
    let pessoas: Int

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        acontecimentos = c.ou(.acontecimentos, [])
        vozes = c.ou(.vozes, 0)
        pessoas = c.ou(.pessoas, 0)
    }

    enum CodingKeys: String, CodingKey { case acontecimentos, vozes, pessoas }
}

// MARK: - O perfil

/// O rosto, a capa ou a moldura escolhida.
///
/// ⚠️ O servidor **já resolve** a escolha: manda o caminho da arte e o hex da cor
/// prontos. Traduzir a chave (`rosto:bogart`) em arte aqui seria a mesma tabela
/// escrita duas vezes, e a segunda cópia envelheceria sozinha.
struct EnfeiteNaTela: Decodable, Sendable {
    let chave: String
    /// O nome da pessoa, o título do filme, ou o hex da cor.
    let rotulo: String
    /// Servível em `/artwork/…`. `nil` na cor, que não tem arte.
    let arte: String?
    let cor: String?
    let aberto: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        chave = c.ou(.chave, "")
        rotulo = c.ou(.rotulo, "")
        arte = c.talvez(.arte)
        cor = c.talvez(.cor)
        aberto = c.ou(.aberto, true)
    }

    enum CodingKeys: String, CodingKey { case chave, rotulo, arte, cor, aberto }
}

/// XP, nível e conquistas.
///
/// ⚠️ `xpDoNivel` e `xpDoProximo` vêm **servidos, não recalculados aqui**: a
/// curva é regra, e regra mora num lugar só. Recalcular no cliente seria a
/// terceira redação de uma fórmula que muda quando o servidor quiser.
struct ProgressoDoUsuario: Decodable, Sendable {
    let xp: Int
    let nivel: Int
    let xpDoNivel: Int
    let xpDoProximo: Int
    let desbloqueadas: Int
    let total: Int

    /// Quanto falta pro próximo, de 0 a 1. `nil` quando a faixa não faz sentido —
    /// e aí a barra **não aparece** em vez de aparecer cheia (§24).
    var fracaoDoNivel: Double? {
        let faixa = xpDoProximo - xpDoNivel
        guard faixa > 0 else { return nil }
        return min(max(Double(xp - xpDoNivel) / Double(faixa), 0), 1)
    }

    var faltamProProximo: Int { max(0, xpDoProximo - xp) }

    enum CodingKeys: String, CodingKey {
        case xp, nivel, desbloqueadas, total
        case xpDoNivel = "xp_do_nivel"
        case xpDoProximo = "xp_do_proximo"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        xp = c.ou(.xp, 0)
        nivel = c.ou(.nivel, 1)
        xpDoNivel = c.ou(.xpDoNivel, 0)
        xpDoProximo = c.ou(.xpDoProximo, 0)
        desbloqueadas = c.ou(.desbloqueadas, 0)
        total = c.ou(.total, 0)
    }
}

/// Uma conquista.
///
/// ⚠️ `em` é `nil` **enquanto trancada**, e a trancada vem junto de propósito:
/// «um rosto que ninguém sabe que existe não é perseguido». A tela mostra as duas
/// e a diferença é visual, não a ausência.
struct ConquistaNaTela: Decodable, Sendable, Identifiable {
    let chave: String
    let nome: String
    let descricao: String
    let pontos: Int
    let em: String?

    var id: String { chave }
    var aberta: Bool { em != nil }

    /// `facil` · `media` · `dificil` · `impossivel` · `nivel` · `saga`.
    ///
    /// ## ⚠️ Eu tinha **tirado** este campo, e estava errado
    ///
    /// O raciocínio parecia bom: «os pontos já dizem a dificuldade, e `+10` ao
    /// lado de `+150` diz melhor; uma segunda escala seria a mesma coisa escrita
    /// duas vezes». Só que a `camada` **não é uma escala** — ela é o **agrupador**
    /// da tela: o Android desenha `FÁCEIS · 9 de 12`, `MÉDIAS`, `DIFÍCEIS` como
    /// seções, cada uma com a própria contagem.
    ///
    /// A régua «não declare contrato que ninguém lê» continua certa. O que estava
    /// errado era a outra metade: eu decidi que ninguém lia **sem abrir a tela que
    /// lê**. É a mesma falha de método que trocou a planta inteira do app —
    /// deduzir onde dava pra olhar.
    let camada: String

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        chave = c.ou(.chave, "")
        nome = c.ou(.nome, "")
        descricao = c.ou(.descricao, "")
        camada = c.ou(.camada, "facil")
        pontos = c.ou(.pontos, 0)
        em = c.talvez(.em)
    }

    enum CodingKeys: String, CodingKey { case chave, nome, descricao, camada, pontos, em }
}

/// Uma linha do placar de amigos.
///
/// ⚠️ Ela existe porque **o placar existe** na tela do Android — `VOCÊ E SEUS
/// AMIGOS · 3`, com posição, nível e XP, e a sua linha acesa. A doc da web diz
/// que ele foi «pedido e nunca existiu» até aparecer; eu não o tinha mapeado
/// porque não tinha aberto a tela.
struct AmigoNoPlacar: Decodable, Sendable, Identifiable {
    let id: String
    let displayName: String
    let nivel: Int
    let xp: Int
    /// ⚠️ Quem é **você** vem do servidor, e não de comparar ids aqui: o app não
    /// guarda o próprio id em lugar nenhum, e recomparar seria inventar a segunda
    /// fonte de uma verdade que já chega pronta.
    let eu: Bool

    enum CodingKeys: String, CodingKey {
        case id, nivel, xp, eu
        case displayName = "display_name"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        displayName = c.ou(.displayName, "")
        nivel = c.ou(.nivel, 1)
        xp = c.ou(.xp, 0)
        eu = c.ou(.eu, false)
    }
}

/// `GET /api/perfil` — quem você é dentro da casa.
///
/// ## ⚠️ O que **não** está mapeado, e por quê
///
/// A resposta traz `rostos`, `capas`, `molduras`, `titulos_disponiveis` e
/// `tags_disponiveis`. Os cinco existem pro **editor** do perfil, que este app não
/// tem — eles são a lista do que dá pra escolher, e escolher é o que falta.
/// Declará-los seria contrato que ninguém confere.
struct Perfil: Decodable, Sendable {
    let displayName: String
    let username: String
    let progresso: ProgressoDoUsuario?
    let tituloNome: String?
    let bio: String?
    let conquistas: [ConquistaNaTela]
    let amigos: [AmigoNoPlacar]
    let avatar: EnfeiteNaTela?
    let capa: EnfeiteNaTela?
    /// O hex da moldura. ⚠️ **O servidor traduz a chave** — a lista de cores é
    /// dele, e traduzir aqui seria a mesma lista escrita duas vezes.
    let moldura: String?

    enum CodingKeys: String, CodingKey {
        case username, progresso, bio, conquistas, amigos, avatar, capa, moldura
        case displayName = "display_name"
        case tituloNome = "titulo_nome"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        displayName = c.ou(.displayName, "")
        username = c.ou(.username, "")
        progresso = c.talvez(.progresso)
        tituloNome = c.talvez(.tituloNome)
        bio = c.talvez(.bio)
        conquistas = c.ou(.conquistas, [])
        amigos = c.ou(.amigos, [])
        avatar = c.talvez(.avatar)
        capa = c.talvez(.capa)
        moldura = c.talvez(.moldura)
    }
}

// MARK: - O guia

/// Um filme da capa da revista.
struct FilmeDaCapa: Decodable, Sendable, Identifiable {
    let id: String
    let titulo: String
    let ano: Int?
    let poster: String?
    let diretor: String?
    /// ⚠️ **A única coisa da capa que é sua.** A revista é igual pra todo mundo;
    /// este `Bool` é o que não é — e por isso a tela o desenha como marca e não
    /// como selo.
    let visto: Bool

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        titulo = c.ou(.titulo, "")
        ano = c.talvez(.ano)
        poster = c.talvez(.poster)
        diretor = c.talvez(.diretor)
        visto = c.ou(.visto, false)
    }

    enum CodingKeys: String, CodingKey { case id, titulo, ano, poster, diretor, visto }
}

/// «Em cartaz esta semana» — o evento coletivo.
struct EventoDaSemana: Decodable, Sendable {
    /// `"obra"` ou `"saga"`. ⚠️ Só `"obra"` abre ficha: o `id` de uma saga é de
    /// coleção, e mandá-lo pra tela da obra daria erro. Oferecer o toque que vai
    /// falhar é o §53 ao contrário.
    let tipo: String
    let id: String
    let titulo: String
    let poster: String?
    let obras: Int
    let suas: Int
    let participou: Bool
    /// ⚠️ **Quem participou aparece pra todo mundo** — é o que faz o evento ser
    /// coletivo. Um evento em que ninguém sabe quem foi não é evento, é tarefa.
    let participantes: [String]

    var abreFicha: Bool { tipo == "obra" && !id.isEmpty }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        tipo = c.ou(.tipo, "")
        id = c.ou(.id, "")
        titulo = c.ou(.titulo, "")
        poster = c.talvez(.poster)
        obras = c.ou(.obras, 0)
        suas = c.ou(.suas, 0)
        participou = c.ou(.participou, false)
        participantes = c.ou(.participantes, [])
    }

    enum CodingKeys: String, CodingKey {
        case tipo, id, titulo, poster, obras, suas, participou, participantes
    }
}

/// `GET /api/guia/revista` — a capa da semana.
///
/// ## ⚠️ Ela é **igual pra todo mundo**, e é isso que ela é
///
/// O oposto do «para você»: o mesmo tema, os mesmos filmes e o mesmo ensaio
/// chegam pros três moradores, e viram na mesma segunda-feira que a vitrine da
/// locadora. É o que dá assunto em comum.
struct Revista: Decodable, Sendable {
    /// Quando vira — a mesma segunda da vitrine (ver `Loja.viraEm`).
    let viraEm: String?
    /// `"genero"`, `"decada"`, `"pais"`, `"diretor"` ou `"saga"`.
    let eixo: String
    /// O tema: «Romance». É o letreiro da capa.
    let tema: String
    let filmes: [FilmeDaCapa]
    /// ⚠️ `nil` quando não há chave do LLM ou o texto ainda não foi gerado. A
    /// tela **omite a seção** — não mostra «carregando» nem inventa prosa.
    let ensaio: String?
    /// ⚠️ O selo, e **não é enfeite**: quem lê tem direito de saber que aquele
    /// parágrafo não foi escrito por gente. Texto de máquina sai sempre creditado
    /// — a mesma regra do crédito `WIKIPÉDIA` das curiosidades.
    let ensaioPor: String?
    let evento: EventoDaSemana?

    /// «gênero da semana», «década da semana».
    ///
    /// ⚠️ Montado aqui pelo mesmo motivo do `Acontecimento.frase`: o servidor
    /// manda o eixo em **código** e a frase em português é da tela. `nil` num eixo
    /// que este app não conhece — e aí o rótulo não desenha, em vez de escrever
    /// «saga da semana» sobre um eixo que talvez não seja isso (§18).
    var rotuloDoEixo: String? {
        switch eixo {
        case "genero": "gênero da semana"
        case "decada": "década da semana"
        case "pais": "país da semana"
        case "diretor": "diretor da semana"
        case "saga": "saga da semana"
        default: nil
        }
    }

    /// Os parágrafos do ensaio, já limpos. Vazia quando não há ensaio — e aí a
    /// seção inteira some.
    var paragrafos: [String] {
        (ensaio ?? "").split(separator: "\n")
            .map { $0.trimmingCharacters(in: .whitespaces) }
            .filter { !$0.isEmpty }
    }

    enum CodingKeys: String, CodingKey {
        case eixo, tema, filmes, ensaio, evento
        case viraEm = "vira_em"
        case ensaioPor = "ensaio_por"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        viraEm = c.talvez(.viraEm)
        eixo = c.ou(.eixo, "")
        tema = c.ou(.tema, "")
        filmes = c.ou(.filmes, [])
        ensaio = c.talvez(.ensaio)
        ensaioPor = c.talvez(.ensaioPor)
        evento = c.talvez(.evento)
    }
}

/// Uma pessoa do guia — e o quanto do trabalho dela **você** já viu.
///
/// ## ⚠️ `terminadas de obras` é o que faz isto ser um guia
///
/// Sem esse par isto é uma lista de créditos, que qualquer site tem. Com ele é um
/// mapa do que falta: a contagem cruza a pessoa com o **seu** histórico.
///
/// ⚠️ `known_for`, `comecadas`, `posters` e `total` vêm na resposta e **não** são
/// mapeados: nenhuma parte desta tela os lê, e contrato que ninguém confere é
/// contrato que envelhece calado.
struct PessoaDoGuia: Decodable, Sendable, Identifiable {
    let id: String
    let name: String
    let imagePath: String?
    /// ⚠️ Conta **títulos**, não obras: uma série inteira é um título só.
    let obras: Int
    let terminadas: Int

    enum CodingKeys: String, CodingKey {
        case id, name, obras, terminadas
        case imagePath = "image_path"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        name = c.ou(.name, "")
        imagePath = c.talvez(.imagePath)
        obras = c.ou(.obras, 0)
        terminadas = c.ou(.terminadas, 0)
    }
}

/// Um eixo que não é pessoa: gênero, década ou país.
struct FaixaDoGuia: Decodable, Sendable, Identifiable {
    let rotulo: String
    /// ⚠️ O que vai pro filtro da biblioteca: `genre:Terror` nos gêneros e
    /// países, **o ano** (`1980`) nas décadas. As duas formas, e é o conteúdo que
    /// decide — não o nome da fileira.
    let chave: String
    let obras: Int

    var id: String { chave }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        rotulo = c.ou(.rotulo, "")
        chave = c.ou(.chave, "")
        obras = c.ou(.obras, 0)
    }

    enum CodingKeys: String, CodingKey { case rotulo, chave, obras }
}

/// `GET /api/guia` — o índice atrás da capa.
struct GuiaEixos: Decodable, Sendable {
    let direcao: [PessoaDoGuia]
    let elenco: [PessoaDoGuia]
    let trilha: [PessoaDoGuia]
    let generos: [FaixaDoGuia]
    let decadas: [FaixaDoGuia]
    /// Só países com 2 obras ou mais: dos 33 do acervo, 10 têm um filme só, e um
    /// país com uma obra não é prateleira.
    let paises: [FaixaDoGuia]
    /// ⚠️ Quantos filmes **não** são dos Estados Unidos. Vem junto porque sem ele
    /// o eixo diz «Estados Unidos 491» e o resto vira rodapé — é este número que
    /// faz a região valer uma seção.
    let foraDeHollywood: Int

    var vazio: Bool { generos.isEmpty && direcao.isEmpty }

    enum CodingKeys: String, CodingKey {
        case direcao, elenco, trilha, generos, decadas, paises
        case foraDeHollywood = "fora_de_hollywood"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        direcao = c.ou(.direcao, [])
        elenco = c.ou(.elenco, [])
        trilha = c.ou(.trilha, [])
        generos = c.ou(.generos, [])
        decadas = c.ou(.decadas, [])
        paises = c.ou(.paises, [])
        foraDeHollywood = c.ou(.foraDeHollywood, 0)
    }
}

// MARK: - Ao vivo

/// Um canal de fonte externa, com o que o EPG diz estar no ar.
///
/// ⚠️ Quase tudo é opcional porque **canal sem EPG é normal**: a lista vem de um
/// M3U, e o XMLTV é outra coisa que pode faltar. Um canal sem programação existe
/// e está no ar — o que não se sabe é o que está passando, e as duas coisas são
/// diferentes (§18).
struct CanalNoAr: Decodable, Sendable, Identifiable {
    let id: String
    let name: String
    let number: String?
    let logo: String?
    let grupo: String?
    let titulo: String?
    let comeca: String?
    let termina: String?
    /// O que entra depois deste, quando o servidor sabe.
    let aSeguir: String?
    /// O `programme_id` do guia — o que casa com um lembrete. `nil` nos canais do
    /// Odeon, que não têm EPG externo e por isso não têm o que lembrar.
    let programaId: Int?
    let arte: String?
    /// A obra e o arquivo: é o que «ver desde o início» tocaria.
    let obraId: String?
    let arquivoId: String?

    enum CodingKeys: String, CodingKey {
        case id, name, number, grupo, titulo, comeca, termina, arte
        case logo = "logo_url"
        case aSeguir = "a_seguir"
        case programaId = "programme_id"
        case obraId = "work_id"
        case arquivoId = "media_file_id"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        name = c.ou(.name, "")
        number = c.talvez(.number)
        logo = c.talvez(.logo)
        grupo = c.talvez(.grupo)
        titulo = c.talvez(.titulo)
        comeca = c.talvez(.comeca)
        termina = c.talvez(.termina)
        aSeguir = c.talvez(.aSeguir)
        programaId = c.talvez(.programaId)
        arte = c.talvez(.arte)
        obraId = c.talvez(.obraId)
        arquivoId = c.talvez(.arquivoId)
    }
}

struct CanalDoOdeon: Decodable, Sendable, Identifiable {
    let slug: String
    let nome: String
    let numero: String

    var id: String { slug }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        slug = c.ou(.slug, "")
        nome = c.ou(.nome, "")
        numero = c.ou(.numero, "")
    }

    enum CodingKeys: String, CodingKey { case slug, nome, numero }
}

struct ProgramaDoOdeon: Decodable, Sendable, Identifiable {
    let id: String
    let canal: String
    let obraId: String
    let arquivoId: String?
    let title: String
    let year: Int?
    let arte: String?
    let categoria: String?
    let comeca: String
    let termina: String

    enum CodingKeys: String, CodingKey {
        case id, canal, title, year, arte, categoria
        case obraId = "work_id"
        case arquivoId = "media_file_id"
        case comeca = "starts_at"
        case termina = "ends_at"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        canal = c.ou(.canal, "")
        obraId = c.ou(.obraId, "")
        arquivoId = c.talvez(.arquivoId)
        title = c.ou(.title, "")
        year = c.talvez(.year)
        arte = c.talvez(.arte)
        categoria = c.talvez(.categoria)
        comeca = c.ou(.comeca, "")
        termina = c.ou(.termina, "")
    }
}

/// `GET /api/live/odeon` — a grade dos canais que o **próprio Odeon** programa.
///
/// ⚠️ «Calculada, não guardada: duas chamadas no mesmo dia devolvem a mesma
/// programação.» E o `agora` é o **relógio do servidor** — ver `emCartaz`.
struct GradeDoOdeon: Decodable, Sendable {
    let agora: String
    let canais: [CanalDoOdeon]
    let programas: [ProgramaDoOdeon]

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        agora = c.ou(.agora, "")
        canais = c.ou(.canais, [])
        programas = c.ou(.programas, [])
    }

    enum CodingKeys: String, CodingKey { case agora, canais, programas }
}

/// `POST /api/live/{canal}/watch` — a sessão de um canal sintonizado.
struct CanalAberto: Decodable, Sendable {
    let nome: String
    let sessaoId: String
    let urlDaPlaylist: String
    let mode: String?

    enum CodingKeys: String, CodingKey {
        case channel
        case sessaoId = "session_id"
        case urlDaPlaylist = "playlist_url"
        case mode
    }

    enum ChavesDoCanal: String, CodingKey { case id, name }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        sessaoId = c.ou(.sessaoId, "")
        urlDaPlaylist = c.ou(.urlDaPlaylist, "")
        mode = c.talvez(.mode)
        let canal = try? c.nestedContainer(keyedBy: ChavesDoCanal.self, forKey: .channel)
        nome = canal?.ou(.name, "") ?? ""
    }
}

// MARK: - A prateleira

/// Uma fita que está **em mãos** — fora da estante.
struct Emprestada: Decodable, Sendable, Identifiable {
    let id: Int
    /// ⚠️ O mesmo id que `/api/library` devolve — é por ele que a estante casa.
    let caixaId: String
    /// **Todos** os ids que abrem esta caixa — 17/08/2026.
    ///
    /// ## ⚠️ É a peça que devolve o «levar pra casa» ao app
    ///
    /// O botão não existia porque o 403 era imprevisível, e o §53 proíbe oferecer
    /// o que a validação vai negar. A causa saiu da investigação do servidor e
    /// **não era permissão**: o mesmo filme existe duas vezes neste acervo (44
    /// casos). A biblioteca desenha um cartão pro grupo; a locadora trancava por
    /// `work_id` — a prateleira dizia o id de um rip e o cartão conhecia o outro.
    ///
    /// Com a lista a conta é local: se o id que estou olhando está aqui, a caixa
    /// **está fora**, e o `meu` diz se está comigo.
    let caixaIds: [String]
    let titulo: String
    let quemNome: String
    let meu: Bool
    let venceEm: String?
    let poster: String?
    let corDominante: String?
    let ano: Int?

    enum CodingKeys: String, CodingKey {
        case id, titulo, meu, poster, ano
        case caixaId = "caixa_id"
        case caixaIds = "caixa_ids"
        case quemNome = "quem_nome"
        case venceEm = "vence_em"
        case corDominante = "dominant_color"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, 0)
        caixaId = c.ou(.caixaId, "")
        caixaIds = c.ou(.caixaIds, [])
        titulo = c.ou(.titulo, "")
        quemNome = c.ou(.quemNome, "")
        meu = c.ou(.meu, false)
        venceEm = c.talvez(.venceEm)
        poster = c.talvez(.poster)
        corDominante = c.talvez(.corDominante)
        ano = c.talvez(.ano)
    }
}

/// Alguém que frequenta a loja, e quanto tem na mão.
///
/// ⚠️ São as pessoas do **servidor**, não as de um grupo: com estoque único, quem
/// te barra pode ser qualquer uma delas.
struct PessoaNaLoja: Decodable, Sendable, Identifiable {
    let id: String
    let nome: String
    let naMao: Int
    /// Quantas fitas dela **alguém teve que rebobinar**. A reputação, e cada
    /// unidade é uma vez em que outra pessoa gastou os segundos por causa dela.
    let zoadas: Int
    /// ⚠️ E quantas ela rebobinou dos outros. **O outro lado precisa existir**: um
    /// placar que só conta o defeito faz de todo mundo réu.
    let rebobinou: Int
    /// Fitas que ela deixou no meio **agora**. Estado, não histórico — some no
    /// instante em que alguém rebobina, e é a única das três que dá pra consertar
    /// sozinha.
    let noMeio: Int

    /// ⚠️ Quem não tem fita nem fama **não é notícia** (§24). É a mesma régua dos
    /// chips que esta nota substituiu.
    var temOQueDizer: Bool { naMao > 0 || zoadas > 0 || rebobinou > 0 || noMeio > 0 }

    enum CodingKeys: String, CodingKey {
        case id, zoadas, rebobinou
        case nome = "display_name"
        case naMao = "na_mao"
        case noMeio = "no_meio"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = c.ou(.id, "")
        nome = c.ou(.nome, "")
        naMao = c.ou(.naMao, 0)
        zoadas = c.ou(.zoadas, 0)
        rebobinou = c.ou(.rebobinou, 0)
        noMeio = c.ou(.noMeio, 0)
    }
}

struct OpcoesDaLocadora: Decodable, Sendable {
    let prazoEmDias: Int

    enum CodingKeys: String, CodingKey { case prazoEmDias = "prazo_dias" }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        prazoEmDias = c.ou(.prazoEmDias, 0)
    }
}

/// `GET /api/locadora/prateleira` — o que está **em mãos**, e a régua do formato.
///
/// ⚠️ «Não devolve o estado das 746 caixas — devolve as poucas que estão em mãos.
/// Quem cruza com a estante é a tela, que já tem as caixas carregadas.»
struct Prateleira: Decodable, Sendable {
    let emprestadas: [Emprestada]
    let pessoas: [PessoaNaLoja]
    let opcoes: OpcoesDaLocadora?
    /// Quantas ainda dá pra pegar. Zero é o limite — e a nota diz isso com todas
    /// as letras, porque «não dá» sem motivo é o §8b.
    let possoPegar: Int

    /// O corte entre fita e disco.
    ///
    /// ## ⚠️ **Não é constante daqui, de propósito**
    ///
    /// O servidor usa o mesmo número pra decidir se uma caixa **rebobina**. Se os
    /// dois divergissem, uma caixa desenhada como VHS recusaria o rebobinar — a
    /// mesma família do botão que dizia «ver as 644» e abria 1.424.
    ///
    /// Foi por isso que a locadora deste app desenhou tudo como DVD até agora: eu
    /// não tinha este número, e inventar um ano de corte seria a segunda cópia de
    /// uma regra que já existe.
    let ultimoAnoVhs: Int

    enum CodingKeys: String, CodingKey {
        case emprestadas, pessoas, opcoes
        case possoPegar = "posso_pegar"
        case ultimoAnoVhs = "ultimo_ano_vhs"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        emprestadas = c.ou(.emprestadas, [])
        pessoas = c.ou(.pessoas, [])
        opcoes = c.talvez(.opcoes)
        possoPegar = c.ou(.possoPegar, 0)
        ultimoAnoVhs = c.ou(.ultimoAnoVhs, 0)
    }

    /// Esta obra é fita ou disco?
    ///
    /// ⚠️ **Sem ano, é disco.** É o §18: na dúvida, o app não afirma que uma obra
    /// é de uma era que ele não sabe qual é — e disco é o caso mais comum do
    /// acervo.
    func ehVhs(ano: Int?) -> Bool {
        guard ultimoAnoVhs > 0, let ano else { return false }
        return ano <= ultimoAnoVhs
    }

    /// As minhas, separadas das dos outros.
    ///
    /// ⚠️ A prateleira mistura tudo de propósito — quem te barra pode ser qualquer
    /// morador, e ver isso é parte da ideia. Mas a tela precisa da separação: nas
    /// minhas dá pra devolver, nas dos outros só dá pra pedir.
    var minhas: [Emprestada] { emprestadas.filter(\.meu) }
    var dosOutros: [Emprestada] { emprestadas.filter { !$0.meu } }
}

// MARK: - O menu do disco

/// Um capítulo, como o **autor do disco** o cortou.
struct Capitulo: Decodable, Sendable, Identifiable {
    let inicio: Double
    let fim: Double?
    /// ⚠️ `nil` em **98,4% deste acervo**. Exibir o timecode como nome de
    /// capítulo seria inventar um metadado com cara de dado (§18) — quem desenha
    /// numera («Capítulo 03») e assume a numeração como sua.
    let titulo: String?

    var id: Double { inicio }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        inicio = c.ou(.inicio, 0)
        fim = c.talvez(.fim)
        titulo = c.talvez(.titulo)
    }

    enum CodingKeys: String, CodingKey { case inicio, fim, titulo }
}

/// `GET /api/works/{obra}/menu` — o menu do disco.
///
/// ## ⚠️ Ele existe porque um DVD **não é um arquivo**
///
/// A biblioteca toca; o disco tem menu. É a diferença que a locadora inteira
/// existe pra encenar, e é por isso que este menu abre **só por ela** e **só em
/// DVD**: a fita não tem menu, tem rebobinar (§14.4).
struct MenuDoDisco: Decodable, Sendable {
    let obraId: String
    let arquivoId: String
    let titulo: String
    let ano: Int?
    let cor: String?
    let backdrop: String?
    let duracao: Double?
    /// ⚠️ `nil` quando não há de onde continuar — e aí **o item nem existe** no
    /// menu (§24). Um «continuar» que começa do zero é um «do começo» com outro
    /// nome.
    let posicao: Double?
    let terminado: Bool
    let capitulos: [Capitulo]
    /// Idiomas distintos, na ordem das faixas do disco.
    let legendas: [String]
    /// O clima: o índice da estante que reivindicaria este filme na locadora.
    ///
    /// ⚠️ **O índice é o contrato.** Ele é a posição na lista `ESTANTES` do
    /// servidor, e a tabela de climas do app é indexada por ele — mexer na ordem
    /// de lá sem mexer aqui troca o clima de todo mundo.
    let clima: Int
    let climaNome: String

    /// Dá pra continuar? Só com posição, não terminado, e passando de um minuto
    /// — a mesma régua da ficha.
    var temComoContinuar: Bool { !terminado && (posicao ?? 0) > 60 }

    /// `1:23:45` de onde parou, pro rótulo do «continuar».
    var ponteiro: String { relogioDaSessao(posicao ?? 0) }

    enum CodingKeys: String, CodingKey {
        case titulo, ano, cor, backdrop, duracao, posicao, terminado, capitulos, legendas, clima
        case obraId = "work_id"
        case arquivoId = "media_file_id"
        case climaNome = "clima_nome"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        obraId = c.ou(.obraId, "")
        arquivoId = c.ou(.arquivoId, "")
        titulo = c.ou(.titulo, "")
        ano = c.talvez(.ano)
        cor = c.talvez(.cor)
        backdrop = c.talvez(.backdrop)
        duracao = c.talvez(.duracao)
        posicao = c.talvez(.posicao)
        terminado = c.ou(.terminado, false)
        capitulos = c.ou(.capitulos, [])
        legendas = c.ou(.legendas, [])
        clima = c.ou(.clima, 11)
        climaNome = c.ou(.climaNome, "")
    }
}

/// Uma **coleção** — a série, e cada temporada dela.
///
/// ⚠️ Chegou em 18/08/2026 e substitui um monte de reserva: pôster de temporada
/// (461 de 473), sinopse (232), nome próprio (26), e sinopse e backdrop da série
/// (115 e 118 das 120). Ver `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`.
struct Colecao: Decodable, Sendable, Identifiable {
    let id: String
    let title: String
    let year: Int?
    let overview: String?
    /// O número da temporada. ⚠️ É `position`: numa coleção genérica ele é a
    /// ordem, e numa temporada ele **é** o número.
    let position: Int?
    let poster: String?
    let backdrop: String?
    let quantosItens: Int
    let quantosVistos: Int

    enum CodingKeys: String, CodingKey {
        case id, title, year, overview, position, poster, backdrop
        case quantosItens = "item_count"
        case quantosVistos = "finished_count"
    }

    init(from decoder: Decoder) throws {
        let c = try decoder.container(keyedBy: CodingKeys.self)
        id = try c.decode(String.self, forKey: .id)
        title = try c.decodeIfPresent(String.self, forKey: .title) ?? ""
        year = try c.decodeIfPresent(Int.self, forKey: .year)
        overview = try c.decodeIfPresent(String.self, forKey: .overview)
        position = try c.decodeIfPresent(Int.self, forKey: .position)
        poster = try c.decodeIfPresent(String.self, forKey: .poster)
        backdrop = try c.decodeIfPresent(String.self, forKey: .backdrop)
        quantosItens = try c.decodeIfPresent(Int.self, forKey: .quantosItens) ?? 0
        quantosVistos = try c.decodeIfPresent(Int.self, forKey: .quantosVistos) ?? 0
    }
}

/// A resposta de `GET /api/collections/{id}`: a série e as temporadas dela.
struct ColecaoComFilhos: Decodable, Sendable {
    let collection: Colecao
    let children: [Colecao]
}
