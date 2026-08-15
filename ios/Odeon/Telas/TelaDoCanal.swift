import AVKit
import SwiftUI

/// Um canal sintonizado.
///
/// ## ⚠️ Ela é parecida com o player e **não é o player**
///
/// A `TelaDoPlayer` sabe tudo sobre uma obra: duração, faixas de áudio, legendas,
/// onde parou, onde vai parar. Um canal não tem nada disso — não há arquivo, não
/// há ficha, não há progresso pra marcar. Reaproveitar aquela tela obrigaria
/// metade dela a existir desligada, e cada campo desligado é um lugar onde algum
/// dia alguém escreve `if canal == nil`.
///
/// ## ⚠️ O que **não** existe aqui, e por quê
///
/// | | |
/// |---|---|
/// | tira de filme | não há cenas: o servidor extrai fotogramas de um **arquivo**, e canal não é arquivo |
/// | «faltam» | o fim é o fim do **programa**, não do fluxo — e o programa muda embaixo de você |
/// | ⟲10 · ⟳30 | ao vivo, «voltar dez segundos» é uma promessa que a janela do HLS não garante |
/// | marcar progresso | não há obra onde marcar |
///
/// O que sobra é o que uma televisão tem: a imagem, o nome do canal, o que está
/// passando, e o botão de desligar.
@Observable
@MainActor
final class ModeloDoCanal {
    var player: AVPlayer?
    var recado: String? = "sintonizando…"
    /// A sessão aberta no servidor. ⚠️ Ela **tem** de ser encerrada ao sair: um
    /// canal é ffmpeg rodando, e sair sem avisar deixa o servidor de casa
    /// transcodificando pra ninguém.
    var sessaoAberta: String?

    private let odeon: RepositorioOdeon
    let quadro: QuadroNoAr

    init(odeon: RepositorioOdeon, quadro: QuadroNoAr) {
        self.odeon = odeon
        self.quadro = quadro
    }

    func sintonizar() async {
        do {
            let canal = try await odeon.sintonizar(canal: quadro.canalId)
            sessaoAberta = canal.sessaoId
            guard let url = try await odeon.urlDeMidia(canal.urlDaPlaylist) else {
                recado = "não deu pra montar o endereço do canal"
                return
            }
            let player = AVPlayer(url: url)
            self.player = player
            player.play()
            recado = nil
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "este canal não abriu"
        }
    }

    func desligar() async {
        player?.pause()
        player = nil
        if let sessaoAberta {
            await odeon.encerrarSessao(sessaoAberta)
            self.sessaoAberta = nil
        }
    }
}

struct TelaDoCanal: View {
    @State private var modelo: ModeloDoCanal
    let aoVoltar: () -> Void

    @State private var mostrarControles = true

    init(odeon: RepositorioOdeon, quadro: QuadroNoAr, aoVoltar: @escaping () -> Void) {
        _modelo = State(wrappedValue: ModeloDoCanal(odeon: odeon, quadro: quadro))
        self.aoVoltar = aoVoltar
    }

    var body: some View {
        ZStack {
            /// ⚠️ O toque mora no fundo preto, e não na camada de vídeo nem no
            /// `ZStack` — as duas já falharam no player, e o comentário lá conta
            /// por quê. O fundo preenche a tela por definição.
            Color.black.ignoresSafeArea()
                .contentShape(.rect)
                .onTapGesture {
                    withAnimation(.easeOut(duration: 0.18)) { mostrarControles.toggle() }
                }

            if let player = modelo.player {
                CamadaDeVideo(player: player).ignoresSafeArea()
            }

            if let recado = modelo.recado {
                VStack(alignment: .leading, spacing: 10) {
                    Text("AO VIVO")
                        .font(Tipo.rotulo())
                        .tracking(Tipo.espacoDoRotulo)
                        .foregroundStyle(.red)
                    Text(recado)
                        .font(Tipo.letreiro(26))
                        .foregroundStyle(Cores.texto)
                }
                .padding(28)
            }

            if modelo.player != nil {
                controles.opacity(mostrarControles ? 1 : 0)
                    .allowsHitTesting(mostrarControles)
            }
        }
        .task { await modelo.sintonizar() }
    }

    private var controles: some View {
        VStack {
            HStack(spacing: 12) {
                Button { Task { await modelo.desligar(); aoVoltar() } } label: {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .contentShape(.rect)
                }
                /// ⚠️ O ponto é **vermelho**, e não o dourado do plano: aqui ele
                /// não diz como o servidor está entregando, diz que **é ao vivo**.
                /// É a convenção que toda televisão usa, e usar o dourado da casa
                /// para isso seria trocar um significado conhecido por um interno.
                Circle().fill(.red).frame(width: 9, height: 9)
                    .shadow(color: .red.opacity(0.8), radius: 5)

                VStack(alignment: .leading, spacing: 1) {
                    Text(modelo.quadro.canalNome)
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                    /// O que está passando. ⚠️ Some quando o canal não tem EPG —
                    /// «sem programação» já foi dito na lista, e repetir aqui, por
                    /// cima do filme, seria insistir no que não se sabe.
                    if modelo.quadro.titulo != "sem programação" {
                        Text(modelo.quadro.titulo)
                            .font(.system(size: 13))
                            .foregroundStyle(.white.opacity(0.75))
                            .lineLimit(1)
                    }
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 8)
            .padding(.top, 4)

            Spacer()
        }
        .background {
            VStack {
                LinearGradient(colors: [.black.opacity(0.8), .clear],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: 150)
                Spacer()
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }
        .task(id: mostrarControles) {
            guard mostrarControles else { return }
            try? await Task.sleep(for: .seconds(4))
            withAnimation { mostrarControles = false }
        }
    }
}
