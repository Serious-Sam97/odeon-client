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
    /// ⚠️ `nil` **na fita**, e não é economia de código: a fita não tem menu, tem
    /// rebobinar (§14.4). O que decide é o formato do objeto na mão, e por isso
    /// quem monta o palco já sabe a resposta — não há o que perguntar aqui.
    let aoPorNoAparelho: (() -> Void)?
    /// ## Levar a caixa pra casa
    ///
    /// ⚠️ **Frases prontas, e não a regra.** O palco desenha um objeto; quem sabe
    /// de empréstimo, limite e escassez é a locadora. `acaoDeLevar` nulo é botão
    /// que **não existe** — o §53 —, e quando não dá, o `avisoDaCaixa` diz por
    /// quê. Um aviso não é botão desabilitado: é uma frase.
    var acaoDeLevar: String? = nil
    var avisoDaCaixa: String? = nil
    var aoLevar: () -> Void = {}

    @State private var aberta = false

    /// ⚠️ **118°**: passa dos 90° o bastante pra ficar claro que está aberta, e
    /// para antes de encostar na lombada do outro lado.
    private var abertura: CGFloat { aberta ? 118 : 0 }

    /// A caixa na mão é **grande**: 2,2× a da prateleira. Um objeto que se pega
    /// não tem o tamanho de um objeto que se olha de longe.
    private var naMao: Medidas { medidas.vezes(2.2) }

    /// ⚠️ A dica muda com **o que dá pra fazer agora**, e é o que faz o toque no
    /// disco existir: um gesto que ninguém descobre é um gesto que não há.
    private var dica: String {
        guard aberta else { return "arraste pra girar · toque à direita pra abrir" }
        return aoPorNoAparelho == nil
            ? "toque de novo pra fechar a caixa"
            : "toque no disco pra pôr no aparelho"
    }

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
                    CaixaEm3D(
                        medidas: naMao, giravel: true, abertura: abertura,
                        /// ⚠️ O interior segue o **formato**: cubo de disco no keep
                        /// case, berço de cassete na fita. É a mesma decisão que
                        /// escolhe a espessura, e as duas têm de concordar — uma
                        /// caixa fina com berço de fita dentro seria um objeto que
                        /// não existe.
                        interiorDeDisco: !ehVhs,
                        ehEscuro: !ehVhs,
                    ) { lado, luz in
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

                /// ## A mídia, quando a caixa está aberta
                ///
                /// ⚠️ Ela aparece **embaixo** da caixa, e não dentro dela: no vão
                /// da tampa aberta a perspectiva a esmagaria, e o que se quer é o
                /// objeto que saiu — a mão tira o disco do estojo pra olhar.
                if aberta {
                    Group {
                        if ehVhs {
                            FitaVHS(largura: naMao.largura * 0.86, titulo: caixa.titulo)
                        } else {
                            /// ⚠️ **Tocar no disco é pôr o disco**, e é por isso
                            /// que ele não virou botão: o gesto já existia no
                            /// objeto. A mão tira o disco do estojo e o põe no
                            /// aparelho — um `pôr no aparelho` embaixo da caixa
                            /// seria a interface dizendo o que o disco já diz.
                            Disco(tamanho: naMao.largura * 0.72, cor: cor,
                                  pose: Pose(), capa: caixa.poster, odeon: odeon)
                                .contentShape(.circle)
                                .onTapGesture { aoPorNoAparelho?() }
                        }
                    }
                    .shadow(color: .black.opacity(0.7), radius: 12, y: 8)
                    .transition(.opacity.combined(with: .scale(scale: 0.7)))
                }

                /// ⚠️ O texto do palco é **da interface**, não do objeto: título e
                /// ano estão impressos na capa, e repeti-los aqui seria a tela
                /// falando sobre a caixa que está na mão de quem lê.
                ///
                /// Fica só o que a mão não descobre sozinha: o que dá pra **fazer**.
                VStack(spacing: 14) {
                    Text(dica)
                        .font(.system(size: 12))
                        .foregroundStyle(Cores.textoApagado)

                    /// ⚠️ «Levar pra casa» esteve fora do produto inteiro por
                    /// causa do §53 — o 403 era imprevisível. Com o `caixa_ids`
                    /// do servidor (17/08/2026) a locadora prevê, e o botão
                    /// voltou. Ele vem **antes** do «ver a ficha» porque é a
                    /// coisa que se faz com a loja; a ficha é sobre o filme.
                    if let acaoDeLevar {
                        Button(action: aoLevar) {
                            Text(acaoDeLevar)
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(Cores.destaque)
                                .frame(minHeight: 44)
                                .contentShape(.rect)
                        }
                        .buttonStyle(.plain)
                    }
                    if let avisoDaCaixa {
                        Text(avisoDaCaixa)
                            .font(.system(size: 12))
                            .foregroundStyle(Cores.textoApagado)
                    }

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
