package dev.odeon.android.ui.aovivo

import dev.odeon.android.dados.CanalNoAr
import dev.odeon.android.dados.GradeDoOdeon
import dev.odeon.android.dados.RepositorioOdeon

/// O que está no ar num canal — a peça que a sintonia, a grade e o **fim do
/// filme** compartilham.
///
/// ## ⚠️ Por que isto mora no `:core` e não na tela
///
/// Ela nasceu privada dentro do `TelaAoVivoDaTv`, e ficou errada no dia em que o
/// player precisou da mesma resposta. Quando o arquivo acaba num canal, alguém
/// tem de perguntar «e agora, o que está passando aqui?» — e a resposta tem de
/// ser **a mesma** que a grade desenha, senão a tela e o que toca discordam sobre
/// o que é o canal.
///
data class QuadroNoAr(
    val canalId: String,
    val canalNome: String,
    val numero: String,
    val titulo: String,
    val ano: Int?,
    val categoria: String?,
    val arte: String?,
    val obraId: String?,
    val arquivoId: String?,
    val comecaMs: Long,
    val terminaMs: Long,
    /// O `programme_id` do guia — o que casa com um lembrete. `null` nos canais
    /// do Odeon, que não têm EPG externo e por isso não têm o que lembrar.
    val programaId: Int? = null,
    /// O que entra depois deste, quando o servidor sabe.
    val aSeguir: String? = null,
    /// O logo do canal, quando existe. Os do Odeon não têm — e aí é o nome.
    val logo: String? = null,
)

/// O que está no ar em cada canal do Odeon, agora.
///
/// ⚠️ «no ar» é `comeca <= agora < termina`, e o `<` no fim importa: no segundo
/// exato da virada, dois programas seriam elegíveis, e a tela piscaria entre os
/// dois uma vez por filme.
fun emCartaz(
    agoraMs: Long,
    doOdeon: GradeDoOdeon?,
    /// ⚠️ **Os canais de fonte externa, e eles faltavam** — relatado pelo dono:
    /// «cadê os outros canais fora os odeons? videoteca, canal da Disney etc».
    ///
    /// Eles estavam sendo **buscados** (`/api/live/channels`) e guardados no
    /// estado desde o primeiro dia, e a tela desenhava só a grade do Odeon. Ou
    /// seja: o dado chegava, ninguém olhava.
    ///
    /// É o defeito mais silencioso que existe — não há erro, não há tela vazia,
    /// só um pedaço do mundo que o app decidiu não mostrar sem dizer a ninguém.
    /// Um `logcat` limpo e um build verde o escondem perfeitamente.
    externos: List<CanalNoAr> = emptyList(),
): List<QuadroNoAr> {
    if (agoraMs <= 0) return emptyList()

    /// ⚠️ **Os do Odeon vêm primeiro**, e é a mesma ordem da web: são os que a
    /// casa programa, os únicos com obra e arquivo atrás, e portanto os únicos em
    /// que «sintonizar» leva a algum lugar hoje.
    val daCasa = doOdeon?.canais.orEmpty().mapNotNull { canal ->
        doOdeon?.programas.orEmpty()
            .filter { it.canal == canal.slug }
            .firstOrNull { p ->
                val i = emMillis(p.comeca)
                val f = emMillis(p.termina)
                i > 0 && f > 0 && agoraMs >= i && agoraMs < f
            }
            ?.let { p ->
                QuadroNoAr(
                    canalId = canal.slug,
                    canalNome = canal.nome,
                    numero = canal.numero,
                    titulo = p.title,
                    ano = p.year,
                    categoria = p.categoria,
                    arte = p.arte,
                    obraId = p.obraId,
                    arquivoId = p.arquivoId,
                    comecaMs = emMillis(p.comeca),
                    terminaMs = emMillis(p.termina),
                )
            }
    }

    /// ⚠️ Canal sem EPG **entra assim mesmo**, com o título vazio virando «sem
    /// programação». Ele existe e está no ar; o que não se sabe é o que está
    /// passando — e as duas coisas são diferentes (§18). Escondê-lo faria a
    /// sintonia mentir sobre quantos canais a casa tem.
    val deFora = externos.map { c ->
        QuadroNoAr(
            canalId = c.id,
            canalNome = c.name,
            numero = c.number ?: "—",
            titulo = c.titulo ?: "sem programação",
            ano = null,
            categoria = c.grupo,
            arte = c.arte,
            obraId = c.obraId,
            arquivoId = c.arquivoId,
            comecaMs = c.comeca?.let { emMillis(it) } ?: 0L,
            terminaMs = c.termina?.let { emMillis(it) } ?: 0L,
            programaId = c.programaId,
            aSeguir = c.aSeguir,
            logo = c.logo,
        )
    }

    return daCasa + deFora
}

/// O que está passando **neste canal, agora** — perguntado de novo ao servidor.
///
/// ## ⚠️ Isto é o que transforma «tocar um arquivo» em «ficar num canal»
///
/// Quando o filme acaba, a única pergunta honesta é a que uma televisão faz:
/// *o que está no ar agora?* — e não *qual era o próximo da lista quando eu
/// liguei*. As duas dão respostas diferentes sempre que o filme e a faixa da
/// grade não terminam no mesmo segundo, que é o caso normal: o arquivo tem a
/// duração que tem, e a grade arredonda.
///
/// Por isso a grade é **buscada de novo** em vez de reaproveitada da memória. O
/// app pode ter ficado horas no mesmo filme; a grade que ele carregou ao abrir a
/// tela é história, não notícia.
///
/// ⚠️ **O relógio é o do servidor**, tirado da resposta que acabou de chegar —
/// o mesmo cuidado do `ModeloAoVivo`. Perguntar «o que está no ar» com o relógio
/// da TV, que pode estar minutos fora, escolheria o programa errado exatamente
/// nas viradas, que é quando esta função é chamada.
suspend fun oQueEstaNoArAgora(odeon: RepositorioOdeon, canalId: String): SintoniaAgora? {
    val doOdeon = runCatching { odeon.gradeDoOdeon() }.getOrNull()
    val canais = runCatching { odeon.canaisAoVivo() }.getOrNull().orEmpty()

    val marca = doOdeon?.agora
    val agoraMs = marca?.let { emMillis(it) }?.takeIf { it > 0 }
        ?: System.currentTimeMillis()

    val quadro = emCartaz(agoraMs, doOdeon, canais).firstOrNull { it.canalId == canalId }
        ?: return null

    return SintoniaAgora(quadro, quantoJaPassou(agoraMs, quadro))
}

/// O programa **e o ponto dele**, juntos.
///
/// ⚠️ Os dois voltam no mesmo objeto porque o ponto só é calculável com o
/// relógio que escolheu o programa. Devolver só o quadro obrigaria quem chamou a
/// perguntar as horas de novo — e a única fonte que ele tem é o relógio da TV,
/// que é justamente o que esta função existe pra não usar.
data class SintoniaAgora(val quadro: QuadroNoAr, val comecarEm: Double)

/// Quantos segundos do programa já correram — o ponto onde a transmissão está.
///
/// ⚠️ `coerceAtLeast(0)` porque um canal externo sem EPG chega com `comecaMs = 0`
/// (ver o `emCartaz`), e sem o piso isso viraria um deslocamento de cinquenta e
/// tantos anos em segundos.
fun quantoJaPassou(agoraMs: Long, q: QuadroNoAr): Double =
    ((agoraMs - q.comecaMs).coerceAtLeast(0L) / 1000.0)
