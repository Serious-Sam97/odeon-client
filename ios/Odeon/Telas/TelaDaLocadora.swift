import SwiftUI

/// A locadora — a vitrine.
///
/// ## ⚠️ O que esta tela **não** faz, e é decisão
///
/// Ela não pega fita. `POST /api/locadora/alugar` **escreve no acervo compartilhado
/// por três pessoas**: um empréstimo criado por engano fica no perfil de alguém, e
/// desfazê-lo é do outro lado. O botão entra quando houver quem confirme na tela.
///
/// ⚠️ E há um segundo motivo, que é o §53: a ficha do Android oferece «pegar a
/// fita» em toda obra e o servidor recusa algumas com **403**, porque a locadora
/// tem 600 caixas sobre 17.498 obras e o cliente **não tem como prever qual**.
/// Está escrito como Pedido 1 no `PEDIDOS-AO-SERVIDOR.md` e continua aberto. Um
/// botão que leva a 403 é defeito, não funcionalidade.
/// O que dá pra fazer com uma caixa **agora**.
///
/// ## ⚠️ Ela existe porque o app voltou a poder prever a recusa
///
/// O «levar pra casa» esteve fora do produto inteiro por causa do §53 — «não
/// oferecer o que a validação vai negar» — e a validação era imprevisível.
/// Deixou de ser em 17/08/2026, quando o servidor passou a mandar `caixa_ids` em
/// cada empréstimo: todos os ids que abrem aquela caixa.
///
/// ⚠️ Os quatro casos são os mesmos do Android, com os mesmos nomes. Divergir
/// faria a mesma caixa dizer coisas diferentes em dois aparelhos da mesma casa.
enum SituacaoDaCaixa: Equatable {
    /// Dá pra levar, e o número é quantas ainda cabem no seu limite.
    case livre(Int)
    /// Já está com você — é o 403 «esta já está com você», previsto aqui.
    case comigo
    /// Está com outra pessoa. ⚠️ O nome vem junto porque «indisponível» não é
    /// resposta numa casa de três: quem está com a fita é a informação.
    case comOutro(String)
    /// Você está no limite. Não é sobre esta caixa, é sobre você.
    case noLimite
}

@Observable
@MainActor
final class ModeloDaLocadora {
    var loja: Loja?
    /// O que está em mãos, e a régua do formato. `nil` até chegar — e aí toda
    /// caixa é disco, que é o padrão honesto.
    var prateleira: Prateleira?
    var recado: String?

    /// A fita cujo «devolver» está **armado**. Ver `devolver`.
    var confirmandoDevolver: Int?
    /// A que está sendo devolvida agora — tranca contra dois toques.
    var devolvendo: Int?
    /// A caixa cujo «levar» está armado, e a que está sendo levada.
    var confirmandoLevar: String?
    var levando: String?

    private let odeon: RepositorioOdeon
    init(odeon: RepositorioOdeon) { self.odeon = odeon }

    /// O que dá pra fazer com esta caixa, **sem perguntar ao servidor**.
    ///
    /// ⚠️ A ordem das perguntas é a ordem da verdade: primeiro «está fora?»
    /// (porque uma caixa emprestada não é sua nem livre, independente do seu
    /// limite), depois «é minha?», e só então o limite.
    func situacao(daCaixa id: String) -> SituacaoDaCaixa {
        let fora = (prateleira?.emprestadas ?? [])
            .first { $0.caixaId == id || $0.caixaIds.contains(id) }
        if let fora { return fora.meu ? .comigo : .comOutro(fora.quemNome) }
        let podem = prateleira?.possoPegar ?? 0
        return podem > 0 ? .livre(podem) : .noLimite
    }

    /// Levar a caixa. **Escreve no acervo de três pessoas** — dois toques.
    ///
    /// ⚠️ A regra é a mesma do Android e vem do §11: pegar e devolver pedem
    /// confirmação na tela, e nenhum dos dois é disparado por navegação,
    /// montagem ou retentativa automática. **Nada aqui tenta de novo sozinho**:
    /// um `POST` que cria empréstimo repetido em silêncio é como duas fitas saem
    /// da estante por um toque.
    func levar(_ id: String) async {
        guard levando == nil else { return }
        guard confirmandoLevar == id else { confirmandoLevar = id; return }
        confirmandoLevar = nil
        levando = id
        do { try await odeon.alugar(obra: id) } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "não deu pra levar a fita"
        }
        levando = nil
        await carregar()
    }

    /// Devolver. **Escreve**, e por isso pede o segundo toque como o levar.
    func devolver(_ emprestimo: Int) async {
        guard devolvendo == nil else { return }
        guard confirmandoDevolver == emprestimo else { confirmandoDevolver = emprestimo; return }
        confirmandoDevolver = nil
        devolvendo = emprestimo
        do { try await odeon.devolver(emprestimo: emprestimo) } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "a devolução não completou"
        }
        devolvendo = nil
        await carregar()
    }

    /// Pedir de volta. ⚠️ **Um toque só**, e é decisão: ela não encurta prazo de
    /// ninguém — põe um recado na caixa de quem está com a fita. O efeito é um
    /// aviso, não uma perda, e pedir confirmação pra avisar seria cerimônia.
    func pedirDeVolta(_ emprestimo: Int) async {
        do { try await odeon.pedirDeVolta(emprestimo: emprestimo) } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "o pedido não foi"
        }
        await carregar()
    }

    func carregar() async {
        do {
            _ = try? await odeon.garantirTokenDeMidia()
            /// ⚠️ As duas em paralelo, e **a prateleira falhando não derruba a
            /// loja**: a vitrine é o que a tela é, e o que está em mãos é um
            /// acréscimo. Amarrá-las num `try` só faria a locadora sumir por causa
            /// de um empréstimo.
            async let vitrine = try await odeon.estantes()
            async let emMaos = try? await odeon.prateleira()
            loja = try await vitrine
            prateleira = await emMaos
            await odeon.conferirTokenDeMidia(comArte: loja?.estantes.first?.caixas.first?.poster)
            recado = nil
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "a loja não abriu"
        }
    }

    func capa(_ caixa: CaixaExposta) -> URL? { odeon.urlDaArte(caixa.poster) }

    /// «a vitrine vira domingo» · `nil`.
    ///
    /// ## ⚠️ A conta **saiu daqui**, e a saída é a decisão
    ///
    /// Ela morava inteira nesta classe, e ia ser reescrita no guia — porque a
    /// revista vira **no mesmo instante** que esta vitrine. Duas contas do mesmo
    /// relógio é uma que envelhece sozinha, e o `Virada.kt` do Android já tinha o
    /// nome disso: «duas telas dizendo o mesmo instante com palavras diferentes
    /// fariam parecer dois relógios».
    ///
    /// O que sobrou aqui é o **verbo**, que é o que muda entre as duas telas: a
    /// locadora diz «a vitrine vira …» e a revista diz «até …». Ver `Virada.swift`
    /// pro resto, inclusive pro carimbo ISO cru que apareceu nesta tela.
    var quandoVira: String? {
        viraQuando(loja?.viraEm).map { "a vitrine vira \($0)" }
    }
}

struct TelaDaLocadora: View {
    let odeon: RepositorioOdeon
    let insignia: Insignia
    let aoAbrirPerfil: () -> Void
    let aoSair: () -> Void
    let aoEscolher: (CaixaExposta) -> Void
    /// ⚠️ O menu do disco leva ao player, e **só a raiz** sabe abrir um player.
    let aoTocarDoMenu: (MenuDoDisco, Double) -> Void

    @Environment(\.horizontalSizeClass) private var largura

    /// ⚠️ A caixa cresce com a sala, pela mesma razão que o cartaz da grade: numa
    /// prateleira de 1376pt, uma caixa de 104 é miniatura de vitrine, não fita que
    /// se pega. E aqui dói mais que na grade — a caixa **é o produto** (§1.3: «um
    /// catálogo de arquivos lista linhas; uma locadora tem caixas que se pegam»),
    /// e uma caixa pequena demais para o dedo girar deixa de ser objeto.
    /// ⚠️ As medidas vêm do **formato**, não de um número meu. Ver `Medidas`:
    /// DVD 102×144×11, VHS 79×144×19 — os do Android. A escala muda o tamanho na
    /// prateleira sem mudar a proporção do objeto.
    private var escala: CGFloat { largura == .regular ? 1.0 : 0.72 }

    /// A obra cujo **disco está no aparelho**. `nil` é o menu fechado.
    ///
    /// ⚠️ Ele mora **fora** do `naMao` porque o palco fecha quando o menu abre: o
    /// disco saiu da caixa e foi pro aparelho, e a caixa vazia continuar na tela
    /// atrás do menu seria o objeto em dois lugares ao mesmo tempo.
    @State private var noAparelho: NoAparelho?

    /// ⚠️ Um `String?` não serve ao `fullScreenCover(item:)` — ele quer
    /// `Identifiable`. Envolver é mais honesto que conformar `String`.
    private struct NoAparelho: Identifiable { let id: String }

    /// A caixa que está **na mão**. `nil` é a loja em repouso.
    ///
    /// ⚠️ Ela mora aqui e não na prateleira: o palco fica por cima da tela inteira
    /// e **fora da rolagem**, e um estado dentro da fileira subiria junto com ela.
    @State private var naMao: CaixaExposta?

    @State private var modelo: ModeloDaLocadora

    init(
        odeon: RepositorioOdeon, insignia: Insignia,
        aoAbrirPerfil: @escaping () -> Void, aoSair: @escaping () -> Void,
        aoEscolher: @escaping (CaixaExposta) -> Void,
        aoTocarDoMenu: @escaping (MenuDoDisco, Double) -> Void,
    ) {
        self.odeon = odeon
        self.insignia = insignia
        self.aoAbrirPerfil = aoAbrirPerfil
        self.aoSair = aoSair
        self.aoEscolher = aoEscolher
        self.aoTocarDoMenu = aoTocarDoMenu
        _modelo = State(wrappedValue: ModeloDaLocadora(odeon: odeon))
    }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 30) {
                    cabecalho

                    if let recado = modelo.recado {
                        Text(recado).font(.system(size: 14)).foregroundStyle(Cores.textoApagado)
                    }

                    comigo

                    ForEach(modelo.loja?.estantes ?? []) { estante in
                        prateleira(estante)
                    }

                    /// ⚠️ A nota fecha a visita: anda-se pelas estantes e, na
                    /// saída, o caixa entrega a notinha.
                    if let p = modelo.prateleira {
                        NotaDoCaixa(prateleira: p, noAcervo: modelo.loja?.noAcervo ?? 0)
                            .frame(maxWidth: .infinity)
                            .padding(.top, 14)
                    }
                }
                .padding(.vertical, 18)
            }
        }
        .task { if modelo.loja == nil { await modelo.carregar() } }
        .overlay {
            if let caixa = naMao {
                PalcoDaCaixa(
                    odeon: odeon, caixa: caixa,
                    medidas: medidasDe(caixa), ehVhs: ehVhs(caixa), cor: cor(caixa),
                    aoFechar: { withAnimation(.easeOut(duration: 0.2)) { naMao = nil } },
                    aoVerAFicha: { naMao = nil; aoEscolher(caixa) },
                    /// A fita não tem menu, tem rebobinar (§14.4).
                    aoPorNoAparelho: ehVhs(caixa) ? nil : {
                        naMao = nil
                        noAparelho = NoAparelho(id: caixa.id)
                    },
                    /// As frases de levar, decididas **aqui** — ver `situacao`.
                    /// Só a `livre` vira botão; as outras três são respostas.
                    acaoDeLevar: {
                        if case let .livre(quantas) = modelo.situacao(daCaixa: caixa.id) {
                            if modelo.levando == caixa.id { return "levando…" }
                            if modelo.confirmandoLevar == caixa.id { return "levar mesmo?" }
                            return "levar pra casa · \(quantas) \(quantas == 1 ? "resta" : "restam")"
                        }
                        return nil
                    }(),
                    avisoDaCaixa: {
                        switch modelo.situacao(daCaixa: caixa.id) {
                        case .comigo: "esta já está com você"
                        case let .comOutro(quem): "está com \(quem)"
                        case .noLimite: "você está no limite de fitas"
                        case .livre: nil
                        }
                    }(),
                    aoLevar: { Task { await modelo.levar(caixa.id) } },
                )
                .transition(.opacity.combined(with: .scale(scale: 0.86)))
            }
        }
        .fullScreenCover(item: $noAparelho) { alvo in
            MenuDeDVD(
                odeon: odeon, obraId: alvo.id,
                aoTocar: { disco, segundos in
                    noAparelho = nil
                    aoTocarDoMenu(disco, segundos)
                },
                aoFechar: { noAparelho = nil },
            )
        }
    }

    /// «COMIGO» — as fitas que estão fora da estante.
    ///
    /// ## ⚠️ Ela vem **antes** das prateleiras, e é decisão
    ///
    /// O que está na sua mão é o que você precisa devolver; o que está na estante
    /// é o que você pode pegar. A primeira é dívida, a segunda é convite — e
    /// dívida se lê antes.
    ///
    /// ⚠️ E ela mostra **as suas e as dos outros**, separadas. A prateleira mistura
    /// tudo de propósito, porque quem te barra pode ser qualquer morador e ver isso
    /// é parte da ideia; mas nas suas dá pra devolver e nas dos outros só dá pra
    /// pedir, e um gesto que muda de dono não pode ficar na mesma fileira (§53).
    ///
    /// ⚠️ **Devolver e pedir não existem aqui ainda.** As duas escrevem no acervo
    /// compartilhado, e o §11 é explícito: mexer no empréstimo de alguém precisa de
    /// quem confirme na tela. Por enquanto esta seção **conta**, e não age.
    @ViewBuilder
    private var comigo: some View {
        if let prateleira = modelo.prateleira, !prateleira.emprestadas.isEmpty {
            VStack(alignment: .leading, spacing: 14) {
                fileiraEmMaos("COMIGO", prateleira.minhas)
                fileiraEmMaos("COM OS OUTROS", prateleira.dosOutros)
            }
        }
    }

    @ViewBuilder
    private func fileiraEmMaos(_ titulo: String, _ fitas: [Emprestada]) -> some View {
        if !fitas.isEmpty {
            VStack(alignment: .leading, spacing: 12) {
                RotuloDeSecao(texto: titulo, contagem: "\(fitas.count)")
                    .padding(.horizontal, 20)

                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(alignment: .bottom, spacing: largura == .regular ? 30 : 22) {
                        ForEach(fitas) { fita in
                            VStack(alignment: .leading, spacing: 8) {
                                CaixaEm3D(
                                    medidas: medidasDaFita(fita).vezes(escala),
                                    giravel: false,
                                ) { lado, luz in
                                    FaceDaCaixa(
                                        odeon: odeon, lado: lado, luz: luz,
                                        medidas: medidasDaFita(fita).vezes(escala),
                                        ehVhs: ehVhsDaFita(fita), titulo: fita.titulo,
                                        cor: corDaFita(fita), capa: fita.poster,
                                    )
                                }

                                /// ⚠️ A cinta de papel com o prazo — e ela **some**
                                /// quando não há data. «Sem prazo» ocuparia a cinta
                                /// com uma não-informação (§24).
                                if let prazo = prazoDoEmprestimo(fita.venceEm) {
                                    Text(fita.meu ? prazo.frase : "com \(fita.quemNome)")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundStyle(prazo.dias <= 2 && fita.meu
                                            ? Color(hex: 0xD9534F) : Cores.tintaDoBilhete)
                                        .padding(.horizontal, 8).padding(.vertical, 3)
                                        .background(Cores.papel, in: .rect(cornerRadius: 2))
                                }

                                /// ## ⚠️ Devolver e pedir **passaram a existir** — 17/08/2026
                                ///
                                /// Esta seção «contava e não agia», e a folha
                                /// acima dizia por quê: as duas escrevem no
                                /// acervo compartilhado, e o §11 pede quem
                                /// confirme na tela.
                                ///
                                /// A confirmação chegou, e é a mesma do Android:
                                /// **dois toques pra devolver** (o primeiro arma,
                                /// o segundo devolve) e **um só pra pedir** — ela
                                /// não encurta o prazo de ninguém, põe um recado
                                /// na caixa de quem está com a fita. O efeito é
                                /// um aviso, não uma perda, e pedir confirmação
                                /// pra avisar seria cerimônia.
                                if fita.meu {
                                    Button {
                                        Task { await modelo.devolver(fita.id) }
                                    } label: {
                                        Text(modelo.devolvendo == fita.id
                                            ? "devolvendo…"
                                            : modelo.confirmandoDevolver == fita.id
                                                ? "devolver mesmo?" : "devolver")
                                            .font(.system(size: 12))
                                            /// ⚠️ O vermelho é o mesmo hex que a
                                            /// cinta do prazo vencido usa nesta
                                            /// tela — não há `Cores.perigo` neste
                                            /// cliente, e inventar um segundo
                                            /// vermelho faria duas urgências
                                            /// diferentes na mesma fileira.
                                            .foregroundStyle(modelo.confirmandoDevolver == fita.id
                                                ? Color(hex: 0xD9534F) : Cores.destaque)
                                            .frame(minHeight: 44)
                                            .contentShape(.rect)
                                    }
                                    .buttonStyle(.plain)
                                } else {
                                    Button {
                                        Task { await modelo.pedirDeVolta(fita.id) }
                                    } label: {
                                        /// ⚠️ Sem «já pedida» aqui: o
                                        /// `pedido_por_nome` chega na resposta e
                                        /// este cliente ainda não o mapeia.
                                        /// Escrever o estado sem ter o dado seria
                                        /// afirmar o que não se sabe (§18) — o
                                        /// botão diz o que faz, e a recarga
                                        /// mostra o resultado.
                                        Text("pedir de volta")
                                            .font(.system(size: 12))
                                            .foregroundStyle(Cores.destaque)
                                            .frame(minHeight: 44)
                                            .contentShape(.rect)
                                    }
                                    .buttonStyle(.plain)
                                }
                            }
                        }
                    }
                    .padding(.leading, largura == .regular ? 44 : 34)
                    .padding(.trailing, 20)
                }
            }
        }
    }

    private func ehVhsDaFita(_ f: Emprestada) -> Bool {
        modelo.prateleira?.ehVhs(ano: f.ano) ?? false
    }

    private func medidasDaFita(_ f: Emprestada) -> Medidas {
        ehVhsDaFita(f) ? .vhs : .dvd
    }

    private func corDaFita(_ f: Emprestada) -> Color {
        guard let hex = f.corDominante else { return Cores.fundoElevado }
        let limpo = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard let valor = UInt32(limpo, radix: 16) else { return Cores.fundoElevado }
        return Color(hex: valor)
    }

    /// A entrada da loja: a arandela acesa, o letreiro na luz dela, e as
    /// etiquetas de papel penduradas com as contagens.
    ///
    /// ⚠️ O topo deixou de ser cabeçalho de app e virou **a parede da entrada** —
    /// é o desenho que o dono aprovou. O `CabecalhoDaTela` das outras abas não
    /// entra aqui: nesta tela o nome da loja é o letreiro dela, não um título de
    /// página. O rosto continua no canto, por cima da parede.
    private var cabecalho: some View {
        ZStack(alignment: .topTrailing) {
            VStack(spacing: 0) {
                Arandela()

                Text("locadora")
                    .font(Tipo.letreiro(34))
                    .tracking(1.4)
                    .foregroundStyle(Color(hex: 0xE8CF9A))
                    /// ⚠️ O halo é **da lâmpada**, não da letra: é o facho da
                    /// arandela chegando no texto. Sem ele o letreiro é uma
                    /// palavra dourada; com ele, está aceso.
                    .shadow(color: Cores.destaque.opacity(0.45), radius: 13)
                    .padding(.top, -6)

                /// ⚠️ O subtítulo do mock dizia «aberta até meia-noite» — e saiu:
                /// a loja não fecha de verdade, e horário inventado é mentira com
                /// cara de metadado (§18). «Acervo da casa» é o que ela é.
                Text("ACERVO DA CASA")
                    .font(Tipo.rotulo(10))
                    .tracking(3.0)
                    .foregroundStyle(Cores.destaqueApagado)
                    .padding(.top, 3)

                etiquetas
                    .padding(.top, 20)

                /// ⚠️ **`vira_em` é o que torna a vitrine promessa, e não
                /// sorteio.** Uma seleção que muda sem data anunciada é
                /// aleatoriedade; com data, é programação. Some sem data (§24).
                if let frase = modelo.quandoVira {
                    Text(frase)
                        .font(.system(size: 12))
                        .foregroundStyle(Cores.textoApagado)
                        .padding(.top, 14)
                }
            }
            .frame(maxWidth: .infinity)

            RostoNoCanto(insignia: insignia, aoAbrirPerfil: aoAbrirPerfil, aoSair: aoSair)
                .padding(.trailing, 20)
        }
    }

    /// As duas contagens da porta, em papel por barbante.
    ///
    /// ⚠️ Elas **só nascem com a vitrine na mão**: sem ela, dois papeizinhos
    /// dizendo «0» seriam o app afirmando sobre um acervo que não conseguiu ler.
    /// Erro de rede não é resposta vazia (§18).
    @ViewBuilder
    private var etiquetas: some View {
        if let loja = modelo.loja, !loja.estantes.isEmpty {
            HStack(alignment: .top, spacing: 22) {
                EtiquetaPendurada(
                    numero: "\(loja.estantes.reduce(0) { $0 + $1.caixas.count })",
                    rotulo: "na prateleira",
                    /// ⚠️ Os ângulos são **constantes**, uma por etiqueta. Sorteá-los
                    /// faria o papel tremer a cada redesenho da tela.
                    angulo: -2.5,
                )
                EtiquetaPendurada(
                    numero: loja.noAcervo.comMilhar,
                    rotulo: "no acervo",
                    angulo: 2.0,
                )
            }
        }
    }

    private func prateleira(_ estante: EstanteExposta) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            /// ⚠️ A placa diz «16 de 113», e não «16». O total é **do acervo**,
            /// não do que está à vista — a vitrine é uma amostra que gira, e dizer
            /// só o que se vê faria a loja parecer do tamanho da prateleira.
            HStack(alignment: .bottom, spacing: 10) {
                PlaquinhaDaEstante(nome: estante.nome, cor: papelDaEstante(estante))
                if estante.total > estante.caixas.count {
                    Text("\(estante.caixas.count) de \(estante.total.comMilhar)")
                        .font(.system(size: 11).monospacedDigit())
                        .foregroundStyle(Cores.destaqueApagado)
                        .padding(.bottom, 3)
                }
            }
            .padding(.horizontal, 20)
            .padding(.bottom, 10)
            .zIndex(1)

            ScrollView(.horizontal, showsIndicators: false) {
                HStack(alignment: .bottom, spacing: largura == .regular ? 30 : 22) {
                    ForEach(estante.caixas) { caixa in
                        /// ⚠️ Tocar **pega a caixa**, e não abre a ficha. É o que
                        /// o Android faz: a caixa sai da estante e vai pra mão, e a
                        /// ficha vira uma das coisas que se pode fazer com ela — não
                        /// a única, e não a primeira.
                        Button { withAnimation(.easeOut(duration: 0.22)) { naMao = caixa } } label: {
                            /// ⚠️ **`giravel: false` na estante.** Lá o arrasto é
                            /// da fileira, e disputar o gesto faria a lista não
                            /// rolar. A caixa gira na mão, no palco — não aqui.
                            CaixaEm3D(
                                medidas: medidasDe(caixa).vezes(escala),
                                giravel: false,
                            ) { lado, luz in
                                FaceDaCaixa(
                                    odeon: odeon,
                                    lado: lado, luz: luz,
                                    medidas: medidasDe(caixa).vezes(escala),
                                    ehVhs: ehVhs(caixa),
                                    titulo: caixa.titulo,
                                    cor: cor(caixa),
                                    capa: caixa.poster,
                                )
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                /// A folga à esquerda é a espessura da lombada: sem ela a primeira
                /// caixa fica com o canto cortado na borda da tela.
                .padding(.leading, largura == .regular ? 44 : 34)
                .padding(.trailing, 20)
            }

            /// ⚠️ **Sem espaço entre a caixa e a tábua.** A web diz por quê: «a
            /// tábua encosta na base das caixas; com folga embaixo o conjunto lê
            /// como cartão, não como objeto». O `spacing: 0` desta `VStack` é essa
            /// frase.
            TabuaDaPrateleira()
        }
    }

    /// A cor do papel da plaquinha.
    ///
    /// ⚠️ Ela vem do **nome da estante**, e não de um sorteio: a mesma estante
    /// tem sempre o mesmo papel, em toda abertura do app e em todo aparelho. É
    /// assim que se acha «Terror» de relance — e um papel que muda de cor a cada
    /// visita não é uma etiqueta, é um piscar.
    private func papelDaEstante(_ estante: EstanteExposta) -> Color {
        let papeis: [Color] = [
            Color(hex: 0xF0DE8C), Color(hex: 0xBFD9E8), Color(hex: 0xE8C7B8),
            Color(hex: 0xC9E0BC), Color(hex: 0xE3CDE8), Color(hex: 0xF2ECE0),
        ]
        let soma = estante.nome.unicodeScalars.reduce(0) { $0 &+ Int($1.value) }
        return papeis[soma % papeis.count]
    }

    /// Fita ou disco?
    ///
    /// ⚠️ O corte é o `ultimo_ano_vhs` do **servidor** — o mesmo número que decide
    /// se a caixa rebobina. Tê-lo em dois lugares é como os dois passariam a
    /// discordar, e uma caixa desenhada como VHS recusaria o rebobinar.
    ///
    /// ⚠️ Sem a prateleira carregada, **disco**: na dúvida o app não afirma que
    /// uma obra é de uma era que ele não sabe qual é (§18).
    private func ehVhs(_ caixa: CaixaExposta) -> Bool {
        modelo.prateleira?.ehVhs(ano: caixa.ano) ?? false
    }

    private func medidasDe(_ caixa: CaixaExposta) -> Medidas {
        ehVhs(caixa) ? .vhs : .dvd
    }

    /// Cor **do servidor**, nunca sorteada (§18).
    private func cor(_ caixa: CaixaExposta) -> Color {
        guard let hex = caixa.corDominante else { return Cores.fundoElevado }
        let limpo = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard let valor = UInt32(limpo, radix: 16) else { return Cores.fundoElevado }
        return Color(hex: valor)
    }
}
