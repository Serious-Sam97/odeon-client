package dev.odeon.android.ui.biblioteca

import dev.odeon.android.dados.ObraDaLista
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/// Os testes do agrupamento por temporada.
///
/// ## O que eles guardam
///
/// A **ordem**, que é a única coisa que esta função faz e a única que o
/// screenshot não denuncia: uma série curta cabe na tela e o erro salta; uma de
/// 62 episódios com a temporada 10 antes da 2 parece certa até alguém procurar
/// um episódio específico e não achar.
///
/// Os três casos que a ordem tem que acertar vêm do acervo real, e não de
/// hipótese: **3.350 obras esperam revisão** hoje, então episódio sem número e
/// sem temporada é rotina, e a temporada 0 é o que o TMDB usa pros especiais.
class PorTemporadaTest {

    private fun ep(temporada: Int?, episodio: Int?, titulo: String = "ep") = ObraDaLista(
        id = "$temporada-$episodio-$titulo",
        title = titulo,
        temporada = temporada,
        episodio = episodio,
    )

    @Test
    fun `as temporadas saem em ordem numerica, e nao alfabetica`() {
        /// `10` antes de `2` é o defeito clássico de ordenar número como texto,
        /// e ele só aparece a partir da décima temporada — ou seja, nunca nos
        /// testes pequenos e sempre nas séries longas.
        val grupos = porTemporada(
            listOf(ep(10, 1), ep(2, 1), ep(1, 1)),
        )

        assertEquals(listOf("temporada 1", "temporada 2", "temporada 10"), grupos.map { it.first })
    }

    @Test
    fun `os episodios saem numerados dentro da temporada`() {
        val grupos = porTemporada(
            listOf(ep(1, 3, "c"), ep(1, 1, "a"), ep(1, 2, "b")),
        )

        assertEquals(listOf("a", "b", "c"), grupos.single().second.map { it.title })
    }

    @Test
    fun `a temporada zero e Especiais, e vem antes da primeira`() {
        /// É a convenção do TMDB, e ela é a razão de o rótulo não ser
        /// «temporada 0» — ninguém chama assim.
        val grupos = porTemporada(listOf(ep(1, 1), ep(0, 1)))

        assertEquals(listOf("especiais", "temporada 1"), grupos.map { it.first })
    }

    @Test
    fun `sem temporada vai pro fim, e nao pro comeco`() {
        /// `null` ordenado ingenuamente vem primeiro, e aí a série começa por um
        /// monte de arquivo que a identificação não numerou — parecendo que os
        /// episódios de verdade estão no fim.
        val grupos = porTemporada(listOf(ep(null, null), ep(1, 1), ep(2, 1)))

        assertEquals(listOf("temporada 1", "temporada 2", "sem temporada"), grupos.map { it.first })
    }

    @Test
    fun `episodio sem numero fica por ultimo dentro do grupo`() {
        val grupos = porTemporada(listOf(ep(1, null, "solto"), ep(1, 1, "primeiro")))

        assertEquals(listOf("primeiro", "solto"), grupos.single().second.map { it.title })
    }

    @Test
    fun `sem episodio nenhum nao ha grupo nenhum`() {
        /// A tela desenha um rótulo de seção por grupo. Um grupo vazio seria uma
        /// régua dourada com nada embaixo (§24).
        assertEquals(emptyList<Pair<String, List<ObraDaLista>>>(), porTemporada(emptyList()))
    }

    @Test
    fun `o codigo do episodio e SxxExx, com dois digitos`() {
        /// `S1E1` desalinha uma grade inteira quando o vizinho é `S01E10`. E sem
        /// numeração o código é **nulo**, não uma string vazia: quem desenha
        /// omite a etiqueta em vez de desenhar uma caixa vazia por cima do
        /// quadro.
        assertEquals("S01E01", ep(1, 1).codigo)
        assertEquals("S10E23", ep(10, 23).codigo)
        assertEquals("ep 137", ep(null, 137).codigo)
        assertNull(ep(null, null).codigo)
    }
}
