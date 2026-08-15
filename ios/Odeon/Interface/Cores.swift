import SwiftUI

/// A paleta da casa.
///
/// ## ⚠️ Ela não é escolha desta plataforma — é a mesma dos outros dois clientes
///
/// Os cinco valores abaixo saem do `styles.css` da web e do `ui/Tema.kt` do
/// Android, campo a campo. O diagnóstico do redesenho do celular
/// (`android/docs/REDESENHO.md` §1) foi justamente que a cor **já estava certa** e
/// o app ainda parecia outro produto — o que faltava era tipografia, ritmo e
/// objeto, não paleta.
///
/// Ou seja: mexer aqui é divergir de dois clientes de uma vez. Se uma cor
/// precisar mudar, muda nos três.
///
/// | | aqui | web |
/// |---|---|---|
/// | fundo | `#0A0A0C` | `--bg` |
/// | elevado | `#131318` | `--bg-raised` |
/// | texto | `#ECEEF4` | `--fg` |
/// | apagado | `#8B8D9A` | `--fg-muted` |
/// | destaque | `#E0B062` | `--accent` |
///
/// ⚠️ **Não há tema claro, e é decisão.** «O produto é uma sala escura; um tema
/// claro seria uma segunda paleta pra manter sem ninguém pedindo»
/// (`REDESENHO.md` §5). Por isso as telas fixam o esquema escuro em vez de
/// seguir o do sistema — um Odeon branco não é o Odeon.
enum Cores {
    static let fundo = Color(hex: 0x0A0A0C)
    static let fundoElevado = Color(hex: 0x131318)
    static let texto = Color(hex: 0xECEEF4)
    static let textoApagado = Color(hex: 0x8B8D9A)
    static let destaque = Color(hex: 0xE0B062)

    // MARK: - A matéria da locadora

    /// ⚠️ Daqui pra baixo as cores **não querem dizer nada** — elas são a cor de
    /// uma coisa. É a distinção que o `Cores.kt` do Android faz e que vale a pena
    /// copiar inteira: o `destaque` é semântico (isto é tocável, isto é seu), e a
    /// madeira é madeira. No dia em que alguém mexer no dourado da interface, a
    /// prateleira não pode mudar de tom junto.

    /// `--accent-hot`: o topo da luz. É o que a arandela joga na parede.
    static let destaqueQuente = Color(hex: 0xFFD98A)
    /// `--accent-dim`: o filamento apagado — réguas, bordas, o latão da cúpula.
    static let destaqueApagado = Color(hex: 0x8A6A3A)

    /// O papel, e as duas tintas que vivem **sobre superfície clara**.
    ///
    /// ⚠️ Elas existem só onde a tela desenha um **objeto de papel** — a etiqueta
    /// pendurada, a plaquinha da estante. Papel não é preto, e escrever com a cor
    /// da interface em cima dele desfaz o objeto.
    static let papel = Color(hex: 0xF2ECE0)
    static let tintaDoPapel = Color(hex: 0x5C5548)
    static let tintaDoBilhete = Color(hex: 0x241A09)

    /// A madeira da prateleira. Dois tons porque tábua de uma cor só lê como
    /// tarja marrom: o claro é onde a luz da loja bate, o fundo é a sombra.
    static let madeira = Color(hex: 0x6B4A2A)
    static let madeiraFunda = Color(hex: 0x3A2716)
}

extension Color {
    /// `0xE0B062` → a cor. Existe pra a tabela acima poder ser lida do mesmo
    /// jeito que se lê o `styles.css` — conferir hex contra hex é o que mantém os
    /// três clientes iguais, e um `Color(red:green:blue:)` em ponto flutuante
    /// tornaria essa conferência impossível de fazer com o olho.
    init(hex: UInt32) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: 1,
        )
    }
}
