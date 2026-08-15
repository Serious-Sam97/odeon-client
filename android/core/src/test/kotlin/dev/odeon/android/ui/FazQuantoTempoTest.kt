package dev.odeon.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

/// A escada de «faz quanto tempo», conferida contra o `quando()` do
/// `web/src/Mural.tsx:801`.
///
/// Os degraus são testados **na borda** de cada um, e não no meio: um `<` que
/// vira `<=` por descuido só aparece em 6 dias contra 7, nunca em 3.
class FazQuantoTempoTest {

    /// Uma segunda-feira qualquer, ao meio-dia UTC. Fixa, e não `Instant.now()`:
    /// um teste que depende do relógio da máquina passa hoje e falha na
    /// virada do mês.
    private val agora = Instant.parse("2026-08-12T12:00:00Z")

    private fun hàDias(dias: Long) = agora.minusMillis(dias * 86_400_000L).toString()

    @Test
    fun `hoje quando foi agora`() {
        assertEquals("hoje", fazQuantoTempo(agora.toString(), agora))
        assertEquals("hoje", fazQuantoTempo(hàDias(0), agora))
    }

    /// ⚠️ O carimbo adiantado cai em «hoje», e não em «há -1 dias». É o que o
    /// `Math.floor` da web faz com um número negativo, e o caso existe de
    /// verdade: o relógio de uma TV recém ligada costuma estar errado.
    @Test
    fun `carimbo no futuro nao vira numero negativo`() {
        val amanha = agora.plusMillis(86_400_000L).toString()
        assertEquals("hoje", fazQuantoTempo(amanha, agora))
    }

    @Test
    fun `ontem e os dias soltos`() {
        assertEquals("ontem", fazQuantoTempo(hàDias(1), agora))
        assertEquals("há 2 dias", fazQuantoTempo(hàDias(2), agora))
        assertEquals("há 6 dias", fazQuantoTempo(hàDias(6), agora))
    }

    /// A borda entre dias e semanas: 6 ainda é dia, 7 já é semana.
    @Test
    fun `sete dias vira uma semana no singular`() {
        assertEquals("há uma semana", fazQuantoTempo(hàDias(7), agora))
        assertEquals("há uma semana", fazQuantoTempo(hàDias(13), agora))
        assertEquals("há 2 semanas", fazQuantoTempo(hàDias(14), agora))
        assertEquals("há 4 semanas", fazQuantoTempo(hàDias(29), agora))
    }

    /// Passado um mês, vira data — porque «há 7 semanas» não diz nada a ninguém.
    @Test
    fun `depois de trinta dias vira data por extenso`() {
        /// 30 dias antes de 12/08 é 13/07.
        assertEquals("13 de julho", fazQuantoTempo(hàDias(30), agora))
    }

    /// A data seca, sem hora, que o servidor manda em algumas rotas. O
    /// `instanteDe` a resolve como meia-noite **local**, então o dia exato
    /// depende do fuso da máquina — o que se afirma aqui é só que ela não
    /// devolve nulo.
    @Test
    fun `aceita a data seca`() {
        assertEquals("hoje", fazQuantoTempo("2026-08-12", Instant.parse("2026-08-12T23:00:00Z")))
    }

    @Test
    fun `sem carimbo nao desenha`() {
        assertNull(fazQuantoTempo(null, agora))
        assertNull(fazQuantoTempo("", agora))
        assertNull(fazQuantoTempo("ontem de manhã", agora))
    }
}
