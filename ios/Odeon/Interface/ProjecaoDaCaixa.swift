import CoreGraphics
import Foundation

/// A projeção 3D da caixa — **uma câmera para todas as faces**.
///
/// ⚠️ O nome tem «DaCaixa» porque `Projecao.swift` já é a do **player** (a
/// camada de vídeo e a tira de filme). No Android as duas convivem porque moram
/// em pastas diferentes; em Swift o módulo é plano, e o nome tem de carregar o
/// escopo que a pasta carregava.
///
/// ## ⚠️ Este arquivo conserta exatamente o que eu tinha feito
///
/// A `CaixaDeVHS` daqui eram **duas faces**, cada uma com o próprio
/// `rotation3DEffect`, `anchor` e `perspective`. O `Projecao.kt` do Android
/// descreve esse arranjo como o defeito que ele veio consertar:
///
/// > «como as camadas não dividem o mesmo ponto de fuga, a junta só fecha **na
/// > pose de repouso**. Por isso a pose é fixa e não acompanha o dedo — animar o
/// > ângulo abriria a junta no meio do caminho.»
///
/// Eu li esse parágrafo, escrevi no meu arquivo que estava resolvido «com uma
/// câmera para as duas faces», e entreguei duas câmeras. O dono viu na tela:
/// «que caixas 3d feias».
///
/// ## Como funciona: oito vértices, uma câmera, seis homografias
///
/// A caixa é um paralelepípedo com o centro na origem. Cada lado é um retângulo
/// de quatro cantos **no espaço da caixa**; girar é girar os cantos, projetar é
/// dividir pela profundidade, e desenhar é mapear o retângulo do conteúdo nos
/// quatro pontos que saíram.
///
/// É o `transform-style: preserve-3d` do CSS escrito à mão — e o que importa é
/// que a **câmera é uma só**, então arestas que se tocam na caixa se tocam na
/// tela, em qualquer ângulo.
enum ProjecaoDaCaixa {}

/// Um ponto no espaço da caixa. A origem é o **centro**, `y` cresce pra baixo — a
/// convenção da tela — e `z` cresce na direção de quem olha.
struct Vetor3: Equatable, Sendable {
    var x: CGFloat
    var y: CGFloat
    var z: CGFloat
}

/// Os seis lados.
///
/// ## ⚠️ Eram quatro no Android, e o dono viu o buraco
///
/// A primeira versão de lá desenhava capa, lombada, topo e contracapa, com o
/// argumento de que «a base e a lateral direita nunca aparecem, porque a caixa
/// gira no máximo ±42°». O argumento morreu junto com o teto: com giro livre, a
/// **lateral direita** aparece em todo o caminho entre a capa e o verso, e o que
/// se via ali era o vazio atrás da caixa.
///
/// Uma caixa com um lado faltando não é uma caixa: é cenário de teatro visto de
/// trás.
enum Lado: CaseIterable, Sendable {
    case capa, lombada, topo, contracapa, lateralDireita, base
}

/// A pose: quanto a caixa girou em cada eixo.
struct Pose: Equatable, Sendable {
    var giroY: CGFloat = Pose.repousoY
    var giroX: CGFloat = Pose.repousoX

    /// A pose de repouso é a da folha da web: `rotateX(3deg) rotateY(22deg)`. Uma
    /// caixa de frente é uma capa; uma caixa a 22° é um **objeto numa prateleira**.
    static let repousoY: CGFloat = 22
    static let repousoX: CGFloat = 3

    /// ⚠️ ±42° é o teto **da estante**. Na mão não vale: o dono pediu «quando você
    /// move com o dedo, dá pra ver o verso também», e 42° é justamente o que
    /// impede — o verso começa depois dos 90°.
    static let teto: CGFloat = 42

    /// ⚠️ Grau por pixel de arrasto, e os dois números são da web. A diferença não
    /// é descuido: o polegar anda muito mais na horizontal, e igualar os dois
    /// faria a caixa tombar ao menor tremor da mão.
    static let grauPorPixelHorizontal: CGFloat = 0.5
    static let grauPorPixelVertical: CGFloat = 0.32

    /// ⚠️ Livre, o giro horizontal **dá a volta** e o vertical continua preso. Não
    /// é assimetria por preguiça: uma caixa que tomba 180° pra frente fica de
    /// cabeça pra baixo, e ninguém vira uma caixa assim na mão.
    func somando(dx: CGFloat, dy: CGFloat, livre: Bool = false) -> Pose {
        Pose(
            giroY: livre ? Pose.daVolta(giroY + dx) : min(max(giroY + dx, -Pose.teto), Pose.teto),
            giroX: min(max(giroX + dy, -Pose.teto), Pose.teto),
        )
    }

    var mostrandoOVerso: Bool { abs(Pose.daVolta(giroY)) > 90 }

    /// Mantém o ângulo em −180…180. Sem isto, girar dez vezes acumula 3.600°, e a
    /// volta ao repouso desenrola as dez voltas na cara de quem soltou.
    static func daVolta(_ graus: CGFloat) -> CGFloat {
        var g = graus.truncatingRemainder(dividingBy: 360)
        if g > 180 { g -= 360 }
        if g < -180 { g += 360 }
        return g
    }
}

/// As medidas da caixa, em pontos.
///
/// ## ⚠️ Elas mudam por formato, e é o que faz um VHS não ser um DVD
///
/// | | largura | altura | espessura |
/// |---|---|---|---|
/// | DVD | 102 | 144 | 11 |
/// | VHS | 79 | 144 | 19 |
///
/// Os números são os do Android. A fita é mais **estreita e quase o dobro de
/// grossa**; o keep case é largo e fino. Uma caixa só pra tudo é a diferença
/// entre uma locadora e uma pilha de retângulos — e era o que eu tinha.
struct Medidas: Equatable, Sendable {
    var largura: CGFloat
    var altura: CGFloat
    var espessura: CGFloat

    static let dvd = Medidas(largura: 102, altura: 144, espessura: 11)
    static let vhs = Medidas(largura: 79, altura: 144, espessura: 19)

    /// Escala as três juntas — a estante usa caixas menores que o palco, e escalar
    /// só a largura mudaria o formato do objeto.
    func vezes(_ fator: CGFloat) -> Medidas {
        Medidas(largura: largura * fator, altura: altura * fator, espessura: espessura * fator)
    }
}

/// O tamanho do desenho de cada lado.
///
/// ⚠️ A lombada é espessura × altura; o topo é largura × espessura. Trocar os
/// dois desenha uma caixa que parece certa até alguém reparar que o título da
/// lombada está deitado.
func tamanhoDoLado(_ lado: Lado, _ m: Medidas) -> CGSize {
    switch lado {
    case .capa, .contracapa: CGSize(width: m.largura, height: m.altura)
    case .lombada, .lateralDireita: CGSize(width: m.espessura, height: m.altura)
    case .topo, .base: CGSize(width: m.largura, height: m.espessura)
    }
}

/// Os quatro cantos de um lado, no espaço da caixa, **na ordem do conteúdo**:
/// superior esquerdo, superior direito, inferior direito, inferior esquerdo.
///
/// ## ⚠️ A ordem orienta a face, e tem consequência visível
///
/// A contracapa é lida de trás: o canto esquerdo **do conteúdo** é o direito da
/// caixa. Com a ordem ingênua, o verso sai espelhado — e texto espelhado é o tipo
/// de defeito que passa num teste e salta numa captura.
func cantosDoLado(_ lado: Lado, _ m: Medidas, abertura: CGFloat = 0) -> [Vetor3] {
    let x = m.largura / 2, y = m.altura / 2, z = m.espessura / 2

    /// A capa aberta gira **em torno da dobradiça**, que é a aresta da lombada —
    /// não o centro: uma tampa que gira pelo meio atravessa a própria caixa.
    ///
    /// ⚠️ E abre **pra fora**: o seno soma no `z`. No Android o sinal esteve
    /// trocado e o dono viu — «ele tá abrindo pra dentro e não pra fora».
    ///
    /// ⚠️ O `dz` é o que mantém a dobradiça **parada**. Sem ele o eixo fica no
    /// plano do meio da espessura e a tampa «voa»: o ponto que deveria ficar
    /// cravado se desloca ~0,86 × espessura nos 118° de abertura — por isso a fita
    /// incomodava mais que o disco, com mais que o dobro de vão.
    if abertura != 0, lado == .capa {
        let a = abertura * .pi / 180
        return cantosDoLado(.capa, m).map { p in
            let dx = p.x + x, dz = p.z - z
            return Vetor3(x: dx * cos(a) - dz * sin(a) - x, y: p.y,
                          z: dx * sin(a) + dz * cos(a) + z)
        }
    }

    switch lado {
    case .capa:
        return [Vetor3(x: -x, y: -y, z: z), Vetor3(x: x, y: -y, z: z),
                Vetor3(x: x, y: y, z: z), Vetor3(x: -x, y: y, z: z)]
    case .contracapa:
        return [Vetor3(x: x, y: -y, z: -z), Vetor3(x: -x, y: -y, z: -z),
                Vetor3(x: -x, y: y, z: -z), Vetor3(x: x, y: y, z: -z)]
    case .lombada:
        return [Vetor3(x: -x, y: -y, z: -z), Vetor3(x: -x, y: -y, z: z),
                Vetor3(x: -x, y: y, z: z), Vetor3(x: -x, y: y, z: -z)]
    case .topo:
        return [Vetor3(x: -x, y: -y, z: -z), Vetor3(x: x, y: -y, z: -z),
                Vetor3(x: x, y: -y, z: z), Vetor3(x: -x, y: -y, z: z)]
    case .lateralDireita:
        /// ⚠️ Com a caixa **aberta** ela é só a metade de trás: a metade da frente
        /// viaja com a tampa (ver `cantosDaMeiaLateral`). Uma caixa são duas
        /// conchas presas na lombada, e é na fresta do meio desta lateral que ela
        /// se parte.
        if abertura != 0 {
            return [Vetor3(x: x, y: -y, z: 0), Vetor3(x: x, y: -y, z: -z),
                    Vetor3(x: x, y: y, z: -z), Vetor3(x: x, y: y, z: 0)]
        }
        return [Vetor3(x: x, y: -y, z: z), Vetor3(x: x, y: -y, z: -z),
                Vetor3(x: x, y: y, z: -z), Vetor3(x: x, y: y, z: z)]
    case .base:
        /// Espelho do topo, e por isso a ordem inverte: um lado visto de baixo tem
        /// a frente onde o de cima tem o fundo.
        return [Vetor3(x: -x, y: y, z: z), Vetor3(x: x, y: y, z: z),
                Vetor3(x: x, y: y, z: -z), Vetor3(x: -x, y: y, z: -z)]
    }
}

/// A **meia-lateral que viaja com a tampa** — o lábio da concha da frente.
///
/// ⚠️ Ela existe porque o dono viu: «a abertura do DVD e do VHS está abrindo na
/// frente somente, deveria abrir na metade da lateral». Só a capa girando lê como
/// tampa chapada colada numa caixa maciça.
///
/// ⚠️ A rotação é a **mesma conta** da capa, duplicada de propósito: são duas
/// peças de uma concha rígida, e o dia em que a dobradiça mudar as duas têm de
/// mudar juntas.
func cantosDaMeiaLateral(_ m: Medidas, abertura: CGFloat) -> [Vetor3] {
    let x = m.largura / 2, y = m.altura / 2, z = m.espessura / 2
    let crus = [Vetor3(x: x, y: -y, z: z), Vetor3(x: x, y: -y, z: 0),
                Vetor3(x: x, y: y, z: 0), Vetor3(x: x, y: y, z: z)]
    guard abertura != 0 else { return crus }

    let a = abertura * .pi / 180
    return crus.map { p in
        let dx = p.x + x, dz = p.z - z
        return Vetor3(x: dx * cos(a) - dz * sin(a) - x, y: p.y,
                      z: dx * sin(a) + dz * cos(a) + z)
    }
}

/// Gira um ponto pela pose — **Y primeiro, X depois**.
///
/// ⚠️ A ordem importa: invertida, a caixa inclinada giraria em torno de um eixo
/// que já saiu do lugar, e o movimento pareceria cambalhota em vez de giro sobre
/// a prateleira.
///
/// ⚠️ E os sinais são os do **gesto**, não os do CSS: X positivo mostra o topo
/// (arrastar pra cima tomba a caixa pra frente), e Y positivo traz a lombada pra
/// frente. Quem chama **subtrai** o arrasto — no Android a primeira versão somava
/// e o dono viu na hora: «parece que a caixa vai pro lado contrário do dedo».
func girado(_ v: Vetor3, _ pose: Pose) -> Vetor3 {
    let ry = pose.giroY * .pi / 180
    let rx = pose.giroX * .pi / 180

    let x1 = v.x * cos(ry) + v.z * sin(ry)
    let z1 = -v.x * sin(ry) + v.z * cos(ry)

    let y2 = v.y * cos(rx) + z1 * sin(rx)
    let z2 = -v.y * sin(rx) + z1 * cos(rx)

    return Vetor3(x: x1, y: y2, z: z2)
}

/// A perspectiva: quanto mais perto de quem olha, maior.
///
/// ⚠️ O piso de 1 é rede contra divisão por zero. Não acontece nos ângulos desta
/// tela — a câmera está a oito larguras —, mas um `NaN` num canto apaga a face
/// inteira sem dizer nada, e isso é caro demais pra depender de «não acontece».
func projetado(_ v: Vetor3, distancia: CGFloat) -> CGPoint {
    let escala = distancia / max(distancia - v.z, 1)
    return CGPoint(x: v.x * escala, y: v.y * escala)
}

/// Os quatro cantos de um lado, **já projetados**, no espaço do desenho.
func cantosNaTela(
    _ lado: Lado, pose: Pose, m: Medidas, distancia: CGFloat,
    abertura: CGFloat = 0, centro: CGPoint,
) -> [CGPoint] {
    cantosDoLado(lado, m, abertura: abertura).map { canto in
        let p = projetado(girado(canto, pose), distancia: distancia)
        return CGPoint(x: p.x + centro.x, y: p.y + centro.y)
    }
}

/// A normal do lado depois de girado — calculada **dos próprios cantos**.
///
/// ⚠️ De propósito, e não de uma tabela: uma tabela de normais é uma segunda
/// verdade sobre a mesma caixa, e o dia em que um canto mudar de sinal e a tabela
/// não, o recorte passa a esconder a face errada.
func normalGirada(_ lado: Lado, pose: Pose, m: Medidas, abertura: CGFloat = 0) -> Vetor3 {
    let c = cantosDoLado(lado, m, abertura: abertura).map { girado($0, pose) }
    let u = Vetor3(x: c[1].x - c[0].x, y: c[1].y - c[0].y, z: c[1].z - c[0].z)
    let v = Vetor3(x: c[3].x - c[0].x, y: c[3].y - c[0].y, z: c[3].z - c[0].z)
    return Vetor3(x: u.y * v.z - u.z * v.y,
                  y: u.z * v.x - u.x * v.z,
                  z: u.x * v.y - u.y * v.x)
}

/// Este lado está virado pra quem olha?
///
/// ⚠️ É o **recorte de face de costas**, e não é otimização: sem ele a contracapa
/// é desenhada por cima da capa em metade dos ângulos, e o que se vê é a sinopse
/// espelhada sobre o pôster.
func deFrente(_ lado: Lado, pose: Pose, m: Medidas, abertura: CGFloat = 0) -> Bool {
    normalGirada(lado, pose: pose, m: m, abertura: abertura).z > 0
}

/// A que profundidade está o centro do lado. Maior = mais perto.
func profundidade(_ lado: Lado, pose: Pose, m: Medidas, abertura: CGFloat = 0) -> CGFloat {
    let zs = cantosDoLado(lado, m, abertura: abertura).map { girado($0, pose).z }
    return zs.reduce(0, +) / CGFloat(zs.count)
}

/// Os lados visíveis, **do mais fundo pro mais próximo** — a ordem do pintor.
///
/// ⚠️ Sem ela a lombada some por baixo da capa em vez de encostar nela.
func ladosVisiveis(pose: Pose, m: Medidas, abertura: CGFloat = 0) -> [Lado] {
    Lado.allCases
        .filter { deFrente($0, pose: pose, m: m, abertura: abertura) }
        .sorted { profundidade($0, pose: pose, m: m, abertura: abertura)
            < profundidade($1, pose: pose, m: m, abertura: abertura) }
}

/// Quanta luz este lado pega, de 0 a 1.
///
/// ⚠️ **Nunca preto**: um lado que foge da luz continua iluminado pelo ambiente
/// da loja. 0,45 é o piso, e é o que impede a lombada de virar um buraco quando a
/// caixa está quase de frente.
func luzDoLado(_ lado: Lado, pose: Pose, m: Medidas, abertura: CGFloat = 0) -> CGFloat {
    let n = normalGirada(lado, pose: pose, m: m, abertura: abertura)
    let tamanho = max(sqrt(n.x * n.x + n.y * n.y + n.z * n.z), 0.0001)
    let incidencia = min(max((-n.y / tamanho) * 0.45 + (n.z / tamanho) * 0.89, 0), 1)
    return 0.45 + 0.55 * incidencia
}
