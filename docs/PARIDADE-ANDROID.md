# Odeon — o app Android medido contra a web

## Sobre este arquivo

O [`WEB-REFERENCIA.md`](WEB-REFERENCIA.md) é a **régua**: o que a web faz, tela
por tela. Este é a **leitura da régua**: o que o app tem hoje, o que falta, e
qual das duas coisas é atraso e qual é decisão já registrada.

Ele responde uma pergunta só, e é a que o próprio `WEB-REFERENCIA.md` diz existir
pra responder: *"o app já tem isso?"*.

Mesma regra de autoria dos outros documentos:

- **Decidido** — palavra de quem decide.
- **Proposto** — sugestão, pode ser vetada sem discussão.
- **Medido** — número tirado do código, hoje.

Onde não houver marca, é **fato de código**: está escrito em
`android/app/src/main/kotlin` ou em `web/src`, e foi lido para escrever a linha.

Este documento **não decide nada**. Ele não muda a sequência da
[`APP-ANDROID.md`](APP-ANDROID.md) §5, não promove nem adia fase, e não propõe
apagar o que já foi escrito. Onde ele sugere ordem, a sugestão está marcada como
**Proposto** e é isso que ela é.

**Medido em 05/08/2026**, sobre `android/app/src`:

| | |
|---|---|
| arquivos `.kt` (main) | **54** · **13.259 linhas** |
| testes | **9 arquivos** · **817 linhas** · **61 testes**, todos passando |
| rotas declaradas no `OdeonApi` | **25**, de 113 |
| rotas servidas por URL montada (sem Retrofit) | 3 — `/artwork/*`, `/api/stream/*`, a playlist HLS |
| telas | **8** — 5 abas, a ficha, o player e o perfil |
| sobreposições | **1** — a gaveta do "eu" |

⚠️ Os números acima já incluem **as duas rodadas de 05/08/2026**, que mexeram
nesta tabela enquanto ela era escrita. O que elas fizeram está no §10.

E o mesmo recorte do lado da web, pra comparação ficar honesta: **29 arquivos**,
**16.891 linhas**, **11 telas** com endereço próprio e **6** sobreposições.

---

## 0. Índice

| § | o que |
|---|---|
| 1 | O casco: abas, sessão, barramento |
| 2 | Tela por tela — as onze da web, e o que existe de cada |
| 3 | As regras do §15, e como o app se sai nelas |
| 4 | O checklist de paridade, linha a linha |
| 5 | O que o app tem e a web não |
| 6 | Os furos que estão dentro da v1 |
| 7 | A inversão de ordem, registrada |
| 8 | Campos que chegam e ninguém desenha |
| 9 | Um desalinhamento de contrato dentro do app |
| 10 | A rodada de 05/08/2026 — o que ela mudou nesta tabela |
| 11 | A caixa em 3D — o que existe hoje, e o que foi **decidido** |

---

## 1. O casco

### 1.1 Os endereços, e as abas

A web tem **sete** entradas de navegação à esquerda e mais quatro telas atrás da
gaveta de manutenção. O app tem **cinco abas** e um atalho:

| web (§1.1) | app |
|---|---|
| para você | ✅ aba, a última |
| biblioteca | ✅ aba, a primeira |
| coleções | ❌ não existe |
| locadora | ✅ aba |
| guia | ✅ aba |
| ao vivo | ❌ não existe |
| mural | ✅ aba |
| perfil | ◐ existe, e chega-se por **um toque no próprio rosto** — a gaveta do canto, como na web |
| revisão · pastas · admin | ❌ nenhuma das três |
| — | **baixados**, que a web não tem: atalho no cabeçalho da biblioteca |

A ordem das abas e o corte de baixados estão argumentados em
[`AppOdeon.kt`](../android/app/src/main/kotlin/dev/odeon/android/ui/AppOdeon.kt)
— seis abas dariam 68,5dp cada, e «biblioteca» ocupa 61dp a 12sp. É decisão do
app, medida, e não uma falta.

**O que não tem equivalente nenhum:** a gaveta de manutenção (com a pílula de
obras esperando revisão), o holofote, o condensar por rolagem e a marca que pulsa
durante varredura. A gaveta "eu" e a busca **passaram a existir em 05/08/2026** —
a primeira no canto de cima à direita, com o rosto, o anel do nível e o selo; a
segunda como última linha do cabeçalho da biblioteca, e não na barra, porque aqui
não há barra. No lugar da barra da web há a **barra do facho**
([`Facho.kt`](../android/app/src/main/kotlin/dev/odeon/android/ui/Facho.kt)), que
é peça própria do app e não tenta ser a mesma coisa.

### 1.2 Sessão, papéis e tokens

Esta é a parte em que o app está **em dia**.

| web (§1.3) | app |
|---|---|
| token de sessão em `localStorage`, `Bearer` | ✅ DataStore, em claro (decisão do §4 da espec) |
| token de mídia separado e curto (8h), `?token=` | ✅ e **sem renovar sozinho** — §43 respeitado no `garantirTokenDeMidia` |
| `DEVICE_ID` guardado, pra descartar o próprio eco | ✅ existe no `Cofre` e viaja na marca de progresso |
| papéis `admin` · `user` · `guest` | ✅ o `Usuario` traz `role` e o `eAdmin` |
| endereço da API digitado, https antes de http | ✅ `EnderecoDoServidor` + `procurarServidor` |
| mixed content explicado | n/a — não há página https hospedando o app |

✅ E o eco do `device_id` **passou a ter o que descartar** — ver §1.3.

### 1.3 O barramento — **existe desde 05/08/2026**

Era a falta mais estrutural do app: o item **10** do checklist, fase **3**, com as
fases 4 a 7 feitas por cima dele. Agora é uma conexão SSE pro app inteiro
([`Barramento.kt`](../android/app/src/main/kotlin/dev/odeon/android/dados/Barramento.kt)),
no `OdeonApp` ao lado do OkHttp — cinco tentativas de reconexão com espera
crescente, token de mídia na query e o **eco do próprio aparelho descartado** pelo
`device_id`.

| evento | quem ouve, hoje |
|---|---|
| `progress` | ✅ a fileira de continuar, a ficha aberta e **o player**, que persegue a posição do outro aparelho quando a diferença passa de 5s |
| `locadora` | ✅ a loja recarrega e o recado aparece por 6s |
| `mensagem` · `junto` · `programme_starting` · `match_finished` · `scrub_finished` | chegam como `Outro` e ninguém ouve — não há mural que escreva, sala, ao vivo nem faixa de servidor |

⚠️ Os cinco sem ouvinte **chegam mesmo assim**, de propósito: um `when` que
engole tipo desconhecido é como um evento novo do servidor nunca aparece pra quem
for escrever a tela.

### 1.4 As faixas que aparecem por cima de qualquer tela

Nenhuma das seis do §1.5 existe: sem aviso de `TMDB_API_KEY`, sem faixa de
varredura, sem faixa de identificação, sem faixa de erro global, sem aviso de
programa. As cinco primeiras são de administração e o app não tem admin; a sexta
depende do ao vivo.

O que existe no lugar, e é do app: o erro **por tela**, escrito onde a falha
aconteceu (a biblioteca põe a frase fora da grade justamente pra rolar não a
esconder).

### 1.5 Listagem e paginação

| web (§1.7) | app |
|---|---|
| `/api/library` agrupada | ✅ é a única fonte da grade |
| `/api/works` plana, dentro de coleção | ✅ é a fonte de dentro da série |
| `PAGE_SIZE = 120` + `carregar mais` | ✅ **60**, e rolagem infinita em vez de botão |
| `"120 de 17.930"` | ✅ `"N de M"`, e some enquanto o total é nulo |

---

## 2. Tela por tela

### §2 Entrar — **parcial**

| web | app |
|---|---|
| entrar (usuário e senha) | ✅ |
| campo de servidor, com https antes de http | ✅ e melhor: é o **primeiro tempo** da tela (§6 do `CONTINUAR-ANDROID.md`) |
| primeira execução (`needs_setup`) | ❌ o `needs_setup` vira **uma frase de erro** ([`ModeloDeLogin.kt:64`](../android/app/src/main/kotlin/dev/odeon/android/ui/login/ModeloDeLogin.kt:64)) |
| entrar com convite | ❌ |
| erro genérico no login | ✅ |
| **sair** | ✅ desde 05/08/2026, na gaveta do canto |
| trocar de servidor | ◐ sair **não** esquece o endereço, e é decisão do `Cofre`: «quem sai da conta quase nunca quer redigitar o endereço da própria casa» |

### §3 Para você — **parcial**

| web | app |
|---|---|
| chips `Tenho` (tempo) | ✅ os seis, e o servidor é quem repergunta |
| chips `Pra` (humor, das tags `mood:`) | ❌ |
| termômetro de conhecimento (0 a 1) | ◐ existe o `aindaNaoTeConhece` do servidor, que vira uma frase — não há a conta dos sinais nem as três marcas |
| continue de onde parou | ✅ **mas na biblioteca**, não aqui — é o herói da chegada |
| calibragem: 6 capas com ♥ e ✕ | ❌ e com ela some o `works/{id}/feedback` inteiro |
| herói · também hoje · a fila | ✅ herói e cartões |
| **o motivo em todo item** | ✅ e o modelo **descarta** o que vem sem motivo |
| número de afinidade (score×100) | ❌ |
| TasteInspector | ❌ |
| desafios | ❌ (são §10.3, pós-v1) |

O eixo do tempo e o motivo legível são a tese da tela, e os dois estão lá. O que
falta é o que **alimenta** a curadoria — sem ♥/✕ e sem calibragem, o app consome
o perfil de gosto e nunca escreve nele.

### §4 Biblioteca — **parcial, e é aqui que dói**

| web | app |
|---|---|
| grade agrupada, paginada, com `N de M` | ✅ |
| continuar assistindo | ✅ (herói + fileira) |
| **busca** | ✅ desde 05/08/2026 — campo no cabeçalho, debounce de 250ms, e o herói e a fileira de continuar somem enquanto se busca |
| ordenar por (6 opções) | ✅ num menu, e não seis chips |
| filtros por tag, com contagem | ✅ agrupados por `tag-namespaces`, com a contagem em cada chip |
| modo das tags (qualquer ⇄ todas) | ✅ e o chip **só nasce com 2+ tags**, como na web |
| duração (curto · médio · longo) | ✅ |
| identificação (5 estados, inclusive ignoradas) | ✅ |
| atalho `N sem identificar →` | ❌ não há revisão pra onde mandar (§53) |
| chip "Dentro de" | ✅ · **"Com"** (pessoa) ❌, porque não há como filtrar por pessoa ainda |
| **entrar na série → temporada → episódio** | ✅ o cartão de série vira filtro de coleção, como na web |
| `EntryCard` de série (selo `N ep`, barra de vistos) | ◐ o cartaz diz `N episódios`; não há barra de terminados da série |
| `Card` de obra (badges, progresso, metadados) | ✅ progresso e `1969 · 816p · 2h22`; **sem** os badges `sem metadata` / `revisar 63%` |
| `EpisodeCard` com o `still` 16:9 | ✅ com o código `S01E01`, o progresso e o visto apagado |
| `⋯` → Gerenciar | ❌ |

Duas linhas desta tabela são fase **1** do §5 da espec: a busca (item 3) e o
filtro (item 4). A terceira — série → temporada → episódio — é o item 5, também
fase 1.

### §5 Coleções — **não existe**

Nenhuma rota `collections/*` é falada. Item 17, pós-v1. Sem observação.

### §6 Locadora — **parcial, e falta a metade que é a ideia**

O que existe:

- a **vitrine** (`/api/locadora/estantes`), com estantes, placa `16 de 113`,
  tábua de madeira e o halo da luz da loja;
- `comigo` e `na mão de alguém`, das `/api/locadora/prateleira`;
- **pegar** — mas pela ficha da obra, não pela caixa;
- **devolver**, com o arrasto de 96dp e a confirmação por gesto;
- a linha de regras (escassez, limite, prazo, `posso pegar`);
- `a vitrine vira <quando>`, pela mesma palavra que o guia usa;
- a caixa como objeto: lombada, três quartos, verso com quem levou.

O que não existe:

| web (§6) | app |
|---|---|
| **rebobinar, e a tela da fita** | ❌ — e a §6 chama isso de «o atrito que é a ideia» |
| **pedir de volta** | ❌ o `pedido_por_nome` é **lido e mostrado**, mas não há como pedir |
| o balcão: chips de pessoa, fama `✕N` / `⟲N` | ❌ |
| devoluções recentes (`fulano devolveu rebobinada`) | ❌ o campo chega e não é desenhado — ver §8 |
| recado ao vivo (6s) | ❌ depende do barramento |
| prazo na caixa (`3 dias`, `vence amanhã`, vermelho a 2) | ❌ o `vence_em` chega e não é desenhado |
| corte VHS × DVD (`ultimo_ano_vhs`) | ❌ o campo chega e não é usado |
| as três contagens da porta da loja | ◐ falta o `no_acervo` (`de 600 no acervo`) |
| a caixa na mão: `voando → na-mao → abrindo → midia` | ❌ há o verso, não há o palco |
| escassez barrando a abertura da caixa | ❌ |

**A profundidade 3D não entra nesta conta**: a §3 da espec já decidiu que a
estante do app é 2D e que o giro vira `flip`. Isso está feito e não é falta.

### §7 Guia — **quase inteiro, e travado por outra tela**

| web (§7) | app |
|---|---|
| a revista da semana: eixo, tema, filmes | ✅ |
| o ensaio, com `escrito por <modelo>` | ✅ e só quando existe |
| selo `visto` nos filmes | ✅ como borda acesa, não como selo |
| o evento coletivo, com quem participou | ✅ |
| eixos de pessoa: direção · elenco · trilha | ✅ as três fileiras |
| gêneros · décadas · países + `fora de Hollywood` | ✅ |
| **tocar num eixo filtra a biblioteca** | ❌ e o comentário da tela diz por quê: a biblioteca não tem filtro |
| `ver as 1.191` — a lista completa do eixo | ❌ |
| **a ficha da pessoa** (filmografia agrupada, seu histórico) | ❌ |

O guia é a tela pós-v1 mais completa do app — e é também a que mais depende de
uma tela de fase 1: sem o filtro da biblioteca, metade dos seus toques não leva a
lugar nenhum, e por isso não são oferecidos (§53).

### §8 Ao vivo — **não existe**

Nenhuma rota `live/*`. Item 19, pós-v1.

### §9 Mural — **só a primeira das três salas, e só de leitura**

| web (§9) | app |
|---|---|
| o feed, com a frase montada no cliente | ✅ os acontecimentos, com «você» no lugar do seu nome |
| escrever post | ❌ |
| comentar / apagar post | ❌ |
| salas de assistir junto abertas | ❌ |
| presença lateral (30s) | ❌ |
| rodapé de honestidade (`só 1 das 3 apareceu`) | ❌ |
| sala **conversas** | ❌ |
| sala **gente** (amizades, busca, pedidos) | ❌ |

### §10 Perfil — **parcial desde 05/08/2026, e é de leitura**

| web (§10) | app |
|---|---|
| capa, rosto, nome, título, bio, tags | ✅ e cada linha some quando não há (§24) |
| nível, barra da **fatia**, `N XP · faltam M · X de Y conquistas` | ✅ |
| a moldura tinge a tela | ✅ e tinge também a insígnia em toda aba |
| vitrine | ✅ leitura, na ordem escolhida |
| placar de amigos (só com 2+) | ✅ com a sua linha destacada |
| conquistas por camada, `N de M` | ✅ e a trancada **não mostra os pontos** |
| avatar desenhado por hash (§10.6) | ✅ e com **o mesmo hash da web**, testado |
| **o editor** (título, tags, rosto, capa, cor, vitrine, bio) | ❌ é um `PUT` que o app não manda |
| **desafios** (§10.3) | ❌ outra rota |
| **retrospectiva** (§10.4) | ❌ outra rota |
| `/p/<quem>` — o perfil de outra pessoa | ❌ e não há por onde chegar: o mural não tem a sala `gente` |

Item 22 é pós-v1, e continua sendo: **isto não foi promover a fase.** A tela
entrou porque a insígnia pedida no canto sai de `GET /api/perfil`, que devolve o
perfil inteiro — e um item de menu chamado `perfil` que não abre nada seria o
§8b. Ver o §10.

### §11 Revisão · §12 Pastas · §13 Admin — **não existem**

Itens 23, 24 e 25, pós-v1. São as telas de manutenção, e o app não tem nem a
gaveta que levaria a elas.

### §14 As seis sobreposições

| | estado |
|---|---|
| **14.1 A ficha** | ◐ **parcial** — ver abaixo |
| **14.2 Gerenciar** | ❌ |
| **14.3 O player** | ✅ **a peça mais completa do app** |
| **14.4 O menu de DVD** | ❌ (a trilha sintetizada está vetada pela §3; o menu, não) |
| **14.5 A sala (junto)** | ❌ |
| **14.6 Servidor** | ❌ |

#### 14.1 A ficha, em detalhe

Ela é tela empilhada no app, não sobreposição — decisão de plataforma, e não
falta.

| web | app |
|---|---|
| arte de topo, pôster, título, original, ano, duração | ✅ e com paralaxe por inclinação |
| `▸ assistir` / `▸ continuar` | ✅ — **sem** o `faltam 47min` |
| desabilitado sem arquivo, com o motivo | ✅ `sem arquivo no acervo` |
| barra de retomada | ❌ |
| ficha técnica do arquivo | ✅ nas versões |
| selo do modo de reprodução | ✅ **e a web não tem isso na ficha** — só no player |
| sinopse | ✅ |
| tags | ✅ |
| `⧉ assistir junto` | ❌ |
| `♥` / `✕` | ❌ |
| `✎ editar` (admin) | ❌ |
| **Você sabia** (curiosidades) | ❌ |
| **O que a gente achou** (estrelas, texto, amigos, comentários) | ❌ |
| elenco em fileira | ❌ |
| produção · coleções · relações | ❌ |
| baixar pra ver sem rede | ✅ **e a web não tem** |
| pegar a fita na locadora | ✅ com háptico de `LongPress` |

#### 14.3 O player, em detalhe

| web | app |
|---|---|
| plano (Direct Play · Remux · Transcode) | ✅ e as capacidades vêm do `MediaCodecList`, não de um `canPlayType` |
| entrega: `/api/stream` ou HLS | ✅ |
| timeline com buffer e knob | ✅ |
| **preview de sprites** | ✅ folha baixada uma vez |
| regiões fora da sessão marcadas | ❌ |
| legendas | ✅ faixas nativas |
| queimar ASS/PGS, forçando transcode | ❌ |
| selo do modo **com o "por quê"** | ✅ |
| progresso: heartbeat + `start`/`pause`/`seek`/`finish`/`abandon` | ✅ |
| correção entre aparelhos (evento `progress`) | ❌ sem barramento |
| encerrar a sessão de transcode ao sair | ✅ |
| cromo some em 3s, mesmo pausado | ✅ |
| espelho de sala (assistir junto) | ❌ |
| — | ✅ **PiP**, sessão de mídia, Cast, gestos de brilho e volume: nada disso existe na web |

---

## 3. As regras do §15

Elas são o que faz a web parecer um produto só, e são a parte em que o app está
mais perto da régua — em vários casos por escrito, com o número medido no
comentário.

| regra (§15) | como o app se sai |
|---|---|
| **linha limpa some** | ✅ está em quase todo `if` do app, citando o §24 |
| **nada de clique que não faz nada** | ✅ e o caso mais forte é o guia: o eixo **não é clicável** porque não há filtro pra onde levar |
| **o produto não oferece o que a validação vai negar** | ✅ §53, inclusive no atalho de launcher que só vale com sessão |
| **o número vem com denominador** | ✅ `N de M` na grade, `16 de 113` na estante |
| **nada inventado** | ✅ o ensaio leva o selo do modelo; sem dado, a seção não nasce |
| **a justificativa é conteúdo** | ✅ motivos da recomendação e motivos do modo de reprodução |
| **alma não pode custar enjoo** | ◐ `ANIMATOR_DURATION_SCALE` é respeitado na inclinação e na marquise; **não há uma varredura que confirme as demais animações** |
| **moldura vazia em vez de "carregando"** | ◐ as tábuas da locadora sim; a biblioteca e o guia usam `CircularProgressIndicator` |
| **arrastar fileira com o mouse** | n/a — no celular o dedo já rola |
| **a cor da obra só toca arte** | ✅ `dominant_color` em lavagem e fundo, nunca em texto |
| **zero bytes** | ◐ ícones em vetor e desenho próprio; **não há avatar** porque não há perfil |
| **uma conexão SSE pro app inteiro** | ❌ zero conexões |

---

## 4. O checklist de paridade, linha a linha

As 25 capacidades do §16 da referência, com o estado medido hoje. A coluna
"fase" é a da [`APP-ANDROID.md`](APP-ANDROID.md) §5 e **não** é reordenada aqui.

| # | capacidade | fase | estado |
|---|---|---|---|
| 1 | entrar · setup · convite · trocar de servidor | 1 | ◐ entrar, o endereço e **sair**. Sem setup e sem convite |
| 2 | token de mídia e artwork | 1 | ✅ |
| 3 | biblioteca agrupada + paginação + busca | 1 | ✅ |
| 4 | filtros (tags, duração, estado, ordem) | 1 | ✅ |
| 5 | série → temporada → episódio | 1 | ✅ |
| 6 | ficha da obra | 1–2 | ◐ sinopse, tags, versões e selo. Sem curiosidades, avaliações, elenco, coleções, relações |
| 7 | player: plano, HLS, legendas, selo | 2 | ✅ |
| 8 | preview de seek por sprites | 2 | ✅ |
| 9 | progresso e continuar | 3 | ✅ |
| 10 | **barramento** | 3 | ✅ |
| 11 | Cast | 4 | ✅ com o perfil do Chromecast na negociação (§4c) |
| 12 | locadora completa | 5 | ◐ vitrine, empréstimos, pegar e devolver. **Sem rebobinar, sem a fita, sem pedir de volta, sem balcão** |
| 13 | menu de DVD e capítulos | 5 | ◐ vinheta, climas, itens e capítulos ✅; **sem a trilha** (vetada na §3) e sem o filme rodando de fundo |
| 14 | download offline | 6 | ✅ com o prazo viajando junto do arquivo |
| 15 | para você | 7 | ◐ tempo e motivos. Sem humor, calibragem, ♥/✕ e perfil de gosto |
| 16 | desafios e XP | pós-v1 | ❌ |
| 17 | coleções e ordens de exibição | pós-v1 | ❌ |
| 18 | guia e revista da semana | pós-v1 | ◐ **feito** — falta a ficha da pessoa e o toque no eixo |
| 19 | ao vivo | pós-v1 | ❌ |
| 20 | mural, conversas, amizades, presença | pós-v1 | ◐ **só o feed, só leitura** |
| 21 | assistir junto | pós-v1 | ❌ |
| 22 | perfil, enfeites, vitrine, conquistas | pós-v1 | ◐ **leitura feita** — falta o editor, e os desafios e a retrospectiva são outras rotas |
| 23 | revisão por pasta e por arquivo | pós-v1 | ❌ |
| 24 | bibliotecas e navegador de pastas | pós-v1 | ❌ |
| 25 | admin | pós-v1 | ❌ |

**Medido em 05/08/2026:** das 15 capacidades da v1, **10 estão inteiras** e **5
parciais**. **Nenhuma falta inteira** — que é o marco que este documento nasceu
pra medir: de manhã eram quatro sem nada.
Das 10 pós-v1, **3 foram começadas** e 7 não.

Na primeira redação deste documento, algumas horas antes, eram 6 · 5 · 4 e 2 · 8.
A diferença são as duas rodadas do §10.

---

## 5. O que o app tem e a web não

Vale contar, porque paridade não é dívida de mão única — e três destes itens são
justamente o que a §5 da espec chamou de *"o que só faz sentido no celular"*.

| | onde |
|---|---|
| **download offline**, com o prazo da fita viajando com o arquivo | `midia/ServicoDeDownload.kt`, `dados/Downloads.kt`, `RelogioQueNaoVolta.kt` |
| **Picture-in-Picture** e sessão de mídia | `TelaDoPlayer.kt`, `midia/ServicoDeMidia.kt` |
| **Cast**, com o perfil do Chromecast na negociação | `dados/Cast.kt`, `midia/OpcoesDeCast.kt` |
| **widget de continuar** na tela inicial | `widget/WidgetDeContinuar.kt` |
| atalhos de launcher por aba | `res/xml/atalhos.xml` |
| gestos: brilho e volume no player, arrasto pra devolver | `TelaDoPlayer.kt`, `TelaDaLocadora.kt` |
| háptico com dois pesos (pegar × girar) | `TelaDaObra.kt`, `TelaDaLocadora.kt` |
| paralaxe por inclinação do aparelho | `ui/Inclinacao.kt` |
| transição de elemento compartilhado grade → ficha | `AppOdeon.kt` |
| o selo do modo de reprodução **na ficha**, antes do toque | `TelaDaObra.kt` |

---

## 6. Os furos que estão dentro da v1

**Proposto** — é uma ordem de leitura, não uma decisão de sequência. A decisão é
de quem decide, e a sequência oficial continua sendo a §5 da espec.

1. ~~**`sair`**~~ — feito em 05/08/2026, na gaveta do canto (§10).
2. ~~**A busca na biblioteca**~~ — feita em 05/08/2026 (§10).
3. ~~**O filtro da biblioteca**~~ — feito em 05/08/2026 (§10).
4. ~~**Entrar na série**~~ — feito em 05/08/2026 (§10).
5. ~~**O barramento**~~ — feito em 05/08/2026 (§10).
5b. ~~**O menu de DVD**~~ — feito em 05/08/2026 (§10), sem a trilha.
6. **A fita e o rebobinar.** É o que a referência chama de «o atrito que é a
   ideia» — e é o que separa a locadora do app de uma lista de empréstimos com
   arte bonita.
7. ~~**O toque no eixo do guia**~~ — feito em 05/08/2026 (§10).
8. **A caixa em 3D, girada com o dedo** — **Decidido** em 05/08/2026, e o item
   6 desta lista (a fita) passa a ser um pedaço dele. O estado atual, o caminho
   técnico e a ordem das peças estão no §11.

---

## 7. A inversão de ordem, registrada

Ela não é acusação, é fato de código, e vale estar escrita porque explica o
formato desta tabela:

> **Mural e guia foram construídos — os dois são pós-v1 — enquanto busca, filtro,
> série → episódio e o barramento, que são fase 1 e fase 3, continuam de fora.**

E os dois entraram **só de leitura**: o mural não posta nem comenta, o guia não
filtra. O guia é o caso mais claro do custo: ele está quase inteiro e depende de
uma tela de fase 1 pra que metade dos seus toques exista.

---

## 8. Campos que chegam e ninguém desenha

O padrão que já apareceu cinco vezes nesta história — «o servidor já dava e o
cliente não pegava» — tem hoje mais quatro casos, **todos já mapeados no
`Modelos.kt`**, o que os torna baratos:

| campo | rota | o que ele desenharia |
|---|---|---|
| `Prateleira.devolvidas` | `locadora/prateleira` | as devoluções do balcão (`fulano devolveu rebobinada`) |
| `Prateleira.ultimo_ano_vhs` | `locadora/prateleira` | o corte VHS × DVD, que decide se a caixa rebobina |
| `Emprestada.vence_em` | `locadora/prateleira` | o prazo na caixa (`3 dias`, `vence amanhã`) |
| `Loja.no_acervo` | `locadora/estantes` | o `de 600 no acervo` da porta da loja |
| `CaixaExposta.temporadas` | `locadora/estantes` | o `N temporadas` na caixa de coleção |

Os cinco são da locadora, e os cinco somados são boa parte do §6 que falta.

---

## 9. Um desalinhamento de contrato dentro do app

[`OdeonApi.kt:55`](../android/app/src/main/kotlin/dev/odeon/android/dados/OdeonApi.kt:55)
ainda diz, sobre `locadora/liberadas`:

> «⚠️ Tem que ser perguntada **antes** de desenhar o play (§66), inclusive pro
> admin.»

Isso foi **revogado pelo §71** em 04/08/2026 — a biblioteca é modo livre, e o
`RepositorioOdeon` já traz a correção escrita logo abaixo da mesma rota. São duas
verdades opostas sobre a mesma linha, no mesmo repositório, e a errada é a que
está mais perto de quem for chamar a rota.

Não foi corrigido aqui: este documento **descreve**, e mexer no comentário é
mexer no código.

---

## 10. A rodada de 05/08/2026

Ela nasceu deste documento e mexeu nele — por isso está registrada aqui, e não
só no histórico do git.

**O que foi pedido:** «no canto superior direito a foto redonda do profile e o
nível igual temos no web, assim ele clica lá e cria um dropdown com perfil e
sair» — e, dos dois primeiros furos do §6, a **busca** como campo do cabeçalho,
rolando junto com ele.

**O que entrou:**

| | onde |
|---|---|
| a insígnia: rosto, anel do nível e selo | `ui/Insignia.kt` |
| a marca desenhada por hash, com **o mesmo hash da web** | `ui/Insignia.kt` · `ui/Cor.kt` (OKLCh → sRGB) |
| a gaveta do canto, com `perfil` e `sair` | `ui/perfil/GavetaDoEu.kt` |
| o perfil de leitura | `ui/perfil/TelaDoPerfil.kt` |
| `GET /api/perfil` e os modelos | `dados/OdeonApi.kt` · `dados/Modelos.kt` |
| a busca, com debounce de 250ms | `ui/biblioteca/*` |
| 10 testes novos | `PerfilTest` · `InsigniaTest` |

**A regra que decidiu o tamanho:** uma rota só. Tudo o que a tela do perfil
desenha sai de `GET /api/perfil`, que já tinha de ser chamada pra a insígnia
existir. Nada foi adicionado ao contrato pra a tela ficar mais completa — o
editor, os desafios e a retrospectiva ficaram de fora **por serem outras rotas**,
e não por serem menos importantes.

### A terceira rodada: a caixa em 3D

O §11 deixou de ser plano e virou código. O que entrou:

| | onde |
|---|---|
| a projeção: oito vértices, **uma câmera**, quatro homografias | `ui/locadora/Projecao.kt` |
| a caixa com as três faces coerentes e o **giro no dedo** | `ui/locadora/CaixaEm3D.kt` |
| o **palco**: caixa na mão, abrir pela aresta, mídia saindo | `ui/locadora/Palco.kt` |
| o **disco** e a **fita**, desenhados — zero bytes | `ui/locadora/Midia.kt` |
| a **tela da fita** e o rebobinar proporcional | `ui/locadora/Palco.kt` |
| `locadora/fita/{obra}` · `locadora/rebobinar` · `locadora/pedir/{id}` | `dados/OdeonApi.kt` |
| 11 testes novos, sobre a geometria | `ProjecaoTest` |

**A decisão técnica que fez caber:** não é OpenGL nem `Matrix` 4×4 do Compose —
é `setPolyToPoly` do Android. Os quatro cantos de cada face são projetados à mão
e viram uma homografia. A `Matrix` 4×4 foi a primeira tentativa e **a prateleira
apareceu vazia**: a conversão do Compose pra `android.graphics.Matrix` recusa
componente em `z`, que é exatamente o que uma caixa tem.

E projetar à mão trouxe o que uma matriz montada dentro de um `Composable` nunca
teria: a geometria virou **função pura, testada**. O teste que importa é o que
prova o conserto — as arestas de capa e lombada coincidem em **qualquer** pose, e
não só na de repouso, porque as duas leem o mesmo canto do mesmo objeto.

### A quarta rodada: pagar as dívidas que a terceira abriu

Tudo pequeno, tudo do §8 — campos que chegavam do servidor e ninguém desenhava:

| | o que passou a existir |
|---|---|
| `Emprestada.vence_em` | o **prazo** na caixa: `5 dias`, `vence amanhã`, `vence hoje`, `venceu` — vermelho a dois dias, como na web |
| `pedir de volta` | o **botão**, nas caixas dos outros, e ele some quando alguém já pediu (§53) |
| `Prateleira.devolvidas` | a seção **«acabou de voltar»**: `fulano devolveu Tetris — rebobinada` |
| `Prateleira.ultimo_ano_vhs` | o selo **`VHS`/`DVD`** no pé da lombada, com o corte vindo do servidor |

E, fora da locadora, **a ponte do guia**: tocar num eixo leva à biblioteca já
filtrada. Gênero e país viram `tags=[genre:Terror]` + `kind=movie`; década vira
`year_from`/`year_to` com ordem por ano. O `chave` já vinha no dado desde sempre,
esperando quem o recebesse — o comentário que dizia «a próxima coisa óbvia a
fazer nesta tela é o outro lado dessa ponte» virou código e saiu.

**Medido no aparelho:** tocar em `1990` no guia abriu a biblioteca em **60 de
114**, com `filtros 2`, ordem `ano`, e *007: O Mundo Não é o Bastante (1999)* na
frente.

⚠️ **Um defeito novo, encontrado ao verificar:** a insígnia do canto fica **por
cima** do conteúdo que rola, e no canto superior direito ela ganha o toque. Na
prática: uma pílula do guia que role até ali não é mais clicável. São 48dp de
tela, e o conserto pode ser tanto recuar o conteúdo quanto esconder a insígnia ao
rolar — fica anotado, não decidido.

### A quinta rodada: o barramento

O último item de fase 3, e o que fazia a §1.3 deste documento existir. O que
entrou está descrito lá em cima; o que vale registrar aqui são as três decisões:

1. **Uma conexão, no `OdeonApp`.** Uma por tela seriam cinco SSE contra o servidor
   de casa, e cinco reconexões a cada piscada de wifi.
2. **Ligada da composição, não do `Application`.** Só a composição sabe que há
   sessão; o `Application` nasce antes do login, e conectar sem token é abrir
   conexão pra tomar 401. Como efeito colateral desejado, sair do app fecha a
   conexão — um SSE vivo em segundo plano é o servidor segurando linha pra
   ninguém.
3. **O eco descartado no barramento, e não em quem ouve.** Se o descarte
   estivesse no player, ele perseguiria a própria posição de um segundo atrás,
   pra sempre.

**Medido no aparelho:** a conexão abriu sem um aviso no log, e a seção «acabou de
voltar» apareceu com **8 devoluções reais** — `sam devolveu 007 Contra a
Chantagem Atômica — rebobinada`. O parser das frases tem 4 testes.

### A sexta rodada: o menu de DVD

O último item de v1 que não existia. Ele abre **só pela locadora e só em disco** —
«a fita não tem menu, tem rebobinar» —, e o `▸ assistir` da biblioteca continua
indo direto pro filme.

| a web tem | aqui |
|---|---|
| vinheta de 2,5s, qualquer tecla pula | ✅ e qualquer toque pula |
| doze climas, um por estante | ✅ **em cor e forma** — quatro vinhetas pra doze climas, como lá |
| a trilha sintetizada | ❌ **vetada na §3 da espec** (Web Audio → `AudioTrack`) |
| o filme rodando de fundo, e os itens como janelas pra ele | ◐ backdrop com deriva; a cena viva abriria uma sessão de HLS só pro menu |
| `Continuar` · `Do começo` · `Capítulos` · `Legendas` | ✅ e `Continuar` **não nasce** sem posição (§24) |
| grade de capítulos com a origem dita | ✅ com doze molduras vazias enquanto carrega |

⚠️ **O índice do clima é o contrato**: ele é a posição na lista `ESTANTES` do
servidor. A tabela daqui guarda os mesmos doze na mesma ordem, com a tinta e a
vinheta — os cinco campos do sintetizador ficaram de fora, e os nomes ficaram pra
o dia em que o som entrar não precisar reescrever a tabela.

**Medido no aparelho:** *Juno* abriu com «2007 · Comédia», `Tocar`, `Capítulos
12`. E *As Aventuras de Ichabod* (1949) **não** abriu menu — caiu na ficha, que é
o certo: pelo corte do servidor ele é fita, e fita não tem menu.

**Mais um defeito de screenshot:** o fundo passava por cima do menu — «Tocar»
disputando espaço com uma camiseta vermelha. Era o meio da lavagem a 34%, o mesmo
erro que a ficha já tinha corrigido uma vez. Num menu de disco o fundo é
ambiente: ele diz de que filme é o menu, não é pra ser visto.

### A sétima rodada: **a paisagem**, que ninguém tinha olhado

A pergunta do dono foi «tu viu o menu e o player quando roda na horizontal?». A
resposta era **não** — as sete rodadas anteriores foram todas em pé. Girar o
aparelho achou quatro defeitos em dez minutos.

| o que aparecia deitado | conserto |
|---|---|
| o **trilho de navegação ao lado do menu de DVD** — «biblioteca · locadora · mural» de pé, ao lado de um menu que devia ser tela cheia | o palco e o menu avisam que são sobreposição, e o `AppOdeon` tira o esqueleto — o mesmo caminho que o player já tomava |
| **o cromo do player em cima do filme**: «−10s · pausar · +30s» sobre um assoalho claro | duas faixas de degradê, uma em cada ponta. Em pé o filme é letterboxed e o cromo caía nas tarjas pretas — era legível **por acidente de proporção** |
| **a caixa não cabia**: o título «Tetris» cortado ao meio e a dica sumida | o tamanho da caixa sai da altura disponível (50%, teto nos 285dp de pé) |
| **girar reiniciava a vinheta** do menu, jogando quem estava nos capítulos de volta pro começo | `rememberSaveable` — a vinheta é «toda vez que se põe o disco», não «toda vez que se vira o telefone» |

⚠️ **O que a paisagem prova, e vale mais que os quatro consertos:** uma tela
verificada só em pé está **metade verificada**. Os quatro defeitos existiam desde
que cada peça foi escrita, passaram por 76 testes e por `lintDebug` limpo, e
nenhum apareceria sem girar o aparelho — é o §1 do `CONTINUAR-ANDROID.md` outra
vez, com um eixo a mais.

### A oitava rodada: a caixa que o dono mandou refazer

> «a visão 3D do VHS e do DVD está feia demais. Aumente o tamanho. Quando você
> move com o dedo, faça dar pra ver o verso também, e melhore o movimento.
> Mandei uma foto do verso de um DVD. E quando clicar em play, tem que cair no
> filme, não nos detalhes.»

| | o que mudou |
|---|---|
| **tamanho** | a conta olha as duas dimensões — 72% da altura ou 88% da largura, o que vier menor. Os 190dp de antes eram «o que cabia sem pensar» |
| **o verso** | o teto de ±42° saiu no horizontal: o giro dá a volta inteira, e o verso começa depois dos 90° |
| **a contracapa** | título, `ano · tipo · mídia`, sinopse, uma cena, ficha técnica em fonte de máquina, código de barras derivado do uuid e o `▸ ASSISTIR` — a ordem da foto |
| **todas as áreas** | a caixa tinha **quatro** faces e virou **seis**: faltavam a lateral da abertura e a base, e com o giro completo o que aparecia no lugar delas era o fundo da tela atravessando o objeto |
| **play** | o botão do verso vai **direto pro player**. Confirmado pela sessão de mídia: `state=PLAYING` |

### O movimento levou três tentativas, e a terceira achou o erro da segunda

1. **Encaixe por metade**: «passou dos 90°?». Um empurrão de 500px girava 250°,
   caía a **três graus** do verso e o encaixe puxava de volta pra capa — o verso
   aparecia no meio do gesto e sumia.
2. **Inércia solta** (`animateDecay`): em graus, a velocidade de um dedo vira
   700°/s. A caixa dava duas voltas e parava onde calhasse.
3. **Alvo calculado**, como um seletor: soma um pedaço da velocidade à posição
   pra saber pra onde o gesto apontava, e vai direto pra face mais próxima dali.

E o sinal do giro teve a mesma história. A queixa era «a caixa vai pro lado
contrário do dedo», e a primeira reação — inverter a horizontal — **estava
errada**: quem estava invertido era o **vertical**. A régua que resolveu não é
gosto, é a projeção: o centro da capa cai em `x = (e/2)·sen(giroY)` e
`y = (e/2)·sen(giroX)`, então **giro positivo move a capa pra direita e pra
baixo**. Pra ela seguir o dedo, os dois eixos somam o arrasto. O vertical vinha
subtraindo, com um comentário que dizia «invertido de propósito» — era invertido
por engano.

⚠️ **O teste que a rodada deixou**: «de qualquer ângulo há sempre face virada pra
frente». Ele varre 73 × 7 poses e é o que garante que a caixa é um sólido
fechado — se alguém tirar um lado, ele cai antes de a foto denunciar.

### Os três defeitos que só o screenshot achou

É a lição mais cara do projeto, cobrada de novo (§1 do `CONTINUAR-ANDROID.md`).
O código compilava, passava nos 54 testes e não tinha um achado de lint nos
arquivos novos nas três vezes.

1. **O selo do nível saía cortado.** A gaveta recortava a insígnia num círculo
   pra o toque ficar redondo, e o selo mora encostado na borda — o que aparecia
   na tela era uma foice dourada com meio algarismo dentro.
2. **O selo comia um quarto do rosto.** 46% era a proporção da web, mas lá o selo
   **transborda** a insígnia e aqui ele encosta por dentro: mesma proporção,
   caixas diferentes, tamanhos diferentes na tela. Ficou 38%.
3. **Buscar deixava a resposta abaixo da dobra.** Com «goldfinger» digitado, a
   tela mostrava o herói de 16:9, a fileira de continuar com quatro cartazes, e
   só então os **2 de 2** resultados. Os dois somem enquanto durar a busca.

### Como foi verificado

Compilado e instalado num emulador na própria máquina, com sessão real contra o
servidor de casa — `assembleDebug` ✅, **79 testes** ✅, `lintDebug` sem nenhum
achado nos arquivos destas rodadas. A insígnia, a gaveta, o perfil e a busca foram
**vistos funcionando**, e é de fotos deles que saíram os três defeitos acima.

⚠️ **A caixa em 3D foi vista; o palco não.** A prateleira com as caixas novas
está fotografada — perspectiva na capa, lombada colada sem fresta, topo visível.
Já o palco (caixa na mão, abrir, disco saindo) foi verificado pela **árvore de
acessibilidade**, e não por foto: o `screencap` deste emulador devolve quadro
preto nessa tela. O que a árvore prova é o fluxo — a dica passa de «toque na
abertura, à direita, para abrir» pra «toque no disco para assistir» depois do
toque na aresta certa —, e o que ela **não** prova é a aparência. Fica pendente
de olhar, e é justamente o tipo de coisa que o §1 do `CONTINUAR-ANDROID.md` diz
que só a foto pega.

⚠️ Nada foi escrito no acervo: as rodadas só leem. O `sair` **não** foi tocado no
aparelho de propósito — a senha da conta não está aqui, e sair sem poder voltar
deixaria o emulador inútil pra a próxima verificação.

### A segunda rodada do mesmo dia: o filtro e a série

**O que entrou:**

| | onde |
|---|---|
| o `Filtros`, com os doze parâmetros que o servidor aceita | `dados/Filtros.kt` |
| a barra: `filtros ▾` com pílula, ordem, modo das tags, `limpar ✕` | `ui/biblioteca/BarraDeFiltros.kt` |
| o painel: tags por grupo **com contagem**, duração, identificação | idem |
| **entrar na série** → temporada → episódio, com o `still` 16:9 | `ui/biblioteca/*` |
| `GET /api/works`, `/api/tags`, `/api/tag-namespaces` | `dados/OdeonApi.kt` |
| 7 testes novos, sobre a ordem das temporadas | `PorTemporadaTest` |

**Medido no aparelho:** `Terror` devolveu **60 de 145** — o mesmo número que o
chip prometia. *Breaking Bad* abriu em **60 de 62**, quebrada em `TEMPORADA 1`
(7) e `TEMPORADA 2` (13), cada episódio com o próprio quadro.

**Mais um defeito que só a foto achou:** a temporada 2 dizia **`1 3`**. O número
do `RotuloDeSecao` herdava a letra espaçada do rótulo em caixa alta, e dois
algarismos separados se leem como dois números. Letra espaçada é regra de texto;
número é quantidade, e quantidade se lê junta.

**Uma observação pro dono, e é do servidor:** o grupo de países aparece
rotulado **`COUNTRY`**, em inglês, porque é isso que `/api/tag-namespaces`
manda no `label`. A web mostra o mesmo. Não foi traduzido aqui de propósito —
traduzir no cliente criaria a segunda cópia da tabela de rótulos, que é
exatamente o que buscar o `label` do servidor evita.

---

## 11. A caixa em 3D — o que existe hoje, e o que foi decidido

**Decidido em 05/08/2026**, e fecha o «em aberto» da
[`APP-ANDROID.md`](APP-ANDROID.md) §3: a caixa em 3D **é requisito**, com o disco
e a fita, e **o dedo é o controle**.

### O que existe hoje, medido no código e visto no aparelho

Não é nada, e também não é o que a web tem — é um meio-termo que vale descrever
com precisão porque metade do trabalho já está feita:

| | estado |
|---|---|
| a caixa tem **duas faces** — lombada e capa | ✅ `rotationY` de 68° e −22°, com `cameraDistance` |
| a pose de três quartos (`rotateX(3) rotateY(22)` da web) | ◐ só o `rotateY`; não há inclinação vertical |
| o verniz diagonal, a cinta de papel, o nome de quem levou | ✅ |
| virar a caixa pra ler o verso | ✅ mas é um **flip de 180° por toque**, não um giro contínuo |
| **girar com o dedo, em dois eixos** | ❌ |
| **a caixa voar da estante até o centro** (o palco) | ❌ |
| **abrir a caixa pela aresta oposta à dobradiça** | ❌ |
| **o disco**, saindo e ficando girável | ❌ |
| **a fita**, com carretel e rebobinar | ❌ |
| o topo da caixa (a terceira face) | ❌ |
| o corte VHS × DVD | ❌ o `ultimo_ano_vhs` chega e não é usado (§8) |

⚠️ **E há um limite honesto no que existe**, escrito no próprio código: como
cada face é uma camada com transformação própria, elas **não dividem o mesmo
ponto de fuga**. A junta entre lombada e capa só fecha na pose de repouso — por
isso a pose é fixa e **não acompanha o dedo** hoje. Animar o ângulo abre uma
fresta no meio do caminho.

Ou seja: o que impede o giro com o dedo não é falta de vontade, é a arquitetura
de camadas. Ela precisa mudar.

### O caminho, e por que não é OpenGL

Três opções, e a do meio é a que serve:

| caminho | o que dá | o que custa |
|---|---|---|
| **camadas com `graphicsLayer`** (hoje) | pose fixa bonita | a junta abre fora da pose; é o teto |
| **projeção própria num `Canvas`** | uma câmera só, faces coerentes, giro livre, disco e fita desenhados no mesmo espaço | escrever a projeção — uma matriz e oito vértices |
| **OpenGL / Filament** | cena 3D de verdade, luz, sombra | «um projeto dentro do projeto», e traz uma superfície separada que briga com o Compose |

O caminho do meio é o que a `APP-ANDROID.md` §3 chamou de «superfície com render
próprio», mas **sem sair do Compose**: `android.graphics.Camera` já faz
perspectiva de plano, e uma matriz compartilhada entre as faces é exatamente o
que o `preserve-3d` da web dá de graça. As faces deixam de ser `Composable`s e
viram desenho — e aí disco, fita e carretel entram no mesmo espaço, porque
passam a ser geometria e não `Box`.

**Proposto** — a ordem em que as peças entram, cada uma valendo sozinha:

1. **A caixa coerente**: as três faces (capa, lombada, topo) numa projeção só.
2. **O giro com o dedo**, em dois eixos, com os limites da web — 0,5°/px na
   horizontal, 0,32° na vertical, teto de ±42°, e limiar de 6px separando girar
   de tocar.
3. **O palco**: tocar tira a caixa da estante e a traz ao centro (o FLIP da web —
   mede o retângulo de origem e anima a diferença).
4. **Abrir**: tocar na aresta oposta à dobradiça abre e **entrega a mídia**.
5. **O disco e a fita** como objetos giráveis, no mesmo espaço projetado — e o
   corte entre eles é o `ultimo_ano_vhs`, que já chega do servidor.
6. **A tela da fita e o rebobinar** — que já era o item 6 do §6, e que só faz
   sentido depois que a fita existir como objeto.

⚠️ **O que continua fora, e é decisão anterior:** a trilha sintetizada do menu de
DVD (§3 da espec, `AudioTrack` com buffer PCM). O menu em si pode existir sem
ela; o som é outro pedido.

---

## 12. O que este documento não cobre

- **O porquê de cada escolha da web.** Está no `docs/DESIGN.md` do repositório do
  servidor. Os `§NN` citados aqui são de lá.
- **O que muda de forma no Android por decisão** (estante 3D, trilha do menu de
  DVD, negociação de codec): [`APP-ANDROID.md`](APP-ANDROID.md) §3.
- **A ordem de implementação**: [`APP-ANDROID.md`](APP-ANDROID.md) §5. O §6 daqui
  é **Proposto** e não a substitui.
- **O estado operacional do app** (como compilar, como alcançar o servidor, o que
  nunca foi visto rodando): [`CONTINUAR-ANDROID.md`](CONTINUAR-ANDROID.md).
