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

---

### R6 — O filme como filme

**O que muda:** entra a perfuração de película nas bordas do cartão de destaque
do "para você", como na web. E — experimental — um **grão** discreto sobre a
arte, que é o que a web sugere sem fazer.

**A pergunta honesta:** grão em cima de pôster pode virar sujeira. Proponho
entrar atrás de uma chave, medir com screenshot em três pôsteres (claro, escuro,
ilustrado), e só ficar se sobreviver aos três.

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

---

### R8 — O corpo do aparelho

O experimental de verdade, e o que só existe aqui.

- **Paralaxe por giroscópio** no pôster da ficha: a arte se move ~4dp com a
  inclinação. Volume sem 3D.
- **Borda a borda** na ficha: o backdrop sobe até debaixo da barra de status,
  com o texto respeitando as áreas seguras.
- **Detente háptico no seek**: um tique a cada 10 minutos de filme arrastados —
  a timeline passa a ter textura.
- **Gesto de devolver**: arrastar a caixa pra baixo devolve a fita, com o
  háptico do R5 no fim.

**⚠️ O giroscópio precisa de chave.** Movimento constante na tela é o oposto de
acessível pra quem tem sensibilidade a movimento, e o Android tem a preferência
do sistema pra isso (`Settings.Global.ANIMATOR_DURATION_SCALE` = 0). Respeitá-la
não é opcional.

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
| **1** | decisão da §6 + R1 + R2 | o app **parece** o Odeon, sem nada se mexer |
| **2** | R3 + R4 | a biblioteca vira acervo, com filtro e metadado |
| **3** | R5 + R7 | as coisas viram objetos, e as telas se ligam |
| **4** | R6 + R8 | o experimental — película, giroscópio, háptico |
| **5** | R9 | o app sai do app |

A leva 1 é a que mais muda a impressão por linha escrita. A 4 é a mais
divertida, e é a que mais precisa de screenshot pra não virar barulho.
