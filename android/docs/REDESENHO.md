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

## 0. O que já foi decidido e feito — atualizado em 04/08/2026

**As duas decisões que a §6 e a R1 pediam foram tomadas pelo dono:**

| | decidido |
|---|---|
| onde ficam os destinos (§6) | **barra inferior adaptativa** — e não a opção 3, que era a preferência escrita abaixo |
| a fonte serifada (R1) | **embutir no APK** |

E as **levas 1, 2 e 3 estão feitas e vistas em aparelho**, mais a **R6 inteira**
e **um quarto da R8**: a decisão da §6, e as fases R1, R2, R3, R4, R5, R6 e R7.
O resto da R8 e a R9 continuam sendo proposta, e a §6 abaixo fica como registro
do que se pesou.

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
> ### O que da R8 ainda não entrou
>
> Paralaxe por giroscópio, borda a borda na ficha, e o gesto de arrastar a caixa
> pra baixo pra devolver. Só o detente foi feito.

---

### R9 — Fora do app

- **Widget** de "continuar assistindo" — a mesma rota `/api/continue`, sem tela.
  A §4b da espec já lista isso como coisa que o servidor dá de graça.
- **Atalhos** ao segurar o ícone: continuar, locadora, baixados.
- **A arte no controle de mídia**: hoje a notificação da sessão sobe sem capa.
  O `MediaSession` aceita `artworkUri`, e o app já tem a URL.

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
| **5** | R9 | o app sai do app |

A leva 1 é a que mais muda a impressão por linha escrita. A 4 é a mais
divertida, e é a que mais precisa de screenshot pra não virar barulho.
