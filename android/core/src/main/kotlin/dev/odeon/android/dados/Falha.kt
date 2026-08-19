package dev.odeon.android.dados

import retrofit2.HttpException
import java.io.IOException

/// Como uma falha **se lê**.
///
/// ## ⚠️ Ela existe porque metade das telas mostrava o inglês do OkHttp
///
/// A `ModeloDaBiblioteca` já classificava direito — 401 vira «a sessão expirou»,
/// outro código vira «o servidor respondeu 502», e `IOException` vira «sem
/// resposta do servidor». O resto do app não: quem tratava fazia
/// `e.message ?: "alguma frase"`, e `e.message` de uma falha de rede é isto,
/// medido na locadora com a rede desligada em 17/08/2026:
///
/// ```
/// Unable to resolve host "odeon-api.serious-sam.dev": No address associated with hostname
/// ```
///
/// Inglês, vocabulário de resolvedor de DNS, e o endereço do servidor no meio da
/// tela. Não é uma frase para uma pessoa — é o `strerror` do sistema vazando
/// pela interface.
///
/// ## Por que uma função e não uma frase por tela
///
/// Porque a pergunta «o que houve?» tem as mesmas quatro respostas em toda tela,
/// e escrevê-las cinco vezes garante que as cinco divirjam. É o mesmo argumento
/// do `nomeDoIdioma` e do `Etiqueta.rotulo`: **traduzir código em frase é
/// desenho, e desenho que se repete se contradiz**.
///
/// ⚠️ O que ela **não** faz é inventar causa. Uma exceção que não é HTTP nem de
/// entrada e saída cai na frase genérica, e não numa adivinhação simpática —
/// «verifique sua conexão» sobre um `NullPointerException` seria mandar a pessoa
/// consertar a casa dela por causa de um defeito nosso (§18).
fun fraseDaFalha(e: Throwable, generica: String = "não deu certo"): String = when {
    e is HttpException && e.code() == 401 -> "a sessão expirou — entre de novo"
    e is HttpException -> "o servidor respondeu ${e.code()}"
    /// ⚠️ `IOException` cobre host que não resolve, tempo esgotado e conexão
    /// recusada — três causas com **uma** consequência para quem olha: a
    /// pergunta não chegou. Separá-las diria à pessoa qual camada de rede falhou,
    /// que é informação de quem administra o servidor, não de quem quer um filme.
    e is IOException -> "sem resposta do servidor"
    else -> generica
}
