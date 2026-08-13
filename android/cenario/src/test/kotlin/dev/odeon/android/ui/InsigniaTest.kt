package dev.odeon.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes da marca desenhada.
///
/// ## Por que uma conta de hash merece teste
///
/// Porque ela é **compartilhada com outro cliente**, e a divergência seria
/// invisível de cada lado. O `hueFromTitle` da web (`api.ts:2462`) e o
/// `matizDoNome` daqui têm que devolver o mesmo número pro mesmo nome — senão
/// `rudney` fica verde no navegador e azul no celular, e uma marca que muda de
/// cor entre clientes deixa de identificar alguém.
///
/// Os valores esperados abaixo foram calculados **pela conta da web**, à mão:
/// `hash = hash * 31 + código`, com estouro de 32 bits, e depois `|hash| % 360`.
class InsigniaTest {

    /// A mesma conta do JavaScript, escrita de outro jeito — se as duas
    /// implementações concordarem, a daqui está certa por construção e não por
    /// eu ter copiado um número.
    private fun comoAWebFaz(texto: String): Int {
        var hash = 0
        for (c in texto) {
            hash = (hash shl 5) - hash + c.code
        }
        return if (hash == Int.MIN_VALUE) 0 else kotlin.math.abs(hash) % 360
    }

    @Test
    fun `o matiz e o mesmo que a web calcula`() {
        listOf("sam", "rudney", "gabriel", "Sam", "", "Ana Clara", "007").forEach { nome ->
            assertEquals("o matiz de «$nome» divergiu da web", comoAWebFaz(nome), matizDoNome(nome))
        }
    }

    @Test
    fun `o matiz cabe sempre no circulo`() {
        /// Um matiz fora de 0..359 viraria uma cor girada — ou, na figura, um
        /// índice negativo. Os nomes longos são os que chegam perto do estouro.
        listOf("a", "zzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzzz", "Rudney da Silva Sobrinho").forEach {
            val matiz = matizDoNome(it)
            assertTrue("matiz fora do círculo: $matiz", matiz in 0..359)
        }
    }

    @Test
    fun `o mesmo nome da sempre a mesma marca`() {
        /// É a propriedade inteira da marca derivada: ela não é sorteada, é
        /// calculada. Duas chamadas em momentos diferentes têm que coincidir.
        assertEquals(matizDoNome("gabriel"), matizDoNome("gabriel"))
        assertEquals(matizDoNome("gabriel" + "gabriel"), matizDoNome("gabrielgabriel"))
    }

    @Test
    fun `a inicial e maiuscula, e nome vazio nao vira letra em branco`() {
        assertEquals("S", inicialDe("sam"))
        assertEquals("R", inicialDe("  rudney "))
        assertEquals("É", inicialDe("Élio"))
        /// O servidor não manda nome vazio — mas uma resposta truncada mandaria,
        /// e uma marca com a letra em branco parece um retrato que não carregou.
        assertEquals("?", inicialDe("   "))
    }
}
