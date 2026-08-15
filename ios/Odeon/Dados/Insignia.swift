import Foundation
import Observation

/// Quem você é, no canto de toda tela.
///
/// ## ⚠️ Uma chamada, dois usos — e é o que o Android já dizia
///
/// O `RepositorioOdeon.kt` de lá registra: «esta chamada serve duas coisas ao
/// mesmo tempo: a **insígnia**, que fica no canto de toda tela, e a tela do
/// perfil». O rosto, o anel de nível e a lista de conquistas saem da **mesma**
/// resposta — «o número muda devagar, e a barra não é lugar de ficar
/// perguntando».
///
/// ⚠️ Por isso ela mora **acima das abas** e não dentro de nenhuma: cinco telas
/// desenham o mesmo avatar, e cinco cópias seriam cinco requisições e cinco
/// momentos diferentes de ficar velha.
@Observable
@MainActor
final class Insignia {
    private(set) var perfil: Perfil?

    @ObservationIgnored let odeon: RepositorioOdeon
    @ObservationIgnored private var carregando = false

    init(odeon: RepositorioOdeon) { self.odeon = odeon }

    var nivel: Int? { perfil?.progresso?.nivel }
    var fracao: Double? { perfil?.progresso?.fracaoDoNivel }
    /// ⚠️ O **caminho**, e não a URL: ver `ArteDoOdeon`.
    var caminhoDoRosto: String? { perfil?.avatar?.arte }

    /// ⚠️ Uma vez por abertura do app, e **em silêncio**: se falhar, o canto fica
    /// sem rosto e nenhuma tela quebra. A insígnia é enfeite informativo — nada
    /// que se faça no app depende dela.
    func carregar() async {
        guard perfil == nil, !carregando else { return }
        carregando = true
        defer { carregando = false }
        _ = try? await odeon.garantirTokenDeMidia()
        perfil = try? await odeon.perfil()
    }
}
