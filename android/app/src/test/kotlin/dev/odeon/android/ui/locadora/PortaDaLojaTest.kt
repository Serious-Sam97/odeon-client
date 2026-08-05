package dev.odeon.android.ui.locadora

import dev.odeon.android.dados.CaixaExposta
import dev.odeon.android.dados.Emprestada
import dev.odeon.android.dados.EstanteExposta
import dev.odeon.android.dados.Loja
import dev.odeon.android.dados.Prateleira
import org.junit.Assert.assertEquals
import org.junit.Test

/// Os testes da porta da loja e do buraco na estante.
///
/// ## O que eles guardam
///
/// O buraco é a única coisa desta tela que o screenshot **não** denuncia: uma
/// caixa a menos numa fileira de oito é indistinguível de uma fileira de sete, e
/// a foto de uma prateleira não diz quantas caixas deviam estar nela. O número
/// da porta é a única testemunha — e se as duas contas discordarem, a tela passa
/// a afirmar «3 fora» com as três caixas desenhadas ao lado.
///
/// Por isso os dois são testados **juntos**: o que importa não é cada conta, é
/// que `sorteadas − naPrateleira` seja exatamente o que sumiu da fileira.
class PortaDaLojaTest {

    private fun caixa(id: String, serie: Boolean = false, temporadas: Int = 0) =
        CaixaExposta(id = id, titulo = id, serie = serie, temporadas = temporadas)

    private fun emprestimo(
        caixaId: String,
        meu: Boolean = false,
        exclusivo: Boolean = false,
    ) = Emprestada(
        id = caixaId.hashCode(),
        caixaId = caixaId,
        titulo = caixaId,
        quemNome = if (meu) "sam" else "rudney",
        meu = meu,
        exclusivo = exclusivo,
    )

    private fun estado(
        estantes: List<EstanteExposta>,
        emprestadas: List<Emprestada> = emptyList(),
        noAcervo: Int = 600,
    ) = EstadoDaLocadora(
        carregando = false,
        loja = Loja(estantes = estantes, noAcervo = noAcervo),
        prateleira = Prateleira(emprestadas = emprestadas),
    )

    // -- o buraco -------------------------------------------------------------

    /// A caixa que **tranca** sai da fileira. É o caso da escassez ligada, que é
    /// como o servidor de casa está configurado hoje.
    @Test
    fun `a caixa exclusiva some da estante`() {
        val e = estado(
            estantes = listOf(EstanteExposta("Terror", total = 145, caixas = listOf(caixa("a"), caixa("b")))),
            emprestadas = listOf(emprestimo("a", exclusivo = true)),
        )
        assertEquals(listOf("b"), e.expostas.single().caixas.map { it.id })
        assertEquals(1, e.naPrateleira)
        assertEquals(2, e.sorteadas)
    }

    /// A **sua** também sai, mesmo sem exclusividade: ela já está com você, e
    /// uma caixa exposta que você não pode pegar de novo é um convite falso
    /// (§53).
    @Test
    fun `a minha some da estante mesmo sem exclusividade`() {
        val e = estado(
            estantes = listOf(EstanteExposta("Terror", total = 145, caixas = listOf(caixa("a"), caixa("b")))),
            emprestadas = listOf(emprestimo("a", meu = true, exclusivo = false)),
        )
        assertEquals(listOf("b"), e.expostas.single().caixas.map { it.id })
    }

    /// ⚠️ O caso que é fácil errar pro lado contrário: **escassez desligada**.
    ///
    /// O empréstimo de outra pessoa não é exclusivo, e a caixa **fica**. Sumir
    /// com ela seria encenar uma disputa que a opção do servidor desligou — e a
    /// porta da loja diria «1 fora» sobre uma caixa que qualquer um pode pegar.
    @Test
    fun `a de outra pessoa sem exclusividade continua exposta`() {
        val e = estado(
            estantes = listOf(EstanteExposta("Terror", total = 145, caixas = listOf(caixa("a"), caixa("b")))),
            emprestadas = listOf(emprestimo("a", meu = false, exclusivo = false)),
        )
        assertEquals(listOf("a", "b"), e.expostas.single().caixas.map { it.id })
        assertEquals(2, e.naPrateleira)
        assertEquals(2, e.sorteadas)
    }

    /// Estante que ficou sem nada não vira placa (§24) — mas as caixas dela
    /// continuam contando como sorteadas, senão o buraco desapareceria junto com
    /// a estante e a porta diria «0 fora».
    @Test
    fun `estante que esvaziou some, e o buraco dela continua contado`() {
        val e = estado(
            estantes = listOf(
                EstanteExposta("Terror", total = 145, caixas = listOf(caixa("a"))),
                EstanteExposta("Guerra", total = 16, caixas = listOf(caixa("b"), caixa("c"))),
            ),
            emprestadas = listOf(emprestimo("a", exclusivo = true)),
        )
        assertEquals(listOf("Guerra"), e.expostas.map { it.nome })
        assertEquals(2, e.naPrateleira)
        assertEquals(3, e.sorteadas)
    }

    /// Sem vitrine não há estante nenhuma — e não é o mesmo que vitrine vazia.
    @Test
    fun `sem loja nao ha expostas`() {
        assertEquals(emptyList<EstanteExposta>(), EstadoDaLocadora().expostas)
        assertEquals(0, EstadoDaLocadora().sorteadas)
    }

    /// A prateleira pode falhar sem a vitrine falhar (são duas rotas). Sem ela
    /// não se sabe de empréstimo nenhum, e a estante fica inteira — o que é
    /// honesto: o app não vai inventar um buraco que não conseguiu ler.
    @Test
    fun `sem prateleira a estante fica inteira`() {
        val e = EstadoDaLocadora(
            carregando = false,
            loja = Loja(estantes = listOf(EstanteExposta("Terror", total = 145, caixas = listOf(caixa("a")))), noAcervo = 600),
        )
        assertEquals(1, e.naPrateleira)
        assertEquals(1, e.sorteadas)
    }

    // -- a frase --------------------------------------------------------------

    /// A frase inteira, com as três contagens e o buraco — a da §6 da
    /// referência, ao pé da letra.
    @Test
    fun `as tres contagens, com o buraco`() {
        assertEquals(
            "37 caixas na prateleira, 3 fora · 40 nesta semana, de 600 no acervo",
            portaDaLoja(naPrateleira = 37, sorteadas = 40, noAcervo = 600),
        )
    }

    /// Sem ninguém com fita, o `, N fora` **não nasce**. «, 0 fora» é ruído, e
    /// §24.
    @Test
    fun `sem buraco o fora nao aparece`() {
        assertEquals(
            "40 caixas na prateleira · 40 nesta semana, de 600 no acervo",
            portaDaLoja(naPrateleira = 40, sorteadas = 40, noAcervo = 600),
        )
    }

    /// A concordância, que a web não faz. «1 caixas na prateleira» é erro de
    /// leitura, não economia.
    @Test
    fun `uma caixa e caixa, nao caixas`() {
        assertEquals(
            "1 caixa na prateleira, 39 fora · 40 nesta semana, de 600 no acervo",
            portaDaLoja(naPrateleira = 1, sorteadas = 40, noAcervo = 600),
        )
    }

    /// ⚠️ Servidor que não mandou `no_acervo` some com a oração inteira. «de 0
    /// no acervo» seria uma loja vazia com quarenta caixas na tela — o §18 na
    /// forma mais fácil de deixar passar, porque o zero *parece* um número.
    @Test
    fun `sem no_acervo a oracao some`() {
        assertEquals(
            "37 caixas na prateleira, 3 fora · 40 nesta semana",
            portaDaLoja(naPrateleira = 37, sorteadas = 40, noAcervo = 0),
        )
    }

    /// Os dois vazios dizem coisas **opostas**: um é a loja funcionando com o
    /// estoque todo na rua, o outro é a vitrine não ter nascido. Trocá-los faria
    /// a tela culpar o acervo por um sucesso, e vice-versa.
    @Test
    fun `os dois vazios sao frases diferentes`() {
        assertEquals(
            "a prateleira está vazia — está tudo emprestado",
            portaDaLoja(naPrateleira = 0, sorteadas = 40, noAcervo = 600),
        )
        assertEquals(
            "nada com capa por aqui",
            portaDaLoja(naPrateleira = 0, sorteadas = 0, noAcervo = 600),
        )
    }

    // -- as duas contas, juntas -----------------------------------------------

    /// ⚠️ **O teste que importa**, e o único que pega o defeito de verdade: o
    /// número que a porta diz e o que sumiu da fileira têm que ser o mesmo.
    ///
    /// Cada conta sozinha pode estar certa e as duas discordarem — foi assim que
    /// esta tela quase saiu, com `sorteadas` lendo as estantes filtradas e o
    /// `fora` dando zero pra sempre.
    @Test
    fun `o numero da porta e o que sumiu da fileira sao o mesmo`() {
        val estantes = listOf(
            EstanteExposta("Terror", total = 145, caixas = (1..8).map { caixa("t$it") }),
            EstanteExposta("Guerra", total = 16, caixas = (1..2).map { caixa("g$it") }),
        )
        val e = estado(
            estantes = estantes,
            emprestadas = listOf(
                emprestimo("t1", exclusivo = true),
                emprestimo("t2", meu = true),
                emprestimo("g1", exclusivo = true),
            ),
        )

        val sumiram = estantes.sumOf { it.caixas.size } - e.expostas.sumOf { it.caixas.size }
        assertEquals(3, sumiram)
        assertEquals(
            "7 caixas na prateleira, 3 fora · 10 nesta semana, de 600 no acervo",
            portaDaLoja(e.naPrateleira, e.sorteadas, 600),
        )
    }
}
