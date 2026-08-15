package dev.odeon.android.dados

/// Normalização do endereço do servidor.
///
/// ## É um porte, e a origem está dita
///
/// Isto é o `ServerUrl.kt` de `clients/shared`, traduzido pro idioma deste
/// projeto. **É a terceira cópia do mesmo conhecimento** — a primeira é a do
/// KMP, a segunda é a que a web faz à mão em `web/src/api.ts`.
///
/// A espec assumiu esse custo quando decidiu o app nativo (§1), e é por isso que
/// ele mora dentro do `odeon-client` e não em repositório próprio: num
/// repositório só, um `grep` alcança as três.
///
/// ## O que ele resolve
///
/// O que a pessoa digita é `rog` ou `10.0.2.2`, não `https://rog:8443/`. Exigir
/// o formato exato é o tipo de atrito que faz alguém achar que o servidor está
/// fora.
///
/// E "qual esquema?" não deveria ser pergunta: tenta **https primeiro** e cai
/// pra http se ninguém responder. Quem sabe qual dos dois está ligado é o
/// servidor, não quem digita.
object EnderecoDoServidor {

    /// As portas padrão do Odeon quando não vem uma.
    const val PORTA_HTTPS = 8443
    const val PORTA_HTTP = 8080

    /// Limpa e completa o que foi digitado. `null` quando não sobra host.
    ///
    /// Preserva o esquema se veio um; não inventa porta se veio uma.
    fun normalizar(digitado: String): String? {
        val limpo = digitado.trim()
        if (limpo.isEmpty()) return null

        // O esquema sai ANTES de qualquer limpeza de barra — senão o `//` dele
        // é confundido com barra sobrando e `https://` vira `https:`.
        val esquema = when {
            limpo.startsWith("https://") -> "https://"
            limpo.startsWith("http://") -> "http://"
            else -> null
        }
        val resto = esquema?.let { limpo.removePrefix(it) } ?: limpo

        // Só o host importa; caminho digitado por engano é descartado.
        val hostPorta = resto.substringBefore('/').trim()
        if (hostPorta.isEmpty()) return null

        // Um host precisa ter alguma coisa além de pontuação.
        if (hostPorta.none { it.isLetterOrDigit() }) return null

        return (esquema ?: "") + hostPorta
    }

    /// Endereços a tentar, em ordem.
    ///
    /// Com esquema explícito, respeita a escolha e não tenta o outro — se a
    /// pessoa escreveu `http://`, tentar https por baixo seria surpresa.
    fun candidatos(digitado: String): List<String> {
        val normal = normalizar(digitado) ?: return emptyList()

        if (normal.startsWith("http://") || normal.startsWith("https://")) {
            return listOf(normal)
        }

        val temPorta = normal.substringAfterLast(':', "").toIntOrNull() != null

        return if (temPorta) {
            // Porta explícita sem esquema: pode ser qualquer um dos dois.
            listOf("https://$normal", "http://$normal")
        } else {
            listOf(
                "https://$normal:$PORTA_HTTPS",
                "http://$normal:$PORTA_HTTP",
            )
        }
    }

    fun eSeguro(url: String): Boolean = url.startsWith("https://")
}
