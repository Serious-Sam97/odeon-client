import Foundation
import Testing
@testable import Odeon

/// A sonda do acervo — **fala com o servidor de verdade**.
///
/// ## ⚠️ Ela não é um teste, é uma investigação
///
/// Não afirma nada sobre o app: ela pergunta ao acervo do dono o que ele tem, e
/// imprime. Existe porque a pergunta que decide o custo do iOS não pode ser
/// respondida por leitura de código — «o AVFoundation abre `avi`» é uma coisa,
/// «este avi do acervo toca» é outra, e só a segunda vale.
///
/// ⚠️ Ela **depende de sessão aberta no simulador**. Roda dentro do app (o
/// `TEST_HOST`), então lê o mesmo Keychain — se ninguém entrou, ela pula em vez
/// de falhar, porque falta de login não é defeito do código.
///
/// ⚠️ E ela **bate no servidor da casa**: ~40 requisições de leitura. Não rodar em
/// laço, não rodar junto do resto por hábito.
struct SondaDoAcervo {

    private var odeon: RepositorioOdeon { RepositorioOdeon(cofre: Cofre()) }

    private func temSessao() -> Bool {
        let cofre = Cofre()
        return cofre.sessao != nil && cofre.servidor != nil
    }

    /// Amostra o acervo e conta contêiner por contêiner.
    ///
    /// O servidor já mediu isso no banco (matroska 55%, mov/mp4 29,8%, avi 14,7%).
    /// A amostra daqui serve pra outra coisa: **pegar ids de avi de verdade** pra
    /// perguntar o plano deles.
    @Test("amostra: que contêineres aparecem, e ache um avi")
    func amostrar() async throws {
        guard temSessao() else {
            print("⚠️ sem sessão no simulador — entre no app e rode de novo")
            return
        }

        /// ⚠️ **Amostrar só a primeira página engana.** A primeira tentativa leu
        /// 40 obras do começo e achou zero avi — com avi valendo 14,7% do acervo,
        /// zero em 40 é improvável demais pra ser acaso: a ordenação padrão
        /// enviesa. Agora a amostra é espalhada pelo acervo inteiro.
        var pagina: [ItemDaBiblioteca] = []
        for salto in [0, 1500, 3000, 4500, 6000, 7500] {
            let p = try await odeon.biblioteca(pulando: salto, limite: 12)
            pagina += p
        }
        print("── biblioteca: \(pagina.count) entradas amostradas em 6 pontos do acervo ──")

        var porContainer: [String: Int] = [:]
        var avis: [(obra: String, arquivo: String, video: String, audio: String)] = []

        /// ⚠️ A primeira versão usava `try?` aqui e **engolia a falha em
        /// silêncio** — a amostra saiu vazia e não dizia por quê, que é o defeito
        /// que este projeto persegue desde o primeiro dia. Agora conta.
        var avulsas = 0, fichasOk = 0, fichasFalhas = 0, semArquivo = 0
        var ultimoErro = ""

        /// ⚠️ Limitado a 40 de propósito: cada obra é uma requisição ao servidor
        /// de casa, e ele atende três pessoas de verdade.
        for item in pagina.filter({ !$0.eSerie }).prefix(40) {
            avulsas += 1
            let obra: ObraDetalhada
            do {
                obra = try await odeon.obra(item.id)
                fichasOk += 1
            } catch {
                fichasFalhas += 1
                ultimoErro = (error as? FalhaDoOdeon)?.errorDescription ?? "\(error)"
                continue
            }
            if obra.files.isEmpty { semArquivo += 1 }
            for arquivo in obra.files {
                let container = arquivo.container ?? "?"
                porContainer[container, default: 0] += 1
                if container.contains("avi") || container == "avi" {
                    avis.append((
                        obra: obra.id,
                        arquivo: arquivo.id,
                        video: arquivo.codecDeVideo ?? "?",
                        audio: arquivo.codecDeAudio ?? "?",
                    ))
                }
            }
        }

        print("""
        ── a amostra ──
           entradas na página: \(pagina.count)
           avulsas (não série): \(avulsas)
           fichas lidas: \(fichasOk)   fichas que falharam: \(fichasFalhas)
           fichas sem arquivo nenhum: \(semArquivo)
           último erro: \(ultimoErro.isEmpty ? "nenhum" : ultimoErro)
        """)
        print("── contêineres na amostra ──")
        for (c, n) in porContainer.sorted(by: { $0.value > $1.value }) {
            print("   \(c.padding(toLength: 14, withPad: " ", startingAt: 0)) \(n)")
        }

        print("── avi achados: \(avis.count) ──")
        for a in avis.prefix(5) {
            print("   arquivo=\(a.arquivo)  vídeo=\(a.video)  áudio=\(a.audio)")
        }

        /// A pergunta que vale 2.265 arquivos: o que o servidor decide pra um avi
        /// **com a lista de capacidades que este cliente declara agora**.
        for a in avis.prefix(3) {
            guard let plano = try? await odeon.plano(arquivo: a.arquivo) else {
                print("   plano de \(a.arquivo): falhou")
                continue
            }
            print("""
               ── plano de \(a.arquivo)
                  mode=\(plano.mode) video=\(plano.video) audio=\(plano.audio)
                  reasons=\(plano.reasons.joined(separator: " | "))
                  url direta=\(plano.urlDireta != nil ? "sim" : "não")
            """)
        }
    }

    /// O plano dos dois arquivos do 007, que são o controle das outras
    /// investigações — um mkv/ac3 e um mp4/aac.
    ///
    /// O servidor já disse o que espera: `direct_stream` copiando os dois fluxos
    /// no mkv, e `direct_play` no mp4. Isto confere o mesmo pelo caminho do
    /// cliente, com a query que o cliente monta de verdade.
    @Test("o controle: os dois 007, pelo caminho do cliente")
    func oControle() async throws {
        guard temSessao() else {
            print("⚠️ sem sessão no simulador — entre no app e rode de novo")
            return
        }

        let arquivos = [
            ("pt-BR mkv/ac3", "a2274591-541d-4e83-bbe3-6f1b35b6cc6a"),
            ("inglês mp4/aac", "2531ac55-1f33-4252-a1d8-c4e878fbb757"),
        ]
        for (nome, id) in arquivos {
            guard let plano = try? await odeon.plano(arquivo: id) else {
                print("\(nome): não deu pra pedir o plano")
                continue
            }
            print("""
            ── \(nome)
               mode=\(plano.mode)  video=\(plano.video)  audio=\(plano.audio)
               reasons=\(plano.reasons.joined(separator: " | "))
               faixas de áudio=\(plano.faixasDeAudio.count)  legendas=\(plano.subtitles.count)
            """)
        }
    }
}
