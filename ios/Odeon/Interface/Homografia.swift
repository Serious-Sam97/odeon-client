import QuartzCore

/// Mapear um retângulo em **quatro pontos quaisquer** — o `setPolyToPoly` que o
/// Android usa, pela porta que a Apple abre.
///
/// ## ⚠️ Por que não `rotation3DEffect`
///
/// Foi o que eu tinha, e é o defeito: ele gira **uma view por vez**, cada uma com
/// a própria câmera. Duas faces com câmeras diferentes são dois desenhos que se
/// encontram por coincidência num ângulo só — e por isso a caixa anterior tinha
/// pose quase fixa e junta que abria ao girar.
///
/// ## Por que não uma `CGAffineTransform`
///
/// Porque afim **não faz perspectiva**: ela preserva paralelismo, e o que uma
/// face inclinada faz é justamente deixar de ser paralelogramo. Duas arestas que
/// convergem pro ponto de fuga não cabem numa matriz 2×3.
///
/// ## A conta
///
/// Uma homografia é a matriz 3×3 que leva (x, y, 1) em (x′w, y′w, w). Com quatro
/// pares de pontos, os oito coeficientes saem de um sistema linear 8×8 —
/// resolvido aqui por eliminação de Gauss.
///
/// ⚠️ E o `CATransform3D` a recebe **transposta e com as linhas trocadas**: o
/// terceiro eixo da homografia é o `w`, que no espaço 4×4 mora na quarta coluna
/// (`m14`, `m24`, `m44`), não na terceira. Foi o que levou tempo pra acertar, e o
/// sintoma de errar é a face aparecer espelhada ou sumir — nunca um erro.
enum Homografia {

    /// A transformação que leva o retângulo `0…largura × 0…altura` nos quatro
    /// pontos dados, na ordem: superior esquerdo, superior direito, inferior
    /// direito, inferior esquerdo.
    ///
    /// `nil` quando o sistema é degenerado — três pontos em linha, ou a face vista
    /// exatamente de perfil. ⚠️ E `nil` é **melhor que uma matriz qualquer**: uma
    /// face de perfil tem área zero, e insistir nela desenha um risco no meio da
    /// caixa.
    static func mapeando(
        largura: CGFloat, altura: CGFloat, para cantos: [CGPoint],
    ) -> CATransform3D? {
        guard cantos.count == 4, largura > 0, altura > 0 else { return nil }

        let origem = [
            CGPoint(x: 0, y: 0), CGPoint(x: largura, y: 0),
            CGPoint(x: largura, y: altura), CGPoint(x: 0, y: altura),
        ]

        /// O sistema: cada par de pontos dá duas equações.
        var a = [[Double]](repeating: [Double](repeating: 0, count: 9), count: 8)
        for i in 0 ..< 4 {
            let (x, y) = (Double(origem[i].x), Double(origem[i].y))
            let (u, v) = (Double(cantos[i].x), Double(cantos[i].y))
            a[i * 2] = [x, y, 1, 0, 0, 0, -u * x, -u * y, u]
            a[i * 2 + 1] = [0, 0, 0, x, y, 1, -v * x, -v * y, v]
        }

        guard let h = resolver(&a) else { return nil }

        /// ⚠️ Aqui mora a troca de eixo: `h[6]` e `h[7]` são os termos do `w`, e
        /// eles vão pra **quarta** coluna do 4×4 (`m14`/`m24`), não pra terceira.
        var t = CATransform3DIdentity
        t.m11 = CGFloat(h[0]); t.m21 = CGFloat(h[1]); t.m41 = CGFloat(h[2])
        t.m12 = CGFloat(h[3]); t.m22 = CGFloat(h[4]); t.m42 = CGFloat(h[5])
        t.m14 = CGFloat(h[6]); t.m24 = CGFloat(h[7]); t.m44 = 1
        t.m33 = 1
        return t
    }

    /// Eliminação de Gauss com pivô parcial.
    ///
    /// ⚠️ **Com pivô**, e não ingênua: sem trocar linhas, um zero na diagonal —
    /// que acontece toda vez que a caixa passa pelos 0° ou 90° — divide por zero e
    /// a face vira `NaN`. Um `NaN` numa matriz apaga a camada inteira em silêncio.
    private static func resolver(_ a: inout [[Double]]) -> [Double]? {
        let n = 8
        for coluna in 0 ..< n {
            var melhor = coluna
            for linha in (coluna + 1) ..< n where abs(a[linha][coluna]) > abs(a[melhor][coluna]) {
                melhor = linha
            }
            guard abs(a[melhor][coluna]) > 1e-10 else { return nil }
            a.swapAt(coluna, melhor)

            let pivo = a[coluna][coluna]
            for j in coluna ... n { a[coluna][j] /= pivo }

            for linha in 0 ..< n where linha != coluna {
                let fator = a[linha][coluna]
                guard fator != 0 else { continue }
                for j in coluna ... n { a[linha][j] -= fator * a[coluna][j] }
            }
        }
        return (0 ..< n).map { a[$0][n] }
    }
}
