package dev.odeon.android.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

/// Os testes da conversão entre o tempo da sessão e o tempo do filme.
///
/// ## Por que uma soma merece teste
///
/// Porque ela **já estava escrita** — o `deslocamentoMs` existe desde 04/08/2026,
/// com um comentário de dez linhas explicando exatamente este defeito — e ainda
/// assim o cromo do player passou uma rodada inteira sem aplicá-la. Compilando,
/// com 109 testes verdes e lint limpo.
///
/// O que a escondeu é que ela é a **identidade** no caso comum. Em Direct Play o
/// deslocamento vale zero, e aí somar ou não somar dá o mesmo número. O defeito
/// só aparece na interseção de duas condições — HLS **e** retomada — e é por isso
/// que a tira foi dada como verificada na véspera: ela tinha sido aberta num
/// filme começando do zero.
///
/// Os números daqui são medidos, e não inventados: *007: A Serviço Secreto de Sua
/// Majestade*, 2h22m17s de probe, retomado aos 1h19m17s por HLS porque o cliente
/// não toca ac3. Foi essa sessão que mostrou `0:43` na tela com o filme em
/// Blofeld no chalé.
class TempoDeFilmeTest {

    /// A duração que o probe do servidor mediu, e que viaja desde a ficha.
    private val duracaoDoFilme = 8_537_000L // 2h22m17s

    /// Onde o filme foi retomado — e, em HLS, o quanto a sessão pulou antes do
    /// primeiro quadro.
    private val deslocamento = 4_757_000L // 1h19m17s

    // ---- Direct Play: as duas funções são a identidade -----------------------

    /// Metade do acervo toca direto, e nesse caminho não há sessão nenhuma: a
    /// posição do player **é** a posição no arquivo. Este é o caso que fez o
    /// defeito passar despercebido, então ele é o primeiro a ficar preso.
    @Test
    fun `sem deslocamento as duas conversoes nao mexem no numero`() {
        assertEquals(0L, tempoDeFilme(0L, 0L))
        assertEquals(43_000L, tempoDeFilme(43_000L, 0L))
        assertEquals(43_000L, tempoDeSessao(43_000L, 0L))
        assertEquals(duracaoDoFilme, tempoDeFilme(duracaoDoFilme, 0L))
    }

    // ---- HLS retomado: o caso fotografado ------------------------------------

    /// O quadro exato que a foto pegou: 43 segundos de sessão, e o relógio
    /// anunciando `0:43` num filme que estava em 1h20.
    @Test
    fun `43s de sessao numa retomada de 1h19 sao 1h20 de filme`() {
        assertEquals(4_800_000L, tempoDeFilme(43_000L, deslocamento)) // 1h20m00s
    }

    /// E o «faltam», que é o mesmo erro visto do outro lado: contra a duração do
    /// arquivo, a posição da sessão anuncia o filme inteiro pela frente.
    @Test
    fun `o faltam passa a falar do que falta mesmo`() {
        val cru = duracaoDoFilme - 43_000L
        assertEquals(8_494_000L, cru) // «faltam 2:21:34» — o que a tela dizia

        val certo = duracaoDoFilme - tempoDeFilme(43_000L, deslocamento)
        assertEquals(3_737_000L, certo) // «faltam 1:02:17» — o que a ficha dizia
    }

    /// A tira desenha `posição / duração`, e as duas precisam ser da mesma régua.
    /// Com a posição crua a janela do projetor não saía da primeira célula.
    @Test
    fun `a fracao da tira sai da primeira celula`() {
        val fracaoCrua = 43_000f / duracaoDoFilme
        assertEquals(0.005f, fracaoCrua, 0.001f) // meio por cento: a célula 1

        val fracaoCerta = tempoDeFilme(43_000L, deslocamento).toFloat() / duracaoDoFilme
        assertEquals(0.562f, fracaoCerta, 0.001f) // pouco depois do meio
    }

    // ---- A volta: escolher um segundo do filme -------------------------------

    /// Ida e volta fecham. É o que garante que arrastar até onde já se está não
    /// mova o filme.
    @Test
    fun `ida e volta fecham`() {
        val naSessao = 2_926_300L
        assertEquals(naSessao, tempoDeSessao(tempoDeFilme(naSessao, deslocamento), deslocamento))
    }

    /// ⚠️ **O pior dos defeitos, virado teste.**
    ///
    /// Tocar em 20% da tira é pedir o minuto 28 do filme. Antes, esse número ia
    /// cru pro `seekTo` e virava o minuto 28 **da sessão** — que é 1h47 de filme,
    /// uma hora e vinte à frente de onde o dedo encostou.
    ///
    /// E não parava na tela: a marca de progresso grava a posição depois do
    /// pulo, então um toque errado na timeline escrevia o lugar errado no banco
    /// de três pessoas.
    @Test
    fun `tocar em 20 por cento da tira nao pula pra 1h47`() {
        val vintePorCento = (0.2f * duracaoDoFilme).toLong()
        assertEquals(1_707_400L, vintePorCento) // 28m27s de filme

        /// O que acontecia antes: o mesmo número entregue como tempo de sessão.
        assertEquals(6_464_400L, tempoDeFilme(vintePorCento, deslocamento)) // 1h47m44s

        /// O que acontece agora: a sessão não tem o minuto 28, então para no
        /// começo dela em vez de saltar pra frente. Ver `tempoDeSessao`.
        assertEquals(0L, tempoDeSessao(vintePorCento, deslocamento))
    }

    /// Um alvo que a sessão **tem** cai no lugar certo dela.
    @Test
    fun `tocar em 90 por cento cai no lugar certo da sessao`() {
        val noventa = (0.9f * duracaoDoFilme).toLong()
        assertEquals(2_926_300L, tempoDeSessao(noventa, deslocamento))
        assertEquals(noventa, tempoDeFilme(tempoDeSessao(noventa, deslocamento), deslocamento))
    }

    // ---- Os saltos relativos -------------------------------------------------

    /// Eram os únicos controles que acertavam antes desta rodada, porque somar
    /// 30s dá o mesmo pulo nas duas réguas. Depois que a `posicao` virou tempo de
    /// filme eles passaram a precisar da volta — e este teste é o que impede
    /// alguém de "simplificar" removendo-a.
    @Test
    fun `mais 30s anda 30s, e nao o deslocamento inteiro`() {
        val posicaoNoFilme = tempoDeFilme(43_000L, deslocamento)
        assertEquals(73_000L, tempoDeSessao(posicaoNoFilme + 30_000L, deslocamento))
    }

    /// ⚠️ Rebobinar antes do ponto onde a sessão começou para no começo dela.
    ///
    /// Não é arredondamento: os segmentos anteriores a 1h19 nunca foram gerados
    /// por este ffmpeg. Voltar de verdade exigiria abrir outra sessão.
    @Test
    fun `menos 10s no comeco da sessao para em zero, e nao em negativo`() {
        val quaseNoComeco = tempoDeFilme(4_000L, deslocamento)
        assertEquals(0L, tempoDeSessao(quaseNoComeco - 10_000L, deslocamento))
    }

    /// O mesmo piso, no caminho sem sessão: rebobinar no começo do filme não
    /// devolve tempo negativo pro player.
    @Test
    fun `menos 10s no comeco do filme tambem para em zero`() {
        assertEquals(0L, tempoDeSessao(4_000L - 10_000L, 0L))
    }
}
