import SwiftUI

/// O palco: a caixa **na mão**, fora da estante.
///
/// ## ⚠️ Ele fica por cima da tela inteira, e fora da rolagem
///
/// Dentro da coluna que rola, a caixa na mão subiria e desceria com a prateleira
/// — e o que se quer é o contrário: **o resto da loja para, e sobra o objeto**.
///
/// ## O que o dedo faz
///
/// | gesto | efeito |
/// |---|---|
/// | arrastar | gira a caixa **livre**, com inércia — ela fica onde parou |
/// | tocar na metade **direita** | abre a caixa |
/// | tocar no fundo | guarda a caixa e fecha o palco |
///
/// ⚠️ A abertura ser a metade direita **não é escolha de layout**: a dobradiça
/// fica do lado da lombada, e a lombada está à esquerda. Abrir pelo outro lado
/// seria abrir pela dobradiça.
///
/// ⚠️ E é aqui que o giro livre finalmente serve: na estante a caixa **não** gira
/// («lá o arrasto é da fileira»), então o `giravel` do `CaixaEm3D` nasceu sem uso.
/// O palco é o lugar onde o dono pediu «quando você move com o dedo, dá pra ver o
/// verso também».
struct PalcoDaCaixa: View {
    let odeon: RepositorioOdeon
    let caixa: CaixaExposta
    let medidas: Medidas
    let ehVhs: Bool
    let cor: Color
    let aoFechar: () -> Void
    let aoVerAFicha: () -> Void

    @State private var aberta = false

    /// ⚠️ **118°**: passa dos 90° o bastante pra ficar claro que está aberta, e
    /// para antes de encostar na lombada do outro lado.
    private var abertura: CGFloat { aberta ? 118 : 0 }

    /// A caixa na mão é **grande**: 2,2× a da prateleira. Um objeto que se pega
    /// não tem o tamanho de um objeto que se olha de longe.
    private var naMao: Medidas { medidas.vezes(2.2) }

    var body: some View {
        ZStack {
            /// O escurecimento **recebe o toque**: tocar fora é guardar a caixa —
            /// o gesto que a mão espera, largar o objeto na prateleira.
            Color.black.opacity(0.82)
                .ignoresSafeArea()
                .contentShape(.rect)
                .onTapGesture { aoFechar() }

            VStack(spacing: 26) {
                Spacer(minLength: 0)

                ZStack {
                    CaixaEm3D(medidas: naMao, giravel: true, abertura: abertura) { lado, luz in
                        FaceDaCaixa(
                            odeon: odeon, lado: lado, luz: luz, medidas: naMao,
                            ehVhs: ehVhs, titulo: caixa.titulo, cor: cor, capa: caixa.poster,
                        )
                    }

                    /// ⚠️ A metade direita abre, e é uma camada à parte porque o
                    /// arrasto é da caixa inteira: um toque que abrisse dentro do
                    /// mesmo gesto faria toda parada de giro virar abertura.
                    HStack(spacing: 0) {
                        Color.clear
                        Color.clear
                            .contentShape(.rect)
                            .onTapGesture {
                                withAnimation(.spring(response: 0.5, dampingFraction: 0.75)) {
                                    aberta.toggle()
                                }
                            }
                    }
                    .frame(width: naMao.largura, height: naMao.altura)
                }

                /// ⚠️ O texto do palco é **da interface**, não do objeto: título e
                /// ano estão impressos na capa, e repeti-los aqui seria a tela
                /// falando sobre a caixa que está na mão de quem lê.
                ///
                /// Fica só o que a mão não descobre sozinha: o que dá pra **fazer**.
                VStack(spacing: 14) {
                    Text(aberta ? "toque de novo pra fechar a caixa"
                        : "arraste pra girar · toque à direita pra abrir")
                        .font(.system(size: 12))
                        .foregroundStyle(Cores.textoApagado)

                    Button(action: aoVerAFicha) {
                        Text("ver a ficha")
                            .font(.system(size: 16, weight: .semibold))
                            .padding(.horizontal, 22).padding(.vertical, 13)
                            .background(Cores.destaque, in: .capsule)
                            .foregroundStyle(Cores.fundo)
                    }
                    .buttonStyle(.plain)
                }
                Spacer(minLength: 0)
            }
            .padding(.bottom, 40)
        }
    }
}
