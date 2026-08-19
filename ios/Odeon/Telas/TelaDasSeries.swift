import SwiftUI

/// As séries, biblioteca própria — o desenho aprovado em 18/08/2026.
///
/// ## ⚠️ Aba, e não prateleira
///
/// A primeira tentativa foi uma fileira de pílulas no topo da biblioteca
/// (`tudo · série · filme · anime`). O dono olhou e disse o que estava errado:
/// **a separação parecia um filtro** — uma fileira de chips idêntica à de
/// filtros logo abaixo dela.
///
/// Duas bibliotecas separadas, como o Jellyfin faz e como ele tinha proposto no
/// primeiro dia.
///
/// ## A organização é **onde você está**
///
/// | | |
/// |---|---|
/// | **na metade** | as começadas, em quadro 16:9, com `S01E04 · faltam 21min` |
/// | **todas as séries** | o resto, em pôster, com `6 temporadas` ou `63 episódios` |
struct TelaDasSeries: View {
    let odeon: RepositorioOdeon
    let insignia: Insignia
    let aoAbrirPerfil: () -> Void
    let aoSair: () -> Void
    let aoAbrirSerie: (ItemDaBiblioteca) -> Void
    let aoAbrirObra: (String) -> Void

    @State private var modelo: ModeloDaBiblioteca?
    @State private var filtros = Filtros()

    /// ⚠️ Só as séries da fileira de continuar — ela já vem colapsada por série
    /// (ver `colapsarPorSerie`), então cada linha é uma série e não um episódio.
    private var naMetade: [ItemPraContinuar] {
        (modelo?.comecados ?? []).filter { $0.tituloDaSerie != nil }
    }

    private var comecadas: Set<String> {
        Set(naMetade.compactMap(\.tituloDaSerie))
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 0) {
                    CabecalhoDaTela(
                        titulo: "séries",
                        contagem: modelo.map { m in
                            (feito: "\(m.itens.count)", total: "\(m.total ?? m.itens.count)")
                        },
                        insignia: insignia,
                        aoAbrirPerfil: aoAbrirPerfil,
                        aoSair: aoSair,
                    )

                    if !naMetade.isEmpty {
                        RotuloDeSecao(texto: "na metade", contagem: "\(naMetade.count)")
                            .padding(.horizontal, 16)
                        ScrollView(.horizontal, showsIndicators: false) {
                            HStack(alignment: .top, spacing: 14) {
                                ForEach(naMetade) { item in
                                    NaMetade(odeon: odeon, item: item) { aoAbrirObra(item.id) }
                                }
                            }
                            .padding(.horizontal, 16)
                        }
                    }

                    RotuloDeSecao(
                        texto: naMetade.isEmpty ? "todas as séries" : "o resto",
                        contagem: modelo?.total.map { "\(max(0, $0 - comecadas.count))" },
                    )
                    .padding(.horizontal, 16)

                    LazyVGrid(
                        columns: [GridItem(.adaptive(minimum: 104, maximum: 160), spacing: 12)],
                        spacing: 20,
                    ) {
                        ForEach((modelo?.itens ?? []).filter { !comecadas.contains($0.title) }) { item in
                            CartazDaSerie(odeon: odeon, item: item) {
                                if item.eSerie { aoAbrirSerie(item) } else { aoAbrirObra(item.id) }
                            }
                        }
                    }
                    .padding(.horizontal, 16)
                    .padding(.bottom, 28)
                }
            }
            .background(Cores.fundo)
        }
        .task {
            if modelo == nil {
                let m = ModeloDaBiblioteca(odeon: odeon)
                modelo = m
                await m.carregarPrateleiras()
                m.escolherPrateleira(m.prateleiras.first { $0.value.hasPrefix("série") }?.chave)
            }
        }
    }
}

private struct NaMetade: View {
    let odeon: RepositorioOdeon
    let item: ItemPraContinuar
    let aoTocar: () -> Void

    var body: some View {
        Button(action: aoTocar) {
            VStack(alignment: .leading, spacing: 8) {
                /// ⚠️ `Color.clear` + `background`, e não `scaledToFill` — a
                /// armadilha que este app já pagou seis vezes.
                Color.clear
                    .frame(width: 240)
                    .aspectRatio(16.0 / 9.0, contentMode: .fit)
                    .background { ArteDoOdeon(odeon: odeon, caminho: item.arte) }
                    .background(Cores.fundoElevado)
                    .clipShape(.rect(cornerRadius: 10))
                    .overlay(alignment: .bottomLeading) {
                        if let f = andado { BarraDeAndado(fracao: f) }
                    }
                Text(item.tituloDaSerie ?? item.title)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Cores.texto)
                    .lineLimit(1)
                Text(linha)
                    .font(Tipo.rotulo(11))
                    .tracking(Tipo.espacoDoRotulo)
                    .foregroundStyle(Cores.destaque)
                    .lineLimit(1)
            }
            .frame(width: 240)
        }
        .buttonStyle(.plain)
    }

    private var andado: Double? {
        guard let onde = item.ondeParou, let total = item.duracaoEmSegundos, total > 0
        else { return nil }
        return min(max(onde / total, 0), 1)
    }

    private var linha: String {
        var partes: [String] = []
        if let t = item.temporada, let e = item.episodio {
            partes.append(String(format: "S%02dE%02d", t, e))
        } else if let e = item.episodio {
            partes.append("ep \(e)")
        }
        if let total = item.duracaoEmSegundos, total > 0 {
            let faltam = Int((total - (item.ondeParou ?? 0)) / 60)
            if faltam > 0 { partes.append("faltam \(faltam)min") }
        }
        return partes.joined(separator: " · ")
    }
}

private struct CartazDaSerie: View {
    let odeon: RepositorioOdeon
    let item: ItemDaBiblioteca
    let aoTocar: () -> Void

    var body: some View {
        Button(action: aoTocar) {
            VStack(alignment: .leading, spacing: 6) {
                Color.clear
                    .aspectRatio(2.0 / 3.0, contentMode: .fit)
                    .background { ArteDoOdeon(odeon: odeon, caminho: item.poster) }
                    .background(Cores.fundoElevado)
                    .clipShape(.rect(cornerRadius: 8))
                    .overlay(alignment: .bottomLeading) {
                        /// ⚠️ Numa série, «andado» é quantos episódios acabaram.
                        if item.quantasObras > 0, item.quantasVistas > 0 {
                            BarraDeAndado(
                                fracao: Double(item.quantasVistas) / Double(item.quantasObras),
                            )
                        }
                    }
                Text(item.title)
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(Cores.texto)
                    .lineLimit(2)
                    .multilineTextAlignment(.leading)
                Text(quantoTem)
                    .font(Tipo.rotulo(10))
                    .tracking(Tipo.espacoDoRotulo)
                    .foregroundStyle(Cores.textoApagado)
                    .lineLimit(1)
            }
        }
        .buttonStyle(.plain)
    }

    /// ⚠️ **Um número só** — `6 temporadas · 63 ep` não cabe sob um pôster de um
    /// terço da tela. Temporadas quando há mais de uma, episódios quando há uma
    /// só: «1 temporada» não informa nada.
    private var quantoTem: String {
        if item.quantasTemporadas > 1 { return "\(item.quantasTemporadas) temporadas" }
        if item.quantasObras > 0 {
            return "\(item.quantasObras) episódio" + (item.quantasObras > 1 ? "s" : "")
        }
        return item.year.map { "\($0)" } ?? ""
    }
}
