import SwiftUI

/// Uma caixa de VHS — capa, lombada, e o dedo girando.
///
/// ## Por que ela existe, e por que ela é o produto
///
/// O diagnóstico do redesenho (`REDESENHO.md` §1.3) diz que o que separava o app
/// da web não era cor nem fonte, era **objeto**: «a web desenha coisas, não
/// registros — a caixa de VHS tem **lombada**, e fica de pé na prateleira». E
/// fecha: *um catálogo de arquivos lista linhas; uma locadora tem caixas que se
/// pegam.*
///
/// ## ⚠️ Uma câmera para as duas faces — a lição que custou caro no Android
///
/// O `Projecao.kt` do Android registra o defeito que o antecedeu: a caixa era
/// montada com **uma transformação por face**, cada uma com o próprio ângulo e o
/// próprio ponto de fuga. O resultado é que «como as camadas não dividem o mesmo
/// ponto de fuga, a junta só fecha **na pose de repouso**» — e por isso a caixa
/// de lá nasceu **imóvel**, com pose fixa, até alguém consertar a projeção.
///
/// Aqui as duas faces giram em torno do **mesmo eixo**, com a mesma perspectiva:
/// a lombada é posicionada na borda e recebe o mesmo `rotation3DEffect`, com
/// `anchor` na dobra. É o que deixa o dedo girar sem a junta abrir.
///
/// ⚠️ E o ângulo tem **limite**. Passando de ~55° a lombada viraria a face
/// principal e a capa desapareceria — a caixa deixaria de ser uma caixa vista de
/// frente e viraria um objeto de lado, que não é o gesto de quem folheia estante.
struct CaixaDeVHS<Capa: View>: View {
    let largura: CGFloat
    /// A espessura da caixa. Uma fita VHS tem ~25mm numa capa de ~190mm — a
    /// proporção sai daí, não de gosto.
    var lombada: CGFloat { largura * 0.13 }
    let cor: Color
    let titulo: String
    @ViewBuilder let capa: Capa

    /// Quanto o dedo já girou. ⚠️ Ela **não** volta sozinha ao soltar: quem
    /// girou a caixa quer olhá-la. Voltar sozinho seria a caixa discordando do
    /// gesto.
    @State private var angulo: Double = 18

    private let limite: Double = 55

    var body: some View {
        ZStack(alignment: .leading) {
            /// A lombada, atrás e à esquerda. Ela gira com a capa, no mesmo eixo
            /// e com a mesma perspectiva — ver o cabeçalho.
            lombadaDaCaixa
                .frame(width: lombada, height: largura * 1.5)
                .rotation3DEffect(
                    .degrees(angulo - 90),
                    axis: (x: 0, y: 1, z: 0),
                    anchor: .trailing,
                    perspective: 0.6,
                )
                .offset(x: -lombada)

            capa
                .frame(width: largura, height: largura * 1.5)
                .clipShape(.rect(cornerRadius: 2))
                .rotation3DEffect(
                    .degrees(angulo),
                    axis: (x: 0, y: 1, z: 0),
                    anchor: .leading,
                    perspective: 0.6,
                )
        }
        .frame(width: largura, height: largura * 1.5)
        /// ⚠️ A sombra é **caída pra baixo**, não em volta: caixa pousada na
        /// prateleira, não flutuando. Objeto que flutua vira cartão.
        .shadow(color: .black.opacity(0.6), radius: 8, x: 4, y: 8)
        .contentShape(.rect)
        .gesture(
            DragGesture()
                .onChanged { gesto in
                    /// ⚠️ O dedo anda **mais** que o ângulo, de propósito: 1° por
                    /// ponto faria a caixa dar meia-volta num arrasto curto, e o
                    /// gesto viraria giro em vez de exame.
                    let novo = angulo + gesto.translation.width * 0.35
                    angulo = min(max(novo, -limite), limite)
                },
        )
        .animation(.interactiveSpring(duration: 0.25), value: angulo)
        .accessibilityLabel(titulo)
    }

    private var lombadaDaCaixa: some View {
        ZStack {
            /// A lombada é a **mesma cor da obra**, escurecida — é a mesma caixa,
            /// vista de canto, e não uma peça de outra cor.
            ///
            /// ⚠️ Cor do servidor, nunca sorteada (§18).
            LinearGradient(
                colors: [cor.opacity(0.95), cor.opacity(0.55)],
                startPoint: .leading, endPoint: .trailing,
            )
            .overlay(Color.black.opacity(0.35))

            /// O título impresso na lombada, de lado — é o que se lê numa estante
            /// com as caixas de pé.
            Text(titulo)
                .font(.system(size: max(7, largura * 0.055), weight: .semibold))
                .foregroundStyle(.white.opacity(0.85))
                .lineLimit(1)
                .frame(width: largura * 1.4)
                .rotationEffect(.degrees(-90))
                .fixedSize()
        }
        .clipShape(.rect(cornerRadius: 1))
    }
}
