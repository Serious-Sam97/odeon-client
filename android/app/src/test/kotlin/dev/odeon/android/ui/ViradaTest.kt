package dev.odeon.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/// Os testes da virada da semana.
///
/// ## Por que uma frase de três palavras merece teste
///
/// Porque ela é a mesma frase em **duas telas** — a capa do guia e a vitrine da
/// locadora — e porque o que ela recebe é uma data que vem do servidor em dois
/// formatos. Um `null` calado aqui não estoura: a linha só some, e ninguém
/// descobre que a locadora parou de dizer quando a vitrine vira.
///
/// O `agora` entra por parâmetro justamente pra isto: um teste que chamasse
/// `Instant.now()` mediria o dia em que rodou.
class ViradaTest {

    private val agora = Instant.parse("2026-08-04T12:00:00Z")

    @Test
    fun `carimbo completo daqui a cinco dias vira segunda`() {
        assertEquals("segunda", viraQuando("2026-08-09T12:00:00Z", agora))
    }

    @Test
    fun `menos de um dia vira amanha`() {
        assertEquals("amanhã", viraQuando("2026-08-05T09:00:00Z", agora))
    }

    @Test
    fun `data ja passada tambem vira amanha — e e a regra da web`() {
        // `dias <= 1` engole o negativo de propósito: uma revista com a virada
        // vencida é a que está prestes a trocar, não uma que trocou no passado.
        assertEquals("amanhã", viraQuando("2026-08-01T12:00:00Z", agora))
    }

    /// A data seca (`2026-08-10`) é o outro formato que o servidor manda, e o
    /// `Instant.parse` sozinho a recusa. Ela vale como **meia-noite local**: a
    /// semana que vira é a da casa, não a de Greenwich.
    @Test
    fun `data seca parseia, e no fuso de casa`() {
        val daquiATresDias = LocalDate.ofInstant(agora, ZoneId.systemDefault()).plusDays(3)
        assertEquals("segunda", viraQuando(daquiATresDias.toString(), agora))
    }

    /// §24: o que não dá pra dizer não vira "—" nem "vira em null" — some.
    @Test
    fun `o que nao parseia devolve nulo, e a frase some`() {
        assertNull(viraQuando(null, agora))
        assertNull(viraQuando("", agora))
        assertNull(viraQuando("   ", agora))
        assertNull(viraQuando("segunda que vem", agora))
    }
}
