import AVFoundation
import Testing
@testable import Odeon

/// A sonda: pergunta ao aparelho **cru**, sem passar pela minha tradução.
///
/// ## ⚠️ Por que ela existe — 14/08/2026
///
/// O `CapacidadesDoAparelho.conteineres` filtra `audiovisualTypes()` por uma
/// tabela escrita à mão. Isso significa que **um contêiner que o aparelho declara
/// e eu não previ fica invisível** — é exatamente o «lista fixa mente nos dois
/// sentidos» do Android, cometido dentro da camada que existe pra evitá-lo.
///
/// O servidor mediu o acervo e devolveu a lista real de codecs
/// (`mp3, dts, pcm_s16le, mpeg2video, mpeg4, msmpeg4v3`) e de contêineres
/// (`matroska 55%, mov/mp4 30%, avi 14,7%`). Com essa lista na mão, a pergunta
/// certa deixa de ser «o que eu lembro de perguntar» e passa a ser **«o aparelho
/// toca o que este acervo tem?»**.
///
/// Esta sonda não afirma nada: ela imprime, pra o próximo passo ser decidido por
/// número e não por memória.
struct SondaDeCapacidades {

    @Test("cru: tudo o que o AVFoundation declara abrir")
    func tiposCrus() {
        let tipos = AVURLAsset.audiovisualTypes().map(\.rawValue).sorted()
        print("── audiovisualTypes (\(tipos.count)) ──")
        for t in tipos { print("   \(t)") }

        let mimes = AVURLAsset.audiovisualMIMETypes().sorted()
        print("── audiovisualMIMETypes (\(mimes.count)) ──")
        for m in mimes { print("   \(m)") }
    }

    /// As grafias possíveis de cada codec que **este acervo** tem.
    ///
    /// ⚠️ Uma resposta negativa aqui pode ser o aparelho recusando **ou** a minha
    /// grafia estar errada — foi o que aconteceu com o mp3. Por isso cada codec
    /// vai com várias formas: se alguma passar, o aparelho toca.
    @Test("codecs do acervo, em todas as grafias que eu saiba escrever")
    func codecsDoAcervo() {
        let perguntas: [(nome: String, mimes: [String])] = [
            // ── áudio que o acervo tem
            ("mp3", [
                "audio/mpeg",
                "audio/mp3",
                #"video/mp4; codecs="mp4a.40.34""#,
                #"video/mp4; codecs="mp3""#,
                #"video/mp4; codecs=".mp3""#,
                #"audio/mpeg; codecs="mp3""#,
            ]),
            ("aac", [#"video/mp4; codecs="mp4a.40.2""#]),
            ("ac3", [#"video/mp4; codecs="ac-3""#]),
            ("eac3", [#"video/mp4; codecs="ec-3""#]),
            ("dts", [#"video/mp4; codecs="dtsc""#, "audio/vnd.dts"]),
            ("pcm_s16le", [
                #"video/quicktime; codecs="lpcm""#,
                #"video/quicktime; codecs="sowt""#,
                "audio/wav",
            ]),
            ("vorbis", [#"video/mp4; codecs="vorbis""#, "audio/ogg"]),
            ("flac", [#"video/mp4; codecs="fLaC""#, "audio/flac"]),
            ("opus", [#"video/mp4; codecs="Opus""#, #"video/mp4; codecs="opus""#]),
            ("alac", [#"video/mp4; codecs="alac""#]),

            // ── vídeo que o acervo tem
            ("h264", [#"video/mp4; codecs="avc1.640028""#]),
            ("hevc", [#"video/mp4; codecs="hvc1.1.6.L93.B0""#]),
            ("av1", [#"video/mp4; codecs="av01.0.05M.08""#]),
            ("mpeg4", [
                #"video/mp4; codecs="mp4v.20.9""#,
                #"video/mp4; codecs="mp4v.20.3""#,
                #"video/mp4; codecs="mp4v""#,
            ]),
            ("mpeg2video", [#"video/mp4; codecs="mp2v""#, "video/mpeg"]),
            ("msmpeg4v3", [#"video/mp4; codecs="div3""#, "video/x-msvideo"]),
        ]

        print("── codecs, por grafia ──")
        for p in perguntas {
            let aceitas = p.mimes.filter { AVURLAsset.isPlayableExtendedMIMEType($0) }
            let veredito = aceitas.isEmpty ? "NÃO" : "sim"
            print("   \(p.nome.padding(toLength: 12, withPad: " ", startingAt: 0)) \(veredito)")
            for a in aceitas { print("        ↳ \(a)") }
        }
    }
}
