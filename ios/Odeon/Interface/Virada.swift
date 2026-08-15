import Foundation

/// Quando a semana vira, em palavra.
///
/// ## ⚠️ Por que isto é uma função só, e não duas frases
///
/// A revista do guia e a vitrine da locadora viram **na mesma segunda-feira** —
/// é literalmente o mesmo instante, e é o que dá assunto em comum. O
/// `Virada.kt` do Android diz o que acontece quando isso se espalha: «duas telas
/// dizendo o mesmo instante com palavras diferentes fariam parecer dois
/// relógios».
///
/// Este arquivo nasceu de exatamente esse defeito, aqui: a `TelaDaLocadora` tinha
/// a conta inteira dentro dela, e o guia ia ganhar a sua. Duas contas do mesmo
/// relógio é uma que envelhece sozinha.
///
/// ## ⚠️ E a frase é **crua** de propósito
///
/// Ela devolve `segunda-feira`, e não `a vitrine vira segunda-feira` nem `até
/// segunda-feira`. Quem chama põe o verbo — a locadora diz «a vitrine vira …» e a
/// revista diz «até …», e as duas orações pedem a mesma palavra no fim.
///
/// Isso manda numa escolha que não é óbvia: passando de uma semana, ela devolve
/// **a data** (`18 de agosto`) e não `em 9 dias`. «Até em 9 dias» não é
/// português, e uma função que só serve depois de um verbo específico voltaria a
/// ser duas funções na primeira vez que alguém precisasse do outro.
///
/// ## O nulo
///
/// `nil` quando o campo não veio, não parseia, ou já passou — e aí a frase inteira
/// some (§24), em vez de escrever «vira em null» ou chutar uma data. É o mesmo
/// motivo de devolver `String?` e não `""`: string vazia ainda desenha um vão.
///
/// ⚠️ O defeito que isto conserta apareceu na primeira captura da locadora: a
/// tela mostrava **«a vitrine vira em 2026-08-17T03:00:00Z»** — carimbo ISO na
/// cara de quem está escolhendo filme. É a mesma família do «HTTP 403» que o §8b
/// condena: visível sem ser legível.
///
/// ⚠️ O `fuso` é parâmetro, e não uma leitura de `TimeZone.current` lá dentro.
/// A borda desta função **é** o fuso: a vitrine vira às 3h da manhã UTC, que em
/// São Paulo é meia-noite — e «vira segunda» ou «vira domingo» depende disso.
/// Sem poder fixá-lo, o teste da borda mediria o relógio da máquina que o roda, e
/// passaria ou falharia por motivo nenhum.
func viraQuando(_ iso: String?, agora: Date = .now, fuso: TimeZone = .current) -> String? {
    guard let cru = iso, !cru.isEmpty else { return nil }

    /// ⚠️ Duas tentativas, e a segunda não é zelo: o servidor manda
    /// `2026-08-17T03:00:00Z` **sem** fração de segundo, e um `ISO8601DateFormatter`
    /// configurado com `.withFractionalSeconds` recusa exatamente essa forma.
    let comFracao = ISO8601DateFormatter()
    comFracao.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    guard let quando = comFracao.date(from: cru) ?? ISO8601DateFormatter().date(from: cru) else {
        return nil
    }

    var calendario = Calendar(identifier: .gregorian)
    calendario.locale = Locale(identifier: "pt_BR")
    calendario.timeZone = fuso

    /// ⚠️ A conta é em **dias de calendário**, e não em «faz menos de 24h». Numa
    /// casa, «amanhã» é o dia seguinte no calendário: a vitrine que vira às 3 da
    /// manhã de segunda vira «amanhã» às 23h de domingo, e não «em 4 horas».
    let dias = calendario.dateComponents(
        [.day],
        from: calendario.startOfDay(for: agora),
        to: calendario.startOfDay(for: quando),
    ).day ?? 0

    switch dias {
    case ..<0: return nil
    case 0: return "hoje"
    case 1: return "amanhã"
    case 2 ... 6:
        /// Dentro da semana, o dia da semana é mais útil que a contagem: «vira
        /// domingo» é uma data que a pessoa reconhece sem contar nos dedos. É a
        /// decisão da web, e o `Virada.kt` a defende: «numa casa, "vira segunda" é
        /// a informação; "vira em 5 dias" é um cronômetro».
        return escrito("EEEE", quando, fuso)
    default:
        return escrito("d 'de' MMMM", quando, fuso)
    }
}

private func escrito(_ formato: String, _ quando: Date, _ fuso: TimeZone) -> String {
    let f = DateFormatter()
    f.locale = Locale(identifier: "pt_BR")
    f.timeZone = fuso
    f.dateFormat = formato
    return f.string(from: quando)
}
