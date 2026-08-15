import SwiftUI

/// O cabeçalho de uma aba: o nome da tela, e você no canto.
///
/// ## ⚠️ Ele é a correção de um erro de leitura, não uma preferência
///
/// As telas deste app nasceram com `RotuloDeSecao` no topo — versalete espaçado
/// com filete até a margem: `BIBLIOTECA ──────── 60 de 8.273`. Aberto o
/// emulador do Android em 15/08/2026, o que roda lá é outra coisa:
///
/// ```
/// biblioteca  60 de 8.273                    (avatar com anel de nível)
/// ```
///
/// **Serifa, minúscula, grande** — o mesmo letreiro que a locadora usa no nome
/// dela. O `RotuloDeSecao` continua existindo e continua certo, mas para o que
/// ele é: separar **seções dentro** de uma tela (`CONTINUAR`, `ESTA SEMANA`),
/// não nomear a tela.
///
/// ⚠️ Era o §1.2 aplicado ao contrário: o rótulo de seção dá ritmo *dentro* da
/// página; usá-lo como título faz toda tela parecer a primeira seção de uma
/// página maior que não existe.
///
/// ## ⚠️ E o avatar é a porta do perfil
///
/// No Android não há ícone de casa. O perfil entra pelo **rosto no canto**, com
/// o anel de nível em volta e o número dentro — e o toque abre um menu curto
/// (`perfil`, `sair`). Eu havia inventado uma «casa» que agrupava mural, guia,
/// perfil e baixados; nada disso existe lá, e os três primeiros são abas.
struct CabecalhoDaTela: View {
    let titulo: String
    /// «60 de 8.273». `nil` some — e some inteiro (§24).
    var contagem: (feito: String, total: String)?
    let insignia: Insignia
    let aoAbrirPerfil: () -> Void
    let aoSair: () -> Void

    var body: some View {
        HStack(alignment: .firstTextBaseline, spacing: 10) {
            Text(titulo)
                .font(Tipo.letreiro(34))
                .foregroundStyle(Cores.texto)

            if let contagem {
                /// ⚠️ **Dois pesos na mesma linha**, como no Android: o que já
                /// carregou em destaque e o total em destaque, com o «de» apagado
                /// no meio. Não é enfeite — é o que faz «60 de 8.273» ser lido
                /// como uma fração e não como duas coisas soltas.
                (
                    Text(contagem.feito).foregroundStyle(Cores.destaque)
                        + Text(" de ").foregroundStyle(Cores.textoApagado)
                        + Text(contagem.total).foregroundStyle(Cores.destaque)
                )
                .font(.system(size: 17).monospacedDigit())
            }

            Spacer(minLength: 0)
            RostoNoCanto(insignia: insignia, aoAbrirPerfil: aoAbrirPerfil, aoSair: aoSair)
        }
        .padding(.horizontal, 20)
        .padding(.top, 8)
    }
}

/// Só o rosto, sem título.
///
/// ⚠️ Ele existe separado porque a **locadora não tem cabeçalho de app**: lá o
/// nome da loja é um letreiro aceso sob uma arandela, não um título de página. O
/// rosto continua no canto — é a mesma porta em toda tela —, mas a parede atrás
/// dele é outra.
struct RostoNoCanto: View {
    let insignia: Insignia
    let aoAbrirPerfil: () -> Void
    let aoSair: () -> Void

    @State private var menuAberto = false

    var body: some View { rosto }

    private var rosto: some View {
        Button { menuAberto = true } label: {
            ZStack {
                Circle().fill(Cores.fundoElevado)
                ArteDoOdeon(odeon: insignia.odeon, caminho: insignia.caminhoDoRosto)
            }
            .frame(width: 44, height: 44)
            .clipShape(.circle)
            /// ⚠️ O anel **é o progresso**, não uma borda: ele fecha na proporção
            /// do nível. Um anel cheio por falta de dado seria decoração com cara
            /// de conquista (§18), então sem fração ele não desenha — sobra só o
            /// rosto.
            .overlay {
                if let fracao = insignia.fracao {
                    Circle()
                        .trim(from: 0, to: fracao)
                        .stroke(Cores.destaque, style: .init(lineWidth: 2.5, lineCap: .round))
                        .rotationEffect(.degrees(-90))
                        .padding(-3)
                }
            }
            .overlay(alignment: .bottomTrailing) {
                if let nivel = insignia.nivel {
                    Text("\(nivel)")
                        .font(.system(size: 11, weight: .bold).monospacedDigit())
                        .foregroundStyle(Cores.fundo)
                        .frame(width: 19, height: 19)
                        .background(Cores.destaque, in: .circle)
                        .offset(x: 3, y: 3)
                }
            }
            /// O alvo de toque é maior que o desenho — a mesma lição do
            /// `CabecalhoDaCasa`: 44pt de rosto mais o anel ainda é pouco pro
            /// polegar chegar sem mirar.
            .frame(width: 54, height: 54)
            .contentShape(.rect)
        }
        .buttonStyle(.plain)
        .popover(isPresented: $menuAberto, attachmentAnchor: .point(.bottom)) {
            menu.presentationCompactAdaptation(.popover)
        }
    }

    private var menu: some View {
        VStack(alignment: .leading, spacing: 0) {
            VStack(alignment: .leading, spacing: 5) {
                Text(insignia.perfil?.displayName ?? "…")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Cores.texto)
                if let p = insignia.perfil?.progresso {
                    Text("nível \(p.nivel) · \(p.desbloqueadas) de \(p.total) conquistas")
                        .font(.system(size: 13))
                        .foregroundStyle(Cores.textoApagado)
                    if let fracao = p.fracaoDoNivel {
                        GeometryReader { g in
                            ZStack(alignment: .leading) {
                                Capsule().fill(Cores.textoApagado.opacity(0.25))
                                Capsule().fill(Cores.destaque).frame(width: g.size.width * fracao)
                            }
                        }
                        .frame(height: 3)
                    }
                }
            }
            .padding(16)

            Divider().overlay(Cores.textoApagado.opacity(0.25))

            Button { menuAberto = false; aoAbrirPerfil() } label: {
                HStack {
                    Text("perfil").foregroundStyle(Cores.texto)
                    Spacer()
                    Text("›").foregroundStyle(Cores.destaque)
                }
                .font(.system(size: 16))
                .padding(.horizontal, 16).padding(.vertical, 13)
                .contentShape(.rect)
            }
            .buttonStyle(.plain)

            Button { menuAberto = false; aoSair() } label: {
                Text("sair")
                    .font(.system(size: 16))
                    .foregroundStyle(Cores.textoApagado)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(.horizontal, 16).padding(.vertical, 13)
                    .contentShape(.rect)
            }
            .buttonStyle(.plain)
        }
        .frame(width: 280)
        .background(Cores.fundoElevado)
    }
}
