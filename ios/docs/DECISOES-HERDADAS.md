# Decisões herdadas do Android

Escrito em **14/08/2026**. É a F0 do `PLANO.md`.

## Como usar este documento

O pedido do dono foi «não usar o código do Android, **só ver as decisões**». Isto
é a colheita: as regras que atravessam a plataforma, cada uma com **de onde veio**
e **o que custou aprender**.

⚠️ **Nada aqui é sobre Compose.** Se uma linha só faz sentido com `@Composable`,
`FocusRequester` ou `Modifier`, ela não devia ter entrado — apague e me avise.

⚠️ E o inverso vale mais: **uma regra sem o porquê não serve.** O valor deste
documento não é a lista de regras, é a lista de *contas já pagas*. Quem só ler o
que fazer vai repetir o erro que gerou o que fazer.

---

## 1. As quatro regras que valem em toda tela

Elas vêm da espec e aparecem em quase todo arquivo do Android. São a diferença
entre este produto e um catálogo.

### §18 — sem dado, a tela **omite**

Não inventa, não escreve «—», não sorteia. Um pôster nulo é **48% do acervo**
(8.598 de 17.930, medido em 04/08/2026): declarar não-nulo faria a biblioteca
falhar em quase metade do que ela lista.

⚠️ A forma mais difícil de notar: **cor inventada**. Uma etiqueta sem `color` usa
a cor da casa, **nunca** uma sorteada — uma cor por etiqueta pareceria
classificação vinda do acervo.

### §24 — linha vazia **some** inteira

Não fica reservada, não vira espaço em branco. A linha de metadados da ficha
omite **item por item** (`1969 · 816p · 2h22`), e não a linha toda: uma linha que
escreve «— · —» é pior que uma linha mais curta.

### §53 — não oferecer o que a validação vai negar

A ficha oferecia «pegar a fita» em toda obra e o servidor recusava algumas com
403. Uma frase boa sobre uma recusa evitável **continua sendo uma recusa
evitável** — o conserto é não oferecer.

### §8b — erro **visível e legível**

`HTTP 403 Forbidden` é visível sem ser legível: é status de protocolo, e diz que
houve recusa sem dizer o quê. Cada código vira uma frase em português que diz o
que aconteceu e o que dá pra fazer.

### E uma quinta, que nasceu no redesenho: **movimento tem que significar**

Toda animação responde a uma pergunta — *de onde isso veio*, *o que mudou*,
*quanto falta*. A que não responder nenhuma sai. É o que separa o experimental de
barulho.

---

## 2. O contrato — como se lê, não só o que tem

A terceira cópia à mão vai em `ios/`. Estas são as regras de leitura que já
custaram defeito:

- **Todo campo opcional é opcional de verdade.** Os `?` do Kotlin não são
  cautela — são o que o servidor devolve.
- **Campo desconhecido é descartado em silêncio.** É o que permite o servidor
  ganhar campo novo sem quebrar o cliente, e é o que **obriga o comentário a
  existir**, porque a omissão não faz barulho. No Swift isso é `decodeIfPresent`
  e nenhum `throw` por chave extra.
- ⚠️ **Campo obrigatório é uma arma apontada pro próprio pé.** Um `label` que
  vira obrigatório transforma uma renomeação de JSON em **filme que não abre**.
  Padrão vazio e queda no rótulo posicional é melhor que exceção.
- ⚠️ **`work_count` não soma o acervo.** Ele conta só o que foi identificado; com
  4.415 obras `unmatched`, a soma dos chips não dá o tamanho da biblioteca.
- ⚠️ **Uma obra pode ter mais de um arquivo, e um filme mais de uma obra.** São
  coisas diferentes e confundi-las funde o que só devia ser agrupado — ver §5.

---

## 3. Reprodução — as regras que custaram caro

### De onde continuar

```
terminado           → 0
posição ≤ 5s        → 0
faltam ≤ 60s        → 0
senão               → a posição
```

⚠️ **O piso é 5s e não 30s**, e foi decisão do dono contra a web: «a pessoa pode
assistir um teco e voltar, isso já deve salvar o progresso dela». Com 30s, um
teco de quinze segundos era salvo no servidor e **ignorado pelo botão**.

⚠️ **Terminado retoma do zero**, e isso conserta um defeito que deixava o filme
impossível de reabrir: `position_seconds` de quem viu até o fim **é** o fim, e a
sessão nascia com quase nada pela frente.

⚠️ **Duração `0` quer dizer «o servidor não sabe»**, nunca «o filme dura zero».
Sem essa leitura, todo arquivo sem probe voltava pro começo.

### As faixas vêm do plano, não do player

Perguntar ao player sempre responde «uma»: em transcodificação o ffmpeg do
servidor põe uma faixa só na playlist. O dual audio sumia **exatamente nos
arquivos que o têm**. A lista verdadeira vem de `/api/playback/{id}/plan`.

### O rótulo vem pronto do servidor

`label` de faixa e de legenda, e o `reasons` do plano. Montar «Português - AC3
5.1» no cliente seria a **quarta** redação da mesma frase entre web, Android,
servidor e iOS.

⚠️ **`und` não é idioma.** É *undetermined* em ISO 639 — o contêiner dizendo que
não sabe. O menu de faixas do Android abriu com uma faixa chamada «und» na cara
do dono em 06/08/2026. Queda posicional («faixa 1», «versão 2») diz o mesmo e diz
em português.

### O selo do plano é conteúdo, não enfeite

`direct_play` · `direct_stream` · `transcode`. Ele responde «vai transcodificar?»
antes de a pessoa gastar o toque — e num servidor de casa que atende três pessoas
isso vale. Falhar em obtê-lo **não derruba a tela**: o selo some (§24) e o play
continua.

---

## 4. ⚠️ O que muda no iOS, e é estrutural

O `CapacidadesDoAparelho.kt` do Android já escreveu a diferença, falando da web:

> «É a diferença mais visível pra web, que não põe `mkv` na lista — o navegador
> realmente não abre Matroska, e o Android abre. Ou seja: este app vai ganhar
> `direct_play` onde a web recebia remux.»

**O AVPlayer está do lado da web.** Consequências que já dá pra escrever:

| | Android | iOS |
|---|---|---|
| contêineres | `mp4, mkv, webm, mov` | `mp4, mov, m4v` + HLS |
| como descobre | `MediaCodecList`, o que o aparelho **tem** | lista, porque o AVFoundation não enumera |
| efeito no servidor | direto na maioria | **remux** onde for mkv |

⚠️ E a regra de honestidade continua valendo: **lista fixa mente nos dois
sentidos**. Declarar de menos pede transcodificação à toa (ffmpeg na máquina da
casa por nada); declarar de mais entrega arquivo que não abre. O Android aprendeu
isso duas vezes — inclusive descobrindo que o comentário «sem ac3 sempre» estava
velho e a TV **passou a declarar ac3** em algum momento.

**Pendente:** quantos arquivos do acervo são mkv. É consulta ao servidor e ela
dimensiona o custo — está no §4.1 do `PLANO.md`.

---

## 5. Agrupar não é fundir

O acervo tem o mesmo filme duas vezes quando não havia dual audio — um em pt-BR e
outro em inglês. Desde 14/08/2026 o servidor os agrupa numa entrada.

- A grade mostra **um cartão**; a escolha mostra **as duas obras**; o toque abre a
  **ficha da obra escolhida**, com o id e o progresso dela.
- ⚠️ **Nada é fundido.** Fundir apagaria o `position_seconds` de uma das duas —
  foi a objeção que segurou o pedido de 04/08 a 14/08.
- ⚠️ **Uma versão só nunca abre escolha.** Pergunta com uma resposta é o §24.
- ⚠️ **O foco/ordem nasce na versão mais adiantada** — é a resposta pra «qual eu
  estava vendo».
- ⚠️ **Nem toda versão tem nome.** O 007 em inglês não declara idioma, então sai
  como «versão 2». Quem distingue as duas é o **«parou em»**; sem ele a escolha
  seria `818p` contra `816p`, dois pixels de diferença.

⚠️ E a busca precisa da mesma escolha. No Android ela usa a **mesma rota** da
grade, e esquecer disso tornou a segunda versão inalcançável — pior do que antes
do agrupamento. **No iOS, toda tela que lista pela biblioteca herda a escolha.**

---

## 6. A cenografia — por que ela existe

Isto não é enfeite. O diagnóstico do redesenho foi que o app tinha a paleta certa
e parecia outro produto, e a causa eram três coisas:

### 6.1 Tipografia — a mais visível de todas

A web tem **duas** famílias e usa a segunda em 53 lugares: **título e número são
serifados, o resto é sem serifa**. O app era sem serifa em 100% da tela, e por
isso «Drive» era item de lista em vez de **letreiro de cinema**.

### 6.2 Ritmo — o rótulo em versalete espaçado

```
ESTA NOITE ─────────────────────────────────────────
FRANQUIAS ─────────────────────────────────────  133
```

`letter-spacing` até `0.28em`, com uma linha correndo até a margem. É o que dá ar
de programa impresso, e é o que faz seis blocos não virarem seis listas.

⚠️ **Caixa alta é do chamador, não da tipografia.** O verso da caixa é encarte
impresso e grita `1H36`; o cartão dos baixados é texto de tela e diz `1h36`.
Mesma conta, tipografia diferente.

### 6.3 Objeto — a tese do produto

A web desenha **coisas**, não registros: a caixa de VHS tem **lombada** e fica de
pé; a coleção abre os pôsteres **em leque**; a afinidade é um **36** em serifa de
dois centímetros. *Um catálogo lista linhas; uma locadora tem caixas que se
pegam.*

### As lições das peças, que valem em SwiftUI igual

- **Uma câmera para todas as faces.** A caixa 3D era feita com uma transformação
  por face, cada uma com o próprio ponto de fuga — e a junta só fechava na pose
  de repouso. Por isso ela era imóvel. **Ponto de fuga compartilhado é o que
  deixa o dedo girar a caixa.**
- **A cortina veste uma espera que já existe.** «Coisa de segundos, não podemos
  ser tão lerdos pra abrir o filme em si.» Abrir um filme já custa pedir o plano,
  montar a URL e encher o buffer — a abertura mora **em cima** disso, e não soma
  tempo novo.
- ⚠️ **Medir antes de copiar.** Uma fase pediu «a perfuração de película das
  bordas, como na web» — e ao ir buscar os números na folha de estilo, **ela não
  existia**. O que havia era uma lavagem radial. Copiar do que se lembra da web é
  como se inventa requisito.

---

## 7. Navegação — o raciocínio, não a peça

O Android decidiu **barra inferior em retrato, trilho lateral em paisagem e
tablet**. A peça é do Android; o raciocínio atravessa:

| | medido |
|---|---|
| barra inferior em paisagem | **21% da tela**, e **zero fileiras** da grade visíveis |
| cabeçalho fixo (removido antes) | 17% |
| trilho lateral | **8,6% da largura**, devolvendo a altura inteira |

⚠️ **O padrão de uma biblioteca não é a decisão do produto.** A primeira montagem
confiou no comportamento padrão do componente e saiu **pior** que o defeito que
já tinha sido consertado uma vez. Aceitar o padrão é uma escolha, e escolha entra
medida — vale igual pro `TabView` e o `NavigationSplitView` da Apple.

---

## 8. As armadilhas já pagas

| | o que acontece se esquecer |
|---|---|
| **token de mídia** | emitir um novo **aposenta o anterior** — renovar no meio do filme derruba o próprio player |
| **sprites do seek** | **só o 404** quer dizer «não há». Mascarar o 401 fez a web achar que o acervo inteiro não tinha preview |
| **sessão de HLS** | encerrar não é higiene, é **CPU do servidor de casa** — sem isso o ffmpeg fica vivo até o coletor passar |
| **progresso** | mandar `device_id`; é o que faz «parei na TV e continuo no ônibus» existir |
| **busca** | esperar ~250ms antes de consultar. Digitar «goldfinger» sem espera são **onze** consultas sobre 17.930 obras |
| **lista durante a busca** | **não apagar** a grade enquanto a nova resposta não chega, senão a tela pisca onze vezes na mesma palavra |

---

## 9. Como se mede que está pronto

A régua da casa, traduzida pro iOS:

1. **Ver no simulador**, com captura — e no aparelho do dono quando houver.
2. Compilar e passar em teste **não diz nada** sobre layout, gesto ou animação.
3. **Contraste medido** em todo texto sobre arte. O título sobre a cor da obra já
   foi **1,02:1** neste projeto, e virou 17,36:1 depois de medido.
4. **Quadro perdido** na rolagem da grade — é a única tela com 8.316 itens.
5. ⚠️ E a lição de como **não** medir: a régua de fps foi tentada no emulador e
   **não decidiu nada** — a variância entre execuções era maior que a diferença
   entre as versões. Medida ruim decide errado com cara de número.

---

## 10. O que **não** atravessa

| | |
|---|---|
| foco, D-pad, trilho, `BackHandler` | o dedo não tem foco |
| Media3, `MediaCodec`, `stepSetPQFormat` | são do decodificador da TCL |
| `TvProvider`, canal da home, leanback | são do Android TV |
| tema claro | «o produto é uma sala escura» — e uma segunda paleta é manutenção sem pedido |
| copiar as sete abas da web | é desenho de mouse |
| estante 3D com profundidade real | ficou de fora no Android por custo; **no iOS reavaliar** — SwiftUI e Metal compõem em Z, e a restrição que a barrou lá não existe aqui |
