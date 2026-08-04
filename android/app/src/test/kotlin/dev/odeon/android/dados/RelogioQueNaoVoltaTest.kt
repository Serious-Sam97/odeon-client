package dev.odeon.android.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes do relógio que não volta.
///
/// ## Por que esta é a segunda coisa mais testada do app
///
/// Porque é a peça que a própria espec chama de frágil, e porque ela decide se
/// um filme toca ou não **sem rede** — ou seja, sem ninguém pra corrigir o erro
/// depois. Um relógio que aceita andar pra trás não estoura: ele libera, calado,
/// o que devia ter vencido.
///
/// Os instantes são epoch em milissegundos. Os números são pequenos de
/// propósito: o que se prova aqui é a ordem, não a data.
class RelogioQueNaoVoltaTest {

    // ------------------------------------------------------------- o relógio

    @Test
    fun `relogio adiantado vale, porque so anda pra frente`() {
        assertEquals(200L, RelogioQueNaoVolta.agora(doAparelho = 200, maiorJaVisto = 100))
    }

    @Test
    fun `relogio atrasado NAO vale — vale o maior ja visto`() {
        // É o caso inteiro: alguém atrasa o relógio pra estender o empréstimo.
        assertEquals(500L, RelogioQueNaoVolta.agora(doAparelho = 100, maiorJaVisto = 500))
    }

    @Test
    fun `o maior ja visto sobe com o relogio e com o servidor`() {
        var maior = 0L
        maior = RelogioQueNaoVolta.maiorDepoisDeVer(maior, 100) // tique local
        assertEquals(100L, maior)

        maior = RelogioQueNaoVolta.maiorDepoisDeVer(maior, 900) // cabeçalho Date
        assertEquals(900L, maior)

        // E não desce quando o local está atrás do que o servidor já disse.
        maior = RelogioQueNaoVolta.maiorDepoisDeVer(maior, 150)
        assertEquals(900L, maior)
    }

    // -------------------------------------------------------------- a fita

    @Test
    fun `fita sem prazo nunca vence`() {
        // É todo download vindo da **biblioteca**, que é modo livre desde o §71.
        assertFalse(RelogioQueNaoVolta.venceu(venceEm = null, doAparelho = 999_999, maiorJaVisto = 999_999))
    }

    @Test
    fun `fita vence quando o prazo passa`() {
        assertTrue(RelogioQueNaoVolta.venceu(venceEm = 100, doAparelho = 101, maiorJaVisto = 0))
    }

    @Test
    fun `fita ainda vale antes do prazo`() {
        assertFalse(RelogioQueNaoVolta.venceu(venceEm = 100, doAparelho = 99, maiorJaVisto = 0))
    }

    @Test
    fun `atrasar o relogio NAO ressuscita uma fita vencida`() {
        // O aparelho já viu o instante 500 (por tique ou por cabeçalho do
        // servidor). Depois alguém pôs o relógio em 50, antes do vencimento 100.
        // Sem a memória do máximo, isso destravaria o filme.
        assertTrue(RelogioQueNaoVolta.venceu(venceEm = 100, doAparelho = 50, maiorJaVisto = 500))
    }

    @Test
    fun `o instante exato do vencimento ja conta como vencido`() {
        // `>=`, e não `>`: uma fita que vence às 20h não toca às 20h em ponto.
        assertTrue(RelogioQueNaoVolta.venceu(venceEm = 100, doAparelho = 100, maiorJaVisto = 0))
    }

    // ------------------------------------------------------------- a origem

    @Test
    fun `so o que veio da locadora expira`() {
        // A decisão do dono depois do §71: a biblioteca é modo livre, então um
        // download dela que travasse seria o app mais restrito offline do que
        // online.
        assertTrue(OrigemDoDownload.LOCADORA.expira)
        assertFalse(OrigemDoDownload.BIBLIOTECA.expira)
    }
}
