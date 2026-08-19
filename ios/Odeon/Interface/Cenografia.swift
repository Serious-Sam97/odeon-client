import SwiftUI

/// A cenografia da locadora — «A loja da esquina, 21h».
///
/// ## De onde este arquivo vem
///
/// Do `cenario/…/Cenografia.kt` do Android, e a origem dele é o dono: pediu «o
/// maior feel possível de locadora, aquela nostalgia», olhou vinte conceitos e
/// escolheu um. **As prateleiras ficam** — «com o celular não tem como fugir das
/// prateleiras» — e o resto da tela vira **matéria**: madeira, papel, luz.
///
/// | | |
/// |---|---|
/// | a **arandela** | a luz quente que acende o título no topo |
/// | as **etiquetas penduradas** | as contagens da porta, em papel por barbante |
/// | a **plaquinha** | o nome da estante, colado na madeira que ele nomeia |
/// | a **tábua** | a prateleira onde as caixas repousam |
///
/// ⚠️ Ela existe porque a comparação com o emulador do Android, em 15/08/2026,
/// mostrou que o iOS tinha a informação certa e **nenhuma matéria**: um rótulo de
/// seção e caixas sobre um filete de 2pt. É a F5 do plano, que estava `◐`.
///
/// ## A régua
///
/// Papel é `Cores.papel` e tinta é tinta de papel. A cenografia usa material que
/// o app já tem — é o que faz a locadora parecer do mesmo prédio que o cinema.
enum Cenografia {}

/// A arandela: a meia-cúpula de latão e o facho que ela joga na parede.
///
/// ⚠️ O título embaixo dela **não tem luz própria** — quem brilha é a lâmpada, e
/// o texto está *na* luz. Por isso os dois andam juntos e nesta ordem.
struct Arandela: View {
    var body: some View {
        Canvas { contexto, tamanho in
            let cx = tamanho.width / 2
            let raio = tamanho.width * 0.38

            /// O facho, primeiro — a cúpula é desenhada por cima da nascente dele.
            ///
            /// ⚠️ **Em círculo, e não em retângulo.** O Android registra a foto
            /// que provou isso: um `drawRect` com pincel radial vira uma **tarja**
            /// — o gradiente morre fora do quadro e o recorte deixa duas arestas
            /// retas atravessando o topo. O círculo é do tamanho da própria luz, e
            /// o que não é luz não é pintado.
            let centro = CGPoint(x: cx, y: 30)
            contexto.fill(
                Path(ellipseIn: CGRect(x: centro.x - raio, y: centro.y - raio,
                                       width: raio * 2, height: raio * 2)),
                with: .radialGradient(
                    Gradient(colors: [
                        Cores.destaqueQuente.opacity(0.28),
                        Cores.destaqueQuente.opacity(0.07),
                        .clear,
                    ]),
                    center: centro, startRadius: 0, endRadius: raio,
                ),
            )

            /// A meia-cúpula: um arco de latão com a boca pra baixo.
            var cupula = Path()
            cupula.addArc(center: CGPoint(x: cx, y: 30), radius: 56,
                          startAngle: .degrees(180), endAngle: .degrees(360),
                          clockwise: false)
            cupula.closeSubpath()
            contexto.fill(
                cupula,
                with: .linearGradient(
                    Gradient(colors: [Color(hex: 0x8A6A3A), Color(hex: 0x5A4326)]),
                    startPoint: CGPoint(x: cx, y: 4), endPoint: CGPoint(x: cx, y: 30),
                ),
            )

            /// O fio de luz na boca da cúpula — é ele que diz «acesa».
            var fio = Path()
            fio.move(to: CGPoint(x: cx - 52, y: 30))
            fio.addLine(to: CGPoint(x: cx + 52, y: 30))
            contexto.stroke(fio, with: .color(Cores.destaqueQuente.opacity(0.85)), lineWidth: 2.5)
        }
        .frame(height: 46)
        .allowsHitTesting(false)
    }
}

/// Uma etiqueta de papel pendurada por barbante.
///
/// ⚠️ O torto é **fixo por etiqueta** e vem de fora. Ângulo sorteado mudaria a
/// cada redesenho e a etiqueta tremeria pendurada — a mesma regra que o Android
/// aplica ao varal.
struct EtiquetaPendurada: View {
    let numero: String
    let rotulo: String
    let angulo: Double

    var body: some View {
        VStack(spacing: 0) {
            /// O barbante: um traço fino descendo até o papel.
            Rectangle()
                .fill(Color(hex: 0xCDB98A).opacity(0.7))
                .frame(width: 1.5, height: 16)

            HStack(alignment: .lastTextBaseline, spacing: 5) {
                Text(numero)
                    .font(Tipo.letreiro(19).weight(.bold))
                    .foregroundStyle(Cores.tintaDoBilhete)
                /// ⚠️ O rótulo é **manuscrito**, e o número não: o número é o
                /// dado, a palavra é a mão de quem escreveu o papelzinho. Duas
                /// letras diferentes no mesmo papel é o que faz parecer escrito, e
                /// não impresso.
                Text(rotulo)
                    .font(.custom("SnellRoundhand-Bold", size: 14))
                    .foregroundStyle(Cores.tintaDoPapel)
            }
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(Cores.papel, in: .rect(cornerRadius: 2))
            /// O ilhós: o furinho de metal por onde passa o barbante.
            .overlay(alignment: .top) {
                Circle()
                    .fill(Color(hex: 0x3A2C18))
                    .frame(width: 6, height: 6)
                    .overlay(Circle().stroke(Cores.papel, lineWidth: 1.5))
                    .offset(y: -3)
            }
            .rotationEffect(.degrees(angulo))
            .shadow(color: .black.opacity(0.5), radius: 4, y: 3)
        }
    }
}

/// A plaquinha de papel colada na madeira, com o nome da estante.
///
/// ⚠️ Ela é **colada**, e a fita adesiva é o que diz isso: sem a tirinha por cima
/// ela lê como um rótulo desenhado na madeira, que é outra coisa. E cada estante
/// tem sua cor de papel — é assim que se acha «Terror» de relance numa loja.
struct PlaquinhaDaEstante: View {
    let nome: String
    let cor: Color

    var body: some View {
        Text(nome)
            .font(.custom("SnellRoundhand-Bold", size: 19))
            .foregroundStyle(Cores.tintaDoBilhete)
            .padding(.horizontal, 14).padding(.vertical, 5)
            .background(cor)
            .overlay(alignment: .top) {
                /// A fita adesiva: um retângulo translúcido meio torto por cima
                /// da borda de cima do papel.
                Rectangle()
                    .fill(.white.opacity(0.22))
                    .frame(width: 34, height: 15)
                    .rotationEffect(.degrees(-4))
                    .offset(y: -6)
            }
            .rotationEffect(.degrees(-1.5))
            .shadow(color: .black.opacity(0.45), radius: 3, y: 2)
    }
}

/// A tábua da prateleira.
///
/// ## ⚠️ Ela encosta na base das caixas, e o vão é o defeito
///
/// A web já sabia, e o Android copiou a frase: «a tábua encosta na base das
/// caixas. Com folga embaixo o conjunto lê como cartão, não como objeto».
///
/// E ela **sangra pras laterais** de propósito: prateleira de loja não acaba onde
/// acaba a fileira — ela atravessa a parede. Uma tábua com margem nos dois lados
/// vira um móvel solto no meio do nada.
///
/// ⚠️ O halo é a luz da loja batendo na madeira. Sem ele a tábua é uma tarja
/// marrom; com ele é uma **superfície iluminada** — o mesmo argumento da
/// arandela, aplicado a um lugar em vez de a um objeto.
struct TabuaDaPrateleira: View {
    var body: some View {
        ZStack(alignment: .top) {
            LinearGradient(
                colors: [Cores.madeira, Cores.madeiraFunda],
                startPoint: .top, endPoint: .bottom,
            )
            /// A quina de cima, onde a luz pega primeiro.
            LinearGradient(
                colors: [Cores.destaqueQuente.opacity(0.30), .clear],
                startPoint: .top, endPoint: .bottom,
            )
            .frame(height: 8)
            .blur(radius: 3)
        }
        .frame(height: 16)
        .shadow(color: .black.opacity(0.6), radius: 6, y: 3)
    }
}

/// A nota do caixa — o resumo da loja, impresso, no fim da rolagem.
///
/// ## ⚠️ Por que ela mora no fim
///
/// O resumo de quem-está-com-o-quê ocupava o topo no Android, **antes** de a
/// pessoa ver a loja. No desenho aprovado ele virou o fechamento: você anda pelas
/// estantes e, na saída, o caixa te entrega a notinha — acervo, as pessoas, seu
/// limite, o prazo da casa. É o mesmo dado com a ordem de uma visita de verdade.
struct NotaDoCaixa: View {
    let prateleira: Prateleira
    let noAcervo: Int

    private let tinta = Cores.tintaDoPapel
    private func mono(_ tamanho: CGFloat, _ peso: Font.Weight = .bold) -> Font {
        .system(size: tamanho, weight: peso, design: .monospaced)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            Text("LOCADORA ODEON")
                .font(mono(16)).tracking(2.2).foregroundStyle(tinta)
                .frame(maxWidth: .infinity)
            Text("— acervo da casa —")
                .font(mono(8.5, .regular)).foregroundStyle(tinta.opacity(0.7))
                .frame(maxWidth: .infinity)

            tracejado
            if noAcervo > 0 { linha("NO ACERVO", noAcervo.comMilhar) }
            if let prazo = prateleira.opcoes?.prazoEmDias, prazo > 0 {
                linha("PRAZO DA CASA", "\(prazo) DIAS")
            }

            /// ⚠️ Só quem tem fita ou fama — «quem não tem nada não é notícia».
            let gente = prateleira.pessoas.filter(\.temOQueDizer)
            if !gente.isEmpty {
                tracejado
                ForEach(gente) { pessoa in
                    linha(pessoa.nome, [
                        pessoa.naMao > 0 ? "\(pessoa.naMao) fora" : nil,
                        pessoa.noMeio > 0 ? "\(pessoa.noMeio) no meio" : nil,
                        pessoa.zoadas > 0 ? "✕\(pessoa.zoadas)" : nil,
                        pessoa.rebobinou > 0 ? "⟲\(pessoa.rebobinou)" : nil,
                    ].compactMap { $0 }.joined(separator: " · "))
                }
            }

            tracejado
            /// ⚠️ No limite a frase **vem com a saída junto**: «não dá» sem dizer o
            /// que fazer é o §8b na forma mais fria.
            if prateleira.possoPegar > 0 {
                linha("VOCÊ PODE PEGAR", "+\(prateleira.possoPegar)")
            } else {
                linha("VOCÊ ESTÁ NO LIMITE", "")
                Text("devolva uma pra pegar outra")
                    .font(mono(9, .regular)).foregroundStyle(tinta.opacity(0.8))
            }

            /// O carimbo, torto como todo carimbo.
            Text("VOLTE SEMPRE")
                .font(mono(11)).tracking(0.9)
                .foregroundStyle(Color(hex: 0xB22C2C).opacity(0.8))
                .padding(.horizontal, 10).padding(.vertical, 4)
                .overlay(
                    RoundedRectangle(cornerRadius: 5)
                        .stroke(Color(hex: 0xB22C2C).opacity(0.75), lineWidth: 2.5),
                )
                .rotationEffect(.degrees(-8))
                .frame(maxWidth: .infinity, alignment: .trailing)
                .padding(.top, 10).padding(.trailing, 4)
        }
        .frame(width: 300, alignment: .leading)
        .padding(.horizontal, 18).padding(.vertical, 16)
        .background {
            /// ⚠️ A **serrilha** é feita de círculos da cor do papel — recortes, e
            /// não dentes pintados. Pintados por cima, eles ficariam da cor errada
            /// no dia em que o fundo mudar.
            VStack(spacing: 0) {
                Cores.papel
                HStack(spacing: 6) {
                    ForEach(0 ..< 19, id: \.self) { _ in
                        Circle().fill(Cores.papel).frame(width: 10, height: 10)
                    }
                }
                .frame(height: 10)
                .offset(y: -5)
            }
        }
    }

    private func linha(_ esquerda: String, _ direita: String) -> some View {
        HStack {
            Text(esquerda).font(mono(11)).foregroundStyle(tinta)
            Spacer(minLength: 8)
            Text(direita).font(mono(11)).foregroundStyle(tinta)
        }
    }

    private var tracejado: some View {
        Path { p in
            p.move(to: .zero)
            p.addLine(to: CGPoint(x: 264, y: 0))
        }
        .stroke(Color(hex: 0xB8AC8A), style: StrokeStyle(lineWidth: 1.5, dash: [6, 4]))
        .frame(height: 2)
    }
}
