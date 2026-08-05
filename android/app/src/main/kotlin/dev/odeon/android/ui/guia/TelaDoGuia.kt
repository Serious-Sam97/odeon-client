package dev.odeon.android.ui.guia

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.FaixaDoGuia
import dev.odeon.android.dados.PessoaDoGuia
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.PilulaDeEtiqueta
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.chega
import dev.odeon.android.ui.pegaLuz

/// O guia — os eixos pelos quais o acervo pode ser olhado.
///
/// ## Ele responde uma pergunta que nenhuma outra tela responde
///
/// A biblioteca responde «o que existe». O para-você responde «o que assisto
/// agora». O guia responde **«por onde eu entro»** — por diretor, por gênero,
/// por década, por país. É a diferença entre uma lista e um índice.
///
/// ## O número que faz a região existir
///
/// `fora_de_hollywood` vem junto dos países, e o comentário da web explica por
/// quê: «sem ele o eixo diz "Estados Unidos 491" e o resto vira rodapé. Este é o
/// número que faz a região valer uma seção — é a pergunta que ninguém conseguia
/// fazer antes».
///
/// ## O que esta versão **não** faz, e está escrito
///
/// **Tocar num eixo não filtra a biblioteca ainda.** O `chave` já vem no dado
/// (`genre:Terror`, o ano da década) e é exatamente o que iria pro filtro — mas
/// a biblioteca não tem filtro. Oferecer o toque que não leva a lugar nenhum
/// seria o §8b; então nada aqui é clicável, e a próxima coisa óbvia a fazer
/// nesta tela é o outro lado dessa ponte.
@Composable
fun TelaDoGuia(modelo: ModeloDoGuia) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    if (estado.carregando) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = Cores.destaque)
        }
        return
    }

    val eixos = estado.eixos

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text("guia", style = MaterialTheme.typography.headlineSmall, color = Cores.texto)

        if (eixos.generos.isEmpty() && eixos.direcao.isEmpty()) {
            Text(
                text = "o guia ainda não tem o que cruzar — o acervo precisa de identificação",
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.textoApagado,
                modifier = Modifier.padding(top = 8.dp),
            )
            return@Column
        }

        FaixaDeEixos("gêneros", eixos.generos)
        FaixaDeEixos("décadas", eixos.decadas)

        if (eixos.paises.isNotEmpty()) {
            FaixaDeEixos("de onde vêm", eixos.paises)
            /// O número que a web insiste em mostrar junto.
            if (eixos.foraDeHollywood > 0) {
                Text(
                    text = "${eixos.foraDeHollywood} filmes vêm de fora dos Estados Unidos",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.destaque,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }
        }

        FileiraDePessoas("direção", eixos.direcao, modelo::arte)
        FileiraDePessoas("elenco", eixos.elenco, modelo::arte)
        FileiraDePessoas("trilha", eixos.trilha, modelo::arte)
    }
}

/// Um eixo que não é pessoa: gênero, década, país.
///
/// Desenhado como **etiqueta**, e não como filtro, de propósito: a
/// `PilulaDeEtiqueta` é a forma que este app usa pra "fato que não se toca". Se
/// um dia o toque filtrar a biblioteca, ela vira `PilulaDeFiltro` — e a troca de
/// componente é o que vai contar que a coisa passou a fazer algo.
@Composable
private fun FaixaDeEixos(titulo: String, faixas: List<FaixaDoGuia>) {
    if (faixas.isEmpty()) return
    RotuloDeSecao(texto = titulo, numero = faixas.size)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        itemsIndexed(faixas, key = { _, f -> f.chave }) { i, faixa ->
            Box(Modifier.chega(i)) {
                PilulaDeEtiqueta(namespace = faixa.rotulo, valor = "${faixa.obras}")
            }
        }
    }
}

/// Uma fileira de gente, com rosto e o quanto do trabalho dela você já viu.
///
/// ## «7 de 23» é o que faz isto ser um guia e não um elenco
///
/// A contagem cruza a pessoa com **o seu histórico**: quantas obras dela existem
/// no acervo e quantas você terminou. Sem ela a fileira é uma lista de nomes;
/// com ela é um mapa do que falta.
///
/// §24: quem não tem `obras` não desenha contagem, e quem não tem rosto cai na
/// inicial sobre a cor elevada — nunca num avatar genérico, que seria inventar
/// uma cara.
@Composable
private fun FileiraDePessoas(
    titulo: String,
    pessoas: List<PessoaDoGuia>,
    arte: (String?) -> String?,
) {
    if (pessoas.isEmpty()) return
    RotuloDeSecao(texto = titulo, numero = pessoas.size)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        itemsIndexed(pessoas, key = { _, p -> p.id }) { i, pessoa ->
            Column(
                modifier = Modifier.chega(i).width(76.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .pegaLuz(CircleShape)
                        .clip(CircleShape)
                        .background(Cores.fundoElevado),
                    contentAlignment = Alignment.Center,
                ) {
                    val rosto = arte(pessoa.imagem)
                    if (rosto != null) {
                        AsyncImage(
                            model = rosto,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )
                    } else {
                        Text(
                            text = pessoa.name.take(1).uppercase(),
                            style = MaterialTheme.typography.headlineSmall,
                            color = Cores.destaqueApagado,
                        )
                    }
                }
                Text(
                    text = pessoa.name,
                    style = MaterialTheme.typography.labelSmall,
                    color = Cores.texto,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                if (pessoa.obras > 0) {
                    Text(
                        text = "${pessoa.terminadas} de ${pessoa.obras}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Cores.textoApagado,
                    )
                }
            }
        }
    }
}
