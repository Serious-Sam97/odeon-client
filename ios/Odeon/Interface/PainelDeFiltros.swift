import SwiftUI

/// O painel de filtros — a peça que faltava pro chip `filtros ▾` existir.
///
/// ## ⚠️ Ele não estava faltando por esquecimento
///
/// A barra de chips desta biblioteca tinha dois dos três do Android, e a falta
/// era **declarada**: «`filtros ▾` não entra porque o painel de filtros não
/// existe neste app — um chip que abre nada é o §8b». A ausência foi honesta
/// enquanto durou; o que ela custava era o acervo de 8.333 entradas só podendo
/// ser recortado pelo guia, de fora.
///
/// ## Os grupos vêm **do servidor**, e é o ponto
///
/// `GET /api/tag-namespaces` manda o rótulo («Gênero») e a posição de cada
/// grupo, e `GET /api/tags` manda as etiquetas com quantas obras cada uma
/// alcança. A tela não traduz `genre` nem decide a ordem: se um namespace novo
/// aparecer no acervo amanhã, ele nasce aqui sozinho, com o nome que o servidor
/// deu.
///
/// ⚠️ **Namespace sem rótulo não some.** Ele cai num grupo com o próprio
/// namespace de título — descartá-lo seria o app decidir que uma etiqueta do
/// acervo não existe porque a tabela de rótulos do servidor está incompleta, que
/// é exatamente o defeito que o `country` cru causou na ficha.
struct PainelDeFiltros: View {
    let etiquetas: [EtiquetaDoAcervo]
    let espacos: [EspacoDeEtiqueta]
    @Binding var filtros: Filtros

    /// Os grupos, na ordem do servidor.
    ///
    /// ⚠️ Grupo **sem etiqueta não vira seção vazia** (§24), e a ordem é a
    /// `posicao` de quem tem rótulo; quem não tem vai pro fim, porque um grupo
    /// que o servidor não nomeou não pode reivindicar precedência sobre os que
    /// ele nomeou.
    private var grupos: [(rotulo: String, itens: [EtiquetaDoAcervo])] {
        let porNamespace = Dictionary(grouping: etiquetas, by: \.namespace)
        let ordem = Dictionary(uniqueKeysWithValues: espacos.map { ($0.namespace, $0) })
        return porNamespace
            .map { ns, itens in
                (ordem[ns]?.posicao ?? Int.max, ordem[ns]?.rotulo ?? ns, itens)
            }
            .sorted { ($0.0, $0.1) < ($1.0, $1.1) }
            .map { (rotulo: $0.1, itens: $0.2.sorted { $0.quantasObras > $1.quantasObras }) }
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 18) {
            /// ⚠️ «limpar» só existe **com filtro ligado**. Um «limpar» sobre
            /// nada é um botão que não faz nada (§24), e nesta tela ele seria o
            /// segundo — o chip da barra já tem o seu ✕.
            if filtros.ligado {
                Button {
                    /// ⚠️ A **ordem sobrevive** ao limpar: ela não é filtro, é
                    /// como a grade está arrumada. Zerar tudo jogaria fora uma
                    /// escolha que a pessoa não pediu pra desfazer.
                    filtros = Filtros(ordem: filtros.ordem)
                } label: {
                    Text("limpar tudo")
                        .font(.system(size: 13))
                        .foregroundStyle(Cores.destaque)
                        .frame(minHeight: 44)
                        .contentShape(.rect)
                }
                .buttonStyle(.plain)
            }

            /// ## As décadas, montadas aqui
            ///
            /// ⚠️ Elas **não são etiquetas**: o servidor filtra ano por `anoDe` e
            /// `anoAte`, e não por `decade:1980`. Montá-las na tela é a mesma
            /// decisão do guia, que já manda uma década como faixa de anos — e é
            /// o que evita pedir ao servidor um vocabulário que ele não tem.
            grupo("DÉCADA") {
                FlowDeChips(itens: Self.decadas.map { "\($0)s" }) { rotulo in
                    let ano = Int(rotulo.dropLast()) ?? 0
                    let ligado = filtros.anoDe == ano
                    return Chip(texto: rotulo, ligado: ligado) {
                        if ligado {
                            filtros.anoDe = nil
                            filtros.anoAte = nil
                            filtros.rotulo = ""
                        } else {
                            filtros.anoDe = ano
                            filtros.anoAte = ano + 9
                            filtros.rotulo = rotulo
                        }
                    }
                }
            }

            grupo("FORMATO") {
                FlowDeChips(itens: ["filme", "série"]) { rotulo in
                    let valor = rotulo == "filme" ? "movie" : "series"
                    let ligado = filtros.tipo == valor
                    return Chip(texto: rotulo, ligado: ligado) {
                        filtros.tipo = ligado ? nil : valor
                        if !ligado, filtros.rotulo.isEmpty { filtros.rotulo = rotulo }
                    }
                }
            }

            ForEach(grupos, id: \.rotulo) { grupo in
                self.grupo(grupo.rotulo.uppercased()) {
                    /// ⚠️ **Trinta por grupo**, e a razão é o país: são 40 e a
                    /// cauda tem uma obra cada. Uma lista que rola por dez telas
                    /// pra chegar em «África do Sul · 1» não é um filtro, é um
                    /// censo — e o que a pessoa procura está no começo, porque a
                    /// ordem é por quantidade.
                    FlowDeChips(itens: grupo.itens.prefix(30).map(\.chave)) { chave in
                        let item = grupo.itens.first { $0.chave == chave }
                        let ligado = filtros.etiqueta == chave
                        return Chip(
                            texto: "\(item?.value ?? chave) \(item?.quantasObras ?? 0)",
                            ligado: ligado,
                        ) {
                            filtros.etiqueta = ligado ? nil : chave
                            filtros.rotulo = ligado ? "" : (item?.value ?? "")
                        }
                    }
                }
            }
        }
        .padding(.vertical, 8)
    }

    @ViewBuilder
    private func grupo(_ titulo: String, @ViewBuilder _ conteudo: () -> some View) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(titulo)
                .font(Tipo.rotulo(10)).tracking(2.4)
                .foregroundStyle(Cores.destaqueApagado)
            conteudo()
        }
    }

    /// ⚠️ Fixas, e é o que o acervo tem: de 1930 a 2020. Sair da tabela do
    /// servidor pra montá-las seria pedir uma agregação que ele não expõe, por um
    /// ganho de zero — décadas não mudam.
    private static let decadas = [2020, 2010, 2000, 1990, 1980, 1970, 1960, 1950, 1940, 1930]
}

/// Um chip do painel.
///
/// ⚠️ Alvo de **44dp** e a marca de ligado é o **fundo**, não a borda: no painel
/// há dezenas deles, e uma borda dourada a 22% (que é o que a pílula da ficha
/// usa) se perde no meio de trinta irmãos.
private struct Chip: View {
    let texto: String
    let ligado: Bool
    let aoTocar: () -> Void

    var body: some View {
        Button(action: aoTocar) {
            Text(texto)
                .font(.system(size: 13))
                .foregroundStyle(ligado ? Cores.fundo : Cores.texto)
                .padding(.horizontal, 12)
                .frame(minHeight: 44)
                .background(ligado ? Cores.destaque : Cores.fundoElevado, in: .capsule)
                .contentShape(.capsule)
        }
        .buttonStyle(.plain)
    }
}

/// Os chips quebrando linha.
///
/// ⚠️ `LazyVGrid` adaptativo e não um `HStack` que rola: trinta chips numa fila
/// horizontal escondem vinte e cinco atrás da borda, e um filtro que não se vê é
/// um filtro que não existe. Quebrar linha mostra o grupo inteiro de uma vez.
private struct FlowDeChips<Conteudo: View>: View {
    let itens: [String]
    @ViewBuilder let chip: (String) -> Conteudo

    var body: some View {
        LazyVGrid(
            columns: [GridItem(.adaptive(minimum: 96, maximum: 220), spacing: 8, alignment: .leading)],
            alignment: .leading,
            spacing: 8,
        ) {
            ForEach(itens, id: \.self) { chip($0) }
        }
    }
}
