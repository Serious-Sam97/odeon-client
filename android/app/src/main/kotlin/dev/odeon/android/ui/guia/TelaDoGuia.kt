package dev.odeon.android.ui.guia

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.clickable
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import dev.odeon.android.dados.EventoDaSemana
import dev.odeon.android.dados.FaixaDoGuia
import dev.odeon.android.dados.FilmeDaCapa
import dev.odeon.android.dados.PessoaDoGuia
import dev.odeon.android.dados.Revista
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.PilulaDeEtiqueta
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.Serifada
import dev.odeon.android.ui.Tipo
import dev.odeon.android.ui.chega
import dev.odeon.android.ui.pegaLuz
import dev.odeon.android.ui.viraQuando

/// O guia — a revista da semana, e o índice atrás dela.
///
/// ## A capa é o guia; os eixos são o índice
///
/// A primeira versão desta tela foi construída contra `GET /api/guia`, que
/// devolve os eixos — direção, elenco, trilha, gêneros, décadas, países. Um
/// índice. E o dono olhou e disse a coisa certa: «você não pegou a maior essência
/// do Guia, ele ter informações legais igual temos no web com o **gênero da
/// semana** e **em cartaz essa semana**».
///
/// O guia da web não é um índice: é uma **revista semanal**, e ela mora noutra
/// rota (`GET /api/guia/revista`) que o app não chamava. Foi a quinta vez nesta
/// história em que o servidor já mandava o dado e o cliente não pegava.
///
/// A ordem desta tela é a da web, e ela carrega a decisão: **a revista em cima,
/// o índice embaixo**. É a diferença entre uma enciclopédia e uma revista — a
/// enciclopédia continua ali, mas não é o que se vê ao abrir.
///
/// ## Ela responde uma pergunta que nenhuma outra tela responde
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
/// ## O toque no eixo — a ponte que faltava, fechada em 05/08/2026
///
/// O comentário que estava aqui dizia: «tocar num eixo não filtra a biblioteca
/// ainda… a biblioteca não tem filtro. Oferecer o toque que não leva a lugar
/// nenhum seria o §8b; então nenhum eixo é clicável, e a próxima coisa óbvia a
/// fazer nesta tela é o outro lado dessa ponte».
///
/// A biblioteca ganhou filtro na mesma manhã, e este é o outro lado: `Terror`
/// leva à biblioteca com `tags=[genre:Terror]` e `kind=movie`, e a década leva
/// com `year_from`/`year_to` e ordem por ano. **Nenhuma tela nova** — o `chave`
/// já vinha no dado desde sempre, esperando quem o recebesse.
@Composable
fun TelaDoGuia(
    modelo: ModeloDoGuia,
    aoAbrirObra: (String) -> Unit = {},
    /// Tocar num eixo leva à biblioteca já filtrada. Ver `aoFiltrar` no
    /// `AppOdeon` — e o comentário grande logo acima, que era a dívida.
    aoFiltrar: (dev.odeon.android.dados.Filtros) -> Unit = {},
) {
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

        estado.revista?.let { revista ->
            Capa(revista = revista, arte = modelo::arte, aoAbrirObra = aoAbrirObra)
        }

        if (eixos.generos.isEmpty() && eixos.direcao.isEmpty()) {
            /// A frase só aparece quando **não há capa**.
            ///
            /// Com capa na tela, dizer "o guia ainda não tem o que cruzar" seria
            /// desmentir os oito filmes logo acima. Os eixos vazios aqui são bem
            /// mais provavelmente a rota que não respondeu — e §24: o que não tem
            /// o que dizer some, em vez de virar diagnóstico errado.
            if (estado.revista == null) {
                Text(
                    text = "o guia ainda não tem o que cruzar — o acervo precisa de identificação",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Cores.textoApagado,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
            return@Column
        }

        FaixaDeEixos("gêneros", eixos.generos, aoTocar = { faixa -> aoFiltrar(filtroDoEixo(faixa)) })
        FaixaDeEixos("décadas", eixos.decadas, aoTocar = { faixa -> aoFiltrar(filtroDoEixo(faixa)) })

        if (eixos.paises.isNotEmpty()) {
            FaixaDeEixos("de onde vêm", eixos.paises, aoTocar = { faixa -> aoFiltrar(filtroDoEixo(faixa)) })
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

/// A capa da semana: o tema, o ensaio, os filmes e o que está em cartaz.
///
/// ## Ela é igual pra todo mundo, e é isso que ela é
///
/// A revista não é recomendação: o mesmo tema, os mesmos filmes e o mesmo ensaio
/// chegam pra todos os moradores, e viram na mesma segunda-feira que a vitrine
/// da locadora. É o que dá assunto em comum — o oposto do para-você, que é
/// sorteado por pessoa.
///
/// A única coisa desta capa que é sua é o `visto` de cada filme, e por isso ele
/// é desenhado como **marca e não como selo**: a borda do cartaz acende, e mais
/// nada.
@Composable
private fun Capa(
    revista: Revista,
    arte: (String?) -> String?,
    aoAbrirObra: (String) -> Unit,
) {
    /// Capa sem filme não é capa. Sem eles restaria um letreiro solto sobre o
    /// índice, que lê como cabeçalho quebrado — §24 aplicado à seção inteira.
    if (revista.filmes.isEmpty()) return

    Column(Modifier.padding(top = 12.dp)) {

        /// O rótulo do eixo — «GÊNERO DA SEMANA».
        ///
        /// ⚠️ Em `destaque`, e **não** em `destaqueApagado` como a web faz no
        /// `.revista-eixo`. O `Rotulo.kt` já mediu esse par: `--accent-dim` sobre
        /// o fundo dá 3,96:1, reprovado no AA pra letra pequena, enquanto
        /// `--accent` dá 9,94:1. Copiar a cor da web aqui traria o defeito de
        /// contraste junto — e este rótulo é 11sp em versalete espaçado, que é o
        /// pior caso possível pra pouco contraste.
        revista.rotuloDoEixo?.let {
            Text(text = it.uppercase(), style = Tipo.rotulo, color = Cores.destaque)
        }

        /// O letreiro. É o maior tipo de qualquer tela do app, e é de propósito:
        /// numa revista, o tema **é** a capa.
        Text(
            text = revista.tema,
            style = MaterialTheme.typography.displaySmall,
            color = Cores.texto,
            modifier = Modifier.padding(top = 6.dp),
        )

        viraQuando(revista.viraEm)?.let {
            Text(
                text = "até $it",
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        /// O ensaio, quando existe — e **só** quando existe.
        ///
        /// Sem chave do LLM ele simplesmente não está aqui: nada de esqueleto,
        /// nada de "em breve", nada de prosa inventada pelo cliente (§18, §24).
        ///
        /// Serifado porque na web ele é `--font-serif`: é o único texto corrido
        /// do app, e serifa em texto corrido é o que separa "matéria" de
        /// "interface".
        if (revista.paragrafos.isNotEmpty()) {
            Column(
                modifier = Modifier.padding(top = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                revista.paragrafos.forEach { paragrafo ->
                    Text(
                        text = paragrafo,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = Serifada,
                            lineHeight = 25.sp,
                        ),
                        color = Cores.texto,
                    )
                }

                /// O selo, e ele **não é enfeite**.
                ///
                /// «Quem lê tem direito de saber que aquele parágrafo não foi
                /// escrito por gente — a mesma regra do crédito `WIKIPÉDIA` das
                /// curiosidades (§32).» Texto de máquina sai sempre creditado, e
                /// é por isso que o crédito mora **dentro** do bloco do ensaio:
                /// separar os dois é como um deles some numa refatoração.
                revista.ensaioPor?.takeIf { it.isNotBlank() }?.let {
                    Text(
                        text = "escrito por $it".uppercase(),
                        style = Tipo.rotulo,
                        color = Cores.destaque,
                    )
                }
            }
        }

        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            modifier = Modifier.padding(top = 16.dp),
        ) {
            itemsIndexed(revista.filmes, key = { _, f -> f.id }) { i, filme ->
                CartazDaCapa(
                    filme = filme,
                    arte = arte(filme.poster),
                    indice = i,
                    aoTocar = { aoAbrirObra(filme.id) },
                )
            }
        }

        revista.evento?.let {
            EmCartaz(
                evento = it,
                arte = arte(it.poster),
                quando = viraQuando(revista.viraEm),
                aoAbrirObra = aoAbrirObra,
            )
        }
    }
}

/// Um filme da capa: cartaz, título, `ano · diretor`.
///
/// ## O `visto` é uma borda, e essa contenção é a decisão
///
/// A web resolve com uma linha de CSS — a borda do cartaz troca de
/// `--line` pra `--accent` — e o comentário dela diz por quê: «é a única coisa
/// desta capa que não é igual pra todo mundo, e por isso é discreta: uma marca,
/// não um selo». Um "✓ visto" escrito sobre oito cartazes transformaria a capa
/// da revista num relatório de progresso.
@Composable
private fun CartazDaCapa(
    filme: FilmeDaCapa,
    arte: String?,
    indice: Int,
    aoTocar: () -> Unit,
) {
    val forma = RoundedCornerShape(4.dp)
    Column(
        modifier = Modifier.chega(indice).width(116.dp).clickable(onClick = aoTocar),
        verticalArrangement = Arrangement.spacedBy(5.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(2f / 3f)
                .pegaLuz(forma, forca = if (filme.visto) 0.85f else 0.22f)
                .clip(forma)
                .background(Cores.fundoElevado),
        ) {
            /// Sem pôster o quadro fica **vazio**, com a cor elevada e a borda —
            /// e não com um ícone de imagem quebrada. São 4.794 obras sem arte no
            /// acervo; um símbolo de erro em cima delas diria que o servidor
            /// falhou, quando o que houve é que a arte não existe (§18).
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
            text = filme.titulo,
            style = MaterialTheme.typography.bodySmall.copy(fontWeight = FontWeight.SemiBold),
            color = Cores.texto,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )

        /// A segunda linha é montada só com o que existe, e some inteira quando
        /// não sobra nada — o `ano` falta em obra não identificada e o `diretor`
        /// falta em quem não tem crédito. "· 2008" com o ponto órfão é o §24.
        listOfNotNull(filme.ano?.toString(), filme.diretor?.takeIf { it.isNotBlank() })
            .joinToString(" · ")
            .takeIf { it.isNotEmpty() }
            ?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.labelSmall,
                    color = Cores.textoApagado,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
    }
}

/// O filtro que um eixo do guia vira.
///
/// ## As duas formas do `chave`, e por que só ele decide
///
/// O servidor manda `genre:Terror` pros gêneros e países — o mesmo formato que a
/// barra de filtros usa — e **o ano** pras décadas (`1980`). Distinguir pelo
/// conteúdo, e não pelo nome da faixa, é o que faz esta função não precisar
/// saber de qual fileira o toque veio.
///
/// ⚠️ **`kind=movie` junto**, e é da web (§7): gênero e década no guia são «só
/// filmes». Sem isso, tocar em «Comédia» traria 3.220 entradas em que a maioria
/// é episódio de série — e o número que a pílula prometia era o dos filmes.
internal fun filtroDoEixo(faixa: FaixaDoGuia): dev.odeon.android.dados.Filtros {
    val decada = faixa.chave.toIntOrNull()
    return if (decada != null) {
        dev.odeon.android.dados.Filtros(
            anoDe = decada,
            anoAte = decada + 9,
            tipo = "movie",
            /// Dentro de uma década, a ordem por ano é a que conta a história —
            /// «em destaque» embaralharia dez anos de cinema.
            ordem = "year",
        )
    } else {
        dev.odeon.android.dados.Filtros(etiquetas = listOf(faixa.chave), tipo = "movie")
    }
}

/// «EM CARTAZ ESTA SEMANA» — o evento.
///
/// ## É o que amarra a revista ao resto do app
///
/// Participar dá XP e conquista, e **quem participou aparece pra todo mundo** —
/// que é o ponto de o evento ser coletivo. Um evento em que ninguém sabe quem
/// foi não é evento, é tarefa.
///
/// ⚠️ Só abre ficha quando `tipo` é `"obra"`. O `id` de uma saga é de coleção, e
/// mandá-lo pra tela da obra daria erro — oferecer o toque que vai falhar é o
/// §53 ao contrário.
@Composable
private fun EmCartaz(
    evento: EventoDaSemana,
    arte: String?,
    quando: String?,
    aoAbrirObra: (String) -> Unit,
) {
    val abre = evento.tipo == "obra"
    Row(
        modifier = Modifier
            .padding(top = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Cores.fundoElevado)
            .clickable(enabled = abre) { aoAbrirObra(evento.id) }
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        /// Sem pôster o quadro **não existe** — e isto é da web, palavra por
        /// palavra: «uma moldura vazia ao lado do texto lê como imagem quebrada».
        /// Coleção do TMDB nem sempre traz arte própria.
        if (arte != null) {
            Box(
                modifier = Modifier
                    .width(58.dp)
                    .aspectRatio(2f / 3f)
                    .pegaLuz(RoundedCornerShape(4.dp))
                    .clip(RoundedCornerShape(4.dp))
                    .background(Cores.fundoAfundado),
            ) {
                AsyncImage(
                    model = arte,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize(),
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "em cartaz esta semana".uppercase(),
                style = Tipo.rotulo,
                color = Cores.destaque,
            )
            Text(
                text = evento.titulo,
                style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = Cores.texto,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = chamadaDoEvento(evento, quando),
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
            )
            /// Quem já esteve.
            if (evento.participantes.isNotEmpty()) {
                Text(
                    text = evento.participantes.joinToString(", ") +
                        if (evento.participantes.size == 1) " participou" else " participaram",
                    style = MaterialTheme.typography.labelSmall,
                    color = Cores.destaque,
                )
            }
        }
    }
}

/// A chamada do evento, montada aqui porque é **desenho** e não dado — a mesma
/// divisão do `Acontecimento.frase`.
///
/// A web escreve "até segunda" fixo no meio da frase; aqui o prazo vem do mesmo
/// relógio da vitrine (ver `viraQuando`), e quando o `vira_em` não diz nada a
/// oração inteira some, em vez de prometer um dia que ninguém conferiu.
///
/// `suas de obras` só entra numa saga: em obra única a frase seria "você já viu
/// 0 de 1", que é escrever com número aquilo que o convite já diz.
internal fun chamadaDoEvento(evento: EventoDaSemana, quando: String?): String {
    if (evento.participou) return "Você participou."
    val prazo = quando?.let { " até $it" }.orEmpty()
    return if (evento.obras > 1) {
        "Termine uma das ${evento.obras} obras$prazo pra participar. " +
            "Você já viu ${evento.suas} de ${evento.obras}."
    } else {
        "Termine$prazo pra participar."
    }
}

/// Um eixo que não é pessoa: gênero, década, país.
///
/// Desenhado como **etiqueta**, e não como filtro, de propósito: a
/// `PilulaDeEtiqueta` é a forma que este app usa pra "fato que não se toca". Se
/// um dia o toque filtrar a biblioteca, ela vira `PilulaDeFiltro` — e a troca de
/// componente é o que vai contar que a coisa passou a fazer algo.
@Composable
private fun FaixaDeEixos(
    titulo: String,
    faixas: List<FaixaDoGuia>,
    aoTocar: ((FaixaDoGuia) -> Unit)? = null,
) {
    if (faixas.isEmpty()) return
    RotuloDeSecao(texto = titulo, numero = faixas.size)
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier.padding(bottom = 12.dp),
    ) {
        itemsIndexed(faixas, key = { _, f -> f.chave }) { i, faixa ->
            Box(
                Modifier
                    .chega(i)
                    .then(
                        if (aoTocar == null) Modifier
                        else Modifier.clickable { aoTocar(faixa) },
                    ),
            ) {
                PilulaDeEtiqueta(rotulo = faixa.rotulo, valor = "${faixa.obras}")
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
