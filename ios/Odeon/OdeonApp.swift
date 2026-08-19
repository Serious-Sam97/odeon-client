import SwiftUI

/// O Odeon no iPhone e no iPad.
///
/// ## Onde isto está — F3 do `docs/PLANO.md`
///
/// Endereço, login, sessão, contrato, capacidades e **o player**. A biblioteca
/// (F4) ainda não existe: o caminho pra chegar num filme é a fileira de
/// «continuar», que é a lista mais curta que leva a vídeo de verdade.
///
/// ⚠️ O player vem **antes** das telas de propósito. Se o acervo não tocasse no
/// iPhone, toda tela feita antes disso teria sido feita sobre suposição — e a
/// investigação do §26 do Android é o registro do que essa aposta custa.
@main
struct OdeonApp: App {
    /// ⚠️ Escuro fixo: não há tema claro neste produto («o produto é uma sala
    /// escura; um tema claro seria uma segunda paleta pra manter sem ninguém
    /// pedindo» — `REDESENHO.md` §5).
    var body: some Scene {
        WindowGroup {
            Raiz().preferredColorScheme(.dark)
        }
    }
}

/// Quem decide qual tela existe: há sessão guardada ou não.
///
/// ⚠️ Ter token **não** prova que ele vale — dura 90 dias e pode ter sido
/// revogado. A prova vem no primeiro 401. Verificar na abertura custaria uma
/// requisição em todo arranque pra responder o que a primeira chamada já responde.
struct Raiz: View {
    private let odeon = RepositorioOdeon(cofre: Cofre())
    @State private var entrou: Bool

    /// ## ⚠️ **E** endereço, não só sessão — um estado que o app alcançava
    ///
    /// A sessão mora no **Keychain** e o endereço no **UserDefaults**, e os dois
    /// não morrem juntos: o Keychain sobrevive a coisas que apagam o UserDefaults.
    ///
    /// Visto na tela em 15/08/2026: o app abriu **já logado** — sem pedir senha,
    /// porque o token estava lá — e todas as abas disseram «sem resposta do
    /// servidor», para sempre. A tela de login, que é onde mora o campo do
    /// endereço, era inalcançável.
    ///
    /// ⚠️ E não dá pra consertar adivinhando: este app **não tem palpite padrão**
    /// de endereço, de propósito — a casa de cada um está num lugar. Uma sessão
    /// que não sabe com quem falar não é uma sessão; é uma tela vazia com um token
    /// dentro. Então o app volta pro login, que é onde a pergunta pode ser feita.
    init() {
        let cofre = Cofre()
        _entrou = State(initialValue: cofre.sessao != nil && cofre.servidor != nil)
    }

    var body: some View {
        if entrou {
            Abas(odeon: odeon) { entrou = false }
        } else {
            TelaDeLogin(odeon: odeon) { _ in entrou = true }
        }
    }
}

/// ⚠️ A `TelaDeContinuar` **deixou de existir**, e é parte da correção de
/// 15/08/2026: no Android «continuar» nunca foi um destino — é o herói no topo da
/// biblioteca e uma fileira logo abaixo dele. Uma aba própria dava a esses filmes
/// o mesmo peso que o acervo inteiro, e tirava da biblioteca a primeira coisa que
/// ela tem a dizer quando abre: «você parou aqui».
///
/// O que ela sabia agora mora no `ModeloDaBiblioteca`.

/// As abas.
///
/// ## ⚠️ A peça é da Apple; o raciocínio é herdado
///
/// O Android decidiu **barra inferior em retrato e trilho lateral em paisagem e
/// tablet**, e a decisão veio de medida: a barra inferior comia **21% da tela em
/// paisagem, com zero fileiras da grade visíveis**; o trilho custa 8,6% da
/// largura e devolve a altura inteira.
///
/// O `TabView` do iOS resolve isso sozinho no iPad e em paisagem desde o iOS 18 —
/// mas a lição que atravessa é a outra: **«o padrão de uma biblioteca não é a
/// decisão do produto»**. A primeira montagem do Android confiou no padrão do
/// componente e saiu pior que o defeito já consertado uma vez. Então isto fica
/// anotado pra ser **medido em paisagem e no iPad**, não presumido.
struct Abas: View {
    let odeon: RepositorioOdeon
    let aoSair: () -> Void

    init(odeon: RepositorioOdeon, aoSair: @escaping () -> Void) {
        self.odeon = odeon
        self.aoSair = aoSair
        _baixados = State(wrappedValue: Baixados(odeon: odeon))
        _insignia = State(wrappedValue: Insignia(odeon: odeon))
    }

    @State private var escolhendoVersao: ItemDaBiblioteca?
    /// O perfil, aberto pelo rosto do canto.
    @State private var perfilAberto = false
    @State private var paraVoceAberto = false
    /// Os baixados, abertos pelo chip da barra de filtros da biblioteca.
    @State private var baixadosAbertos = false
    @State private var muralAberto = false


    /// ## ⚠️ Um estado só, e não dois — o defeito que isto conserta
    ///
    /// A primeira versão tinha `abrindoFicha` e `tocando` como dois `@State`
    /// independentes, cada um com sua `fullScreenCover`. Abrir o player a partir
    /// da ficha fazia `abrindoFicha = nil` e `tocando = …` **no mesmo quadro** — e
    /// o SwiftUI não apresenta uma cobertura enquanto outra está saindo. Visto na
    /// tela: a ficha fechava, o player não abria, e voltava pra a grade sem uma
    /// palavra de explicação.
    ///
    /// Com um estado só, a transição é troca de valor e não corrida entre dois.
    /// É a mesma família do `Filtros` do Android: campos que **viajam juntos**
    /// viram um objeto, senão cada regra vira uma linha que alguém esquece.
    @State private var destino: Destino?

    /// Qual aba está aberta.
    ///
    /// ⚠️ Ela existe porque o **guia manda na biblioteca**: tocar em «Terror»
    /// fecha a casa, liga o filtro e precisa levar a pessoa até a grade. Sem esta
    /// ligação o filtro entraria em silêncio numa aba que ninguém está olhando, e
    /// a casa fecharia sobre a tela em que já se estava — o que lê como toque que
    /// não fez nada.
    @State private var aba: Aba = .biblioteca

    /// ## ⚠️ **Cinco**, e não quatro — corrigido em 15/08/2026
    ///
    /// Elas eram biblioteca, locadora, para-você e **continuar**, com mural,
    /// guia, perfil e baixados atrás de um ícone de casa que eu inventei. Aberto
    /// o emulador do Android, o app real tem outra planta: **mural e guia são
    /// abas**, «continuar» é uma seção dentro da biblioteca, o perfil entra pelo
    /// rosto no canto e os baixados são um **filtro** da grade.
    ///
    /// ⚠️ O erro tem nome e é meu: construí contra o `REDESENHO.md` §6, que é um
    /// documento de **proposta** («a decisão que precisa vir antes da R1») e fala
    /// em quatro destinos. O app seguiu adiante e ninguém reescreveu a proposta.
    /// A régua da casa — «ver na tela» — eu apliquei ao meu trabalho e não à
    /// minha referência.
    /// ## ⚠️ **Cinco cabem; a sexta some.** Medido em 15/08/2026
    ///
    /// Com seis, o iOS desenhou `biblioteca · locadora · mural · guia · Mais` e
    /// enfiou **ao vivo e para-você** na gaveta. Trocar um destino invisível por
    /// dois é pior que o problema.
    ///
    /// Então a barra tem cinco, e quem sai é o **mural** — não por ser menos
    /// importante, mas por ser o que menos se abre num dia: ele conta o que os
    /// outros fizeram, e isso se lê de vez em quando. «Ao vivo» é uma pergunta
    /// que se faz **agora**, e pergunta de agora não pode estar a dois toques.
    /// ⚠️ **`series` entrou e `paraVoce` saiu da barra** — 18/08/2026. As séries
    /// viraram biblioteca própria, como o dono aprovou; o «para você» desceu pra
    /// gaveta do canto, o mesmo caminho que o mural fez no Android quando o ao
    /// vivo entrou. Cinco lugares, e a régua é a de sempre.
    enum Aba: Hashable { case biblioteca, series, aoVivo, locadora, guia }

    /// O filtro da biblioteca.
    ///
    /// ⚠️ Ele mora **aqui**, e não na grade, porque quem o liga é outra tela. É a
    /// mesma regra do `Destino`: o estado mora no menor lugar que enxerga todos os
    /// donos, senão vira duas cópias que discordam.
    @State private var filtros = Filtros()

    /// A prateleira do aparelho.
    ///
    /// ⚠️ **Uma instância pro app inteiro**, e não uma por tela. Ela abre uma
    /// `URLSession` de fundo com identificador fixo, e o `URLSession` recusa uma
    /// segunda sessão com o mesmo identificador — dois `Baixados` seriam um
    /// funcionando e outro mudo. Nasce aqui porque aqui é o menor lugar que
    /// enxerga a ficha (que baixa), a tela dos baixados (que lista) e o player
    /// (que toca do disco).
    @State private var baixados: Baixados
    /// ⚠️ Uma insígnia pro app inteiro: as cinco abas desenham o mesmo rosto no
    /// canto, e cinco cópias seriam cinco requisições e cinco momentos diferentes
    /// de ficar velha.
    @State private var insignia: Insignia

    enum Destino: Identifiable {
        case ficha(String)
        case tocando(Alvo)
        /// ⚠️ Um caso próprio, e não um `Alvo` com um `URL?` dentro. O que
        /// distingue este destino não é um campo a mais: é **não haver servidor**
        /// na história. Enfiá-lo no `Alvo` faria as duas aberturas passarem pelo
        /// mesmo caminho, e o dia em que alguém acrescentasse uma chamada de rede
        /// ali quebraria o offline sem nenhum sinal.
        case doDisco(FichaDoBaixado)
        /// ⚠️ Um caso próprio, como o `doDisco`: o que distingue um canal não é um
        /// campo a mais — é **não haver arquivo nem obra**. O player recebe uma
        /// playlist e um nome, e nada mais existe pra pedir.
        case canal(QuadroNoAr)
        /// ## ⚠️ A ficha de uma série — 18/08/2026
        ///
        /// Um caso próprio, e não `.ficha` com outro id: o id de uma série é de
        /// **coleção**, e `/api/works/{id}` não conhece coleção. Tocar numa série
        /// abria a ficha da obra e dava **404** — o mesmo defeito que a locadora
        /// pagou com a caixa de temporadas, e que a TV pagou na busca.
        case serie(id: String, titulo: String)
        var id: String {
            switch self {
            case let .ficha(obra): "ficha:" + obra
            case let .tocando(alvo): "toca:" + alvo.arquivo
            case let .doDisco(ficha): "disco:" + ficha.arquivoId
            case let .canal(q): "canal:" + q.canalId
            case let .serie(id, _): "serie:" + id
            }
        }
    }

    /// O que o player precisa saber. Um tipo em vez de cinco `@State` soltos:
    /// eles viajam juntos e separá-los é como um deles fica velho.
    struct Alvo: Identifiable {
        let id = UUID()
        let obra: String
        let arquivo: String
        let duracao: Double?
        var titulo: String = ""
        let comecarEm: Double
        /// ⚠️ **De onde se veio**, porque sair tem de devolver ao mesmo lugar.
        ///
        /// Visto na tela: sintonizei o Odeon 1, o filme tocou, e ao sair caí na
        /// **ficha de «O Lagosta»** — uma tela que eu nunca abri, sobre um filme
        /// que eu não escolhi. Escolhi um **canal**. Voltar pra ficha é o app
        /// contando outra história do que aconteceu.
        var doAoVivo = false
    }

    var body: some View {
        TabView(selection: $aba) {
            /// ⚠️ Ela se chama **filmes**: as séries saíram daqui.
            Tab("filmes", systemImage: "square.grid.2x2", value: Aba.biblioteca) {
                TelaDaBiblioteca(
                    odeon: odeon, insignia: insignia, baixados: baixados,
                    filtros: $filtros,
                    aoAbrirPerfil: { perfilAberto = true },
                    aoAbrirBaixados: { baixadosAbertos = true },
                    aoSair: aoSair,
                    /// ⚠️ Vai pra **ficha**, e não direto pro filme — é o que o
                    /// Android faz na mesma fileira. A ficha mostra de onde vai
                    /// continuar, oferece o começo, e é de onde se escolhe
                    /// arquivo. Pular direto economizaria um toque e tiraria as
                    /// outras três coisas.
                    aoEscolher: { item in
                        /// ⚠️ Filme com mais de uma versão **pergunta qual** antes
                        /// de abrir a ficha — os 43 grupos. E a escolha leva pra a
                        /// ficha **da obra escolhida**: nada é fundido, e cada
                        /// versão tem ficha própria.
                        /// ⚠️ A série vem **antes** da escolha de versão: uma
                        /// coleção não tem versões, e perguntar «qual delas?»
                        /// sobre uma série seria a segunda pergunta errada em
                        /// cima da primeira.
                        if item.eSerie {
                            destino = .serie(id: item.id, titulo: item.title)
                        } else if item.temEscolhaDeVersao {
                            escolhendoVersao = item
                        } else {
                            destino = .ficha(item.id)
                        }
                    },
                    aoContinuar: { destino = .ficha($0.id) },
                )
            }
            /// ## As séries, biblioteca própria · 18/08/2026
            ///
            /// ⚠️ **Três lombadas na estante**, e não uma TV nem um `play`: uma
            /// TV diria «ao vivo» (a aba do lado já é essa) e um `play` diria
            /// «tocar», que é o que toda aba leva a fazer. O que só a série tem é
            /// ser muitas coisas guardadas juntas.
            Tab("séries", systemImage: "books.vertical", value: Aba.series) {
                TelaDasSeries(
                    odeon: odeon, insignia: insignia,
                    aoAbrirPerfil: { perfilAberto = true },
                    aoSair: aoSair,
                    aoAbrirSerie: { destino = .serie(id: $0.id, titulo: $0.title) },
                    aoAbrirObra: { destino = .ficha($0) },
                )
            }
            Tab("locadora", systemImage: "film.stack", value: Aba.locadora) {
                TelaDaLocadora(odeon: odeon, insignia: insignia,
                               aoAbrirPerfil: { perfilAberto = true }, aoSair: aoSair)
                { caixa in destino = .ficha(caixa.id) }
                aoTocarDoMenu: { disco, segundos in
                    destino = .tocando(Alvo(
                        obra: disco.obraId, arquivo: disco.arquivoId,
                        duracao: disco.duracao, titulo: disco.titulo,
                        comecarEm: segundos,
                    ))
                }
            }
            Tab("guia", systemImage: "newspaper", value: Aba.guia) {
                TelaDoGuia(
                    odeon: odeon,
                    insignia: insignia,
                    aoAbrirObra: { destino = .ficha($0) },
                    /// ⚠️ Ligar o filtro **e** trocar de aba: o toque em «Terror»
                    /// tem que parecer uma coisa só, e uma grade filtrada atrás de
                    /// uma aba que ninguém está olhando lê como toque que não fez
                    /// nada.
                    aoFiltrar: { filtros = $0; aba = .biblioteca },
                    aoAbrirPerfil: { perfilAberto = true },
                    aoSair: aoSair,
                    aoAbrirMural: { muralAberto = true },
                )
            }
            /// ⚠️ **Ao vivo é aba**, e não uma linha dentro do guia.
            ///
            /// Eu a tinha posto no guia com um argumento que parecia bom — o iOS
            /// colapsa em «More» a partir da sexta. O dono olhou e disse que
            /// faltava no menu, e está certo: **um destino que não está na barra
            /// não existe**. O custo do «More» é sobre entrar; esconder num
            /// submenu de outra tela é custo maior, e sem aviso nenhum.
            Tab("ao vivo", systemImage: "dot.radiowaves.left.and.right", value: Aba.aoVivo) {
                TelaAoVivo(
                    odeon: odeon, insignia: insignia,
                    aoAbrirPerfil: { perfilAberto = true },
                    aoSairDaConta: aoSair,
                    aoSintonizar: { quadro in
                        if quadro.podeTocarDireto, let obra = quadro.obraId, let arquivo = quadro.arquivoId {
                            destino = .tocando(Alvo(
                                obra: obra, arquivo: arquivo, duracao: nil,
                                titulo: quadro.titulo,
                                comecarEm: quadro.quantoJaPassou(agora: .now),
                                doAoVivo: true,
                            ))
                        } else {
                            destino = .canal(quadro)
                        }
                    },
                )
            }
        }
        .tint(Cores.destaque)
        .sheet(item: $escolhendoVersao) { item in
            EscolhaDeVersao(item: item) { versao in
                escolhendoVersao = nil
                destino = .ficha(versao.id)
            }
            .presentationDetents([.height(300)])
        }
        .sheet(isPresented: $perfilAberto) {
            TelaDoPerfil(odeon: odeon) { perfilAberto = false }
        }
        /// ⚠️ **«para você» virou folha** — 18/08/2026. Ele saiu da barra pra as
        /// séries entrarem, e não sumiu: abre pelo mesmo canto que o perfil. É a
        /// mesma decisão que o Android tomou com o mural, com o mesmo argumento —
        /// descoberta é o que se procura de vez em quando, e não o que se pega
        /// às nove da noite.
        .sheet(isPresented: $paraVoceAberto) {
            TelaParaVoce(odeon: odeon, insignia: insignia,
                         aoAbrirPerfil: { perfilAberto = true }, aoSair: aoSair)
            { item in
                paraVoceAberto = false
                destino = .ficha(item.id)
            }
        }
        .sheet(isPresented: $muralAberto) {
            TelaDoMural(
                odeon: odeon, insignia: insignia,
                aoEscolher: { obra in
                    muralAberto = false
                    destino = .ficha(obra)
                },
                aoAbrirPerfil: { perfilAberto = true },
                aoSair: aoSair,
                aoFechar: { muralAberto = false },
            )
        }
        .sheet(isPresented: $baixadosAbertos) {
            TelaDosBaixados(
                odeon: odeon,
                baixados: baixados,
                /// ⚠️ Ela vai **direto pro player**, e não pra ficha como o resto.
                /// A ficha pede rede pra existir, e este é o único caminho do app
                /// que tem que funcionar sem ela.
                aoTocar: { ficha in
                    baixadosAbertos = false
                    destino = .doDisco(ficha)
                },
                aoFechar: { baixadosAbertos = false },
            )
        }
        .task { await insignia.carregar() }
        .fullScreenCover(item: $destino) { onde in
            switch onde {
            case let .ficha(id):
                TelaDaObra(
                    odeon: odeon,
                    baixados: baixados,
                    obraId: id,
                    /// A troca é **um** movimento: a ficha vira player no mesmo
                    /// estado, sem fechar e reabrir cobertura.
                    /// ⚠️ **A duração e o título viajam junto**, e não são
                    /// buscados de novo lá dentro. O player precisa dos dois pra
                    /// desenhar a barra de cima e o «faltam» — e a duração
                    /// **especialmente**: em HLS o item chega sem ela, e foi
                    /// justamente isso que pôs «4:44 AM» na tela.
                    aoTocar: { arquivo, comecarEm, duracao, titulo in
                        destino = .tocando(Alvo(obra: id, arquivo: arquivo,
                                                duracao: duracao, titulo: titulo,
                                                comecarEm: comecarEm))
                    },
                    aoVoltar: { destino = nil },
                )
            case let .serie(id, titulo):
                /// ⚠️ Uma `NavigationStack` **aqui dentro**, e não uma cobertura
                /// nova por temporada: série → temporada é uma pilha de dois, e o
                /// «voltar» do iOS já sabe desenhar isso. Empilhar coberturas
                /// daria duas telas cheias sem gesto de voltar entre elas.
                NavigationStack {
                    TelaDaSerie(
                        odeon: odeon,
                        modelo: ModeloDaSerie(odeon: odeon, serieId: id, titulo: titulo),
                        aoTocar: { episodio, em in
                            destino = .tocando(Alvo(
                                obra: episodio.id,
                                arquivo: episodio.arquivoId ?? "",
                                duracao: episodio.duracaoEmSegundos,
                                titulo: episodio.title,
                                comecarEm: em,
                            ))
                        },
                        aoFechar: { destino = nil },
                    )
                }
            case let .canal(quadro):
                TelaDoCanal(odeon: odeon, quadro: quadro) {
                    destino = nil
                    aba = .aoVivo
                }
            case let .doDisco(ficha):
                TelaDoPlayer(
                    odeon: odeon, obra: ficha.obraId, arquivo: ficha.arquivoId,
                    local: Baixados.arquivoNoDisco(ficha),
                    duracao: ficha.duracaoEmSegundos, titulo: ficha.titulo, comecarEm: 0,
                    /// ⚠️ Sair volta pra **casa**, e não pra a ficha: veio-se dos
                    /// baixados, e a ficha precisaria de rede pra abrir.
                    aoVoltar: { destino = nil; baixadosAbertos = true },
                )
            case let .tocando(alvo):
                TelaDoPlayer(
                    odeon: odeon, obra: alvo.obra, arquivo: alvo.arquivo,
                    duracao: alvo.duracao, titulo: alvo.titulo, comecarEm: alvo.comecarEm,
                    /// ⚠️ Canal **não registra**. Ver `ModeloDoPlayer.doAoVivo`: o
                    /// mesmo campo que já dizia pra onde voltar agora diz o que
                    /// não gravar.
                    doAoVivo: alvo.doAoVivo,
                    /// ⚠️ Sair do filme volta pra **ficha**, não pra a grade: é de
                    /// lá que se veio, e é lá que estão as outras coisas pra fazer
                    /// com esta obra.
                    /// ⚠️ Sair volta pra **onde se entrou**: pra a ficha quando se
                    /// veio dela, e pra a lista de canais quando se sintonizou.
                    aoVoltar: {
                        if alvo.doAoVivo {
                            destino = nil
                            aba = .aoVivo
                        } else {
                            destino = .ficha(alvo.obra)
                        }
                    },
                )
            }
        }
    }
}


