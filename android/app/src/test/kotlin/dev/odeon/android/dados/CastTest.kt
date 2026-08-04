package dev.odeon.android.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes do Cast.
///
/// ## Por que só isto é testado da fase 4
///
/// Porque é o que dá pra provar sem um Chromecast na mesa. Descobrir o aparelho,
/// conectar e mandar a mídia só se verifica na rede de casa — a §4c diz que o
/// Cast do Odeon é recurso de casa, e daqui a tailnet não leva a TV junto.
///
/// Então o que sobra é justamente a parte que **erra em silêncio**: um endereço
/// que a TV não alcança vira tela preta na sala, sem mensagem, num aparelho onde
/// não há nem onde mostrar erro. Isso é lógica pura, e lógica pura tem prova.
class CastTest {

    // --------------------------------------------------- o endereço da tailnet

    @Test
    fun `o IP da tailnet deste projeto e reconhecido`() {
        // É o endereço que o `CONTINUAR-ANDROID.md` manda digitar no login, e o
        // que este app tem cadastrado hoje.
        assertTrue(EnderecoParaCast.eDaTailnet("100.77.253.18"))
    }

    @Test
    fun `a faixa vai de 100_64 a 100_127, e as bordas contam`() {
        assertTrue(EnderecoParaCast.eDaTailnet("100.64.0.0"))
        assertTrue(EnderecoParaCast.eDaTailnet("100.127.255.255"))

        // `/10` são os dez primeiros bits, não os oito. Um `startsWith("100.")`
        // acertaria os de cima e erraria estes dois — que são endereços
        // públicos de verdade, e portanto alcançáveis pela TV.
        assertFalse(EnderecoParaCast.eDaTailnet("100.63.255.255"))
        assertFalse(EnderecoParaCast.eDaTailnet("100.128.0.1"))
    }

    @Test
    fun `IP de rede local nao e tailnet`() {
        // O caso que o Cast precisa que funcione: servidor na LAN de casa.
        assertFalse(EnderecoParaCast.eDaTailnet("192.168.0.10"))
        assertFalse(EnderecoParaCast.eDaTailnet("10.0.0.5"))
    }

    @Test
    fun `nome de maquina nao e IP e nao e tailnet`() {
        // `rog` é o exemplo do placeholder da tela de login.
        assertFalse(EnderecoParaCast.eDaTailnet("rog"))
        assertFalse(EnderecoParaCast.eDaTailnet("serious-server"))
    }

    @Test
    fun `texto com cara de IP mas com letra nao passa`() {
        assertFalse(EnderecoParaCast.eDaTailnet("100.77.253.abc"))
        assertFalse(EnderecoParaCast.eDaTailnet("100.77.253"))
    }

    // ------------------------------------------------- o servidor alcança a TV?

    @Test
    fun `o servidor de hoje NAO e alcancavel pela TV`() {
        // Este é o teste que documenta o limite. Com o servidor na tailnet, o
        // Cast não tem como funcionar — e o app tem que dizer isso em vez de
        // mandar pra TV uma URL que ela não abre.
        assertFalse(EnderecoParaCast.alcancavelPelaTv("http://100.77.253.18:8085"))
    }

    @Test
    fun `servidor na LAN e alcancavel pela TV`() {
        assertTrue(EnderecoParaCast.alcancavelPelaTv("http://192.168.0.10:8085"))
    }

    @Test
    fun `sem servidor a resposta e nao`() {
        // "Ainda não sei" não vira "pode": oferecer Cast sem saber o endereço
        // seria adivinhar.
        assertFalse(EnderecoParaCast.alcancavelPelaTv(null))
    }

    // ------------------------------------------------------ o perfil da TV

    @Test
    fun `o perfil do Chromecast NAO e o do celular`() {
        // A diferença concreta: o celular declara `mkv` e ganha Direct Play onde
        // a web recebia remux. O Chromecast não lê Matroska, e declarar que lê
        // trocaria transcode por tela preta.
        assertFalse(PerfilDeCast.CONTAINERS.contains("mkv"))
        assertTrue(PerfilDeCast.CONTAINERS.contains("mp4"))
    }

    @Test
    fun `o perfil e conservador nos codecs que dependem do modelo`() {
        // hevc, av1 e vp9 dependem da geração do aparelho; ac3 depende da saída
        // de áudio. Todos ficam de fora — errar pra transcode custa CPU, errar
        // pro outro lado custa o filme.
        assertEquals("h264,vp8", PerfilDeCast.VIDEO)
        assertEquals("aac,mp3", PerfilDeCast.AUDIO)
        assertFalse(PerfilDeCast.AUDIO.contains("ac3"))
    }
}
