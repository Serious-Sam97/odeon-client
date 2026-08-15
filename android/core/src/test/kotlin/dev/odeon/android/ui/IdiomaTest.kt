package dev.odeon.android.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// Os testes do nome do idioma — o rótulo da modal de versões.
///
/// A regra que todos eles cercam é uma só: **a tela não inventa idioma**. Quando
/// o arquivo não declara, ou declara algo que esta tabela não conhece, a resposta
/// é `null` e quem chama omite — nunca o código cru.
class IdiomaTest {

    @Test
    fun `traduz os codigos que este acervo tem`() {
        assertEquals("Português", idiomaEmPortugues("por"))
        assertEquals("Português", idiomaEmPortugues("pt"))
        assertEquals("Inglês", idiomaEmPortugues("eng"))
        assertEquals("Japonês", idiomaEmPortugues("jpn"))
    }

    /// O `ffprobe` escreve `fre` e o ISO 639-1 escreve `fr`; os dois aparecem no
    /// acervo, e os dois têm que dar no mesmo nome.
    @Test
    fun `aceita as duas grafias do mesmo idioma`() {
        assertEquals("Francês", idiomaEmPortugues("fra"))
        assertEquals("Francês", idiomaEmPortugues("fre"))
        assertEquals("Alemão", idiomaEmPortugues("ger"))
        assertEquals("Alemão", idiomaEmPortugues("deu"))
    }

    @Test
    fun `nao se importa com caixa nem espaco`() {
        assertEquals("Português", idiomaEmPortugues(" POR "))
    }

    /// ⚠️ `und` é o contêiner dizendo que **não sabe**, e não um idioma.
    ///
    /// O servidor já o recusa antes de mandar; a guarda daqui é a segunda linha.
    /// A primeira vez que ele passou, o menu de faixas do player abriu com uma
    /// faixa chamada «und» na cara do dono — 06/08/2026.
    @Test
    fun `und nao e idioma`() {
        assertNull(idiomaEmPortugues("und"))
    }

    @Test
    fun `codigo desconhecido nao vira texto`() {
        assertNull(idiomaEmPortugues("xyz"))
        assertNull(idiomaEmPortugues(""))
    }

    /// ⚠️ **O caso do dono.** O 007 em inglês do acervo chega com `audio_langs`
    /// vazio, e a modal precisa cair na queda posicional («versão 2») em vez de
    /// escrever qualquer coisa. Se este teste passar a devolver texto, a tela
    /// começou a inventar.
    @Test
    fun `lista vazia nao tem nome`() {
        assertNull(idiomasEmPortugues(emptyList()))
    }

    @Test
    fun `uma faixa vira um nome`() {
        assertEquals("Português", idiomasEmPortugues(listOf("por")))
    }

    /// Dual audio — o que o dono procurou e não achou. Vale escrever por extenso:
    /// é a versão que dispensa a escolha.
    @Test
    fun `duas faixas viram a frase inteira`() {
        assertEquals("Português e Inglês", idiomasEmPortugues(listOf("por", "eng")))
        assertEquals(
            "Português, Inglês e Espanhol",
            idiomasEmPortugues(listOf("por", "eng", "spa")),
        )
    }

    /// ⚠️ O desconhecido é **descartado**, e não vira reticência: `por + hun` sai
    /// como «Português», e não «Português e …». A segunda forma prometeria uma
    /// informação que a tela não tem.
    @Test
    fun `descarta o que nao conhece em vez de reticenciar`() {
        assertEquals("Português", idiomasEmPortugues(listOf("por", "hun")))
        assertNull(idiomasEmPortugues(listOf("hun", "und")))
    }

    /// Duas faixas do mesmo idioma (dublagem e comentário, por exemplo) não viram
    /// «Português e Português».
    @Test
    fun `nao repete idioma`() {
        assertEquals("Português", idiomasEmPortugues(listOf("por", "pt")))
    }
}
