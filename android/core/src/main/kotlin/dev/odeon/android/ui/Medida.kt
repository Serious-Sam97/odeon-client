package dev.odeon.android.ui

/// Duração e tamanho, escritos uma vez só.
///
/// ## Por que elas saíram da `Contracapa` — 05/08/2026
///
/// As duas nasceram no verso da caixa da locadora, que era quem primeiro precisou
/// escrever «1H36» e «1,9 GB». Quando a tela de baixados passou a dizer o mesmo,
/// havia três caminhos: importar de `ui.locadora` (uma tela dependendo da
/// aparência de outra), copiar (a terceira redação da mesma frase, e a terceira a
/// divergir) ou mover pra um lugar que não é de ninguém. É o terceiro.
///
/// ⚠️ **A caixa alta ficou com o chamador**, e é a mesma decisão que o
/// `Tipo.rotulo` já tinha tomado: «`TextStyle` não tem `text-transform`, e a
/// caixa alta é do chamador, não daqui». O verso da caixa é encarte impresso e
/// grita `1H36`; o cartão dos baixados é texto de tela e diz `1h36`. Mesma
/// conta, tipografia diferente — e se a função devolvesse maiúscula, quem quer
/// minúscula teria de desfazer.

/// `1h36` · `36min`.
///
/// Sem segundos, e é escolha: ninguém decide ver um filme por causa de 40s. A
/// hora só aparece quando existe, senão sai «0h36» — que é a mesma informação com
/// um zero a mais pra ler.
fun duracaoCompacta(segundos: Double): String {
    val total = segundos.toLong()
    val horas = total / 3600
    val minutos = (total % 3600) / 60
    return if (horas > 0) "${horas}h%02d".format(minutos) else "${minutos}min"
}

/// `1,9 GB` · `840 MB`.
///
/// **Vírgula**, porque o app inteiro escreve em português, e o `%,.1f` do Java
/// obedece ao `Locale` da máquina — que num emulador em inglês devolveria ponto.
/// A troca é feita à mão pra o número não mudar de idioma junto com o aparelho.
///
/// Abaixo de 1 GB vira MB **inteiro**: uma casa decimal em megabyte é precisão
/// que ninguém usa pra decidir o que apagar.
fun tamanhoCompacto(bytes: Long): String {
    val gb = bytes / 1_073_741_824.0
    if (gb >= 1) return "%,.1f GB".format(gb).replace('.', ',')
    return "${(bytes / 1_048_576.0).toInt()} MB"
}
