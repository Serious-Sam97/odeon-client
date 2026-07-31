package dev.odeon.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SpriteInfoTest {

    private val sprite = SpriteInfo(
        mediaFileId = "x",
        path = "x.jpg",
        intervalSeconds = 5.2f,
        columns = 10,
        rows = 12,
        thumbWidth = 160,
        thumbHeight = 90,
        frameCount = 120,
    )

    /** Mesma aritmética do player web — se divergir, o preview mostra o quadro errado. */
    @Test
    fun quadro_no_meio_do_video() {
        assertEquals(71, sprite.frameAt(374.0))
    }

    @Test
    fun nunca_estoura_a_grade() {
        assertEquals(119, sprite.frameAt(999_999.0))
        assertEquals(0, sprite.frameAt(-10.0))
    }
}

class WorkListItemTest {

    private fun work(position: Double?, duration: Double?) = WorkListItem(
        id = "1",
        kind = "movie",
        title = "Teste",
        positionSeconds = position,
        durationSeconds = duration,
    )

    @Test
    fun progresso_pela_metade() {
        assertEquals(0.5f, work(300.0, 600.0).progress)
    }

    @Test
    fun sem_duracao_nao_divide_por_zero() {
        assertEquals(0f, work(300.0, 0.0).progress)
        assertEquals(0f, work(300.0, null).progress)
    }

    @Test
    fun progresso_nunca_passa_de_um() {
        assertTrue(work(9000.0, 600.0).progress <= 1f)
    }

    @Test
    fun rotulo_de_episodio() {
        val episode = WorkListItem(
            id = "1", kind = "episode", title = "Teste",
            seasonNumber = 2, episodeNumber = 7,
        )
        assertEquals("S02E07", episode.episodeLabel)
        assertNull(work(null, null).episodeLabel)
    }
}

class TagRowTest {
    @Test
    fun chave_no_formato_que_o_backend_espera() {
        assertEquals("mood:melancólico", TagRow("1", "mood", "melancólico").key)
    }
}
