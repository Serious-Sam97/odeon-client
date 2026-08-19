package dev.odeon.android.ui.serie

import dev.odeon.android.dados.ObraDaLista
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private fun ep(
    id: String,
    temporada: Int? = 1,
    episodio: Int? = 1,
    still: String? = null,
    visto: Boolean? = null,
    parouEm: Double? = null,
) = ObraDaLista(
    id = id,
    title = id,
    temporada = temporada,
    episodio = episodio,
    still = still,
    finished = visto,
    ondeParou = parouEm,
)

class TemporadasTest {

    @Test
    fun `agrupa por temporada e ordena por episodio`() {
        val t = agruparEmTemporadas(
            listOf(
                ep("s2e1", 2, 1), ep("s1e2", 1, 2), ep("s1e1", 1, 1), ep("s2e2", 2, 2),
            ),
        )
        assertEquals(listOf(1, 2), t.map { it.numero })
        assertEquals(listOf("s1e1", "s1e2"), t[0].episodios.map { it.id })
        assertEquals(listOf("s2e1", "s2e2"), t[1].episodios.map { it.id })
    }

    @Test
    fun `episodio sem temporada ganha grupo proprio em vez de ser dobrado na 1`() {
        val t = agruparEmTemporadas(listOf(ep("solto", temporada = null), ep("s1e1", 1, 1)))
        assertEquals(listOf(-1, 1), t.map { it.numero })
        assertEquals("Sem temporada", t[0].rotulo)
        assertEquals(1, t[1].quantos)
    }

    @Test
    fun `a temporada zero se chama Especiais`() {
        val t = agruparEmTemporadas(listOf(ep("esp", 0, 1), ep("s1e1", 1, 1)))
        assertEquals(listOf(0, 1), t.map { it.numero })
        assertEquals("Especiais", t[0].rotulo)
        assertEquals("Temporada 1", t[1].rotulo)
    }

    @Test
    fun `os especiais e os sem temporada vem antes da primeira`() {
        val t = agruparEmTemporadas(
            listOf(ep("s1", 1, 1), ep("esp", 0, 1), ep("solto", null, 1)),
        )
        assertEquals(listOf(-1, 0, 1), t.map { it.numero })
    }

    @Test
    fun `episodio sem numero vai pro fim da temporada`() {
        val t = agruparEmTemporadas(listOf(ep("sem", 1, null), ep("piloto", 1, 1)))
        assertEquals(listOf("piloto", "sem"), t[0].episodios.map { it.id })
    }

    @Test
    fun `a arte da temporada e o primeiro still que existir`() {
        val t = agruparEmTemporadas(
            listOf(ep("s1e1", 1, 1, still = null), ep("s1e2", 1, 2, still = "b.jpg")),
        )
        assertEquals("b.jpg", t[0].arte)
    }

    @Test
    fun `sem nenhum still a arte e nula em vez de um caminho inventado`() {
        assertNull(agruparEmTemporadas(listOf(ep("s1e1", 1, 1))).first().arte)
    }

    @Test
    fun `andado e nulo quando nada foi visto`() {
        assertNull(agruparEmTemporadas(listOf(ep("a", 1, 1))).first().andado)
    }

    @Test
    fun `andado conta os vistos sobre o total`() {
        val t = agruparEmTemporadas(
            listOf(ep("a", 1, 1, visto = true), ep("b", 1, 2), ep("c", 1, 3), ep("d", 1, 4)),
        )
        assertEquals(0.25f, t.first().andado!!, 0.0001f)
        assertEquals(1, t.first().vistos)
    }

    @Test
    fun `o comecado ganha do proximo nao visto`() {
        val t = agruparEmTemporadas(
            listOf(
                ep("e1", 1, 1, visto = true),
                ep("e2", 1, 2, parouEm = 300.0),
                ep("e3", 1, 3),
            ),
        )
        val onde = ondeParar(t)!!
        assertEquals("e2", onde.episodio.id)
        assertTrue(onde.comecado)
    }

    @Test
    fun `sem nada comecado oferece o primeiro nao visto e nao chama de continuar`() {
        val t = agruparEmTemporadas(listOf(ep("e1", 1, 1, visto = true), ep("e2", 1, 2)))
        val onde = ondeParar(t)!!
        assertEquals("e2", onde.episodio.id)
        assertTrue(!onde.comecado)
    }

    @Test
    fun `serie inteira vista volta o primeiro episodio em vez de ficar sem botao`() {
        val t = agruparEmTemporadas(
            listOf(ep("e1", 1, 1, visto = true), ep("e2", 1, 2, visto = true)),
        )
        val onde = ondeParar(t)!!
        assertEquals("e1", onde.episodio.id)
        assertTrue(!onde.comecado)
    }

    @Test
    fun `episodio marcado visto mas com posicao nao conta como comecado`() {
        val t = agruparEmTemporadas(
            listOf(ep("e1", 1, 1, visto = true, parouEm = 1200.0), ep("e2", 1, 2)),
        )
        val onde = ondeParar(t)!!
        assertEquals("e2", onde.episodio.id)
    }

    @Test
    fun `a continuidade atravessa a virada de temporada`() {
        val t = agruparEmTemporadas(
            listOf(
                ep("s1e1", 1, 1, visto = true),
                ep("s2e1", 2, 1, parouEm = 60.0),
            ),
        )
        assertEquals("s2e1", ondeParar(t)!!.episodio.id)
    }

    @Test
    fun `sem episodio nenhum nao ha onde parar`() {
        assertNull(ondeParar(emptyList()))
    }
}
