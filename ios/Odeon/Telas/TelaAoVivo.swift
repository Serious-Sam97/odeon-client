import SwiftUI

/// Ao vivo — o que está passando **agora**.
///
/// ## ⚠️ Ela é a primeira tela em que este cliente passa à frente do Android
///
/// O celular de lá não tem ao vivo; a TV e a web têm. O dono confirmou que foi
/// esquecimento, não decisão — então aqui ela existe, e as decisões vieram do
/// `NoArAgora.kt` e da `TelaAoVivoDaTv`, que já pagaram por elas.
///
/// ## ⚠️ Ela **é aba**, e eu tinha errado isso
///
/// Nasceu como uma linha dentro do guia, com um argumento que parecia bom: o iOS
/// colapsa em «More» a partir da sexta aba, e o `TelaDoMural` já registrava que
/// «o iOS os enfiaria num More que ninguém abre».
///
/// O dono olhou e disse que **faltava no menu**. Está certo, e o meu argumento
/// media a coisa errada: o custo do «More» é sobre **entrar** num destino;
/// esconder o destino dentro de outra tela é custo maior, e sem aviso nenhum —
/// quem não souber que o guia leva ao ao vivo nunca descobre que existe.
///
/// ⚠️ A régua que sobra é a de sempre: **destino que não está na barra não
/// existe.** O «More» é feio; invisível é pior.
@Observable
@MainActor
final class ModeloAoVivo {
    var quadros: [QuadroNoAr] = []
    var carregando = true
    var recado: String?
    /// O relógio **do servidor**, da última resposta. Ver `emCartaz`.
    var agora = Date.now

    private let odeon: RepositorioOdeon
    init(odeon: RepositorioOdeon) { self.odeon = odeon }

    /// ⚠️ As duas rotas em paralelo, e **uma falhando não derruba a outra**: os
    /// canais do Odeon e os de fonte externa são dois mundos com dois motivos
    /// independentes de faltar. Amarrá-los num `try` só faria a casa sumir por
    /// causa de um M3U fora do ar.
    func carregar() async {
        carregando = true
        defer { carregando = false }
        _ = try? await odeon.garantirTokenDeMidia()

        async let grade = try? await odeon.gradeDoOdeon()
        async let externos = try? await odeon.canaisAoVivo()
        let (daCasa, deFora) = await (grade, externos)

        /// ⚠️ O relógio vem da resposta. Sem grade, cai no do aparelho — que é o
        /// menos ruim, e não o certo.
        agora = instanteISO(daCasa?.agora) ?? .now
        quadros = emCartaz(agora: agora, doOdeon: daCasa, externos: deFora ?? [])
        recado = (daCasa == nil && deFora == nil) ? "não deu pra falar com os canais" : nil
    }


}

struct TelaAoVivo: View {
    let odeon: RepositorioOdeon
    let insignia: Insignia
    let aoSintonizar: (QuadroNoAr) -> Void
    var aoAbrirPerfil: () -> Void = {}
    var aoSairDaConta: () -> Void = {}
    /// ⚠️ `nil` quando ela é **aba**: aba não tem pra onde voltar — é raiz tanto
    /// quanto a biblioteca. O botão só existe quando alguém a abriu de dentro de
    /// outra tela.
    let aoFechar: (() -> Void)?

    @State private var modelo: ModeloAoVivo

    init(
        odeon: RepositorioOdeon, insignia: Insignia,
        aoAbrirPerfil: @escaping () -> Void = {},
        aoSairDaConta: @escaping () -> Void = {},
        aoSintonizar: @escaping (QuadroNoAr) -> Void,
        aoFechar: (() -> Void)? = nil,
    ) {
        self.odeon = odeon
        self.insignia = insignia
        self.aoAbrirPerfil = aoAbrirPerfil
        self.aoSairDaConta = aoSairDaConta
        self.aoSintonizar = aoSintonizar
        self.aoFechar = aoFechar
        _modelo = State(wrappedValue: ModeloAoVivo(odeon: odeon))
    }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    if let aoFechar {
                        CabecalhoDaCasa(
                            texto: "AO VIVO",
                            contagem: modelo.quadros.isEmpty ? nil : "\(modelo.quadros.count) canais",
                            saida: "voltar",
                            aoSair: aoFechar,
                        )
                    } else {
                        CabecalhoDaTela(
                            titulo: "ao vivo",
                            contagem: modelo.quadros.isEmpty ? nil
                                : ("\(modelo.quadros.count)", "canais"),
                            insignia: insignia,
                            aoAbrirPerfil: aoAbrirPerfil,
                            aoSair: aoSairDaConta,
                        )
                        .padding(.horizontal, -20)
                    }

                    if let recado = modelo.recado {
                        Text(recado).font(.system(size: 14)).foregroundStyle(Cores.textoApagado)
                    } else if modelo.quadros.isEmpty, !modelo.carregando {
                        Text("nenhum canal no ar")
                            .font(.system(size: 14))
                            .foregroundStyle(Cores.textoApagado)
                    }

                    /// ⚠️ Os da casa e os de fora são **duas seções**, e não uma
                    /// lista com um selo: os primeiros têm grade, obra e arquivo
                    /// atrás; os outros são um fluxo que alguém transmite. Misturá-
                    /// los faria «sintonizar» prometer a mesma coisa nos dois.
                    secao("CANAIS DA CASA", modelo.quadros.filter(\.daCasa))
                    secao("DE FORA", modelo.quadros.filter { !$0.daCasa })
                }
                .frame(maxWidth: 620, alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(20)
            }
        }
        .task { if modelo.quadros.isEmpty { await modelo.carregar() } }
    }

    @ViewBuilder
    private func secao(_ titulo: String, _ quadros: [QuadroNoAr]) -> some View {
        if !quadros.isEmpty {
            VStack(alignment: .leading, spacing: 10) {
                RotuloDeSecao(texto: titulo, contagem: "\(quadros.count)")
                    .padding(.top, 6)
                ForEach(quadros) { linha($0) }
            }
        }
    }

    private func linha(_ q: QuadroNoAr) -> some View {
        Button { aoSintonizar(q) } label: {
            HStack(alignment: .top, spacing: 12) {
                /// O logo, quando há; o número do canal, quando não. ⚠️ Os do
                /// Odeon não têm logo — e aí o número **é** a identidade, como
                /// numa televisão.
                ZStack {
                    Rectangle().fill(Cores.fundo)
                    if q.logo != nil {
                        ArteDoOdeon(odeon: odeon, caminho: q.logo, preenchendo: false)
                    } else {
                        Text(q.numero)
                            .font(Tipo.letreiro(17))
                            .foregroundStyle(Cores.destaque)
                    }
                }
                .frame(width: 52, height: 52)
                .clipShape(.rect(cornerRadius: 6))

                VStack(alignment: .leading, spacing: 3) {
                    Text(q.canalNome)
                        .font(Tipo.rotulo(10))
                        .tracking(2.2)
                        .foregroundStyle(Cores.destaque)
                    Text(q.titulo)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(Cores.texto)
                        .multilineTextAlignment(.leading)

                    /// A linha de baixo omite item por item (§24).
                    let detalhe = [
                        q.ano.map(String.init),
                        q.categoria,
                        q.quantoFalta(agora: modelo.agora),
                    ].compactMap { $0 }.joined(separator: " · ")
                    if !detalhe.isEmpty {
                        Text(detalhe).font(.system(size: 12)).foregroundStyle(Cores.textoApagado)
                    }

                    /// ⚠️ A barra do programa só existe quando dá pra calculá-la —
                    /// canal sem EPG não tem começo nem fim, e uma barra zerada
                    /// seria decoração com cara de dado (§18).
                    if let andamento = q.andamento(agora: modelo.agora) {
                        GeometryReader { g in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Cores.textoApagado.opacity(0.25))
                                Capsule().fill(Cores.destaque).frame(width: g.size.width * andamento)
                            }
                        }
                        .frame(height: 3)
                        .padding(.top, 3)
                    }

                    /// ⚠️ «a seguir» é o que faz um canal ser um canal: sem ele a
                    /// linha diz o que está passando, e não que há **depois**.
                    if let aSeguir = q.aSeguir, !aSeguir.isEmpty {
                        Text("a seguir · \(aSeguir)")
                            .font(.system(size: 11))
                            .foregroundStyle(Cores.textoApagado.opacity(0.8))
                            .padding(.top, 2)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Cores.fundoElevado, in: .rect(cornerRadius: 10))
        }
        .buttonStyle(.plain)
    }
}
