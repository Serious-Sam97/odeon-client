package dev.odeon.android.ui.mural

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.Acontecimento
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.chega
import dev.odeon.android.ui.pegaLuz

/// O mural — o que aconteceu na casa.
///
/// ## É a primeira tela do app que não é sobre você
///
/// Biblioteca, locadora, baixados e para-você respondem perguntas de quem está
/// segurando o telefone. Esta responde **o que os outros andaram fazendo** — e
/// é o que faz o Odeon ser de uma casa e não de uma pessoa. A camada social é
/// citada no §2 do `CONTINUAR-ANDROID.md` como parte do produto desde sempre.
///
/// ## Esta versão é a metade barata, e está escrito qual metade
///
/// O mural da web tem **811 linhas**: posts, mensagens, salas de assistir
/// junto, comentários. Aqui é `GET /api/feed` desenhado — a linha do tempo, e
/// mais nada.
///
/// **Não tem:** escrever post, comentar, e as salas do "junto" (que são outras
/// oito rotas). Falta escrito em vez de fingido: nenhum botão aparece
/// desabilitado, porque oferecer o que não existe é o §53.
@Composable
fun TelaDoMural(
    modelo: ModeloDoMural,
    aoAbrirObra: (String) -> Unit = {},
    /// ⚠️ Ele **voltou a ser folha** no dia em que o ao vivo pegou o lugar dele na
    /// barra, e por isso precisa de saída própria: uma tela sem aba acesa e sem
    /// «voltar» só se fecha pelo gesto do sistema, que é saída sem sinal (§8b).
    ///
    /// É o mesmo `‹ voltar` do perfil, que chega pela mesma gaveta.
    aoVoltar: () -> Unit = {},
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    if (estado.carregando) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cores.destaque)
        }
        return
    }

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        TextButton(
            onClick = aoVoltar,
            contentPadding = PaddingValues(0.dp),
        ) {
            Text("‹ voltar", color = Cores.destaque)
        }

        Text("mural", style = MaterialTheme.typography.headlineSmall, color = Cores.texto)

        /// ⚠️ **«2 de 3 vozes» é o §8b numa métrica**, e a frase é da web:
        /// «um mural com um nome só não é uma conversa — e a tela diz isso em
        /// vez de parecer completa».
        ///
        /// Sem isso, um mural com um morador ativo parece um mural cheio. Com
        /// isso, ele diz que está quieto.
        if (estado.mural.pessoas > 0) {
            Text(
                text = "${estado.mural.vozes} de ${estado.mural.pessoas} pessoas apareceram",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
            )
        }

        val linhas = estado.mural.acontecimentos.filter { it.frase != null }

        if (linhas.isEmpty()) {
            Text(
                text = "ainda não aconteceu nada por aqui",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.textoApagado,
            )
            return@Column
        }

        RotuloDeSecao(texto = "esta semana", numero = linhas.size)

        linhas.forEachIndexed { i, acontecimento ->
            Linha(
                acontecimento = acontecimento,
                arte = modelo.arte(acontecimento.poster),
                indice = i,
                aoTocar = { acontecimento.obraId?.let(aoAbrirObra) },
            )
        }
    }
}

/// Uma linha do mural: quem, o que fez, e a arte da obra.
///
/// ## O nome de quem fez é o destaque, e não o título do filme
///
/// É o que separa esta tela da biblioteca. Lá o sujeito é a obra; aqui o
/// sujeito é a **pessoa** — «rudney pegou a fita de Cassino Royale» —, e a
/// hierarquia tipográfica diz isso antes de a frase ser lida.
///
/// O «você» no lugar do próprio nome não é enfeite: ler o próprio nome em
/// terceira pessoa num feed da própria casa lê como notificação de sistema.
@Composable
private fun Linha(
    acontecimento: Acontecimento,
    arte: String?,
    indice: Int,
    aoTocar: () -> Unit,
) {
    Row(
        modifier = Modifier
            .chega(indice)
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            .background(Cores.fundoElevado)
            .clickable(enabled = acontecimento.obraId != null, onClick = aoTocar)
            .padding(10.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(
            modifier = Modifier
                .width(46.dp)
                .aspectRatio(2f / 3f)
                .pegaLuz(RoundedCornerShape(4.dp))
                .clip(RoundedCornerShape(4.dp))
                .background(Cores.fundoAfundado),
        ) {
            if (arte != null) {
                AsyncImage(
                    model = arte,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                Text(
                    text = if (acontecimento.meu) "você" else acontecimento.quem,
                    style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Cores.destaque,
                )
                Text(
                    text = acontecimento.frase.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.textoApagado,
                )
            }
            Text(
                text = acontecimento.titulo,
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            /// O detalhe vem **pronto do servidor** — é uma nota, um recado, uma
            /// frase de resenha. Ao contrário do verbo, ele é conteúdo, e por
            /// isso não é montado aqui. §24: sem detalhe, sem linha.
            acontecimento.detalhe?.takeIf { it.isNotBlank() }?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.textoApagado,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
