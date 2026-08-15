import Foundation
import Testing
@testable import Odeon

/// Os testes do contrato e da conta de retomada.
///
/// ## ⚠️ Por que o contrato JSON merece teste, e é o motivo mais forte daqui
///
/// Porque **um nome de campo errado não estoura na tela** — ele vira lista vazia,
/// e lista vazia é exatamente o estado normal do mundo. O `FaixaDeAudioTest` do
/// Android diz isso melhor: «o defeito de contrato e o estado normal do mundo são
/// indistinguíveis na tela. O botão simplesmente não nasce, e ninguém descobre
/// por quê.»
///
/// No Swift o risco é o oposto e igualmente sério: o `Codable` **lança** quando a
/// chave falta, e o servidor omite chave de propósito. Sem a tolerância escrita à
/// mão, o app quebraria no caso comum.
struct ContratoTests {

    private let json = JSONDecoder()

    // MARK: - As versões de um filme

    /// O caso do dono: dois rips do mesmo 007, um em pt-BR e outro em inglês.
    /// Este é o formato que o servidor relatou em 14/08/2026.
    private let entradaComDuasVersoes = """
    {
      "id": "eddbfd12-1111-2222-3333-444444444444",
      "is_series": false,
      "title": "007: A Serviço Secreto de Sua Majestade",
      "year": 1969,
      "total": 8273,
      "versions": [
        { "id": "eddbfd12-1111-2222-3333-444444444444", "height": 818,
          "size_bytes": 2469606195, "audio_langs": ["por"],
          "position_seconds": 1558.5, "finished": false },
        { "id": "a950f840-f6f7-4390-b023-94eb14e59abd", "height": 816,
          "size_bytes": 2394829619, "audio_langs": [],
          "position_seconds": 4925.8, "finished": false }
      ]
    }
    """.data(using: .utf8)!

    @Test("lê as duas versões e os campos que a escolha desenha")
    func leAsDuasVersoes() throws {
        let item = try json.decode(ItemDaBiblioteca.self, from: entradaComDuasVersoes)
        #expect(item.versoes.count == 2)
        #expect(item.versoes[0].idiomasDeAudio == ["por"])
        #expect(item.versoes[0].height == 818)
        #expect(item.versoes[1].id == "a950f840-f6f7-4390-b023-94eb14e59abd")
        #expect(item.temEscolhaDeVersao)
    }

    /// ⚠️ O caso que decide se a escolha vale a pena existir.
    ///
    /// Com um dos lados sem idioma, o nome não distingue as duas — quem distingue
    /// é o `position_seconds`. Se este campo sumir do contrato, a modal vira
    /// `818p` contra `816p`, que é escolha nenhuma.
    @Test("o arquivo em inglês chega sem idioma, e o «parou em» é o que sobra")
    func inglesSemIdioma() throws {
        let item = try json.decode(ItemDaBiblioteca.self, from: entradaComDuasVersoes)
        let ingles = item.versoes[1]
        #expect(ingles.idiomasDeAudio.isEmpty, "o inglês não declara idioma")
        #expect(ingles.ondeParou == 4925.8)
    }

    /// ⚠️ **O caso de 8.230 das 8.273 entradas.** O servidor omite a chave, e no
    /// Swift chave ausente lança se ninguém tratar. Este teste é a prova de que a
    /// tolerância existe — sem ele, o app quebraria no caso comum e passaria no
    /// caso raro.
    @Test("sem a chave versions, decodifica e não há escolha")
    func semVersions() throws {
        let cru = #"{"id":"x","is_series":false,"title":"Star Wars III","total":8273}"#
        let item = try json.decode(ItemDaBiblioteca.self, from: Data(cru.utf8))
        #expect(item.versoes.isEmpty)
        #expect(!item.temEscolhaDeVersao)
        #expect(item.total == 8273)
    }

    /// ⚠️ Versão sem `id` é descartada, e a biblioteca **não** morre. O pior caso
    /// é a escolha não abrir e o cartão abrir a obra representante — o
    /// comportamento de antes do agrupamento. Degrada, não quebra.
    @Test("versão sem id não conta")
    func versaoSemId() throws {
        let cru = """
        {"id":"x","is_series":false,"title":"Cassino Royale","total":1,
         "versions":[{"height":800,"audio_langs":["por"]},
                     {"id":"real","height":798,"audio_langs":[]}]}
        """
        let item = try json.decode(ItemDaBiblioteca.self, from: Data(cru.utf8))
        #expect(item.versoes.count == 2, "as duas foram lidas")
        #expect(item.versoesEscolhiveis.count == 1, "só uma serve")
        #expect(!item.temEscolhaDeVersao, "e com uma só, não há escolha")
    }

    /// Uma versão só nunca abre escolha — pergunta com uma resposta é o §24.
    @Test("uma versão só não abre escolha")
    func umaVersaoSo() throws {
        let cru = #"{"id":"x","is_series":false,"title":"Y","total":1,"versions":[{"id":"a","height":1080,"audio_langs":["por"]}]}"#
        let item = try json.decode(ItemDaBiblioteca.self, from: Data(cru.utf8))
        #expect(!item.temEscolhaDeVersao)
    }

    /// ⚠️ Campo com tipo trocado **não derruba a tela**. É a escolha do `ou`:
    /// degradar pro padrão em vez de lançar. Um `total` que virasse texto no
    /// servidor não pode apagar a biblioteca inteira.
    @Test("tipo errado cai no padrão em vez de lançar")
    func tipoErrado() throws {
        let cru = #"{"id":"x","is_series":false,"title":"Z","total":"muitos"}"#
        let item = try json.decode(ItemDaBiblioteca.self, from: Data(cru.utf8))
        #expect(item.total == 0)
        #expect(item.title == "Z")
    }

    // MARK: - De onde continuar

    /// ⚠️ O piso é **5s** e não 30s, e foi decisão do dono contra a web: «a
    /// pessoa pode assistir um teco e voltar, isso já deve salvar o progresso
    /// dela».
    @Test("um teco conta, e um toque acidental não")
    func pisoDeCincoSegundos() {
        #expect(ondeContinuar(ondeParou: 15, duracaoEmSegundos: 7200, finished: false) == 15)
        #expect(ondeContinuar(ondeParou: 4, duracaoEmSegundos: 7200, finished: false) == 0)
        #expect(ondeContinuar(ondeParou: 5, duracaoEmSegundos: 7200, finished: false) == 0)
    }

    /// ⚠️ O defeito que deixava o filme **impossível de reabrir**: a posição de
    /// quem viu até o fim é o fim, e o player retomava lá.
    @Test("terminado volta pro começo")
    func terminadoVoltaProComeco() {
        #expect(ondeContinuar(ondeParou: 7100, duracaoEmSegundos: 7200, finished: true) == 0)
    }

    @Test("parado a 99% também volta, mesmo sem o servidor ter marcado")
    func quaseNoFim() {
        #expect(ondeContinuar(ondeParou: 7150, duracaoEmSegundos: 7200, finished: false) == 0)
    }

    /// ⚠️ **Duração `0` quer dizer «o servidor não sabe»**, nunca «o filme dura
    /// zero». Ela chega zerada em arquivo sem probe guardada, e sem esta leitura
    /// a conta `0 − 3401 ≤ 60` é verdadeira — mandando pro começo **todo** filme
    /// não medido.
    @Test("sem duração conhecida, retoma mesmo assim")
    func duracaoDesconhecida() {
        #expect(ondeContinuar(ondeParou: 3401, duracaoEmSegundos: nil, finished: false) == 3401)
        #expect(ondeContinuar(ondeParou: 3401, duracaoEmSegundos: 0, finished: false) == 3401)
    }

    // MARK: - A fração vista

    /// `nil` quando não dá pra saber — e aí a barrinha **não aparece**, em vez de
    /// aparecer zerada (§24).
    @Test("a barrinha some quando não há como calculá-la")
    func fracaoVista() throws {
        let cru = #"{"id":"a","title":"T","position_seconds":3600,"duration_seconds":7200}"#
        let item = try json.decode(ItemPraContinuar.self, from: Data(cru.utf8))
        #expect(item.fracaoVista == 0.5)

        let semDuracao = #"{"id":"a","title":"T","position_seconds":3600}"#
        let outro = try json.decode(ItemPraContinuar.self, from: Data(semDuracao.utf8))
        #expect(outro.fracaoVista == nil)
    }

    /// A arte da fileira, da mais específica pra menos: o quadro do episódio
    /// ganha do pôster da série, porque quem parou no meio reconhece a cena.
    @Test("a arte preferida é o still, não o pôster")
    func artePreferida() throws {
        let cru = #"{"id":"a","title":"T","poster":"p.jpg","backdrop":"b.jpg","still":"s.jpg"}"#
        let item = try json.decode(ItemPraContinuar.self, from: Data(cru.utf8))
        #expect(item.arte == "s.jpg")
    }
}

/// O perfil — a conta do nível, que é a única que a tela faz sozinha.
///
/// ## ⚠️ Por que só a fração é testada aqui
///
/// Porque é a **única** coisa desta tela que o cliente calcula. XP, nível, faixa e
/// conquistas vêm servidos: a curva é regra, e regra mora num lugar só. O que
/// sobra pro app é transformar três inteiros numa barra — e é aí que mora o
/// defeito silencioso, porque **barra cheia por falta de dado é indistinguível de
/// barra cheia por mérito**.
struct PerfilTests {

    private let json = JSONDecoder()

    private func progresso(_ corpo: String) throws -> ProgressoDoUsuario {
        try json.decode(ProgressoDoUsuario.self, from: corpo.data(using: .utf8)!)
    }

    @Test("a barra mede a faixa do nível, e não o XP total")
    func aBarraMedeAFaixa() throws {
        /// Os números do acervo real em 15/08/2026: nível 3, 384 de uma faixa que
        /// vai de 300 a 600. A fração é 84/300 — **não** 384/600, que daria 64% e
        /// seria a barra do XP acumulado numa régua que não é a dele.
        let p = try progresso(#"{"xp":384,"nivel":3,"xp_do_nivel":300,"xp_do_proximo":600,"desbloqueadas":9,"total":80}"#)
        #expect(p.fracaoDoNivel == 0.28)
        #expect(p.faltamProProximo == 216)
    }

    @Test("faixa de largura zero não vira barra cheia — a barra some")
    func faixaZeradaNaoViraBarra() throws {
        /// O último nível, ou um servidor que ainda não sabe o próximo. Sem esta
        /// guarda a divisão por zero daria `inf`, o `min(…, 1)` a aparariam em 1, e
        /// a tela mostraria **100% de progresso pra quem não progrediu** — dado
        /// inventado com cara de conquista (§18).
        #expect(try progresso(#"{"xp":384,"nivel":9,"xp_do_nivel":600,"xp_do_proximo":600}"#).fracaoDoNivel == nil)
        #expect(try progresso(#"{"xp":384,"nivel":9}"#).fracaoDoNivel == nil)
    }

    @Test("XP fora da faixa não estoura a barra nem a inverte")
    func xpForaDaFaixaNaoEstoura() throws {
        #expect(try progresso(#"{"xp":9999,"nivel":3,"xp_do_nivel":300,"xp_do_proximo":600}"#).fracaoDoNivel == 1)
        #expect(try progresso(#"{"xp":10,"nivel":3,"xp_do_nivel":300,"xp_do_proximo":600}"#).fracaoDoNivel == 0)
        #expect(try progresso(#"{"xp":9999,"nivel":3,"xp_do_nivel":300,"xp_do_proximo":600}"#).faltamProProximo == 0)
    }

    @Test("a trancada chega sem data e continua na lista")
    func aTrancadaChegaSemData() throws {
        /// ⚠️ `em: null` é o **estado normal** de 71 das 80 conquistas, e não
        /// defeito. Se a tolerância tratasse `null` como erro, a tela perderia as
        /// trancadas — e «um rosto que ninguém sabe que existe não é perseguido».
        let corpo = """
        [{"chave":"primeira_fita","nome":"A primeira fita","descricao":"Termine uma obra",
          "camada":"facil","pontos":10,"em":"2026-06-02T21:14:00Z"},
         {"chave":"estante_inteira","nome":"A estante inteira","descricao":"Complete 10 sagas",
          "camada":"dificil","pontos":150,"em":null}]
        """.data(using: .utf8)!
        let lista = try json.decode([ConquistaNaTela].self, from: corpo)
        #expect(lista.count == 2)
        #expect(lista[0].aberta)
        #expect(!lista[1].aberta)
        #expect(lista[1].pontos == 150)
    }

    @Test("o perfil sem enfeite nenhum decodifica")
    func perfilPelado() throws {
        /// Quem nunca escolheu rosto, capa nem moldura. ⚠️ No Swift isto **lança**
        /// sem a tolerância escrita à mão — e seria a tela inteira em branco por
        /// causa de três campos opcionais.
        let p = try json.decode(Perfil.self, from: #"{"username":"sam","display_name":"sam"}"#.data(using: .utf8)!)
        #expect(p.username == "sam")
        #expect(p.avatar == nil)
        #expect(p.capa == nil)
        #expect(p.moldura == nil)
        #expect(p.progresso == nil)
        #expect(p.conquistas.isEmpty)
    }
}

/// O guia: o filtro que um eixo vira, e o relógio da virada.
///
/// ## ⚠️ Por que só estas duas coisas
///
/// São as únicas que o cliente **decide**. Os eixos, os nomes, as contagens e o
/// ensaio vêm prontos; o que a tela faz sozinha é ler o `chave` e virar filtro, e
/// ler o `vira_em` e virar palavra. As duas são silenciosas quando erram — um
/// filtro errado devolve uma grade plausível, e uma data errada é uma frase bem
/// escrita —, que é exatamente o perfil de defeito que teste pega e olho não.
struct GuiaTests {

    private let json = JSONDecoder()

    private func faixa(_ rotulo: String, _ chave: String) throws -> FaixaDoGuia {
        try json.decode(
            FaixaDoGuia.self,
            from: #"{"rotulo":"\#(rotulo)","chave":"\#(chave)","obras":228}"#.data(using: .utf8)!,
        )
    }

    @Test("as duas formas do «chave», e é o conteúdo que decide")
    func asDuasFormasDoChave() throws {
        /// ⚠️ Gênero **e país** mandam `genre:…`; a década manda o ano cru. A
        /// função não sabe de qual fileira o toque veio, e é isso que a mantém
        /// sendo uma só — distinguir pelo nome da fileira faria «país» precisar de
        /// um terceiro ramo no dia em que o servidor mandasse `country:…`.
        let genero = Filtros.doEixo(try faixa("Drama", "genre:Drama"))
        #expect(genero.etiqueta == "genre:Drama")
        #expect(genero.anoDe == nil)
        #expect(genero.rotulo == "Drama")

        let decada = Filtros.doEixo(try faixa("1980", "1980"))
        #expect(decada.etiqueta == nil)
        #expect(decada.anoDe == 1980)
        #expect(decada.anoAte == 1989)
        /// Dentro de uma década a ordem é por ano — «em destaque» embaralharia dez
        /// anos de cinema.
        #expect(decada.ordem == "year")
    }

    @Test("kind=movie vai nos dois, e é o que faz a pílula não mentir")
    func kindMovieVaiNosDois() throws {
        /// ⚠️ Sem ele, «Comédia» traria 3.220 entradas em que a maioria é episódio
        /// de série, e o número que a pílula prometia era o dos filmes. Medido em
        /// 15/08/2026: `tags=genre:Drama` sozinho devolve **252**, e com
        /// `kind=movie` devolve **216**.
        #expect(Filtros.doEixo(try faixa("Drama", "genre:Drama")).tipo == "movie")
        #expect(Filtros.doEixo(try faixa("1980", "1980")).tipo == "movie")
    }

    @Test("o filtro desligado não manda parâmetro nenhum")
    func desligadoNaoMandaNada() {
        #expect(!Filtros().ligado)
        #expect(Filtros(rotulo: "Drama").ligado == false)
    }

    @Test("o eixo desconhecido não vira rótulo inventado")
    func eixoDesconhecidoNaoViraRotulo() throws {
        /// ⚠️ O servidor manda o eixo em código, e a frase em português é da tela.
        /// Um eixo que este app não conhece **não desenha rótulo**, em vez de
        /// escrever «saga da semana» sobre algo que talvez não seja isso (§18).
        func eixo(_ e: String) throws -> Revista {
            try json.decode(Revista.self, from: #"{"eixo":"\#(e)","tema":"X"}"#.data(using: .utf8)!)
        }
        #expect(try eixo("genero").rotuloDoEixo == "gênero da semana")
        #expect(try eixo("diretor").rotuloDoEixo == "diretor da semana")
        #expect(try eixo("elenco_favorito").rotuloDoEixo == nil)
        #expect(try eixo("").rotuloDoEixo == nil)
    }

    @Test("sem ensaio não há parágrafo, e aí a seção some")
    func semEnsaioNaoHaParagrafo() throws {
        func revista(_ corpo: String) throws -> Revista {
            try json.decode(Revista.self, from: corpo.data(using: .utf8)!)
        }
        /// ⚠️ `null` é o estado normal quando não há chave do LLM. A tela omite a
        /// seção — não mostra «carregando» nem inventa prosa.
        #expect(try revista(#"{"eixo":"genero","tema":"X","ensaio":null}"#).paragrafos.isEmpty)
        /// Linha vazia entre parágrafos não vira parágrafo vazio, que desenharia
        /// um vão no meio da matéria (§24).
        #expect(try revista(#"{"ensaio":"um\n\n  \ndois"}"#).paragrafos == ["um", "dois"])
    }

    @Test("a saga não abre ficha, e a obra abre")
    func aSagaNaoAbreFicha() throws {
        /// ⚠️ O `id` de uma saga é de **coleção**, e mandá-lo pra tela da obra
        /// daria erro — oferecer o toque que vai falhar é o §53 ao contrário.
        func evento(_ tipo: String, _ id: String) throws -> EventoDaSemana {
            try json.decode(
                EventoDaSemana.self,
                from: #"{"tipo":"\#(tipo)","id":"\#(id)","titulo":"X"}"#.data(using: .utf8)!,
            )
        }
        #expect(try evento("obra", "abc").abreFicha)
        #expect(try !evento("saga", "abc").abreFicha)
        #expect(try !evento("obra", "").abreFicha)
    }

    @Test("a chamada do evento muda com o que se sabe")
    func aChamadaDoEventoMuda() throws {
        func evento(obras: Int, suas: Int, participou: Bool) throws -> EventoDaSemana {
            try json.decode(EventoDaSemana.self, from: """
            {"tipo":"saga","id":"a","titulo":"X","obras":\(obras),"suas":\(suas),
             "participou":\(participou)}
            """.data(using: .utf8)!)
        }
        #expect(try chamadaDoEvento(evento(obras: 4, suas: 1, participou: true), quando: "segunda")
            == "Você participou.")
        /// ⚠️ «suas de obras» só entra numa saga: em obra única a frase seria
        /// «você já viu 0 de 1», que é escrever com número o que o convite já diz.
        #expect(try chamadaDoEvento(evento(obras: 1, suas: 0, participou: false), quando: "segunda")
            == "Termine até segunda pra participar.")
        /// E sem prazo a oração inteira some, em vez de prometer um dia que
        /// ninguém conferiu.
        #expect(try chamadaDoEvento(evento(obras: 1, suas: 0, participou: false), quando: nil)
            == "Termine pra participar.")
    }
}

/// O relógio da virada — a locadora e a revista viram no **mesmo instante**.
///
/// ## ⚠️ O fuso é fixado, e a fixação é o teste
///
/// A vitrine vira às **3h da manhã UTC**, que em São Paulo é meia-noite. Isso não
/// é detalhe: é o que decide entre «vira segunda» e «vira domingo», e entre
/// «amanhã» e «hoje». Um teste que lesse o relógio da máquina passaria aqui e
/// falharia numa CI em UTC — pelo fuso, não pelo código.
struct ViradaTests {

    /// Meia-noite de segunda-feira em São Paulo, no formato que o servidor manda.
    private let segunda = "2026-08-17T03:00:00Z"
    private let saoPaulo = TimeZone(identifier: "America/Sao_Paulo")!

    private func em(_ iso: String) -> Date { ISO8601DateFormatter().date(from: iso)! }

    private func frase(_ iso: String, _ agora: String) -> String? {
        viraQuando(iso, agora: em(agora), fuso: saoPaulo)
    }

    @Test("dentro da semana, o dia da semana — e não a contagem")
    func dentroDaSemanaODiaDaSemana() {
        /// «Numa casa, "vira segunda" é a informação; "vira em 5 dias" é um
        /// cronômetro.» É a decisão da web, herdada inteira.
        #expect(frase(segunda, "2026-08-14T10:00:00Z") == "segunda-feira")
        /// E o dia sai da data, não de uma constante: um domingo diz «domingo».
        #expect(frase("2026-08-16T03:00:00Z", "2026-08-13T10:00:00Z") == "domingo")
    }

    @Test("«amanhã» é dia de calendário, e não «faz menos de 24h»")
    func amanhaEDiaDeCalendario() {
        /// ⚠️ A vitrine vira à meia-noite de segunda. Às 20h de domingo faltam
        /// **quatro horas** — e a palavra certa é «amanhã», não «em 4 horas». Numa
        /// casa, amanhã é o dia seguinte no calendário.
        #expect(frase(segunda, "2026-08-16T23:00:00Z") == "amanhã")
        /// E às 22h de domingo faltam duas horas: continua sendo amanhã.
        #expect(frase(segunda, "2026-08-17T01:00:00Z") == "amanhã")
        #expect(frase(segunda, "2026-08-17T04:00:00Z") == "hoje")
    }

    @Test("passado não vira frase")
    func passadoNaoViraFrase() {
        /// Já virou. A linha **some** em vez de prometer uma virada que aconteceu
        /// (§24) — uma vitrine sem data anunciada continua girando; o que ela
        /// perde é a promessa, não o funcionamento.
        #expect(frase(segunda, "2026-08-18T04:00:00Z") == nil)
    }

    @Test("passando de uma semana vira data, e não «em 9 dias»")
    func passandoDeUmaSemanaViraData() {
        /// ⚠️ Porque quem chama põe o verbo: «até **em 9 dias**» não é português.
        /// Uma função que só serve depois de um verbo específico voltaria a ser
        /// duas funções na primeira vez que alguém precisasse do outro.
        #expect(frase(segunda, "2026-08-05T10:00:00Z") == "17 de agosto")
    }

    @Test("carimbo que não parseia não vaza pra tela")
    func carimboQueNaoParseiaNaoVaza() {
        /// ⚠️ O defeito que isto conserta esteve na tela: **«a vitrine vira em
        /// 2026-08-17T03:00:00Z»**. Visível sem ser legível — a família do §8b.
        #expect(viraQuando(nil) == nil)
        #expect(viraQuando("") == nil)
        #expect(viraQuando("segunda que vem") == nil)
        /// ⚠️ E este é o caso que quase escapou: o servidor manda **sem** fração
        /// de segundo, e um `ISO8601DateFormatter` com `.withFractionalSeconds`
        /// recusa exatamente essa forma. Sem a segunda tentativa a frase sumiria
        /// sempre — e sumir em silêncio é o defeito que ninguém abre chamado.
        #expect(frase(segunda, "2026-08-14T10:00:00Z") != nil)
        #expect(frase("2026-08-17T03:00:00.000Z", "2026-08-14T10:00:00Z") != nil)
    }
}

/// Os baixados: as duas regras que decidem se um filme guardado abre.
struct BaixadosTests {

    @Test("a extensão vem do nome do servidor, e o padrão é o único chute honesto")
    func aExtensaoVemDoServidor() {
        /// ⚠️ Isto custou duas capturas. Um mp4 perfeito gravado como `.filme`
        /// deu **`AVFoundationErrorDomain -11828`**: o AVFoundation escolhe o
        /// demuxer de arquivo local **pela extensão**, não pelos bytes — não
        /// fareja o `ftyp`.
        #expect(FichaDoBaixado.extensaoDe("007 Contra a Chantagem Atômica (1965).mp4") == "mp4")
        #expect(FichaDoBaixado.extensaoDe("Filme.MOV") == "mov")
        #expect(FichaDoBaixado.extensaoDe("serie.s01e04.m4v") == "m4v")
        /// Ponto no meio do nome não é extensão do nome inteiro.
        #expect(FichaDoBaixado.extensaoDe("Amélie.Poulain.2001.1080p.mp4") == "mp4")
        /// ⚠️ Sem extensão, `mp4` — e é o chute menos ruim porque **só
        /// `direct_play` chega aqui**, e o iPhone declara mp4, mov e m4v.
        #expect(FichaDoBaixado.extensaoDe("filme sem extensao") == "mp4")
        #expect(FichaDoBaixado.extensaoDe("") == "mp4")
    }

    @MainActor
    @Test("o nome no disco é o id, e não a URL")
    func oNomeNoDiscoEOId() {
        /// ⚠️ A URL carrega o token de mídia, que **muda a cada renovação** — o
        /// mesmo filme viraria dois downloads e dois arquivos de 2 GB. O
        /// `Downloads.kt` do Android registra o mesmo tropeço.
        let ficha = FichaDoBaixado(
            obraId: "obra", arquivoId: "88e992c3", titulo: "X",
            poster: nil, backdrop: nil, duracaoEmSegundos: nil, ano: nil, extensao: "mp4",
        )
        #expect(Baixados.arquivoNoDisco(ficha).lastPathComponent == "88e992c3.mp4")
    }
}

/// Ao vivo: quem está no ar, e o que «sintonizar» significa.
///
/// ## ⚠️ Estas contas erram **em silêncio**
///
/// Um programa escolhido errado não dá erro: dá um filme plausível começando num
/// minuto plausível. E a borda — o segundo exato da virada — só aparece uma vez
/// por programa, na casa de alguém, sem ninguém olhando.
struct AoVivoTests {

    private let json = JSONDecoder()

    private func grade(_ corpo: String) throws -> GradeDoOdeon {
        try json.decode(GradeDoOdeon.self, from: corpo.data(using: .utf8)!)
    }

    private func em(_ iso: String) -> Date { ISO8601DateFormatter().date(from: iso)! }

    /// Um canal com dois programas colados: um acaba às 10:00, o outro começa às
    /// 10:00.
    private let doisColados = """
    {"agora":"2026-08-15T09:30:00Z",
     "canais":[{"slug":"odeon-1","nome":"Odeon 1","numero":"101"}],
     "programas":[
       {"id":"a","canal":"odeon-1","work_id":"o1","media_file_id":"f1",
        "title":"O primeiro","year":2015,"starts_at":"2026-08-15T09:00:00Z",
        "ends_at":"2026-08-15T10:00:00Z"},
       {"id":"b","canal":"odeon-1","work_id":"o2","media_file_id":"f2",
        "title":"O segundo","year":2020,"starts_at":"2026-08-15T10:00:00Z",
        "ends_at":"2026-08-15T11:00:00Z"}]}
    """

    @Test("na virada, só um está no ar — e é o que começa")
    func naViradaSoUmEstaNoAr() throws {
        let g = try grade(doisColados)
        /// ⚠️ **`começa <= agora < termina`, e o `<` no fim é o teste.** No segundo
        /// exato das 10:00 os dois seriam elegíveis com `<=` dos dois lados, e a
        /// tela piscaria entre eles uma vez por programa.
        #expect(emCartaz(agora: em("2026-08-15T10:00:00Z"), doOdeon: g).first?.titulo == "O segundo")
        #expect(emCartaz(agora: em("2026-08-15T09:59:59Z"), doOdeon: g).first?.titulo == "O primeiro")
        /// Um canal, um quadro — nunca dois.
        #expect(emCartaz(agora: em("2026-08-15T10:00:00Z"), doOdeon: g).count == 1)
    }

    @Test("fora da grade, o canal não aparece")
    func foraDaGradeOCanalNaoAparece() throws {
        /// ⚠️ Canal **da casa** sem programa agora some, e é diferente do canal de
        /// fora sem EPG: aqui a casa sabe a grade inteira, então «nada no ar» é
        /// uma afirmação verdadeira, não uma ausência de dado (§18).
        #expect(emCartaz(agora: em("2026-08-15T23:00:00Z"), doOdeon: try grade(doisColados)).isEmpty)
    }

    @Test("canal de fora sem EPG entra assim mesmo")
    func canalSemEpgEntra() throws {
        /// ⚠️ Ele existe e está no ar; o que não se sabe é o que está passando, e
        /// as duas coisas são diferentes. Escondê-lo faria a lista mentir sobre
        /// quantos canais a casa tem.
        let canais = try json.decode([CanalNoAr].self, from: """
        [{"id":"c1","name":"Videoteca","number":"7"}]
        """.data(using: .utf8)!)
        let quadros = emCartaz(agora: em("2026-08-15T10:00:00Z"), doOdeon: nil, externos: canais)
        #expect(quadros.count == 1)
        #expect(quadros[0].titulo == "sem programação")
        #expect(quadros[0].numero == "7")
        /// E sem começo nem fim **não há barra** — barra zerada seria decoração
        /// com cara de dado.
        #expect(quadros[0].andamento(agora: em("2026-08-15T10:00:00Z")) == nil)
    }

    @Test("os da casa vêm primeiro")
    func osDaCasaVemPrimeiro() throws {
        let canais = try json.decode([CanalNoAr].self, from: #"[{"id":"c1","name":"Videoteca"}]"#.data(using: .utf8)!)
        let quadros = emCartaz(agora: em("2026-08-15T09:30:00Z"),
                               doOdeon: try grade(doisColados), externos: canais)
        #expect(quadros.map(\.daCasa) == [true, false])
    }

    @Test("«sintonizar» é uma coisa na casa e outra fora — e mandar a errada deu 400")
    func sintonizarSaoDuasCoisas() throws {
        let daCasa = emCartaz(agora: em("2026-08-15T09:30:00Z"), doOdeon: try grade(doisColados))[0]
        /// Canal da casa tem obra e arquivo: sintonizar é **tocar o filme** no
        /// ponto em que ele já está.
        #expect(daCasa.podeTocarDireto)
        #expect(daCasa.quantoJaPassou(agora: em("2026-08-15T09:30:00Z")) == 1800)

        let deFora = try json.decode([CanalNoAr].self, from: #"[{"id":"c1","name":"X"}]"#.data(using: .utf8)!)
        /// ⚠️ Sem obra atrás, o caminho é o `POST /api/live/{id}/watch`. Mandá-lo
        /// num canal da casa respondeu **400** na tela.
        #expect(!emCartaz(agora: .now, doOdeon: nil, externos: deFora)[0].podeTocarDireto)
    }

    @Test("relógio adiantado não pede instante negativo")
    func relogioAdiantadoNaoPedeNegativo() throws {
        let q = emCartaz(agora: em("2026-08-15T09:30:00Z"), doOdeon: try grade(doisColados))[0]
        #expect(q.quantoJaPassou(agora: em("2026-08-15T08:00:00Z")) == 0)
    }
}
