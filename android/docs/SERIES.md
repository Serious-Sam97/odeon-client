# As séries na TV — o que existe hoje, e o que proponho

Visto na TCL em 17/08/2026, com `Arrested Development` (5 temporadas, 84
episódios) e `Arcane` (2 temporadas). Tudo abaixo foi medido na tela ou lido no
código; nada é suposição de comportamento.

---

## 1. O que existe hoje

Uma série é um `ItemDaBiblioteca` com `eSerie = true`. Escolhê-la na grade não
abre ficha: chama `ModeloDaBiblioteca.entrarNaSerie`, que troca a grade pela
**listagem plana** de `/api/works?collection=…`. A tela é a mesma
`TelaDaBibliotecaDaTv`, com outro cabeçalho.

O cabeçalho traz `‹ sair da série`, o título e `84 de 84`. Abaixo, uma grade de
sete colunas com um `Cartaz` por episódio: `titulo = episodio.title`,
`detalhe = episodio.codigo` (`S01E01`), arte = `still ?: backdrop ?: poster`.

### As seis coisas que a tela mostrou

| | o que se vê | por quê |
|---|---|---|
| **1** | **o quadro do episódio espremido num cartaz** | `ObraDaLista.arte` prefere o `still`, que é **16:9**. O `Cartaz` é 112×168dp, **2:3**. Cada episódio perde ~45% da largura no corte central: o `S01E03` do `Arrested` virou uma roda de bicicleta |
| **2** | **84 episódios numa parede só** | a grade é plana e contínua: `S03E13` e `S04E01` são vizinhos, sem nada entre eles. Cinco temporadas sem fronteira, e sem jeito de pular pra uma |
| **3** | **entrei no meio da temporada 3** | a `LazyVerticalGrid` usa **um** `rememberLazyGridState` (linha 79), compartilhado entre a biblioteca e os episódios. Eu tinha rolado 16 fileiras na biblioteca; a série abriu na fileira 16 dela |
| **4** | **o nome da série some ao rolar** | o cabeçalho é um `item(span = maxLineSpan)` **dentro** da grade. Três fileiras abaixo, nada na tela diz em que série você está |
| **5** | **nenhum episódio diz se foi visto** | `ObraDaLista` traz `ondeParou` **e** `finished`, e a grade de episódios não passa `andado` — a de filmes passa. O dado chega e é jogado fora |
| **6** | **«Episódio 11», e o `S03E11` embaixo, pequeno e cinza** | onde o servidor tem título, ele aparece (`Piloto`, `Banana-chefe`). Onde não tem, a linha grande vira `Episódio N` — e aí a única informação real é a linha pequena. A hierarquia se inverte sozinha |

### E três coisas fora da tela da série

- **Abrir uma série pela busca dá 404.** A grade da biblioteca desdobra
  (`item.eSerie -> entrarNaSerie`); a busca manda todo mundo pra `aoAbrirObra`, e
  o `/api/works/{id}` de uma coleção não existe. Erro legível («a ficha não abriu
  · o servidor respondeu 404»), destino errado.
- **`2 tem…`** — o cartaz da série trunca `2 temporadas` na largura de 112dp.
- **Não existe «próximo episódio».** `grep` por `proximoEpisodio` em todos os
  módulos: zero. Quando um episódio acaba, acaba.

---

## 2. O redesign

O princípio é um só, e a casa já o escreveu — está no comentário do `Quadro`:

> «quem parou no meio já sabe **que** filme é, e quer saber **onde estava**. Uma
> capa não diz isso; um quadro diz.»

Um episódio é sempre esse caso. Ninguém procura o `S03E11` pela capa da série —
a capa é a mesma nos 84. **O episódio não é um cartaz; é um quadro.**

### 2.1 O quadro no lugar do cartaz

Trocar `Cartaz` por `Quadro` (240×135dp, 16:9) na grade de episódios. A peça já
existe, já é usada no «continuar» e no mural, já aceita `andado` e `detalhe`. O
`still` para de ser cortado.

⚠️ Sete colunas de 112dp viram três de 240dp. **É a intenção**: 84 miniaturas
ilegíveis não são um índice, são um mosaico.

### 2.2 Temporadas viram fileiras, e a grade morre

Uma `LazyRow` por temporada, com um cabeçalho de fileira:

    TEMPORADA 1   22 episódios · 22 vistos     ▸
    TEMPORADA 2   18 episódios · 4 vistos      ▸
    TEMPORADA 3   13 episódios                 ▸

É o vocabulário que a home da TV já usa, e é o que casa com o D-pad: **▲▼ troca
de temporada, ◀▶ anda nos episódios**. Numa grade de sete colunas, ▼ desce sete
episódios — um movimento que não quer dizer nada.

⚠️ A fileira **começa no primeiro não-visto** da temporada, não no episódio 1.

### 2.3 Um herói da série, e ele responde «onde eu parei»

No topo, o mesmo herói do ao vivo: arte larga sangrando à direita, e por cima:

    Arrested Development          5 temporadas · 84 episódios · 26 vistos
    ▸ CONTINUAR  S03E11 · faltam 12min          ⟲ do começo

O `continuar` é o botão principal e resolve em **zero apertos** a pergunta que
hoje custa uma caçada. Os dados já chegam: `ondeParou`/`finished` por episódio, e
`finished_count` já vem no cartaz da série sem pedir nada novo ao servidor.

⚠️ Sem episódio começado, o botão é `▸ COMEÇAR S01E01` — e não um `continuar`
apagado. §53: não oferecer o que a validação vai negar.

### 2.4 O cabeçalho não rola

Hoje ele é item da grade e sobe junto. Passa a ser fixo acima das fileiras: em
qualquer ponto da rolagem se lê a série, a temporada e o `sair`.

### 2.5 Cada quadro diz o que já aconteceu com ele

- barra dourada de `andado` quando `ondeParou > 0` — a peça já desenha;
- ✓ discreto no canto quando `finished`;
- `detalhe` = `S03E11 · 22min`, com o **código na frente**, porque é ele que
  identifica. O título vai na linha grande **quando existe**; quando não existe,
  o código sobe pra linha grande e não se escreve «Episódio 11» (§18 — não
  inventar um nome que o acervo não tem).

### 2.6 Ao acabar um episódio, o próximo

O mesmo `aoAcabar` que o ao vivo ganhou esta semana, com outro destino: em vez de
perguntar à grade o que está no ar, perguntar à série qual é o próximo `S/E`. Uma
contagem de 10s com `assistir agora` / `ficar por aqui`.

⚠️ **E aqui o registro de progresso continua ligado** — ao contrário do ao vivo.
Quem escolheu a série escolheu; o `doAoVivo` não entra nesta história.

---

## 3. Ordem que eu proporia

| | | por que nesta ordem |
|---|---|---|
| 1 | `Quadro` no lugar do `Cartaz` + `andado` + ✓ | uma tela, peças que já existem, e é a metade do ganho |
| 2 | zerar a rolagem ao entrar/sair da série | defeito puro, três linhas |
| 3 | série na busca não pode dar 404 | defeito puro, um `when` |
| 4 | fileiras por temporada + cabeçalho fixo | a mudança estrutural |
| 5 | herói com `continuar` | depende de 4 pra ter onde morar |
| 6 | próximo episódio | o maior, e o único que mexe no player |

Os três primeiros cabem numa tarde e já mudam a tela. Do 4 em diante é redesenho
de verdade.

---

## 4. Feitos em 17/08/2026 — os três primeiros

Tudo verificado na TCL, salvo onde está dito o contrário.

### O quadro no lugar do cartaz ✓ visto

`Cartaz` → `Quadro` na grade de episódios, com `andado` e a marca de visto. Sete
colunas viraram três, e o `still` deixou de ser cortado: o `S01E03` do `Arrested`
voltou a ser a cena inteira em vez de uma roda de bicicleta.

⚠️ **A barra de andado e o ✓ não foram vistos acesos** — nem `Arcane` nem
`Arrested Development` têm episódio começado nesta conta. O caminho do dado está
escrito (`ondeParou`/`finished` do `ObraDaLista`, os mesmos campos que a grade de
filmes já usa), mas quem confirma isso é a primeira série que alguém assistir.

### A rolagem herdada ✓ visto ao entrar, ⚠️ não visto ao sair

Entrar: verificado. Com a biblioteca 16 fileiras abaixo, o `Arrested` abriu em
`Piloto · S01E01`, com o cabeçalho na tela — antes abria em `S03E11`.

⚠️ **Sair devolvendo a posição do acervo: escrito, não exercitado.** Compila e é
simétrico ao de entrar, mas não consegui pôr o D-pad no lugar certo pra medir —
ver a nota do foco abaixo.

### A série pela busca ✓ visto

`Arcane` abre a série em vez de dar 404. O destino já existia; a busca passou a
mandar o **item inteiro** pro `entrarNaSerie`, e não id+título pro
`abrirSerieDeFora` — que é de onde vinha o `18 de 0`.

### E duas coisas que só apareceram porque a tela foi olhada

| | |
|---|---|
| **`18 de 0`, depois `18 de 1`** | os dois números vieram do servidor: `work_count` de `/api/library?q=` devolve **1** pra `Arcane`, enquanto o da grade devolve **84** pro `Arrested`. O cabeçalho passou a só afirmar um total que **se sustenta** — se ele for menor que o já carregado, escreve `18 episódios` e ponto |
| **a série abria com o menu por cima** | `temFoco` é «há itens **ou** episódios», e entrando numa série ele já era `true` e continuava `true` — o `LaunchedEffect` nunca redisparava. O cartão focado sumia, o foco subia pro trilho, e o trilho **abre quando tem foco** |

⚠️ E o conserto do foco custou duas tentativas: `requestFocus()` num nó ainda não
anexado **não lança** — volta normal e não faz nada. O primeiro laço parava no
primeiro «deu certo» e não fazia diferença nenhuma. Ver `insista` no fim do
arquivo.

## 5. O que fica torto

**Sair de uma série (ou voltar de uma ficha) larga o foco no trilho**, e o trilho
aberto cobre um terço da tela. Uma seta ▶ resolve, e é anterior a este trabalho —
mas é o mesmo defeito de sempre nesta casa: **foco sem dono sobe**. Vale como o
próximo item da fila, junto com os do redesenho.

---

## 6. Construído em 18/08/2026 — a TV

Desenho aprovado pelo dono («APROVADO eu amei»), com um pedido a mais: **as capas
de temporada devem vir do TMDB, como o Jellyfin faz**. Elas vêm — mas pelo
servidor, que é quem identifica a obra e quem já baixa arte. Ver
`PEDIDOS-AO-SERVIDOR.md, «já entregue» 10`.

### O que existe agora

| | |
|---|---|
| `ui/serie/ModeloDaSerie.kt` (`:core`) | carrega a série **inteira** (o agrupamento exige), agrupa por `season_number`, e decide onde parar. 13 testes |
| `TelaDaSerieDaTv` | letreiro, contagem, `continuar`/`começar` com o episódio escrito, `do começo`, fileira de temporadas |
| `TelaDaTemporadaDaTv` | a lista: quadro 16:9, número, título, `S01E04 · 43min · faltam 21min`, ✓ de visto, barra de andado |
| `Onde.Serie` / `Onde.Temporada` | destinos de verdade, cada um com foco e `BackHandler` próprios |

⚠️ A grade plana **morreu**: `item.eSerie` na biblioteca e na busca agora abre a
ficha. O `entrarNaSerie` do `ModeloDaBiblioteca` continua existindo porque o
celular ainda o usa — ele sai quando o celular receber estas telas.

### Visto na TCL

- `Arcane` abre com `2 temporadas · 18 episódios` e duas fileiras de temporada
  com arte **diferente** uma da outra;
- a temporada lista os 9 episódios com título e duração reais (`41min`, `38min`);
- assisti 45s do `S01E01` e voltei: o botão virou **`▸ continuar S01E01 ·
  Entrando na brincadeira`**, o `do começo` nasceu ao lado, e a linha do episódio
  virou `S01E01 · 41min · faltam 40min` em dourado, com a barra no quadro.

### ⚠️ Três coisas que o desenho não previu e a tela cobrou

| | |
|---|---|
| **o mockup era em pixels; a TV desenha dp a 2×** | com `Sala.quadroL` (240dp) cabiam **duas** fileiras na tela. O quadro da lista é 150×84dp — menor que o da sala, e é a única medida deste arquivo que não sai de `Sala` |
| **o mesmo pano de fundo não serve às duas telas** | na ficha a arte é o assunto; na lista ela virou um rosto de dois metros atrás de nove linhas. Daí o `PanoDeFundo(forte = …)` |
| **a ficha do episódio voltava pra home** | ela sempre voltou pra casa, e estava certo enquanto só a grade a abria. Vinda de uma temporada, pulava dois degraus — o mesmo defeito que o canal teve, com a mesma cura: a tela passou a **carregar de onde veio** |

⚠️ E um que eu escrevi e a tela não pegaria: o `continuar` e o `do começo`
apontavam os dois para `tocarEm = 0.0`. Compilava, abria o player, e o botão que
diz «continuar» começaria do zero — o defeito só apareceria pra quem tivesse
parado no meio.

## 7. O celular — 18/08/2026

Mesmo desenho, mesmas palavras, mesma ordem. `TelaDaSerie` e `TelaDaTemporada`
no `:app`, sobre o **mesmo `ModeloDaSerie`** do `:core` — a lógica de agrupar,
contar e decidir onde parar não foi escrita duas vezes.

### O que muda da sala, e por quê

| | |
|---|---|
| o pano de fundo é **um bloco 16:9 no topo**, não a tela toda | numa coluna estreita, arte atrás de texto vira textura |
| o botão principal é **largura cheia**, 52dp | é o alvo mais tocado da tela |
| as temporadas rolam **na horizontal** | vertical empurraria a primeira ação pra fora da dobra numa série de 5 |
| o quadro do episódio é 128dp | cabem oito na tela; na TV cabem cinco de 150dp, porque lá se lê a três metros |

### Visto no emulador

`Arcane` abre com as duas temporadas e arte diferente em cada uma; a temporada 1
lista os **9 episódios** com título e duração reais, oito visíveis sem rolar.

⚠️ **O «voltar» nascia em cima do relógio.** O bloco de arte é borda a borda de
propósito — ele sobe até debaixo da barra de status, como a ficha. Faltava o
`statusBarsPadding` **no botão** (e não no `Box`, que tiraria a sangria da arte).

### ⚠️ E o mesmo defeito da TV estava aqui, esperando

A ficha do episódio voltava pra **biblioteca**, pulando a temporada de onde veio.
Consertado do mesmo jeito — `Onde.Ficha` carrega `daSerie`/`daTemporada` — e com
um cuidado a mais que a TV não precisou: no celular há **duas** saídas, a tecla
e o botão desenhado, e as duas tinham que passar a concordar. Dois caminhos de
saída para lugares diferentes é o defeito mais fácil de escrever e o mais difícil
de notar lendo.

### Anotado de passagem, não consertado

Com o teclado aberto, a grade da busca fica **vazia** mesmo com o cabeçalho
dizendo `1 de 1`; o item aparece assim que o teclado fecha. Não é do trabalho de
séries e não foi investigado.

## 8. iOS — 18/08/2026

⚠️ **No iOS série não era tratada de forma alguma.** `item.eSerie` mudava um
rótulo no cartaz e mais nada; tocar numa série abria a ficha da obra e dava
**404** — o mesmo defeito que a locadora pagou com a caixa de temporadas e que a
TV pagou na busca, agora pela terceira vez, no terceiro cliente.

Foi preciso construir a base junto com as telas:

| | |
|---|---|
| `ObraDaLista` em `Modelos.swift` | a listagem **plana**; o iOS só conhecia a agrupada |
| `obras(colecao:)` no repositório | `GET /api/works?collection=…` |
| `ModeloDaSerie.swift` | `@Observable`, espelhando o Kotlin regra por regra — inclusive «Especiais» e «Sem temporada» |
| `TelaDaSerie` / `TelaDaTemporada` | o desenho aprovado, com `NavigationStack` de dois degraus |

⚠️ **As duas telas usam `Color.clear` + `background`** pra arte, e não
`scaledToFill`. É a sexta vez que este app encontra a mesma armadilha: uma
imagem que preenche reivindica a largura intrínseca dela, e os cartões saem com
larguras diferentes. Ver o `MenuDeDVD`.

### ⚠️ Não foi visto rodando

Compila, os 86 testes do iOS passam, e o app abre — **na tela de login**. Não
tenho a senha da casa e não é coisa que eu deva digitar. Então as duas telas do
iOS estão na mesma situação em que a TV esteve ontem: escritas, compiladas e
**não exercitadas**.

Quem entrar no simulador e tocar numa série fecha essa conta.

## 9. A web — 18/08/2026

⚠️ **Ela era a única que não estava quebrada.** Desde a R3 a web já navegava
série → temporada → episódio: `filters.collection` trocava a grade pela listagem
plana, e o `porTemporada` já agrupava — inclusive com **Especiais** (temporada 0)
e **Sem temporada**, que foi de onde o Android e o iOS copiaram a regra certa,
depois de eu ter dobrado os dois na temporada 1.

O que ela fazia era empilhar **todas** as temporadas na mesma rolagem, cada uma
com sua grade. Em `Malcolm` são 151 cartões em sete blocos.

### O que mudou

| | |
|---|---|
| `FichaDaSerie` | pano de fundo, letreiro, contagem, `continuar`/`começar` com o episódio, `do começo`, fileira de temporadas com arte própria |
| `ListaDaTemporada` | a lista: quadro 16:9, número, título, `S01E04 · 43min · faltam 21min`, ✓ de visto, barra de andado |
| `ondeParar` / `codigoDoEpisodio` | as mesmas regras dos outros três clientes |
| `EpisodeCard` | **removido** — 73 linhas que só existiam pra grade empilhada |

⚠️ A `.grid.larga` saiu junto: ela existia só pros cartões de episódio.

### ⚠️ Não foi vista rodando

`tsc` limpo, `vite build` passa, o dev server sobe e a tela abre — **no login**.
Mesmo muro do iOS: não tenho a senha da casa e não é coisa que eu deva digitar.

Então a web e o iOS estão os dois onde a TV esteve ontem: escritos, compilados e
não exercitados. A TV e o celular, esses foram vistos.

---

## 10. O «continuando» colapsado por série — 18/08/2026

Primeiro item da ordem aprovada pelo dono, depois de medir o acervo: **120
séries contra ~8.213 filmes** na biblioteca agrupada. As 120 seguram 8.475
episódios — por conteúdo são metade do acervo, por espaço são 1,4% da grade.

### O defeito

A rota `/api/continue` devolve por **obra**, e episódio é obra. Quem parou no
meio de três episódios de uma série recebia três cartões, os três escritos com o
nome da série — porque todo cliente desenha `tituloDaSerie ?: title`. Lado a
lado eles não dizem três coisas: dizem a mesma coisa três vezes, e empurram pra
fora da fileira as obras que **de fato** são outras.

### O conserto, num ponto por cliente

`colapsarPorSerie` — Kotlin, Swift e TypeScript — aplicado **onde a lista é
lida**, e não onde ela é desenhada:

| | |
|---|---|
| `RepositorioOdeon.paraContinuar()` | serve a grade, o widget e o canal da home da TV |
| `RepositorioOdeon.paraContinuar()` (iOS) | idem |
| `api.continueWatching()` (web) | idem |

Sobrevive o **primeiro** de cada série, porque a rota devolve por recência: o
episódio mais recente é onde a pessoa estava. Manter o de menor `season/episode`
daria o começo da série a quem está no fim dela. Filme não é tocado. 6 testes.

⚠️ A chave é o **título**, porque a rota não manda id de coleção. Duas séries
homônimas colapsariam numa. O acervo tem um par quase-homônimo — `A Pantera
cor-de-rosa` (1978) e `A Pantera Cor-de-Rosa` (1993) — e ele **não** colide
porque a comparação é sensível a caixa. É sorte, está escrito, e o pedido do
`collection_id` fica para o servidor.

### O `collection_id` no ao vivo, medido — 19/08/2026

Ele **existe** e nenhum dos quatro clientes lê. Medido no aparelho, lendo o corpo
cru das respostas antes do parse (o `ignoreUnknownKeys` do Kotlin faz o app
engolir campo novo em silêncio, então o modelo não serve de prova):

| rota | tem `collection_id`? | o que os números disseram |
|---|---|---|
| `/api/live/channels` | **sim** | 18 canais · 13 com `work_id` · 1 com `collection_id` |
| `/api/live/guide` | **sim** | 255 programas · 96 só obra · **59 só coleção** · **0 com os dois** · 100 com nenhum |
| `/api/live/odeon` | não | só `work_id`/`media_file_id` — a grade da casa toca arquivo, e episódio ali é obra |

⚠️ **O par é mutuamente exclusivo.** Episódio de série vem com `collection_id` e
`work_id: null` — e é por isso que hoje o app trata um episódio no ar como
«programa sem nada atrás», igual a um canal sem EPG. O `collection_id` é o mesmo
id que a tela da série já recebe (`ModeloDaSerie(serieId)`), então a ponte é
direta quando alguém decidir onde ela encosta na tela.

⚠️ **A superfície é que não existe no celular**: a grade da programação é desenho
puro (os blocos não recebem toque e o `Programacao` descarta tudo menos título,
início e fim), e o toque do cartão já foi fixado como «um toque e já vai». Sem
decidir onde a ficha da série é alcançada, consumir o campo não tem onde aparecer.

### ⚠️ Não foi exercitado na tela, e digo por quê

A conta do emulador tem **29 filmes** começados e **nenhuma série**. Toquei dois
episódios do `Arcane` pra criar o caso, ~40 s cada, e eles não entraram na
fileira — provavelmente abaixo do piso de 30 s que o `OndeContinuarTest` já
documenta, porque o player leva alguns segundos pra começar. A TV, que tinha o
caso montado, foi desligada no meio da tarde.

Então: a regra tem seis testes e um ponto de aplicação por cliente; a fileira
colapsada **eu não vi**.

### Dois defeitos que a caçada expôs — e esses foram vistos

| | |
|---|---|
| **a origem não atravessava o player** | a ficha sabia de onde veio, mas quem voltava do player remontava a ficha do zero: o «voltar» que devia devolver à temporada devolvia à biblioteca. É o terceiro `canalId`/`canalNome` deste arquivo — **quem empilha uma tela carrega a de baixo junto** |
| **o rótulo mentia** | o botão dizia `‹ biblioteca` sempre. Agora diz `‹ Temporada 1` — o texto e o toque contando a mesma história |

Os dois consertados e vistos no emulador.

---

## 11. As prateleiras — a TV primeiro · 18/08/2026

Segundo item da ordem aprovada. A TV veio primeiro porque era o **único cliente
sem filtro nenhum**: nem formato, nem gênero. Numa grade de 8.333 entradas onde
120 são séries — **uma em 69** — isso queria dizer que série só se alcançava
pela busca, e só sabendo o nome.

### Como ficou

`Filtros.prateleira`, separado das `etiquetas` de propósito: prateleira **não é
filtro**, é em qual metade do acervo se está. Quem está nas séries e limpa os
filtros continua nas séries.

⚠️ As prateleiras **não são escritas à mão**: vêm do espaço `format` que o
servidor declara. Se ele acrescentar um formato amanhã, ele aparece — e por isso
a TV mostra `tudo · série · filme · anime`, quatro, e não os dois do mockup.

### ⚠️ A contagem teve de ser **perguntada**, e é a lição do `18 de 1`

O mockup aprovado dizia `8.213 filmes · 120 séries`. Os dois números estavam
errados, e pelo mesmo motivo: eu tinha lido a `work_count` da etiqueta, que conta
**obras**. A etiqueta `format:série` conta **8.475 obras** (episódios) e a grade
mostra **120 entradas**. Pôr 8.475 ao lado de uma grade de 120 é exatamente o
`18 de 1` de novo.

Então cada prateleira é perguntada ao servidor com uma consulta de **uma linha**
— o `total` vem repetido em toda linha, então `limit=1` já responde. Visto na
TCL: `série 120 · filme 834 · anime 3`, e o cabeçalho acompanha (`60 de 834`,
`60 de 120`).

### ⚠️⚠️ E aí apareceu o número que ninguém tinha visto

    120 séries  +  834 filmes  +  3 animes  =  957
    a biblioteca tem                          8.333

**~7.376 entradas não têm formato nenhum.** São 88% do acervo sem classificação —
obras que a identificação não casou. Elas só existem em «tudo».

Isso não invalida a prateleira: quem quer série continua achando as 120 em dois
apertos, o que hoje é impossível. Mas muda o que «filmes» quer dizer — a
prateleira mostra 834 **filmes identificados**, e não «os filmes». Decisão do
dono, e está na mesa:

| | |
|---|---|
| **deixar como está** | «filme» é uma prateleira honesta de 834; o resto vive em «tudo» |
| **inverter** | a prateleira vira `séries` e `tudo o mais`, e some a promessa de que «filme» é uma metade |
| **pedir ao servidor** | um formato inferido pra quem não casou (episódio tem `season_number`; o resto é filme) |

### Visto na TCL

`tudo` aceso na abertura, `série 120` e `filme 834` trocando a grade e o
cabeçalho junto. O alternador **não aparece dentro de uma série** — ali a grade é
dos episódios daquela série, e trocar de prateleira no meio disso não quer dizer
nada (§24).

### As outras três telas — 18/08/2026

Mesmo desenho, mesma decisão, mesmas palavras:

| | |
|---|---|
| **celular** | fileira de pílulas acima da barra de filtros, rolando na horizontal. Visto: `série 120` aceso, `60 de 120` no cabeçalho |
| **iOS** | `Filtros.prateleira` + `PilulaDaPrateleira`, a prateleira e a etiqueta juntas no mesmo `?tags=` |
| **web** | `Filters.shelf` + `.prateleiras`, com a mesma `.chip` da barra de filtros |

⚠️ **A busca passou a dizer onde procura.** Ela **sempre** respeitou a
prateleira — as duas viajam no mesmo pedido —, mas escrevia «buscar na
biblioteca…» em todas. Agora diz `buscar nas séries…`. Foi o que me confundiu
medindo o acervo hoje de manhã: digitei `arcane`, recebi um resultado e não sabia
se tinha procurado em tudo.

Visto no emulador. No iOS e na web, escrito e compilado — **os dois continuam
atrás do login**.

### Uma decisão que ficou, e vale saber

O **herói e o «continuar» não seguem a prateleira**: estando nas séries, o herói
ainda pode ser um filme começado. É de propósito — a prateleira é sobre navegar o
acervo, e o «continuar» é sobre o **seu estado**. Trocar de prateleira não devia
esconder o que você está no meio de assistir.

---

## 12. O servidor respondeu — e as reservas saíram · 18/08/2026

O `PEDIDOS-AO-SERVIDOR.md, «já entregue» 9, §9 e §10` foram atendidos no mesmo dia. O que a
ficha da série montava de reserva agora vem pronto:

| reserva de ontem | de onde vem agora |
|---|---|
| pôster de temporada = `still` do 1º episódio | **pôster do TMDB**, 461 de 473 |
| sinopse da série = **omitida** | `collection.overview`, 115 das 120 |
| backdrop = do 1º episódio | `collection.backdrop`, 118 das 120 |
| sinopse por episódio = **não desenhada** | `overview` em `/api/works`, 7.628 de 14.844 |
| nome da temporada = `Temporada N` | nome próprio quando há (26 das 473) |

⚠️ **As reservas ficaram todas no lugar**, e não por preguiça: 12 temporadas não
têm pôster (a de especiais, na maioria), 5 séries não têm sinopse, 2 não têm
backdrop. A junção é **campo a campo** — cada um cai pro que havia antes quando o
de lá é nulo —, e a coleção **não bloqueia**: ela é pedida junto dos episódios e
a tela abre sem ela.

⚠️ E a junção é pelo `position`, que numa temporada é o número dela. Temporada
que o servidor manda e a listagem de episódios não tem é **ignorada**: a tela
mostra o que existe em arquivo, não o que o TMDB diz que deveria existir (§18).

### ⚠️ O cartão de temporada virou **retrato**, e a tela é que cobrou

Ele era 16:9 porque a única imagem era o `still` do primeiro episódio. Com o
pôster de verdade — que é 2:3 — o quadro deitado cortava a arte inteira: visto no
emulador, a `Temporada 1` do Arcane virou **um olho**.

É a mesma lição do episódio, ao contrário. Lá a moldura era retrato e a imagem
16:9; aqui a imagem virou retrato e a moldura não acompanhou. **A moldura segue a
imagem** — nos quatro clientes.

### As prateleiras pegaram os números novos sem uma linha de código

`série 120 → 5.143`, `filme 834 → 981`, e uma prateleira nova apareceu sozinha:
`clipe 24`. Foi o que a decisão de tirar a lista do servidor comprou.

⚠️ E o aviso deles («a prateleira série passa a ser majoritariamente episódio
solto») **não se confirmou na tela**: rolando fundo, o que aparece é Naruto (220
ep), O Mentalista (149 ep), Os Simpsons, Narcos — séries agrupadas, com contagem.
Não caracterizei a cauda inteira; o que vi foram séries.

### Visto no emulador

Arcane abre com a sinopse de verdade, `Temporada 1` e `Temporada 2` com **pôsteres
próprios e diferentes**, e a lista da temporada com a sinopse de cada episódio.

---

## 13. Séries virou **aba** — e o que as duas tentativas antes dela ensinaram

O dono olhou a prateleira de pílulas e disse: **«ficou feio demais»**. Estava
certo, e o diagnóstico é preciso: eu tinha feito **a separação parecer um
filtro**. Uma fileira de chips idêntica à de `filtros ▾` logo abaixo, e a mais
importante das duas com a cara da menos.

### As duas tentativas que falharam, e por quê

| tentativa | por que morreu |
|---|---|
| **caixa de coleção em 3D** | é o **mesmo objeto da locadora**, onde ele significa *escassez e posse* — «pegue a fita, ela sai da estante dos outros». Repeti-lo aqui gastaria esse significado. Quem viu foi o dono, com uma pergunta: «então usar as mesmas capas da locadora?» |
| **estante de lombadas** | obrigava a **ler de lado**. E eu tinha **elogiado isso como virtude** («você lê os títulos de cabeça virada»), o que é o pior tipo de erro: defender o defeito |

O padrão das duas: eu estava **decorando** em vez de resolver.

### O que ficou é o que ele propôs no primeiro dia

**Duas bibliotecas separadas**, como o Jellyfin faz. A barra de baixo virou
`filmes · séries · ao vivo · locadora · guia`, e o «para você» desceu pra gaveta
do canto — o mesmo caminho que o mural fez quando o ao vivo entrou.

| | |
|---|---|
| **filmes** | a biblioteca de sempre, sem série no meio. `3.187` |
| **séries** | `na metade` (as começadas, em quadro largo) e depois `todas as séries` |

### ⚠️ O `?tags_not=` é o que fez a aba dos filmes ser honesta

Fixar `format:filme` daria **981** e esconderia as 2.182 entradas que o scanner
não classifica. Com a negação, a aba dos filmes é «tudo que não é série»:
**3.187**, e o `total` do cabeçalho fala do mesmo conjunto que a grade.

⚠️ **O anime entra na exclusão.** O servidor mediu: `tags_not=format:série`
sozinho deixa passar o `Beyblade` — 43 episódios que carregam `format:anime` e
não `format:série`. Uma série de 43 episódios na aba dos filmes é o defeito que
essa aba existe pra não ter.

### ⚠️ Dois defeitos que só a tela mostrou

| | |
|---|---|
| **a aba das séries abria com 007** | `primeiraPagina()` lançava uma corrotina **solta**, que ninguém conseguia cancelar: a consulta filtrada saía primeiro e a do acervo inteiro chegava depois, por cima. É corrida — some sozinha num servidor rápido e volta num lento |
| **e demorava 8 segundos pra se corrigir** | o `sóSéries` esperava as **quatro sondas de contagem** que existiam pra escrever `série 5143` dentro da pílula. As pílulas viraram abas; as consultas ficaram. Uma aba que mostra a coisa errada por oito segundos não está carregando — está mentindo, e depois se corrige |

⚠️ E a linha do cartaz saía `4 temporadas ·` cortada. A regra agora é **um número
só**: temporadas quando há mais de uma, episódios quando há uma só. «1 temporada»
não informa nada — toda série tem pelo menos uma.

### Falta

A TV, o iOS e a web **continuam com a prateleira de pílulas**. O desenho aprovado
é o de abas, e eles ainda não o receberam.

### As abas nas outras três · 18/08/2026

| | |
|---|---|
| **TV** | `SÉRIES` entrou na trilha logo abaixo de `FILMES`, com o mesmo ícone de três lombadas. A trilha é vertical e tem espaço — o que não tinha era a barra do celular |
| **iOS** | `séries` entrou na barra e o **«para você» virou folha**, pelo mesmo canto do perfil. Mesma decisão que o Android tomou com o mural |
| **web** | `filmes · séries` na barra de cima. As duas abas desenham o **mesmo bloco** — o que muda é o filtro que a aba escolhe |

⚠️ **Nenhuma das três foi vista rodando.** A TCL está desligada; o iOS e a web
seguem atrás do login. As três compilam, e o Android passa nos testes.

⚠️ E um detalhe que só a build da TV pegou: o `ic_aba_series.xml` copiado do
`:app` trazia `?attr/colorControlNormal`, que **não existe** no tema da sala — a
build quebra no link dos recursos, não no Kotlin. Na TV quem tinge é o `Icon` do
chamador.

⚠️ **Um teste do iOS falhou uma vez e passou na seguinte**, com o mesmo binário.
Fica anotado como instável, e não como «passa».

### Seis relatos do dono, e o que cada um era · 18/08/2026

| | o que era |
|---|---|
| **1. a aba das séries sem padding** | e sem **cabeçalho e sem busca** — eu tinha escrito a grade e esquecido a tela em volta dela. Agora tem `séries 5143`, o campo de busca e os mesmos 16dp da grade dos filmes |
| **2. série no «continuar» dos filmes** | a grade já não trazia série (o servidor tira), mas a **fileira de continuar vem de outra rota** e trazia. Agora cada aba fala do que ela guarda: 29 → 28 na dos filmes |
| **4. o «voltar» caía nos filmes** | a ficha da série voltava pra `Onde.Biblioteca`, que agora é a **dos filmes** — um lugar onde a série que se acabou de fechar não existe. E episódio sem origem também |
| **5. o menu de baixo «num modo full do nada»** | o player escondia as barras e, ao sair, devolvia **as barras mas não o comportamento delas**: ficava `TRANSIENT_BARS_BY_SWIPE`, que mede a janela como tela cheia. No tablet o menu ia parar debaixo da barra do sistema |
| **6. mural e «para você» sem saída** | as duas saíram da barra e viraram tela empilhada **sem nenhuma âncora** — a única saída era o gesto do sistema. Ganharam um `‹ voltar` desenhado |

⚠️ **A regra que o 5 deixa:** quem muda um modo da janela **restaura os dois
lados dele** — o que está visível *e* como ele se comporta. `show()` sem
`BEHAVIOR_DEFAULT` é meio conserto.

### ⚠️ 3. «não consigo executar nenhuma série» — não consertado

A ficha do `S01E01` do Arcane diz, ela mesma, o que está acontecendo:

    transcodificando · o cliente não toca áudio em eac3

Ou seja: os arquivos das séries têm áudio **E-AC3**, o aparelho não decodifica, e
toda série passa por transcodificação — que é o caminho mais frágil e mais lento.
A tela preta é depois disso.

⚠️ **Não fui além disso**, e não vou fingir que fui: falta abrir o player com log
e ver se a sessão de transcodificação nasce, se ela morre, ou se o player desiste
esperando. É uma sessão de diagnóstico própria, não um item ao lado de outros
cinco.
