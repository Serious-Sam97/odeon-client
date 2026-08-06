package dev.odeon.android.ui.player

import androidx.media3.common.PlaybackException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes da frase que aparece quando a reprodução morre no meio.
///
/// ## Por que uma frase merece teste
///
/// Porque até 06/08/2026 **não havia frase nenhuma**, e o custo disso está
/// medido: o filme entrava em `ERROR`, a rede voltava e ele continuava em
/// `ERROR`, o play não fazia nada, e a tela seguia desenhando um relógio que
/// andava. O dono resumiu como «para tudo de funcionar até eu voltar e iniciar
/// dnv».
///
/// O que este teste prende não é a redação — é a **regra**: cada frase sai de um
/// código que o Media3 afirma, e nenhuma inventa causa (§18). O caso não mapeado
/// carrega a mensagem crua entre parênteses em vez de fingir diagnóstico.
class FrasePraFalhaTest {

    @Test
    fun `queda de rede diz que foi a conexao`() {
        val frase = frasePraFalha(
            PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED,
            "Unable to connect",
            eHls = true,
        )
        assertEquals("a conexão com o servidor caiu no meio do filme", frase)
    }

    @Test
    fun `tempo esgotado tambem e a conexao`() {
        assertEquals(
            "a conexão com o servidor caiu no meio do filme",
            frasePraFalha(PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_TIMEOUT, null, eHls = false),
        )
    }

    /// ⚠️ **O mesmo código diz coisas diferentes conforme a fonte**, e é a parte
    /// que importa: 404 num segmento de HLS quase sempre é o trecho não existir
    /// naquela sessão — o `ffmpeg` escreve do começo ao fim, e alcançar outro
    /// ponto exige outra sessão. O `Player.tsx` da web diz o mesmo em texto.
    ///
    /// No arquivo direto não há sessão nenhuma, e o mesmo 404 quer dizer outra
    /// coisa: o servidor não entregou o arquivo.
    @Test
    fun `404 em hls fala da sessao, e em arquivo direto fala do arquivo`() {
        val emHls = frasePraFalha(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, null, eHls = true)
        assertEquals("este trecho não está na sessão de transcodificação que estava aberta", emHls)

        val emDireto = frasePraFalha(PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND, null, eHls = false)
        assertEquals("o servidor não entregou o arquivo", emDireto)
    }

    @Test
    fun `status ruim segue a mesma separacao do 404`() {
        assertEquals(
            "este trecho não está na sessão de transcodificação que estava aberta",
            frasePraFalha(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, null, eHls = true),
        )
        assertEquals(
            "o servidor não entregou o arquivo",
            frasePraFalha(PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS, null, eHls = false),
        )
    }

    /// A única falha da lista em que insistir não adianta — e por isso ela diz
    /// que o limite é o aparelho, em vez de convidar a tentar de novo.
    ///
    /// ⚠️ **Este teste já pagou por si.** A primeira versão mapeava o
    /// decodificador como `3000..3999`, e `ERROR_CODE_DECODING_FAILED` vale
    /// **4003** — a faixa 3000 é de *parsing*. O código compilava, e a frase que
    /// apareceria num aparelho sem decodificador seria a genérica.
    @Test
    fun `falha de decodificador fala do aparelho`() {
        val frase = frasePraFalha(
            PlaybackException.ERROR_CODE_DECODING_FAILED,
            "MediaCodec error",
            eHls = false,
        )
        assertEquals("este aparelho não deu conta de decodificar o filme", frase)
        assertEquals(4003, PlaybackException.ERROR_CODE_DECODING_FAILED)
    }

    /// E a faixa vizinha diz outra coisa, de propósito: contêiner ou manifesto
    /// quebrado não é limite do aparelho — é o que chegou estar errado, e uma
    /// sessão nova pode resolver.
    @Test
    fun `parsing quebrado fala do que chegou, e nao do aparelho`() {
        val frase = frasePraFalha(
            PlaybackException.ERROR_CODE_PARSING_MANIFEST_MALFORMED,
            null,
            eHls = true,
        )
        assertEquals("o que chegou do servidor veio num formato que não deu pra ler", frase)
    }

    /// ⚠️ O que não se sabe não vira diagnóstico. A frase é genérica e a mensagem
    /// crua vai junto — pra quem for investigar ter por onde começar, sem a tela
    /// afirmar uma causa que ninguém apurou.
    @Test
    fun `codigo nao mapeado nao inventa causa, e carrega a mensagem crua`() {
        val frase = frasePraFalha(
            PlaybackException.ERROR_CODE_IO_UNSPECIFIED,
            "algo estranho",
            eHls = true,
        )
        assertEquals("a reprodução parou (algo estranho)", frase)
    }

    /// E sem mensagem nenhuma ela não vira "a reprodução parou (null)" — §24: o
    /// que não há some, em vez de virar texto.
    @Test
    fun `sem mensagem crua a frase termina limpa`() {
        val frase = frasePraFalha(PlaybackException.ERROR_CODE_IO_UNSPECIFIED, null, eHls = false)
        assertEquals("a reprodução parou", frase)
        assertTrue(!frase.contains("null"))
    }
}
