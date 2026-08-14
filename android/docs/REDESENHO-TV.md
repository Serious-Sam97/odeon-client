# Redesenho do `:tv` — o Odeon na sala

> **Proposta, não plano aprovado.** Mesma régua do `docs/REDESENHO.md`: as levas
> da §9 são aprovadas uma a uma pelo dono antes de virarem código.

Escrito em **12/08/2026**, depois de o `:tv` rodar numa TCL Smart TV Pro e de o
dono mostrar cinco telas do celular — biblioteca, locadora, guia, player e
perfil — dizendo o que cada uma **é**.

---

## 0. O que este documento é, e o que ele não é

O `:tv` de hoje funciona: entra, lista 8.316 obras, abre a ficha, toca o filme.
E é **plano**. Ele tem a paleta da casa e não tem a casa: nenhuma luz, nenhum
papel, nenhuma película, nenhuma cortina. É o Odeon com as cores certas e sem a
experiência — que, num produto cuja tese é «não é um catálogo de arquivos, é uma
biblioteca que te conhece», é perder o argumento inteiro.

⚠️ **Este documento não é «copie o layout do celular».** O dono foi explícito:

> «Remember you should get the ideias and the experience of what i show to you,
> not create a 1-1 because android version is small, so use your creativity.»

⚠️ E igualmente explícito no sentido contrário, sobre as **peças**: «you can
literally copy the models from android version». As duas frases não brigam — a
**encenação** se traduz, o **objeto** se copia. Ver a §0.1, que lista quais.

Um celular tem 411dp de largura e um dedo. Uma TV tem 960dp e cinco teclas. As
mesmas ideias precisam de **outra encenação** — e em três casos a encenação de
TV é melhor que a do celular, porque a sala é literalmente o lugar que a
metáfora descreve.

---

## 0.1 ⚠️ ANTES DE ESCREVER QUALQUER LINHA: rode o app de celular

Este documento descreve com palavras coisas que **já existem e já funcionam** no
`:app`. Palavra nenhuma substitui ver a caixa girando na mão e a cortina
abrindo. Quem for implementar isto **roda o celular primeiro**:

```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
emulator -avd Medium_Phone &          # ou um celular por USB
./gradlew :app:installDebug
```

E olha, nesta ordem:

1. **a locadora** — escolha uma caixa, gire com o dedo, abra
2. **um filme** — veja a cortina abrir, e arraste a película da timeline
3. **o menu de baixo** — troque de aba e veja a lâmpada firmar o arco

### O que se **copia**, e não se reinventa

⚠️ A frase da §1 («este documento não é copie o celular») fala da **encenação**
— do layout, do tamanho, de como se aponta. Ela **não** vale pro código destas
peças. Estas se copiam, e o pedido do dono foi literal:

> «3d models for each (You can literally copy the models from android version)»
> «when we click on a movie the 3d vhs/dvd appear on the center (…) again grab
> this from the done android version»
> «i'm using a film roller with the actual scenes on it (already doing on
> android so check and replicate with a bigger version for tv obviously)»

| peça | arquivo no `:app` | o que ela faz |
|---|---|---|
| **a caixa 3D** | `ui/locadora/CaixaEm3D.kt` (631 l.) | o modelo em três quartos, com lombada, espessura e verniz |
| **o palco** | `ui/locadora/Palco.kt` (558 l.) | a caixa que voa pro centro e se deixa girar |
| **as tintas da lombada** | `ui/locadora/TintasDaCapa.kt` (115 l.) | as duas cores tiradas da capa pela `Palette` |
| **a contracapa** | `ui/locadora/Contracapa.kt` (437 l.) | o verso, quando a caixa abre |
| **a película** | `ui/player/Tira.kt` (596 l.) | a timeline como rolo de filme, com as cenas de verdade |
| **a cortina** | `ui/player/Cortina.kt` (452 l.) | as lâmpadas, o pano vermelho, e a abertura |
| **o facho** | `ui/Facho.kt` (475 l.) | a lâmpada de arco e o feixe com poeira. ⚠️ só **metade** dele desce — ver §3.2 |
| **a projeção** | `ui/locadora/Projecao.kt` (452 l.) | o grão e a luz de projetor |

⚠️ Estas oito não fecham sozinhas: `escalaDeAnimacao` e `MarcaDoNome` vêm junto,
e a §3.4 diz de onde.

**Nenhuma delas é pra reescrever.** Elas já foram desenhadas, medidas e
corrigidas em aparelho — várias carregam nos comentários um defeito que já
custou caro (a duração mínima da cortina, o tamanho da tira deitada, a zona
segura da caixa). Reescrever é jogar isso fora e pagar de novo.

O que muda no `:tv` é **como se aponta pra elas** (D-pad em vez de dedo) e **de
que tamanho** (dez pés em vez de trinta centímetros). Ver §3 pro custo medido de
movê-las e §9/T0 pra onde elas vão.

---

## 1. A régua: o que se copia, o que se traduz, o que se inventa

| | |
|---|---|
| **copia-se** | a tinta, a voz, as regras de omissão, o código que não desenha — **e os objetos: a caixa 3D, a película, a cortina, o facho** (§0.1) |
| **traduz-se** | o layout, o tamanho, e tudo que dependia do dedo, do giroscópio ou de 411dp |
| **inventa-se** | só onde a TV tem algo que o celular não tem — e há três lugares |

⚠️ E a régua que este projeto já tem, e que vale mais que todas: **medir, não
lembrar**. Este `:tv` acumulou, em um dia, **oito** defeitos que só o aparelho
encontrou, e **quatro comentários que afirmavam comportamento falso**. Está no
`README.md`. Quem for implementar isto vê a tela antes de escrever que ela
funciona.

---

## 2. O vocabulário da casa

Extraído das cinco telas. É isto que faz o Odeon parecer o Odeon.

### 2.1 A luz é **um objeto só**, e aparece em três lugares

É o achado mais importante deste levantamento, e não estava em nenhuma tela
isolada — estava no comentário do `Tira.kt`:

> «**a lente** — o cabeçote do projetor no ponto atual, com o halo do `Facho`.
> (…) é a mesma lente que vive na barra de navegação, aqui fazendo a única coisa
> que uma lente de projetor faz.»

Ou seja, o app tem **um** sistema de luz:

| onde | o que ele é |
|---|---|
| barra de destinos | o facho, e a lente que o projeta |
| herói da biblioteca | as lâmpadas da marquise |
| timeline do player | a mesma lente, correndo sobre a película |

Não são três efeitos parecidos. É um projetor, visto de três ângulos.

**A piscada** é a lâmpada de arco firmando, e os números estão no `ui/Facho.kt`:

```
0.08 at 0 · 1.35 at 90 · 0.22 at 200 · 1.15 at 320 · 0.40 at 450
1.08 at 600 · 0.68 at 780 · 1.03 at 950 · 0.90 at 1080 · 1.0 at 1200
```

> «O primeiro pico passa de 1 de propósito — é o estouro do arco, e é ele que faz
> parecer que a luz **nasceu** em vez de aparecer.»

⚠️ Copiar esses dez números **literalmente**. Eles são o som da casa.

### 2.2 O papel

A locadora tem coisas de papel: as plaquinhas penduradas por pino e fio, giradas
uns graus para lados opostos; as etiquetas de estante escritas à mão em papel
colorido, presas com fita — amarelo pra *Terror*, azul pra *Documentário*.

As tintas já existem no `:core`: `papel`, `tintaDoPapel`, `tintaDoBilhete`. O
comentário lá avisa que **não são «o tema claro»** — são a cor de um objeto.

### 2.3 A película

A folha de sprites que o servidor já gera vira a barra do tempo. Três coisas
fazem parecer filme e não fileira de fotos: as **perfurações** (duas fileiras,
passo fixo), **o já visto em cor cheia contra o que vem a 34%**, e **a lente**.

> «Você não arrasta até um tempo — arrasta até uma **imagem**.»

### 2.4 A cortina

Vermelha, com o nome do filme, abrindo. E vem com uma regra que é metade do
desenho:

> «coisa de segundos, não podemos ser tão lerdos pra abrir o filme em si»

Ela **veste uma espera que já existe** — plano, URL, buffer até o primeiro
quadro — que hoje mostra tela preta. ⚠️ E tem um **piso**, descoberto no
aparelho: a primeira versão cortava a coreografia no `READY`, e em Direct Play
local isso é tão rápido que o dono disse **duas vezes** que as luzes não
existiam.

### 2.5 A serifada tem **três** papéis, não dois

| | |
|---|---|
| título de tela | `biblioteca`, `guia`, `sam` |
| letreiro | o herói, o tema da capa da revista |
| **texto editorial corrido** | o ensaio da revista |

O terceiro eu tinha perdido. Da fonte: «serifa em texto corrido é o que separa
**matéria** de …». É o que faz o ensaio parecer revista e não interface.

⚠️ O que **não** é serifado: título dentro de cartaz, rótulos, botões, tudo que
é interface.

### 2.6 A voz

| frase | onde | ⚠️ |
|---|---|---|
| **faltam X** | herói, cartões de continuar, player, XP | é sempre «faltam», nunca «continuar de» |
| **até segunda** | guia | prazo pra participar |
| **vira segunda** | locadora | a vitrine gira |

O `:tv` de hoje erra as três: escreve `continuar de 2min` e `vira segunda` no
guia.

### 2.7 As contagens

`60 de 8.316` — **o carregado é dourado, o total é apagado**. E o rótulo de seção
carrega a contagem **na ponta direita da régua** (`CONTINUAR ──── 15`).

Nem toda tela tem número: a biblioteca tem, o guia não.

### 2.8 Estado é **preenchimento**, não cor

Pílula sem estado: contorno `linha`, texto apagado. Pílula com estado: **sólida
dourada**, texto escuro. Vale pro filtro, pro selo, pro que for.

---

## 3. O que atravessa de graça — medido

⚠️ Eu tinha escrito no `TelaDaLocadoraDaTv.kt` que a caixa 3D «existe pra ser
pegada» e não atravessa. **Estava errado**: o argumento era sobre afordância de
toque e escorregou pra uma conclusão sobre código. O dedo não atravessa; o
objeto atravessa inteiro.

Contagem de imports de `androidx.compose.material3` **de celular** (o que
impediria de compilar no `:tv`):

| arquivo | linhas | material3 |
|---|---|---|
| `locadora/CaixaEm3D.kt` | 631 | **0** |
| `player/Tira.kt` | 596 | **0** |
| `locadora/Palco.kt` | 558 | 3 |
| `ui/Facho.kt` | 475 | 2 |
| `player/Cortina.kt` | 452 | 2 |
| `locadora/Projecao.kt` | 452 | **0** |
| `locadora/Contracapa.kt` | 437 | 1 |
| `locadora/TintasDaCapa.kt` | 115 | **0** |
| | **3.716** | **1.794 com zero** |

E o `CaixaEm3D` já prevê o caso: a assinatura recebe `pose` de fora, «quando
alguém quer controlá-la».

### ⚠️ A tabela acima foi refeita em 12/08/2026, e a anterior media outro conjunto

A primeira versão desta tabela dizia **≈2.155 linhas com zero acoplamento**, e a
conta estava certa — `631+596+452+254+115+107 = 2155`, confere. O problema é que
ela não somava as peças da **§0.1**: incluía `player/Botoes.kt` (254) e
`player/Cast.kt` (107), 361 linhas que a T0 **não move**, e omitia
`locadora/Contracapa.kt` (437 l., 1 material3), que a T0 **move**.

É o defeito clássico desta casa em miniatura: um número medido de verdade,
respondendo a uma pergunta ligeiramente diferente da que se estava fazendo. O
`Botoes` e o `Cast` atravessam de graça — isso continua verdade e continua útil
quando a T2 chegar —, só não é disto que a T0 trata.

⚠️ E o número que importa pra T0 não é o de linhas soltas, é **o custo de
desacoplar as que sobram**. Medido chamada por chamada:

| arquivo | o que ele usa | chamadas |
|---|---|---|
| `Palco.kt` | `Text`, `MaterialTheme.typography`, `TextButton` | 13 |
| `Contracapa.kt` | `Text` | 10 |
| `Cortina.kt` | `Text`, `MaterialTheme.typography` | 2 |
| `Facho.kt` | `Icon`, `Text` — **as duas ficam no `:app`**, ver abaixo | 0 |

**25 chamadas**, mais um arquivo partido. É pouco, e não é `git mv`.

### 3.1 Onde esse código deve morar

⚠️ O `:core` é «tudo que não desenha» — e isto desenha. Pôr a `CaixaEm3D` lá
apaga a única régua que o módulo tem.

**Proposta: um quarto módulo, `:cenario`** — a cenografia compartilhada.

```
:core     → o que não desenha: dados, modelos, os Modelo*.kt, a paleta
:cenario  → o que desenha e não é de nenhum aparelho: a caixa 3D, a película,
            o facho, a cortina, a projeção, as tintas da capa
:app      → o celular: toque, PiP, download, Cast, haptics
:tv       → a sala: D-pad, foco, dez pés, a home do Google TV
```

A régua do `:cenario`: **só `foundation` + `ui` + `animation`**. Nenhum
`material3`, de nenhum dos dois sabores. Assim ele compila nos dois lados, e a
briga que a espec §4 previu continua não acontecendo.

⚠️ Isso é uma **segunda extração**, e vale a mesma cautela da primeira: ela sai
barata porque a fronteira já existe (as 1.794 medidas acima), não porque
extrações sejam baratas.

### 3.2 ⚠️ O `Facho.kt` é duas coisas num arquivo só, e só uma desce

Este é o único lugar onde a T0 **não** é `git mv`, e vale escrever antes que
alguém tente.

O arquivo tem 475 linhas e dois moradores. A **luz** — o envelope de dez quadros
do arco, o cone radial de sete paradas, a poeira determinística, a lente — está
desenhada **dentro** do `Canvas` do `BarraDoFacho`, que é a barra de baixo **do
celular**: `WindowInsets.safeDrawing`, `Row` de `Column`, `selectable`, e os dois
únicos `Icon`/`Text` do arquivo. Os dois `ALTURA_DA_*` são medidas da barra do
celular, com a conta do inset do gesto escrita em cima delas.

Mover o arquivo inteiro levaria a barra de navegação do celular pra dentro do
módulo compartilhado — e a §4 acabou de decidir que na TV a mesma luz mora numa
**cabine vertical na borda esquerda**, que não é a mesma peça.

Então o `Facho.kt` se **parte**:

| | onde fica |
|---|---|
| o envelope do arco, o cone, a poeira, a lente | `:cenario` |
| `BarraDoFacho`, `ALTURA_DA_FILEIRA`, `ALTURA_DA_LUZ`, `DestinoDoFacho` | `:app` |

⚠️ Partir **não é reescrever**: os dez quadros-chave, as sete paradas do radial,
os dois primos do espalhamento e o `2.6f` do raio são copiados dígito por dígito.
A §2.1 chama esses dez números de «o som da casa», e um deles trocado é um som
diferente. O que muda é só de onde eles são chamados.

### 3.3 A tipografia, que a proposta original não resolvia

`Palco`, `Contracapa` e `Tira` leem `Tipo`, `Serifada` e
`MaterialTheme.typography` — e as três moram no `Tema.kt` do `:app`. Num módulo
sem `material3` o `MaterialTheme` não existe, então isto precisava de decisão.

**Decidido: `Tipo` e `Serifada` descem pro `:core`**, junto do `Cores`. As duas
são `androidx.compose.ui.text` puro — **zero** imports de `material3` —, e a
fonte já mora lá (`core/src/main/assets`, mais o `dev.odeon.nucleo.R`). É o mesmo
argumento que já pôs a paleta no `:core` e está escrito no topo do `Tema.kt`:
uma cor não desenha, e a alternativa era o `:tv` copiar o dourado. Um `TextStyle`
também não desenha, e a alternativa era o `:tv` copiar a serifada.

O que fica no `Tema.kt`: a `TipografiaOdeon`, o `EsquemaEscuro` e o `TemaOdeon` —
que são a tradução do Material **de toque**, e são do celular.

Os três `MaterialTheme.typography.<slot>` que sobram (2 no `Palco`, 1 na
`Cortina`) viram estilo passado de fora ou lido do `Tipo`, e não `MaterialTheme`.

### 3.4 Duas peças a mais vêm junto, e não estavam na §0.1

O fecho de dependências das oito é maior que oito:

| | linhas | material3 | quem precisa |
|---|---|---|---|
| `ui/Animacao.kt` — `escalaDeAnimacao()` | 58 | **0** | a `Cortina` |
| `ui/Insignia.kt` — `MarcaDoNome` | 269 | 1 | a `Tira` |

A `Cortina` chama `escalaDeAnimacao()` pra respeitar quem desligou animação no
sistema — e é justamente a peça que tem um **piso de duração** (§2.4), então essa
linha não é enfeite. A `Tira` desenha `MarcaDoNome` no varal, e ela é a marca
derivada do nome, com o hash que tem que bater com o da web.

---

## 4. O trilho — a peça que mais muda

Hoje: 96dp fechados, seis ícones, uma barrinha dourada de 3dp. Nenhuma luz.

**A ideia:** na barra do celular o facho nasce **abaixo** e sobe. Numa TV a
única borda que sobra é a **esquerda** — e ali está exatamente onde, numa sala,
fica a cabine de projeção.

> **O trilho não é um menu. É a cabine.** A lâmpada mora nele, e o feixe abre
> pra direita, sobre a tela toda.

### 4.1 O que ele ganha

```
┌──────────┐
│  ◉  sam  │  ← o perfil, com o anel e o selo do nível
│     nv 2 │
├──────────┤
│  ⌕  buscar│  ← a busca vira ícone (pedido do dono)
├──────────┤
│  ▦  biblioteca
│  ▯  locadora     ← o escolhido tem a lente, e o feixe sai dele
│  ▤  mural
│  ✛  guia
│  ★  para você
└──────────┘
```

- **perfil no topo**, com avatar, anel de progresso e o selo do nível — o mesmo
  desenho da insígnia do celular. Escolhê-lo abre a tela de perfil.
- **busca como ícone**, logo abaixo. Escolhê-la abre a busca — e a da TV é a do
  sistema, por voz, que é a única que presta (ver §5.1).
- os seis destinos, e no escolhido **a lente**: um disco de luz com halo, o
  mesmo objeto do player.

### 4.2 O feixe

Ao trocar de destino:

1. a lente do destino antigo apaga
2. a lente do novo **firma o arco** — os dez keyframes, 1200ms
3. e um **feixe horizontal** abre da lente pra direita, esmaecendo ao longo da
   tela, com **poeira em suspensão** — a mesma poeira do celular

⚠️ O feixe é **decoração de fundo**, desenhado atrás do conteúdo com alfa baixo.
Ele não pode competir com um pôster nem atrapalhar a leitura de um título. Se na
TV ele ficar forte demais, ele diminui — a régua é a mesma do grão da R6, que
foi **testado e reprovado** no celular.

### 4.3 Aberto e fechado

Fechado, 96dp: só as lentes e os ícones. Aberto (foco dentro), 260dp: as
palavras. Isso já funciona hoje e não muda.

---

## 5. As seis telas

### 5.1 biblioteca

**A ideia:** a tela é o herói. No celular o herói é um cartão de 16:9 dentro de
uma coluna; numa TV, a tela **já tem** o tamanho e a proporção daquele cartão.

```
┌────────────────────────────────────────────┐
│ ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  ·  │ ← a marquise, na borda de cima
│                                            │
│   (arte do que está pela metade)           │
│                                            │
│   Jackass 3.5                              │
│   faltam 80min                             │
│   ▬▬▬▬▬▬▬░░░░░░░░░░░░░░░░░░░░░░░░░░░░░░    │
├────────────────────────────────────────────┤
│ CONTINUAR ──────────────────────────── 15  │
│ [quadro] [quadro] [quadro] [quadro]        │
│ BIBLIOTECA ─────────────── 60 de 8.316     │
│ [cartaz] [cartaz] [cartaz] [cartaz] [·]    │
└────────────────────────────────────────────┘
```

**A marquise vira o teto da tela.** Uma fileira de lâmpadas douradas ao longo da
borda superior, com o mesmo brilho e a mesma piscada do trilho. É a terceira
aparição do projetor, e a que transforma a TV numa **sala** em vez de uma grade.

⚠️ Elas piscam **na entrada da tela** e depois ficam acesas, respirando devagar.
Uma fileira de luzes piscando o tempo todo atrás de um filme é epilepsia, não
identidade.

- o **herói ocupa a primeira dobra** e mostra o que está pela metade — mesma
  regra do celular: `faltam X`, barra de progresso, título em serifada
- descer leva às fileiras; o herói **encolhe** e vira um cabeçalho fino,
  liberando a tela — é o gesto que uma TV faz melhor que um celular
- `CONTINUAR` com a contagem na ponta da régua
- `BIBLIOTECA` com `60 de 8.316` — dourado o carregado, apagado o total

**A busca.** Não há campo de texto nesta tela. Digitar com D-pad é soletrar. O
ícone no trilho abre a busca do **sistema** — por voz —, que já está implementada
(`busca/ProvedorDeBusca.kt`) e nunca foi exercitada. Um campo de texto aqui seria
oferecer o pior caminho como se fosse o principal.

### 5.2 locadora

**A ideia:** é a única tela que não tem cabeçalho — tem **fachada**. E numa TV a
fachada pode ser o que ela é de verdade: uma loja que você atravessa.

```
        ╭──────────────╮
       ╱   locadora     ╲          ← a marquise em arco, acesa
      │  ACERVO DA CASA  │
      ╰──────────────────╯
   ┌────────┐      ┌────────┐
   │  40    │      │  40    │      ← as plaquinhas, penduradas e tortas
   │na prat.│      │nesta s.│
```

⚠️ **O conflito da borda esquerda.** A fachada quer o centro; o trilho ocupa a
esquerda. Proposta: na locadora, o trilho **apaga a luz e vira silhueta** —
fechado, escuro, quase parte da parede — e a marquise da loja assume como fonte
de luz da tela. É coerente: entrou-se na loja, e a luz agora é dela. Sair pro
trilho reacende o facho.

**As estantes** viram fileiras horizontais de verdade: prateleira de madeira com
veio, lábio iluminado na frente, etiqueta de papel colorido presa com fita
girada uns graus, `6 de 145` no canto, e as caixas **em pé, em três quartos**,
mostrando a lombada — que é como uma estante de verdade se lê.

**A caixa na mão — o palco.** Escolher uma caixa:

1. o resto da tela **escurece e desfoca**
2. a caixa **voa pro centro** e cresce
3. `◀ ▶` **giram** a caixa (é o `pose` que a `CaixaEm3D` já aceita de fora)
4. `OK` **abre** — vira o menu de disco / a contracapa
5. `voltar` devolve à estante

A dica embaixo, na voz do celular traduzida: `◀ ▶ girar · OK abrir`.

⚠️ Numa TV isso é **melhor** que no celular: a caixa pode ser enorme, e a
lombada — que no celular tem 40dp — passa a ser legível de verdade.

### 5.3 guia

**A ideia:** é uma revista. Numa TV, uma revista aberta é uma **página dupla**.

```
┌─────────────────────┬──────────────────────┐
│ DIRETOR DA SEMANA   │                      │
│                     │   [pôster] [pôster]  │
│  湯山邦彦            │   [pôster]           │
│  até segunda        │                      │
│                     │  EM CARTAZ ESTA SEM. │
│  (o ensaio, em      │  Pokémon: O Filme    │
│   serifada, coluna  │  Termine até segunda │
│   de leitura)       │                      │
│  ESCRITO POR …      │                      │
└─────────────────────┴──────────────────────┘
```

- ⚠️ **o ensaio em serifada** — é matéria, não interface
- a coluna de leitura tem largura travada (~50% da tela); linha de texto
  atravessando 1920px é ilegível, e eu já paguei esse defeito na ficha
- `ESCRITO POR X` é **assinatura em versalete dourado**, não pílula
- `até segunda`, não «vira»
- os eixos (direção, elenco, gêneros, décadas, países) descem abaixo da dobra,
  como fileiras de fichas

⚠️ As fichas de eixo **não são focáveis hoje**, e é honesto: não existe a tela
que mostraria «tudo de Kubrick». Continuam assim até existir (§53).

### 5.4 mural

*Não houve imagem desta tela; o que segue vem das regras acima e do
`ui/mural/TelaDoMural.kt` do celular.*

**A ideia:** é um feed, e feed se lê de cima pra baixo — isso não muda. O que
muda é que numa TV cabe **a foto grande ao lado da frase**.

- cada acontecimento: arte à esquerda, frase à direita, `há 3 dias` na ponta
- `você` em dourado, o resto em branco
- `3 de 3 vozes` embaixo do título — o §8b em métrica, e ele fica
- ⚠️ o que não tem `frase` **não desenha** — servidor mais novo que o app manda
  tipos desconhecidos, e o silêncio é a resposta certa

### 5.5 para você

*Idem: sem imagem, derivado.*

**A ideia:** a recomendação com motivo é «a tese do projeto numa tela só» (§5 da
espec). Então o **motivo** tem que ser tão visível quanto o cartaz.

- um **herói** com as lâmpadas da marquise, como a biblioteca — a R6 do celular
- o filtro de tempo em pílulas, **no alto**, porque numa TV o que está embaixo
  custa apertos, e «tenho uma hora e meia» é a pergunta real de quem senta às
  onze da noite
- e o `porque` de cada recomendação **junto do cartaz**, não numa lista separada
  como está hoje

### 5.6 perfil

⚠️ **A tela de hoje está errada em duas coisas**, e as duas são de sentido:

1. eu filtro só as conquistas **abertas**. O celular mostra todas, agrupadas por
   camada, com as trancadas apagadas e **sem os pontos**. O §10.5 esconde o
   **número**, não a conquista — sem as trancadas não há o que perseguir.
2. eu desenho conquistas como cartões numa fileira. É **lista de marcação**:
   `✓` ou `☐`, nome, descrição, e `+10 XP` só na aberta.

**A ideia pra TV:** a capa (`EnfeiteEscolhido.capa`) borda a borda no topo,
esmaecendo — que é o que o celular faz e a TV faz melhor. Identidade grande à
esquerda: avatar com anel e selo, nome em serifada, `@sam`, a barra de XP larga,
e `299 XP · faltam 1 pro nível 3 · 7 de 80 conquistas`.

À direita, em **duas ou três colunas** (a TV tem largura que o celular não tem),
as camadas na ordem decidida:

```
fáceis · médias · sagas · difíceis · impossíveis · marcos de nível
```

> «não é alfabética nem por pontos: é a de dificuldade percebida (…) e por último
> os marcos de nível, que **não se perseguem, acontecem**»

E o placar da casa, com **a sua linha em cartão elevado** — é assim que «eu» se
marca, não pintando o nome de dourado como está hoje.

---

## 6. O player — a tela do dia a dia

### 6.1 A cortina abre a sessão

Ao escolher assistir: as lâmpadas piscam, a cortina vermelha aparece com o nome
do filme, e abre.

⚠️ Ela mora **dentro da espera que já existe** e tem um piso de duração — os dois
detalhes que o celular descobriu no aparelho, e que quem implementar não deve
redescobrir do zero. `Cortina.kt` já resolve os dois.

Numa TV, com a tela inteira e no escuro, isto é a coisa mais próxima de uma sala
de cinema que este produto vai chegar. É a peça de maior retorno do documento.

### 6.2 A película é a timeline

A `Tira` inteira, **maior**. O dono já corrigiu isso uma vez no celular
(«aumente o tamanho da timeline, está muito pequeno») e a TV é só largura.

**A tradução criativa, e é onde a TV ganha:** no celular você arrasta a película
até a cena. Numa TV não há arrasto — então **a lente fica parada no centro e a
película corre por baixo**. É literalmente o que um projetor faz, e resolve a
navegação sem gesto nenhum:

```
        ┌───────────────╥───────────────┐
  ◀◀    │▓▓▓▓▓▓▓▓▓▓▓▓▓▓▓║░░░░░░░░░░░░░░░│    ▶▶
        │  já visto     ║   o que vem   │
        └───────────────╨───────────────┘
                  ▲ a lente, fixa
```

- o que passou em **cor cheia**, o que vem a **34%** — a régua do celular
- perfurações em cima e embaixo
- ◀ ▶ correm a película **sem acender o cromo** (isso já funciona hoje e é a
  regra mais importante do player de TV)
- sem folha de sprites → as 12 cenas; sem elas → barra fina. **Nunca** inventar
  retângulo colorido (§18)

### 6.3 O transporte

- `↺10` · **disco dourado grande com halo** (play/pause) · `↻30`
- ⚠️ **assimétrico**: 10 atrás, 30 à frente. Confirmado na fonte do celular.
  O `:tv` faz ±10 hoje.
- **legenda** e **áudio** como pedido pelo dono — e cada um só nasce quando há o
  que escolher (§53). O filme conferido tinha uma faixa de áudio só, e o botão
  corretamente não apareceu.
- o plano vira **ponto colorido** ao lado do título, não pílula com texto

### 6.4 O relógio

`3:56` à esquerda, `faltam 1:20:53` à direita. Já está certo hoje.

---

## 7. A ficha da obra

Está perto. O que falta:

- `continuar de 2min` → **`faltam Xmin`**, a voz da casa
- as **cenas** já descem no varal — mantém
- ⚠️ o texto já foi consertado pra 55% da largura depois de sair do véu na TCL;
  a fração e o degradê agora são **o mesmo número**, e devem continuar sendo

---

## 8. O que **não** atravessa, e o que entra no lugar

| do celular | por quê | o que entra |
|---|---|---|
| arrasto pra girar a caixa | não há dedo | `◀ ▶` giram; `OK` abre |
| háptico com dois pesos | não há mão | a piscada da lente, que é o mesmo «senti» pelos olhos |
| paralaxe por acelerômetro | a TV não se inclina | nada. É o único que não tem substituto, e tudo bem |
| campo de busca | digitar é soletrar | busca do sistema, por voz, pelo ícone do trilho |
| PiP · download · Cast | a TV **é** o destino | nada |

---

## 9. As levas

Cada uma é aprovável e entregável sozinha, e cada uma termina **vista rodando na
TCL** — não compilando.

| leva | o que entra | por que primeiro |
|---|---|---|
| **T0** | o módulo `:cenario` — **mover** (não reescrever) `CaixaEm3D`, `Palco`, `Contracapa`, `TintasDaCapa`, `Tira`, `Cortina` e `Projecao` do `:app`; **partir** o `Facho` (§3.2); descer `Tipo` e `Serifada` pro `:core` (§3.3); e levar junto `escalaDeAnimacao` e `MarcaDoNome` (§3.4). Ver §0.1 | nada abaixo funciona sem ele, e é o único passo que mexe no `:app` |
| ~~**T1**~~ ✅ | **a luz**: o trilho vira cabine — perfil, busca, lentes, o feixe e a piscada de arco. **Feita e vista na TCL em 12/08/2026** — ver §10.3 | é o que o dono pediu primeiro, e é o que mais muda a impressão |
| ~~**T2**~~ ◐ | **o player**: a cortina e a película, mais os botões de áudio/legenda e o 10/30. **Feita e vista na TCL**, com **uma pendência medida** — a lente da §6.2 não ficou parada. Ver §10.4 | é a tela do dia a dia |
| ~~**T3**~~ ✅ | **a locadora**: fachada, plaquinhas de papel, estantes de madeira, e a caixa 3D no palco. **Feita e vista na TCL** — ver §10.9 e §10.10 | é a tela com mais identidade e a que mais some hoje |
| ~~**T4**~~ ✅ | **a biblioteca**: o herói com a marquise, as contagens douradas, o herói que encolhe. **Feita e vista na TCL** — ver §10.12 | |
| **T5** | **perfil e guia**: as conquistas como lista por camada, a página dupla da revista, o ensaio em serifada | são correções de sentido, não de brilho |
| **T6** | **mural e para você** | são as duas sem imagem de referência — ficam por último de propósito |

⚠️ A voz (`faltam`/`até`/`vira`) e as contagens douradas são **correções soltas**
e entram junto da primeira leva que tocar cada tela. Não precisam de leva
própria.

---

## 10. O que este documento não sabe

Honestidade, na régua da casa:

- **Não vi mural nem para-você** do celular. As §5.4 e §5.5 são derivadas das
  regras e do código, não de uma tela. Podem estar erradas de um jeito que só uma
  imagem corrige.
- ~~**Não sei o custo de desenho na TCL.**~~ **Agora sei, e a resposta é ruim.**
  Ver a §10.1 logo abaixo — foi medido em 12/08/2026, e é o achado mais
  importante da T0.
- **O feixe pode ser demais.** Ele é o efeito com maior chance de virar ruído
  atrás de um pôster. Se for, ele diminui — e isso é resultado, não fracasso: o
  grão da R6 foi testado e reprovado no celular, e o app ficou melhor sem ele.
- **A fileira na home do Google TV** publica sem erro mas nunca foi vista. Não é
  deste redesenho, mas está no mesmo aparelho.

---

## 10.1 ⚠️ O custo da caixa 3D na TCL — medido em 12/08/2026

A §10 dizia «não sei o custo de desenho na TCL (…) a leva T0 deve terminar com um
número medido no aparelho». Aqui está o número, e ele **reprova a caixa na
estante** — sem reprovar a caixa.

### Como foi medido

A prateleira da locadora do `:tv` foi trocada de `Cartaz` plano pra
`CaixaEm3D` + `FaceDaCaixa` em tamanho de sala (`240dp` de altura, o fator
`240/144 = 1,667` aplicado às três medidas do celular). Aí, com `dumpsys gfxinfo`
zerado, **catorze `▶` a 0,45s** dentro de uma fileira só — sem trocar de fileira,
pra o número não medir carregamento de imagem.

E o mesmo gesto na **biblioteca**, que continua com cartaz plano, como controle.

| | quadros | jank | **50º percentil** |
|---|---|---|---|
| biblioteca — cartaz plano (controle) | 47 | 44,7% | **42ms** |
| estante — caixa 3D (teste) | 37 | **100%** | **200ms** |

200ms é **5 fps**. E os contadores dizem onde dói:

```
Slow UI thread:            37 de 37
Slow issue draw commands:  37 de 37
Slow bitmap uploads:        0 de 37   ⬅️
```

⚠️ **Zero em `bitmap uploads`.** Não é a rede nem o Coil decodificando pôster —
é composição e desenho. A suspeita mais forte é o `BoxWithConstraints`: a
`FaceDaCaixa` usa um por face, e o `CaixaEm3D` desenha seis faces. Com quatro
caixas na tela isso é **24 subcomposições** por quadro, e subcomposição é
justamente o que o `BoxWithConstraints` custa.

### ⚠️ O que este número **não** diz

Ele não diz que a peça compartilhada foi um erro, e é importante separar as duas
coisas porque elas se confundem fácil:

| pergunta | resposta |
|---|---|
| a caixa **fica boa** a três metros? | **sim** — e melhor que o cartaz plano. A lombada ficou legível, com título, tarja dourada, miniatura e `2024 · DVD`, exatamente como a §5.2 previu |
| o código compartilhado **funcionou** nos dois? | **sim** — compilou e desenhou no `:tv` sem uma linha de mudança nas peças |
| dá pra pôr **oito** delas numa prateleira rolável? | **não neste aparelho** |

E o corolário que decide a arquitetura: **uma cópia da caixa pro `:tv` seria
exatamente igual de lenta.** O custo é da peça, não da fronteira de módulo — não
há versão «de TV» deste desenho que custe menos por ser copiada. Copiar pagaria o
preço de manter duas e não compraria um quadro sequer.

### O que fazer com isso

A §5.2 já colocava a caixa 3D **no palco** — uma, no centro, quando se escolhe —
e a prateleira era o lugar onde ela aparecia «em pé, em três quartos». O número
acima diz que a segunda metade dessa frase é cara e a primeira não foi medida.

Três caminhos, e a T3 escolhe com o aparelho na mão:

1. **a caixa só no palco**, e a prateleira volta ao cartaz plano. É o menor risco
   e mantém o momento que importa — a caixa grande, girando, é o que o dono
   pediu. ⚠️ Falta medir **uma** caixa sozinha; 200ms com quatro não diz quanto
   custa uma.
2. **prateleira com caixa mais barata** — tirar o `BoxWithConstraints` da
   `FaceDaCaixa` (as medidas viriam por parâmetro, como já acontece com a letra
   do `Palco`), ou rasterizar a caixa parada num `graphicsLayer` já que na
   prateleira a pose não muda.
3. **a prateleira desenha a lombada e mais nada** — que é o que uma estante de
   verdade mostra, e é uma face em vez de seis.

⚠️ Nada disso é T0. O que a T0 devia entregar era o número, e o número está aqui.

---

## 10.2 ⚠️ O `Texto` do `:cenario`, e três modelos errados do `Text` do Material

Registro de um defeito que **só o screenshot achou**, e que eu errei três vezes
antes de acertar. Vale escrever porque o próximo módulo compartilhado vai bater
na mesma pedra.

### O problema

O `:cenario` não pode depender de `material3` (§3.1), e as peças escrevem texto —
21 chamadas. Elas passaram a usar um invólucro sobre o `BasicText` do
`foundation`. A pergunta é: **o que o `Text` do Material fazia que o `BasicText`
não faz?**

### As três respostas erradas, e o que cada uma custou

| # | o que eu supus | o que o aparelho mostrou |
|---|---|---|
| 1 | «as peças escrevem o `TextStyle` inteiro à mão, então basta `TextStyle.Default`» | a lombada da caixa saiu diferente numa faixa de 24px por toda a altura do objeto |
| 2 | «o `Text` funde a chamada por cima do `LocalTextStyle`; basta reconstruir o `bodyLarge` à mão» | o selo do nível fechou em **0**, e a caixa do palco **subiu 13px** — 411.010 pixels. Duas telas discordando é a assinatura de um modelo errado |
| 3 | «então o hospedeiro empresta o `LocalTextStyle` dele, e eu fundo por baixo» | palco **0**, selo ainda fora |

### A resposta certa

São **dois** caminhos, e confundi-los é o erro:

| | |
|---|---|
| o `LocalTextStyle` | é o **valor padrão** do parâmetro `style`. Quem passa `style = X` não o vê |
| os campos **soltos** (`fontSize`, `fontWeight`, `lineHeight`, `letterSpacing`, `textAlign`) | são fundidos **por cima** do `style` |

```
Texto(style = X, ...)          → X vale inteiro, campos em branco em branco
Texto(fontSize = 12.sp, ...)   → LocalLetraDoHospedeiro.merge(os soltos)
```

⚠️ E houve um **quarto** erro, que não era de modelo e sim de execução: os quatro
campos soltos foram declarados na assinatura e **esquecidos no corpo**. O `Bold`
do algarismo do selo não chegava, e a foto mostrou um «3» visivelmente mais fino
que o original — 996 pixels. Uma substituição de texto que falhou calada, num
arquivo que eu tinha acabado de reescrever três vezes.

### O resultado, medido

| tela | pixels diferentes do original |
|---|---|
| palco — a caixa, a lombada, a dica | **0** |
| perfil — a insígnia, o selo, o placar | **0** |
| locadora | 3.448 — dos quais **3.414 são o selo mostrando `3` em vez de `2`**, porque o nível subiu durante a sessão. Os 34 restantes são grão solto |

Ou seja: o celular atravessou a mudança de módulo **sem uma diferença de
desenho**, e as três telas foram conferidas por comparação de pixel, não por
leitura de código.

### ⚠️ A lição, que é de método e não de Compose

Nenhum dos quatro erros aparece num build verde. Os quatro passaram por
`assembleDebug`, pelos 155 testes e pelo `lintDebug` limpo — e os quatro foram
achados pela mesma coisa: **tirar o screenshot antes e depois e subtrair**.

O segundo é o mais instrutivo. Ele deu **zero** numa tela e 411.010 na outra, e
foi essa discordância — não o número em si — que provou que o modelo estava
errado. Uma tela só teria me deixado publicar a versão errada com uma medição
verdadeira do lado.

---

## 10.3 A T1 na TCL — o que a tela cobrou

A leva foi escrita, instalada e percorrida com o controle. Quatro coisas só
apareceram aí, e nenhuma delas quebra build.

### ⚠️ O retrato nasceu vazio

O topo do trilho mostrava um anel escuro com um `·` no lugar do nível. O
`ModeloDoPerfil` só era acordado quando alguém **abria** a tela do perfil — o que
bastava enquanto nada mais mostrava o rosto. Agora o rosto está em toda tela, e a
primeira impressão do app era um buraco.

Conserto: um `carregarSePreciso()` na abertura da casa. A guarda dele faz as duas
chamadas virarem uma requisição.

### ⚠️ A poeira virou um campo de estrelas

A §4.2 avisou — «ele não pode competir com um pôster» — e a §10 marcou o feixe
como o efeito de maior risco do documento. Na primeira foto ele acendeu grão no
**canto superior direito**, a dois metros de qualquer luz.

E a causa não era o alfa: era a **região**. No celular a mesma conta roda dentro
de uma caixa de 143dp, então o pó fica confinado por construção; aqui a caixa é a
sala inteira e a mesma conta acende quase tudo. Baixar a força só deixaria o
campo de estrelas mais fraco.

| | antes | agora |
|---|---|---|
| espessura do feixe (`alcance`) | 50% da altura | **20%** |
| até onde o pó chega (`raio`) | a tela toda | **metade da largura** |

Depois disso os pôsteres ficaram limpos e o pó ficou onde a luz está.

⚠️ **O `FORCA_DO_FEIXE` continua sendo um chute** — 0,34, com nome próprio pra
ser fácil de mexer. Ele não foi calibrado contra nada; foi escolhido pra errar
pra menos, que é o que a §4.2 manda.

### ⚠️ Com o perfil escolhido, não havia lente nenhuma

O perfil saiu da fileira e foi pro topo, e a fileira era quem desenhava a lente.
Resultado: escolher o perfil apagava a lâmpada **e o feixe continuava saindo da
última posição conhecida** — luz sem lâmpada, que é a única coisa que este trilho
não pode fazer.

O retrato passou a carregar a lente também.

### ⚠️ A busca **não foi vista funcionando**, e fica assim escrito

O botão dispara e o sistema aceita. O que aparece é o **onboarding do launcher da
Google TV**, não uma busca.

Isolado antes de culpar o código: os dois intents resolvem pro app certo
(`com.google.android.katniss`), e disparar `android.search.action.GLOBAL_SEARCH`
**direto pelo `adb`**, sem passar pelo app, cai no mesmo onboarding. É estado do
aparelho — a conta da Google TV desta TCL não terminou de ser configurada.

Ou seja: o caminho está certo e o destino não foi alcançado. O README continua
listando «a busca por voz» como não vista, e deve continuar.

### O que **foi** visto funcionando

| | |
|---|---|
| o retrato | avatar, anel de progresso e selo do nível — a `Insignia` do `:cenario`, a mesma do celular |
| a lente | disco de luz com halo no destino escolhido, e ela **fica no escolhido enquanto o foco anda** |
| a piscada | conferida em vídeo: a lente antiga apaga, some no vale, e a nova firma o arco |
| o feixe | abre da lente pra direita, com poeira, atrás do conteúdo |
| **o D-pad** | percorre os **sete** itens e para nas duas pontas — conferido por `uiautomator`, item a item |

⚠️ A última linha é a que mais importa, e é a lição mais cara deste projeto: o
trilho ganhou dois itens novos, e «o D-pad alcança» não é coisa que se deduza de
um build verde. Foi medido: `[26,83]` o retrato, `[20,233]` a busca, e os cinco
destinos abaixo.

---

## 10.4 A T2 na TCL — o que entrou, e a única coisa que não saiu

### O que foi visto rodando

| | |
|---|---|
| **a cortina** | as lâmpadas piscam, o pano vermelho aparece com o nome do filme e abre. Veio inteira do `:cenario`, com o piso de duração que o celular pagou pra descobrir |
| **a película** | a `Tira` do tamanho da sala: cenas de verdade, perfurações, o já visto em cor cheia contra o que vem, e a lente sobre o ponto atual |
| **o 10/30** | era ±10; agora é `↺ 10s` e `30s ↻`, como o celular e como a §6.3 |
| **o ponto do plano** | a pílula «direto / transcodificando» virou um ponto colorido ao lado do título |
| **áudio e legenda** | já estavam certos antes desta leva: cada botão só nasce quando há o que escolher (§53), e no filme conferido o `cc` apareceu e o de áudio não |

⚠️ **O cromo não nasce enquanto o pano está no ar.** É lição do celular, e sem ela
o resultado é título, relógio e «faltam 1:37:48» flutuando sobre uma cortina
fechada — a interface anunciando um filme que não começou.

### ⚠️ A lente **não** ficou parada, e a §6.2 pedia que ficasse

> «No celular você arrasta a película até a cena. Numa TV não há arrasto — então
> a lente fica parada no centro e a película corre por baixo.»

O plano era conseguir isso **sem tocar na `Tira`**: ela não recebe `modifier` e
usa `fillMaxWidth()` por dentro, então bastaria pô-la num pai três vezes mais
largo que a tela e deslocar o pai. A lente que ela desenha em `fracao` cairia no
centro por construção.

Duas medições na TCL derrubaram as duas versões:

| | o que a foto mostrou |
|---|---|
| com `Modifier.width` | a lente **240px à esquerda** do centro — medido procurando as colunas douradas da borda no PNG, contra um centro de tela em 960 |
| com `requiredWidth` | a película encolheu pra ~45% da largura e a lente **sumiu** |

A primeira tem causa conhecida e vale registrar porque é uma armadilha geral do
Compose: **`Modifier.width` é preferência, não ordem.** Pedir 3× dentro de uma
caixa de 1× devolve 1×, sem aviso. Só o deslocamento acontecia, e por isso o erro
da lente variava com a fração — o que confunde, porque parece calibração.

A segunda **não foi diagnosticada**, e está escrita assim de propósito. A `Tira`
tem `BoxWithConstraints` aninhado, e o que ela mede deixa de ser o que a janela
mostra assim que o pai fura a restrição — mas isso é hipótese, não medida.

### Por que parei aí

Fazer a lente parar provavelmente pede um modo **dentro** da `Tira`: uma película
mais larga que a janela, com deslocamento próprio. Isso é mudar a peça — e a T0
decidiu que as peças se movem e não se reescrevem.

Abrir essa exceção no meio da T2, sozinho, sem o dono ter visto o que já está de
pé, seria decidir a coisa mais cara do documento em silêncio. A primeira linha da
§6.2 — «A `Tira` inteira, **maior**» — está entregue; a segunda fica como
pendência **medida**.

---

## 10.5 O cromo do player, refeito a pedido — 12/08/2026

O dono viu a T2 rodando e pediu quatro coisas. Nenhuma delas era gosto solto: as
quatro têm argumento, e uma era defeito.

### «feios demais» — a fileira era cinco pílulas de texto

`pausar` · `‹ 10s` · `10s ›` · `cc` · `sair`, ancoradas à esquerda. Viraram:

| | |
|---|---|
| **centralizado** | a fileira mora no meio da tela, que é onde os olhos já estão |
| **o disco clássico** | `pausar` escrito virou o ▶/⏸ desenhado — o `BotaoDeTocar` do celular, com o disco dourado e o halo que a §6.3 sempre pediu |
| **o 10 à esquerda** | «coloque 10s pra esquerda do botão play pause». É a ordem do celular, e ela é **espacial**: voltar fica do lado de onde o filme veio |
| **o `sair` no canto** | rodapé esquerdo, longe do transporte |

⚠️ O `sair` no canto não é arrumação: ele não é comando de filme, é comando de
**sessão**. No meio do transporte ele ficava a uma seta do `30s ↻` — e a única
tecla que se aperta às cegas num controle de TV é justamente a seta.

E os três botões vêm do `:cenario`: o `Botoes.kt` atravessou como a §3 previu —
254 linhas, zero `material3`, «atravessa de graça». São **os mesmos objetos** do
celular, não uma segunda versão deles.

### ⚠️ O defeito: passar o foco pelo botão já buscava

> «só de ficar em cima do botão de voltar 10s ou avançar 30s o filme já muda sem
> eu clicar»

A causa estava em duas linhas do `onKeyEvent`:

```kotlin
Key.DirectionLeft  -> { pular(-10); true }
Key.DirectionRight -> { pular(10); true }
```

Elas valiam **sempre**. Com o cromo aberto, andar entre os botões é ◀▶ — então
cada passo do foco buscava dez segundos. E como o evento era consumido, o foco
**também não andava**: o que parecia «o filme muda sozinho» era a fileira inteira
sequestrada.

Agora as duas devolvem `false` com o cromo aberto. A busca por seta continua
existindo onde ela foi desenhada pra existir: sobre o filme, sem interface.

Conferido pausando o filme e apertando ◀ seis vezes com o cromo aberto: **1:50:55
antes, 1:50:55 depois**.

### A película aparece ao buscar, e some sozinha

> «dentro do filme ao usar o dpad do controle para esquerda ou direita a timeline
> de film roll deve aparecer para você acompanhar»

Ela aparece **sozinha**, sem o resto do cromo — e isso é o que faz a regra antiga
continuar de pé. «◀ ▶ correm a película sem acender o cromo» queria dizer «sem
tapar o filme com botões», não «sem mostrar onde você está».

⚠️ **A primeira versão nunca a apagava.** O temporizador era
`LaunchedEffect(espiando, posicao)`, e `posicao` muda a cada 200ms com o filme
correndo: o efeito reiniciava antes de completar os 2s, e a espiada durava para
sempre. Contando **saltos** em vez de posições, o relógio só reinicia quando a
pessoa aperta a seta de novo.

Medido na TCL, pixels claros na faixa da película: **5.899** antes, **11.449**
logo depois da seta, **1.094** quatro segundos depois.

---

## 10.6 Dois relatos do dono, e um deles eu diagnostiquei errado primeiro

### ⚠️ Os botões do transporte não eram selecionáveis

> «os botões voltar 10s e avançar 30s não são selecionáveis no player»

Causa: o `BotaoDeSalto` e o `BotaoDeTocar` vêm do `:cenario` e usam
`Modifier.clickable` — que é **dedo**. Um nó `clickable` não é alvo de D-pad; ele
nem entra na busca de foco. Enquanto eram `BotaoDaSala`, o `Focavel` vinha junto
e ninguém precisou pensar nisso.

⚠️ É a §8 em código: «háptico → não há mão» vale pro toque inteiro. **A peça
atravessa; o jeito de apontar pra ela não.** Trocar o desenho trocou o input
junto, calado — e o play/pause continuou respondendo porque o `OK` é tratado pelo
`onKeyEvent` da tela, o que escondeu metade do defeito.

Conserto: cada um dentro de um `Focavel`. Conferido na TCL com foto — o anel
percorre `10` → disco → `30`.

### ⚠️ A ficha cortava o que não cabia, nas duas pontas

> «o botão pra assistir não aparece no 007 serviço secreto, já no cassino Royale
> os botões estão sem texto dentro dos botões»

Dois relatos, **uma** causa: a coluna da ficha era `fillMaxHeight()` com
`Arrangement.Center` e sem rolagem. Conteúdo maior que a caixa não some por
baixo — é cortado em cima **e** embaixo, porque centralizar transborda dos dois
lados.

| filme | o que se via |
|---|---|
| 007, título de três linhas | os botões inteiros fora da tela |
| Cassino Royale, título de uma | os botões cortados, sobrando a borda — «sem texto dentro» |

O segundo é o mais traiçoeiro: não parece corte, parece botão desenhado errado.

O conserto é `verticalScroll` **com** o `Center`, que parece contradição e não é:
`fillMaxHeight` fixa a altura mínima na da tela e o scroll solta a máxima, então
a coluna mede `max(conteúdo, tela)`. Ficha curta continua centralizada; ficha
longa rola. E numa TV ninguém rola de propósito — quem rola é o **foco**.

### ⚠️ E a pílula vazia, que eu diagnostiquei errado

Junto veio «uma pílula sem texto» no fim da fileira de etiquetas. Meu primeiro
palpite foi etiqueta com `value` em branco, e eu filtrei por `isNotBlank()`.

**A foto seguinte mostrou a pílula vazia no mesmo lugar.**

Ela não era vazia: era a quinta etiqueta, cortada pela largura da coluna (55% da
tela). Uma `Row` não quebra linha — transborda, e o recorte deixou só a borda
esquerda, que lê como um botão sem texto. O mesmo valia pra fileira de botões:
`voltar` virava `vo`.

Conserto de verdade: `FlowRow` nas duas fileiras. O filtro de `isNotBlank` ficou
como rede de segurança, **rebaixado a isso no comentário** — porque um conserto
que não consertou, deixado com cara de conserto, é o próximo a enganar alguém.

---

## 10.7 Três ajustes de mão, e o que cada um ensinou

### O anel do play abraçava o halo, não o disco

> «esse círculo do select do play tá muito grande»

O `BotaoDeTocar` é um disco de **60dp** dentro de um halo de **124dp**, e o halo
é conteúdo — é ele que dá luz ao botão. O anel do `Focavel` abraça o conteúdo,
então saía um círculo com o dobro do diâmetro da coisa que ele deveria marcar.

Encolher o halo não serve (é a peça) e recortar também não (o halo sumiria). O
`Focavel` ganhou `anel = false`, e quem sabe onde o disco está desenha o anel em
volta **dele** — 72dp, que são os 60 do disco mais 6 de respiro de cada lado.

⚠️ É a segunda vez nesta leva que a fronteira `:cenario`/`:tv` cobra a mesma
coisa: **a peça atravessa, e o que envolve a peça é do aparelho.** Primeiro foi o
foco (`clickable` × `Focavel`), agora o anel.

### O que separava a película do play não era o vão escrito

> «desce um pouco a timeline film roll junto com o nome para mais próximo do play
> button»

O `Spacer` entre os dois tinha 18dp — mas o halo do `BotaoDeTocar` põe **32dp de
transparente** acima do disco antes de qualquer pixel aceso. O vão real era 50, e
mexer só no número escrito teria dado meio conserto.

### O `sair` virou peça própria

A pílula genérica dizia a coisa errada de três jeitos: peso de botão principal
num canto onde ninguém precisa dele, uma palavra sem direção, e aparência de
apertável o tempo todo.

Agora é fantasma com chevron — contorno até o foco chegar, e aí **enche**, que é
a §2.8 da casa: «estado é preenchimento, não cor». O alvo continua com 44dp de
altura mínima; encolher o desenho não é encolher o que se acerta com o controle.

⚠️ Conferido que ele continua alcançável: `◀` a partir do `10` cai nele
(`[84,928]`), que é a direção espacialmente certa. `▼` não chega, e não deveria —
não há nada abaixo do transporte.

---

## 10.8 A viagem pelo rolo — e a §6.2 responde de outro jeito

> «quando eu vejo os botões de play e pause eu posso usar o dpad pra cima e ir na
> timeline, usando o dpad dela me fazendo de um jeito rápido viajar no rolo do
> filme até onde eu quiser pra frente ou trás, daí se eu apertar o ok do
> controle no dpad o filme vai pro ponto que escolhi»

### ⚠️ Isto resolve o que a §10.4 tinha deixado em aberto

A §6.2 queria «a lente parada no centro e a película correndo por baixo», e eu
não consegui fazer sair sem mexer na `Tira` — está registrado, com as duas
medições que falharam.

**O pedido acima resolve a mesma coisa por outro caminho.** O problema que a
lente parada existia pra resolver era «como se navega um rolo sem dedo». A
resposta agora é: **o rolo não corre — o foco anda nele**. A lente vai até a
cena, os quadros em volta são os de lá, e `OK` leva o filme até ali.

⚠️ Isso não torna a §6.2 errada; torna-a **opcional**. O que ela pedia era uma
encenação pra uma função, e a função está entregue com outra encenação — que por
acaso é mais de TV, porque usa foco em vez de simular arrasto.

### Como é

| tecla | o que faz |
|---|---|
| `▲` do transporte | o foco sobe pra película |
| `◀` `▶` | viajam **um quadro** por aperto |
| `OK` | o filme vai pro ponto escolhido, e o foco volta pro play |
| `▼` · `voltar` | desistem — **não** são consumidos de propósito |

### ⚠️ E ela não anuncia nada, por corte do dono

A primeira versão punha um anel dourado em volta da película inteira e trocava o
«faltam» por um `OK PRA IR ATÉ AQUI`. Os dois saíram:

> «não precisa do contorno amarelo na timeline quando tu vai controlar ela (…)
> pode tirar o ok para ir até aqui — essas duas coisas são intuitivas por si só,
> deixe somente a funcionalidade»

O argumento é o da casa, aplicado com mais rigor do que eu tinha aplicado: **a
lente já se move quando as setas andam, e os quadros em volta trocam junto.** O
objeto está dizendo o que acontece; o anel e a frase diziam a mesma coisa uma
terceira e uma quarta vez. É o §24 — instrução que a própria coisa ensina não é
instrução, é ruído —, e é o mesmo desenho do `Palco` do celular, onde a dica de
arrastar some pra sempre depois de obedecida.

⚠️ E o «faltam» ficou **melhor** sem a troca: durante a viagem ele conta o que
sobra **do ponto que se está escolhendo**, que é exatamente a pergunta de quem
navega o rolo — «se eu for pra cá, quanto ainda tem?».

Junto saiu o parâmetro `viajando` da peça, que existia só pra desenhar as duas
coisas. Parâmetro que sobrevive ao próprio uso vira alavanca que não liga em
nada.

**Um quadro por aperto, e não N segundos.** A `Tira` desenha até 40 quadros ao
longo do filme, então `duração/40` é a distância exata de um quadro pro vizinho.
Num filme de 2h22 dá ~3min30 por aperto e o rolo inteiro em 40 — o «de um jeito
rápido» pedido. E é a §2.3 virando navegação: «Você não arrasta até um tempo —
arrasta até uma **imagem**.»

### As decisões que não são óbvias

⚠️ **`alvoDaViagem` é anulável, e isso é o desenho.** A tela precisa responder
duas perguntas ao mesmo tempo — *onde o filme está* e *onde eu estou olhando*. Com
um número só, desistir da viagem não teria pra onde voltar. É a mesma separação
que o `Trilho` faz entre foco e destino: percorrer não é escolher.

⚠️ **O `onKeyEvent` mora no nó focado, não na tela.** Eventos sobem do foco pra
fora, então o teclado da película consome `◀▶` antes do bloco da tela inteira —
aquele que salta dez segundos com o cromo apagado. Os dois nunca estão em jogo ao
mesmo tempo, e por isso não brigam.

⚠️ **O cromo não apaga durante a viagem.** `alvoDaViagem` entrou na conta do
auto-apagar: sumir com a película no meio de uma navegação é levar embora
justamente o que se está usando.

⚠️ **A ponta direita troca de assunto.** Viajando, «faltam 45:49» vira `OK PRA IR
ATÉ AQUI` — §24: a instrução aparece quando é necessária. Um tempo restante
durante uma viagem é um número sobre um filme que ninguém está vendo.

### Conferido na TCL

`1:57:49` → seis `◀` → `1:36:29` (21 minutos, ~3min30 por aperto) → `OK` → o
filme pulou pra `1:36:29`, o `faltam` voltou, e o foco reapareceu no disco do
play (`[821,763][1099,1041]`).

---

## 10.9 A caixa no palco — o número que faltava da §10.1

A §10.1 mediu **oito** caixas numa prateleira rolável e reprovou a prateleira,
deixando escrito que faltava medir **uma**, no palco, «e esse número ainda não
foi medido». Aqui está.

| | quadros | jank | **50º percentil** |
|---|---|---|---|
| biblioteca — cartaz plano (controle) | 47 | 44,7% | **42ms** |
| estante — oito caixas 3D | 37 | 100% | **200ms** |
| **palco — uma caixa, girando** | 31 | **100%** | **97ms** |

### O que o número diz, e o que ele não diz

⚠️ **97ms é ~10fps, e continua 100% de jank.** Não é o número que se queria. Mas
ele não é a metade de 200 por acaso, e a conta não é linear: oito caixas custam
o dobro de uma, não oito vezes. Há um custo fixo grande por quadro — coerente com
o diagnóstico da §10.1, de que o gasto é **composição** (`BoxWithConstraints` por
face, seis faces) e não pixel.

⚠️ E há uma diferença de regime que o número sozinho esconde: na prateleira os
200ms atrapalhavam **rolagem**, que é contínua e que a pessoa faz o tempo todo.
Aqui os 97ms atrapalham **giro**, que dura três ou quatro apertos e acontece uma
vez por caixa. O mesmo fps incomoda menos.

⚠️ **Mas incomoda.** A pose anima em 180ms; a 97ms por quadro isso são dois
quadros, ou seja o giro lê como um salto e não como um objeto virando — que é
justamente o que a animação existe pra evitar. **Isto não foi resolvido**, e as
saídas continuam sendo as três da §10.1: tirar o `BoxWithConstraints` da
`FaceDaCaixa`, rasterizar a caixa parada, ou desenhar menos faces.

Fica assim registrado em vez de escondido: a caixa no palco **está de pé e é
bonita**, e o giro dela é mais duro do que devia.

### E um erro de leitura da §5.2, corrigido em foto

A §5.2 convida ao exagero — «a caixa pode ser enorme» — e eu li «enorme» e pus
**520dp** de altura. Na TCL ela saiu cortada em cima e embaixo, e levou o título
e a dica pra fora da tela junto.

O teto não é gosto, é conta: 540dp de sala, menos 54 de overscan, menos 82 de
título e dica com os respiros, sobram **404**. Ficou em 380, com 24 de folga pra
caixa poder crescer no giro sem encostar no título.

---

## 10.10 A locadora virou uma loja — T3 na TCL

### O que entrou

| | |
|---|---|
| **a fachada** | a arandela de latão com o facho, `locadora` em serifada acesa a 54sp, e `ACERVO DA CASA` em versalete |
| **as plaquinhas** | as duas penduradas por pino e fio, tortas pra lados opostos, com `40 na prateleira` e `40 nesta semana` escritos à mão |
| **as estantes** | prateleira de madeira com o lábio iluminado, e a etiqueta de papel colorido presa com fita — verde pra *Infantil*, creme pra *Ficção científica* |
| **a caixa no palco** | escolher pega a caixa **na mão**; `◀ ▶` giram, `OK` abre, `voltar` devolve |
| **o trilho em silhueta** | na locadora a cabine apaga e a marquise da loja vira a fonte de luz |

⚠️ **O título de tela saiu.** É o ponto da §5.2 — «a única tela que não tem
cabeçalho: tem fachada» —, e a contagem foi junto: ela agora está nas plaquinhas,
que é onde uma locadora de verdade a põe.

### O `Cenografia.kt` atravessou inteiro, e foi medido antes

402 linhas, **um** import de `material3`, e as dependências todas já do outro
lado (`Cores`, `Serifada`, `Prateleira`). A arandela, as etiquetas penduradas, a
plaquinha de estante e a etiqueta de prazo estavam num arquivo só desde o
celular, porque já eram cenário — a T3 só precisou mudá-lo de módulo.

A `Tabua` veio junto, promovida de `private` na tela do celular. Uma prateleira
de madeira não é do celular nem da TV: é do **lugar**.

### ⚠️ E a cabine apagando fez o feixe sumir junto, de propósito

A §5.2 pede o trilho em silhueta na locadora. Só apagar a lente deixaria o feixe
saindo de uma lâmpada que não existe — **o defeito exato que a T1 mediu** quando
o perfil escolhido deixava a luz órfã. Os dois apagam juntos.

«Sair pro trilho reacende» sai de graça: a cabine só se apaga **fechada**, e ela
abre ao receber foco. Quem vai escolher outro destino vê a luz voltar no caminho.

### Dois erros de vão, achados em foto

| | o que a foto mostrou |
|---|---|
| a tábua com 6dp de respiro | passava por cima da segunda linha do cartaz — «Sonic 3: O Filme» sobrevivia e «2024» ficava metade atrás da madeira |
| a plaquinha com 10dp | a fita encostava no primeiro cartaz, e papel colado num pôster não é o desenho: ele é colado na **madeira** |

O primeiro tem uma lição além do número: eu medi o vão contra a **arte** e não
contra o **cartão**. O `Cartaz` desta casa tem título e detalhe abaixo do pôster
e ainda cresce 12% ao focar — a tábua tem que ficar abaixo de tudo isso.

---

## 10.11 Quatro defeitos da locadora, relatados pelo dono

### 1. ⚠️ Voltar perdia o lugar **e** abria o menu

> «ao entrar na capa 3d tu aperta voltar e ele volta pro começo e abre o menu
> lateral, deveria só guardar a capa mas continuar no filme que tu tava»

Três causas empilhadas, e cada uma escondia a seguinte:

| | |
|---|---|
| a rolagem | o `return` que desenha o palco **descarta** a `LazyColumn`. Com ela vai a posição — e o alvo focado. Conserto: o `rememberLazyListState` subiu pra fora do `if` |
| o alvo do foco | eu apontava pro «primeiro cartaz da primeira estante», que costuma estar seis fileiras acima e **fora de composição**. `requestFocus` num nó que não existe não faz nada, e o foco cai no primeiro alvo que existe: o trilho. Conserto: guardar o **id** da caixa pega, antes de guardá-la |
| uma tecla engolida | um dos dois `voltar` sumia. **Não descobri quem engolia** — a saída foi tratar `Key.Back` no `onKeyEvent` do palco além do `BackHandler`, porque ele mora no nó focado e vê a tecla antes de qualquer despachante |

⚠️ E `voltar` passou a desfazer **um** passo: com a caixa aberta ele fecha a
caixa; fechada, guarda. Desfazer dois de uma tecla é como se perde o lugar sem
entender por quê.

Conferido: **0 pixels** de diferença entre a estante antes de pegar a caixa e
depois de guardá-la.

### 2. ⚠️ Não dava pra ver o verso

`Pose.somando` trava em ±42° por padrão, e esse teto é o curso de um **arrasto de
dedo** — no celular a caixa acompanha a mão e volta. Numa TV a seta não tem esse
limite, e travar em 42° dava à caixa um lado só.

Com `livre = true` a volta é inteira: quatro setas mostram a lombada, quinze
mostram a contracapa. A `Contracapa` — uma das oito peças da §0.1 — estava
desenhada e **inalcançável** no `:tv` até aqui.

### 3. ⚠️ `OK` não abria nada, e não havia como assistir

> «apertar ok na visão 3d não abre a capa e nem tem como eu pegar emprestado ou
> assistir o filme»

O `OK` chamava `abrirOMenu` do modelo, que põe o menu de disco **no estado** e
conta com alguém desenhando. No celular quem desenha é o `MenuDeDVD`; no `:tv`
**ninguém desenhava**. A tecla acendia um estado invisível.

É o §8b clássico — um toque que não responde — e passou porque eu liguei a
chamada sem seguir o que ela acende **até a tela**.

Agora `OK` faz o que a §5.2 diz: abre a caixa. E a caixa aberta entrega
`assistir · ver a ficha · guardar`, em `BotaoDaSala` focáveis — não no `▸
ASSISTIR` impresso na contracapa, que é de dedo e que o D-pad não alcança.

### 4. ⚠️ Faltava o fundo da prateleira

> «as prateleiras não estão parecidas, cadê o fundo das prateleiras de madeira?»

Eu tinha posto só a **tábua**, que é o lábio da frente, e chamei aquilo de
estante. Não é: uma prateleira tem fundo. Sem ele os cartazes flutuavam num vão
preto com um risco de madeira embaixo.

O painel é o do celular, dígito por dígito — os três marrons do degradê e o veio
em linhas pretas a 7% com passo de 14dp. É o veio que faz a superfície ser
madeira e não um retângulo marrom.

---

## 10.12 A T4, pela metade — e a metade que falta está nomeada

### O que entrou, e está visto na TCL

| | |
|---|---|
| **a marquise** | uma fileira de lâmpadas douradas no **teto da tela**, borda a borda. Terceira aparição do projetor (§2.1) |
| **as contagens douradas** | `60 de 8.316` — o carregado aceso, o total apagado (§2.7) |
| **a régua com a contagem** | `CONTINUAR ─────── 17`, com o número na ponta direita |

⚠️ **A marquise ganhou um compasso próprio, e a §5.1 exigiu.** No celular a
faixa de luz corre pelas lâmpadas **sem parar**, e ali está certo: é um enfeite
de 3dp no topo de um cartão, visto por alguns segundos. Numa TV a mesma fileira
atravessa 1920px no teto de uma tela que alguém deixa aberta enquanto decide —
e a §5.1 nomeia o resultado: «uma fileira de luzes piscando o tempo todo atrás
de um filme é epilepsia, não identidade».

Então na sala ela **pisca uma vez na entrada** — com os dez quadros do
`brilhoDoArco`, os mesmos do facho e do trilho — e depois respira num seno de
5,2s. O celular ficou intacto: é um parâmetro com padrão.

⚠️ Ela também **não** respeita o `overscanV`, de propósito. A régua do `Sala`
já dizia: «a margem é do conteúdo, não da tela». Uma fileira de lâmpadas na
moldura da TV é o que uma marquise de verdade faz.

### ⚠️ O separador de milhar estava errado, e mudava o sentido

A primeira versão usou `"%,d".format(n)`, que pega o `Locale` do aparelho, e o
comentário afirmava que isso era «o certo pra quem está lendo». Na TCL,
configurada em inglês, saiu **`8,316`**.

Em português a vírgula é separador **decimal**: `8,316` não se lê «oito mil», se
lê «oito vírgula três». O acervo inteiro virou um número menor que nove.

A régua da casa já tinha a resposta: este app é escrito em português do começo ao
fim. Um número que muda de forma conforme o idioma do aparelho seria a única
coisa da tela que não é. `pt-BR` explícito, e agora sai `8.316`.

### O herói, e o erro de estrutura que a TCL pegou na terceira seta

> «o **herói ocupa a primeira dobra** e mostra o que está pela metade — `faltam
> X`, barra de progresso, título em serifada. Descer leva às fileiras; o herói
> **encolhe** e vira um cabeçalho fino, liberando a tela — é o gesto que uma TV
> faz melhor que um celular.»

Está feito: arte 16:9 na primeira dobra, título em serifada, `faltam 24min` em
dourado, barra de progresso larga, e a primeira fileira de cartazes espiando por
baixo — 306dp dos 540 da sala, que é a conta da «primeira dobra».

⚠️ **A primeira versão pôs o herói como item da grade**, e o comentário afirmava
que assim «descer não faz ele sumir, faz ele ceder». **A TCL desmentiu na
terceira seta**: item de lista rola pra fora, e o herói simplesmente ia embora —
que é o desenho do celular, não o da §5.1.

O medo que me levou ao item era `contentPadding` animado numa lista preguiçosa,
que treme. Ele não se aplicava: numa `Column`, quem muda de tamanho é o **irmão**
do grid, não o padding — a grade só recebe menos altura. Agora o herói encolhe de
306dp pra 104 e **fica**, e a diferença entre «um cabeçalho que sai» e «um que
cede» é exatamente o que a §5.1 chamou de o gesto que a TV faz melhor.

⚠️ E o texto some antes da caixa terminar de encolher — em 55% do curso. Nos
últimos quadros o título ficaria espremido contra a borda e leria como defeito de
layout; o que resta da faixa é arte e luz, que é o que um cabeçalho fino deve
ser.

---

## 10.13 O dono viu a T4 na sala e cortou duas coisas que a doc pedia

### ⚠️ O teto saiu, e o herói voltou a rolar

> «que teto o que, ao rolar tudo vai sumindo normal, ignora esse teto feio aí,
> deixa como tava»

Duas peças saíram no mesmo pedido, e as duas eram implementações **fiéis** da
§5.1:

| | o que a doc pedia | o que aconteceu na sala |
|---|---|---|
| a marquise | «a marquise vira o teto da tela» | uma faixa de lâmpadas fixa no topo, que fica no caminho de uma grade que se atravessa com a seta |
| o herói | «o herói **encolhe** e vira um cabeçalho fino, liberando a tela» | um cabeçalho que se agarra ao topo em vez de sair |

⚠️ **A doc perde para o aparelho, e essa é a régua da casa.** Eu não errei a
implementação: implementei o que estava escrito, e o que estava escrito não
sobreviveu a três metros. Uma ideia boa no papel que incomoda na sala é uma ideia
ruim — e a §10 do próprio documento já previa isso pro feixe: «se for, ele
diminui, e isso é resultado, não fracasso».

O herói **fica** — ele continua sendo a primeira dobra, com a arte grande, o
`faltam` e a barra. O que saiu foi ele insistir em ficar no topo.

### ⚠️ Tudo encolheu 20%, e o cartaz encolheu 30%

> «as capas dos filmes estão muito grandes (…) para que caiba uns 6 filmes a cada
> linha ou algo assim (…) então diminua tudo 20% (menos a locadora)»

O pedido tem uma **regra** (20%) e um **alvo** (seis por fileira), e os dois não
dão o mesmo número. Valeu o alvo, porque é ele que se vê:

```
768dp úteis  ÷  6 filmes  =  128dp por vaga
128 − 16 de vão            =  112dp de cartaz   (−30%, não −20%)
```

O resto da sala levou os 20% pedidos. Conferido na TCL: **seis por fileira**.

⚠️ **A locadora ficou de fora**, como pedido — e isso obrigou uma segunda medida
(`cartazLdaEstante`). Duas medidas para a mesma coisa é dívida, e ela está no
`Sala.kt` com nome e motivo: a estante tem prateleira, plaquinha e etiqueta, e
encolher só o filme deixaria a moldura desproporcional ao que ela guarda.

### ⚠️ O crescer ao focar saiu

> «ao usar o dpad para percorrer os filmes (…) tu colocou um efeito meio estranho
> que mexe tudo na tela, tire ele, quero só ir percorrendo os filmes sem muito
> estresse ou movimentos»

O comentário do `ESCALA_DO_FOCO` defendia 1,12 com um argumento que continua
certo em teoria — o item focado passa **por cima** do vizinho em vez de empurrá-lo,
e empurrar faria a fileira andar a cada seta.

Ele errava por um degrau: eu me preocupei com o vizinho **andar** e não com o
item **inchar**. Numa grade de oitenta cartazes, cada seta faz um pular 12% e o
anterior desinchar — a três metros isso lê como a tela respirando a cada aperto.

Dá pra tirar porque o foco tinha **duas** respostas para «onde eu estou?», e uma
bastava: o anel dourado de 3dp do `Focavel` continua lá.

---

## 11. O ao vivo — a primeira coisa que a TV ganha **antes** do celular

Não é leva do redesenho: é uma feature que faltava nos dois clientes, e que o
dono pediu pra nascer aqui. O argumento é bom e vale registrar, porque é o
primeiro caso neste projeto em que a TV lidera:

> Um canal ao vivo é uma coisa de **sala**. Zapear com o polegar num ônibus é
> percorrer uma lista; zapear com o controle no sofá **é televisão** — e a
> diferença não é de tamanho de tela, é de gesto.

Foi construída a partir da versão da web (`web/src/AoVivo.tsx`, 1.493 linhas),
que já tinha o desenho e os endpoints resolvidos.

### O que ficou onde

| | |
|---|---|
| `:core` | os modelos (`CanalNoAr`, `Guia`, `GradeDoOdeon`, `CanalAberto`), quatro chamadas de API, e o `ModeloAoVivo` |
| `:tv` | a tela, o destino no trilho, e o ícone da antena |

⚠️ Os modelos foram pro `:core` mesmo sendo a TV quem os usa, pela régua de
sempre: **eles não desenham**. Quando o celular quiser o ao vivo, acha tudo
pronto — que é o oposto do que aconteceria pondo-os no `:tv` «porque é lá que se
usa».

### ⚠️ O relógio é o do **servidor**, e isso governa o modelo inteiro

A `Guia` traz `agora` junto com os programas, e o comentário da web diz por quê:
«a agulha do "agora" tem que ser desenhada contra o mesmo relógio que produziu a
grade».

Numa TV isso é mais que rigor. O relógio de uma TCL sai de fábrica errado e só
acerta se alguém ligar a hora automática — e uma linha meia hora fora do lugar
não parece um relógio errado, parece uma **grade** errada.

O modelo guarda o `agora` do servidor e **anda com ele**, um segundo por segundo,
sem consultar o da TV. A grade se repede de minuto em minuto e cada resposta
reancora.

### ⚠️ Sintonizar entra no filme **onde a transmissão está**

É a metáfora inteira funcionando: ligar a TV no meio de um filme te dá o meio do
filme. O `ProgramaDoOdeon` traz `work_id` e `media_file_id`, e o deslocamento é
`agora − comeca` — então o player já existente recebe o filme e o ponto, sem
precisar de caminho novo.

⚠️ **Isso só vale pros canais que o Odeon programa.** Os de M3U externo têm
playlist de HLS e não obra, e o player da sala hoje só sabe abrir obra+arquivo.
Eles aparecem na sintonia e **não ganham botão** (§53) — a tela diz «este canal
ainda não toca na sala» em vez de oferecer um botão que não leva a lugar nenhum.

### O que **não** foi feito, e está nomeado

| | |
|---|---|
| tocar canal de M3U | precisa de um caminho de HLS cru no player — ver acima |
| a pílula `fontes` e os lembretes | os endpoints existem no `:core`, a tela não os usa |

### A agulha e o `0–9` — vistos na TCL, e a foto cobrou um defeito

Os dois foram conferidos com o controle. E o aparelho fez o que costuma fazer:
achou uma colisão que o build não tinha como achar.

| | conferido |
|---|---|
| a agulha | a linha vermelha desce da faixa `AGORA` e corta os canais, caindo entre `09:00` e `10:00` — coerente com o programa em curso, que começou 09:20 |
| o `0–9` | `1` `0` `3` → o painel dourado apareceu no canto com `103`, e 2,5s depois ele sumiu **e a arte do herói trocou** pro canal 103 |

⚠️ **O controle da TCL manda `Key.One`.** Era a pergunta em aberto — o mapa cobre
`Key.One` e `Key.NumPad1` porque eu não sabia qual, e agora sei qual dos dois é.
O outro fica: cobrir os dois não custa nada e o próximo controle pode divergir.

### ⚠️ O defeito que a foto achou: `09:0AGORA`

O badge e os rótulos de hora estavam na mesma linha. Na TCL a agulha calhou de
cair perto das 09:00 e o `AGORA` desenhou **por cima** do rótulo: o que se lia era
`09:0AGORA`.

⚠️ E o mais instrutivo: **a web tem o mesmo defeito**, e ninguém tinha visto. Lá o
badge e as horas também dividem a linha; no screenshot que o dono mandou a agulha
estava longe de uma hora cheia, então não colidiu. Um defeito que só aparece
quando o relógio calha de estar num certo lugar é um defeito que a foto encontra
por sorte — e desta vez a sorte veio pro lado certo.

Conserto: uma faixa reservada só pro badge, acima da régua. Ela não é margem — é
o que impede a colisão em qualquer horário, em vez de depender de onde o relógio
está.

#### A agulha, como está escrita

Ela é **uma só** pra todos os canais, e é por causa dela que a grade é desenhada
em minutos em vez de `weight` por programa: com `weight` cada faixa ganharia uma
escala de tempo própria, as 08:00 de um canal cairiam num x diferente das 08:00
do outro, e uma linha vertical cortando as duas seria uma mentira desenhada com
régua.

Ela é vermelha, e é o segundo vermelho de propósito desta casa (o outro é o «NO
AR»). Aqui não é erro: é a convenção que toda grade de TV já ensinou — e o
dourado da casa a deixaria indistinguível do bloco do programa em curso, que já é
dourado.

Junto entrou a **régua de horas**, sem a qual a agulha diz «aqui» mas não diz
«que horas são aqui».

#### O `0–9`, como está escrito

⚠️ **O que se digita aparece na tela**, grande, no canto superior direito — no
lugar do relógio, que é onde uma televisão mostra isso desde sempre. Sem esse
retorno, digitar `101` vira adivinhação: aperta-se `1`, nada acontece, e não dá
pra saber se a tecla falhou ou se o app está esperando o resto. É o §8b na forma
mais literal.

O buffer sintoniza **1,2s depois da última tecla** e o contador reinicia a cada
dígito — é o que deixa digitar `101` sem o `1` sintonizar sozinho no caminho.

### 11.5 A densidade, os canais que faltavam, e o ▼ que abria o menu

Três relatos do dono numa mensagem só, vistos na TCL: «diminui uns 40% da escala
de tudo do ao vivo», «cadê os outros canais fora os odeons? videoteca, canal da
Disney», «se tem eu não consigo ver pq ir pra baixo faz aparecer o menu lateral».

#### Os canais de fora — o dado chegava, ninguém olhava

⚠️ **O pior dos três, e o mais silencioso.** Os canais externos vinham de
`/api/live/channels` e a programação deles de `/api/live/guide`; os dois já eram
buscados, os dois já estavam no estado desde o primeiro dia, e a tela desenhava
só `gradeDoOdeon`. Sem erro, sem tela vazia, sem nada no `logcat` — só um pedaço
do mundo que o app decidiu não mostrar sem avisar. Um build verde esconde isto
perfeitamente, e foi preciso alguém olhar a TV e sentir falta da Videoteca.

Depois do conserto a grade mostra **15 canais**: os três do Odeon, e `1 Tela
Quente`, `2 Sessão Seriado`, `3 Videoteca`, `4 Clipe Show`, `10 Sessão Impacto`,
`11 Sessão Comédia`, `12 Sessão Aventura`, `13 Sessão Drama`, `14 Sessão
Suspense`, `15 Sessão Futuro`.

As duas fontes são **achatadas num tipo comum** (`FaixaDaGrade` / `BlocoDaGrade`)
antes de desenhar. Não é arrumação: a régua tem de continuar sendo uma só, e
deixar cada fonte desenhar no seu laço faria a agulha vertical depender de qual
laço desenhou a linha que ela corta.

#### O ▼ que abria o menu — não era o dono errando o botão

A linha do tempo era **desenho puro**: nenhum nó focável nela. Descendo da
sintonia, o foco procurava vizinho abaixo, não achava nada, e o único focável em
qualquer direção era o trilho à esquerda. O ▼ virava «abre o menu».

Conserto: cada faixa da grade é focável, e diz o foco pelo próprio fundo — sem o
halo dourado do `Focavel`, que numa faixa de 26dp cobriria a faixa inteira e é o
tipo de movimento que o dono já pediu pra tirar.

#### A escala — e por que ela mora nesta tela

O ao vivo é a única tela desta casa que **quer** densidade: uma grade existe pra
comparar canais de relance, e comparar exige ver muitos ao mesmo tempo. Um cartaz
exige o contrário. Por isso `RotuloMiudo`/`CorpoMiudo` moram no arquivo do ao
vivo e não em `TipoDaSala` — mexer lá encolheria a biblioteca, a locadora e o
player junto, e o player, palavra do dono, «está perfeito».

⚠️ O `letterSpacing` caiu **mais** que o corpo (0.28em → 0.12em). Em `em` o
espaço acompanha a letra, então encolher só o corpo preservaria a proporção de
ar; e num cartão de 139dp o que estourava era a largura, não a altura —
`TELA QUENTE` virava `TELA Q…` por causa do espaçamento.

#### Quatro defeitos que só a foto na TV achou

1. **A hora cortada ao meio** no cartão focado. O `.clip(forma)` do `Focavel`
   recorta o conteúdo na borda, e o texto encostado no pé sobrava pela descida da
   fonte. O corte já existia sem foco; o foco só desenhou uma linha exatamente
   onde ele acontece.
2. **`09:20` virando `9:20`** no cartão focado. A borda de 3dp é desenhada por
   dentro dos limites e pintava em cima do primeiro dígito. Um número com um
   dígito a menos não é um número apertado — é outro número.
3. **`12:` picado** nos blocos estreitos. Num programa de meia hora a faixa tem
   48dp. Bloco estreito passou a não escrever hora: a posição na régua já diz
   quando, e a redundância picada é pior que a redundância ausente.
4. **Bloco que começa antes das 09:00 desenhado largo demais.** Encostar o começo
   em zero sem encurtar a largura desenhava `Capitão América` das 07:43 terminando
   77 minutos depois do que devia. Passava despercebido porque o bloco seguinte
   era desenhado por cima — o último de cada faixa denunciaria. O fim é a âncora;
   a largura é o que sobra dele.

**Conferido na TCL**, com o controle: os 15 canais na sintonia e na grade, o ▼
caindo em `Odeon 1` (dourado) sem abrir o trilho, `Capitão América 07:43`
terminando exatamente onde `Anjos da Noite 10:11` começa, e `O Senhor dos Anéis
07:37` em `11:32`.

### 11.6 «O app morreu, mesmo filme não mudou» — o canal que não era canal

Relatado pelo dono com uma foto da tela de erro do player, depois de deixar um
canal rodando. Duas coisas no mesmo relato, e as duas reproduzidas na TCL.

#### O que realmente acontecia

O filme chega ao fim. A tela fica **preta**, o cronômetro marca `faltam 0:00`, o
botão vira play. Nada mais acontece. Da poltrona isso é indistinguível de um app
travado — daí «o app morreu». E apertar play ali **recomeça o mesmo filme do
zero**, que é a outra metade: «mesmo filme não mudou».

⚠️ Nenhum dos dois é travamento. É um player que recebeu **um arquivo** e nunca
soube que estava num canal: `setMediaItem` de item único, sem `STATE_ENDED`, sem
`onMediaItemTransition`. Terminar não era um acontecimento nesta tela — era a
ausência de qualquer um.

⚠️ **O comentário do botão «sintonizar» já afirmava o contrário.** Ele dizia que
entrar no meio de um filme era «a metáfora inteira funcionando, é o que uma
emissora faz». Descrevia o primeiro minuto e afirmava o resto — escrito antes de
alguém ver um programa terminar. É exatamente o erro que o §0.1 existe pra
impedir, cometido de novo.

#### A grade real, medida

Duas medidas que mudaram o desenho do conserto:

- **As faixas batem com a duração dos arquivos.** `Batman` ocupa 08:45–11:30
  (165 min) para 2:44:32 de arquivo; `Sr. Ninguém` ocupa 10:59–13:35 (156 min)
  para 2:35:36. Não há folga sistemática.
- **Entre uma faixa e a seguinte há um vão de ~4 minutos.** `Planeta do Tesouro`
  acaba 10:55 e `Sr. Ninguém` só começa 10:59; `Batman` acaba 11:30 e `A Hora do
  Rush` entra 11:34. O padrão se repete em todos os canais do Odeon.

Ou seja: **o fim de todo filme cai num vão.** Um conserto que desistisse no
primeiro «nada no ar» quebraria o canal a cada programa.

#### O conserto

O `canalId` passa a viajar com o filme (`Onde.Filme`). Quando o arquivo acaba, o
app pergunta **ao servidor** o que está no ar naquele canal agora — e não «qual
era o próximo quando eu liguei», porque o app pode ter passado horas no mesmo
filme e a grade carregada lá atrás é história.

`QuadroNoAr` e `emCartaz` desceram pro `:core` por causa disso: a resposta do
fim do filme tem de ser **a mesma** que a grade desenha, senão a tela e o que
toca discordam sobre o que é o canal.

Três guardas, cada uma paga por uma observação:

1. **Espera o vão.** Repergunta a cada 30s por até seis minutos, e desiste se
   nesse tempo a pessoa abriu outra coisa — sintonizar por cima seria arrancá-la
   da tela em que está.
2. **Enquanto espera, mostra a tela do ao vivo** com o canal selecionado, não uma
   tela preta. Quatro minutos de preto seriam «o app morreu» de novo. Lá estão o
   canal, a agulha e a hora em que o próximo começa.
3. **Nunca re-sintoniza o mesmo arquivo.** Se a faixa for maior que o arquivo,
   perguntar «o que está passando?» devolve o filme que acabou de terminar;
   re-sintonizá-lo num ponto além do fim o faria terminar na hora, girando
   sozinho. Trocar uma tela preta parada por uma piscando não é conserto.

#### Conferido na TCL

Reproduzido antes: fim de arquivo → tela preta; play → filme do zero.

**A guarda do mesmo arquivo**, com o `Sr. Ninguém` levado ao último quadro: o
`STATE_ENDED` disparou, o log registrou `volta 0 no canal odeon-1 -> Sr. Ninguém
mesmo=true`, o laço não aconteceu, e a tela virou o ao vivo com o Odeon 1
selecionado e o `sintonizar` à mão — não o preto.

**A virada de verdade**, esperando o `Batman` acabar sozinho no Corujão, sem
tocar no controle:

```
11:30:08  tocando: Batman: O Cavaleiro das Trevas Ressurge
11:30:39  tocando: —    volta 0 no canal odeon-corujao -> vão
11:31:09  …             volta 1 -> vão
   ⋮                       ⋮   (o vão de quatro minutos)
11:34:12  …             volta 7 -> vão
11:34:43  …             volta 8 -> A Hora do Rush mesmo=false
```

Bate com a grade no minuto: `Batman` até 11:30, `A Hora do Rush` a partir de
11:34. As oito voltas são exatamente o vão, atravessado em vez de desistido.

**E o filme seguinte de fato tocou.** Conferido depois, na tela: `A Hora do
Rush` no player, botão em pausa (ou seja, rodando), `0:50` de posição. Os
cinquenta segundos são a prova fina do deslocamento — a virada aconteceu às
11:34:43 e o programa tinha começado 11:34, então o ponto de entrada era ~43s.
Sintonizar «onde a transmissão está» acertou por questão de segundos.

#### ⚠️ Uma consequência que apareceu na conferência — e um conserto que errou o alvo antes de acertar

O app foi morto e reaberto entre a virada e a conferência, e voltou tocando `A
Hora do Rush` **do ponto salvo** — 0:50 — com o relógio já em 12:14. Ou seja:
quarenta minutos atrás da transmissão.

Isso é o comportamento normal de «continuar de onde parou», e num canal ele está
errado: ao vivo que atrasa quarenta minutos não é ao vivo. Retomar um canal
deveria re-sincronizar com o agora, não com a última posição.

**O primeiro conserto mirou o mecanismo errado**, e vale registrar porque o erro
é do tipo que este documento existe pra evitar.

Eu supus que o app tinha sido **morto e restaurado**, e escrevi a regra em cima
disso: um carimbo de criação em `Onde.Filme` e um `LaunchedEffect` que
re-sintonizava quando a entrada tivesse minutos nas costas. Parecia certo.

O aparelho desmentiu em dois passos:

1. `onde` é um `remember` simples, **não** `rememberSaveable` — o app nunca
   restaura essa navegação. Se o processo morresse, ele voltaria pra casa, não
   pro filme.
2. A aritmética fechou o caso: `A Hora do Rush` entrou em 43s e foi encontrado em
   0:50. Tocou **sete segundos**. Não houve restauração nenhuma — a TV apagou o
   painel, a reprodução pausou junto, e quarenta minutos depois continuou de onde
   parou.

O `LaunchedEffect` nunca teria rodado nesse caminho: a composição não é recriada
quando a TV dorme. Um conserto verde, com teste passando, que não conserta nada —
e que só não virou dívida porque a medida veio antes do commit.

**O conserto certo escuta o ciclo de vida**: `ON_RESUME` no player devolve a
posição do filme, e quem sabe que há um canal atrás decide. Programa diferente
troca sempre; o mesmo programa só se a transmissão já passou dois minutos à
frente — que é a folga que separa isto de brigar com o botão de pausa. Pausar um
canal e voltar logo é coisa que se faz; dois minutos de atraso já não são uma
pausa, são um cochilo da TV.

#### O `addObserver` que dispara sozinho

⚠️ **`addObserver` despacha na hora os eventos até o estado atual.** Registrar o
observador com a tela já em pé produz um `ON_RESUME` **sintético** imediato — e
como cada re-sintonia recria o player, e o player re-registra o observador, isso
vira laço.

Foi visto acontecer, três voltas numa única retomada:

```
13:00:42  voltou ao frente: pos=0s     → acordou … aovivo=7266s → re-sintoniza
13:00:43  voltou ao frente: pos=0s     → acordou … aovivo=7267s → re-sintoniza
13:00:43  voltou ao frente: pos=7267s  → recusa
```

O `pos=0s` das duas primeiras é a segunda cara do mesmo problema: o player
recém-criado ainda não tem posição, e com zero a folga de dois minutos nunca
protegeria ninguém — toda volta pareceria um atraso enorme.

Conserto: só vale o `ON_RESUME` que vem **depois** de um `ON_PAUSE`. O disparo
sintético não tem pausa antes dele, então não conta; e a posição lida passa a ser
a real, do player que estava tocando.

#### Conferido na TCL

O painel apagando derruba a rede da TCL junto (`KEYCODE_SLEEP` mata a interface),
então o atraso foi produzido de outro jeito: voltando no rolo do filme, que é o
mesmo estado — «você está atrás da transmissão».

| | |
|---|---|
| com atraso de 16 min | `pos=6512s` contra `aovivo=7462s` → **uma** volta, e a posição saltou pra 2:04:32, que é o ao vivo às 13:04 |
| sem atraso (10 s) | `pos=7573s` contra `aovivo=7583s` → recusou, nenhuma reconstrução |

⚠️ E uma medida que mudou o entendimento: **o app não pausa no fundo.** Mandado
pro lançador, ele tocou 236s em 239s de relógio. O atraso não nasce de sair do
app — nasce do painel apagando. São coisas diferentes e a segunda é a que dói.

#### ⚠️ O logcat da TCL rola rápido demais pra ler depois

Duas rodadas de teste foram perdidas achando que o código não tinha disparado,
quando o que faltava era a linha — a TCL cospe um volume enorme de `P/Quality` e
evicta o buffer em segundos. `logcat -d` depois do fato não serve aqui; a captura
tem de estar aberta **antes**.

E o log tem de vir **antes** das saídas antecipadas: com ele depois dos `return`,
«não disparou» e «disparou e desistiu» produzem o mesmo silêncio.

#### O que fica em aberto

⚠️ **O `o servidor não entregou o arquivo` da foto não foi reproduzido.** É um
4xx num stream direto. Pode ter sido consequência de ficar parado no fim por
muito tempo, mas isso é hipótese — não foi visto acontecer, e não vira afirmação.

⚠️ **Fora de um canal, o fim do arquivo continua sendo tela preta.** O mesmo
acontece num filme do acervo. O que fazer ali — voltar pra ficha? marcar como
visto? — é outra decisão, e não a que foi pedida. Fica anotado em vez de
resolvido às escondidas.

⚠️ **No trilho, ◀ sai do app** pro lançador do sistema. Descoberto por acidente
ao dirigir a TV por `adb` — duas vezes. Pode ser o que se quer; pode não ser.

## 12. T5 — o perfil

### 12.1 Os dois erros de sentido

A §5.6 apontava dois, e os dois eram de **significado**, não de brilho.

**Só as abertas apareciam.** A tela fazia `conquistas.filter { it.aberta }`. O
§10.5 esconde o **número** de uma conquista trancada — ponto que não se tem é
promessa —, mas não a conquista. Sem as trancadas a tela vira um troféu de coisas
já feitas, e some justamente a metade que dá motivo pra fazer a próxima. Agora
mostra as 80, agrupadas nas seis camadas na ordem de dificuldade percebida, com
`9 de 12` em cada uma.

**Eram cartões, viraram marcações.** Uma fileira de cartões de 280dp diz «cada
uma destas é um objeto importante»; uma lista com `✓` e `☐` diz «isto é uma
lista, e faltam estas». A segunda é a verdade. Em três colunas, porque a TV tem
largura que o celular não tem — e uma coluna só obrigaria a rolar oitenta linhas
com o D-pad, que é o gesto mais caro desta sala.

**O placar:** a minha linha virou **cartão elevado**. Dourado nesta casa quer
dizer «em foco» ou «destaque» — é a cor do anel, do botão principal, do programa
no ar. Usá-lo pra dizer «este sou eu» empresta um significado que ele já tem, e
num placar de seis linhas o olho lê dourado como «primeiro colocado». Elevação
diz outra coisa e diz certo: esta linha está mais perto de você.

### 12.2 A rolagem que faltava — e o mesmo defeito pela terceira vez

⚠️ Numa TV **o foco é quem rola**. A `LazyColumn` só anda quando o foco entra num
item fora da vista, o que funciona na biblioteca (todo item é um cartaz focável) e
falha inteiro numa tela que é texto.

Sem nada focável abaixo, o ▼ procurava vizinho, não achava, e ia pro trilho: o
placar da casa era **inalcançável**. É literalmente o defeito que o dono relatou
no ao vivo — «ir pra baixo faz aparecer o menu lateral» — aparecendo numa terceira
tela.

Lá o conserto foi tornar cada faixa focável, porque cada faixa **é** uma coisa.
Aqui não: oitenta conquistas não são oitenta destinos, são um texto comprido.
Então nasceu o `Modifier.rolavelComOControle`, no `tv/ui/Rolagem.kt`: a coluna
inteira ganha foco e as setas rolam a página.

⚠️ **Nas pontas o evento não é consumido** — no topo o ▲ tem de escapar pra quem
está acima, no fim o ▼ também. Consumir sempre prenderia o foco num poço, e do
pior tipo: parece que o controle parou de funcionar.

### 12.3 Quatro defeitos que só a TV mostrou

1. **A capa não esmaecia o bastante.** O degradê ia de 0,45 a 1,0 ao longo de
   420dp, e a foto continuava visível debaixo da lista: `Rebobine antes de
   devolver` disputava com um rosto. Capa escolhida pela pessoa não tem contraste
   previsível — não há foto «segura» que se possa presumir, então quem cede é a
   foto.
2. **`TipoDaSala.rotulo` na descrição.** Os `0.28em` que fazem `CONQUISTAS`
   respirar em caixa alta esparramavam `Termine uma obra` em três linhas numa
   coluna estreita. Espaçamento de rótulo é pra rótulo; descrição é texto, e
   texto se lê junto.
3. **Os pontos roubavam largura do nome.** Lado a lado numa coluna de três,
   `Sessão dupla e meia` quebrava em duas linhas enquanto a vizinha trancada, sem
   pontos, cabia numa — duas conquistas iguais com alturas diferentes por causa
   de um número. Empilhados, some.
4. **A capa ficava parada enquanto a lista rolava**, pondo um rosto atrás das
   «impossíveis», a meia tela de onde deveria estar. Paralaxe ali não era
   escolha, era descuido: a capa virou o primeiro **item** e sobe junto com o
   nome, que é o que «no topo» quer dizer.

E o botão de sair, que é sobreposição por necessidade (irmão da coluna, não
filho), **some com o cabeçalho**: parado, ele tapava `Sabe os diálogos` no meio
das médias. É a ação daquele bloco — quando o nome sai da tela, a pergunta que ele
responde saiu junto.

### 12.4 Uma nota de bancada

⚠️ Este arquivo levou quatro rodadas de cirurgia de chave malfeita — inclusive uma
que apagou a assinatura da própria função, e como o `:tv` ainda **não está
commitado** não havia cópia no git pra restaurar. Reconstruído à mão.

A lição é velha e continua verdadeira: em Kotlin, mexer por índice de linha e por
`replace` de bloco é apostar. Quando a estrutura importa, ler a região inteira
primeiro custa menos que consertar depois.

**Conferido na TCL:** as 80 conquistas nas seis camadas, `✓`/`☐`, pontos só nas
abertas, três colunas; a rolagem chegando ao placar; `sam` em cartão elevado; a
capa subindo com o cabeçalho; e o botão de sair sumindo ao rolar.

## 13. T5 — o guia

### 13.1 A página dupla

Uma revista aberta é uma página dupla, e agora é isso que a tela é. À esquerda o
letreiro, o tema, o prazo e a matéria; à direita a seleção e o que está em cartaz.

⚠️ **A coluna de leitura tem largura travada, e não é estética.** Uma linha de
texto atravessando 1920px é ilegível — o olho perde a volta e relê a mesma linha.
É defeito conhecido de tipografia, e esta casa já o pagou uma vez na ficha do
filme. Metade da tela é a trava, e é o que transforma «um parágrafo largo» em
coluna de revista.

⚠️ **O ensaio sai em serifada.** Nesta casa a serifada aparece quando a coisa
escrita **é** o assunto — o título do herói, o nome na cortina, o nome no perfil.
Um ensaio é matéria, não interface: é o texto mais «assunto» que esta tela tem.

Três correções de vocabulário, todas de sentido:

- **`até segunda`, não `vira segunda`.** «Vira» descreve o que o servidor faz;
  «até» descreve o que sobra pra quem está lendo — e é a pergunta real de quem
  olha a revista.
- **`ESCRITO POR X` é versalete dourado, não pílula.** Pílula é rótulo clicável
  ou etiqueta de dado — gênero, país, década. Crédito de autoria não é nenhum dos
  dois: é a assinatura no pé da matéria, e revista nenhuma põe o nome do autor
  dentro de uma cápsula. Ela mora **dentro** do bloco do ensaio e some com ele:
  um `ESCRITO POR` órfão diria que alguém escreveu algo que não está aqui.
- **`FlowRow` na seleção, não `LazyRow`.** A `LazyRow` quer largura infinita, e
  esta metade de página é finita por decisão. O `FlowRow` deixa a seleção quebrar
  em duas fileiras dentro da página — que é o que uma revista faz com fotos.

Os eixos ficam abaixo da dobra, como fileiras de fichas, e continuam **não
focáveis**: não existe a tela que mostraria «tudo de Kubrick», e fingir que
existe seria pior que a ausência (§53).

### 13.2 `1 vistas`

⚠️ Visto na TCL, na ficha do Nolan: `9 obras · 1 vistas`.

É um erro pequeno e é o tipo que fica, porque quem escreveu leu `"$n vistas"`
como um **molde** e não como uma frase — e o molde só está errado num caso, o do
1, que é justamente o mais comum numa casa em que se acabou de ver o primeiro
filme de alguém.

**Conferido na TCL:** o letreiro `DIRETOR DA SEMANA`, `湯山邦彦` em serifada,
`até segunda`, o ensaio em coluna de leitura, a seleção quebrando em duas
fileiras à direita, `ESCRITO POR LLAMA-3.3-70B-VERSATILE` em versalete dourado,
`EM CARTAZ ESTA SEMANA · Pokémon: O Filme · 0 de 1`, os cinco eixos abaixo da
dobra, e `1 vista` no singular.

⚠️ A rolagem por D-pad do perfil (`rolavelComOControle`) foi reusada aqui pelo
mesmo motivo: as fichas de eixo não são focáveis, então sem ela o ▼ escaparia pro
trilho e metade da revista seria inalcançável. Terceira tela com o mesmo defeito,
segunda com o mesmo conserto.

## 14. T6 — o mural e o para você

### 14.1 O mural já estava certo

Conferido na TCL antes de mexer, e não precisou de mexida: arte à esquerda, frase
à direita, `há 3 dias` na ponta, `você` em dourado e o resto em branco, `3 de 7
vozes` embaixo do título, e o filtro que **não desenha** o que não tem frase —
servidor mais novo que o app manda tipos desconhecidos, e o silêncio é a resposta
certa.

⚠️ Ele também já rola sozinho, porque cada acontecimento é `Focavel`. É a mesma
razão pela qual a biblioteca nunca precisou do `rolavelComOControle`: onde todo
item é um destino, o foco basta.

### 14.2 O para você — o motivo saiu da nota de rodapé

**O que estava errado:** o `porque` de cada recomendação morava numa seção «por
que estes» lá embaixo, com os quatro primeiros títulos repetidos e o motivo ao
lado. Isso obriga a pessoa a casar duas listas de cabeça: ver o cartaz aqui,
procurar o nome ali, ler o motivo.

«A recomendação com motivo é a tese do projeto numa tela só» (§5 da espec) — e
uma tese que exige dois olhares não está numa tela só. O motivo é **do cartaz**,
então passou a morar nele. O ano cedeu o lugar: dos dois, o motivo é o que esta
tela existe pra dizer, e o ano está na ficha, a um `OK` de distância.

**As lâmpadas da marquise** entraram, como na biblioteca — a R6 do celular. Não é
enfeite: é o que diz «isto aqui é a entrada do cinema, não uma lista de
arquivos». E o filtro de tempo ficou **no alto**, porque numa TV o que está
embaixo custa apertos, e «tenho uma hora e meia» é a pergunta de quem senta às
onze da noite. Pergunta que se faz primeiro se responde primeiro.

### 14.3 `você costuma`

⚠️ Visto na TCL na primeira tentativa: o motivo entrou junto do cartaz e saiu
**picado** — `você costuma`, e nada mais. Motivo cortado é pior que motivo
ausente: não diz nada e ainda ocupa o lugar de quem diria.

A causa era `maxLines = 1` no detalhe do `Cartaz`, que servia enquanto o detalhe
era um dado curto (`1998 · 湯山邦彦`). Com duas linhas ainda cortava (`você
costuma terminar Can…`); com três, cabe: `você costuma terminar Canadá (79%)`,
`você terminou 2 obras com Burn Gorman`.

⚠️ E virou **parâmetro**, não regra nova. Na biblioteca e na locadora o detalhe
continua em uma linha de propósito: deixá-lo crescer faria cartazes vizinhos terem
alturas diferentes conforme o tamanho do nome do diretor, e fileira de cartaz é
grade — grade quer altura igual. Quem sabe qual é o caso é quem chama.

**Conferido na TCL:** as lâmpadas acesas, `para você` em serifada, as três pílulas
de tempo focáveis no alto com `qualquer` ligada, e o motivo inteiro embaixo de
cada cartaz.

## 15. Os canais de fora — o caminho existe, a porta ainda responde 401

O «este canal ainda não toca na sala» saiu da tela. O que estava por trás dele
eram **três** coisas, e nenhuma era a que o recado sugeria.

### 15.1 Faltava a dependência, não o código

⚠️ O `:tv` nasceu **sem `media3-exoplayer-hls`**. O `:app` sempre teve. Metade da
sintonia ficou decorativa por falta de uma linha no `build.gradle.kts` — e
ninguém notou porque os canais do Odeon tocam pelo caminho direto, com arquivo.

### 15.2 A tela é separada, e é decisão

O `TelaDoPlayerDaTv` é a tela de assistir um **filme**: negocia plano de
transcodificação, marca posição, monta a tira de miniaturas, escolhe legenda e
faixa de áudio, sabe voltar dez segundos. Um canal de M3U externo não tem obra,
arquivo, duração nem onde parar — ele já está no meio quando você chega e
continua depois que você sai.

Enfiar isso no `ModeloDoPlayer` significaria costurar «e se não houver obra» em
cada um daqueles caminhos, e cada costura dessas é um lugar onde o filme normal
pode quebrar depois. Então nasceu o `TelaDoCanalAoVivoDaTv`: playlist, tarja com
o nome, e `voltar`.

⚠️ **Sem barra de progresso, e é o §24.** Uma barra precisa de um fim; uma
transmissão não tem. Vazia diria «isto está no começo», que é falso; cheia diria
«acabou», que é pior.

### 15.3 `playlist_url` é um caminho, não uma URL

Primeira tentativa na TCL:

```
FileNotFoundException: /api/hls/5793acb4-…/index.m3u8:
  open failed: ENOENT (No such file or directory)
    at androidx.media3.datasource.FileDataSource.openLocalFile
```

⚠️ `FileDataSource` na pilha é a assinatura do defeito: sem esquema e sem host,
`/api/hls/…` não é «relativo ao servidor» para o ExoPlayer — é um **caminho de
arquivo local**, e ele foi procurar no cartão de memória.

A `urlDeMidia` do `:core` resolve base e token de uma vez, e é a mesma que o
player de filme usa pra sua própria playlist (`ModeloDoPlayer:814`).

### 15.4 O 401, e a resposta escrita na própria web

Com a URL absoluta o pedido chega ao servidor e volta:

```
HttpDataSource$InvalidResponseCodeException: Response code: 401
```

Eu tinha registrado isto como pergunta pro servidor. Estava errado: a resposta já
estava escrita nesta casa, no `web/src/hls.ts`, num comentário que alguém deixou
depois de pagar o mesmo defeito no navegador.

> **O token vai por header, não por query.** O `?token=` da URL da playlist NÃO
> chega nos segmentos: o ffmpeg escreve os nomes de forma relativa
> (`seg00000.ts`), e resolução relativa descarta a query string. O segmento saía
> sem credencial, o servidor devolvia 401 (…). O `xhrSetup` vale pra todo pedido,
> então o header resolve playlist e segmento de uma vez.

A web manda **os dois**: token de mídia na query e `Authorization: Bearer` da
sessão em cada pedido do hls.js. O ExoPlayer estava mandando só o primeiro.

⚠️ O equivalente do `xhrSetup` aqui é
`DefaultHttpDataSource.Factory().setDefaultRequestProperties(...)`: ele vale pra
todo pedido daquela fonte, playlist e segmento.

⚠️ **A lição não é sobre HLS.** A resposta estava a um `grep` de distância, num
comentário escrito por quem já tinha errado isso antes. Eu preferi declarar
pendência a procurar. O projeto inteiro é construído sobre a ideia de que o
comentário registra o porquê — e o registro só vale se alguém for ler.

**Conferido na TCL:** o `Sessão Seriado` — canal de M3U externo, via ErsatzTV —
tocando com imagem, a tarja `NO AR`, o nome do canal e a dica de sair.

### 15.5 Uma pergunta que fica

⚠️ O player de **filme** monta a URL de HLS pela mesma `urlDeMidia` (query
token) e não declara cabeçalho nenhum (`ModeloDoPlayer:814`). Se o argumento do
`hls.ts` vale igual lá — e não há por que não valer, é o mesmo `ffmpeg` —, então
filme em transcodificação falha no primeiro segmento pelo mesmo motivo, e ninguém
notou porque nesta casa quase tudo toca **direto**.

Não mexi: o player de filme monta o `ExoPlayer` por outro caminho (sessão de
mídia), e trocar a fonte dele sem ter um caso que reproduza seria consertar no
escuro. Fica anotado como o próximo lugar a olhar.

## 16. O trilho — a grossura, a caixa e a sobreposição que não sobreviveu

«Hoje ele tá feio pra caralho (…) ocupa um puta espaço que a maior parte é
inútil, principalmente de grossura.» Estava certo, e a conta é curta.

### 16.1 Os 96dp eram 44 de insígnia e 52 de padding

`horizontal = 20dp` no `Focavel` mais `11dp` (ou `14dp`) no `Row`, dos dois
lados. O conteúdo ainda pedia `overscanH = 48dp` por cima, então nada aparecia
antes de **144dp** numa tela de 960.

Agora: ícone de 20dp num vão de **40dp**, e o conteúdo começa em 88dp. Medido com
`uiautomator`: o primeiro cartaz saiu de x=280px para **x=176px**.

⚠️ **A caixa de foco saiu.** Cada item ganhava `fundoElevado` arredondado, e numa
coluna isso lê como grade de botões. Quem marca o **escolhido** é o facho — a luz
do projetor, um item por vez; quem marca o **focado** é o ícone dourado. Duas
coisas diferentes pediam dois sinais diferentes, e estavam usando o mesmo.

A insígnia caiu de 44dp pra 26dp: o nível deixou de ser um número dentro do selo
e virou só a cor do anel. O número volta quando o painel abre, que é onde há
espaço pra ele significar alguma coisa.

### 16.2 ⚠️ A sobreposição não sobreviveu ao D-pad

A maquete previa o painel **por cima** do conteúdo, sem empurrar nada. Foi
construído, e desenhou exatamente como se queria — e **parou de colapsar**: o ▶
não devolvia o foco, e o menu ficava aberto pra sempre.

A causa é geométrica e não tem conserto barato. A busca direcional do Compose
procura um alvo *naquela direção*; com o painel de 240dp por cima, tudo o que
estava à direita estava **debaixo** dele. Foco de D-pad não entende profundidade
— entende posição.

Por isso TV empurra em vez de sobrepor, e não é falta de imaginação: é a única
topologia em que «à direita» quer dizer a mesma coisa pro olho e pro foco.

O que sobrou da ideia é o que importava — o vão de 40dp. O empurrão só acontece
enquanto o menu está aberto, que é o instante em que a pessoa está olhando pro
menu e não pra grade.

### 16.3 Dois enganos meus, no caminho

⚠️ **«A luz é dez vezes menor que no celular»** — eu disse isso olhando o
`desenhaOCone(raio = size.width * 5.5f)` da lente de 3dp, e **não vi** que o
`FeixeDaCabine` desenha o cone de raio igual à largura da tela por trás. A luz de
sala estava lá; o que eu comparei era o pontinho. Afirmação feita a partir de um
número lido sem procurar o outro.

⚠️ **«O trilho parou de colapsar»** — conclusão tirada de duas fotos pareadas que
saíram idênticas. Medido depois com `uiautomator`, o foco **estava** indo de
`[8,113]` (trilho) para `[176,324]` (conteúdo): o ▶ funcionava, e o que falhava
era o meu roteiro, que disparava a tecla antes de a tela assentar. Duas rodadas
de conserto gastas num defeito que não existia.

E um defeito que existia mesmo: ao encolher os paddings, meu `replace` casou só
com o bloco do retrato e **não** com o dos itens, que ficaram com 34dp de padding
por lado dentro de um vão de 40dp. Resultado na TCL: o trilho com o rosto e a
divisória, e **nenhum ícone** — recortados por excesso de largura.

**Conferido na TCL:** fechado com sete ícones num vão de 40dp e o facho no
escolhido; aberto com os rótulos inteiros num painel opaco de 220dp.

### 16.4 A luz mais forte, e a margem pela segunda vez

«As luzes devem parecer que estão saindo de onde tu escolheu, e ao escolher um
novo, piscar de leve como se estivessem ligando, igual faço no Android.»

⚠️ **A piscada já era a mesma.** O `brilhoDoArco` do `:cenario` tem os dez quadros
do celular, digito por digito, e o `FeixeDaCabine` os refaz a cada troca de
destino. O que faltava era **força**: com `FORCA_DO_FEIXE = 0.34` a coreografia
acontecia e ninguém via. Subiu pra `0.55` — o pico dos quadros é 1,35, então o
primeiro estalo chega a ~0,74 e assenta em 0,55.

E a lente do item foi de `5.5×` pra `12×` a largura da fresta. É este cone curto
que faz a luz parecer **sair do item**: com 16dp de halo, menor que o próprio
ícone, o olho lia enfeite, não fonte.

Medido nas fotos, no calor médio (r−b) da faixa ao lado do trilho: **27,9 → 35,5**.

⚠️ **E não adiantou: «a luz tá uma merda ainda».** O dono estava certo de novo, e
desta vez o erro era meu duas vezes — porque a resposta estava escrita no próprio
`Arco.kt`, no comentário da peça que eu estava usando:

> `desenhaOCone` — o radial é isotrópico, e o que faz ele parecer um cone é o
> **centro cair fora da área visível**.

O feixe da TV tinha `raio = size.width`: meio disco de 960dp de raio sobre uma
tela de 960dp. Um radial esticado assim não tem queda visível em lugar nenhum —
vira um lavado marrom uniforme cobrindo tudo, inclusive o canto oposto. **Não é
um facho fraco, é névoa**, e aumentar a força só engrossa a névoa. Foi o que eu
fiz na primeira tentativa.

No celular a lente fica na aresta de baixo e a barra tem 89dp: o que se vê é uma
**fatia** do radial, e a fatia é o cone. Aqui a fatia se faz pelo raio — 340dp
numa tela de 960 significa que a luz nasce forte na lente, atravessa o trilho,
alcança a primeira coluna de cartazes e **acaba**. É o acabar que faz o olho ver
de onde ela veio.

⚠️ A medida de calor que eu tinha usado pra dizer «melhorou» era ruim: ela mede a
faixa ao lado do trilho, onde a névoa também era quente. Névoa e facho dão o
mesmo número ali. O que separa os dois é o **resto da tela** — e isso a foto
mostra e o número não mostrava.

⚠️ **Não consegui fotografar a piscada.** O `screencap` leva uns 300ms e a
coreografia inteira dura 1200ms com picos aos 90 e aos 200 — a rajada de cinco
quadros pegou a lente já assentada, com brilho constante (176,8 · 176,8 · 177,3).
O mecanismo está no código e é o mesmo do celular; a coreografia em si fica
conferida a olho, não por medida.

#### A margem, pela segunda vez

O dono reclamou do mesmo espaço duas vezes, e da segunda estava certo de novo:
depois de o trilho encolher pra 40dp, o conteúdo ainda começava em 88dp, porque
somava `overscanH = 48dp` por cima.

⚠️ 48dp é a regra dos 5% de overscan, e ela vale pra **borda da tela**. À esquerda
não há borda: há o trilho, que já é margem. O `overscanH` caiu pra 24dp e o
`TelaInicialDaTv` devolve os 24 que faltam **só à direita**, onde a borda existe.

Um número e uma linha, em vez de trocar as trinta e seis chamadas espalhadas —
que seriam trinta e seis chances de errar uma.

Medido com `uiautomator`: o primeiro cartaz foi de **x=280px** (antes de tudo)
para **x=176px** (trilho magro) e agora para **x=128px**.

### 16.5 Três da foto do dono: o rosto oval, a pastilha branca, e o menu que ficava aberto

#### «A foto de perfil tá casada»

⚠️ Estava oval, e a causa é a lição que esta casa já pagou uma vez: **`size` é
preferência, não ordem.**

A conta do trilho fechado é apertada por construção. Com `Focavel` a 2dp, `Row` a
2dp, lente de 3, vão de 8 e insígnia de 26, dão **45dp querendo caber em 40**.
Quando não cabe, o que acontece **não é corte**: o pai reduz a largura, mantém a
altura, e o rosto redondo vira elipse — em silêncio.

Consertado nos dois lados. A aritmética (o vão caiu pra 3dp e o `Row` perdeu o
padding horizontal: 2+3+3+26+2 = 36) e uma trava: a insígnia passou a usar
`requiredSize`. Com ela, um erro futuro **transborda** em vez de deformar.
Defeito visível se conserta; defeito silencioso vira característica.

#### «A luz tá estranha»

⚠️ Era uma **pastilha branca chapada** ao lado do item escolhido, e a culpa é do
`12×` que eu tinha acabado de pôr.

`desenhaOCone` pinta um `drawRect` no `DrawScope` **daquela caixa** — 3×20dp. O
radial nunca vaza pra fora dela. Com raio de 36dp a caixa inteira fica no topo do
gradiente e satura. Eu tinha subido o número achando que a luz ia «sair do item»,
sem notar que ela não tem por onde sair.

Quem ilumina a sala é o `FeixeDaCabine`, em tela cheia. A lente do item é só o
ponto quente da fonte, e precisa de queda **dentro dos próprios 20dp** pra
parecer um ponto. Voltou pra `5.5×`.

#### «Ao selecionar uma opção do menu, fazer ele fechar»

O trilho abre quando tem foco — então fechar não é um comando, é uma
consequência: devolver o foco ao conteúdo. Sem isso, apertar `OK` trocava a tela
e **deixava o painel aberto por cima dela**.

⚠️ O pedido não pode ser feito na hora do clique: a tela nova ainda não foi
composta e não há para onde mandar o foco. Vira um recado que um `LaunchedEffect`
entrega 80ms depois.

⚠️ E é um **contador**, não um booleano: escolher o mesmo destino duas vezes tem
de fechar o menu as duas vezes, e um booleano que já está `true` não dispara
efeito nenhum.

**Conferido na TCL:** rosto redondo, lente com queda em vez de pastilha, e o
`OK` fechando o trilho com o foco no primeiro cartaz.

## 17. O herói passa cenas do filme — primeiro paradas, depois em vídeo

Pedido do dono: «o filme que está no topo, em vez de ficar com uma imagem
estática, carregar cenas do filme».

### 17.1 As cenas já existiam

⚠️ Não foi preciso nada novo do servidor. A **folha de sprites** (`GET
/api/media/{arquivo}/scrub`) é gerada pro preview de seek — é ela que desenha o
rolo de miniaturas do player. São quadros do próprio filme, já servidos e já
cacheados pelo Coil, porque é o mesmo arquivo que o rolo usa.

O recorte é a técnica que o celular já tinha: mede a imagem em `colunas × linhas`
o tamanho da caixa e a **empurra** pra que a célula certa caia na janela. Nada é
decodificado duas vezes.

### 17.2 ⚠️ Só cenas que você já viu

Os quadros saem do trecho entre o começo e `ondeParou` — **nunca depois**. Um
herói de «continuar assistindo» que mostrasse o terceiro ato seria um spoiler
entregue justamente por quem devia estar te convidando a voltar.

As duas pontas do intervalo têm motivo: começa em 8% do visto porque o zero é
logotipo de estúdio e crédito de abertura, e para em 96% porque o quadro seguinte
é o que a pessoa ainda não viu.

⚠️ **Esta regra ganhou teste**, e é o único pedaço desta leva que tem. Ela é
lógica pura e invisível: um erro aqui não quebra tela, não aparece no lint e não
estoura nada — só mostra a alguém uma cena que ela não viu. Defeito silencioso,
prejuízo irreversível. São seis casos, incluindo «filme mal começado não rende
cena» e «a primeira cena não cai na abertura».

Seis segundos por cena, `Crossfade` de 1,2s. Longo de propósito: é o fundo de uma
tela que se navega, e o §4.2 é explícito — «ele não pode competir com um pôster».

### 17.3 ⚠️ Conferido pela metade, e o que falta não é código

Na TCL o herói **não trocou de cena**, e o log diz por quê:

```
arquivo=cbf498cb-… parou=950.329 folha=não gerada
```

Arquivo existe, 950s vistos — e o `/scrub` respondeu **404**. As folhas vêm de um
trabalho em lote (`POST /api/scrub`, na página do Servidor da web), não sob
demanda: um filme que nunca entrou no lote não tem tira.

O que **está** conferido é a queda: sem folha, o herói fica na arte estática,
igual a antes, sem piscar. É o §24 e ele funciona.

O que **não** está conferido é a troca em si, na TV. Roda o lote de scrub e ela
acende — e é uma coisa que eu não posso fazer pelo dono, porque é a máquina dele
gerando arquivo.

⚠️ Por isso o log ficou. «Não mudou nada» é o pior sintoma que existe: não
distingue arquivo sem id, filme mal começado e folha inexistente. A linha responde
os três.

### 17.4 «Cenas» era **vídeo**, não quadro parado

O dono corrigiu: «cenas que eu digo é um videozinho rodando de uma cena
aleatória». Outra peça, outro risco — e o risco estava documentado nesta casa
antes de a prévia existir, no `TelaDoPlayerDaTv`:

> «solta o **decodificador de hardware** — e numa TCL ele é um só, então quem o
> segura impede o próximo filme de abrir.»

Um fundo decorativo que segura o decodificador é um fundo que impede alguém de
assistir. A prévia é cercada por cinco condições, e nenhuma é enfeite:

| | |
|---|---|
| **só toca direto** | transcodificar pra enfeitar um fundo põe o servidor a trabalhar porque alguém está *olhando* a tela, sem ter pedido nada |
| **só depois de 3s parado** | passar pela biblioteca a caminho de outra tela não acende decodificador |
| **solta no `ON_PAUSE`**, não no `ON_STOP` | o primeiro chega antes; entre os dois há a janela em que a prévia e o filme existiriam juntos |
| **morre em 45s** | uma TV esquecida na biblioteca não pode segurar o decodificador a tarde inteira |
| **sem som** | é fundo, e fundo que fala interrompe |

A regra de spoiler continua: o ponto de entrada é sorteado entre 8% e 96% do
trecho **já visto**.

#### ⚠️ Dois erros meus, e o segundo é o mesmo de duas semanas atrás

**Um:** reusei a lista de cenas da folha de sprites pra escolher o ponto do
vídeo. A folha é 404 na maioria dos filmes desta casa, então a prévia nascia
morta junto com ela — por dependência que ela não tinha. São coisas
independentes: quadro parado precisa da tira; vídeo precisa de um segundo e de um
arquivo. Viraram duas funções e dois testes.

**Dois:** `401`. O mesmo código, a mesma causa e o mesmo conserto dos canais de
fora — as rotas de mídia querem `Authorization: Bearer` na fonte de dados, e o
`?token=` da URL não vale por ele. Da primeira vez a resposta estava escrita no
`web/src/hls.ts` e eu não procurei. Da segunda estava escrita no
`RepositorioOdeon` **por mim**, na mesma semana.

#### Conferido, e o que falta

✅ A prévia toca: três capturas espaçadas mostram três cenas distintas do filme no
herói, e a diferença média entre elas é de 13 a 23 níveis de cinza — contra 0,0
antes do conserto do `401`.

⚠️ **Não conferido: abrir um filme logo depois da prévia.** É exatamente o risco
do decodificador, e é o que eu mais queria ter visto. A navegação por `adb` me
deixou preso na tela do perfil em três tentativas seguidas, e insistir custaria
mais do que vale — o dono está na frente da TV e leva dez segundos.

Foi por não ter conferido que a prévia ganhou as duas últimas travas (`ON_PAUSE`
e os 45s). Elas não substituem a conferência; elas reduzem a janela enquanto ela
não acontece.

## 18. O menu travado — três custos removidos, e uma medida que não serviu

«Abrir e fechar o menu, movimentar ele tá meio travado, tem como otimizar?»

### 18.1 Os três custos

**A varredura do pó.** O `desenhaAPoeira` percorria a **área de desenho inteira**:
numa tela de 1920×1080 são ~2050 iterações por quadro, e a esmagadora maioria
termina em «alfa pequeno demais, não desenha». No celular isso nunca doeu porque
a caixa tem 143dp de altura; na TV a caixa é a sala. Agora o laço é recortado
pelo **próprio raio** do facho — fora dele o resultado já era zero, então não
muda um pixel do que se vê; muda quantas contas se faz pra não desenhar nada.

**A animação de largura do trilho.** O `animateContentSize(tween(160))` parecia
barato — 160ms de uma barra crescendo. Mas o trilho é o primeiro filho de uma
`Row`: mudar a largura dele a cada quadro obriga a **grade inteira ao lado** a se
remedir e reposicionar, sessenta vezes. Removido. O menu aparece em vez de
crescer, e o dono já tinha pedido isso em outra tela: «sem mt estresse ou
movimentos».

**⚠️ A leitura da lente no escopo errado — este é o grande.** `alturaDaLente` era
um `Float` passado por valor pro `FeixeDaCabine`. Em Compose, **quem lê um estado
é quem recompõe quando ele muda** — e a leitura acontecia no corpo do
`TelaInicialDaTv`, que é o pai de todas as telas. Cada movimento de foco no
trilho move a lente, e mover a lente recompunha **a biblioteca inteira** junto.

Passou a ser `() -> Float`, lido dentro do `Canvas`. A leitura caiu na fase de
desenho: a composição não é invalidada, e só aquele retângulo é repintado.

### 18.2 ⚠️ E a medida não serviu — o que é um resultado

Medido com `dumpsys gfxinfo` durante o vaivém do menu:

| | quadros travados | mediana | gpu |
|---|---|---|---|
| antes | 86% | 150ms | 21ms |
| depois das três | 100% | 400ms | — |
| com o facho **desligado** | 98% | 350ms | 13ms |

Os números **pioraram** depois de mudanças que só podem ajudar, e desligar o
facho inteiro mudou pouco. Isso não é um resultado sobre o app — é um resultado
sobre a régua.

A causa provável: esta tela **desenha sob demanda**, não continuamente. São ~43
quadros em 18 segundos, cerca de dois por tecla. Um quadro que acorda depois de
segundos parado sempre «perde» o vsync que nunca tentou alcançar, e o `gfxinfo`
conta isso como travamento. A métrica serve pra animação contínua — foi assim que
ela mediu a caixa 3D na T0, e ali ela funcionou.

⚠️ **Então os três consertos entram sem prova de melhora.** Cada um remove um
custo real e verificável por leitura — 2050 iterações desperdiçadas, um relayout
global por quadro de animação, e uma recomposição de tela inteira por tecla —, mas
o número que eu tinha pra mostrar não é confiável, e apresentá-lo como vitória
seria pior que não medir.

Quem sabe se melhorou é quem estava sentindo: o dono, no sofá.

## 19. O ao vivo — oito melhorias, e um logo que voltou atrás

Pedido do dono: «gostaria de melhorar a experiência, veja o que temos e proponha
diversas melhorias» — e depois, «do all».

### 19.1 O que já chegava e não aparecia

Metade das melhorias não precisou de rota nova. Estes campos vinham em toda
resposta e a tela ignorava:

| dado | onde | virou |
|---|---|---|
| `a_seguir` | `CanalNoAr` | a linha «a seguir · X» no cartão da sintonia |
| `programme_id` | `ProgramaDoGuia` | a chave do lembrete |
| `description`, `year`, `categoria` | idem | a ficha do bloco |
| `lembrete` | idem | a estrela na grade |

### 19.2 A grade deixou de ser tabela

⚠️ **Quem é focável agora é o bloco, não a faixa.** Antes a faixa inteira era um
alvo só, e servia pra rolar; via-se `Toy Story 3 · 16:46` e não se podia fazer
nada com aquilo.

Com o bloco focável, ◀ ▶ andam de programa em programa e ▲ ▼ trocam de canal —
a navegação que todo guia de TV já ensinou. **E a rolagem horizontal veio de
brinde**: o foco puxa a vista, então «olhar pra hoje à noite» deixou de precisar
de um controle próprio. Uma melhoria que eu tinha listado separada saiu junto com
a outra.

⚠️ Um `ScrollState` **só** pra todas as faixas. Elas têm de andar juntas: duas
faixas em posições horizontais diferentes seriam duas réguas de tempo, e a agulha
do agora cortaria as duas mentindo para uma.

### 19.3 Os lembretes, que existiam em todo lugar menos no app

`GET/POST/DELETE /api/live/reminders` estavam na web desde sempre e **não
existiam no `:core`**. O `ProgramaDoGuia` já vinha com `lembrete: Boolean` — o
app lia o campo e não tinha o que fazer com ele.

⚠️ A estrela só acende **depois** da confirmação do servidor. Pintar antes é o
otimismo que faz alguém perder o programa: um lembrete que parece marcado e não
está mente sobre a única coisa que ele promete.

### 19.4 Os favoritos moram no aparelho

São 23 canais, e os seus ficam onde o servidor os pôs. Fixar sobe o canal pro
começo da sintonia, na **ordem de fixação** — que é a resposta que a pessoa deu à
pergunta «quais são os seus», e a primeira resposta é a mais forte.

⚠️ Guardados no `Cofre` deste aparelho e não no servidor: «quais são os meus
canais» é pergunta da sala, não da casa. A TV da cozinha pode querer outros três.

⚠️ E fixar mora num **botão no herói**, não num toque longo no cartão: o
`Focavel` desta casa não tem clique longo, e ensinar um gesto escondido num
controle de cinco teclas é esconder a função.

### 19.5 ⚠️ O logo foi tentado e revertido

`CanalNoAr.logo_url` vem dos canais de M3U como URL **externa absoluta**, e a
`urlDaArte` desta casa prefixa `$base/artwork/` cegamente. O resultado foi
`…/artwork/https://…` — 404.

Na TCL os canais de fora ficaram com o número e **nada mais**: o nome tinha saído
pra dar lugar a uma imagem que nunca chegou. Trocar informação por espaço vazio é
pior que não ter logo, então voltou o nome.

Pra ele existir, o logo externo precisa de um caminho que não passe pelo
`/artwork/` — e isso é conversa de servidor, não de tela.

### 19.6 O que eu vi e o que eu não vi

**Visto na TCL:** o botão `☆ fixar` ao lado do `▸ sintonizar`; a linha
`a seguir · Os Incríveis 2` nos canais de fora; o contorno de «começa em menos de
15 minutos» no `O Lagosta 17:31`; e o número sem nome que denunciou o logo.

⚠️ **Não visto:** o bloco focável, a ficha que o `OK` abre, o lembrete marcando, e
a reordenação por favorito. Compilam, passam no lint e nos 164 testes — mas isso
não é o mesmo que ter funcionado, e esta doc existe justamente pra não confundir
as duas coisas.

O motivo é raso e vale registrar: a navegação por `adb` me levou três vezes
seguidas pra telas erradas (biblioteca, perfil, guia), e o consentimento do Gemini
apareceu no meio. Insistir custaria mais do que vale — o dono está na frente da
TV.

### 19.7 Quatro relatos depois de rodar

**«A timeline tá fixa, não consigo ir além dos horários já mostrados.»**

⚠️ A rolagem horizontal existia — e não tinha para onde rolar. Seis horas a 1,6dp
por minuto dão **576dp** numa janela de ~800dp: o conteúdo cabia inteiro. E o dado
nem chegava a tanto (`guiaAoVivo` pedia 4h, `gradeDoOdeon` 5h).

Agora são **doze horas** nos dois pedidos e na largura: 1152dp, mais largo que a
tela — que é a condição pra existir «pra frente». Conferido: a régua vai de 17:00
a 00:00 e continua.

**«A modal que abre é feia.»**

Ela era feia por **falta de assunto**: um retângulo escuro com texto. Um programa
é um filme, e um filme tem cara — e o guia já mandava `arte` quando o casamento
com o acervo foi seguro, que estava sendo jogada fora.

Agora a arte sangra pela direita com degradê horizontal, o título é serifado, e a
sinopse cabe inteira. É o mesmo desenho do herói do ao vivo, e a repetição é de
propósito: as duas telas falam do mesmo tipo de coisa.

**«Voltar de um canal me leva pra ficha do filme e depois pra home.»**

⚠️ O canal do Odeon abre o **player de filme** — é um arquivo do acervo, afinal —
e o `voltar` dele leva à ficha da obra. Isso é certo pra quem escolheu um filme e
errado pra quem escolheu um canal: esse não pediu o filme, pediu o canal.

O `canalId` já viajava com o filme desde o conserto da virada de programa. Aqui
ele responde a segunda pergunta: **de onde essa pessoa veio**.

⚠️ **O lembrete não foi confirmado.** Apertei `☆ me avise` e o botão continuou em
`☆ me avise` — o estado não virou. Pode ser o servidor recusando a rota, pode ser
que o aperto não tenha chegado ao botão (a navegação por `adb` estava me levando
pra telas erradas na mesma rodada). Instrumentei a falha com log e não consegui
uma captura limpa. Fica como **não conferido**, não como pronto.

## 20. O lembrete vira aviso no celular

«O "me avise" não vai notificar em lugar algum. Talvez aproveitar a notificação
do celular pra isso?» — e, junto: «o android já tem notificações e lembrete do
canal ao vivo, dá pra aproveitar».

⚠️ **Conferido antes de construir, e não tinha.** O `:app` não tem tela de ao
vivo, não tem lembrete e não tem notificação própria — o que existe é a
notificação de download, que o Media3 gerencia por dentro do `DownloadService`, e
a permissão `POST_NOTIFICATIONS` já declarada no manifesto. Essa última sim foi
aproveitada; o resto nasceu agora.

### 20.1 Por que no celular, e por que um trabalho periódico

O lembrete é marcado **na sala**, na grade do ao vivo, e guardado no servidor. Mas
o aviso não serve numa TV: quem marcou «me avise» não está na frente dela — se
estivesse, não precisaria de aviso. O celular é o aparelho que anda com a pessoa.

⚠️ E um `AlarmManager` sozinho não basta. Ele sabe disparar numa hora marcada, e é
o que dispara o aviso — mas alguém precisa **descobrir** os lembretes, que nascem
na TV. Sem uma pergunta periódica, um lembrete marcado às 18h para as 20h40 só
seria visto se o celular fosse aberto no meio-tempo — que é justamente o que a
pessoa não vai fazer, porque marcou pra não ter de lembrar.

Quinze minutos é o piso do `WorkManager` e serve: cada rodada agenda tudo o que
começa na **próxima hora**, então a janela nunca perde nada por atraso de
descoberta.

### 20.2 Os dez minutos

Escolhidos pelo dono, e o número decide **o que a frase pode dizer**: com cinco,
«começa em cinco minutos» é um susto; com trinta, vira agenda e a pessoa esquece
de novo. Dez dá tempo de sentar.

⚠️ A frase diz **quanto falta**, não a hora. «Começa às 20:40» obriga quem lê a
olhar o relógio e fazer a conta; «começa em 10 minutos» já é a resposta. É o §8b
numa linha de notificação.

### 20.3 As três defesas

| | |
|---|---|
| `setExactAndAllowWhileIdle` | com o celular no bolso e a tela apagada há horas, o Doze adiaria um alarme comum — e um aviso de «começa em 10 minutos» que chega às 21h05 não é atrasado, é lixo |
| queda pra alarme inexato | o Android 12+ pode negar o exato; alguns minutos de folga ainda servem, um app que fecha na cara de alguém não |
| permissão conferida **no receptor** | entre marcar o lembrete e a hora dele a pessoa pode ter desligado as notificações, e postar sem permissão é exceção dentro de um `BroadcastReceiver` — ou seja, processo derrubado |

⚠️ **Não conferido no aparelho.** Compila, passa no lint e nos 164 testes, mas um
aviso que depende de quinze minutos de trabalho periódico, de um alarme e de um
lembrete marcado na TV não se prova numa captura de tela. E o lembrete em si
continua sem confirmação (§19.7) — se ele não estiver marcando, este aviso não tem
o que anunciar.

A ordem de conferência é: marcar um lembrete na TV, ver a estrela ficar; depois
esperar o programa se aproximar com o celular no bolso.

## 21. A TV dormia no meio do filme

«Quando fica assistindo filme, a TV acaba entrando em modo screensaver.»

### 21.1 O sistema não sabe que você está assistindo

Ele conta **interações** — tecla, toque, controle — e um filme de duas horas não
tem nenhuma. Do ponto de vista do Android, quem assiste em silêncio é quem saiu
da sala.

⚠️ **Nenhum dos três players desta casa dizia o contrário.** Não havia
`KEEP_SCREEN_ON` nem `keepScreenOn` em lugar nenhum — nem na TV, nem no celular.

É o tipo de defeito que não aparece em teste nem em captura: ele precisa de
**tempo passando** pra existir, e todo teste é apressado. Só quem assiste um filme
inteiro encontra.

### 21.2 Só enquanto **toca**

A tentação é acender no `PlayerView` e esquecer. Mas um filme pausado é um filme
que alguém deixou pra lá, e segurar uma TV acesa a noite inteira porque um player
está aberto e parado é trocar um incômodo por um pior.

⚠️ E o `keepScreenOn` é o da `View`, não a flag da janela: a flag mora na janela e
depende de alguém lembrar de desligá-la, inclusive por caminhos que ninguém
previu. O da `View` morre **com a `View`** — o `onDispose` é rede, não é a única
defesa.

### 21.3 ⚠️ E a primeira medição não media nada

Eu conferi com `dumpsys window | grep -c KEEP_SCREEN_ON` e deu `1` — tocando,
pausado, fora do player, sempre `1`. Parecia funcionar sempre, o que já devia ter
me alertado.

O `grep` estava casando com **`WM_DEBUG_KEEP_SCREEN_ON`**, um nome numa lista de
grupos de log de depuração. A medida não tinha relação nenhuma com o estado.

A régua certa é o `dumpsys power`: a flag vira um `SCREEN_BRIGHT_WAKE_LOCK` que o
WindowManager segura **em nome do app**.

```
tocando        : 1
pausado        : 0
tocando de novo: 1
fora do player : 0
```

```
SCREEN_BRIGHT_WAKE_LOCK 'WindowManager/displayId:0'
  ACQ=-30s165ms  ws=WorkSource{10011 dev.odeon.android.tv.debug}
```

⚠️ É a segunda vez nesta doc que uma medida ruim quase virou conclusão — a outra
foi o calor do facho (§16.4). O padrão é o mesmo: um número que **sempre** dá a
resposta que se queria não está medindo o que se pensa.

## 22. A ficha — primeira passada, e o que ela ainda não é

«A tela que mostra descrição, imagens etc — a que temos hoje é muito feia e sem
graça.»

⚠️ A §7 desta doc dizia «está perto». Estava errada, e quem viu rodando manda.

### 22.1 As imagens existiam e estavam fora da tela

O varal de cenas está na ficha da TV **desde sempre** — como o **último** item da
coluna, depois do título, da sinopse, das etiquetas, dos botões e do recado da
locadora.

Numa TV isso quer dizer *fora da tela*. Uma fileira de fotos que só aparece pra
quem rola até o fim é uma fileira que ninguém vê — e a pessoa conclui, com toda a
razão, que a tela não tem imagem nenhuma.

Subiram pra logo depois dos botões. O recado da locadora desceu: ele é raro e é
aviso, e aviso não disputa espaço com o que a tela existe pra mostrar.

### 22.2 `800p · h264` saiu da linha do título

Eles estavam logo abaixo do nome do filme — o lugar mais valioso da tela — e **não
é assim que alguém escolhe um filme**. Resolução e codec respondem «vai tocar
bem?», que é pergunta de quem já decidiu; ano e duração respondem «é este que eu
quero?», que é a de quem está olhando.

### 22.3 Seis pílulas viraram quatro

Eram `Estados Unidos`, `Reino Unido`, `filme`, `Ação`, `Aventura`, `Thriller`.
Metade não ajuda a escolher: `filme` numa tela de filme é ruído, e o país
raramente é o critério de quem decide o que ver hoje à noite. Seis pílulas iguais
também achatam o valor de cada uma — quando tudo é etiqueta, nenhuma é destaque.

⚠️ O filtro é por `namespace` e é **tolerante**: um nome que o servidor não use
não casa, e a etiqueta continua aparecendo. Errar aqui mostra demais, nunca de
menos.

### 22.4 ⚠️ Isto é uma passada, não o redesenho

Mover três coisas não deixa uma tela bonita. O que ficou **por fazer**, e que é o
que de fato responderia «sem graça»:

- **o fundo em movimento** — a mesma prévia em vídeo do herói da biblioteca, que
  o dono aprovou. A peça existe, mas está privada dentro do `HeroiDaSala` e
  precisa sair de lá antes de ser reusada
- **a caixa 3D** no lugar do pôster plano. Ela existe no `:cenario` e é a peça com
  mais identidade da casa — e é também a que a §10.1 mediu como cara. Uma só, sem
  animação, pode caber
- **o `logo` da obra** no lugar do título em texto, quando o `artwork` tiver um
- **elenco e direção**, que a ficha não mostra e o acervo tem em `tags`

⚠️ E não foi vista rodando: a navegação por `adb` caiu na tela do guia, e eu
preferi parar a insistir. O que está aqui é legível no código e passou no portão —
mas «passou no portão» não é «ficou bonito», e a segunda coisa só o dono decide.

### 22.5 O redesenho, primeira parte — e nada de peça nova

O dono aprovou a maquete com o argumento certo: «a caixa 3D já temos da locadora
e a fita já usamos do próprio player». Foi o que guiou a implementação.

**O véu passou a decidir.** Ele ia de 0,96 a 0,80 e só então caía — cobria a
imagem inteira com uma gaze e não deixava nem ler direito nem ver a arte. «Muito
feia e sem graça» é o que uma tela indecisa parece. Agora é opaco até 38%,
esmaece até 66%, e some.

**As cenas viraram película.** Eram retângulos arredondados com vão entre eles —
uma galeria que podia ser de qualquer app. Agora são quadros colados num rolo,
com os furos de arrasto em cima e embaixo.

⚠️ E os furos são **os do player**: o `Perfuracoes` do `Tira.kt` era privado e
fixo no tamanho da barra fina. Ganhou medidas e virou público — os padrões
continuam os do player, então quem já o usava não mudou de aparência. Abrir uma
peça sem mexer em quem a usa é o único jeito honesto de reaproveitar.

**A cascata de entrada.** Título e ficha entram, depois sinopse e etiquetas,
depois a película — 70ms entre cada, subindo 14dp. É a diferença entre uma tela
que **abre** e uma que **aparece**: sem ela tudo materializa junto e o olho não
sabe por onde começar, que era metade do «sem graça».

⚠️ **Só na entrada.** Nada anima em repouso: depois de meio segundo a tela está
parada e não custa mais nada. Animação contínua numa tela de leitura é o que a
biblioteca já reprovou.

### 22.6 ⚠️ O que a foto mostrou que falta

Conferido na TCL: o véu decide e a película tem furos. **Mas a tira está presa na
coluna de texto** — ela herdou os 55% de largura do bloco em que nasceu — e sai
cortada pela borda de baixo.

Ela precisa sair da coluna e atravessar a tela, como na maquete. Isso é mudança
de estrutura, não de estilo, e eu preferi parar aqui a começá-la sem poder
conferir.

Falta também a **caixa 3D**, que é a segunda parte combinada — e é a que precisa
de medição antes de entrar, porque a §10.1 mediu 97ms por quadro com uma só.

### 22.7 A fita atravessa, e a caixa entra parada

**A fita saiu da coluna.** Ela nascera dentro do bloco de texto e **herdou os 55%
de largura dele** — uma película de meia tela, cortada pela borda de baixo. É o
defeito clássico de reaproveitar um lugar em vez de escolher um.

Um rolo de filme não tem meia largura. Agora ela é irmã da coluna, ancorada no pé
do `Box`, e atravessa de ponta a ponta. A coluna ganhou folga embaixo do tamanho
dela: a fita flutua sobre o texto, e sem a folga o último parágrafo rolaria pra
debaixo e nunca seria lido.

**A caixa 3D entrou, e entrou parada.**

⚠️ `poseControlada` fixa, **sem giro e sem respiração** — e isso é a §10.1
mandando: uma caixa sozinha custou **97ms por quadro** na TCL. Girando
continuamente, ela sozinha derrubaria a ficha pra 10fps.

Parada ela é desenhada uma vez e não custa mais nada. E o volume — que é o que ela
veio dar, e que um pôster plano não tem — **não precisa de movimento pra existir**.
A proposta previa uma respiração de ±3°; ela fica de fora até haver medida que a
permita, e isso é decisão, não esquecimento.

É a mesma `CaixaEm3D` da locadora e a mesma `FaceDaCaixa`, com o `ano` indo pra
lombada. Nenhuma peça nova — de novo.

**Vista rodando** (o `adb` voltou depois de reiniciar o servidor):

| | |
|---|---|
| a caixa aparece com **volume** | sim — lombada visível, e ela lê como objeto, não como pôster torto |
| a fita atravessa inteira | sim, de ponta a ponta, com os furos em cima e embaixo |
| a entrada continua fluida | mediana de 109ms **durante os ~500ms da cascata**, e parada depois |

⚠️ **E a foto cobrou dois preços que a caixa criou**, nenhum previsto na maquete:

**A coluna encolheu.** Ela era `fillMaxWidth(0.55f)`, e quando a caixa entrou à
esquerda esses 55% passaram a **incluir** os 254dp dela: sobrou pouco mais de um
terço, e a sinopse quebrava a cada cinco palavras. Fração é medida de quem não tem
vizinhos. Virou largura própria — 620dp, a mesma coluna de leitura da §5.3.

**O título saía por cima.** Com a folga da fita, a altura útil caía pra ~296dp, e
não cabiam título, ficha, sinopse, etiquetas e botões. O que acontece então não é
corte: o foco entra no `assistir`, a rolagem o traz pra vista, e **o nome do filme
sai pela borda de cima**. A ficha abria sem dizer de que filme é.

Consertado encolhendo o quadro da fita de 118 pra 94dp — ela continua
atravessando, o que ela não faz mais é empurrar o título pra fora.

⚠️ Este último conserto **não foi reconferido**: a navegação por `adb` me levou ao
lançador e ao player antes de eu conseguir reabrir a ficha. Os dois primeiros
achados estão vistos; o terceiro é código que responde a uma causa medida, mas não
é foto.

~~A TCL saiu da rede antes da instalação~~, e por `adb` não
há como acordá-la. O portão passou (`assembleDebug`, 164 testes, lint limpo nos
quatro), mas isto é a terceira vez nesta doc em que passar no portão não é o mesmo
que ter funcionado — e a caixa é justamente a peça com custo medido conhecido.

**A conferir quando a TV voltar:** se a caixa aparece com volume à esquerda, se a
fita atravessa inteira, e se a entrada continua fluida com a caixa em cena.

### 22.8 O título, e o terceiro conserto do mesmo sintoma

Primeiro encolhi a folga da fita. Depois o quadro dela. O nome do filme continuou
sumindo da ficha.

⚠️ **A causa não era altura, era quem rola.** O foco abre na fila de botões, a
rolagem os traz pra vista, e tudo o que está acima deles sobe junto — o título
entre eles. Folga nenhuma resolve isso, porque o conteúdo cresce **com o filme**:
dois botões num filme novo, três num começado, sinopse de três ou de seis linhas.

O que resolve é o título **não pertencer à parte que rola**. A coluna virou duas:
o bloco de título e ficha fixo em cima, e só sinopse, etiquetas e botões dentro do
`verticalScroll`.

⚠️ Levei três tentativas pra parar de tratar o sintoma. As duas primeiras foram
números — 190dp, depois 150dp, depois 94dp de quadro — e nenhuma podia funcionar,
porque nenhuma respondia à pergunta «por que ele sobe?». É o mesmo padrão das duas
medidas ruins da §16.4 e da §21.3: mexer no que dá pra mexer, em vez de descobrir
o que está acontecendo.

**Conferido na TCL:** a caixa 3D com volume e lombada, a sinopse legível na coluna
de 620dp, os três botões, e a fita atravessando com os furos.

⚠️ **Não conferido:** o título fixo em si. A navegação por `adb` me levou ao «para
você» na última tentativa, e eu tinha esgotado as idas ao aparelho. O portão passou
(164 testes, lint limpo nos quatro), e a mudança responde a uma causa que **foi**
observada — mas isso não é a foto.

### 22.9 O lembrete: `ok` deixou de ter poder de veto

O botão `☆ me avise` não virava, **sem erro no log e sem mudança na tela** — o pior
par de sintomas que existe, porque não diz nem que falhou.

A causa: eu exigia `ok == true` da resposta, e `LembreteMarcado.ok` tem `false`
como padrão. Um campo que o servidor pode não mandar — ou mandar com outro nome —
tinha poder de veto sobre algo que já havia acontecido.

⚠️ Agora chegar sem exceção **é** sucesso: o `201` é a resposta, o `ok` é o aceno.
E a resposta inteira é registrada em log, pra a próxima vez não custar uma rodada.

**Também não reconferido** — pelo mesmo motivo da §22.8.
