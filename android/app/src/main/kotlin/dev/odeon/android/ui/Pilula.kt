package dev.odeon.android.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/// As pílulas — o que a web usa em todo lugar onde há um corte a escolher ou um
/// dado curto a mostrar.
///
/// ## Duas, e a diferença entre elas não é visual: é se dá pra tocar
///
/// | | o que é | web | onde |
/// |---|---|---|---|
/// | [PilulaDeFiltro] | uma **escolha**, com estado | `.chip` / `.chip.on` | "tenho 90 min" no para-você |
/// | [PilulaDeEtiqueta] | um **fato** da obra | `.cartaz-chip` | as etiquetas na ficha |
///
/// Separá-las é o §8b e o §18 ao mesmo tempo: uma pílula que parece tocável e
/// não faz nada é o defeito que este projeto persegue desde o começo, e uma
/// etiqueta desenhada igual a um filtro afirma que dá pra filtrar por ela — o
/// que hoje não é verdade em nenhuma tela deste app.

/// Uma escolha, ligada ou desligada. `.chip` da web (`styles.css:1101`).
///
/// ## O que ela substitui
///
/// O filtro de tempo do "para você" era três `TextButton` soltos, e o único
/// sinal de qual valia era a **cor da letra**. A `.chip.on` da web muda três
/// coisas de uma vez — borda, fundo e letra —, e é o que faz o corte escolhido
/// ser lido de relance em vez de procurado.
///
/// ⚠️ **A primeira versão disto era dourado sólido com letra escura**, e estava
/// errada: mais forte que a web, sem nenhum número que justificasse divergir.
/// Os valores abaixo saem da folha, medidos:
///
/// | | `.chip` | `.chip.on` |
/// |---|---|---|
/// | fundo | `--bg-raised` | `--accent` a **8%** |
/// | borda | 1px `--line` | `--accent` |
/// | letra | `--fg-muted` | `--accent` |
/// | forma | 999px, padding 6/13, 12px | idem |
///
/// ## A única divergência, e ela tem motivo
///
/// `minimumInteractiveComponentSize()`. O padding de 6px da web dá uma pílula de
/// ~30dp de altura, que é alvo de **mouse**. No dedo o mínimo do Material é
/// 48dp, e o modificador estende a área de toque **sem** engordar o que é
/// desenhado — a pílula continua do tamanho da web, e o toque para de errar.
@Composable
fun PilulaDeFiltro(
    texto: String,
    selecionada: Boolean,
    aoTocar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val forma = RoundedCornerShape(percent = 50)
    Text(
        text = texto,
        style = Tipo.pilula,
        color = if (selecionada) Cores.destaque else Cores.textoApagado,
        modifier = modifier
            .minimumInteractiveComponentSize()
            .clip(forma)
            /// Selecionada, ela **acende por baixo** em vez de só ganhar um
            /// fundo a 8%. É o mesmo alfa da `.chip.on` da web somado a um
            /// radial vindo da base — a diferença entre "está marcada" e "está
            /// ligada". Ver `Luz.porBaixo`.
            .background(Cores.fundoElevado)
            .acendePorBaixo(selecionada)
            .border(
                BorderStroke(1.dp, if (selecionada) Cores.destaque else Cores.linha),
                forma,
            )
            /// `Role.Tab` e não `Role.Button`: pro leitor de tela estas pílulas
            /// são uma escolha entre alternativas exclusivas, e é o que faz ele
            /// anunciar "selecionado" em vez de só ler o rótulo.
            .clickable(role = Role.Tab, onClick = aoTocar)
            .padding(horizontal = 13.dp, vertical = 6.dp),
    )
}

/// Uma etiqueta da obra. Não se toca, e por isso não tem fundo.
///
/// ## Ela mostra o namespace **e** o valor, e isso é da web
///
/// O `Details.tsx:860` desenha `{t.namespace}<b>{t.value}</b>` — "genero
/// **Crime**", "pais **Estados Unidos**". A hierarquia é a informação: o
/// namespace apagado diz *de que tipo* é a etiqueta, e o valor em destaque é o
/// que se lê.
///
/// Desenhar só o valor economizaria espaço e perderia isso — "Crime" e
/// "Estados Unidos" lado a lado, sem qualificador, viram duas palavras soltas.
///
/// ## A cor tinge a **borda**, e não o fundo
///
/// É o que a web faz (`style={{ borderColor: t.color }}`), e é a escolha certa
/// pelo §18: a borda é enfeite, o fundo seria destaque. Uma etiqueta com fundo
/// colorido no meio de outras sem pareceria mais importante que as outras — e
/// `color` no servidor não quer dizer importância, quer dizer que alguém pintou
/// aquela tag.
///
/// Nulo é o caso comum, e cai na linha da casa. **Não há sorteio de cor.**
@Composable
fun PilulaDeEtiqueta(
    namespace: String,
    valor: String,
    modifier: Modifier = Modifier,
    cor: Color? = null,
) {
    val forma = RoundedCornerShape(percent = 50)
    Row(
        modifier = modifier
            .clip(forma)
            .border(BorderStroke(1.dp, cor ?: Cores.linha), forma)
            .padding(horizontal = 12.dp, vertical = 5.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(namespace, style = Tipo.pilula, color = Cores.textoApagado)
        Text(
            text = valor,
            style = Tipo.pilula.copy(fontWeight = FontWeight.SemiBold),
            color = Cores.texto,
        )
    }
}
