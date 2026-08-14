package dev.odeon.android.tv.telas

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import dev.odeon.android.dados.ondeContinuar
import dev.odeon.android.tv.ui.BotaoDaSala
import dev.odeon.android.tv.ui.Pilula
import dev.odeon.android.tv.ui.Recado
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.ui.graphics.graphicsLayer
import kotlinx.coroutines.delay
import dev.odeon.android.ui.locadora.FaceDaCaixa
import dev.odeon.android.ui.locadora.Pose
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.locadora.CaixaEm3D
import dev.odeon.android.ui.player.Perfuracoes
import dev.odeon.android.ui.corDeHex
import dev.odeon.android.ui.duracaoCompacta
import dev.odeon.android.ui.obra.ModeloDaObra
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow

/// A ficha da obra, na sala — e o lugar de onde o filme começa.
///
/// ## A arte deitada ocupa a tela inteira, e o texto vive num véu por cima
///
/// No celular a ficha é uma coluna: pôster, título, sinopse, botão. Numa TV
/// isso desperdiça o que a TV tem de melhor — uma tela grande, a três metros,
/// num quarto escuro é onde um `backdrop` de 1920px finalmente é visto do
/// tamanho que ele foi feito.
///
/// Então a arte vai borda a borda (**sem** overscan: fundo pode sangrar, e
/// empurrá-lo pra dentro deixaria uma tarja preta em volta), e o conteúdo mora
/// numa coluna à esquerda, sobre um véu que desce da esquerda pra direita. É o
/// desenho que todo app de TV converge — e converge porque é o que deixa o texto
/// legível sobre uma imagem que não se controla.
@Composable
fun TelaDaObraDaTv(
    modelo: ModeloDaObra,
    aoTocar: (obraId: String, arquivoId: String, titulo: String, comecarEm: Double, capa: String?) -> Unit,
    aoVoltar: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()
    val botaoPrincipal = remember { FocusRequester() }

    /// ## ⚠️ Sem isto, «voltar» na ficha sai **do app**
    ///
    /// Relatado pelo dono em 12/08/2026: «pressing back from a movie details»
    /// caía na home da Google TV em vez de voltar pra biblioteca.
    ///
    /// A causa é simples e é a pior espécie: **não havia tratamento nenhum**.
    /// Sem um `BackHandler`, a tecla nunca chega ao app — o sistema a trata como
    /// «terminar a Activity», e como esta é a única Activity do módulo, terminar
    /// significa sair pro launcher. O botão «voltar» desenhado na tela
    /// funcionava, o que tornava o defeito mais confuso ainda: a tela *tinha*
    /// como voltar, só não pela tecla que todo mundo aperta.
    ///
    /// ⚠️ E o comentário do `Onde`, no `AtividadeDaTv`, afirmava que a tecla «é
    /// tratada por `BackHandler` em cada tela, uma por uma». Não era: só o
    /// player tinha. É a terceira vez neste módulo que um comentário afirma um
    /// comportamento que o aparelho desmentiu — as outras duas estão no
    /// `TelaDeLoginDaTv` e no `Trilho`.
    BackHandler { aoVoltar() }

    LaunchedEffect(estado.obra?.id) {
        if (estado.obra != null) runCatching { botaoPrincipal.requestFocus() }
    }

    val obra = estado.obra
    if (obra == null) {
        Recado(
            titulo = if (estado.carregando) "…" else "a ficha não abriu",
            detalhe = estado.erro,
            modifier = modifier,
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                if (!estado.carregando) {
                    BotaoDaSala("tentar de novo", modelo::tentarDeNovo, principal = true)
                }
                BotaoDaSala("voltar", aoVoltar)
            }
        }
        return
    }

    Box(modifier.fillMaxSize().background(Cores.fundo)) {
        val fundo = modelo.capa(obra.artwork["backdrop"] ?: obra.artwork["poster"])
        if (fundo != null) {
            AsyncImage(
                model = fundo,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }

        /// ## Dois véus, e não um
        ///
        /// O horizontal segura a coluna de texto; o vertical segura a base, onde
        /// as pílulas e a fileira de cenas ficam. Um véu só, mais escuro, daria
        /// conta dos dois e mataria a arte — que é justamente o que esta tela
        /// existe pra mostrar.
        Box(
            Modifier.fillMaxSize().background(
                /// ## ⚠️ O véu passou a **decidir**
                ///
                /// Ele ia de 0,96 a 0,80 até a fração do texto e só então caía —
                /// cobria a imagem inteira com uma gaze e não deixava nem ler
                /// direito nem ver a arte. «Muito feia e sem graça» é o que uma
                /// tela indecisa parece.
                ///
                /// Agora: opaco de verdade até 38%, esmaece até 66%, e some. O
                /// texto tem o lado dele e a imagem tem o dela — e o filme volta
                /// a aparecer na tela que é sobre ele.
                Brush.horizontalGradient(
                    0f to Cores.fundo.copy(alpha = 0.97f),
                    0.38f to Cores.fundo.copy(alpha = 0.95f),
                    0.66f to Color.Transparent,
                    1f to Color.Transparent,
                ),
            ),
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0.55f to Color.Transparent,
                    1f to Cores.fundo.copy(alpha = 0.92f),
                ),
            ),
        )

        /// ## ⚠️ A coluna era **mais larga que a tela** — medido na TCL em
        /// 12/08/2026
        ///
        /// Estava `width(1050.dp)`. A tela útil da TV é **960dp**: a coluna de
        /// texto era 90dp mais larga que a TV inteira, e a sinopse corria até a
        /// borda direita — bem além dos 55% onde o véu ainda escurece a arte.
        /// Resultado: metade do texto sobre a foto crua, do jeito que ela viesse.
        ///
        /// Uma medida absoluta escrita de cabeça não tem como acertar aqui, e a
        /// fração tem: `0.55f` é **o mesmo número** onde o degradê horizontal
        /// chega em transparente, logo abaixo. Os dois passam a ser a mesma
        /// decisão em vez de dois números que precisam concordar por sorte.
        /// ## ⚠️ Ela **rola**, e não rolava — dois defeitos relatados, uma causa
        ///
        /// > «na tela da descrição do filme o botão pra assistir o filme não
        /// > aparece no 007 serviço secreto, já no cassino Royale os botões estão
        /// > sem texto dentro dos botões»
        ///
        /// Pareciam dois problemas e eram o mesmo: esta coluna era
        /// `fillMaxHeight()` com `Arrangement.Center` e **sem rolagem nenhuma**.
        /// O que não cabia não sumia por baixo — era cortado **nas duas pontas**,
        /// porque centralizar conteúdo maior que a caixa empurra metade pra cima
        /// e metade pra baixo.
        ///
        /// | filme | o que se via |
        /// |---|---|
        /// | 007, título de **três** linhas | os botões inteiros abaixo da dobra — «não aparece» |
        /// | Cassino Royale, título de uma | os botões cortados na horizontal, sobrando só a borda — «sem texto dentro» |
        ///
        /// O segundo é o mais traiçoeiro dos dois, porque não parece corte:
        /// parece um botão desenhado errado. Foi por isso que virou dois pedidos.
        ///
        /// ## Por que `verticalScroll` **e** `Center` juntos, que parece contradição
        ///
        /// Não é: as duas valem em regimes diferentes, e é exatamente o que se
        /// quer. O `fillMaxHeight` fixa a altura **mínima** na da tela; o
        /// `verticalScroll` solta a **máxima**. Então a coluna mede
        /// `max(conteúdo, tela)`:
        ///
        /// - ficha curta → a coluna tem a altura da tela e o `Center` centraliza,
        ///   que é o desenho de hoje, intacto
        /// - ficha longa → a coluna cresce e rola, e o `Center` não tem folga pra
        ///   distribuir
        ///
        /// ⚠️ E numa TV ninguém rola de propósito: quem rola é o **foco**. O
        /// Compose traz pra vista o nó focado, então descer até o botão o traz
        /// junto — sem barra, sem gesto, sem instrução na tela.
        Column(
            Modifier
                .fillMaxHeight()
                /// ⚠️ **Largura fixa, e não fração da tela.**
                ///
                /// Ela era `fillMaxWidth(FRACAO_DO_TEXTO)` — 55% da tela. Quando
                /// a caixa entrou à esquerda, esses 55% passaram a incluir os
                /// 254dp que ela ocupa: sobrou pouco mais de um terço pro texto, e
                /// na TCL a sinopse quebrava a cada cinco palavras.
                ///
                /// Fração é uma medida de quem não tem vizinhos. Com a caixa ao
                /// lado, a coluna precisa de uma largura **sua** — 620dp, que é a
                /// coluna de leitura que a §5.3 já tinha estabelecido pro guia.
                .width(620.dp)
                .verticalScroll(rememberScrollState())
                /// ⚠️ Folga embaixo do tamanho da fita: ela flutua sobre a
                /// coluna, e sem isto o último parágrafo rolaria pra debaixo dela
                /// e nunca chegaria a ser lido.
                .padding(
                    start = Sala.overscanH + LARGURA_DA_CAIXA + 44.dp,
                    end = Sala.overscanH,
                    top = Sala.overscanV,
                    bottom = ALTURA_DA_FITA,
                ),

            /// ⚠️ Deixou de centralizar. Com a folga da fita embaixo, centrar
            /// empurrava o título pra fora da borda de cima — visto na TCL, a
            /// ficha abria já cortada no nome do filme.
            verticalArrangement = Arrangement.Top,
        ) {
            obra.temporada?.let { t ->
                Text(
                    text = "T$t" + (obra.episodio?.let { "E$it" } ?: ""),
                    style = dev.odeon.android.tv.ui.TipoDaSala.rotulo,
                    color = Cores.destaque,
                )
                Spacer(Modifier.height(10.dp))
            }

            Cascata(0) {
            Column {
            Text(
                text = obra.title,
                style = MaterialTheme.typography.displayMedium,
                color = Cores.texto,
            )

            /// O título original só aparece quando **difere**. Repeti-lo embaixo
            /// do título seria escrever a mesma palavra duas vezes com tamanhos
            /// diferentes.
            obra.tituloOriginal?.takeIf { it.isNotBlank() && it != obra.title }?.let {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cores.textoApagado,
                )
            }

            Spacer(Modifier.height(18.dp))
            LinhaDeFicha(estado, obra.year, obra.duracaoEmSegundos)
            }
            }

            Cascata(1) {
            Column {
            obra.overview?.takeIf { it.isNotBlank() }?.let {
                Spacer(Modifier.height(22.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Cores.texto,
                    maxLines = 5,
                    overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis,
                )
            }

            if (obra.tags.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                /// ## ⚠️ `FlowRow`, e o primeiro diagnóstico estava errado
                ///
                /// O dono viu «uma pílula sem texto dentro» no fim desta fileira.
                /// Meu primeiro palpite foi etiqueta com `value` em branco, e eu
                /// cheguei a filtrar por `isNotBlank()`. **A foto seguinte
                /// mostrou a pílula vazia no mesmo lugar.**
                ///
                /// Ela não é vazia: é a **quinta** etiqueta, cortada pela largura
                /// da coluna. Esta coluna tem 55% da tela (ver `FRACAO_DO_TEXTO`),
                /// e uma `Row` não quebra linha — ela transborda e o que passa da
                /// borda é recortado. Sobrou a borda esquerda da pílula, que lê
                /// exatamente como um botão sem texto.
                ///
                /// ⚠️ O filtro de `isNotBlank` **ficou**, mas rebaixado a rede de
                /// segurança e não conserto: ele nunca foi o problema, e o
                /// comentário que dizia que era está corrigido aqui pra não
                /// mentir pro próximo.
                ///
                /// `FlowRow` quebra pra baixo quando não cabe — e agora a coluna
                /// rola, então a segunda linha de etiquetas não empurra mais os
                /// botões pra fora da tela. Os dois consertos só funcionam juntos.
                @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    /// ⚠️ Só o `value`, e não `genero: Crime` — é o que a web
                    /// faz, e o `Etiqueta` explica por quê. A cor vem do
                    /// servidor e, quando falta, cai na cor da casa: **nunca**
                    /// uma cor sorteada, que pareceria classificação vinda do
                    /// acervo (§18).
                    /// Rede de segurança: etiqueta sem texto não vira pílula. O
                    /// §24 vale — o que não tem o que dizer não ocupa lugar —,
                    /// mas ver o comentário acima: **não** era isto que o dono
                    /// estava vendo.
                    ///
                    /// O `take(6)` vem depois do filtro de propósito: com ele
                    /// antes, uma etiqueta vazia gastaria uma das seis vagas.
                    /// ⚠️ **Gênero, e não país nem «filme».**
                    ///
                    /// Eram seis pílulas: `Estados Unidos`, `Reino Unido`,
                    /// `filme`, `Ação`, `Aventura`, `Thriller`. Metade delas não
                    /// ajuda a escolher — `filme` numa tela de filme é ruído, e o
                    /// país raramente é o critério de quem está decidindo o que
                    /// ver hoje à noite.
                    ///
                    /// Seis pílulas iguais também achatam o valor de cada uma:
                    /// quando tudo é etiqueta, nenhuma é destaque.
                    obra.tags
                        .filter { it.value.isNotBlank() && it.namespace !in ETIQUETAS_MUDAS }
                        .take(4)
                        .forEach { etiqueta ->
                        Pilula(
                            texto = etiqueta.value,
                            cor = corDeHex(etiqueta.color) ?: Cores.destaqueApagado,
                        )
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
            }
            }

            val comecarEm = ondeContinuar(obra.ondeParou, obra.duracaoEmSegundos, obra.finished)
            /// ⚠️ `FlowRow` aqui pelo **mesmo** motivo das etiquetas, e o
            /// sintoma era o mesmo que o dono relatou: «os botões estão sem
            /// texto dentro dos botões».
            ///
            /// `continuar de 1h56` + `do começo` + `voltar` não cabem nos 55% da
            /// coluna, e uma `Row` não quebra: o terceiro botão era recortado na
            /// borda e sobrava `vo`. Com um filme de título curto e sem retomada
            /// o rótulo é só `assistir`, cabe, e o defeito desaparece — que é por
            /// que ele parecia depender do filme.
            ///
            /// Quebrando pra baixo, o botão inteiro existe sempre. E ele continua
            /// alcançável porque a coluna agora rola atrás do foco.
            @OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BotaoDaSala(
                    /// ⚠️ «continuar» e «assistir» não são a mesma frase, e
                    /// quem decide é o `ondeContinuar` do `:core` — não uma
                    /// comparação escrita aqui. Ele já sabe que abaixo de 5s não
                    /// é retomada, que a menos de um minuto do fim é recomeço, e
                    /// que `duração 0` quer dizer «o servidor não sabe».
                    rotulo = when {
                        !estado.temComoTocar -> "sem arquivo"
                        comecarEm > 0 -> "continuar de ${duracaoCompacta(comecarEm)}"
                        else -> "assistir"
                    },
                    principal = true,
                    habilitado = estado.temComoTocar,
                    modifier = Modifier.focusRequester(botaoPrincipal),
                    aoEscolher = {
                        val arquivo = estado.arquivo ?: return@BotaoDaSala
                        aoTocar(
                            obra.id,
                            arquivo.id,
                            obra.title,
                            comecarEm,
                            modelo.capa(obra.artwork["poster"]),
                        )
                    },
                )

                /// Recomeçar do zero, e só quando há de onde continuar — senão
                /// seriam dois botões dizendo a mesma coisa (§53).
                if (comecarEm > 0 && estado.temComoTocar) {
                    BotaoDaSala(
                        rotulo = "do começo",
                        aoEscolher = {
                            val arquivo = estado.arquivo ?: return@BotaoDaSala
                            aoTocar(
                                obra.id,
                                arquivo.id,
                                obra.title,
                                0.0,
                                modelo.capa(obra.artwork["poster"]),
                            )
                        },
                    )
                }

                BotaoDaSala("voltar", aoVoltar)
            }

            /// ## ⚠️ As cenas subiram, e é o conserto do relato
            ///
            /// «A tela é muito feia e sem graça (…) descrição, imagens.» As
            /// imagens **existiam** — o varal de cenas estava lá desde sempre,
            /// como **último** item da coluna, depois do título, da sinopse, das
            /// etiquetas, dos botões e do recado da locadora.
            ///
            /// Numa TV isso quer dizer fora da tela. Uma fileira de fotos que só
            /// aparece pra quem rola até o fim de uma ficha é uma fileira que
            /// ninguém vê — e a pessoa conclui, com razão, que a tela não tem
            /// imagem nenhuma.
            ///
            /// Agora vêm logo depois dos botões: acima do recado da locadora, que
            /// é raro, e acima do que é do arquivo, que é de máquina.

            /// ## As cenas descem depois, e a ficha não espera por elas
            ///
            /// A rota custa ~3s medidos na primeira visita de cada obra — ela
            /// decodifica doze pontos do arquivo — e fica em cache pra sempre
            /// depois. Segurar a ficha por isso seria trocar três segundos de
            /// espera por uma fileira de fotos.

            /// O recado da locadora vem **depois** das cenas: ele é raro e é
            /// aviso, e aviso não disputa espaço com o que a tela existe pra
            /// mostrar.
            /// O recado da locadora — «pegando…», ou o que aconteceu.
            estado.recadoDaLocadora?.let {
                Spacer(Modifier.height(18.dp))
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.destaqueQuente,
                )
            }
        }
        /// ## ⚠️ A caixa 3D, e ela entra **sem respirar**
        ///
        /// É a peça com mais identidade da casa e até agora só existia na
        /// locadora. Aqui ela é a âncora da ficha: capa, lombada, e o volume que
        /// um pôster plano não tem.
        ///
        /// ⚠️ **`giravel = false` e nenhuma animação de repouso**, e isso é a
        /// medida da §10.1 mandando: uma caixa sozinha custou **97ms por quadro**
        /// na TCL. Girando continuamente ela sozinha derrubaria a tela pra 10fps.
        ///
        /// Parada ela é desenhada uma vez e não custa mais nada — o volume, que é
        /// o que ela veio dar, não precisa de movimento pra existir. A proposta
        /// tinha uma respiração de ±3°; ela fica de fora até haver medida que a
        /// permita.
        Box(
            Modifier
                .align(Alignment.CenterStart)
                /// A mesma folga da coluna: a caixa também não pode ficar debaixo
                /// da fita.
                .padding(start = Sala.overscanH, bottom = ALTURA_DA_FITA),
        ) {
            Cascata(0) {
                CaixaEm3D(
                    largura = LARGURA_DA_CAIXA,
                    altura = LARGURA_DA_CAIXA * 1.48f,
                    espessura = 26.dp,
                    /// ⚠️ Uma pose fixa e de leve, pra ela ler como **objeto** e
                    /// não como pôster inclinado. É a mesma caixa da locadora,
                    /// parada: o volume não precisa de movimento pra existir.
                    poseControlada = Pose(giroY = -22f, giroX = 4f),
                ) { lado, luz, poseDoQuadro ->
                    FaceDaCaixa(
                        lado = lado,
                        luz = luz,
                        pose = poseDoQuadro,
                        titulo = obra.title,
                        arte = modelo.capa(obra.artwork["poster"]),
                        cor = corDeHex(obra.corDominante),
                        ano = obra.year,
                    )
                }
            }
        }

        /// ## ⚠️ A película atravessa a tela, e por isso saiu da coluna
        ///
        /// Ela nasceu dentro da coluna de texto e **herdou os 55% de largura**
        /// dela: uma tira de película de meia tela, cortada pela borda de baixo.
        /// Visto na TCL, e é o defeito clássico de reaproveitar um lugar em vez
        /// de escolher um.
        ///
        /// Um rolo de filme não tem meia largura. Ele agora é irmão da coluna,
        /// ancorado no pé do `Box`, e atravessa de ponta a ponta — que é o que
        /// ele é: a fita passando na frente da lâmpada.
        Box(Modifier.align(Alignment.BottomStart).fillMaxWidth()) {
                Cascata(3) {
                if (estado.cenas.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    RotuloDeSecao("do filme")
                    Spacer(Modifier.height(10.dp))
                    /// ## ⚠️ As cenas viram **película**, e a peça é a do player
                    ///
                    /// Eram retângulos arredondados com vão entre eles — uma galeria
                    /// que podia ser de qualquer app. Agora são quadros colados num
                    /// rolo, com os furos de arrasto em cima e embaixo.
                    ///
                    /// ⚠️ E os furos são os **do player**: o `Perfuracoes` do
                    /// `Tira.kt` era privado e fixo no tamanho da barra fina; ganhou
                    /// medidas e virou público. Nenhuma peça nova — a casa já tinha
                    /// película desenhada, e ela estava trancada dentro de outra tela.
                    Column(
                        Modifier
                            .background(Color(0xFF0D0B09), RoundedCornerShape(4.dp))
                            .padding(vertical = 6.dp),
                    ) {
                        Perfuracoes(quantos = 22, largura = 9.dp, altura = 5.dp)
                        Spacer(Modifier.height(8.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                            items(estado.cenas.take(8)) { cena ->
                                Box(
                                    Modifier
                                        .size(width = 168.dp, height = 94.dp)
                                        .background(Cores.fundoAfundado),
                                ) {
                                    AsyncImage(
                                        model = modelo.capa(cena.imagem),
                                        contentDescription = null,
                                        contentScale = ContentScale.Crop,
                                        modifier = Modifier.fillMaxSize(),
                                    )
                                }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                        Perfuracoes(quantos = 22, largura = 9.dp, altura = 5.dp)
                    }
                }
                }
        }
    }
}

/// Onde a coluna de texto termina, e onde o véu deixa de escurecer.
///
/// É **um** número pros dois porque eles são a mesma decisão: o texto vive onde
/// a arte está coberta. Separá-los é como o defeito de 12/08 nasceu — a coluna
/// foi crescendo e o véu ficou onde estava.
private const val FRACAO_DO_TEXTO = 0.55f

/// `1969 · 2h22 · 1080p · direto` — a linha de ficha.
///
/// Item por item, omitindo o que falta (§24). O selo do plano entra **só quando
/// o plano já chegou**: escrever «direto» antes de perguntar seria afirmar sobre
/// o que ainda não se sabe.
@Composable
private fun LinhaDeFicha(
    estado: dev.odeon.android.ui.obra.EstadoDaObra,
    ano: Int?,
    duracao: Double?,
) {
    /// ## ⚠️ `800p` e `h264` saíram daqui
    ///
    /// Eles estavam na linha logo abaixo do título — o lugar mais valioso da
    /// tela — e **não é assim que alguém escolhe um filme**. Resolução e codec
    /// são resposta a «vai tocar bem?», que é uma pergunta de quem já decidiu
    /// assistir; ano e duração respondem «é este que eu quero?», que é a
    /// pergunta de quem está olhando.
    ///
    /// Eles não sumiram: desceram pro pé da ficha, junto do resto do que é do
    /// arquivo e não da obra.
    val pedacos = buildList {
        ano?.let { add(it.toString()) }
        duracao?.takeIf { it > 0 }?.let { add(duracaoCompacta(it)) }
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = pedacos.joinToString("  ·  "),
            style = MaterialTheme.typography.bodyMedium,
            color = Cores.textoApagado,
        )
        estado.plano?.let { plano ->
            Spacer(Modifier.width(16.dp))
            /// ⚠️ O selo diz **o que vai acontecer neste aparelho**, e por isso
            /// ele só existe depois de o plano responder. Uma TCL toca hevc de
            /// 4K direto onde um celular transcodificaria — é a diferença que
            /// esta pílula existe pra mostrar.
            Pilula(
                texto = if (plano.eDireto) "direto" else "transcodificando",
                cor = if (plano.eDireto) Cores.certo else Cores.destaque,
                tinta = if (plano.eDireto) Cores.certo else Cores.destaque,
            )
        }
    }
}


/// As etiquetas que **não** ajudam a escolher um filme.
///
/// ⚠️ Elas continuam existindo e continuam servindo pra filtrar o acervo — o que
/// muda é que não ocupam mais a ficha. `filme` numa tela de filme é ruído, e o
/// país raramente é o critério de quem está decidindo o que ver hoje à noite.
///
/// ⚠️ A lista é por `namespace` e é **tolerante**: um nome que o servidor não use
/// simplesmente não casa, e a etiqueta continua aparecendo. Errar aqui mostra
/// demais, nunca de menos — que é o lado certo pra errar.
private val ETIQUETAS_MUDAS = setOf("country", "pais", "format", "kind", "tipo", "type")


/// A cascata de entrada: cada bloco sobe 14dp e acende, um atrás do outro.
///
/// ## ⚠️ Ela é a diferença entre uma tela que **abre** e uma que **aparece**
///
/// Sem ela a ficha inteira materializa de uma vez, e o olho não sabe por onde
/// começar — que foi metade do «sem graça». Com 70ms entre um bloco e o próximo,
/// a ordem de leitura vira uma coisa que a tela **conta**: título, ficha,
/// sinopse, botões, cenas.
///
/// ⚠️ **Só na entrada.** Nada aqui anima em repouso: depois de 500ms a tela está
/// parada e não custa mais nada. Animação contínua numa tela de leitura é o que a
/// biblioteca já reprovou («tire ele, quero só ir percorrendo sem mt estresse»).
///
/// ⚠️ E ela respeita quem desligou animação no sistema: com `escalaDeAnimacao` em
/// zero o `tween` termina no primeiro quadro, e a tela aparece pronta — sem
/// piscar e sem esperar.
@Composable
private fun Cascata(
    ordem: Int,
    conteudo: @Composable () -> Unit,
) {
    val entrou = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(ordem * 70L)
        entrou.animateTo(1f, tween(durationMillis = 380))
    }
    Box(
        Modifier
            .graphicsLayer {
                alpha = entrou.value
                translationY = (1f - entrou.value) * 14.dp.toPx()
            },
    ) {
        conteudo()
    }
}


/// A altura que a fita ocupa no pé da tela — quadros mais os dois furos.
/// ⚠️ 150dp, e o número veio de uma foto.
///
/// Com 190 a coluna ficava com ~296dp de altura útil, e não cabiam título, ficha,
/// sinopse, etiquetas e botões. O que acontece então não é corte: o foco entra no
/// `assistir`, a rolagem o traz pra vista, e **o título sai por cima da borda** —
/// a ficha abria sem o nome do filme.
///
/// A fita perdeu 22dp de quadro e ganhou o resto da tela de volta. Ela continua
/// atravessando; o que ela não faz mais é empurrar o título pra fora.
private val ALTURA_DA_FITA = 150.dp

/// A caixa da ficha. 210dp é o que sobra pro texto ainda ter coluna de leitura
/// depois dela — e é grande o bastante pra lombada ser legível a três metros,
/// que era o argumento da §5.2 pra caixa existir numa TV.
private val LARGURA_DA_CAIXA = 210.dp
