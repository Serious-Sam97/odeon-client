package dev.odeon.android.ui.locadora

import android.graphics.Color as CorDoAndroid
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.palette.graphics.Palette
import coil3.BitmapImage
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import dev.odeon.android.ui.Cores
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/// As duas tintas da lombada, tiradas da própria capa.
///
/// ## Por que duas, e por que da capa
///
/// A lombada escolhida pelo dono (a «opção C» de 06/08/2026) é a de gráfica de
/// estúdio: um bloco de cor em cima, outro mais escuro embaixo, divididos pelo
/// fio dourado da casa. O servidor manda **uma** cor (`dominant_color`) — serve
/// pro papelão, não pra uma impressão de duas tintas. As duas saem do bitmap da
/// capa, que o Coil já tem na mão.
///
/// ## Por que a `Palette`, e não uma conta nossa
///
/// Média de pixels devolve marrom em quase todo pôster — foi medido no varal de
/// mockups: a média do Ichabod (um pôster de noite azul e roxa) deu **cinza**.
/// A Palette clusteriza e separa `vibrant` de `muted`, que é exatamente a
/// diferença entre «a cor que o pôster tem» e «a cor que o pôster é». É a
/// biblioteca oficial, estável desde 2014, e a primeira dependência que a
/// locadora adiciona — com o aval explícito do dono.
data class TintasDaCapa(val cima: Color, val baixo: Color)

/// Extrai as tintas da capa, com o fallback da casa enquanto (ou se) não dá.
///
/// ⚠️ **Sem arte, sem rede, ou ainda carregando: as tintas saem da
/// `dominant_color`** — o mesmo lerp do papelão de antes. A lombada nunca fica
/// esperando: ela nasce no fallback e **troca** quando a paleta chega, que é o
/// mesmo contrato do pôster (nasce cor, vira imagem).
///
/// ## ⚠️ `allowHardware(false)`, e não é opcional
///
/// O Coil entrega bitmap de hardware por padrão, e bitmap de hardware **não se
/// lê** — a Palette precisa dos pixels e morre com `IllegalStateException`. O
/// pedido aqui é separado do da tela (96px, sem hardware) e o Coil deduplica
/// pelo cache de disco, então a capa não é baixada duas vezes.
@Composable
fun tintasDaCapa(arte: String?, corDominante: Color?): TintasDaCapa {
    /// O fallback: a dominante clareada em cima, quase preta embaixo — a mesma
    /// família do papelão tingido que a lombada usava antes.
    val base = corDominante ?: Cores.fundoElevado
    val fallback = TintasDaCapa(
        cima = lerp(Color.Black, base, 0.55f),
        baixo = lerp(Color.Black, base, 0.22f),
    )

    var tintas by remember(arte) { mutableStateOf<TintasDaCapa?>(null) }
    val contexto = LocalContext.current

    LaunchedEffect(arte) {
        if (arte == null) return@LaunchedEffect
        val resultado = SingletonImageLoader.get(contexto).execute(
            ImageRequest.Builder(contexto)
                .data(arte)
                .allowHardware(false)
                /// 96px bastam: paleta é estatística de cor, não leitura de
                /// detalhe — e é o que faz a extração custar milissegundos.
                .size(Size(96, 96))
                .build(),
        )
        val bitmap = ((resultado as? SuccessResult)?.image as? BitmapImage)?.bitmap
            ?: return@LaunchedEffect
        val paleta = withContext(Dispatchers.Default) { Palette.from(bitmap).generate() }

        /// A de cima é a mais **viva** que a capa tem; a de baixo, a viva
        /// escura — e uma não pode ser a outra. Sem swatch vivo nenhum (capas
        /// preto-e-branco existem), o fallback fica, que é o certo: inventar
        /// vivacidade num pôster sóbrio seria pintar o que a obra não é.
        val cima = paleta.vibrantSwatch ?: paleta.lightVibrantSwatch ?: paleta.dominantSwatch
        val baixo = paleta.darkVibrantSwatch ?: paleta.darkMutedSwatch
        if (cima != null) {
            tintas = TintasDaCapa(
                cima = temperada(cima.rgb),
                baixo = baixo?.let { temperada(it.rgb, teto = 0.40f) }
                    ?: lerp(Color.Black, temperada(cima.rgb), 0.42f),
            )
        }
    }

    return tintas ?: fallback
}

/// Ajusta uma cor da paleta pra faixa em que texto branco lê sobre ela.
///
/// A Palette devolve a cor **como impressa no pôster** — que pode ser um
/// amarelo de 95% de luz onde branco some, ou um azul de 8% que vira preto. A
/// tempera prende o valor entre um chão e um teto e dá um empurrão de
/// saturação, que é o que a gráfica faria: tinta chapada, não a foto.
private fun temperada(rgb: Int, chao: Float = 0.34f, teto: Float = 0.62f): Color {
    val hsv = FloatArray(3)
    CorDoAndroid.colorToHSV(rgb, hsv)
    hsv[1] = (hsv[1] * 1.25f).coerceAtMost(1f)
    hsv[2] = hsv[2].coerceIn(chao, teto)
    return Color(CorDoAndroid.HSVToColor(hsv))
}
