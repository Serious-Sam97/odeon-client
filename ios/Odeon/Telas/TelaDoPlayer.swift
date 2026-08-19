import AVFoundation
import AVKit
import SwiftUI

/// O player.
///
/// ## As três formas de receber um filme, e o que cada uma custa
///
/// | | o que é | custo no servidor |
/// |---|---|---|
/// | `direct_play` | o arquivo original | **zero** |
/// | `direct_stream` | remux, `-c copy` | ffmpeg em cópia + sessão |
/// | `transcode` | re-encode | CPU de verdade |
///
/// ⚠️ **No iOS o `direct_stream` é o caso comum** — 53,3% do acervo, contra 29,4%
/// de direto. É a diferença de o AVPlayer não abrir Matroska, e é o que faz este
/// player ter que encerrar sessão direito (ver `aoSair`).
@Observable
@MainActor
final class ModeloDoPlayer {
    /// O que a tela mostra enquanto não há vídeo. `nil` quando já há.
    var recado: String? = "abrindo…"
    var player: AVPlayer?

    /// As faixas de áudio que o **arquivo** tem, e qual está tocando.
    ///
    /// ## ⚠️ Elas vêm do plano, **não** do player
    ///
    /// Perguntar ao player sempre responde «uma»: em transcodificação o ffmpeg do
    /// servidor põe uma faixa só na playlist, e o dual audio sumia exatamente nos
    /// arquivos que o têm. O servidor mediu **3.469 arquivos** do acervo com duas
    /// ou mais faixas, e o par recorrente é `ac3:por | aac:eng`.
    var faixas: [FaixaDeAudio] = []
    var faixaEmUso: Int?

    /// As legendas que o **arquivo** tem, e qual está escolhida.
    ///
    /// ⚠️ `nil` é «sem legenda», e é o padrão. Ligar legenda sozinho seria decidir
    /// pela pessoa uma coisa que ela percebe na hora — e desligar custaria mais
    /// toques que ligar.
    var legendas: [FaixaDeLegenda] = []
    var legendaEscolhida: Int?
    /// A fala que vale agora. É o que a tela desenha.
    var falaAgora: FalaDaLegenda?

    /// Onde o filme está, em segundos. Atualizado **quatro vezes por segundo** —
    /// é o que a tira de filme e o relógio pedem.
    ///
    /// ⚠️ Repare que ela é diferente do relógio de **progresso**, que sobe ao
    /// servidor a cada 20 s. Um é desenho, o outro é rede: juntá-los faria o app
    /// falar com a casa quatro vezes por segundo.
    var posicao: Double = 0
    var tocando = true
    /// Os fotogramas da tira. Vazia enquanto não chegam — e aí a tira **não
    /// desenha**, em vez de virar uma faixa preta esperando (§24).
    var cenas: [Cena] = []
    /// O que a barra de cima escreve. Vem da ficha por fora — o player não abre
    /// a obra de novo só pra saber o nome dela.
    var titulo: String = ""
    /// `direct_play` · `direct_stream` · `transcode`, pro ponto da barra de cima.
    var modoDoPlano: String?

    /// Quanto o filme dura, **segundo a ficha**.
    ///
    /// ## ⚠️ E não segundo o player, que não sabe
    ///
    /// Em HLS o item chega com `duration` indefinida — a playlist sai como
    /// `EVENT` sem `#EXT-X-ENDLIST`, e sem isso ninguém sabe onde o conteúdo
    /// acaba. Foi o que pôs **«4:44 AM»** na barra do AVKit.
    ///
    /// A ficha sabe: o `duration_seconds` do arquivo veio do ffprobe. Perguntar
    /// pra quem sabe é a diferença entre uma barra e um cronômetro de transmissão.
    var duracaoDaSessao: Double? {
        if let duracao, duracao > 0 { return duracao }
        /// ⚠️ **Antes do item**: a medida do servidor é do arquivo inteiro; a do
        /// item, numa sessão em geração, é só o que já foi gerado.
        if let doServidor = duracaoDoServidor, doServidor > 0 { return doServidor }
        let doItem = player?.currentItem?.duration.seconds
        return (doItem?.isFinite == true && doItem! > 0) ? doItem : nil
    }

    var quantoFalta: Double? { duracaoDaSessao.map { max(0, $0 - posicao) } }

    private var falas: [FalaDaLegenda] = []
    private var relogioDaLegenda: Any?

    /// A sessão de HLS aberta, se houver. É ela que precisa ser encerrada.
    private var sessaoAberta: String?
    private var observador: Any?
    private var vigia: Task<Void, Never>?

    let odeon: RepositorioOdeon
    private let obra: String
    private let arquivo: String
    private let duracao: Double?

    /// A duração que o **servidor mediu**, quando quem abriu não sabia.
    ///
    /// ## ⚠️ Ela existe porque há caminhos que abrem o player sem duração
    ///
    /// O canal do ao vivo passa `nil`, e sem ela a `duracaoDaSessao` cai no
    /// `currentItem.duration` — que numa playlist `EVENT` é indefinida, e foi o
    /// que pôs **«4:44 AM»** na barra do AVKit.
    ///
    /// O servidor passou a mandar `duration_seconds` no plano e na sessão
    /// (17/08/2026), como resposta ao pedido da playlist `VOD`: declarar `VOD`
    /// exigia varrer os keyframes da fonte, 1m33s por arquivo, contra 25s de
    /// espera da playlist. O número resolve o mesmo pelo lado barato.
    private var duracaoDoServidor: Double?
    /// De onde começar, **já passado pela régua** do `ondeContinuar`.
    private let comecarEm: Double

    /// O arquivo no disco, quando este filme foi guardado.
    ///
    /// ## ⚠️ Com ele, **nada é pedido ao servidor** — e é o ponto dos baixados
    ///
    /// Sem esta ramificação o player pediria o plano antes de tocar, e o plano
    /// precisa de rede. Um filme de 2 GB guardado no aparelho que só abre com
    /// Wi-Fi não é um filme guardado; é um cache com etapa extra.
    private let local: URL?

    /// ## ⚠️ Veio do **ao vivo**? Então nada disto conta.
    ///
    /// O pedido é do dono, e a história que o motivou é a prova:
    ///
    /// > «eu mesmo acabei dormindo no ao vivo e quando vi o app registrou que eu
    /// > vi um monte de filme»
    ///
    /// Um canal **corre sozinho**. Ninguém escolheu aqueles filmes e ninguém
    /// decidiu parar no minuto 47 — a grade seguiu e a pessoa estava dormindo. O
    /// progresso gravado ali não é memória do que se assistiu; é memória do que
    /// passou na frente de uma tela ligada.
    ///
    /// E o estrago passa de «continuar»: o mesmo registro alimenta o «para
    /// você». Uma noite de sono no canal de terror ensina ao algoritmo um gosto
    /// que ninguém tem.
    ///
    /// ⚠️ **A regra é por onde se entrou, não pelo que se tocou.** O mesmo filme,
    /// aberto pela biblioteca ou pela locadora, conta normalmente — lá houve
    /// escolha. O `Alvo.doAoVivo` já viajava pra saber pra onde **voltar**; agora
    /// diz também o que **não** registrar.
    private let doAoVivo: Bool

    init(
        odeon: RepositorioOdeon, obra: String, arquivo: String,
        duracao: Double?, titulo: String = "", comecarEm: Double, local: URL? = nil,
        doAoVivo: Bool = false,
    ) {
        self.titulo = titulo
        self.odeon = odeon
        self.obra = obra
        self.arquivo = arquivo
        self.duracao = duracao
        self.comecarEm = comecarEm
        self.local = local
        self.doAoVivo = doAoVivo
    }

    /// O progresso sobe? Só fora do ao vivo — ver `doAoVivo`.
    ///
    /// ⚠️ Ela existe como propriedade em vez de um `if` repetido nos dois pontos
    /// que marcam (o relógio de 20s e a saída): dois `if` iguais em lugares
    /// distantes é a receita pra um deles ser esquecido no dia em que aparecer
    /// um terceiro ponto de marcação.
    var registraProgresso: Bool { !doAoVivo }

    /// Já tentamos renovar o token de mídia **nesta tentativa de abertura**?
    ///
    /// ## ⚠️ Metade do motivo desta trava acabou — 17/08/2026
    ///
    /// Ela tinha duas justificativas, e o servidor derrubou uma:
    ///
    /// | motivo | hoje |
    /// |---|---|
    /// | um 401 que **não** é de token vira laço: renova, falha, renova | continua valendo |
    /// | cada renovação **aposenta o token dos outros aparelhos** | ❌ o token virou **por aparelho** |
    ///
    /// A resposta do servidor diz «podem tirar a trava de renovação única», e
    /// tirar **inteira** seria trocar um defeito por outro: o laço não some
    /// porque o token deixou de ser destrutivo.
    ///
    /// O que muda é o **alcance**: ela deixa de ser «uma vez por abertura do
    /// player, para sempre» e passa a ser «uma vez por tentativa». Quem recupera
    /// zera a trava — então um filme de três horas cujo token vença duas vezes
    /// renova duas vezes, que era o caso que a trava velha condenava a um
    /// «o filme não abriu» no meio da sessão.
    private var jaRenovou = false

    func arte(_ caminho: String) -> URL? { odeon.urlDaArte(caminho) }

    func abrir(faixaDeAudio: Int? = nil, apartirDe: Double? = nil) async {
        /// ⚠️ O disco primeiro, e sem `try`: um arquivo local não pede plano, não
        /// pede sessão e não pede token. É o caminho mais curto do app inteiro, e
        /// tem que continuar sendo — qualquer requisição aqui reintroduz a rede
        /// numa tela que existe pra não precisar dela.
        if let local {
            await abrirDoDisco(local, apartirDe: apartirDe)
            return
        }
        do {
            let plano = try await odeon.plano(arquivo: arquivo, faixaDeAudio: faixaDeAudio)
            if let medida = plano.duracaoEmSegundos, medida > 0 { duracaoDoServidor = medida }
            let ponto = apartirDe ?? comecarEm

            let url: URL?
            if plano.eDireto, let direta = plano.urlDireta {
                /// ⚠️ Direto **não** quer dizer «sem token»: o `direct_url` abre
                /// bytes, e bytes pedem o token de mídia.
                url = try await odeon.urlDeMidia(direta)
            } else {
                /// ⚠️ A sessão nasce **já no ponto de retomada**. Abrir em zero e
                /// depois buscar faria o ffmpeg produzir desde o começo um trecho
                /// que ninguém vai ver — e num servidor de casa isso é trabalho
                /// jogado fora.
                let sessao = try await odeon.abrirSessao(
                    arquivo: arquivo,
                    comecandoEm: Int(ponto),
                    faixaDeAudio: faixaDeAudio,
                )
                sessaoAberta = sessao.id
                /// ⚠️ E a da sessão, pelo mesmo motivo: uma sessão retomada pode
                /// não ter passado pelo plano.
                if let medida = sessao.duracaoEmSegundos, medida > 0 {
                    duracaoDoServidor = medida
                }
                url = try await odeon.urlDeMidia(sessao.urlDaPlaylist)
            }

            guard let url else {
                recado = "não deu pra montar o endereço do filme"
                return
            }

            /// ## O `?token=` da URL basta — desde 14/08/2026, e não bastava antes
            ///
            /// ⚠️ A playlist sempre carregou (ela leva o token na query). Os
            /// **segmentos** dentro dela eram relativos, e o AVPlayer os resolve
            /// contra a playlist **sem levar a query junto** — cada segmento
            /// chegava sem credencial e tomava 401. Medido:
            ///
            /// ```
            /// HTTP 401 Unauthorized
            /// uri=…/api/hls/be788e35-…/seg00008.ts
            /// ```
            ///
            /// É a **terceira** vez que este defeito aparece nesta casa: o
            /// `RepositorioOdeon.kt` do Android já escrevia «o `?token=` da URL não
            /// basta pro ExoPlayer — foi 401 nos canais ao vivo e foi 401 aqui,
            /// pelo mesmo motivo e com uma semana de diferença».
            ///
            /// ⚠️ **A saída do Android não serve aqui.** Lá se manda `Authorization`
            /// no cabeçalho; o AVFoundation não tem jeito suportado de injetar
            /// cabeçalho, e a chave não documentada `AVURLAssetHTTPHeaderFieldsKey`
            /// foi tentada e **piorou**: o item ficava em `.unknown` carregando zero.
            ///
            /// O conserto ficou no servidor: quando a playlist é pedida com
            /// `?token=`, cada linha de segmento sai carimbada com o mesmo token.
            /// É o mesmo token de mídia, mesmo escopo, mesmo vencimento — muda só
            /// onde ele aparece. Cliente que usa cabeçalho (Android, web) recebe a
            /// playlist byte a byte igual à de antes.
            let item = AVPlayerItem(url: url)
            let player = AVPlayer(playerItem: item)
            self.player = player

            /// ⚠️ Em `direct_play` a retomada é aqui; em HLS ela já veio no
            /// `start` da sessão, e buscar de novo seria pedir ao ffmpeg um
            /// trecho que ele já pulou.
            if plano.eDireto, ponto > 0 {
                await player.seek(to: CMTime(seconds: ponto, preferredTimescale: 600))
            }

            /// A tira de filme, pedida à parte: ela custa ffmpeg no servidor e o
            /// filme não espera por ela.
            Task { [weak self, odeon, obra] in
                let achadas = (try? await odeon.cenas(obra: obra)) ?? []
                await MainActor.run { self?.cenas = achadas }
            }

            modoDoPlano = plano.mode
            faixas = plano.faixasDeAudio
            faixaEmUso = plano.faixaDeAudio ?? plano.faixasDeAudio.first?.index
            legendas = plano.subtitles

            player.play()
            recado = nil
            marcarDeTemposEmTempos(player)
            vigiarFalha(item)
        } catch {
            recado = (error as? FalhaDoOdeon)?.errorDescription ?? "não deu pra abrir o filme"
        }
    }

    /// Abre o que está no aparelho.
    ///
    /// ⚠️ **Sem faixas e sem legendas na tela**, e isso é honestidade e não
    /// esquecimento: elas vêm do plano, que vem do servidor. O arquivo guardado
    /// tem as faixas que tem, e o AVFoundation escolhe a primeira. Desenhar um
    /// menu de áudio vazio ou um botão de legenda que não faz nada seria oferecer
    /// o que não há (§53) — o menu simplesmente não aparece.
    ///
    /// ⚠️ E o progresso continua sendo marcado, **quando dá**. Sem rede a chamada
    /// falha e é engolida: o `marcarProgresso` já trata isso. O que não pode é o
    /// filme não abrir por causa dela.
    private func abrirDoDisco(_ url: URL, apartirDe: Double?) async {
        let item = AVPlayerItem(url: url)
        let player = AVPlayer(playerItem: item)
        self.player = player

        let ponto = apartirDe ?? comecarEm
        if ponto > 0 {
            await player.seek(to: CMTime(seconds: ponto, preferredTimescale: 600))
        }

        player.play()
        recado = nil
        Task { [weak self, odeon, obra] in
            let achadas = (try? await odeon.cenas(obra: obra)) ?? []
            await MainActor.run { self?.cenas = achadas }
        }
        marcarDeTemposEmTempos(player)
        vigiarFalha(item)
    }

    /// ⚠️ **Sem isto o player falha em silêncio**, e foi o que aconteceu na
    /// primeira tentativa: a sessão abriu, a URL foi montada, o `AVPlayer` nasceu
    /// — e a tela mostrou o ícone de «não consigo tocar» do AVKit sem uma palavra
    /// de explicação. O §8b cobra erro **visível e legível**, e «um triângulo
    /// riscado» não é nenhum dos dois.
    ///
    /// O erro de verdade mora no `AVPlayerItem`, não no `AVPlayer`, e só aparece
    /// quando o status vira `.failed`.
    private func vigiarFalha(_ item: AVPlayerItem) {
        vigia = Task { [weak self] in
            for volta in 0 ..< 40 {
                try? await Task.sleep(for: .milliseconds(500))
                guard let self else { return }

                /// ⚠️ Instantâneo do estado a cada 2 s. Sem isto, «tela preta com
                /// cursor em `--:--`» é indistinguível de buffer, de playlist vazia e
                /// de vídeo sem faixa — três causas com o mesmo sintoma.
                /// ⚠️ **Voltou a tocar? A trava zera.** Com o token por aparelho a
                /// renovação não custa nada a ninguém, então o que ela precisa
                /// impedir é só o laço — e um laço não toca. Ver `jaRenovou`.
                if item.status == .readyToPlay, (self.player?.rate ?? 0) > 0 {
                    self.jaRenovou = false
                }

                if volta % 4 == 0 {
                    let d = item.duration.seconds
                    let carregado = item.loadedTimeRanges.first?.timeRangeValue.duration.seconds ?? 0
                    let dur = d.isFinite ? String(format: "%.0f", d) : "indefinida"
                    print("-- t+\(volta / 2)s status=\(item.status.rawValue)"
                        + " rate=\(self.player?.rate ?? -1)"
                        + " duracao=\(dur)"
                        + " carregado=\(String(format: "%.1f", carregado))s"
                        + " bufferVazio=\(item.isPlaybackBufferEmpty)"
                        + " deveSegurar=\(item.isPlaybackLikelyToKeepUp)")
                }

                if item.status == .failed {
                    /// ⚠️ **401 de mídia: o token pode ter morrido.**
                    ///
                    /// Ele morre por dois motivos, e os dois são normais nesta
                    /// casa: o servidor pode ter limpado os tokens, e — pior —
                    /// **qualquer outro aparelho que peça um token aposenta o
                    /// meu** (TV, celular, web). Com quatro clientes, isto vai
                    /// acontecer no meio de um filme.
                    ///
                    /// A regra herdada é «não renovar por precaução», e ela
                    /// continua: aqui só se renova **depois de o token provar que
                    /// morreu**. Uma vez por tentativa, e recomeça a abertura —
                    /// ver a folha do `jaRenovou`.
                    if !self.jaRenovou, Self.pareceTokenMorto(item) {
                        self.jaRenovou = true
                        print("-- 401 de mídia: renovando o token e reabrindo")
                        _ = try? await self.odeon.renovarTokenDeMidia()
                        await self.abrir(faixaDeAudio: self.faixaEmUso)
                        return
                    }
                    let erro = item.error
                    let detalhe = (erro as NSError?).map {
                        "\($0.domain) \($0.code): \($0.localizedDescription)"
                    } ?? "sem detalhe"
                    print("── player falhou: \(detalhe)")
                    if let sub = (item.error as NSError?)?.userInfo[NSUnderlyingErrorKey] as? NSError {
                        print("   causa: \(sub.domain) \(sub.code) \(sub.localizedDescription)")
                    }
                    for log in item.errorLog()?.events ?? [] {
                        print("   log: status=\(log.errorStatusCode) \(log.errorComment ?? "") uri=\(log.uri ?? "")")
                    }
                    recado = "o filme não abriu — \(detalhe)"
                    return
                }
                if item.status == .readyToPlay { return }
            }
        }
    }

    /// Escolhe a legenda — ou desliga, com `nil`.
    ///
    /// ⚠️ Ela **não** reabre a sessão, ao contrário do áudio: a legenda é um
    /// arquivo à parte, desenhado por cima. Trocar é baixar outro texto, e o filme
    /// nem pisca.
    func escolherLegenda(_ indice: Int?) async {
        legendaEscolhida = indice
        falaAgora = nil
        falas = []

        guard let indice else {
            pararRelogioDaLegenda()
            return
        }
        falas = (try? await odeon.legenda(arquivo: arquivo, indice: indice)) ?? []
        if falas.isEmpty {
            /// §8b: falhar calado aqui seria a legenda «não funcionar» sem
            /// ninguém saber por quê.
            recado = "essa legenda não veio"
            legendaEscolhida = nil
            return
        }
        comecarRelogioDaLegenda()
    }

    /// ⚠️ Quatro vezes por segundo, e **só com legenda ligada**.
    ///
    /// Não por quadro: a busca é binária sobre ~1.500 falas, mas acordar a
    /// interface 60 vezes por segundo pra trocar um texto que muda a cada 3 s é
    /// bateria gasta à toa. 250 ms é abaixo do que o olho nota num corte de fala.
    private func comecarRelogioDaLegenda() {
        pararRelogioDaLegenda()
        guard let player else { return }
        relogioDaLegenda = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main,
        ) { [weak self] tempo in
            guard let self else { return }
            let nova = Legenda.falaEm(tempo.seconds, self.falas)
            if nova != self.falaAgora { self.falaAgora = nova }
        }
    }

    private func pararRelogioDaLegenda() {
        if let relogioDaLegenda { player?.removeTimeObserver(relogioDaLegenda) }
        relogioDaLegenda = nil
    }

    /// Troca a faixa de áudio.
    ///
    /// ## ⚠️ Trocar de faixa **exige sessão nova**, como o ponto de retomada
    ///
    /// A playlist já foi escrita com a faixa anterior, e o ffmpeg daquela sessão
    /// não muda de ideia no meio. Então isto **reabre** o filme na posição atual,
    /// com a faixa pedida — e encerra a sessão velha, senão fica um ffmpeg vivo
    /// por troca de idioma.
    ///
    /// ⚠️ Em `direct_play` não há sessão e o arquivo vai inteiro: ali quem escolhe
    /// é o próprio AVPlayer, pelas faixas que já estão dentro do arquivo. Este
    /// caminho é o do remux e da transcodificação.
    func trocarFaixa(_ indice: Int) async {
        guard indice != faixaEmUso else { return }
        let posicao = player?.currentTime().seconds ?? comecarEm

        if let velha = sessaoAberta {
            await odeon.encerrarSessao(velha)
            sessaoAberta = nil
        }
        vigia?.cancel(); vigia = nil
        if let observador { player?.removeTimeObserver(observador) }
        observador = nil
        player?.pause()
        player = nil

        faixaEmUso = indice
        recado = "trocando o áudio…"
        await abrir(faixaDeAudio: indice, apartirDe: posicao)
    }

    /// O 401 chega por **dois caminhos diferentes**, e olhar só um deixa metade
    /// dos filmes sem conserto.
    ///
    /// ## ⚠️ O defeito que isto conserta — visto na tela
    ///
    /// A primeira versão olhava só o `errorLog()` do item, procurando o `-16840`
    /// do CoreMedia. Isso funciona em **HLS**, onde o log existe — e num
    /// `direct_play` o log vem **vazio**, porque `AVPlayerItemErrorLog` é
    /// instrumentação de streaming. Resultado: o token morria, o remux se
    /// recuperava sozinho e o arquivo direto ficava na tela com
    /// «NSURLErrorDomain -1013» — que é 401 com outro nome.
    ///
    /// Ou seja: a renovação existia e cobria só 53% dos casos. Agora olha os dois
    /// lados — o log (HLS) e a cadeia de erro do próprio item (arquivo direto).
    private static func pareceTokenMorto(_ item: AVPlayerItem) -> Bool {
        if (item.errorLog()?.events ?? []).contains(where: { $0.errorStatusCode == -16840 }) {
            return true
        }
        guard let erro = item.error as NSError? else { return false }
        /// `-1013` é `NSURLErrorUserAuthenticationRequired`. O `-16840` também
        /// aparece aqui, como erro subjacente, quando o item falha sem log.
        var atual: NSError? = erro
        while let e = atual {
            if e.code == -1013 || e.code == -16840 { return true }
            atual = e.userInfo[NSUnderlyingErrorKey] as? NSError
        }
        return false
    }

    /// Marca o progresso a cada 20 s.
    ///
    /// ⚠️ Não a cada segundo: são três pessoas dividindo um servidor de casa, e
    /// «onde eu parei» não precisa de precisão de segundo. Vinte segundos é o
    /// suficiente pra retomar sem parecer que perdeu nada.
    /// O relógio **de desenho**: quatro vezes por segundo, sem tocar na rede.
    private var relogioDaTela: Any?

    private func marcarDeTemposEmTempos(_ player: AVPlayer) {
        relogioDaTela = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 0.25, preferredTimescale: 600),
            queue: .main,
        ) { [weak self] tempo in
            guard let self else { return }
            posicao = tempo.seconds
            tocando = player.rate > 0
        }
        observador = player.addPeriodicTimeObserver(
            forInterval: CMTime(seconds: 20, preferredTimescale: 1),
            queue: .main,
        ) { [weak self] tempo in
            guard let self else { return }
            let posicao = tempo.seconds
            guard posicao > 0, registraProgresso else { return }
            Task { [odeon, obra, duracao, arquivo] in
                await odeon.marcarProgresso(
                    obra: obra,
                    posicao: posicao,
                    duracao: duracao,
                    arquivo: arquivo,
                )
            }
        }
    }

    func alternarPausa() {
        guard let player else { return }
        if player.rate > 0 { player.pause() } else { player.play() }
        tocando = player.rate > 0
    }

    /// ⚠️ `pular` **prende nas bordas**: sem o teto, ⟳30 no fim manda o filme pra
    /// depois do fim e o AVPlayer para sem dizer nada; sem o piso, ⟲10 no começo
    /// pede um instante negativo.
    func pular(_ segundos: Double) async {
        guard let player else { return }
        let alvo = min(max(0, posicao + segundos), duracaoDaSessao ?? .greatestFiniteMagnitude)
        await irPara(alvo)
    }

    func irPara(_ segundos: Double) async {
        guard let player else { return }
        posicao = segundos
        await player.seek(to: CMTime(seconds: segundos, preferredTimescale: 600),
                          toleranceBefore: .zero, toleranceAfter: .zero)
    }

    /// Sair: marca onde parou e **encerra a sessão**.
    ///
    /// ⚠️ A ordem importa. Marcar depois de encerrar arriscaria perder a posição
    /// se a saída for abrupta, e a posição é o que a pessoa vai querer amanhã.
    func aoSair() async {
        vigia?.cancel(); vigia = nil
        pararRelogioDaLegenda()
        if let observador { player?.removeTimeObserver(observador) }
        if let relogioDaTela { player?.removeTimeObserver(relogioDaTela) }
        observador = nil
        relogioDaTela = nil

        if let posicao = player?.currentTime().seconds, posicao > 0, registraProgresso {
            await odeon.marcarProgresso(
                obra: obra,
                posicao: posicao,
                duracao: duracao,
                arquivo: arquivo,
            )
        }
        player?.pause()
        player = nil

        /// ⚠️ **Isto não é higiene, é CPU do servidor de casa** — e no iOS metade
        /// do acervo abre sessão. Ver `RepositorioOdeon.encerrarSessao`.
        if let sessaoAberta {
            await odeon.encerrarSessao(sessaoAberta)
            self.sessaoAberta = nil
        }
    }
}

struct TelaDoPlayer: View {
    @State private var modelo: ModeloDoPlayer
    /// ⚠️ Começam **visíveis**: a primeira coisa que se quer saber ao abrir é de
    /// onde o filme está começando. Eles se apagam sozinhos em 4 s.
    @State private var mostrarControles = true
    let aoVoltar: () -> Void

    init(
        odeon: RepositorioOdeon,
        obra: String,
        arquivo: String,
        local: URL? = nil,
        duracao: Double?,
        titulo: String = "",
        comecarEm: Double,
        doAoVivo: Bool = false,
        aoVoltar: @escaping () -> Void,
    ) {
        _modelo = State(wrappedValue: ModeloDoPlayer(
            odeon: odeon, obra: obra, arquivo: arquivo,
            duracao: duracao, titulo: titulo, comecarEm: comecarEm, local: local,
            doAoVivo: doAoVivo,
        ))
        self.aoVoltar = aoVoltar
    }

    var body: some View {
        ZStack {
            /// ## ⚠️ O toque mora **no fundo**, e não no `ZStack`
            ///
            /// Esta foi a terceira tentativa, e as duas primeiras ensinaram:
            ///
            /// | onde | o que aconteceu |
            /// |---|---|
            /// | na `CamadaDeVideo` | a `UIView` absorve o toque antes do SwiftUI, e o filme tem tarjas — a camada é a área do **quadro**, não da tela |
            /// | no `ZStack` inteiro | não disparava; um `contentShape` num empilhamento com `UIViewRepresentable` dentro não é a região que parece ser |
            ///
            /// O fundo preto **preenche a tela por definição** e é a única camada
            /// deste `ZStack` de que isso é verdade. É nele que o toque mora.
            ///
            /// ⚠️ E um toque que **pausasse** faria quem quer só ver quanto falta
            /// parar o filme pra descobrir. Mostrar e esconder é o gesto certo.
            Color.black.ignoresSafeArea()
                .contentShape(.rect)
                .onTapGesture {
                    withAnimation(.easeOut(duration: 0.18)) { mostrarControles.toggle() }
                }

            if let player = modelo.player {
                CamadaDeVideo(player: player).ignoresSafeArea()
            }

            /// ⚠️ O recado só existe enquanto **não** há vídeo. Ele não fica por
            /// cima do filme: movimento não pode esconder estado, mas estado
            /// também não fica em cima de conteúdo depois de resolvido (§8b).
            if let recado = modelo.recado {
                VStack(alignment: .leading, spacing: 10) {
                    Text("SESSÃO")
                        .font(Tipo.rotulo())
                        .tracking(Tipo.espacoDoRotulo)
                        .foregroundStyle(Cores.destaque)
                    Text(recado)
                        .font(Tipo.letreiro(26))
                        .foregroundStyle(Cores.texto)
                }
                .padding(28)
            }

            /// A legenda, desenhada por nós.
            ///
            /// ⚠️ **Ela precisa sobreviver a qualquer imagem.** Texto branco sobre
            /// cena clara é o defeito que o Android mediu como contraste de
            /// **1,02:1** — ilegível. A sombra atrás faz o trabalho sem véu
            /// retangular, que taparia parte do filme mesmo no silêncio.
            if let fala = modelo.falaAgora {
                VStack {
                    Spacer()
                    Text(fala.texto)
                        .font(.system(size: 17, weight: .medium))
                        .foregroundStyle(.white)
                        .multilineTextAlignment(.center)
                        .shadow(color: .black.opacity(0.95), radius: 3, y: 1)
                        .shadow(color: .black.opacity(0.7), radius: 8)
                        .padding(.horizontal, 24)
                        .padding(.bottom, 96)
                }
                /// ⚠️ Ela não recebe toque: o gesto do meio da tela é do player, e
                /// uma legenda que engole o toque faria os controles não abrirem
                /// exatamente quando há alguém falando.
                .allowsHitTesting(false)
            }

            /// ## Os controles — nossos, e não os da Apple
            ///
            /// Ver `Projecao.swift`: os do sistema mostravam **«4:44 AM»** num
            /// filme de 2006, porque a playlist de HLS sai como `EVENT` e o
            /// AVPlayer é obrigado a tratá-la como transmissão. Estes sabem a
            /// duração pela ficha.
            if modelo.player != nil {
                controles.opacity(mostrarControles ? 1 : 0)
                    .allowsHitTesting(mostrarControles)
            }

        }
        .task { await modelo.abrir() }
    }

    /// A camada de controles: barra de cima, tira de filme, botões e relógio.
    private var controles: some View {
        VStack(spacing: 0) {
            barraDeCima
            Spacer()
            rodape
        }
        /// ⚠️ Dois véus rasos, e não um fundo escuro inteiro: o filme continua
        /// visível no meio. Um painel opaco por cima do vídeo é a interface
        /// tapando o conteúdo — que é o que os controles existem pra evitar.
        .background {
            VStack {
                LinearGradient(colors: [.black.opacity(0.75), .clear],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: 140)
                Spacer()
                LinearGradient(colors: [.clear, .black.opacity(0.85)],
                               startPoint: .top, endPoint: .bottom)
                    .frame(height: 260)
            }
            .ignoresSafeArea()
            .allowsHitTesting(false)
        }
        .task(id: modelo.tocando) {
            /// ⚠️ Somem sozinhos **tocando**, e ficam **pausado**: quem pausou
            /// está mexendo nos controles, e apagá-los na cara da pessoa é a
            /// interface discordando do gesto.
            guard modelo.tocando else { return }
            try? await Task.sleep(for: .seconds(4))
            withAnimation { mostrarControles = false }
        }
    }

    private var barraDeCima: some View {
        HStack(spacing: 12) {
            Button { Task { await modelo.aoSair(); aoVoltar() } } label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(width: 44, height: 44)
                    .contentShape(.rect)
            }
            /// ⚠️ O **ponto do plano**: verde é `direto`, dourado é remux, laranja
            /// é transcodificando. É o mesmo dado do selo da ficha, aqui reduzido
            /// a um ponto — no meio de um filme ninguém quer ler «codecs batem»,
            /// mas saber que o servidor está trabalhando explica um travamento.
            if let cor = corDoPlano {
                Circle().fill(cor).frame(width: 9, height: 9)
                    .shadow(color: cor.opacity(0.8), radius: 5)
            }
            Text(modelo.titulo)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
            Spacer(minLength: 0)

            /// ⚠️ O menu de áudio só existe quando **há escolha**: um menu com
            /// uma faixa é pergunta sem resposta alternativa (§24).
            if modelo.faixas.count > 1 {
                Menu {
                    ForEach(modelo.faixas, id: \.index) { faixa in
                        Button { Task { await modelo.trocarFaixa(faixa.index) } } label: {
                            if modelo.faixaEmUso == faixa.index {
                                Label(rotuloDaFaixa(faixa), systemImage: "checkmark")
                            } else {
                                Text(rotuloDaFaixa(faixa))
                            }
                        }
                    }
                } label: {
                    Image(systemName: "waveform")
                        .font(.system(size: 18))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                }
            }

            /// ⚠️ O de legenda existe **mesmo com uma faixa**, ao contrário do de
            /// áudio: «sem legenda» é uma escolha de verdade, e é o padrão.
            if !modelo.legendas.isEmpty {
                Menu {
                    Button { Task { await modelo.escolherLegenda(nil) } } label: {
                        if modelo.legendaEscolhida == nil {
                            Label("sem legenda", systemImage: "checkmark")
                        } else {
                            Text("sem legenda")
                        }
                    }
                    ForEach(modelo.legendas, id: \.index) { legenda in
                        Button { Task { await modelo.escolherLegenda(legenda.index) } } label: {
                            if modelo.legendaEscolhida == legenda.index {
                                Label(rotuloDaLegenda(legenda), systemImage: "checkmark")
                            } else {
                                Text(rotuloDaLegenda(legenda))
                            }
                        }
                    }
                } label: {
                    Image(systemName: "captions.bubble")
                        .font(.system(size: 18))
                        .foregroundStyle(modelo.legendaEscolhida == nil ? .white : Cores.destaque)
                        .frame(width: 44, height: 44)
                }
            }
        }
        .padding(.horizontal, 8)
        .padding(.top, 4)
    }

    private var rodape: some View {
        VStack(spacing: 14) {
            /// ⚠️ A tira só desenha quando **há fotogramas**: uma faixa preta com
            /// furos e nada dentro seria moldura sem quadro (§24).
            if !modelo.cenas.isEmpty {
                TiraDeFilme(
                    cenas: modelo.cenas,
                    posicao: modelo.posicao,
                    odeon: modelo.odeon,
                    aoEscolher: { alvo in Task { await modelo.irPara(alvo) } },
                )
            }

            HStack(spacing: 34) {
                botaoDePulo(-10, icone: "gobackward.10")
                Button { modelo.alternarPausa() } label: {
                    Image(systemName: modelo.tocando ? "pause.fill" : "play.fill")
                        .font(.system(size: 26))
                        .foregroundStyle(Cores.fundo)
                        .frame(width: 66, height: 66)
                        .background(Cores.destaque, in: .circle)
                        .shadow(color: Cores.destaque.opacity(0.5), radius: 14)
                }
                .buttonStyle(.plain)
                botaoDePulo(30, icone: "goforward.30")
            }

            /// ⚠️ **`faltam`, e não a duração total.** É a mesma escolha do herói
            /// da biblioteca: no meio de um filme, o que se pergunta é quanto
            /// falta. E some quando não dá pra saber, em vez de mostrar `--:--`.
            HStack {
                Text(relogioDaSessao(modelo.posicao))
                    .font(.system(size: 13).monospacedDigit())
                    .foregroundStyle(.white.opacity(0.85))
                Spacer()
                if let falta = modelo.quantoFalta {
                    Text("faltam \(relogioDaSessao(falta))")
                        .font(.system(size: 13).monospacedDigit())
                        .foregroundStyle(.white.opacity(0.6))
                }
            }
            .padding(.horizontal, 20)
        }
        .padding(.bottom, 12)
    }

    private func botaoDePulo(_ segundos: Double, icone: String) -> some View {
        Button { Task { await modelo.pular(segundos) } } label: {
            Image(systemName: icone)
                .font(.system(size: 26))
                .foregroundStyle(.white)
                .frame(width: 54, height: 54)
                .contentShape(.rect)
        }
        .buttonStyle(.plain)
    }

    /// Verde · dourado · laranja — os mesmos três do selo da ficha.
    private var corDoPlano: Color? {
        switch modelo.modoDoPlano {
        case "direct_play": .green
        case "direct_stream": Cores.destaque
        case "transcode": .orange
        default: nil
        }
    }
}


/// O rótulo de uma faixa de áudio, com as quedas na ordem certa.
///
/// ⚠️ **O `label` vem pronto do servidor** — codec, idioma e canais já vêm
/// compostos por quem tem a probe. Montar «Português - AC3 5.1» aqui seria a
/// **quarta** redação da mesma frase entre web, Android, servidor e iOS.
///
/// As quedas existem pro caso de ele vir vazio, e cada uma é fato e não chute:
///
/// | | |
/// |---|---|
/// | `title` | o que quem ripou escreveu na faixa |
/// | `language` | o código do contêiner |
/// | `faixa N` | a posição, que é o que sempre se sabe |
///
/// ⚠️ **`und` conta como ausente.** Em ISO 639 ele quer dizer *undetermined* — o
/// contêiner declarando que **não sabe**. O menu de faixas do Android abriu com
/// uma faixa chamada «und» na cara do dono em 06/08/2026; `faixa 1` diz o mesmo, e
/// diz em português.
func rotuloDaFaixa(_ faixa: FaixaDeAudio) -> String {
    if !faixa.label.isEmpty { return faixa.label }
    if let t = faixa.title, !t.isEmpty { return t }
    if let l = faixa.language, !l.isEmpty, l != "und" {
        return idiomasEmPortugues([l]) ?? l
    }
    return "faixa \(faixa.index + 1)"
}


/// O rótulo de uma legenda, com as mesmas quedas do áudio.
///
/// ⚠️ O `label` vem pronto do servidor — «Português (forçada)» é frase dele, e
/// remontá-la aqui seria a quarta redação. As quedas existem pro caso de ele vir
/// vazio, e `und` continua não sendo idioma.
func rotuloDaLegenda(_ legenda: FaixaDeLegenda) -> String {
    if !legenda.label.isEmpty { return legenda.label }
    if let l = legenda.language, !l.isEmpty, l != "und" {
        let nome = idiomasEmPortugues([l]) ?? l
        return legenda.forced ? nome + " (forçada)" : nome
    }
    return "legenda \(abs(legenda.index))"
}
