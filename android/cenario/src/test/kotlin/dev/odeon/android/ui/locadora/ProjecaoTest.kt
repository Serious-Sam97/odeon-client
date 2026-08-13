package dev.odeon.android.ui.locadora

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes da projeção da caixa.
///
/// ## Por que uma caixa desenhada merece teste
///
/// Porque o defeito dela é **silencioso**. Uma face com a normal invertida não
/// estoura, não avisa e não some: ela é desenhada por cima da capa, e o que
/// aparece na tela é o pôster com a sinopse espelhada em cima — que parece
/// problema de arte, e é de sinal.
///
/// E porque esta é a única parte do app que é geometria pura. O screenshot pega
/// o resto; a ordem de pintura de quatro faces em 42° de giro, não.
class ProjecaoTest {

    private val medidas = Medidas(largura = 96f, altura = 144f, espessura = 26f)

    @Test
    fun `no repouso a caixa mostra capa, lombada e topo`() {
        /// A pose de repouso é a da web — 22° na horizontal, 3° na vertical. É
        /// justamente o ângulo em que os três lados aparecem, e é por isso que
        /// ele foi escolhido lá: uma caixa que mostra três lados é uma caixa.
        val visiveis = ladosVisiveis(Pose(), medidas).toSet()

        assertTrue("a capa some no repouso", Lado.Capa in visiveis)
        assertTrue("a lombada some no repouso", Lado.Lombada in visiveis)
        assertTrue("o topo some no repouso", Lado.Topo in visiveis)
        assertFalse("a contracapa não pode aparecer de frente", Lado.Contracapa in visiveis)
    }

    @Test
    fun `os lados saem em ordem de profundidade`() {
        /// ## ⚠️ O primeiro palpite estava errado, e vale ficar escrito
        ///
        /// Este teste dizia «a capa é desenhada por último, porque está na
        /// frente». Ele reprovou — e o certo era ele, não o código: no repouso a
        /// **lombada** tem o centro mais perto de quem olha que a capa, porque a
        /// caixa está girada 22° e a lombada avançou.
        ///
        /// E aí veio a parte que importa: **a ordem não muda nada.** A caixa é
        /// convexa, e as faces de frente de um sólido convexo não se sobrepõem
        /// na tela — cada uma ocupa um pedaço do contorno e só. A ordem existe
        /// pra o caso de as arestas se tocarem em pixels compartilhados, não pra
        /// evitar uma face cobrir a outra.
        ///
        /// O que o teste guarda, então, é a promessa da função: sair ordenado.
        val ordem = ladosVisiveis(Pose(), medidas)
        val profundidades = ordem.map { profundidade(it, Pose(), medidas) }

        assertEquals(profundidades.sorted(), profundidades)
    }

    @Test
    fun `a caixa e fechada — de qualquer angulo ha sempre face virada pra frente`() {
        /// ## O teste que o dono provocou
        ///
        /// «Melhore muito a caixa, adicione todas as áreas.» Ela tinha quatro
        /// faces, e o giro passou a dar a volta inteira: entre a capa e o verso,
        /// a **lateral da abertura** ficava sem nada, e o que aparecia era o
        /// fundo da tela atravessando o objeto.
        ///
        /// Com os seis lados, um sólido fechado — e a propriedade que isso
        /// garante é esta: **não existe ângulo sem face de frente**. Se um dia
        /// alguém tirar um lado, este teste cai antes de a foto denunciar.
        var g = -180
        while (g <= 180) {
            var x = -42
            while (x <= 42) {
                val pose = Pose(giroY = g.toFloat(), giroX = x.toFloat())
                assertTrue(
                    "nenhuma face de frente em giroY=$g giroX=$x",
                    ladosVisiveis(pose, medidas).isNotEmpty(),
                )
                x += 14
            }
            g += 5
        }
    }

    @Test
    fun `de costas aparece a contracapa, e a capa some`() {
        /// O verso é o pedido «dá pra ver o verso também» virado em teste: a meia
        /// volta tem que trocar as duas faces, e não mostrar as duas.
        val virada = ladosVisiveis(Pose(giroY = 180f - 22f, giroX = 3f), medidas).toSet()

        assertTrue(Lado.Contracapa in virada)
        assertFalse(Lado.Capa in virada)
    }

    @Test
    fun `a lateral da abertura aparece no meio do caminho`() {
        /// Entre a capa e o verso passa-se pela lateral direita — o lado que não
        /// existia. Em 80° ela é a face que mais se vê.
        val meio = ladosVisiveis(Pose(giroY = -80f, giroX = 3f), medidas).toSet()

        assertTrue("a lateral da abertura sumiu no meio do giro", Lado.LateralDireita in meio)
    }

    @Test
    fun `girando pro outro lado, a lombada some`() {
        /// A lombada é a face esquerda: ela só aparece quando a caixa gira
        /// mostrando o lado esquerdo. Ao girar pro outro lado ela some — e não
        /// aparece a direita no lugar, porque a direita não é desenhada (e nunca
        /// chega a ficar de frente dentro do teto de 42°).
        val visiveis = ladosVisiveis(Pose(giroY = -30f, giroX = 3f), medidas).toSet()

        assertTrue(Lado.Capa in visiveis)
        assertFalse(Lado.Lombada in visiveis)
    }

    @Test
    fun `inclinando pra tras, o topo some`() {
        /// Olhar a caixa de baixo esconde o topo. Sem o recorte, ele continuaria
        /// desenhado — espelhado, e por cima da capa.
        val visiveis = ladosVisiveis(Pose(giroY = 22f, giroX = -30f), medidas).toSet()

        assertFalse(Lado.Topo in visiveis)
    }

    @Test
    fun `o teto de 42 graus vale nos dois eixos`() {
        val pose = Pose().somando(dx = 999f, dy = 999f)
        assertEquals(Pose.TETO, pose.giroY, 0.001f)
        assertEquals(Pose.TETO, pose.giroX, 0.001f)

        val outroLado = Pose().somando(dx = -999f, dy = -999f)
        assertEquals(-Pose.TETO, outroLado.giroY, 0.001f)
        assertEquals(-Pose.TETO, outroLado.giroX, 0.001f)
    }

    @Test
    fun `os cantos poem cada lado no seu plano`() {
        /// A lombada inteira mora em `x = -48`; o topo inteiro em `y = -72`. Um
        /// canto fora do plano é uma face torta — e torta ela ainda desenha,
        /// só que com a aresta descolada da vizinha.
        assertTrue(cantosDoLado(Lado.Lombada, medidas).all { it.x == -48f })
        assertTrue(cantosDoLado(Lado.Topo, medidas).all { it.y == -72f })
        assertTrue(cantosDoLado(Lado.Capa, medidas).all { it.z == 13f })
        assertTrue(cantosDoLado(Lado.Contracapa, medidas).all { it.z == -13f })
    }

    @Test
    fun `as faces vizinhas dividem a mesma aresta`() {
        /// **É o teste que existe pra provar o conserto.** A junta entre capa e
        /// lombada abria na versão de camadas; aqui as duas leem o mesmo canto
        /// do mesmo objeto, então elas coincidem por construção — em qualquer
        /// pose, e não só na de repouso.
        listOf(Pose(), Pose(0f, 0f), Pose(42f, 42f), Pose(-30f, 12f)).forEach { pose ->
            val capa = cantosNaTela(Lado.Capa, pose, medidas, 800f, 0f, 0f, 0f)
            val lombada = cantosNaTela(Lado.Lombada, pose, medidas, 800f, 0f, 0f, 0f)

            /// O canto superior esquerdo da capa é o superior **direito** da
            /// lombada: é a aresta em que as duas se encontram.
            assertEquals("x da aresta de cima em $pose", capa[0], lombada[2], 0.01f)
            assertEquals("y da aresta de cima em $pose", capa[1], lombada[3], 0.01f)
            /// E o inferior esquerdo da capa é o inferior direito da lombada.
            assertEquals("x da aresta de baixo em $pose", capa[6], lombada[4], 0.01f)
            assertEquals("y da aresta de baixo em $pose", capa[7], lombada[5], 0.01f)
        }
    }

    @Test
    fun `a perspectiva aumenta o que esta mais perto`() {
        /// Sem isto a caixa é uma projeção ortogonal — arestas paralelas, que o
        /// olho lê como desenho técnico e não como objeto numa vitrine.
        val perto = projetado(Vetor3(10f, 0f, 60f), 800f).first
        val longe = projetado(Vetor3(10f, 0f, -60f), 800f).first

        assertTrue("o canto da frente tem que projetar mais longe do centro", perto > longe)
    }

    @Test
    fun `a lombada e espessura por altura, e o topo e largura por espessura`() {
        /// Trocar os dois desenha uma caixa que parece certa até alguém reparar
        /// que o título da lombada está deitado.
        assertEquals(26f to 144f, tamanhoDoLado(Lado.Lombada, medidas))
        assertEquals(96f to 26f, tamanhoDoLado(Lado.Topo, medidas))
        assertEquals(96f to 144f, tamanhoDoLado(Lado.Capa, medidas))
    }

    @Test
    fun `a luz muda de face conforme a caixa gira`() {
        /// É o que faz o giro ser **sentido**: com a caixa quase de frente a capa
        /// está clara e a lombada escura; girando, elas trocam de papel.
        val quaseDeFrente = Pose(giroY = 2f, giroX = 0f)
        val bemGirada = Pose(giroY = 42f, giroX = 0f)

        assertTrue(luzNoLado(Lado.Capa, quaseDeFrente, medidas) > luzNoLado(Lado.Lombada, quaseDeFrente, medidas))
        assertTrue(
            "a lombada tem que clarear ao virar pra frente",
            luzNoLado(Lado.Lombada, bemGirada, medidas) > luzNoLado(Lado.Lombada, quaseDeFrente, medidas),
        )
    }

    @Test
    fun `a dobradica fica parada em qualquer abertura`() {
        /// ## O teste que a tampa voadora pediu
        ///
        /// > «a porta da capa não tá conectada à lateral, fica meio que voando»
        ///
        /// A tampa girava em torno do **plano do meio** da espessura em vez da
        /// aresta da frente, e a dobradiça se deslocava ~0,86 × a espessura ao
        /// abrir. Não estourava nada: era uma caixa desenhada com a porta solta
        /// no ar — o tipo de defeito que só a foto pega, e este teste passa a
        /// pegar antes.
        ///
        /// A aresta da dobradiça são os cantos **0 e 3** da capa (superior e
        /// inferior esquerdos), e eles têm que ser os mesmos com a caixa
        /// fechada ou escancarada. É um invariante, não um número medido.
        val fechada = cantosDoLado(Lado.Capa, medidas)
        listOf(1f, 45f, 90f, 118f).forEach { angulo ->
            val aberta = cantosDoLado(Lado.Capa, medidas, abertura = angulo)
            listOf(0, 3).forEach { canto ->
                assertEquals(
                    "a dobradiça andou em x a $angulo°",
                    fechada[canto].x.toDouble(),
                    aberta[canto].x.toDouble(),
                    0.01,
                )
                assertEquals(
                    "a dobradiça andou em z a $angulo°",
                    fechada[canto].z.toDouble(),
                    aberta[canto].z.toDouble(),
                    0.01,
                )
            }
        }
    }

    @Test
    fun `a meia-lateral acompanha a tampa`() {
        /// A concha da frente é **rígida**: a capa e o lábio da lateral são a
        /// mesma peça de plástico. A aresta onde elas se encontram — a borda
        /// livre da capa, cantos 1 e 2 — tem que cair exatamente sobre os cantos
        /// 0 e 3 do lábio, em qualquer abertura.
        ///
        /// É este teste que autoriza a conta da dobradiça a estar escrita duas
        /// vezes: o dia em que uma mudar sem a outra, a concha racha aqui.
        listOf(0f, 30f, 90f, 118f).forEach { angulo ->
            val capa = cantosDoLado(Lado.Capa, medidas, abertura = angulo)
            val labio = cantosDaMeiaLateral(medidas, abertura = angulo)
            listOf(1 to 0, 2 to 3).forEach { (naCapa, noLabio) ->
                assertEquals(
                    "a concha rachou em x a $angulo°",
                    capa[naCapa].x.toDouble(), labio[noLabio].x.toDouble(), 0.01,
                )
                assertEquals(
                    "a concha rachou em z a $angulo°",
                    capa[naCapa].z.toDouble(), labio[noLabio].z.toDouble(), 0.01,
                )
            }
        }
    }

    @Test
    fun `a lateral da abertura se parte ao meio quando a caixa abre`() {
        /// Fechada, a lateral atravessa a espessura inteira. Aberta, ela é só a
        /// metade de trás — a da frente virou o lábio da tampa. Sem isso a caixa
        /// ficaria com uma espessura e meia, e a tampa lia como placa colada.
        val z = medidas.espessura / 2f
        val fechada = cantosDoLado(Lado.LateralDireita, medidas)
        val aberta = cantosDoLado(Lado.LateralDireita, medidas, abertura = 118f)

        assertEquals("fechada, a lateral tem a espessura toda", z.toDouble(), fechada[0].z.toDouble(), 0.01)
        assertEquals("aberta, a lateral para no meio", 0.0, aberta[0].z.toDouble(), 0.01)
        assertEquals("o fundo da lateral não pode se mexer", fechada[1].z.toDouble(), aberta[1].z.toDouble(), 0.01)
    }

    @Test
    fun `a tampa abre pra fora, na direcao de quem olha`() {
        /// O sinal do seno já esteve trocado e a tampa abria **pra dentro** da
        /// tela. A régua: a borda livre (cantos 1 e 2) tem que ganhar `z` — vir
        /// na direção de quem segura o estojo.
        val fechada = cantosDoLado(Lado.Capa, medidas)
        val aberta = cantosDoLado(Lado.Capa, medidas, abertura = 60f)
        assertTrue(
            "a borda livre foi pra dentro da tela em vez de vir pra fora",
            aberta[1].z > fechada[1].z,
        )
    }

    @Test
    fun `nenhuma face fica preta`() {
        /// O piso de 0,45 é o que impede a lombada de virar um buraco. Uma face
        /// preta não lê como lado escuro: lê como recorte.
        Lado.entries.forEach { lado ->
            listOf(-42f, -20f, 0f, 22f, 42f).forEach { giro ->
                val luz = luzNoLado(lado, Pose(giroY = giro, giroX = 3f), medidas)
                assertTrue("$lado a $giro° ficou preto demais: $luz", luz >= 0.45f)
                assertTrue("$lado a $giro° estourou: $luz", luz <= 1f)
            }
        }
    }
}
