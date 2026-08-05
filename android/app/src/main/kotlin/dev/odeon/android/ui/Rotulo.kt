package dev.odeon.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/// O cabeçalho de seção: versalete espaçado, régua até a margem, número à
/// direita quando houver um.
///
/// ```
/// CONTINUAR ────────────────────────────────────────
/// BAIXADOS ──────────────────────────────────────  3
/// FRANQUIAS ───────────────────────────────────  133
/// ```
///
/// ## O que ele substitui, e por que isso muda a tela
///
/// Antes cada seção era um `Text(…, style = titleMedium)`. Funciona, e não diz
/// nada: três blocos de texto em sequência são lidos como três listas soltas.
/// O que faz uma página ter **seções** é a régua, não o tamanho da letra — é o
/// que dá à web o ar de programa impresso, e era a diferença mais barata de
/// copiar que este app ainda não tinha.
///
/// ## Tudo aqui é medido no `.strip` da web
///
/// | | `styles.css` |
/// |---|---|
/// | rótulo | `:2049` — 11px, peso 700, `letter-spacing: 0.28em`, caixa alta, `--accent` |
/// | régua | `:2059` — 1px, **gradiente** de `--accent-dim` até transparente |
/// | vão | `--s4`, 18px |
///
/// **O gradiente não é enfeite gratuito**, e é o único item desta lista que dá
/// vontade de simplificar pra uma linha chapada. Ele é o que faz a régua sair
/// do rótulo e **morrer** na margem em vez de bater nela — uma linha sólida de
/// borda a borda lê como divisor de lista, que é exatamente a coisa de que esta
/// tela está fugindo.
///
/// ## O número, e a regra que ele obedece
///
/// ⚠️ §24: **linha vazia some, não vira "—"**. O número é `Int?` e não `Int`
/// com sentinela: quando a contagem ainda não chegou do servidor, ele não
/// desenha. Escrever "0" enquanto se espera é afirmar que a seção está vazia —
/// §18, não mentir com cara de metadado.
///
/// ## O contraste, medido
///
/// `--accent` (`#E0B062`) sobre `--bg` (`#0A0A0C`) dá **9,94:1**, folgado no AAA
/// mesmo pros 11sp deste rótulo, que é texto pequeno e pediria 4,5:1.
///
/// Vale registrar por que a medição foi feita: a web tem **outro** rótulo, o
/// `.player-tech` (`:2180`), que usa `--accent-dim` (`#8A6A3A`) e dá **3,96:1**
/// — reprovado no AA pro tamanho dele. Copiar "o rótulo da web" sem olhar qual
/// era teria trazido junto um defeito de contraste.
@Composable
fun RotuloDeSecao(
    texto: String,
    modifier: Modifier = Modifier,
    numero: Int? = null,
) {
    Row(
        modifier = modifier.padding(top = 24.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Text(
            /// A caixa alta é aplicada aqui, e não no `Tipo.rotulo`.
            ///
            /// `TextStyle` não tem `text-transform`, e é bom que não tenha: o
            /// texto continua escrito em minúscula no código, como todo o resto
            /// deste app, e quem lê a chamada vê `RotuloDeSecao("continuar")` em
            /// vez de um `"CONTINUAR"` gritado no meio do Kotlin.
            text = texto.uppercase(),
            style = Tipo.rotulo,
            color = Cores.destaque,
        )

        /// A régua.
        ///
        /// `weight(1f)` é o `flex: 1` da web — ela come o que sobrar entre o
        /// rótulo e o número. Com `numero` nulo ela vai até a margem; com número,
        /// para antes dele.
        ///
        /// 1.dp e não 1.px: numa tela de 3x isso vira 3 pixels físicos, que é o
        /// que a web também desenha ao dizer `1px` num aparelho de 3x.
        Spacer(
            modifier = Modifier
                .weight(1f)
                .height(1.dp)
                .background(
                    Brush.horizontalGradient(
                        listOf(Cores.destaqueApagado, Color.Transparent),
                    ),
                ),
        )

        /// Sem número, **nada** é emitido aqui — nem um filho vazio de largura
        /// zero. O `spacedBy(18.dp)` só separa filhos que existem, então um
        /// `Spacer(0.dp)` neste lugar não seria neutro: ele custaria os mesmos
        /// 18dp de vão e a régua morreria 18dp antes da margem.
        if (numero != null) {
            Text(
                text = "$numero",
                /// ⚠️ **Sem o tracking do rótulo**, e foi um screenshot que
                /// mandou tirar.
                ///
                /// `Tipo.rotulo` tem letra espaçada, que é o que faz
                /// `TEMPORADA 2` respirar em caixa alta. Aplicado a um número de
                /// dois dígitos, o mesmo espaçamento separa os algarismos: a
                /// segunda temporada de *Breaking Bad* dizia **`1 3`** em vez de
                /// `13`, e a leitura de relance era «uma temporada, três
                /// episódios».
                ///
                /// Letra espaçada é regra de texto. Número não é texto — é
                /// quantidade, e quantidade se lê junta.
                style = Tipo.rotulo.copy(letterSpacing = 0.sp),
                color = Cores.textoApagado,
            )
        }
    }
}
