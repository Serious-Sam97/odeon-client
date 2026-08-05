package dev.odeon.android.ui.perfil

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.AmigoNoPlacar
import dev.odeon.android.dados.ConquistaNaTela
import dev.odeon.android.dados.NaVitrine
import dev.odeon.android.dados.Perfil
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Insignia
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.corDeHex

/// O perfil — a primeira versão, e ela é **de leitura**.
///
/// ## Por que ela existe agora, e não na fase que a espec marcou
///
/// O perfil é pós-v1 (item 22 do checklist). O que entrou nesta rodada foi a
/// **insígnia** do canto, pedida como está na web — e a insígnia sai de
/// `GET /api/perfil`, que devolve o perfil inteiro. Com a resposta já na mão, a
/// gaveta ficou com duas linhas: `perfil` e `sair`.
///
/// E `perfil` tinha que levar a algum lugar. Um item de menu que não abre nada é
/// o §8b em duas palavras — então ou ele não existia, ou a tela existia. O dono
/// pediu as duas linhas, e esta é a tela que a segunda pede: **tudo o que a
/// resposta já traz, e nada além disso.**
///
/// ## O que ela não faz, e é por não estar nesta rota
///
/// | | onde mora |
/// |---|---|
/// | o **editor** (título, tags, rosto, capa, cor, vitrine, bio) | `PUT /api/perfil` |
/// | os **desafios** | `GET /api/desafios` |
/// | a **retrospectiva** | `GET /api/retrospectiva` |
/// | o perfil de **outra pessoa** | `/api/perfil/{id}`, e não há por onde chegar nele |
///
/// Nenhum dos quatro foi esquecido: os três primeiros são outras rotas, e o
/// quarto precisa da sala `gente` do mural, que também não existe. §53 — o
/// produto não oferece o que a validação vai negar.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TelaDoPerfil(
    modelo: ModeloDoPerfil,
    aoVoltar: () -> Unit,
    aoAbrirObra: (String) -> Unit = {},
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val perfil = estado.perfil

    if (perfil == null) {
        Box(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing),
            contentAlignment = Alignment.Center,
        ) {
            if (estado.carregando) {
                CircularProgressIndicator(color = Cores.destaque)
            } else {
                /// Aqui houve uma pergunta — alguém tocou em «perfil» — e ela
                /// ficou sem resposta. §8b: erro visível, com caminho de volta.
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "não deu pra abrir o perfil",
                        color = Cores.perigo,
                        style = MaterialTheme.typography.bodyMedium,
                        textAlign = TextAlign.Center,
                    )
                    TextButton(onClick = modelo::carregar) { Text("tentar de novo") }
                    TextButton(onClick = aoVoltar) { Text("voltar") }
                }
            }
        }
        return
    }

    /// A cor da moldura tinge a tela inteira, e é o que faz escolher uma cor
    /// significar alguma coisa (R43 da web). Sem escolha, o dourado da casa.
    val cor = corDeHex(perfil.moldura) ?: Cores.destaque
    val capa = modelo.arte(perfil.capa?.arte)

    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        /// A capa, borda a borda — o mesmo arranjo da ficha (R8): arte por baixo
        /// da barra de status, conteúdo respeitando as áreas seguras.
        ///
        /// ⚠️ Sem capa escolhida isto não desenha nada (§24). A web usa a arte de
        /// um filme do acervo como padrão; aqui o servidor manda `null` e o app
        /// não sorteia um filme por conta própria — sortear seria a tela
        /// afirmando um gosto que ninguém declarou.
        if (capa != null) {
            Box(Modifier.fillMaxWidth().height(160.dp).clipToBounds()) {
                AsyncImage(
                    model = capa,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to Cores.fundo.copy(alpha = 0.55f),
                            0.45f to Cores.fundo.copy(alpha = 0.25f),
                            1f to Cores.fundo,
                        ),
                    ),
                )
            }
        }

        Column(
            modifier = Modifier
                .windowInsetsPadding(
                    if (capa != null) {
                        WindowInsets.safeDrawing.only(
                            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
                        )
                    } else {
                        WindowInsets.safeDrawing
                    },
                )
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(
                onClick = aoVoltar,
                contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
            ) {
                Text("‹ voltar", color = Cores.destaque)
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Insignia(
                    nome = perfil.nome,
                    rosto = modelo.arte(perfil.avatar?.arte),
                    nivel = perfil.progresso.nivel,
                    fatia = perfil.fatiaDoNivel,
                    cor = cor,
                    /// 104dp — a web usa 84px, e aqui a tela é mais estreita:
                    /// o rosto é a primeira coisa da própria página da pessoa,
                    /// e num celular ele divide a linha com duas palavras, não
                    /// com uma barra inteira.
                    tamanho = 104.dp,
                )
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = perfil.nome,
                        style = MaterialTheme.typography.headlineSmall,
                        color = Cores.texto,
                    )
                    /// O título desbloqueado, quando há. Ele é o que a pessoa
                    /// escolheu ser chamada — some inteiro quando não escolheu,
                    /// em vez de virar "sem título" (§24).
                    perfil.tituloNome?.takeIf { it.isNotBlank() }?.let {
                        Text(it, style = Tipo.pilula, color = cor)
                    }
                    Text(
                        text = "@${perfil.username}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Cores.textoApagado,
                    )
                }
            }

            BarraDoNivel(perfil = perfil, cor = cor)

            perfil.bio?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, color = Cores.texto)
            }

            if (perfil.tags.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    perfil.tags.forEach { etiqueta ->
                        Text(
                            text = "#$etiqueta",
                            style = Tipo.pilula,
                            color = Cores.textoApagado,
                            modifier = Modifier
                                .clip(RoundedCornerShape(percent = 50))
                                .background(Cores.fundoElevado)
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                        )
                    }
                }
            }

            if (perfil.vitrine.isNotEmpty()) {
                RotuloDeSecao(texto = "vitrine", numero = perfil.vitrine.size)
                LazyRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    items(perfil.vitrine, key = { it.id }) { obra ->
                        NaVitrineCartaz(
                            obra = obra,
                            arte = modelo.arte(obra.poster),
                            aoTocar = { aoAbrirObra(obra.id) },
                        )
                    }
                }
            }

            /// O placar só existe com **2 ou mais** amigos, e a regra é da web.
            ///
            /// Um placar de uma linha é a pessoa competindo consigo mesma — e
            /// pior, é a tela dizendo «você está em primeiro» sobre uma corrida
            /// que não tem ninguém.
            if (perfil.amigos.size >= 2) {
                RotuloDeSecao(texto = "você e seus amigos", numero = perfil.amigos.size)
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    perfil.amigos.forEachIndexed { posicao, amigo ->
                        LinhaDoPlacar(posicao = posicao + 1, amigo = amigo, cor = cor)
                    }
                }
            }

            Conquistas(conquistas = perfil.conquistas, cor = cor)
        }
    }
}

/// A barra do nível, e a linha que a explica.
///
/// ## Ela mede a **fatia**, não o total
///
/// É o §10.1: a barra vai do começo do nível atual até o começo do próximo. Uma
/// barra sobre o XP total ficaria parada por semanas em quem já subiu muito, e
/// aí ela não estaria medindo progresso nenhum.
@Composable
private fun BarraDoNivel(perfil: Perfil, cor: Color) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(Cores.linha),
        ) {
            Box(
                Modifier
                    .fillMaxWidth(perfil.fatiaDoNivel)
                    .height(6.dp)
                    .clip(RoundedCornerShape(percent = 50))
                    .background(cor),
            )
        }

        /// `4.210 XP · faltam 790 pro nível 7 · 12 de 80 conquistas`.
        ///
        /// Cada pedaço entra só se existir: no último nível da curva não há
        /// «faltam», e escrever «faltam 0» seria prometer uma subida que não vem
        /// (§18). O denominador das conquistas é obrigatório — §15, «o número vem
        /// com denominador».
        val g = perfil.progresso
        val frase = listOfNotNull(
            "${milhar(g.xp)} XP",
            perfil.faltamPraSubir?.let { "faltam ${milhar(it)} pro nível ${g.nivel + 1}" },
            "${g.desbloqueadas} de ${g.total} conquistas".takeIf { g.total > 0 },
        ).joinToString(" · ")

        Text(frase, style = MaterialTheme.typography.bodySmall, color = Cores.textoApagado)
    }
}

@Composable
private fun NaVitrineCartaz(obra: NaVitrine, arte: String?, aoTocar: () -> Unit) {
    Column(
        modifier = Modifier.width(96.dp).clickable(onClick = aoTocar),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .clip(RoundedCornerShape(6.dp))
                .background(Cores.fundoElevado),
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
        Text(
            text = obra.titulo,
            style = Tipo.pilula,
            color = Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun LinhaDoPlacar(posicao: Int, amigo: AmigoNoPlacar, cor: Color) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(6.dp))
            /// A sua linha é destacada — §10.5. Sem isso, achar-se num placar de
            /// oito nomes é ler oito nomes.
            .background(if (amigo.eu) Cores.fundoElevado else Color.Transparent)
            .padding(horizontal = 8.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$posicao",
            style = Tipo.pilula,
            color = if (amigo.eu) cor else Cores.textoApagado,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = amigo.nome,
                style = MaterialTheme.typography.bodyMedium,
                color = Cores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            amigo.titulo?.takeIf { it.isNotBlank() }?.let {
                Text(it, style = Tipo.pilula, color = Cores.textoApagado, maxLines = 1)
            }
        }
        Text(
            text = "nível ${amigo.nivel} · ${milhar(amigo.xp)} XP",
            style = Tipo.pilula,
            color = Cores.textoApagado,
        )
    }
}

/// As conquistas, **agrupadas em camadas** e na ordem da web (§10.5).
///
/// A ordem não é alfabética nem por pontos: é a de dificuldade percebida —
/// fáceis, médias, sagas, difíceis, impossíveis, e por último os marcos de
/// nível, que não se perseguem, acontecem.
@Composable
private fun Conquistas(conquistas: List<ConquistaNaTela>, cor: Color) {
    if (conquistas.isEmpty()) return

    val camadas = listOf(
        "facil" to "fáceis",
        "media" to "médias",
        "saga" to "sagas",
        "dificil" to "difíceis",
        "impossivel" to "impossíveis",
        "nivel" to "marcos de nível",
    )

    camadas.forEach { (chave, rotulo) ->
        val dessaCamada = conquistas.filter { it.camada == chave }
        if (dessaCamada.isEmpty()) return@forEach

        val abertas = dessaCamada.count { it.aberta }
        RotuloDeSecao(texto = rotulo)
        Text(
            text = "$abertas de ${dessaCamada.size}",
            style = Tipo.pilula,
            color = Cores.textoApagado,
        )
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            dessaCamada.forEach { Conquista(it, cor) }
        }
    }
}

@Composable
private fun Conquista(conquista: ConquistaNaTela, cor: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = if (conquista.aberta) "✓" else "□",
            style = Tipo.pilula,
            color = if (conquista.aberta) cor else Cores.textoApagado,
        )
        Column(Modifier.weight(1f)) {
            Text(
                text = conquista.nome,
                style = MaterialTheme.typography.bodyMedium,
                color = if (conquista.aberta) Cores.texto else Cores.textoApagado,
            )
            Text(
                text = conquista.descricao,
                style = Tipo.pilula,
                color = Cores.textoApagado,
            )
        }
        /// ⚠️ Os pontos aparecem **só na aberta**, e é regra da web: numa
        /// trancada eles seriam promessa, e o projeto não promete número.
        if (conquista.aberta && conquista.pontos > 0) {
            Text("+${conquista.pontos} XP", style = Tipo.pilula, color = cor)
        }
    }
}

/// `4210` vira `4.210`.
///
/// Ponto e não vírgula, e à mão em vez de `NumberFormat`: o app inteiro escreve
/// em português do Brasil independente do idioma do aparelho — as rotas, os
/// rótulos e as frases são todos fixos —, e um separador que muda com a região
/// faria «4,210 XP» aparecer no meio de uma frase em português.
internal fun milhar(n: Int): String {
    val digitos = n.toString()
    if (digitos.length <= 3) return digitos
    return digitos.reversed().chunked(3).joinToString(".").reversed()
}
