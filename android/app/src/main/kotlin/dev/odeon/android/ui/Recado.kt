package dev.odeon.android.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/// O que a tela diz quando não deu — título, motivo, e o que fazer agora.
///
/// ## Por que ele mora aqui, e não dentro de uma tela
///
/// Nasceu dentro do `TelaDaSerie`, e ficou lá enquanto só a série e a temporada
/// o usavam. Mudou de casa em 19/08/2026, quando o **ao vivo** precisou dizer «a
/// transmissão parou»: uma peça que três pacotes desenham não pode ser um detalhe
/// privado de um deles — a alternativa era o `aovivo` importar de `serie`, que
/// deixaria a leitura do código sugerindo um parentesco que não existe.
///
/// ⚠️ **`aoTentar` é anulável de propósito.** Há erro que não tem segunda chance
/// — «esta série não tem episódios» não melhora por insistir —, e um botão
/// «tentar de novo» que não pode dar certo é pior que nenhum: ele empurra a culpa
/// pra quem está lendo (§18).
@Composable
internal fun Recado(
    titulo: String,
    detalhe: String?,
    aoTentar: (() -> Unit)?,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier.fillMaxSize().background(Cores.fundo).padding(28.dp),
        verticalArrangement = Arrangement.Center,
    ) {
        Text(titulo, style = MaterialTheme.typography.titleMedium, color = Cores.texto)
        detalhe?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, style = MaterialTheme.typography.bodyMedium, color = Cores.textoApagado)
        }
        Spacer(Modifier.height(12.dp))
        Row {
            aoTentar?.let { TextButton(onClick = it) { Text("tentar de novo", color = Cores.destaque) } }
            TextButton(onClick = aoVoltar) { Text("voltar", color = Cores.textoApagado) }
        }
    }
}
