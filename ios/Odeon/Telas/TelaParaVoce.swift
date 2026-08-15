import SwiftUI

/// «Para você» — recomendação **com motivo**.
///
/// ## Ela é a tese do projeto numa tela só
///
/// O README do Odeon resume o produto assim: *não é um catálogo de arquivos, é
/// uma biblioteca que te conhece*. Esta é a tela onde isso ou é verdade ou não é.
///
/// ⚠️ E o que faz ser verdade é o **motivo**, não a ordem. Uma lista ordenada por
/// um número que ninguém vê é indistinguível de «os mais recentes» — a frase é a
/// metade que importa, e ela vem pronta do servidor, que é quem tem o perfil, os
/// vetores e o histórico.
@Observable
@MainActor
final class ModeloParaVoce {
    var itens: [Recomendacao] = []
    var perfil: PerfilDeGosto?
    var aindaNaoTeConhece = false
    var carregando = true
    var recado: String?

    /// «Tenho quanto tempo?» — o filtro que faz esta tela ser de celular.
    ///
    /// ⚠️ `nil` é «não importa», e é o padrão. Um filtro ligado por default
    /// esconderia metade do acervo sem ninguém ter pedido.
    var minutos: Int? {
        didSet { if minutos != oldValue { Task { await carregar() } } }
    }

    private let odeon: RepositorioOdeon
    init(odeon: RepositorioOdeon) { self.odeon = odeon }

    func carregar() async {
        carregando = true
        defer { carregando = false }
        do {
            _ = try? await odeon.garantirTokenDeMidia()
            let resposta = try await odeon.paraVoce(minutos: minutos)
            itens = resposta.items
            perfil = resposta.profile
            aindaNaoTeConhece = resposta.aindaNaoTeConhece
            recado = nil
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "não deu pra carregar"
        }
    }

    func arte(_ item: Recomendacao) -> String? {
        /// ⚠️ `backdrop` primeiro aqui, ao contrário da grade. Um cartão largo com
        /// pôster vertical dentro deixa duas tarjas pretas; e nesta tela o cartão
        /// é largo porque precisa caber a frase.
        item.backdrop ?? item.poster
    }
}

struct TelaParaVoce: View {
    let odeon: RepositorioOdeon
    let insignia: Insignia
    let aoAbrirPerfil: () -> Void
    let aoSair: () -> Void
    let aoEscolher: (Recomendacao) -> Void

    @State private var modelo: ModeloParaVoce

    init(
        odeon: RepositorioOdeon, insignia: Insignia,
        aoAbrirPerfil: @escaping () -> Void, aoSair: @escaping () -> Void,
        aoEscolher: @escaping (Recomendacao) -> Void,
    ) {
        self.odeon = odeon
        self.insignia = insignia
        self.aoAbrirPerfil = aoAbrirPerfil
        self.aoSair = aoSair
        self.aoEscolher = aoEscolher
        _modelo = State(wrappedValue: ModeloParaVoce(odeon: odeon))
    }

    /// As faixas de tempo. ⚠️ São **poucas e redondas** de propósito: quem abre o
    /// app às onze da noite quer «tenho uma hora», não um seletor de minutos.
    /// ⚠️ **Seis, e as do Android** — não três que eu escolhi. A régua é a mesma
    /// («quem abre o app às onze da noite quer "tenho uma hora"»), mas quem
    /// decidiu quais são as horas foi o produto, e o produto já tinha decidido:
    /// `qualquer tempo · 15 min · 30 min · 45 min · 1h · 2h`. Três faixas grossas
    /// não respondem «tenho vinte minutos antes de dormir».
    private let faixas: [(rotulo: String, minutos: Int?)] = [
        ("qualquer tempo", nil), ("15 min", 15), ("30 min", 30),
        ("45 min", 45), ("1h", 60), ("2h", 120),
    ]

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    CabecalhoDaTela(
                        titulo: "para você",
                        insignia: insignia, aoAbrirPerfil: aoAbrirPerfil, aoSair: aoSair,
                    )
                    .padding(.horizontal, -20)

                    /// ⚠️ «TENHO» — o rótulo que faz as pílulas serem uma
                    /// pergunta e não uma barra de filtros. Sem ele, «15 min» ao
                    /// lado de «30 min» pode ser duração do filme; com ele, é o
                    /// tempo que **você** tem.
                    RotuloDeSecao(texto: "TENHO")
                        .padding(.top, 2)
                    tempo

                    /// ⚠️ **A distinção que a tela existe pra fazer.** «Não
                    /// recomendo nada» e «ainda não te conheço» são a mesma tela
                    /// vazia e coisas opostas: a primeira parece algoritmo ruim, a
                    /// segunda é um convite a assistir mais.
                    if modelo.aindaNaoTeConhece {
                        aviso(
                            "ainda estou te conhecendo",
                            detalhe: modelo.perfil.map {
                                "você começou \($0.obrasTocadas) e terminou \($0.finished). "
                                    + "Assista mais um pouco e isto aqui fica seu."
                            } ?? "assista mais um pouco e isto aqui fica seu.",
                        )
                    }

                    if let recado = modelo.recado {
                        aviso("não deu", detalhe: recado)
                    } else if modelo.itens.isEmpty, !modelo.carregando, !modelo.aindaNaoTeConhece {
                        /// Vazio **com** filtro é diferente de vazio sem: um é «não
                        /// tenho nada tão curto», o outro é «não tenho nada».
                        aviso(
                            modelo.minutos == nil ? "nada por aqui ainda" : "nada tão curto",
                            detalhe: modelo.minutos == nil ? nil : "tente uma faixa de tempo maior.",
                        )
                    }

                    if let primeiro = modelo.itens.first { heroi(primeiro) }
                    ForEach(modelo.itens.dropFirst()) { item in
                        Button { aoEscolher(item) } label: { cartao(item) }
                            .buttonStyle(.plain)
                    }
                }
                .frame(maxWidth: 620, alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(20)
            }
        }
        .task { if modelo.itens.isEmpty { await modelo.carregar() } }
    }

    private var tempo: some View {
        /// ⚠️ Rolam na horizontal em vez de quebrar em duas linhas como no
        /// Android: seis pílulas não cabem em 402pt, e a segunda linha empurra o
        /// herói pra fora da primeira tela — que é o lugar onde ele serve.
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 8) {
            ForEach(faixas, id: \.rotulo) { faixa in
                let ligada = modelo.minutos == faixa.minutos
                Button { modelo.minutos = faixa.minutos } label: {
                    Text(faixa.rotulo)
                        .font(.system(size: 13, weight: ligada ? .semibold : .regular))
                        .padding(.horizontal, 13).padding(.vertical, 7)
                        .background(ligada ? Cores.destaque : Cores.fundoElevado, in: .capsule)
                        .foregroundStyle(ligada ? Cores.fundo : Cores.textoApagado)
                }
                .buttonStyle(.plain)
                }
            }
        }
    }

    /// O herói: o primeiro da lista, grande.
    ///
    /// ⚠️ Ele existe no Android e é a mesma decisão do herói da biblioteca — uma
    /// tela de recomendação que abre com seis cartões iguais não recomenda, lista.
    /// O primeiro tem que **parecer** a resposta.
    private func heroi(_ item: Recomendacao) -> some View {
        Button { aoEscolher(item) } label: {
            ZStack(alignment: .bottomLeading) {
                Rectangle().fill(corDaObra(item))
                ArteDoOdeon(odeon: odeon, caminho: modelo.arte(item))
                LinearGradient(colors: [.clear, .black.opacity(0.85)],
                               startPoint: .center, endPoint: .bottom)
                VStack(alignment: .leading, spacing: 4) {
                    Text(item.title)
                        .font(Tipo.letreiro(28))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    if let porque = item.porque {
                        Text(porque).font(.system(size: 14)).foregroundStyle(Cores.destaque)
                    }
                }
                .padding(16)
            }
            .frame(height: 220)
            .clipShape(.rect(cornerRadius: 12))
        }
        .buttonStyle(.plain)
    }

    /// Uma linha da lista.
    ///
    /// ## ⚠️ Era um cartão largo com o título por cima da arte; virou linha
    ///
    /// No Android o resto da lista é **cartaz pequeno à esquerda, texto à
    /// direita** — e a razão em destaque embaixo do ano. A diferença não é
    /// estética: com seis cartões-herói empilhados, a tela vira uma pilha de
    /// pôsteres e a razão — que é a metade que importa — some no meio do
    /// desenho. Um herói e uma lista dizem qual é a aposta; seis heróis não
    /// dizem nada.
    private func cartao(_ item: Recomendacao) -> some View {
        HStack(alignment: .top, spacing: 14) {
            ZStack {
                Rectangle().fill(corDaObra(item))
                ArteDoOdeon(odeon: odeon, caminho: item.poster)
            }
            .frame(width: 78, height: 117)
            .clipShape(.rect(cornerRadius: 6))

            VStack(alignment: .leading, spacing: 5) {
                Text(item.title)
                    .font(.system(size: 17, weight: .medium))
                    .foregroundStyle(Cores.texto)
                    .multilineTextAlignment(.leading)
                if let ano = item.year {
                    Text(String(ano)).font(.system(size: 14)).foregroundStyle(Cores.textoApagado)
                }
                /// ⚠️ **A metade que importa**, e sem o rótulo «porque» que eu
                /// tinha inventado: a frase do servidor já começa com «você
                /// costuma…», e um rótulo antes dela era legenda de uma coisa que
                /// se explica sozinha.
                if let porque = item.porque {
                    Text(porque)
                        .font(.system(size: 14))
                        .foregroundStyle(Cores.destaque)
                        .multilineTextAlignment(.leading)
                }
            }
            Spacer(minLength: 0)
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Cores.fundoElevado, in: .rect(cornerRadius: 11))
    }

    private func aviso(_ titulo: String, detalhe: String?) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            Text(titulo).font(Tipo.letreiro(19)).foregroundStyle(Cores.texto)
            if let detalhe {
                Text(detalhe).font(.system(size: 14)).foregroundStyle(Cores.textoApagado)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Cores.fundoElevado, in: .rect(cornerRadius: 11))
    }

    /// Cor **do servidor**, nunca sorteada — §18.
    private func corDaObra(_ item: Recomendacao) -> Color {
        guard let hex = item.corDominante else { return Cores.fundoElevado }
        let limpo = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard let valor = UInt32(limpo, radix: 16) else { return Cores.fundoElevado }
        return Color(hex: valor)
    }
}
