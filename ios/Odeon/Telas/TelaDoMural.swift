import SwiftUI

/// O mural — o que aconteceu na casa.
///
/// ## ⚠️ Ela existe porque este acervo tem **outras pessoas dentro**
///
/// A locadora não é uma metáfora solta: são três moradores dividindo um servidor,
/// e o mural é onde isso aparece. «Pausar o próprio filme e voltar é continuar;
/// encontrar a fita de outra pessoa no minuto 47 é outra coisa.»
///
/// ## ⚠️ Por que ela **não** é uma aba
///
/// O `REDESENHO.md` §6 registra que o celular tem **quatro destinos**, e que
/// copiar as sete abas da web «é desenho de mouse». Com mural, guia, perfil,
/// baixados e ao vivo seriam nove — e o iOS os enfiaria num «More» que ninguém
/// abre. As quatro abas são as quatro coisas que se faz sempre; o resto entra
/// pela casa, que é este ícone.
@Observable
@MainActor
final class ModeloDoMural {
    var mural: Mural?
    var recado: String?

    private let odeon: RepositorioOdeon
    init(odeon: RepositorioOdeon) { self.odeon = odeon }

    /// ⚠️ Só o que a tela **sabe dizer**. Um tipo desconhecido não vira linha
    /// muda: some. É o §18 aplicado a um feed — melhor uma linha a menos que uma
    /// linha dizendo «alguém fez algo com alguma coisa».
    var linhas: [Acontecimento] {
        (mural?.acontecimentos ?? []).filter { $0.frase != nil }
    }

    /// Quantas destas aconteceram **nos últimos sete dias**.
    ///
    /// ## ⚠️ O corte é o tempo, e ele já chegava
    ///
    /// O Android escreve `ESTA SEMANA ──── 27` acima do feed, e o `Virada.kt` de
    /// lá defende o critério: o corte certo num mural é **quando**, não «os
    /// primeiros N». Cortar em N mente nos dois sentidos — num dia movimentado
    /// esconde notícia, e num dia parado promove a notícia o que é da semana
    /// passada.
    ///
    /// ⚠️ `quando` vem em toda linha e não era lido por ninguém aqui. É o mesmo
    /// tipo de campo que já chegava e o cliente não pegava.
    var quantasDaSemana: Int {
        let leitor = ISO8601DateFormatter()
        leitor.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        let limite = Date.now.addingTimeInterval(-7 * 86_400)
        return linhas.count { a in
            guard let quando = leitor.date(from: a.quando)
                ?? ISO8601DateFormatter().date(from: a.quando) else { return false }
            return quando >= limite
        }
    }

    func carregar() async {
        do {
            _ = try? await odeon.garantirTokenDeMidia()
            mural = try await odeon.mural()
            recado = nil
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "o mural não abriu"
        }
    }


}

struct TelaDoMural: View {
    let odeon: RepositorioOdeon
    let insignia: Insignia
    let aoEscolher: (String) -> Void
    let aoAbrirPerfil: () -> Void
    let aoSair: () -> Void
    /// ⚠️ `nil` quando é aba; aqui ela voltou a ser folha, então existe.
    let aoFechar: (() -> Void)?

    @State private var modelo: ModeloDoMural

    init(
        odeon: RepositorioOdeon,
        insignia: Insignia,
        aoEscolher: @escaping (String) -> Void,
        aoAbrirPerfil: @escaping () -> Void,
        aoSair: @escaping () -> Void,
        aoFechar: (() -> Void)? = nil,
    ) {
        self.aoFechar = aoFechar
        self.odeon = odeon
        self.insignia = insignia
        self.aoEscolher = aoEscolher
        self.aoAbrirPerfil = aoAbrirPerfil
        self.aoSair = aoSair
        _modelo = State(wrappedValue: ModeloDoMural(odeon: odeon))
    }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let aoFechar {
                        CabecalhoDaCasa(texto: "MURAL", saida: "voltar", aoSair: aoFechar)
                    } else {
                        CabecalhoDaTela(
                            titulo: "mural",
                            insignia: insignia, aoAbrirPerfil: aoAbrirPerfil, aoSair: aoSair,
                        )
                        .padding(.horizontal, -20)
                    }

                    /// ⚠️ **«um mural com um nome só não é uma conversa»** — o
                    /// número existe pra a tela dizer isso em vez de parecer
                    /// completa. Some quando não há o que comparar (§24).
                    if let m = modelo.mural, m.pessoas > 1 {
                        Text(m.vozes <= 1
                            ? "só você apareceu por aqui — são \(m.pessoas) na casa"
                            : "\(m.vozes) de \(m.pessoas) pessoas apareceram")
                            .font(.system(size: 12))
                            .foregroundStyle(Cores.textoApagado)
                    }

                    /// ⚠️ Some quando não há o que contar — um rótulo de seção
                    /// sobre uma lista vazia é cabeçalho quebrado (§24).
                    if modelo.quantasDaSemana > 0 {
                        RotuloDeSecao(texto: "ESTA SEMANA", contagem: "\(modelo.quantasDaSemana)")
                            .padding(.top, 4)
                    }

                    if let recado = modelo.recado {
                        Text(recado).font(.system(size: 14)).foregroundStyle(Cores.textoApagado)
                    } else if modelo.linhas.isEmpty, modelo.mural != nil {
                        Text("ninguém fez nada ainda")
                            .font(.system(size: 14))
                            .foregroundStyle(Cores.textoApagado)
                    }

                    ForEach(modelo.linhas) { a in
                        linha(a)
                    }
                }
                .frame(maxWidth: 620, alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(20)
            }
        }
        .task { if modelo.mural == nil { await modelo.carregar() } }
    }

    private func linha(_ a: Acontecimento) -> some View {
        Button {
            if let obra = a.obraId { aoEscolher(obra) }
        } label: {
            HStack(alignment: .top, spacing: 12) {
                /// A capa é pequena e existe pra reconhecer o filme de relance —
                /// não é o assunto da linha, que é a **pessoa**.
                ZStack {
                    Rectangle().fill(Cores.fundoElevado)
                    ArteDoOdeon(odeon: odeon, caminho: a.poster)
                }
                .frame(width: 40, height: 60)
                .clipShape(.rect(cornerRadius: 4))

                VStack(alignment: .leading, spacing: 3) {
                    /// ⚠️ **O nome vem antes do verbo**, e é o ponto da tela: o
                    /// assunto é quem fez, não o que foi feito. «sam terminou X»
                    /// e não «X foi terminado».
                    (
                        Text(a.meu ? "você" : a.quem)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(Cores.destaque)
                            + Text(" \(a.frase ?? "") ")
                            .font(.system(size: 15))
                            .foregroundStyle(Cores.textoApagado)
                            + Text(a.titulo)
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(Cores.texto)
                    )
                    .multilineTextAlignment(.leading)

                    /// O `detalhe` **é conteúdo** (uma nota, um recado) e por isso
                    /// vem pronto do servidor — ao contrário da frase.
                    /// ⚠️ **Sem aspas e sem itálico**, como no Android. As
                    /// guilhemetas eram invenção minha: o `detalhe` não é citação
                    /// de ninguém — é o estado da fita («sem rebobinar · atrasada
                    /// · pelo prazo»), e vesti-lo de fala fazia parecer recado de
                    /// gente.
                    if let detalhe = a.detalhe, !detalhe.isEmpty {
                        Text(detalhe)
                            .font(.system(size: 13))
                            .foregroundStyle(Cores.textoApagado)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Cores.fundoElevado.opacity(0.6), in: .rect(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }
}
