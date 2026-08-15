package dev.odeon.android.tv.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.dados.ItemDaBiblioteca
import dev.odeon.android.dados.VersaoDaObra
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.duracaoCompacta
import dev.odeon.android.ui.idiomasEmPortugues
import dev.odeon.android.ui.tamanhoCompacto

/// A escolha de versão, quando o mesmo filme está no acervo mais de uma vez.
///
/// ## Por que ela existe
///
/// O dono baixou alguns filmes **duas vezes** — um em pt-BR e outro em inglês —
/// porque não achou dual audio. Até 14/08/2026 os dois ocupavam cartões separados
/// na grade, com a mesma capa, o mesmo ano e a mesma duração; a única diferença
/// visível eram dois pixels de altura. Agora o servidor os agrupa
/// (`ItemDaBiblioteca.versoes`, §2.1 do `docs/PEDIDOS-AO-SERVIDOR.md`) e a escolha
/// acontece aqui.
///
/// ## ⚠️ Ela escolhe uma **obra**, e não um arquivo
///
/// Cada versão tem id próprio, progresso próprio e ficha própria — o toque abre a
/// ficha daquela obra, inteira, com o botão de assistir de sempre. **Nada é
/// fundido.** Fundir apagaria o `position_seconds` de uma das duas, que é
/// exatamente a objeção que segurou este pedido desde 04/08/2026.
///
/// Não confundir com o seletor de arquivos da ficha (`ModeloDaObra.escolherArquivo`):
/// aquele escolhe entre arquivos **de uma obra**, este entre obras.
///
/// ## ⚠️ Por que ela mora aqui e não dentro de uma tela — 14/08/2026
///
/// Nasceu `private` dentro da `TelaDaBibliotecaDaTv`, e a TV denunciou o erro na
/// primeira tentativa de conferir: **a busca da sala usa a mesma `/api/library`**
/// (`ModeloDaBusca` chama `odeon.biblioteca`), então ela passou a receber as
/// entradas agrupadas e continuava abrindo `aoAbrirObra(item.id)` direto — a
/// segunda versão ficava **inalcançável pela busca**, que é pior do que era antes
/// do agrupamento.
///
/// É a mesma lição que o `rotuloDaFaixa` deixou escrita quando saiu do `:app`:
/// peça que duas telas precisam não pode ser `private` de uma delas, senão a
/// segunda escreve a própria redação — ou, como aqui, simplesmente não escreve.
///
/// ## ⚠️ Nem toda versão tem nome, e a modal não inventa um
///
/// O 007 em inglês deste acervo não declara idioma na faixa de áudio, então chega
/// com `audio_langs: []` e sai aqui como **«versão 2»** — a mesma queda posicional
/// que o `rotuloDaFaixa` usa desde 06/08/2026 («faixa 1»). Escrever «Inglês» ali
/// seria inventar um metadado que ninguém mediu.
///
/// Quem distingue as duas, nesse caso, é o **«parou em»**: uma linha diz «parou em
/// 25min» e a outra «parou em 1h22», e quem assistiu reconhece a sua na hora. Sem
/// esse campo a modal seria `818p` contra `816p`, que é escolha nenhuma.
///
/// A forma é a do `MenuDeFaixasDaSala` do player, de propósito — véu, caixa
/// centrada, foco preso dentro e ◀ pra sair. É o gesto que esta casa já tem.
///
/// ## ⚠️ O ◀ é tratado **aqui dentro**, e não por `BackHandler` — medido na TCL
///
/// A primeira versão seguia o menu de faixas: nada de handler próprio, e quem
/// atendia o ◀ era a tela que abriu a modal, com `BackHandler(enabled = modal
/// aberta)`. **Na TV isso não funcionou**, e a medida é do dia 14/08/2026: com uma
/// sonda dentro do handler, o ◀ na modal aberta **não imprimiu nada** — nem o meu
/// handler nem o da tela de cima (que teria trocado de aba) chegaram a rodar. A
/// tela ficou byte a byte idêntica em duas capturas seguidas, e o `CENTER` logo
/// depois ainda escolheu a versão focada: a modal continuava lá, viva, e a tecla
/// tinha sumido.
///
/// ⚠️ O mesmo ◀ funciona na ficha e nesta busca **sem** a modal, então não é o
/// `adb` nem a tecla — é o `BackHandler` de `enabled` variável não recebendo a
/// chamada. O do player, que funciona há semanas, é `enabled = true` fixo; o da
/// série na biblioteca também só alterna depois de composto. A causa exata não
/// foi encontrada, e **isto está registrado como não explicado** no §28 do
/// `REDESENHO-TV.md` — o que está resolvido é o sintoma.
///
/// A saída é a que o `Campo.kt` já usa nesta casa: `onPreviewKeyEvent`, que desce
/// da raiz até o nó focado e portanto passa por este `Column` **antes** de
/// qualquer coisa lá dentro. O foco está preso aqui (`exit = Cancel`), então
/// enquanto a modal existe é por aqui que a tecla passa — e quando ela fecha, o
/// modificador some junto e o ◀ volta a ser da tela.
///
/// ⚠️ Continua havendo **um** lugar decidindo o que a tecla faz, que era a razão
/// do desenho antigo. Ele só mudou de casa: da tela para a modal.
@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
internal fun EscolhaDeVersaoDaSala(
    item: ItemDaBiblioteca,
    aoEscolher: (VersaoDaObra) -> Unit,
    aoFechar: () -> Unit,
) {
    val versoes = item.versoesEscolhiveis

    /// ⚠️ O foco nasce na versão **em que se parou**, e não na primeira.
    ///
    /// É a resposta pra «qual delas eu estava vendo», que é a pergunta que quem
    /// tem dois 007 faz toda vez — lembra-se do minuto, não do rip. Com duas
    /// começadas ganha a **mais adiantada**, que é a que se estava assistindo por
    /// último com mais probabilidade. Sem nenhuma começada, cai na primeira.
    val inicial = remember(versoes) {
        versoes.withIndex()
            .filter { (_, versao) -> (versao.ondeParou ?: 0.0) > 0 }
            .maxByOrNull { (_, versao) -> versao.ondeParou ?: 0.0 }
            ?.index ?: 0
    }

    /// ⚠️ O foco **entra** na modal e não sai dela pelas setas — a mesma dupla do
    /// menu de faixas. A grade continua composta atrás do véu e continua focável;
    /// sem pedir o foco, o ▼ moveria o cartaz lá atrás com a modal na cara de
    /// quem está olhando. E sem `exit = Cancel`, sair pela borda de baixo cairia
    /// de volta na grade em vez de não fazer nada.
    val focoInicial = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focoInicial.requestFocus() } }

    Box(
        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.82f)),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .focusGroup()
                /// ⚠️ **Antes** do `focusGroup` na leitura, mas o que importa é
                /// que ele é ancestral do item focado: o preview desce da raiz
                /// até o foco, então a tecla passa por aqui antes de chegar em
                /// qualquer `Focavel` — e antes de virar «voltar» do sistema.
                ///
                /// Só o `KeyDown` conta: sem essa guarda o `KeyUp` da mesma
                /// tecla fecharia a modal de novo, já fechada, e a tecla vazaria
                /// pra tela de baixo.
                .onPreviewKeyEvent { evento ->
                    val ehVoltar = evento.key == Key.Back || evento.key == Key.Escape
                    if (ehVoltar && evento.type == KeyEventType.KeyDown) {
                        aoFechar()
                        true
                    } else {
                        false
                    }
                }
                .focusProperties { exit = { FocusRequester.Cancel } }
                .background(Cores.fundoAfundado, RoundedCornerShape(14.dp))
                .border(1.dp, Cores.destaqueApagado.copy(alpha = 0.4f), RoundedCornerShape(14.dp))
                .padding(horizontal = 44.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Text(
                text = "${versoes.size} VERSÕES",
                style = TipoDaSala.rotulo,
                color = Cores.destaque,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = item.title,
                style = MaterialTheme.typography.titleMedium,
                color = Cores.texto,
            )
            Spacer(Modifier.height(16.dp))

            versoes.forEachIndexed { indice, versao ->
                LinhaDeVersao(
                    versao = versao,
                    posicao = indice,
                    modifier = if (indice == inicial) {
                        Modifier.focusRequester(focoInicial)
                    } else {
                        Modifier
                    },
                    aoEscolher = { aoEscolher(versao) },
                )
            }
        }
    }
}

/// Uma linha da modal: o nome, o que se sabe do arquivo, e onde se parou.
@Composable
private fun LinhaDeVersao(
    versao: VersaoDaObra,
    posicao: Int,
    modifier: Modifier = Modifier,
    aoEscolher: () -> Unit,
) {
    /// ⚠️ A queda é **posicional**, e é a do `rotuloDaFaixa`: sem idioma
    /// declarado, «versão 2» diz o que se sabe sem afirmar o que não se sabe.
    val nome = idiomasEmPortugues(versao.idiomasDeAudio) ?: "versão ${posicao + 1}"

    /// `818p · 2,3 GB` — item por item, e a linha some inteira se não houver
    /// nenhum. É o §24, a mesma régua do `detalheDoItem` da grade.
    val tecnico = buildList {
        versao.height?.let { add("${it}p") }
        versao.tamanhoEmBytes?.let { add(tamanhoCompacto(it)) }
    }.takeIf { it.isNotEmpty() }?.joinToString(" · ")

    /// ⚠️ Filme **terminado não tem «parou em»**, e por isso o `finished` corta
    /// aqui: `position_seconds` de quem viu até o fim **é** o fim, e escrever
    /// «parou em 2h22» sobre o crédito final é a mesma mentira que o
    /// `ondeContinuar` já conserta na ficha. O piso de 5s é o mesmo dele — abaixo
    /// disso é toque acidental, não «parou».
    val parou = versao.ondeParou
        ?.takeIf { it > 5 && !versao.finished }
        ?.let { "parou em ${duracaoCompacta(it)}" }

    Focavel(aoEscolher = aoEscolher, forma = RoundedCornerShape(8.dp), modifier = modifier) { focado ->
        Row(
            Modifier
                .width(420.dp)
                .background(
                    if (focado) Cores.destaqueQuente else Color.Transparent,
                    RoundedCornerShape(8.dp),
                )
                .padding(horizontal = 18.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = nome,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (focado) Cores.fundoAfundado else Cores.texto,
                )
                if (parou != null) {
                    Text(
                        text = parou,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (focado) Cores.fundoAfundado else Cores.textoApagado,
                    )
                }
            }
            if (tecnico != null) {
                Spacer(Modifier.width(16.dp))
                Text(
                    text = tecnico,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (focado) Cores.fundoAfundado else Cores.textoApagado,
                )
            }
        }
    }
}
