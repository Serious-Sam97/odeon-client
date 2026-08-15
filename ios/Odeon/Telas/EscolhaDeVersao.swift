import SwiftUI

/// A escolha de versão, quando o mesmo filme está no acervo mais de uma vez.
///
/// ## Por que ela existe
///
/// O dono baixou alguns filmes **duas vezes** — um em pt-BR e outro em inglês —
/// porque não achou dual audio. Até 14/08/2026 os dois ocupavam cartões separados
/// na grade, com a mesma capa, o mesmo ano e a mesma duração; a única diferença
/// visível eram dois pixels de altura. Agora o servidor os agrupa e a escolha
/// acontece aqui. São **43 grupos** no acervo.
///
/// ## ⚠️ Ela escolhe uma **obra**, e não um arquivo
///
/// Cada versão tem id próprio, progresso próprio e ficha própria. **Nada é
/// fundido** — fundir apagaria o `position_seconds` de uma das duas, que foi a
/// objeção que segurou este pedido de 04/08 a 14/08/2026.
///
/// ## ⚠️ Nem toda versão tem nome, e ela não inventa um
///
/// O 007 em inglês deste acervo não declara idioma na faixa de áudio, então chega
/// com `audio_langs: []` e sai aqui como **«versão 2»** — queda posicional, a
/// mesma que o menu de faixas do Android usa desde 06/08/2026.
///
/// Quem distingue as duas, nesse caso, é o **«parou em»**: uma diz «parou em
/// 25min» e a outra «parou em 1h22». Sem esse campo a escolha seria `818p` contra
/// `816p` — dois pixels, que é escolha nenhuma.
struct EscolhaDeVersao: View {
    let item: ItemDaBiblioteca
    let aoEscolher: (VersaoDaObra) -> Void

    var body: some View {
        let versoes = item.versoesEscolhiveis

        ZStack {
            Cores.fundo.ignoresSafeArea()

            VStack(alignment: .leading, spacing: 4) {
                Text("\(versoes.count) VERSÕES")
                    .font(Tipo.rotulo())
                    .tracking(Tipo.espacoDoRotulo)
                    .foregroundStyle(Cores.destaque)

                Text(item.title)
                    .font(Tipo.letreiro(20))
                    .foregroundStyle(Cores.texto)
                    .padding(.bottom, 14)

                /// ⚠️ A **mais adiantada primeiro** — é a resposta pra «qual eu
                /// estava vendo», que é a pergunta de quem tem dois 007: lembra-se
                /// do minuto, não do rip.
                ForEach(versoes.sorted { ($0.ondeParou ?? 0) > ($1.ondeParou ?? 0) }, id: \.id) { versao in
                    Button { aoEscolher(versao) } label: { linha(versao, entre: versoes) }
                        .buttonStyle(.plain)
                }
                Spacer()
            }
            .padding(22)
        }
    }

    private func linha(_ versao: VersaoDaObra, entre versoes: [VersaoDaObra]) -> some View {
        /// ⚠️ Queda **posicional** quando o arquivo não declara idioma — a mesma do
        /// `rotuloDaFaixa` («faixa 1»). Escrever «Inglês» num arquivo que não diz
        /// que é inglês seria inventar metadado.
        let posicao = (versoes.firstIndex { $0.id == versao.id } ?? 0) + 1
        let nome = idiomasEmPortugues(versao.idiomasDeAudio) ?? "versão \(posicao)"

        /// `818p · 2,3 GB` — item por item; a linha some inteira se não houver
        /// nenhum (§24).
        let tecnico = [
            versao.height.map { "\($0)p" },
            versao.tamanhoEmBytes.map(tamanhoCompacto),
        ].compactMap { $0 }.joined(separator: " · ")

        /// ⚠️ Filme terminado **não** tem «parou em»: a posição de quem viu até o
        /// fim é o fim.
        ///
        /// ## ⚠️ E abaixo de um minuto ele não diz o minuto — visto na tela
        ///
        /// A primeira versão escrevia `parou em \(duracaoCompacta(p))` com piso de
        /// 5 s, e a tela mostrou **«parou em 0min»**: o `duracaoCompacta` não tem
        /// segundos (de propósito — «ninguém decide ver um filme por causa de
        /// 40s»), então tudo entre 6 e 59 s vira zero. Uma linha que diz «0min»
        /// não diz nada, e é o §24 sendo violado por quem o escreveu.
        ///
        /// ⚠️ O piso de retomada **continua 5 s**: foi decisão do dono que um teco
        /// conta («a pessoa pode assistir um teco e voltar, isso já deve salvar o
        /// progresso dela»). O que muda é só a frase — retomar aos 20 s é certo,
        /// escrever «0min» é que não é.
        let parou = (versao.ondeParou).flatMap { p -> String? in
            guard p > 5, !versao.finished else { return nil }
            return p < 60 ? "parou no começo" : "parou em \(duracaoCompacta(p))"
        }

        return HStack(alignment: .center) {
            VStack(alignment: .leading, spacing: 3) {
                Text(nome)
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(Cores.texto)
                if let parou {
                    Text(parou).font(.system(size: 13)).foregroundStyle(Cores.textoApagado)
                }
            }
            Spacer()
            if !tecnico.isEmpty {
                Text(tecnico).font(.system(size: 13)).foregroundStyle(Cores.textoApagado)
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity)
        .background(Cores.fundoElevado, in: .rect(cornerRadius: 11))
        .padding(.bottom, 8)
    }
}

// MARK: - As medidas, escritas uma vez só

/// `1h36` · `36min`. Sem segundos, e é escolha: ninguém decide ver um filme por
/// causa de 40 s.
func duracaoCompacta(_ segundos: Double) -> String {
    let total = Int(segundos)
    let horas = total / 3600
    let minutos = (total % 3600) / 60
    return horas > 0 ? String(format: "%dh%02d", horas, minutos) : "\(minutos)min"
}

/// `1,9 GB` · `840 MB`.
///
/// ⚠️ **Vírgula**, porque o app inteiro escreve em português — e à mão, pra o
/// número não mudar de idioma junto com o aparelho.
func tamanhoCompacto(_ bytes: Int64) -> String {
    let gb = Double(bytes) / 1_073_741_824
    if gb >= 1 { return String(format: "%.1f GB", gb).replacingOccurrences(of: ".", with: ",") }
    return "\(Int(Double(bytes) / 1_048_576)) MB"
}

/// O nome de um idioma em português, a partir do código do contêiner.
///
/// ⚠️ É a **segunda** cópia desta tabela — a outra é `ui/Idioma.kt` do Android.
/// Não dava pra evitar pedindo o nome pronto ao servidor: ali ele manda **código**
/// de propósito, e traduzir código em nome é desenho.
///
/// ⚠️ **Código desconhecido vira `nil`, e a linha omite.** Mostrar `hun` numa
/// escolha é mostrar dado de contêiner com cara de idioma — e `und` (que em ISO
/// 639 quer dizer *undetermined*) é o caso que já mordeu o Android.
private let IDIOMAS: [String: String] = [
    "por": "Português", "pt": "Português",
    "eng": "Inglês", "en": "Inglês",
    "spa": "Espanhol", "es": "Espanhol",
    "fra": "Francês", "fre": "Francês", "fr": "Francês",
    "deu": "Alemão", "ger": "Alemão", "de": "Alemão",
    "ita": "Italiano", "it": "Italiano",
    "jpn": "Japonês", "ja": "Japonês",
    "kor": "Coreano", "ko": "Coreano",
    "rus": "Russo", "ru": "Russo",
]

func idiomasEmPortugues(_ codigos: [String]) -> String? {
    var nomes: [String] = []
    for c in codigos {
        if let n = IDIOMAS[c.trimmingCharacters(in: .whitespaces).lowercased()], !nomes.contains(n) {
            nomes.append(n)
        }
    }
    if nomes.isEmpty { return nil }
    if nomes.count == 1 { return nomes[0] }
    return nomes.dropLast().joined(separator: ", ") + " e " + nomes[nomes.count - 1]
}
