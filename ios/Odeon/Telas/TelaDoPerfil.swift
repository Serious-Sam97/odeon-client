import SwiftUI

/// O perfil — quem você é dentro da casa.
///
/// ## ⚠️ Ela é de **leitura**, e isso é escopo, não esquecimento
///
/// A resposta traz os catálogos de rosto, capa, moldura, título e tag — tudo o
/// que o **editor** precisaria. O Android também não tem editor, e o `Modelos.kt`
/// registra por quê: «eles são a lista do que dá pra escolher, e escolher é o que
/// falta». Declarar e desenhar o catálogo sem o gesto de escolher seria oferecer
/// uma vitrine sem porta.
@Observable
@MainActor
final class ModeloDoPerfil {
    var perfil: Perfil?
    var recado: String?

    private let odeon: RepositorioOdeon
    init(odeon: RepositorioOdeon) { self.odeon = odeon }

    /// As conquistas **agrupadas por camada**, na ordem das camadas.
    ///
    /// ⚠️ E as trancadas **ficam**, em vez de sumir: «um rosto que ninguém sabe
    /// que existe não é perseguido». A diferença entre as duas é visual, não a
    /// ausência de uma delas.
    ///
    /// ⚠️ Dentro de cada camada a ordem é a **do servidor**, e não alfabética como
    /// eu tinha feito: a lista chega numa ordem que alguém escolheu, e reordenar
    /// aqui é o cliente discordando de quem sabe.
    var porCamada: [(camada: String, rotulo: String, itens: [ConquistaNaTela])] {
        let ordem = ["facil", "media", "dificil", "impossivel", "nivel", "saga"]
        let rotulos = [
            "facil": "FÁCEIS", "media": "MÉDIAS", "dificil": "DIFÍCEIS",
            "impossivel": "IMPOSSÍVEIS", "nivel": "DE NÍVEL", "saga": "DE SAGA",
        ]
        let todas = perfil?.conquistas ?? []
        let camadas = ordem + Array(Set(todas.map(\.camada)).subtracting(ordem)).sorted()
        return camadas.compactMap { camada in
            let itens = todas.filter { $0.camada == camada }
            guard !itens.isEmpty else { return nil }
            /// ⚠️ Camada que o app não conhece usa a própria chave em caixa alta,
            /// em vez de sumir: uma conquista sem seção seria uma conquista
            /// invisível, e o §18 manda omitir o que não se sabe — não o que se
            /// sabe pela metade.
            return (camada, rotulos[camada] ?? camada.uppercased(), itens)
        }
    }

    func carregar() async {
        do {
            _ = try? await odeon.garantirTokenDeMidia()
            perfil = try await odeon.perfil()
            recado = nil
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "o perfil não abriu"
        }
    }

    func arte(_ enfeite: EnfeiteNaTela?) -> String? { enfeite?.arte }
}

struct TelaDoPerfil: View {
    let odeon: RepositorioOdeon
    let aoFechar: () -> Void

    @State private var modelo: ModeloDoPerfil

    init(odeon: RepositorioOdeon, aoFechar: @escaping () -> Void) {
        self.odeon = odeon
        self.aoFechar = aoFechar
        _modelo = State(wrappedValue: ModeloDoPerfil(odeon: odeon))
    }

    var body: some View {
        ZStack(alignment: .top) {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    /// ⚠️ A capa é **de ponta a ponta e sem cantos**: ela é a
                    /// parede atrás de tudo, não um cartão. A minha versão a
                    /// desenhava numa caixinha arredondada com margem, e ali ela
                    /// virava enfeite; sangrando pra fora ela vira o lugar.
                    capa

                    VStack(alignment: .leading, spacing: 20) {
                        if let recado = modelo.recado {
                            Text(recado).font(.system(size: 14)).foregroundStyle(Cores.textoApagado)
                        }
                        if let p = modelo.perfil {
                            retrato(p)
                            if let progresso = p.progresso { nivel(progresso) }
                            if let bio = p.bio, !bio.isEmpty {
                                Text(bio).font(.system(size: 14))
                                    .foregroundStyle(Cores.texto.opacity(0.85))
                            }
                            placar(p)
                            conquistas
                        }
                    }
                    .padding(.horizontal, 20)
                }
                .frame(maxWidth: 620, alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(.bottom, 30)
            }

            Button("‹ voltar", action: aoFechar)
                .font(.system(size: 16))
                .foregroundStyle(Cores.destaque)
                .padding(.horizontal, 20)
                .frame(height: 44)
                .contentShape(.rect)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.top, 6)
        }
        /// ⚠️ Sem isto a folha abre **vazia** — só o «voltar» sobre o preto. Foi o
        /// que a captura mostrou: reescrevendo o corpo desta tela eu levei junto o
        /// `.task` que a carrega, e nada no código reclamou. Compilou, passou nos
        /// 56 testes, e não tinha conteúdo.
        .task { if modelo.perfil == nil { await modelo.carregar() } }
    }

    @ViewBuilder
    private var capa: some View {
        if let arte = modelo.arte(modelo.perfil?.capa) {
            /// ⚠️ Em `background`, e não num `ZStack` medido: `scaledToFill`
            /// reivindica a largura do arquivo e estica a coluna inteira. Ver o
            /// comentário grande na `fachada` da `TelaDaObra`.
            Color.clear
                .frame(height: 190)
                .frame(maxWidth: .infinity)
                .background {
                    ZStack(alignment: .bottom) {
                        Cores.fundoElevado
                        ArteDoOdeon(odeon: odeon, caminho: arte)
                        /// O véu costura a foto ao fundo: sem ele há uma aresta
                        /// reta atravessando a tela, e a capa lê como imagem
                        /// colada por cima.
                        LinearGradient(colors: [.clear, Cores.fundo],
                                       startPoint: .center, endPoint: .bottom)
                    }
                }
                .clipped()
        }
    }

    /// O rosto grande, com o mesmo anel e o mesmo número do canto de toda tela.
    ///
    /// ⚠️ **A mesma insígnia, em outro tamanho** — e não um segundo desenho de
    /// avatar. Dois desenhos da mesma coisa divergem no dia em que alguém mexer
    /// num deles.
    private func retrato(_ p: Perfil) -> some View {
        HStack(spacing: 16) {
            ZStack {
                Circle().fill(Cores.fundoElevado)
                ArteDoOdeon(odeon: odeon, caminho: modelo.arte(p.avatar))
            }
            .frame(width: 92, height: 92)
            .clipShape(.circle)
            .overlay {
                if let fracao = p.progresso?.fracaoDoNivel {
                    ZStack {
                        Circle().stroke(Cores.textoApagado.opacity(0.25), lineWidth: 5)
                        Circle().trim(from: 0, to: fracao)
                            .stroke(corDaMoldura(p) ?? Cores.destaque,
                                    style: .init(lineWidth: 5, lineCap: .round))
                            .rotationEffect(.degrees(-90))
                    }
                    .padding(-7)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if let nivel = p.progresso?.nivel {
                    Text("\(nivel)")
                        .font(.system(size: 19, weight: .bold).monospacedDigit())
                        .foregroundStyle(Cores.fundo)
                        .frame(width: 36, height: 36)
                        .background(Cores.destaque, in: .circle)
                        .offset(x: 6, y: 4)
                }
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(p.displayName.isEmpty ? p.username : p.displayName)
                    .font(Tipo.letreiro(32))
                    .foregroundStyle(Cores.texto)
                /// ⚠️ **`@usuário`**, e não o título conquistado que eu tinha
                /// posto aqui: o Android mostra a identidade — quem é —, e o
                /// título é enfeite que vai noutro lugar.
                Text("@\(p.username)")
                    .font(.system(size: 15))
                    .foregroundStyle(Cores.textoApagado)
            }
            Spacer(minLength: 0)
        }
        .padding(.top, 6)
    }

    /// ⚠️ **Uma barra e uma linha**, e não o cartão com «nv 3» gigante que eu
    /// tinha desenhado. O nível já está no anel e na medalha do rosto logo acima;
    /// repeti-lo em corpo 30 era dizer a mesma coisa três vezes na mesma tela.
    private func nivel(_ progresso: ProgressoDoUsuario) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            if let fracao = progresso.fracaoDoNivel {
                GeometryReader { g in
                    ZStack(alignment: .leading) {
                        Capsule().fill(Cores.textoApagado.opacity(0.22))
                        Capsule().fill(Cores.destaque).frame(width: g.size.width * fracao)
                    }
                }
                .frame(height: 5)
            }
            /// A linha omite item por item (§24): sem faixa de nível, o «faltam»
            /// some e o resto continua.
            Text([
                "\(progresso.xp.comMilhar) XP",
                progresso.fracaoDoNivel == nil ? nil
                    : "faltam \(progresso.faltamProProximo.comMilhar) pro nível \(progresso.nivel + 1)",
                "\(progresso.desbloqueadas) de \(progresso.total) conquistas",
            ].compactMap { $0 }.joined(separator: " · "))
                .font(.system(size: 14))
                .foregroundStyle(Cores.textoApagado)
        }
    }

    /// «VOCÊ E SEUS AMIGOS» — o placar.
    ///
    /// ## ⚠️ Ele é o que faz o XP significar alguma coisa
    ///
    /// A doc da web diz que a comparação com os amigos «foi pedida e nunca
    /// existiu» até esta tela. Um número de XP sozinho é um número; ao lado de
    /// duas outras pessoas da mesma casa, é um placar — e é isso que faz alguém
    /// perseguir um rosto que ninguém sabe que existe.
    @ViewBuilder
    private func placar(_ p: Perfil) -> some View {
        if !p.amigos.isEmpty {
            VStack(alignment: .leading, spacing: 8) {
                RotuloDeSecao(texto: "VOCÊ E SEUS AMIGOS", contagem: "\(p.amigos.count)")
                ForEach(Array(p.amigos.enumerated()), id: \.element.id) { posicao, amigo in
                    HStack(spacing: 12) {
                        Text("\(posicao + 1)")
                            .font(.system(size: 15).monospacedDigit())
                            .foregroundStyle(amigo.eu ? Cores.destaque : Cores.textoApagado)
                            .frame(width: 18, alignment: .trailing)
                        Text(amigo.displayName)
                            .font(.system(size: 16, weight: amigo.eu ? .semibold : .regular))
                            .foregroundStyle(Cores.texto)
                        Spacer(minLength: 0)
                        Text("nível \(amigo.nivel) · \(amigo.xp.comMilhar) XP")
                            .font(.system(size: 14).monospacedDigit())
                            .foregroundStyle(Cores.textoApagado)
                    }
                    .padding(.horizontal, 14).padding(.vertical, 12)
                    /// ⚠️ **A sua linha acende**, e é a única diferença: um placar
                    /// em que você não se acha de relance é uma tabela.
                    .background {
                        if amigo.eu {
                            RoundedRectangle(cornerRadius: 10).fill(Cores.fundoElevado)
                        }
                    }
                }
            }
        }
    }

    private var conquistas: some View {
        VStack(alignment: .leading, spacing: 22) {
            ForEach(modelo.porCamada, id: \.camada) { grupo in
                VStack(alignment: .leading, spacing: 10) {
                    RotuloDeSecao(texto: grupo.rotulo)
                    /// ⚠️ «9 de 12» **por camada**, e não o total: é a contagem
                    /// que responde «quanto falta desta prateleira», que é a
                    /// pergunta que a seção faz.
                    Text("\(grupo.itens.count { $0.aberta }) de \(grupo.itens.count)")
                        .font(.system(size: 14).monospacedDigit())
                        .foregroundStyle(Cores.textoApagado)

                    ForEach(grupo.itens) { c in linhaDaConquista(c) }
                }
            }
        }
    }

    private func linhaDaConquista(_ c: ConquistaNaTela) -> some View {
        HStack(alignment: .top, spacing: 10) {
            /// ⚠️ A marca ocupa lugar **sempre**, aberta ou não. Se ela só
            /// existisse na conquistada, as outras andariam pra esquerda e o olho
            /// leria isso como se fossem de outro tipo.
            Text(c.aberta ? "✓" : "☐")
                .font(.system(size: 15))
                .foregroundStyle(c.aberta ? Cores.destaque : Cores.textoApagado.opacity(0.5))
                .frame(width: 18, alignment: .leading)

            VStack(alignment: .leading, spacing: 2) {
                Text(c.nome)
                    .font(.system(size: 16))
                    .foregroundStyle(c.aberta ? Cores.texto : Cores.textoApagado)
                Text(c.descricao)
                    .font(.system(size: 13))
                    .foregroundStyle(Cores.textoApagado.opacity(c.aberta ? 1 : 0.7))
            }
            Spacer(minLength: 0)
            if c.pontos > 0 {
                /// ⚠️ «+10 **XP**», com a unidade: é o que o Android escreve, e
                /// sem ela o número flutua sem dizer de quê.
                Text("+\(c.pontos) XP")
                    .font(.system(size: 13).monospacedDigit())
                    .foregroundStyle(c.aberta ? Cores.destaque : Cores.textoApagado.opacity(0.5))
            }
        }
        .padding(.vertical, 5)
    }

    private func corDaMoldura(_ p: Perfil) -> Color? {
        guard let hex = p.moldura else { return nil }
        let limpo = hex.hasPrefix("#") ? String(hex.dropFirst()) : hex
        guard let valor = UInt32(limpo, radix: 16) else { return nil }
        return Color(hex: valor)
    }
}
