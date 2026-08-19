package dev.odeon.android.ui.aovivo

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.Tipo

/// Ao vivo, no celular — **com a cara da TV**.
///
/// ## ⚠️ A primeira versão era uma lista, e o dono disse que estava horrível
///
/// Ela era uma fileira vertical de cartões de texto, um por canal, com barra e
/// «faltam». Funcionava e não parecia televisão. A folha de então argumentava que
/// «zapear com o polegar num ônibus é percorrer uma lista» — e o argumento estava
/// errado: a lista serve pra **escolher**, mas o ao vivo não é uma lista de
/// opções, é uma coisa que **já está acontecendo**. Isso se mostra, não se
/// enumera.
///
/// O desenho agora é o do `TelaAoVivoDaTv`, adaptado ao toque:
///
/// | a TV tem | aqui |
/// |---|---|
/// | herói com a arte sangrando à direita e degradê horizontal | ✅ |
/// | ponto vermelho + «NO AR» + nome do canal | ✅ |
/// | `COMEÇOU 09:20` · barra · `FALTAM 41 MIN` | ✅ |
/// | fileira de cartões de 139dp com o número em selo | ✅ |
/// | `▲▼ ZAPEIA` e número digitado | ❌ não há controle remoto num celular |
/// | grade de 12h que rola | ❌ é o **guia**, e ele já é uma aba |
///
/// ⚠️ **O vermelho é o único do app fora de erro**, e é o da TV: «vermelho aqui
/// não é erro, é a luz do estúdio — a convenção que toda televisão do mundo já
/// ensinou».
@Composable
fun TelaAoVivo(
    modelo: ModeloAoVivo,
    aoSintonizar: (QuadroNoAr, comecarEm: Double) -> Unit,
    /// ⚠️ **Canal sem obra casada.** Ele não abre um filme — abre a
    /// **transmissão**, que o ErsatzTV serve com ou sem casamento. É o caminho
    /// que a TV sempre teve e o celular não. Ver `TelaDoCanal`.
    aoSintonizarDeFora: (canalId: String, nome: String) -> Unit,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    /// ⚠️ A conta é **por minuto**, não por segundo: o modelo anda com o relógio
    /// de segundo em segundo (é o que a TV precisa), e recalcular `emCartaz` a
    /// cada batida custou 46% de quadros perdidos, medido em 17/08/2026. Nada
    /// aqui mostra segundos — o que vira é o «faltam» e a barra.
    val minuto = estado.agoraMs / 60_000

    val ordenados = remember(minuto, estado.doOdeon, estado.canais, estado.favoritos) {
        emCartaz(estado.agoraMs, estado.doOdeon, estado.canais)
            .sortedBy { q ->
                estado.favoritos.indexOf(q.canalId).takeIf { it >= 0 } ?: Int.MAX_VALUE
            }
    }

    /// Qual canal está no herói.
    ///
    /// ⚠️ Ele **muda por toque**, não por foco: na TV o herói segue o controle;
    /// aqui segue o dedo, e tocar num cartão é o que lá se chama focar.
    var escolhido by remember { mutableStateOf<String?>(null) }

    /// O canal que está abrindo agora. ⚠️ Ele **se apaga ao voltar** — ver o
    /// `LaunchedEffect` logo abaixo: sem isso, quem sai do player encontra o
    /// cartão ainda girando, dizendo que carrega algo que já carregou.
    var sintonizando by remember { mutableStateOf<String?>(null) }

    /// O que a tela tem a dizer sobre o último toque. Hoje só uma coisa: que o
    /// programa não tem arquivo. Antes disso, o toque era mudo.
    var recado by remember { mutableStateOf<String?>(null) }

    /// ⚠️ Voltar do player **apaga o rodinho**. A tela não é destruída ao
    /// navegar (ela é um destino da mesma árvore), então o estado sobrevive à
    /// ida e volta — e um cartão girando pra sempre é pior que nenhum.
    LaunchedEffect(estado.agoraMs) { sintonizando = null }

    val noHeroi = ordenados.firstOrNull { it.canalId == escolhido } ?: ordenados.firstOrNull()

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
        Text(
            "ao vivo",
            style = MaterialTheme.typography.headlineSmall,
            color = Cores.texto,
            modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 12.dp),
        )

        estado.erro?.let { recado ->
            Text(
                recado, style = Tipo.pilula, color = Cores.textoApagado,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        if (estado.carregando && ordenados.isEmpty()) {
            Text(
                "sintonizando…", style = Tipo.pilula, color = Cores.textoApagado,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }

        noHeroi?.let { quadro ->
            Heroi(
                quadro = quadro,
                agoraMs = minuto * 60_000,
                arte = modelo.arte(quadro.arte),
                fixado = quadro.canalId in estado.favoritos,
                aoFixar = { modelo.alternarFavorito(quadro.canalId) },
                /// ⚠️ **Aqui vai o relógio de segundo**, e não o do minuto: o
                /// ponto onde a transmissão está decide onde o filme abre, e
                /// arredondar abriria até 59s fora.
                /// ⚠️ O herói sintoniza como os cartões — **um caminho só**.
                aoSintonizar = { abrir(quadro, estado.agoraMs, aoSintonizarDeFora, aoSintonizar) },
            )
        }

        /// ⚠️ O recado do último toque, **logo acima da fileira** — perto do
        /// cartão que o produziu, e não numa barra no rodapé que aparece longe
        /// da mão. §8b: erro visível **e** legível.
        recado?.let { texto ->
            Text(
                text = texto,
                style = Tipo.pilula,
                color = Cores.perigo,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            )
        }

        if (ordenados.isNotEmpty()) {
            Box(Modifier.padding(horizontal = 16.dp)) {
                RotuloDeSecao(texto = "sintonia", numero = ordenados.size)
            }
            /// ## ⚠️ Os cartões **quebram linha**, e a TV os põe em fileira
            ///
            /// É a única divergência de desenho, e ela veio da tela: com uma
            /// `LazyRow` cabiam três canais e sobrava **metade do celular em
            /// preto** — vinte canais escondidos atrás da borda direita, com um
            /// vão embaixo do tamanho de um herói.
            ///
            /// Na sala a fileira se justifica porque o controle anda nela sem
            /// esforço e a tela é larga. Aqui ela esconderia dezessete canais pra
            /// preservar um formato, e o formato existe pra mostrar canais.
            ///
            /// ⚠️ **O cartão é o mesmo da TV** — 139dp, número em selo, véu no
            /// que não está no herói. O que muda é como eles se arrumam.
            val porLinha = 2
            ordenados.chunked(porLinha).forEach { linha ->
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 5.dp),
                ) {
                    linha.forEach { quadro ->
                        Box(Modifier.weight(1f)) {
                            CartaoDeCanal(
                                quadro = quadro,
                                agoraMs = minuto * 60_000,
                                arte = modelo.arte(quadro.arte),
                                escolhido = quadro.canalId == noHeroi?.canalId,
                                fixado = quadro.canalId in estado.favoritos,
                                sintonizando = quadro.canalId == sintonizando,
                                /// ## ⚠️ **Um toque, e não dois** — 18/08/2026
                                ///
                                /// > «ao selecionar um canal tu tem que clicar no
                                /// > programa e aí clicar no banner no topo; o
                                /// > ideal é tu clicar e já ir pro programa»
                                ///
                                /// O toque só **elegia** o herói, e sintonizar era
                                /// um segundo toque lá em cima. Numa fileira que
                                /// existe pra zapear, escolher e assistir eram a
                                /// mesma intenção partida em dois gestos.
                                ///
                                /// ⚠️ O herói **continua acompanhando**: quem toca
                                /// vê o cartão acender enquanto a tela troca, e ao
                                /// voltar do player o canal certo está no topo.
                                /// ## ⚠️ **Um caminho só: a transmissão** — 18/08/2026
                                ///
                                /// > «canal Tela Quente ainda tá com tela preta.
                                /// > Porra, vamos ter que ir de canal em canal?
                                /// > NÃO É TUDO A MESMA MERDA?»
                                ///
                                /// É, e eu é que tinha feito duas. O toque
                                /// escolhia caminho conforme o **casamento**:
                                /// casado abria **o arquivo** — sessão nova,
                                /// codec, transcodificação, tudo que pode dar
                                /// errado — e não casado abria a **transmissão**,
                                /// que o ErsatzTV entrega pronta. Cada canal se
                                /// comportava de um jeito, porque dependia do
                                /// programa que estava no ar naquele minuto.
                                ///
                                /// ⚠️ E o que o caminho do arquivo comprava era
                                /// **nada**: o ao vivo não registra progresso —
                                /// regra do dono, de 17/08 — então barra,
                                /// posição e «continuar» não existem aqui de
                                /// qualquer forma. Restava só o risco.
                                ///
                                /// Agora o ao vivo faz o que um canal faz:
                                /// **sintoniza**. O casamento passou a servir
                                /// pro que ele é bom — escrever o nome do
                                /// programa e o quanto já passou no cartão.
                                aoTocar = {
                                    escolhido = quadro.canalId
                                    sintonizando = quadro.canalId
                                    abrir(quadro, estado.agoraMs, aoSintonizarDeFora, aoSintonizar)
                                },
                            )
                        }
                    }
                    /// ⚠️ A última linha ímpar precisa do vão à direita, senão o
                    /// cartão sozinho estica pro dobro da largura dos irmãos.
                    if (linha.size < porLinha) {
                        repeat(porLinha - linha.size) { Box(Modifier.weight(1f)) }
                    }
                }
            }
            Box(Modifier.padding(horizontal = 16.dp)) {
                RotuloDeSecao(texto = "programação")
            }
            Programacao(
                agoraMs = estado.agoraMs,
                doOdeon = estado.doOdeon,
                guia = estado.guia,
                externos = estado.canais,
            )

            /// O respiro do fim: a barra do facho flutua por cima do conteúdo, e
            /// sem isto o último cartão fica embaixo dos rótulos das abas.
            Spacer(Modifier.height(dev.odeon.android.ui.ALTURA_DA_FILEIRA))
        }
    }
}

/// O herói — o canal que está no ar agora.
///
/// ⚠️ **A arte sangra pela direita e o texto mora na esquerda**, com um degradê
/// horizontal no meio. É o desenho da TV, e o motivo é o mesmo: a capa **vira**
/// fundo em vez de ser cortada por uma borda, e aí o texto não precisa de caixa.
@Composable
private fun Heroi(
    quadro: QuadroNoAr,
    agoraMs: Long,
    arte: String?,
    fixado: Boolean,
    aoFixar: () -> Unit,
    aoSintonizar: () -> Unit,
) {
    val duracao = (quadro.terminaMs - quadro.comecaMs).takeIf { it > 0 }
    val andado = duracao?.let {
        ((agoraMs - quadro.comecaMs).toFloat() / it.toFloat()).coerceIn(0f, 1f)
    }

    Box(
        Modifier
            .fillMaxWidth()
            /// ⚠️ 16:9 em vez dos 204dp fixos da TV: lá a largura é conhecida;
            /// aqui ela vai de 393dp a 1024dp, e altura fixa deixaria o herói
            /// atarracado no tablet.
            .aspectRatio(16f / 9f)
            .clickable(onClick = aoSintonizar),
    ) {
        if (arte != null) {
            AsyncImage(
                model = arte,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter,
                modifier = Modifier.fillMaxSize(),
            )
        }
        Box(
            Modifier.fillMaxSize().background(
                Brush.horizontalGradient(
                    0f to Cores.fundo,
                    0.42f to Cores.fundo.copy(alpha = 0.92f),
                    1f to Cores.fundo.copy(alpha = 0.35f),
                ),
            ),
        )

        Column(
            Modifier
                .align(Alignment.CenterStart)
                /// ⚠️ **0,78 e não 0,64**: com 64% o título quebrava em «Sweeney
                /// Todd: O Barbeiro De…», e o nome do programa é a coisa que o
                /// herói existe pra dizer. O degradê cobre até 42% opaco, então
                /// há tinta suficiente atrás desta largura.
                .fillMaxWidth(0.78f)
                .padding(horizontal = 16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                /// ⚠️ O ponto vermelho e o «NO AR» — a **única coisa vermelha**
                /// desta casa fora de erro, e de propósito: aqui vermelho não é
                /// defeito, é a luz do estúdio.
                Box(
                    Modifier
                        .requiredSize(8.dp)
                        .clip(CircleShape)
                        .background(Cores.perigo),
                )
                Spacer(Modifier.width(8.dp))
                Text("NO AR", style = RotuloMiudo, color = Cores.perigo)
                Spacer(Modifier.width(12.dp))
                Text(
                    quadro.canalNome,
                    style = RotuloMiudo,
                    color = Cores.textoApagado,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = quadro.titulo,
                style = MaterialTheme.typography.headlineSmall,
                color = Cores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (andado != null) {
                Spacer(Modifier.height(11.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "COMEÇOU ${hora(quadro.comecaMs)}",
                        style = RotuloMiudo, color = Cores.textoApagado,
                    )
                    Box(
                        Modifier
                            .padding(horizontal = 10.dp)
                            .weight(1f)
                            .height(3.dp)
                            .clip(CircleShape)
                            .background(Cores.linha),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(andado)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(Cores.destaque),
                        )
                    }
                    Text(
                        text = "FALTAM ${((quadro.terminaMs - agoraMs) / 60_000).coerceAtLeast(0)} MIN",
                        style = RotuloMiudo, color = Cores.textoApagado,
                    )
                }
            }

            quadro.aSeguir?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    "a seguir · $it",
                    style = Tipo.pilula,
                    color = Cores.textoApagado,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        /// Fixar, no canto. ⚠️ Alvo próprio de 44dp **fora** do toque do herói: o
        /// herói sintoniza, e um canto que sintonizasse quando se quis fixar
        /// abriria um filme por engano.
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .requiredSize(44.dp)
                .clip(CircleShape)
                .clickable(
                    onClickLabel = if (fixado) "desafixar canal" else "fixar canal",
                    onClick = aoFixar,
                ),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = if (fixado) "★" else "☆",
                color = if (fixado) Cores.destaque else Cores.texto,
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

/// Um cartão da fileira de sintonia — o `CartaoDeCanal` da TV, no tamanho do dedo.
@Composable
private fun CartaoDeCanal(
    quadro: QuadroNoAr,
    agoraMs: Long,
    arte: String?,
    escolhido: Boolean,
    fixado: Boolean,
    /// ⚠️ **Este cartão está sintonizando agora.** Ver o véu com o rodinho lá
    /// embaixo — e o comentário do `aoTocar`, na chamada.
    sintonizando: Boolean,
    aoTocar: () -> Unit,
) {
    val duracao = (quadro.terminaMs - quadro.comecaMs).takeIf { it > 0 }
    val andado = duracao?.let {
        ((agoraMs - quadro.comecaMs).toFloat() / it.toFloat()).coerceIn(0f, 1f)
    }
    val forma = RoundedCornerShape(6.dp)

    Column(
        /// ⚠️ Sem largura fixa: na TV o cartão tem 139dp porque a fileira é
        /// infinita; aqui ele divide a linha com o irmão, e travar 139 deixaria
        /// uma faixa morta no tablet.
        modifier = Modifier.fillMaxWidth().clickable(onClick = aoTocar),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Box(Modifier.fillMaxWidth().aspectRatio(16f / 9f).clip(forma).background(Cores.fundoElevado)) {
            if (arte != null) {
                AsyncImage(
                    model = arte,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            /// ⚠️ **O que não está no herói leva um véu.** Na TV quem marca o
            /// escolhido é o foco; aqui não há foco, e sem uma marca a fileira
            /// não diz qual destes é o que está desenhado logo acima.
            if (!escolhido && !sintonizando) {
                Box(Modifier.fillMaxSize().background(Cores.fundo.copy(alpha = 0.45f)))
            }

            /// ## ⚠️ O rodinho de sintonizar — 18/08/2026
            ///
            /// > «tem alguns programas que simplesmente não iniciam, seria legal
            /// > ao clicar, caso precise carregar algo, tu colocar um loading»
            ///
            /// Entre o toque e o primeiro quadro há um plano de reprodução e,
            /// quase sempre, uma sessão de transcodificação abrindo no servidor.
            /// Isso leva segundos, e **até agora a tela não dizia nada** — o
            /// dedo tocava e o mundo ficava igual, que é indistinguível de um
            /// toque que não pegou.
            if (sintonizando) {
                Box(
                    Modifier.fillMaxSize().background(Cores.fundoAfundado.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = Cores.destaque,
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(26.dp),
                    )
                }
            }

            /// O número em selo, como na TV.
            Text(
                text = quadro.numero,
                style = RotuloMiudo,
                color = Cores.fundoAfundado,
                modifier = Modifier
                    .padding(6.dp)
                    .background(Cores.destaque, RoundedCornerShape(3.dp))
                    .padding(horizontal = 5.dp, vertical = 2.dp),
            )

            if (fixado) {
                Text(
                    "★",
                    color = Cores.destaque,
                    style = Tipo.pilula,
                    modifier = Modifier.align(Alignment.TopEnd).padding(6.dp),
                )
            }

            andado?.let {
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Cores.linha),
                ) {
                    Box(Modifier.fillMaxWidth(it).height(3.dp).background(Cores.destaque))
                }
            }
        }

        Text(
            text = quadro.canalNome.uppercase(),
            style = RotuloMiudo,
            color = if (escolhido) Cores.destaqueQuente else Cores.textoApagado,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = quadro.titulo,
            style = Tipo.pilula,
            fontWeight = FontWeight.Medium,
            color = Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/// `09:20` — a hora de um instante.
private fun hora(ms: Long): String {
    if (ms <= 0) return "--:--"
    val f = java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault())
    return f.format(java.util.Date(ms))
}

/// O rótulo miúdo da TV, com o mesmo corpo e o mesmo espaçamento.
private val RotuloMiudo = Tipo.rotulo.copy(fontSize = 9.sp, letterSpacing = 0.12.em)


/// ## A programação — a grade da TV, no celular
///
/// ⚠️ Ela faltava, e a folha da primeira versão dizia por quê: «doze horas a
/// 1,6dp por minuto dão 1152dp de rolagem lateral dentro de uma tela que já rola
/// pra baixo — dois eixos disputando o mesmo dedo». O dono pediu, e o argumento
/// era fraco: **dois eixos só disputam quando os dois são a mesma coisa**. Aqui a
/// página rola pra baixo (canais) e a grade rola pro lado (tempo) — são perguntas
/// diferentes, e o dedo sabe qual está fazendo.
///
/// ⚠️ **Os nomes ficam parados à esquerda e só o tempo corre.** É como toda grade
/// de TV do mundo funciona, e sem isso rolar três horas pra frente deixa a pessoa
/// olhando retângulos sem saber de quem são.
@Composable
private fun Programacao(
    agoraMs: Long,
    doOdeon: dev.odeon.android.dados.GradeDoOdeon?,
    guia: dev.odeon.android.dados.Guia?,
    externos: List<dev.odeon.android.dados.CanalNoAr>,
) {
    if (agoraMs <= 0) return
    val rolagem = rememberScrollState()

    /// ⚠️ A grade começa **na hora cheia anterior ao agora**, e não no agora:
    /// começando no agora, o programa em curso apareceria cortado pela borda
    /// esquerda — e o que está no ar é justamente o que se quer ver inteiro.
    val inicio = agoraMs - (agoraMs % 3_600_000L)

    /// ⚠️ O `remember` não é economia: sem ele a grade inteira seria refeita a
    /// cada segundo do relógio, 60 vezes por minuto, para desenhar exatamente os
    /// mesmos retângulos.
    val faixas = remember(doOdeon, guia, externos, inicio) {
        val daCasa = doOdeon?.canais.orEmpty().map { canal ->
            canal.nome to doOdeon?.programas.orEmpty()
                .filter { it.canal == canal.slug }
                .mapNotNull { p ->
                    val i = emMillis(p.comeca)
                    val f = emMillis(p.termina)
                    if (i > 0 && f > i) Triple(p.title, i, f) else null
                }
        }
        /// ⚠️ A programação dos externos vem do **guia**, não do canal: o
        /// `CanalNoAr` traz só o que está no ar agora. Sem cruzar com o guia, cada
        /// canal de fora teria um retângulo só — e uma grade com um bloco por
        /// linha não é grade, é lista com espaço desperdiçado.
        val porCanal = guia?.programas.orEmpty().groupBy { it.canalId }
        val deFora = externos.map { c ->
            c.name to porCanal[c.id].orEmpty().mapNotNull { p ->
                val i = emMillis(p.comeca)
                val f = emMillis(p.termina)
                if (i > 0 && f > i) Triple(p.title, i, f) else null
            }
        }
        (daCasa + deFora).filter { it.second.isNotEmpty() }
    }

    if (faixas.isEmpty()) return

    val horas = 12
    val largura = (horas * 60 * LARGURA_DO_MINUTO).dp
    val agora = ((agoraMs - inicio) / 60_000f * LARGURA_DO_MINUTO).dp

    Row(Modifier.padding(start = 16.dp, top = 4.dp)) {
        /// A coluna dos nomes, **parada**.
        Column(Modifier.width(84.dp)) {
            Spacer(Modifier.height(22.dp))
            faixas.forEach { (nome, _) ->
                Box(Modifier.height(46.dp), contentAlignment = Alignment.CenterStart) {
                    Text(
                        nome.uppercase(),
                        style = RotuloMiudo,
                        color = Cores.textoApagado,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
            }
        }

        Box(Modifier.horizontalScroll(rolagem)) {
            Column(Modifier.width(largura)) {
                /// A régua das horas.
                Box(Modifier.fillMaxWidth().height(22.dp)) {
                    for (h in 0 until horas) {
                        Text(
                            hora(inicio + h * 3_600_000L),
                            style = RotuloMiudo,
                            color = Cores.destaqueApagado,
                            modifier = Modifier.padding(start = (h * 60 * LARGURA_DO_MINUTO).dp),
                        )
                    }
                }

                faixas.forEach { (_, blocos) ->
                    Box(Modifier.fillMaxWidth().height(46.dp)) {
                        blocos.forEach { (titulo, i, f) ->
                            val de = ((i - inicio) / 60_000f * LARGURA_DO_MINUTO)
                            val ate = ((f - inicio) / 60_000f * LARGURA_DO_MINUTO)
                            if (ate > 0 && de < horas * 60 * LARGURA_DO_MINUTO) {
                                val noAr = agoraMs in i until f
                                Box(
                                    Modifier
                                        .padding(start = de.coerceAtLeast(0f).dp, end = 2.dp, bottom = 4.dp)
                                        .width((ate - de.coerceAtLeast(0f)).dp)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(4.dp))
                                        /// ⚠️ O que está **no ar** é o único aceso:
                                        /// numa grade de doze horas, sem isso não
                                        /// há como achar o agora sem contar horas
                                        /// na régua.
                                        .background(
                                            if (noAr) Cores.destaque.copy(alpha = 0.22f)
                                            else Cores.fundoElevado,
                                        ),
                                    contentAlignment = Alignment.CenterStart,
                                ) {
                                    Text(
                                        titulo,
                                        style = Tipo.pilula,
                                        color = if (noAr) Cores.destaqueQuente else Cores.textoApagado,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.padding(horizontal = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }

            /// A agulha do agora. ⚠️ Ela é **vermelha**, como o «NO AR» do herói —
            /// é a mesma coisa dita de outro jeito, e usar duas cores para um só
            /// fato faria a grade parecer falar de outra hora.
            Box(
                Modifier
                    .padding(start = agora)
                    .width(2.dp)
                    .height((22 + faixas.size * 46).dp)
                    .background(Cores.perigo),
            )
        }
    }
}

/// 1,6dp por minuto — o número da TV, e é ele que faz doze horas caberem em
/// 1152dp de rolagem.
private const val LARGURA_DO_MINUTO = 1.6f

/// Como se abre um programa do ao vivo. **Uma regra, para todos os canais.**
///
/// ## ⚠️ São dois tipos de canal, e eu tratei como um — 18/08/2026
///
/// Medido: `não sintonizou odeon-1: HTTP 400`. O servidor recusa, e com razão.
///
/// | tipo | o que é | como se abre |
/// |---|---|---|
/// | **externo** (ErsatzTV, M3U) | uma transmissão de verdade, já saindo | `sintonizar` — pega o stream |
/// | **do Odeon** | uma **grade calculada** sobre o acervo; não existe stream nenhum | abre o arquivo no ponto em que ele estaria |
///
/// Um canal do Odeon não é transmitido por ninguém: o servidor calcula que «às
/// 20h o canal 1 está no minuto 34 do Batman» e o cliente abre o arquivo ali. É
/// por isso que o caminho do arquivo existia — e eu o apaguei achando que era
/// duplicação.
///
/// ⚠️ **O discriminador é o `programaId`**, que a própria folha do modelo já
/// explicava: «`null` nos canais do Odeon, que não têm EPG externo». Estava
/// escrito, e eu não li antes de simplificar.
///
/// ⚠️ E a lição das últimas horas: «é tudo a mesma coisa?» era **quase** certo.
/// O caminho tem de ser um só **por tipo de canal** — o que não pode é variar
/// conforme o programa que está no ar naquele minuto, que era o defeito
/// original.
private fun abrir(
    quadro: QuadroNoAr,
    agoraMs: Long,
    aoSintonizarDeFora: (String, String) -> Unit,
    aoSintonizar: (QuadroNoAr, Double) -> Unit,
) {
    val externo = quadro.programaId != null
    if (externo) {
        aoSintonizarDeFora(quadro.canalId, quadro.canalNome)
    } else {
        aoSintonizar(quadro, quantoJaPassou(agoraMs, quadro))
    }
}
