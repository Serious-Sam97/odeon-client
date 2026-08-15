import Foundation

/// Normalização do endereço do servidor.
///
/// ## ⚠️ É a **quarta** cópia deste conhecimento, e o número importa
///
/// A primeira é o `ServerUrl.kt` do KMP em `clients/shared`; a segunda é a que a
/// web faz à mão em `web/src/api.ts`; a terceira é o `EnderecoDoServidor.kt` do
/// Android. Esta é a quarta.
///
/// A espec assumiu esse custo quando escolheu clientes nativos, e é por isso que
/// todos moram no mesmo repositório: **num repositório só, um `grep` alcança as
/// quatro**. Quem mudar a regra de porta padrão aqui tem que mudar nos outros
/// três — e o `PLANO.md` §1 registra isso como dívida assumida de olhos abertos.
///
/// ## O que ele resolve
///
/// O que a pessoa digita é `rog` ou `100.77.253.18`, não `https://rog:8443/`.
/// Exigir o formato exato é o tipo de atrito que faz alguém achar que o servidor
/// está fora do ar.
///
/// E «qual esquema?» não deveria ser pergunta: tenta **https primeiro** e cai pra
/// http se ninguém responder. Quem sabe qual dos dois está ligado é o servidor,
/// não quem digita.
enum EnderecoDoServidor {
    /// As portas padrão do Odeon quando não vem uma.
    static let portaHTTPS = 8443
    static let portaHTTP = 8080

    /// Limpa e completa o que foi digitado. `nil` quando não sobra host.
    ///
    /// Preserva o esquema se veio um; não inventa porta se veio uma.
    static func normalizar(_ digitado: String) -> String? {
        let limpo = digitado.trimmingCharacters(in: .whitespacesAndNewlines)
        if limpo.isEmpty { return nil }

        // ⚠️ O esquema sai ANTES de qualquer limpeza de barra — senão o `//` dele
        // é confundido com barra sobrando e `https://` vira `https:`.
        let esquema: String? =
            if limpo.hasPrefix("https://") { "https://" }
            else if limpo.hasPrefix("http://") { "http://" }
            else { nil }

        let resto = esquema.map { String(limpo.dropFirst($0.count)) } ?? limpo

        // Só o host importa; caminho digitado por engano é descartado.
        let hostPorta = resto.prefix(while: { $0 != "/" })
            .trimmingCharacters(in: .whitespaces)
        if hostPorta.isEmpty { return nil }

        // Um host precisa ter alguma coisa além de pontuação.
        if !hostPorta.contains(where: { $0.isLetter || $0.isNumber }) { return nil }

        return (esquema ?? "") + hostPorta
    }

    /// Endereços a tentar, em ordem.
    ///
    /// ⚠️ Com esquema explícito, respeita a escolha e **não** tenta o outro — se
    /// a pessoa escreveu `http://`, tentar https por baixo seria surpresa.
    static func candidatos(_ digitado: String) -> [String] {
        guard let normal = normalizar(digitado) else { return [] }

        if normal.hasPrefix("http://") || normal.hasPrefix("https://") {
            return [normal]
        }

        let depoisDosDoisPontos = normal.split(separator: ":").last.map(String.init) ?? ""
        let temPorta = Int(depoisDosDoisPontos) != nil

        if temPorta {
            // Porta explícita sem esquema: pode ser qualquer um dos dois.
            return ["https://\(normal)", "http://\(normal)"]
        }
        return [
            "https://\(normal):\(portaHTTPS)",
            "http://\(normal):\(portaHTTP)",
        ]
    }

    static func eSeguro(_ url: String) -> Bool { url.hasPrefix("https://") }
}
