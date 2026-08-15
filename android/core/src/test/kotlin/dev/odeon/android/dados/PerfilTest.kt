package dev.odeon.android.dados

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Os testes do perfil.
///
/// ## O que eles guardam
///
/// Duas coisas, e a segunda é a que morde.
///
/// O **contrato**: a resposta traz cinco listas que este app não declara (os
/// catálogos do editor), e o `ignoreUnknownKeys` tem que engoli-las sem
/// derrubar a insígnia — que é a peça que fica por cima de toda aba.
///
/// E **a conta do anel**. Ela é a única aritmética desta rodada, sai da web
/// (`App.tsx:244`), e erra de três maneiras diferentes se for escrita de
/// cabeça: divisão por zero na faixa vazia, fatia acima de 1 quando o XP passa
/// do próximo nível, e «faltam 0» no topo da curva. Os três casos estão aqui
/// porque nenhum deles aparece na conta feliz.
class PerfilTest {

    private val json = Json { ignoreUnknownKeys = true }

    /// A resposta como o servidor manda, com os campos do editor incluídos —
    /// `rostos`, `capas`, `molduras`, `titulos_disponiveis`, `tags_disponiveis`
    /// — que são exatamente os que o app **não** declara.
    private val comoVemDoServidor = """
        {
          "user_id": "u1",
          "username": "sam",
          "display_name": "Sam",
          "meu": true,
          "progresso": {
            "xp": 4210, "nivel": 6,
            "xp_do_nivel": 3600, "xp_do_proximo": 5000,
            "desbloqueadas": 12, "total": 80
          },
          "titulo": "cinefilo",
          "titulo_nome": "Cinéfilo",
          "tags": ["terror", "anos 80"],
          "bio": "assisto tudo",
          "vitrine": [
            {"id": "w1", "titulo": "Juno", "ano": 2007, "poster": "/p/juno.jpg"}
          ],
          "conquistas": [
            {"chave": "primeira", "nome": "A primeira", "descricao": "Termine um filme",
             "camada": "facil", "pontos": 10, "titulo": false, "tag": null,
             "em": "2026-07-01T10:00:00Z"},
            {"chave": "maratona", "nome": "Maratona", "descricao": "Cinco num dia",
             "camada": "dificil", "pontos": 90, "titulo": true, "tag": null, "em": null}
          ],
          "amigos": [
            {"id": "u1", "username": "sam", "display_name": "Sam", "nivel": 6,
             "xp": 4210, "desbloqueadas": 12, "titulo": "Cinéfilo", "eu": true},
            {"id": "u2", "username": "rudney", "display_name": "Rudney", "nivel": 4,
             "xp": 2100, "desbloqueadas": 7, "titulo": null, "eu": false}
          ],
          "avatar": {"chave": "rosto:bogart", "rotulo": "Bogart", "arte": "/p/bogart.jpg",
                     "exige": null, "exige_nome": null, "cor": null, "aberto": true},
          "capa": null,
          "moldura": "#7A5FBF",
          "rostos": [{"chave": "rosto:x", "rotulo": "X", "arte": null, "exige": "conq",
                      "exige_nome": "A primeira", "cor": null, "aberto": false}],
          "capas": [],
          "molduras": [],
          "titulos_disponiveis": [["cinefilo", "Cinéfilo"]],
          "tags_disponiveis": ["terror"]
        }
    """.trimIndent()

    @Test
    fun `desserializa a resposta inteira, inclusive o que o app nao declara`() {
        val perfil = json.decodeFromString<Perfil>(comoVemDoServidor)

        assertEquals("Sam", perfil.nome)
        assertEquals("sam", perfil.username)
        assertTrue(perfil.meu)
        assertEquals(6, perfil.progresso.nivel)
        assertEquals("Cinéfilo", perfil.tituloNome)
        assertEquals(listOf("terror", "anos 80"), perfil.tags)
        assertEquals("#7A5FBF", perfil.moldura)
        assertEquals("/p/bogart.jpg", perfil.avatar?.arte)
        /// A capa nula é o caso comum de quem não escolheu — e é o que faz a
        /// tela não desenhar faixa nenhuma no topo.
        assertNull(perfil.capa)
    }

    @Test
    fun `a conquista trancada se distingue pela data, e nao pelos pontos`() {
        val perfil = json.decodeFromString<Perfil>(comoVemDoServidor)
        val (aberta, trancada) = perfil.conquistas.partition { it.aberta }

        assertEquals(listOf("A primeira"), aberta.map { it.nome })
        assertEquals(listOf("Maratona"), trancada.map { it.nome })
        /// ⚠️ A trancada **tem** pontos na resposta (90). Quem esconde é a tela,
        /// e é por isso que o modelo não os zera: um dia o editor vai querer
        /// mostrá-los na hora em que ela abrir.
        assertEquals(90, trancada.single().pontos)
    }

    @Test
    fun `a fatia do nivel e o quanto se andou dentro da faixa`() {
        val perfil = json.decodeFromString<Perfil>(comoVemDoServidor)

        // 4210 - 3600 = 610, sobre uma faixa de 5000 - 3600 = 1400.
        assertEquals(610f / 1400f, perfil.fatiaDoNivel, 0.0001f)
        assertEquals(790, perfil.faltamPraSubir)
    }

    @Test
    fun `faixa vazia nao divide por zero`() {
        /// O último nível da curva: o servidor manda os dois marcos iguais. Sem
        /// o `max(1, …)` isto seria `NaN`, e um `NaN` no `sweepAngle` do arco
        /// não desenha nada — o anel sumiria justamente de quem chegou ao fim.
        val topo = Perfil(
            progresso = ProgressoNoPerfil(xp = 9000, nivel = 12, xpDoNivel = 9000, xpDoProximo = 9000),
        )

        assertEquals(0f, topo.fatiaDoNivel, 0.0001f)
        /// E não escreve «faltam 0», que seria prometer uma subida que não vem.
        assertNull(topo.faltamPraSubir)
    }

    @Test
    fun `xp acima do proximo nivel nao passa de uma volta`() {
        /// Acontece de verdade entre o XP subir e o nível ser recalculado. Sem o
        /// `coerceIn`, o arco daria mais de 360º e voltaria a desenhar por cima
        /// de si mesmo — o anel cheio pareceria vazio.
        val adiantado = Perfil(
            progresso = ProgressoNoPerfil(xp = 5400, nivel = 6, xpDoNivel = 3600, xpDoProximo = 5000),
        )

        assertEquals(1f, adiantado.fatiaDoNivel, 0.0001f)
        assertNull(adiantado.faltamPraSubir)
    }

    @Test
    fun `perfil vazio nao afirma nada`() {
        /// O que sobra quando o servidor manda o mínimo. Interessa porque a
        /// insígnia desenha **antes** de o perfil chegar, e o que ela mostra
        /// nesse instante não pode ser uma afirmação: anel a zero, e nenhuma
        /// conquista contada.
        val vazio = json.decodeFromString<Perfil>("""{}""")

        assertEquals("", vazio.nome)
        assertEquals(1, vazio.progresso.nivel)
        assertEquals(0f, vazio.fatiaDoNivel, 0.0001f)
        assertEquals(0, vazio.progresso.total)
        assertFalse(vazio.conquistas.any())
    }
}
