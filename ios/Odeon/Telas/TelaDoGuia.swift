import SwiftUI

/// O guia — a revista da semana, e o índice atrás dela.
///
/// ## ⚠️ A capa é o guia; os eixos são o índice
///
/// Esta é a decisão mais cara desta tela, e ela foi paga no Android. A tela de lá
/// nasceu construída contra `GET /api/guia`, que devolve os eixos — direção,
/// elenco, gêneros, décadas, países. Um índice. E o dono olhou e disse a coisa
/// certa: «você não pegou a maior essência do Guia, ele ter informações legais
/// igual temos no web com o **gênero da semana** e **em cartaz essa semana**».
///
/// O guia da web não é um índice: é uma **revista semanal**, e ela mora noutra
/// rota (`GET /api/guia/revista`) que o app não chamava. Foi a quinta vez naquela
/// história em que o servidor já mandava o dado e o cliente não pegava.
///
/// A ordem aqui é a de lá, e ela **é** a decisão: revista em cima, índice embaixo.
/// É a diferença entre uma enciclopédia e uma revista — a enciclopédia continua
/// ali, mas não é o que se vê ao abrir.
///
/// ## Ela responde uma pergunta que nenhuma outra tela responde
///
/// A biblioteca responde «o que existe». O para-você responde «o que assisto
/// agora». O guia responde **«por onde eu entro»** — por diretor, por gênero, por
/// década, por país.
@Observable
@MainActor
final class ModeloDoGuia {
    var eixos: GuiaEixos?
    var revista: Revista?
    var carregando = true
    var recado: String?

    private let odeon: RepositorioOdeon
    init(odeon: RepositorioOdeon) { self.odeon = odeon }

    /// ⚠️ As duas rotas em paralelo, e **uma falhando não derruba a outra**. O
    /// ensaio da revista depende de uma chave de LLM que pode não existir e os
    /// eixos dependem de identificação do acervo: são dois motivos independentes
    /// de faltar, e amarrá-los num `try` só faria a capa sumir por causa do índice.
    func carregar() async {
        carregando = true
        defer { carregando = false }
        _ = try? await odeon.garantirTokenDeMidia()

        async let capa = try? await odeon.revista()
        async let indice = try? await odeon.guia()
        (revista, eixos) = await (capa, indice)

        /// O recado só existe quando **as duas** faltaram. Com capa na tela,
        /// dizer «o guia não abriu» seria desmentir os oito filmes logo acima.
        recado = (revista == nil && eixos == nil) ? "o guia não abriu" : nil
    }


}

struct TelaDoGuia: View {
    let odeon: RepositorioOdeon
    let aoAbrirObra: (String) -> Void
    /// Tocar num eixo leva à biblioteca **já filtrada**. Ver o comentário grande
    /// em `FaixaDeEixos` — esta ponte tem dois lados e os dois foram construídos.
    let insignia: Insignia
    let aoFiltrar: (Filtros) -> Void
    let aoAbrirPerfil: () -> Void
    let aoSair: () -> Void
    let aoAbrirMural: () -> Void

    @State private var modelo: ModeloDoGuia

    init(
        odeon: RepositorioOdeon,
        insignia: Insignia,
        aoAbrirObra: @escaping (String) -> Void,
        aoFiltrar: @escaping (Filtros) -> Void,
        aoAbrirPerfil: @escaping () -> Void,
        aoSair: @escaping () -> Void,
        aoAbrirMural: @escaping () -> Void,
    ) {
        self.aoAbrirMural = aoAbrirMural
        self.odeon = odeon
        self.aoAbrirObra = aoAbrirObra
        self.aoFiltrar = aoFiltrar
        self.insignia = insignia
        self.aoAbrirPerfil = aoAbrirPerfil
        self.aoSair = aoSair
        _modelo = State(wrappedValue: ModeloDoGuia(odeon: odeon))
    }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 4) {
                    CabecalhoDaTela(
                        titulo: "guia",
                        insignia: insignia, aoAbrirPerfil: aoAbrirPerfil, aoSair: aoSair,
                    )

                    /// ⚠️ **O mural entra aqui**, e ao vivo tomou o lugar dele na
                    /// barra. Cinco abas é o teto do iPhone — a sexta vira «Mais»,
                    /// e foi medido. Quem sai é o mural porque ele conta o que os
                    /// outros fizeram, e isso se lê de vez em quando; «o que está
                    /// no ar» é pergunta de agora.
                    Button(action: aoAbrirMural) {
                        HStack(spacing: 10) {
                            Text("NA CASA")
                                .font(Tipo.rotulo(11))
                                .tracking(Tipo.espacoDoRotulo)
                                .foregroundStyle(Cores.texto)
                            Spacer(minLength: 0)
                            Text("›").font(.system(size: 17)).foregroundStyle(Cores.destaque)
                        }
                        .padding(.horizontal, 12).padding(.vertical, 11)
                        .background(Cores.fundoElevado, in: .rect(cornerRadius: 10))
                    }
                    .buttonStyle(.plain)
                    .padding(.horizontal, 20)
                    .padding(.top, 12)

                    if let recado = modelo.recado {
                        Text(recado)
                            .font(.system(size: 14))
                            .foregroundStyle(Cores.textoApagado)
                            .padding(.horizontal, 20).padding(.top, 10)
                    }

                    if let revista = modelo.revista {
                        capa(revista)
                    }

                    if let eixos = modelo.eixos, !eixos.vazio {
                        indice(eixos)
                    } else if modelo.revista == nil, !modelo.carregando, modelo.recado == nil {
                        /// ⚠️ A frase só aparece quando **não há capa**. Com a
                        /// revista na tela, dizer «o guia não tem o que cruzar»
                        /// seria diagnóstico errado sobre os oito filmes acima — o
                        /// que faltou foi bem mais provavelmente a rota do índice.
                        Text("o guia ainda não tem o que cruzar — o acervo precisa de identificação")
                            .font(.system(size: 14))
                            .foregroundStyle(Cores.textoApagado)
                            .padding(.horizontal, 20).padding(.top, 10)
                    }
                }
                .padding(.vertical, 18)
            }
        }
        .task { if modelo.eixos == nil, modelo.revista == nil { await modelo.carregar() } }
    }

    // MARK: - A capa

    /// A capa da semana: o tema, o ensaio, os filmes e o que está em cartaz.
    ///
    /// ## ⚠️ Ela é igual pra todo mundo, e é isso que ela é
    ///
    /// A revista não é recomendação: o mesmo tema, os mesmos filmes e o mesmo
    /// ensaio chegam pros três moradores, e viram na mesma segunda-feira que a
    /// vitrine da locadora. É o que dá assunto em comum — o oposto do «para você»,
    /// que é sorteado por pessoa.
    @ViewBuilder
    private func capa(_ revista: Revista) -> some View {
        /// ⚠️ Capa sem filme não é capa. Sem eles restaria um letreiro solto
        /// sobre o índice, que lê como cabeçalho quebrado — o §24 aplicado à
        /// seção inteira, e não campo a campo.
        if !revista.filmes.isEmpty {
            VStack(alignment: .leading, spacing: 0) {
                /// ⚠️ O rótulo do eixo vai em `destaque`, e **não** numa versão
                /// apagada como a web faz no `.revista-eixo`. O Android já mediu
                /// esse par: o tom apagado dá 3,96:1 sobre o fundo, reprovado no
                /// AA pra letra pequena, contra 9,94:1 do destaque. Copiar a cor
                /// da web traria o defeito de contraste junto — e este rótulo é
                /// versalete espaçado de 11pt, que é o pior caso possível.
                if let eixo = revista.rotuloDoEixo {
                    Text(eixo.uppercased())
                        .font(Tipo.rotulo(11))
                        .tracking(Tipo.espacoDoRotulo)
                        .foregroundStyle(Cores.destaque)
                }

                /// O letreiro. É o maior tipo de qualquer tela do app, e é de
                /// propósito: numa revista, o tema **é** a capa.
                Text(revista.tema)
                    .font(Tipo.letreiro(34))
                    .foregroundStyle(Cores.texto)
                    .padding(.top, 6)

                if let ate = viraQuando(revista.viraEm) {
                    Text("até \(ate)")
                        .font(.system(size: 12))
                        .foregroundStyle(Cores.textoApagado)
                        .padding(.top, 4)
                }

                /// ⚠️ O ensaio tem **medida**, e no iPad isso deixa de ser
                /// detalhe: é o único texto corrido do app, e correndo 1032pt de
                /// ponta a ponta o olho perde a linha na volta. A revista impressa
                /// que esta tela imita nunca teve uma coluna de 30cm.
                ensaio(revista)
                    .frame(maxWidth: 640, alignment: .leading)

                /// Os cartazes rolam na horizontal e por isso escapam da margem —
                /// é a fileira, e não a coluna, que manda na largura.
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 10) {
                        ForEach(revista.filmes) { filme in
                            Button { aoAbrirObra(filme.id) } label: { cartaz(filme) }
                                .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .padding(.top, 16)
                .padding(.horizontal, -20)

                if let evento = revista.evento {
                    emCartaz(evento, quando: viraQuando(revista.viraEm))
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 12)
        }
    }

    /// O ensaio, quando existe — e **só** quando existe.
    ///
    /// ⚠️ Sem chave do LLM ele simplesmente não está aqui: nada de esqueleto, nada
    /// de «em breve», nada de prosa inventada pelo cliente (§18, §24).
    @ViewBuilder
    private func ensaio(_ revista: Revista) -> some View {
        if !revista.paragrafos.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                ForEach(Array(revista.paragrafos.enumerated()), id: \.offset) { _, paragrafo in
                    /// Serifado porque na web ele é `--font-serif`: é o único
                    /// texto **corrido** do app, e serifa em texto corrido é o que
                    /// separa «matéria» de «interface».
                    Text(paragrafo)
                        .font(Tipo.letreiro(15))
                        .lineSpacing(6)
                        .foregroundStyle(Cores.texto)
                }

                /// ⚠️ O selo, e ele **não é enfeite**: quem lê tem direito de
                /// saber que aquele parágrafo não foi escrito por gente — a mesma
                /// regra do crédito `WIKIPÉDIA` das curiosidades. E ele mora
                /// **dentro** do bloco do ensaio de propósito: separar os dois é
                /// como um deles some numa refatoração.
                if let por = revista.ensaioPor, !por.isEmpty {
                    Text("escrito por \(por)".uppercased())
                        .font(Tipo.rotulo(10))
                        .tracking(Tipo.espacoDoRotulo)
                        .foregroundStyle(Cores.destaque)
                }
            }
            .padding(.top, 16)
        }
    }

    /// Um filme da capa.
    ///
    /// ## ⚠️ O `visto` é uma borda, e essa contenção é a decisão
    ///
    /// A web resolve com uma linha de CSS — a borda do cartaz troca de cor — e o
    /// comentário dela diz por quê: «é a única coisa desta capa que não é igual
    /// pra todo mundo, e por isso é discreta: uma marca, não um selo». Um «✓
    /// visto» escrito sobre oito cartazes transformaria a capa da revista num
    /// relatório de progresso.
    private func cartaz(_ filme: FilmeDaCapa) -> some View {
        VStack(alignment: .leading, spacing: 5) {
            ZStack {
                Rectangle().fill(Cores.fundoElevado)
                /// ⚠️ Sem pôster o quadro fica **vazio**, e não com um ícone de
                /// imagem quebrada. São 8.598 obras sem arte no acervo; um símbolo
                /// de erro em cima delas diria que o servidor falhou, quando o que
                /// houve é que a arte não existe (§18).
                ArteDoOdeon(odeon: odeon, caminho: filme.poster)
            }
            .frame(width: 116, height: 174)
            .clipShape(.rect(cornerRadius: 4))
            .overlay {
                RoundedRectangle(cornerRadius: 4)
                    .strokeBorder(
                        filme.visto ? Cores.destaque : .white.opacity(0.10),
                        lineWidth: filme.visto ? 1.5 : 0.5,
                    )
            }

            Text(filme.titulo)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(Cores.texto)
                .lineLimit(2)
                .multilineTextAlignment(.leading)

            /// A segunda linha é montada só com o que existe, e some inteira
            /// quando não sobra nada — «· 2008» com o ponto órfão é o §24.
            if let detalhe = [filme.ano.map(String.init), filme.diretor?.isEmpty == false ? filme.diretor : nil]
                .compactMap({ $0 }).joined(separator: " · ").nilSeVazio {
                Text(detalhe)
                    .font(.system(size: 10))
                    .foregroundStyle(Cores.textoApagado)
                    .lineLimit(1)
            }
        }
        .frame(width: 116, alignment: .leading)
    }

    /// «EM CARTAZ ESTA SEMANA» — o evento.
    ///
    /// ## É o que amarra a revista ao resto do app
    ///
    /// Participar dá XP e conquista, e **quem participou aparece pra todo mundo**,
    /// que é o ponto de o evento ser coletivo. Um evento em que ninguém sabe quem
    /// foi não é evento, é tarefa.
    private func emCartaz(_ evento: EventoDaSemana, quando: String?) -> some View {
        /// ⚠️ Só abre ficha quando o tipo é `obra`. O `id` de uma saga é de
        /// coleção, e mandá-lo pra tela da obra daria erro — oferecer o toque que
        /// vai falhar é o §53 ao contrário.
        Button { if evento.abreFicha { aoAbrirObra(evento.id) } } label: {
            HStack(alignment: .top, spacing: 12) {
                /// ⚠️ Sem pôster o quadro **não existe** — e isto é da web,
                /// palavra por palavra: «uma moldura vazia ao lado do texto lê
                /// como imagem quebrada». Coleção do TMDB nem sempre traz arte.
                if evento.poster != nil {
                    ArteDoOdeon(odeon: odeon, caminho: evento.poster)
                        .frame(width: 58, height: 87)
                        .clipped()
                        .clipShape(.rect(cornerRadius: 4))
                }

                VStack(alignment: .leading, spacing: 4) {
                    Text("EM CARTAZ ESTA SEMANA")
                        .font(Tipo.rotulo(10))
                        .tracking(Tipo.espacoDoRotulo)
                        .foregroundStyle(Cores.destaque)
                    Text(evento.titulo)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Cores.texto)
                        .multilineTextAlignment(.leading)
                    Text(chamadaDoEvento(evento, quando: quando))
                        .font(.system(size: 13))
                        .foregroundStyle(Cores.textoApagado)
                        .multilineTextAlignment(.leading)
                    if !evento.participantes.isEmpty {
                        Text(evento.participantes.joined(separator: ", ")
                            + (evento.participantes.count == 1 ? " participou" : " participaram"))
                            .font(.system(size: 11))
                            .foregroundStyle(Cores.destaque)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Cores.fundoElevado, in: .rect(cornerRadius: 8))
        }
        .buttonStyle(.plain)
        .padding(.top, 20)
    }

    // MARK: - O índice

    private func indice(_ eixos: GuiaEixos) -> some View {
        VStack(alignment: .leading, spacing: 14) {
            faixaDeEixos("gêneros", eixos.generos)
            faixaDeEixos("décadas", eixos.decadas)

            if !eixos.paises.isEmpty {
                faixaDeEixos("de onde vêm", eixos.paises)
                /// ⚠️ O número que a web insiste em mostrar junto: sem ele o eixo
                /// diz «Estados Unidos 491» e o resto vira rodapé. É a pergunta
                /// que ninguém conseguia fazer antes de ele existir.
                if eixos.foraDeHollywood > 0 {
                    Text("\(eixos.foraDeHollywood.comMilhar) filmes vêm de fora dos Estados Unidos")
                        .font(.system(size: 12))
                        .foregroundStyle(Cores.destaque)
                        .padding(.horizontal, 20)
                }
            }

            fileiraDePessoas("direção", eixos.direcao)
            fileiraDePessoas("elenco", eixos.elenco)
            fileiraDePessoas("trilha", eixos.trilha)
        }
        .padding(.top, 26)
    }

    /// Uma fileira de eixos que não são pessoa.
    ///
    /// ## ⚠️ A ponte tem dois lados, e os dois existem
    ///
    /// O Android passou um tempo com esta fileira **muda**, e o comentário de lá
    /// registra o motivo: «tocar num eixo não filtra a biblioteca ainda… oferecer
    /// o toque que não leva a lugar nenhum seria o §8b; então nenhum eixo é
    /// clicável». Só depois que a biblioteca ganhou filtro é que o toque entrou.
    ///
    /// Aqui os dois lados foram construídos no mesmo dia — o `Filtros` desta
    /// pasta é a fatia exata que estas pílulas pedem, e a grade mostra qual filtro
    /// está ligado com um ✕ ao lado. **Sem o ✕ isto seria uma armadilha**: tocar
    /// em «Terror» e cair numa grade de 300 sem caminho de volta pras 8.273.
    @ViewBuilder
    private func faixaDeEixos(_ titulo: String, _ faixas: [FaixaDoGuia]) -> some View {
        if !faixas.isEmpty {
            VStack(alignment: .leading, spacing: 9) {
                RotuloDeSecao(texto: titulo.uppercased(), contagem: "\(faixas.count)")
                    .padding(.horizontal, 20)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(faixas) { faixa in
                            Button { aoFiltrar(Filtros.doEixo(faixa)) } label: {
                                HStack(spacing: 6) {
                                    Text(faixa.rotulo)
                                        .font(.system(size: 13))
                                        .foregroundStyle(Cores.texto)
                                    Text(faixa.obras.comMilhar)
                                        .font(.system(size: 12).monospacedDigit())
                                        .foregroundStyle(Cores.destaque)
                                }
                                .padding(.horizontal, 12).padding(.vertical, 7)
                                .background(Cores.fundoElevado, in: .capsule)
                            }
                            .buttonStyle(.plain)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
    }

    /// Uma fileira de gente, com rosto e o quanto do trabalho dela você já viu.
    ///
    /// ## ⚠️ «7 de 23» é o que faz isto ser um guia e não um elenco
    ///
    /// A contagem cruza a pessoa com **o seu** histórico: quantos títulos dela
    /// existem no acervo e quantos você terminou. Sem ela a fileira é uma lista de
    /// nomes; com ela é um mapa do que falta.
    ///
    /// ⚠️ E ela **não é tocável**, ao contrário das pílulas acima: filtrar por
    /// pessoa é o `person=` do servidor, que esta fatia de `Filtros` não declara
    /// porque nenhuma tela daqui o monta. Oferecer o toque antes disso seria o
    /// §8b — e o Android é a prova de que essa espera vale a pena.
    @ViewBuilder
    private func fileiraDePessoas(_ titulo: String, _ pessoas: [PessoaDoGuia]) -> some View {
        if !pessoas.isEmpty {
            VStack(alignment: .leading, spacing: 9) {
                RotuloDeSecao(texto: titulo.uppercased(), contagem: "\(pessoas.count)")
                    .padding(.horizontal, 20)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .top, spacing: 12) {
                        ForEach(pessoas) { pessoa in
                            VStack(spacing: 5) {
                                ZStack {
                                    Circle().fill(Cores.fundoElevado)
                                    if pessoa.imagePath != nil {
                                        ArteDoOdeon(odeon: odeon, caminho: pessoa.imagePath)
                                    } else {
                                        /// Sem rosto, a inicial — e não um ícone de
                                        /// pessoa genérico, que faria 40 círculos
                                        /// idênticos numa fileira de nomes distintos.
                                        Text(pessoa.name.prefix(1).uppercased())
                                            .font(Tipo.letreiro(22))
                                            .foregroundStyle(Cores.destaque.opacity(0.7))
                                    }
                                }
                                .frame(width: 64, height: 64)
                                .clipShape(.circle)

                                Text(pessoa.name)
                                    .font(.system(size: 11))
                                    .foregroundStyle(Cores.texto)
                                    .multilineTextAlignment(.center)
                                    .lineLimit(2)
                                if pessoa.obras > 0 {
                                    Text("\(pessoa.terminadas) de \(pessoa.obras)")
                                        .font(.system(size: 10).monospacedDigit())
                                        .foregroundStyle(Cores.textoApagado)
                                }
                            }
                            .frame(width: 76)
                        }
                    }
                    .padding(.horizontal, 20)
                }
            }
        }
    }
}

/// A chamada do evento, montada aqui porque é **desenho** e não dado — a mesma
/// divisão do `Acontecimento.frase` do mural.
///
/// A web escreve «até segunda» fixo no meio da frase; aqui o prazo vem do mesmo
/// relógio da vitrine, e quando o `vira_em` não diz nada a oração inteira some,
/// em vez de prometer um dia que ninguém conferiu.
///
/// ⚠️ «suas de obras» só entra numa saga: em obra única a frase seria «você já viu
/// 0 de 1», que é escrever com número aquilo que o convite já diz.
func chamadaDoEvento(_ evento: EventoDaSemana, quando: String?) -> String {
    if evento.participou { return "Você participou." }
    let prazo = quando.map { " até \($0)" } ?? ""
    if evento.obras > 1 {
        return "Termine uma das \(evento.obras) obras\(prazo) pra participar. "
            + "Você já viu \(evento.suas) de \(evento.obras)."
    }
    return "Termine\(prazo) pra participar."
}

extension String {
    var nilSeVazio: String? { isEmpty ? nil : self }
}
