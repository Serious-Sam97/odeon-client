package dev.odeon.shared

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class ServerUrlTest {

    @Test
    fun tira_barra_final_e_espaco() {
        assertEquals("https://rog:8443", ServerUrl.normalize("  https://rog:8443/  "))
    }

    @Test
    fun aceita_host_cru_como_a_pessoa_digita_na_tv() {
        assertEquals("rog:8443", ServerUrl.normalize("rog:8443"))
        assertEquals("odeon.tail1234.ts.net", ServerUrl.normalize("odeon.tail1234.ts.net"))
    }

    @Test
    fun caminho_digitado_por_engano_e_descartado() {
        assertEquals("https://rog:8443", ServerUrl.normalize("https://rog:8443/api/works"))
    }

    @Test
    fun lixo_nao_vira_endereco() {
        assertNull(ServerUrl.normalize(""))
        assertNull(ServerUrl.normalize("   "))
        assertNull(ServerUrl.normalize("///"))
        assertNull(ServerUrl.normalize("https://"))
    }

    @Test
    fun sem_esquema_tenta_https_antes_de_http() {
        val list = ServerUrl.candidates("rog:8443")
        assertEquals(listOf("https://rog:8443", "http://rog:8443"), list)
    }

    @Test
    fun sem_esquema_nem_porta_usa_as_portas_do_odeon() {
        assertEquals(
            listOf("https://rog:8443", "http://rog:8080"),
            ServerUrl.candidates("rog"),
        )
    }

    @Test
    fun esquema_explicito_e_respeitado_sem_tentar_o_outro() {
        // Se a pessoa escreveu http://, tentar https por baixo seria surpresa.
        assertEquals(listOf("http://rog:8080"), ServerUrl.candidates("http://rog:8080"))
        assertEquals(listOf("https://rog:8443"), ServerUrl.candidates("https://rog:8443"))
    }

    @Test
    fun reconhece_endereco_seguro() {
        assertTrue(ServerUrl.isSecure("https://rog:8443"))
        assertTrue(!ServerUrl.isSecure("http://rog:8080"))
    }
}

class MixedContentTest {

    @Test
    fun pagina_segura_com_servidor_inseguro_e_problema() {
        val problem = mixedContentProblem(pageIsSecure = true, serverUrl = "http://rog:8080")
        assertTrue(problem != null && problem.contains("bloqueia"))
    }

    @Test
    fun as_outras_combinacoes_passam() {
        assertNull(mixedContentProblem(true, "https://rog:8443"))
        assertNull(mixedContentProblem(false, "http://rog:8080"))
        // página HTTP falando com servidor HTTPS é permitido
        assertNull(mixedContentProblem(false, "https://rog:8443"))
    }
}
