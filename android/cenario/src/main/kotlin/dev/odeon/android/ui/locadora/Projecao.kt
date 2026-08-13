package dev.odeon.android.ui.locadora

import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/// A projeção 3D da caixa — **uma câmera para todas as faces**.
///
/// ## O defeito que este arquivo existe pra consertar
///
/// Até 05/08/2026 a caixa era montada com uma `graphicsLayer` por face, cada uma
/// com o próprio `rotationY` e o próprio `transformOrigin`. O comentário que
/// estava lá dizia a verdade inteira:
///
/// > «como as camadas não dividem o mesmo ponto de fuga, a junta só fecha **na
/// > pose de repouso**. Por isso a pose é fixa e não acompanha o dedo — animar o
/// > ângulo abriria a junta no meio do caminho.»
///
/// Ou seja: o que impedia o dedo de girar a caixa não era o gesto, era a
/// geometria. Duas faces com câmeras diferentes são dois desenhos que se
/// encontram por coincidência num ângulo só.
///
/// ## Como funciona: oito vértices, uma câmera, quatro homografias
///
/// A caixa é um paralelepípedo com o centro na origem. Cada lado é um retângulo
/// de quatro cantos **no espaço da caixa**; girar a caixa é girar os cantos,
/// projetar é dividir pela profundidade, e desenhar é mapear o retângulo do
/// conteúdo nos quatro pontos que saíram.
///
/// É o que o `transform-style: preserve-3d` do CSS faz de graça, escrito à mão —
/// e a parte que importa é que a **câmera é uma só**, então as arestas de duas
/// faces que se tocam na caixa se tocam na tela, em qualquer ângulo.
///
/// ## ⚠️ Por que não a `Matrix` 4×4 do Compose
///
/// Foi a primeira tentativa, e a prateleira apareceu **vazia**. O
/// `DrawTransform.transform` converte a 4×4 numa `android.graphics.Matrix`, que
/// é 3×3 — e a conversão do Compose recusa transformações com componente em `z`,
/// que é exatamente o que uma caixa tem.
///
/// `setPolyToPoly` não tem esse problema: ela recebe quatro pontos de origem e
/// quatro de destino e monta a homografia. É a mesma matemática, entrando pela
/// porta que o Android abre — e de quebra deixa a projeção ser **testável**, que
/// uma matriz montada dentro de um `Composable` nunca seria.
///
/// ## Por que não OpenGL
///
/// A `APP-ANDROID.md` §3 supunha que a estante 3D exigiria «uma superfície
/// OpenGL/Compose com render próprio, e isso é um projeto dentro do projeto».
/// Exigiria, se a cena fosse uma cena. Uma **caixa** são quatro retângulos.
///
/// E ficar no Compose tem um ganho que OpenGL não daria: a capa continua sendo
/// um `AsyncImage` do Coil, com o mesmo cache e o mesmo token de mídia. Numa
/// superfície própria, cada pôster viraria uma textura pra carregar à mão.

/// Um ponto no espaço da caixa. A origem é o **centro** dela, `y` cresce pra
/// baixo — a convenção da tela — e `z` cresce na direção de quem olha.
data class Vetor3(val x: Float, val y: Float, val z: Float)

/// Os seis lados da caixa.
///
/// ## ⚠️ Eram quatro, e o dono viu o buraco
///
/// A primeira versão desenhava capa, lombada, topo e contracapa, com o
/// argumento escrito aqui: «a base e a lateral direita nunca aparecem, porque a
/// caixa gira no máximo ±42°».
///
/// O argumento morreu junto com o teto de 42°. Com o giro dando a volta inteira,
/// a **lateral direita** aparece em todo o caminho entre a capa e o verso — e o
/// que se via ali era o vazio atrás da caixa. Uma caixa com um lado faltando não
/// é uma caixa: é um cenário de teatro visto de trás.
///
/// A base entra pelo mesmo motivo, e é mais barata ainda: ela só aparece quando
/// se olha a caixa de baixo, mas quando aparece, aparece.
enum class Lado { Capa, Lombada, Topo, Contracapa, LateralDireita, Base }

/// A pose da caixa: quanto ela girou em cada eixo.
data class Pose(val giroY: Float = POSE_DE_REPOUSO_Y, val giroX: Float = POSE_DE_REPOUSO_X) {

    /// ## ⚠️ O teto de 42° vale na estante, **não** no palco
    ///
    /// Ele veio da web, e lá fazia sentido: a caixa da vitrine é uma miniatura
    /// que gira um pouco pra mostrar a lombada. Na mão é outra coisa — o dono
    /// pediu «quando você move com o dedo, dá pra ver o verso também», e um
    /// limite de 42° é justamente o que impede isso: o verso começa a aparecer
    /// depois dos 90°.
    ///
    /// Com `livre`, o giro horizontal **dá a volta inteira** e o vertical
    /// continua limitado. Não é assimetria por preguiça: uma caixa que tomba
    /// 180° pra frente fica de cabeça pra baixo, e ninguém vira uma caixa assim
    /// na mão.
    fun somando(dx: Float, dy: Float, livre: Boolean = false): Pose = Pose(
        giroY = if (livre) daVolta(giroY + dx) else (giroY + dx).coerceIn(-TETO, TETO),
        giroX = (giroX + dy).coerceIn(-TETO, TETO),
    )

    /// A face que está virada pra quem olha: `false` é a capa, `true` é o verso.
    val mostrandoOVerso: Boolean get() = kotlin.math.abs(daVolta(giroY)) > 90f

    companion object {
        /// A pose de repouso é a da folha da web (`styles.css:4256`):
        /// `rotateX(3deg) rotateY(22deg)`. Uma caixa de frente é uma capa; uma
        /// caixa a 22° é um objeto numa prateleira.
        const val POSE_DE_REPOUSO_Y = 22f
        const val POSE_DE_REPOUSO_X = 3f

        /// ±42°, o mesmo teto da web. Passar disso mostra a base e a traseira,
        /// que não são desenhadas.
        const val TETO = 42f

        /// Quanto cada pixel de arrasto vale em grau — os dois números são da
        /// web. A diferença entre eles não é descuido: o polegar anda muito mais
        /// na horizontal do que na vertical, e igualar os dois faria a caixa
        /// tombar ao menor tremor da mão.
        const val GRAU_POR_PIXEL_HORIZONTAL = 0.5f
        const val GRAU_POR_PIXEL_VERTICAL = 0.32f

        /// Mantém o ângulo em −180..180.
        ///
        /// Sem isto, girar a caixa dez vezes acumularia 3.600° — e a animação de
        /// volta ao repouso desenrolaria as dez voltas na cara de quem soltou.
        fun daVolta(graus: Float): Float {
            var g = graus % 360f
            if (g > 180f) g -= 360f
            if (g < -180f) g += 360f
            return g
        }

        /// ⚠️ **Aqui morava a `faceAlvo`** — o encaixe que assentava a caixa na
        /// face mais próxima ao fim da inércia, e que custou três versões pra
        /// acertar (as duas primeiras apareceram em foto). Foi removida em
        /// 07/08/2026 por decisão do dono: «quero mover livremente e parar onde
        /// quiser». O giro hoje é `animateDecay` puro — ver o `onDragEnd` da
        /// `CaixaEm3D`.
    }
}

/// As medidas da caixa, em pixels.
data class Medidas(val largura: Float, val altura: Float, val espessura: Float)

/// O tamanho do desenho de cada lado.
///
/// A lombada é espessura × altura; o topo é largura × espessura. Trocar os dois
/// desenha uma caixa que parece certa até alguém reparar que o título da lombada
/// está deitado.
fun tamanhoDoLado(lado: Lado, m: Medidas): Pair<Float, Float> = when (lado) {
    Lado.Capa, Lado.Contracapa -> m.largura to m.altura
    Lado.Lombada, Lado.LateralDireita -> m.espessura to m.altura
    Lado.Topo, Lado.Base -> m.largura to m.espessura
}

/// Os quatro cantos de um lado, no espaço da caixa, **na ordem do conteúdo**:
/// canto superior esquerdo, superior direito, inferior direito, inferior
/// esquerdo — como o desenho de um `Composable` os enxerga.
///
/// ## A ordem é o que orienta a face, e ela tem consequência visível
///
/// A contracapa é lida de trás: o canto esquerdo **do conteúdo** é o direito da
/// caixa. Com a ordem ingênua, o verso sai espelhado — e texto espelhado é o
/// tipo de defeito que passa despercebido num teste e salta num screenshot.
///
/// Na lombada e no topo, a borda de cima do conteúdo é a que fica **atrás** na
/// caixa. É o que faz o título da lombada correr no sentido certo.
fun cantosDoLado(lado: Lado, m: Medidas, abertura: Float = 0f): List<Vetor3> {
    val x = m.largura / 2f
    val y = m.altura / 2f
    val z = m.espessura / 2f

    /// A capa aberta gira **em torno da dobradiça**, que é a aresta da lombada.
    ///
    /// Não é o centro: uma tampa que gira pelo meio atravessa a própria caixa. A
    /// dobradiça de uma caixa de fita fica do lado da lombada, e a abertura é a
    /// aresta oposta — que é exatamente onde o dedo toca pra abrir.
    ///
    /// ## ⚠️ E ela abre **pra fora** — o sinal esteve trocado até 07/08/2026
    ///
    /// A primeira versão girava a borda livre pra `z` negativo — pra **dentro**
    /// da tela, como uma porta abrindo pro lado de lá. O dono viu: «ele tá
    /// abrindo pra dentro e não pra fora». Estojo na mão abre com a tampa vindo
    /// na direção de quem segura; é o seno somando no `z`, e não subtraindo.
    ///
    /// ## ⚠️ E o eixo estava a meia espessura de distância — a tampa voava
    ///
    /// > «a porta da capa não tá conectada à lateral, fica meio que voando»
    ///
    /// O `x` era deslocado pra pôr o zero na aresta esquerda (`dx`), e o `z`
    /// **não era** — ia cru. Com isso a tampa girava em torno de
    /// `(−largura/2, z = 0)`, o **plano do meio** da espessura, e não da aresta
    /// da frente `(−largura/2, +espessura/2)`, que é onde a tampa encontra a
    /// lombada de verdade.
    ///
    /// O sintoma é geométrico e exato: o ponto que deveria ficar cravado se
    /// deslocava ~0,86 × a espessura nos 118° de abertura. Por isso a fita
    /// incomodava mais que o disco — 24% de espessura contra 10%, mais que o
    /// dobro de vão.
    ///
    /// Com `dz`, a dobradiça fica **parada**: em qualquer ângulo, a aresta
    /// esquerda da tampa é a mesma aresta da caixa. O forro do interior vem
    /// consertado junto, porque sai destes mesmos cantos.
    if (abertura != 0f && lado == Lado.Capa) {
        val a = Math.toRadians(abertura.toDouble())
        return cantosDoLado(Lado.Capa, m).map { p ->
            val dx = p.x + x
            val dz = p.z - z
            Vetor3(
                x = (dx * cos(a) - dz * sin(a)).toFloat() - x,
                y = p.y,
                z = (dx * sin(a) + dz * cos(a)).toFloat() + z,
            )
        }
    }

    return when (lado) {
        Lado.Capa -> listOf(
            Vetor3(-x, -y, z), Vetor3(x, -y, z), Vetor3(x, y, z), Vetor3(-x, y, z),
        )
        Lado.Contracapa -> listOf(
            Vetor3(x, -y, -z), Vetor3(-x, -y, -z), Vetor3(-x, y, -z), Vetor3(x, y, -z),
        )
        Lado.Lombada -> listOf(
            Vetor3(-x, -y, -z), Vetor3(-x, -y, z), Vetor3(-x, y, z), Vetor3(-x, y, -z),
        )
        Lado.Topo -> listOf(
            Vetor3(-x, -y, -z), Vetor3(x, -y, -z), Vetor3(x, -y, z), Vetor3(-x, -y, z),
        )
        /// A lateral **da abertura** — o espelho da lombada, do outro lado.
        ///
        /// ⚠️ Com a caixa **aberta** ela é só a metade de trás: ver
        /// [cantosDaMeiaLateral], que carrega a metade da frente junto com a
        /// tampa. Uma caixa é duas conchas presas na lombada, e é na fresta do
        /// meio desta lateral que ela se parte.
        Lado.LateralDireita -> if (abertura != 0f) {
            listOf(
                Vetor3(x, -y, 0f), Vetor3(x, -y, -z), Vetor3(x, y, -z), Vetor3(x, y, 0f),
            )
        } else {
            listOf(
                Vetor3(x, -y, z), Vetor3(x, -y, -z), Vetor3(x, y, -z), Vetor3(x, y, z),
            )
        }
        /// A base. Espelho do topo, e por isso a ordem dos cantos inverte: um
        /// lado visto de baixo tem a frente onde o de cima tem o fundo.
        Lado.Base -> listOf(
            Vetor3(-x, y, z), Vetor3(x, y, z), Vetor3(x, y, -z), Vetor3(-x, y, -z),
        )
    }
}

/// A **meia-lateral que viaja com a tampa** — o lábio da concha da frente.
///
/// ## Por que ela existe — 07/08/2026
///
/// > «a abertura do DVD e do VHS está abrindo na frente somente, deveria abrir
/// > na metade da lateral de abertura»
///
/// Até aqui só a **capa** girava, e a lateral da abertura ficava inteira, parada.
/// O olho lia o que estava desenhado: uma tampa chapada colada na frente de uma
/// caixa maciça. Uma caixa de verdade são **duas conchas** presas na lombada, e
/// a fresta que a `LateralDireita` sempre desenhou no meio da espessura é
/// exatamente onde ela se abre.
///
/// Então a lateral se parte: a metade de trás fica com a contracapa (ver
/// [cantosDoLado]) e esta metade da frente vira o lábio da tampa — o mesmo
/// retângulo, de `z = 0` até a face da capa, girando na mesma dobradiça.
///
/// A rotação é a **mesma conta** da capa, e é de propósito que ela esteja
/// duplicada aqui em vez de fatorada: são duas peças de uma concha rígida, e o
/// dia em que a dobradiça mudar as duas têm que mudar juntas — o teste
/// `a meia-lateral acompanha a tampa` é quem garante isso.
fun cantosDaMeiaLateral(m: Medidas, abertura: Float): List<Vetor3> {
    val x = m.largura / 2f
    val y = m.altura / 2f
    val z = m.espessura / 2f

    val crus = listOf(
        Vetor3(x, -y, z), Vetor3(x, -y, 0f), Vetor3(x, y, 0f), Vetor3(x, y, z),
    )
    if (abertura == 0f) return crus

    val a = Math.toRadians(abertura.toDouble())
    return crus.map { p ->
        val dx = p.x + x
        val dz = p.z - z
        Vetor3(
            x = (dx * cos(a) - dz * sin(a)).toFloat() - x,
            y = p.y,
            z = (dx * sin(a) + dz * cos(a)).toFloat() + z,
        )
    }
}

/// Gira um ponto pela pose — primeiro em torno de Y, depois de X.
///
/// A ordem importa: invertida, a caixa inclinada giraria em torno de um eixo que
/// já saiu do lugar, e o movimento pareceria cambalhota em vez de giro sobre a
/// prateleira.
///
/// ⚠️ O sinal de X é **positivo = mostra o topo**, e não a convenção do CSS. É a
/// convenção do gesto: arrastar o dedo pra cima tomba a caixa pra frente e
/// revela a tampa, que é o que a mão espera de um objeto apoiado pela borda de
/// baixo.
///
/// ⚠️ E o de Y é **positivo = a lombada vem pra frente**, ou seja, o lado
/// esquerdo se aproxima. Quem arrasta o dedo **pra esquerda** espera isso — a
/// mão empurra a face da frente pra esquerda e o lado esquerdo gira em direção a
/// ela. Por isso quem chama subtrai o arrasto em vez de somar; ver `CaixaEm3D`,
/// onde a primeira versão somava e o dono viu na hora: «parece que a caixa vai
/// pro lado contrário do dedo».
fun girado(v: Vetor3, pose: Pose): Vetor3 {
    val ry = Math.toRadians(pose.giroY.toDouble())
    val rx = Math.toRadians(pose.giroX.toDouble())

    val x1 = (v.x * cos(ry) + v.z * sin(ry)).toFloat()
    val z1 = (-v.x * sin(ry) + v.z * cos(ry)).toFloat()

    val y2 = (v.y * cos(rx) + z1 * sin(rx)).toFloat()
    val z2 = (-v.y * sin(rx) + z1 * cos(rx)).toFloat()

    return Vetor3(x1, y2, z2)
}

/// A projeção em perspectiva: quanto mais perto de quem olha, maior.
///
/// `distancia` é o mesmo papel do `cameraDistance` da `graphicsLayer` — a que
/// distância está o olho. Quanto menor, mais forte a perspectiva.
///
/// ⚠️ O `coerceAtLeast` é uma rede de segurança contra a divisão por zero: um
/// ponto **na** câmera projetaria no infinito. Não acontece com os ângulos desta
/// tela — a caixa é pequena e a câmera está a oito larguras —, mas um `NaN` num
/// canto apaga a face inteira sem dizer nada, e isso é caro demais pra deixar
/// depender de "não acontece".
fun projetado(v: Vetor3, distancia: Float): Pair<Float, Float> {
    val escala = distancia / (distancia - v.z).coerceAtLeast(1f)
    return v.x * escala to v.y * escala
}

/// Os quatro cantos de um lado, **já projetados**, em coordenadas do elemento
/// que vai desenhá-lo — origem no canto superior esquerdo dele.
///
/// Devolve os oito números na ordem que o `setPolyToPoly` espera: `x0, y0, x1,
/// y1, …`.
fun cantosNaTela(
    lado: Lado,
    pose: Pose,
    m: Medidas,
    distancia: Float,
    /// Quanto a capa está aberta, em graus. Zero pra caixa fechada.
    abertura: Float = 0f,
    /// Onde fica o centro da caixa dentro do elemento. É meia face, porque cada
    /// lado é desenhado num elemento do tamanho da própria face, centrado na
    /// caixa.
    centroX: Float,
    centroY: Float,
): FloatArray {
    val saida = FloatArray(8)
    cantosDoLado(lado, m, abertura).forEachIndexed { i, canto ->
        val (px, py) = projetado(girado(canto, pose), distancia)
        saida[i * 2] = px + centroX
        saida[i * 2 + 1] = py + centroY
    }
    return saida
}

/// A normal do lado depois de girado — calculada **dos próprios cantos**.
///
/// Sai da mesma lista que o desenho usa, e é de propósito: uma tabela de normais
/// à parte é uma segunda verdade sobre a mesma caixa, e o dia em que um canto
/// mudar de sinal e a tabela não, o recorte passa a esconder a face errada.
fun normalGirada(lado: Lado, pose: Pose, m: Medidas, abertura: Float = 0f): Vetor3 {
    val c = cantosDoLado(lado, m, abertura).map { girado(it, pose) }
    val u = Vetor3(c[1].x - c[0].x, c[1].y - c[0].y, c[1].z - c[0].z)
    val v = Vetor3(c[3].x - c[0].x, c[3].y - c[0].y, c[3].z - c[0].z)
    return Vetor3(
        x = u.y * v.z - u.z * v.y,
        y = u.z * v.x - u.x * v.z,
        z = u.x * v.y - u.y * v.x,
    )
}

/// Este lado está virado pra quem olha?
///
/// ⚠️ É o **recorte de face de costas**, e ele não é otimização: sem ele a
/// contracapa é desenhada por cima da capa em metade dos ângulos, e o que se vê
/// é a sinopse espelhada sobre o pôster.
fun deFrente(lado: Lado, pose: Pose, m: Medidas, abertura: Float = 0f): Boolean =
    normalGirada(lado, pose, m, abertura).z > 0f

/// A que profundidade está o centro do lado. Maior = mais perto de quem olha.
internal fun profundidade(lado: Lado, pose: Pose, m: Medidas, abertura: Float = 0f): Float =
    cantosDoLado(lado, m, abertura).map { girado(it, pose).z }.average().toFloat()

/// Os lados visíveis, **do mais fundo pro mais próximo**.
///
/// A ordem é a do pintor: quem está atrás vai primeiro e é coberto por quem está
/// na frente. Sem ela, a lombada some por baixo da capa em vez de encostar nela.
fun ladosVisiveis(pose: Pose, m: Medidas, abertura: Float = 0f): List<Lado> =
    Lado.entries
        .filter { deFrente(it, pose, m, abertura) }
        .sortedBy { profundidade(it, pose, m, abertura) }

/// O quanto a caixa "pega luz" neste ângulo, de 0 a 1.
///
/// ## Ela não é enfeite: é o que faz o giro ser sentido
///
/// Numa caixa de faces chapadas, girar 20° muda pouco na tela — as arestas andam
/// alguns pixels e o olho não registra volume. O que denuncia volume é a **luz
/// mudando de face**: a capa clareia quando vira pra frente e a lombada escurece
/// quando foge, como aconteceria com um objeto de verdade sob a luz da loja.
///
/// É um difuso de uma fonte só, vindo de cima e da frente — a posição do
/// letreiro da estante, que é o `Luz.kt` aplicado a um objeto em vez de a um
/// lugar.
fun luzNoLado(lado: Lado, pose: Pose, m: Medidas, abertura: Float = 0f): Float {
    val n = normalGirada(lado, pose, m, abertura)
    val tamanho = kotlin.math.sqrt(n.x * n.x + n.y * n.y + n.z * n.z).coerceAtLeast(0.0001f)
    val incidencia = ((-n.y / tamanho) * 0.45f + (n.z / tamanho) * 0.89f).coerceIn(0f, 1f)
    /// Nunca preto: um lado que foge da luz continua iluminado pelo ambiente da
    /// loja. 0,45 é o piso, e é o que impede a lombada de virar um buraco quando
    /// a caixa está quase de frente.
    return 0.45f + 0.55f * incidencia
}

/// O quanto a pose se afastou do repouso, em graus somados.
internal fun distanciaDoRepouso(pose: Pose): Float =
    abs(pose.giroY - Pose.POSE_DE_REPOUSO_Y) + abs(pose.giroX - Pose.POSE_DE_REPOUSO_X)

/// O ponto está dentro do quadrilátero projetado?
///
/// ## Por que o toque precisa disto
///
/// O alvo de abrir era «a metade direita do retângulo do componente» — mas a
/// caixa **projetada** não mora no retângulo: girou, ela desloca e deforma, e o
/// dedo em cima da abertura visível caía fora da conta. O dono sentiu: «apertar
/// na abertura deveria abrir também, não somente no canto do frontal». O toque
/// certo é contra os mesmos quatro cantos que o desenho usa — uma verdade só.
///
/// A conta é a clássica: o ponto está do **mesmo lado** das quatro arestas
/// (produto vetorial com sinal constante). Aceita o quadrilátero em qualquer
/// orientação — horária ou anti — porque só exige consistência, não um sentido.
fun dentroDoQuad(cantos: FloatArray, x: Float, y: Float): Boolean {
    var positivo = false
    var negativo = false
    for (i in 0 until 4) {
        val j = (i + 1) % 4
        val cruz = (cantos[j * 2] - cantos[i * 2]) * (y - cantos[i * 2 + 1]) -
            (cantos[j * 2 + 1] - cantos[i * 2 + 1]) * (x - cantos[i * 2])
        if (cruz > 0f) positivo = true
        if (cruz < 0f) negativo = true
        if (positivo && negativo) return false
    }
    return true
}
