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
