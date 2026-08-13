package dev.odeon.android.dados

/// O relógio que anda pra frente e não pra trás.
///
/// ## O problema, na frase da espec
///
/// «O celular decide pela **hora dele**, e a hora dele é editável. Atrasar o
/// relógio estenderia o empréstimo.»
///
/// Um download da locadora carrega o `vence_em` e para de tocar quando ele
/// passa — mesmo offline, mesmo sem nunca mais falar com o servidor. Isso é o
/// que faz o prazo existir sem rede. E é exatamente o que um relógio atrasado
/// desfaz.
///
/// ## A proposta da §4, implementada
///
/// Guardar o **maior instante já visto** — do relógio do aparelho e de todo
/// cabeçalho `Date` que o servidor mandou. Se a hora atual for menor que esse
/// máximo, vale o máximo. O relógio pode andar pra frente à vontade; pra trás,
/// não anda.
///
/// ## O que isto NÃO é
///
/// Não é criptografia, e não pretende ser: quem quer burlar o próprio servidor
/// de casa consegue de dez jeitos. É o suficiente pra o **acidente** — fuso
/// trocado, relógio errado depois de bateria zerada — não virar nem bloqueio
/// nem brecha.
object RelogioQueNaoVolta {

    /// A hora que vale, dado o relógio e o maior instante já registrado.
    ///
    /// Pura de propósito: quem guarda o máximo é o `Cofre`, quem decide é isto,
    /// e o que decide sem estado tem teste.
    fun agora(doAparelho: Long, maiorJaVisto: Long): Long = maxOf(doAparelho, maiorJaVisto)

    /// O novo máximo, depois de ver um instante.
    ///
    /// Serve pros dois lados: o tique do próprio relógio e o `Date` que veio no
    /// cabeçalho de uma resposta do servidor. O segundo é o que corrige um
    /// aparelho que nasceu com a hora errada — e é de graça, porque toda
    /// resposta HTTP traz um.
    fun maiorDepoisDeVer(maiorJaVisto: Long, visto: Long): Long = maxOf(maiorJaVisto, visto)

    /// A fita venceu?
    ///
    /// `venceEm` nulo é "não vence" — é o caso de todo download que veio pela
    /// **biblioteca**, que é modo livre desde o §71. Ver `OrigemDoDownload`.
    fun venceu(venceEm: Long?, doAparelho: Long, maiorJaVisto: Long): Boolean {
        val prazo = venceEm ?: return false
        return agora(doAparelho, maiorJaVisto) >= prazo
    }
}

/// De onde o download veio — e é isto que decide se ele expira.
///
/// ## A decisão, e por que ela mudou depois do §71
///
/// A §4 dizia que o prazo viajava com o arquivo, ponto. Mas o §71 tornou a
/// biblioteca **modo livre**: quem toca qualquer coisa online sem empréstimo
/// não pode receber um arquivo baixado que trava — seria o app mais restrito
/// offline do que online, e ninguém pede um aparelho que bloqueia o que a rede
/// libera.
///
/// O documento deixou três caminhos e o dono escolheu o segundo: **o prazo vale
/// pra quem baixou pela locadora**, e não pra quem baixou pela biblioteca.
///
/// O app sabe de onde veio mesmo que o servidor não saiba — é uma informação que
/// só existe aqui, e é por isso que ela é gravada junto com o arquivo em vez de
/// perguntada depois.
enum class OrigemDoDownload {
    /// Modo livre (§71). **Não expira.**
    BIBLIOTECA,

    /// Veio com fita pega. Expira no `vence_em`, mesmo offline.
    LOCADORA,
    ;

    val expira: Boolean get() = this == LOCADORA
}
