# Pedidos ao servidor

Escrito em **04/08/2026**, do lado do app Android. Os pedidos 3 e 4 entraram em
**12/08/2026**, do lado do `:tv`.

Estes são os pedidos no formato da **§1b do `docs/CONTINUAR-ANDROID.md`**, prontos
para o dono levar ao `serious-server`. Nenhum deles bloqueia o app hoje — os
quatro são coisas que o app **não consegue decidir sozinho** sem afirmar algo que
não sabe, ou que ele resolve com um paliativo que se sabe temporário.

> A regra que este arquivo obedece: *«Você diz ao dono o que precisa. Ele leva o
> pedido pro `serious-server`, onde a mudança é feita.»* O `odeon-server` não é
> aberto daqui, e nenhum destes pedidos supõe uma solução — eles descrevem o que
> falta.

---

## 1. `POST /api/locadora/pegar` devolve 403 e o app não tem como prever

```
o que preciso: saber quando "pegar a fita" vai ser negado — um campo na ficha
               da obra, ou na prateleira, que diga se aquela obra tem caixa

por quê:       a ficha oferece "pegar a fita na locadora" em toda obra, e o
               servidor recusa algumas com 403. O app não tem nenhum dado que
               distinga as duas antes de tentar

o que quebra:  o §53 — «o produto não oferece o que a validação vai negar». Quem
               toca no botão recebe um erro em vez de uma fita, e o botão fica
               lá pra ser tocado de novo

já tentei:     todos os campos que a prateleira devolve. Na hora do 403 ela dizia
               "escassez ligada · limite de 3 por pessoa · prazo de 7 dias ·
               você ainda pode pegar 3", e nenhuma caixa estava fora. Ou seja: o
               `possoPegar` do `/api/locadora/prateleira` **não** prevê esta
               recusa, e é o único sinal que o cliente tem
```

### O que foi medido

| | |
|---|---|
| obra | *007: A Serviço Secreto de Sua Majestade* (1969) |
| conta | `sam` |
| resposta | `HTTP 403 Forbidden` |
| o que a prateleira dizia | `possoPegar = 3`, nenhuma caixa emprestada |
| a mesma ação, noutra obra | funcionou — *007 Contra a Chantagem Atômica* foi pega e devolvida |

### A hipótese, que é do app e pode estar errada

A locadora tem **600 caixas** sobre **17.498 obras** (§20 do `DESIGN.md`). Uma
obra sem caixa recusar é a explicação mais simples — e se for essa, o pedido é
só um booleano.

**Não sei se é isso**, e o app não tem como saber. Pode ser outra regra.

### O que já foi consertado do lado de cá

A mensagem. O app mostrava `HTTP 403 Forbidden` cru, que é status de protocolo e
não frase — o §8b pede erro **visível**, e um código de status é visível sem ser
legível. Agora ele diz o que aconteceu em português. Isso não resolve o pedido:
uma frase boa sobre uma recusa evitável continua sendo uma recusa evitável.

---

## 2. Duas entradas para o mesmo filme, com resoluções diferentes

```
o que preciso: saber se duas entradas de biblioteca para o mesmo filme, com
               `height` diferente, são intencionais — e, se não forem, se elas
               devem virar uma obra com dois arquivos

por quê:       a grade mostra o mesmo filme duas vezes, e a fileira de
               "continuar" mostra o mesmo filme duas vezes com progresso
               separado — 134min e 141min restantes, no mesmo dia

o que quebra:  nada funcionalmente. A grade parece defeituosa, e quem assiste
               uma das duas não continua na outra

já tentei:     juntar do lado do cliente. Não dá, e não deveria: são ids
               distintos, com `position_seconds` distintos. Fundi-las seria o app
               decidir sozinho que duas obras do acervo são uma — o §18 ao
               contrário, e apagaria o progresso de uma delas na conta
```

### O que a leva 2 descobriu, e que muda a pergunta

Este item estava anotado como **"duplicatas"** desde o traspasse. A linha de
metadados da R4 mostrou que elas **não são duplicatas**:

| | |
|---|---|
| *007: A Serviço Secreto de Sua Majestade* | `1969 · 816p · 2h22` e `1969 · **818p** · 2h22` |
| *007: Cassino Royale* | `2006 · 798p · 2h24` e `2006 · **800p** · 2h24` |

São **rips distintos** do mesmo filme, com alturas que diferem por dois pixels —
provavelmente cortes de barras pretas diferentes. O registro está certo; a
pergunta é de produto.

A pergunta deixou de ser *«por que duplicou»* e passou a ser:

> **Duas cópias do mesmo filme devem ser duas entradas na biblioteca?**

E ela tem consequência prática além da estética: o progresso é por obra, então
quem começa numa e continua na outra recomeça do zero.

### O caso mais claro, medido em 04/08/2026

O **widget** da tela inicial mostra as três coisas mais recentes de
`/api/continue`. Depois de assistir 3 minutos de uma das duas cópias, ele ficou
assim:

```
CONTINUAR
[capa]      [capa]      [capa]
138 min     141 min     65 min
   ↑           ↑
   └───────────┴── o mesmo filme, duas vezes, com progressos diferentes
```

As duas primeiras são *007: A Serviço Secreto de Sua Majestade*. A mesma arte, o
mesmo ano, a mesma duração — e três minutos de diferença, que são exatamente os
que foram assistidos numa delas.

Fora do app isso fica mais evidente que dentro: numa grade de 8.316 entradas dá
pra não reparar; num widget de três, metade da lista é o mesmo filme.

---

## 3. Um token de arte de vida longa, pra a fileira na home da Google TV

> Acrescentado em **12/08/2026**, do lado do `:tv`. Ele não bloqueia nada: a
> fileira funciona, e o paliativo está escrito. O que ele conserta é o dia em que
> ela **para** de funcionar sozinha.

```
o que preciso: um jeito de servir `/artwork/...` que sobreviva à rotação do
               token de mídia — um token longo e de leitura só, ou uma rota de
               arte que aceite o token de sessão

por quê:       a home da Google TV não busca a imagem pelo app. O que se entrega
               ao sistema é uma `Uri`, guardada no `TvProvider`, e quem a baixa
               é o processo do launcher — dias depois, com o Odeon fechado

o que quebra:  o token de mídia roda (§43), e aí as artes já publicadas passam a
               devolver 401. A fileira fica com os retângulos vazios na primeira
               tela da TV, que é o lugar mais visível que este produto tem

já tentei:     republicar a cada abertura do app, que é o que está no código —
               reescreve todas as URLs com o token da vez. Resolve pra quem abre
               o Odeon toda semana; não resolve pra quem passou um mês sem abrir
```

### Por que não dá pra consertar deste lado

O `TvProvider` guarda uma `Uri` e mais nada. Não há gancho de «a imagem falhou,
peça de novo», não há interceptor, e o `OkHttp` desta casa nem está no processo
que faz a requisição. É a única superfície do app em que o cliente entrega uma
credencial e **perde o controle de quando ela é usada**.

O paliativo e o raciocínio inteiro estão em
`android/tv/.../home/CanalDaHome.kt`.

---

## 4. Entrar na TV pelo celular — um código curto trocado por sessão

> Acrescentado em **12/08/2026**. Também não bloqueia: dá pra entrar digitando.
> O que ele conserta é o quanto isso custa.

```
o que preciso: um par de rotas de pareamento — o celular (já logado) pede um
               código curto de vida curta, a TV o troca por uma sessão

por quê:       digitar numa TV é soletrar com o D-pad. Uma senha de doze
               caracteres custa uns oitenta apertos, e o teclado da Google TV
               esconde metade da tela enquanto isso

o que quebra:  nada quebra — é a primeira tela do app sendo a pior. É o que faz
               todo serviço grande de TV empurrar o login pro celular

já tentei:     o que dá pra fazer só do lado do cliente, e está feito: campos
               grandes, foco óbvio, o endereço do servidor junto do login (numa
               TV "não conecta" é quase sempre o IP errado), `ImeAction.Go` na
               senha, e a sessão salva por aparelho — digita-se **uma** vez
```

O raciocínio está em `android/tv/.../ui/Campo.kt`, junto do campo de texto.

---

## O que **não** é pedido de servidor

Fica escrito porque a §1b diz que «boa parte do que parece exigir servidor não
exige», e vale registrar o que foi conferido e resolvido aqui:

| | resolvido onde |
|---|---|
| `height` e `size_bytes` da grade | **já vinham** no `/api/library`; o modelo Android é que os descartava |
| as etiquetas da ficha | **já vinham** no `/api/works/{id}`; idem |
| a capa no controle de mídia | a URL já existia no app — faltava declarar `MediaMetadata` |
| a mensagem do 403 | é texto do cliente |
