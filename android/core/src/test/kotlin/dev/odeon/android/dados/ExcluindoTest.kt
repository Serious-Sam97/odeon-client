package dev.odeon.android.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// O `?tags_not=`, e a armadilha que o servidor avisou.
class ExcluindoTest {

    @Test
    fun `lista vazia nao vira parametro`() {
        /// ⚠️ O servidor lê `?tags_not=` vazio como «não filtre», mas mandar o
        /// parâmetro à toa é pedir pra alguém, um dia, mudar essa leitura e
        /// varrer o acervo inteiro pra fora. O cliente simplesmente não manda.
        assertNull(Filtros().excluindoEmTexto)
    }

    @Test
    fun `uma etiqueta vira ela mesma`() {
        assertEquals("format:série", Filtros(excluindo = listOf("format:série")).excluindoEmTexto)
    }

    @Test
    fun `o par da aba dos filmes vai separado por virgula`() {
        /// O par, e não só a série: `tags_not=format:série` sozinho deixa passar
        /// o `Beyblade` — 43 episódios que carregam `format:anime`. Medido pelo
        /// servidor em 18/08/2026.
        assertEquals(
            "format:série,format:anime",
            Filtros(excluindo = listOf("format:série", "format:anime")).excluindoEmTexto,
        )
    }

    @Test
    fun `excluir nao mexe no tag_mode`() {
        /// O `tag_mode` fala das etiquetas que **somam**. A exclusão é sempre
        /// `any` e o servidor não deixa trocar — mandar o modo por causa dela
        /// diria ao servidor como combinar uma lista que ele não combina.
        assertNull(Filtros(excluindo = listOf("format:série")).modoParaMandar)
    }

    @Test
    fun `excluir convive com prateleira e etiquetas`() {
        val f = Filtros(
            prateleira = "format:série",
            etiquetas = listOf("genre:Terror"),
            excluindo = listOf("format:anime"),
        )
        assertEquals("format:série,genre:Terror", f.etiquetasEmTexto)
        assertEquals("format:anime", f.excluindoEmTexto)
    }
}
