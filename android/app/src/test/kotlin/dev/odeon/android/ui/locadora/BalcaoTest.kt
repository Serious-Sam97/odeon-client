package dev.odeon.android.ui.locadora

import dev.odeon.android.dados.Devolvida
import dev.odeon.android.dados.PessoaNaLoja
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes do balcão.
///
/// ## O que eles guardam, e por que não é o desenho
///
/// O desenho o screenshot pega. O que ele **não** pega é a frase errada sobre
/// uma pessoa real: «fulano devolveu» numa fita que o prazo trouxe de volta
/// sozinha dá crédito por uma coisa que ele não fez, e ninguém que olhe a tela
/// tem como saber que está errado.
///
/// E guardam a regra da fama: quem aparece no balcão. Se ela quebrar pro lado
/// errado, ou some gente que devia estar lá, ou aparece um chip com três zeros
/// pendurados no nome de alguém.
class BalcaoTest {

    private fun devolucao(
        por: String = "membro",
        como: String? = "rebobinada",
        atrasada: Boolean = false,
    ) = Devolvida(
        caixaId = "w1",
        titulo = "Tetris",
        quemNome = "rudney",
        devolvidoComo = como,
        devolvidoPor = por,
        atrasada = atrasada,
    )

    /// ⚠️ **O título vem primeiro, e é a mudança inteira.**
    ///
    /// A versão anterior escrevia «rudney devolveu Tetris — rebobinada», e no
    /// servidor de casa oito de nove linhas abriam com «sam devolveu». O que
    /// distingue uma devolução da outra é o filme, e ele ficava no fim.
    @Test
    fun `o filme vem na frente, quem e como vem atras`() {
        assertEquals("Tetris" to "rebobinada · rudney", fraseDaDevolucao(devolucao()))
        assertEquals(
            "Tetris" to "sem rebobinar · rudney",
            fraseDaDevolucao(devolucao(como = "sem_rebobinar")),
        )
        assertEquals("Tetris" to "até o fim · rudney", fraseDaDevolucao(devolucao(como = "ate_o_fim")))
    }

    @Test
    fun `a que venceu nao diz que alguem devolveu`() {
        /// ⚠️ É o teste que importa. `devolvido_por = prazo` é a fita que voltou
        /// **sozinha** quando o prazo estourou — e escrever «rudney devolveu»
        /// nesse caso é dar crédito por uma coisa que o relógio fez.
        ///
        /// Aqui o verbo fica na **segunda** parte, e é de propósito: nas outras a
        /// parte de trás é quem fez e como; nesta, ela é o que aconteceu — porque
        /// não houve ninguém fazendo.
        assertEquals(
            "Tetris" to "venceu na mão de rudney",
            fraseDaDevolucao(devolucao(por = "prazo")),
        )
    }

    @Test
    fun `condicao desconhecida nao vira texto`() {
        /// O servidor pode ganhar uma condição nova, e esta tela é mais velha
        /// que ela. Sobra o nome, em vez de escrever «unknown · rudney» (§18).
        assertEquals("Tetris" to "rudney", fraseDaDevolucao(devolucao(como = "enrolada")))
        assertEquals("Tetris" to "rudney", fraseDaDevolucao(devolucao(como = null)))
    }

    @Test
    fun `aparece no balcao quem tem fita ou quem tem fama`() {
        /// A segunda metade é a regra que faz a reputação existir: se o número
        /// sumisse junto com a fita, devolver zoado seria de graça.
        assertTrue(PessoaNaLoja(nome = "sam", naMao = 1).temOQueDizer)
        assertTrue("a fama tem que sobreviver à devolução", PessoaNaLoja(nome = "sam", zoadas = 2).temOQueDizer)
        assertTrue(PessoaNaLoja(nome = "sam", rebobinou = 3).temOQueDizer)
        assertTrue(PessoaNaLoja(nome = "sam", noMeio = 1).temOQueDizer)
    }

    @Test
    fun `quem nao tem nada nao vira chip`() {
        /// §24 na forma que mais importa: um `✕0` pendurado no nome de alguém é
        /// uma acusação de nada.
        assertFalse(PessoaNaLoja(nome = "gabriel").temOQueDizer)
    }
}
