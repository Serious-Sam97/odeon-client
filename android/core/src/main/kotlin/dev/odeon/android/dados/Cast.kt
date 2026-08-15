package dev.odeon.android.dados

/// O que o Cast precisa saber **antes** de existir um botão de Cast.
///
/// Este arquivo é lógica pura de propósito: é a única parte da fase 4 que dá pra
/// provar sem um Chromecast na mesa. O resto — descobrir o aparelho, conectar,
/// mandar a mídia — só se verifica na rede de casa, e a §4c já avisa que é
/// assim.

/// Um endereço que o Chromecast **não** consegue alcançar.
///
/// ## O problema que a §4c descreve, e que este projeto tem hoje
///
/// Quem busca o vídeo é o Chromecast, não o celular: o app manda uma URL e vira
/// controle remoto. E o Chromecast **não entra numa tailnet** — ele fala pela
/// rede de casa e mais nada.
///
/// Ou seja: com o servidor cadastrado como `100.77.253.18:8085`, o app mandaria
/// pra TV um endereço que só existe dentro da tailnet. A TV tentaria, não
/// chegaria, e o sintoma seria uma tela preta sem explicação — §8b em cima de um
/// aparelho onde não há nem onde mostrar erro.
///
/// Por isso o Cast pergunta isto antes de se oferecer. O §53 manda: o produto
/// não oferece o que a validação vai negar.
object EnderecoParaCast {

    /// A faixa que o Tailscale usa: `100.64.0.0/10`.
    ///
    /// É o CGNAT do RFC 6598, e o Tailscale reserva ela inteira pros nós da
    /// tailnet. Um endereço aí dentro é, por definição, alcançável só por quem
    /// está na tailnet — e o Chromecast nunca está.
    ///
    /// A faixa vai de `100.64.x.x` a `100.127.x.x`. Reparar que **`100.128`
    /// não é** é o detalhe que um teste pega e um olho não: `/10` são os
    /// primeiros dez bits, não os primeiros oito.
    fun eDaTailnet(host: String): Boolean {
        val partes = host.split(".")
        if (partes.size != 4) return false
        val primeiro = partes[0].toIntOrNull() ?: return false
        val segundo = partes[1].toIntOrNull() ?: return false
        if (partes[2].toIntOrNull() == null || partes[3].toIntOrNull() == null) return false
        return primeiro == 100 && segundo in 64..127
    }

    /// O host de uma base como `http://100.77.253.18:8085`.
    fun hostDe(base: String): String? = runCatching {
        java.net.URI(base).host
    }.getOrNull()

    /// Dá pra mandar este servidor pra TV?
    ///
    /// `null` de base é "ainda não sei", e aí a resposta é **não** — oferecer
    /// Cast sem saber o endereço seria adivinhar.
    fun alcancavelPelaTv(base: String?): Boolean {
        val host = base?.let { hostDe(it) } ?: return false
        return !eDaTailnet(host)
    }
}

/// O que o Chromecast toca — e não o que este celular toca.
///
/// ## O sujeito da negociação muda, e é a parte que mais engana
///
/// `/api/playback/{id}/plan` recebe `video_codecs` e `audio_codecs` **do
/// cliente**, e o §M6 inteiro do servidor assume que *quem pergunta é quem
/// toca*. Com Cast isso deixa de ser verdade: quem toca é a TV.
///
/// A rota já aceita a lista como parâmetro, então o conserto é o app mandar o
/// perfil **do aparelho de Cast** em vez do próprio. Nenhuma linha de servidor —
/// é o que a §4c chama de "não custa backend".
///
/// Mandar o perfil do celular por engano seria pedir Direct Play de um HEVC que
/// a TV não abre, e o defeito apareceria como tela preta na sala.
///
/// ## Os números que justificam o perfil conservador
///
/// Medido sobre os 17.930 arquivos do acervo (§4c):
///
/// | codec | arquivos | Chromecast |
/// |---|---|---|
/// | h264 | **9.550** | toca |
/// | hevc | 5.345 | só nos 4K / Google TV |
/// | mpeg4 (DivX/Xvid) | 2.335 | **não toca** |
/// | av1 | 461 | só nos mais novos |
///
/// Ou seja **~53% em Direct Play e ~47% por transcode**, e cada transcode é
/// trabalho do servidor de casa. Por isso o perfil não é uma lista otimista: um
/// `hevc` declarado a mais num Chromecast antigo troca "transcode que funciona"
/// por "tela preta", e o segundo é muito pior que o primeiro.
object PerfilDeCast {

    /// O denominador comum de todo Chromecast em linha.
    ///
    /// H.264 e VP8 tocam em **todos** eles desde a primeira geração. HEVC, VP9 e
    /// AV1 dependem do modelo, e o SDK não conta com precisão qual é — então
    /// eles ficam de fora até haver como perguntar ao aparelho.
    ///
    /// Ficar de fora significa transcodificar; declarar a mais significa não
    /// tocar. Entre gastar CPU do servidor e não abrir o filme, gasta CPU.
    const val VIDEO = "h264,vp8"

    /// AAC e MP3 em todos. **AC3 e EAC3 ficam de fora** e é a mesma escolha
    /// conservadora: eles dependem de o aparelho de saída aceitar passagem, e
    /// isso muda com a TV, o receiver e o cabo. É, aliás, o mesmo codec que fez
    /// este emulador cair em transcodificação.
    const val AUDIO = "aac,mp3"

    /// Chromecast lê MP4 e WebM, e **não** lê Matroska — que é justamente onde o
    /// app ganha `direct_play` quando toca no próprio celular. Uma diferença
    /// concreta entre o perfil do celular e o da TV.
    const val CONTAINERS = "mp4,webm"

    /// HLS é como o servidor entrega quando não é direto, e o Chromecast fala.
    const val SUPORTA_HLS = "true"
}
