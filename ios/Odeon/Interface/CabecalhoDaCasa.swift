import SwiftUI

/// O cabeçalho de um cômodo da casa: o rótulo, e a saída.
///
/// ## ⚠️ Ele existe por causa de um defeito medido, não por gosto de fatorar
///
/// As três portas da casa — mural, perfil, guia, baixados — repetiam este par:
///
/// ```swift
/// HStack {
///     RotuloDeSecao(texto: "QUEM VOCÊ É")
///     Button("voltar", action: aoFechar)
///         .font(.system(size: 14))
///         .padding(.leading, 8)
/// }
/// ```
///
/// E no perfil **o botão não respondia**. A sonda provou que não era o estado: a
/// folha remontava certo (`folha montada com naCasa=perfil`), mas o `print` dentro
/// da ação **nunca saía**. O toque não chegava.
///
/// A causa é o alvo. Um `Button` cujo rótulo é só texto de 14pt tem área de toque
/// do tamanho do desenho da letra — cerca de **36 × 17pt**. A Apple pede 44 × 44,
/// e não é preciosismo: 17pt de altura é menos que a imprecisão de um polegar. O
/// «fechar» do mural funcionava por sorte de alguns pontos, o que é pior que não
/// funcionar — o defeito fica intermitente e cada pessoa acha que errou o dedo.
///
/// ⚠️ E `.contentShape` é o que faz o `padding` valer: sem ele o `padding` só
/// afasta o desenho, e a área de toque continua colada na letra. É a diferença
/// entre um botão maior e um botão que **parece** maior.
struct CabecalhoDaCasa: View {
    let texto: String
    var contagem: String?
    /// A palavra da saída. ⚠️ «fechar» sai da casa; «voltar» sobe um cômodo. São
    /// coisas diferentes e a tela tem que dizer qual — é o mesmo cuidado do
    /// «assistir» vs «continuar de 47min» da ficha.
    var saida: String = "fechar"
    let aoSair: () -> Void

    var body: some View {
        HStack(spacing: 0) {
            RotuloDeSecao(texto: texto, contagem: contagem)
            Button(action: aoSair) {
                Text(saida)
                    .font(.system(size: 14))
                    .foregroundStyle(Cores.destaque)
                    .padding(.leading, 12)
                    .frame(minHeight: 44)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)
        }
    }
}
