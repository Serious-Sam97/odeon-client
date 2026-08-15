import SwiftUI

/// A locadora — a vitrine.
///
/// ## ⚠️ O que esta tela **não** faz, e é decisão
///
/// Ela não pega fita. `POST /api/locadora/alugar` **escreve no acervo compartilhado
/// por três pessoas**: um empréstimo criado por engano fica no perfil de alguém, e
/// desfazê-lo é do outro lado. O botão entra quando houver quem confirme na tela.
///
/// ⚠️ E há um segundo motivo, que é o §53: a ficha do Android oferece «pegar a
/// fita» em toda obra e o servidor recusa algumas com **403**, porque a locadora
/// tem 600 caixas sobre 17.498 obras e o cliente **não tem como prever qual**.
/// Está escrito como Pedido 1 no `PEDIDOS-AO-SERVIDOR.md` e continua aberto. Um
/// botão que leva a 403 é defeito, não funcionalidade.
@Observable
@MainActor
final class ModeloDaLocadora {
    var loja: Loja?
    var recado: String?

    private let odeon: RepositorioOdeon
    init(odeon: RepositorioOdeon) { self.odeon = odeon }

    func carregar() async {
        do {
            _ = try? await odeon.garantirTokenDeMidia()
            loja = try await odeon.estantes()
            await odeon.conferirTokenDeMidia(comArte: loja?.estantes.first?.caixas.first?.poster)
            recado = nil
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "a loja não abriu"
        }
    }

    func capa(_ caixa: CaixaExposta) -> URL? { odeon.urlDaArte(caixa.poster) }

    /// «a vitrine vira domingo» · `nil`.
    ///
    /// ## ⚠️ A conta **saiu daqui**, e a saída é a decisão
    ///
    /// Ela morava inteira nesta classe, e ia ser reescrita no guia — porque a
    /// revista vira **no mesmo instante** que esta vitrine. Duas contas do mesmo
    /// relógio é uma que envelhece sozinha, e o `Virada.kt` do Android já tinha o
    /// nome disso: «duas telas dizendo o mesmo instante com palavras diferentes
    /// fariam parecer dois relógios».
    ///
    /// O que sobrou aqui é o **verbo**, que é o que muda entre as duas telas: a
    /// locadora diz «a vitrine vira …» e a revista diz «até …». Ver `Virada.swift`
    /// pro resto, inclusive pro carimbo ISO cru que apareceu nesta tela.
    var quandoVira: String? {
        viraQuando(loja?.viraEm).map { "a vitrine vira \($0)" }
    }
}

struct TelaDaLocadora: View {
    let odeon: RepositorioOdeon
    let insignia: Insignia
    let aoAbrirPerfil: () -> Void
    let aoSair: () -> Void
    let aoEscolher: (CaixaExposta) -> Void

    @Environment(\.horizontalSizeClass) private var largura

    /// ⚠️ A caixa cresce com a sala, pela mesma razão que o cartaz da grade: numa
    /// prateleira de 1376pt, uma caixa de 104 é miniatura de vitrine, não fita que
    /// se pega. E aqui dói mais que na grade — a caixa **é o produto** (§1.3: «um
    /// catálogo de arquivos lista linhas; uma locadora tem caixas que se pegam»),
    /// e uma caixa pequena demais para o dedo girar deixa de ser objeto.
    /// ⚠️ As medidas vêm do **formato**, não de um número meu. Ver `Medidas`:
    /// DVD 102×144×11, VHS 79×144×19 — os do Android. A escala muda o tamanho na
    /// prateleira sem mudar a proporção do objeto.
    private var escala: CGFloat { largura == .regular ? 1.0 : 0.72 }

    /// A caixa que está **na mão**. `nil` é a loja em repouso.
    ///
    /// ⚠️ Ela mora aqui e não na prateleira: o palco fica por cima da tela inteira
    /// e **fora da rolagem**, e um estado dentro da fileira subiria junto com ela.
    @State private var naMao: CaixaExposta?

    @State private var modelo: ModeloDaLocadora

    init(
        odeon: RepositorioOdeon, insignia: Insignia,
        aoAbrirPerfil: @escaping () -> Void, aoSair: @escaping () -> Void,
        aoEscolher: @escaping (CaixaExposta) -> Void,
    ) {
        self.odeon = odeon
        self.insignia = insignia
        self.aoAbrirPerfil = aoAbrirPerfil
        self.aoSair = aoSair
        self.aoEscolher = aoEscolher
        _modelo = State(wrappedValue: ModeloDaLocadora(odeon: odeon))
    }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 30) {
                    cabecalho

                    if let recado = modelo.recado {
                        Text(recado).font(.system(size: 14)).foregroundStyle(Cores.textoApagado)
                    }

                    ForEach(modelo.loja?.estantes ?? []) { estante in
                        prateleira(estante)
                    }
                }
                .padding(.vertical, 18)
            }
        }
        .task { if modelo.loja == nil { await modelo.carregar() } }
        .overlay {
            if let caixa = naMao {
                PalcoDaCaixa(
                    odeon: odeon, caixa: caixa,
                    medidas: medidasDe(caixa), ehVhs: ehVhs(caixa), cor: cor(caixa),
                    aoFechar: { withAnimation(.easeOut(duration: 0.2)) { naMao = nil } },
                    aoVerAFicha: { naMao = nil; aoEscolher(caixa) },
                )
                .transition(.opacity.combined(with: .scale(scale: 0.86)))
            }
        }
    }

    /// A entrada da loja: a arandela acesa, o letreiro na luz dela, e as
    /// etiquetas de papel penduradas com as contagens.
    ///
    /// ⚠️ O topo deixou de ser cabeçalho de app e virou **a parede da entrada** —
    /// é o desenho que o dono aprovou. O `CabecalhoDaTela` das outras abas não
    /// entra aqui: nesta tela o nome da loja é o letreiro dela, não um título de
    /// página. O rosto continua no canto, por cima da parede.
    private var cabecalho: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 0) {
                Arandela()

                Text("locadora")
                    .font(Tipo.letreiro(34))
                    .tracking(1.4)
                    .foregroundStyle(Color(hex: 0xE8CF9A))
                    /// ⚠️ O halo é **da lâmpada**, não da letra: é o facho da
                    /// arandela chegando no texto. Sem ele o letreiro é uma
                    /// palavra dourada; com ele, está aceso.
                    .shadow(color: Cores.destaque.opacity(0.45), radius: 13)
                    .padding(.top, -6)

                /// ⚠️ O subtítulo do mock dizia «aberta até meia-noite» — e saiu:
                /// a loja não fecha de verdade, e horário inventado é mentira com
                /// cara de metadado (§18). «Acervo da casa» é o que ela é.
                Text("ACERVO DA CASA")
                    .font(Tipo.rotulo(10))
                    .tracking(3.0)
                    .foregroundStyle(Cores.destaqueApagado)
                    .padding(.top, 3)

                etiquetas
                    .padding(.top, 20)

                /// ⚠️ **`vira_em` é o que torna a vitrine promessa, e não
                /// sorteio.** Uma seleção que muda sem data anunciada é
                /// aleatoriedade; com data, é programação. Some sem data (§24).
                if let frase = modelo.quandoVira {
                    Text(frase)
                        .font(.system(size: 12))
                        .foregroundStyle(Cores.textoApagado)
                        .padding(.top, 14)
                }
            }
            .frame(maxWidth: .infinity)

            RostoNoCanto(insignia: insignia, aoAbrirPerfil: aoAbrirPerfil, aoSair: aoSair)
                .padding(.trailing, 20)
        }
    }

    /// As duas contagens da porta, em papel por barbante.
    ///
    /// ⚠️ Elas **só nascem com a vitrine na mão**: sem ela, dois papeizinhos
    /// dizendo «0» seriam o app afirmando sobre um acervo que não conseguiu ler.
    /// Erro de rede não é resposta vazia (§18).
    @ViewBuilder
    private var etiquetas: some View {
        if let loja = modelo.loja, !loja.estantes.isEmpty {
            HStack(alignment: .top, spacing: 22) {
                EtiquetaPendurada(
                    numero: "\(loja.estantes.reduce(0) { $0 + $1.caixas.count })",
                    rotulo: "na prateleira",
                    /// ⚠️ Os ângulos são **constantes**, uma por etiqueta. Sorteá-los
                    /// faria o papel tremer a cada redesenho da tela.
                    angulo: -2.5,
                )
                EtiquetaPendurada(
                    numero: loja.noAcervo.comMilhar,
                    rotulo: "no acervo",
                    angulo: 2.0,
                )
            }
        }
    }

    private func prateleira(_ estante: EstanteExposta) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            /// ⚠️ A placa diz «16 de 113», e não «16». O total é **do acervo**,
            /// não do que está à vista — a vitrine é uma amostra que gira, e dizer
            /// só o que se vê faria a loja parecer do tamanho da prateleira.
            HStack(alignment: .bottom, spacing: 10) {
                PlaquinhaDaEstante(nome: estante.nome, cor: papelDaEstante(estante))
                if estante.total > estante.caixas.count {
                    Text("\(estante.caixas.count) de \(estante.total.comMilhar)")
                        .font(.system(size: 11).monospacedDigit())
                        .foregroundStyle(Cores.destaqueApagado)
                        .padding(.bottom, 3)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 10)
            .zIndex(1)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .bottom, spacing: largura == .regular ? 30 : 22) {
                    ForEach(estante.caixas) { caixa in
                        /// ⚠️ Tocar **pega a caixa**, e não abre a ficha. É o que
                        /// o Android faz: a caixa sai da estante e vai pra mão, e a
                        /// ficha vira uma das coisas que se pode fazer com ela — não
                        /// a única, e não a primeira.
                        Button { withAnimation(.easeOut(duration: 0.22)) { naMao = caixa } } label: {
                            /// ⚠️ **`giravel: false` na estante.** Lá o arrasto é
                            /// da fileira, e disputar o gesto faria a lista não
                            /// rolar. A caixa gira na mão, no palco — não aqui.
                            CaixaEm3D(
                                medidas: medidasDe(caixa).vezes(escala),
                                giravel: false,
                            ) { lado, luz in
                                FaceDaCaixa(
                                    odeon: odeon,
                                    lado: lado, luz: luz,
                                    medidas: medidasDe(caixa).vezes(escala),
                                    ehVhs: ehVhs(caixa),
                                    titulo: caixa.titulo,
                                    cor: cor(caixa),
                                    capa: caixa.poster,
                                )
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                /// A folga à esquerda é a espessura da lombada: sem ela a primeira
                /// caixa fica com o canto cortado na borda da tela.
                .padding(.leading, largura == .regular ? 44 : 34)
                .padding(.trailing, 20)
            }

            /// ⚠️ **Sem espaço entre a caixa e a tábua.** A web diz por quê: «a
            /// tábua encosta na base das caixas; com folga embaixo o conjunto lê
            /// como cartão, não como objeto». O `spacing: 0` desta `VStack` é essa
            /// frase.
            TabuaDaPrateleira()
        }
    }

    /// A cor do papel da plaquinha.
    ///
    /// ⚠️ Ela vem do **nome da estante**, e não de um sorteio: a mesma estante
    /// tem sempre o mesmo papel, em toda abertura do app e em todo aparelho. É
    /// assim que se acha «Terror» de relance — e um papel que muda de cor a cada
    /// visita não é uma etiqueta, é um piscar.
    private func papelDaEstante(_ estante: EstanteExposta) -> Color {
        let papeis: [Color] = [
            Color(hex: 0xF0DE8C), Color(hex: 0xBFD9E8), Color(hex: 0xE8C7B8),
            Color(hex: 0xC9E0BC), Color(hex: 0xE3CDE8), Color(hex: 0xF2ECE0),
        ]
        let soma = estante.nome.unicodeScalars.reduce(0) { $0 &+ Int($1.value) }
        return papeis[soma % papeis.count]
    }

    /// Fita ou disco?
    ///
    /// ## ⚠️ O corte é do **servidor**, e ainda não chega aqui
    ///
    /// No Android ele vem de `ultimo_ano_vhs`, na `/api/locadora/prateleira` — «o
    /// mesmo número que decide se a caixa rebobina, e tê-lo em dois lugares é como
    /// os dois passariam a discordar». Este cliente ainda não mapeia a
    /// `Prateleira`.
    ///
    /// ⚠️ Enquanto não chega, **tudo é disco**, que é o padrão do Android quando
    /// não há corte: «na dúvida, o app não afirma que uma obra é de uma era que
    /// ele não sabe qual é — e disco é o caso mais comum do acervo». Um corte
    /// chutado aqui seria a segunda cópia de um número que já existe.
    private func ehVhs(_ caixa: CaixaExposta) -> Bool { false }

    private func medidasDe(_ caixa: CaixaExposta) -> Medidas {
        ehVhs(caixa) ? .vhs : .dvd
    }

    /// Cor **do servidor**, nunca sorteada (§18).
    private func cor(_ caixa: CaixaExposta) -> Color {
        guard let hex = caixa.corDominante else { return Cores.fundoElevado }
        let limpo = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard let valor = UInt32(limpo, radix: 16) else { return Cores.fundoElevado }
        return Color(hex: valor)
    }
}
