import SwiftUI

/// A ficha da obra.
///
/// ## ⚠️ «Arquivo» aqui não é «versão» — e confundir os dois funde o acervo
///
/// | | o que é | quem escolhe |
/// |---|---|---|
/// | **versão** | outra **obra** (id, progresso e ficha próprios) que o servidor agrupou | a grade, antes de chegar aqui |
/// | **arquivo** | um dos `files` **desta** obra — outra dublagem, outra qualidade | esta tela |
///
/// Uma obra com dois arquivos é comum; um filme com duas obras são os 43 grupos.
/// A escolha de versão acontece **antes** da ficha; a de arquivo, dentro dela.
@Observable
@MainActor
final class ModeloDaObra {
    var obra: ObraDetalhada?
    var arquivo: ArquivoDeMidia?
    var plano: PlanoDeReproducao?
    /// ⚠️ Separadas da ficha de propósito: doze extrações de ffmpeg custam ~4 s no
    /// servidor de casa, e a ficha não pode ficar parada esperando o varal.
    var cenas: [Cena] = []
    var recado: String?

    private let odeon: RepositorioOdeon
    private let obraId: String

    init(odeon: RepositorioOdeon, obraId: String) {
        self.odeon = odeon
        self.obraId = obraId
    }

    /// Só há o que tocar se houver arquivo. Obra identificada **sem** arquivo
    /// existe no acervo — é linha de catálogo sem mídia — e não toca (§53).
    var temComoTocar: Bool { arquivo != nil }

    /// De onde o botão vai começar, pela régua de sempre.
    var comecarEm: Double {
        guard let obra else { return 0 }
        return ondeContinuar(
            ondeParou: obra.ondeParou,
            duracaoEmSegundos: obra.duracaoEmSegundos,
            finished: obra.finished,
        )
    }

    func carregar() async {
        do {
            let o = try await odeon.obra(obraId)
            obra = o
            /// ⚠️ O **primeiro** arquivo é o de partida, e não há critério melhor:
            /// a ordem é a do servidor. Inventar uma preferência («o maior», «o de
            /// mais altura») seria o app escolhendo dublagem por tamanho.
            arquivo = o.files.first
            await odeon.conferirTokenDeMidia(comArte: o.artwork["poster"])
            if let a = arquivo { await pedirPlano(a) }
            cenas = (try? await odeon.cenas(obra: obraId)) ?? []
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "não deu pra abrir a ficha"
        }
    }

    func escolher(_ novo: ArquivoDeMidia) async {
        guard arquivo?.id != novo.id else { return }
        arquivo = novo
        plano = nil
        await pedirPlano(novo)
    }

    /// O plano é pedido **antes** de tocar, e é decisão: o selo responde «vai
    /// transcodificar?» antes de a pessoa gastar o toque. Num servidor de casa que
    /// atende três pessoas, saber disso antes vale.
    ///
    /// ⚠️ Falhar aqui **não** derruba a ficha: sem plano o selo some (§24) e o
    /// play continua de pé — quem decide de verdade como tocar é o player.


    private func pedirPlano(_ a: ArquivoDeMidia) async {
        let p = try? await odeon.plano(arquivo: a.id)
        /// A corrida: se alguém trocou de arquivo enquanto este plano voava, ele
        /// já não é o plano do arquivo escolhido. Descartar é o certo — mostrar o
        /// selo do outro seria mentir com cara de metadado (§18).
        if arquivo?.id == a.id { plano = p }
    }
}

struct TelaDaObra: View {
    let odeon: RepositorioOdeon
    /// A prateleira do aparelho. ⚠️ Ela vem **de fora** e é a mesma instância do
    /// app inteiro: dois `Baixados` seriam duas sessões de fundo com o mesmo
    /// identificador, e o `URLSession` recusa a segunda.
    let baixados: Baixados
    let obraId: String
    let aoTocar: (String, Double, Double?, String) -> Void
    let aoVoltar: () -> Void

    @State private var modelo: ModeloDaObra

    init(
        odeon: RepositorioOdeon,
        baixados: Baixados,
        obraId: String,
        aoTocar: @escaping (String, Double, Double?, String) -> Void,
        aoVoltar: @escaping () -> Void,
    ) {
        self.odeon = odeon
        self.obraId = obraId
        self.baixados = baixados
        self.aoTocar = aoTocar
        self.aoVoltar = aoVoltar
        _modelo = State(wrappedValue: ModeloDaObra(odeon: odeon, obraId: obraId))
    }

    var body: some View {
        ZStack(alignment: .top) {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    /// ⚠️ O «voltar» **rola junto**, e não fica fixo no topo. Fixo
                    /// ele atravessava o título da marquise assim que a pessoa
                    /// rolava — texto dourado sobre texto branco, visto na tela. A
                    /// ficha não é longa o bastante pra justificar uma barra que
                    /// não sai; e sobre o fotograma ele continua legível porque a
                    /// fachada é escura em cima.
                    fachada
                        .overlay(alignment: .topLeading) {
                            /// ## ⚠️ Ele precisa sobreviver a **qualquer** fotograma
                            ///
                            /// Visto na tela: o fundo de «O Lagosta» é quase
                            /// branco, e o dourado sobre branco ficou no limite do
                            /// ilegível. Sombra ajuda e não basta — é a mesma
                            /// família do contraste de **1,02:1** que este projeto
                            /// já mediu com legenda branca sobre cena clara.
                            ///
                            /// A cápsula escura resolve porque **não depende da
                            /// imagem**: ela leva o próprio fundo. Escurecer a
                            /// fachada inteira seria pagar o preço na foto toda pra
                            /// consertar um canto.
                            Button("‹ biblioteca", action: aoVoltar)
                                .font(.system(size: 16))
                                .foregroundStyle(Cores.destaque)
                                .padding(.horizontal, 14)
                                .frame(height: 36)
                                .background(.black.opacity(0.55), in: .capsule)
                                .contentShape(.capsule)
                                .padding(.horizontal, 14)
                                .padding(.top, 8)
                        }
                    /// ## ⚠️ A coluna de leitura é **só daqui pra baixo**
                    ///
                    /// A fachada e o resto da ficha querem larguras opostas, e no
                    /// iPhone isso não aparecia: 402pt é menos que os 620 da
                    /// coluna, então os dois iam até a borda e pareciam a mesma
                    /// coisa.
                    ///
                    /// Em paisagem no iPad (1376pt) o disfarce cai — a fachada
                    /// virava uma **ilha de 620 no meio da tela**, com preto dos
                    /// dois lados, que é o contrário de «a parede do cinema».
                    /// Sinopse quer medida; parede quer parede.
                    Group {
                        if let obra = modelo.obra {
                            VStack(alignment: .leading, spacing: 18) {
                                marquise(obra)
                                varal
                                if let overview = obra.overview, !overview.isEmpty {
                                    Text(overview)
                                        .font(.system(size: 16))
                                        .lineSpacing(4)
                                        .foregroundStyle(Cores.texto.opacity(0.9))
                                }
                                etiquetas(obra)
                                botoes(obra)
                                arquivos(obra)
                            }
                            .padding(.horizontal, 20)
                        } else if let recado = modelo.recado {
                            Text(recado).font(.system(size: 15))
                                .foregroundStyle(Cores.textoApagado).padding(.horizontal, 20)
                        } else {
                            Text("abrindo…").font(.system(size: 15))
                                .foregroundStyle(Cores.textoApagado).padding(.horizontal, 20)
                        }
                    }
                    .frame(maxWidth: 620, alignment: .leading)
                    .frame(maxWidth: .infinity)
                }
                .padding(.bottom, 34)
            }

        }
        .task { await modelo.carregar() }
    }

    /// A fachada: o fotograma de fundo, de ponta a ponta.
    ///
    /// ⚠️ **Sem cantos e sem margem.** É a parede do cinema, e não uma imagem de
    /// capa dentro de um cartão — a mesma decisão da capa do perfil.
    @ViewBuilder
    private var fachada: some View {
        if let caminho = modelo.obra?.artwork["backdrop"] {
            /// ## ⚠️ A arte vai em `background` — **de novo**
            ///
            /// A primeira montagem era `ZStack { imagem; véu }` com
            /// `.frame(height:).frame(maxWidth: .infinity).clipped()`, e a tela
            /// mostrou a ficha **vazando pelos dois lados**: título cortado,
            /// sinopse cortada, etiquetas saindo pela direita.
            ///
            /// A causa é a mesma do cartão dos baixados, que já tinha me pegado
            /// uma vez: `scaledToFill` numa imagem carregada reivindica a largura
            /// **do arquivo** — 1280pt —, o `ZStack` cresce junto, e
            /// `maxWidth: .infinity` **não limita**, só estica. A coluna inteira
            /// adotou 1280 e a tela recortou o meio.
            ///
            /// `background` não deixa isso acontecer por definição: o que está
            /// atrás nunca decide o tamanho do que está na frente. ⚠️ Eu sabia
            /// disso — está escrito no `TelaDosBaixados` — e reescrevi o defeito
            /// noutra tela no mesmo dia.
            Color.clear
                .frame(height: 210)
                .frame(maxWidth: .infinity)
                .background {
                    ZStack(alignment: .bottom) {
                        Cores.fundoElevado
                        ArteDoOdeon(odeon: odeon, caminho: caminho)
                        LinearGradient(colors: [.clear, Cores.fundo],
                                       startPoint: .center, endPoint: .bottom)
                    }
                }
                .clipped()
        }
    }

    /// A marquise: o nome do filme na fachada, sob as lâmpadas.
    private func marquise(_ obra: ObraDetalhada) -> some View {
        Marquise {
            VStack(spacing: 10) {
                Text(obra.title)
                    .font(Tipo.letreiro(28))
                    .foregroundStyle(Cores.texto)
                    .multilineTextAlignment(.center)

                /// ⚠️ `Thunderball · 1965 · 2h10` — o **título original** entra
                /// aqui, e some só quando não existe (§24). Numa marquise ele é o
                /// que diz de onde o filme veio.
                let partes = [
                    obra.tituloOriginal,
                    obra.year.map(String.init),
                    obra.duracaoEmSegundos.map(duracaoCompacta),
                ].compactMap { $0 }
                if !partes.isEmpty {
                    Text(partes.joined(separator: " · "))
                        .font(Tipo.rotulo(12))
                        .tracking(2.2)
                        .foregroundStyle(Cores.textoApagado)
                        .multilineTextAlignment(.center)
                }

                /// ⚠️ O selo do plano vem com o **motivo**, e não sozinho. «direto»
                /// é uma palavra; «codecs e container batem: vai o arquivo
                /// original» é a explicação de por que este filme abre num toque e
                /// o outro faz o servidor trabalhar. O motivo chega em `reasons` e
                /// eu não o lia.
                if let selo = seloDoPlano {
                    VStack(alignment: .leading, spacing: 3) {
                        Text(selo.texto)
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(selo.cor)
                        if let motivo = modelo.plano?.reasons.first, !motivo.isEmpty {
                            Text(motivo)
                                .font(.system(size: 13))
                                .foregroundStyle(Cores.textoApagado)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 14).padding(.vertical, 10)
                    .background(Cores.fundo.opacity(0.55), in: .rect(cornerRadius: 6))
                    .padding(.top, 2)
                }
            }
        }
    }

    /// ⚠️ O varal **sobe** pra encostar na marquise: o `-18` cancela o respiro da
    /// coluna. Com folga, a corda começa no nada.
    @ViewBuilder
    private var varal: some View {
        if !modelo.cenas.isEmpty {
            VaralDeCenas(
                cenas: modelo.cenas,
                odeon: odeon,
                /// ⚠️ **O varal é navegação**: cada foto é um instante, e tocar
                /// nela entra ali. Só quando há como tocar — foto de uma obra sem
                /// arquivo levaria a lugar nenhum (§53).
                aoEscolher: { cena in
                    if let arquivo = modelo.arquivo { aoTocar(arquivo.id, cena.segundos, modelo.obra?.duracaoEmSegundos, modelo.obra?.title ?? "") }
                },
            )
            .padding(.top, -18)
        }
    }

    @ViewBuilder
    private func etiquetas(_ obra: ObraDetalhada) -> some View {
        if !obra.tags.isEmpty {
            /// Quebram em linhas, como no Android — uma fileira que rola
            /// esconderia metade dos gêneros atrás de um gesto.
            FluxoDeEtiquetas(etiquetas: obra.tags)
        }
    }

    private func cabecalho(_ obra: ObraDetalhada) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(obra.title)
                .font(Tipo.letreiro(30))
                .foregroundStyle(Cores.texto)
                .padding(.top, 34)

            /// A linha de metadados omite **item por item** (§24), e o selo do
            /// plano entra nela — ele é dado, não enfeite.
            HStack(spacing: 10) {
                let partes = [
                    obra.year.map(String.init),
                    obra.duracaoEmSegundos.map(duracaoCompacta),
                ].compactMap { $0 }
                if !partes.isEmpty {
                    Text(partes.joined(separator: " · "))
                        .font(.system(size: 14))
                        .foregroundStyle(Cores.textoApagado)
                }
                if let selo = seloDoPlano {
                    Text(selo.texto)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(selo.cor)
                        .padding(.horizontal, 8).padding(.vertical, 3)
                        .overlay(Capsule().stroke(selo.cor.opacity(0.5), lineWidth: 1))
                }
            }
        }
    }

    /// ⚠️ O selo diz o que **este** aparelho vai receber, e por isso ele é
    /// diferente no iPhone: onde o Android toca direto, o iOS costuma remuxar —
    /// 53,3% do acervo contra 29,4% de direto. Não é enfeite: é a resposta de «vai
    /// esquentar a máquina de casa?».
    private var seloDoPlano: (texto: String, cor: Color)? {
        guard let modo = modelo.plano?.mode else { return nil }
        return switch modo {
        case "direct_play": ("direto", .green)
        case "direct_stream": ("remux", Cores.destaque)
        case "transcode": ("transcodificando", .orange)
        default: (modo, Cores.textoApagado)
        }
    }

    private func botoes(_ obra: ObraDetalhada) -> some View {
        VStack(spacing: 12) {
            if modelo.temComoTocar, let arquivo = modelo.arquivo {
                /// ## ⚠️ O botão virou **bilhete**, e o rótulo diz de onde começa
                ///
                /// Isto já errou duas vezes na tela, e a segunda foi conserto meu
                /// criando defeito novo:
                ///
                /// | | mostrava | por quê era errado |
                /// |---|---|---|
                /// | 1ª | «continuar de **0min**» | o `duracaoCompacta` não tem segundos; tudo entre 6 e 59 s virava zero |
                /// | 2ª | «continuar do começo» **e** «do começo» | dois botões dizendo a mesma coisa |
                ///
                /// Abaixo de um minuto, retomar **é** começar: o rótulo é «assistir»
                /// e o segundo botão nem aparece. A régua de retomada não mudou — o
                /// piso continua 5 s, porque foi decisão do dono que um teco conta.
                BilheteDaSessao(
                    rotulo: modelo.comecarEm < 60 ? "sessão" : "sessão · de onde parou",
                    frase: modelo.comecarEm < 60 ? "assistir"
                        : "continuar · \(duracaoCompacta(modelo.comecarEm))",
                    aoTocar: { aoTocar(arquivo.id, modelo.comecarEm, obra.duracaoEmSegundos, obra.title) },
                )

                HStack(spacing: 10) {
                    /// Só aparece quando há retomada **de verdade**.
                    if modelo.comecarEm >= 60 {
                        Button { aoTocar(arquivo.id, 0, obra.duracaoEmSegundos, obra.title) } label: {
                            rotuloSecundario("do começo", icone: "backward.end")
                        }
                    }
                    guardar(obra)
                }
                .buttonStyle(.plain)
            } else {
                /// §53: o produto não oferece o que a validação vai negar. Obra
                /// sem arquivo não toca, e o botão não existe.
                Text("sem arquivo no acervo")
                    .font(.system(size: 14))
                    .foregroundStyle(Cores.textoApagado)
            }
        }
    }

    private func rotuloSecundario(_ texto: String, icone: String) -> some View {
        HStack(spacing: 8) {
            Image(systemName: icone).font(.system(size: 14))
            Text(texto).font(.system(size: 15))
        }
        .foregroundStyle(Cores.destaque)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 14)
        /// ⚠️ Borda **tracejada**, como no Android: as ações secundárias da ficha
        /// são o picotado do bilhete — o mesmo material, meia força.
        .overlay(
            RoundedRectangle(cornerRadius: 8)
                .stroke(style: .init(lineWidth: 1, dash: [4, 3]))
                .foregroundStyle(Cores.destaque.opacity(0.55)),
        )
    }

    /// «guardar no aparelho».
    ///
    /// ## ⚠️ Ele só existe em `direct_play`, e a regra é do iOS
    ///
    /// No Android baixa-se qualquer coisa, porque o ExoPlayer abre matroska — e
    /// **55% do acervo é matroska**. O AVFoundation não abre. Oferecer o botão em
    /// tudo faria a pessoa gastar 2 GB do próprio disco num arquivo que não vai
    /// abrir: o §53 na versão mais cara, porque o custo não é um erro na tela, é
    /// o armazenamento dela.
    ///
    /// ⚠️ E a pergunta **já foi feita**: o plano é o mesmo que a ficha carregou
    /// pro selo «direto / remux / transcodificando» logo acima. Nenhuma requisição
    /// a mais — o dado que decide o botão já estava na tela, sem ninguém o ler.
    ///
    /// ⚠️ O botão **some** onde não serve, em vez de aparecer desabilitado com um
    /// aviso. Um «guardar» cinza em metade do acervo transformaria a regra numa
    /// reclamação repetida; a tela dos baixados explica a regra uma vez, no vazio,
    /// que é onde alguém a procura.
    @ViewBuilder
    private func guardar(_ obra: ObraDetalhada) -> some View {
        if let arquivo = modelo.arquivo, modelo.plano?.eDireto == true {
            let jaTem = baixados.itens.first { $0.ficha.arquivoId == arquivo.id }
            Button {
                Task {
                    do {
                        try await baixados.baixar(ficha: FichaDoBaixado(
                            obraId: obra.id,
                            arquivoId: arquivo.id,
                            titulo: obra.title,
                            poster: obra.artwork["poster"],
                            /// ⚠️ Gravado **agora**, junto dos bytes: o cartão dos
                            /// baixados precisa se desenhar sem rede, e um caminho
                            /// de arte que só existisse no catálogo faria a tela
                            /// offline mostrar retângulo cinza.
                            backdrop: obra.artwork["backdrop"],
                            duracaoEmSegundos: obra.duracaoEmSegundos,
                            ano: obra.year,
                            /// ⚠️ Do **`filename` do servidor**, e não do que eu
                            /// acho que o arquivo é. Ver `FichaDoBaixado.extensao`:
                            /// sem a extensão certa o filme baixa inteiro e não
                            /// abre.
                            extensao: FichaDoBaixado.extensaoDe(arquivo.filename),
                        ))
                    } catch {
                        modelo.recado = (error as? FalhaDoOdeon)?.errorDescription
                            ?? "não deu pra guardar"
                    }
                }
            } label: {
                rotuloSecundario(
                    jaTem == nil ? "baixar pra ver sem rede"
                        : (jaTem?.pronto == true ? "guardado" : "guardando…"),
                    icone: "arrow.down",
                )
                .opacity(jaTem == nil ? 1 : 0.55)
            }
            .disabled(jaTem != nil)
        }
    }

    /// Os arquivos **desta** obra. Só aparece quando há mais de um: uma lista com
    /// um item é uma escolha que não existe (§24).
    @ViewBuilder
    private func arquivos(_ obra: ObraDetalhada) -> some View {
        if obra.files.count > 1 {
            VStack(alignment: .leading, spacing: 8) {
                Text("ARQUIVOS")
                    .font(Tipo.rotulo(11))
                    .tracking(Tipo.espacoDoRotulo)
                    .foregroundStyle(Cores.textoApagado)
                    .padding(.top, 8)

                ForEach(obra.files) { a in
                    Button { Task { await modelo.escolher(a) } } label: {
                        HStack {
                            Text(a.filename)
                                .font(.system(size: 13))
                                .lineLimit(1)
                                .foregroundStyle(modelo.arquivo?.id == a.id ? Cores.texto : Cores.textoApagado)
                            Spacer()
                            let tec = [
                                a.height.map { "\($0)p" },
                                a.codecDeAudio,
                                a.container,
                            ].compactMap { $0 }.joined(separator: " · ")
                            Text(tec).font(.system(size: 12)).foregroundStyle(Cores.textoApagado)
                        }
                        .padding(12)
                        .background(
                            modelo.arquivo?.id == a.id ? Cores.fundoElevado : .clear,
                            in: .rect(cornerRadius: 9),
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }
}



/// As etiquetas quebrando em linhas.
///
/// ⚠️ Elas **não rolam na horizontal**: um filme tem seis ou sete etiquetas, e
/// escondê-las atrás de um gesto faria a metade dos gêneros existir só pra quem
/// desconfia que há mais. Quebrar em linha mostra tudo de uma vez, que é o que
/// uma lista curta pede.
private struct FluxoDeEtiquetas: View {
    let etiquetas: [EtiquetaDaObra]

    var body: some View {
        FlowLayout(espaco: 8) {
            ForEach(etiquetas) { PilulaDeEtiqueta(etiqueta: $0) }
        }
    }
}

/// Um `Layout` que enche a linha e quebra.
private struct FlowLayout: Layout {
    let espaco: CGFloat

    func sizeThatFits(proposal: ProposedViewSize, subviews: Subviews, cache _: inout ()) -> CGSize {
        /// ## ⚠️ **Nunca devolver largura infinita** — e foi o que a tela mostrou
        ///
        /// A primeira versão fazia `proposal.width ?? .infinity`. Quando o SwiftUI
        /// mede sem propor largura — e ele mede —, isto respondia «sou infinita», a
        /// coluna inteira adotava essa largura, e a ficha **vazou pelos dois
        /// lados**: título cortado, sinopse cortada, etiquetas saindo pela direita.
        ///
        /// Um `Layout` que responde infinito não está dizendo «me dê o que puder»;
        /// está dizendo «eu ocupo tudo», e quem pergunta acredita. Sem proposta, a
        /// resposta honesta é a **linha única**: é o tamanho que ele teria se
        /// coubesse, e quem propõe decide depois.
        guard let largura = proposal.width, largura.isFinite else {
            let tamanhos = subviews.map { $0.sizeThatFits(.unspecified) }
            return CGSize(
                width: tamanhos.reduce(0) { $0 + $1.width + espaco },
                height: tamanhos.map(\.height).max() ?? 0,
            )
        }
        var x: CGFloat = 0, y: CGFloat = 0, alturaDaLinha: CGFloat = 0
        for sub in subviews {
            let t = sub.sizeThatFits(.unspecified)
            if x + t.width > largura, x > 0 {
                x = 0; y += alturaDaLinha + espaco; alturaDaLinha = 0
            }
            x += t.width + espaco
            alturaDaLinha = max(alturaDaLinha, t.height)
        }
        return CGSize(width: largura, height: y + alturaDaLinha)
    }

    func placeSubviews(in bounds: CGRect, proposal: ProposedViewSize,
                       subviews: Subviews, cache _: inout ()) {
        let largura = proposal.width ?? bounds.width
        var x = bounds.minX, y = bounds.minY, alturaDaLinha: CGFloat = 0
        for sub in subviews {
            let t = sub.sizeThatFits(.unspecified)
            if x + t.width > bounds.minX + largura, x > bounds.minX {
                x = bounds.minX; y += alturaDaLinha + espaco; alturaDaLinha = 0
            }
            sub.place(at: CGPoint(x: x, y: y), proposal: ProposedViewSize(t))
            x += t.width + espaco
            alturaDaLinha = max(alturaDaLinha, t.height)
        }
    }
}
