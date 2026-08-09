# Odeon — a web, tela por tela

## Sobre este arquivo

É o **mapa do que a web faz**, escrito para servir de régua ao app Android:
quando alguém perguntar *"o app já tem isso?"*, a resposta está aqui.

Mesma regra de autoria dos outros documentos:

- **Decidido** — palavra de quem decide.
- **Proposto** — sugestão, pode ser vetada sem discussão.
- **Medido** — número tirado do código ou do acervo, hoje.

Onde não houver marca, é **fato de código**: está escrito no `web/src` e foi
lido para escrever esta linha.

Este documento **descreve**, não decide. O que fica de fora do app, o que muda
de forma no celular e em que ordem cada coisa entra está em
[`APP-ANDROID.md`](APP-ANDROID.md) §3, §4b e §5 — e não é repetido aqui.

**Medido em 04/08/2026**, sobre o `web/src`:

| | |
|---|---|
| arquivos `.ts`/`.tsx` | **29** · **16.891 linhas** |
| telas com endereço próprio | **11** (+ `/p/<quem>`) |
| sobreposições (modal/gaveta/palco) | **6** |
| métodos no `api.ts` | ~140, sobre 113 rotas |
| linha do contrato | `web/src/api.ts` — a única cópia que existe |

---

## 0. Índice

| § | o que | endereço |
|---|---|---|
| 1 | O casco: barra, rotas, estado global, barramento | — |
| 2 | Entrar | (sem rota — substitui o app inteiro) |
| 3 | Para você | `/` |
| 4 | Biblioteca | `/biblioteca` |
| 5 | Coleções | `/colecoes` |
| 6 | Locadora | `/locadora` |
| 7 | Guia | `/guia` |
| 8 | Ao vivo | `/ao-vivo` |
| 9 | Mural | `/mural` |
| 10 | Perfil | `/perfil` · `/p/<quem>` |
| 11 | Revisão | `/revisao` |
| 12 | Pastas | `/pastas` |
| 13 | Admin | `/admin` |
| 14 | As seis sobreposições: ficha, gerenciar, player, menu de DVD, junto, servidor | — |
| 15 | As regras que valem em toda tela | — |
| 16 | Checklist de paridade | — |

---

## 1. O casco

Tudo o que segue mora em `App.tsx` (1.331 linhas) e vale em **todas** as telas.
É a parte que um app precisa ter antes da primeira tela existir.

### 1.1 Os endereços

```
/            para você        /mural       mural
/biblioteca  biblioteca       /perfil      perfil (o seu)
/colecoes    coleções         /p/<quem>    perfil de alguém (username ou id)
/locadora    locadora         /revisao     revisão        · manutenção
/guia        guia             /pastas      bibliotecas    · manutenção · admin
/ao-vivo     ao vivo          /admin       administração  · manutenção · admin
```

A aba **é** o endereço (não há `useState` de aba). O corpo continua desenhando
por `tab`; só a origem de `tab` mudou.

### 1.2 A barra de cima

Dois lados, e a divisão é a regra: **navegação de um lado, ferramenta do
outro**.

À esquerda **sete** entradas, todas lugares do acervo, na ordem "onde você entra
→ onde você vai depois": para você · biblioteca · coleções · locadora · guia · ao
vivo · mural.

À direita o que não é acervo:

| elemento | comportamento |
|---|---|
| **marca `◉ ODEON`** | volta pro "para você"; **pulsa** quando há varredura ou identificação rodando |
| **traço da aba** | desliza entre abas; medido do DOM (`ResizeObserver`), não de larguras fixas |
| **holofote** | brilho que segue o mouse pela barra (duas variáveis CSS + `radial-gradient`) |
| **condensar** | encolhe ao rolar, com histerese — 24px pra condensar, 8px pra voltar |
| **busca** | campo `buscar na biblioteca…`, **só na aba biblioteca**; escreve em `filters.q` |
| **gaveta de manutenção** | ícone de três controles (desenhado, não emoji); pílula com o número de obras esperando revisão; abre revisão · pastas (admin) · admin (admin) · servidor… (admin) |
| **gaveta "eu"** | avatar + **anel** de progresso do nível + selo com o número; abre perfil · sair; cadeado 🔒 quando a API é https |
| **acesa por dentro** | a engrenagem fica acesa quando a tela atual é revisão/pastas/admin — senão nenhuma aba ficaria marcada |

A insígnia (nível, fatia do nível, rosto, cor da moldura) sai de **uma** chamada
a `/api/perfil`, na montagem, e é relida no evento de janela `PERFIL_MUDOU` —
disparado pelo editor do perfil, que roda em outra tela.

Tudo isso desliga em `prefers-reduced-motion`.

### 1.3 Sessão, papéis e tokens

| | |
|---|---|
| token de sessão | `localStorage`, mandado como `Authorization: Bearer` |
| token de mídia | **separado e curto (8h)**, renovado a cada boot (`POST /api/auth/media-token`); vai como `?token=` nas URLs de `/artwork`, `/scrub`, `/api/stream` e `/api/events` |
| `DEVICE_ID` | gerado e guardado no navegador; é o que faz o aparelho **descartar o próprio eco** no barramento |
| papéis | `admin` · `user` (morador) · `guest` (convidado) |
| endereço da API | `VITE_API_URL`, ou derivado da própria página (mesmo host, porta 8080 / 8443 sob https); trocável na tela de entrada |
| mixed content | página https + API http é detectado e **explicado**, em vez de parecer servidor fora do ar |

### 1.4 O barramento (SSE)

**Uma conexão pro aplicativo inteiro** (`api.ouvirEventos`), com reconexão até 5
tentativas. Os tipos de evento e quem os escuta:

| evento | quem ouve | o que faz |
|---|---|---|
| `progress` | App, Player | App relê a lista; Player persegue a posição do outro aparelho se a diferença passar de 5s. **Ignora o próprio `device_id`** |
| `junto` | App, Mural | relê a sala; `o_que === "fim"` derruba |
| `locadora` | Locadora, Mural | recarrega o balcão e mostra um recado por 6s (`pegou` · `devolveu` · `pediu` · `venceu`) |
| `mensagem` | Mural | recarrega a lista de conversas |
| `programme_starting` | App (`AvisoDePrograma`) | avisa dentro do produto + `Notification` do sistema |
| `match_finished` · `scrub_finished` | App | relê o status correspondente |

**Polling, onde ele existe:** varredura a cada 1s enquanto roda, identificação a
cada 1,5s, presença do mural a cada 30s, grade do ao vivo a cada 60s, trabalhos
do admin a cada 2s enquanto houver job rodando. O resto é evento.

### 1.5 O que aparece por cima de qualquer tela

1. **Aviso sem `TMDB_API_KEY`** — com o passo a passo pra resolver.
2. **Faixa de varredura** — `N vistos · M novos` + arquivo atual.
3. **Faixa de identificação** — `N de T obras · X casadas · Y pra revisar`. O
   denominador é obrigatório: sem ele um contador parado parece errado em vez de
   inacabado.
4. **Faixa de erro** (mixed content, ou o último erro de rede).
5. **Aviso de programa agendado** — fora do `main` de propósito: tem que
   aparecer em qualquer aba, senão agendar não serviria pra nada.
6. Troca de aba anima (`key={tab}`, sobe 8px e clareia, 0,28s).

### 1.6 O estado que vive no casco

`me`, `works`, `entries`, `total`, `resume`, `filters`, `scan`, `match`,
`scrub`, `playing`, **`sala`**, `detailsOf`, `managing`, `serverOpen`.

Dois merecem nota:

- **`sala` mora aqui e não no player** porque ela **sobrevive ao player**: quem
  fecha o vídeo continua na sala, e quem aceita um convite precisa que o vídeo
  abra sozinho. Ao entrar numa sala, o App busca a obra **inteira**
  (`/api/works/{id}`) em vez de montar um item de lista — o player precisa da
  duração real da obra.
- **`filters`** dispara `refresh` com **debounce de 250ms**.

### 1.7 Listagem e paginação

| | |
|---|---|
| fora de coleção | `GET /api/library` — **agrupada**: uma entrada por série, uma por obra avulsa; cada linha traz o `total` |
| dentro de coleção | `GET /api/works` — **plana**: os episódios daquela série |
| página | `PAGE_SIZE = 120`, com botão `carregar mais N` |
| contagem | `"120 de 17.930"` enquanto houver resto; só o total quando tudo coube |

---

## 2. Entrar

`Login.tsx` · 211 linhas · substitui o aplicativo inteiro enquanto não há sessão.

**Três modos na mesma tela**, e quem decide é `GET /api/auth/status`:

| modo | quando | o que faz |
|---|---|---|
| **primeira execução** | `needs_setup: true` | cria o administrador (usuário, senha, repetir) |
| **entrar** | o normal | usuário e senha |
| **entrar com convite** | botão `tenho um código de convite` | código de 32 caracteres + usuário + senha → cria a conta **e já entra com ela** |

Comportamento:

- **Campo de servidor**: escondido atrás de um chip que mostra o host atual
  (com 🔒 em https). Aparece automaticamente quando a API não responde ou quando
  há mixed content. Trocar de servidor **recarrega a página** — o token vale por
  servidor.
- **Erro genérico no login**: usuário inexistente e senha errada devolvem a
  mesma frase. Distinguir entregaria a lista de usuários válidos. No setup e no
  resgate, o erro do servidor aparece inteiro.
- Mínimo de 8 caracteres; a senha é cifrada com Argon2id (dito na tela).
- A porta do convite **só existe depois** que o servidor tem dono.

---

## 3. Para você — `/`

`ForYou.tsx` · 700 linhas · a raiz. Responde *"o que eu assisto agora"*, não
*"o que existe na biblioteca"*.

### Contexto, no topo

- **`Tenho`** — chips de tempo: qualquer tempo · 15 · 30 · 45 min · 1h · 2h.
- **`Pra`** — chips de humor, das tags `mood:` com `work_count > 0`. A seção
  some quando não há nenhuma.

Cada mudança refaz `GET /api/curation/for-you?minutes=&mood=`.

### O estado frio, e como ele se resolve

A tela mede **quanto o Odeon te conhece**, de 0 a 1:

```
sinais = terminadas×2 + curtidas + bloqueadas + votos_desta_sessão
conhecimento = min(1, sinais / 6)
```

Enquanto `conhecimento < 1`, três blocos existem e depois **somem sozinhos** —
não é uma tela de boas-vindas que se fecha, é um estado que se resolve:

1. **Apresentação** — "o Odeon ainda não te conhece" / "está começando a te
   conhecer", com um termômetro de três marcas (nada · começando · te conheço).
2. **Continue de onde parou** — `/api/continue`, até 5, com barra de progresso e
   *"parou aos N min de M"*.
3. **Calibragem** — 6 capas de `/api/curation/calibrar`, cada uma com ♥ e ✕
   (`POST /api/works/{id}/feedback`). O contador é uma **fileira de lâmpadas de
   marquise**: votar acende uma. Rodapé: `N de 6 · faltam X pra ele arriscar um
   palpite`.

**Os desafios ficam fora dessa condição** — valem igual depois de mil filmes
(ver §10.3).

### O ranking, em três pesos

A curadoria devolve uma ordem, e desenhar todo item igual jogaria essa ordem
fora:

| faixa | quantos | o que mostra |
|---|---|---|
| **Esta noite** (herói) | 1 | arte larga (backdrop → still → pôster), pôster, título, meta, **os motivos em texto**, `▸ Assistir`, ♥/✕, e o **número de afinidade** (score×100) |
| **Também hoje** | 6 | pôster com o número da posição, título, meta, o primeiro motivo |
| **A fila** | o resto | linha com posição, pôster pequeno, título, motivo e barra de score |

**Todo item diz por que foi sugerido.** É o que separa curadoria de sorteio.

`▸ Assistir` fica desabilitado quando a obra não tem arquivo, e o `title`
explica: *"nenhum arquivo tocável nesta obra"* — botão apagado sem motivo é
clique que não faz nada.

### O perfil de gosto, aberto

Chip `o que o Odeon acha que você gosta ▾` abre o **TasteInspector**: obras
tocadas, terminadas, largadas, faixa de duração preferida, hora do dia mais
frequente, tags que você **termina** (com +%) e tags que você **larga** (com −%),
e um aviso quando ainda não há vetor de gosto.

> Recomendação que não se deixa inspecionar é adivinhação.

---

## 4. Biblioteca — `/biblioteca`

`App.tsx` (a tela mora no casco) + `FilterBar.tsx` · 220 linhas.

### A barra de filtros

| controle | opções |
|---|---|
| **ordenar por** | em destaque (padrão) · título · ano · adicionado · duração · aleatório |
| **filtros ▾** | chips por namespace de tag (`mood`, `genre`, `format`, `origin`…), com a contagem de obras em cada |
| **modo das tags** | aparece com 2+ tags ativas: `qualquer tag` ⇄ `todas as tags` |
| **duração** | curto (até 40min) · médio (40–90) · longo (90+) |
| **identificação** | confirmadas · automáticas · em dúvida · sem match · **ignoradas** (o único jeito de ver as descartadas) |
| **limpar ✕** | zera tudo **menos** a coleção em que se está — limpar filtro não deve tirar você de dentro da série |

No grupo `format` aparece um atalho quando há obras varridas e não
identificadas: `· 3.238 sem identificar →`, que leva pra revisão. Os contadores
das tags só contam o que foi identificado, e sem essa linha a tela dizia "filme
713" com 936 filmes no disco.

### O corpo

1. **Chip "Dentro de"** quando se entrou numa série, e **"Com"** quando se
   filtrou por pessoa. Os dois com ✕ pra sair.
2. **Continuar assistindo** — `/api/continue`, quando houver.
3. **Biblioteca `N de M`** — grade de cartões.

### Os três cartões

| cartão | quando | clique | o que mostra |
|---|---|---|---|
| **EntryCard série** | entrada agrupada com `is_series` | **abre a série** (vira filtro de coleção) | pôster, selo `N ep`, barra de quanto você terminou, `ano · N temporadas · N vistos` |
| **Card obra** | filme/avulso | **abre a ficha** | pôster (ou gradiente com o título), badges `sem metadata` / `revisar 63%`, barra de progresso, `SxxExx · ano · 1080p · 2h14 · 4,2 GB`, até 3 tags |
| **EpisodeCard** | dentro de uma série | **abre a ficha** | usa o **`still` 16:9** do episódio, não o pôster da série — com o pôster, 21 episódios eram 21 cópias da mesma imagem |

**Clicar no cartão abre a ficha, nunca o player.** Começar um filme é decisão, e
a decisão precisa da sinopse na frente. O `⋯` no canto abre **Gerenciar**
(§14.2).

Dentro de uma série a lista quebra **por temporada** (`Temporada N` ·
`Especiais` para a 0 · `Sem temporada` para episódio não identificado), cada uma
com `N ep · M vistos`.

---

## 5. Coleções — `/colecoes`

`Collections.tsx` · 456 linhas. A aba de **curadoria** — o que só ela faz.

### A lista

Três faixas, cada uma com um texto de ajuda quando está vazia:

| faixa | o que é |
|---|---|
| **Suas ordens** (`watch_order`) | uma sequência própria — a ordem Machete de Star Wars é o caso clássico |
| **Playlists** (`playlist`) | um punhado de obras juntas, sem ordem obrigatória |
| **Franquias** (`franchise`) | agrupa séries e filmes de um mesmo universo |

Abaixo, **recolhido**: `Criadas pelo matcher — N séries · M temporadas ▾`.
Navegar série → temporada é trabalho da biblioteca; aqui elas existem pra serem
alcançadas, não pra ocupar a tela.

O cartão de coleção mostra **as capas empilhadas** (até 4, vindas do backend),
o tipo, `N obras` e `N dentro`.

### Criar

`+ criar` abre um formulário inline: nome + tipo (ordem de exibição · playlist ·
franquia, cada um com uma frase explicando). Criar já **abre** a coleção nova.

### Dentro de uma coleção

- Cabeçalho com tipo, título, descrição e — só em coleção **manual** — `apagar`.
- **Adicionar obra**: campo de busca (debounce 250ms, mínimo 2 caracteres) com
  até 8 resultados; o que já está na coleção aparece como `já está`.
- **Subcoleções** listadas quando houver.
- **A lista**, numerada. Em `watch_order` e `playlist` ela é **arrastável**: ao
  soltar, a tela reordena localmente e persiste **a lista inteira**
  (`POST /api/collections/{id}/order`) — arrastar do 8º pro 2º desloca seis
  vizinhos, e mandar par a par deixaria o meio inconsistente se uma requisição
  falhasse.
- A linha inteira **toca**; sem arquivo ela fica desabilitada.
- `✕` tira da coleção (só nas manuais).

---

## 6. Locadora — `/locadora`

`Locadora.tsx` · **1.825 linhas**, a maior tela do produto. A biblioteca vista
como uma loja de aluguel — e a unidade **não é a obra, é a caixa**.

### O modelo

| | |
|---|---|
| fonte | `GET /api/locadora/estantes` (a vitrine da semana) e `GET /api/locadora/prateleira` (o balcão) |
| caixa | uma série vira **uma** caixa de coleção, não 21 fitas; o que não tem capa não entra numa estante |
| VHS × DVD | o corte é `loja.ultimo_ano_vhs`, **vindo do servidor** — o mesmo número decide se a caixa rebobina |
| rotação | a vitrine é sorteada por semana e **vira na segunda**; a tela diz `vira segunda` / `vira amanhã` |

### A porta da loja

Três contagens diferentes, e as três importam:

```
37 caixas na prateleira, 3 fora · 40 nesta semana, de 600 no acervo · vira segunda
```

O buraco é **dito**, não deduzido: a caixa alugada **sai** da prateleira e o vão
não é preenchido. Sem essa frase, quem viu 40 ontem conclui que a loja quebrou.

Estados vazios: `a prateleira está vazia — está tudo emprestado` /
`nada com capa por aqui` / `acendendo as luzes…`.

### O balcão

Some inteiro quando não há nada a dizer. Quando há:

- **Chips de pessoa** — quem está com fita **ou quem tem fama**: `N` na mão,
  `✕N` fitas dela que alguém teve que rebobinar, `⟲N` fitas dos outros que ela
  rebobinou. Zero some.
- **Seu limite** — `você pode pegar mais 2` / `você está no limite — devolva uma
  pra pegar outra`.
- **Recado ao vivo** — do barramento, some sozinho em 6s.
- **Devoluções** — `fulano devolveu rebobinada` / `sem rebobinar` / `até o fim`,
  `venceu na mão de fulano`, com selo `atrasada`.

### As estantes

`Em mãos` (sempre visível, independente da rotação — com ela sumiria o "pedir de
volta") · `Começadas` (do `/api/continue`) · `Lançamentos` (do que está **na
prateleira**, não do que foi sorteado) · e as estantes de gênero, com legenda
`16 de 113`.

Cada estante é uma fileira **arrastável com o mouse**. Na carga, as caixas caem
em cascata: 34ms entre caixas, 90ms entre estantes, teto de 8 estantes. Enquanto
carrega, quatro prateleiras **vazias com a madeira desenhada** ocupam a altura
final — quando as caixas chegam, nada salta.

A caixa na estante tem **três faces** (lombada à esquerda, capa, topo), selo
`VHS`/`DVD`, `N temporadas` nas coleções, e a **cinta de papel** com o nome de
quem está com ela.

### A caixa na mão

O palco central, e a peça mais elaborada da web. Fases:
`voando → na-mao → abrindo → midia → (fita) → tocando`, com `guardando` na
volta.

| gesto | efeito |
|---|---|
| clicar na caixa | ela **voa** da estante até o centro (FLIP: mede o retângulo de origem e anima a diferença) |
| arrastar | gira a caixa em dois eixos (0,5°/px na horizontal, 0,32° na vertical, limitada a ±42°). Limiar de **6px** separa girar de clicar |
| clicar na **abertura** (a aresta oposta à dobradiça) | a caixa abre e **entrega a mídia** — disco ou fita — que sai flutuando e fica girável |
| clicar no **centro da mídia** | DVD → abre o **menu de disco** (§14.4). VHS → vai direto pro filme, ou passa pela tela da fita |
| clicar fora da mídia | **guarda** (o inverso exato de abrir) |
| clicar no fundo / `Esc` | fecha o palco |

A **contracapa** (vista ao girar) traz: sinopse — ou, na caixa de coleção, a
lista `Nesta caixa` com os episódios —, até 3 cenas, a ficha técnica, um **código
de barras derivado do uuid** (a mesma caixa tem sempre o mesmo código) e o botão
`▸ assistir` / `▸ continuar` / `▸ ver a série` / `▸ pegue emprestado`.

### O balcão da caixa (as ações)

| situação | o que aparece |
|---|---|
| livre | `pegar emprestado` (com o prazo no `title`), ou `no limite` |
| **sua** | prazo (`3 dias`, `vence amanhã`, `vence hoje` — vermelho a 2 dias), `⟲ rebobinar` (só VHS), `devolver`, e *"fulano pediu de volta"* quando pediram |
| **de outro** | `fulano está com esta há 3 dias` + `pedir de volta`, ou `já pedida de volta` |

Pedir de volta **não encurta o prazo de ninguém** — dar a um membro poder sobre
o prazo do outro transformaria a locadora em disputa.

### A fita, e o atrito que é a ideia

Quando você põe pra tocar um VHS que **outra pessoa** deixou no meio, o filme
não começa: aparece a fita.

- Carretel desenhado com o quanto já rodou, ponteiro `h:mm:ss`, e o nome de quem
  deixou assim (`fulano deixou assim · há 3 dias`).
- **Rebobinar é obrigatório** — não há "dar play daqui". `deixa pra depois`
  volta pro palco.
- A animação dura **proporcional ao que a fita andou**: 1s a cada 12 min, entre
  2,5s e 10s. Os dois carretéis giram em sentidos opostos, a velocidade **cai
  com o que falta**, o rolo da esquerda engorda, há **ruído de fita
  sintetizado** (Web Audio, `RuidoDeFita.ts`) e um **tranco** no fim de curso.
- Em `prefers-reduced-motion` os discos ficam parados — a espera continua igual,
  porque a espera é o conteúdo do gesto.
- Se a fita foi **você** que deixou no meio, nada disso acontece: é a sua sessão
  continuando.
- Rebobinar mexe **na fita**, não no "continuar de onde parou" de ninguém — e
  por isso não pede confirmação.

### A escassez

`comigo = !opcoes.escassez || emprestimo.meu`. Com a escassez ligada, a caixa só
**abre** pra quem está com ela — e o "não" tem a saída a dois centímetros, no
mesmo palco. Com a escassez desligada, a locadora volta a ser um tema.

> ⚠️ **§71 (04/08/2026)**: a exigência de empréstimo é regra **da locadora**. A
> biblioteca, as coleções e a ficha são **modo livre** — nenhuma delas pergunta
> `locadora/liberadas` antes de tocar.

---

## 7. Guia — `/guia`

`Guia.tsx` · 647 linhas. A tese: *um guia que qualquer site tem é a Wikipédia com
passos extras; um que cruza o cânone com o **seu** acervo e o **seu** histórico
não existe em lugar nenhum.*

### A capa: a revista da semana

`GET /api/guia/revista`. Igual pra todo mundo, e virando na **mesma segunda** da
vitrine da locadora — é o que dá assunto em comum.

- **Eixo + tema**: gênero / década / país / diretor / saga da semana.
- **Ensaio**, quando existe — com o selo `escrito por <modelo>`. Sem chave do
  LLM ele simplesmente não está lá; a tela não escreve "em breve".
- **Os filmes**, com selo `visto` nos que você já viu; clique abre a ficha.
- **O evento coletivo**: `Termine uma das 4 obras até segunda pra participar`,
  `Você já viu 1 de 4`, e **quem já participou** — um evento coletivo em que
  ninguém sabe quem foi não é coletivo.

A capa não tem esqueleto: enquanto não chega, ela não existe.

### Os eixos de pessoa

`GET /api/guia` → **Direção** · **Elenco** · **Trilha**, cada um numa fileira
arrastável. *(Produção não entra: enterraria os 1.191 créditos de direção em
assistente de efeitos.)*

Cartão de pessoa: retrato — ou **capas empilhadas** quando não há retrato, ou a
inicial —, nome, `N títulos`, e **só quando houver histórico**: uma barra de
medidor e `N terminados · M começados`.

`ver as 1.191` abre a lista completa do eixo: busca com debounce, grade e
`carregar mais` (40 por página).

### As faixas

| seção | clique leva a |
|---|---|
| **De onde vêm** (países, com `N fora dos Estados Unidos`) | biblioteca filtrada por `tags=[origin:…]`, `kind=movie` |
| **Gênero** (só filmes) | biblioteca filtrada por `tags=[genre:…]`, `kind=movie` |
| **Década** (só filmes) | biblioteca com `yearFrom`/`yearTo` e ordem por ano |

Gênero e década não têm tela própria — e não deveriam ter: `/api/works` já
resolve os dois.

### A ficha da pessoa

Retrato, nome, `known_for`, e a linha que reconcilia os dois números:
`7 títulos em direção · 12 obras no acervo contando os outros papéis`.

Depois: `você terminou 3 e começou 1` — ou, sem histórico,
`você ainda não abriu nenhum` (convite, não relatório vazio).

A filmografia vem de `/api/works?person=` e é **agrupada**: `Filmes e avulsos`
primeiro, depois uma seção por série, da maior pra menor. Cada obra tem selo
`visto` (histórico) e barra de progresso (agora) — os dois juntos dizem coisas
diferentes.

---

## 8. Ao vivo — `/ao-vivo`

`AoVivo.tsx` · 1.493 linhas. Não é uma lista de canais: é **a mesa de quem opera
a emissora**.

### O modelo comum

Duas origens viram a **mesma** estrutura (`Pista` + `Bloco`), e a tela não
precisa saber de onde a programação veio:

| origem | rotas |
|---|---|
| **canais da casa** (o Odeon programando o seu acervo) | `GET /api/live/odeon?hours=5` |
| **IPTV** | `GET /api/live/channels` + `GET /api/live/guide?hours=5` |

Os canais da casa vêm primeiro e são marcados com o selo `ODEON`.

### As quatro peças

**1. No ar** (herói) — arte do programa (ou a marquise do Odeon com as lâmpadas
correndo, quando não há arte), fita `● NO AR` / `INTERVALO`, título, `ano · N
min · programado pelo Odeon | ao vivo`, uma barra de progresso do programa com
`começou 21:30` … `faltam 47 min`, **relógio da emissora com segundos**, e:

- `▸ SINTONIZAR`
- `↺ VER DESDE O INÍCIO` — **só quando há arquivo casado** e o canal é IPTV.

No vão entre programas (a grade da casa tem 4 min de respiro), sintonizar diz
`intervalo — X começa às 22:04` em vez de não fazer nada.

**2. Sintonia** (o dial) — fileira arrastável de cartões. Clique **foca**;
clique no já focado (ou duplo clique) **sintoniza**. Teclado: `↑↓` zapeiam,
`0–9` digitam o canal (dois dígitos, com timeout de 700ms — 1 pode virar 10).

**3. Você agendou** — `GET /api/live/reminders`. Some quando vazio. Cada linha
diz `hoje 21:30` / `amanhã 14:00` / `sábado 20:00`.

**4. A linha do tempo** — janela de **5h começando 45 min atrás** (ver o que
acabou de passar é metade da utilidade de uma grade). A **agulha do "agora"** é
escrita como variável CSS a cada 250ms, sem passar pelo React; o React só é
acordado quando a programação **vira**. Clicar num bloco abre o modal do
programa; se for bloco da casa, abre **a ficha da obra**.

### O modal do programa

Arte, canal, título, subtítulo, `21:30 – 23:15 · 105 min · 2019 · Ação`,
descrição, e — quando o programa foi ligado com segurança a uma obra — a linha
`Esta obra está na sua biblioteca`.

O agendamento (`avisar quando começar` ⇄ `◔ agendado — cancelar`) pede a
permissão de notificação **no clique**, não na carga. E **diz o que o navegador
respondeu**: três frases diferentes para "sem suporte", "bloqueado" e "sem
permissão ainda". Programa que já começou não oferece agendar.

### O player ao vivo

- HLS pelo `ligarHls` compartilhado; canal da casa abre uma sessão de transcode
  com o offset do relógio, e **encerra a sessão anterior** ao virar de programa.
- **Zapear** com `↑↓`: chuvisco + rolagem por ~340ms (a única coisa que
  `prefers-reduced-motion` desliga por completo aqui) e um **banner de canal**
  por 3,4s.
- **Intervalo**: quando o arquivo acaba antes da hora, entra o cartão
  `intervalo · <próximo> · às 22:04` — emendar adiantaria o canal em relação à
  própria grade, que é o que todo mundo está vendo.
- Cromo some após 3s parado, **mesmo pausado**.
- Timeline **sem knob e sem preview**: não há pra onde buscar. Só `● AO VIVO`,
  `no ar há N min` e `a seguir X`.
- Selo do modo (`Remux` / `transcode`), volume, pausar (a transmissão continua),
  `Esc` fecha.

### As fontes (admin)

CRUD de fontes IPTV: nome, URL do `.m3u`, URL do `.xml` (opcional). Cada linha
mostra `N canais`, a data da última importação e o último erro. `importar agora`
dispara `POST /api/live/import` e relê depois de 4s — o import roda em segundo
plano, e reler na hora mostraria a lista velha.

### O aviso de programa (global)

Vive no App e não aqui — o aviso tem que chegar com a aba "ao vivo" **fechada**.

Além de ouvir o barramento, **recupera o aviso perdido**: na abertura ele
pergunta o que está agendado e mostra o que começou há menos de **15 minutos**
(`● JÁ COMEÇOU` em vez de `● COMEÇANDO`). Os avisos já vistos ficam no
`localStorage` (últimos 50), marcados **quando saem da tela**, não quando entram.

---

## 9. Mural — `/mural`

`Mural.tsx` · 811 linhas. Três salas: **mural** · **conversas** · **gente**.

> Decisão de produto registrada: amigo vê o que você está assistindo agora, o
> que largou, o que terminou e suas notas. **Sem chave de privacidade** — a
> amizade **é** o aceite.

### Sala `mural`

1. **Salas de assistir junto abertas** — no topo, porque é a coisa mais viva que
   pode estar acontecendo. `fulano está assistindo junto · <título> · 3 pessoas`
   + `entrar`. **É o convite, e não há tabela de convite**: a sala aberta aparece
   pra quem foi aceito como amigo.
2. **Caixa de post** — 500 caracteres, contador, `Enter` manda e `Shift+Enter`
   quebra linha.
3. **O feed** (`GET /api/feed`) — oito tipos de acontecimento, e **a frase é
   montada no cliente**, porque só o cliente pode dizer "Você" no lugar do seu
   nome:

   | tipo | frase |
   |---|---|
   | `assistindo` | *Fulano está vendo X agora.* (marcado como "agora") |
   | `largou` · `terminou` | *…largou X — no minuto 40.* / *…terminou X.* |
   | `pegou` · `devolveu` · `pediu` | *…pegou X na locadora.* / *…devolveu X — rebobinada.* / *…pediu X de volta.* |
   | `avaliou` | *…avaliou X — ★★★★.* |
   | `postou` | o texto **é** a frase |

   **Só o post** pode ser comentado e apagado — o resto o produto deduziu.
4. **Rodapé de honestidade** — `Só uma das 3 pessoas apareceu por aqui até
   agora.` Um mural em que só uma pessoa fala não está funcionando pela metade:
   está mostrando as coisas como elas são.
5. **Presença**, na lateral (`GET /api/presenca`, recarregada a cada 30s):
   `seus amigos` e `no servidor`, com avatar e `vendo <título>` quando estiver.

### Sala `conversas`

Lista de conversas com prévia da última mensagem e contador de não lidas (o
contador também aparece na aba). Ao abrir, marca como lida. Mensagens com
`Enter` para enviar, rolagem presa no fim. Vazio: *"Você ainda não tem amigos
pra conversar. A aba **gente** resolve isso."*

### Sala `gente`

Busca de pessoas (debounce 250ms) e quatro grupos: **querem ser seus amigos**
(aceitar/recusar) · **seus amigos** (falar · perfil · desfazer) · **esperando
resposta** (cancelar) · **também estão aqui** (adicionar).

Cada linha traz avatar, nome, **o que a pessoa está vendo agora** (cruzado com a
presença que o mural já busca — sem uma segunda consulta), e os atalhos `falar`
e `perfil`. Na **busca** não há atalho de conversa: falar é entre amigos, e
oferecer o botão pra quem vai levar recusa é o produto mentindo pra si mesmo.

---

## 10. Perfil — `/perfil` e `/p/<quem>`

`Perfil.tsx` · 774 linhas (+ `Desafios.tsx` 93 + `Retrospectiva.tsx` 98 +
`Avatar.tsx` 82).

Ir pro perfil **pela barra** é sempre ir pro seu. `/p/<username-ou-id>` abre o de
outra pessoa.

### 10.1 O topo

Capa (a arte de um filme do acervo, com degradê no pé pra o nome ficar legível),
rosto, nome, **título** desbloqueado, bio, tags (`#…`), e o **nível** num selo.

Abaixo: barra da fatia do nível atual (não do total) e
`4.210 XP · faltam 790 pro nível 7 · 12 de 80 conquistas`.

O perfil inteiro é tingido pela **moldura** escolhida, por variável CSS.

### 10.2 O editor (só no seu)

| campo | regra |
|---|---|
| **Título** | `<select>` só com o que foi desbloqueado |
| **Tags** | até 5, das desbloqueadas |
| **Rosto · Capa · Cor** | três galerias; o **trancado aparece** (não é clicável) com `abre com a conquista "X"` — um rosto secreto é um rosto que ninguém persegue |
| **Vitrine** | até 6 obras, **e a ordem é o conteúdo**; setas ‹ › movem, ✕ tira, e uma busca acrescenta |
| **Uma linha sua** | bio de até 140 caracteres |

Salvar dispara o evento `PERFIL_MUDOU`, que faz o cabeçalho reler o rosto e a
moldura sem F5.

`copiar link · /p/<nome>` copia **o endereço da barra**, não uma URL inventada
de compartilhamento.

### 10.3 Desafios (só no seu, e também no "para você")

`GET /api/desafios` — **três** por janela, e eles fazem trabalhos diferentes: um
fácil, um de tema, e um que empurra pra fora do seu gosto.

- Cada um: `□`/`✓`, rótulo, `+N XP`.
- Prazo: `até domingo` / `até amanhã`.
- **Cadência** escolhida pela pessoa — todo dia · 3 em 3 dias · toda semana — e
  **só no perfil**: ajuste não se repete em duas telas.
- **Falhar não custa nada.** A janela fecha, o desafio some, outro é sorteado.
  Sem perda de XP, sem sequência quebrada, sem aviso.

### 10.4 Retrospectiva (só no seu)

`GET /api/retrospectiva`. Não é um painel de números: *"Você terminou 7 filmes do
Villeneuve"*. Blocos sem material **não aparecem**, e o rodapé diz quantos
calaram — `2 capítulos ficaram de fora por não terem o que contar ainda` — em vez
de deixar a pessoa concluir que o Odeon não sabe nada dela.

### 10.5 Vitrine, placar e conquistas

- **Vitrine** — as seis caixas, na sua ordem.
- **Você e seus amigos** — só com 2+ amigos: posição, nome (clicável, leva ao
  perfil), título, nível e XP. Sua linha é destacada.
- **Conquistas** — agrupadas em camadas, na ordem `fáceis · médias · sagas ·
  difíceis · impossíveis · marcos de nível`, cada uma com `N de M`. A trancada
  mostra nome e descrição (mas não os pontos — seriam promessa); a aberta mostra
  `+N XP`.

### 10.6 O avatar

`Avatar.tsx` desenha uma marca **derivada do nome por hash** — cor + uma de
quatro figuras + a inicial — quando não há rosto escolhido. Zero bytes, escala em
qualquer tamanho, e é **o padrão de quem não escolheu**, não um buraco. Um anel
marca quem está assistindo alguma coisa.

### 10.7 A senha (só no seu)

`POST /api/auth/password` — a rota existia desde o M4 e **não tinha cliente
nenhum**: nem web, nem Android. Era alcançável só por `curl`, como as sete da
R16.

`trocar senha` fica ao lado de `editar perfil`, e abre um painel — um de cada
vez, porque são dois assuntos (como você aparece, como você entra) e ninguém lê
os dois ao mesmo tempo. Três campos: senha atual, senha nova, repita a nova.

| recusa | quem decide |
|---|---|
| menos de **8 caracteres** | a tela, por cópia (`SENHA_MINIMA`) — o servidor continua sendo a regra |
| confirmação não bate | a tela |
| nova igual à atual | a tela |
| **senha atual errada** | o servidor, em `401` |

Aquele `401` é a razão de esta ser **a única rota que não passa pelo `json()`**
do `api.ts`: lá, 401 quer dizer *"a sessão acabou"* e limpa o token. Aqui quer
dizer *"você errou a senha antiga"* — e errar a senha antiga não pode custar a
sessão. Pelo caminho comum, um dedo trocado na senha atual jogaria a pessoa na
tela de entrada.

**A troca derruba todos os aparelhos, inclusive este.** O `DELETE` do servidor
não poupa a sessão de quem pediu, embora a nota que ele devolve diga *"as outras
sessões foram encerradas"*. Quem faz a frase virar verdade é o cliente: no
sucesso ele entra de novo, na hora, com a senha nova, e guarda o token novo — os
outros aparelhos saem, este fica. Se essa reentrada falhar, a aba cai no login,
que é o único lugar honesto pra quem ficou sem sessão.

O aviso — *"desconecta os seus outros aparelhos"* — é dito **antes** de trocar.
Pra quem tem TV e celular ligados, esse é o efeito principal do gesto, e
descobri-lo na tela de sucesso seria descobrir tarde.

---

## 11. Revisão — `/revisao`

Duas sub-abas. **A ordem é a recomendação**: pastas primeiro, porque lá uma
decisão vale centenas de arquivos.

### 11.1 Pastas — `Scopes.tsx` · 355 linhas

Lista de pastas pendentes (`GET /api/review/scopes`), com filtro por caminho,
paginação de 50 e cabeçalho `50 de 421 · 7.568 arquivos`.

Cada linha: contagem de pendentes, título sugerido, caminho, `N sem match · M em
dúvida · X já ok · <biblioteca>`, e a **dica dos irmãos** (o que os arquivos já
identificados daquela pasta apontam).

Abrir uma pasta dá o fluxo, **e o passo do meio não é pulável**:

```
exemplos de arquivos → buscar candidato → escolher → [numeração, recursivo] → simular → aplicar
```

- **Numeração**: temporada/episódio (SxxExx) · absoluta (1..N, típica de fansub)
  · não é série.
- **Incluir subpastas** (temporadas).
- **Simular** devolve `N seriam identificados · M ficariam em revisão · K
  chamadas ao provider para P arquivos`, mais uma tabela com até 12 linhas
  (arquivo → SxxExx → título resolvido).
- Só então aparece `aplicar aos P arquivos`.

### 11.2 Arquivos — `Review.tsx` · 392 linhas

A fila por arquivo. Filtros: estado (em dúvida · sem match · as duas),
**candidatos** (`tem candidato — é escolher` / `sem candidato — é o nome do
arquivo` — dois problemas com ações diferentes), nome, e paginação de 50.

Cada cartão mostra:

- O nome do arquivo e **o que o parser entendeu**: título, ano, `S02E05`,
  `ep 137`, grupo de release, `parece anime`.
- A confiança (`63% de confiança` ou `sem score`).
- **Os motivos** de a obra estar ali, quando não vieram de um candidato.
- `buscar de novo` com outro nome · `corrigir o parse` (título/temporada/
  episódio, que salva **e já busca**) · `voltar ao nome do arquivo` · `desfazer`.
- **Os candidatos**, com pôster, provider, score colorido (verde ≥85% · âmbar
  ≥55% · vermelho abaixo) e **a lista de motivos do score**. Só o primeiro leva
  o botão sólido — a lista vem ordenada por confiança justamente porque as
  opções não pesam o mesmo.

> É isto que torna o match auditável, e é o que o Jellyfin nunca mostra.

---

## 12. Pastas — `/pastas` (admin)

`Libraries.tsx` · 417 linhas.

**Lista de bibliotecas**: nome, caminho, `<select>` de tipo (Filmes · Séries ·
Documentários · Stand-up · Shows · Outros), `<select>` de identificador
(automático · só TMDB · só AniList · não identificar), `salvar` — que **só
aparece quando algo mudou** — e `remover` (com aviso de que as obras somem da
biblioteca junto com o histórico, mas os arquivos no disco não são tocados).

**Adicionar pasta** abre uma gaveta com um navegador que parte das **raízes**
montadas no servidor e não deixa sair delas. Cada linha de pasta diz três coisas:

| aparece | responde |
|---|---|
| `47 vídeos` | tem coisa aqui dentro? — contando as subpastas, que é onde episódio mora |
| `parece série` | o que é isto? — palpite do scanner, e só quando os nomes dizem |
| `Séries (DAS0)` | já é do acervo? |

Uma pasta coberta **continua clicável** (navegar por dentro é legítimo), mas o
botão `usar esta pasta` some e no lugar dele fica o motivo: *"ela já está dentro
da biblioteca X"* / *"ela contém a biblioteca X"*. O produto não oferece o que a
validação vai negar.

---

## 13. Admin — `/admin`

`Admin.tsx` · 789 linhas (+ `Convites.tsx` 122). Sete seções, todas telas que
faltavam para rotas que já existiam.

### 13.1 Saúde

**Só mostra o que está torto.** Arquivos que o ffprobe recusa, arquivos sumidos
do disco, horas de grade de TV à frente (vermelho abaixo de 12h), erro por fonte
IPTV, obras esperando revisão, obras sem identificação, sprites faltando. Com
tudo em ordem: *"nada torto por aqui."*

### 13.2 A locadora (as opções que valem pra todo mundo)

| campo | o que a tela explica |
|---|---|
| **Estoque** | caixas expostas **na loja inteira** por semana — não por estante |
| **Prazo** | depois disso a fita volta sozinha — menos se alguém estiver assistindo na hora |
| **Por pessoa** | quantas cada um segura ao mesmo tempo |
| **Escassez** (chave) | ligada: uma cópia por caixa, quem pegou tirou da prateleira. Desligada: ninguém barra ninguém |

`salvar` só aparece quando algo mudou; um erro do servidor **devolve os campos ao
que está gravado**. Com a escassez desligada, a tela avisa que o que já está
emprestado continua exclusivo até voltar.

### 13.3 Pessoas

Tabela com nome, papel, ativo, último login. Ações: **promover/rebaixar**,
**desativar/reativar**, **remover** (com `confirm`). Na sua própria linha não há
botão nenhum — só a palavra `você`.

`+ criar conta` cria um **morador** (vê e assiste tudo) ou um administrador, com
a senha definida ali. O nome do botão é deliberado: o convite, logo abaixo, cria
um **convidado**.

### 13.4 Convites

`emitir convite` (opcionalmente "pra quem é") devolve o código com o aviso na
frente: **aparece uma vez só**, vence em N dias, com `copiar` e `guardei`. O
banco guarda só o SHA-256 — não há "ver de novo", há "emitir outro".

A lista mostra `usado por fulano` / `venceu sem ser usado` / `aberto até 12 ago`,
com `revogar` só no que ainda está aberto.

### 13.5 Aparelhos

Sessões abertas: rótulo, user-agent, visto em, expira em, e `encerrar`.

### 13.6 Trabalhos

Histórico de jobs: tipo, estado (`concluiu` · `cancelado` · `falhou` ·
`interrompido` · `rodando` · `na fila`), início, resumo (`N de M`, ou o erro), e
`cancelar` no que está rodando. Enquanto houver job rodando, a lista — e **só
ela** — recarrega a cada 2s.

### 13.7 Aquecimentos e manutenção

**Aquecimentos** (sagas dos filmes · curiosidades · ficha de produção): um botão
cada, com o estado do último job (`nunca rodou`, `713 de 936 · <atual>`,
`concluiu 04/08 · 21 séries`). O progresso aparece em **Trabalhos**.

**Manutenção** (enriquecer as séries · reparar títulos de episódio · reprocessar
o parse · limpar artwork órfão): **o ensaio é a interface**. Todas rodam em
`ensaiar` primeiro — contam o que fariam e não escrevem nada — e o botão
`executar` só nasce depois, com o número na frente.

---

## 14. As seis sobreposições

Não têm endereço; abrem por cima de qualquer tela.

### 14.1 A ficha (o cartaz) — `Details.tsx` · 1.090 linhas

Abre ao clicar num cartão. `Esc` ou clique no fundo fecha. Erro e carregamento
**também têm moldura** — sem isso, um erro caía fora da tela e o clique parecia
não fazer nada.

**Cabeça**: arte de topo (still → backdrop → pôster), pôster, sobrelinha `série ·
temporada`, título, título original, `2019 · 2h14 · filme` ou `T2 · E5`, e as
ações:

| ação | regra |
|---|---|
| `▸ assistir` / `▸ continuar · faltam 47min` | retomada quando `position > 30s`, não terminado e resta mais de 1 min. Desabilitado sem arquivo |
| `⧉ assistir junto` | abre a sala **e cai dentro dela**; a sala aberta já aparece pros amigos |
| `♥` / `✕` | alimenta o perfil de gosto |
| `✎ editar` | **só admin** — tag, coleção e relação mudam o que todo mundo vê |

Abaixo: barra de retomada, ficha técnica do arquivo (resolução pela **largura**
— um 1080p em 2.35:1 é 1920×818 e chamar de "818p" seria mentir —, codec,
áudio com canais em português, tamanho, container).

**Corpo**, nesta ordem — do que a obra é, pro que ela tem a ver com a sua
estante, pro que você achou:

1. **Sinopse**.
2. **Você sabia** — curiosidades derivadas do grafo e do seu histórico, cada uma
   com ícone por tipo e **crédito com link** quando vem de fonte externa. Nada
   inventado. Quando a obra não rende nenhuma, a seção não nasce.
3. **O que a gente achou** — avaliações: **cinco estrelas** (clicar já grava),
   texto opcional (esse tem botão de guardar), `tirar a nota`, a média com
   `N notas`, e a lista **dos seus amigos** — não uma média global, que seria o
   IMDb com passos extras. Cada review aceita **comentários**.
4. **Elenco** em fileira arrastável (clique leva à biblioteca filtrada pela
   pessoa).
5. **Produção** agrupada por papel · **Tags** · **Coleções** · **Relações** (a
   mesma aresta lida do outro lado aparece como `← relação`).
6. **Editor do grafo** (admin): adicionar/remover tag, adicionar/remover coleção
   manual, criar/apagar relação por busca.

### 14.2 Gerenciar — `Gerenciar.tsx` · 424 linhas

Abre pelo `⋯` do cartão. É o que se faz **com o registro e com o arquivo**, não
com a obra enquanto filme.

- **Arquivo(s)**: caminho clicável que copia, ficha técnica, e o `status` só
  quando não é `probed`.
- **Identificação**: estado em português (`confirmada por uma pessoa` ·
  `automática` · `esperando revisão` · `sem identificação` · `ignorada`),
  confiança, ids externos; busca no provider por **título + ano**, com os
  candidatos e `confirmar`; e `desfazer identificação`.
- **Corrigir o que o parser entendeu**: título, temporada, episódio. Sobrevive a
  nova varredura e a nova identificação — é decisão humana, não resultado.
- **Zona de risco**: `ignorar` (some da biblioteca, não volta na varredura, o
  arquivo fica) e `apagar do disco` — que pergunta ao servidor se pode
  (`/api/storage`), lista os caminhos que vão sumir, e **exige digitar
  "apagar"**.

### 14.3 O player — `Player.tsx` · 824 linhas

A peça mais delicada, e o motivo está no cabeçalho do arquivo: **tempo do
arquivo × tempo da sessão**. Com transcode, o ffmpeg recebe `-ss` e o `<video>`
acha que o filme começa no ponto onde a sessão começou. Tudo aqui trabalha em
`offset + currentTime`, com o total vindo do ffprobe.

| | |
|---|---|
| **plano** | `GET /api/playback/{id}/plan` decide Direct Play · Remux · Transcode a partir do que o navegador declara (`canPlayType`) |
| **entrega** | Direct Play usa `/api/stream/{id}`; o resto abre sessão HLS (`ligarHls`, token **por header** — o `?token=` da playlist não chega nos segmentos) |
| **timeline** | mostra o arquivo inteiro, com as regiões **fora desta sessão** marcadas em vez de escondidas, buffer, knob e **preview de sprites** (folha baixada uma vez, `background-position`, zero requisições ao arrastar) |
| **seek** | fora do que a sessão produziu, avisa em vez de falhar em silêncio |
| **controles** | ↺10s · play/pause · ↻10s · timecode · **legendas** · **selo do modo com "por quê"** · volume · tela cheia |
| **legendas** | faixas de texto entram como `<track>` nativo (sem transcode); ASS/PGS oferecem **queimar**, que preserva o estilo e força transcode — e a tela diz isso |
| **por quê** | o cartão lista os motivos que o servidor deu para o modo escolhido, mais `vídeo copiado bit a bit` / `recodificado` e o encoder |
| **atalhos** | `Esc` fecha · `espaço`/`k` play · `←→` ±10s · `f` tela cheia · `m` mudo |
| **progresso** | heartbeat de 10s enquanto tocando, mais `start` · `pause` · `seek` · `finish` · `abandon` ao desmontar |
| **entre aparelhos** | evento `progress` de outro device corrige a posição quando a diferença passa de 5s |
| **cromo** | some em 3s, **mesmo pausado** (o botão grande de tocar continua no palco) |
| **saída** | fechar encerra a sessão de transcode — senão o ffmpeg fica vivo até o reaper passar |

**Numa sala (assistir junto)**: quem não é host vê um **espelho** — o vídeo
obedece `rodando` e `posicao_segundos` (tolerância de 1,5s) e os controles
**somem** (não ficam desabilitados). O host publica play/pausa/seek pra sala. Cada
participante avisa se está pronto (`readyState >= 2` — *tenho o quadro deste
ponto*), com batida de 20s como sinal de vida.

### 14.4 O menu de DVD — `MenuDVD.tsx` · 677 linhas

Abre **só pela locadora, e só em DVD** — a fita não tem menu, tem rebobinar. O
`▸ assistir` da biblioteca, da busca e da ficha continua indo direto pro filme.

| | |
|---|---|
| **vinheta** | 2,5s, **toda vez**, e qualquer tecla pula. Enquanto ela roda, o menu ainda não existe |
| **clima** | **12**, um por estante da locadora — o servidor manda o índice, e ele vira **som e cor**. Terror é frígio grave; sci-fi é escala de tons inteiros; comédia é maior e staccato |
| **trilha** | sintetizada em Web Audio: pad de duas ondas desafinadas atrás de um filtro passa-baixa + arpejo agendado em frases de 8 notas. **Zero bytes.** Liga/desliga guardado no `localStorage` |
| **fundo** | a cena do filme rodando (sessão HLS muda, com offset), começando só depois de 900ms — abrir e fechar num gesto não deixa ffmpeg pra trás |
| **os itens** | cada um é uma **janela** para o filme: um `<video>` só pintado em quatro `<canvas>` pequenos, cada um recortando uma faixa diferente do quadro |
| **itens** | `Continuar` (com o timecode) · `Do começo`/`Tocar` · `Capítulos` · `Legendas` (ficha, não ação) |
| **capítulos** | grade de miniaturas; a legenda diz a origem — `nos cortes do disco` ou `divididos pelo relógio`. Enquanto carrega, **doze molduras vazias** ocupam o lugar |
| **controle** | setas navegam (4 colunas na grade), `Enter` escolhe, `Esc` volta/fecha. O mouse continua funcionando |

### 14.5 A sala (junto) — `Junto.tsx` · 122 linhas

Painel **ao lado** do filme, não por cima. Traz o título, `encerrar` (host) ou
`sair`, **quem está segurando** (`esperando fulano carregar…` — dizer o nome
transforma uma espera inexplicável numa espera por alguém), a lista de pessoas
com `host` / `carregando` / `sumiu` e o `✕` de expulsar (host), a conversa
**persistida** (quem entra no meio lê o que já foi dito), o campo de recado, e o
rodapé `você manda no play, na pausa e no ponto` / `quem manda é fulano`.

### 14.6 Servidor — `Servidor.tsx` · 226 linhas (admin)

Gaveta com as operações longas, fora da barra de navegação: **Varrer** ·
**Identificar** · **Sprites** · **Embeddings**, cada uma com uma frase dizendo o
que faz e o estado enquanto roda. Mais o mesmo painel de **Saúde** do admin.

O progresso continua aparecendo **no fluxo principal** (as faixas do §1.5):
esconder numa gaveta o aviso de que 17 mil arquivos estão sendo varridos seria
perder exatamente a informação que a implantação ensinou a mostrar.

---

## 15. As regras que valem em toda tela

Elas explicam por que a web parece um produto só, e são o que um app tem que
reproduzir mesmo quando os controles mudam de forma.

| regra | como ela aparece |
|---|---|
| **Linha limpa some** | nada de "0 pendentes", "0 terminadas", "nenhum agendamento". Uma tela que sempre diz alguma coisa ensina a não ser lida — e no dia em que houver número, ele também não será lido |
| **Nada de clique que não faz nada** | botão desabilitado leva o motivo junto; e quando o "não" é regra, o botão **não nasce** |
| **O produto não oferece o que a validação vai negar** | pasta aninhada não tem botão; edição do grafo não existe pra não-admin; título trancado não é selecionável |
| **O número vem com denominador** | `120 de 17.930`, `16 de 113`, `713 de 936`. Um número sem o total convida à conclusão errada |
| **Nada inventado** | curiosidades saem do grafo; a retrospectiva sai do que você assistiu; o ensaio da revista leva o selo do modelo, e sem chave ele não existe |
| **A justificativa é conteúdo** | os motivos da recomendação, os motivos do score do match, os motivos do modo de reprodução |
| **Alma não pode custar enjoo** | tudo desliga em `prefers-reduced-motion` |
| **Moldura vazia em vez de "carregando"** | as prateleiras da locadora, a grade de capítulos, as pistas da grade |
| **Arrastar fileira com o mouse** | `arrasto.ts`, um gancho só para as seis fileiras horizontais. Não mexe em toque — o dedo já rola de nascença. Limiar de 6px, o mesmo do giro da caixa |
| **A cor da obra só toca arte** | `dominant_color` entra em halo, borda e fundo; nunca em texto nem número. Controle é sistema, e sistema é âmbar |
| **Zero bytes** | fonte do sistema, avatar desenhado, ícones em SVG inline, trilha e ruído sintetizados |
| **Uma conexão SSE pro app inteiro** | e o `device_id` descartando o próprio eco |

---

## 16. Checklist de paridade

Uma linha por capacidade, com as rotas que ela consome. **Proposto** como ordem
de leitura, não como ordem de implementação — a sequência decidida está em
[`APP-ANDROID.md`](APP-ANDROID.md) §5.

| # | capacidade | rotas | fase §5 |
|---|---|---|---|
| 1 | entrar · setup · convite · trocar de servidor | `auth/status` `auth/login` `auth/setup` `auth/me` `auth/logout` `convites/resgatar` | 1 |
| 2 | token de mídia e artwork | `auth/media-token` `/artwork/*` | 1 |
| 3 | biblioteca agrupada + paginação + busca | `library` `works` | 1 |
| 4 | filtros (tags, duração, estado, ordem) | `tags` `tag-namespaces` `works` `library` | 1 |
| 5 | série → temporada → episódio | `works?collection=` | 1 |
| 6 | ficha da obra | `works/{id}` `works/{id}/curiosidades` `works/{id}/avaliacao` `works/{id}/credits` | 1–2 |
| 7 | player: plano, HLS, legendas, selo do modo | `playback/{id}/plan` `playback/{id}/session` `hls/{id}` `media/{id}/subtitles/{i}` `stream/{id}` | 2 |
| 8 | preview de seek por sprites | `media/{id}/scrub` `/scrub/*` | 2 |
| 9 | progresso e continuar | `works/{id}/progress` `continue` | 3 |
| 10 | barramento (sincronia entre aparelhos) | `events` | 3 |
| 11 | Cast | `playback/{id}/plan` com o perfil **do Chromecast** | 4 |
| 12 | locadora: vitrine, balcão, pegar/devolver/pedir/rebobinar, a fita | `locadora/estantes` `locadora/prateleira` `locadora/alugar` `locadora/devolver/{id}` `locadora/pedir/{id}` `locadora/rebobinar` `locadora/fita/{id}` | 5 |
| 13 | menu de DVD e capítulos | `works/{id}/menu` `works/{id}/cenas` | 5 |
| 14 | download offline (não existe na web) | `stream/{id}` com Range | 6 |
| 15 | para você: contexto, calibragem, motivos, perfil de gosto | `curation/for-you` `curation/calibrar` `curation/taste` `works/{id}/feedback` | 7 |
| 16 | desafios e XP | `desafios` `desafios/cadencia` `perfil` | pós-v1 |
| 17 | coleções e ordens de exibição | `collections/*` | pós-v1 |
| 18 | guia e revista da semana | `guia` `guia/revista` `guia/pessoas` `people/*` | pós-v1 |
| 19 | ao vivo: pistas, grade, zapear, lembretes | `live/*` | pós-v1 |
| 20 | mural, conversas, amizades, presença | `feed` `posts` `comentarios` `mensagens` `amigos` `pessoas` `presenca` | pós-v1 |
| 21 | assistir junto | `junto/*` | pós-v1 |
| 22 | perfil, enfeites, vitrine, conquistas, retrospectiva, **trocar senha** | `perfil` `retrospectiva` `auth/password` | pós-v1 |
| 23 | revisão por pasta e por arquivo | `review` `review/scopes` `scopes/*` `works/{id}/search|match|parse|reset` | pós-v1 |
| 24 | bibliotecas e navegador de pastas | `libraries` `browse` | pós-v1 |
| 25 | admin: saúde, pessoas, convites, aparelhos, trabalhos, manutenção | `diagnostico` `auth/users` `auth/sessions` `convites` `jobs` `maintenance/*` `locadora/opcoes` | pós-v1 |

---

## 17. O que este documento não cobre

- **O porquê de cada escolha.** Está no `docs/DESIGN.md` do repositório do
  servidor — 7.900 linhas, e as seções dele argumentam sobre os dois lados ao
  mesmo tempo. Os `§NN` citados aqui são de lá.
- **O que muda de forma no Android** (estante 3D, trilha do menu, negociação de
  codec): `APP-ANDROID.md` §3.
- **A ordem de implementação e as decisões do app** (Media3, offline, Cast,
  `minSdk`): `APP-ANDROID.md` §4 e §5.
- **O estado atual do app**: `CONTINUAR-ANDROID.md`.
