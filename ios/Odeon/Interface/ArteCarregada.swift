import SwiftUI

/// Uma imagem carregada **fora** do SwiftUI.
///
/// ## ⚠️ Por que ela existe, e não é preferência
///
/// A capa da caixa 3D não aparecia. A sonda derrubou as duas explicações fáceis
/// — o token vale (200, 128 KB fora do app) e a homografia está sã em três poses
/// — e sobrou o `AsyncImage` dentro de uma face com `projectionEffect`.
///
/// O sintoma casava com isso e com mais nada: a **cor dominante aparecia** (é um
/// `Rectangle`) e a **imagem não**. Se a face não estivesse sendo desenhada, não
/// haveria cor nenhuma.
///
/// O `AsyncImage` só começa a baixar quando o SwiftUI considera o nó visível, e
/// quem decide isso num subgrafo com matriz de projeção não é a gente. Carregar
/// por fora tira a decisão dele: quando a imagem chega, ela **já é** uma `Image`,
/// e a face desenha um dado que existe.
///
/// ## ⚠️ E o cache não é otimização
///
/// Uma prateleira tem seis caixas por estante e várias estantes; sem cache, cada
/// rolagem refaz o mesmo pedido ao **servidor de casa**, que também transcodifica.
/// É a mesma régua dos 250 ms da busca.
@Observable
@MainActor
final class ArteCarregada {
    private(set) var imagem: Image?

    @ObservationIgnored private var pedido: Task<Void, Never>?

    /// O que já veio, guardado pelo endereço.
    ///
    /// ⚠️ A chave é a URL **sem o token**: ele muda a cada renovação, e com ele na
    /// chave a mesma capa entraria no cache de novo a cada troca — que é
    /// exatamente quando a memória menos precisa disso.
    @ObservationIgnored private static var guardadas: [String: Image] = [:]

    /// ## ⚠️ Ela **renova e tenta de novo** — e é aqui que isso tem de morar
    ///
    /// A sonda mostrou `401 · 54 B` em toda capa: o corpo de erro do servidor,
    /// entregue como se fosse um JPEG. O token de mídia morre com frequência nesta
    /// casa — emitir um **apaga o dos outros clientes**, e são quatro.
    ///
    /// Eu já tinha escrito o `conferirTokenDeMidia`, que confere uma arte por
    /// abertura do app. Ele não resolve, e agora sei por quê: **renovar não avisa
    /// ninguém**. As URLs já montadas nas telas continuam com o token velho, e
    /// nada manda as views se redesenharem — o modelo não mudou.
    ///
    /// Então a recuperação mora onde o erro **aparece**: quem baixa a imagem é
    /// quem vê o 401, e é quem pode pedir um token novo e refazer o endereço.
    ///
    /// ⚠️ Uma vez só por imagem. Sem a trava, uma prateleira com 401 vira seis
    /// renovações em sequência — e cada renovação mata a anterior, então o laço
    /// não só é caro como **se auto-alimenta**.
    func carregar(_ caminho: String?, odeon: RepositorioOdeon) {
        pedido?.cancel()
        guard let caminho, !caminho.isEmpty else { imagem = nil; return }

        if let ja = Self.guardadas[caminho] { imagem = ja; return }

        pedido = Task { [weak self] in
            guard let pronta = await Self.baixar(caminho, odeon: odeon) else { return }
            guard !Task.isCancelled else { return }
            Self.guardadas[caminho] = pronta
            self?.imagem = pronta
        }
    }

    private static func baixar(
        _ caminho: String, odeon: RepositorioOdeon, jaRenovou: Bool = false,
    ) async -> Image? {
        guard let url = odeon.urlDaArte(caminho),
              let (dados, resposta) = try? await URLSession.shared.data(from: url)
        else { return nil }

        let status = (resposta as? HTTPURLResponse)?.statusCode ?? -1
        if status == 401, !jaRenovou {
            /// ⚠️ E o token novo tem de entrar **antes** de remontar a URL — é a
            /// ordem que faz a segunda tentativa ser diferente da primeira.
            _ = try? await odeon.renovarTokenDeMidia()
            return await baixar(caminho, odeon: odeon, jaRenovou: true)
        }
        guard status == 200, let ui = UIImage(data: dados) else { return nil }
        return Image(uiImage: ui)
    }
}

/// A capa de uma caixa, já carregada.
///
/// ⚠️ Ela é uma `View` própria pra o `.task(id:)` viver aqui: posto na face, ele
/// reexecutaria a cada giro, porque a face é reconstruída a cada quadro do
/// arrasto.
struct CapaDaCaixa: View {
    let odeon: RepositorioOdeon
    /// ⚠️ O **caminho**, e não a URL pronta: a URL carrega o token, e o token
    /// muda. Guardar o caminho é o que permite remontar o endereço depois de
    /// renovar — e é também o que faz a chave do cache não envelhecer.
    let caminho: String?
    let cor: Color
    let titulo: String

    @State private var arte = ArteCarregada()

    var body: some View {
        ZStack {
            Rectangle().fill(cor)
            if let imagem = arte.imagem {
                imagem.resizable().scaledToFill()
            } else {
                /// Sem arte, o título **é** a capa — melhor que um retângulo mudo.
                /// São 48% do acervo sem pôster: não é caso de borda.
                Text(titulo)
                    .font(Tipo.letreiro(11))
                    .foregroundStyle(.white.opacity(0.85))
                    .multilineTextAlignment(.center)
                    .padding(6)
            }
        }
        .task(id: caminho) { arte.carregar(caminho, odeon: odeon) }
    }
}

/// A arte do acervo, em qualquer tela.
///
/// ## ⚠️ Ela substitui o `AsyncImage` — e o motivo é um defeito medido
///
/// O `AsyncImage` recebe uma URL pronta. Neste app a URL carrega o **token de
/// mídia**, e o token morre: emitir um apaga o dos outros clientes, e são quatro
/// na casa. Quando morre, a resposta é `401` com **54 bytes** de JSON, que não
/// viram imagem — e o `AsyncImage` desenha o vazio, que é exatamente o que uma
/// obra sem pôster também desenha.
///
/// Defeito e estado normal do mundo, indistinguíveis na tela. Visto assim: herói
/// vazio, «continuar» vazio, caixas só com a cor dominante, e **um** cartaz na
/// grade.
///
/// ⚠️ E ela guarda o **caminho**, não a URL: é o que permite remontar o endereço
/// depois de renovar, e o que faz a chave do cache não envelhecer junto com o
/// token.
struct ArteDoOdeon: View {
    let odeon: RepositorioOdeon
    let caminho: String?
    /// `true` preenche o quadro e corta; `false` cabe inteiro dentro dele. Logo é
    /// `scaledToFit` — um logotipo cortado deixa de identificar o canal.
    var preenchendo: Bool = true

    @State private var arte = ArteCarregada()

    var body: some View {
        Group {
            if let imagem = arte.imagem {
                if preenchendo {
                    imagem.resizable().scaledToFill()
                } else {
                    imagem.resizable().scaledToFit()
                }
            } else {
                /// ⚠️ Transparente, e **não** um esqueleto cinza: quem chama já
                /// desenhou a cor da obra atrás. Um retângulo de carregamento por
                /// cima dela piscaria em toda rolagem.
                Color.clear
            }
        }
        .task(id: caminho) { arte.carregar(caminho, odeon: odeon) }
    }
}
