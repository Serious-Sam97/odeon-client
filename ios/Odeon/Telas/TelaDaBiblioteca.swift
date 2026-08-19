import SwiftUI

/// A biblioteca — a grade do acervo.
///
/// ## ⚠️ Ela lista **8.273 entradas**, e esse número manda no desenho
///
/// A rota é `/api/library`, que **agrupa**: uma série é uma linha, não 62. Sem
/// isso os 14.657 episódios do acervo virariam 14.657 cartões iguais — «é
/// listagem de arquivo e não biblioteca».
///
/// E desde 14/08/2026 ela agrupa mais uma coisa: os rips do mesmo filme. São 43
/// grupos, e é por isso que o total é 8.273 e não 8.316.
@Observable
@MainActor
final class ModeloDaBiblioteca {
    var itens: [ItemDaBiblioteca] = []
    var total: Int?
    var carregando = false
    var recado: String?

    /// O que está escrito na busca.
    var busca = "" {
        didSet { if busca != oldValue { depoisDaEspera() } }
    }

    /// O filtro que veio do guia.
    ///
    /// ⚠️ **Sem espera**, ao contrário da busca: ele não vem de teclado, vem de um
    /// toque só. Os 250 ms existem pra não mandar onze consultas enquanto alguém
    /// digita «goldfinger»; aqui há exatamente uma, e adiá-la faria a grade ficar
    /// um quarto de segundo mostrando o acervo inteiro depois de a pessoa já ter
    /// escolhido «Terror».
    /// O catálogo do painel. Vazio até alguém abrir os filtros — ver
    /// `RepositorioOdeon.etiquetasDoAcervo`.
    var etiquetasDoAcervo: [EtiquetaDoAcervo] = []
    var espacosDeEtiqueta: [EspacoDeEtiqueta] = []

    /// ## As prateleiras — 18/08/2026
    ///
    /// Medido no acervo: **120 séries** contra 8.333 entradas. Uma série é 1
    /// cartão em 69 — elas não estavam misturadas, estavam afogadas.
    ///
    /// ⚠️ Vêm do espaço `format` que o **servidor** declara, e não de uma lista
    /// escrita aqui: se ele acrescentar um formato amanhã, ele aparece.
    var prateleiras: [EtiquetaDoAcervo] = []

    /// Quantas **entradas** cada prateleira tem — agrupadas, como a grade mostra.
    ///
    /// ⚠️ Não é a `quantasObras` da etiqueta: ela conta **8.475 obras** pra
    /// `format:série` e a grade mostra **120 entradas**. Pôr 8.475 ao lado de uma
    /// grade de 120 é o mesmo erro do `18 de 1`. Cada número é **perguntado**,
    /// com uma consulta de uma linha.
    var quantasPorPrateleira: [String: Int] = [:]

    private static let espacoDoFormato = "format"

    /// As prateleiras e quantas entradas cada uma tem.
    func carregarPrateleiras() async {
        guard prateleiras.isEmpty else { return }
        let (etiquetas, espacos) = await odeon.etiquetasDoAcervo()
        etiquetasDoAcervo = etiquetas
        espacosDeEtiqueta = espacos
        let formatos = etiquetas
            .filter { $0.namespace == Self.espacoDoFormato && $0.quantasObras > 0 }
            .sorted { $0.quantasObras > $1.quantasObras }
        guard !formatos.isEmpty else { return }
        prateleiras = formatos

        for formato in formatos {
            var filtro = filtros
            filtro.prateleira = formato.chave
            let quantos = (try? await odeon.biblioteca(limite: 1, filtros: filtro))?.first?.total ?? 0
            quantasPorPrateleira[formato.chave] = quantos
        }
    }

    /// A biblioteca **sem** as séries — a aba dos filmes.
    ///
    /// ⚠️ Fixar `format:filme` daria 981 e esconderia as 2.182 entradas que o
    /// scanner não classifica. Tirar as séries dá **3.187**.
    ///
    /// ⚠️ O anime entra na exclusão: `tags_not=format:série` sozinho deixa passar
    /// o `Beyblade` — 43 episódios que carregam `format:anime`.
    func semSéries() {
        let fora = prateleiras
            .filter { $0.value.hasPrefix("série") || $0.value.hasPrefix("anime") }
            .map(\.chave)
        guard !fora.isEmpty else { return }
        filtros.excluindo = fora
    }

    /// Trocar de prateleira. `nil` é «tudo».
    func escolherPrateleira(_ chave: String?) {
        guard filtros.prateleira != chave else { return }
        filtros.prateleira = chave
    }

    /// `nas séries`, `nos filmes`, `na biblioteca` — o que o campo de busca diz.
    ///
    /// ⚠️ A busca **sempre** respeitou a prateleira (as duas viajam no mesmo
    /// pedido); o que faltava era dizer isso. Quem digita e recebe um resultado
    /// precisa saber se procurou em tudo ou só numa prateleira.
    var ondeSeProcura: String {
        guard let formato = prateleiras.first(where: { $0.chave == filtros.prateleira })
        else { return "na biblioteca" }
        return formato.value == "série" ? "nas séries" : "nos \(formato.value)s"
    }

    /// Carrega o catálogo **uma vez**, na primeira abertura do painel.
    func garantirCatalogo() async {
        guard etiquetasDoAcervo.isEmpty else { return }
        let (etiquetas, espacos) = await odeon.etiquetasDoAcervo()
        etiquetasDoAcervo = etiquetas
        espacosDeEtiqueta = espacos
    }

    var filtros = Filtros() {
        didSet { if filtros != oldValue { Task { await recomecar() } } }
    }
    private var trabalhoDaBusca: Task<Void, Never>?

    private let odeon: RepositorioOdeon

    init(odeon: RepositorioOdeon) { self.odeon = odeon; odeonParaArte = odeon }

    /// Ainda há o que buscar? `total` nulo quer dizer «nada chegou ainda» — e a
    /// tela **não escreve 0** nesse caso: zero é uma afirmação, e o app ainda não
    /// sabe disso.
    var temMais: Bool { total.map { itens.count < $0 } ?? true }

    /// Os filmes começados. ⚠️ Eles moram **aqui**, e não numa aba: ver o
    /// comentário no lugar onde a `TelaDeContinuar` existia.
    var comecados: [ItemPraContinuar] = []

    /// O herói do topo: o primeiro da fila, e só quando dá pra tocar.
    ///
    /// ⚠️ Obra sem arquivo existe no acervo — é linha de catálogo sem mídia — e
    /// um herói que não abre seria o §53 no lugar mais visível da tela.
    var heroi: ItemPraContinuar? {
        comecados.first { ($0.arquivoId?.isEmpty == false) }
    }

    /// A fileira, **sem o herói**: ele já está desenhado acima, e repeti-lo faria
    /// a tela abrir com o mesmo filme duas vezes.
    var fileiraDeContinuar: [ItemPraContinuar] {
        guard let heroi else { return comecados }
        return comecados.filter { $0.id != heroi.id }
    }

    func primeiraPagina() async {
        guard itens.isEmpty else { return }
        /// ⚠️ O token de mídia **antes** da primeira página: sem ele o
        /// `urlDaArte` devolve nulo e a grade desenha inteira sem capa nenhuma.
        _ = try? await odeon.garantirTokenDeMidia()
        async let fila = try? await odeon.paraContinuar()
        async let pagina: Void = maisUma()
        comecados = await fila ?? []
        _ = await pagina
    }

    /// ## ⚠️ Os 250 ms, e o que eles evitam
    ///
    /// O mesmo número da web e do Android, e o motivo aqui é o mesmo: o servidor
    /// de casa atende três pessoas e ainda roda o Postgres, o ffmpeg e a
    /// identificação. Digitar «goldfinger» sem espera são **onze** consultas sobre
    /// 17.930 obras pra mostrar o resultado de uma.
    ///
    /// ⚠️ E a grade **não** é apagada enquanto a resposta não chega. Trocar tudo
    /// por um rodinho a cada letra faria a tela piscar onze vezes na mesma palavra
    /// — e o que está na tela continua sendo verdade sobre a busca anterior até a
    /// próxima chegar.
    private func depoisDaEspera() {
        trabalhoDaBusca?.cancel()
        trabalhoDaBusca = Task {
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            await recomecar()
        }
    }

    private func recomecar() async {
        itens = []
        total = nil
        await maisUma()
    }

    func maisUma() async {
        guard !carregando, temMais else { return }
        carregando = true
        defer { carregando = false }
        do {
            let pagina = try await odeon.biblioteca(
                pulando: itens.count, limite: 60,
                /// ⚠️ A busca vai junto **em toda página**, e não só na primeira:
                /// sem ela, rolar até o fim de «bond» pediria a página 2 do acervo
                /// inteiro e emendaria 60 filmes quaisquer embaixo do resultado.
                busca: busca.isEmpty ? nil : busca,
                filtros: filtros,
            )
            /// Ver `conferirTokenDeMidia`: a arte é o único lugar sem sinal de
            /// falha, e a grade é a primeira tela que a pede.
            await odeon.conferirTokenDeMidia(comArte: pagina.first?.poster)
            itens += pagina
            /// O total vem repetido em toda linha (um `count(*) OVER ()` do
            /// servidor), então a primeira linha da página já o traz.
            if let t = pagina.first?.total { total = t }
            else if pagina.isEmpty { total = itens.count }
            recado = nil
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "não deu pra carregar"
        }
    }

    /// ⚠️ O **caminho**, e não a URL: ver `ArteDoOdeon`. A URL carrega o token,
    /// e o token morre — guardar o caminho é o que permite remontar depois.
    let odeonParaArte: RepositorioOdeon

    /// ⚠️ `backdrop` primeiro no herói e na fileira, e `poster` na grade. O herói
    /// é largo; um pôster 2:3 esticado ali deixa duas tarjas pretas.
    func arteDeitada(_ item: ItemPraContinuar) -> String? {
        item.backdrop ?? item.poster
    }
}

struct TelaDaBiblioteca: View {
    let odeon: RepositorioOdeon
    let insignia: Insignia
    let baixados: Baixados
    /// O filtro que o guia ligou. ⚠️ Ele é **de fora**: quem escolheu «Terror»
    /// estava noutra tela, e a grade é só quem obedece. Por isso `@Binding` e não
    /// estado próprio — dois donos do mesmo filtro é o defeito do `Destino` de
    /// novo, com outra roupa.
    @Binding var filtros: Filtros
    /// O painel está aberto? Vive na tela e não no modelo: é cromo, e fechar o
    /// app não deve guardar um painel aberto.
    @State private var painelAberto = false
    let aoAbrirPerfil: () -> Void
    let aoAbrirBaixados: () -> Void
    let aoSair: () -> Void
    let aoEscolher: (ItemDaBiblioteca) -> Void
    let aoContinuar: (ItemPraContinuar) -> Void

    @State private var modelo: ModeloDaBiblioteca

    init(
        odeon: RepositorioOdeon,
        insignia: Insignia,
        baixados: Baixados,
        filtros: Binding<Filtros>,
        aoAbrirPerfil: @escaping () -> Void,
        aoAbrirBaixados: @escaping () -> Void,
        aoSair: @escaping () -> Void,
        aoEscolher: @escaping (ItemDaBiblioteca) -> Void,
        aoContinuar: @escaping (ItemPraContinuar) -> Void,
    ) {
        self.odeon = odeon
        self.insignia = insignia
        self.baixados = baixados
        _filtros = filtros
        self.aoAbrirPerfil = aoAbrirPerfil
        self.aoAbrirBaixados = aoAbrirBaixados
        self.aoSair = aoSair
        self.aoEscolher = aoEscolher
        self.aoContinuar = aoContinuar
        _modelo = State(wrappedValue: ModeloDaBiblioteca(odeon: odeon))
    }

    @Environment(\.horizontalSizeClass) private var largura

    /// ## ⚠️ `.adaptive` **não bastou**, e o iPad provou
    ///
    /// A escolha original era `.adaptive(minimum: 108, maximum: 150)` com o
    /// comentário certo pelo motivo certo: «um cartaz tem largura mínima legível;
    /// quantos cabem é conta da tela, não minha».
    ///
    /// Só que `.adaptive` responde **quantos cabem**, não **quão grandes devem
    /// ser**. Num iPhone de 402pt, 108–150 dá três colunas de cartaz legível. Num
    /// iPad de 1032pt dá **sete**, e sete cartazes numa tela de 13 polegadas são
    /// selos postais — visto na primeira captura do iPad. O mínimo virou o real,
    /// porque `.adaptive` sempre empacota o máximo de colunas que couber.
    ///
    /// ⚠️ É exatamente o defeito que a §7 do plano prevê citando o Android: **«o
    /// padrão de uma biblioteca não é a decisão do produto»**. O componente fez o
    /// que promete; o que faltava era alguém dizer qual é o cartaz certo pra cada
    /// tamanho de sala.
    ///
    /// ⚠️ E a régua é a **classe de tamanho**, não o modelo do aparelho: um iPad
    /// com o app em meia tela é `compact`, e ali o cartaz do iPhone é o certo. Quem
    /// decide é o espaço que se tem, não o metal.
    private var colunas: [GridItem] {
        largura == .regular
            ? [GridItem(.adaptive(minimum: 158, maximum: 210), spacing: 18)]
            : [GridItem(.adaptive(minimum: 108, maximum: 150), spacing: 12)]
    }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                LazyVGrid(columns: colunas, spacing: largura == .regular ? 26 : 18) {
                    Section {
                        ForEach(modelo.itens) { item in
                            Button { aoEscolher(item) } label: { Cartaz(odeon: odeon, item: item) }
                                .buttonStyle(.plain)
                                .task {
                                    /// A paginação pendura no **último** cartaz
                                    /// composto. Numa grade que rola rápido isso
                                    /// dispara uma vez por parada, não por item.
                                    if item.id == modelo.itens.last?.id { await modelo.maisUma() }
                                }
                        }
                    } header: {
                        cabecalho
                    }
                }
                .padding(.horizontal, largura == .regular ? 24 : 16)

                if modelo.carregando {
                    Text("carregando…")
                        .font(.system(size: 13))
                        .foregroundStyle(Cores.textoApagado)
                        .padding(20)
                }
            }
        }
        .task { await modelo.primeiraPagina() }
        /// ⚠️ **A biblioteca do iOS é a dos filmes.** Quem tira as séries é o
        /// servidor, com `?tags_not=format:série,format:anime` — o par, e não só
        /// a série: `format:anime` sozinho deixa passar o `Beyblade`, 43
        /// episódios. Ver `PEDIDOS-AO-SERVIDOR.md, «já entregue» 12`.
        .task {
            await modelo.carregarPrateleiras()
            modelo.semSéries()
        }
        .task(id: filtros) { modelo.filtros = filtros }
    }

    private var cabecalho: some View {
        VStack(alignment: .leading, spacing: 12) {
            CabecalhoDaTela(
                titulo: "biblioteca",
                /// ⚠️ Só escreve a contagem quando **sabe** o total. «12 de 0»
                /// seria pior que não escrever nada (§24).
                contagem: modelo.total.map { ("\(modelo.itens.count)", $0.comMilhar) },
                insignia: insignia,
                aoAbrirPerfil: aoAbrirPerfil,
                aoSair: aoSair,
            )
            .padding(.horizontal, -16)

            TextField("buscar nos filmes…", text: $modelo.busca)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.system(size: 15))
                .foregroundStyle(Cores.texto)
                .padding(.horizontal, 14).padding(.vertical, 11)
                .background(Cores.fundoElevado, in: .capsule)

            /// ⚠️ **A fileira de prateleiras saiu daqui** — 18/08/2026. Ela
            /// parecia o que não era: uma segunda barra de filtros. Séries virou
            /// aba própria; esta tela é a dos **filmes**. Ver `TelaDasSeries`.
            chips

            /// ⚠️ O painel fica **logo abaixo do chip que o abre**, e empurra a
            /// grade em vez de flutuar por cima: quem filtra quer ver o resultado
            /// mudando, e uma folha cobrindo a grade esconde justamente o que o
            /// toque acabou de fazer.
            if painelAberto {
                PainelDeFiltros(
                    etiquetas: modelo.etiquetasDoAcervo,
                    espacos: modelo.espacosDeEtiqueta,
                    filtros: $filtros,
                )
                .task { await modelo.garantirCatalogo() }
            }

            if let recado = modelo.recado {
                Text(recado).font(.system(size: 13)).foregroundStyle(Cores.textoApagado)
            }

            /// ⚠️ «Nada com esse termo» é diferente de «a biblioteca está vazia»,
            /// e a tela precisa dizer qual dos dois (§8b).
            if !modelo.busca.isEmpty, modelo.itens.isEmpty, !modelo.carregando {
                Text("nada com «\(modelo.busca)»")
                    .font(.system(size: 13))
                    .foregroundStyle(Cores.textoApagado)
            }

            /// ⚠️ O herói e a fileira **somem durante a busca**: quem digitou
            /// «goldfinger» está procurando um filme, e o que ele parou de assistir
            /// ontem é ruído em cima do resultado.
            if modelo.busca.isEmpty, !filtros.ligado {
                if let heroi = modelo.heroi { self.heroi(heroi) }
                fileiraDeContinuar
            }
        }
        .padding(.bottom, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Cores.fundo)
    }

    /// A barra de chips.
    ///
    /// ## Os três do Android, enfim — 17/08/2026
    ///
    /// Esta folha dizia, e com razão: «`filtros ▾` não entra porque o painel de
    /// filtros não existe neste app — um chip que abre nada é o §8b». A ausência
    /// foi honesta enquanto durou. Agora o painel existe (`PainelDeFiltros`), e o
    /// chip entrou com ele.
    ///
    /// ⚠️ O `Filtros` daqui continua sendo a fatia que o guia pede — etiqueta,
    /// década, tipo. O painel oferece **o que este modelo sabe carregar**, e não
    /// as doze faixas do Android: um chip que ligasse um campo que a busca ignora
    /// seria o §8b outra vez, com outra cara.
    private var chips: some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
                /// O filtro que veio do guia, com o ✕ que o desfaz — sem ele,
                /// tocar em «Terror» seria uma armadilha sem volta.
                if filtros.ligado {
                    chip(texto: "só \(filtros.rotulo)", ligado: true, marca: "✕") { filtros = Filtros() }
                }

                /// ⚠️ **O chip só abre o painel; ele não filtra.** A marca é `▾` e
                /// não `✕` por isso — quem desliga um filtro é o chip aceso à
                /// esquerda, e dar duas caras à mesma marca faria o `▾` parecer
                /// que desfaz alguma coisa.
                chip(texto: "filtros", ligado: painelAberto, marca: painelAberto ? "▴" : "▾") {
                    painelAberto.toggle()
                }

                Menu {
                    ForEach(Self.ordens, id: \.chave) { o in
                        Button(o.rotulo) { filtros.ordem = o.chave }
                    }
                } label: {
                    chipDesenho(texto: (Self.ordens.first { $0.chave == filtros.ordem }?.rotulo
                        ?? "em destaque"), ligado: false, marca: "▾")
                }

                /// ⚠️ **Some quando não há nada guardado.** «0 no aparelho» é uma
                /// afirmação sobre um estado que a tela dos baixados já explica
                /// melhor — e um chip que leva a uma lista vazia é convite a nada.
                if !baixados.itens.isEmpty {
                    chip(texto: "↓ \(baixados.itens.count) no aparelho",
                         ligado: true, marca: nil, aoTocar: aoAbrirBaixados)
                }
            }
        }
    }

    private static let ordens: [(chave: String?, rotulo: String)] = [
        (nil, "em destaque"), ("title", "título"), ("year", "ano"),
        ("added", "adicionados"), ("duration", "duração"), ("random", "aleatório"),
    ]

    private func chip(texto: String, ligado: Bool, marca: String?,
                      aoTocar: @escaping () -> Void) -> some View {
        Button(action: aoTocar) { chipDesenho(texto: texto, ligado: ligado, marca: marca) }
            .buttonStyle(.plain)
    }

    private func chipDesenho(texto: String, ligado: Bool, marca: String?) -> some View {
        HStack(spacing: 6) {
            Text(texto).font(.system(size: 14))
            if let marca { Text(marca).font(.system(size: 12, weight: .semibold)) }
        }
        .foregroundStyle(ligado ? Cores.fundo : Cores.textoApagado)
        .padding(.horizontal, 14).padding(.vertical, 9)
        .background {
            if ligado { Capsule().fill(Cores.destaque) }
            else { Capsule().stroke(Cores.textoApagado.opacity(0.4), lineWidth: 1) }
        }
        .contentShape(.capsule)
    }

    /// O herói: o filme que está esperando.
    ///
    /// ⚠️ Ele é a primeira coisa da tela porque é a primeira coisa que a
    /// biblioteca tem a dizer quando abre — «você parou aqui» vale mais que
    /// «existem 8.273 coisas».
    private func heroi(_ item: ItemPraContinuar) -> some View {
        Button { aoContinuar(item) } label: {
            ZStack(alignment: .bottomLeading) {
                Rectangle().fill(Cores.fundoElevado)
                ArteDoOdeon(odeon: odeon, caminho: modelo.arteDeitada(item))
                /// O véu: há texto por cima, e título branco sobre cena clara já
                /// foi medido neste projeto em 1,02:1 de contraste.
                LinearGradient(colors: [.clear, .black.opacity(0.85)],
                               startPoint: .center, endPoint: .bottom)

                VStack(alignment: .leading, spacing: 4) {
                    Text(item.tituloDaSerie ?? item.title)
                        .font(Tipo.letreiro(27))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    /// ⚠️ «faltam», e não «parou em»: o que decide se dá tempo hoje
                    /// à noite é o que sobra, não o que já foi.
                    if let falta = item.quantoFalta {
                        Text(falta).font(.system(size: 14)).foregroundStyle(Cores.destaque)
                    }
                }
                .padding(16)
            }
            /// ## ⚠️ Mais alto na tela grande, e não mais largo
            ///
            /// Em paisagem no iPad o herói vira **1376 × 210** — uma tarja de
            /// 6,5:1, que é proporção de banner de site, não de cartaz. Ele é a
            /// primeira coisa que a biblioteca diz ao abrir («você parou aqui»), e
            /// dizer isso numa frestinha é dizer baixinho.
            ///
            /// ⚠️ E cresce em **altura**, não encolhe em largura: ele acompanha a
            /// grade, que usa a tela toda. Um herói estreito no meio de uma grade
            /// larga seria um cartão solto, não o topo da tela.
            .frame(height: largura == .regular ? 320 : 210)
            .clipShape(.rect(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    @ViewBuilder
    private var fileiraDeContinuar: some View {
        let fila = modelo.fileiraDeContinuar
        if !fila.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                /// ⚠️ Aqui o `RotuloDeSecao` está no lugar dele: separando uma
                /// **seção dentro** da tela, não nomeando a tela.
                /// ⚠️ Conta **a fileira**, e não a fila inteira: o herói já está
                /// desenhado acima, e somá-lo aqui faria o número prometer um
                /// cartão a mais do que existe. O Android diz 28 onde a fila tem
                /// 29 — foi assim que a diferença apareceu.
                RotuloDeSecao(texto: "CONTINUAR", contagem: "\(fila.count)")

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 12) {
                        ForEach(fila) { item in
                            Button { aoContinuar(item) } label: { cartaoDeContinuar(item) }
                                .buttonStyle(.plain)
                                /// §53: só é tocável o que tem arquivo.
                                .disabled(item.arquivoId?.isEmpty != false)
                                .opacity(item.arquivoId?.isEmpty != false ? 0.5 : 1)
                        }
                    }
                }
            }
        }
    }

    private func cartaoDeContinuar(_ item: ItemPraContinuar) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack(alignment: .bottom) {
                Rectangle().fill(Cores.fundoElevado)
                ArteDoOdeon(odeon: odeon, caminho: modelo.arteDeitada(item))
                /// ⚠️ A barrinha vive na borda de baixo da arte e só existe quando
                /// dá pra calculá-la — barra zerada seria decoração com cara de
                /// dado (§18).
                if let fracao = item.fracaoVista {
                    GeometryReader { g in
                        ZStack(alignment: .leading) {
                            Rectangle().fill(.white.opacity(0.2))
                            Rectangle().fill(Cores.destaque).frame(width: g.size.width * fracao)
                        }
                    }
                    .frame(height: 3)
                }
            }
            .frame(width: 220, height: 124)
            .clipShape(.rect(cornerRadius: 8))

            Text(item.tituloDaSerie ?? item.title)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Cores.texto)
                .lineLimit(1)
            if let falta = item.quantoFalta {
                Text(falta).font(.system(size: 11)).foregroundStyle(Cores.textoApagado)
            }
        }
        .frame(width: 220, alignment: .leading)
    }
}

/// O cartaz de uma entrada.
private struct Cartaz: View {
    let odeon: RepositorioOdeon
    let item: ItemDaBiblioteca

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            ZStack {
                /// A cor da obra vem do servidor e é o fundo enquanto (ou se) não
                /// houver arte. **48% do acervo não tem pôster**, então isto não é
                /// caso de borda: é quase metade da grade.
                Rectangle().fill(corDaObra)

                if item.poster != nil {
                    ArteDoOdeon(odeon: odeon, caminho: item.poster)
                } else {
                    /// Sem arte, o título **é** o cartaz. Melhor que um retângulo
                    /// mudo com um ícone de imagem quebrada.
                    Text(item.title)
                        .font(Tipo.letreiro(13))
                        .foregroundStyle(Cores.texto.opacity(0.85))
                        .multilineTextAlignment(.center)
                        .padding(6)
                }

                /// ⚠️ A marca de «mais de uma versão» — os 43 grupos. Ela avisa
                /// **antes** do toque que vai haver uma escolha; sem isso a modal
                /// aparece do nada e parece defeito.
                if item.temEscolhaDeVersao {
                    VStack {
                        HStack {
                            Spacer()
                            Text("\(item.versoesEscolhiveis.count) versões")
                                .font(.system(size: 9, weight: .semibold))
                                .foregroundStyle(Cores.fundo)
                                .padding(.horizontal, 5).padding(.vertical, 3)
                                .background(Cores.destaque, in: .capsule)
                                .padding(5)
                        }
                        Spacer()
                    }
                }
            }
            .aspectRatio(2 / 3, contentMode: .fit)
            .clipShape(.rect(cornerRadius: 5))
            /// ## O cartaz é uma **caixa na prateleira**, não um retângulo
            ///
            /// O terceiro item do diagnóstico do redesenho (`REDESENHO.md` §1.3) é
            /// o **objeto**: «a web desenha coisas, não registros — a caixa de VHS
            /// tem lombada e fica de pé na prateleira». O app Android tinha
            /// «retângulos com cantos de 6dp», e o diagnóstico chama isso de o que
            /// separa um catálogo de uma locadora.
            ///
            /// São três traços, e cada um faz uma coisa:
            ///
            /// | | |
            /// |---|---|
            /// | a **lombada** | um filete claro na borda esquerda: é a espessura da caixa vista de frente |
            /// | o **peso** | sombra caída pra baixo, não em volta — objeto pousado, não flutuando |
            /// | o **vinco** | a borda escura fina que separa a arte do fundo, senão capa escura se dissolve no `#0A0A0C` |
            ///
            /// ⚠️ Nenhum deles é dado. É a régua do §18 pelo avesso: decoração não
            /// pode **parecer** dado — e por isso a lombada é luz, e não uma barra
            /// que possa ser confundida com progresso.
            .overlay(alignment: .leading) {
                LinearGradient(
                    colors: [.white.opacity(0.28), .white.opacity(0.04), .clear],
                    startPoint: .leading, endPoint: .trailing,
                )
                .frame(width: 6)
                .blendMode(.plusLighter)
                .allowsHitTesting(false)
            }
            .overlay {
                RoundedRectangle(cornerRadius: 5)
                    .strokeBorder(.white.opacity(0.10), lineWidth: 0.5)
            }
            .shadow(color: .black.opacity(0.55), radius: 5, y: 4)

            Text(item.title)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Cores.texto)
                .lineLimit(2)
                .multilineTextAlignment(.leading)
                .frame(maxWidth: .infinity, alignment: .leading)

            /// A linha de metadados omite **item por item** (§24).
            if let detalhe {
                Text(detalhe)
                    .font(.system(size: 10))
                    .foregroundStyle(Cores.textoApagado)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
        }
    }

    private var detalhe: String? {
        var partes: [String] = []
        if let ano = item.year { partes.append(String(ano)) }
        if item.eSerie, item.quantasTemporadas > 0 {
            partes.append("\(item.quantasTemporadas) temporada" + (item.quantasTemporadas > 1 ? "s" : ""))
        } else if let h = item.height {
            partes.append("\(h)p")
        }
        return partes.isEmpty ? nil : partes.joined(separator: " · ")
    }

    /// ⚠️ Cor **do servidor**, nunca sorteada. Uma cor inventada por obra
    /// pareceria classificação vinda do acervo, que é o §18 na versão mais
    /// difícil de notar.
    private var corDaObra: Color {
        guard let hex = item.corDominante else { return Cores.fundoElevado }
        let limpo = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard let valor = UInt32(limpo, radix: 16) else { return Cores.fundoElevado }
        return Color(hex: valor)
    }
}

extension Int {
    /// `8273` → `8.273`. O separador é o de **português**, sempre — e explícito,
    /// não o do aparelho: num iPhone em inglês sairia `8,273`, que em português é
    /// oito vírgula dois.
    var comMilhar: String {
        let f = NumberFormatter()
        f.numberStyle = .decimal
        f.locale = Locale(identifier: "pt_BR")
        return f.string(from: NSNumber(value: self)) ?? String(self)
    }
}

/// Uma pílula de prateleira.
///
/// ⚠️ A contagem só é escrita quando o servidor **respondeu** por aquela
/// prateleira: enquanto não chega, a pílula tem só o nome — e não um `0`, que é
/// uma afirmação («não há nada ali») que o app ainda não pode fazer.
private struct PilulaDaPrateleira: View {
    let rotulo: String
    let contagem: Int?
    let acesa: Bool
    let aoTocar: () -> Void

    var body: some View {
        Button(action: aoTocar) {
            HStack(spacing: 8) {
                Text(rotulo)
                if let contagem {
                    Text("\(contagem)")
                        .foregroundStyle(acesa ? Cores.destaque.opacity(0.7) : Cores.textoApagado)
                }
            }
            .font(.system(size: 13))
            .foregroundStyle(acesa ? Cores.destaque : Cores.textoApagado)
            .padding(.horizontal, 14)
            .frame(minHeight: 44)
            .background(acesa ? Cores.destaque.opacity(0.12) : Cores.fundoElevado, in: .capsule)
            .overlay {
                Capsule().strokeBorder(acesa ? Cores.destaque : .clear, lineWidth: 1)
            }
            .contentShape(.capsule)
        }
        .buttonStyle(.plain)
    }
}
