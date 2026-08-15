import SwiftUI

/// O rótulo de seção — versalete espaçado, linha até a margem, e o número.
///
/// ## Por que ele existe, e o diagnóstico que o gerou
///
/// O `REDESENHO.md` §1.2 mediu por que o app Android parecia outro produto com a
/// paleta certa. Uma das três causas era o **ritmo**: a web separa seção com um
/// rótulo em versalete espaçado e uma linha que corre até a margem —
///
/// ```
/// ESTA NOITE ─────────────────────────────────────────
/// FRANQUIAS ─────────────────────────────────────  133
/// ```
///
/// — e o app tinha `Text("continuar")` num estilo qualquer. «Funciona, e não diz
/// nada.» É o que dá à página o ar de programa impresso, e é o que faz **uma tela
/// com seis blocos não virar seis listas**.
///
/// ⚠️ O espaçamento **é** o efeito. A web chega a `letter-spacing: 0.28em`; sem
/// ele o rótulo é indistinguível de um texto qualquer, que era o estado anterior.
///
/// ⚠️ E a caixa alta é de quem chama, não daqui — a mesma decisão do `Medida.kt`
/// do Android. O verso da caixa é encarte impresso e grita `1H36`; um cartão de
/// tela diz `1h36`. Se esta peça forçasse maiúscula, quem quer minúscula teria de
/// desfazer.
struct RotuloDeSecao: View {
    let texto: String
    /// O número à direita, depois da linha. `nil` some — e some **inteiro**, em
    /// vez de escrever «0» ou «—» (§24). Zero é uma afirmação, e a tela nem
    /// sempre sabe disso.
    var contagem: String?

    var body: some View {
        HStack(alignment: .center, spacing: 12) {
            Text(texto)
                .font(Tipo.rotulo())
                .tracking(Tipo.espacoDoRotulo)
                .foregroundStyle(Cores.destaque)
                .fixedSize()

            /// A linha. ⚠️ Ela é **um filete de 1px**, não uma borda de caixa: o
            /// que ela faz é levar o olho do rótulo até a margem, e uma linha
            /// grossa viraria divisória — que é outra coisa, e fecharia a seção
            /// em vez de abri-la.
            Rectangle()
                .fill(Cores.destaque.opacity(0.28))
                .frame(height: 1)

            if let contagem {
                Text(contagem)
                    .font(.system(size: 12).monospacedDigit())
                    .foregroundStyle(Cores.textoApagado)
                    .fixedSize()
            }
        }
    }
}

#Preview {
    VStack(spacing: 28) {
        RotuloDeSecao(texto: "CONTINUAR")
        RotuloDeSecao(texto: "BIBLIOTECA", contagem: "60 de 8.273")
    }
    .padding(24)
    .background(Cores.fundo)
    .preferredColorScheme(.dark)
}
