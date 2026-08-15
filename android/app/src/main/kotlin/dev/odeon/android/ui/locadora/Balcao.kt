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
@Composable
fun Balcao(prateleira: Prateleira, recado: String?) {
    /// ## ⚠️ Os chips de pessoa e o limite saíram daqui — 07/08/2026
    ///
    /// Eles moram na **nota do caixa**, no fim da rolagem — o desenho da «loja
    /// da esquina» que o dono aprovou: quem está com o quê e quanto você ainda
    /// pode pegar são o *resumo* da visita, e resumo se recebe na saída. As
    /// três contagens da fama (fora, `✕`, `⟲`) continuam lá, com a mesma regra
    /// de sempre — o placar não pode contar só o defeito.
    ///
    /// O balcão ficou sendo o que é notícia: o recado ao vivo e o que voltou.
    if (recado == null && prateleira.devolvidas.isEmpty()) return

    /// O corte por **data**, e não por contagem — ver `ehDeHoje`.
    val deHoje = prateleira.devolvidas.filter { ehDeHoje(it.devolvidoEm) }
    val antes = prateleira.devolvidas.size - deHoje.size

    /// O histórico começa fechado. Ele é arquivo: quem abriu a loja quer saber o
    /// que aconteceu **hoje**, e a semana passada é consulta.
    var mostrarAntes by rememberSaveable { mutableStateOf(false) }

    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
