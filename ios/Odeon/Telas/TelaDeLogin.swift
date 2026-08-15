import SwiftUI
import UIKit

/// A tela de entrada.
///
/// ## ⚠️ O endereço do servidor fica **junto** do login, e é decisão herdada
///
/// O `Campo.kt` do `:tv` registra o porquê: «numa TV "não conecta" é quase sempre
/// o IP errado». Vale igual no celular de casa — separar o endereço numa tela de
/// ajustes faz a pessoa procurar onde arrumar o que quebrou.
///
/// E ela **não** pergunta «http ou https?». Quem sabe qual está ligado é o
/// servidor: o `EnderecoDoServidor` monta os candidatos e o
/// `descobrirServidor` fica com o que responder.
///
/// ⚠️ Nada aqui grava senha. O token de sessão vai pro Keychain e a senha morre
/// com a chamada — ver `Cofre`.
@Observable
@MainActor
final class ModeloDeLogin {
    var endereco: String = ""
    var usuario: String = ""
    var senha: String = ""

    var entrando = false
    var recado: String?
    /// O que o servidor respondeu quando o endereço foi conferido. `nil` enquanto
    /// ninguém conferiu — e a tela **não escreve nada** nesse caso (§24).
    var achouServidor: String?

    private let odeon: RepositorioOdeon

    init(odeon: RepositorioOdeon) {
        self.odeon = odeon
        endereco = odeon.cofre.servidor ?? ""
    }

    var podeEntrar: Bool {
        !entrando && !endereco.isEmpty && !usuario.isEmpty && !senha.isEmpty
    }

    /// Confere o endereço **antes** de mandar senha pra ele.
    ///
    /// É de graça (a rota responde sem sessão) e responde a pergunta que a pessoa
    /// tem quando não funciona: «o servidor está no ar?». Separá-la do login é o
    /// que transforma «não conecta» em duas perguntas distintas.
    func conferirEndereco() async {
        recado = nil
        achouServidor = nil
        do {
            achouServidor = try await odeon.descobrirServidor(digitado: endereco)
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "não deu pra falar com esse endereço"
        }
    }

    func entrar() async -> Usuario? {
        guard podeEntrar else { return nil }
        entrando = true
        recado = nil
        defer { entrando = false }

        do {
            if achouServidor == nil {
                achouServidor = try await odeon.descobrirServidor(digitado: endereco)
            }
            /// O rótulo do aparelho aparece na tela de sessões do admin. Sessão
            /// sem rótulo aparece anônima, e — pelo que o projeto observou —
            /// costuma ser sinal de linha inserida à mão, não de login de verdade.
            let usuarioLogado = try await odeon.entrar(
                usuario: usuario,
                senha: senha,
                rotulo: UIDevice.current.name,
            )
            senha = ""
            return usuarioLogado
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "não deu pra entrar"
            return nil
        }
    }
}

struct TelaDeLogin: View {
    @State private var modelo: ModeloDeLogin
    let aoEntrar: (Usuario) -> Void

    init(odeon: RepositorioOdeon, aoEntrar: @escaping (Usuario) -> Void) {
        _modelo = State(wrappedValue: ModeloDeLogin(odeon: odeon))
        self.aoEntrar = aoEntrar
    }

    var body: some View {
        ZStack {
            Cores.fundo.ignoresSafeArea()

            ScrollView {
                VStack(alignment: .leading, spacing: 22) {
                    VStack(alignment: .leading, spacing: 8) {
                        Text("ODEON")
                            .font(Tipo.rotulo())
                            .tracking(Tipo.espacoDoRotulo)
                            .foregroundStyle(Cores.destaque)
                        Text("Entrar na sala")
                            .font(Tipo.letreiro(30))
                            .foregroundStyle(Cores.texto)
                    }
                    .padding(.bottom, 6)

                    Campo(rotulo: "SERVIDOR", texto: $modelo.endereco)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()
                        .keyboardType(.URL)
                        .onSubmit { Task { await modelo.conferirEndereco() } }

                    /// ⚠️ Só aparece quando há o que dizer. Um lugar reservado
                    /// pra «status do servidor» vazio é linha que não some (§24).
                    if let achou = modelo.achouServidor {
                        Text("respondeu: \(achou)")
                            .font(.system(size: 13))
                            .foregroundStyle(Cores.destaque)
                    }

                    Campo(rotulo: "USUÁRIO", texto: $modelo.usuario)
                        .textInputAutocapitalization(.never)
                        .autocorrectionDisabled()

                    Campo(rotulo: "SENHA", texto: $modelo.senha, secreto: true)

                    Button {
                        Task { if let u = await modelo.entrar() { aoEntrar(u) } }
                    } label: {
                        HStack {
                            if modelo.entrando { ProgressView().tint(Cores.fundo) }
                            Text(modelo.entrando ? "entrando…" : "entrar")
                                .font(.system(size: 16, weight: .semibold))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 14)
                        .background(Cores.destaque, in: .rect(cornerRadius: 12))
                        .foregroundStyle(Cores.fundo)
                    }
                    .disabled(!modelo.podeEntrar)
                    .opacity(modelo.podeEntrar ? 1 : 0.45)

                    if let recado = modelo.recado {
                        Text(recado)
                            .font(.system(size: 14))
                            .foregroundStyle(Cores.texto)
                            .padding(12)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .background(Cores.fundoElevado, in: .rect(cornerRadius: 10))
                    }
                }
                /// ⚠️ O limite de largura é pro iPad: um campo de texto de 13
                /// polegadas de largura é ilegível. **Não** é solução de layout
                /// pro iPad inteiro — a F7 é que resolve isso, e o esqueleto já
                /// mostrou o sintoma.
                .frame(maxWidth: 460, alignment: .leading)
                .frame(maxWidth: .infinity)
                .padding(28)
            }
        }
        /// ⚠️ Com endereço já guardado, ele é conferido **ao abrir**.
        ///
        /// Não é zelo: é a resposta pra «não conecta», que é o defeito nº 1 de um
        /// app que fala com um servidor de casa. Quem abre o app e vê «respondeu:
        /// http://…» já sabe que o problema é a senha; quem não vê já sabe que é
        /// a rede. Custa uma requisição que não precisa de sessão, e ela responde
        /// a pergunta antes de a pessoa perguntar.
        .task {
            if !modelo.endereco.isEmpty { await modelo.conferirEndereco() }
        }
    }
}

/// Um campo com rótulo em versalete espaçado — a régua tipográfica da casa.
private struct Campo: View {
    let rotulo: String
    @Binding var texto: String
    var secreto: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 7) {
            Text(rotulo)
                .font(Tipo.rotulo(11))
                .tracking(Tipo.espacoDoRotulo)
                .foregroundStyle(Cores.textoApagado)

            Group {
                if secreto {
                    SecureField("", text: $texto)
                } else {
                    TextField("", text: $texto)
                }
            }
            .font(.system(size: 17))
            .foregroundStyle(Cores.texto)
            .padding(.horizontal, 14)
            .padding(.vertical, 13)
            .background(Cores.fundoElevado, in: .rect(cornerRadius: 10))
        }
    }
}
