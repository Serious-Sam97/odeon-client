package dev.odeon.android.dados

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes da revista da semana.
///
/// ## O que eles guardam
///
/// O contrato, e as duas regras que vieram escritas na folha da web: o ensaio
/// ausente **omite a seção**, e o texto de máquina sai **creditado**. As duas
/// são de desenho, mas nascem aqui — se `paragrafos` devolvesse uma linha em
/// branco, a tela desenharia um vão sem saber que estava desenhando.
class RevistaTest {

    private val json = Json { ignoreUnknownKeys = true }

    /// A resposta como o servidor manda: `snake_case`, e campos que este app não
    /// declara (a web tem outros). `ignoreUnknownKeys` é o mesmo do app.
    private val comoVemDoServidor = """
        {
          "semana_de": "2026-08-03",
          "vira_em": "2026-08-10",
          "eixo": "genero",
          "tema": "Romance",
          "filmes": [
            {"id": "w1", "titulo": "Juno", "ano": 2007, "poster": "/p/juno.jpg",
             "diretor": "Jason Reitman", "visto": true},
            {"id": "w2", "titulo": "Aladdin", "ano": null, "poster": null,
             "diretor": null, "visto": false}
          ],
          "ensaio": "O que estes filmes têm em comum é a variedade de décadas.\n\nAladdin é um clássico.",
          "ensaio_por": "llama-3.3-70b-versatile",
          "evento": {
            "tipo": "obra", "id": "w1", "titulo": "Juno", "poster": "/p/juno.jpg",
            "obras": 1, "suas": 0, "participou": false, "participantes": ["rudney"]
          }
        }
    """.trimIndent()

    @Test
    fun `a revista inteira sai do json do servidor`() {
        val r = json.decodeFromString<Revista>(comoVemDoServidor)

        assertEquals("2026-08-10", r.viraEm)
        assertEquals("Romance", r.tema)
        assertEquals(2, r.filmes.size)
        assertEquals("llama-3.3-70b-versatile", r.ensaioPor)
        assertEquals("w1", r.evento?.id)
        assertEquals(listOf("rudney"), r.evento?.participantes)
    }

    /// Os nulos do segundo filme são o caso normal, não o excepcional: 4.794
    /// obras do acervo não têm pôster, e obra não identificada não tem ano nem
    /// diretor. Quem desenha **tem** que tratar isso.
    @Test
    fun `filme sem ano, sem poster e sem diretor decodifica`() {
        val r = json.decodeFromString<Revista>(comoVemDoServidor)
        val aladdin = r.filmes[1]

        assertNull(aladdin.ano)
        assertNull(aladdin.poster)
        assertNull(aladdin.diretor)
        assertEquals(false, aladdin.visto)
    }

    @Test
    fun `o rotulo do eixo vira frase em portugues`() {
        assertEquals("gênero da semana", Revista(eixo = "genero").rotuloDoEixo)
        assertEquals("década da semana", Revista(eixo = "decada").rotuloDoEixo)
        assertEquals("país da semana", Revista(eixo = "pais").rotuloDoEixo)
        assertEquals("diretor da semana", Revista(eixo = "diretor").rotuloDoEixo)
        assertEquals("saga da semana", Revista(eixo = "saga").rotuloDoEixo)
    }

    /// §18: um eixo que este app não conhece não vira frase inventada. O rótulo
    /// some e o letreiro fica sozinho, que é verdade.
    @Test
    fun `eixo desconhecido nao vira frase`() {
        assertNull(Revista(eixo = "trilha-sonora").rotuloDoEixo)
        assertNull(Revista().rotuloDoEixo)
    }

    @Test
    fun `o ensaio vira paragrafos, sem linha em branco no meio`() {
        val r = json.decodeFromString<Revista>(comoVemDoServidor)

        assertEquals(2, r.paragrafos.size)
        assertTrue(r.paragrafos[0].startsWith("O que estes filmes"))
        assertEquals("Aladdin é um clássico.", r.paragrafos[1])
    }

    /// ⚠️ A regra da folha: «`null` quando não há chave do LLM ou o texto ainda
    /// não foi gerado. A tela **omite a seção** — não mostra "carregando" nem
    /// inventa prosa.» Lista vazia é o que faz a seção sumir.
    @Test
    fun `sem ensaio a lista de paragrafos e vazia`() {
        assertEquals(emptyList<String>(), Revista(ensaio = null).paragrafos)
        assertEquals(emptyList<String>(), Revista(ensaio = "   \n\n  ").paragrafos)
    }
}
