import SwiftUI

/// A caixa em três dimensões, e **o dedo é o controle**.
///
/// ## De onde ela vem
///
/// > «no modo locadora os itens vêm em 3D? a capa em 3D, o cd, o vhs a fita
/// > etc… igual no web. usar o touch para isso vai ser muito legal.»
///
/// O que eu tinha entregue eram duas faces em pose quase fixa — exatamente o que
/// o `Projecao.kt` do Android descreve como o estado **anterior** ao conserto de
/// lá. Copiei o defeito junto com o comentário.
///
/// ## Cada lado continua sendo uma `View`
///
/// É o ganho de não ter ido pra Metal: a capa é o mesmo `AsyncImage`, com o mesmo
/// cache e o mesmo token de mídia. O que a homografia faz é desenhar esse
/// conteúdo **no plano da face**, com a perspectiva de todas as outras.
///
/// ⚠️ `projectionEffect` aceita a matriz pronta e **não** impõe câmera própria —
/// é por isso que ele serve e o `rotation3DEffect` não.
struct CaixaEm3D<Face: View>: View {
    let medidas: Medidas
    /// Se o dedo pode girar. ⚠️ **Na estante não pode**: lá o arrasto é da
    /// fileira, e disputar o gesto faria a lista não rolar. Foi decisão do
    /// Android e vale igual aqui.
    var giravel: Bool = false
    var abertura: CGFloat = 0
    /// O interior é de **disco** (cubo no fundo) ou de **fita** (berço do
    /// cassete)? ⚠️ Só importa com a caixa **aberta** — fechada, interior não
    /// existe. É o único pedaço de «arte» que a geometria carrega, porque o forro
    /// é desenhado aqui e não nas faces.
    var interiorDeDisco: Bool = true
    /// A cor do casco, pro lábio da tampa: plástico preto no disco, papelão
    /// tingido na fita. Quem sabe essa cor é quem desenha as faces.
    var ehEscuro: Bool = true
    /// A pose de fora, quando alguém quer controlá-la. `nil` deixa a caixa cuidar
    /// da própria.
    var poseControlada: Pose?
    /// Recebe o lado e **quanta luz ele pega** — a luz escurece por igual, e é o
    /// que faz a lombada ser a mesma matéria da capa, vista de canto.
    @ViewBuilder let face: (Lado, CGFloat) -> Face

    @State private var pose = Pose()
    @State private var poseAoPegar: Pose?
    /// A inércia: ao soltar, a caixa **continua girando** e para onde o atrito a
    /// deixar. Sem isso ela para na hora, e objeto que para na hora é desenho.
    @State private var inercia: Task<Void, Never>?

    /// A câmera está a **oito larguras**. Mais perto exagera a perspectiva e a
    /// caixa vira um cone; mais longe achata e o 3D some.
    private var distancia: CGFloat { medidas.largura * 8 }

    /// O quadro precisa caber a caixa **girada**, e a diagonal é o pior caso.
    private var quadro: CGSize {
        let d = sqrt(medidas.largura * medidas.largura + medidas.espessura * medidas.espessura)
        return CGSize(width: d * 1.15, height: medidas.altura * 1.08)
    }

    private var poseAtual: Pose { poseControlada ?? pose }

    var body: some View {
        let centro = CGPoint(x: quadro.width / 2, y: quadro.height / 2)

        ZStack(alignment: .topLeading) {
            /// ## O forro — o interior que a tampa aberta revela
            ///
            /// ⚠️ **Sem ele, abrir a caixa mostra um buraco.** As faces só são
            /// desenhadas de frente (o recorte de costas da projeção), o que é
            /// exato numa caixa **fechada**: ela é sólida, o interior não existe.
            /// Com a tampa a 118° o olho passa a ver por dentro, e o que há lá é o
            /// fundo do palco — a caixa aberta vira uma lombada flutuando no nada.
            /// É o «cenário de teatro visto de trás» voltando pela porta da tampa.
            ///
            /// O forro são as faces **de costas** desenhadas como quadriláteros
            /// chapados: sem homografia, sem conteúdo, só o polígono na cor do
            /// plástico interno. É o que um estojo aberto mostra de verdade — o
            /// lado de dentro não é impresso.
            ///
            /// ⚠️ Fechada, nada disto roda: o custo do forro só existe enquanto há
            /// interior pra ver.
            if abertura > 0.5 { forro(centro: centro) }

            /// ⚠️ **Da mais funda pra mais próxima** — a ordem do pintor. Sem ela a
            /// lombada some por baixo da capa em vez de encostar nela.
            ForEach(Array(ladosVisiveis(pose: poseAtual, m: medidas, abertura: abertura).enumerated()),
                    id: \.offset) { _, lado in
                let tamanho = tamanhoDoLado(lado, medidas)
                let cantos = cantosNaTela(lado, pose: poseAtual, m: medidas,
                                          distancia: distancia, abertura: abertura, centro: centro)
                if let matriz = Homografia.mapeando(largura: tamanho.width,
                                                    altura: tamanho.height, para: cantos) {
                    face(lado, luzDoLado(lado, pose: poseAtual, m: medidas, abertura: abertura))
                        .frame(width: tamanho.width, height: tamanho.height)
                        /// ## ⚠️ **Recortar antes de projetar**, e não é zelo
                        ///
                        /// A homografia mapeia **o que é desenhado**, não o quadro.
                        /// Conteúdo que transborda o `frame` é mapeado junto, e o
                        /// resultado é uma face do tamanho errado — foi o que o
                        /// título deitado da lombada fez: reportou 292pt de largura
                        /// numa face de 24, e a lombada saiu quinze vezes maior.
                        ///
                        /// O `clipped` é o que garante que a face projetada é a
                        /// face, e não o que por acaso coube dentro dela.
                        .clipped()
                        .projectionEffect(ProjectionTransform(matriz))
                }
            }
        }
        .frame(width: quadro.width, height: quadro.height)
        /// ⚠️ A sombra é **caída pra baixo**, não em volta: caixa pousada na
        /// prateleira, não flutuando. Objeto que flutua vira cartão.
        .shadow(color: .black.opacity(0.6), radius: 9, x: 3, y: 9)
        .contentShape(.rect)
        .gesture(giravel ? arrasto : nil)
    }

    /// O forro e o lábio da concha.
    private func forro(centro: CGPoint) -> some View {
        Canvas { contexto, _ in
            let deCostas = Lado.allCases
                .filter { !deFrente($0, pose: poseAtual, m: medidas, abertura: abertura) }
                .sorted { profundidade($0, pose: poseAtual, m: medidas, abertura: abertura)
                    < profundidade($1, pose: poseAtual, m: medidas, abertura: abertura) }

            for lado in deCostas {
                let pontos = cantosNaTela(lado, pose: poseAtual, m: medidas,
                                          distancia: distancia, abertura: abertura,
                                          centro: centro)
                /// ⚠️ Três tons, e eles são **a luz do palco**: a tampa por dentro
                /// é o forro mais claro — está virada pra cima, de cara pro facho,
                /// e é isso que faz a tampa aberta ser vista como tampa em vez de
                /// sumir de gume no escuro. O fundo pega a luz que entra pela
                /// abertura; as paredes ficam na própria sombra.
                let tom: Color = switch lado {
                case .capa: Color(hex: 0x26262E)
                case .contracapa: Color(hex: 0x1C1C22)
                default: Color(hex: 0x121217)
                }
                contexto.fill(quadrilatero(pontos), with: .color(tom))

                /// ## As peças do interior
                ///
                /// ⚠️ Um estojo aberto **não é liso por dentro**: o keep case tem o
                /// cubo que prende o disco no fundo; o clamshell tem o berço onde o
                /// cassete assenta. Sem elas o interior é «uma textura morta».
                ///
                /// Elas saem de interpolação bilinear dos **mesmos quatro cantos** —
                /// a perspectiva vem de graça da própria face, sem matriz nova.
                if lado == .contracapa { pecaDoInterior(pontos, em: contexto) }
            }

            /// ⚠️ **O lábio da concha** — a meia-lateral que viaja com a tampa. Sem
            /// ela só a capa gira, e o olho lê uma tampa chapada colada numa caixa
            /// maciça. Uma caixa são duas conchas presas na lombada.
            let labio = cantosDaMeiaLateral(medidas, abertura: abertura).map { canto in
                let p = projetado(girado(canto, poseAtual), distancia: distancia)
                return CGPoint(x: p.x + centro.x, y: p.y + centro.y)
            }
            contexto.fill(quadrilatero(labio),
                          with: .color(ehEscuro ? Color(hex: 0x101014) : Color(hex: 0x2A2622)))
        }
        .allowsHitTesting(false)
    }

    /// O cubo do disco, ou o berço do cassete.
    private func pecaDoInterior(_ pontos: [CGPoint], em contexto: GraphicsContext) {
        func ponto(_ u: CGFloat, _ v: CGFloat) -> CGPoint {
            let topo = CGPoint(x: pontos[0].x + (pontos[1].x - pontos[0].x) * u,
                               y: pontos[0].y + (pontos[1].y - pontos[0].y) * u)
            let base = CGPoint(x: pontos[3].x + (pontos[2].x - pontos[3].x) * u,
                               y: pontos[3].y + (pontos[2].y - pontos[3].y) * u)
            return CGPoint(x: topo.x + (base.x - topo.x) * v, y: topo.y + (base.y - topo.y) * v)
        }

        if interiorDeDisco {
            /// O cubo: o anel que prende o disco, no centro do fundo.
            var anel = Path()
            let passos = 28
            for i in 0 ... passos {
                let a = CGFloat(i) / CGFloat(passos) * 2 * .pi
                let p = ponto(0.5 + cos(a) * 0.16, 0.5 + sin(a) * 0.11)
                if i == 0 { anel.move(to: p) } else { anel.addLine(to: p) }
            }
            anel.closeSubpath()
            contexto.stroke(anel, with: .color(.white.opacity(0.14)), lineWidth: 2)
        } else {
            /// O berço: a cavidade retangular onde o cassete assenta.
            var berco = Path()
            berco.move(to: ponto(0.10, 0.14))
            berco.addLine(to: ponto(0.90, 0.14))
            berco.addLine(to: ponto(0.90, 0.86))
            berco.addLine(to: ponto(0.10, 0.86))
            berco.closeSubpath()
            contexto.fill(berco, with: .color(.black.opacity(0.35)))
            contexto.stroke(berco, with: .color(.white.opacity(0.10)), lineWidth: 1.5)
        }
    }

    private func quadrilatero(_ p: [CGPoint]) -> Path {
        var caminho = Path()
        caminho.move(to: p[0])
        for i in 1 ..< p.count { caminho.addLine(to: p[i]) }
        caminho.closeSubpath()
        return caminho
    }

    private var arrasto: some Gesture {
        DragGesture(minimumDistance: 2)
            .onChanged { g in
                inercia?.cancel()
                let base = poseAoPegar ?? poseAtual
                if poseAoPegar == nil { poseAoPegar = base }
                /// ⚠️ **Subtrai** o arrasto. Somando, «parece que a caixa vai pro
                /// lado contrário do dedo» — foi o que o dono viu no Android.
                pose = base.somando(
                    dx: -g.translation.width * Pose.grauPorPixelHorizontal,
                    dy: -g.translation.height * Pose.grauPorPixelVertical,
                    livre: true,
                )
            }
            .onEnded { g in
                poseAoPegar = nil
                /// A velocidade do gesto vira giro que decai — `predictedEnd`
                /// menos o fim real é o quanto o dedo ainda estava andando.
                let sobra = g.predictedEndTranslation.width - g.translation.width
                var velocidade = -sobra * Pose.grauPorPixelHorizontal / 12
                inercia = Task { @MainActor in
                    /// ⚠️ Para quando fica **imperceptível**, e não em zero: uma
                    /// exponencial nunca chega a zero, e o laço rodaria pra sempre
                    /// gastando bateria por um giro que ninguém vê.
                    while abs(velocidade) > 0.05 {
                        pose = pose.somando(dx: velocidade, dy: 0, livre: true)
                        velocidade *= 0.94
                        try? await Task.sleep(for: .milliseconds(16))
                        if Task.isCancelled { return }
                    }
                }
            }
    }
}
