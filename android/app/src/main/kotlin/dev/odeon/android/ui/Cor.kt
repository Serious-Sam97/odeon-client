package dev.odeon.android.ui

import androidx.compose.ui.graphics.Color
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

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

/// Uma cor em OKLCh, como a folha de estilo da web escreve.
///
/// ## Por que a conversão vive aqui, e não uma tabela de cores prontas
///
/// A marca desenhada do avatar (§10.6) tem a cor derivada do nome por hash, e a
/// web a escreve em `oklch(0.32 0.06 var(--hue))` — 360 tons possíveis, um por
/// grau. Passar isso pra cá como tabela seria 360 hexadecimais colados à mão,
/// que envelhecem no dia em que alguém mexer no `L` ou no `C` da folha.
///
/// Com a conversão, o que se copia é **a fórmula da folha**: os mesmos três
/// números do CSS entram aqui e saem na mesma cor. A pessoa `rudney` fica com o
/// mesmo verde no navegador e no celular, que é o ponto inteiro de uma marca
/// derivada — se ela mudar de cor entre os dois clientes, ela deixa de
/// identificar alguém e vira enfeite.
///
/// O caminho é o padrão: OKLCh → OKLab → LMS → sRGB linear → gama.
fun corOklch(l: Float, c: Float, hGraus: Float): Color {
    val h = Math.toRadians(hGraus.toDouble())
    val a = c * cos(h).toFloat()
    val b = c * sin(h).toFloat()

    val lLinha = l + 0.3963377774f * a + 0.2158037573f * b
    val mLinha = l - 0.1055613458f * a - 0.0638541728f * b
    val sLinha = l - 0.0894841775f * a - 1.2914855480f * b

    val lCubo = lLinha * lLinha * lLinha
    val mCubo = mLinha * mLinha * mLinha
    val sCubo = sLinha * sLinha * sLinha

    return Color(
        red = gama(4.0767416621f * lCubo - 3.3077115913f * mCubo + 0.2309699292f * sCubo),
        green = gama(-1.2684380046f * lCubo + 2.6097574011f * mCubo - 0.3413193965f * sCubo),
        blue = gama(-0.0041960863f * lCubo - 0.7034186147f * mCubo + 1.7076147010f * sCubo),
    )
}

/// A curva de transferência do sRGB, e o recorte pro que não cabe no gamute.
///
/// O recorte é o mesmo que o navegador faz: um OKLCh muito saturado não tem
/// equivalente em sRGB, e o que sai é a cor mais próxima que a tela consegue.
private fun gama(linear: Float): Float {
    val v = linear.coerceIn(0f, 1f)
    return if (v <= 0.0031308f) 12.92f * v else 1.055f * v.pow(1f / 2.4f) - 0.055f
}
