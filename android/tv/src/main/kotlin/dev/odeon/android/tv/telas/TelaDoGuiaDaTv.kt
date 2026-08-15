package dev.odeon.android.tv.telas

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import dev.odeon.android.dados.FaixaDoGuia
import dev.odeon.android.dados.PessoaDoGuia
import dev.odeon.android.tv.ui.Cartaz
import dev.odeon.android.tv.ui.Fileira
import dev.odeon.android.tv.ui.FileiraFantasma
import dev.odeon.android.tv.ui.Pilula
import dev.odeon.android.tv.ui.RotuloDeSecao
import dev.odeon.android.tv.ui.Sala
import dev.odeon.android.tv.ui.rolavelComOControle
import dev.odeon.android.tv.ui.TipoDaSala
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.sp
import dev.odeon.android.ui.Serifada
import dev.odeon.android.dados.Revista
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.guia.ModeloDoGuia
import dev.odeon.android.ui.viraQuando

/// O guia — a revista da semana, e os eixos do acervo.
///
/// ## A capa é o que a TV faz melhor que o celular
///
/// A revista tem um tema, uma lista de filmes e às vezes um ensaio de um
/// parágrafo. No celular isso é um cartão que se rola; numa TV é **uma capa** —
/// letreiro grande, os filmes embaixo, e texto que dá pra ler do sofá. É a única
/// tela deste app em que a sala ganha da mão.
///
/// ## ⚠️ O ensaio sai creditado, sempre
///
/// O `ensaioPor` não é enfeite e não é opcional: «quem lê tem direito de saber
/// que aquele parágrafo **não foi escrito por gente**». É a mesma regra do
/// crédito `WIKIPÉDIA` das curiosidades (§32), e é obrigação editorial do
/// projeto — não uma gentileza que a tela da TV pode dispensar por falta de
/// espaço.
///
/// E quando não há ensaio, a seção **some**: não escreve "carregando" nem
/// inventa prosa (§18, §24).
@Composable
fun TelaDoGuiaDaTv(
    modelo: ModeloDoGuia,
    aoAbrirObra: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val estado by modelo.estado.collectAsStateWithLifecycle()

    if (estado.carregando && estado.revista == null && estado.eixos.generos.isEmpty()) {
        Column(
            modifier.fillMaxSize().padding(top = Sala.overscanV),
            verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
        ) {
            FileiraFantasma()
            FileiraFantasma()
        }
        return
    }

    /// ⚠️ A mesma rolagem do perfil, e pelo mesmo motivo: as fichas de eixo **não
    /// são focáveis** (ver abaixo), então sem isto o ▼ escaparia pro trilho e o
    /// resto da revista seria inalcançável.
    val rolagem = androidx.compose.foundation.lazy.rememberLazyListState()
    LazyColumn(
        state = rolagem,
        contentPadding = PaddingValues(vertical = Sala.overscanV),
        verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreFileiras),
        modifier = modifier.fillMaxSize().rolavelComOControle(rolagem),
    ) {
        val revista = estado.revista

        if (revista != null) {
            item { PaginaDupla(revista, modelo, aoAbrirObra) }
        }


        eixoDePessoas("direção", estado.eixos.direcao)?.let { item { it() } }
        eixoDePessoas("elenco", estado.eixos.elenco)?.let { item { it() } }
        eixoDeFaixas("gêneros", estado.eixos.generos)?.let { item { it() } }
        eixoDeFaixas("décadas", estado.eixos.decadas)?.let { item { it() } }
        eixoDeFaixas("países", estado.eixos.paises)?.let { item { it() } }
    }
}

/// ⚠️ Devolve `null` quando a lista é vazia, e quem chama não desenha nada.
///
/// É o §24: um eixo sem entradas viraria um rótulo «DÉCADAS» com um vão embaixo.
/// E eixo vazio é normal — um acervo sem país identificado não tem `paises`.
private fun eixoDePessoas(titulo: String, pessoas: List<PessoaDoGuia>): (@Composable () -> Unit)? {
    if (pessoas.isEmpty()) return null
    return {
        Fileira(titulo) {
            items(pessoas) { pessoa ->
                Ficha(
                    nome = pessoa.name,
                    conta = plural(pessoa.obras, "obra", "obras"),
                    vistas = if (pessoa.terminadas > 0) {
                        plural(pessoa.terminadas, "vista", "vistas")
                    } else {
                        null
                    },
                )
            }
        }
    }
}

private fun eixoDeFaixas(titulo: String, faixas: List<FaixaDoGuia>): (@Composable () -> Unit)? {
    if (faixas.isEmpty()) return null
    return {
        Fileira(titulo) {
            items(faixas) { faixa ->
                Ficha(nome = faixa.rotulo, conta = plural(faixa.obras, "obra", "obras"), vistas = null)
            }
        }
    }
}

/// Uma ficha de eixo — um nome e o quanto dele há no acervo.
///
/// ⚠️ Ela **não é focável**, e isso é honestidade e não descuido: este app não
/// tem a tela que mostraria «tudo de Kubrick». O celular também não a tem. Um
/// cartão que recebe foco e não faz nada quando escolhido é pior que um cartão
/// que não recebe foco — o §53 diz para não oferecer o que não vai responder.
///
/// Quando a busca por eixo existir, ela vira `Focavel` e ganha um `aoEscolher`.
@Composable
private fun Ficha(nome: String, conta: String, vistas: String?) {
    Column(
        Modifier
            .width(260.dp)
            .background(Cores.fundoElevado, androidx.compose.foundation.shape.RoundedCornerShape(10.dp))
            .padding(20.dp),
    ) {
        Text(
            text = nome,
            style = MaterialTheme.typography.bodyLarge,
            color = Cores.texto,
            maxLines = 1,
        )
        Spacer(Modifier.height(8.dp))
        Row {
            Text(conta, style = MaterialTheme.typography.labelMedium, color = Cores.textoApagado)
            if (vistas != null) {
                Spacer(Modifier.width(10.dp))
                Text(vistas, style = MaterialTheme.typography.labelMedium, color = Cores.destaque)
            }
        }
    }
}

/// A revista aberta — **uma página dupla**, que é o que uma revista aberta é.
///
/// ```
/// ┌─────────────────────┬──────────────────────┐
/// │ DIRETOR DA SEMANA   │                      │
/// │                     │   [pôster] [pôster]  │
/// │  湯山邦彦            │   [pôster]           │
/// │  até segunda        │                      │
/// │                     │  EM CARTAZ ESTA SEM. │
/// │  (o ensaio, em      │  Pokémon: O Filme    │
/// │   serifada, coluna  │  Termine até segunda │
/// │   de leitura)       │                      │
/// │  ESCRITO POR …      │                      │
/// └─────────────────────┴──────────────────────┘
/// ```
///
/// ## ⚠️ A coluna de leitura tem largura **travada**, e não é estética
///
/// Uma linha de texto atravessando 1920px é ilegível: o olho perde a volta e
/// relê a mesma linha. É defeito conhecido de tipografia e esta casa já o pagou
/// uma vez, na ficha do filme. Metade da tela é a trava, e é o que transforma
/// «um parágrafo largo» em coluna de revista.
///
/// ⚠️ E o ensaio sai em **serifada**. Nesta casa a serifada aparece quando a
/// coisa escrita **é** o assunto — o título do herói, o nome na cortina, o nome
/// no perfil. Um ensaio é matéria, não interface: é o texto mais «assunto» que
/// esta tela tem.
@Composable
private fun PaginaDupla(
    revista: Revista,
    modelo: ModeloDoGuia,
    aoAbrirObra: (String) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = Sala.overscanH),
        horizontalArrangement = Arrangement.spacedBy(56.dp),
    ) {
        /// A página da esquerda: o letreiro, o tema, o prazo e a matéria.
        Column(Modifier.weight(1f)) {
            revista.rotuloDoEixo?.let {
                Text(it.uppercase(), style = TipoDaSala.rotulo, color = Cores.destaque)
                Spacer(Modifier.height(14.dp))
            }
            Text(
                text = revista.tema,
                style = androidx.compose.ui.text.TextStyle(
                    fontFamily = Serifada,
                    fontSize = 54.sp,
                    lineHeight = 60.sp,
                    color = Cores.texto,
                ),
            )

            /// ⚠️ **`até segunda`, e não `vira segunda`.**
            ///
            /// «Vira» descreve o que o servidor faz; «até» descreve o que sobra
            /// pra quem está lendo. A frase é a mesma de uma cinta de locadora —
            /// e é a pergunta real de quem olha a revista: quanto tempo eu tenho.
            viraQuando(revista.viraEm)?.let {
                Spacer(Modifier.height(12.dp))
                Text(
                    text = "até $it",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.textoApagado,
                )
            }

            revista.ensaio?.takeIf { it.isNotBlank() }?.let { ensaio ->
                Spacer(Modifier.height(30.dp))
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    ensaio.split("\n").filter { it.isNotBlank() }.forEach { paragrafo ->
                        Text(
                            text = paragrafo.trim(),
                            style = androidx.compose.ui.text.TextStyle(
                                fontFamily = Serifada,
                                fontSize = 20.sp,
                                lineHeight = 32.sp,
                                color = Cores.texto,
                            ),
                        )
                    }
                }

                /// ## ⚠️ A assinatura é **versalete dourado**, não pílula
                ///
                /// Pílula é o que esta casa usa pra rótulo clicável ou etiqueta
                /// de dado — gênero, país, década. Um crédito de autoria não é
                /// nenhum dos dois: é a assinatura no pé da matéria, e revista
                /// nenhuma põe o nome do autor dentro de uma cápsula.
                ///
                /// ⚠️ Ela mora **dentro** do bloco do ensaio, e some com ele: sem
                /// texto não há o que assinar, e um `ESCRITO POR` órfão diria que
                /// alguém escreveu algo que não está aqui.
                revista.ensaioPor?.takeIf { it.isNotBlank() }?.let { quem ->
                    Spacer(Modifier.height(24.dp))
                    Text(
                        text = "escrito por $quem".uppercase(),
                        style = TipoDaSala.rotulo,
                        color = Cores.destaque,
                    )
                }
            }
        }

        /// A página da direita: a seleção e o que está em cartaz.
        Column(Modifier.weight(1f)) {
            if (revista.filmes.isNotEmpty()) {
                Text("A SELEÇÃO", style = TipoDaSala.rotulo, color = Cores.destaque)
                Spacer(Modifier.height(16.dp))
                /// ⚠️ Uma `LazyRow` aqui **não** — ela quer largura infinita, e
                /// esta metade da página é finita por decisão. `FlowRow` deixa a
                /// seleção quebrar em duas fileiras dentro da página, que é o que
                /// uma revista faz com fotos.
                androidx.compose.foundation.layout.FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
                    verticalArrangement = Arrangement.spacedBy(Sala.vaoEntreCartazes),
                ) {
                    revista.filmes.forEach { filme ->
                        Cartaz(
                            titulo = filme.titulo,
                            arte = modelo.arte(filme.poster),
                            detalhe = listOfNotNull(
                                filme.ano?.toString(),
                                filme.diretor,
                                if (filme.visto) "visto" else null,
                            ).joinToString(" · ").takeIf { it.isNotEmpty() },
                            aoEscolher = { aoAbrirObra(filme.id) },
                        )
                    }
                }
            }

            revista.evento?.let { evento ->
                Spacer(Modifier.height(32.dp))
                Text("EM CARTAZ ESTA SEMANA", style = TipoDaSala.rotulo, color = Cores.destaque)
                Spacer(Modifier.height(12.dp))
                Text(
                    text = evento.titulo,
                    style = androidx.compose.ui.text.TextStyle(
                        fontFamily = Serifada,
                        fontSize = 30.sp,
                        color = Cores.texto,
                    ),
                )
                /// ⚠️ `2 de 5` é o §8b — quantas você já viu deste ciclo. Sem o
                /// total, o número da esquerda não responde nada.
                if (evento.obras > 0) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "${evento.suas} de ${evento.obras}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Cores.textoApagado,
                    )
                }
            }
        }
    }
}


/// `1 vista`, `3 vistas` — o singular que o guia estava comendo.
///
/// ⚠️ Visto na TCL: `9 obras · 1 vistas`. É um erro pequeno e é o tipo que fica,
/// porque quem escreveu o código leu «$n vistas» como um molde e não como uma
/// frase — e o molde só está errado num caso, o do 1, que é justamente o mais
/// comum numa casa em que se acabou de ver o primeiro filme de alguém.
///
/// Não vale pra zero: o chamador já decide não escrever nada nesse caso, e
/// «0 obras» seria ocupar a ficha com uma não-informação (§24).
private fun plural(n: Int, um: String, muitos: String): String =
    "$n " + if (n == 1) um else muitos
