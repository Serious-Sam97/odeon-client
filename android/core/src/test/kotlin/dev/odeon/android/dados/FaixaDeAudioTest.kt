package dev.odeon.android.dados

import dev.odeon.android.ui.player.rotuloDaFaixa
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes das faixas de áudio do plano.
///
/// ## Por que o contrato JSON merece teste, e é o motivo mais forte daqui
///
/// Porque **um nome de campo errado não estoura**. `kotlinx.serialization` com
/// `ignoreUnknownKeys` e valores padrão trata `audio_tracks` ausente como lista
/// vazia — e lista vazia é exatamente o que o app via antes de o servidor
/// entregar o campo. Ou seja: o defeito de contrato e o estado normal do mundo
/// são **indistinguíveis na tela**. O botão simplesmente não nasce, e ninguém
/// descobre por quê.
///
/// O servidor travou o lado dele pelo mesmo motivo, e escreveu o porquê:
/// `#[serde(flatten)]` quebra o contrato sem quebrar compilação. Este é o espelho
/// dessa trava.
///
/// O JSON abaixo é o formato que o acervo tem de recorrente — o servidor mediu
/// **3.469 arquivos** com duas ou mais faixas, e quase sempre `ac3:por` seguido
/// de `aac:eng`. É o mesmo par que fazia o dual audio sumir: o PT-BR ser ac3 é o
/// que força a transcodificação neste cliente, e a transcodificação é o que
/// deixava uma faixa só na playlist.
class FaixaDeAudioTest {

    private val json = Json { ignoreUnknownKeys = true }

    private val planoComDuasFaixas = """
        {
          "mode": "transcode",
          "video": "copy",
          "audio": "encode",
          "reasons": ["o cliente não toca áudio em ac3"],
          "subtitles": [],
          "audio_track": 0,
          "audio_tracks": [
            {"index":0,"codec":"ac3","language":"por","title":null,"channels":6,
             "label":"Português - AC3 5.1"},
            {"index":1,"codec":"aac","language":"eng","title":"Original","channels":2,
             "label":"Inglês - AAC 2.0"}
          ]
        }
    """.trimIndent()

    // ---- o contrato -----------------------------------------------------------

    @Test
    fun `o plano traz as duas faixas, e qual delas esta tocando`() {
        val plano = json.decodeFromString<PlanoDeReproducao>(planoComDuasFaixas)

        assertEquals(2, plano.faixasDeAudio.size)
        assertEquals(0, plano.faixaDeAudio)
        assertEquals(listOf(0, 1), plano.faixasDeAudio.map { it.index })
        assertEquals(listOf("por", "eng"), plano.faixasDeAudio.map { it.language })
        assertEquals("Português - AC3 5.1", plano.faixasDeAudio[0].label)
    }

    /// ⚠️ O plano de antes do campo continua parseando, e cai no caso «não há o
    /// que escolher» — que é o mesmo que um arquivo de faixa única. Sem isto, um
    /// servidor mais velho derrubaria a reprodução inteira em vez de esconder um
    /// botão.
    @Test
    fun `plano sem o campo continua abrindo o filme`() {
        val antigo = """{"mode":"direct_play","video":"copy","audio":"copy"}"""
        val plano = json.decodeFromString<PlanoDeReproducao>(antigo)

        assertTrue(plano.faixasDeAudio.isEmpty())
        assertEquals(null, plano.faixaDeAudio)
        assertTrue(plano.eDireto)
    }

    /// A tela decide pelo tamanho da lista: com uma faixa não há escolha, e §53
    /// manda não oferecer o que a validação vai negar.
    @Test
    fun `uma faixa so nao rende botao`() {
        val umaSo = """
            {"mode":"direct_play","video":"copy","audio":"copy",
             "audio_tracks":[{"index":0,"codec":"aac","label":"Inglês - AAC 2.0"}]}
        """.trimIndent()
        val plano = json.decodeFromString<PlanoDeReproducao>(umaSo)

        assertEquals(1, plano.faixasDeAudio.size)
        assertTrue(plano.faixasDeAudio.size <= 1)
    }

    // ---- o rótulo -------------------------------------------------------------

    @Test
    fun `o label do servidor ganha de tudo`() {
        val faixa = FaixaDeAudio(
            index = 1,
            codec = "aac",
            language = "eng",
            title = "Original",
            label = "Inglês - AAC 2.0",
        )
        assertEquals("Inglês - AAC 2.0", rotuloDaFaixa(faixa))
    }

    /// ⚠️ Sem `label` a tela **não monta** «Inglês - AAC 2.0» por conta própria —
    /// isso seria a terceira redação da mesma frase. Ela desce pro que o arquivo
    /// diz, em ordem.
    @Test
    fun `sem label desce pro titulo, depois pro idioma`() {
        val comTitulo = FaixaDeAudio(index = 1, title = "Comentário do diretor", language = "eng")
        assertEquals("Comentário do diretor", rotuloDaFaixa(comTitulo))

        val soIdioma = FaixaDeAudio(index = 1, language = "eng")
        assertEquals("eng", rotuloDaFaixa(soIdioma))
    }

    /// ⚠️ **`und` é ausência, não idioma** — o defeito que a foto de 06/08/2026
    /// pegou, com o menu abrindo numa faixa chamada `und`. Em ISO 639 quer dizer
    /// *undetermined*: é o contêiner dizendo que não sabe.
    @Test
    fun `und cai pro rotulo posicional, e nao vira idioma`() {
        assertEquals("faixa 1", rotuloDaFaixa(FaixaDeAudio(index = 0, language = "und")))
        assertEquals("faixa 2", rotuloDaFaixa(FaixaDeAudio(index = 1, language = "und")))
    }

    /// Vazio também é ausência. `""` não é rótulo — é a falta dele, e o §24 manda
    /// a linha vazia sumir em vez de virar "—".
    @Test
    fun `campos em branco contam como ausentes`() {
        val tudoEmBranco = FaixaDeAudio(index = 2, label = "", title = "", language = "")
        assertEquals("faixa 3", rotuloDaFaixa(tudoEmBranco))
    }

    /// O `index` é o do servidor (`-map 0:a:N`), e o rótulo posicional conta a
    /// partir de 1 porque é o que uma pessoa lê. Um não é o outro, e confundi-los
    /// mandaria o pedido pra faixa errada.
    @Test
    fun `o rotulo conta de um, e o indice continua contando de zero`() {
        val terceira = FaixaDeAudio(index = 2)
        assertEquals("faixa 3", rotuloDaFaixa(terceira))
        assertEquals(2, terceira.index)
    }
}
