package dev.odeon.android.dados

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// O qualificador da etiqueta, em português.
///
/// Existe pelo mesmo motivo do `RevistaTest`: a tabela é **desenho**, mora no
/// cliente, e um `else -> null` distraído volta a imprimir chave de banco na
/// ficha — que foi o defeito de 16/08/2026.
class EtiquetaTest {

    private fun etiqueta(ns: String) = Etiqueta(id = "1", namespace = ns, value = "x")

    @Test
    fun `traduz os namespaces que este acervo manda`() {
        assertEquals("país", etiqueta("country").rotulo)
        assertEquals("gênero", etiqueta("genre").rotulo)
        assertEquals("formato", etiqueta("format").rotulo)
        assertEquals("idioma", etiqueta("lang").rotulo)
    }

    /// ⚠️ O servidor já mandou os dois idiomas — a folha do modelo registra
    /// `genero/Crime` e `pais/Estados Unidos` de quando aquilo foi escrito, e a
    /// tela de hoje mostra `country`. As duas formas têm de valer.
    @Test
    fun `aceita as formas antigas, em português`() {
        assertEquals("país", etiqueta("pais").rotulo)
        assertEquals("gênero", etiqueta("genero").rotulo)
        assertEquals("formato", etiqueta("tipo").rotulo)
    }

    @Test
    fun `ignora caixa`() {
        assertEquals("país", etiqueta("COUNTRY").rotulo)
        assertEquals("gênero", etiqueta("Genre").rotulo)
    }

    /// ⚠️ O que ele não conhece **some**, e não vira a chave crua. A pílula
    /// desenha só o valor — ver `PilulaDeEtiqueta`.
    @Test
    fun `namespace desconhecido não vira rótulo`() {
        assertNull(etiqueta("collection_id").rotulo)
        assertNull(etiqueta("").rotulo)
    }
}
