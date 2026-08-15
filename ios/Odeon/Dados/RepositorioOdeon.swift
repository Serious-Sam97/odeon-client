import Foundation

/// A falha de rede, já traduzida.
///
/// ## ⚠️ Ela existe por causa do §8b, e o defeito que ele nomeia
///
/// O Android mostrava `HTTP 403 Forbidden` cru, e o §8b cobra erro **visível**.
/// Ele era — mas visível não é legível: «403» é status de protocolo, diz que
/// houve recusa e não diz o quê. Numa tela que oferece um botão e depois recusa,
/// a frase é a única coisa que a pessoa tem.
enum FalhaDoOdeon: Error, LocalizedError, Sendable {
    case semResposta
    case naoEUmOdeon
    case credenciaisErradas
    case sessaoVenceu
    case recusado(Int)
    /// Uma frase pronta, pra quando o motivo não é HTTP. ⚠️ Ela existe pra o §8b:
    /// «este filme não é guardável no iPhone» é erro **legível**, e obrigar cada
    /// caso desses a virar um `case` novo faria alguém escolher `recusado(0)`.
    case recado(String)

    var errorDescription: String? {
        switch self {
        case .semResposta: "sem resposta do servidor"
        case .naoEUmOdeon: "esse endereço respondeu, mas não é um Odeon"
        case .credenciaisErradas: "usuário ou senha não conferem"
        case .sessaoVenceu: "a sessão venceu — entre de novo"
        case let .recado(frase): frase
        case let .recusado(codigo): "o servidor respondeu \(codigo)"
        }
    }
}

/// A trava de «uma vez por abertura do app».
///
/// ⚠️ Ela é um `actor` porque o `RepositorioOdeon` é `struct` — e é `struct` de
/// propósito: ele não guarda estado, só sabe conversar. Enfiar uma variável
/// mutável nele pra isto daria a ele memória, e memória compartilhada entre cinco
/// telas é o que esta trava existe pra evitar.
private actor ConferenciaDaArte {
    static let combinada = ConferenciaDaArte()
    private var jaFoi = false

    func aindaNaoFoi() -> Bool {
        if jaFoi { return false }
        jaFoi = true
        return true
    }
}

/// A conversa com o servidor.
///
/// ## As duas credenciais, e por que elas não se misturam
///
/// | | vai em | quem usa |
/// |---|---|---|
/// | sessão | `Authorization: Bearer` | toda rota de API |
/// | mídia | `?token=` na URL | pôster, stream, legenda |
///
/// ⚠️ **A rota de sprites é a exceção que já causou defeito**: ela exige
/// cabeçalho e **não** aceita `?token=`. A web mandava só a query, recebia 401,
/// lia «não deu certo» e concluía «não há sprite» — em silêncio, pro acervo
/// inteiro. Quando ela entrar aqui (F4), é com cabeçalho, e **só o 404** vira
/// «não há».
struct RepositorioOdeon: Sendable {
    let cofre: Cofre

    private var sessao: URLSession { .shared }

    private let decodificador: JSONDecoder = {
        let d = JSONDecoder()
        return d
    }()

    // MARK: - O endereço

    /// Descobre qual candidato responde, e **guarda o que respondeu**.
    ///
    /// ⚠️ Ela é a razão de o login não perguntar «http ou https?». Os candidatos
    /// vêm do [EnderecoDoServidor] em ordem — https primeiro —, e o primeiro que
    /// devolver um `/api/auth/status` válido ganha.
    ///
    /// ⚠️ E **responder não basta: tem que ser um Odeon.** Um roteador na porta
    /// 8080 devolve 200 com HTML; sem decodificar a resposta, o app guardaria o
    /// endereço errado e só descobriria no login, com a senha já digitada.
    func descobrirServidor(digitado: String) async throws -> String {
        let candidatos = EnderecoDoServidor.candidatos(digitado)
        if candidatos.isEmpty { throw FalhaDoOdeon.semResposta }

        var ultima: FalhaDoOdeon = .semResposta
        for base in candidatos {
            do {
                _ = try await status(base: base)
                cofre.servidor = base
                return base
            } catch let falha as FalhaDoOdeon {
                ultima = falha
            } catch {
                ultima = .semResposta
            }
        }
        throw ultima
    }

    /// `GET /api/auth/status` — a única rota que responde sem sessão.
    func status(base: String) async throws -> StatusDoServidor {
        guard let url = URL(string: base + "/api/auth/status") else {
            throw FalhaDoOdeon.semResposta
        }
        var pedido = URLRequest(url: url)
        pedido.timeoutInterval = 6

        let (dados, resposta): (Data, URLResponse)
        do {
            (dados, resposta) = try await sessao.data(for: pedido)
        } catch {
            throw FalhaDoOdeon.semResposta
        }
        guard let http = resposta as? HTTPURLResponse, http.statusCode == 200 else {
            throw FalhaDoOdeon.semResposta
        }
        guard let status = try? decodificador.decode(StatusDoServidor.self, from: dados) else {
            // Respondeu, e não é um Odeon. Ver o comentário de `descobrirServidor`.
            throw FalhaDoOdeon.naoEUmOdeon
        }
        return status
    }

    // MARK: - Entrar

    func entrar(usuario: String, senha: String, rotulo: String) async throws -> Usuario {
        let corpo = Credenciais(username: usuario, password: senha, deviceLabel: rotulo)
        let resposta: RespostaDeLogin = try await pedir(
            "/api/auth/login",
            metodo: "POST",
            corpo: try JSONEncoder().encode(corpo),
            comSessao: false,
        )
        cofre.sessao = resposta.token
        /// ⚠️ O token de mídia **não** é pedido aqui. Ele é pedido quando falta,
        /// e nunca «por garantia»: emitir um novo aposenta o anterior, e o
        /// anterior pode estar dentro de um player tocando.
        return resposta.user
    }

    // MARK: - A biblioteca

    func biblioteca(
        pulando: Int = 0, limite: Int = 60, busca: String? = nil,
        filtros: Filtros = Filtros(),
    ) async throws -> [ItemDaBiblioteca] {
        var itens = [URLQueryItem(name: "limit", value: String(limite)),
                     URLQueryItem(name: "offset", value: String(pulando))]
        /// ⚠️ Nulo **não vira parâmetro**. É o que faz esta chamada mandar
        /// `?limit=60` quando não há filtro, em vez de `?q=&kind=` que o servidor
        /// teria que aprender a ignorar.
        if let busca, !busca.isEmpty { itens.append(URLQueryItem(name: "q", value: busca)) }
        if let e = filtros.etiqueta { itens.append(URLQueryItem(name: "tags", value: e)) }
        if let a = filtros.anoDe { itens.append(URLQueryItem(name: "year_from", value: String(a))) }
        if let a = filtros.anoAte { itens.append(URLQueryItem(name: "year_to", value: String(a))) }
        if let t = filtros.tipo { itens.append(URLQueryItem(name: "kind", value: t)) }
        if let o = filtros.ordem { itens.append(URLQueryItem(name: "sort", value: o)) }
        return try await pedir("/api/library", query: itens)
    }

    /// ⚠️ **Custa ~4 s na primeira vez** — doze extrações de ffmpeg no servidor
    /// de casa. Por isso ela é uma chamada à parte e nunca bloqueia a ficha: o
    /// varal aparece quando chega.
    func cenas(obra: String) async throws -> [Cena] {
        try await pedir("/api/works/\(obra)/cenas")
    }

    // MARK: - Ao vivo

    func canaisAoVivo() async throws -> [CanalNoAr] {
        try await pedir("/api/live/channels")
    }

    /// ⚠️ `hours` decide **o tamanho da resposta**, não o que está no ar. Cinco é
    /// o que a web pede: o bastante pra o «a seguir» existir sem baixar o dia.
    func gradeDoOdeon(horas: Int = 5) async throws -> GradeDoOdeon {
        try await pedir("/api/live/odeon", query: [URLQueryItem(name: "hours", value: String(horas))])
    }

    /// Sintoniza — abre uma sessão de HLS pro canal.
    func sintonizar(canal: String) async throws -> CanalAberto {
        try await pedir("/api/live/\(canal)/watch", metodo: "POST")
    }

    // MARK: - O guia

    /// ⚠️ **Duas rotas, e a capa é a que importa.** `/api/guia` devolve os eixos —
    /// um índice. A revista mora noutra rota, e o Android registra o dia em que
    /// isso apareceu: a tela de lá foi construída só contra os eixos, e o dono
    /// disse «você não pegou a maior essência do Guia». Foi a quinta vez naquela
    /// história em que o servidor já mandava o dado e o cliente não o pegava.
    func guia() async throws -> GuiaEixos {
        try await pedir("/api/guia")
    }

    func revista() async throws -> Revista {
        try await pedir("/api/guia/revista")
    }

    func paraContinuar() async throws -> [ItemPraContinuar] {
        try await pedir("/api/continue")
    }

    // MARK: - O perfil

    /// ⚠️ Uma chamada, dois usos: a insígnia do canto e a tela do perfil saem da
    /// **mesma** resposta. «Uma requisição, na montagem: o número muda devagar e a
    /// barra não é lugar de ficar perguntando.»
    func perfil() async throws -> Perfil {
        try await pedir("/api/perfil")
    }

    // MARK: - O mural

    /// ⚠️ A rota é `feed` mesmo — é a única do app cujo endereço não está em
    /// português, e é do servidor, não nossa.
    func mural(limite: Int = 40) async throws -> Mural {
        try await pedir("/api/feed", query: [URLQueryItem(name: "limit", value: String(limite))])
    }

    // MARK: - A locadora

    /// A vitrine. **Só lê.**
    ///
    /// ⚠️ E é de propósito que só isto esteja aqui. `POST /api/locadora/alugar`
    /// **escreve no acervo compartilhado por três pessoas**: um empréstimo criado
    /// por engano fica no perfil de alguém, e a limpeza é do outro lado. Ele entra
    /// quando houver quem confirme na tela — não antes.
    func estantes() async throws -> Loja {
        try await pedir("/api/locadora/estantes")
    }

    // MARK: - Para você

    /// ⚠️ `minutes` filtra por tempo disponível («tenho 90 minutos»), e é o
    /// parâmetro que faz esta tela ser de celular e não de catálogo. Nulo não vira
    /// parâmetro.
    func paraVoce(limite: Int = 24, minutos: Int? = nil) async throws -> ParaVoce {
        var query = [URLQueryItem(name: "limit", value: String(limite))]
        if let minutos { query.append(URLQueryItem(name: "minutes", value: String(minutos))) }
        return try await pedir("/api/curation/for-you", query: query)
    }

    // MARK: - A ficha e o plano

    func obra(_ id: String) async throws -> ObraDetalhada {
        try await pedir("/api/works/\(id)")
    }

    /// Como **este** aparelho vai receber este arquivo.
    ///
    /// ⚠️ As capacidades vão na query, e são as **deste** aparelho — não uma lista
    /// fixa. Ver `CapacidadesDoAparelho`, e o erro que ele já cometeu.
    func plano(arquivo: String, faixaDeAudio: Int? = nil) async throws -> PlanoDeReproducao {
        var query = CapacidadesDoAparelho.query
        /// Nulo não vira parâmetro: sem ele o servidor usa a faixa 0 — e não a
        /// marcada `default`, que mudaria em silêncio o que toca em milhares de
        /// arquivos que ninguém pediu pra mudar.
        if let faixaDeAudio {
            query.append(URLQueryItem(name: "audio_track", value: String(faixaDeAudio)))
        }
        return try await pedir("/api/playback/\(arquivo)/plan", query: query)
    }

    // MARK: - A sessão de HLS

    /// Abre a sessão de transcodificação. **Só quando o plano não for direto.**
    ///
    /// ⚠️ No iOS este é o **caminho comum**, não a exceção: 53,3% do acervo cai
    /// aqui, contra 29,4% de `direct_play`. É o preço de não abrir Matroska.
    func abrirSessao(arquivo: String, comecandoEm: Int = 0, faixaDeAudio: Int? = nil) async throws -> SessaoDeTranscodificacao {
        var query = CapacidadesDoAparelho.query
        query.append(URLQueryItem(name: "start", value: String(comecandoEm)))
        /// ⚠️ Trocar de faixa **exige sessão nova**, como o `start`: a playlist já
        /// foi escrita com a faixa anterior, e o ffmpeg daquela sessão não muda de
        /// ideia no meio.
        if let faixaDeAudio {
            query.append(URLQueryItem(name: "audio_track", value: String(faixaDeAudio)))
        }
        return try await pedir("/api/playback/\(arquivo)/session", metodo: "POST", query: query)
    }

    /// Encerra a sessão.
    ///
    /// ## ⚠️ Não é higiene, é **CPU do servidor de casa**
    ///
    /// O comentário da web é direto: «sem isto o ffmpeg fica vivo até o reaper
    /// passar». O servidor atende três pessoas de verdade e ainda roda o Postgres
    /// e a identificação — um ffmpeg esquecido por sessão abandonada é o tipo de
    /// custo que ninguém vê até a casa toda ficar lenta.
    ///
    /// ⚠️ E no iOS isso pesa **mais** que no Android, porque aqui metade do acervo
    /// abre sessão. Falhar em encerrar não é erro de tela: é a casa esquentando.
    func encerrarSessao(_ sessaoId: String) async {
        guard let base = cofre.servidor,
              let url = URL(string: base + "/api/hls/" + sessaoId)
        else { return }
        var pedido = URLRequest(url: url)
        pedido.httpMethod = "DELETE"
        if let token = cofre.sessao {
            pedido.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }
        /// Falhar aqui não vira tela: quem está saindo do filme não tem o que
        /// fazer com o aviso, e o coletor do servidor ainda pega o resto.
        _ = try? await sessao.data(for: pedido)
    }

    // MARK: - Onde eu parei

    /// Marca o progresso.
    ///
    /// ⚠️ O `device_id` **não é enfeite**: a tese do projeto é continuar na TV o
    /// que começou no ônibus, e pra isso o servidor precisa distinguir de onde veio
    /// cada marca. Ver `Cofre.aparelho`.
    func marcarProgresso(obra: String, posicao: Double, duracao: Double?, arquivo: String?) async {
        struct Marca: Encodable {
            let positionSeconds: Double
            let durationSeconds: Double?
            let mediaFileId: String?
            let client: String
            let deviceId: String
            enum CodingKeys: String, CodingKey {
                case positionSeconds = "position_seconds"
                case durationSeconds = "duration_seconds"
                case mediaFileId = "media_file_id"
                case client
                case deviceId = "device_id"
            }
        }
        let marca = Marca(
            positionSeconds: posicao,
            durationSeconds: duracao,
            mediaFileId: arquivo,
            client: "ios",
            deviceId: cofre.aparelho,
        )
        guard let corpo = try? JSONEncoder().encode(marca) else { return }
        struct Resposta: Decodable { let ok: Bool? }
        _ = try? await pedir("/api/works/\(obra)/progress", metodo: "POST", corpo: corpo) as Resposta
    }

    /// A URL de mídia com o token pendurado — pro `direct_url` do plano.
    func urlDeMidia(_ caminho: String) async throws -> URL? {
        let token = try await garantirTokenDeMidia()
        guard let base = cofre.servidor else { return nil }
        let absoluto = caminho.hasPrefix("http") ? caminho : base + caminho
        guard var partes = URLComponents(string: absoluto) else { return nil }
        var itens = partes.queryItems ?? []
        if !itens.contains(where: { $0.name == "token" }) {
            itens.append(URLQueryItem(name: "token", value: token))
        }
        partes.queryItems = itens
        return partes.url
    }

    // MARK: - Legendas

    /// Baixa **uma** legenda e a devolve já lida.
    ///
    /// ⚠️ Uma, e não todas. O §26.7 do Android registra a suspeita de que anexar
    /// todas as legendas ao player custava quadro descartado — três corridas
    /// caíram pro nível do Jellyfin ao tirá-las. Aqui a escolhida é a única que
    /// atravessa a rede, e ela nem passa pelo decodificador: quem desenha é a tela.
    ///
    /// ⚠️ O servidor devolve **WebVTT** mesmo quando o arquivo é `.srt` — a
    /// conversão é de lá. O `codec=srt` do plano descreve a **origem**, não o que
    /// chega aqui.
    func legenda(arquivo: String, indice: Int) async throws -> [FalaDaLegenda] {
        let token = try await garantirTokenDeMidia()
        guard let base = cofre.servidor,
              var partes = URLComponents(string: base + "/api/media/\(arquivo)/subtitles/\(indice)")
        else { throw FalhaDoOdeon.semResposta }
        partes.queryItems = [URLQueryItem(name: "token", value: token)]
        guard let url = partes.url else { throw FalhaDoOdeon.semResposta }

        let (dados, resposta) = try await sessao.data(from: url)
        guard let http = resposta as? HTTPURLResponse, http.statusCode == 200 else {
            throw FalhaDoOdeon.recusado((resposta as? HTTPURLResponse)?.statusCode ?? -1)
        }
        guard let texto = String(data: dados, encoding: .utf8) else { return [] }
        return Legenda.ler(texto)
    }

    // MARK: - O token de mídia

    /// Garante que há um token de mídia — e **só pede um novo quando não há**.
    ///
    /// ## ⚠️ A regra mais perigosa do contrato inteiro
    ///
    /// **Emitir um token de mídia novo aposenta o anterior.** O anterior é o que
    /// está dentro do player que está tocando: renovar «por garantia» no meio de
    /// um filme derruba o próprio filme. No Android isso está escrito no `Cofre` e
    /// no §43 da espec, e é o tipo de defeito que só aparece com alguém assistindo.
    ///
    /// Por isso aqui não há renovação preventiva, não há relógio e não há
    /// «atualiza a cada abertura». Há uma pergunta: *existe?* Se não, pede.
    /// Pede um token de mídia **novo**, aposentando o que houver.
    ///
    /// ## ⚠️ Ela só deve ser chamada quando o token em mãos **provou estar morto**
    ///
    /// A regra herdada continua valendo: emitir aposenta o anterior, e renovar por
    /// precaução derruba o próprio player. Mas faltava o outro lado dela — **o que
    /// fazer quando o token guardado já não vale**.
    ///
    /// Faltava, e custou caro: uma limpeza no servidor apagou os `media_token` da
    /// conta, o app seguiu usando o cadáver que tinha no Keychain, e todo pedido
    /// de mídia voltava 401. Como o `garantirTokenDeMidia` só pede quando **não
    /// há** token, ele nunca sairia sozinho desse estado — o app ficaria sem mídia
    /// até alguém reinstalar.
    ///
    /// ⚠️ E o 401 é ambíguo de propósito do lado de lá: `{"error":"credenciais
    /// inválidas ou sessão expirada"}` (exatos **54 bytes**) é a resposta tanto pra
    /// token morto quanto pra token do **tipo errado** — o de sessão não abre
    /// bytes. Do cliente, os dois se tratam igual: pede outro e tenta de novo.
    @discardableResult
    func renovarTokenDeMidia() async throws -> String {
        let novo: TokenDeMidia = try await pedir("/api/auth/media-token", metodo: "POST")
        cofre.tokenDeMidia = novo.token
        return novo.token
    }

    @discardableResult
    func garantirTokenDeMidia() async throws -> String {
        if let ja = cofre.tokenDeMidia { return ja }
        let novo: TokenDeMidia = try await pedir("/api/auth/media-token", metodo: "POST")
        cofre.tokenDeMidia = novo.token
        return novo.token
    }

    // MARK: - O encanamento

    private func pedir<T: Decodable>(
        _ caminho: String,
        metodo: String = "GET",
        query: [URLQueryItem] = [],
        corpo: Data? = nil,
        comSessao: Bool = true,
    ) async throws -> T {
        guard let base = cofre.servidor,
              var partes = URLComponents(string: base + caminho)
        else { throw FalhaDoOdeon.semResposta }

        if !query.isEmpty { partes.queryItems = query }
        guard let url = partes.url else { throw FalhaDoOdeon.semResposta }

        var pedido = URLRequest(url: url)
        pedido.httpMethod = metodo
        pedido.timeoutInterval = 20
        if let corpo {
            pedido.httpBody = corpo
            pedido.setValue("application/json", forHTTPHeaderField: "Content-Type")
        }
        if comSessao, let token = cofre.sessao {
            pedido.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        }

        let dados: Data
        let resposta: URLResponse
        do {
            (dados, resposta) = try await sessao.data(for: pedido)
        } catch {
            throw FalhaDoOdeon.semResposta
        }

        guard let http = resposta as? HTTPURLResponse else { throw FalhaDoOdeon.semResposta }
        switch http.statusCode {
        case 200 ..< 300: break
        case 401 where comSessao: throw FalhaDoOdeon.sessaoVenceu
        case 401: throw FalhaDoOdeon.credenciaisErradas
        default: throw FalhaDoOdeon.recusado(http.statusCode)
        }

        do {
            return try decodificador.decode(T.self, from: dados)
        } catch {
            throw FalhaDoOdeon.naoEUmOdeon
        }
    }

    // MARK: - Mídia

    /// A URL de uma arte, com o token de mídia pendurado. `nil` sem token — e aí
    /// a tela desenha a obra sem capa, que é o §18: sem dado, omite.
    /// ## ⚠️ A arte é o único lugar do app **sem recuperação de token**
    ///
    /// O player renova em `-16840`/`-1013`; o download confere o status antes de
    /// gravar. A arte não tem sinal nenhum: o `AsyncImage` recebe um `401` de 54
    /// bytes, não consegue decodificar imagem nenhuma, e **desenha o vazio** — que
    /// é exatamente o que uma obra sem pôster também desenha. Defeito e estado
    /// normal do mundo, indistinguíveis na tela outra vez.
    ///
    /// Visto em 15/08/2026: subi o app do Android no emulador pra comparar, e o
    /// servidor **apagou o token de mídia deste cliente** — é o que ele faz a cada
    /// emissão, e está aberto como pedido. A ficha desenhou a marquise, o varal e
    /// os prendedores, com três Polaroids **pretas** e o fotograma de fundo vazio.
    ///
    /// ## Por que não renovar sempre na abertura
    ///
    /// Seria uma linha e resolveria — e **derrubaria o filme de quem está na
    /// sala**, porque emitir aposenta o token dos outros aparelhos. Trocar um
    /// defeito visível meu por um invisível na TV de outra pessoa não é conserto.
    ///
    /// Então: renova **só com prova**. Uma requisição de uma arte que a tela já ia
    /// pedir; se ela voltar `401`, o token morreu e aí sim vale a troca.
    ///
    /// ⚠️ E é **uma vez por abertura do app**: sem a trava, cinco telas com arte
    /// virariam cinco renovações em sequência, cada uma matando a anterior.
    func conferirTokenDeMidia(comArte caminho: String?) async {
        guard let url = urlDaArte(caminho),
              await ConferenciaDaArte.combinada.aindaNaoFoi() else { return }
        guard let (_, resposta) = try? await URLSession.shared.data(from: url),
              (resposta as? HTTPURLResponse)?.statusCode == 401 else { return }
        _ = try? await renovarTokenDeMidia()
    }

    func urlDaArte(_ caminho: String?) -> URL? {
        guard let caminho, !caminho.isEmpty,
              let base = cofre.servidor,
              let token = cofre.tokenDeMidia,
              var partes = URLComponents(string: base + "/artwork/" + caminho)
        else { return nil }
        partes.queryItems = [URLQueryItem(name: "token", value: token)]
        return partes.url
    }
}
