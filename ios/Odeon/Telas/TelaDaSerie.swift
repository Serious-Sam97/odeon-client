import SwiftUI

/// A ficha de uma série — o desenho aprovado em 18/08/2026, no iPhone.
///
/// ## ⚠️ Ela é a **primeira** tela de série deste app
///
/// Até hoje o iOS não tratava série de forma alguma: `item.eSerie` mudava um
/// rótulo no cartaz e mais nada, e tocar numa série abria a ficha da obra — que
/// dá **404**, porque o id de uma série é de coleção, não de obra. O mesmo
/// defeito que a TV tinha e que a locadora já havia pago.
///
/// O desenho é o da TV e o do Android, nas mesmas palavras e na mesma ordem:
/// onde eu parei, o que é isto, onde está o resto. Ver `docs/SERIES.md` no
/// `android/`.
struct TelaDaSerie: View {
    let odeon: RepositorioOdeon
    @State var modelo: ModeloDaSerie
    let aoTocar: (ObraDaLista, Double) -> Void
    let aoFechar: () -> Void

    /// ⚠️ Guarda o **número**, e não a `TemporadaDaSerie`. O destino precisa de
    /// `Hashable`, e uma temporada carrega os episódios dentro — torná-la
    /// `Hashable` faria a navegação comparar listas inteiras a cada quadro. O
    /// número identifica, e a tela de lá relê do mesmo modelo.
    @State private var temporadaAberta: Int?

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            if let erro = modelo.erro {
                Recado(titulo: "a série não abriu", detalhe: erro) {
                    Button("tentar de novo") { Task { await modelo.carregar() } }
                        .foregroundStyle(Cores.destaque)
                    Button("voltar", action: aoFechar).foregroundStyle(Cores.textoApagado)
                }
            } else if modelo.carregando {
                Esqueleto()
            } else if modelo.vazio {
                Recado(
                    titulo: "esta série está sem episódios",
                    detalhe: "o acervo tem a série, mas nenhum arquivo casou com ela",
                ) {
                    Button("voltar", action: aoFechar).foregroundStyle(Cores.destaque)
                }
            } else {
                conteudo
            }
        }
        .task { if modelo.carregando { await modelo.carregar() } }
        .navigationDestination(item: $temporadaAberta) { numero in
            TelaDaTemporada(
                odeon: odeon,
                modelo: modelo,
                numero: numero,
                aoTocar: { ep in aoTocar(ep, ep.ondeParou ?? 0) },
            )
        }
    }

    private var conteudo: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                /// ⚠️ Bloco 16:9 no topo, e não a tela inteira: numa coluna de
                /// celular, arte atrás de texto vira textura.
                ZStack(alignment: .bottom) {
                    Color.clear
                        .aspectRatio(16.0 / 9.0, contentMode: .fit)
                        .background {
                            ArteDoOdeon(odeon: odeon, caminho: modelo.panoDeFundo)
                        }
                        .clipped()
                    /// O véu pro letreiro encostar na arte sem flutuar sobre ela.
                    LinearGradient(
                        colors: [.clear, Cores.fundo],
                        startPoint: .init(x: 0.5, y: 0.35),
                        endPoint: .bottom,
                    )
                    .allowsHitTesting(false)
                }

                VStack(alignment: .leading, spacing: 0) {
                    Text(modelo.titulo)
                        .font(Tipo.letreiro(30))
                        .foregroundStyle(Cores.texto)
                    Text(contagem)
                        .font(Tipo.rotulo(11))
                        .tracking(Tipo.espacoDoRotulo)
                        .foregroundStyle(Cores.textoApagado)
                        .padding(.top, 8)

                    /// ⚠️ A sinopse da série — 115 das 120 têm. Quem não tem não
                    /// ganha parágrafo nenhum (§24).
                    if let sinopse = modelo.sinopse, !sinopse.isEmpty {
                        Text(sinopse)
                            .font(.subheadline)
                            .foregroundStyle(Cores.textoApagado)
                            .lineLimit(4)
                            .padding(.top, 12)
                    }

                    if let onde = modelo.ondeParou {
                        botao(onde).padding(.top, 20)
                        /// §24: «do começo» só havendo meio.
                        if onde.comecado {
                            Button("do começo") { aoTocar(onde.episodio, 0) }
                                .font(.subheadline)
                                .foregroundStyle(Cores.destaque)
                                .frame(minHeight: 44)
                        }
                    }

                    RotuloDeSecao(texto: "temporadas", contagem: "\(modelo.temporadas.count)")
                        .padding(.top, 22)
                }
                .padding(.horizontal, 20)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 14) {
                        ForEach(modelo.temporadas) { t in
                            CartaoDaTemporada(odeon: odeon, temporada: t) {
                                temporadaAberta = t.numero
                            }
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.bottom, 28)
            }
        }
        .background(Cores.fundo)
    }

    private func botao(_ onde: OndeParouNaSerie) -> some View {
        let cod = onde.episodio.codigo.map { "\($0) · " } ?? ""
        return Button {
            aoTocar(onde.episodio, onde.comecado ? (onde.episodio.ondeParou ?? 0) : 0)
        } label: {
            Text((onde.comecado ? "▸  continuar  " : "▸  começar  ") + cod + onde.episodio.title)
                .font(.headline)
                .foregroundStyle(Cores.fundo)
                .lineLimit(1)
                .frame(maxWidth: .infinity, minHeight: 52)
                .background(Cores.destaqueQuente, in: .capsule)
        }
        .buttonStyle(.plain)
    }

    /// `2021 · 2 temporadas · 18 episódios · 3 vistos` — cada pedaço só entra se
    /// tiver o que dizer (§24).
    private var contagem: String {
        var partes: [String] = []
        if let ano = modelo.ano { partes.append("\(ano)") }
        let t = modelo.temporadas.count
        if t > 0 { partes.append("\(t) temporada" + (t > 1 ? "s" : "")) }
        let e = modelo.quantosEpisodios
        if e > 0 { partes.append("\(e) episódio" + (e > 1 ? "s" : "")) }
        let v = modelo.quantosVistos
        if v > 0 { partes.append("\(v) visto" + (v > 1 ? "s" : "")) }
        return partes.joined(separator: "  ·  ")
    }
}

private struct CartaoDaTemporada: View {
    let odeon: RepositorioOdeon
    let temporada: TemporadaDaSerie
    let aoTocar: () -> Void

    var body: some View {
        Button(action: aoTocar) {
            VStack(alignment: .leading, spacing: 8) {
                /// ⚠️ `Color.clear` com `aspectRatio` e a arte no `background` —
                /// e **não** a imagem com `scaledToFill`. Uma imagem que preenche
                /// reivindica a largura intrínseca dela, e as temporadas sairiam
                /// com larguras diferentes. É o defeito que este app já pagou
                /// cinco vezes; ver o `MenuDeDVD`.
                /// ## ⚠️ O cartão virou **retrato** — 18/08/2026
                ///
                /// Ele era 16:9 porque a única imagem era o `still` do primeiro
                /// episódio. O servidor passou a mandar o **pôster da temporada**
                /// do TMDB, e pôster é 2:3 — num quadro deitado a `Temporada 1`
                /// do Arcane virava um olho. **A moldura segue a imagem.**
                ZStack(alignment: .bottomLeading) {
                    Color.clear
                        .aspectRatio(2.0 / 3.0, contentMode: .fit)
                        .background { ArteDoOdeon(odeon: odeon, caminho: temporada.arte) }
                        .background(Cores.fundoElevado)
                        .clipShape(.rect(cornerRadius: 12))
                    if let andado = temporada.andado {
                        BarraDeAndado(fracao: andado)
                    }
                }
                Text(temporada.rotulo)
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(Cores.texto)
                Text(sub)
                    .font(Tipo.rotulo(11))
                    .tracking(Tipo.espacoDoRotulo)
                    .foregroundStyle(Cores.textoApagado)
            }
            .frame(width: 150)
        }
        .buttonStyle(.plain)
    }

    private var sub: String {
        var partes = ["\(temporada.quantos) episódio" + (temporada.quantos > 1 ? "s" : "")]
        if temporada.vistos > 0 {
            partes.append("\(temporada.vistos) visto" + (temporada.vistos > 1 ? "s" : ""))
        }
        return partes.joined(separator: "  ·  ")
    }
}

struct BarraDeAndado: View {
    let fracao: Double

    var body: some View {
        GeometryReader { g in
            ZStack(alignment: .leading) {
                Color.black.opacity(0.55)
                Cores.destaque.frame(width: g.size.width * min(max(fracao, 0), 1))
            }
        }
        .frame(height: 4)
    }
}

struct MarcaDeVisto: View {
    var body: some View {
        Image(systemName: "checkmark")
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Cores.destaque)
            .frame(width: 24, height: 24)
            .background(.black.opacity(0.78), in: .circle)
    }
}

private struct Esqueleto: View {
    /// §15: quadros vazios, e nunca a palavra «carregando».
    var body: some View {
        VStack(alignment: .leading, spacing: 20) {
            Color.clear.aspectRatio(16.0 / 9.0, contentMode: .fit)
                .background(Cores.fundoElevado)
            HStack(spacing: 14) {
                ForEach(0..<2, id: \.self) { _ in
                    Color.clear
                        .frame(width: 230)
                        .aspectRatio(16.0 / 9.0, contentMode: .fit)
                        .background(Cores.fundoElevado)
                        .clipShape(.rect(cornerRadius: 12))
                }
            }
            .padding(.horizontal, 20)
            Spacer()
        }
    }
}

struct Recado<Acoes: View>: View {
    let titulo: String
    var detalhe: String?
    @ViewBuilder let acoes: () -> Acoes

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            Text(titulo).font(.title3).foregroundStyle(Cores.texto)
            if let detalhe {
                Text(detalhe).font(.subheadline).foregroundStyle(Cores.textoApagado)
            }
            HStack(spacing: 16) { acoes() }.frame(minHeight: 44)
        }
        .padding(28)
    }
}
