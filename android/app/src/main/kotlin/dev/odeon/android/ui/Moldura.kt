package dev.odeon.android.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/// Como marcar o pôster de uma obra para a transição compartilhada — R7.
///
/// ## Por que não é só um `Modifier`
///
/// A primeira versão passava `moldura: Modifier` pras telas, e o lint reprovou
/// com `ModifierParameter` — três vezes. A regra dele é boa e vale ler o que ela
/// realmente diz: um parâmetro do tipo `Modifier` num composable é, por
/// convenção, **o modificador daquele composable**, chamado `modifier`, primeiro
/// entre os opcionais. Quem lê a assinatura conta com isso.
///
/// E aqui não era isso. O que viaja não é o modificador da tela: é uma
/// instrução sobre **um elemento lá dentro** — o pôster —, que a tela nem sabe
/// para que serve. Renomear pra `modifier` teria calado o lint e mentido na
/// assinatura.
///
/// Um tipo próprio resolve os dois: o lint para de reclamar porque não há mais
/// `Modifier` na assinatura, e quem lê vê o que a coisa é.
///
/// ## Por que a tela recebe isto em vez dos escopos
///
/// O `SharedTransitionScope` e o `AnimatedVisibilityScope` são API experimental
/// de animação, e só existem dentro do `AnimatedContent` do `AppOdeon`. Passá-los
/// adiante faria **toda tela que desenha um pôster** depender deles pra
/// compilar — inclusive nas prévias e nos testes, onde não há transição nenhuma
/// acontecendo.
///
/// O padrão [Nenhuma] é o que torna isso verdade: sem ninguém pra transicionar,
/// a grade desenha exatamente igual.
fun interface MolduraDoCartaz {
    @Composable
    fun de(obraId: String): Modifier

    companion object {
        /// A moldura que não faz nada — o padrão de toda tela.
        val Nenhuma = MolduraDoCartaz { Modifier }
    }
}
