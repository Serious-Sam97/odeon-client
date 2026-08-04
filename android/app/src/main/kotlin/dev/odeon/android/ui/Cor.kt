package dev.odeon.android.ui

import androidx.compose.ui.graphics.Color

/// Uma cor `#RRGGBB` vinda do servidor, ou `null`.
///
/// ## Duas coisas do servidor passam por aqui, e são diferentes
///
/// | | quantas têm | o que ela pinta |
/// |---|---|---|
/// | `dominant_color` da obra | **9.332 de 17.930** | o fundo do cartaz sem pôster, e a tinta da tela |
/// | `color` da etiqueta | poucas, e é normal | a **borda** da pílula, nunca o fundo |
///
/// A cor dominante não custa requisição nenhuma: vem na mesma linha da listagem,
/// e é o que faz a grade parecer o acervo antes de a primeira imagem chegar.
///
/// ## O `null` é o caminho normal, não o de erro
///
/// Qualquer coisa que não seja seis dígitos hexadecimais vira `null` em vez de
/// estourar — uma cor inválida não pode derrubar a biblioteca. E quem chama
/// **tem** que ter um caminho pro nulo: 48% do acervo não tem pôster, logo não
/// tem cor extraída dele.
///
/// Estava dentro do `TelaDaBiblioteca` como `corDaObra` até a R3, quando a ficha
/// passou a precisar da mesma conta pras etiquetas. Duas cópias de um parser é
/// como uma delas passa a aceitar `#RGB` e a outra não.
fun corDeHex(hex: String?): Color? {
    val limpo = hex?.removePrefix("#")?.takeIf { it.length == 6 } ?: return null
    val valor = limpo.toLongOrNull(16) ?: return null
    return Color(valor or 0xFF000000L)
}
