package dev.odeon.android.ui

import org.junit.Assert.assertEquals
import org.junit.Test

/// Duração e tamanho, e o que eles guardam.
///
/// As duas viviam no verso da caixa da locadora e passaram a servir também a tela
/// de baixados. É por isso que existem testes agora: enquanto a única leitora era
/// a contracapa, um erro aparecia na foto do verso; com duas telas, um erro
/// aparece nas duas e ninguém sabe qual das duas está errada.
class MedidaTest {

    @Test
    fun `a hora so aparece quando existe`() {
        assertEquals("1h36", duracaoCompacta(5_760.0))
        assertEquals("2h22", duracaoCompacta(8_520.0))
        /// ⚠️ Sem hora, **sem o `0h`**. «0h36» é a mesma informação com um zero
        /// a mais pra ler, e num cartão de 100dp cada caractere disputa espaço.
        assertEquals("36min", duracaoCompacta(2_160.0))
        assertEquals("0min", duracaoCompacta(0.0))
    }

    /// O minuto vem com **dois dígitos** depois da hora: `2h05`, e não `2h5`.
    /// Hora sem zero à esquerda se lê como outro número — é o mesmo defeito do
    /// «1 3» que o rótulo de temporada cobrou numa rodada anterior.
    @Test
    fun `o minuto tem sempre dois digitos depois da hora`() {
        assertEquals("2h05", duracaoCompacta(7_500.0))
        assertEquals("1h00", duracaoCompacta(3_600.0))
    }

    /// ⚠️ **Vírgula, sempre.** O `%,.1f` do Java obedece ao `Locale` da máquina, e
    /// num emulador em inglês devolveria `2.1 GB`. A troca é feita à mão pra o
    /// número não mudar de idioma junto com o aparelho — este teste é o que
    /// impede alguém de «simplificar» removendo o `replace`.
    @Test
    fun `o gigabyte sai com virgula, nao com ponto`() {
        assertEquals("2,1 GB", tamanhoCompacto(2_254_857_830L))
        assertEquals("1,0 GB", tamanhoCompacto(1_073_741_824L))
    }

    /// Abaixo de 1 GB vira MB inteiro: uma casa decimal em megabyte é precisão
    /// que ninguém usa pra decidir o que apagar.
    @Test
    fun `abaixo de um giga vira megabyte inteiro`() {
        assertEquals("840 MB", tamanhoCompacto(880_803_840L))
        assertEquals("0 MB", tamanhoCompacto(0L))
    }

    /// A fronteira exata, que é onde uma comparação `>` em vez de `>=` erraria.
    @Test
    fun `um giga cravado ja e giga`() {
        assertEquals("1,0 GB", tamanhoCompacto(1_073_741_824L))
        assertEquals("1023 MB", tamanhoCompacto(1_073_741_823L))
    }
}
