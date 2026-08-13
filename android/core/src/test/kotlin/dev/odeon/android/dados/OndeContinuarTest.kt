package dev.odeon.android.dados

import org.junit.Assert.assertEquals
import org.junit.Test

/// Os testes de «de onde continuar».
///
/// ## O defeito que ela conserta tinha uma vítima real
///
/// > «tentei começar o família de aluguel que tu tinha terminado, o filme abre
/// > no final dele e dps aparece essa mensagem»
///
/// Um filme visto até o fim guarda `position_seconds` **no fim**, e o app
/// retomava lá. Em transcodificação a sessão nascia sem nada pela frente e a
/// reprodução morria com «este trecho não está na sessão de transcodificação» —
/// ou seja, terminar um filme o tornava impossível de reabrir.
///
/// ## O que este teste prende é a fronteira, não a redação
///
/// Os dois números — 30s de piso e 60s de sobra — são da web (`Details.tsx`), e
/// o de baixo vem com o comentário de lá: «é o mesmo piso que o `/api/continue`
/// usa pra decidir o que é começou». Testar as bordas é o que impede alguém de
/// "arredondar" um deles aqui e criar a terceira redação da regra.
class OndeContinuarTest {

    /// Um filme de 1h49m50s — *Família de Aluguel*, o do relato.
    private val duracao = 6590.0

    // ---- o caso do relato -----------------------------------------------------

    /// ⚠️ **`finished` manda, e sozinho.** É o veredito do servidor, o mesmo que
    /// tira a obra da fileira de continuar — honrá-lo aqui é o que evita a
    /// terceira redação da regra.
    @Test
    fun `filme terminado volta pro zero`() {
        assertEquals(0.0, ondeContinuar(6589.0, duracao, finished = true), 0.001)
    }

    /// E terminado no meio também: se o servidor disse que acabou, acabou. Uma
    /// obra pode ser marcada como vista sem a posição estar no fim.
    @Test
    fun `terminado ganha da posicao, esteja ela onde estiver`() {
        assertEquals(0.0, ondeContinuar(3000.0, duracao, finished = true), 0.001)
    }

    // ---- o piso de baixo ------------------------------------------------------

    /// ⚠️ **5s, e não os 30 da web — decisão do dono**: «assistir um teco e
    /// voltar já deve salvar o progresso». O que sobra de piso separa o toque
    /// acidental de assistir de verdade. Este teste prende a fronteira nova; o
    /// registro da divergência está na própria função.
    @Test
    fun `um teco conta, so o toque acidental nao`() {
        assertEquals(0.0, ondeContinuar(0.0, duracao, finished = false), 0.001)
        assertEquals(0.0, ondeContinuar(5.0, duracao, finished = false), 0.001)
        assertEquals(6.0, ondeContinuar(6.0, duracao, finished = false), 0.001)
        /// O teco do relato: quinze segundos retomam.
        assertEquals(15.0, ondeContinuar(15.0, duracao, finished = false), 0.001)
    }

    // ---- o piso de cima, que é o que o relato expôs ---------------------------

    /// ⚠️ **A terceira condição é a que pega o caso de borda sem inventar
    /// limiar.** Mesmo que o `finished` não venha — servidor mais velho, ou a
    /// régua dele ainda não fechada —, um minuto de filme pela frente é pouco pra
    /// chamar de continuar. E é pouco demais pra uma sessão de transcodificação
    /// existir, que é onde o defeito doía.
    @Test
    fun `parado a um minuto do fim tambem recomeca`() {
        assertEquals(0.0, ondeContinuar(duracao - 10, duracao, finished = false), 0.001)
        assertEquals(0.0, ondeContinuar(duracao - 60, duracao, finished = false), 0.001)
        assertEquals(duracao - 61, ondeContinuar(duracao - 61, duracao, finished = false), 0.001)
    }

    // ---- o meio, que é o caso comum -------------------------------------------

    @Test
    fun `no meio do filme continua de onde parou`() {
        assertEquals(3401.0, ondeContinuar(3401.0, duracao, finished = false), 0.001)
    }

    // ---- sem duração ----------------------------------------------------------

    /// ⚠️ Sem duração não dá pra saber quanto falta, e aí a decisão é **retomar**
    /// — que é o que o app fazia antes desta função existir. Recusar por falta de
    /// informação jogaria fora a posição de quem está no meio de um filme cuja
    /// duração o servidor não mediu, e isso é perder dado de verdade pra evitar
    /// um caso de borda.
    @Test
    fun `sem duracao conhecida, retoma`() {
        assertEquals(3401.0, ondeContinuar(3401.0, null, finished = false), 0.001)
    }

    /// Mas o piso de baixo continua valendo sem duração: ele não depende dela.
    @Test
    fun `sem duracao o piso do toque acidental continua de pe`() {
        assertEquals(0.0, ondeContinuar(4.0, null, finished = false), 0.001)
        assertEquals(12.0, ondeContinuar(12.0, null, finished = false), 0.001)
    }

    /// E `finished` continua ganhando de tudo, inclusive da falta de duração.
    @Test
    fun `sem duracao, terminado ainda volta pro zero`() {
        assertEquals(0.0, ondeContinuar(3401.0, null, finished = true), 0.001)
    }

    /// Duração zero é o servidor não sabendo, e não um filme de zero segundo —
    /// `duration_seconds` chega assim em arquivo sem probe. Tratar como ausente
    /// evita `0 - onde <= 60` mandar todo mundo pro começo.
    @Test
    fun `duracao zerada nao manda todo mundo pro comeco`() {
        assertEquals(3401.0, ondeContinuar(3401.0, 0.0, finished = false), 0.001)
    }
}
