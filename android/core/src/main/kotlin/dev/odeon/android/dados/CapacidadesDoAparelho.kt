package dev.odeon.android.dados

import android.media.MediaCodecList

/// O que **este** aparelho realmente toca, perguntado a ele.
///
/// ## Por que perguntar, e não listar
///
/// A web faz a mesma coisa com `canPlayType`, e o comentário dela vale igual
/// aqui: uma lista fixa mente nos dois sentidos. Um aparelho com decodificador
/// de HEVC receberia transcodificação à toa — e transcodificar é a única das
/// três coisas que custa CPU do servidor, com três pessoas de verdade dividindo
/// a mesma máquina. Um aparelho velho receberia um arquivo que não abre.
///
/// ## E aqui a resposta é melhor que a da web
///
/// É o que a §3 da espec registra: `canPlayType` devolve `"probably"`,
/// `"maybe"` ou vazio — um palpite em texto. O `MediaCodecList` devolve **os
/// decodificadores que existem no aparelho**, por tipo MIME, e dá pra perguntar
/// se são de verdade ou emulados em software.
///
/// Isso não muda o servidor: a rota `/api/playback/{id}/plan` já recebe a lista
/// como parâmetro, e é a mesma lista que a web monta. O §4c da espec conta que
/// esse detalhe é o que faz o Cast (fase 4) não custar backend nenhum — lá o app
/// vai mandar o perfil do Chromecast por esta mesma porta.
///
/// ## O `MediaCodecList` é caro, e por isso isto é `object` com cache
///
/// Ele varre os decodificadores do sistema. Medido em milissegundos, mas a
/// resposta **não muda enquanto o app roda** — decodificador não aparece no meio
/// da sessão. Perguntar a cada play seria pagar de novo por um número constante.
object CapacidadesDoAparelho {

    /// Os contêineres. Fixos, e é honesto que sejam.
    ///
    /// Contêiner não tem decodificador pra perguntar: quem lê `mkv` e `mp4` é o
    /// extrator do Media3, não o hardware. E o Media3 lê os quatro em todo
    /// `minSdk` que este app aceita (26+).
    ///
    /// É a diferença mais visível pra web, que não põe `mkv` na lista — o
    /// navegador realmente não abre Matroska, e o Android abre. Ou seja: **este
    /// app vai ganhar `direct_play` onde a web recebia remux**, e isso é o
    /// servidor economizando trabalho por causa de uma linha.
    private val CONTEINERES = listOf("mp4", "mkv", "webm", "mov")

    /// MIME -> o nome que o servidor usa. Os nomes são os da web, de propósito:
    /// quem lê a query do lado do Rust é o mesmo código pros dois clientes.
    private val VIDEO = mapOf(
        "video/avc" to "h264",
        "video/hevc" to "hevc",
        "video/x-vnd.on2.vp8" to "vp8",
        "video/x-vnd.on2.vp9" to "vp9",
        "video/av01" to "av1",
    )

    private val AUDIO = mapOf(
        "audio/mp4a-latm" to "aac",
        "audio/mpeg" to "mp3",
        "audio/opus" to "opus",
        "audio/vorbis" to "vorbis",
        "audio/ac3" to "ac3",
        "audio/eac3" to "eac3",
        "audio/flac" to "flac",
    )

    private val decodificadores: Set<String> by lazy { levantar() }

    val containers: String get() = CONTEINERES.joinToString(",")

    /// ## ⚠️ HEVC diz a **profundidade** — 18/08/2026
    ///
    /// Este arquivo perguntava «existe decodificador de `video/hevc`?» e nunca
    /// **qual perfil**. O emulador tem HEVC Main (8 bits) e responde que sim; o
    /// acervo é Main 10. O servidor copiou o vídeo confiando na resposta, e o
    /// player parou com `format_supported=NO_EXCEEDS_CAPABILITIES` — tela preta.
    ///
    /// ⚠️ **A primeira correção daqui estava errada**, e o servidor mediu por
    /// quê: eu ia fazer `hevc` significar «8 bits» e sumir com ele quando não
    /// houvesse Main 10. Só que `hevc` já está no ar em três clientes — iOS, TV
    /// e web —, e o AVPlayer e a TCL decodificam Main 10 sem problema. Estreitar
    /// o sentido da palavra viraria 5.319 arquivos de cópia em recodificação, da
    /// noite pro dia, em aparelhos que não precisavam.
    ///
    /// Então a precisão entra por **palavra nova**:
    ///
    /// | o cliente diz | 8 bits | 10 bits |
    /// |---|---|---|
    /// | `hevc` | copia | copia |
    /// | `hevc10` | copia | copia |
    /// | `hevc8` | copia | **recodifica** |
    ///
    /// ⚠️ Este cliente **nunca manda `hevc` puro**: ele é o token ambíguo, o que
    /// promete tudo. Quem faz os dois diz `hevc10`; quem só faz 8 bits diz
    /// `hevc8` e ganha a proteção. Dizer a verdade custa uma palavra.
    val codecsDeVideo: String
        get() = VIDEO
            .filterKeys { it in decodificadores }
            .map { (mime, nome) ->
                if (mime == "video/hevc") if (fazHevc10) "hevc10" else "hevc8" else nome
            }
            .joinToString(",")

    /// O aparelho decodifica **HEVC Main 10**?
    ///
    /// ⚠️ `Main10` e não `Main`: é o perfil que carrega 10 bits, e é o do acervo.
    /// A pergunta é feita pelo `CodecCapabilities`, que é o único lugar onde o
    /// Android diz o perfil — o `supportedTypes` só diz o MIME.
    private val fazHevc10: Boolean by lazy {
        runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS)
                .codecInfos
                .filterNot { it.isEncoder }
                .filter { "video/hevc" in it.supportedTypes.map(String::lowercase) }
                .any { info ->
                    info.getCapabilitiesForType("video/hevc").profileLevels.any {
                        it.profile == android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                            it.profile ==
                            android.media.MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10
                    }
                }
        }.getOrDefault(false)
    }

    val codecsDeAudio: String
        get() = AUDIO.filterKeys { it in decodificadores }.values.joinToString(",")

    /// Os tipos MIME que este aparelho sabe **decodificar**.
    ///
    /// `REGULAR_CODECS` e não `ALL_CODECS`: o segundo inclui os codificadores, e
    /// um aparelho que sabe *escrever* HEVC não necessariamente sabe *ler* todo
    /// perfil dele. A pergunta aqui é só sobre ler.
    ///
    /// Falha vira conjunto vazio, e o efeito disso é o pior plano possível
    /// (transcodifica tudo) em vez de um plano errado que promete o que o
    /// aparelho não toca. Entre custar CPU e não abrir o filme, custa CPU.
    private fun levantar(): Set<String> = runCatching {
        MediaCodecList(MediaCodecList.REGULAR_CODECS)
            .codecInfos
            .filterNot { it.isEncoder }
            .flatMap { it.supportedTypes.asIterable() }
            .map { it.lowercase() }
            .toSet()
    }.getOrDefault(emptySet())
}
