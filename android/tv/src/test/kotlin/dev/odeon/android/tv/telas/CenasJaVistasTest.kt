package dev.odeon.android.tv.telas

import dev.odeon.android.dados.FolhaDeSprites
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// A regra do spoiler do herói.
///
/// ⚠️ Ela é testada porque é **lógica pura e invisível**: um erro aqui não quebra
/// a tela, não aparece no lint e não estoura teste nenhum — só mostra a uma
/// pessoa uma cena do filme que ela ainda não viu. O defeito é silencioso e o
/// prejuízo é irreversível.
class CenasJaVistasTest {

    private fun folha(quadros: Int = 400, intervalo: Double = 10.0) = FolhaDeSprites(
        arquivoId = "a",
        path = "x.jpg",
        intervaloSegundos = intervalo,
        columns = 20,
        rows = 20,
        larguraDaMiniatura = 160,
        alturaDaMiniatura = 90,
        quantosQuadros = quadros,
    )

    @Test
    fun `nenhuma cena passa do ponto onde parou`() {
        val parou = 3_600.0
        val cenas = cenasJaVistas(folha(), parou)
        assertTrue("com uma hora vista tem de haver cena", cenas.isNotEmpty())
        assertTrue("cena depois de $parou é spoiler: $cenas", cenas.all { it < parou })
    }

    @Test
    fun `filme mal comecado nao rende cena`() {
        /// Menos de dois minutos: o trecho visto é abertura, e abertura não é
        /// cena. Devolver vazio deixa o herói na arte estática.
        assertEquals(emptyList<Int>(), cenasJaVistas(folha(), 90.0))
    }

    @Test
    fun `sem posicao nao rende cena`() {
        assertEquals(emptyList<Int>(), cenasJaVistas(folha(), null))
    }

    @Test
    fun `sem folha nao rende cena`() {
        assertEquals(emptyList<Int>(), cenasJaVistas(null, 3_600.0))
    }

    @Test
    fun `pula a abertura`() {
        /// ⚠️ A primeira cena não pode ser o segundo zero: lá está o logotipo do
        /// estúdio, que é igual em todo filme da distribuidora e não diz nada
        /// sobre este.
        val cenas = cenasJaVistas(folha(), 3_600.0)
        assertTrue("a primeira cena caiu na abertura: ${cenas.first()}", cenas.first() > 120)
    }

    @Test
    fun `as cenas sao distintas e crescentes`() {
        val cenas = cenasJaVistas(folha(), 3_600.0)
        assertEquals("cena repetida faz o laço parecer travado", cenas.distinct(), cenas)
        assertEquals(cenas.sorted(), cenas)
    }
}

/// A mesma regra de spoiler, agora pro **vídeo** da prévia.
///
/// ⚠️ Ela existe separada porque o ponto do vídeo **não depende da folha de
/// sprites** — e essa confusão já custou uma rodada: a prévia nascia morta em
/// todo filme sem tira, porque eu tinha reusado a lista de cenas dos quadros
/// parados. Duas fontes, duas funções, dois testes.
class UmaCenaJaVistaTest {

    @Test
    fun `nunca passa do ponto onde parou`() {
        val parou = 3_600.0
        repeat(50) {
            val s = umaCenaJaVista(parou)!!
            assertTrue("cena em $s é spoiler de quem parou em $parou", s < parou)
        }
    }

    @Test
    fun `pula a abertura`() {
        /// Determinístico: com o sorteio sempre em zero, o resultado é o piso.
        assertEquals(288, umaCenaJaVista(3_600.0) { 0 })
    }

    @Test
    fun `filme mal comecado nao tem previa`() {
        assertEquals(null, umaCenaJaVista(90.0))
        assertEquals(null, umaCenaJaVista(null))
    }
}
