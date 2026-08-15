import Foundation

/// Uma fala da legenda: quando entra, quando sai, e o quê.
struct FalaDaLegenda: Sendable, Equatable {
    let de: Double
    let ate: Double
    let texto: String
}

/// O leitor de WebVTT.
///
/// ## Por que o cliente desenha a legenda, em vez de o player
///
/// O AVFoundation **não enxerga nenhuma legenda deste acervo**, e isso foi medido
/// (`SondaDeLegendas`): num `direct_play` com duas legendas no plano, o
/// `AVMediaSelectionGroup` de `legible` veio com **zero opções**; no HLS, idem —
/// o servidor não põe *rendition* de legenda na playlist.
///
/// A razão é o formato: as legendas deste acervo são **arquivos externos**
/// (índices negativos no plano, `origem=arquivo`), servidos em
/// `/api/media/{arquivo}/subtitles/{índice}`. A web as consome como `<track>`; o
/// AVPlayer não tem equivalente pra faixa externa remota.
///
/// Sobram três caminhos, e o escolhido é o terceiro:
///
/// | | por que não |
/// |---|---|
/// | pedir *rendition* de legenda no HLS ao servidor | resolveria 53% e **não** o `direct_play`, que não tem playlist |
/// | montar `AVMutableComposition` com a faixa | o AVFoundation não carrega faixa de texto remota assim |
/// | **ler o VTT e desenhar** | funciona nos dois caminhos, sem servidor, e dá controle de tipografia |
///
/// ⚠️ E o terceiro tem um efeito colateral bom: o §26.7 do Android registra a
/// suspeita de que **anexar todas as legendas ao player custava quadro**. Aqui só
/// a escolhida é baixada, e ela nem passa pelo decodificador.
enum Legenda {

    /// Lê um WebVTT inteiro.
    ///
    /// ⚠️ Tolerante de propósito: bloco sem tempo é ignorado em vez de derrubar o
    /// arquivo. Uma legenda com uma linha estranha no meio ainda é uma legenda —
    /// e cair fora por causa dela deixaria o filme sem nenhuma.
    static func ler(_ texto: String) -> [FalaDaLegenda] {
        var falas: [FalaDaLegenda] = []
        /// `\r\n` existe em legenda baixada de tudo quanto é lugar.
        let linhas = texto.replacingOccurrences(of: "\r\n", with: "\n")
            .replacingOccurrences(of: "\r", with: "\n")
            .components(separatedBy: "\n")

        var i = 0
        while i < linhas.count {
            let linha = linhas[i]
            guard let (de, ate) = tempos(linha) else { i += 1; continue }

            var corpo: [String] = []
            i += 1
            while i < linhas.count, !linhas[i].trimmingCharacters(in: .whitespaces).isEmpty {
                /// ⚠️ Se a próxima linha já for um tempo, o bloco anterior acabou
                /// sem linha em branco — acontece, e engolir a fala seguinte seria
                /// perder duas de uma vez.
                if tempos(linhas[i]) != nil { break }
                corpo.append(linhas[i])
                i += 1
            }

            let junto = corpo.joined(separator: "\n").trimmingCharacters(in: .whitespacesAndNewlines)
            if !junto.isEmpty, ate > de {
                falas.append(FalaDaLegenda(de: de, ate: ate, texto: semMarcacao(junto)))
            }
        }
        return falas
    }

    /// `00:54.263 --> 00:57.733` ou `01:02:03.456 --> 01:02:05.000`.
    ///
    /// ## ⚠️ A hora é **opcional**, e ignorar isso quebra em silêncio
    ///
    /// As legendas deste acervo saem sem o campo de hora na primeira hora de
    /// filme: `00:54.263` é *54 segundos*, não 54 minutos. Um leitor que exija
    /// `HH:MM:SS` funcionaria a partir de 1h00 e falharia antes — o tipo de
    /// defeito que passa em teste curto e aparece no começo de todo filme.
    static func tempos(_ linha: String) -> (Double, Double)? {
        guard linha.contains("-->") else { return nil }
        let lados = linha.components(separatedBy: "-->")
        guard lados.count == 2,
              let de = instante(lados[0]),
              let ate = instante(lados[1])
        else { return nil }
        return (de, ate)
    }

    /// Um instante em segundos. Aceita `SS.mmm`, `MM:SS.mmm` e `HH:MM:SS.mmm`,
    /// com vírgula ou ponto no decimal (o `.srt` usa vírgula, e legenda
    /// convertida às pressas às vezes mantém).
    static func instante(_ cru: String) -> Double? {
        /// O que vem depois do tempo (posição, alinhamento) não interessa aqui.
        let limpo = cru.trimmingCharacters(in: .whitespaces)
            .components(separatedBy: " ").first ?? ""
        let comPonto = limpo.replacingOccurrences(of: ",", with: ".")
        let partes = comPonto.components(separatedBy: ":")
        guard !partes.isEmpty, partes.count <= 3 else { return nil }

        var total = 0.0
        for parte in partes {
            guard let valor = Double(parte) else { return nil }
            total = total * 60 + valor
        }
        return total
    }

    /// Tira as marcações de estilo do VTT (`<i>`, `<c.amarelo>`, `<00:01.000>`).
    ///
    /// ⚠️ **Não** interpreta: descarta. Renderizar itálico é desenho, e este
    /// arquivo é leitura — se um dia a tela quiser itálico, o lugar é lá, com o
    /// texto já separado da marcação.
    static func semMarcacao(_ texto: String) -> String {
        texto.replacingOccurrences(
            of: "<[^>]+>", with: "", options: .regularExpression,
        )
    }

    /// A fala que vale **neste** instante, ou `nil`.
    ///
    /// ⚠️ Busca binária, e não varredura: um filme tem ~1.500 falas e isto roda a
    /// cada quadro da interface. Varrer a lista sessenta vezes por segundo é o
    /// tipo de custo que não aparece em teste e aparece em bateria.
    static func falaEm(_ segundos: Double, _ falas: [FalaDaLegenda]) -> FalaDaLegenda? {
        var baixo = 0
        var alto = falas.count - 1
        while baixo <= alto {
            let meio = (baixo + alto) / 2
            let f = falas[meio]
            if segundos < f.de { alto = meio - 1 }
            else if segundos > f.ate { baixo = meio + 1 }
            else { return f }
        }
        return nil
    }
}
