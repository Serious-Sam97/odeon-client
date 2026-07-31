package dev.odeon.shared

/**
 * Normalização do endereço do servidor.
 *
 * O que o usuário digita num controle de TV é `rog:8443`, não
 * `https://rog:8443/`. Exigir o formato exato é o tipo de atrito que faz a
 * pessoa achar que o servidor está fora.
 *
 * E "qual esquema?" não deveria ser pergunta: o app tenta **https primeiro** e
 * cai pra http se ninguém responder. Quem sabe qual dos dois está ligado é o
 * servidor, não quem digita.
 */
object ServerUrl {

    /** Porta padrão do HTTPS do Odeon quando o usuário não informa uma. */
    const val DEFAULT_HTTPS_PORT = 8443
    const val DEFAULT_HTTP_PORT = 8080

    /**
     * Limpa e completa o que foi digitado. `null` quando não sobra host.
     *
     * Preserva o esquema se veio um; não inventa porta se veio uma.
     */
    fun normalize(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null

        // O esquema sai ANTES de qualquer limpeza de barra — senão o `//` dele
        // é confundido com barra sobrando e "https://" vira "https:".
        val scheme = when {
            trimmed.startsWith("https://") -> "https://"
            trimmed.startsWith("http://") -> "http://"
            else -> null
        }
        val rest = scheme?.let { trimmed.removePrefix(it) } ?: trimmed

        // Só o host importa aqui; caminho digitado por engano é descartado.
        val hostPort = rest.substringBefore('/').trim()
        if (hostPort.isEmpty()) return null

        // Um host precisa ter alguma coisa além de pontuação.
        if (hostPort.none { it.isLetterOrDigit() }) return null

        return (scheme ?: "") + hostPort
    }

    /**
     * Endereços a tentar, em ordem.
     *
     * Com esquema explícito, respeita a escolha e não tenta o outro — se a
     * pessoa escreveu `http://`, tentar https por baixo seria surpresa.
     */
    fun candidates(input: String): List<String> {
        val normalized = normalize(input) ?: return emptyList()

        if (normalized.startsWith("http://") || normalized.startsWith("https://")) {
            return listOf(normalized)
        }

        val hasPort = normalized.substringAfterLast(':', "").toIntOrNull() != null

        return if (hasPort) {
            // Porta explícita sem esquema: pode ser qualquer um dos dois.
            listOf("https://$normalized", "http://$normalized")
        } else {
            // Sem porta nem esquema: as portas padrão do Odeon.
            listOf(
                "https://$normalized:$DEFAULT_HTTPS_PORT",
                "http://$normalized:$DEFAULT_HTTP_PORT",
            )
        }
    }

    fun isSecure(url: String): Boolean = url.startsWith("https://")
}

/**
 * O `<video>`/ExoPlayer também busca por conta própria, então isto vale pra
 * qualquer URL de mídia: página segura não pode carregar recurso inseguro.
 */
fun mixedContentProblem(pageIsSecure: Boolean, serverUrl: String): String? =
    if (pageIsSecure && !ServerUrl.isSecure(serverUrl)) {
        "esta página está em HTTPS e o servidor em HTTP — o navegador bloqueia a " +
            "mistura. Use https:// no endereço, ou abra a interface por HTTP."
    } else {
        null
    }
