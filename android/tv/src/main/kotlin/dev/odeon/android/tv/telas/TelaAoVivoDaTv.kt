package dev.odeon.android.tv.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.odeon.android.dados.CanalNoAr
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Focavel
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.TipoDaSala
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.border
import androidx.activity.compose.BackHandler
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.Serifada
import dev.odeon.android.ui.aovivo.ModeloAoVivo
import dev.odeon.android.ui.aovivo.QuadroNoAr
import dev.odeon.android.ui.aovivo.emCartaz
import dev.odeon.android.ui.aovivo.emMillis
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.focusable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import kotlinx.coroutines.delay

/// O ao vivo — a única tela deste app que a **TV ganhou antes do celular**.
///
/// ## Por que ela existe, e por que aqui primeiro
///
/// Um canal ao vivo é uma coisa de sala. Zapear com o polegar num ônibus é
/// percorrer uma lista; zapear com o controle no sofá **é televisão** — e a
/// diferença não é de tamanho de tela, é de gesto. A tecla que troca de canal e
/// o número que se digita não têm equivalente num celular.
///
/// ## As três partes, e o que cada uma responde
///
/// | | a pergunta |
/// |---|---|
/// | **o que está no ar** | «o que eu vejo **agora**?» — capa inteira, título em letreiro, quanto falta, e o botão de entrar |
/// | **a sintonia** | «o que mais está passando?» — a fileira de canais, cada um com o que tem no ar |
/// | **a linha do tempo** | «o que vem depois?» — a grade, com a agulha do agora |
///
/// ⚠️ **A ordem não é de importância, é de distância.** Quem liga a TV quer a
/// primeira; quem já está aqui quer a segunda; quem está decidindo a noite quer a
/// terceira. Descer é ir do imediato ao planejado.
@Composable
fun TelaAoVivoDaTv(
    modelo: ModeloAoVivo,
    aoTocar: (obraId: String, arquivoId: String, titulo: String, comecarEm: Double, capa: String?, canalId: String?) -> Unit,
    /// ⚠️ Canal **sem obra** — o de M3U externo. Ele não abre um filme, abre
    /// uma transmissão, e por isso tem porta própria (ver `TelaDoCanalAoVivoDaTv`).
    aoSintonizarDeFora: (canalId: String, nome: String) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier,
    saidaEsquerda: FocusRequester? = null,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    if (estado.erro != null && estado.canais.isEmpty() && estado.doOdeon == null) {
        Recado(titulo = "nada no ar", detalhe = estado.erro, modifier = modifier) {
            BotaoDaSala("tentar de novo", modelo::carregar, principal = true)
        }
        return
    }

    /// ⚠️ **Os canais do Odeon vêm primeiro**, e não é ordem alfabética nem de
    /// número: são os únicos que a casa programa, os únicos com obra e arquivo
    /// atrás, e portanto os únicos em que «sintonizar» leva a algum lugar hoje.
    /// Pôr depois dos de M3U seria enterrar o que funciona embaixo do que não.
    val doOdeon = estado.doOdeon
    /// ⚠️ **Os fixados sobem, na ordem em que foram fixados.**
    ///
    /// São 23 canais, e os seus ficam onde o servidor os pôs. A ordem de fixação
    /// é melhor que alfabética ou por número: ela é a resposta que a pessoa deu
    /// à pergunta «quais são os seus», e a primeira resposta é a mais forte.
    ///
    /// O resto mantém a ordem do servidor — os do Odeon primeiro, depois os de
    /// fora —, porque uma lista que se reordena sozinha é uma lista em que não se
    /// decora onde as coisas estão.
    val noAr = remember(estado) {
        val todos = emCartaz(estado.agoraMs, doOdeon, estado.canais)
        val fixados = estado.favoritos
        if (fixados.isEmpty()) {
            todos
        } else {
            todos.sortedBy { fixados.indexOf(it.canalId).takeIf { i -> i >= 0 } ?: Int.MAX_VALUE }
        }
    }

    /// ## ⚠️ Digitar o canal — e ele **aparece enquanto se digita**
    ///
    /// O `1` de `101` sozinho não é um canal; é o começo de três. Um controle de
    /// TV resolve isso há quarenta anos do mesmo jeito: mostra o que foi digitado
    /// num canto e espera um instante antes de sintonizar.
    ///
    /// Sem esse retorno, digitar vira adivinhação — a pessoa aperta `1`, nada
    /// acontece, e ela não sabe se a tecla não funciona ou se o app está
    /// esperando o resto. É o §8b na forma mais literal.
    var digitado by remember { mutableStateOf("") }

    /// O bloco cuja ficha está aberta. `null` é a grade normal.
    var blocoAberto by remember { mutableStateOf<BlocoDaGrade?>(null) }

    /// ⚠️ **1,2s a partir da última tecla**, e o contador reinicia a cada
    /// dígito — é o que deixa digitar `101` sem que o `1` sintonize sozinho no
    /// caminho. Depois de sintonizar, o buffer some.
    LaunchedEffect(digitado) {
        if (digitado.isEmpty()) return@LaunchedEffect
        delay(1_200)
        noAr.firstOrNull { it.numero == digitado }?.let { modelo.escolher(it.canalId) }
        digitado = ""
    }

    Box(Modifier.fillMaxSize()) {
    LazyColumn(
        contentPadding = PaddingValues(vertical = Sala.overscanV),
        verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
        modifier = modifier
            .fillMaxSize()
            /// ⚠️ `onKeyEvent` **antes** de `focusable`, e no pai de todos os
            /// alvos: eventos de tecla sobem do nó focado pra fora, então este
            /// bloco vê o dígito depois de os cartões terem ignorado. É a mesma
            /// ordem que o player desta casa já usa, e escrita ao contrário ela
            /// simplesmente não roda.
            .onKeyEvent { evento ->
                if (evento.type != KeyEventType.KeyDown) return@onKeyEvent false
                val digito = DIGITOS[evento.key]
                if (digito != null) {
                    /// Três é o tamanho do maior número de canal da casa
                    /// (`101`…`103`). Passar disso recomeça, em vez de acumular
                    /// uma string que nunca vai casar com nada.
                    digitado = (if (digitado.length >= 3) "" else digitado) + digito
                    true
                } else {
                    false
                }
            },
    ) {
        item {
            val escolhidoAgora = estado.escolhido ?: noAr.firstOrNull()?.canalId
            NoAr(
                estado.agoraMs, noAr,
                fixado = escolhidoAgora != null && escolhidoAgora in estado.favoritos,
                aoFixar = { escolhidoAgora?.let { modelo.alternarFavorito(it) } },
                canais = estado.canais, escolhido = estado.escolhido, modelo = modelo,
                aoTocar = aoTocar, aoSintonizarDeFora = aoSintonizarDeFora,
            )
        }

        if (estado.canais.isNotEmpty() || noAr.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = Sala.overscanH)) {
                    RotuloDeSecao("sintonia")
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = "▲▼ ZAPEIA",
                        style = RotuloMiudo,
                        color = Cores.textoApagado,
                    )
                }
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
                    contentPadding = PaddingValues(horizontal = Sala.overscanH),
                ) {
                    itemsIndexed(noAr) { indice, quadro ->
                        CartaoDeCanal(
                            quadro = quadro,
                            fixado = quadro.canalId in estado.favoritos,
                            agoraMs = estado.agoraMs,
                            arte = modelo.arte(quadro.arte),
                            saidaEsquerda = if (indice == 0) saidaEsquerda else null,
                            escolhido = quadro.canalId == estado.escolhido,
                            aoFocar = { modelo.escolher(quadro.canalId) },
                            /// ⚠️ Sem arquivo ou sem obra, `OK` **não faz nada**
                            /// — e é melhor que abrir um recado. O canal já diz
                            /// no herói que não toca aqui; repetir no clique
                            /// seria o §8b ao contrário, informar duas vezes o
                            /// que não se pode resolver.
                            aoEscolher = {
                                val obra = quadro.obraId
                                val arquivo = quadro.arquivoId
                                if (obra == null || arquivo == null) {
                                    aoSintonizarDeFora(quadro.canalId, quadro.canalNome)
                                } else {
                                    aoTocar(
                                        obra,
                                        arquivo,
                                        quadro.titulo,
                                        quantoJaPassou(estado.agoraMs, quadro),
                                        modelo.arte(quadro.arte),
                                        quadro.canalId,
                                    )
                                }
                            },
                        )
                    }
                }
            }
        }

        if (noAr.isNotEmpty()) {
            item {
                Column(Modifier.padding(horizontal = Sala.overscanH)) {
                    RotuloDeSecao("a linha do tempo")
                }
            }
            item { LinhaDoTempoAoVivo(
                    estado.agoraMs, doOdeon, estado.canais, estado.guia,
                    estado.lembretes, { blocoAberto = it }, modelo,
                ) }
        }
    }

    /// ## O que está sendo digitado, grande, no canto
    ///
    /// ⚠️ **Ele mora fora da lista de propósito.** Dentro dela ele rolaria com o
    /// conteúdo, e o retorno de uma tecla que se acabou de apertar não pode
    /// depender de onde a pessoa parou de rolar.
    ///
    /// Canto superior direito, no lugar do relógio — porque é exatamente ali que
    /// uma televisão mostra o canal digitado desde sempre, e porque enquanto se
    /// digita a hora é a informação menos urgente da tela.
    if (digitado.isNotEmpty()) {
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = Sala.overscanH, vertical = Sala.overscanV)
                .background(Cores.fundoAfundado.copy(alpha = 0.92f), RoundedCornerShape(8.dp))
                .padding(horizontal = 13.dp, vertical = 7.dp),
        ) {
            Text(
                text = digitado,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = Serifada,
                    fontSize = 26.sp,
                    color = Cores.destaqueQuente,
                ),
            )
        }
    }

    /// ⚠️ A ficha é a **última camada** da tela, e não um filho da grade: ela
    /// cobre tudo, e o `Focavel` de baixo não pode continuar recebendo tecla
    /// enquanto ela está aberta.
    blocoAberto?.let { bloco ->
        FichaDoBloco(
            bloco = bloco,
            lembrado = bloco.programaId != null && bloco.programaId in estado.lembretes,
            aoAlternarLembrete = { bloco.programaId?.let { modelo.alternarLembrete(it) } },
            aoFechar = { blocoAberto = null },
        )
    }
    }
}

/// Um programa no ar, com o canal junto — o que a tela desenha em três lugares.
///
/// ⚠️ Ele existe pra **não** fazer a tela reconciliar `CanalDoOdeon` com
/// `ProgramaDoOdeon` três vezes. A grade vem como duas listas que se cruzam por
/// slug, e cruzar num lugar só é o que evita a terceira versão da mesma conta.
private fun quantoJaPassou(agoraMs: Long, q: QuadroNoAr): Double =
    ((agoraMs - q.comecaMs).coerceAtLeast(0L) / 1000.0)

/// `07:19`. O relógio do **servidor**, não o da TV — ver o `ModeloAoVivo`.
private fun hora(ms: Long): String {
    if (ms <= 0) return "--:--"
    val z = java.time.Instant.ofEpochMilli(ms).atZone(java.time.ZoneId.systemDefault())
    return "%02d:%02d".format(z.hour, z.minute)
}

/// A primeira dobra: o que está no ar no canal escolhido.
@Composable
private fun NoAr(
    agoraMs: Long,
    noAr: List<QuadroNoAr>,
    fixado: Boolean,
    aoFixar: () -> Unit,
    canais: List<CanalNoAr>,
    escolhido: String?,
    modelo: ModeloAoVivo,
    aoTocar: (String, String, String, Double, String?, String?) -> Unit,
    aoSintonizarDeFora: (String, String) -> Unit,
) {
    val quadro = noAr.firstOrNull { it.canalId == escolhido } ?: noAr.firstOrNull()

    Box(Modifier.fillMaxWidth().height(204.dp)) {
        /// ⚠️ **A arte sangra pela direita e o texto mora na esquerda**, que é o
        /// desenho da web. O degradê horizontal é o que faz as duas coisas
        /// caberem sem uma caixa em volta do texto: a capa **vira** fundo em vez
        /// de ser cortada por uma borda.
        quadro?.arte?.let { caminho ->
            AsyncImage(
                model = modelo.arte(caminho),
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

        Column(Modifier.align(Alignment.CenterStart).padding(horizontal = Sala.overscanH)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                /// O ponto vermelho e o «NO AR». É a única coisa vermelha desta
                /// casa, e é de propósito: vermelho aqui não é erro, é a luz do
                /// estúdio — a convenção que toda televisão do mundo já ensinou.
                Box(
                    Modifier
                        .width(10.dp)
                        .height(10.dp)
                        .clip(RoundedCornerShape(5.dp))
                        .background(Cores.perigo),
                )
                Spacer(Modifier.width(10.dp))
                Text("NO AR", style = RotuloMiudo, color = Cores.perigo)
                if (quadro != null) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = quadro.canalNome,
                        style = RotuloMiudo,
                        color = Cores.textoApagado,
                    )
                    quadro.categoria?.let {
                        Spacer(Modifier.width(16.dp))
                        Text(it, style = RotuloMiudo, color = Cores.textoApagado)
                    }
                }
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = quadro?.titulo ?: "sem programação",
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = Serifada,
                    fontSize = 31.sp,
                    lineHeight = 34.sp,
                    color = Cores.texto,
                ),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )

            if (quadro != null) {
                Spacer(Modifier.height(11.dp))
                /// `COMEÇOU 07:19 ──●── FALTAM 91 MIN` — a barra é o programa, e
                /// não o filme: ela conta o que **a transmissão** já andou, que é
                /// a pergunta de quem chega no meio.
                val duracao = (quadro.terminaMs - quadro.comecaMs).coerceAtLeast(1L)
                val andado = ((agoraMs - quadro.comecaMs).toFloat() / duracao).coerceIn(0f, 1f)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "COMEÇOU ${hora(quadro.comecaMs)}",
                        style = RotuloMiudo,
                        color = Cores.textoApagado,
                    )
                    Spacer(Modifier.width(16.dp))
                    Box(
                        Modifier
                            .width(252.dp)
                            .height(3.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Cores.linha),
                    ) {
                        Box(
                            Modifier
                                .fillMaxWidth(andado)
                                .fillMaxSize()
                                .background(Cores.destaque),
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Text(
                        text = "FALTAM ${((quadro.terminaMs - agoraMs) / 60000).coerceAtLeast(0)} MIN",
                        style = RotuloMiudo,
                        color = Cores.textoApagado,
                    )
                }

                Spacer(Modifier.height(12.dp))

                /// ⚠️ **Sintonizar entra no filme onde a transmissão está** — e
                /// isso é a metáfora inteira funcionando. Ligar a TV no meio de
                /// um filme te dá o meio do filme; é o que uma emissora faz, e é
                /// o que separa isto de uma lista de reprodução.
                ///
                /// ⚠️ **O `canalId` vai junto, e é o que faz a metáfora durar
                /// mais que um programa.** Sem ele o player recebe um arquivo e
                /// mais nada; quando o arquivo acaba ele não tem a quem
                /// perguntar o que vem a seguir, e foi exatamente isso que o
                /// dono viu na TCL: «após um tempo rodando ao vivo o app morreu,
                /// mesmo filme não mudou».
                ///
                /// O parágrafo acima já estava escrito aqui **antes** de alguém
                /// ver um programa terminar. Ele descrevia o primeiro minuto e
                /// afirmava o resto. Fica, porque agora é verdade — mas foi
                /// preciso um filme chegar ao fim numa sala pra ele virar
                /// verdade.
                val obra = quadro.obraId
                val arquivo = quadro.arquivoId
                /// ⚠️ **Fixar mora aqui**, e não num toque longo no cartão: o
                /// `Focavel` desta casa não tem clique longo, e ensinar um gesto
                /// escondido num controle de cinco teclas é esconder a função.
                /// No herói ela fica ao lado de «sintonizar», que é onde o canal
                /// escolhido já está sendo olhado.
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                if (arquivo != null && obra != null) {
                    BotaoDaSala(
                        rotulo = "▸ sintonizar",
                        principal = true,
                        aoEscolher = {
                            aoTocar(
                                obra,
                                arquivo,
                                quadro.titulo,
                                quantoJaPassou(agoraMs, quadro),
                                modelo.arte(quadro.arte),
                                quadro.canalId,
                            )
                        },
                    )
                } else {
                    /// ⚠️ **Canal sem obra agora toca**, e a frase «este canal
                    /// ainda não toca na sala» saiu daqui.
                    ///
                    /// Ela era honesta enquanto era verdade: o `:tv` nasceu sem a
                    /// dependência de HLS, e `sintonizar` devolve exatamente uma
                    /// playlist. Metade da sintonia ficou decorativa por falta de
                    /// uma linha no `build.gradle.kts` — não de código.
                    BotaoDaSala(
                        rotulo = "▸ sintonizar",
                        principal = true,
                        aoEscolher = { aoSintonizarDeFora(quadro.canalId, quadro.canalNome) },
                    )
                }
                BotaoDaSala(
                    rotulo = if (fixado) "★ fixado" else "☆ fixar",
                    aoEscolher = aoFixar,
                )
                }
            }
        }

        /// O relógio grande, no canto. Ele é o do servidor — o mesmo que desenha
        /// a agulha da grade lá embaixo.
        Text(
            text = hora(agoraMs),
            style = androidx.compose.ui.text.TextStyle(
                fontFamily = Serifada,
                fontSize = 28.sp,
                color = Cores.texto.copy(alpha = 0.9f),
            ),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(horizontal = Sala.overscanH, vertical = 10.dp),
        )
    }
}

/// Um canal na fileira da sintonia.
///
/// ⚠️ **Focar já troca o canal do herói**, sem apertar nada. É o zapear: numa TV
/// de verdade a tecla de canal já mostra o canal, e não «seleciona pra depois
/// confirmar». Apertar `OK` entra no filme.
@Composable
private fun CartaoDeCanal(
    quadro: QuadroNoAr,
    fixado: Boolean,
    agoraMs: Long,
    arte: String?,
    escolhido: Boolean,
    saidaEsquerda: FocusRequester?,
    aoFocar: () -> Unit,
    aoEscolher: () -> Unit,
) {
    val forma = RoundedCornerShape(8.dp)
    Focavel(
        aoEscolher = aoEscolher,
        forma = forma,
        aoFocar = { se -> if (se) aoFocar() },
        modifier = Modifier.width(139.dp),
    ) { focado ->
        /// ⚠️ **Um respiro embaixo**, e ele é o conserto de um corte que só a
        /// foto na TV mostrou: a linha `09:20 — 10:55` aparecia cortada ao meio
        /// no cartão **focado** e inteira nos outros.
        ///
        /// A causa é o `.clip(forma)` do [Focavel]: ele recorta o conteúdo na
        /// borda da caixa, e o texto encostado no pé sobra por causa do descida
        /// da fonte. Sem foco não há borda desenhada, então o mesmo corte
        /// existia e ninguém via — o defeito não nasceu com o foco, o foco só o
        /// tornou visível ao desenhar uma linha exatamente onde ele acontece.
        Column(Modifier.padding(bottom = 4.dp)) {
            Box(Modifier.fillMaxWidth().height(67.dp).clip(forma)) {
                if (arte != null) {
                    AsyncImage(
                        model = arte,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )
                }
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            listOf(Cores.fundo.copy(alpha = 0.72f), Cores.fundo.copy(alpha = 0.2f)),
                        ),
                    ),
                )
                Row(Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = quadro.numero,
                        style = RotuloMiudo,
                        color = Cores.fundoAfundado,
                        modifier = Modifier
                            .background(Cores.destaque, RoundedCornerShape(3.dp))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                    Spacer(Modifier.width(8.dp))
                    /// ⚠️ **O logo foi tentado e revertido**, e o motivo é do
                    /// dado, não do desenho.
                    ///
                    /// `CanalNoAr.logo_url` vem dos canais de M3U como URL
                    /// **externa absoluta**, e a `urlDaArte` desta casa prefixa
                    /// `$base/artwork/` cegamente: o resultado foi
                    /// `…/artwork/https://…`, que é 404. Na TCL os canais de fora
                    /// ficaram com o número e **nada mais** — o nome tinha saído
                    /// pra dar lugar a uma imagem que nunca chegou.
                    ///
                    /// Trocar informação por espaço vazio é pior que não ter
                    /// logo. Pra ele voltar, o logo externo precisa de um caminho
                    /// que não passe pelo `/artwork/` — e isso é conversa de
                    /// servidor, não de tela.
                    Text(
                        text = quadro.canalNome.uppercase(),
                        style = RotuloMiudo,
                        color = if (focado || escolhido) Cores.destaqueQuente else Cores.textoApagado,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                /// A barrinha do quanto já passou, colada no pé do cartão — a
                /// mesma ideia da R4 do celular: o progresso é **do objeto**, não
                /// uma legenda embaixo dele.
                val duracao = (quadro.terminaMs - quadro.comecaMs).coerceAtLeast(1L)
                Box(
                    Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(((agoraMs - quadro.comecaMs).toFloat() / duracao).coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(Cores.destaque),
                )
            }
            Spacer(Modifier.height(8.dp))
            /// ⚠️ **Um recuo lateral só no texto**, e a arte continua encostando
            /// na borda de propósito.
            ///
            /// A borda de 3dp do [Focavel] é desenhada **por dentro** dos limites
            /// da caixa, então ela pinta em cima dos 3dp iniciais do conteúdo. Na
            /// arte isso não custa nada — é fundo. Na primeira letra custa: na
            /// TCL, `09:20` aparecia como `9:20` no cartão focado, com o zero
            /// comido pela borda. Um número com um dígito a menos não é um número
            /// apertado, é outro número.
            val recuo = Modifier.padding(horizontal = 4.dp)
            Text(
                text = quadro.titulo,
                style = CorpoMiudo,
                color = if (focado) Cores.texto else Cores.textoApagado,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = recuo,
            )
            Text(
                text = "${hora(quadro.comecaMs)} — ${hora(quadro.terminaMs)}",
                style = RotuloMiudo,
                color = Cores.textoApagado,
                modifier = recuo,
            )
            /// ⚠️ **«a seguir» já vinha em toda resposta e não aparecia.**
            ///
            /// `CanalNoAr.aSeguir` chega desde o primeiro dia; a tela lia o campo
            /// e não fazia nada com ele. É a pergunta de quem zapeia — «vale
            /// esperar?» — respondida sem custar um byte a mais de rede.
            quadro.aSeguir?.takeIf { it.isNotBlank() }?.let { proximo ->
                Text(
                    text = "a seguir · $proximo",
                    style = RotuloMiudo,
                    color = Cores.destaqueApagado,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = recuo,
                )
            }
        }
    }
}

/// A grade: uma faixa por canal, o tempo correndo pra direita.
///
/// ## ⚠️ Ela é desenhada em **minutos**, e não em «um bloco por programa»
///
/// A tentação é uma `Row` de blocos com `weight` proporcional à duração. Não
/// serve: os canais não começam nem terminam juntos, e com `weight` cada faixa
/// ganharia uma escala de tempo própria — 20:00 do canal 1 cairia num x
/// diferente do 20:00 do canal 2, e a agulha do agora deixaria de significar
/// alguma coisa.
///
/// Aqui a régua é uma só pra tela inteira: [LARGURA_DO_MINUTO] dp por minuto,
/// contados a partir do início da grade. Cada bloco é posicionado e medido nessa
/// régua, e por isso a coluna vertical do agora corta os canais no mesmo
/// instante.
@Composable
private fun LinhaDoTempoAoVivo(
    agoraMs: Long,
    doOdeon: dev.odeon.android.dados.GradeDoOdeon?,
    externos: List<CanalNoAr>,
    guia: dev.odeon.android.dados.Guia?,
    lembretes: Set<Int>,
    aoAbrirBloco: (BlocoDaGrade) -> Unit,
    modelo: ModeloAoVivo,
) {
    val rolagemDaGrade = androidx.compose.foundation.rememberScrollState()
    if (agoraMs <= 0) return

    /// ⚠️ A grade começa **na hora cheia anterior ao agora**, e não no `agora`.
    /// Começando no agora, o programa em curso apareceria cortado pela borda
    /// esquerda — e o que está no ar é justamente o que se quer ver inteiro.
    val inicio = agoraMs - (agoraMs % 3_600_000L)
    val ondeEstaOAgora = ((agoraMs - inicio) / 60_000f) * LARGURA_DO_MINUTO

    /// ⚠️ As duas fontes achatadas num formato só, **fora do desenho**.
    ///
    /// Podia-se desenhar cada fonte no seu laço e economizar este passo. Não
    /// dá: o `remember` abaixo é o que impede a grade inteira de ser refeita a
    /// cada segundo do relógio, e `agoraMs` anda de segundo em segundo. Sem
    /// ele, o custo desta tela seria pago 60 vezes por minuto para desenhar
    /// exatamente os mesmos retângulos.
    val faixas = remember(doOdeon, externos, guia, inicio) {
        val daCasa = doOdeon?.canais.orEmpty().map { canal ->
            FaixaDaGrade(
                nome = canal.nome,
                blocos = doOdeon?.programas.orEmpty()
                    .filter { it.canal == canal.slug }
                    .mapNotNull { p ->
                        val i0 = emMillis(p.comeca)
                        val i1 = emMillis(p.termina)
                        if (i0 <= 0 || i1 <= i0) {
                            null
                        } else {
                            BlocoDaGrade(p.title, i0, i1, categoria = p.categoria)
                        }
                    },
            )
        }

        /// ⚠️ A programação dos externos vem do **guia**, não do canal: o
        /// `CanalNoAr` traz só o que está no ar agora, um título e nada mais.
        /// Sem cruzar com o guia, a faixa de cada canal de fora teria um único
        /// retângulo — o de agora —, e uma grade com um bloco por linha não é
        /// uma grade, é uma lista com espaço desperdiçado.
        val porCanal = guia?.programas.orEmpty().groupBy { it.canalId }
        val deFora = externos.map { c ->
            FaixaDaGrade(
                nome = listOfNotNull(c.number, c.name).joinToString(" "),
                blocos = porCanal[c.id].orEmpty().mapNotNull { p ->
                    val i0 = emMillis(p.comeca)
                    val i1 = emMillis(p.termina)
                    if (i0 <= 0 || i1 <= i0) {
                        null
                    } else {
                        BlocoDaGrade(
                            titulo = p.title,
                            comecaMs = i0,
                            terminaMs = i1,
                            programaId = p.id,
                            descricao = p.description,
                            ano = p.year,
                            categoria = p.categoria,
                        )
                    }
                },
            )
        }

        daCasa + deFora
    }

    if (faixas.isEmpty()) return
    val quantosCanais = faixas.size

    Box(Modifier.fillMaxWidth().padding(horizontal = Sala.overscanH)) {
        Column {
            /// ⚠️ **Uma faixa só pro `AGORA`, acima da régua** — e ela existe por
            /// causa de uma colisão que só a foto mostrou.
            ///
            /// O badge e os rótulos de hora estavam na mesma linha, e na TCL a
            /// agulha calhou de cair perto das 09:00: o `AGORA` desenhou **por
            /// cima** do `09:00`, e o que se lia era `09:0AGORA`.
            ///
            /// Na web isso não acontece porque lá a agulha estava longe de uma
            /// hora cheia na hora do screenshot — ou seja, o desenho de lá tem o
            /// mesmo defeito e ninguém tinha visto. Reservar a faixa resolve pros
            /// dois casos em vez de depender de onde o relógio está.
            Spacer(Modifier.height(ALTURA_DO_AGORA))

            /// ## A régua de horas
            ///
            /// ⚠️ Ela existe pra a agulha significar alguma coisa. Uma linha
            /// vermelha sozinha diz «aqui»; com as horas escritas em volta, ela
            /// diz **que horas** são aqui — e é isso que transforma a faixa numa
            /// grade de programação em vez de um gráfico.
            Row(Modifier.padding(start = LARGURA_DO_ROTULO + 10.dp).horizontalScroll(rolagemDaGrade)) {
                repeat(6) { i ->
                    Box(Modifier.width((60f * LARGURA_DO_MINUTO).dp)) {
                        Text(
                            text = hora(inicio + i * 3_600_000L),
                            style = RotuloMiudo,
                            color = Cores.textoApagado,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            /// ⚠️ **As duas fontes na mesma grade** — os canais da casa e os de
            /// fora, um atrás do outro, com a mesma régua de minutos.
            ///
            /// Antes daqui a grade lia só `doOdeon`, e o dono viu o buraco na TV:
            /// «cadê os outros canais fora os odeons? videoteca, canal da Disney».
            /// Os externos vinham de `/api/live/channels` e a programação deles de
            /// `/api/live/guide` — os dois já buscados, os dois já no estado, e
            /// nenhum dos dois desenhado.
            ///
            /// A régua tem de continuar sendo **uma só**: é o que faz a agulha
            /// cortar Odeon e Disney no mesmo instante (ver o bloco dela abaixo).
            /// Por isso as faixas são achatadas num formato comum antes de
            /// desenhar, em vez de cada fonte desenhar do seu jeito.
            faixas.forEach { faixa ->
                /// ⚠️ **Quem é focável agora é o bloco, não a faixa.**
                ///
                /// A faixa inteira era um alvo só, e servia pra rolar. Mas uma
                /// grade em que se vê `Toy Story 3 · 16:46` e não se pode fazer
                /// nada com aquilo é uma tabela, não um guia — e foi o que o dono
                /// pediu pra mudar.
                ///
                /// Com o bloco focável, ◀ ▶ andam de programa em programa e
                /// ▲ ▼ trocam de canal, que é a navegação que todo guia de TV do
                /// mundo já ensinou. E a rolagem horizontal vem de brinde: o foco
                /// puxa a vista, então «olhar pra hoje à noite» deixou de precisar
                /// de um controle próprio.
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = faixa.nome,
                        style = RotuloMiudo,
                        color = Cores.textoApagado,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(LARGURA_DO_ROTULO),
                    )
                    Spacer(Modifier.width(10.dp))
                    /// ⚠️ **Um `ScrollState` só pra todas as faixas.** Elas têm de
                    /// andar juntas: duas faixas em posições horizontais
                    /// diferentes seriam duas réguas de tempo, e a agulha do agora
                    /// cortaria as duas mentindo para uma.
                    Box(
                        Modifier
                            .height(30.dp)
                            .horizontalScroll(rolagemDaGrade),
                    ) {
                        Box(Modifier.width(LARGURA_DA_GRADE_INTEIRA).height(30.dp)) {
                            faixa.blocos.forEach { b ->
                                val comeco = ((b.comecaMs - inicio) / 60_000f) * LARGURA_DO_MINUTO
                                val fim = ((b.terminaMs - inicio) / 60_000f) * LARGURA_DO_MINUTO
                                if (fim <= 0f) return@forEach
                                val esquerda = comeco.coerceAtLeast(0f)
                                val largura = fim - esquerda
                                if (largura <= 0f) return@forEach

                                BlocoDaFaixa(
                                    bloco = b,
                                    agoraMs = agoraMs,
                                    esquerda = esquerda.dp,
                                    largura = largura.dp,
                                    lembrado = b.programaId != null && b.programaId in lembretes,
                                    aoEscolher = { aoAbrirBloco(b) },
                                )
                            }
                        }
                    }
                }
                Spacer(Modifier.height(4.dp))
            }
        }

        /// ## ⚠️ A agulha do agora — e ela é **uma só** pra todos os canais
        ///
        /// É por causa dela que a grade é desenhada em minutos e não com `weight`
        /// por programa. Com `weight`, cada faixa ganharia uma escala de tempo
        /// própria: as 08:00 do Odeon 1 cairiam num x diferente das 08:00 do
        /// Corujão, e uma linha vertical cortando as duas seria uma mentira
        /// desenhada com régua.
        ///
        /// Aqui a régua é a mesma pra tela inteira — [LARGURA_DO_MINUTO] dp por
        /// minuto a partir da hora cheia —, então a agulha corta todos os canais
        /// **no mesmo instante**, que é a única coisa que ela promete.
        ///
        /// ⚠️ Vermelha, e é a segunda vez que esta casa usa vermelho de propósito
        /// (a outra é o «NO AR» lá em cima). Aqui ele não é erro: é a convenção
        /// que toda grade de TV do mundo já ensinou, e trocá-la pelo dourado da
        /// casa deixaria a agulha indistinguível do programa em curso, que já é
        /// dourado.
        Box(
            Modifier
                .padding(
                    start = LARGURA_DO_ROTULO + 10.dp + ondeEstaOAgora.dp,
                    top = ALTURA_DO_AGORA,
                )
                .width(2.dp)
                .height(ALTURA_DA_GRADE + (quantosCanais * 32).dp)
                .background(Cores.perigo),
        )
        Text(
            text = "AGORA",
            style = RotuloMiudo,
            color = Cores.fundo,
            modifier = Modifier
                .padding(start = (LARGURA_DO_ROTULO + 10.dp + ondeEstaOAgora.dp - 26.dp))
                .background(Cores.perigo, RoundedCornerShape(3.dp))
                .padding(horizontal = 6.dp, vertical = 2.dp),
        )
    }
}

/// A coluna dos nomes de canal, à esquerda da grade.
private val LARGURA_DO_ROTULO = 90.dp

/// A altura da régua de horas mais o respiro — é de onde a agulha começa a
/// descer, pra ela não cortar o próprio rótulo `AGORA`.
private val ALTURA_DA_GRADE = 16.dp

/// A faixa reservada pro badge `AGORA`, acima da régua de horas.
///
/// ⚠️ Ela não é margem: é o que impede o badge de escrever por cima de um rótulo
/// de hora quando a agulha cai perto de uma hora cheia. Ver o comentário no
/// corpo — o defeito apareceu na TCL como `09:0AGORA`.
private val ALTURA_DO_AGORA = 16.dp

private const val LARGURA_DO_MINUTO = 1.6f


/// As dez teclas de número do controle.
///
/// ⚠️ Um mapa e não `evento.key.nativeKeyCode - KEYCODE_0`, que é a forma curta e
/// erra: o `Key` do Compose não promete que os dez fiquem em sequência, e alguns
/// controles mandam o teclado numérico (`NumPad`) em vez das teclas de cima. O
/// mapa cobre os dois e não depende de aritmética sobre um código opaco.
private val DIGITOS: Map<Key, String> = mapOf(
    Key.Zero to "0", Key.One to "1", Key.Two to "2", Key.Three to "3", Key.Four to "4",
    Key.Five to "5", Key.Six to "6", Key.Seven to "7", Key.Eight to "8", Key.Nine to "9",
    Key.NumPad0 to "0", Key.NumPad1 to "1", Key.NumPad2 to "2", Key.NumPad3 to "3",
    Key.NumPad4 to "4", Key.NumPad5 to "5", Key.NumPad6 to "6", Key.NumPad7 to "7",
    Key.NumPad8 to "8", Key.NumPad9 to "9",
)


/// Uma linha da grade — um canal, venha ele da casa ou de fora.
///
/// ⚠️ Este tipo existe pra a régua ser **uma só**. As duas fontes chegam com
/// formatos diferentes (`ProgramaDoOdeon` tem `canal`, `ProgramaDoGuia` tem
/// `channel_id`), e desenhar cada uma no seu laço deixaria a agulha vertical
/// dependendo de qual laço desenhou a linha que ela corta.
private data class FaixaDaGrade(val nome: String, val blocos: List<BlocoDaGrade>)

/// Um programa já em milissegundos — as datas de texto ficam do lado de fora.
private data class BlocoDaGrade(
    val titulo: String,
    val comecaMs: Long,
    val terminaMs: Long,
    /// O `programme_id` do guia. `null` num canal do Odeon, que não tem EPG
    /// externo — e por isso não tem lembrete: não há programa a que se agarrar.
    val programaId: Int? = null,
    val descricao: String? = null,
    val ano: Int? = null,
    val categoria: String? = null,
)


/// ## ⚠️ A escala **desta tela**, e por que ela não é a da casa
///
/// O dono viu a grade na TCL e disse: «diminui uns 40% da escala de tudo do ao
/// vivo… para caber mais coisas na tela». O ao vivo é a única tela desta casa
/// que **quer** densidade: uma grade de programação existe pra você comparar
/// canais de relance, e comparar exige ver muitos ao mesmo tempo. Um cartaz
/// exige o contrário.
///
/// Por isso a redução mora aqui e não em [TipoDaSala]: mexer no `rotulo` da sala
/// encolheria a biblioteca, a locadora e o player junto — e o player, palavra do
/// dono, «está perfeito».
///
/// ⚠️ O `letterSpacing` cai **mais** que o corpo (0.28em → 0.12em), e isso não é
/// arredondamento. Em `em` o espaço acompanha a letra, então encolher só o corpo
/// preservaria a proporção de ar entre as letras — e num cartão de 139dp o que
/// estourava não era a altura, era a largura: `TELA QUENTE` virava `TELA Q…` por
/// causa do espaçamento, não do tamanho.
private val RotuloMiudo = TipoDaSala.rotulo.copy(
    fontSize = 9.sp,
    letterSpacing = 0.12.em,
)

/// O corpo miúdo — o título do programa no cartão de canal.
///
/// 12sp a três metros é pequeno, e é uma escolha: aqui o título é a **legenda**
/// de uma arte que já ocupa o cartão inteiro, e quem quer lê-lo tem o herói lá
/// em cima em 31sp dizendo a mesma coisa.
private val CorpoMiudo = androidx.compose.ui.text.TextStyle(fontSize = 12.sp)


/// A largura total da grade — seis horas de programação, roláveis.
///
/// ⚠️ Ela é fixa e generosa de propósito: os blocos são posicionados em minutos a
/// partir da hora cheia, e uma caixa que só medisse o visível cortaria tudo o que
/// vem depois — que é exatamente o «e hoje à noite?» que a grade existe pra
/// responder.
private val LARGURA_DA_GRADE_INTEIRA = (6 * 60 * LARGURA_DO_MINUTO).dp

/// Um programa na grade — focável, e por isso um destino.
@Composable
private fun BlocoDaFaixa(
    bloco: BlocoDaGrade,
    agoraMs: Long,
    esquerda: androidx.compose.ui.unit.Dp,
    largura: androidx.compose.ui.unit.Dp,
    lembrado: Boolean,
    aoEscolher: () -> Unit,
) {
    val noAr = agoraMs in bloco.comecaMs..bloco.terminaMs

    /// ⚠️ **Começa em menos de 15 minutos** — a informação mais perecível desta
    /// tela, e a que não estava nela. Um contorno basta: quem está olhando a
    /// grade não precisa de um aviso, precisa de saber onde olhar.
    val jaJa = !noAr &&
        bloco.comecaMs > agoraMs &&
        bloco.comecaMs - agoraMs <= 15 * 60_000L

    Focavel(
        aoEscolher = aoEscolher,
        forma = RoundedCornerShape(4.dp),
        escalar = false,
        anel = false,
        modifier = Modifier.padding(start = esquerda).width(largura),
    ) { focado ->
        Box(
            Modifier
                .fillMaxWidth()
                .height(26.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(
                    when {
                        focado -> Cores.destaqueQuente.copy(alpha = 0.34f)
                        noAr -> Cores.destaque.copy(alpha = 0.16f)
                        else -> Cores.fundoElevado
                    },
                )
                .then(
                    if (jaJa && !focado) {
                        Modifier.border(1.dp, Cores.destaqueApagado, RoundedCornerShape(4.dp))
                    } else {
                        Modifier
                    },
                )
                .padding(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    /// ⚠️ A estrela vem **antes** do título, e é a única coisa que
                    /// pode empurrá-lo: um lembrete marcado é um compromisso que a
                    /// pessoa assumiu, e compromisso não fica escondido no fim de
                    /// um texto cortado por reticências.
                    if (lembrado) {
                        Text("★", style = RotuloMiudo, color = Cores.destaqueQuente)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        text = bloco.titulo,
                        style = RotuloMiudo,
                        color = if (focado) Cores.texto else Cores.texto,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (largura >= 60.dp) {
                    Text(
                        text = hora(bloco.comecaMs),
                        style = RotuloMiudo,
                        color = Cores.textoApagado,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

/// A ficha de um programa da grade — o que o `OK` num bloco abre.
///
/// ## ⚠️ Ela existe porque a grade era uma tabela
///
/// Via-se `Toy Story 3 · 16:46` e não se podia fazer nada. Aqui está o que o
/// guia já sabia e não mostrava — sinopse, ano, categoria — e a única ação que
/// faz sentido num programa que ainda não começou: **ser avisado**.
///
/// ⚠️ Sem botão de «assistir». Um programa de canal externo que ainda não
/// começou não tem o que tocar, e um botão que não pode cumprir é pior que a
/// ausência dele (§53).
@Composable
private fun FichaDoBloco(
    bloco: BlocoDaGrade,
    lembrado: Boolean,
    aoAlternarLembrete: () -> Unit,
    aoFechar: () -> Unit,
) {
    BackHandler { aoFechar() }
    Box(
        Modifier
            .fillMaxSize()
            .background(Cores.fundo.copy(alpha = 0.88f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .width(700.dp)
                .background(Cores.fundoElevado, RoundedCornerShape(12.dp))
                .padding(36.dp),
        ) {
            Text(
                text = listOfNotNull(hora(bloco.comecaMs), bloco.categoria, bloco.ano?.toString())
                    .joinToString(" · ")
                    .uppercase(),
                style = RotuloMiudo,
                color = Cores.destaque,
            )
            Spacer(Modifier.height(14.dp))
            Text(
                text = bloco.titulo,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = Serifada,
                    fontSize = 34.sp,
                    color = Cores.texto,
                ),
            )
            /// ⚠️ A sinopse **só quando existe** — e o guia externo quase sempre
            /// manda vazio. Um parágrafo em branco reservado «pro caso de vir»
            /// é buraco desenhado (§24).
            bloco.descricao?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = it,
                    style = CorpoMiudo,
                    color = Cores.textoApagado,
                    maxLines = 5,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(Modifier.height(26.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                /// ⚠️ Canal do Odeon não tem `programme_id`, e por isso não tem
                /// lembrete: não há programa no guia externo a que se agarrar.
                /// A ficha continua útil — ela mostra o que vai passar — e o
                /// botão simplesmente não aparece.
                if (bloco.programaId != null) {
                    BotaoDaSala(
                        rotulo = if (lembrado) "★ não me avise mais" else "☆ me avise",
                        principal = !lembrado,
                        aoEscolher = aoAlternarLembrete,
                    )
                }
                BotaoDaSala("fechar", aoFechar)
            }
        }
    }
}
