import SwiftUI

/// O que se vê em cada lado da caixa.
///
/// ## ⚠️ O que muda entre DVD e VHS é a **matéria**, não só a medida
///
/// | | DVD | VHS |
/// |---|---|---|
/// | casco | plástico preto de keep case | papelão tingido |
/// | lombada | fina (11pt) e brilhante | grossa (19pt) e fosca |
///
/// A arte **sangra nos dois formatos**: a moldura de plástico em volta do
/// encarte existiu no Android e saiu, porque o dono viu — «todos os DVDs estão
/// com essa borda preta na capa». Quem diferencia um do outro é a espessura, o
/// material e o selo.
struct FaceDaCaixa: View {
    let odeon: RepositorioOdeon
    let lado: Lado
    let luz: CGFloat
    let medidas: Medidas
    let ehVhs: Bool
    let titulo: String
    let cor: Color
    let capa: String?

    var body: some View {
        ZStack {
            conteudo
            /// A luz do lado, aplicada por cima de tudo. ⚠️ Um véu **preto** e não
            /// uma opacidade: escurecer a face é sombra, apagá-la é fantasma.
            Color.black.opacity(1 - luz)
        }
        .compositingGroup()
    }

    @ViewBuilder
    private var conteudo: some View {
        switch lado {
        case .capa:
            /// ⚠️ A capa vem de `CapaDaCaixa`, que carrega a imagem **fora** do
            /// SwiftUI. Ver `ArteCarregada`: um `AsyncImage` aqui dentro não
            /// chegava a baixar, e o sintoma era a cor dominante sozinha.
            Color.clear
                .background { CapaDaCaixa(odeon: odeon, caminho: capa, cor: cor, titulo: titulo) }
                .clipped()

        case .lombada:
            ZStack {
                casco
                /// O título impresso na lombada, deitado — é o que se lê numa
                /// estante com as caixas de pé.
                ///
                /// ⚠️ O corpo sai da **espessura**: no DVD (11pt) sobra pouco e a
                /// letra é miúda; no VHS (19pt) cabe quase o dobro. Um tamanho fixo
                /// faria a fita parecer um DVD gordo.
                Text(titulo)
                    .font(.system(size: max(5, medidas.espessura * 0.42), weight: .semibold))
                    .foregroundStyle(.white.opacity(0.92))
                    .lineLimit(1)
                    .frame(width: medidas.altura * 0.92)
                    .rotationEffect(.degrees(-90))
                    .fixedSize()
            }

        case .contracapa:
            ZStack {
                casco
                /// ⚠️ O verso **não repete a capa**. Ele é o que uma contracapa
                /// tem: a cor da obra escurecida e o título pequeno. Repetir o
                /// pôster nos dois lados faria a caixa parecer impressa em espelho.
                LinearGradient(colors: [cor.opacity(0.55), .black.opacity(0.85)],
                               startPoint: .top, endPoint: .bottom)
                Text(titulo)
                    .font(.system(size: 7, weight: .medium))
                    .foregroundStyle(.white.opacity(0.5))
                    .lineLimit(2)
                    .multilineTextAlignment(.center)
                    .padding(6)
                    .frame(maxHeight: .infinity, alignment: .bottom)
            }

        case .topo, .base, .lateralDireita:
            /// As três faces «de matéria»: nelas não há arte, há **plástico ou
            /// papelão**. É o que impede a caixa de parecer oca quando gira.
            casco
        }
    }

    /// O casco: preto brilhante no DVD, papelão fosco no VHS.
    private var casco: some View {
        ZStack {
            Rectangle().fill(ehVhs ? Color(hex: 0x2A2622) : Color(hex: 0x101014))
            /// ⚠️ O brilho **só no plástico**: papelão não reflete. É esse detalhe
            /// que faz o olho separar uma fita de um keep case sem ler nada.
            if !ehVhs {
                LinearGradient(
                    colors: [.white.opacity(0.16), .clear, .white.opacity(0.05)],
                    startPoint: .topLeading, endPoint: .bottomTrailing,
                )
            }
        }
    }
}
