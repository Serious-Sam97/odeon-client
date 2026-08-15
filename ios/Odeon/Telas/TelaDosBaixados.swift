import SwiftUI

/// Os baixados — o que existe no aparelho sem o servidor por perto.
///
/// ## ⚠️ O defeito que a foto do Android pegou, e que esta tela não repete
///
/// A tela de lá nasceu como uma lista de retângulos com título, uma frase de
/// estado e um botão `apagar`. O comentário registra o que era pior que a
/// aparência: **não havia como assistir**. «A única ação de um filme de 2 GB
/// baixado pra ver sem rede era apagá-lo.» Cinco campos chegavam no modelo e
/// nenhum era desenhado — arte, bytes, duração, origem, e o toque.
///
/// Então aqui a régua é: **cada coisa que o disco sabe aparece, e tocar é a ação
/// principal**. Apagar é a secundária, e mora à direita.
///
/// ## ⚠️ Ela é a única tela que funciona com o servidor desligado
///
/// Todas as outras pedem rede. Esta lê o índice do disco, e por isso a ficha de
/// cada filme foi gravada **junto com os bytes**: título, ano, duração e o
/// caminho da arte. A arte em si vem da rede e some sem ela — mas o cartão
/// continua legível, com a cor da casa no lugar da imagem, em vez de virar um vão.
@Observable
@MainActor
final class ModeloDosBaixados {
    var recado: String?

    let baixados: Baixados
    private let odeon: RepositorioOdeon

    init(odeon: RepositorioOdeon, baixados: Baixados) {
        self.odeon = odeon
        self.baixados = baixados
    }

    func arte(_ b: Baixado) -> String? {
        b.ficha.backdrop ?? b.ficha.poster
    }
}

struct TelaDosBaixados: View {
    let odeon: RepositorioOdeon
    let baixados: Baixados
    /// ⚠️ Ela **toca do disco**, e é o ponto da tela. Ver `TelaDoPlayer`: o
    /// caminho local entra no lugar do plano, e nenhuma requisição é feita.
    let aoTocar: (FichaDoBaixado) -> Void
    let aoFechar: () -> Void

    @State private var modelo: ModeloDosBaixados

    init(
        odeon: RepositorioOdeon,
        baixados: Baixados,
        aoTocar: @escaping (FichaDoBaixado) -> Void,
        aoFechar: @escaping () -> Void,
    ) {
        self.odeon = odeon
        self.baixados = baixados
        self.aoTocar = aoTocar
        self.aoFechar = aoFechar
        _modelo = State(wrappedValue: ModeloDosBaixados(odeon: odeon, baixados: baixados))
    }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    CabecalhoDaCasa(
                            texto: "NO APARELHO",
                            /// ⚠️ O tamanho ocupado é o assunto de uma tela de
                            /// armazenamento, e a do Android não o mostrava tendo
                            /// o dado em mãos. Some quando não há nada — «0 bytes»
                            /// é uma afirmação sobre um estado que a lista vazia
                            /// já diz melhor (§24).
                            contagem: baixados.itens.isEmpty ? nil : baixados.bytesNoDisco.emTamanho,
                            saida: "voltar",
                            aoSair: aoFechar,
                    )

                    if let recado = modelo.recado {
                        Text(recado).font(.system(size: 13)).foregroundStyle(Cores.textoApagado)
                    }

                    if baixados.itens.isEmpty {
                        vazio
                    }

                    ForEach(baixados.itens) { b in
                        cartao(b)
                    }
                }
                .frame(maxWidth: 620, alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(20)
            }
        }
    }

    /// ⚠️ O vazio **explica a regra**, em vez de só dizer «nada aqui». Quem abriu
    /// esta tela procurando o botão de baixar precisa saber por que metade do
    /// acervo não o tem — senão a ausência lê como defeito.
    private var vazio: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("nada guardado ainda")
                .font(Tipo.letreiro(19))
                .foregroundStyle(Cores.texto)
            Text("na ficha de um filme, «guardar no aparelho». "
                + "Só aparece nos que o iPhone abre sozinho — o resto precisa do servidor pra tocar.")
                .font(.system(size: 13))
                .foregroundStyle(Cores.textoApagado)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(16)
        .background(Cores.fundoElevado, in: .rect(cornerRadius: 11))
    }

    private func cartao(_ b: Baixado) -> some View {
        HStack(spacing: 0) {
            /// ⚠️ Só é tocável o que está **pronto**. Um filme a 40% abre com
            /// metade dos bytes e o AVFoundation falha sem dizer por quê — é o §53
            /// com um arquivo incompleto no lugar de um 403.
            Button { if b.pronto { aoTocar(b.ficha) } } label: {
                VStack(alignment: .leading, spacing: 3) {
                    Text(b.ficha.titulo)
                        .font(Tipo.letreiro(18))
                        .foregroundStyle(.white)
                        .lineLimit(2)
                        .multilineTextAlignment(.leading)
                    Text(linhaDeBaixo(b))
                        .font(.system(size: 11).monospacedDigit())
                        .foregroundStyle(b.falhou ? Cores.destaque : .white.opacity(0.75))
                }
                .padding(12)
                /// ## ⚠️ A arte vai em `background`, e é a correção de um defeito visto
                ///
                /// A primeira montagem punha imagem e texto num `ZStack` com
                /// `.frame(height: 104)` por fora — o mesmo arranjo do cartão do
                /// «para você», que funciona lá. Aqui **não funcionou**: na tela, o
                /// título saiu cortado ao meio e a linha de `1965 · 130min · 2,23 GB`
                /// não apareceu.
                ///
                /// A causa é o `scaledToFill` numa `HStack`. Lá o cartão manda na
                /// largura; aqui ele divide a linha com a lixeira, e uma arte
                /// deitada com largura proposta e altura livre reivindica **a altura
                /// da imagem original** — centenas de pontos. O `ZStack` cresce
                /// junto, o `.frame(104)` recorta o **meio**, e o texto ancorado no
                /// rodapé fica fora do recorte.
                ///
                /// `background` não deixa isso acontecer por definição: **o que está
                /// atrás nunca decide o tamanho do que está na frente**. O texto
                /// manda, a arte preenche. É a diferença entre empilhar e forrar.
                .frame(maxWidth: .infinity, minHeight: 104, alignment: .bottomLeading)
                .background {
                    ZStack {
                        Rectangle().fill(Cores.fundoElevado)
                        ArteDoOdeon(odeon: odeon, caminho: modelo.arte(b))
                        /// O véu, pelo mesmo motivo de sempre: há texto por cima, e
                        /// título branco sobre cena clara já foi medido neste
                        /// projeto em 1,02:1 de contraste.
                        LinearGradient(
                            colors: [.clear, .black.opacity(0.9)],
                            startPoint: .center, endPoint: .bottom,
                        )
                    }
                }
                .clipShape(.rect(cornerRadius: 10))
                /// A barra vive **na borda de baixo do cartão**, e não numa linha
                /// própria: é a fita enchendo, e o cartão é a fita.
                .overlay(alignment: .bottom) {
                    if let fracao = b.fracao, !b.pronto {
                        GeometryReader { g in
                            ZStack(alignment: .leading) {
                                Rectangle().fill(.white.opacity(0.18))
                                Rectangle().fill(Cores.destaque)
                                    .frame(width: g.size.width * fracao)
                            }
                        }
                        .frame(height: 3)
                    }
                }
                /// ⚠️ O que ainda não está pronto fica **apagado**, e não some: a
                /// pessoa pediu aquele filme e precisa ver que ele está vindo.
                .opacity(b.pronto ? 1 : 0.72)
            }
            .buttonStyle(.plain)

            Button { baixados.apagar(b.ficha.arquivoId) } label: {
                Image(systemName: "trash")
                    .font(.system(size: 15))
                    .foregroundStyle(Cores.textoApagado)
                    .frame(width: 46)
                    .frame(maxHeight: .infinity)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)
        }
    }

    /// A linha de baixo diz **uma coisa por vez**, e a que importa agora.
    ///
    /// ⚠️ Falhou é o caso que não pode virar silêncio: um cartão parado em 40% sem
    /// dizer nada é indistinguível de um download lento, e a pessoa espera pra
    /// sempre (§8b).
    private func linhaDeBaixo(_ b: Baixado) -> String {
        if b.falhou { return "não veio inteiro — apague e peça de novo" }
        if b.pronto {
            return [
                b.ficha.ano.map(String.init),
                b.ficha.duracaoEmSegundos.map { "\(Int($0) / 60)min" },
                b.bytes.emTamanho,
            ].compactMap { $0 }.joined(separator: " · ")
        }
        /// Baixando: o número que responde «falta muito?». Sem o total conhecido,
        /// só o que já veio — em vez de uma porcentagem inventada.
        if let total = b.bytesTotais {
            return "\(b.bytes.emTamanho) de \(total.emTamanho)"
        }
        return "começando…"
    }
}
