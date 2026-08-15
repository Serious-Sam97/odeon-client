import SwiftUI

/// A tipografia da casa — e ela é a diferença mais visível de todas.
///
/// ## O diagnóstico que criou este arquivo
///
/// O `REDESENHO.md` §1.1 mediu por que o app Android parecia outro produto com a
/// paleta certa: **a web tem duas famílias e usa a segunda em 53 lugares**, e o
/// app era sem serifa em 100% da tela.
///
/// > «na web, "Drive" e "Harry Potter e a Ordem da Fênix" são **letreiro de
/// > cinema**; aqui são item de lista.»
///
/// A regra, então: **título e número são serifados; o resto é sem serifa.**
///
/// ⚠️ No Android isso custou embutir uma fonte no APK. Aqui não custa: o
/// `.serif` do SwiftUI resolve para a New York, que é uma serifa de texto
/// desenhada pela Apple e já está no aparelho. **Não embutir fonte** enquanto a
/// do sistema servir — peso de bundle sem ninguém pedindo.
enum Tipo {
    /// O letreiro: título de obra, nome de seção grande, o número gigante da
    /// afinidade. É a peça que faz a tela ser cinema e não lista.
    static func letreiro(_ tamanho: CGFloat = 34) -> Font {
        .system(size: tamanho, weight: .semibold, design: .serif)
    }

    /// O rótulo de seção, em versalete espaçado.
    ///
    /// ## ⚠️ O espaçamento é o efeito inteiro, e ele é grande
    ///
    /// A web chega a `letter-spacing: 0.28em`, e é o que dá à página o ar de
    /// programa impresso — «é o que faz uma tela com seis blocos não virar seis
    /// listas» (`REDESENHO.md` §1.2). Um rótulo sem o espaçamento é
    /// indistinguível de um `Text` qualquer, que era exatamente o estado
    /// anterior do Android.
    ///
    /// ⚠️ **A caixa alta é de quem chama**, não daqui. É a mesma decisão que o
    /// `Medida.kt` do Android registrou: o verso da caixa é encarte impresso e
    /// grita `1H36`; o cartão dos baixados é texto de tela e diz `1h36`. Se esta
    /// função forçasse maiúscula, quem quer minúscula teria de desfazer.
    static func rotulo(_ tamanho: CGFloat = 12) -> Font {
        .system(size: tamanho, weight: .semibold, design: .default)
    }

    /// O espaçamento do rótulo, pra usar junto com [rotulo] no `.tracking`.
    static let espacoDoRotulo: CGFloat = 3.2
}
