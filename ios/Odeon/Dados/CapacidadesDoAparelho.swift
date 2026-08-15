import AVFoundation
import VideoToolbox

/// O que **este** aparelho realmente toca, perguntado a ele.
///
/// ## ⚠️ Perguntar, e não listar — a regra herdada, e ela vale nos dois sentidos
///
/// O `CapacidadesDoAparelho.kt` do Android diz por quê, e a razão é a mesma aqui:
///
/// > «uma lista fixa mente nos dois sentidos. Um aparelho com decodificador de
/// > HEVC receberia transcodificação à toa — e transcodificar é a única das três
/// > coisas que custa CPU do servidor, com três pessoas de verdade dividindo a
/// > mesma máquina. Um aparelho velho receberia um arquivo que não abre.»
///
/// A web pergunta ao `canPlayType`; o Android ao `MediaCodecList`; aqui ao
/// `AVURLAsset`. Mesma porta do servidor, mesmos nomes de parâmetro.
///
/// ## ⚠️ A armadilha que este arquivo já caiu, e a regra que saiu dela
///
/// A primeira versão perguntava `audio/ogg` pra saber de **vorbis** e
/// `video/x-msvideo` pra saber de **msmpeg4v3**. As duas responderam «sim» — e as
/// duas respostas eram sobre o **contêiner**, não sobre o codec. Declarar vorbis
/// por causa disso seria pedir ao servidor que mandasse um arquivo que o app não
/// abre: exatamente o segundo sentido em que a lista fixa mente.
///
/// A regra que saiu daí, e que está imposta em código abaixo: **só vale pergunta
/// com `codecs=`**. Um MIME sem parâmetro de codec pergunta «você abre esta
/// casca?», e a resposta dele não autoriza declarar nada sobre o conteúdo.
///
/// ## ⚠️ Isto **não** vale no simulador como vale no aparelho
///
/// O simulador roda sobre o Mac. `VTIsHardwareDecodeSupported` responde pelo
/// hospedeiro, não pelo telefone — medida ali é indício, não achado. É a mesma
/// armadilha da régua de fps no emulador do Android.
enum CapacidadesDoAparelho {

    /// ⚠️ **Só pergunta qualificada por codec conta.** Ver o cabeçalho: sem
    /// `codecs=`, quem responde é o contêiner, e a resposta não serve.
    private static func tocaCodec(_ mimes: [String]) -> Bool {
        mimes.contains { mime in
            guard mime.contains("codecs=") else {
                assertionFailure("pergunta sem `codecs=` não vale: \(mime)")
                return false
            }
            return AVURLAsset.isPlayableExtendedMIMEType(mime)
        }
    }

    /// Os contêineres que o AVFoundation abre, no vocabulário do servidor.
    ///
    /// ## ⚠️ A tabela é fixa, e por isso ela **avisa** quando não conhece algo
    ///
    /// Traduzir UTI da Apple pro nome que o servidor usa exige tabela. O risco é
    /// ela virar a mesma lista fixa que este arquivo existe pra evitar: um
    /// contêiner declarado pelo aparelho e ausente daqui fica **invisível**.
    ///
    /// Foi o que aconteceu: a primeira versão não tinha `public.avi`, e o acervo
    /// tem **2.645 arquivos avi (14,7%)** — eles sumiam da conta em silêncio. Por
    /// isso o `naoMapeados` existe e é impresso pela sonda.
    ///
    /// ⚠️ **`avi` foi declarado e teve que sair.** `public.avi` está entre os
    /// tipos que o aparelho diz abrir, e abrir a casca é literalmente tudo o que
    /// ele faz: um avi do acervo teve faixa, resolução e duração lidas, e nenhum
    /// quadro decodificado. A ressalva que eu tinha escrito aqui («só um arquivo
    /// de verdade fecha essa pergunta») estava certa — e a resposta foi não.
    static var conteineres: [String] {
        let tipos = Set(AVURLAsset.audiovisualTypes().map(\.rawValue))
        return tabelaDeConteineres
            .filter { tipos.contains($0.uti) }
            .map(\.servidor)
    }

    /// Os UTIs que o aparelho declara e esta tabela não conhece — sem os `dyn.`,
    /// que são tipos não registrados e não interessam.
    static var conteineresNaoMapeados: [String] {
        let conhecidos = Set(tabelaDeConteineres.map(\.uti))
        return AVURLAsset.audiovisualTypes()
            .map(\.rawValue)
            .filter { !$0.hasPrefix("dyn.") && !conhecidos.contains($0) }
            .sorted()
    }

    private static let tabelaDeConteineres: [(uti: String, servidor: String)] = [
        ("public.mpeg-4", "mp4"),
        ("com.apple.quicktime-movie", "mov"),
        ("com.apple.m4v-video", "m4v"),
        ("org.matroska.mkv", "mkv"),
        ("public.webm", "webm"),
    ]

    /// Os codecs de vídeo.
    static var codecsDeVideo: [String] {
        let perguntas: [(servidor: String, mimes: [String])] = [
            ("h264", [#"video/mp4; codecs="avc1.640028""#]),
            ("hevc", [#"video/mp4; codecs="hvc1.1.6.L93.B0""#]),
            ("av1", [#"video/mp4; codecs="av01.0.05M.08""#]),
            /// ⚠️ **`mpeg4` foi declarado e teve que sair — 14/08/2026.**
            ///
            /// O `isPlayableExtendedMIMEType` respondeu **sim** pra
            /// `mp4v.20.9`, e um arquivo de verdade respondeu **não**: um avi do
            /// acervo (704x396, 25 fps) teve contêiner lido, faixa achada,
            /// duração medida — e o `AVAssetImageGenerator` devolveu «Não é
            /// possível abrir». **Nenhum quadro saiu.**
            ///
            /// Enquanto declarado, ele fazia o servidor mandar `direct_play` em
            /// 2.265 arquivos que **antes transcodificavam e funcionavam**. A
            /// declaração errada é pior que a ausência dela.
            /// ⚠️ `msmpeg4v3` **não** entra: a única resposta positiva veio de
            /// `video/x-msvideo`, que é o contêiner. Ver o cabeçalho.
            /// ⚠️ `mpeg2video` respondeu NÃO — os 76 do acervo transcodificam.
        ]
        return perguntas.filter { tocaCodec($0.mimes) }.map(\.servidor)
    }

    /// Os codecs de áudio.
    static var codecsDeAudio: [String] {
        let perguntas: [(servidor: String, mimes: [String])] = [
            ("aac", [#"video/mp4; codecs="mp4a.40.2""#]),
            /// ⚠️ **`mp3` foi declarado e teve que sair — e o caso é o mais
            /// traiçoeiro dos três.**
            ///
            /// O servidor contou **576 arquivos** que viravam re-encode de áudio à
            /// toa por ele não estar declarado, então declará-lo parecia ganho
            /// puro. A prova, com arquivos fabricados (`ProvaDeMp3`):
            ///
            /// | sujeito | faixas de áudio | PCM |
            /// |---|---|---|
            /// | `.mp3` avulso | 1 | ✅ 267 KB |
            /// | mp3 em **`.mov`** | 1 | ✅ 264 KB |
            /// | mp3 em **`.mp4`** | **0** | ❌ faixa invisível |
            ///
            /// O iOS decodifica mp3 — **menos dentro de mp4**, onde o
            /// AVFoundation nem enumera a faixa. E o sintoma disso não é «não
            /// abre»: é **vídeo tocando mudo**, que parece funcionar e é o
            /// defeito que o usuário não tem como diagnosticar.
            ///
            /// ⚠️ **O contrato não sabe expressar isto.** `containers` e
            /// `audio_codecs` são listas independentes: não há como dizer «mp3
            /// sim em mov, não em mp4». Enquanto não houver, a resposta segura é
            /// não declarar — o custo é transcodificação à toa em 63 arquivos mov
            /// que funcionariam, e o benefício é nenhum arquivo mudo.
            ///
            /// ⚠️ **E falta uma medida pra ele voltar**: qual contêiner o
            /// `direct_stream` usa nos segmentos HLS. Se for MPEG-TS, mp3 passa e
            /// os 212 mkv+mp3 funcionam; se for fMP4, eles tocariam mudos.
            ("ac3", [#"video/mp4; codecs="ac-3""#]),
            ("eac3", [#"video/mp4; codecs="ec-3""#]),
            ("alac", [#"video/mp4; codecs="alac""#]),
            ("opus", [#"video/mp4; codecs="Opus""#]),
            ("flac", [#"video/mp4; codecs="fLaC""#]),
            /// Os 10 `pcm_s16le` do acervo.
            ("pcm", [#"video/quicktime; codecs="lpcm""#]),
            /// ⚠️ `vorbis` **não** entra — a positiva veio de `audio/ogg`, sem
            /// `codecs=`. ⚠️ `dts` respondeu NÃO: os 15 do acervo transcodificam.
        ]
        return perguntas.filter { tocaCodec($0.mimes) }.map(\.servidor)
    }

    /// HLS é nativo no iOS, e é o caminho que o servidor já usa pra a web. Não é
    /// pergunta: é a plataforma.
    static let suportaHLS = true

    /// Se o HEVC é decodificado por **hardware**. Não entra na query do plano — o
    /// servidor pergunta «toca?», não «toca sem esquentar?».
    static var hevcPorHardware: Bool {
        VTIsHardwareDecodeSupported(kCMVideoCodecType_HEVC)
    }

    /// A query que vai pro `/api/playback/{id}/plan`.
    static var query: [URLQueryItem] {
        [
            URLQueryItem(name: "containers", value: conteineres.joined(separator: ",")),
            URLQueryItem(name: "video_codecs", value: codecsDeVideo.joined(separator: ",")),
            URLQueryItem(name: "audio_codecs", value: codecsDeAudio.joined(separator: ",")),
            URLQueryItem(name: "supports_hls", value: "true"),
        ]
    }

    /// Tudo junto, pra registrar de uma vez o que este aparelho respondeu.
    static func medido() -> String {
        """
        contêineres:  \(conteineres.joined(separator: ","))
        vídeo:        \(codecsDeVideo.joined(separator: ","))
        áudio:        \(codecsDeAudio.joined(separator: ","))
        hls:          \(suportaHLS)
        hevc em hw:   \(hevcPorHardware)
        não mapeados: \(conteineresNaoMapeados.joined(separator: ", "))
        """
    }
}
