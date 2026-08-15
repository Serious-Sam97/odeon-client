import AVFoundation
import SwiftUI

/// A projeção: a camada de vídeo e a tira de filme.
///
/// ## ⚠️ Por que o player deixou de ser o da Apple
///
/// A tela usava `VideoPlayer` do AVKit — os controles do sistema, de graça. E
/// eles mostraram, num filme de 2006, **«4:44 AM»**: um horário de relógio com
/// marcador de borda ao vivo, porque a playlist de HLS sai como `EVENT` sem
/// `#EXT-X-ENDLIST` e o AVPlayer é **obrigado** a tratá-la como transmissão. Isso
/// vale pros dois modos que passam por HLS — ~70% do acervo.
///
/// O Android nunca teve esse problema porque nunca perguntou ao player onde o
/// filme acaba: **a duração vem da ficha**, e a barra é dele.
///
/// ⚠️ O pedido ao servidor continua de pé (Pedido 4): uma playlist `VOD` conserta
/// isso na origem, pros quatro clientes. Mas a barra própria não é um contorno —
/// é o que a tira de filme exige de qualquer jeito, e é o que faz o player deste
/// app ser deste app.
struct CamadaDeVideo: UIViewRepresentable {
    let player: AVPlayer

    func makeUIView(context _: Context) -> CaixaDeProjecao {
        let caixa = CaixaDeProjecao()
        caixa.backgroundColor = .black
        /// ## ⚠️ Ela **não recebe toque**, e sem esta linha o player fica mudo
        ///
        /// Uma `UIView` comum tem `isUserInteractionEnabled = true` e **absorve**
        /// o toque antes de o SwiftUI enxergar o gesto. Na tela isso apareceu
        /// assim: o filme rodava, o toque no meio não fazia nada, e os controles
        /// nunca voltavam depois de sumirem sozinhos — um player sem pausa e sem
        /// saída, só com o botão físico.
        ///
        /// Ela não tem nada pra fazer com um toque: é vidro. Quem escuta é a tela.
        caixa.isUserInteractionEnabled = false
        caixa.camada.player = player
        /// ⚠️ `resizeAspect` e não `resizeAspectFill`: cortar as bordas de um
        /// filme pra encher a tela é decidir pelo diretor onde termina o quadro.
        /// As tarjas pretas são o formato, não desperdício.
        caixa.camada.videoGravity = .resizeAspect
        return caixa
    }

    func updateUIView(_ caixa: CaixaDeProjecao, context _: Context) {
        if caixa.camada.player !== player { caixa.camada.player = player }
    }
}

/// A `UIView` cuja camada **é** a de vídeo.
///
/// ⚠️ Assim ela é redimensionada pelo sistema junto da view. Uma `AVPlayerLayer`
/// adicionada como sublayer precisaria de `layoutSubviews` à mão, e o vídeo
/// ficaria do tamanho errado por um quadro a cada rotação.
final class CaixaDeProjecao: UIView {
    override static var layerClass: AnyClass { AVPlayerLayer.self }
    var camada: AVPlayerLayer { layer as! AVPlayerLayer }
}

/// A tira de filme — a barra de tempo, em 35 mm.
///
/// ## ⚠️ Ela é a barra **e** o mapa
///
/// Uma barra de progresso diz onde você está numa reta. Esta diz onde você está
/// **no filme**: os fotogramas são os mesmos do varal da ficha, e reconhecer a
/// cena é o que permite voltar «até antes do cofre» sem tatear.
///
/// ⚠️ E os furos de arrasto nas bordas não são enfeite: sem eles a fileira é uma
/// tira de miniaturas. São eles que dizem «isto é filme».
struct TiraDeFilme: View {
    let cenas: [Cena]
    let posicao: Double
    let odeon: RepositorioOdeon
    let aoEscolher: (Double) -> Void

    private let alturaDoQuadro: CGFloat = 44

    /// ## ⚠️ Ela **não rola** — o filme inteiro cabe na largura
    ///
    /// A primeira montagem era um `ScrollView` horizontal com quadros de 78pt:
    /// cinco e meio à vista, o resto atrás de um arrasto. Comparada com a do
    /// Android, a diferença ficou óbvia — lá os doze fotogramas cabem de uma vez.
    ///
    /// E não é cosmética: o cabeçalho deste arquivo diz que ela é **a barra e o
    /// mapa**. Se é preciso arrastar pra ver o resto, ela deixou de ser barra —
    /// «onde eu estou no filme» é uma pergunta sobre o **todo**, e um todo que não
    /// cabe na tela não responde. Nenhuma barra de progresso rola.
    ///
    /// Então os quadros dividem a largura: doze cenas viram doze fatias, e a
    /// posição no filme é a posição na tela.
    var body: some View {
        GeometryReader { g in
            let largura = max(1, (g.size.width - 20) / CGFloat(max(1, cenas.count)))
            HStack(spacing: 1) {
                ForEach(cenas) { cena in
                    Button { aoEscolher(cena.segundos) } label: { quadro(cena, largura: largura) }
                        .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, 10)
            .frame(maxHeight: .infinity)
        }
        .frame(height: alturaDoQuadro + 16)
        .background {
            ZStack {
                Color.black.opacity(0.85)
                VStack {
                    furos
                    Spacer()
                    furos
                }
            }
        }
    }

    /// A fileira de furos de arrasto, em cima e embaixo.
    private var furos: some View {
        GeometryReader { g in
            let passo: CGFloat = 14
            HStack(spacing: 0) {
                ForEach(0 ..< max(2, Int(g.size.width / passo)), id: \.self) { _ in
                    RoundedRectangle(cornerRadius: 1)
                        .fill(Color(hex: 0xD8D2C4))
                        .frame(width: 6, height: 5)
                        .frame(maxWidth: .infinity)
                }
            }
        }
        .frame(height: 8)
        .allowsHitTesting(false)
    }

    private func quadro(_ cena: Cena, largura: CGFloat) -> some View {
        /// ⚠️ **O quadro atual é o último que já começou**, e não o mais próximo:
        /// no minuto 47 você está *dentro* da cena que começou aos 44, não a
        /// caminho da que começa aos 50. Arredondar pro mais próximo faria a
        /// moldura pular pra frente antes de a cena chegar.
        let atual = cenas.last { $0.segundos <= posicao }?.id == cena.id

        return Color.clear
            /// ⚠️ A largura vem **da divisão**, não da proporção do fotograma: o
            /// que manda aqui é o filme caber, e um quadro 16:9 fixo faria doze
            /// cenas transbordarem e vinte, sobrarem. O recorte é do `clipped`.
            .frame(width: largura, height: alturaDoQuadro)
            .background {
                ZStack {
                    Rectangle().fill(Cores.fundoElevado)
                    ArteDoOdeon(odeon: odeon, caminho: cena.imagem)
                }
            }
            .clipped()
            /// ⚠️ Os outros quadros ficam **apagados**, e não escondidos: a tira
            /// inteira é o mapa, e só o «você está aqui» acende.
            .opacity(atual ? 1 : 0.45)
            .overlay {
                if atual {
                    Rectangle()
                        .strokeBorder(Cores.destaque, lineWidth: 2.5)
                        .shadow(color: Cores.destaque.opacity(0.8), radius: 6)
                }
            }
    }
}

/// `2:09:35` · `47:12` · `0:47`.
///
/// ⚠️ Sem zero à esquerda na primeira casa e **com** nas outras: `2:09:35`, e não
/// `02:09:35` nem `2:9:35`. É como um relógio de filme se escreve.
func relogioDaSessao(_ segundos: Double) -> String {
    let inteiro = max(0, Int(segundos))
    let h = inteiro / 3600, m = (inteiro % 3600) / 60, s = inteiro % 60
    return h > 0
        ? String(format: "%d:%02d:%02d", h, m, s)
        : String(format: "%d:%02d", m, s)
}
