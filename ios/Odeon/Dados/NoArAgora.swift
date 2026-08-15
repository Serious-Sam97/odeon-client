import Foundation

/// O que está no ar num canal, agora.
///
/// ## ⚠️ Por que isto é um tipo, e não um pedaço da tela
///
/// O `NoArAgora.kt` do Android registra o dia em que aprendeu: a peça nasceu
/// privada dentro da tela e ficou errada quando o **player** precisou da mesma
/// resposta. Quando o arquivo acaba num canal, alguém tem de perguntar «e agora,
/// o que está passando aqui?» — e a resposta tem de ser **a mesma** que a grade
/// desenha, senão a tela e o que toca discordam sobre o que é o canal.
struct QuadroNoAr: Sendable, Identifiable {
    let canalId: String
    let canalNome: String
    let numero: String
    let titulo: String
    let ano: Int?
    let categoria: String?
    let arte: String?
    let logo: String?
    let comeca: Date?
    let termina: Date?
    /// O que entra depois, quando o servidor sabe.
    let aSeguir: String?
    /// A obra e o arquivo atrás do programa. ⚠️ **Só os canais da casa os têm**, e
    /// é o que decide o que «sintonizar» faz — ver `podeTocarDireto`.
    let obraId: String?
    let arquivoId: String?
    /// ⚠️ `true` só nos canais que a **casa programa**: são os únicos com obra e
    /// arquivo atrás. É o que separa «este canal tem uma grade» de «este canal é
    /// um fluxo que alguém está transmitindo».
    let daCasa: Bool

    var id: String { canalId }

    /// ## ⚠️ «Sintonizar» são **duas coisas**, e mandar a errada dá 400
    ///
    /// Visto na tela: tocar num canal do Odeon respondeu **`o servidor respondeu
    /// 400`**. A causa é minha — eu mandava `POST /api/live/{id}/watch` pra
    /// qualquer canal, e essa rota é dos **de fora**.
    ///
    /// Um canal da casa não é um fluxo que alguém transmite: é uma **grade sobre
    /// o acervo**. Sintonizar nele é abrir o filme no minuto em que ele já está —
    /// o mesmo arquivo que a ficha abriria, com um `comecarEm` diferente. Não há
    /// stream pra pedir porque não há stream: há um horário.
    ///
    /// É por isso que a tela tem **duas seções** e não uma lista com um selo: o
    /// que o toque faz muda, e a régua do §53 diz que o produto não pode oferecer
    /// o mesmo gesto pra duas coisas que respondem diferente.
    var podeTocarDireto: Bool {
        obraId?.isEmpty == false && arquivoId?.isEmpty == false
    }

    /// Quanto do programa já passou, em segundos — o ponto onde a televisão
    /// estaria se você tivesse ligado no começo.
    ///
    /// ⚠️ Preso em zero: um relógio adiantado pediria um instante negativo.
    func quantoJaPassou(agora: Date) -> Double {
        guard let comeca else { return 0 }
        return max(0, agora.timeIntervalSince(comeca))
    }

    /// Quanto do programa já passou, de 0 a 1. `nil` quando não dá pra saber — e
    /// aí a barra **não desenha**, em vez de fingir início (§18).
    func andamento(agora: Date) -> Double? {
        guard let comeca, let termina else { return nil }
        let total = termina.timeIntervalSince(comeca)
        guard total > 0 else { return nil }
        return min(max(agora.timeIntervalSince(comeca) / total, 0), 1)
    }

    /// «faltam 32min». `nil` quando não há fim conhecido.
    func quantoFalta(agora: Date) -> String? {
        guard let termina else { return nil }
        let minutos = Int(termina.timeIntervalSince(agora) / 60)
        guard minutos >= 1 else { return nil }
        return "faltam \(minutos)min"
    }
}

/// O que está no ar em **cada** canal, agora.
///
/// ## ⚠️ O relógio é o **do servidor**
///
/// `agora` vem da resposta que acabou de chegar, e não do aparelho. Perguntar «o
/// que está no ar» com o relógio do iPhone, que pode estar minutos fora,
/// escolheria o programa errado exatamente nas viradas — que é quando esta conta
/// é feita.
///
/// ⚠️ E «no ar» é `começa <= agora < termina`. **O `<` no fim importa**: no
/// segundo exato da virada, dois programas seriam elegíveis, e a tela piscaria
/// entre os dois uma vez por filme.
func emCartaz(agora: Date, doOdeon: GradeDoOdeon?, externos: [CanalNoAr] = []) -> [QuadroNoAr] {
    /// ⚠️ **Os do Odeon vêm primeiro**, e é a ordem da web: são os que a casa
    /// programa, os únicos com obra e arquivo atrás, e portanto os únicos em que
    /// sintonizar leva a algum lugar hoje.
    let daCasa: [QuadroNoAr] = (doOdeon?.canais ?? []).compactMap { canal in
        let noAr = (doOdeon?.programas ?? [])
            .filter { $0.canal == canal.slug }
            .first { p in
                guard let i = instanteISO(p.comeca), let f = instanteISO(p.termina) else { return false }
                return agora >= i && agora < f
            }
        guard let p = noAr else { return nil }
        return QuadroNoAr(
            canalId: canal.slug, canalNome: canal.nome, numero: canal.numero,
            titulo: p.title, ano: p.year, categoria: p.categoria, arte: p.arte,
            logo: nil,
            comeca: instanteISO(p.comeca), termina: instanteISO(p.termina),
            aSeguir: nil, obraId: p.obraId, arquivoId: p.arquivoId, daCasa: true,
        )
    }

    /// ⚠️ Canal sem EPG **entra assim mesmo**, com o título vazio virando «sem
    /// programação». Ele existe e está no ar; o que não se sabe é o que está
    /// passando, e as duas coisas são diferentes (§18). Escondê-lo faria a lista
    /// mentir sobre quantos canais a casa tem.
    ///
    /// ⚠️ E este era o defeito que o dono relatou no Android — «cadê os outros
    /// canais fora os odeons?». Eles **estavam sendo buscados** e guardados desde
    /// o primeiro dia, e a tela desenhava só a grade da casa. O dado chegava e
    /// ninguém olhava: sem erro, sem tela vazia, só um pedaço do mundo que o app
    /// decidiu não mostrar. Um build verde esconde isso perfeitamente.
    let deFora = externos.map { c in
        QuadroNoAr(
            canalId: c.id, canalNome: c.name, numero: c.number ?? "—",
            titulo: (c.titulo?.isEmpty == false) ? c.titulo! : "sem programação",
            ano: nil, categoria: c.grupo, arte: c.arte, logo: c.logo,
            comeca: instanteISO(c.comeca), termina: instanteISO(c.termina),
            aSeguir: c.aSeguir, obraId: c.obraId, arquivoId: c.arquivoId, daCasa: false,
        )
    }

    return daCasa + deFora
}

/// ⚠️ Duas tentativas, e a segunda não é zelo: o servidor manda uns instantes
/// **com** fração de segundo e outros sem, e um `ISO8601DateFormatter` fixado em
/// `.withFractionalSeconds` recusa exatamente a forma que não a tem. É a mesma
/// pedra do `Virada.swift`.
func instanteISO(_ cru: String?) -> Date? {
    guard let cru, !cru.isEmpty else { return nil }
    let comFracao = ISO8601DateFormatter()
    comFracao.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
    return comFracao.date(from: cru) ?? ISO8601DateFormatter().date(from: cru)
}
