import SwiftUI

/// A fachada da ficha: a **marquise** e o **varal**.
///
/// ## De onde elas vêm
///
/// Do `obra/Marquise.kt` do Android, e o comentário de lá dá a chave: elas «não
/// ficaram» — a marquise é a **fachada do cinema** e o varal são as fotos de cena
/// penduradas nela. A ficha deixa de ser um registro sobre um filme e passa a ser
/// a entrada de uma sessão.
///
/// ⚠️ E **o varal é navegação, não enfeite**: cada foto é um instante do filme, e
/// é por ali que se entra no meio dele.

/// A marquise: a moldura de lâmpadas com o nome em cima da porta.
///
/// ⚠️ As lâmpadas são **contadas pela largura**, não um número fixo: uma moldura
/// com 14 lâmpadas fixas fica com o espaçamento errado em toda tela que não for a
/// que serviu de régua. Numa marquise de verdade elas são igualmente espaçadas
/// porque foram parafusadas de tantos em tantos centímetros.
struct Marquise<Conteudo: View>: View {
    @ViewBuilder let conteudo: Conteudo

    /// De quantos em quantos pontos vem a próxima lâmpada.
    private let passo: CGFloat = 42

    var body: some View {
        conteudo
            .frame(maxWidth: .infinity)
            .padding(.horizontal, 18)
            .padding(.vertical, 26)
            .background(Cores.fundoElevado.opacity(0.55), in: .rect(cornerRadius: 10))
            .overlay {
                RoundedRectangle(cornerRadius: 10)
                    .stroke(Cores.destaque.opacity(0.55), lineWidth: 1)
            }
            .overlay(alignment: .top) { fileiraDeLampadas }
            .overlay(alignment: .bottom) { fileiraDeLampadas }
    }

    private var fileiraDeLampadas: some View {
        GeometryReader { g in
            let quantas = max(2, Int(g.size.width / passo))
            HStack(spacing: 0) {
                ForEach(0 ..< quantas, id: \.self) { _ in
                    Lampada()
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .frame(height: 14)
        .padding(.horizontal, 8)
        .allowsHitTesting(false)
    }
}

/// Uma lâmpada da marquise.
///
/// ⚠️ Ela é **um ponto quente com um halo**, e não um círculo dourado: o que faz
/// parecer lâmpada acesa é a luz em volta, não a cor do vidro. Sem o halo é uma
/// fileira de bolinhas.
private struct Lampada: View {
    var body: some View {
        Circle()
            .fill(Cores.destaqueQuente)
            .frame(width: 9, height: 9)
            .shadow(color: Cores.destaqueQuente.opacity(0.9), radius: 5)
            .shadow(color: Cores.destaque.opacity(0.5), radius: 9)
    }
}

/// O varal: as fotos de cena penduradas por prendedores de madeira.
///
/// ## ⚠️ Sem margem entre a marquise e o varal
///
/// É o ponto do desenho, e o Android registra: **o fio sai dos cantos de baixo do
/// letreiro** e verga com o peso das fotos. «Isso é o que os cinemas de rua faziam
/// de verdade: o letreiro em cima, as fotos de cena na vitrine embaixo. Empilhar
/// as duas seria pôr dois enfeites na mesma tela; pendurar uma na outra é uma
/// fachada.»
///
/// ## ⚠️ Ele **anda**, e mostra as doze — não três
///
/// A primeira versão daqui pendurava `cenas.prefix(3)` paradas. O motivo escrito
/// era bom — doze prendedores em 402pt dariam 33pt por foto, miniatura de
/// miniatura — mas ele respondia a pergunta errada: **o que mudou não é o número,
/// é o varal poder ser arrastado.** Com arrasto, as outras nove deixam de precisar
/// caber ao mesmo tempo.
///
/// Então `fotosNaTela` manda no **tamanho** e parou de mandar na quantidade.
struct VaralDeCenas: View {
    let cenas: [Cena]
    let odeon: RepositorioOdeon
    let aoEscolher: (Cena) -> Void

    /// Quantas cabem de uma vez. ⚠️ E **de propósito não divide exato**: a quarta
    /// foto fica mordida na borda direita, e é ela que conta que há mais varal do
    /// que cabe. Sem esse pedaço cortado, a tela volta a parecer a fileira parada
    /// de antes e ninguém descobre o arrasto.
    private let fotosNaTela: CGFloat = 3
    private let espaco: CGFloat = 12
    /// Onde os pregos mordem a corda, contado da borda.
    ///
    /// ⚠️ **10pt porque é o raio do canto da marquise.** O letreiro tem canto de
    /// 10, então o canto de baixo dele não está em `x = 0`: está 10 pra dentro.
    /// Prego na borda seria prego pendurado no ar ao lado do letreiro, e a costura
    /// que sustenta a fachada inteira se perderia por dez pixels.
    private let recuoDoPrego: CGFloat = 10
    /// A altura da caixa toda.
    ///
    /// ## ⚠️ Ela errou duas vezes, e a segunda explicou a primeira
    ///
    /// | | o que apareceu |
    /// |---|---|
    /// | 176 | a legenda da foto mais funda saía **cortada** embaixo |
    /// | 196 | continuou cortando, **e** abriu um vão morto entre o varal e a sinopse |
    ///
    /// Aumentar não resolvia porque o corte **não era falta de altura**: a `HStack`
    /// ficava centralizada na caixa, então metade da folga ia pra cima e o
    /// deslocamento da corda empurrava a foto pra fora por baixo. Quanto maior a
    /// caixa, maior o vão em cima — e o corte, igual.
    ///
    /// Com o conteúdo preso no topo, a conta fecha e é apertada de propósito:
    /// 20 do prendedor + 98 do papel − 4 da sobreposição = 114, mais a barriga da
    /// corda, que chega a 34 com a tração. 148, com folga pro giro.
    private let alturaDoVaral: CGFloat = 162

    @State private var deslocamento: CGFloat = 0
    /// A tração na corda: quanto o dedo está puxando, com sinal, entre −1 e 1.
    ///
    /// ⚠️ **Filtrada, e não lida crua.** O delta por quadro pula muito — um quadro
    /// perdido vira pico, e o pico faria a barriga da corda dar um tranco. A média
    /// corrida de 0,6/0,4 é o mínimo que tira o tranco sem atrasar a resposta a
    /// ponto de a corda parecer molenga.
    @State private var tracao: CGFloat = 0

    var body: some View {
        /// ⚠️ **Sem cenas, sem varal** — nem fio, nem prendedor, nem espaço vazio.
        /// §24 e §53 juntos: um fio pendurado sem fotos prometeria uma navegação
        /// que não existe, e arrastar um varal que não sai do lugar é pior que não
        /// poder arrastar.
        if cenas.count >= 3 {
            GeometryReader { g in
                let largura = (g.size.width - 2 * espaco) / fotosNaTela
                ZStack(alignment: .topLeading) {
                    corda(em: g.size.width)

                    ScrollView(.horizontal, showsIndicators: false) {
                        HStack(alignment: .top, spacing: espaco) {
                            ForEach(Array(cenas.enumerated()), id: \.element.id) { i, cena in
                                Button { aoEscolher(cena) } label: {
                                    polaroide(cena, indice: i, largura: largura)
                                }
                                .buttonStyle(.plain)
                                /// ⚠️ Cada foto **desce até a corda no x dela**: a
                                /// do meio pende mais que as das pontas, e todas
                                /// sobem e descem enquanto o varal anda. É isso que
                                /// separa «penduradas» de «alinhadas».
                                .offset(y: alturaDaCorda(
                                    emX: espaco + CGFloat(i) * (largura + espaco)
                                        + largura / 2 - deslocamento,
                                    largura: g.size.width,
                                ))
                            }
                        }
                        .padding(.horizontal, espaco)
                        /// ⚠️ **Preso no topo.** Sem isto a `HStack` se centraliza
                        /// na caixa, e o `offset` da corda — que só empurra pra
                        /// baixo — leva a foto pra fora do recorte da rolagem.
                        .frame(maxHeight: .infinity, alignment: .top)
                    }
                    .onScrollGeometryChange(for: CGFloat.self) { $0.contentOffset.x } action: { velho, novo in
                        deslocamento = novo
                        /// 40pt por quadro é o que um arrasto firme faz; acima
                        /// disso já é arremesso, e a corda satura em vez de virar
                        /// borracha.
                        let alvo = min(max((novo - velho) / 40, -1), 1)
                        tracao = tracao * 0.6 + alvo * 0.4
                    }
                    .onScrollPhaseChange { _, fase in
                        /// Soltou o dedo, a corda volta — com mola, porque corda
                        /// esticada que para seca não é corda.
                        if fase == .idle {
                            withAnimation(.spring(response: 0.55, dampingFraction: 0.35)) {
                                tracao = 0
                            }
                        }
                    }
                }
            }
            .frame(height: alturaDoVaral)
        }
    }

    /// A altura da corda num `x` da tela — a Bézier que o `Canvas` desenha.
    ///
    /// ⚠️ Ela e o desenho usam **os mesmos números**, e é o que faz a foto tocar a
    /// corda em vez de flutuar perto dela. Duas contas separadas divergiriam no
    /// primeiro ajuste de flecha.
    private func alturaDaCorda(emX x: CGFloat, largura: CGFloat) -> CGFloat {
        let x0 = recuoDoPrego, x1 = largura - recuoDoPrego
        guard x1 > x0 else { return 0 }
        let t = min(max((x - x0) / (x1 - x0), 0), 1)
        /// ⚠️ A tração **afunda** a corda: puxar corda esticada é ver ela ceder.
        let controle = 58 + abs(tracao) * 10
        /// Bézier quadrática com as duas pontas em y = 0.
        return 2 * (1 - t) * t * controle
    }

    /// A corda, e os dois pregos.
    ///
    /// ⚠️ **A barriga escorrega contra o arrasto**: puxou pra esquerda, ela fica
    /// pra trás. É o que uma corda com peso faz, e é o que separa «a corda
    /// respondeu» de «a corda é um desenho parado atrás de fotos que andam».
    private func corda(em largura: CGFloat) -> some View {
        Canvas { contexto, _ in
            let x0 = recuoDoPrego, x1 = largura - recuoDoPrego, y: CGFloat = 6
            var caminho = Path()
            caminho.move(to: CGPoint(x: x0, y: y))
            caminho.addQuadCurve(
                to: CGPoint(x: x1, y: y),
                control: CGPoint(x: largura / 2 - tracao * 40, y: y + 58 + abs(tracao) * 10),
            )
            contexto.stroke(
                caminho,
                with: .color(Color(hex: 0xE8E2D4).opacity(0.75)),
                style: .init(lineWidth: 2, lineCap: .round, dash: [3, 2]),
            )
            /// Os pregos: os dois pontos por onde a corda está presa no letreiro.
            for x in [x0, x1] {
                contexto.fill(
                    Path(ellipseIn: CGRect(x: x - 2.5, y: y - 2.5, width: 5, height: 5)),
                    with: .color(Cores.destaqueApagado),
                )
            }
        }
        .frame(height: 80)
        .allowsHitTesting(false)
    }

    private func polaroide(_ cena: Cena, indice: Int, largura: CGFloat) -> some View {
        /// ⚠️ Os ângulos são **fixos por posição**, não sorteados: a mesma ficha
        /// aberta duas vezes tem o mesmo varal. Sorteio faria as fotos dançarem a
        /// cada redesenho — a mesma regra das etiquetas da locadora.
        let angulos: [Double] = [-3.5, 1.5, 2.5, -2, 3, -1]

        return VStack(spacing: 0) {
            /// O prendedor de madeira, mordendo a borda de cima do papel.
            RoundedRectangle(cornerRadius: 1.5)
                .fill(Color(hex: 0xC9A063))
                .frame(width: 13, height: 20)
                .zIndex(1)

            VStack(spacing: 6) {
                /// ⚠️ A foto vai em `background`: `scaledToFill` numa imagem
                /// carregada reivindica a largura **do arquivo**, e a coluna da
                /// ficha inteira adotaria isso. Ver o comentário grande na
                /// `fachada` da `TelaDaObra` — este defeito já apareceu na tela.
                Color.clear
                    .frame(height: (largura - 12) * 9 / 16)
                    .background {
                        ZStack {
                            Rectangle().fill(Cores.fundoElevado)
                            ArteDoOdeon(odeon: odeon, caminho: cena.imagem)
                        }
                    }
                    .clipped()

                /// ⚠️ A tarja branca de baixo é **mais alta que as outras três** —
                /// é o que faz um retângulo branco ser uma Polaroid. Margens iguais
                /// dariam uma moldura, que é outra coisa.
                Text(relogioDaSessao(cena.segundos))
                    .font(Tipo.rotulo(9))
                    .tracking(1.6)
                    .foregroundStyle(Cores.tintaDoPapel)
                    .frame(height: 22)
            }
            /// ⚠️ A largura é do **papel inteiro**, margem incluída: pondo o
            /// `frame` depois do `padding`, cada foto media `largura + 12` e a
            /// conta de «quantas cabem» deixava de fechar.
            .padding(.horizontal, 6)
            .padding(.top, 6)
            .frame(width: largura)
            .background(Cores.papel)
            .offset(y: -4)
        }
        .rotationEffect(.degrees(angulos[indice % angulos.count]), anchor: .top)
        .shadow(color: .black.opacity(0.55), radius: 5, y: 4)
    }
}

/// O bilhete da sessão — o botão de tocar, em forma de ingresso.
///
/// ## ⚠️ Ele é um **objeto**, e é o argumento do redesenho inteiro
///
/// O §1.3 diz que o que separava o app da web não era cor nem fonte, era objeto:
/// «um catálogo de arquivos lista linhas; uma locadora tem caixas que se pegam».
/// Aqui a mesma frase vira botão: começar um filme não é confirmar um formulário,
/// é **entrar na sessão**. O picote e as duas meias-luas nas laterais são o que
/// fazem o retângulo dourado ser um ingresso.
struct BilheteDaSessao: View {
    let rotulo: String
    let frase: String
    let aoTocar: () -> Void

    var body: some View {
        Button(action: aoTocar) {
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 4) {
                    Text(rotulo.uppercased())
                        .font(Tipo.rotulo(10))
                        .tracking(2.6)
                        .foregroundStyle(Cores.fundo.opacity(0.65))
                    Text(frase)
                        .font(Tipo.letreiro(21))
                        .foregroundStyle(Cores.fundo)
                }
                Spacer(minLength: 12)

                /// O picote: a linha tracejada por onde o bilhete se rasga.
                Rectangle()
                    .fill(Cores.fundo.opacity(0.35))
                    .frame(width: 1)
                    .frame(maxHeight: .infinity)
                    .overlay(
                        Rectangle().fill(Color.clear)
                            .background(
                                Rectangle().stroke(style: .init(lineWidth: 1, dash: [4, 4]))
                                    .foregroundStyle(Cores.fundo.opacity(0.35)),
                            ),
                    )
                    .padding(.vertical, 6)

                Image(systemName: "play.fill")
                    .font(.system(size: 20))
                    .foregroundStyle(Cores.fundo)
                    .frame(width: 56)
            }
            .padding(.horizontal, 18).padding(.vertical, 16)
            .frame(maxWidth: .infinity)
            .background {
                LinearGradient(
                    colors: [Color(hex: 0xF0CE86), Cores.destaque],
                    startPoint: .topLeading, endPoint: .bottomTrailing,
                )
            }
            .clipShape(.rect(cornerRadius: 8))
            /// As duas meias-luas: as mordidas do picotador nas bordas.
            .overlay(alignment: .top) { mordida }
            .overlay(alignment: .bottom) { mordida }
        }
        .buttonStyle(.plain)
    }

    private var mordida: some View {
        Circle()
            .fill(Cores.fundo)
            .frame(width: 16, height: 16)
            .offset(x: 46)
            .frame(maxWidth: .infinity, alignment: .trailing)
            .offset(y: 0)
            .allowsHitTesting(false)
    }
}

/// Uma etiqueta da obra: `genre` apagado, `Ação` em negrito.
struct PilulaDeEtiqueta: View {
    let etiqueta: EtiquetaDaObra

    var body: some View {
        HStack(spacing: 6) {
            /// ⚠️ O qualificador **some** quando não se sabe traduzi-lo, em vez de
            /// virar a chave crua do banco. Ver `EtiquetaDaObra.rotulo`.
            if let rotulo = etiqueta.rotulo {
                Text(rotulo)
                    .font(.system(size: 13))
                    .foregroundStyle(Cores.textoApagado)
            }
            Text(etiqueta.value)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Cores.texto)
        }
        .padding(.horizontal, 12).padding(.vertical, 7)
        .overlay(Capsule().stroke(Cores.textoApagado.opacity(0.35), lineWidth: 1))
    }
}
