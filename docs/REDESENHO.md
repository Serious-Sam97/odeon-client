
---

## 16. Tablet, sem comprar um tablet — 17/08/2026

Não era preciso AVD novo: `wm size 1600x2560` + `wm density 320` dão **800dp de
largura lógica** no mesmo emulador, e `wm size reset` desfaz. Vale anotar porque a
alternativa (criar e baixar um AVD de tablet) custa dez vezes mais pela mesma
resposta.

| tela | no celular (393dp) | no tablet (800dp) |
|---|---|---|
| biblioteca | 3 colunas de cartaz, 2 no «continuar» | **6 colunas**, 4 no «continuar», herói ocupando a largura |
| ao vivo | 1 canal por linha, 7 à vista | **2 colunas, 20 canais à vista** |

As duas se adaptam porque usam `GridCells.Adaptive` — a biblioteca desde sempre, o
ao vivo desde o conserto de paisagem da §11.1. Nenhuma das duas tem um `if` de
largura, e é esse o ponto: a mesma linha de código serve celular em pé, celular
deitado e tablet.

⚠️ **Incidente feliz**: o emulador voltou sem rede, e a primeira captura do tablet
pegou a biblioteca dizendo **«sem resposta do servidor»** — a frase da `fraseDaFalha`
(§13.3) funcionando num caminho que eu não tinha planejado testar de novo.

---

## 21. O ao vivo ganhou a cara da TV — 17/08/2026

> «esse ao vivo ta horrivel»

E estava. A tela era uma fileira **vertical** de cartões de texto — funcionava e
não parecia televisão.

### O argumento que eu tinha escrito, e que estava errado

A folha da primeira versão defendia a lista assim: «zapear com o polegar num
ônibus é percorrer uma lista». O erro está no verbo. Uma lista serve pra
**escolher** entre opções; o ao vivo não é uma lista de opções, é **uma coisa que
já está acontecendo**. Isso se mostra, não se enumera — e mostrar era exatamente o
que a TV já fazia.

### O que veio da TV, item por item

| a TV tem | aqui |
|---|---|
| herói com a arte sangrando à direita e degradê horizontal | ✅ |
| ponto vermelho + «NO AR» + nome do canal | ✅ |
| `COMEÇOU 19:09` · barra · `FALTAM 80 MIN` | ✅ |
| cartão com o número em **selo** sobre a arte, barra no pé | ✅ |
| véu no que **não** está no herói (lá é o foco que marca) | ✅ |
| `▲▼ ZAPEIA`, número digitado, grade de 12h | ❌ não há controle remoto, e a grade é o guia |

⚠️ **O vermelho é o único do app fora de erro**, e é o da TV: «vermelho aqui não é
erro, é a luz do estúdio».

### A única divergência, e ela veio da tela

A TV põe os cartões em **fileira**. A primeira montagem aqui copiou isso e o
screenshot mostrou o preço: cabiam **três canais** e sobrava **metade do celular
em preto**, com dezessete escondidos atrás da borda direita.

Na sala a fileira se justifica — o controle anda nela sem esforço e a tela é
larga. Aqui ela esconderia dezessete canais pra preservar um formato, e o formato
existe pra mostrar canais. Viraram **duas colunas**, com o mesmo cartão.

⚠️ E a linha ímpar precisa do vão à direita: sem ele o cartão sozinho estica pro
dobro da largura dos irmãos.

### O ícone da aba estava torto

Ele tinha o miolo em `y=9.6` e um mastro descendo até `19.8` — a massa em cima e
um rabo embaixo, ao lado de uma grade e de uma estrela que são simétricas. Agora o
círculo está em `12,12`, o centro exato do quadro, e as ondas abrem simétricas.
Sem mastro: era ele que puxava tudo pra baixo.

Medido nos pixels da barra, o centro vertical de cada glifo:

| biblioteca | locadora | guia | **ao vivo** | para você |
|---|---|---|---|---|
| 37,0px | 39,5px | 41,9px | **39,5px** | 39,6px |

Ele caiu exatamente na mediana dos irmãos.

### Visto na tela

Herói com «A Morte do Superman», `COMEÇOU 19:09` · `FALTAM 80 MIN`; **21 canais**
em duas colunas, cada um com número, arte, barra e o programa; o último cartão
inteiro com respiro sobre a barra de abas.
