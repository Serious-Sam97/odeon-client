# O redesenho do app Android

Escrito em **04/08/2026**, depois de a v1 inteira estar rodando — as sete fases
da §5 da espec, todas vistas em aparelho.

Este documento é sobre a **segunda** pergunta, que só faz sentido agora que a
primeira está respondida: o app funciona, e ele ainda não se parece com o Odeon.

> ⚠️ **É proposta, não plano aprovado.** A regra 4 da casa manda planejar junto,
> e a §7 do `CONTINUAR-ANDROID.md` repete: *"sempre planejar comigo"*. Cada fase
> abaixo tem o que muda, por quê, e como saber se deu certo — mas a ordem e o
> corte são do dono.

---

## 0b. O segundo redesenho — «dar vida»

Pedido em 04/08/2026, depois de as nove fases estarem feitas: *«não refazer,
melhorar, dar vida»*, e a direção dada pelo dono foi **brincar com o preto e o
amarelo**.

### O diagnóstico, e ele não era falta de cor

| uso | app **antes** | web |
|---|---|---|
| `destaque` / `--accent` | 26 | 210 |
| `destaqueApagado` / `--accent-dim` | **2** | 107 |
| `destaqueQuente` / `--accent-hot` | 2 | 19 |
| dourado como **luz** — sombra, halo, gradiente | **0** | **19** |
| animações declaradas | 4 | 36 |

**O app usava o dourado como tinta; a web usa como luz.** E o `Tema.kt` já
chamava os três tons de «o filamento aceso», «o topo da luz» e «o filamento
apagado» desde a fase 1 — nomes que descreviam uma sala acesa que o código nunca
desenhou.

A proposta foi desenhada e aprovada antes de qualquer linha. Três levas:

| leva | o que entrou |
|---|---|
| **1 — a luz** ✅ | `ui/Luz.kt`: os três tons ganham papel. Contorno dourado nos cartazes, barra de progresso que esquenta na ponta, pílula que acende por baixo, e o botão de assistir **projetando** halo |
| **2 — a chegada** ✅ | herói na biblioteca (a tela mais visitada era a mais plana), a **tábua** sob as caixas da locadora, e a `dominant_color` tingindo a chegada |
| **3 — o ritual** ✅ | `ui/Chegada.kt` — as caixas caem na prateleira, escalonadas — e **apagar a luz**: tocar em assistir escurece a sala antes de trocar de tela |

### E a locadora virou **loja**

Sobrou preto na locadora depois da leva 2, e a pergunta era como preencher. A
resposta não era desenho: era uma rota.

**`/api/locadora/estantes` existe desde antes deste app e nenhuma linha dele a
chamava.** Ela devolve `Loja { estantes[], no_acervo, semana_de, vira_em }` — a
vitrine inteira, em estantes com nome. O que a tela mostrava era só o que
**saiu** da estante: os seus empréstimos e os dos outros.

É o mesmo padrão de `height`, `size_bytes` e `tags` — o servidor já dava, o
cliente não pegava. A diferença é o tamanho: não era um campo, era metade de uma
tela.

Agora ela desenha as estantes com a placa da web (`.placa span`: serifada de
24px em `--accent` **com halo de 24px a 42%** — o dourado como luz na forma mais
literal que a folha tem), as caixas na mesma pose de três quartos das
emprestadas, e a tábua por baixo. A placa diz «Terror · 8 de 145», e o segundo
número é do **acervo**, não da vitrine: é o que impede a placa de mentir sobre
uma seleção que gira.

O `vira_em` entrou junto, e o comentário da web explica por quê: «é o que torna
a rotação **promessa, não sorteio**».

⚠️ E isso tornou obsoleto o conserto anterior: as duas tábuas vazias do estado
sem empréstimo existiam pra preencher um vazio que deixou de existir. Elas agora
só aparecem quando **não há vitrine** — o que muda não é a regra, é o que está
em volta.

### Três coisas que o screenshot achou

**A locadora vazia contradizia a própria frase.** Ela diz «nenhuma caixa fora da
estante» — afirmação positiva — e desenhava isso como 1.400 pixels de preto.
Agora desenha as tábuas vazias: a mobília da loja existe mesmo sem caixa fora.
⚠️ Sem inventar estoque — esta tela só conhece o que **saiu** da estante.

**O herói saía cinza.** `brightness(0.32)` sobre um pôster de neve branca tira a
cor junto com a luz, e a lavagem a 30% não repunha. A web usa 42% na
`.hero-wash`, e o número dela não era estético: era corretivo.

**`padding` negativo derruba o app.** A tábua sangra 16dp pra cada lado, e a
primeira versão pediu isso com `padding(horizontal = (-16).dp)`. Compilou, passou
no lint, e o app caiu ao abrir a locadora com `IllegalArgumentException: Padding
must be non-negative`. O jeito certo é medir com folga e posicionar deslocado —
mais uma para a lista de «compilar e passar no lint não é ter visto».

---

## 0. O que já foi decidido e feito — atualizado em 04/08/2026

**As duas decisões que a §6 e a R1 pediam foram tomadas pelo dono:**

| | decidido |
|---|---|
| onde ficam os destinos (§6) | **barra inferior adaptativa** — e não a opção 3, que era a preferência escrita abaixo |
| a fonte serifada (R1) | **embutir no APK** |

E o **redesenho inteiro está feito e visto em aparelho** — a decisão da §6 e as
nove fases, R1 a R9. A §6 abaixo fica como registro do que se pesou.

> ### ⚠️ A régua de fps da R4 não é aplicável no emulador — e isso precisa ser
> ### resolvido antes da leva 3
>
> A R4 diz: «a régua é o quadro perdido — se a rolagem sair de 60fps no emulador,
> o enfeite sai». Tentado, com `dumpsys gfxinfo`, seis arrastos iguais sobre
> conteúdo já carregado:
>
> | | quadros | perdidos | 90º percentil |
> |---|---|---|---|
> | com o afundar ao toque | 151 | 43,0% | 81ms |
> | sem enfeite nenhum | 87 | 66,7% | 85ms |
>
> **A versão sem o enfeite saiu pior**, o que não pode ser verdade — e 87 contra
> 151 quadros pro mesmo gesto explica: a variância entre execuções é maior que a
> diferença entre as versões. O emulador não segura 60fps nesta grade em nenhuma
> das duas (mediana de 32ms e 36ms, ou seja ~30fps).
>
> A régua como está escrita **não decide nada**. Ela precisa de aparelho de
> verdade, ou de `androidx.benchmark`, que roda a rolagem N vezes e devolve
> intervalo de confiança em vez de uma amostra. **Isso vale antes da leva 4**,
> que é onde entram giroscópio e película — os enfeites que de fato custam
> quadro, e sobre os quais uma medida ruim decidiria errado.

> ### O que a leva 1 ensinou, e que muda a régua das próximas
>
> **A §7 deste documento manda medir com screenshot, e ele achou um defeito que
> compilar e passar no lint não achavam** — pela sexta vez neste projeto.
>
> O `NavigationSuiteScaffold` do Material escolhe **barra inferior quando a
> altura é compacta**, que é exatamente o celular deitado. Medido: **230 dos
> 1080 pixels — 21% da tela — e zero fileiras da grade visíveis**. É o mesmo
> defeito de 17% que fez o cabeçalho fixo sair deste app, e o conserto foi
> passar um `layoutType` explícito.
>
> A lição pras levas 3 e 4, que são as que mexem em movimento: **o padrão de uma
> biblioteca não é a decisão do produto.** Aceitar o padrão é uma escolha, e
> escolha entra medida.

---

## 1. O diagnóstico, e ele não é o que parecia

A primeira suspeita seria cor. **Está errada.**

O `ui/Tema.kt` já carrega a paleta da web, campo a campo, e o comentário dele
diz isso desde a fase 1:

| | app | web (`styles.css`) |
|---|---|---|
| fundo | `#0A0A0C` | `--bg` |
| elevado | `#131318` | `--bg-raised` |
| texto | `#ECEEF4` | `--fg` |
| apagado | `#8B8D9A` | `--fg-muted` |
| destaque | `#E0B062` | `--accent` |

As duas telas são do mesmo projeto pela cor. **O que falta é outra coisa, e são
três:**

### 1.1 A tipografia — a diferença mais visível de todas

A web tem **duas** famílias, e usa a segunda em 53 lugares:

```css
--font-display: ui-serif, Georgia, "Noto Serif", "Times New Roman", serif;
```

Ela aparece em `hero-title`, `player-title`, `pick-title`, `colecao-body h3`, no
número gigante da afinidade, no relógio do "ao vivo". Ou seja: **título e número
são serifados; o resto é sem serifa.**

O app Android é sem serifa em 100% da tela. Não há `res/font/` — nunca houve.

Essa é a razão de as duas telas parecerem de produtos diferentes mesmo com a
mesma paleta: na web, "Drive" e "Harry Potter e a Ordem da Fênix" são **letreiro
de cinema**; aqui são item de lista.

### 1.2 O ritmo — os rótulos e a régua

A web separa seção com um rótulo em **versalete espaçado** e uma linha que corre
até a margem:

```
ESTA NOITE ─────────────────────────────────────────
CONTINUAR ASSISTINDO
COLEÇÕES ──────────────────────────────────  + criar
FRANQUIAS ─────────────────────────────────────  133
```

O `letter-spacing` chega a `0.28em`. É o que dá à página o ar de programa
impresso — e é o que faz uma tela com seis blocos não virar seis listas.

O app tem `Text("continuar")` em `titleMedium`. Funciona, e não diz nada.

### 1.3 O objeto — a coisa que o Odeon é e o app ainda não

A web desenha **coisas**, não registros:

- a caixa de VHS tem **lombada**, e fica de pé na prateleira
- a coleção mostra os pôsteres **em leque**, como quem abre um fichário
- o cartão do "para você" tem **perfuração de filme** nas bordas
- a afinidade é um **36** em serifa de dois centímetros, com `AFINIDADE` embaixo

Nada disso é enfeite: é a tese. Um catálogo de arquivos lista linhas; uma
locadora tem caixas que se pegam.

O app tem retângulos com cantos de 6dp. O flip da caixa (fase 5) é a **única**
coisa que já entrou nessa direção, e ela funcionou.

---

## 2. A régua, pra o experimental não virar barulho

O pedido é **muito experimental**, e o documento leva isso a sério. Mas o
projeto já tem quatro regras, e elas continuam valendo — inclusive contra o
experimento:

1. **Medir antes de desenhar.** Uma animação entra com o número do quadro
   perdido, não com "ficou legal".
2. **Não mentir com cara de metadado** (§18). Nenhuma decoração pode parecer
   dado. Uma barra que não é progresso não pode parecer barra de progresso.
3. **Errar em silêncio é o defeito** (§8b). Movimento não pode esconder estado:
   se a tela está esperando rede, ela diz — mesmo bonita.
4. **Não corrigir sozinho.** Cada fase abaixo é proposta.

E uma quinta, que nasce aqui:

5. **Movimento tem que significar.** Toda animação deste plano responde a uma
   pergunta — *de onde isso veio*, *o que mudou*, *quanto falta*. A que não
   responder nenhuma sai.

**O teste de cada fase é o mesmo do resto do projeto: o screenshot.** Ele achou
o pôster com classe errada, a barra duplicada, a contagem errada, o título
invisível sobre `#F0F0F0` e a duração que andava pra trás. Ele acha desenho
também.

---

## 3. O que o celular tem que o navegador não tem

É a lista de brinquedos, e ela é o que justifica "experimental" em vez de
"porte da web":

| | o que dá pra fazer | onde |
|---|---|---|
| **háptico** | a fita "encaixa" na mão ao ser pega; o seek tem detente | locadora, player |
| **giroscópio** | o pôster tem paralaxe ao inclinar o aparelho — a caixa tem volume | grade, ficha |
| **borda a borda** | arte sob a barra de status, entalhe incluído | ficha, player |
| **gesto** | arrastar pra baixo devolve a fita; puxar de lado troca de versão | locadora, ficha |
| **transição compartilhada** | o pôster **vira** a ficha, em vez de a ficha aparecer | grade → ficha |
| **sensor de luz** | o brilho do "modo cinema" acompanha a sala | player |
| **widget e atalho** | "continuar assistindo" na tela inicial, sem abrir o app | fora do app |
| **always-on / notificação** | a arte da obra no controle de mídia | fora do app |

Nada aí precisa de servidor. É a mesma régua da §4c do Cast: o que já existe
basta.

---

## 4. As fases

Da fundação pra superfície. As três primeiras mudam **todas** as telas de uma
vez e não mexem em comportamento nenhum — é de propósito: elas são o maior ganho
por linha escrita, e a menor chance de quebrar o que já foi verificado.

### R1 — A tipografia

**O que muda:** entra uma família serifada de display, e ela passa a valer para
título de obra, título de tela e **número grande**. O resto do texto continua
sem serifa.

**Onde:** `Tema.kt` ganha um `Typography` de verdade, com papéis nomeados —
`letreiro` (título de obra), `numeral` (36, 17:14), `rotulo` (versalete
espaçado), e os de corpo.

**A decisão que vale pensar antes:** a web usa `ui-serif`, que no Android **não
existe** — não há serifa de sistema garantida. Ou entra uma fonte no APK
(~200 KB por peso) ou o app fica sem display serifado. Proponho embutir, com um
peso só, e medir o tamanho do APK antes e depois.

**Como saber se deu certo:** screenshot lado a lado da ficha, antes e depois. Se
o título não virar letreiro, a fonte escolhida está errada.

---

### R2 — O rótulo e a régua

**O que muda:** todo cabeçalho de seção vira versalete espaçado com linha até a
margem, e com **o número à direita** quando houver um.

```
CONTINUAR ────────────────────────────────────────
BAIXADOS ──────────────────────────────────────  3
FRANQUIAS ───────────────────────────────────  133
```

**Por que cedo:** é o que faz a biblioteca deixar de ser uma grade com um texto
em cima. E é barato — um composable, usado em seis lugares.

**Como saber se deu certo:** a tela da locadora tem hoje três blocos de texto em
sequência. Depois desta fase ela tem três **seções**.

---

### R3 — Os chips

**O que muda:** entram as pílulas que a web usa em todo lugar — o filtro de tempo
("TENHO: 15 min · 30 min · 1h"), as tags da obra ("Estados Unidos", "filme",
"Crime"), o "filtros ▾" e o "ordenar por".

**O ganho concreto:** o "para você" já tem o filtro de tempo **funcionando** e
desenhado como três `TextButton` soltos. A web mostra seis cortes em pílulas, e
é a mesma informação lida de relance.

**⚠️ Cuidado do §18:** tag é dado, chip é forma. Um chip de "Crime" só aparece
se a obra tiver a tag — nunca um "—" nem um chip vazio.

> ### ✅ Feito, e duas coisas que a implementação corrigiu no plano
>
> **Os cortes de tempo não eram três nem seis inventados.** São os seis do
> `TIME_OPTIONS` do `ForYou.tsx:13` — `qualquer tempo`, `15 min`, `30 min`,
> `45 min`, `1h`, `2h`. A primeira versão desta fase inventou um "1h30" e perdeu
> o "15 min", que é justamente o corte que responde «tenho um episódio de tempo».
>
> **A etiqueta mostra namespace *e* valor.** O `Details.tsx:860` desenha
> `{namespace}<b>{value}</b>` — "genre **Ação**", "country **Reino Unido**" — e a
> `color` da tag tinge a **borda**, nunca o fundo. Fundo colorido faria uma
> etiqueta parecer mais importante que as outras, e `color` no servidor não quer
> dizer importância.

---

### R4 — O cartaz vira objeto

**O que muda:**

- a barra de progresso passa a morar **dentro** do pôster, colada na base (a web
  faz assim, e o app já faz isso na fileira de "continuar" — falta na grade)
- entra a linha de metadados: `1969 · 816p · 2h22 · 2.3 GB`
- entram as tags embaixo
- **paralaxe no toque**: o pôster afunda e inclina levemente, e volta

**A medida que este item precisa:** a grade tem 8.316 entradas e rola rápido.
Cada enfeite aqui é multiplicado por tudo que está na tela. A régua é o quadro
perdido — se a rolagem sair de 60fps no emulador, o enfeite sai.

> ### ✅ Feito, com dois cortes que o aparelho impôs
>
> **As tags saíram da grade.** O `LibraryEntry` não as traz — elas são do
> `WorkDetail`. Desenhá-las no cartaz exigiria uma requisição por entrada, e a
> grade tem 8.316. Elas ficaram na ficha (R3).
>
> **O tamanho saiu da linha de metadados.** `1969 · 816p · 2h22 · 2,3 GB` não
> cabe num cartaz de 108dp: no aparelho a linha virou `1969 · 816p · 2h22 · …`,
> e a reticência promete um dado que nenhum gesto daquela tela alcança. O tamanho
> foi pra ficha, ao lado do botão de baixar — que é o único momento em que ele
> importa.
>
> **E `height`/`size_bytes` já vinham na resposta.** Os dois estão no
> `LibraryEntry` da web desde antes deste app existir, e o modelo Android os
> descartava calado. A linha de metadados não custou pedido de servidor nenhum.

---

### R5 — A fita como coisa

**O que muda:** a caixa da locadora ganha **lombada** e fica de pé, como na web.
O flip que já existe passa a girar um objeto com três faces visíveis (frente,
lombada, verso) em vez de duas.

**Háptico:** um toque curto ao pegar a fita, e um mais seco ao devolver. É a
primeira vez que o app usa o corpo do aparelho, e é o lugar certo — pegar uma
fita é um gesto físico na metáfora.

**O que NÃO entra, e é a §3 da espec:** a estante 3D com profundidade de cena.
Ela é CSS 3D e o Compose não compõe hierarquia em Z. O caminho seria uma
superfície OpenGL, e isso é um projeto dentro do projeto. **Continua em aberto.**

> ### ✅ Feito — e a pose importava mais que a lombada
>
> O item dizia «ganha lombada e fica de pé». Medindo a folha, o que faz a caixa
> ser caixa é a **pose**: a web nunca a desenha chapada, ela repousa em
> `rotateX(3deg) rotateY(22deg)` (`styles.css:4256`), e é isso que revela a
> lateral. Lombada sem pose seria uma tarja colada ao lado de uma capa.
>
> Espessura de 27% da largura, tirada do `.caixa.vhs` (28 de 104). E entrou o
> `.brilho` (`:4385`), o verniz diagonal — o comentário da folha diz que ele «é
> o que faz o olho ler objeto em vez de imagem», e é o item de melhor retorno
> por linha desta fase inteira.
>
> **O limite honesto:** o Compose não tem `preserve-3d`, então as duas faces são
> camadas separadas encostadas por conta. A junta só fecha **na pose de
> repouso** — por isso a pose é fixa e não acompanha o dedo. Animar o ângulo
> abriria uma fresta no meio do caminho, e caixa com fresta é pior que caixa
> chapada.
>
> **Dois defeitos que o screenshot pegou:** a lombada mostrava `007 C…` porque
> `graphicsLayer` gira o que já foi medido, e o texto era medido nos 38dp da
> lombada antes de girar (`requiredWidth` conserta); e virar a caixa a espremia
> numa tira, porque o giro estava na capa, que já girava sobre a **aresta
> esquerda** — girar 158° ali é abrir uma porta, não virar um objeto. O giro
> passou pro objeto inteiro, com a pose por dentro.
>
> **O háptico entrou com dois pesos**, e a diferença é semântica: tique seco
> (`TextHandleMove`) pra virar a caixa, batida (`LongPress`) pra pegar e pra
> devolver — que são os gestos que escrevem no acervo de três pessoas.

---

### R6 — O filme como filme

**O que muda:** entra a perfuração de película nas bordas do cartão de destaque
do "para você", como na web. E — experimental — um **grão** discreto sobre a
arte, que é o que a web sugere sem fazer.

**A pergunta honesta:** grão em cima de pôster pode virar sujeira. Proponho
entrar atrás de uma chave, medir com screenshot em três pôsteres (claro, escuro,
ilustrado), e só ficar se sobreviver aos três.

> ### ⚠️ A premissa da perfuração estava errada, e a folha é que disse
>
> A fase diz «a perfuração de película nas bordas do cartão de destaque do "para
> você", **como na web**». Fui buscar os números na folha pra copiar, e ela não
> existe: o `.pick-art` (`styles.css:2317`) é arte com uma lavagem radial e mais
> nada, e não há `repeating-radial-gradient` de furo em lugar nenhum do arquivo.
>
> O que o herói do "para você" tem, e o `ForYou.tsx:369` põe exatamente ali, é a
> **`.bulbs`** — as lâmpadas da marquise com a luz correndo por elas. O
> comentário dela na folha diz: «é o efeito mais da casa que este projeto tem».
>
> **Foi ela que entrou, e é substituição — vetável.** A perfuração continua
> sendo uma ideia legítima e são umas 20 linhas; ela só não é «como na web».
>
> Entrou junto o **herói**, porque a fase pressupunha um "cartão de destaque"
> que o app não tinha: o primeiro da lista virou cartaz 16:9 com a arte a 32% de
> brilho (o `brightness(0.32)` da `.hero-art`), título em letreiro serifado e o
> motivo em destaque. Sem ele, "para você" era uma lista ordenada por `score` —
> e uma lista ordenada por score é indistinguível de "os mais recentes".
>
> ### ✅ O grão foi testado como a fase mandou, e **reprovou**
>
> Dois screenshots do mesmo recorte da grade, com e sem a camada, ampliados
> 1,8× — a grade mostra os três casos lado a lado, então não foram três telas:
>
> | pôster | o que aconteceu |
> |---|---|
> | **claro** — capa de neve branca | **reprovou.** A neve fica manchada. É a sujeira que esta fase previu |
> | **escuro** — Cassino Royale | some no preto. Nem ajuda nem atrapalha |
> | **ilustrado** | embarra de leve; a trama da ilustração e a do grão brigam |
>
> Um pior, um neutro, nenhum melhor — e o critério escrito era sobreviver aos
> três. E o caso que reprova é o mais comum do acervo: **8.598 obras (48%) não
> têm pôster** e caem no fundo chapado da cor dominante, que é onde grão mais
> aparece e menos tem o que texturizar.
>
> Fica escrito e desligado em `Grao.LIGADO`, não apagado — reavaliar custa
> trocar `false` por `true`.
>
> ⚠️ É **julgamento visual sobre um A/B**, não número. Não há régua numérica pra
> "parece sujo", e inventar uma seria pior que assumir o julgamento.

---

### R7 — O movimento com sentido

**O que muda:** a transição da grade pra ficha vira **elemento compartilhado** —
o pôster tocado cresce e vira o pôster da ficha. É a resposta visual à pergunta
"de onde essa tela veio", e é a coisa que mais separa um app nativo de uma
página.

**E o que já existe fica melhor:** o botão que vira "continuar", o selo que
aparece, a fileira que se atualiza ao voltar do player — hoje tudo isso troca
sem transição. São três lugares onde um `AnimatedContent` responde "o que
mudou".

> ### ✅ Feito — a transição grade → ficha
>
> `SharedTransitionLayout` + `AnimatedContent` no `AppOdeon`, com o pôster
> marcado pela chave `cartaz-{obraId}`. Verificado com o
> `animator_duration_scale` do sistema em 10×: no meio do voo o pôster aparece
> **opaco** enquanto a ficha e a barra de abas fazem cross-fade por baixo, que é
> a assinatura de um elemento compartilhado desenhado na camada de cima.
>
> **⚠️ O player fica de fora do `AnimatedContent`, e é decisão.** Ele desenha
> vídeo num `SurfaceView` dentro de um `AndroidView`; animar a entrada dele faria
> a superfície nascer e morrer junto com a animação. O sintoma seria um piscão
> preto no começo do filme — ou o PiP perdendo a superfície ao encolher. E não
> se ganharia nada: não há elemento compartilhado entre uma ficha e um vídeo.
>
> **As três trocas menores da segunda metade deste item não entraram** — o botão
> que vira "continuar", o selo, a fileira que se atualiza. Ficam pra quando
> houver quem peça.
>
> Os outros dois itens da fase 4 (`R6` película e grão, `R8` giroscópio e
> háptico no seek) continuam proposta. Vale registrar uma coisa que veio de
> graça e que a R8 exigia: **o Compose já respeita o
> `ANIMATOR_DURATION_SCALE = 0` do sistema** em `animate*AsState` e
> `AnimatedContent`, então tanto o afundar do cartaz quanto esta transição já
> somem pra quem desligou animação. A chave que a R8 pede é só pro giroscópio,
> que não passa por esse caminho.

---

### R8 — O corpo do aparelho

O experimental de verdade, e o que só existe aqui.

- **Paralaxe por giroscópio** no pôster da ficha: a arte se move ~4dp com a
  inclinação. Volume sem 3D.
- **Borda a borda** na ficha: o backdrop sobe até debaixo da barra de status,
  com o texto respeitando as áreas seguras.
- **Detente háptico no seek**: um tique a cada 10 minutos de filme arrastados —
  a timeline passa a ter textura. ✅ **Feito.** O passo ganhou piso: o menor
  entre 10 minutos e um vinte avos da duração, senão um episódio de 22 minutos
  daria dois tiques, o que é enfeite e não textura. O tique é o **seco**
  (`TextHandleMove`), pelo mesmo motivo da escala da R5: arrastar não escreve
  nada, e a batida está reservada pros gestos que mudam o acervo.
- **Gesto de devolver**: arrastar a caixa pra baixo devolve a fita, com o
  háptico do R5 no fim.

**⚠️ O giroscópio precisa de chave.** Movimento constante na tela é o oposto de
acessível pra quem tem sensibilidade a movimento, e o Android tem a preferência
do sistema pra isso (`Settings.Global.ANIMATOR_DURATION_SCALE` = 0). Respeitá-la
não é opcional.

> ### O que já respeita a preferência, e de graça
>
> Tudo que passa por `animate*AsState`, `AnimatedContent` e
> `rememberInfiniteTransition` lê o `MotionDurationScale` do contexto, que no
> Android sai justamente do `ANIMATOR_DURATION_SCALE`. Ou seja: o afundar do
> cartaz (R4), a transição compartilhada (R7) e a luz correndo na marquise (R6)
> **já somem** pra quem desligou animação nos ajustes, sem uma linha escrita
> pra isso.
>
> A chave que esta fase pede continua necessária **só pro giroscópio**, que lê
> sensor e não passa por esse caminho.
>
> ### ✅ A R8 fechou — com um item declarado como não visto
>
> **Paralaxe.** O sensor certo é o **acelerômetro**, não o giroscópio: o
> giroscópio mede velocidade angular, e integrar isso pra achar posição acumula
> deriva em segundos. O acelerômetro em repouso mede a gravidade, e dela sai a
> inclinação absoluta sem integrar nada. Verificado alimentando o emulador com
> `adb emu sensor set acceleration`: entre −8 e +8 no eixo x a arte desloca
> visivelmente dentro da moldura.
>
> A primeira leitura vira o zero, e não a vertical — senão quem lê deitado veria
> o pôster encostado no canto o tempo todo.
>
> **A chave é lida à mão aqui**, e é o único lugar do redesenho onde precisou:
> sensor não passa por `MotionDurationScale`. Com a preferência em zero o
> listener **nem é registrado** — não é o valor que vira zero, é o sensor que não
> liga.
>
> **Borda a borda.** O backdrop sobe até debaixo da barra de status; o conteúdo
> respeita as áreas seguras. Três defeitos que o screenshot pegou: a arte 4%
> maior pra a paralaxe **vazava** os 220dp sem `clipToBounds`; o `safeDrawing`
> inteiro no conteúdo abria um vão de 40dp porque somava a barra de status **de
> novo**; e com o meio do degradê transparente, um backdrop claro virava uma
> faixa branca gritando.
>
> **✅ O gesto de devolver foi visto rodando** — a caixa desce, desbota, o
> rótulo vira "solte pra devolver", e soltar devolve. Feito duas vezes, e a
> prateleira ficou limpa no fim.
>
> ⚠️ Ele chegou a ser declarado como "não visto", e a declaração estava errada:
> um `adb input swipe` de 900ms não dispara o gesto e com 1600ms dispara sempre.
> O arrasto precisa vencer o `touchSlop` **e** andar 96dp, e rápido demais os
> dois chegam numa rajada que o detector lê como um evento só. É limite da
> ferramenta de teste, não do gesto — e o sintoma é idêntico ao de um gesto
> quebrado, que é justamente o que torna a confusão fácil.

---

### R9 — Fora do app

- **Widget** de "continuar assistindo" — a mesma rota `/api/continue`, sem tela.
  A §4b da espec já lista isso como coisa que o servidor dá de graça.
  ✅ **Feito**, com Glance 1.1.1. Três capas com o quanto falta de cada uma.

  > **Ele não compartilha nada com o app.** Um widget é desenhado pelo launcher
  > a partir de `RemoteViews`; o Glance dá a sintaxe do Compose e gera
  > `RemoteViews` por baixo. Nada de `ui/` entra: nem `Cores`, nem `Tipo`, nem a
  > serifada. A paleta está repetida à mão, e está escrito que está.
  >
  > **Três capas e não todas**: cada bitmap atravessa a fronteira de processo
  > dentro do `RemoteViews`, e o `Binder` corta em ~1 MB por transação.
  >
  > ⚠️ **Dois defeitos que o screenshot pegou.** As capas não apareciam: o
  > `provideGlance` é `suspend` mas não é `suspend` **na IO**, e o `execute()`
  > bloqueante do OkHttp virava `NetworkOnMainThreadException` que o
  > `runCatching` engolia — erro de despachante disfarçado de dado ausente, com
  > o widget parecendo certo e o acervo parecendo sem pôster. E "faltam 141min"
  > truncava nos 64dp da coluna; ficou só o número, que é o dado.
  >
  > ✅ **Ele se atualiza ao assistir.** O `AppOdeon` chama `updateAll` ao voltar
  > do player, na mesma carona do `voltasDoPlayer` que a ficha e a fileira de
  > continuar já usavam. Verificado: assistir 3 minutos e voltar mudou o widget
  > de "141 min" pra "138 min" em segundos — sem isso, o launcher só repediria
  > em até 30 minutos, e um widget cujo assunto é "onde eu parei" mostrando
  > posição velha é o §18.
- **Atalhos** ao segurar o ícone: continuar, locadora, baixados. ✅ **Feito**,
  estáticos — eles levam a lugares, não a obras, então não mudam. O dinâmico
  ("continuar assistindo *Drive*", com capa) fica em aberto.
  ✅ Trocar de aba com o app **já aberto** funciona. Exigiu duas coisas: um
  `onNewIntent` que empurre o extra pra um `MutableState`, e —
  **`android:launchMode="singleTop"`** no manifesto, sem o qual o `onNewIntent`
  nunca é chamado. O `am start` chega a dizer «intent has been delivered to
  currently running top-most instance», o que engana: o que foi entregue é a
  ordem de trazer pra frente, não o `Intent` com o extra.
- **A arte no controle de mídia**: hoje a notificação da sessão sobe sem capa.
  O `MediaSession` aceita `artworkUri`, e o app já tem a URL. ✅ **Feito** — e o
  buraco era maior do que a fase dizia: a `MediaItem` não declarava
  `MediaMetadata` nenhuma, então faltava **o título também**.

---

## 5. O que eu proponho NÃO fazer

Escrito pra o documento não prometer o que não vai entregar — é a mesma função
da §3 da espec.

| | por quê |
|---|---|
| **estante 3D com profundidade** | Compose não compõe hierarquia em Z; exige superfície OpenGL própria |
| **trilha sintetizada do menu de DVD** | é Web Audio gerando som em tempo real (§47); no Android é reescrever o sintetizador em `AudioTrack` |
| **tema claro** | o produto é uma sala escura; um tema claro seria uma segunda paleta pra manter sem ninguém pedindo |
| **copiar a barra de navegação da web** | sete abas no topo é desenho de mouse. No celular o app tem quatro destinos e eles cabem em outro lugar — e é onde entra a decisão de navegação abaixo |

---

## 6. A decisão que precisa vir antes da R1

**Onde ficam os destinos.**

Hoje eles estão como três links no cabeçalho da biblioteca (`locadora ›`,
`baixados ›`, `para você ›`), e isso foi escolha de fase, não de desenho: eram
dois destinos quando nasceu.

Agora são quatro, e a web tem sete abas. As opções:

1. **Barra inferior** (o padrão do Android). Custa ~80dp permanentes, que num app
   de pôsteres é uma fileira a menos.
2. **Gaveta lateral.** Não custa tela, e esconde tudo atrás de um gesto que
   pouca gente faz.
3. **Continuar no cabeçalho**, mas desenhado — os mesmos links, virando chips que
   rolam junto com a grade.

Tenho preferência pela **3**, e o motivo é medido: o cabeçalho fixo já foi
removido uma vez neste app justamente por comer 17% da tela em paisagem. Mas a
1 é o que quem usa Android espera, e "esperado" tem valor que não aparece em
screenshot.

**Isto muda o esqueleto de todas as telas, então vem antes da R1.**

> ### ✅ Decidido: a **1**, na variante adaptativa
>
> Barra inferior em retrato, **trilho lateral** em paisagem e em tablet. O trilho
> é o que responde à objeção de altura acima — ele custa 8,6% da **largura** e
> devolve a altura inteira, medido em 04/08/2026 no emulador.
>
> E a objeção era boa: a primeira montagem confiou no padrão do
> `NavigationSuiteScaffold`, que mantém a barra embaixo em paisagem, e o
> resultado foi pior do que os 17% do cabeçalho fixo — **21%, com zero fileiras
> da grade visíveis**. Ver a §0.
>
> O que a opção 3 (chips que rolam junto) tinha e esta não tem: custo zero de
> tela permanente. O que ela não tinha: resposta pra tablet, e o destino
> continuar existindo depois que a pessoa rolou.

---

## 7. Como medir que o redesenho não quebrou nada

O projeto tem 29 testes e um app verificado tela a tela. O redesenho não pode
desfazer isso:

- **as sete fases continuam sendo percorridas a cada leva** — entrar, abrir a
  ficha, tocar, voltar, pegar a fita, devolver, baixar
- **os 29 testes continuam verdes** (eles são de lógica pura, e nenhum deles
  olha pixel — por isso sobrevivem ao redesenho)
- **screenshot antes e depois**, lado a lado, de cada tela tocada
- **contraste medido** em todo texto sobre arte, como já foi feito com o título
  sobre a cor da obra (que era **1,02:1** e virou 17,36:1)
- **quadro perdido** na rolagem da grade, que é a única tela com 8.316 itens

---

## 8. Ordem sugerida, e o que ela entrega

| leva | fases | o que se vê no fim |
|---|---|---|
| **1** ✅ | decisão da §6 + R1 + R2 | o app **parece** o Odeon, sem nada se mexer |
| **2** ✅ | R3 + R4 | a biblioteca vira acervo, com filtro e metadado |
| **3** ✅ | R5 + R7 | as coisas viram objetos, e as telas se ligam |
| **4** ◐ | R6 + R8 | o experimental — película, giroscópio, háptico |
| **5** ✅ | R9 | o app sai do app |

A leva 1 é a que mais muda a impressão por linha escrita. A 4 é a mais
divertida, e é a que mais precisa de screenshot pra não virar barulho.

---

## 9. O ao vivo no celular — 16/08/2026

Ele existia na TV e **não existia aqui**, e o dono deu por falta: «o ao vivo que
esqueci no android». O `ModeloAoVivo` já morava no `:core` desde o redesenho da
TV, com a folha dizendo por quê — «quando o celular quiser o ao vivo, acha tudo
pronto». Achou: **nenhuma linha de modelo foi escrita** pra esta tela. Só desenho.

### A barra: entrou o ao vivo, saiu o mural

São cinco lugares e havia cinco donos. A régua já estava neste repositório, medida
em 04/08/2026: a seis abas cada uma fica com 68,5dp e «biblioteca» ocupa 61dp a
12sp — **não cabe**. Entrar custava sair.

Saiu o mural, e não por valer menos: ele é o que **já aconteceu**. O ao vivo é o
único destino do app com hora marcada — perder o mural de hoje é lê-lo amanhã;
perder o filme das 21h é perdê-lo. O mural virou linha da gaveta do canto, que
está em toda aba, e ganhou um `‹ voltar` próprio: sem aba acesa e sem saída
visível, só o gesto do sistema o fecharia, que é saída sem sinal (§8b).

A barra ficou **igual à do iOS**: biblioteca · locadora · guia · ao vivo · para você.

### O que a tela do celular não copia da TV

Herói de topo, fileiras horizontais, `▲▼ zapeia` e a grade de 12h ficaram de fora.
A grade é a ausência que mais pesa e a mais deliberada: numa TV ela responde «o
que passa hoje à noite» com o controle na mão; num celular de 400dp, doze horas a
1,6dp por minuto dão 1152dp de rolagem lateral dentro de uma tela que já rola pra
baixo — dois eixos disputando o mesmo dedo. Sobra a pergunta que o celular faz de
verdade: **o que está passando agora**.

### Visto na tela, no emulador

**21 canais** em «no ar agora», cada um com ponto verde, nome, título, barra de
andamento, «faltam Xmin» e «a seguir». Fixado o Odeon Matinê, ele subiu pro topo
com a estrela cheia. Sintonizado o Odeon Corujão: «Sherlock Holmes» abriu em
**30:23**, batendo com os «faltam 98min» do cartão, e o log confirma
`state=PLAYING` avançando. Saindo do player, voltou **pro ao vivo** — não pra
ficha. O relógio anda sozinho: numa volta, Sherlock foi de 98 pra 97min e o Harry
Potter passou a «acabando».

### ⚠️ O defeito que eu escrevi tendo lido o comentário que o descrevia

Na primeira versão a moldura pedia `quadro.logo ?: quadro.arte`, e na tela os
quatro canais externos ficaram com o quadro **vazio** — nem imagem nem o número
que eu tinha posto de reserva.

A causa estava escrita a dois arquivos dali, no `TelaAoVivoDaTv`, que tentou o
logo e reverteu: `logo_url` vem dos canais de M3U como URL **externa absoluta**, e
a `urlDaArte` prefixa `$base/artwork/` cegamente — dá `…/artwork/https://…`, que é
404. Como o `logo` não era nulo, a reserva do número nem chegava a rodar.

Trocado por `quadro.arte`, a arte voltou em **todos** os canais, externos
inclusive — melhor que na TV, que ficou só com o nome. O logo externo continua
precisando de um caminho que não passe pelo `/artwork/`, e isso é conversa de
servidor.

### O que ficou de fora, dito como tal

- **A continuidade de canal** — quando o arquivo acaba, perguntar «e agora, o que
  está no ar?» — existe na TV (`AtividadeDaTv`) e **não** no celular. O
  `oQueEstaNoArAgora` está no `:core` pronto pra ela.
- **O ramo do canal sem obra** não é alcançável neste acervo: o ErsatzTV serve o
  próprio acervo com EPG casado, então todo canal tem obra atrás. Fica anotado
  como **não exercitado**, e não como «funciona».

---

## 10. Passada geral no celular — 16/08/2026

Percorridas as cinco abas, a ficha, o painel de filtros e o «para você» com
screenshot em cada uma. Sete achados; **três eram do cliente e estão consertados**,
quatro são do servidor e viraram os pedidos 5, 6 e 7.

### 10.1 A ficha mostrava chave de banco

Nove pílulas em «Sr. Ninguém», quatro começando com a palavra `country`, mais
`format filme`, `genre Drama` e `lang inglês`.

O desenho não estava errado — a folha da `PilulaDeEtiqueta` argumenta bem que o
qualificador apagado diz *de que tipo* é a etiqueta, e é o que a web faz. Errada
estava a **premissa**: aquela folha foi escrita quando o servidor mandava
`genero/Crime` e `pais/Estados Unidos`, e hoje ele manda `genre` e `country`.

Agora quem traduz é `Etiqueta.rotulo`, pela regra já escrita duas vezes neste
repositório (`nomeDoIdioma`, `Revista.rotuloDoEixo`): traduzir código em nome é
desenho, e mora no cliente. **Namespace desconhecido devolve `null` e a pílula
omite o qualificador** — perder a palavra é o pior caso aceitável; imprimir a
chave não é. Tem teste, como o do eixo tem.

Visto na tela, em «007 Contra a Chantagem Atômica»: `país Reino Unido` ·
`formato filme` · `gênero Ação` · `gênero Aventura` · `gênero Thriller` ·
`idioma inglês`.

### 10.2 A porta de entrada era um vão preto

Da abertura até a grade aparecer passam-se cerca de dez segundos — **4,3s só de
arranque do processo** (`am start -W`, build de depuração no emulador) e o resto
entre `retomar()` e a carga do acervo. Nesse tempo havia dois riscos dourados
girando em telas pretas, um por fase, e nada mais.

Duas correções, e são diferentes de propósito:

- **A biblioteca ganhou esqueleto** — cabeçalho de verdade (com os callbacks de
  verdade: a busca já funciona enquanto carrega) e doze molduras vazias. É a §15,
  que a locadora e a grade de capítulos já aplicavam.
- **O `Decidindo` ganhou o `Saguao`** — a marca da casa, respirando. Ali não cabe
  esqueleto: não se sabe se vem a biblioteca ou o login, e desenhar moldura de
  cartaz pra quem vai cair no login é prometer acervo a quem não entrou (§18). O
  que dá pra afirmar sem mentir é de quem é o app.

### ⚠️ 10.3 O defeito que eu mesmo criei, e o que ele ensina

O `Saguao` nasceu chamado `Chegada.kt`, em `dev.odeon.android.ui` — e o
`:cenario` **já tem** um `Chegada.kt` no mesmo pacote, com o modificador `chega`
das caixas caindo na prateleira. Dois arquivos de mesmo nome no mesmo pacote viram
**a mesma classe JVM**, e o APK saiu com duas `ChegadaKt`: uma no `classes7.dex`,
outra no `classes17.dex`.

O compilador não reclamou — são módulos diferentes, e cada um compilou o seu. Quem
reclamou foi o aparelho:

```
NoSuchMethodError: No static method Chegada(Landroidx/compose/runtime/Composer;I)V
  in class Ldev/odeon/android/ui/ChegadaKt;
```

E o sintoma **mentia**: `NoSuchMethodError` numa classe que existe é a assinatura
de dex velho, então gastei um `:app:clean` e uma instalação direta do APK atrás de
um problema de instalação que não existia. O `.class` compilado tinha a assinatura
certa o tempo todo — `javap` mostrou isso —, e foi o `dexdump` do APK que mostrou
as duas classes.

**A lição é de arquitetura, não de descuido:** quatro módulos compartilham o
pacote `dev.odeon.android.ui`, e nesse arranjo o nome do arquivo é global. Um
`grep` por nome de arquivo antes de criar um é barato; o build não protege.

### 10.4 O que a passada olhou e não mudou

- A locadora, o guia e o player não tinham defeito visível nesta passada.
- «ESCRITO POR LLAMA-3.3-70B-VERSATILE» no guia é o identificador do modelo
  aparecendo cru. É **honesto** — dizer que o ensaio é de máquina importa —, mas o
  slug é de desenvolvedor. Fica anotado, sem mexer: virar «escrito por uma
  máquina» perde qual máquina, e essa é uma decisão do dono, não minha.
- O herói de «continuar» passou a mostrar o filme que eu assisti **pelo canal**.
  É defensável (assistiu-se de fato), e é comportamento de servidor. Anotado, não
  mexido.

---

## 11. Segunda passada de usabilidade — 17/08/2026

A primeira passada olhou telas. Esta olhou o que screenshot não mostra: **rotação,
alvo de toque e o caminho de um gesto destrutivo**. Três achados, os três do
cliente, os três consertados e medidos.

### 11.1 O ao vivo piorava deitado

Deitado o telefone, cada linha de canal esticava os 2400px: o título ficava
sozinho com um vão até a estrela no canto, a barra de andamento virava um fio de
ponta a ponta, e **cabiam menos canais que em pé** — sete viraram dois e meio. A
tela ganhava espaço e piorava com ele.

Virou `GridCells.Adaptive(360.dp)`, que é o mesmo remédio da grade da biblioteca:
a largura vira **mais canais**, não canais mais largos. Medido depois: duas
colunas em paisagem, cinco canais à vista, e a estrela de volta ao lado da linha.

### ⚠️ 11.2 A confirmação de apagar era um gatilho armado

O `apagar` dos baixados já pedia segundo toque, e a folha defendia bem por quê —
«o empréstimo se refaz em dois segundos, 2 GB voltam por download inteiro».

O que faltava era o **esquecimento**. Uma vez armado, o `confirmando` só voltava a
`false` se o id do item mudasse — ou seja, nunca. Alguém toca em «apagar», muda de
ideia, sai da tela; meia hora depois volta, encosta no mesmo lugar, e 2,3 GB somem
— com a pergunta tendo sido feita meia hora antes, a alguém que já esqueceu que
respondeu.

Uma pergunta que não expira não é pergunta. Agora desarma em quatro segundos.

E o alvo era de **20dp de altura** — o controle mais destrutivo do app era o menor
dele, menos da metade do mínimo do Material. A régua da gaveta (38dp) não vale
aqui, e vale dizer por quê: lá a linha ocupa a largura inteira de um painel de
duas, e errar é acertar a outra. Este é um alvo isolado no canto de um cartão.
Errar aqui é acertar o nada; acertar de leve é armar a pergunta sem querer.

Medido depois: **50 × 46dp**, e o ciclo inteiro conferido no `uiautomator` —
toque → «apagar mesmo?» → seis segundos → «apagar», com os dois downloads
intactos.

### 11.3 A varredura de alvos, e o que ela absolveu

Escrito um medidor (`alvos.sh`) que lê o `uiautomator dump` e aponta todo alvo
clicável abaixo de 44dp. Rodado nas cinco abas e nos baixados:

| tela | alvos abaixo de 44dp |
|---|---|
| biblioteca · locadora · guia · para você · baixados | **0** |
| ao vivo | 1 — e era **meu** |

A estrela de fixar pedia `size(44.dp)`, que é **proposta**: a `Row` a apertava pra
41dp. Virou `requiredSize`, e ganhou `onClickLabel` — ela não tinha rótulo nenhum,
e «★» é glifo, não palavra. Depois: as oito estrelas visíveis medem 46×46dp.

⚠️ **O medidor tem um falso positivo conhecido:** o `uiautomator` reporta limites
**visíveis**, então a última linha cortada pela borda da tela aparece como alvo
baixo. Vale conferir a posição antes de acreditar — foi o que me fez perseguir uma
estrela que já estava certa.

### 11.4 Uma armadilha do ambiente, anotada pra não custar de novo

O `adb exec-out screencap` desta sessão passou a **devolver preto** com o app
desenhando normalmente — três diagnósticos falsos saíram daí, um deles com o app
inteiro dado como quebrado. Quem desempatou foi o `uiautomator dump`: 52 nós, a
tela toda presente.

Junto com a colisão de `ChegadaKt` do dia 16, são duas vezes que **o sintoma
apontou pro lugar errado**. A regra que sobra: quando a tela parecer vazia, dumpar
a hierarquia antes de mexer no código.

---

## 12. Quadro perdido, medido — 17/08/2026

A régua desta casa é «ver na TV antes de escrever que funciona… compilar e passar
nos testes não dizem nada sobre quadro perdido». Até aqui o quadro perdido nunca
tinha sido **medido**. Foi agora, com `dumpsys gfxinfo` e oito arrastos por tela.

⚠️ **Tudo abaixo é build de depuração em emulador**, que é o pior caso possível:
sem R8, Compose sem modo de release, GPU emulada. O número absoluto não vale nada;
o que vale é a **comparação entre telas do mesmo build**.

| tela | quadros perdidos | 90º percentil |
|---|---|---|
| para você | 10,4% | 53ms |
| guia | 19,3% | 48ms |
| **ao vivo** | **46,3%** | 109ms |
| **biblioteca** | **59,6%** | 101ms |

Duas telas três a seis vezes acima da linha de base. Uma tinha causa; a outra não.

### 12.1 O ao vivo recalculava tudo sessenta vezes por minuto

O `ModeloAoVivo` anda com o relógio de segundo em segundo — decisão da TV, e boa,
porque lá o relógio aparece na tela. Só que a tela do celular **recalculava a cada
batida**: `emCartaz` percorrendo canais e grade inteira, e os 21 quadros
reordenados. Uma vez por segundo, para sempre, com a tela aberta.

E o trabalho era **invisível**: nada nesta tela mostra segundos. O que muda de
verdade é «faltam 41min» e a barra de andamento, e os dois viram quando o minuto
vira. `agoraMs / 60_000` como chave do `remember` transforma sessenta contas
idênticas em uma.

⚠️ O relógio de segundo continua indo pro `aoSintonizar`: o ponto onde a
transmissão está decide onde o filme abre, e arredondar pro minuto abriria até 59s
fora. **O minuto serve pra desenhar; pra sintonizar, serve o instante.**

Medido depois: **46,3% → 23,8%**, e nas duas repetições seguintes **14,4% e
11,2%** — a linha de base do «para você». O primeiro número pós-conserto carregava
o custo da primeira composição.

### ⚠️ 12.2 A biblioteca: três hipóteses minhas, três desmentidas pela medição

Esta seção é o que **não** foi consertado, e por quê. As três hipóteses eram
plausíveis e as três morreram na régua:

| hipótese | como testei | resultado |
|---|---|---|
| o grão de película | li o `Grao.kt` | **desligado** por chave, e só no player |
| o elemento compartilhado da R7 | troquei por `Modifier` e remedi | 35,8% → 30,0% — **6 pontos**, e o 90º percentil **idêntico** |
| cartaz grande demais pra célula | li o cache do Coil | 500×750 e ~100 KB pra célula de 297px — **1,7×**, que é sadio |

Rolando **de volta sobre imagens já em cache**: 59,6% → 35,8%, com `Slow bitmap
uploads` em zero. Ou seja ~40% do custo é rede e decodificação — esperado, e
limitado, já que o tamanho do cartaz é sadio.

O que sobra (≈30% contra 10% da linha de base) está espalhado em layout e desenho
de uma grade densa, e **não tenho evidência de defeito específico**. A conclusão
honesta é essa, e ela vem com um encaminhamento em vez de um conserto inventado:
**medir de novo num build de release, no aparelho de verdade**, antes de otimizar.
Otimizar contra um número de emulador é ajustar o app ao instrumento.

⚠️ O elemento compartilhado **foi restaurado**. Ele custa 6 pontos num build de
depuração, e é a R7 — «o pôster tocado cresce e vira o pôster da ficha», que o dono
pediu. Seis pontos de emulador não derrubam uma decisão de produto; se derrubarem,
que seja com número de aparelho na mão.

---

## 13. O app sem rede — 17/08/2026

Cenário que nunca tinha sido testado, e que é **o cenário desta casa**: o servidor
mora numa tailnet, e a tailnet cai. Desliguei wifi e dados do emulador e abri as
cinco abas.

### ⚠️ 13.1 A locadora **matava o app**

Não era degradação, era morte: abrir a aba da locadora sem rede encerrava o
processo. As outras quatro seguiam de pé. Reproduzi três vezes, três crashes.

A causa é a pegadinha mais conhecida de corrotinas, e o código tinha a cara de
quem já a tratou:

```kotlin
try {
    val prateleira = async { odeon.prateleira() }   // <- lança sem rede
    val loja = async { odeon.estantes() }
    …prateleira.await()…
} catch (e: Exception) { /* nunca roda */ }
```

Em concorrência estruturada um `async` que falha **não guarda a exceção até o
`await`**: ele cancela o job pai na hora, e a exceção sobe pela hierarquia direto
pro tratador do escopo. O `catch` logo abaixo é decoração. Compila, passa no lint,
e só aparece quando a chamada falha.

⚠️ **E o rastro não aponta pro culpado.** O que chega é a pilha do OkHttp com o
`UnknownHostException` e **nenhuma linha** de `dev.odeon.android.ui` — porque a
exceção é retomada de corrotina. Achei cruzando duas listas: «qual aba cai»
(testando uma a uma) com «quem usa `async`» (duas telas no app inteiro). Fica
anotado porque o próximo a caçar isto vai ver o mesmo rastro mudo.

O conserto é pôr o `runCatching` **dentro** de cada `async`. O guia levou o mesmo
tratamento: lá nunca estourou, e só por sorte de camada — as rotas dele já são
tratadas no repositório. Proteção na outra ponta significa que o dia em que
`guia()` lançar, a tela mata o app sem mudar uma linha dela.

### 13.2 A vitrine vazia que não era vazia

Consertado o crash, a locadora sem rede passou a dizer **«nada com capa por
aqui»** — afirmando que o acervo não tem capa nenhuma quando o que houve foi não
ter perguntado a ninguém.

O mais interessante: a tela **já tinha a guarda certa**, escrita com todas as
letras — «só nasce com a vitrine na mão; erro de rede não é resposta vazia (§18)»
—, e a guarda é `estado.loja?.let`. Só que o repositório devolvia `Loja()` vazia em
caso de falha, **e um objeto vazio passa por uma guarda de nulo**.

Ou seja: a decisão de produto estava certa numa camada e anulada na outra, sem que
nenhuma das duas estivesse errada isoladamente. `estantes()` passou a devolver
`null` na falha, e aí as duas intenções valem juntas — a vitrine some (a tela não
afirma nada sobre ela) e os empréstimos continuam, que era o ponto de não estourar.

### 13.3 O inglês do OkHttp na cara do usuário

Com o erro finalmente visível, ele apareceu assim:

```
Unable to resolve host "odeon-api.serious-sam.dev": No address associated with hostname
```

Vocabulário de resolvedor de DNS e o endereço do servidor no meio da loja. A
`ModeloDaBiblioteca` já classificava direito — e era a **única** do app; o resto
mostrava `e.message`.

Aquela classificação virou `fraseDaFalha`, no `:core`, pelo mesmo argumento do
`nomeDoIdioma` e do `Etiqueta.rotulo`: traduzir código em frase é desenho, e
desenho que se repete se contradiz. A biblioteca passou a usar a peça que ela
mesma inspirou.

⚠️ Ela **não inventa causa**: o que não é HTTP nem entrada/saída cai na frase
genérica. «Verifique sua conexão» sobre um `NullPointerException` seria mandar a
pessoa consertar a casa dela por causa de um defeito nosso.

### Visto na tela

Sem rede, a locadora agora diz **«sem resposta do servidor»** e mais nada sobre o
acervo — conferido no `uiautomator`, sem «nada com capa» e sem menção a host. Com
a rede de volta, a loja abre normal: «mais 8 antes ›», COMIGO, NA MÃO DE ALGUÉM.
Contagem de crashes do dia: 3 antes do conserto, 3 depois.

---

## 14. Busca sem resultado, e um erro meu no caminho — 17/08/2026

### 14.1 A frase que existia e não aparecia

Buscar «zzzqqq» na biblioteca dava **«0 de 0» e um vão preto**. A tela tem a frase
certa escrita, com o argumento junto — «uma grade em branco depois de digitar
parece defeito: o campo tem texto, a tela não tem nada, e nada explica a ligação»
— e ela simplesmente não saía.

Medido com dois `Log` temporários:

| pergunta | resposta |
|---|---|
| a condição liga? | `vazioComFiltro=true`, `naTela=0`, `erro=null` |
| o `item` da grade é registrado? | sim, o bloco roda |
| o item **compõe**? | **nunca**, zero vezes |

Ou seja: a `LazyVerticalGrid` recebia o item e não o punha na tela, enquanto os
dois itens antes dele (cabeçalho e barra de filtros) compunham normalmente.

⚠️ **A causa dentro da grade ficou sem explicação**, e está anotada como tal em
vez de virar teoria bonita. O que se sabe é o que foi medido: registrado, não
composto.

O conserto foi mudar de lugar. Primeiro pra fora da grade — e aí a frase apareceu
**acima do título**, a resposta antes da pergunta. Depois pra dentro do item da
barra de filtros, que comprovadamente compõe, e é onde ela pertence: colada nos
chips, no ponto em que os resultados começariam.

Conferido nos três estados: acervo cheio (60 de 8.333, sem frase), busca com
resultado («bond» → 41 de 41, sem frase), busca vazia (a frase, e só ela).

### ⚠️ 14.2 O `catch (e: Exception)` que eu tinha acabado de escrever

Ao levar a classificação de erros pra `fraseDaFalha` (§13.3), troquei os dois
`catch` específicos da biblioteca por um `catch (e: Exception)`. Isso pega
**`CancellationException`** — e esta tela cancela o tempo todo: cada tecla
digitada na busca derruba a requisição anterior.

O efeito seria pior que um erro à toa: o cancelamento viraria `erro`, e
`erro != null` **desliga o `vazioComFiltro`** — ou seja, a frase da §14.1 pararia
de aparecer de novo, agora por minha causa, e o defeito voltaria fechando o
círculo.

Cancelamento não é falha: é a corrotina sendo desfeita de propósito, e engoli-lo
quebra a concorrência estruturada. Ele sobe, e o `catch` genérico vem depois.

### 14.3 O instrumento mentiu duas vezes, e as duas custaram caro

- **`screencap` devolvendo preto** com a interface viva: a essa altura pela
  segunda vez na sessão. O `uiautomator dump` desempata — mas nesta rodada o
  próprio app estava desenhando 0,3% de pixels não-pretos, e **reiniciar o
  emulador** foi o que devolveu a tela. Sem tela confiável não há régua, e a régua
  desta casa é a tela.
- **O `uiautomator` não vê `onClickLabel`.** Ele lista `content-desc`, e os
  controles do player usam rótulo de **ação**. A varredura acusou «oito controles
  sem rótulo» no player; a fonte mostrou que os oito têm. Quase «consertei» o que
  não estava quebrado — e o que salvou foi conferir na fonte antes de escrever
  código.

A regra que fica: **medida que acusa defeito vale uma segunda fonte antes de virar
conserto.** As duas vezes em que pulei essa etapa nesta sessão custaram um ciclo
de build cada.

---

## 15. As telas que faltavam olhar — 17/08/2026

Perfil, ponte do guia, palco e menu de DVD. **Três passaram sem defeito**; a
quarta rendeu uma linha de texto que mentia.

### 15.1 O que passou

- **Perfil**: XP com «faltam 183 pro nível 4», o placar dos três moradores, e as
  conquistas agrupadas por camada («FÁCEIS · 10 de 12»). Zero alvos abaixo de
  44dp.
- **Ponte guia → biblioteca**: tocar no eixo «2000» leva à grade já filtrada, com
  o chip `limpar ✕` que devolve os 8.333. Os dois sentidos funcionam.
- **Palco e menu de DVD**: a caixa vai pra mão, a abertura abre, o disco sai com a
  arte, e o menu traz `Tocar` · `Capítulos 10` · `‹ guardar o disco` — **sem
  «Continuar»** (nada assistido) e **sem «Legendas»** (não há), que é o §24
  funcionando. A grade de capítulos diz «nos cortes do disco», e este disco tem
  cortes de verdade.

### ⚠️ 15.2 A dica que prometia filme e entregava menu

Com a caixa aberta, o palco dizia **«toque no disco para assistir»**. O toque abre
o **menu** — `Tocar`, `Capítulos` — e o filme só começa um toque depois.

O detalhe bom é que esta mesma folha **já tinha corrigido um erro da mesma
família** duas linhas acima: «"toque no disco" com uma fita exposta era o §18 em
uma linha, e passou despercebido enquanto a caixa era igual pros dois». A frase
foi consertada pro objeto certo e continuou errada no **destino**.

E a assimetria é real, não descuido de quem escreveu: a fita vai direto pro filme,
o disco abre menu — «a fita não tem menu, tem rebobinar». Então a fita continua
dizendo «para assistir», e o disco passou a dizer **«toque no disco pra pôr no
aparelho»**: é o que a mão está fazendo, é verdade nos dois destinos, e é a mesma
frase que o iOS usa.

### 15.3 Uma medida que ficou pro pedido de contagem

O eixo «décadas» do guia diz **2000 · 256**. Tocar nele leva à biblioteca filtrada
pela mesma década, que diz **273**. Mesmo filtro, um toque de distância, dois
números.

Não é defeito de cliente — é o pedido «o guia conta rips, a biblioteca conta
grupos», e virou a **forma mais rápida de reproduzi-lo**, anotada no `AMANHA.md`.

---

## 17. Contraste medido nos pixels, e o beco da caixa de série — 17/08/2026

### 17.1 A régua da casa, aplicada com um medidor

O repositório mede contraste desde sempre («era **1,02:1** e virou 17,36:1»,
«`--accent` sobre `--bg` dá **9,94:1**»), mas nunca teve ferramenta. Agora tem uma:
lê os pixels da captura, separa texto de fundo e devolve a razão WCAG.

⚠️ **Ela se validou sozinha:** o rótulo `CONTINUAR` mediu **9,94:1** — o mesmo
número que a folha do `RotuloDeSecao` tinha anotado à mão. Método e histórico
concordam.

| onde | razão | veredito |
|---|---|---|
| título do herói sobre a arte | 14,18:1 | ok |
| «faltam 128min» sobre a arte | 9,25:1 | ok |
| rótulo da aba sobre o facho | 10,43:1 | ok |
| menu de DVD · título | 12,97:1 | ok |
| menu de DVD · item «Tocar» | 11,98:1 | ok |
| menu de DVD · «2026 · Comédia» | 5,73:1 | ok |
| **menu de DVD · «‹ guardar o disco»** | **3,67:1** | **reprova** |

Um em sete, e é justamente **a saída**: fora o gesto do sistema, aquele link é a
única porta de uma tela cheia. `textoApagado` rende ~7:1 sobre o preto da casa, e
este é o único texto do app desenhado sobre uma área lavada de arte — que ali
chegou a (71, 49, 39). O cinza que serve em toda tela não serve nesta. Passou a
`Cores.texto`, que resolve sem mexer na lavagem.

### ⚠️ 17.2 A caixa de série é um beco sem saída

Tocando no disco de **The White Lotus** (e antes no de **Dexter**): o menu do
disco dá 404, o app cai pra ficha como previsto — e a ficha **também** dá 404.
Sobra uma tela de erro com «tentar de novo» e «voltar», e o «tentar de novo» não
tem como funcionar nunca.

A causa é de contrato: o id de uma caixa de série **não é um id de obra**. Ele
funciona como *filtro de coleção* — é assim que a biblioteca abre séries
(`filtros.colecao = id`, e o `entrarNaSerie` guarda título e contagem pro
cabeçalho). Ou seja o destino existe; o que falta é a locadora mandar pra ele.

**Não consertei**, e o motivo é escopo honesto: ligar isso direito significa a
locadora entregar título e número de episódios pra a biblioteca montar o
cabeçalho da série e o chip «Dentro de». É mudança de navegação, não conserto de
uma linha, e meio-feita ficaria pior que descrita.

**O que foi feito**: a falha ficou legível. A ficha mostrava **`HTTP 404 `** — com
o espaço sobrando que o Retrofit deixa — e agora diz «o servidor respondeu 404».
É o mesmo defeito que esta classe já tinha corrigido no «pegar a fita», com o
argumento escrito ao lado («visível não é legível»), e que tinha ficado para trás
no carregamento da própria ficha.

⚠️ O `catch` da ficha também deixou de engolir `CancellationException`, pelo mesmo
motivo da §14.2.

---

## 18. A caixa de série achou seu destino — 17/08/2026

O beco da §17.2 está fechado, e o conserto não precisou de rota nova: **o destino
já existia**.

### O que estava errado

Tocar no disco de uma caixa de temporadas encadeava dois caminhos, os dois
errados: `/api/works/{id}/menu` dava 404, o app caía pra ficha como previsto, e
`/api/works/{id}` **também** dava 404 — sobrando uma tela de erro cujo «tentar de
novo» não podia funcionar nunca.

A causa, dita na forma mais curta: **o id de uma caixa de série não é um id de
obra**. Ele é de *coleção*, e funciona em `/api/works?colecao=…` — que é
exatamente o que o `entrarNaSerie` da biblioteca já usava para abrir séries.

### O conserto, e por que ele é pequeno

Três peças, todas espelhando coisa que já existia:

| peça | espelha |
|---|---|
| `ModeloDaBiblioteca.abrirSerieDeFora(id, titulo)` | o `entrarNaSerie`, sem precisar de um `ItemDaBiblioteca` |
| `seriePedida` no `AppOdeon` | o `filtroPedido`, que o guia já usava pra mandar filtro de uma tela pra outra |
| `if (naMao.serie)` no palco | o campo que **já** desenha o selo «3 TEMPORADAS» na lombada |

⚠️ A decisão é tomada **antes de perguntar**: a `CaixaExposta` já diz `serie`, e
gastar uma ida ao servidor pra descobrir um 404 que o objeto na mão anunciava é o
§53 ao contrário — não se pede o que se sabe que será negado.

⚠️ **Sem contagem, e tudo bem.** A caixa não traz quantos episódios a série tem, e
o `total` nulo cai numa regra que o `buscar` já tinha escrito: «se a série tinha
zero lá, o que se tem agora é o que chegou». O número aparece da primeira página
em vez de o app afirmar um que não recebeu.

### Visto na tela

- **The White Lotus**: abre com «21 de 21», o chip «dentro de The White Lotus ✕», e
  as três temporadas (6 · 7 · 8) com still e nome de cada episódio.
- O **✕** devolve os 8.333 do acervo.
- A caixa de **filme** («O Drama») continua abrindo o menu do disco — `Tocar` ·
  `Capítulos 10` · `‹ guardar o disco`. O ramo novo não passa por ela.

---

## 19. O ao vivo não conta — 17/08/2026

Pedido do dono, e a história que o motivou é a especificação inteira:

> «eu mesmo acabei dormindo no ao vivo e quando vi o app registrou que eu vi um
> monte de filme»

### A regra, em uma frase

**Biblioteca e locadora registram; o ao vivo não.** O que se escolhe conta pro
«continuar», pro histórico e pro «para você»; o que a grade empurrou na frente de
uma tela ligada, não.

⚠️ E o critério é **por onde se entrou, não pelo que se tocou**. O mesmo filme,
aberto pela biblioteca, conta normalmente — lá houve escolha. Um canal corre
sozinho: ninguém escolheu aqueles filmes e ninguém decidiu parar no minuto 47.

### O estrago passava de «continuar»

O mesmo registro alimenta a curadoria. Uma noite de sono no canal de terror não
suja só uma fileira — ela **ensina ao algoritmo um gosto que ninguém tem**, e o
«para você» passa a responder a uma pessoa que não existe.

### O conserto, e onde ele coube

Uma linha de guarda, no único ponto de cada cliente que escreve histórico:

| cliente | onde | como |
|---|---|---|
| Android (celular **e** TV) | `ModeloDoPlayer.marcarNoFilme` | `if (doAoVivo) return`, com o `canalId` que já viajava |
| iOS | `ModeloDoPlayer` | `registraProgresso`, consultada nos **dois** pontos que marcam |
| web | — | **já fazia certo**: o `PlayerAoVivo` nunca chamou `api.progress` |

⚠️ A web ser a única correta diz o que aconteceu: os nativos **copiaram o player
de filme para o canal** e levaram junto o que não devia ir. A folha do canal
existia dos dois lados (`canalId`, `doAoVivo`) — e servia só pra saber pra onde
voltar.

⚠️ No iOS a guarda virou **propriedade** (`registraProgresso`) em vez de dois `if`
iguais: o relógio de 20s e a saída marcam em lugares distantes, e dois guardas
iguais separados são a receita pra um deles ser esquecido quando aparecer o
terceiro.

⚠️ **A posição local continua sendo anotada** nos dois nativos. Ela é o que impede
o player de voltar ao começo se se refizer no meio; o que não sai é o registro
**no servidor**.

### Visto na tela, os dois sentidos

| | antes | depois |
|---|---|---|
| canal «Odeon Matinê» com «Os Jovens Titãs», ~2min tocando | entrava no «continuar» | **não entra** |
| «007 Contra Octopussy» aberto pela biblioteca, ~1min | entrava | **continua entrando** («faltam 129min») |

O primeiro caso é o que gerou o pedido: foi assim que «Sherlock Holmes» apareceu
no herói da biblioteca depois de eu sintonizar um canal para testar o ao vivo.

---

## 20. O «levar pra casa» voltou, e a continuidade de canal chegou ao celular — 17/08/2026

### 20.1 O botão que o §53 tinha proibido

Ele esteve fora **do produto inteiro** porque o servidor recusava com 403 de forma
imprevisível, e um botão que leva a uma recusa é pior que botão nenhum. A causa
saiu da investigação do servidor e **não era permissão**: o mesmo filme existe
duas vezes (44 casos). Desde a R47 a biblioteca desenha um cartão pro grupo, e a
locadora trancava por `work_id` — a prateleira dizia o id de um rip e o cartão
conhecia o outro.

Com `caixa_ids` em cada empréstimo, a conta virou local e certa:

| situação | na tela |
|---|---|
| livre | botão **«levar pra casa · 2 restam»** |
| já é sua | frase «esta já está com você» |
| com outro | frase «está com o serious-sam» |
| no limite | frase «você está no limite de fitas» |

⚠️ **Só a primeira é botão.** As outras três são respostas, não ações — e um botão
desabilitado convida ao toque que não responde (§8b).

⚠️ E o mesmo `caixa_ids` consertou um defeito **nosso** que ninguém tinha pedido: o
filtro da vitrine (`expostas`) casava só pelo `caixaId`, então uma caixa levada por
alguém continuava exposta com a outra cara. É a mesma raiz do 403, vista do lado
da prateleira — a escassez não escasseava aqui também.

**Visto na tela, o ciclo inteiro:** «levar pra casa · 2 restam» → «levar mesmo?» →
o empréstimo nasce → o palco passa a dizer **«esta já está com você»**, previsto
localmente, sem perguntar. A porta da loja foi de «39 na prateleira · 1 fora» para
«38 · 2 fora», e o COMIGO de 1 para 2.

### 20.2 A continuidade de canal no celular

O canal do celular **acabava numa tela parada**: o filme terminava e o app ficava
olhando o fim. A TV já tinha o caminho, e o `oQueEstaNoArAgora` esperava no `:core`
desde então.

Portado com a prudência de lá, e cada linha dela custou um ciclo de teste na sala:
volta pro ao vivo **antes** de procurar; desiste se a pessoa saiu; e **pula o mesmo
arquivo**, porque se a faixa da grade for maior que o filme, «o que está passando?»
devolve o que acabou de acabar.

⚠️ **Escrito e compilando, mas não visto disparar.** Exercitá-lo exige um filme
chegando ao fim num canal, e não encontrei jeito honesto de forçar isso no
emulador. Fica anotado como **não exercitado** — a régua desta casa não deixa
escrever a outra coisa.

### ⚠️ 20.3 Um empréstimo de verdade ficou no acervo

Verificar o «levar pra casa» significa criar um empréstimo no servidor que três
pessoas usam — não há ambiente de teste, e a folha do `ModeloDaLocadora` avisa
isso. Criei um: **«Independence Day», em nome do sam**, prazo de 7 dias.

Tentei devolvê-lo pelo app e **não consegui**: devolver é gesto de segurar («solte
pra devolver»), e o `adb` não reproduz — quatro tentativas, todas viraram toque
simples e abriram o palco. O gesto funciona com dedo; o que falha é o simulador de
entrada.

Fica registrado aqui em vez de escondido: **a devolução é um gesto de dois
segundos na aba locadora**, segurando a caixa no COMIGO.

### 21.1 A aba foi pro meio, e a programação chegou — 17/08/2026

Duas correções ao que eu tinha entregue.

**«coloque o icon do ao vivo no meio do menu inferior»** — eu tinha lido o pedido
anterior como «centralize o glifo» e centralizei o desenho dentro do quadro de
24dp. Era outra coisa: **a aba** devia ficar no centro da barra. Ela era a quarta
de cinco e virou a terceira.

E o lugar se defende com a régua que a barra já usa — a ordem é a de uso esperado.
Numa barra de cinco, o terceiro é o único que **os dois polegares alcançam** sem
reposicionar a mão. Some-se o conteúdo: das cinco, esta é a única com hora
marcada. Perder o mural de hoje é lê-lo amanhã; perder o filme das 21h é perdê-lo.

**«falta também a programação»** — faltava, e o argumento que a excluía era fraco.
A folha dizia: «doze horas a 1,6dp por minuto dão 1152dp de rolagem lateral dentro
de uma tela que já rola pra baixo — dois eixos disputando o mesmo dedo».

⚠️ **Dois eixos só disputam quando os dois são a mesma coisa.** Aqui a página rola
pra baixo (canais) e a grade rola pro lado (tempo): são perguntas diferentes, e o
dedo sabe qual está fazendo. A grade entrou com os mesmos 1,6dp por minuto da TV.

O que ela copia da sala:

- começa **na hora cheia anterior ao agora**, senão o programa em curso nasce
  cortado pela borda esquerda — e é justamente o que se quer ver inteiro
- a **agulha vermelha** do agora, na mesma cor do «NO AR» do herói: é o mesmo fato
  dito de outro jeito, e duas cores fariam a grade parecer falar de outra hora
- o que está no ar é o **único aceso** — numa grade de doze horas, sem isso não há
  como achar o agora sem contar horas na régua
- a programação dos externos vem do **guia**, não do canal: o `CanalNoAr` traz só
  o que está no ar, e uma grade com um bloco por linha é uma lista com espaço
  desperdiçado

⚠️ E os **nomes ficam parados à esquerda** enquanto só o tempo corre. É como toda
grade de TV funciona, e sem isso rolar três horas pra frente deixa a pessoa
olhando retângulos sem saber de quem são.

⚠️ O `remember` da grade não é economia: sem ele ela seria refeita a cada segundo
do relógio — 60 vezes por minuto para desenhar exatamente os mesmos retângulos.

**Visto na tela:** régua `19:00 · 20:00 · 21:00 · 22:00`, agulha logo depois das
19:00, «Sweeney Todd» aceso no Odeon 1 seguido de «Pulse», e as faixas de todos os
canais com nome parado à esquerda.

---

## 22. Dois players no ao vivo, e um que nunca sumia — 17/08/2026

> «de vez em quando aparece um player diferente mostrando ao vivo e outro com o
> nosso player usual… eu quero que no ao vivo somente apareça esse player ao vivo
> sem mostrar timeline pausar etc… E sumir dps de um tempo pq hj esse player do
> ao vivo nao some»

### A causa: eram mesmo dois, e quem escolhia era o acaso

| player | servia | tinha |
|---|---|---|
| `TelaDoCanalAoVivoDaTv` | canal **sem obra** atrás | tarja com o nome, e nada mais |
| o player normal | canal **com** obra | linha do tempo, tira, pausa, ±10s/30s |

Quem decide qual dos dois é o **programa que está no ar naquele minuto**: se ele
casou com uma obra do acervo, abre o player de filme; se não casou, abre o de
canal. O mesmo canal, em horas diferentes, abria caras diferentes — e daí o «de
vez em quando».

### O conserto: manda **de onde se entrou**, não o que há atrás

O `EstadoDoPlayer.aoVivo` já podia ser derivado — o `canalId` viajava desde o
conserto de «sair do canal cai na ficha errada», e o `doAoVivo` desde o de «não
registrar o que passou na tela». Agora ele também decide o cromo.

⚠️ **Some o transporte, não o filme.** Pausar, saltar e arrastar são gestos sobre
um tempo que **não é seu**: a grade segue correndo, pausar não pausa a
transmissão, e voltar 10s só afasta você do que está no ar. A tira de miniaturas
sai pelo mesmo motivo — ela é um mapa pra escolher onde entrar, e numa transmissão
não se escolhe.

⚠️ **Legenda e áudio ficam.** Essas são sobre **como** se vê, não sobre quando.

⚠️ E a lâmpada do plano dá lugar ao **ponto vermelho + «NO AR» + o canal**: «direto
ou transcodificando» é uma conta sobre o arquivo, e quem vê uma transmissão não
escolheu arquivo nenhum. O nome do canal viaja junto porque o título é o do
**programa** — sem ele, «quem não vê o nome não sabe onde caiu».

### A tarja que não sumia

A folha dela defendia isso por escrito, e o argumento vale **pra quem acabou de
chegar**: sem a tarja, quem cai num canal sem guia não sabe onde está. Só que o
resto do tempo ela deixa de informar e passa a tapar — e a faixa preta de baixo de
um 16:9 não é garantida: em 4:3 ou 2.39:1 a tarja cai sobre a imagem.

Seis segundos atendem os dois, e ela **volta a qualquer toque** — que é o que
separa «sumiu» de «foi embora».

### Visto na tela (celular)

Sintonizando o Odeon Matinê: o cromo é `‹ · ● NO AR · Odeon Matinê · Como
Treina… · girar · janelinha`, e **nada embaixo** — sem linha do tempo, sem tira,
sem transporte, sem «faltam». Ele some sozinho em 3s, como o do filme.

### Visto na TCL — e a guarda estava pela metade · 17/08/2026

O dono ligou a TV e pareou por Wi-Fi; o que apareceu na tela derrubou a nota
anterior («compila, mas não foi exercitado»). Sintonizando o `Odeon 1`, o cromo
era: **tira de quadros, `1:21:21`, `faltam 7:05`, `SAIR`** e o nome do programa. A
guarda `if (!estado.aoVivo)` existia — e cobria **só os três botões de transporte**.

A metade que faltava é a que mais parece um player de filme. A `PeliculaDaSala`
desenha a tira **e** os dois relógios num bloco só, então ela inteira sai no canal.
E «faltam 7:05» era o pior dos três: é o resto do **arquivo**, não do que está no
ar — um número que promete um fim que um canal não tem.

⚠️ **O `SAIR` colidiu com o «NO AR».** Tirada a película, a coluna do cromo ficou
com uma linha só e desceu até o rodapé, em cima do `SAIR` — que mora ancorado no
`BottomStart` da tela, e não dentro da coluna. Foi preciso subir a coluna 60dp no
canal (44 do botão + respiro). É o tipo de defeito que **só a tela mostra**: nada
no código dizia que dois filhos de `Box` iam parar no mesmo canto.

O nome do canal precisou atravessar três arquivos até o player (`Onde.Filme` →
`TelaInicialDaTv` → `TelaAoVivoDaTv`), porque só o `canalId` viajava — e id não é
coisa que se lê na sala.

**Como ficou, na tela:** `● NO AR · Odeon 1 · Uma Noite no Museu 3: O Segredo da
Tumba`, o `SAIR` abaixo, e nada mais. Some sozinho em 4s e volta a qualquer tecla.

### E o ao vivo não registrou nada

Dois programas assistidos no canal, minutos cada. Voltando à biblioteca, o
`CONTINUAR` seguia com **os mesmos 3** — 007, Idiocracia, Fique Rico. Nem `Pulse`
nem `Uma Noite no Museu 3` entraram. É a regra do dono («eu dormi no ao vivo e o
app registrou um monte de filme») verificada no aparelho onde ela mais importa: a
TV é justamente onde se dorme.

### O teclado da busca prendia o foco, e a causa era a âncora que não existia

Seta pra esquerda na primeira coluna (`a`/`g`/`m`) não saía pro trilho: o foco
simplesmente não se movia, e a única porta era o `voltar` do controle — que numa
TV não é onde a mão procura o menu.

A intenção estava escrita e correta: as teclas da coluna 0 já traziam
`focusProperties { left = saidaEsquerda }`, e o `saidaEsquerda` era o
`focoDoTrilho`. O que faltava era **o outro lado do fio**. O `Trilho` amarrava
esse `FocusRequester` dentro do laço dos `ItemDoTrilho`, no item do destino
atual — e a busca é **filtrada desse laço**, porque ela não é um item: é o
`BotaoDaBusca`, desenhado acima da divisória.

Ou seja: estando na busca, nenhum nó do trilho segurava o requester, e o ◀
apontava pro vazio.

⚠️ E não é um canto raro. **A busca é justamente a tela onde se fica mais tempo
preso**, porque soletrar com D-pad leva dezenas de apertos — é a tela em que
querer voltar pro menu é mais provável, e era a única em que não dava.

Agora o `BotaoDaBusca` recebe a âncora quando o destino atual é a busca. Visto na
TCL: ◀ na coluna 0 abre o trilho com `BUSCAR` em foco, ▶ devolve o foco ao
teclado, e ◀ ▼ ▼ `OK` leva ao ao vivo.

### ⚠️ O «trilho pede dois OK» não existia — era o instrumento

Ficou anotado aqui, e está errado. Repetido com o app recém-aberto e **um** `OK`,
o trilho comuta de primeira.

O que produziu o falso positivo: naquela volta eu havia rodado um
`uiautomator dump` entre as setas e o `OK`, pra ler onde estava o foco. O dump
liga um serviço de acessibilidade, e ele mexe no foco que eu estava medindo — o
`OK` seguinte caiu no vazio.

É a **quarta** vez nesta série que o instrumento mente (antes: o `screencap`
preto, o `onClickLabel` invisível ao `uiautomator`, e a ordem do «continuar» no
iOS). A regra que sobra: quando a medição contradisser a tela, refazer **sem** o
medidor no meio antes de escrever que há defeito.

### O ao vivo do celular, dois relatos do dono · 18/08/2026

> «ao selecionar um canal tu tem que clicar no programa e aí clicar no banner no
> topo, o ideal é tu clicar e já ir para o programa»

O toque no cartão só **elegia** o herói; sintonizar era um segundo toque lá em
cima. Numa fileira que existe pra zapear, escolher e assistir eram a mesma
intenção partida em dois gestos. Agora um toque sintoniza — e o herói continua
acompanhando, então ao voltar do player o canal certo está no topo.

> «tem alguns programas que simplesmente não iniciam, seria legal ao clicar,
> caso precise carregar algo, tu colocar um loading»

⚠️ **A causa que eu achei era silêncio, não lentidão.** O toque caía num
`if (obra != null && arquivo != null)` que, sendo falso, **não fazia nada** — e o
comentário ao lado dele dizia que esse ramo «não é alcançado neste acervo». O
dono acabou de provar que é. Um programa sem arquivo casado não vai tocar, mas
isso é uma coisa a **dizer** (§8b), e agora a tela escreve
`«O Diabo Veste Prada» não tem arquivo no acervo` logo acima da fileira.

⚠️ E o rodinho entrou onde faltava, que **não** era o player: ele já mostra a
cortina com o nome do programa enquanto a sessão abre. O que não existia era
feedback entre o **dedo e a cortina** — o cartão tocado agora escurece e gira até
a tela trocar.

⚠️ **O caso «não tem arquivo» não foi exercitado.** Nenhum dos 21 canais deste
acervo caiu nele hoje; o que eu vi na tela foi o toque único funcionando. Fica
como escrito e não visto — que é a mesma situação em que o comentário anterior
estava quando afirmou que o ramo era inalcançável.

### A tira não voltava ao zero · 18/08/2026

> «tô tentando o piloto de Abbott e não consigo arrastar a linha do tempo pro zero»

Havia **dois caminhos de busca** no player do celular, e só um sabia de HLS.

| | |
|---|---|
| `saltarPara` (setas, capítulos) | alvo antes do `deslocamentoMs` → **reabre a sessão** ali |
| o arrasto da tira | `seekTo` direto |

Uma sessão de transcodificação **começa onde foi pedida** e não tem nada atrás
disso. O `tempoDeSessao` tem `coerceAtLeast(0)`, que **cala**: pedir o minuto 0
de um filme cuja sessão começou aos 12 vira «segundo 0 da sessão» — o minuto 12.
O dedo ia até a esquerda e o vídeo não se mexia.

⚠️ Só morde com `deslocamentoMs > 0`: **continuar** algo por HLS. Quem abre do
começo nunca viu, e é por isso que sobreviveu — até alguém arrastar pra trás num
episódio retomado.

⚠️ A lição não é o `coerceAtLeast`: é que **duas portas para a mesma ação
divergem**. O `saltarPara` já sabia tudo; o arrasto não passava por ele. Agora
passa.

---

## 23. A tela preta do ao vivo, com o áudio tocando por trás — 19/08/2026

> «de fato alguns canais abrem mega rápido e outros ficam com tela preta pra
> sempre; esses estão com áudio rodando de fundo normal, e quando tu clica em
> voltar a imagem do filme mostra por 1 segundo»

⚠️ **As duas metades do relato eram o diagnóstico inteiro**, e nenhuma delas cabia
na hipótese que eu tinha (sessão fria no servidor): áudio tocando prova que o
canal está entregando, e um quadro que pisca ao sair prova que havia imagem
decodificada o tempo todo. Não era rede. Era superfície.

### O que a varredura mediu, antes de mexer

Um toque em cada um dos 21 canais, com o veredito lido do próprio framebuffer:

| | resultado |
|---|---|
| canais do Odeon (player de filme) | imagem, 2 de 2 |
| canais de fora (`TelaDoCanal`) | **preto em 17 de 17** |

Nos 17, o log dizia a mesma coisa: h264 decodificando quadro 1920×1080, áudio
saindo, `PlayerView` criado, player anexado — e o `dumpsys SurfaceFlinger` **sem
nenhuma camada de `SurfaceView`**. O codec estava ligado num consumidor `unnamed`:
a superfície-fantasma que o ExoPlayer usa quando não há tela para desenhar.

### A causa: a ordem, e ela dependia de uma corrida

Instrumentando o próprio `PlayerView` três segundos depois de anexado:

| | superfície de vídeo | tamanho dela | tela |
|---|---|---|---|
| `url` chegou **antes** de a cortina terminar | válida | 1080×**607** — mediu o 16:9 | imagem |
| cortina terminou **antes** de a `url` chegar | **inválida** | 1080×2400 — nunca mediu nada | preta pra sempre |

O `return@Box` da cortina pulava **a própria superfície**. Quando o `PlayerView`
era inserido numa tela já desenhada sem ele, o `SurfaceView` nascia sem superfície
e não ganhava uma depois — e como o player já estava tocando, ele seguia
decodificando pro fantasma.

⚠️ **O `TelaDoPlayer` nunca teve o defeito, e o motivo estava escrito lá**: a
`Superficie(player)` é a **primeira** coisa do `Box`, composta sempre, e o
`return@Box` da cortina só pula o **cromo**. O conserto foi dar essa forma ao
`TelaDoCanal` — superfície primeiro, cortina por cima, player entrando nela quando
chegar.

### Verificado

Duas varreduras completas depois do conserto, cada uma tocando os 21 canais:
**19/21 e 20/21 com imagem**, e `SurfaceView` de verdade em **21 de 21** — o
fantasma sumiu. Os três casos que a primeira leitura chamou de preto (Sessão
Drama, Corujão do Terror, Disney Channel) foram medidos de novo comparando dois
quadros com segundos de diferença: **vídeo andando nos três**, entre 5.716 e 9.206
amostras mudando. Eram cena escura, não tela morta — o limiar do medidor é que era
cego.

⚠️ **A mesma forma errada estava na TV**, no `TelaDoCanalAoVivoDaTv`: a superfície
também só entrava dentro de `if (player != null)`. Lá não há cortina, mas a corrida
é a mesma. **Consertado do mesmo jeito, a pedido do dono, e não exercitado** — a
TCL está desligada. Compila; é tudo que se pode dizer dele. Quando a TV voltar, o
teste é entrar num canal de fora: som sem imagem quer dizer que não pegou.

E um achado de arrasto: o `:app:lintDebug` já estava **vermelho antes de tudo
isso** — o `TelaDoCanal` nasceu ontem sem o `@OptIn(UnstableApi)` que a tela irmã
da TV carrega desde sempre, e os quatro erros eram do bloco do player, não do
conserto. Anotado e fechado junto.

### O que ficou de fora, dito como tal

- **Quando o canal não abre, a tela do celular continua muda.** A TV tem
  `onPlayerError` e um recado com «tentar de novo»; o celular não tem nenhum dos
  dois, e um erro de transmissão vira preto sem explicação. Anotado, não feito.
- O tempo até a primeira imagem **não** foi medido de forma confiável: as
  primeiras tentativas mediram por brilho de tela (cego em cena escura) e por
  marcador de log (que pegava o player anterior). O que está verificado é que
  abre, não em quanto tempo.

### O recado quando não dá — 19/08/2026

O item que a seção acima tinha deixado escrito como «não feito» foi feito no
mesmo dia, a pedido do dono. Antes disto o celular tinha **meia cobertura**: um
canal que não abria virava uma linha vermelha no meio do preto, sem saída; e uma
transmissão que **caía depois de começar** não passava por lugar nenhum — o player
parava e a tela seguia preta com «NO AR» escrito em cima, afirmando o contrário
do que estava acontecendo (§18).

Agora são dois recados, com título diferente porque são coisas diferentes:

| | quando | o que oferece |
|---|---|---|
| «o canal não abriu» | o `sintonizar` não devolveu playlist | tentar de novo · voltar |
| «a transmissão parou» | `onPlayerError` depois de já estar no ar | tentar de novo · voltar |

⚠️ **«Tentar de novo» aqui re-sintoniza, e não é `prepare()`.** O que costuma ter
morrido é a sessão do servidor; preparar de novo um player apontado pra uma
playlist que não existe mais falha na hora, de novo. Pedir o canal outra vez cobre
os dois casos — sessão caída e rede caída. A TV faz `prepare()`, e essa diferença
está anotada nos dois lados.

⚠️ O `Recado` **mudou de casa** nesta passada: nasceu dentro do `TelaDaSerie` e
agora mora em `ui/Recado.kt`, porque três pacotes o desenham. A alternativa era o
`aovivo` importar de `serie`, sugerindo um parentesco que não existe.

#### Exercitado, e como

Cortando a rede do emulador (`cmd connectivity airplane-mode`), que é o jeito de
produzir os dois erros sem depender do servidor cair:

| | o que a tela mostrou |
|---|---|
| rede cortada **antes** do toque | «o canal não abriu · o servidor não entregou a transmissão deste canal.» |
| rede de volta, tocando «tentar de novo» | o canal sintonizou e o filme apareceu — *De Volta para o Futuro III*, no ar na Tela Quente |
| rede cortada **com a transmissão no ar** | «a transmissão parou · o canal saiu do ar, a fonte parou de responder, ou a rede caiu.» |

#### O que continua de fora, dito como tal

- **A TV não ganhou nada disto.** Ela já tinha os dois recados desde 17/08; o que
  ela não tem é a retentativa no «não deu pra sintonizar», e o «tentar de novo»
  dela é `prepare()`. Fica anotado — a TCL segue desligada.
