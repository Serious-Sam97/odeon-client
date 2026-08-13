package dev.odeon.android.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// Os testes do barramento.
///
/// ## O que dá pra testar sem servidor, e é justo o que morde
///
/// A conexão em si precisa de rede; **a frase** não. E é a frase que erra em
/// silêncio: um `o_que` que o app não conhece viraria texto quebrado na tela da
/// locadora, e um `null` num campo viraria a palavra `null` no meio de um recado.
///
/// A regra é a mesma do mural: o servidor manda o acontecimento em código, o
/// cliente monta o português. Quem não sabe montar **não mostra nada** (§18).
class BarramentoTest {

    private fun recado(oQue: String, titulo: String? = "Tetris", quem: String? = "rudney") =
        EventoDoServidor.NaLocadora(oQue = oQue, titulo = titulo, quem = quem).recado

    @Test
    fun `os quatro acontecimentos da locadora viram frase`() {
        assertEquals("rudney pegou Tetris", recado("pegou"))
        assertEquals("rudney devolveu Tetris", recado("devolveu"))
        assertEquals("rudney pediu Tetris de volta", recado("pediu"))
        assertEquals("Tetris venceu na mão de rudney", recado("venceu"))
    }

    @Test
    fun `acontecimento desconhecido nao vira frase`() {
        /// O servidor pode ganhar um tipo novo a qualquer momento — e a tela
        /// deste app é mais velha que ele. «rudney reservou Tetris» seria uma
        /// invenção; nada é a resposta certa.
        assertNull(recado("reservou"))
        assertNull(recado(""))
    }

    @Test
    fun `sem quem ou sem titulo, nao ha recado`() {
        /// Os dois campos são opcionais no contrato, e um recado pela metade
        /// («devolveu Tetris» — quem?) é pior que recado nenhum: ele faz a
        /// pessoa procurar a informação que falta.
        assertNull(recado("pegou", quem = null))
        assertNull(recado("pegou", titulo = null))
    }

    @Test
    fun `o tipo desconhecido chega como Outro, e nao some`() {
        /// Os cinco tipos que ainda não têm tela — mural, junto, ao vivo e as
        /// faixas do servidor — precisam **chegar** pra quem for escrever essas
        /// telas descobrir que o barramento já os entrega. Um `when` que engole
        /// o desconhecido é como um evento novo nunca aparece.
        val evento: EventoDoServidor = EventoDoServidor.Outro("programme_starting")
        assertEquals("programme_starting", (evento as EventoDoServidor.Outro).tipo)
    }
}
