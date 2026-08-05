package dev.odeon.android.ui

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeParseException
import kotlin.math.ceil

/// Quando a semana vira, em palavra.
///
/// ## Por que isto é uma função só, e não duas frases
///
/// A revista do guia e a vitrine da locadora viram **na mesma segunda-feira** —
/// é literalmente o mesmo instante, e é o que dá assunto em comum (`IDEIAS.md`
/// §2.4). Duas telas dizendo o mesmo instante com palavras diferentes fariam
/// parecer dois relógios; e até agora era pior que isso: a locadora imprimia o
/// `vira_em` cru, como uma data de banco de dados vazando pra tela.
///
/// A gramática é a da web (`Guia.tsx:143`), copiada inteira:
///
/// ```
/// dias <= 1  →  "amanhã"
/// senão      →  "segunda"
/// ```
///
/// "Segunda" sem contar quantos dias faltam é decisão da web e ela está certa:
/// numa casa, "vira segunda" é a informação; "vira em 5 dias" é um cronômetro.
///
/// ## O nulo
///
/// `null` quando o campo não veio ou não parseia — e aí a frase inteira some
/// (§24), em vez de escrever "vira em null" ou chutar uma data. É o mesmo motivo
/// de a função devolver `String?` e não `""`: string vazia ainda desenha um vão.
fun viraQuando(iso: String?, agora: Instant = Instant.now()): String? {
    val quando = instanteDe(iso) ?: return null
    val dias = ceil((quando.toEpochMilli() - agora.toEpochMilli()) / 86_400_000.0)
    return if (dias <= 1) "amanhã" else "segunda"
}

/// O prazo de um empréstimo, em palavra — `3 dias`, `vence amanhã`, `vence
/// hoje`, `venceu`.
///
/// ## Ela some quando não há data, e é o §24
///
/// `vence_em` nulo é empréstimo sem prazo, e escrever «sem prazo» na caixa seria
/// ocupar a cinta de papel com uma não-informação.
///
/// ## Os três dias que mudam de cor são decisão da web
///
/// `vence hoje` e `vence amanhã` saem em vermelho lá (§6 da referência: «vermelho
/// a 2 dias»). Aqui quem pinta é a tela; esta função só devolve a frase e quantos
/// dias faltam, porque cor é assunto de quem desenha.
fun prazoDoEmprestimo(iso: String?, agora: Instant = Instant.now()): Pair<String, Int>? {
    val quando = instanteDe(iso) ?: return null
    val dias = ceil((quando.toEpochMilli() - agora.toEpochMilli()) / 86_400_000.0).toInt()
    val frase = when {
        dias < 0 -> "venceu"
        dias == 0 -> "vence hoje"
        dias == 1 -> "vence amanhã"
        else -> "$dias dias"
    }
    return frase to dias
}

/// Isto aconteceu **hoje**?
///
/// ## O que ela resolve, e por que não é um corte em N
///
/// O balcão listava todas as devoluções que o servidor mandasse — nove, no
/// servidor de casa, e as nove com a mesma cara. A reação óbvia é cortar em três
/// e pôr um «mais 6». Ela mente nos dois sentidos: num dia em que voltaram cinco
/// fitas, esconde duas notícias; num dia parado, promove a histórico o que
/// aconteceu na semana passada.
///
/// O corte certo é o tempo, e ele **já chegava**: `devolvido_em` vem em toda
/// devolução e não era lido por ninguém — nem aqui, nem na web, onde ele só
/// serve de chave de lista. Era o sétimo campo do §8, e o único que não desenhava
/// nem escondia nada: **datava**.
///
/// ## Por que dia local, e não «faz menos de 24h»
///
/// «Hoje» numa casa é o dia do calendário, não uma janela deslizante. Uma fita
/// devolvida às 23h ontem não é notícia de hoje às 8h da manhã, mesmo tendo nove
/// horas de idade — e uma devolvida às 00h10 é, mesmo tendo dez minutos.
///
/// `false` quando a data não veio ou não parseia: sem carimbo, a devolução cai no
/// histórico. É o §18 — o app não promove a notícia o que ele não sabe quando
/// aconteceu.
fun ehDeHoje(iso: String?, agora: Instant = Instant.now()): Boolean {
    val quando = instanteDe(iso) ?: return false
    val fuso = ZoneId.systemDefault()
    return quando.atZone(fuso).toLocalDate() == agora.atZone(fuso).toLocalDate()
}

/// O `vira_em` em instante.
///
/// Duas formas porque o servidor manda as duas conforme a rota: a data seca
/// (`2026-08-10`) e o carimbo completo (`2026-08-10T03:00:00Z`). O `new Date()`
/// da web engole as duas sem que ninguém tenha escrito uma linha pra isso — aqui
/// é preciso escrever, e a ordem importa: `Instant.parse` recusa a data seca.
///
/// A data seca vira **meia-noite local**, e não UTC: a semana que vira é a da
/// casa, não a de Greenwich.
private fun instanteDe(iso: String?): Instant? {
    val texto = iso?.trim().orEmpty()
    if (texto.isEmpty()) return null
    return try {
        Instant.parse(texto)
    } catch (_: DateTimeParseException) {
        try {
            LocalDate.parse(texto).atStartOfDay(ZoneId.systemDefault()).toInstant()
        } catch (_: DateTimeParseException) {
            null
        }
    }
}
