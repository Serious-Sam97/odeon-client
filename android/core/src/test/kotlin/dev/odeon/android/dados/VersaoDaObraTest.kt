package dev.odeon.android.dados

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes do agrupamento de versões — `ItemDaBiblioteca.versions`.
///
/// ## ⚠️ O que estes testes provam, e o que eles **não** provam
///
/// Eles travam a **leitura** do cliente: dado este JSON, o app entende isto. É a
/// mesma razão do `FaixaDeAudioTest` — com `ignoreUnknownKeys` e valores padrão,
/// um nome de campo errado **não estoura**: `versions` ausente vira lista vazia,
/// e lista vazia é indistinguível de «este filme só tem uma versão». O defeito de
/// contrato e o estado normal do mundo dão na mesma tela.
///
/// Eles **não** provam que o servidor manda estes campos. O JSON abaixo é escrito
/// aqui, não capturado — a resposta real do `/api/library` só foi vista no relato
/// do servidor, e a TV estava fora da rede na hora de conferir. Quem prova o
/// outro lado é a tela, e está anotado como pendente no §28 do
/// `docs/REDESENHO-TV.md`.
///
/// O caso é o do dono: dois rips do mesmo 007, um em pt-BR e outro em inglês,
/// baixados separados por não haver dual audio.
class VersaoDaObraTest {

    private val json = Json { ignoreUnknownKeys = true }

    /// O 007, na forma que o servidor relatou em 14/08/2026.
    ///
    /// ⚠️ O `audio_langs` vazio do segundo **não é falta de dado neste teste**: é
    /// o arquivo em inglês, cuja faixa aac não declara idioma. O servidor recusa
    /// mandar `und`, e a recusa é a certa.
    private val entradaComDuasVersoes = """
        {
          "id": "eddbfd12-1111-2222-3333-444444444444",
          "is_series": false,
          "title": "007: A Serviço Secreto de Sua Majestade",
          "year": 1969,
          "total": 8273,
          "versions": [
            {
              "id": "eddbfd12-1111-2222-3333-444444444444",
              "media_file_id": "a2274591-541d-4e83-bbe3-6f1b35b6cc6a",
              "height": 818,
              "size_bytes": 2469606195,
              "duration_seconds": 8520.0,
              "audio_langs": ["por"],
              "position_seconds": 1558.5,
              "finished": false
            },
            {
              "id": "a950f840-f6f7-4390-b023-94eb14e59abd",
              "media_file_id": "2531ac55-1f33-4252-a1d8-c4e878fbb757",
              "height": 816,
              "size_bytes": 2394829619,
              "duration_seconds": 8520.0,
              "audio_langs": [],
              "position_seconds": 4925.8,
              "finished": false
            }
          ]
        }
    """.trimIndent()

    @Test
    fun `le as duas versoes e os campos que a modal desenha`() {
        val item = json.decodeFromString<ItemDaBiblioteca>(entradaComDuasVersoes)

        assertEquals(2, item.versoes.size)
        assertEquals(listOf("por"), item.versoes[0].idiomasDeAudio)
        assertEquals(818, item.versoes[0].height)
        assertEquals(1558.5, item.versoes[0].ondeParou!!, 0.01)
        assertEquals("a950f840-f6f7-4390-b023-94eb14e59abd", item.versoes[1].id)
    }

    /// ⚠️ O caso que decide se a modal vale a pena existir.
    ///
    /// Com um dos lados sem idioma, o nome não distingue as duas — quem
    /// distingue é o `position_seconds`. Se este campo sumir do contrato, a modal
    /// vira `818p` contra `816p`, que é escolha nenhuma.
    @Test
    fun `o arquivo em ingles chega sem idioma, e o onde parou e o que sobra`() {
        val item = json.decodeFromString<ItemDaBiblioteca>(entradaComDuasVersoes)
        val ingles = item.versoes[1]

        assertTrue("o inglês não declara idioma", ingles.idiomasDeAudio.isEmpty())
        assertEquals(4925.8, ingles.ondeParou!!, 0.01)
    }

    @Test
    fun `duas versoes abrem escolha`() {
        val item = json.decodeFromString<ItemDaBiblioteca>(entradaComDuasVersoes)
        assertTrue(item.temEscolhaDeVersao)
        assertEquals(2, item.versoesEscolhiveis.size)
    }

    /// ⚠️ O caso de 8.230 das 8.273 entradas: o servidor **omite** a chave.
    ///
    /// É o teste que separa «o filme tem uma versão» de «o campo mudou de nome no
    /// servidor» — e os dois dão exatamente a mesma tela, que é por que isto está
    /// escrito.
    @Test
    fun `sem a chave versions, nao ha escolha e a grade segue como antes`() {
        val item = json.decodeFromString<ItemDaBiblioteca>(
            """{"id":"x","is_series":false,"title":"Star Wars III","total":8273}""",
        )

        assertTrue(item.versoes.isEmpty())
        assertFalse(item.temEscolhaDeVersao)
    }

    /// ⚠️ Versão sem `id` é descartada, e a biblioteca **não** morre.
    ///
    /// O `id` tem `""` como padrão de propósito: campo obrigatório transformaria
    /// uma renomeação de JSON numa grade que não carrega. Com o padrão, o pior
    /// caso é a modal não abrir e o cartão abrir a obra representante — o
    /// comportamento de antes deste improvement.
    @Test
    fun `versao sem id nao conta, e degrada em vez de quebrar`() {
        val item = json.decodeFromString<ItemDaBiblioteca>(
            """
            {"id":"x","is_series":false,"title":"Cassino Royale","total":1,
             "versions":[{"height":800,"audio_langs":["por"]},
                         {"id":"real","height":798,"audio_langs":[]}]}
            """.trimIndent(),
        )

        assertEquals("as duas foram lidas", 2, item.versoes.size)
        assertEquals("só uma serve", 1, item.versoesEscolhiveis.size)
        assertFalse("e com uma só, a modal não abre", item.temEscolhaDeVersao)
    }

    /// Uma versão só nunca abre modal — pergunta com uma resposta é o §24.
    @Test
    fun `uma versao so nao abre modal`() {
        val item = json.decodeFromString<ItemDaBiblioteca>(
            """{"id":"x","is_series":false,"title":"Y","total":1,
                "versions":[{"id":"a","height":1080,"audio_langs":["por"]}]}""",
        )

        assertFalse(item.temEscolhaDeVersao)
    }
}
