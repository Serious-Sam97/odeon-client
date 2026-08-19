import SwiftUI

/// Os episódios de uma temporada, no iPhone.
///
/// ⚠️ **Lista, e não grade** — o episódio não se escolhe pela imagem (a capa é a
/// mesma nos dezoito), escolhe-se pelo número e pelo que já aconteceu com ele. A
/// fileira põe quadro, número, título e estado na mesma linha de leitura.
///
/// ⚠️ Falta a sinopse por episódio, e é falta do servidor: `ObraDaLista` não traz
/// `overview`. Ver `PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`. Enquanto não vier, a linha não é
/// desenhada — não se inventa sinopse a partir do título (§18).
struct TelaDaTemporada: View {
    let odeon: RepositorioOdeon
    let modelo: ModeloDaSerie
    let numero: Int
    let aoTocar: (ObraDaLista) -> Void

    private var temporada: TemporadaDaSerie? { modelo.temporada(numero) }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()
            if let t = temporada {
                ScrollView {
                    VStack(alignment: .leading, spacing: 4) {
                        cabecalho(t).padding(.horizontal, 20).padding(.bottom, 14)
                        ForEach(t.episodios) { ep in
                            Fileira(odeon: odeon, episodio: ep) { aoTocar(ep) }
                        }
                    }
                    .padding(.bottom, 28)
                }
            } else {
                Recado(
                    titulo: "esta temporada não está aqui",
                    detalhe: modelo.erro ?? "os episódios dela não chegaram",
                ) { EmptyView() }
            }
        }
        .navigationTitle(temporada?.rotulo ?? "")
        .navigationBarTitleDisplayMode(.inline)
    }

    private func cabecalho(_ t: TemporadaDaSerie) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(t.rotulo).font(Tipo.letreiro(28)).foregroundStyle(Cores.texto)
            Text(sub(t))
                .font(Tipo.rotulo(11))
                .tracking(Tipo.espacoDoRotulo)
                .foregroundStyle(Cores.textoApagado)
        }
    }

    private func sub(_ t: TemporadaDaSerie) -> String {
        var partes = ["\(t.quantos) episódio" + (t.quantos > 1 ? "s" : "")]
        if t.vistos > 0 { partes.append("\(t.vistos) visto" + (t.vistos > 1 ? "s" : "")) }
        return partes.joined(separator: "  ·  ")
    }
}

private struct Fileira: View {
    let odeon: RepositorioOdeon
    let episodio: ObraDaLista
    let aoTocar: () -> Void

    private var visto: Bool { episodio.finished == true }

    private var andado: Double {
        guard let onde = episodio.ondeParou,
              let total = episodio.duracaoEmSegundos, total > 0 else { return 0 }
        return min(max(onde / total, 0), 1)
    }

    var body: some View {
        Button(action: aoTocar) {
            HStack(alignment: .center, spacing: 14) {
                ZStack(alignment: .topTrailing) {
                    /// Mesma armadilha do cartão da temporada: `Color.clear` com
                    /// a arte no `background`, pra a fileira não herdar a largura
                    /// intrínseca da imagem.
                    Color.clear
                        .frame(width: 128)
                        .aspectRatio(16.0 / 9.0, contentMode: .fit)
                        .background { ArteDoOdeon(odeon: odeon, caminho: episodio.arte) }
                        .background(Cores.fundoElevado)
                        .clipShape(.rect(cornerRadius: 8))
                        .overlay(alignment: .bottomLeading) {
                            /// Um **ou** outro: quem terminou não parou no meio.
                            if !visto, andado > 0 { BarraDeAndado(fracao: andado) }
                        }
                        .overlay {
                            if visto { Color.black.opacity(0.45).clipShape(.rect(cornerRadius: 8)) }
                        }
                    if visto { MarcaDeVisto().padding(4) }
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack(alignment: .top, spacing: 10) {
                        if let n = episodio.episodio {
                            Text("\(n)")
                                .font(Tipo.letreiro(20))
                                .foregroundStyle(Cores.destaqueApagado)
                        }
                        Text(episodio.title)
                            .font(.subheadline.weight(.semibold))
                            /// Visto apaga o título — é a marca que se lê de relance.
                            .foregroundStyle(visto ? Cores.textoApagado : Cores.texto)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                    Text(sub)
                        .font(Tipo.rotulo(11))
                        .tracking(Tipo.espacoDoRotulo)
                        .foregroundStyle(andado > 0 && !visto ? Cores.destaque : Cores.textoApagado)
                    /// ⚠️ A sinopse chegou em 18/08/2026 — era «falta do
                    /// servidor, não desenho», e deixou de ser. Metade dos
                    /// episódios não tem, e aí a linha **não existe** (§24).
                    if let sinopse = episodio.overview, !sinopse.isEmpty {
                        Text(sinopse)
                            .font(.caption)
                            .foregroundStyle(Cores.textoApagado)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                    }
                }
                Spacer(minLength: 0)
            }
            /// ⚠️ 64pt de mínima e a fileira **inteira** é o alvo — a régua de
            /// toque da casa.
            .frame(minHeight: 64)
            .padding(.horizontal, 16)
            .padding(.vertical, 8)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }

    /// `S01E04 · 43min`, e `· faltam 21min` quando há onde voltar. Cada pedaço só
    /// entra se existir — duração nula não vira `0min` (§18).
    private var sub: String {
        var partes: [String] = []
        if let c = episodio.codigo { partes.append(c) }
        if let d = episodio.duracaoEmSegundos, d > 0 { partes.append("\(Int(d / 60))min") }
        if andado > 0, !visto {
            let faltam = Int(((episodio.duracaoEmSegundos ?? 0) - (episodio.ondeParou ?? 0)) / 60)
            if faltam > 0 { partes.append("faltam \(faltam)min") }
        }
        return partes.joined(separator: "  ·  ")
    }
}
