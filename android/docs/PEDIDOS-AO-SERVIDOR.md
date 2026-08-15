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

### 2.1 A resposta de produto veio — **14/08/2026**

A pergunta aberta acima («duas cópias do mesmo filme devem ser duas entradas?»)
era do dono, e ele respondeu. Vale a explicação inteira, porque ela muda o que se
pede:

> «temos diversos filmes com o mesmo nome, esses são versões que eu não achei em
> dual audio e baixei 2 vezes, 1 em pt-BR e outro em inglês.»
>
> «Caso a pessoa selecione um filme que tem mais de uma versão, uma modal abre
> mostrando as versões, daí a pessoa escolhe e aí sim abre a página de ficha do
> filme com o botão de assistir normal.»

⚠️ **Isto é agrupar, e não fundir** — e a diferença é o que destrava o pedido. O
«já tentei» acima diz que juntar «não dá e não deveria», e continua certo: fundir
apagaria o `position_seconds` de uma das duas. No desenho do dono nada é fundido.
A grade mostra **um cartão**, a modal mostra **as duas obras**, e o toque abre a
**ficha da obra escolhida** — inteira, com o id dela e o progresso dela. As duas
continuam existindo no acervo; o que muda é quantas vezes o filme ocupa a grade.

⚠️ E é bom dizer o que isto **não** conserta, pra ninguém achar que conserta: o
progresso continua por obra. Quem começar na versão em inglês e voltar na pt-BR
continua recomeçando do zero. A modal apenas passa a **mostrar** em qual das duas
você parou, o que hoje não dá pra saber sem abrir as duas.

```
o que preciso: /api/library colapsando os rips do mesmo filme numa entrada só,
               com as versões dentro dela — e, em cada versão, o idioma do áudio

por quê:       a escolha entre as duas é de **idioma**, e o idioma é justamente
               o que a listagem não manda. Hoje o único dado que as distingue é
               `height`: 816p contra 818p. Uma modal que pergunta «qual versão?»
               e só sabe escrever dois números com dois pixels de diferença pede
               uma decisão sem dar a informação pra tomá-la — é pior que o
               cartão duplicado, porque parece uma escolha e não é

o que quebra:  agrupar do lado do cliente não fecha, e são três buracos — os
               três sem conserto daqui. Estão medidos abaixo

já tentei:     os três, e cada um bate numa parede diferente do contrato atual
```

### Os três buracos, e por que nenhum tem conserto do lado de cá

**1. A chave de junção não existe na listagem.** `/api/library` não devolve
`external_ids`; ele existe só em `GET /api/works/{id}` (`web/src/api.ts:561`),
que é uma requisição **por obra**. Numa grade de 8.316 entradas, inviável. Sobra
título+ano como heurística — e ela erra exatamente onde o acervo é fraco: dois
rips com títulos ligeiramente diferentes, ou um deles `unmatched`, cujo «título»
é o nome do arquivo. ⚠️ Quem tem a identificação é o servidor; adivinhá-la aqui
seria o app decidir por semelhança de texto o que lá é uma chave.

**2. A paginação desfaz o agrupamento, e mexe no foco.** A grade carrega de 60 em
60 por `offset`, e o padrão de ordenação do app é `featured`
(`Filtros.ordem`) — não `title`. Ou seja: as duas cópias **não** são vizinhas, e
a gêmea de um cartão da página 1 pode chegar na página 3. O cartão teria que
mudar depois de desenhado, e numa grade de TV mudar a contagem de itens **move o
foco de lugar**. É a família de defeito que o §25 do `REDESENHO-TV.md` passou o
dia caçando.

**3. O `total` passaria a mentir.** Ele vem de um `count(*) OVER ()` contando
entradas **não** agrupadas. O cabeçalho da grade escreve `carregadas / total` e o
gatilho de paginação é `quantosNaTela < total`. Agrupando só no cliente, os dois
números passam a falar de coisas diferentes — e o «carregadas» seria o único
correto.

### A forma pedida

Uma proposta, não uma exigência — o servidor decide como fazer. O que importa é
que os três buracos acima fechem do lado de lá.

```jsonc
// GET /api/library — uma entrada por **filme**, e não por rip
{
  "id": "eddbfd12-…",          // a versão que representa o cartão
  "is_series": false,
  "title": "007: A Serviço Secreto de Sua Majestade",
  "year": 1969,
  // … todo o resto da entrada como hoje, sem mudança

  // ⚠️ Ausente (ou omitido) quando o filme tem uma versão só — que é o caso de
  // quase todo o acervo. Mandá-lo com um item em 17.930 linhas é peso por nada,
  // e a regra do cliente fica trivial: **há `versions`, há modal**.
  "versions": [
    {
      "id": "eddbfd12-…",         // o work id — é por ele que a ficha abre
      "media_file_id": "a2274591-…",
      "height": 816,
      "size_bytes": 2469606195,
      "duration_seconds": 8520,
      "audio_langs": ["por"],     // ⚠️ é este campo que faz a modal existir
      "position_seconds": 4320,
      "finished": false
    },
    { "id": "a950f840-…", "height": 818, "audio_langs": ["eng"], "…": "…" }
  ]
}
```

E mais três condições, que são o que separa isto de um agrupamento por parecença:

| | |
|---|---|
| a chave | a **identificação** (o id externo), nunca título+ano |
| o alcance | só `is_series = false`. ⚠️ Agrupar série por identificação juntaria episódios |
| a confiança | só o que o servidor já identificou. Dois `unmatched` não têm chave, e chutar neles é o §18 ao contrário |

⚠️ **E o `total` tem que contar entradas agrupadas**, senão o buraco 3 só muda de
lado.

⚠️ **Uma saída pra ver os rips separados** — `?versions=flat`, ou o nome que o
servidor preferir. A revisão do acervo precisa enxergar arquivo por arquivo, e um
agrupamento sem escape esconde exatamente de quem precisa ver.

### O que o cliente faz quando isto chegar

Duas cópias do contrato à mão, e as duas mudam: `android/core/…/dados/Modelos.kt`
e `web/src/api.ts`. Três grades desenham: `:tv`, `:app` e a web.

⚠️ O `clients/shared` **não** entra: o README dele o declara superado em
12/08/2026, e o `Models.kt` de lá nem tem a biblioteca.

### A pergunta que fica aberta, e é a mesma causa

`/api/continue` tem o problema idêntico, e o caso medido está logo acima — o
widget de três itens com o mesmo filme duas vezes. **Não estou pedindo junto**
porque ali a decisão é outra: duas versões com progressos diferentes são duas
coisas pra continuar de verdade, e colapsá-las obrigaria a escolher qual
progresso sobrevive na fileira. Fica anotado pro dia em que alguém decidir.

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

---

## 3. O guia conta rips; a biblioteca conta grupos

**Medido em 15/08/2026, contra o acervo de casa, pelo cliente iOS.**

A ponte guia → biblioteca ficou pronta no iPhone: tocar em «Drama 228» leva à
grade filtrada. Só que a grade abre dizendo **216**.

```
guia (/api/guia, eixo Drama):              228
biblioteca (tags=genre:Drama&kind=movie):  216
biblioteca (tags=genre:Drama, sem kind):   252

das 216 entradas: 11 são grupo de versão
rips a mais que entradas:                  12
→ 216 + 12 = 228
```

**Não é o `kind`.** É o agrupamento de versões implementado no dia 14/08: o
`/api/guia` conta **rips** e o `/api/library` conta **grupos**. Onze filmes de
Drama têm segunda versão — um deles tem três — e são exatamente os doze que
faltam.

### Por que isto não é cosmético

A pílula do guia é uma **promessa de tamanho**: «Drama 228» é o que faz alguém
tocar. Entregar 216 é a família do §8b — visível, e mentindo baixinho. E como
quem conta é o servidor, os quatro clientes erram igual: TV, celular, web e iOS.

### O pedido

Que o `/api/guia` (e o `/api/guia/revista`, se ele contar do mesmo jeito) conte
pela **mesma chave de agrupamento** que o `/api/library` usa — o
`external_ids->>'tmdb'`. Um filme com dois rips é um filme.

⚠️ **Nenhum cliente deve consertar isto na tela.** Descontar as versões no
cliente exigiria carregar as 216 entradas só pra corrigir um número de pílula, e
seria a quarta cópia de uma regra de contagem. É conta de banco, e o banco é um
só.

---

## 4. A playlist de HLS é `EVENT`, e por isso o player vira transmissão

**Medido em 15/08/2026, contra o acervo de casa, pelo cliente iOS.**

Tocando «007: Cassino Royale» no iPhone, a barra do player não mostrava
`11:03 / 2:24:00`. Mostrava **«4:44 AM»** — um horário de relógio — com o
marcador de borda ao vivo, do jeito que um player mostra uma transmissão. Num
filme de 2006.

O diagnóstico interno dizia o mesmo em outra língua: `duracao=indefinida`.

A playlist, lida direto de `/api/hls/{sessão}/index.m3u8`:

```
#EXTM3U
#EXT-X-VERSION:6
#EXT-X-TARGETDURATION:10
#EXT-X-MEDIA-SEQUENCE:0
#EXT-X-PLAYLIST-TYPE:EVENT      ← aqui
#EXT-X-INDEPENDENT-SEGMENTS
#EXTINF:10.010000,
seg00000.ts?token=…
#EXTINF:10.010000,
seg00001.ts?token=…
#EXTINF:10.010000,
seg00002.ts?token=…
                                 ← e não há #EXT-X-ENDLIST
```

Três segmentos listados, e a lista cresce conforme o ffmpeg produz.

### Por que isto não é escolha do cliente

A RFC 8216 é explícita: numa playlist `EVENT` sem `#EXT-X-ENDLIST`, o cliente
**não sabe onde o conteúdo termina** e é obrigado a tratá-la como transmissão em
andamento. Não há bandeira, opção nem gambiarra do lado de cá que faça o AVPlayer
mostrar «2:24:00» de uma lista que não diz durar 2:24:00.

O custo é o que se perde:

| | |
|---|---|
| **arrastar até o minuto 90** | não dá — a janela de busca é só o que já foi publicado |
| **saber quanto falta** | a barra não tem fim, então não tem proporção |
| **o relógio na tela** | mostra a hora do dia, que é o §8b: visível, e mentindo |

E isto **não é do `transcode`**: vale para os dois modos que passam por HLS —
`direct_stream` e `transcode` —, que somam **~70% do acervo** pelo perfil do iOS.
São quatro clientes com o mesmo teto.

### O pedido

Que a playlist saia **completa e marcada como VOD**: o servidor sabe a duração do
arquivo e o tamanho do segmento, então sabe listar todos os `#EXTINF` de uma vez,
fechar com `#EXT-X-ENDLIST` e declarar `#EXT-X-PLAYLIST-TYPE:VOD` — gerando cada
segmento **quando ele for pedido**, que é o padrão de segmentador sob demanda.

⚠️ **Nenhum cliente deve consertar isto na tela.** Dá pra desenhar uma barra
própria que saiba a duração da ficha e busque reabrindo a sessão noutro instante
— e aí seriam quatro barras à mão, quatro vezes a mesma regra, para contornar um
fato que mora num lugar só.
