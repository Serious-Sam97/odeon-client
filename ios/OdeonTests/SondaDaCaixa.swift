import Foundation
import Testing
@testable import Odeon

/// A sonda da caixa — **por que a capa não desenha**.
///
/// ## ⚠️ Duas hipóteses já caíram
///
/// | | como caiu |
/// |---|---|
/// | o token morreu | a arte abre com **200 e 128 KB** fora do app |
/// | a minha renovação estragava | tirei-a e a capa continuou sem aparecer |
///
/// O que sobra é a geometria: a homografia recebe os quatro cantos da capa e
/// devolve uma matriz. Se essa matriz for degenerada — ou se os cantos vierem
/// numa ordem que a inverta — a face é desenhada **em área zero ou de costas**, e
/// o que se vê é o fundo por baixo dela: a cor dominante.
///
/// ⚠️ E é isso que o sintoma diz: a **cor aparece** e a **imagem não**. Se a face
/// não desenhasse, não haveria cor nenhuma — o que sobra é uma face desenhada com
/// a imagem colapsada.
struct SondaDaCaixa {

    @Test("a homografia da capa devolve uma matriz sã?")
    func aHomografiaDaCapa() {
        let m = Medidas.dvd
        let centro = CGPoint(x: 60, y: 78)

        for pose in [Pose(), Pose(giroY: 0, giroX: 0), Pose(giroY: 45, giroX: 10)] {
            let cantos = cantosNaTela(.capa, pose: pose, m: m, distancia: m.largura * 8,
                                      centro: centro)
            let t = tamanhoDoLado(.capa, m)
            let matriz = Homografia.mapeando(largura: t.width, altura: t.height, para: cantos)
            print("── pose y=\(pose.giroY) x=\(pose.giroX)")
            print("   cantos: \(cantos.map { "(\(Int($0.x)),\(Int($0.y)))" }.joined(separator: " "))")
            print("   matriz: \(matriz == nil ? "NIL" : "ok")")
            if let matriz {
                print("   m11=\(String(format: "%.3f", matriz.m11)) m22=\(String(format: "%.3f", matriz.m22)) "
                    + "m41=\(String(format: "%.1f", matriz.m41)) m42=\(String(format: "%.1f", matriz.m42)) "
                    + "m14=\(String(format: "%.5f", matriz.m14))")
            }
            #expect(matriz != nil)
        }
    }

    @Test("no repouso a lombada é um filete, e não meia caixa")
    func aLombadaEUmFilete() {
        /// ⚠️ A conta que a captura contradiz. Em repouso (22°), a lombada
        /// projetada tem de ser `espessura · sen 22°` contra `largura · cos 22°` da
        /// capa — no DVD, **4%** da largura dela. Na tela do palco ela apareceu com
        /// mais da metade, que é a assinatura de uma caixa girada ~65°.
        let m = Medidas.dvd.vezes(2.2)
        let centro = CGPoint(x: 130, y: 160)
        let capa = cantosNaTela(.capa, pose: Pose(), m: m, distancia: m.largura * 8, centro: centro)
        let lombada = cantosNaTela(.lombada, pose: Pose(), m: m, distancia: m.largura * 8, centro: centro)

        let larguraDaCapa = abs(capa[1].x - capa[0].x)
        let larguraDaLombada = abs(lombada[1].x - lombada[0].x)
        print("── no repouso: capa \(Int(larguraDaCapa))pt · lombada \(Int(larguraDaLombada))pt "
            + "= \(Int(larguraDaLombada / larguraDaCapa * 100))%")
        #expect(larguraDaLombada / larguraDaCapa < 0.12)
    }

    @Test("a proporção da lombada não muda com a escala")
    func aProporcaoNaoMudaComAEscala() {
        /// ⚠️ A estante desenha certo e o palco desenha errado, e a **única**
        /// diferença entre os dois é o fator: 0,72 lá, 2,2 aqui.
        for fator in [0.72, 1.0, 2.2] as [CGFloat] {
            let m = Medidas.dvd.vezes(fator)
            let d = m.largura * 8
            let capa = cantosNaTela(.capa, pose: Pose(), m: m, distancia: d, centro: .zero)
            let lombada = cantosNaTela(.lombada, pose: Pose(), m: m, distancia: d, centro: .zero)
            let razao = abs(lombada[1].x - lombada[0].x) / abs(capa[1].x - capa[0].x)
            print("── fator \(fator): lombada/capa = \(String(format: "%.1f", razao * 100))%")
            #expect(razao < 0.12)
        }
    }

    @Test("a capa está de frente no repouso, e a contracapa não")
    func aCapaEstaDeFrente() {
        let m = Medidas.dvd
        #expect(deFrente(.capa, pose: Pose(), m: m))
        #expect(!deFrente(.contracapa, pose: Pose(), m: m))
        /// E a lombada aparece no repouso — é o que faz a caixa ter volume a 22°.
        #expect(deFrente(.lombada, pose: Pose(), m: m))
        print("── visíveis no repouso: \(ladosVisiveis(pose: Pose(), m: m))")
        print("── luz da capa: \(String(format: "%.2f", luzDoLado(.capa, pose: Pose(), m: m)))")
    }
}
