import Testing
@testable import Odeon

/// Os testes do leitor de WebVTT.
///
/// ## ⚠️ Por que ele merece teste, sendo «só» texto
///
/// Porque o defeito dele é **silencioso e parcial**: uma legenda que some no
/// primeiro minuto e volta depois de uma hora não parece defeito de leitura —
/// parece arquivo ruim. E o formato deste acervo tem exatamente essa armadilha.
struct LegendaTests {

    /// O formato real, copiado da resposta do servidor (007, faixa pt-BR).
    private let vttDoAcervo = """
    WEBVTT

    00:54.370 --> 00:57.670
    Há anos que digo que o equipamento
    especial está obsoleto.

    00:57.770 --> 01:02.570
    E a análise computacional revela
    uma nova abordagem: a miniaturização.
    """

    @Test("lê o formato que o servidor devolve")
    func leOAcervo() {
        let falas = Legenda.ler(vttDoAcervo)
        #expect(falas.count == 2)
        #expect(falas[0].texto == "Há anos que digo que o equipamento\nespecial está obsoleto.")
        #expect(falas[0].de == 54.37)
    }

    /// ⚠️ **A armadilha do formato deste acervo.** `00:54.263` é 54 **segundos**,
    /// não 54 minutos: o campo de hora só aparece depois de 1h de filme. Um leitor
    /// que exija `HH:MM:SS` funcionaria a partir de 1h00 e falharia antes — defeito
    /// que passa em teste curto e aparece no começo de **todo** filme.
    @Test("a hora é opcional, e sem ela o campo da frente é MINUTO")
    func horaOpcional() {
        #expect(Legenda.instante("00:54.263") == 54.263)
        #expect(Legenda.instante("01:02.570") == 62.570)
        #expect(Legenda.instante("01:02:03.500") == 3723.5)
        #expect(Legenda.instante("12.5") == 12.5)
    }

    /// O `.srt` usa vírgula no decimal, e conversão apressada às vezes mantém.
    @Test("aceita vírgula no decimal")
    func virgula() {
        #expect(Legenda.instante("00:54,263") == 54.263)
    }

    /// O que vem depois do tempo (posição, alinhamento) não é tempo.
    @Test("ignora o que vem depois do tempo")
    func sobras() {
        let t = Legenda.tempos("00:01.000 --> 00:02.000 align:start position:10%")
        #expect(t?.0 == 1.0)
        #expect(t?.1 == 2.0)
    }

    /// ⚠️ Bloco sem tempo não derruba o arquivo. Uma legenda com uma linha
    /// estranha no meio ainda é uma legenda; cair fora deixaria o filme sem
    /// nenhuma.
    @Test("bloco estragado é pulado, o resto sobrevive")
    func tolerante() {
        let falas = Legenda.ler("""
        WEBVTT

        NOTE isto é um comentário

        lixo sem tempo nenhum

        00:01.000 --> 00:02.000
        vale
        """)
        #expect(falas.count == 1)
        #expect(falas[0].texto == "vale")
    }

    /// Sem linha em branco entre blocos — acontece, e engolir a fala seguinte
    /// perderia duas de uma vez.
    @Test("bloco colado no seguinte não come a próxima fala")
    func semLinhaEmBranco() {
        let falas = Legenda.ler("""
        WEBVTT
        00:01.000 --> 00:02.000
        primeira
        00:03.000 --> 00:04.000
        segunda
        """)
        #expect(falas.count == 2)
        #expect(falas[1].texto == "segunda")
    }

    @Test("marcação de estilo é descartada, não interpretada")
    func semMarcacao() {
        #expect(Legenda.semMarcacao("<i>oi</i> <c.amarelo>tchau</c>") == "oi tchau")
    }

    /// A busca é binária porque roda a cada quadro da interface sobre ~1.500
    /// falas. Aqui só se confere que ela acha o certo, inclusive nas bordas.
    @Test("acha a fala do instante, e nada fora dela")
    func buscaNoInstante() {
        let falas = Legenda.ler(vttDoAcervo)
        #expect(Legenda.falaEm(55, falas)?.texto.hasPrefix("Há anos") == true)
        #expect(Legenda.falaEm(60, falas)?.texto.hasPrefix("E a análise") == true)
        #expect(Legenda.falaEm(10, falas) == nil)
        #expect(Legenda.falaEm(57.7, falas) == nil, "o vão entre duas falas é silêncio")
        #expect(Legenda.falaEm(54.37, falas) != nil, "a borda de entrada conta")
    }
}
