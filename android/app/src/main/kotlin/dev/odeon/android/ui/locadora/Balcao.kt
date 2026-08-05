package dev.odeon.android.ui.locadora

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.odeon.android.dados.Devolvida
import dev.odeon.android.dados.Prateleira
import dev.odeon.android.ui.Cores
import dev.odeon.android.ui.RotuloDeSecao
import dev.odeon.android.ui.ehDeHoje
import dev.odeon.android.ui.MarcaDoNome
import dev.odeon.android.ui.Tipo

/// O balcão — **quem está na loja, e o que acabou de acontecer nela**.
///
/// ## O que ele é, e por que não é uma lista de empréstimos
///
/// A prateleira já diz quais caixas saíram. O balcão diz **quem** — e é a única
/// parte do app em que as outras pessoas da casa aparecem por nome, com o que
/// fizeram com as fitas.
///
/// Ele tem quatro coisas, na ordem da web (§6 da referência):
///
/// | | |
/// |---|---|
/// | **os chips de pessoa** | quem tem fita na mão **ou** quem tem fama |
/// | **o seu limite** | «você pode pegar mais 2» ou «você está no limite» |
/// | **o recado ao vivo** | o que o barramento acabou de contar, e some em 6s |
/// | **as devoluções** | o que voltou, e **como** voltou |
///
/// ## ⚠️ O balcão inteiro some quando não há o que dizer
///
/// É o §24 na forma mais literal do app, e o comentário da web explica o custo
/// de não fazer isso: «um balcão que diz *nenhuma fita fora · nenhuma devolução*
/// em todas as visitas ensina a não olhar pro balcão, e aí o dia em que houver
/// algo também não será lido».
///
/// O limite sozinho **não** segura o balcão em pé: ele é contexto de quem já
/// está olhando, não notícia.
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Balcao(prateleira: Prateleira, recado: String?) {
    /// Quem aparece: quem tem fita **ou** quem tem fama.
    ///
    /// A segunda metade é o que faz a reputação existir. O comentário da web é
    /// a regra: «a fama tem que sobreviver à devolução, senão ninguém carrega
    /// nada» — se o número sumisse junto com a fita, devolver zoado seria de
    /// graça.
    val gente = prateleira.pessoas.filter { it.temOQueDizer }
    if (recado == null && gente.isEmpty() && prateleira.devolvidas.isEmpty()) return

    /// O corte por **data**, e não por contagem — ver `ehDeHoje`.
    val deHoje = prateleira.devolvidas.filter { ehDeHoje(it.devolvidoEm) }
    val antes = prateleira.devolvidas.size - deHoje.size

    /// O histórico começa fechado. Ele é arquivo: quem abriu a loja quer saber o
    /// que aconteceu **hoje**, e a semana passada é consulta.
    var mostrarAntes by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        /// ## Os chips e o limite dividem a linha, e é de propósito
        ///
        /// «você pode pegar mais 3» ocupava uma linha inteira logo abaixo dos
        /// chips — e as duas coisas são o mesmo assunto: quem está com o quê. O
        /// limite é a **sua** linha no placar que os chips desenham pros outros.
        ///
        /// O `FlowRow` faz o resto: em telas estreitas ou com muita gente na
        /// loja, ele cai pra linha de baixo sozinho, e aí volta a ser o que era
        /// antes — sem que ninguém tenha escrito duas versões do layout.
        if (gente.isNotEmpty() || prateleira.devolvidas.isNotEmpty()) {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                gente.forEach { pessoa ->
                    ChipDePessoa(
                        nome = pessoa.nome,
                        naMao = pessoa.naMao,
                        zoadas = pessoa.zoadas,
                        rebobinou = pessoa.rebobinou,
                    )
                }

                /// O limite, e as duas frases dizem coisas diferentes.
                ///
                /// «Pegar mais 2» é permissão; «devolva uma pra pegar outra» é o
                /// caminho de saída. A segunda existe porque um limite sem saída
                /// é uma parede — e o §8b manda o não vir com o motivo junto.
                ///
                /// ⚠️ O «você» saiu da primeira frase e **ficou na segunda**. Ao
                /// lado de chips que dizem `rudney` e `sam`, «pegar mais 3» já se
                /// lê como sendo seu — é a linha sem nome. Mas a frase do limite
                /// atingido é um não, e um não precisa saber com quem fala.
                Text(
                    text = if (prateleira.possoPegar > 0) {
                        "pegar mais ${prateleira.possoPegar}"
                    } else {
                        "você está no limite — devolva uma pra pegar outra"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (prateleira.possoPegar > 0) Cores.textoApagado else Cores.destaque,
                    /// Centrado na altura do chip: o chip tem 30dp com o rosto
                    /// dentro, e o texto sozinho encostaria no topo da linha.
                    modifier = Modifier.align(Alignment.CenterVertically),
                )
            }
        }

        /// O recado ao vivo do barramento. Some sozinho em 6s — é notícia, não
        /// estado. Ver `ModeloDaLocadora.ouvirOBarramento`.
        recado?.let {
            Text(text = it, style = MaterialTheme.typography.bodyMedium, color = Cores.destaque)
        }

        /// ## «Hoje» só nasce quando houve hoje
        ///
        /// Num dia parado a seção inteira some, e o balcão fica sendo os chips e
        /// mais nada — §24. É o oposto do que havia aqui: nove linhas iguais
        /// todo dia ensinam a não olhar pro balcão, e aí o dia em que alguém
        /// devolver alguma coisa também não será lido.
        ///
        /// ⚠️ **E ele não tem mais rótulo.** A régua dourada do `RotuloDeSecao`
        /// custava ~80px pra encabeçar, em dias normais, uma linha só — mais
        /// cabeçalho do que conteúdo.
        ///
        /// O que faz o trabalho dele agora é o **`mais N antes ›` logo abaixo**:
        /// a palavra «antes» só significa alguma coisa se o que está acima for
        /// depois, e é assim que a lista se datou sozinha. Onde não há histórico
        /// o link some — e aí não há dois grupos pra confundir, há um.
        if (deHoje.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                deHoje.forEach { LinhaDaDevolucao(it) }
            }
        }

        /// ## O histórico, atrás de um toque
        ///
        /// ⚠️ **A seta abre de verdade.** Um `›` que não leva a lugar nenhum é o
        /// §8b, e a alternativa que eu havia proposto — sumir com o histórico —
        /// perde dado: numa casa de três pessoas, «quem devolveu o quê na semana
        /// passada» é exatamente o tipo de coisa que se procura.
        if (antes > 0) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = if (mostrarAntes) "esconder as $antes de antes" else "mais $antes antes ›",
                    style = MaterialTheme.typography.bodySmall,
                    color = Cores.destaqueApagado,
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .clickable { mostrarAntes = !mostrarAntes }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                )
                if (mostrarAntes) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        prateleira.devolvidas.filterNot { ehDeHoje(it.devolvidoEm) }
                            .forEach { LinhaDaDevolucao(it) }
                    }
                }
            }
        }
    }
}

/// Um chip: o rosto, o nome e as três contagens.
///
/// ## As três contagens, e por que as duas últimas existem juntas
///
/// | | |
/// |---|---|
/// | **`N`** | fitas na mão agora |
/// | **`✕N`** | fitas dela que **alguém teve que rebobinar** |
/// | **`⟲N`** | fitas dos outros que **ela** rebobinou |
///
/// A terceira não é enfeite de simetria: «um placar que só conta o defeito faz
/// de todo mundo réu», diz o contrato da web. Sem o `⟲`, o balcão vira um mural
/// de acusação.
///
/// **Zero some.** Nenhuma das três vira «0» — §24, e aqui ele importa mais que
/// em qualquer outro lugar do app: um `✕0` pendurado no nome de alguém é uma
/// acusação de nada.
@Composable
private fun ChipDePessoa(nome: String, naMao: Int, zoadas: Int, rebobinou: Int) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(percent = 50))
            .background(Cores.fundoElevado)
            .border(1.dp, Cores.linha, RoundedCornerShape(percent = 50))
            .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        /// A marca do nome — a **mesma** da insígnia do canto e do avatar do
        /// perfil. Sai do mesmo hash, então a pessoa tem a mesma cor no chip do
        /// balcão e no placar do perfil, e é isso que faz um rosto de 22dp
        /// identificar alguém.
        MarcaDoNome(nome = nome, tamanho = 22.dp)

        Text(text = nome, style = Tipo.pilula, color = Cores.texto)

        /// Na mão agora. É a contagem que muda toda hora, e por isso é a única
        /// desenhada como **pastilha cheia** — as outras duas são histórico.
        if (naMao > 0) {
            Text(
                text = "$naMao",
                style = Tipo.pilula,
                color = Cores.fundo,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Cores.destaque)
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            )
        }

        /// A fama. Vermelho no que ela deve, verde no que ela pagou — e as duas
        /// cores são as que o app já usa pra perigo e acerto, não um par novo.
        if (zoadas > 0) {
            Text("✕$zoadas", style = Tipo.pilula, color = Cores.perigo)
        }
        if (rebobinou > 0) {
            Text("⟲$rebobinou", style = Tipo.pilula, color = Cores.certo)
        }
    }
}

/// Uma devolução: **o filme na frente**, quem e como atrás, e o selo de atrasada.
///
/// ## A inversão, e ela é a mudança que mais muda a tela
///
/// A versão anterior escrevia «fulano devolveu Tetris — rebobinada», e no
/// servidor de casa oito das nove linhas começavam com **«sam devolveu»**. O olho
/// batia oito vezes na mesma abertura antes de chegar ao que distingue uma linha
/// da outra.
///
/// Invertido, o que vem primeiro é o que se reconhece: o filme. Quem devolveu e
/// em que estado ficam atrás, apagados — porque numa casa de três pessoas o nome
/// é quase sempre o mesmo, e o título nunca é.
@Composable
private fun LinhaDaDevolucao(devolvida: Devolvida) {
    val (titulo, atras) = fraseDaDevolucao(devolvida)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f, fill = false),
        ) {
            Text(
                text = titulo,
                style = MaterialTheme.typography.bodySmall,
                color = Cores.texto,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false),
            )
            Text(
                text = atras,
                style = MaterialTheme.typography.bodySmall,
                color = Cores.textoApagado,
                maxLines = 1,
            )
        }
        /// ⚠️ O selo é **só de atraso**, e não de condição.
        ///
        /// «Sem rebobinar» já está na frase; pôr um selo vermelho nisso também
        /// seria cobrar duas vezes pelo mesmo. Atrasada é outra coisa — é o
        /// prazo da casa, não a etiqueta de quem devolveu.
        if (devolvida.atrasada) {
            Text(
                text = "atrasada",
                style = Tipo.pilula,
                color = Cores.perigo,
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(Cores.perigo.copy(alpha = 0.14f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

/// `Tetris` + `rebobinada · rudney` · `Tetris` + `venceu na mão de rudney`.
///
/// ## Devolve **duas** partes, e é a tela que pede
///
/// A primeira é o filme e vai em cor cheia; a segunda é quem e como, e vai
/// apagada. Montar uma frase só e mandar a tela recortá-la seria escrever a
/// gramática em dois lugares.
///
/// ## As duas formas dizem coisas diferentes
///
/// `devolvido_por = prazo` é a fita que **voltou sozinha** quando o prazo
/// estourou. Escrever «rudney devolveu» nesse caso seria dar crédito por uma
/// coisa que o relógio fez — e por isso o verbo continua na segunda parte: aqui
/// a ação não é de ninguém.
///
/// E o `devolvido_como` que o app não conhece **não vira texto**: sobra o nome,
/// em vez de escrever «unknown · rudney» (§18). O servidor pode ganhar uma
/// condição nova, e a tela deste app é mais velha que ela.
internal fun fraseDaDevolucao(devolvida: Devolvida): Pair<String, String> {
    if (devolvida.devolvidoPor == "prazo") {
        return devolvida.titulo to "venceu na mão de ${devolvida.quemNome}"
    }
    val como = when (devolvida.devolvidoComo) {
        "rebobinada" -> "rebobinada · "
        "sem_rebobinar" -> "sem rebobinar · "
        "ate_o_fim" -> "até o fim · "
        else -> ""
    }
    return devolvida.titulo to "$como${devolvida.quemNome}"
}
