package dev.odeon.android.dados

import org.junit.Assert.assertEquals
import org.junit.Test

private fun item(id: String, serie: String? = null, t: Int? = null, e: Int? = null) =
    ItemPraContinuar(id = id, title = id, tituloDaSerie = serie, temporada = t, episodio = e)

class ContinuarPorSerieTest {

    @Test
    fun `tres episodios da mesma serie viram um`() {
        val r = colapsarPorSerie(
            listOf(
                item("s1e4", "Arcane", 1, 4),
                item("s1e2", "Arcane", 1, 2),
                item("s1e1", "Arcane", 1, 1),
            ),
        )
        assertEquals(listOf("s1e4"), r.map { it.id })
    }

    @Test
    fun `sobrevive o primeiro da lista, que e o mais recente`() {
        val r = colapsarPorSerie(listOf(item("s3e9", "Arcane", 3, 9), item("s1e1", "Arcane", 1, 1)))
        assertEquals("s3e9", r.first().id)
    }

    @Test
    fun `filme nao e tocado, mesmo com titulo repetido`() {
        val r = colapsarPorSerie(listOf(item("a"), item("b"), item("c")))
        assertEquals(listOf("a", "b", "c"), r.map { it.id })
    }

    @Test
    fun `series diferentes continuam separadas e na ordem que chegaram`() {
        val r = colapsarPorSerie(
            listOf(
                item("arcane", "Arcane"),
                item("abbott", "Abbott Elementary"),
                item("arcane2", "Arcane"),
                item("filme"),
            ),
        )
        assertEquals(listOf("arcane", "abbott", "filme"), r.map { it.id })
    }

    @Test
    fun `as duas panteras nao colapsam porque a caixa difere`() {
        val r = colapsarPorSerie(
            listOf(
                item("p78", "A Pantera cor-de-rosa"),
                item("p93", "A Pantera Cor-de-Rosa"),
            ),
        )
        assertEquals(2, r.size)
    }

    @Test
    fun `fileira vazia continua vazia`() {
        assertEquals(emptyList<ItemPraContinuar>(), colapsarPorSerie(emptyList()))
    }
}
