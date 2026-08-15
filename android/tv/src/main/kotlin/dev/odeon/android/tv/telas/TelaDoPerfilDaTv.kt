package dev.odeon.android.tv.telas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Cartaz
import dev.odeon.android.tv.ui.Pilula
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.rolavelComOControle
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import dev.odeon.android.tv.ui.TipoDaSala
import dev.odeon.android.dados.ConquistaNaTela
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.sp
import dev.odeon.android.ui.Serifada
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.corDeHex
import dev.odeon.android.ui.perfil.ModeloDoPerfil

/// O perfil — quem você é dentro da casa, e a porta de saída.
///
/// ## ⚠️ A porta de saída fica **à vista**
///
/// Num celular «sair da conta» mora em ajustes porque o aparelho é de uma pessoa
/// só. Numa TV não: a sala é de todo mundo, e a TV fica logada em quem entrou
/// primeiro. Trocar de conta numa TV é o caso real, não a exceção — então o
/// botão está no alto, junto do nome, que é onde a pergunta «quem está logado
/// aqui» é feita.
@Composable
fun TelaDoPerfilDaTv(
    modelo: ModeloDoPerfil,
    aoAbrirObra: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) { modelo.carregarSePreciso() }

    val perfil = estado.perfil
    if (perfil == null) {
        Recado(
            titulo = if (estado.carregando) "…" else "o perfil não veio",
            detalhe = if (estado.carregando) {
                null
            } else {
                "a sessão continua valendo — o que falhou foi a rota do perfil."
            },
            modifier = modifier,
        ) {
            if (!estado.carregando) {
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    BotaoDaSala("tentar de novo", modelo::carregar, principal = true)
                    BotaoDaSala("sair desta conta", modelo::sair)
                }
            }
        }
        return
    }

    /// ⚠️ O fundo é declarado **aqui**, e não herdado.
    ///
    /// O degradê da capa termina em `Cores.fundo`; se a superfície atrás for
    /// outro tom, a borda de baixo da capa aparece como uma emenda horizontal —
    /// e foi assim que ela apareceu na TCL. Duas cores quase iguais desenham uma
    /// linha; iguais não desenham nada.
    Box(modifier.fillMaxSize().background(Cores.fundo)) {
    val rolagem = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        state = rolagem,
        contentPadding = PaddingValues(
            horizontal = Sala.overscanH,
            vertical = Sala.overscanV,
        ),
        verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
        modifier = Modifier.fillMaxSize().rolavelComOControle(rolagem),
    ) {
        item {
            /// ## ⚠️ A capa mora no **primeiro item**, e não atrás da tela
            ///
            /// `EnfeiteEscolhido.capa` é a única imagem que a pessoa escolheu pra
            /// representá-la, e no celular é uma faixa no topo. Numa TV virar
            /// faixa seria desperdiçá-la: aqui ela sangra borda a borda atrás do
            /// nome, esmaecendo antes de encontrar a lista.
            ///
            /// Ela começou como fundo do `Box` externo — parada, enquanto a lista
            /// rolava por cima. Na TCL isso pôs um rosto atrás das conquistas
            /// «impossíveis», a meia tela de onde ela deveria estar. Paralaxe ali
            /// não era escolha, era descuido: como item, a capa sobe junto com o
            /// nome, que é o que «no topo» quer dizer.
            ///
            /// ⚠️ O degradê não é enfeite, é o que torna o texto legível — e é
            /// **opaco já no meio da altura**, não no fim. A capa é escolhida pela
            /// pessoa, então não há foto «segura» que se possa presumir; quem
            /// cede é a foto.
            Box {
                perfil.capa?.arte?.let { caminho ->
                    Box(Modifier.matchParentSize()) {
                        AsyncImage(
                            model = modelo.arte(caminho),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            alignment = Alignment.TopCenter,
                            modifier = Modifier.fillMaxSize(),
                        )
                        Box(
                            Modifier.fillMaxSize().background(
                                Brush.verticalGradient(
                                    0f to Cores.fundo.copy(alpha = 0.35f),
                                    0.55f to Cores.fundo.copy(alpha = 0.88f),
                                    1f to Cores.fundo,
                                ),
                            ),
                        )
                    }
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(vertical = 20.dp),
                ) {
                Insignia(
                    arte = modelo.arte(perfil.avatar?.arte),
                    nome = perfil.nome.ifBlank { perfil.username },
                    fatia = perfil.fatiaDoNivel,
                    moldura = perfil.moldura,
                )
                Spacer(Modifier.width(30.dp))
                Column(Modifier.weight(1f)) {
                    /// ⚠️ **Serifada**, como o título do herói e o nome na
                    /// cortina. É a letra que esta casa usa quando a coisa
                    /// escrita **é** o assunto, e não um rótulo sobre ele — e um
                    /// nome próprio numa tela de perfil é exatamente isso.
                    Text(
                        text = perfil.nome.ifBlank { perfil.username },
                        style = androidx.compose.ui.text.TextStyle(
                            fontFamily = Serifada,
                            fontSize = 44.sp,
                            color = Cores.texto,
                        ),
                    )
                    Text(
                        text = "@${perfil.username}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cores.textoApagado,
                    )

                    Spacer(Modifier.height(14.dp))

                    /// ## ⚠️ A barra larga, e a linha que a explica
                    ///
                    /// O anel da insígnia já mostra a fatia do nível, mas ele tem
                    /// 44dp e a três metros é lido como enfeite. A barra larga é a
                    /// mesma informação em tamanho de sala.
                    ///
                    /// A linha embaixo é o §8b: `299 XP` sozinho não responde nada
                    /// — «299 é muito?». `faltam 1 pro nível 3` responde, e `7 de
                    /// 80 conquistas` diz de onde vem o resto.
                    Box(
                        Modifier
                            .width(560.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .background(Cores.linha),
                    ) {
                        Box(
                            Modifier
                                .fillMaxHeight()
                                .fillMaxWidth(perfil.fatiaDoNivel)
                                .background(corDeHex(perfil.moldura) ?: Cores.destaque),
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = buildList {
                            add("${milhar(perfil.progresso.xp)} XP")
                            /// ⚠️ «faltam N» só entra quando **há** próximo nível.
                            /// `faltamPraSubir` devolve `null` no fim da curva, e
                            /// escrever «faltam 0» seria dizer que o nível vira já
                            /// (§24).
                            perfil.faltamPraSubir?.let {
                                add("faltam $it pro nível ${perfil.progresso.nivel + 1}")
                            }
                            add(
                                "${perfil.progresso.desbloqueadas} de " +
                                    "${perfil.progresso.total} conquistas",
                            )
                        }.joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cores.textoApagado,
                    )

                    Spacer(Modifier.height(12.dp))

                    perfil.tituloNome?.let {
                        Spacer(Modifier.height(10.dp))
                        Pilula(it, cor = Cores.destaque, tinta = Cores.destaque)
                    }
                }
                }
            }
        }

        perfil.bio?.takeIf { it.isNotBlank() }?.let { bio ->
            item {
                Text(
                    text = bio,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cores.textoApagado,
                    modifier = Modifier.width(1000.dp),
                )
            }
        }

        /// A vitrine. **A ordem é o conteúdo** (§10.2) — por isso é lista e não
        /// conjunto, e por isso ela é desenhada na ordem que veio.
        if (perfil.vitrine.isNotEmpty()) {
            item {
                Column {
                    RotuloDeSecao("na estante")
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes)) {
                        items(perfil.vitrine) { obra ->
                            Cartaz(
                                titulo = obra.titulo,
                                arte = modelo.arte(obra.poster),
                                detalhe = obra.ano?.toString(),
                                aoEscolher = { aoAbrirObra(obra.id) },
                            )
                        }
                    }
                }
            }
        }

        /// ## ⚠️ **Todas** as conquistas, e não só as abertas
        ///
        /// A tela mostrava `perfil.conquistas.filter { it.aberta }`, e isso é um
        /// erro de sentido, não de desenho. O §10.5 esconde o **número** de uma
        /// conquista trancada — ponto que não se tem é promessa —, mas não a
        /// conquista. Sem as trancadas não há o que perseguir: a tela vira um
        /// troféu de coisas já feitas, e some justamente a metade que dá motivo
        /// pra fazer a próxima.
        ///
        /// ⚠️ E são **marcações**, não cartões. Uma fileira de cartões de 280dp
        /// diz «cada uma destas é um objeto importante»; uma lista com `✓` e `☐`
        /// diz «isto é uma lista, e faltam estas». A segunda é a verdade.
        if (perfil.conquistas.isNotEmpty()) {
            item {
                Column {
                    RotuloDeSecao(
                        "conquistas · ${perfil.progresso.desbloqueadas} de ${perfil.progresso.total}",
                    )
                    Spacer(Modifier.height(14.dp))
                    /// A mesma cor da moldura que borda a insígnia lá em cima: o `✓`
                    /// e os pontos são **desta pessoa**, e usar o dourado da
                    /// casa aqui perderia a única cor que o perfil tem de seu.
                    Conquistas(perfil.conquistas, corDeHex(perfil.moldura) ?: Cores.destaque)
                }
            }
        }

        if (perfil.amigos.isNotEmpty()) {
            item {
                Column {
                    RotuloDeSecao("o placar da casa")
                    perfil.amigos.forEach { amigo ->
                        /// ⚠️ **A minha linha é um cartão elevado, não um nome
                        /// dourado.**
                        ///
                        /// Dourado nesta casa quer dizer «isto está em foco» ou
                        /// «isto é o destaque» — é a cor do anel, do botão
                        /// principal, do programa no ar. Usá-lo pra dizer «este
                        /// sou eu» empresta um significado que ele já tem, e num
                        /// placar de seis linhas o olho lê o dourado como «o
                        /// primeiro colocado».
                        ///
                        /// Elevação diz outra coisa e diz certo: esta linha está
                        /// **mais perto de você** que as outras.
                        Row(
                            Modifier
                                .width(700.dp)
                                .then(
                                    if (amigo.eu) {
                                        Modifier
                                            .background(Cores.fundoElevado, RoundedCornerShape(10.dp))
                                            .padding(horizontal = 16.dp, vertical = 12.dp)
                                    } else {
                                        Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                    },
                                ),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = amigo.nome,
                                style = MaterialTheme.typography.bodyLarge,
                                color = Cores.texto,
                                modifier = Modifier.weight(1f),
                            )
                            amigo.titulo?.let {
                                Text(
                                    text = it,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Cores.textoApagado,
                                )
                                Spacer(Modifier.width(20.dp))
                            }
                            Text(
                                text = "nível ${amigo.nivel}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Cores.textoApagado,
                            )
                        }
                    }
                }
            }
        }
    }

        /// ⚠️ **Só existe enquanto o cabeçalho existe.**
        ///
        /// Ele é uma sobreposição porque precisa ser irmão da coluna e não filho
        /// dela — filho, ficaria inalcançável (ver [rolavelComOControle]). Mas
        /// sobreposição que fica parada enquanto a lista rola vira uma pílula
        /// escura em cima das conquistas: na TCL ela tapou `Sabe…` e `Reass…` no
        /// meio das médias.
        ///
        /// Some com o cabeçalho, então. É a ação **daquele** bloco — «esta conta
        /// é a sua» —, e quando o nome sai da tela a pergunta que ele responde
        /// saiu junto.
        ///
        /// ⚠️ Sumir não perde foco: só se rola com o foco **na coluna**, então o
        /// botão nunca está focado na hora em que desaparece.
        val cabecalhoAVista by remember {
            derivedStateOf { rolagem.firstVisibleItemIndex == 0 }
        }
        if (cabecalhoAVista) {
            BotaoDaSala(
                rotulo = "sair desta conta",
                aoEscolher = modelo::sair,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = Sala.overscanH, vertical = Sala.overscanV),
            )
        }
    }
}

/// O rosto redondo com o anel do nível.
///
/// ## Ela é a `Insignia` do `:app` refeita, e não a mesma
///
/// A do celular ficou lá porque **ela desenha**, e a régua do `:core` é o que
/// não desenha. Aqui ela nasce de novo em 40 linhas, no tamanho da sala (112dp
/// contra 38dp), sem a marca derivada por hash — que no celular existe pra quem
/// não escolheu rosto, e aqui cai no dourado da casa.
///
/// ⚠️ A cor da moldura vem **pronta do servidor**, em hex, e o `null` é o normal
/// de quem não escolheu — e aí se cai no dourado da casa, nunca numa cor
/// sorteada. É a regra escrita no próprio `Perfil.moldura`.
@Composable
private fun Insignia(arte: String?, nome: String, fatia: Float, moldura: String?) {
    val cor = corDeHex(moldura) ?: Cores.destaque
    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val traco = 5.dp.toPx()
            /// O trilho inteiro, apagado, e por cima dele a fatia andada. Sem o
            /// trilho, um nível recém-começado desenharia um risco solto no
            /// vazio — não se saberia que ele é parte de um círculo.
            drawArc(
                color = Cores.linha,
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                style = Stroke(width = traco),
                topLeft = Offset(traco / 2, traco / 2),
                size = Size(size.width - traco, size.height - traco),
            )
            drawArc(
                color = cor,
                startAngle = -90f,
                sweepAngle = 360f * fatia,
                useCenter = false,
                style = Stroke(width = traco),
                topLeft = Offset(traco / 2, traco / 2),
                size = Size(size.width - traco, size.height - traco),
            )
        }
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(Cores.fundoElevado),
            contentAlignment = Alignment.Center,
        ) {
            if (arte != null) {
                AsyncImage(
                    model = arte,
                    contentDescription = nome,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Text(
                    text = nome.take(1).uppercase(),
                    style = MaterialTheme.typography.headlineMedium,
                    color = cor,
                )
            }
        }
    }
}

/// As conquistas, **agrupadas em camadas**, na ordem do celular (§10.5).
///
/// A ordem não é alfabética nem por pontos: é a de dificuldade percebida —
/// fáceis, médias, sagas, difíceis, impossíveis, e por último os marcos de
/// nível, que não se perseguem, acontecem.
///
/// ## ⚠️ Duas ou três colunas, porque a TV tem largura que o celular não tem
///
/// No celular a lista é uma coluna e rola. Aqui, uma coluna só desperdiçaria
/// dois terços da tela e obrigaria a rolar por oitenta linhas com o D-pad — que
/// é o gesto mais caro que existe nesta sala. Em três colunas a maior parte das
/// camadas cabe sem rolagem nenhuma.
@Composable
private fun Conquistas(conquistas: List<ConquistaNaTela>, cor: Color) {
    val camadas = listOf(
        "facil" to "fáceis",
        "media" to "médias",
        "saga" to "sagas",
        "dificil" to "difíceis",
        "impossivel" to "impossíveis",
        "nivel" to "marcos de nível",
    )

    Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
        camadas.forEach { (chave, rotulo) ->
            val dessaCamada = conquistas.filter { it.camada == chave }
            if (dessaCamada.isEmpty()) return@forEach

            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = rotulo,
                        style = TipoDaSala.rotulo,
                        color = Cores.destaque,
                    )
                    Spacer(Modifier.width(12.dp))
                    /// ⚠️ `3 de 12` fica **na camada**, e não só no topo. É o
                    /// §8b: o total geral responde «como vou eu no todo», este
                    /// responde «quanto falta deste bolo» — que é a pergunta de
                    /// quem está olhando esta lista agora.
                    Text(
                        text = "${dessaCamada.count { it.aberta }} de ${dessaCamada.size}",
                        style = TipoDaSala.rotulo,
                        color = Cores.textoApagado,
                    )
                }
                Spacer(Modifier.height(10.dp))

                /// ⚠️ Sem `LazyVerticalGrid`: a tela inteira já é uma
                /// `LazyColumn`, e grade preguiçosa dentro de coluna preguiçosa
                /// exige altura fixa — que aqui seria um número inventado, e o
                /// primeiro a quebrar quando o servidor ganhar a camada
                /// seguinte. Três colunas de `Column` medem o que têm.
                val porColuna = (dessaCamada.size + 2) / 3
                Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                    dessaCamada.chunked(porColuna.coerceAtLeast(1)).forEach { coluna ->
                        Column(
                            Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            coluna.forEach { Conquista(it, cor) }
                        }
                    }
                }
            }
        }
    }
}

/// Uma marcação: `✓` ou `☐`, nome, descrição, e os pontos **só na aberta**.
@Composable
private fun Conquista(conquista: ConquistaNaTela, cor: Color) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(
            text = if (conquista.aberta) "✓" else "☐",
            style = MaterialTheme.typography.bodyMedium,
            color = if (conquista.aberta) cor else Cores.textoApagado,
        )
        /// ⚠️ O nome e os pontos moram **na mesma coluna**, empilhados, e não
        /// lado a lado. Numa `Row` de três colunas o `+10 XP` roubava largura do
        /// nome, e `Sessão dupla e meia` quebrava em duas linhas enquanto a
        /// vizinha trancada, sem pontos, cabia numa. Duas conquistas iguais
        /// ganhavam alturas diferentes por causa de um número.
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = conquista.nome,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (conquista.aberta) Cores.texto else Cores.textoApagado,
                    modifier = Modifier.weight(1f, fill = false),
                )
                if (conquista.aberta && conquista.pontos > 0) {
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "+${conquista.pontos}",
                        style = LetraDaDescricao,
                        color = cor,
                    )
                }
            }
            /// ⚠️ **Sem o espaçamento do rótulo.** `TipoDaSala.rotulo` carrega
            /// `0.28em`, que é certo pra `CONQUISTAS` em caixa alta e errado pra
            /// uma frase: na TCL, `Termine uma obra` saiu esparramada em três
            /// linhas dentro de uma coluna estreita. Espaçamento de rótulo é pra
            /// rótulo — descrição é texto, e texto se lê junto.
            Text(
                text = conquista.descricao,
                style = LetraDaDescricao,
                color = Cores.textoApagado,
            )
        }
    }
}

/// A letra das descrições e dos pontos nesta tela.
///
/// ⚠️ Ela existe por causa do `letterSpacing`, não do tamanho: o `rotulo` da sala
/// espaça `0.28em`, e numa coluna de lista isso é o que faz `Termine uma obra`
/// virar três linhas. Aqui o espaçamento é o da fonte.
private val LetraDaDescricao = androidx.compose.ui.text.TextStyle(fontSize = 14.sp)

/// `4210` vira `4.210`.
///
/// Ponto e não vírgula, e à mão em vez de `NumberFormat`: o app inteiro escreve
/// em português do Brasil independente do idioma do aparelho, e um separador que
/// muda com a região faria «4,210 XP» aparecer no meio de uma frase em português.
/// É o mesmo defeito que a biblioteca já pagou com `8,316` numa TV em inglês.
private fun milhar(n: Int): String {
    val digitos = n.toString()
    if (digitos.length <= 3) return digitos
    return digitos.reversed().chunked(3).joinToString(".").reversed()
}
