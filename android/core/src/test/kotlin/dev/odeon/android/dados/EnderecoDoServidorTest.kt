package dev.odeon.android.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes do endereço do servidor.
///
/// ## Por que esta é a primeira coisa testada do app
///
/// Porque é a única lógica da fase 1 que **não precisa de servidor** e que erra
/// em silêncio quando erra: um endereço mal normalizado não estoura, ele
/// simplesmente não conecta — e o sintoma na tela é "o servidor não respondeu",
/// que aponta pro lugar errado.
///
/// Os casos são os mesmos do `ServerUrlTest` do `clients/shared`, porque este
/// arquivo é o porte daquele. Duas cópias do mesmo conhecimento merecem duas
/// cópias da mesma prova — senão a que não tem teste é a que envelhece.
class EnderecoDoServidorTest {

    @Test
    fun `so o host vira as duas portas padrao`() {
        assertEquals(
            listOf("https://rog:8443", "http://rog:8080"),
            EnderecoDoServidor.candidatos("rog"),
        )
    }

    @Test
    fun `esquema explicito e respeitado e nao tenta o outro`() {
        // Quem escreveu `http://` escolheu. Tentar https por baixo seria
        // surpresa — e pior, uma surpresa que às vezes funciona.
        assertEquals(
            listOf("http://rog:8080"),
            EnderecoDoServidor.candidatos("http://rog:8080"),
        )
    }

    @Test
    fun `porta explicita sem esquema tenta os dois na mesma porta`() {
        assertEquals(
            listOf("https://rog:9000", "http://rog:9000"),
            EnderecoDoServidor.candidatos("rog:9000"),
        )
    }

    @Test
    fun `o caminho digitado por engano e descartado`() {
        assertEquals("rog", EnderecoDoServidor.normalizar("rog/biblioteca"))
    }

    @Test
    fun `o esquema sai antes da limpeza de barra`() {
        // A armadilha que o comentário do código descreve: limpar barra antes
        // faria `https://` virar `https:`.
        assertEquals("https://rog", EnderecoDoServidor.normalizar("https://rog"))
    }

    @Test
    fun `espaco em volta nao conta`() {
        assertEquals("rog", EnderecoDoServidor.normalizar("  rog  "))
    }

    @Test
    fun `vazio e pontuacao pura nao sao endereco`() {
        assertNull(EnderecoDoServidor.normalizar(""))
        assertNull(EnderecoDoServidor.normalizar("   "))
        assertNull(EnderecoDoServidor.normalizar("://"))
        assertNull(EnderecoDoServidor.normalizar("..."))
        assertTrue(EnderecoDoServidor.candidatos("").isEmpty())
    }

    @Test
    fun `ip com porta funciona igual`() {
        assertEquals(
            listOf("https://192.168.0.10:8085", "http://192.168.0.10:8085"),
            EnderecoDoServidor.candidatos("192.168.0.10:8085"),
        )
    }

    @Test
    fun `o emulador aponta pro host pelo 10 ponto 0 ponto 2 ponto 2`() {
        // `10.0.2.2` é como o emulador do Android alcança a máquina que o roda.
        // Ele não tem porta, então cai nas padrão — e é o caso que mais vai ser
        // digitado durante o desenvolvimento.
        assertEquals(
            listOf("https://10.0.2.2:8443", "http://10.0.2.2:8080"),
            EnderecoDoServidor.candidatos("10.0.2.2"),
        )
    }

    @Test
    fun `e seguro so quando o esquema diz que e`() {
        assertTrue(EnderecoDoServidor.eSeguro("https://rog:8443"))
        assertTrue(!EnderecoDoServidor.eSeguro("http://rog:8080"))
    }
}
