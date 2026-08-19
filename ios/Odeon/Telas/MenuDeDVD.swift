import SwiftUI

/// O menu de DVD — o último pedaço da locadora, e ele abre **só por ela**.
///
/// ## Por que só aqui, e só em disco
///
/// «A fita não tem menu, tem rebobinar» (§14.4). E o «assistir» da biblioteca, da
/// busca e da ficha continua indo **direto pro filme**: o menu é o objeto
/// encenando o que ele é, não um pedágio no caminho de quem só quer assistir.
///
/// ## O que veio do Android, e o que ficou de fora **por decisão já tomada lá**
///
/// | a web tem | aqui |
/// |---|---|
/// | vinheta de 2,5s, e qualquer tecla pula | ✅ e qualquer toque pula |
/// | doze climas, um por estante | ✅ **em cor e forma**, a mesma tabela |
/// | a trilha sintetizada | ❌ vetada na espec — é Web Audio, e o equivalente é reescrever o sintetizador |
/// | o filme rodando de fundo | ◐ o backdrop com deriva lenta |
/// | `Continuar` · `Do começo` · `Capítulos` · `Legendas` | ✅ os quatro |
/// | a grade de capítulos, com a origem dita | ✅ e com **doze molduras vazias** enquanto carrega |
///
/// A cena viva de fundo abriria uma sessão de HLS só pra desenhar um enfeite — e
/// este servidor de casa atende três pessoas com o Postgres do lado.
@Observable
@MainActor
final class ModeloDoMenuDoDisco {
    var disco: MenuDoDisco?
    var cenas: [Cena] = []
    var recado: String?

    private let odeon: RepositorioOdeon
    private let obraId: String

    init(odeon: RepositorioOdeon, obraId: String) {
        self.odeon = odeon
        self.obraId = obraId
    }

    func carregar() async {
        do {
            disco = try await odeon.menuDoDisco(obra: obraId)
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "este disco não abriu"
            return
        }
        /// ⚠️ As cenas vêm **depois**, e a falha delas não derruba o menu: sem
        /// elas a grade de capítulos fica com as molduras vazias, e os quatro
        /// itens continuam servindo. Um menu de disco que não abre porque as
        /// miniaturas não vieram seria o §53 ao contrário.
        cenas = (try? await odeon.cenas(obra: obraId)) ?? []
    }
}

struct MenuDeDVD: View {
    let odeon: RepositorioOdeon
    let obraId: String
    let aoTocar: (MenuDoDisco, Double) -> Void
    let aoFechar: () -> Void

    @State private var modelo: ModeloDoMenuDoDisco
    /// ⚠️ A vinheta roda **toda vez que se põe o disco**, e é da web: ela é a
    /// lombada do menu, o que separa «abri um arquivo» de «pus um disco».
    ///
    /// No Android isto precisou de `rememberSaveable`, senão girar o aparelho
    /// recriava a atividade e a vinheta rodava de novo. Aqui `@State` já
    /// sobrevive à rotação — o SwiftUI não recria a `View` por causa dela.
    @State private var passouAVinheta = false
    @State private var nosCapitulos = false
    @State private var derivou = false

    init(
        odeon: RepositorioOdeon,
        obraId: String,
        aoTocar: @escaping (MenuDoDisco, Double) -> Void,
        aoFechar: @escaping () -> Void,
    ) {
        self.odeon = odeon
        self.obraId = obraId
        self.aoTocar = aoTocar
        self.aoFechar = aoFechar
        _modelo = State(wrappedValue: ModeloDoMenuDoDisco(odeon: odeon, obraId: obraId))
    }

    private var clima: Clima { climaDe(modelo.disco?.clima) }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            fundo

            if !passouAVinheta {
                /// ⚠️ Nada até o disco chegar — e é o certo: o leitor lê o disco
                /// antes de o menu existir. Uma vinheta em cima de um clima que
                /// ainda não se sabe é enfeite mentindo.
                if let d = modelo.disco {
                    Vinheta(clima: clima, nome: d.climaNome)
                }
            } else if nosCapitulos, let d = modelo.disco {
                capitulos(d)
            } else {
                menu
            }
        }
        /// ⚠️ Tocar **pula a vinheta**: ela é encenação, e encenação que não se
        /// pode pular vira espera. O toque só existe enquanto ela está rodando —
        /// depois disso a tela inteira absorvendo toques comeria os itens.
        .contentShape(.rect)
        .onTapGesture {
            if !passouAVinheta { withAnimation(.easeOut(duration: 0.25)) { passouAVinheta = true } }
        }
        .task {
            /// ## ⚠️ Carregar **antes**, e não junto
            ///
            /// A primeira versão corria a busca e os 2,5s em paralelo, pra a
            /// vinheta não custar espera. Só que o clima vem **na resposta**: com
            /// as duas correndo juntas, a vinheta começava no clima 11 (Drama,
            /// marrom, onda) e trocava pra o do filme no meio do caminho.
            ///
            /// Uma vinheta que anuncia o clima errado durante metade da própria
            /// duração não anuncia nada — e o clima é a única coisa que ela diz.
            /// Ela espera o disco, como um leitor espera o disco.
            await modelo.carregar()
            try? await Task.sleep(for: .milliseconds(2500))
            withAnimation(.easeOut(duration: 0.25)) { passouAVinheta = true }
            /// A deriva do fundo: um passeio lento de 4% sobre a arte, em 24s. A
            /// arte é desenhada **maior** que a moldura pra ter folga — senão a
            /// borda apareceria quando ela anda.
            withAnimation(.linear(duration: 24)) { derivou = true }
        }
    }

    /// O fundo: o backdrop com uma deriva lenta.
    @ViewBuilder
    private var fundo: some View {
        if let backdrop = modelo.disco?.backdrop {
            ArteDoOdeon(odeon: odeon, caminho: backdrop)
                .scaleEffect(1.06)
                .offset(x: derivou ? -24 : 0, y: derivou ? 10 : 0)
                .ignoresSafeArea()
                .clipped()

            /// ## ⚠️ A lavagem é **pesada de propósito**
            ///
            /// A primeira versão do Android deixava o meio a 34% e o screenshot
            /// mostrou o resultado: o pôster passava por cima do menu inteiro,
            /// com «Tocar» disputando espaço com uma camiseta vermelha. É o mesmo
            /// defeito que a ficha já tinha corrigido uma vez — «com o meio
            /// transparente, um backdrop claro vira uma faixa branca gritando».
            ///
            /// Num menu de disco o fundo é **ambiente**: existe pra dizer de que
            /// filme é o menu, não pra ser visto. Duas camadas, então — um véu
            /// escuro parelho e a tinta do clima por cima dele.
            Cores.fundo.opacity(0.80).ignoresSafeArea()
            LinearGradient(
                colors: [.clear, clima.tinta.opacity(0.22), Cores.fundo.opacity(0.70)],
                startPoint: .top, endPoint: .bottom,
            )
            .ignoresSafeArea()
        }
    }

    private var menu: some View {
        VStack(alignment: .leading, spacing: 10) {
            Spacer(minLength: 0)

            if let recado = modelo.recado {
                Text(recado).font(.system(size: 14)).foregroundStyle(Cores.textoApagado)
            }

            if let d = modelo.disco {
                Text(d.titulo)
                    .font(Tipo.letreiro(30))
                    .foregroundStyle(Cores.texto)
                    .lineLimit(3)
                    .multilineTextAlignment(.leading)

                /// O ano e o clima, na linha de baixo. O nome do clima vem do
                /// servidor — é o nome da estante que reivindicou o filme, e é o
                /// que liga o menu à prateleira de onde a caixa saiu.
                Text([d.ano.map(String.init), d.climaNome.isEmpty ? nil : d.climaNome]
                    .compactMap { $0 }.joined(separator: " · "))
                    .font(Tipo.rotulo(11)).tracking(2.2)
                    .foregroundStyle(clima.tinta)

                Color.clear.frame(height: 20)

                /// ⚠️ **`Continuar` só existe quando há de onde continuar** (§24).
                if d.temComoContinuar {
                    ItemDoMenu(rotulo: "Continuar", detalhe: d.ponteiro, clima: clima) {
                        aoTocar(d, d.posicao ?? 0)
                    }
                }
                ItemDoMenu(rotulo: d.temComoContinuar ? "Do começo" : "Tocar", clima: clima) {
                    aoTocar(d, 0)
                }
                /// Capítulos só existe se houver capítulo — e há disco sem nenhum.
                if !d.capitulos.isEmpty || !modelo.cenas.isEmpty {
                    ItemDoMenu(
                        rotulo: "Capítulos",
                        detalhe: "\(max(d.capitulos.count, modelo.cenas.count))",
                        clima: clima,
                    ) { nosCapitulos = true }
                }
                /// **Legendas é ficha, não ação** — a web diz isso com todas as
                /// letras. Trocar de legenda é no player, com o filme na tela;
                /// aqui é a informação de que elas existem, que é o que um
                /// encarte diz.
                if !d.legendas.isEmpty {
                    ItemDoMenu(
                        rotulo: "Legendas",
                        detalhe: d.legendas.joined(separator: " · "),
                        clima: clima, aoTocar: nil,
                    )
                }
            }

            Color.clear.frame(height: 12)
            Button(action: aoFechar) {
                Text("‹ guardar o disco")
                    .font(.system(size: 15))
                    .foregroundStyle(Cores.textoApagado)
                    .frame(minHeight: 44)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)

            Spacer(minLength: 0)
        }
        .frame(maxWidth: 620, alignment: .leading)
        .frame(maxWidth: .infinity)
        .padding(.horizontal, 28)
    }

    /// A grade de capítulos.
    ///
    /// ## As doze molduras vazias
    ///
    /// Elas são §15 — «moldura vazia em vez de carregando» — e o motivo é o mesmo
    /// das prateleiras da locadora: as miniaturas vêm de outra rota, e sem as
    /// molduras a grade nasce com zero de altura e **empurra a tela pra cima**
    /// quando as imagens chegam.
    private func capitulos(_ d: MenuDoDisco) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Button { nosCapitulos = false } label: {
                Text("‹ menu")
                    .font(.system(size: 15))
                    .foregroundStyle(clima.tinta)
                    .frame(minHeight: 44)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)

            /// A legenda diz **de onde vieram** os capítulos, e é a diferença
            /// entre um dado do disco e uma conta do app. §18: o app não deixa
            /// parecer que dividiu o filme quando foi o autor do disco quem
            /// dividiu — nem o contrário.
            Text(modelo.cenas.contains { $0.origem == "capitulo" } || !d.capitulos.isEmpty
                ? "nos cortes do disco" : "divididos pelo relógio")
                .font(Tipo.rotulo(10)).tracking(2)
                .foregroundStyle(Cores.textoApagado)

            ScrollView {
                LazyVGrid(
                    columns: [GridItem(.adaptive(minimum: 132), spacing: 10)],
                    spacing: 10,
                ) {
                    if modelo.cenas.isEmpty {
                        ForEach(0..<12, id: \.self) { _ in
                            Rectangle().fill(Cores.fundoElevado)
                                .aspectRatio(16.0 / 9.0, contentMode: .fit)
                                .clipShape(.rect(cornerRadius: 4))
                        }
                    } else {
                        ForEach(modelo.cenas) { cena in
                            Button { aoTocar(d, cena.segundos) } label: {
                                VStack(spacing: 4) {
                                    /// ## ⚠️ A arte vai em `background`, **de novo**
                                    ///
                                    /// Visto na tela: as molduras de uma mesma
                                    /// linha saíram com 185, 220, 225, 225 e 240pt.
                                    /// Numa grade adaptativa as colunas são iguais
                                    /// por construção — quem as desigualou foi o
                                    /// conteúdo, reivindicando a largura da imagem
                                    /// original em vez de aceitar a proposta.
                                    ///
                                    /// É o quinto lugar deste app onde eu escrevi
                                    /// isto: cartão dos baixados, fachada da ficha,
                                    /// polaroide do varal, capa da caixa, e aqui.
                                    /// `Color.clear` aceita **qualquer** proposta, e
                                    /// o que está atrás nunca decide o tamanho do
                                    /// que está na frente.
                                    Color.clear
                                        .aspectRatio(16.0 / 9.0, contentMode: .fit)
                                        .background {
                                            ZStack {
                                                Rectangle().fill(Cores.fundoElevado)
                                                ArteDoOdeon(odeon: odeon, caminho: cena.imagem)
                                            }
                                        }
                                        .clipShape(.rect(cornerRadius: 4))

                                    Text(relogioDaSessao(cena.segundos))
                                        .font(Tipo.rotulo(10)).tracking(1.4)
                                        .foregroundStyle(Cores.textoApagado)
                                }
                            }
                            .buttonStyle(.plain)
                        }
                    }
                }
            }
        }
        .frame(maxWidth: 760)
        .frame(maxWidth: .infinity)
        .padding(16)
    }
}

/// Um item do menu.
///
/// `aoTocar` nulo é **ficha, não ação** — o caso das legendas. Ele não fica
/// desabilitado com cara de botão quebrado: fica sem o traço e sem o toque, que é
/// como uma linha de encarte se parece.
private struct ItemDoMenu: View {
    let rotulo: String
    var detalhe: String? = nil
    let clima: Clima
    var aoTocar: (() -> Void)?

    var body: some View {
        let linha = HStack(spacing: 12) {
            /// O traço de seleção, à esquerda — o cursor de um menu de disco, que
            /// nunca foi um retângulo com fundo.
            Rectangle()
                .fill(aoTocar == nil ? Cores.textoApagado.opacity(0.35) : clima.tinta)
                .frame(width: aoTocar == nil ? 2 : 14, height: 2)
            Text(rotulo)
                .font(Tipo.letreiro(20))
                .foregroundStyle(aoTocar == nil ? Cores.textoApagado : Cores.texto)
            if let detalhe {
                Text(detalhe)
                    .font(.system(size: 12).monospacedDigit())
                    .foregroundStyle(Cores.textoApagado)
                    .lineLimit(1)
            }
            Spacer(minLength: 0)
        }
        .padding(.vertical, 10)
        .frame(minHeight: 44)

        if let aoTocar {
            Button(action: aoTocar) { linha.contentShape(.rect) }.buttonStyle(.plain)
        } else {
            linha
        }
    }
}

/// A vinheta: 2,5s, e qualquer toque pula.
///
/// Quatro formas para doze climas — «doze animações distintas seriam doze coisas
/// pra manter», diz a folha da web, e a conta é a mesma aqui.
private struct Vinheta: View {
    let clima: Clima
    let nome: String

    @State private var comecouEm = Date.now

    var body: some View {
        TimelineView(.animation) { quadro in
            let avanco = min(quadro.date.timeIntervalSince(comecouEm) / 2.5, 1)
            ZStack {
                Canvas { contexto, tamanho in
                    let centro = CGPoint(x: tamanho.width / 2, y: tamanho.height / 2)
                    let maior = min(tamanho.width, tamanho.height)
                    switch clima.vinheta {
                    /// **Risco** — terror, guerra, ação: uma lâmina que atravessa.
                    case .risco:
                        var caminho = Path()
                        caminho.move(to: CGPoint(x: 0, y: centro.y))
                        caminho.addLine(to: CGPoint(x: tamanho.width * avanco, y: centro.y))
                        contexto.stroke(caminho, with: .color(clima.tinta), lineWidth: 3)

                    /// **Íris** — animação, infantil, comédia: o círculo que abre,
                    /// como o fecha-íris dos desenhos antigos.
                    case .iris:
                        contexto.stroke(
                            circulo(centro: centro, raio: maior * 0.5 * avanco),
                            with: .color(clima.tinta), lineWidth: 2.5,
                        )

                    /// **Onda** — faroeste, crime, drama: círculos concêntricos
                    /// que se afastam.
                    case .onda:
                        for i in 0..<3 {
                            let fase = (avanco + Double(i) * 0.33).truncatingRemainder(dividingBy: 1)
                            contexto.stroke(
                                circulo(centro: centro, raio: maior * 0.5 * fase),
                                with: .color(clima.tinta.opacity((1 - fase) * 0.8)), lineWidth: 2,
                            )
                        }

                    /// **Brilho** — documentário, sci-fi, romance: um halo que
                    /// acende e assenta.
                    case .brilho:
                        contexto.fill(
                            circulo(centro: centro, raio: maior * 0.5),
                            with: .radialGradient(
                                Gradient(colors: [clima.tinta.opacity(0.55 * avanco), .clear]),
                                center: centro, startRadius: 0, endRadius: maior * 0.5,
                            ),
                        )
                    }
                }
                Text(nome.uppercased())
                    .font(Tipo.rotulo(11)).tracking(3)
                    .foregroundStyle(clima.tinta)
            }
        }
    }

    private func circulo(centro: CGPoint, raio: CGFloat) -> Path {
        Path(ellipseIn: CGRect(
            x: centro.x - raio, y: centro.y - raio, width: raio * 2, height: raio * 2,
        ))
    }
}

/// O clima, em cor e forma.
///
/// ## ⚠️ O índice **é** o contrato
///
/// Ele vem do servidor e é a posição na lista `ESTANTES` da locadora. Mexer na
/// ordem de lá sem mexer aqui troca o clima de todo mundo — o comentário é da
/// web, vale no Android e vale aqui.
///
/// ## A paleta daqui é exceção deliberada
///
/// O §12 fechou a paleta do app, e esta tela sai dela de propósito. A decisão do
/// `IDEIAS.md` §3.7 é explícita: «o estilo sai da temática do filme — comédia e
/// terror não ganham o mesmo menu». Um menu de disco não é cromo do produto; é a
/// arte da edição especial, e ela nunca combinou com o resto da estante.
///
/// ## O que sobrou da tabela da web, e o que não veio
///
/// Lá cada clima tem escala, raiz, andamento, timbre e corte de filtro — os cinco
/// campos do **sintetizador**, vetado nesta versão. Ficaram os dois que
/// sobrevivem sem som: a **tinta** e a **forma da vinheta**. Os nomes e a ordem
/// são os mesmos, pra o dia em que o som entrar não precisar reescrever a tabela.
struct Clima: Sendable {
    let tinta: Color
    let vinheta: FormaDaVinheta
}

enum FormaDaVinheta: Sendable { case risco, iris, onda, brilho }

private let climas: [Clima] = [
    Clima(tinta: Color(hex: 0x8C1C1C), vinheta: .risco),  // 0 · Terror
    Clima(tinta: Color(hex: 0xC08A3E), vinheta: .onda),   // 1 · Faroeste
    Clima(tinta: Color(hex: 0x6F7A52), vinheta: .risco),  // 2 · Guerra
    Clima(tinta: Color(hex: 0x5B7F95), vinheta: .brilho), // 3 · Documentário
    Clima(tinta: Color(hex: 0xD97AB0), vinheta: .iris),   // 4 · Animação
    Clima(tinta: Color(hex: 0xE0B04A), vinheta: .iris),   // 5 · Infantil
    Clima(tinta: Color(hex: 0x4FB3C8), vinheta: .brilho), // 6 · Ficção científica
    Clima(tinta: Color(hex: 0xD9762B), vinheta: .risco),  // 7 · Ação e aventura
    Clima(tinta: Color(hex: 0x4A5F9E), vinheta: .onda),   // 8 · Crime e suspense
    Clima(tinta: Color(hex: 0xE08A5A), vinheta: .iris),   // 9 · Comédia
    Clima(tinta: Color(hex: 0xC4708C), vinheta: .brilho), // 10 · Romance
    Clima(tinta: Color(hex: 0xA08258), vinheta: .onda),   // 11 · Drama
]

/// O clima de um índice. Fora da faixa cai no **11 · Drama**, que é o sumidouro
/// da web pelo mesmo motivo: é o clima que menos afirma coisa alguma.
func climaDe(_ indice: Int?) -> Clima {
    guard let indice, climas.indices.contains(indice) else { return climas[11] }
    return climas[indice]
}
