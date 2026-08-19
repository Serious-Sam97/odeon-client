import SwiftUI

/// O disco e a fita — a mídia que sai de dentro da caixa.
///
/// ## ⚠️ Eles são **desenhados**, e não imagens
///
/// É a régua de «zero bytes» da casa, a mesma que rendeu a marquise e a
/// arandela: um disco é um círculo com anéis e um reflexo; uma fita é um
/// retângulo com dois carretéis. Desenhados, eles escalam em qualquer tamanho e
/// não precisam de arte que alguém tenha de produzir pra 17.930 obras.
///
/// E, ao contrário do pôster, eles são **iguais pra todo mundo** — um DVD é um
/// DVD. O que muda de obra pra obra é a cor que o disco reflete, e ela sai da
/// `dominant_color` que o servidor já extraiu.
enum Midia {}

/// O disco, visto de frente e inclinado pela pose.
///
/// ## A inclinação é o que faz ele parecer disco
///
/// Um círculo chapado é uma moeda de desenho animado. O que o olho reconhece
/// como disco é a **elipse** — o círculo visto de esguelha — mais o brilho que
/// corre pela superfície. A elipse vem de achatar a altura pelo cosseno da
/// inclinação, que é a projeção de um círculo num plano girado.
struct Disco: View {
    let tamanho: CGFloat
    let cor: Color
    let pose: Pose
    /// A arte do rótulo. ⚠️ **Disco prensado tem a arte impressa de furo a
    /// borda** — o prateado genérico é um DVD-R queimado em casa, e uma locadora
    /// não aluga DVD-R. `nil` degrada pro prateado, que é a verdade nos 48% do
    /// acervo sem pôster (§24).
    let capa: String?
    let odeon: RepositorioOdeon

    /// ⚠️ O achatamento tem **piso**: de perfil o disco viraria uma linha e
    /// sumiria no meio do gesto, o que parece defeito.
    private var achatado: CGFloat { min(max(cos(pose.giroX * .pi / 180), 0.35), 1) }

    /// ⚠️ Ele acompanha o giro da caixa **por um terço**: está solto no estojo,
    /// não parafusado na tampa.
    private var giroNaTela: CGFloat { pose.giroY * 0.35 }

    var body: some View {
        ZStack {
            /// ⚠️ A arte gira **com o disco**; o verniz, não — reflexo é da luz da
            /// sala, e a luz não roda junto com o rótulo.
            if capa != nil {
                ArteDoOdeon(odeon: odeon, caminho: capa)
                    .frame(width: tamanho * 0.96, height: tamanho * 0.96)
                    .clipShape(.circle)
                    .rotationEffect(.degrees(giroNaTela))
            }

            Canvas { contexto, size in
                let centro = CGPoint(x: size.width / 2, y: size.height / 2)
                let raio = min(size.width, size.height) / 2
                desenhar(contexto, centro: centro, raio: raio, temArte: capa != nil)
                verniz(contexto, centro: centro, raio: raio)
            }
        }
        .frame(width: tamanho, height: tamanho)
        /// ⚠️ O achatamento é do **conjunto**, e não oval por oval: achatando cada
        /// anel, a arte sairia redonda sobre anéis achatados. A elipse tem de ser
        /// do disco inteiro.
        .scaleEffect(x: 1, y: achatado)
    }

    private func desenhar(_ contexto: GraphicsContext, centro: CGPoint,
                          raio: CGFloat, temArte: Bool) {
        func circulo(_ r: CGFloat) -> Path {
            Path(ellipseIn: CGRect(x: centro.x - r, y: centro.y - r, width: r * 2, height: r * 2))
        }

        if !temArte {
            /// ⚠️ A cor da obra entra **na reflexão**, não no plástico: «a cor da
            /// obra só toca arte».
            contexto.fill(circulo(raio), with: .linearGradient(
                Gradient(stops: [
                    .init(color: Color(hex: 0xB9BDC7), location: 0),
                    .init(color: Color(hex: 0xEDEFF3), location: 0.35),
                    .init(color: cor.opacity(0.55), location: 0.5),
                    .init(color: Color(hex: 0xEDEFF3), location: 0.65),
                    .init(color: Color(hex: 0x8E939D), location: 1),
                ]),
                startPoint: CGPoint(x: centro.x - raio, y: centro.y - raio),
                endPoint: CGPoint(x: centro.x + raio, y: centro.y + raio),
            ))
        }

        /// A borda de leitura: o aro de policarbonato **sem impressão**, com o
        /// vinco escuro onde os dados acabam. É o que separa «círculo com imagem»
        /// de «disco prensado» — todo prensado tem esse aro nu.
        contexto.stroke(circulo(raio * 0.98),
                        with: .color(Color(hex: 0xC9CDD6).opacity(temArte ? 0.9 : 0.5)),
                        lineWidth: raio * 0.05)
        contexto.stroke(circulo(raio * 0.945),
                        with: .color(.black.opacity(0.22)), lineWidth: raio * 0.018)

        /// O cubo: o anel-espelho em volta do furo — a parte prateada que sobra em
        /// qualquer rótulo —, o plástico translúcido e o furo.
        contexto.fill(circulo(raio * 0.34), with: .conicGradient(
            Gradient(colors: [
                Color(hex: 0xD8DCE4), Color(hex: 0xAEB3BE), Color(hex: 0xEDEFF3),
                Color(hex: 0xB9BDC7), Color(hex: 0xD8DCE4),
            ]), center: centro, angle: .zero,
        ))
        contexto.fill(circulo(raio * 0.22), with: .color(Color(hex: 0xE4E7ED)))
        contexto.fill(circulo(raio * 0.13), with: .color(Cores.fundo))
        contexto.stroke(circulo(raio * 0.135),
                        with: .color(.white.opacity(0.35)), lineWidth: raio * 0.015)
    }

    /// A luz sobre o policarbonato.
    ///
    /// ⚠️ Separada do disco porque **não gira com ele**: reflexo é da lâmpada da
    /// sala, e a lâmpada fica parada enquanto o rótulo roda.
    private func verniz(_ contexto: GraphicsContext, centro: CGPoint, raio: CGFloat) {
        contexto.fill(
            Path(ellipseIn: CGRect(x: centro.x - raio, y: centro.y - raio,
                                   width: raio * 2, height: raio * 2)),
            with: .conicGradient(
                Gradient(stops: [
                    .init(color: .clear, location: 0),
                    .init(color: .white.opacity(0.30), location: 0.12),
                    .init(color: .white.opacity(0.05), location: 0.22),
                    .init(color: .clear, location: 0.34),
                    .init(color: .clear, location: 1),
                ]), center: centro, angle: .degrees(-40),
            ),
        )
    }
}

/// A fita VHS.
struct FitaVHS: View {
    let largura: CGFloat
    let titulo: String?

    var body: some View {
        Canvas { contexto, size in
            let r = size.height * 0.27
            let esquerda = CGPoint(x: size.width * 0.31, y: size.height * 0.46)
            let direita = CGPoint(x: size.width * 0.69, y: size.height * 0.46)

            /// A carcaça, com o topo mais claro — plástico fosco pega luz de cima.
            contexto.fill(
                Path(roundedRect: CGRect(origin: .zero, size: size),
                     cornerRadius: size.height * 0.06),
                with: .linearGradient(
                    Gradient(colors: [Color(hex: 0x2A2A2E), Color(hex: 0x141416)]),
                    startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height),
                ),
            )

            /// Os frisos de pega — as linhas em relevo que toda carcaça tem nas
            /// beiradas. Detalhe barato que tira o «retângulo liso» da fita.
            for x in [0.045, 0.075, 0.925, 0.955] as [CGFloat] {
                var friso = Path()
                friso.move(to: CGPoint(x: size.width * x, y: size.height * 0.12))
                friso.addLine(to: CGPoint(x: size.width * x, y: size.height * 0.88))
                contexto.stroke(friso, with: .color(.white.opacity(0.05)),
                                lineWidth: size.width * 0.008)
            }

            /// ⚠️ A janela, e os rolos moram **dentro dela**: com o centro no meio
            /// da carcaça o rolo cheio vazava o vidro e invadia o rótulo. O centro
            /// é o da janela (0,46).
            contexto.fill(
                Path(roundedRect: CGRect(x: size.width * 0.12, y: size.height * 0.16,
                                         width: size.width * 0.76, height: size.height * 0.60),
                     cornerRadius: size.height * 0.04),
                with: .color(Color(hex: 0x0B0B0D)),
            )

            /// Os dois carretéis, e a fita esticada entre eles.
            for centro in [esquerda, direita] {
                contexto.fill(
                    Path(ellipseIn: CGRect(x: centro.x - r, y: centro.y - r,
                                           width: r * 2, height: r * 2)),
                    with: .color(Color(hex: 0x1A1A1E)),
                )
                contexto.stroke(
                    Path(ellipseIn: CGRect(x: centro.x - r * 0.42, y: centro.y - r * 0.42,
                                           width: r * 0.84, height: r * 0.84)),
                    with: .color(.white.opacity(0.18)), lineWidth: r * 0.10,
                )
            }
            var fita = Path()
            fita.move(to: CGPoint(x: esquerda.x, y: esquerda.y - r))
            fita.addLine(to: CGPoint(x: direita.x, y: direita.y - r))
            contexto.stroke(fita, with: .color(Color(hex: 0x3A2E24)), lineWidth: r * 0.16)

            /// ⚠️ O rótulo é **impresso**, com o nome do filme — a referência é a
            /// fita d'*O Rei Leão*, não um retângulo bege mudo.
            contexto.fill(
                Path(roundedRect: CGRect(x: size.width * 0.10, y: size.height * 0.80,
                                         width: size.width * 0.80, height: size.height * 0.14),
                     cornerRadius: 2),
                with: .color(Cores.papel),
            )
            if let titulo {
                let texto = contexto.resolve(
                    Text(titulo)
                        .font(.system(size: max(5, size.height * 0.085), weight: .semibold))
                        .foregroundStyle(Cores.tintaDoBilhete),
                )
                contexto.draw(texto, at: CGPoint(x: size.width / 2, y: size.height * 0.87))
            }
        }
        .frame(width: largura, height: largura * 0.58)
    }
}
