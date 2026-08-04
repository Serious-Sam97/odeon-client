package dev.odeon.android.ui.baixados

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.odeon.android.dados.Baixado
import dev.odeon.android.ui.Cores

/// Os downloads.
///
/// ## O que ela mostra além do progresso
///
/// **Se a fita venceu.** É a única tela do app onde isso aparece, porque é a
/// única onde o arquivo existe sem o servidor por perto — e um filme que não
/// toca precisa dizer por quê antes de alguém achar que quebrou.
///
/// E vencido **não some do disco** (§4): «se você pegar a fita de novo, volta a
/// tocar sem baixar de novo — e um filme de 4 GB rebaixado por causa de um
/// empréstimo que voltou é o tipo de coisa que faz alguém desligar o offline».
/// Quem apaga é quem toca no botão.
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
fun TelaDosBaixados(modelo: ModeloDosBaixados, aoVoltar: () -> Unit) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(onClick = aoVoltar, contentPadding = PaddingValues(0.dp)) {
            Text("‹ biblioteca", color = Cores.destaque)
        }

        Text("baixados", style = MaterialTheme.typography.headlineSmall, color = Cores.texto)

        if (estado.itens.isEmpty()) {
            /// Vazio com frase, e não em branco: "não há nada baixado" é
            /// informação; uma tela muda é dúvida.
            Text(
                text = "nada baixado ainda",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.textoApagado,
            )
            return@Column
        }

        estado.itens.forEach { item ->
            Item(
                item = item,
                venceu = modelo.venceu(item),
                quandoVence = modelo.quandoVence(item),
                aoApagar = { modelo.apagar(item.id) },
            )
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
@Composable
private fun Item(item: Baixado, venceu: Boolean, quandoVence: String?, aoApagar: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Cores.fundoElevado)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = item.ficha.titulo,
            style = MaterialTheme.typography.bodyMedium,
            color = if (venceu) Cores.textoApagado else Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        Text(
            text = linhaDeEstado(item, venceu, quandoVence),
            style = MaterialTheme.typography.labelSmall,
            color = when {
                venceu -> Cores.perigo
                item.falhou -> Cores.perigo
                item.pronto -> Cores.certo
                else -> Cores.textoApagado
            },
        )

        /// A barra só existe enquanto está baixando. Pronto não precisa de
        /// barra cheia, e vencido não precisa de barra nenhuma (§24).
        if (item.baixando) {
            Box(
                Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                    .background(Cores.fundoAfundado),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth((item.porcentagem / 100f).coerceIn(0f, 1f))
                        .height(4.dp)
                        .background(Cores.destaque),
                )
            }
        }

        TextButton(onClick = aoApagar, contentPadding = PaddingValues(0.dp)) {
            Text("apagar", color = Cores.textoApagado)
        }
    }
}

@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
private fun linhaDeEstado(item: Baixado, venceu: Boolean, quandoVence: String?): String = when {
    venceu -> "a fita venceu — pegue de novo na locadora e ela volta a tocar"
    item.falhou -> "falhou"
    item.pronto && quandoVence != null -> "no aparelho · vence $quandoVence"
    item.pronto -> "no aparelho"
    item.baixando -> "baixando ${item.porcentagem.toInt()}%"
    else -> "na fila"
}
