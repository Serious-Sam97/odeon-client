package dev.odeon.android.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/// Os testes do corte por data do balcão.
///
/// ## O que eles guardam
///
/// A tentação de cortar a lista de devoluções em «as três últimas» é grande e
/// está errada nos dois sentidos: num dia movimentado esconde notícia, num dia
/// parado promove a notícia o que é história. O corte é o dia, e o dia é a coisa
/// que mais fácil se implementa errado — fuso, meia-noite, e o carimbo que o
/// servidor manda em dois formatos.
///
/// ⚠️ Todos passam `agora` explícito. Um teste de data que lê o relógio do
/// sistema passa hoje e falha na virada do ano, às 21h de 31 de dezembro, na
/// máquina de outra pessoa.
class EhDeHojeTest {

    private val fuso: ZoneId = ZoneId.systemDefault()

    private fun instante(data: String, hora: String = "12:00"): Instant =
        LocalDateTime.parse("${data}T$hora").atZone(fuso).toInstant()

    private val agora = instante("2026-08-05", "14:30")

    @Test
    fun `o mesmo dia e hoje, a qualquer hora dele`() {
        assertTrue(ehDeHoje(instante("2026-08-05", "00:01").toString(), agora))
        assertTrue(ehDeHoje(instante("2026-08-05", "14:29").toString(), agora))
        assertTrue(ehDeHoje(instante("2026-08-05", "23:59").toString(), agora))
    }

    /// ⚠️ **O caso que decide o desenho todo.** Uma fita devolvida às 23h de
    /// ontem tem nove horas de idade às 8h de hoje — menos que uma devolvida às
    /// 8h de hoje e lida às 20h. Ainda assim ela **não** é notícia de hoje.
    ///
    /// «Hoje» numa casa é o dia do calendário, não uma janela de 24h deslizante.
    /// Se isto virasse `agora - 24h`, o balcão diria «hoje» sobre a noite de
    /// ontem toda manhã.
    @Test
    fun `ontem as 23h nao e hoje, mesmo sendo ha poucas horas`() {
        val ontemTarde = instante("2026-08-04", "23:50")
        assertFalse(ehDeHoje(ontemTarde.toString(), instante("2026-08-05", "08:00")))
    }

    @Test
    fun `logo depois da meia-noite ja e hoje, mesmo com dez minutos`() {
        assertTrue(ehDeHoje(instante("2026-08-05", "00:10").toString(), instante("2026-08-05", "00:20")))
    }

    @Test
    fun `amanha nao e hoje`() {
        assertFalse(ehDeHoje(instante("2026-08-06", "09:00").toString(), agora))
    }

    /// O servidor manda as duas formas conforme a rota — o carimbo completo e a
    /// data seca. A data seca vira **meia-noite local**, e não UTC: o dia da casa
    /// não é o de Greenwich.
    @Test
    fun `a data seca tambem conta, e no fuso da casa`() {
        assertTrue(ehDeHoje("2026-08-05", agora))
        assertFalse(ehDeHoje("2026-08-04", agora))
    }

    /// ⚠️ Sem carimbo, a devolução cai no **histórico** — nunca em «hoje».
    ///
    /// É o §18: o app não promove a notícia o que ele não sabe quando aconteceu.
    /// O erro pro outro lado seria pior — um `null` tratado como hoje encheria a
    /// seção de notícias com tudo o que o servidor não datou.
    @Test
    fun `sem data, nao e hoje`() {
        assertFalse(ehDeHoje(null, agora))
        assertFalse(ehDeHoje("", agora))
        assertFalse(ehDeHoje("   ", agora))
        assertFalse(ehDeHoje("ontem", agora))
    }

    /// A virada do ano é onde uma comparação por número do dia quebra: 1º de
    /// janeiro e 31 de dezembro são dias vizinhos com um ano de distância.
    @Test
    fun `a virada do ano nao confunde`() {
        val reveillon = instante("2025-12-31", "23:30")
        val anoNovo = instante("2026-01-01", "00:30")
        assertFalse(ehDeHoje(reveillon.toString(), anoNovo))
        assertTrue(ehDeHoje(anoNovo.toString(), anoNovo))
    }

    /// O dia de hoje, lido do relógio de verdade, é hoje. É o único teste que
    /// toca o relógio do sistema, e ele existe pra provar que o valor padrão do
    /// parâmetro está ligado em algo.
    @Test
    fun `o padrao usa o relogio de agora`() {
        assertTrue(ehDeHoje(LocalDate.now(fuso).toString()))
    }
}
