package dev.odeon.android.ui.guia

import dev.odeon.android.dados.EventoDaSemana
import org.junit.Assert.assertEquals
import org.junit.Test

/// Os testes da chamada do «em cartaz esta semana».
///
/// ## Ela é a única frase do app montada a partir de quatro campos
///
/// `participou`, `obras`, `suas` e o prazo se combinam em quatro frases
/// diferentes, e três delas mentem se o ramo errado sair: dizer "termine pra
/// participar" pra quem já participou, prometer um prazo que não veio no dado,
/// ou escrever "você já viu 0 de 1" numa obra só.
class ChamadaDoEventoTest {

    private val obra = EventoDaSemana(tipo = "obra", id = "w1", titulo = "Juno", obras = 1)

    @Test
    fun `obra unica convida com o prazo`() {
        assertEquals("Termine até segunda pra participar.", chamadaDoEvento(obra, "segunda"))
    }

    /// §24 e §18 juntos: sem `vira_em` legível, a oração do prazo **some**. A
    /// frase continua verdadeira; ela é que deixa de prometer o dia.
    @Test
    fun `sem prazo a frase perde a oracao, e nao o sentido`() {
        assertEquals("Termine pra participar.", chamadaDoEvento(obra, null))
    }

    /// Saga: aí o "de quantas" é a informação, porque a pessoa pode já estar no
    /// meio dela.
    @Test
    fun `saga conta quantas sao e quantas voce ja viu`() {
        val saga = EventoDaSemana(tipo = "saga", id = "c9", titulo = "Rocky", obras = 6, suas = 2)
        assertEquals(
            "Termine uma das 6 obras até amanhã pra participar. Você já viu 2 de 6.",
            chamadaDoEvento(saga, "amanhã"),
        )
    }

    @Test
    fun `quem participou nao e convidado de novo`() {
        assertEquals("Você participou.", chamadaDoEvento(obra.copy(participou = true), "segunda"))
    }
}
