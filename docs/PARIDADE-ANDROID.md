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

**Medido em 06/08/2026**, sobre `android/app/src`:

| | |
|---|---|
| arquivos `.kt` (main) | **70** · **22.503 linhas** |
| testes | **19 arquivos** · **2.079 linhas** · **144 testes**, todos passando |
| rotas declaradas no `OdeonApi` | **30**, de 113 |
| rotas servidas por URL montada (sem Retrofit) | 3 — `/artwork/*`, `/api/stream/*`, a playlist HLS |
| telas | **8** — 5 abas, a ficha, o player e o perfil |
| sobreposições | **1** — a gaveta do "eu" |

⚠️ A tabela estava parada em **54 arquivos e 61 testes** — os números da manhã de
05/08, escritos antes das rodadas daquele dia e nunca refeitos. Ficou registrado
porque é o modo mais comum de um documento assim envelhecer: a prosa cresce, e a
medida no alto continua dizendo o que era verdade quando alguém a escreveu.

O que mudou desde então está no §10: as **doze rodadas de 05/08** (a caixa, o
balcão, a locadora, o player) e as **doze de 06/08** (o relógio do player, as
faixas de áudio, o dono da sessão, a ficha).

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
| 10 | As rodadas de 05/08/2026 — o que elas mudaram nesta tabela |
| 10b | As rodadas de 06/08/2026 — o player cobra, e a ficha vira fachada |
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
| o balcão: chips de pessoa, fama `✕N` / `⟲N` | ✅ desde 05/08/2026 |
| devoluções recentes (`fulano devolveu rebobinada`) | ❌ o campo chega e não é desenhado — ver §8 |
| recado ao vivo (6s) | ❌ depende do barramento |
| prazo na caixa (`3 dias`, `vence amanhã`, vermelho a 2) | ❌ o `vence_em` chega e não é desenhado |
| corte VHS × DVD (`ultimo_ano_vhs`) | ❌ o campo chega e não é usado |
| as três contagens da porta da loja | ✅ desde 05/08/2026 — e com o **buraco na estante**, que é o que as faz contar |
| a caixa na mão: `voando → na-mao → abrindo → midia` | ❌ há o verso, não há o palco |
| escassez barrando a abertura da caixa | ❌ |

**A profundidade 3D saiu desta conta por outro motivo — 06/08/2026.** A frase que
estava aqui dizia que a §3 já decidira que a estante do app é 2D e que o giro
vira `flip`. Deixou de ser verdade: o `Projecao.kt` deu ao app uma câmera única
com oito vértices e quatro homografias por `setPolyToPoly`, e a caixa passou a
ter **seis faces** e giro de 360° no palco. O app não ficou devendo 3D — ele
alcançou o da web.

E a conta virou nos dois sentidos. O que o app fez em cima disso **a web não
tinha**, e voltou pra cá em 09/08:

| do app | pra web |
|---|---|
| a cenografia («a loja da esquina, 21h»): arandela, plaquinha, etiquetas, nota do caixa | ✅ `Cenografia.tsx` |
| a lombada de duas tintas tiradas da capa | ✅ `tintas.ts`, com histograma no lugar da `Palette` |
| o resumo como recibo no fim da visita | ✅ **parcial e de propósito** — o recado ao vivo ficou no topo, porque a web tem um e o app não |

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
| 12 | locadora completa | 5 | ✅ vitrine, palco, fita, rebobinar, pedir de volta, prazo, VHS×DVD, balcão e **a porta da loja** |
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

**Medido em 05/08/2026:** das 15 capacidades da v1, **11 estão inteiras** e **4
parciais**. **Nenhuma falta inteira** — que é o marco que este documento nasceu
pra medir: de manhã eram quatro sem nada.

A décima primeira é a **locadora**, fechada na décima rodada — e ela era a maior
das quinze.
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
cliente não pegava». Todos eram da locadora, e **quatro dos seis foram pagos em
05/08/2026**:

| campo | rota | estado |
|---|---|---|
| `Prateleira.devolvidas` | `locadora/prateleira` | ✅ as devoluções, dentro do balcão |
| `Prateleira.pessoas` | `locadora/prateleira` | ✅ **os chips de pessoa**, com a fama |
| `Prateleira.ultimo_ano_vhs` | `locadora/prateleira` | ✅ o selo `VHS`/`DVD` e o corte que decide se a caixa rebobina |
| `Emprestada.vence_em` | `locadora/prateleira` | ✅ o prazo na caixa |
| `Loja.no_acervo` | `locadora/estantes` | ✅ o `de 850 no acervo` da porta da loja |
| `CaixaExposta.temporadas` | `locadora/estantes` | ✅ a faixa `3 TEMPORADAS` no pé da capa |
| `Emprestada.exclusivo` | `locadora/prateleira` | ✅ **o buraco na estante** — e este não desenhava, escondia |

**A lista fechou em 05/08/2026.** Os seis campos que chegavam e ninguém desenhava
foram pagos no mesmo dia, e o sexto — o `exclusivo` — só apareceu porque a porta
da loja precisou dele: as três contagens não fecham sem a caixa alugada sair da
fileira.

⚠️ **E ele é de um tipo diferente dos outros cinco.** Os cinco primeiros eram
campos que **não desenhavam**; este era um campo que não **escondia**. O padrão
do §8 tem, portanto, uma segunda forma, e ela é mais difícil de achar: a tela não
fica com um vão, fica com uma caixa a mais — e uma caixa a mais parece certo.

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

## 10. As rodadas de 05/08/2026

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

### A nona rodada: o balcão

A última peça grande da locadora, e a que faz as outras pessoas da casa
aparecerem por nome.

| | |
|---|---|
| **os chips** | quem tem fita na mão **ou** quem tem fama, com o rosto por hash — o mesmo da insígnia e do perfil |
| **as três contagens** | `N` na mão · `✕N` fitas dela que alguém rebobinou · `⟲N` fitas dos outros que ela rebobinou. **Zero some** |
| **o seu limite** | «você pode pegar mais 3», ou o caminho de saída quando não dá |
| **o recado ao vivo** | já existia solto; agora mora no balcão |
| **as devoluções** | com o selo `atrasada` e a distinção que faltava (ver abaixo) |

**A regra que faz a reputação existir**, e ela é da web: o chip aparece pra quem
tem fita **ou** pra quem tem fama. Se o número sumisse junto com a fita, devolver
zoado seria de graça.

**E o `⟲` não é simetria decorativa**: «um placar que só conta o defeito faz de
todo mundo réu».

⚠️ **Uma frase que estava errada e ninguém veria:** `devolvido_por = prazo` é a
fita que voltou **sozinha** quando o prazo estourou. A versão anterior escrevia
«fulano devolveu» nos dois casos — dando crédito por uma coisa que o relógio fez,
sobre uma pessoa real, sem nada na tela que denunciasse. Tem teste agora.

E o «você ainda pode pegar N» saiu da linha de regras: as regras são o contrato
da loja, o limite é seu. Nos dois lugares, um dos números ficaria velho primeiro.

### A décima rodada: a porta da loja, e o buraco que ela contava

A última peça da locadora. O pedido foi «a porta da loja» — o `no_acervo` e o
`temporadas`, os dois últimos campos do §8 — e a leitura da régua achou uma
terceira coisa **dentro** dela.

**A frase pedida não fechava sozinha:**

```
37 caixas na prateleira, 3 fora · 40 nesta semana, de 600 no acervo
```

O `, 3 fora` é a diferença entre o que a semana sorteou e o que está à vista — e
o app tinha as duas iguais, sempre, porque desenhava `loja.estantes` cru: a caixa
que alguém levou continuava de pé na prateleira, com a arte inteira,
indistinguível de uma que dá pra pegar. A conta daria `0 fora` pra sempre, e no
dia em que alguém pegasse uma fita a linha diria «40 na prateleira» com 39 caixas
na tela.

**Decidido pelo dono, perguntado antes de escrever:** a caixa alugada some da
estante, e as duas coisas entram juntas.

| | |
|---|---|
| **o buraco** | a caixa que **tranca** (`exclusivo`) ou que é **sua** sai da fileira, e o vão fica aberto |
| **as três contagens** | `N na prateleira[, M fora] · S nesta semana, de A no acervo` |
| **os dois vazios** | `a prateleira está vazia — está tudo emprestado` × `nada com capa por aqui` |
| **a faixa de temporadas** | `3 TEMPORADAS` no pé da capa, na tinta da própria obra |
| 12 testes novos | `PortaDaLojaTest` |

**Por que o buraco não é preenchido**, e é a frase da web que decide: «puxar uma
caixa nova do acervo pra tapar o vão faria a loja ter 40 sempre, e aí levar uma
fita não custaria nada a ninguém». A escassez tem que ser **vista** na fileira; a
porta da loja só a nomeia depois.

⚠️ **`no_acervo` vem do servidor, e somar as placas dá outro número.** Medido nas
dez estantes desta semana: os totais somam **813**, e o servidor manda **850**.
Faltam as estantes que a semana não sorteou — o acervo delas some junto com a
placa. A web já tinha o recado escrito, e o app agora tem o número.

**Uma divergência de tamanho, assumida:** a faixa de temporadas é 7px numa caixa
de 130px na web (5,4%). Os mesmos 5,4% nos 96dp desta caixa dariam **5,2sp**, que
não é texto, é textura. Ficou 8sp. É o mesmo erro que o selo do nível cobrou na
oitava rodada — mesma proporção, caixas diferentes.

**E a concordância é nossa:** a web escreve `${total} caixas na prateleira`, e com
uma caixa sai «1 caixas». Aqui sai «1 caixa».

**Medido no aparelho:** a porta abriu em `40 caixas na prateleira · 40 nesta
semana, de 850 no acervo`, sem o `, N fora` porque não há nenhuma fita fora hoje
(§24) — e o `3 TEMPORADAS` apareceu na capa de *Digimon*, na estante «Animação».

### O buraco foi visto — e custou um empréstimo de verdade

**Autorizado pelo dono**, e é a única escrita desta rodada. A caixa escolhida foi
*Sid & Nancy - O Amor Mata*, de propósito: ela era a **única** caixa da estante
`Romance`, então uma fita só provava as duas regras ao mesmo tempo.

| | antes | com a fita na mão |
|---|---|---|
| a porta | `40 caixas na prateleira · 40 nesta semana, de 850 no acervo` | `39 caixas na prateleira, **1 fora** · 40 nesta semana, de 850 no acervo` |
| a estante `Romance` | `1 de 10`, com a caixa | **sumiu inteira** — §24 |
| o chip do `sam` | `✕1 ⟲1` | `**1** ✕1 ⟲1`, com a pastilha cheia |
| o limite | «você pode pegar mais 3» | «você pode pegar mais 2» |
| a seção `comigo` | não existia | `COMIGO 1`, com a caixa |

Devolvida em seguida pelo arrasto de 96dp, e **tudo voltou ao lugar**: a porta a
`40 caixas na prateleira` sem o `, N fora`, a estante `Romance` de volta com a
caixa dentro, o limite em 3.

⚠️ **O que ficou no acervo, e precisa estar dito** (§11 do
`CONTINUAR-ANDROID.md`): um empréstimo e uma devolução na conta do `sam`, e a
linha `sam devolveu Sid & Nancy - O Amor Mata — rebobinada` no balcão. Nada mais.

⚠️ **E um achado de brinde, que é anterior a esta rodada:** pegar a fita **pela
ficha** não atualiza a locadora que já estava aberta. A tela continuou dizendo
`40 · você pode pegar mais 3` até o app ser reaberto. Não é o barramento
falhando — é ele funcionando: o eco do próprio aparelho é descartado pelo
`device_id` (§1.3), e foi o próprio aparelho que pegou a fita. O recado existe pra
contar o que **os outros** fizeram. Fica anotado, não consertado.

### A décima primeira rodada: o topo da locadora, que o dono achou feio

> «essa parte de cima da locadora tá bem feia, melhore» — e depois, cortando o
> escopo: «não toque nas prateleiras, eu tô falando da parte de cima somente».

**O diagnóstico, medido:** o topo comia **1.189px dos 2.256** utilizáveis. A loja
começava depois da metade da tela, numa tela cujo assunto são as caixas.

E a dissonância visual tinha nome: `comigo`, `na mão de alguém` e todas as
estantes são **fileiras horizontais de objetos**. O balcão era a única coisa
vertical da tela, e a única feita de frases.

| | o que mudou |
|---|---|
| **a data** | `HOJE` mostra o que voltou hoje; o resto vira `mais N antes ›`, que **abre** |
| **a placa** | os dois números em serifa dourada — a tinta das placas de estante, que faltava na porta |
| **o limite** | subiu pra linha dos chips: as duas coisas são o mesmo assunto |
| **a frase** | o filme na frente, quem e como atrás e apagados |
| **o eco** | «nenhuma caixa fora da estante» morreu quando há vitrine |
| 8 testes novos | `EhDeHojeTest` |

⚠️ **`devolvido_em` era o sétimo campo do §8, e o mais escondido de todos.** Ele
chega em toda devolução e não era lido por ninguém — nem aqui, nem na web, onde
serve só de chave de lista. Os outros seis não desenhavam ou escondiam; este
**datava**, e sem ele a lista não tinha como encolher por verdade.

**Por que não cortar em «as três últimas»:** o corte por contagem mente nos dois
sentidos. Num dia em que voltaram cinco fitas, esconde notícia; num dia parado,
promove a notícia o que aconteceu na semana passada. O corte por dia não mente em
nenhum — e num dia sem devolução a seção simplesmente não nasce (§24).

**O «hoje» é o dia do calendário, e não «faz menos de 24h».** Uma fita devolvida
às 23h de ontem tem nove horas às 8h da manhã e **não** é notícia de hoje; uma
devolvida às 00h10 é, com dez minutos. Tem teste pros dois, e pra virada do ano.

**Medido no aparelho:** a primeira estante saiu de **1.189px** pra **977px** na
primeira leva, e pra **706px** depois do segundo corte — **41% a menos**. Antes
não aparecia uma caixa sequer na primeira tela; agora cabem **três estantes**
inteiras, com as caixas.

⚠️ **A primeira estimativa estava otimista, e vale registrar por quê.** A proposta
falava em ~470px e a primeira leva deu 977. Faltou contar duas coisas na conta de
guardanapo: a placa cresceu de uma linha pra duas — é o preço do destaque — e
**cada seção paga 42dp de respiro**, o `spacedBy(16.dp)` da coluna multiplicado
por mais seções do que antes. Foi medindo o entregue que os 271px restantes
apareceram.

### O segundo corte: as regras da casa saíram da tela

**Decidido pelo dono**, depois de ver o número. Duas coisas foram embora:

| | |
|---|---|
| o rótulo `HOJE` | ~80px de régua dourada pra encabeçar, em dia normal, **uma linha** |
| as regras da casa | duas linhas de 84px imediatamente antes da primeira estante |

**O rótulo não deixou buraco porque o `mais N antes ›` faz o trabalho dele.** A
palavra «antes» só significa alguma coisa se o que está acima for depois — a
lista se datou sozinha, com o link que já existia. E onde não há histórico o link
some, e aí não há dois grupos pra confundir: há um.

**As regras foram o corte com mais consequência**, e cada informação delas
continua dita em outro lugar — *menos uma*:

| | onde ficou |
|---|---|
| o limite | na linha dos chips — «pegar mais 3», e é o número que muda |
| o prazo | na cinta de cada caixa — «5 dias», «vence amanhã» |
| a escassez | **no buraco da fileira**, que passou a existir nesta mesma rodada |
| `escassez desligada` | **em lugar nenhum** — perda aceita |

A terceira linha é o que autorizou a remoção, e ela só ficou verdadeira hoje:
enquanto a caixa alugada continuava de pé na prateleira, «escassez ligada» era a
única pista de que uma cópia por caixa era regra. Com a fileira encurtando na
frente de quem olha e a porta contando quantas faltam, a regra virou **coisa
vista** — e uma frase que repete o que se vê é legenda de tela.

A quarta é perda de verdade e está escrita como tal: loja sem escassez não tem
buraco nenhum pra notar a ausência. O argumento pra aceitar é que «ninguém te
barra» não muda nada no que você pode fazer.

⚠️ **A `regras()` foi apagada, não comentada.** Código morto guardado «por via das
dúvidas» é a próxima pessoa lendo duas versões da mesma regra sem saber qual vale.
Ficou só uma nota no lugar, apontando pra onde o porquê está escrito.

### O terceiro corte: a barra de baixo, e o que a queixa realmente era

> «o lower menu está com uma black bar muito grande, talvez diminuir um pouco?»

**Medido:** a barra ocupava **72dp de fileira mais ~25dp do inset do gesto = 97dp**,
contra os 80 que o Material reserva pra barra inteira, insets incluídos. Mas a
altura era metade do problema.

⚠️ **A outra metade não era altura, era o fundo parando cedo.** O
`windowInsetsPadding` era aplicado **por fora** da barra, no `AppOdeon`: a barra
inteira subia, e entre ela e a borda do aparelho ficava o fundo da tela — preto
chapado, sem o degradê e sem o cone. Uma tarja de 25dp separando a barra acesa da
beirada, e é ela que a foto do dono mostrava.

| | |
|---|---|
| o inset foi **pra dentro** da barra | o degradê e o cone descem até a borda; só a fileira recua |
| a fileira encolheu, **duas vezes** | 72 → 64 → **54dp**: `6 + 22 + 3 + ~17 + 5` |
| `ALTURA_DA_LUZ` encolheu junto | 118 → 105 → **89dp**, porque ela é **derivada**: raio `54 × 2,6 = 140`, e `140 − 54 = 86` mais a mesma folga de 3dp de antes |
| o ícone | 24 → **22dp** — 24 era o padrão do `Icon`, não escolha desta barra |

| | fileira | com o inset |
|---|---|---|
| antes | 72dp | 97dp |
| primeiro corte | 64dp | 89dp |
| **agora** | **54dp** | **79dp** |

**A terceira linha é a que teria quebrado calada.** `ALTURA_DA_LUZ` não é gosto: é
o espaço que o cone precisa pra fechar dentro da caixa em vez de ser cortado. Um
encolhimento da fileira sem ela traria de volta a aresta reta no topo do facho —
o defeito que uma rodada anterior já tinha consertado uma vez.

**79dp é o piso desta forma.** Abaixo disso o rótulo teria de sair, e aí a barra
deixa de dizer o que cada aba é — outra conversa, não um ajuste de número.

⚠️ **E aqui eu errei uma vez, e a foto corrigiu.** A primeira versão prendeu o
`Canvas` da luz à altura da luz mais a fileira, com o argumento de que «a lente
nasce na aresta da fileira, não na do aparelho». A foto mostrou o cone terminando
numa **linha horizontal visível** na base da fileira, com o inset preto embaixo —
a mesma tarja, 25dp mais curta.

O argumento certo já estava escrito no próprio arquivo desde que a barra nasceu:
«a lente fica **abaixo** da borda: a luz entra na barra vinda de fora dela, que é
o que uma janela de projeção faz». Com o `Canvas` cobrindo a caixa inteira, a
lente cai na borda do aparelho e o cone preenche o inset. Não sobra aresta.

⚠️ **O `screencap` preto voltou, e o conserto de antes não bastou.** Desta vez o
`force-stop` + relançar deu quadro preto na primeira tentativa e quadro real na
segunda, 3s depois. O que funciona é **tentar de novo até o PNG passar de ~100 KB**
— quadro vazio dá ~20 KB, quadro real passa de 1 MB, e a diferença de tamanho é o
teste mais barato que existe pra saber se a foto presta.

### O quarto corte: a gaveta do «eu», e a segunda peça pintada por outra pessoa

> «esse menu que aparece está feio demais, redesenhe»

**O diagnóstico é o mesmo que tirou a `NavigationBar` deste app.** Nada do que
estava feio era escolha desta tela: o `DropdownMenuItem` do Material impõe **48dp
de altura mínima** mais 12dp de respiro vertical próprio, e o `DropdownMenu` vem
sem borda. Dois itens viravam ~120dp de caixa escura pra 24dp de texto, boiando
sobre um fundo igualmente escuro.

| | o que entrou |
|---|---|
| **o cabeçalho** | nome, nível e a fatia — a gaveta passa a dizer **de quem** ela é |
| **a moldura tinge** | a borda sai da cor que a pessoa escolheu (§10), com o dourado da casa como piso |
| **as linhas** | 38dp, contra 48+12 |
| **o `›`** | só no `perfil`, porque só ele leva a algum lugar |

**O cabeçalho não repete o rosto**, e é decisão: a insígnia de 48dp está desenhada
logo acima, ainda na tela, e o painel abre colado nela. O que a insígnia **não**
consegue dizer é o nome — na web ele fica escrito ao lado dela na barra de cima, e
aqui não há barra. A fatia do nível é o mesmo número do anel, na forma que um anel
de 48dp não dá: dá pra ver se falta pouco ou se falta tudo.

⚠️ **E um defeito que só a foto pegou, de novo.** A primeira versão escrevia
«nível 2 · 6 de 80». Ao lado de «nível 2», um `6 de 80` sem substantivo se lê como
se fosse sobre o nível — número sem o nome da coisa que ele conta é o §18 na forma
mais barata de cometer. Ficou «6 de 80 conquistas», e cabe na largura.

### O quinto corte: os baixados, e a tela onde não dava pra assistir

> «bora fazer o redesign dessa tela, me surpreenda […] e adicione o menu inferior»

**O defeito maior não era aparência.** `TelaDosBaixados(modelo)` não recebia
callback nenhum: a única ação de um filme de 2 GB baixado pra ver sem rede era
**apagá-lo**. Quem quisesse assistir tinha de voltar à biblioteca e procurar a
obra.

E cinco coisas chegavam no modelo sem virar tela: `poster`, `bytes`,
`duracaoEmSegundos`, `origem` e a arte deitada, que nem existia na ficha.

| | o que entrou |
|---|---|
| **assistir** | o cartão inteiro toca, e há um `▸ assistir` na linha de ação |
| **a arte** | `backdrop` novo na `FichaDoDownload`, gravado junto dos bytes; o pôster é a reserva |
| **o cabeçalho** | `5 filmes · 9,6 GB no aparelho`, na serifa dourada das placas |
| **o prazo** | selo sobre a arte, e só em quem veio da locadora |
| **apagar** | pede o segundo toque, como o devolver da locadora |
| **o menu inferior** | e ele acende `biblioteca` — ver abaixo |
| 5 testes novos | `MedidaTest` |

**A conta que decidiu o cartão:** área útil de ~810dp (914 menos status, barra e
inset). Título 40 + cabeçalho 40 deixam ~730, e **cinco cartões cabem sem rolar**
com a faixa da arte em 100dp. Cinco é o número que importa — quem baixa filme tem
meia dúzia, não duzentos.

**O menu inferior forçou uma decisão.** `baixados` não é uma das cinco abas, e o
`BarraDoFacho` acende a selecionada — sem nenhuma, ele cairia na primeira por
`coerceAtLeast(0)` e a barra diria «biblioteca» por acidente de implementação.
Agora diz por decisão: baixados é sub-tela da biblioteca — chega-se pelo `no
aparelho ›` do cabeçalho dela e o voltar leva pra lá. O facho fica na **seção**.

⚠️ **E duas funções mudaram de casa.** `duracaoCompacta` e `tamanhoCompacto`
moravam na `Contracapa` da locadora, que foi quem primeiro precisou escrever
«1H36» e «1,9 GB». Com a segunda leitora, foram pra `ui/Medida.kt` — e a caixa
alta ficou com o chamador, que é a mesma decisão que o `Tipo.rotulo` já tinha
tomado. O verso da caixa é encarte impresso e grita `1H36`; o cartão é texto de
tela e diz `1h36`.

#### ⚠️ Um defeito que eu escrevi e peguei antes da foto — e ele apagava dado real

A primeira versão do «assistir» passava `ondeParou = 0.0`, com o argumento de que
a posição mora no servidor e esta tela existe pra quando não há servidor.

**O que ele não considerou é que o player escreve.** O heartbeat manda a posição
pra `POST /api/works/{obra}/progress` a cada poucos segundos — abrir no zero um
filme visto pela metade e deixar rodar dez segundos grava zero, e o «faltam
141min» de uma pessoa real vira «faltam 2h22». Perda silenciosa, sem nada na tela
que denunciasse.

A correção não custa o offline: com rede, pergunta e abre onde parou; **sem rede a
chamada falha e cai em zero — que é o certo nesse caso**, porque sem rede o
heartbeat também não sobe e não há posição pra apagar. O erro só existia no
cruzamento «tem rede, mas a tela decidiu ignorá-la».

#### E um que só a foto pegou: a tarja branca no alto da arte

O cartaz de *007: A Serviço Secreto* tem uma faixa branca no topo, e ela batia
direto na borda de cima do cartão — uma linha branca de ponta a ponta, que lia
como defeito de recorte. O véu só existia embaixo, pra dar chão ao título.

É a **terceira vez** que este projeto tropeça no mesmo lugar: cromo claro
encostando em borda já custou o fundo do menu de disco e o cromo do player em
paisagem. A arte do acervo tem 8.316 origens e nenhuma garantia de margem — o véu
agora escurece as duas pontas, 0,22 em cima, e ele dá chão ao selo do prazo, que
mora nesse canto.

⚠️ **O `backdrop` não foi visto em foto**: o único download do aparelho foi
gravado antes do campo existir, e caiu na reserva do pôster — que é exatamente o
caminho que o comentário previu. Ver o `backdrop` de verdade pede um download
novo, e não foi feito porque baixar um filme inteiro pra conferir enquadramento é
caro na rede de casa.

### O sexto corte: o topo da biblioteca, e a porta que ninguém via

**Medido:** **535px de cromo** antes do primeiro cartaz, em cinco linhas — e
**123px eram um vão vazio** entre o atalho dos baixados e a fileira de filtros.

| | o que mudou |
|---|---|
| **título e contagem** | dividem a linha, e `60 de 8.316` ganhou a serifa dourada das placas — mais o **ponto de milhar**, que faltava |
| **a busca** | fica inteira, por escolha do dono |
| **o atalho** | virou **pastilha acesa** na fileira dos filtros: `↓ 1 no aparelho` |
| **condensar ao rolar** | uma barra fina com **a busca**, `filtros` e `↓ N` — e paga a dívida do §1.1 |

**535px → ~245px.**

⚠️ **O atalho dos baixados era o defeito de produto**, e a queixa foi exata: «tão
simples e escondido que ninguém vai ver». Ele é a **única porta** pra tela de
baixados — a mesma que a rodada anterior redesenhou — e era um `TextButton` com
«no aparelho ›» numa linha só dele.

O que lhe faltava é o que todo o resto do app tem: **um número**. «no aparelho ›»
é uma palavra; «↓ 1 no aparelho» é um lugar com coisa dentro.

**E ele foi pra fileira dos filtros por argumento, não por espaço:** «me mostre o
que está aqui» é o mesmo gesto que `filtros ▾` — estreitar 8.316 entradas. Ele
estava fora da fileira onde mora o seu próprio tipo de ação. O comentário que
morava no lugar dele já previa isto («o próximo passo óbvio é ele virar filtro»);
falta a outra metade — ele ainda abre tela em vez de filtrar a grade.

**A pastilha gasta o dourado cheio**, que o app reserva pro que está aceso. O que
paga a conta é o §24: ela **só existe quando há download**. Um dourado que aparece
por um motivo não gasta a cor — gasta quem fica aceso à toa.

#### Três defeitos, e dois só a foto pegou

1. ⚠️ **A insígnia do canto cobria a pastilha.** A `GavetaDoEu` é desenhada por
   cima de toda tela, e a barra condensada não reservou o lugar dela: o `↓ 1`
   sumia atrás do rosto. É **a pendência que este documento já tinha anotado** por
   outro sintoma — «a insígnia rouba o toque nos 48dp do canto» —, agora cobrando
   pelo desenho. A barra recua 68dp à direita (48 da insígnia + 12 do respiro
   dela + 8 de folga).
2. **O `⤓` não existe na fonte do aparelho.** O glifo certo pra «baixado» é a seta
   pra barra, e o Android substituiu por uma seta comum sem avisar. Um glifo que
   depende de substituição desenha coisa diferente em cada aparelho — ficou `↓`,
   que está garantido.
3. **Um swipe virou toque** durante a verificação e abriu a ficha do *Juno* em vez
   de rolar. Não é defeito do app; fica anotado porque custou uma rodada de foto:
   `input swipe` no emulador precisa de curso longo (1800→700) e **500ms**, senão
   é lido como tap.

### O sétimo corte: a aba que muda de nome, e o clique que não fazia nada

> «ao entrar em baixados não tem como voltar à biblioteca» — e, junto: «deixe o
> menu inferior mudar de biblioteca para baixados, e quando eu voltar a mesma
> coisa»

⚠️ **O defeito estava previsto no meu próprio comentário, e eu o deixei passar.**
Quando baixados passou a acender `biblioteca` na barra, escrevi ali: «tocar em
biblioteca com o facho já aceso não faz nada — sair de baixados é o voltar, como
sempre foi». Isso é o §8b escrito com outras palavras: **a única saída visível da
tela não respondia ao toque**. O guarda era `if (aba != atual)`.

O pedido do dono resolve os dois de uma vez:

| | |
|---|---|
| **o rótulo muda** | em baixados a primeira aba diz `baixados`, com o `ic_aba_baixados` que já existia desde quando ele foi aba de verdade |
| **o toque volta** | tocar na aba acesa leva à **raiz da seção** — o padrão de sempre, no Android e no iOS |

A barra deixa de dizer em que *seção* você está e passa a dizer **onde** você
está. E o caminho de volta fica óbvio sem instrução nenhuma: o nome que ela mostra
é o do lugar que você quer deixar.

**A peça que fez caber:** as abas ganharam uma `FaceDaAba` — rótulo, ícone,
seleção e o que o toque faz, resolvidos antes de desenhar. Sem ela, a barra do
facho e o trilho de paisagem teriam **cada um a sua cópia** do «se estiver em
baixados, escreva outra coisa» — duas cópias que divergem no dia em que a segunda
sub-tela aparecer. Agora o esqueleto desenha e não decide.

**Verificado no aparelho:** a pastilha abre baixados e a barra passa a `baixados`;
tocar nela devolve a grade com `60 de 8.316` e a barra volta a `biblioteca`.

### A oitava rodada: o player, e a cortina que abre a sessão

> «bora fazer o redesign do player, eu quero uma experiência» — e depois: «as
> luzes piscam revelando as cortinas fechadas e aí tu anima pra abrir, mas coisa
> de segundos, não podemos ser tão lerdos pra abrir o filme em si»

**O diagnóstico**, medido em foto deitado: o player tinha as peças mais completas
do app — plano, sprites, gestos, PiP, Cast, legendas — e a apresentação de uma
camada de depuração. `−10s pausar +30s 0:35 / 1:37:48` em texto cru amontoado no
canto inferior esquerdo de uma tela de 2.400px; a timeline um fio de 2px; **sem
título**; e a frase de impedimento do Cast, com 90 caracteres, atravessada no meio
do filme por cima do logo da distribuidora.

E o de fundo: **o app inteiro é cinema** — o facho, as caixas de VHS, o menu de
disco, o balcão — e o player, a única tela onde se assiste a um filme, era a menos
cinematográfica de todas.

#### A cortina — e a regra que a impede de ser lerdeza

Ela **veste uma espera que já existe**: pedir o plano, montar a URL, encher o
buffer. Esse tempo existe hoje e o que ele mostra é tela preta.

| | |
|---|---|
| lâmpadas | 0 → 320ms, fora de fase, revelando o pano do escuro |
| pano visível | até 520ms, com o letreiro |
| abre | até ~950ms |
| **filme pronto antes** | a cortina **corta** |
| **filme demorou** | segura, teto de **1,2s**, e depois abre mesmo assim |
| **qualquer toque** | pula — precedente da vinheta do menu de disco |

⚠️ Passado o teto, a cortina abre **sobre um filme que ainda não começou**. Um
pano parado finge que está acontecendo alguma coisa; um buffer visível diz a
verdade.

⚠️ **Sem animação no sistema, sem cortina.** O §15 manda, e isso obrigou a
separar duas coisas que pareciam uma: `tween` dentro de `animateTo` **já é
descontado** pelo `MotionDurationScale`; `delay` de corrotina **não é**.
Multiplicar o `tween` aplicaria o desconto duas vezes. A `ui/Animacao.kt` nasceu
pra escrever essa distinção uma vez só.

#### O que mais entrou

| | |
|---|---|
| **o título** | não existia. Um player sem o nome do que toca só serve a quem lembra o que abriu — e este app tem PiP, sessão de mídia e Cast, três caminhos de voltar sem ter escolhido |
| **o transporte** | centrado, com o play num disco de 56dp. Era texto de 12sp num canto |
| **«faltam 1:37»** | no lugar de `0:35 / 1:37:48`. A fração obriga quem lê a subtrair, e é a palavra que a ficha e o continuar já usavam |
| **o impedimento do Cast** | subiu pro alto, com duas linhas — saiu do meio do filme |
| **o grão** | o `Grao.Camada` já existia e nunca tinha sido usado no player |
| **a tira de filme** | a timeline como película: perfurações, os fotogramas da folha de sprites, e a lente como cabeçote |

#### Quatro defeitos, e a foto pegou todos

1. **A cortina cobria só 75% da tela.** Num `Row`, `fillMaxWidth(0.5f)` mede sobre
   o **espaço que sobrou**: a primeira metade comia 50% e a segunda 50% dos 50%
   restantes. Virou `weight(1f)`.
2. **O cromo desenhava por cima do pano fechado** — título, selo, `voltar` e
   «faltam 1:37:48» anunciando um filme que não tinha começado. O cromo passou a
   não nascer enquanto a cortina está no ar.
3. **O pano nascia aceso.** As lâmpadas só somavam brilho a um vermelho que já
   estava lá — o pedido era o contrário. Entrou um véu de breu que a luz **tira**.
4. **As lâmpadas não apareciam.** Estavam declaradas antes do pano, e num `Box`
   quem vem depois fica por cima: eram pintadas e cobertas no mesmo quadro. A
   ordem certa também é a física — a marquise fica na frente do pano.

#### ⚠️ A cortina cortava cedo, e por isso «não havia luzes»

O dono disse duas vezes que as luzes não existiam. Elas existiam — e o defeito
não era o desenho delas, era **a regra de tempo**.

A primeira versão cortava a coreografia assim que o player chegasse a `READY`,
pra nunca atrasar o filme. Num Direct Play local isso acontece **logo depois da
piscada**: a marquise acendia e a cortina já abria por cima. Duravam um piscar de
olhos, e a conclusão de quem viu foi a certa.

Ele então afrouxou a própria restrição: «não precisa ser voando o início, pode
levar uns 2 segundos a mais».

| | antes | agora |
|---|---|---|
| a piscada | rampa linear de 320ms | **900ms de lâmpada de arco**, com quedas e picos |
| o pano no ar | até 520ms | até **1.500ms** |
| abre | 430ms | **700ms** |
| total | 950ms | **2,2s** |
| filme pronto antes | **cortava** | não corta mais — a coreografia tem tempo próprio |
| teto | 1,2s | **4s**, e ele agora só responde ao filme lento |

⚠️ **A piscada deixou de ser rampa e virou `keyframes`**, e é o que mais mudou a
leitura: uma subida lisa é um *fade*, e ninguém chama fade de «as luzes piscam».
A curva agora cai e passa do ponto antes de assentar — é a mesma família da
piscada do `BarraDoFacho`, que já existia por esse motivo.

#### Os botões, e as luzes que «não existiam»

> «dar mais vida aos botões de pause, avançar e retroceder» · «As luzes (VOCÊ
> ESQUECEU DAS LUZES)»

**As luzes existiam** — e estavam invisíveis, que dá no mesmo. Seis pontos de 6dp
ocupando 42% da largura. Viraram **doze bulbos de 10dp, na largura inteira, com
halo radial**: o derrame é o que separa uma lâmpada de um adesivo amarelo.

⚠️ A primeira correção pôs **16**, e a foto mostrou a última cortada: 16 caixas de
33dp dão 528dp numa tela de 411. Doze caixas de 28dp dão 336, e sobram 35 pro
`SpaceBetween`.

**Os botões deixaram de ser texto.** `−10s` e `+30s` eram `TextButton` de 12sp;
agora são arcos desenhados com o número dentro, que **giram no sentido do salto**
ao serem tocados. O play virou um disco de 60dp com degradê e **o facho por trás**
— a mesma luz da barra de navegação, aqui dizendo qual é o botão principal sem
precisar ser maior que os outros.

Desenhados à mão, e não de biblioteca: os cinco ícones das abas já são vetores
escritos aqui, e o §15 chama isso de «zero bytes». Além disso o salto **precisa
dizer quantos segundos** — desenhando, o número é parte da forma.

⚠️ **As duas setas nasceram trocadas**, e só a foto pegou: o `10` apontava pra
frente e o `30` pra trás. A primeira versão espelhava o **arco** e deixava a ponta
seguir junto. A régua que resolveu: o arco é **o mesmo** nos dois; o que muda é de
que lado da abertura a ponta mora e pra onde ela olha.

#### ⚠️ A tira ganhou imagem **sem depender de job nenhum** — e eu tinha errado a análise

> «por que o web consegue ter os capítulos e você não?»

A pergunta do dono desmontou uma conclusão minha. Eu havia tratado a folha de
sprites e as cenas como **alternativas** e concluído que, sem a folha, não havia
imagem possível — foi isso que deixou a tira cinza e me fez pedir uma varredura do
acervo inteiro.

São dois mecanismos, e **o segundo não precisa de varredura**:

| | folha de sprites | cenas |
|---|---|---|
| rota | `GET /api/media/{arquivo}/scrub` | `GET /api/works/{obra}/cenas` |
| quando | trabalho em lote, uma vez por arquivo | **sob demanda** |
| quantas | uma a cada `interval_seconds` | **doze** |
| custo | decodifica o arquivo inteiro | **~3s, medido** |

Doze pontos são pouco pra preview de arrasto — e é exatamente a conta certa pra
tira, que mostra **11 ou 12 células** num celular. Cada célula pega a cena mais
próxima do instante que representa (mais próxima, e não a anterior: com ~11min por
cena, pegar a anterior mostraria imagem de onze minutos atrás).

A folha continua tendo precedência quando existe — ela dá o quadro **daquele**
instante, e não o mais próximo.

**Medido no aparelho:** 12 cenas em 3s para *007: A Serviço Secreto*, e a tira
desenhando os quadros de verdade, com o trecho visto revelado contra o resto
apagado.

⚠️ **E isto cancela o pedido de varredura.** O `POST /api/scrub` continua sendo o
que dá resolução fina ao arrasto — mas a tira **não depende mais dele** pra ter
imagem. O status do job, para registro: rodou uma vez em 01/08, fez **63 de
16.836** em três minutos e parou.

#### ⚠️ Paisagem: a tarja preta que comia meia tela

> «no modo horizontal essa barra preta com os itens está muito grande, indo até a
> metade da tela»

Duas causas, e a primeira eu tinha criado sem perceber.

**A tarja era literal.** Havia um `Color.Black` a **55%** por trás da coluna do
cromo de baixo — e ele ficou redundante quando o «foco» trouxe um véu uniforme
sobre a tela inteira. Duas camadas escurecendo o mesmo lugar, e a de baixo com
aresta visível. Em pé passava; deitado, onde a altura é escassa, virava uma faixa.
Saiu: o véu sozinho dá o contraste, e sem borda.

**E o cromo estava em três fileiras** — a tira, o transporte, os tempos. Deitado a
tela tem ~411dp de altura, e três fileiras empilhadas comem quase metade.

| | em pé | deitado |
|---|---|---|
| a tira | 30dp | **48dp** — ver abaixo |
| o disco de play | 60dp | **46dp** |
| os tempos | fileira própria | **na fileira do transporte**, nas duas pontas |
| respiro | 12dp / 8dp | **6dp / 4dp** |
| fileiras | 3 | **2** |

A régua é a mesma que o `EsqueletoComAbas` usa pra virar trilho —
`isHeightAtLeastBreakpoint(HEIGHT_DP_MEDIUM_LOWER_BOUND)` —, e usá-la aqui também
é o que garante que as duas peças concordem sobre o que é «espremido».

**Medido no aparelho, deitado:** os tempos e o transporte na mesma faixa
(y 917–959 de 1080), e a tarja preta fora — o filme aparece por trás do cromo.

#### ⚠️ E a tira, deitada, **cresce** — a primeira reação estava errada

A resposta inicial a «o cromo come metade da tela» foi encolher tudo, tira
inclusive, pra 22dp. O dono corrigiu: «aumente o tamanho da timeline, está muito
pequeno».

Ele está certo pela forma da tela. Deitado sobra **largura** (914dp contra 411) e
falta altura; a tira é a única peça do cromo que usa a largura inteira, então é a
que mais ganha e a que menos custa em altura por pixel de informação. Quem
devolveu o espaço foram **a tarja que saiu e as três fileiras que viraram duas** —
não o encolhimento da película.

| | em pé | deitado |
|---|---|---|
| altura da tira | 30dp | **48dp** |
| largura do quadro | 34dp | **62dp** |
| a janela | 40dp | **70dp** |

⚠️ **Altura e largura crescem juntas.** Subir só a altura deixaria a célula quase
quadrada, e recortar um quadro 16:9 num quadrado joga fora metade da cena. 62 por
38 (a altura menos o respiro das perfurações) dá **1,63** — perto o bastante de
16:9 pra o recorte tirar só as beiradas.

**Fotografado**, depois de reiniciar o emulador: a tira ocupa a largura inteira
com ~15 células de cena em cor, a janela do projetor emoldura o quadro atual com o
halo e a lente, e o transporte fica numa fileira só com os tempos nas pontas — com
o filme aparecendo por trás de tudo.

⚠️ **E o renderizador morreu pela segunda vez no mesmo dia**, no meio desta
verificação: `screencap` a 18 KB, `screenrecord` a 24 KB, e o dono vendo tela
preta. O teste continua valendo — a árvore de acessibilidade respondia — e o
conserto também: `adb emu kill`, esperar o processo morrer, subir de novo. **Duas
vezes em um dia sugere que reiniciar o emulador vire rotina de sessão longa**, e
não remédio de emergência.

#### A cor dos quadros, e a janela do projetor

O dono mandou uma referência com a tira colorida. A tentação é sortear uma paleta
por célula — e isso seria a tela afirmando cor de cena que ela não conhece (§18).

**Não precisa: a cor já estava lá.** Os quadros são fotogramas do filme; estavam
apagados porque a célula por ver desenhava a 34% de alfa sobre fundo escuro. Subir
pra **70%** e saturar em **1,45** mostra a cor que já está no arquivo. É revelar, e
não pintar.

Três intensidades foram propostas e a escolhida foi a média. A forte (88% e 1,8)
ficava bonita e **matava a função primária**: com tudo brilhando, a lavagem quente
do trecho visto some no meio da cor e a tira deixa de responder «onde eu estou».

⚠️ **O efeito depende do filme, e isso não é defeito.** *007* de 1969 tem paleta
lavada e satura menos vistoso que um filme moderno. A referência tinha cores muito
separadas porque eram cenas de **filmes diferentes**; num filme só a tira é mais
harmônica, que é o correto.

**E o cabeçote virou janela.** Era um traço de 2dp; agora é uma moldura acesa em
volta do quadro atual, com halo e a lente no pé. A mudança não é decorativa: um
traço marca *uma posição*; uma janela com o quadro dentro diz que a película
**está passando por ali**.

⚠️ **Dois defeitos de posicionamento, os dois pegos por foto:**

1. A janela desenhava como dois traços finos. A primeira versão aninhava uma caixa
   de largura fracionária com um `offset` — e fração encadeada com deslocamento é
   o mesmo defeito que a cortina já tinha cobrado. Com a largura na mão
   (`BoxWithConstraints`), a conta é uma linha.
2. O halo e a lente foram parar na ponta esquerda enquanto a moldura estava no
   meio: **`offset` antes de `align`** desloca e *depois* alinha, e o alinhamento
   come o deslocamento. `align` primeiro faz o `x` ser absoluto.

#### A tira: tocar pra pular, e o cinza que não dizia nada

Duas queixas do dono ao usar, e as duas eram defeito:

**«não consigo clicar pra avançar».** A tira só escutava arrasto, e
`detectHorizontalDragGestures` **não dispara em toque parado** — o dedo tem que
andar. Encostar num ponto da timeline não fazia nada. ⚠️ E o buraco **não era
novo**: a `Linha` que ela substituiu tinha o mesmo desde sempre, e ninguém tinha
percebido porque ninguém tinha tentado.

O conserto são dois `pointerInput` e não um: toque e arrasto são detectores
diferentes, e empilhá-los no mesmo bloco faz um engolir o outro.

**«a timeline é um negócio só cinza».** Com a folha de sprites ausente, as células
são todas escuras — e a lavagem do trecho visto estava em 0,06→0,22 de alfa, fraca
demais pra separar o que passou do que falta. A tira inteira lia como uma barra
cinza.

A função primária de uma timeline é **dizer onde você está**, e isso tem que
sobreviver à ausência dos quadros. A lavagem subiu pra 0,20→0,52 e as células por
ver escureceram. **Medido no aparelho:** tocando a 60% da tira, o filme foi de
`faltam 1:36:30` pra `faltam 38:13`, e a tira mostra o trecho visto em âmbar
contra o resto escuro.

#### ⚠️ Perseguir só a si mesmo — um defeito que o campo novo revelou

Com o `user_id` no ar, apareceu um erro que existia calado: **o player perseguia a
posição de qualquer pessoa**. Medido no aparelho, com o filme aos 2min e um
progresso de outro aparelho aos 1h25: **o filme pulou pra 1h25**.

Enquanto o evento não dizia de quem era, isso estava certo — a única leitura
possível era «outro aparelho seu». Agora a regra separa:

| de quem | o que fazer |
|---|---|
| **de outro aparelho seu** | perseguir — é o «parou na TV, continua no ônibus» |
| **de outra pessoa** | **marcar na tira**, e não tocar no seu filme |

Sem saber quem se é, vale o comportamento antigo: `meuUsuarioId` nulo só acontece
enquanto o `auth/me` não respondeu, e nesse instante perseguir é o que o app fazia
ontem.

⚠️ **Nenhum dos dois foi verificado**, e o motivo está abaixo.

#### ⚠️ Eu derrubei o barramento do próprio app, com a armadilha documentada

Pra ler o evento cru eu mintei um token de mídia com `curl`. O §43 avisa: **emitir
um token novo aposenta o anterior**. E o `garantirTokenDeMidia` só pede um token
quando **não há nenhum guardado** — ou seja, o app seguiu usando o token morto,
tomou 401, tentou cinco vezes e desistiu. Nenhum evento chega desde então.

Caí na armadilha exatamente pra testar a coisa que ela quebra.

⚠️ **E há um defeito de produto aqui, independente do meu erro:** o app **não se
recupera** de um token de mídia aposentado. Qualquer coisa que emita um token novo
pra a mesma conta — o web abrindo, outro aparelho entrando — mata o barramento
deste até a sessão ser refeita. E mata **em silêncio**.

O conserto certo é o barramento pedir um token novo ao tomar 401, **uma vez**,
antes de desistir. Isso não viola o §43: renovar quando o antigo comprovadamente
morreu é diferente de renovar no meio de um filme. **Não foi feito** — é decisão
do dono, e não era o pedido desta rodada.

#### ⚠️ O borrão não funciona, e é limitação de plataforma

O «foco» — o filme sair de foco com o cromo aberto — **foi tentado e falha**, por
dois caminhos:

- `Modifier.blur` age na camada de composição, e o vídeo não está nela.
- `View.setRenderEffect` (API 31+) **também não**: medido no aparelho com **API
  37**, o código roda e o filme continua nítido. O `SurfaceView` é composto pelo
  SurfaceFlinger num plano separado, fora do alcance de efeito da hierarquia.

O que faria funcionar é `surface_type=texture_view`, e o preço é de vídeo: some o
overlay de hardware e cada quadro passa a ser copiado pra uma textura — num HEVC
4K em Direct Play, que é metade deste acervo, isso aparece em bateria e quadro
perdido. **Fica como decisão do dono.** O código morto foi apagado; o véu uniforme
faz a legibilidade sozinho, e ele nunca foi plano B — existiria de qualquer jeito
pros aparelhos abaixo da API 31.

#### ⚠️ A tira caía numa barra fina, e o erro era de projeto

> «meu, você não fez quase nada do que eu pediu» — e ele estava certo.

A primeira versão fazia a tira **depender** da folha de sprites: sem folha, ela
colapsava numa barra de 3px. Como **nenhum arquivo deste acervo tem folha
gerada**, o redesenho inteiro desaparecia no aparelho.

O erro não foi o 404 — foi ter amarrado a forma ao conteúdo. **Uma tira de filme
sem os fotogramas revelados continua sendo uma tira de filme**: tem perfuração,
tem célula, tem a janela do projetor passando por ela. O que falta é a imagem, e
ela chega quando o servidor gerar, sem esta tela mudar de forma.

E não é inventar dado (§18): célula escura é película **não revelada**, que é
literalmente o estado do arquivo. Desenhar retângulos coloridos no lugar dos
quadros, aí sim, seria afirmar cena que não se sabe.

**Visto no aparelho:** a tira desenha as perfurações nas duas bordas, as células e
a lente, com os quadros vazios. Os fotogramas continuam pendentes do servidor. Testado em *Armas em Jogo* e em *Juno*: a rota devolve 404
nos dois, e o `erroDaFolha` fica nulo, que é o caso documentado de «o servidor
ainda não gerou».

#### ⚠️ E não era pedido de servidor — era um botão que já existe

Esta rodada registrou aqui um pedido pro `serious-server` pedindo que ele
«gerasse as folhas de sprites». **Estava errado, e quem corrigiu foi o dono**, com
uma pergunta: «mas não fazemos algo assim quando selecionamos capítulos no menu de
DVD? no web já é assim, dá uma olhada».

Dando: o web tem uma tela de servidor (`Servidor.tsx`) com uma operação chamada
**Sprites**, ao lado de «Identificar» e «Embeddings». A descrição é dela:

> «Folha de miniaturas pro preview de seek. Decodifica o arquivo inteiro, então é
> a mais demorada — uma vez por arquivo, e fica em cache pra sempre.»

O botão chama `POST /api/scrub`, e há `GET /api/scrub/status` devolvendo
`running · current · total · done · failed · errors`. **É operação, não código.**
O 404 nunca foi pendência de implementação: é uma tarefa de manutenção que ninguém
disparou neste acervo.

E o `scrub_finished` que o barramento já emite é o aviso de que terminou — o app
passa a preencher a tira sozinho, sem release novo.

⚠️ **E as cenas do menu de disco não são a mesma coisa**, que era a outra metade
da pergunta:

| | folha de sprites | cenas do menu |
|---|---|---|
| rota | `GET /api/media/{arquivo}/scrub` | `GET /api/works/{obra}/cenas` |
| o que é | **uma imagem** com o filme inteiro em grade | **doze imagens** avulsas |
| quando | lote, uma vez por arquivo, cache pra sempre | sob demanda |
| custo | decodifica o arquivo inteiro | ~4s na primeira vez |

O comentário do `api.ts` do web confirma o porquê da separação: a grade de cenas é
pedida **ao entrar na tela de cenas, nunca ao abrir o menu**. Doze pontos não
cobrem 1h37, e pedir uma extração por posição arrastada seria uma requisição por
pixel — que é exatamente o problema que a folha existe pra resolver.

**O pedido que sobra pro servidor** é só o `quem_nome` no evento `progress` do
barramento, pras marcas de onde a casa parou. Esse sim é campo novo.

⚠️ **Escrito no acervo:** o progresso de *Armas em Jogo* e de *Juno* na conta do
`sam`, das verificações. Os dois ficaram perto do início.

### ⚠️ O `screencap` preto tem conserto, e não era a tela

O `PARIDADE` dizia que o `screencap` devolve quadro preto «no palco e no menu de
disco», e por isso o palco nunca foi fotografado. **Não é da tela** — nesta
rodada ele veio preto na loja inteira, e depois na biblioteca também (21 KB, o
tamanho de um quadro vazio), com o system UI aparecendo por cima.

O que destrava é **`force-stop` e relançar o app** antes de capturar:

```bash
adb shell am force-stop dev.odeon.android.debug
adb shell monkey -p dev.odeon.android.debug -c android.intent.category.LAUNCHER 1
```

Depois disso a mesma tela deu 1,0 MB de PNG legível. A superfície do app entra
num estado que a captura não lê — provavelmente depois de o app ficar horas em
segundo plano — e reiniciar o processo a recria.

**Isso desbloqueia a pendência do palco**, que estava anotada como «nunca visto em
foto». Não foi feita aqui porque não era o pedido.

#### ⚠️ E este diagnóstico estava errado. A causa é o **emulador**, não o app

Ao fim de 05/08/2026 o quadro preto deixou de ser intermitente e virou permanente
— **o dono viu a tela do aparelho preta**, não só a captura. A prova de que não
era o app:

| | |
|---|---|
| `dumpsys window` | `mCurrentFocus` na `AtividadePrincipal`, `screenState=SCREEN_STATE_ON` |
| `logcat` | nenhuma exceção, nenhum `FATAL` |
| `uiautomator dump` | **a árvore inteira**, com a grade composta: Juno, os 007, tudo |
| `screencap` | 21 KB — preto uniforme |

Ou seja: o Compose compunha, o app respondia, e **só o renderizador do emulador
estava morto**. O `force-stop` funcionava porque recriava a superfície; quando o
renderizador degrada de vez, ele para de bastar.

**O conserto é reiniciar o emulador**, e vale trocar o modo de GPU na volta:

```bash
adb emu kill
sleep 10
"$HOME/Library/Android/sdk/emulator/emulator" -avd Medium_Phone -no-boot-anim -gpu host &
```

⚠️ **Espere o processo antigo morrer.** Subir o novo antes derruba com «Running
multiple emulators with the same AVD» — o `sleep` não é enfeite.

⚠️ **Trocar `-gpu` faz o emulador ignorar o snapshot** («starting from scratch:
different renderer configured»). O `userdata` **sobrevive**: o app continuou
instalado e a sessão do `sam` continuou aberta, verificado depois do reboot. O que
se perde é o boot rápido.

**Como saber qual dos dois é** antes de reiniciar à toa: se o `uiautomator dump`
traz texto e o `screencap` dá ~20 KB, é o renderizador. Se a árvore vier vazia, aí
sim é o app.

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
servidor de casa — `assembleDebug` ✅, **109 testes** ✅, `lintDebug` sem nenhum
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

## 10b. As rodadas de 06/08/2026 — o player cobra, e a ficha vira fachada

Doze rodadas, e elas têm uma forma em comum que vale dizer antes: **quase nada
aqui foi achado lendo código**. Foi o dono usando o app, uma queixa por vez, e
cada queixa desmontando uma suposição que tinha passado por compilação, testes e
lint. A lista de defeitos abaixo é, quase inteira, coisa que estava verde.

### A primeira: o relógio do player mentia, e ninguém tinha olhado

O `deslocamentoMs` existe desde 04/08 com um comentário de dez linhas explicando
exatamente o defeito que ele evita: em HLS a sessão abre em `start=N`, então o
segundo zero do player é o segundo N do filme. Ele era aplicado em **dois**
lugares — a marca de progresso e a perseguição — e esquecido em todos os outros.

**Fotografado**, retomando *007: A Serviço Secreto* aos 1h22 por HLS: o filme
aparecia certo, Blofeld no chalé, e o cromo dizia **`0:43`** com «faltam
**2:21:35**» — o filme inteiro. A janela do projetor morava na primeira célula da
tira, e a lavagem do trecho visto media meio por cento.

| | quem morde |
|---|---|
| o relógio e o «faltam» | contavam da sessão contra a duração do arquivo |
| a janela na tira | primeira célula o filme todo |
| **tocar na tira** | **o pior** — fração do filme, `seekTo` da sessão: tocar em 20% de um filme retomado em 1h19 pedia 1h47 |
| os saltos de −10s e +30s | ✅ relativos, e relativo não se desloca |
| a marca de progresso | ✅ |

⚠️ **A prova de que era só a tela:** o servidor recebeu a posição certa o tempo
todo. A biblioteca foi de `faltam 63min` pra `faltam 60min` enquanto o cromo
anunciava o filme inteiro pela frente.

E havia agravante de dado: um toque errado na tira pulava pro lugar errado, e a
marca gravava **aquele** lugar. Erro de tela virando erro no banco de três
pessoas.

O conserto são duas funções com nome — `tempoDeFilme` e `tempoDeSessao` — e a
tela convertendo **na borda**, numa linha só. Espalhar `+ deslocamentoMs` pelos
sete pontos de uso foi o que criou o defeito da primeira vez.

⚠️ **Só morde com `deslocamentoMs > 0`**, que é continuar um filme que vem por
HLS. Em Direct Play as duas funções são a identidade — e é por isso que 109
testes verdes e um lint limpo não pegaram nada, e por que a tira tinha sido dada
como verificada na véspera: ela foi aberta num filme começando do zero.

**Depois:** `1:20:29` · «faltam `1:01:49`», soma fechando com a duração do probe.

### A segunda: o filme não parava ao voltar, e a barra de status estava no ar

Duas queixas na mesma frase, e a primeira era de desenho do serviço.

O `ServicoDeMidia` foi escrito pra o player **sobreviver** à tela — é o que faz a
janelinha e os controles da tela de bloqueio existirem. Só que «a tela saiu de
cena» tinha virado sinônimo de «continue tocando». A distinção que faltava é *por
que* ela saiu:

| | |
|---|---|
| janelinha, ou o app foi pro fundo | a tela continua composta, e o filme segue |
| `voltar`, ou o botão do sistema | a tela é destruída, e o filme acaba |

⚠️ **Girar não passa por aqui**, e é o `configChanges` do manifesto que garante.
⚠️ **Quem está na TV não para** — desligar a sala porque alguém fechou a tela do
celular seria o oposto do que a §4c promete.

**Medido:** antes de voltar, `PLAYING(3)`; depois, **`NONE(0)`**, notificação de
mídia fora.

E o player era a única tela que usava a tela inteira **e ainda tinha** a hora, o
sinal e a bateria desenhados sobre a imagem — está nas fotos da véspera: `11:40`
sobre o rosto de quem está no quadro. Entrou o `ModoDeSala`, com
`BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE`: escondida pra sempre prenderia quem quer
ver a hora.

### A terceira: o cabeçalho do player, que o dono chamou de feio

> «me dê um redesign da parte de cima do Player, o que temos hj ta mt feio»

Eram **quatro blocos** empilhados à esquerda, cada um com um tamanho e nenhuma
margem em comum: título serifado truncado, `janelinha` e `voltar` como palavras
cruas, a pílula do plano, e noventa caracteres explicando o Cast.

Três defeitos de fundo, e nenhum é gosto:

- **O `voltar` era a palavra mais apagada da tela** — a ação mais usada de um
  player, em 13sp, sem alvo próprio, no canto oposto ao polegar. Virou galo na
  borda de entrada com 44dp.
- **O dado menos importante era o segundo mais gritante.** `transcodificando` diz
  respeito a uma decisão já tomada pelo servidor. Virou **lâmpada** — âmbar
  transcodificando, verde direto —, com a palavra ao toque. A forma já existia no
  app: é a lâmpada da marquise da cortina.
- **A frase do Cast era a coisa mais larga da tela.** Deitada, caía sobre madeira
  clara e sumia. O ícone nasce riscado e a frase é a resposta ao toque.

⚠️ **Isso não afrouxa o §53 nem o §8b:** um ícone riscado não oferece — nega de
cara —, e o toque responde com a frase inteira, incluindo onde se resolve. O que
saiu foi só a permanência.

⚠️ **E uma coisa que o código respondeu e mudou o desenho:** não existe ação de
«mandar pra TV». O `EstadoDoCast` apenas *observa* uma sessão iniciada por fora.
Um ícone de cast aceso seria um clique que não faz nada — então ele nasce **só
quando há impedimento**.

**Duas rodadas de ícone, as duas cobradas por ampliação:** na primeira os arcos do
cast entravam por dentro do retângulo e viraram rabisco; na segunda o vão do
riscado tinha 3,2× a barra e apagava o glifo.

### A quarta: `cc` e o áudio no rodapé

> «O icon de legenda tu pode colocar em baixo e mude o icon para o icon cc. Tem
> que adicionar um icon do lado de legenda para o audio tb, alguns filmes tem
> dual audio»

Legenda e áudio respondem à **mesma** pergunta — «em que língua eu vou ver isto?».
Separá-los, um no alto e outro em lugar nenhum, era o que fazia a legenda parecer
prima do `voltar` e do PiP, que são navegação.

⚠️ **`cc` contraria a regra 1, e é decisão do dono.** A primeira versão desenhou
duas barras de propósito, com o argumento escrito. O contra-argumento dele é bom e
está registrado: `cc` não se lê como palavra em lugar nenhum — se lê como símbolo,
do mesmo jeito que `▶` não é inglês.

**E um defeito que a foto pegou:** o menu de áudio abriu com a faixa chamada
**`und`**. Não é idioma — em ISO 639 quer dizer *undetermined*, o contêiner
declarando que não sabe. Agora `und` e vazio caem pro rótulo posicional.

### A quinta: o dual audio que sumia, e por que só ele

O botão de áudio não aparecia em lugar nenhum, e o dono mandou o caso: *Família de
Aluguel*, que no Jellyfin mostra `PT-BR 5.1 Dolby Digital+` e `Inglês 5.1 AAC`.

**A causa não era o app.** Perguntar ao `Player.currentTracks` sempre responde
«uma»: ele oferece o que está na **playlist**, e em transcodificação o `ffmpeg`
põe uma faixa só. E o filme é transcodificado porque o PT-BR é `eac3` — medi o
emulador, e ele não tem decodificador de ac3 nem de eac3:

```
audio/mp4a-latm · audio/mpeg · audio/opus · audio/vorbis · audio/flac
```

⚠️ **Ou seja: o dual audio sumia exatamente nos arquivos que o têm.** O servidor
mediu depois: **3.469 arquivos** do acervo com duas ou mais faixas, e o formato
recorrente é `ac3:por | aac:eng` — o PT-BR ser ac3 é o que força o transcode que
faz a segunda faixa desaparecer.

O servidor entregou `audio_tracks[]` no plano e `audio_track=N` em `/plan` e
`/session`. Do lado do cliente: a lista passou a vir do **plano**, e trocar refaz
o plano e não só a sessão — porque o `mode` depende do codec da faixa escolhida.

**Medido, ponta a ponta:** sem pedido, `transcodificando` e lâmpada âmbar; pedindo
a faixa 1 (`aac:eng`), **`direto`** e lâmpada **verde**, com a posição
atravessando a troca (`5:05` → `5:49`). Escolher a faixa inglesa removeu o único
motivo de transcodificar.

⚠️ **Em `direct_play` a faixa é reaplicada no cliente**, senão o menu mudaria o
rótulo sem mudar uma nota: o plano novo devolve a **mesma** URL, e o player
recarrega escolhendo a primeira sozinho.

### A sexta: a reprodução morria sem dizer nada

> «quando eu passo o filme pra frente clicando em um ponto avançado da timeline o
> mesmo só trava e não funciona mais»

**Não havia `Player.Listener` no app inteiro.** O `estado.erro` cobria falha ao
*montar o plano*; tudo que quebrasse **depois** do `prepare` não tinha por onde
aparecer.

**Medido**, cortando a rede no meio do filme:

| | |
|---|---|
| o player | `ERROR(7)` |
| a rede voltando sozinha | continua `ERROR` |
| apertar o play | continua `ERROR`, posição congelada |
| a tela | título, relógio e «faltam» **como se estivesse tocando** |

Duas coisas somadas, e a primeira é do Media3 e não é defeito: depois de um
`PlaybackException` o player fica ocioso, e **`play()` não faz nada** — só
`prepare()` levanta.

Agora há um ouvinte, **uma** tentativa calada de `prepare()` — que resolve piscada
de rede sem ninguém ver mais que um engasgo — e, no segundo erro, uma frase que
sai do código que o Media3 afirma, com um `tentar de novo` que refaz plano e
sessão **no ponto onde parou** (`1:28:11`, verificado).

⚠️ **Um teste pagou por si aqui:** `ERROR_CODE_DECODING_FAILED` vale **4003**, e eu
tinha mapeado o decodificador como `3000..3999` — a faixa 3000 é de *parsing*.
Compilava, e a frase num aparelho sem decodificador seria a genérica.

### A sétima: filme terminado ficava impossível de reabrir

> «tentei começar o família de aluguel que tu tinha terminado, o filme abre no
> final dele e dps aparece essa mensagem»

`position_seconds` de um filme visto até o fim **é** o fim, e o app retomava lá.
Em `direct_play` seria só esquisito; em HLS a sessão nascia sem nada pela frente e
a reprodução morria com «este trecho não está na sessão de transcodificação».

⚠️ **A régua não foi inventada.** O `Details.tsx` da web tem a linha, com o
comentário sobre o piso:

```js
const retomando = work.position_seconds > 30 && !work.finished && restam > 60;
```

O `ObraDetalhada` **já recebia `finished`** do servidor, e o app inteiro ignorava.
Virou uma função só — `ondeContinuar` —, usada pela ficha e pelos baixados, e o
rótulo do botão sai da **mesma** função que decide a posição.

⚠️ **E um teste cobrou outro erro meu:** `duration_seconds` chega **zerado** em
arquivo sem probe, e sem tratar zero como ausente a conta `0 − 3401 ≤ 60` mandava
pro começo *todo* filme não medido.

**Um pedaço ficou do lado do servidor**, e ele fechou depois: o `finished` era
grudento — a posição gravava, mas a obra nunca voltava a poder ser continuada. O
`RESTART_RATIO` de 5% resolveu, e o app não precisou de uma linha: ele já
escrevia o heartbeat e já honrava o campo. **Verificado:** `assistir` → tocar 54s
do começo → **`continuar`**, e a obra de volta à fileira.

⚠️ **E um erro de diagnóstico meu, registrado porque é instrutivo:** eu afirmei que
a posição não estava sendo gravada, com base na barra de progresso do cartão não
se mover. Estava sendo gravada — o que eu li era **a lista em cache**, carregada
antes da reprodução, e o app não a refaz.

### A oitava: a sessão de HLS não tinha dono

> «aperta assistir no família de aluguel que já tá aberto e o mesmo continua em um
> ponto avançado (…) se continuar indo e voltando tu vai ver que o filme avança
> bastante»

**Medido**, abrindo o mesmo filme quatro vezes com ~17 segundos de relógio entre
uma e outra:

| abertura | posição |
|---|---|
| 1 | `0:13` |
| 2 | `11:02` |
| 3 | `20:24` |
| 4 | `27:35` |

Não era o filme avançando — era a **borda viva** da playlist. O
`viewModel(key = "player:…")` do `AppOdeon` é do escopo da **atividade**, não da
rota: sair do player não limpa o modelo, ele fica guardado com a URL da sessão
anterior dentro, e aquela sessão continua sendo escrita pelo ffmpeg. Reabrir uma
playlist sem `ENDLIST` faz o ExoPlayer entrar na borda dela.

⚠️ **E o mesmo escopo era um vazamento de ffmpeg.** O `onCleared` só roda quando a
atividade morre, então cada filme aberto deixava uma sessão viva no
`serious-server` até o app ser fechado. **É a hipótese que o comentário da
`sessaoAberta` registrava sem conseguir provar** — a que explicava o mesmo arquivo
devolvendo `direto` numa execução e `transcodificando` na outra, com capacidades
idênticas.

Trocar o escopo mexeria na navegação inteira. A tela passou a declarar as duas
pontas: `garantirPreparado` ao entrar, `encerrar` ao sair.

### A nona: o salto que congelava, e por que só num filme

> «no família de aluguel se eu tento dar um avanço clicando na timeline o mesmo só
> trava e não funciona mais»

Em HLS a playlist só contém o trecho entre o `start` da sessão e o ponto a que a
transcodificação chegou. Pedir fora disso **não dá erro**: o ExoPlayer espera.
**Medido**, saltando de `47:20` pra `1:32:52`: `BUFFERING`, e ficou. Destravou
sozinho depois de ~15s, quando o ffmpeg alcançou — o que é sorte, não desenho.

⚠️ **E o conserto da oitava rodada tornou isto visível antes de melhorar.**
Enquanto as sessões ficavam abandonadas, o ffmpeg seguia transcodificando em
segundo plano e a playlist ficava minutos à frente — o que fazia o salto funcionar
por acidente. Fechar a sessão ao sair tirou esse colchão.

⚠️ **E é a resposta do «por que só o Família de Aluguel»:** ele era o único
`transcodificando` dos que o dono testou. Os outros são `direto`, onde o arquivo
inteiro está no aparelho e todo instante existe. A transcodificação anda a ~**35×**
o tempo real neste servidor — medido nas quatro reaberturas —, então uma sessão
com 30s de vida tem uns 17 minutos escritos: clicar dentro deles funciona, além
deles congela.

Agora salto pra fora da sessão vira **sessão nova naquele ponto**. Quem diz o que
a sessão tem é o `Player.duration` — justamente o número que a timeline não usa
porque mente durante a transcodificação. Inútil como denominador, exato como «até
onde dá».

E isso resolve a outra ponta de graça: o `tempoDeSessao` documentava que rebobinar
antes do início da sessão parava no começo dela, porque «voltar de verdade exigiria
abrir outra sessão». Exigia — e agora existe.

**Medido:** `0:12` → `1:33:09` e `1:33:09` → `22:09`, os dois tocando em 3s, sem
passar por `BUFFERING`.

### A décima: a cortina que reabria, e a legenda que caía junto

> «pq no família de aluguel quando eu clico avanço na timeline o mesmo abre as
> cortinas novamente? no guns akimbo e em outros não aparece»

Refazer a sessão desmonta e remonta o `Reprodutor`, e ele levava junto o
`remember` que guardava «a cortina já abriu». O comentário dela sempre disse a
intenção certa — «acontece uma vez só na vida **desta tela**» — mas o estado morava
na vida da **fonte**, e a distinção não custava nada enquanto a fonte nunca trocava
no meio de uma visita.

Conferindo o que mais caía junto apareceu coisa pior: com `PT-BR FULL` no ar, um
salto devolvia o menu marcando **`sem legenda`** — e não era só o rótulo, a faixa
sumia, porque o `MediaItem` novo traz outros `TrackGroup`s e o override antigo não
casa com nenhum.

Cortina que reabre é feio; **legenda que cai sozinha é a pessoa perdendo uma
escolha sem ninguém dizer nada**. As duas subiram pro `TelaDoPlayer`, e a legenda é
reaplicada no `onTracksChanged` — o único momento em que dá, porque um override
aponta pra um grupo *daquela* fonte.

⚠️ **Anotado e não consertado:** o menu de legendas deste filme tem 16+ faixas e é
uma coluna sem rolagem — medido de `y 37` a `y 1080`, com a última entrada cortada
na borda. As faixas existem, aparecem, e não dá pra chegar nelas.

### A décima primeira: o progresso que oscilava, e o piso do «teco»

> «tem hora que o botão volta como assistir (…) tem hora que ele aparece continuar
> e funciona tranquilo»

**Duas causas somadas.**

Sair do player dispara a marca de `abandon` e a releitura da ficha **no mesmo
instante**, e quem chega primeiro ao servidor é sorteio. Quando a leitura ganhava,
o botão saía do dado **anterior** à sessão que acabou de acontecer. Agora o player
anota a posição a cada 200ms e o `voltar` leva o número junto como **dica** — não
há releitura mais fresca que o que o app viu com os próprios olhos.

⚠️ **A segunda era minha:** ao refazer a sessão eu zerava o `deslocamentoMs`
**antes** de o `Reprodutor` desmontar, e o desmonte grava uma marca convertendo com
o deslocamento que estiver no estado. Zerado, tempo de sessão virava tempo de
filme: posição errada no banco a cada salto que refazia sessão.

E uma regra nova, ditada pelo dono:

> «Ao iniciar um filme a pessoa pode assistir um teco e voltar, isso já deve salvar
> o progresso dela.»

O piso de «começou» caiu de **30s pra 5s**. Com 30, um teco de quinze segundos era
salvo no servidor e **ignorado pelo botão**. Os 5 que sobram separam só o toque
acidental.

⚠️ **Diverge da web e do `/api/continue` de propósito**, e fica registrado: a
fileira do servidor ainda considera «começou» a partir de 30s, então um teco de 15s
retoma pela ficha mas não aparece na fileira. **Alinhar é pedido pro
`serious-server`.**

**Medido:** três voltas seguidas, três `continuar`, posição batendo (`1:04:50` →
`1:04:51`), sem a deriva de dez minutos por volta.

### A décima segunda: a ficha vira fachada

> «Quero um redesign dessa tela, tá absurdamente simples, feia. Gosto de coisas
> experimentais, com animação, luzes, algo que lembre odeon»

Era pôster à esquerda, quatro linhas de metadado à direita, sinopse, pílulas e um
botão. **A única tela do app que não sabia que o app é um cinema:** o player tinha
cortina, película e facho; a locadora tinha caixa, cinta e estante; a ficha tinha
um formulário.

Foram desenhadas **dez direções** e mostradas ao dono. Ele escolheu duas — a
**marquise** e o **varal** — e pediu o misto. As duas viraram uma só, e a costura
não é decorativa: **o fio sai dos cantos de baixo do letreiro** e verga com o peso
das fotos. É o que os cinemas de rua faziam — o letreiro em cima, as fotos de cena
na vitrine embaixo. Empilhar as duas seria pôr dois enfeites na mesma tela.

| | |
|---|---|
| a marquise | letreiro serifado com 14 lâmpadas, e o selo do plano **dentro** dele |
| o varal | três cenas reais penduradas por prendedores, com o fio vergando |
| o bilhete | o «continuar» como ingresso picotado, com o canhoto dizendo de onde parou |
| os canhotos | baixar e pegar a fita, como talões arrancados do bilhete |

As lâmpadas acendem com a **mesma curva de `keyframes` da cortina** do player, e
depois respiram — **cada bulbo com seu desvio**, porque letreiro em que todas as
lâmpadas fazem a mesma coisa ao mesmo tempo é um retângulo que pisca. As fotos caem
com `spring` de amortecimento baixo e balançam até assentar, e a rotação nasce **no
prendedor**, que é por onde elas estão presas.

⚠️ **E o varal é navegação, não enfeite.** Cada foto é uma cena real de
`GET /api/works/{obra}/cenas` — a mesma rota que enche a tira do player — e tocar
numa abre o filme naquele minuto: **`CENA 07` levou a `55:10`**, verificado. As três
são espalhadas pelo filme, e não as três primeiras: essas são sempre logo de
distribuidora, plano de estabelecimento e primeira fala.

Sem cenas, o varal não nasce — nem fio, nem espaço vazio. §24 e §53 juntos: um fio
pendurado sem fotos prometeria uma navegação que não existe.

⚠️ **Entraram três cores no tema** — `papel`, `tintaDoPapel`, `tintaDoBilhete`. Não
é «tema claro», e não deve haver um: a sala é escura por decisão, e clarear a
interface desfaria o facho, a cortina e a lâmpada do plano de uma vez. Elas existem
só onde a tela desenha **um objeto de papel**, e o branco é sujo de propósito
(`F2ECE0`) porque branco puro no meio de tela escura vira buraco de luz em vez de
objeto.

**E os dois botões do rodapé ficaram pra trás na primeira entrega** — «você esqueceu
de atualizar esses dois tb». Eram a tela velha sobrevivendo embaixo: dois
`TextButton` de cinza apagado, empilhados. Viraram canhotos vazados, lado a lado,
com o picote na borda de cima dizendo de onde foram arrancados. **Vazados onde o
bilhete é cheio**, porque baixar é a exceção e dar a eles o mesmo peso faria a tela
perguntar uma coisa que já estava respondida.

### O botão de girar, no cabeçalho do player

Pedido à parte, e pequeno: um botão que trava a orientação, à esquerda da
janelinha. O ícone mostra o **destino** e não o estado — deitado desenha uma tela
em pé, e vice-versa —, que é a mesma régua dos arcos de `10` e `30`.

⚠️ **O `ModoDeSala` devolve a orientação ao sair**, junto com as barras. Sem isso a
trava vazaria pro app inteiro: a biblioteca ficaria deitada porque alguém quis ver
um filme deitado, e a única saída seria fechar o app. Devolve como `UNSPECIFIED` —
o **não pedido** —, pra valer a trava de rotação da própria pessoa.

### O que a foto pegou, e o código não

É a lição do §1 do `CONTINUAR-ANDROID.md` cobrada mais uma vez, e vale a lista
porque **todos passaram por compilação, 144 testes e lint limpo**:

1. **Um `padding` negativo derrubou o app** na primeira abertura da ficha
   redesenhada. O Compose recusa em tempo de execução com «Padding must be
   non-negative» — é `offset`.
2. **O selo do plano cobria oito bulbos** da marquise. A causa era sutil: o
   `padding` estava no `Box`, e padding encolhe a **área de conteúdo** — que é onde
   o `align` das lâmpadas mira. Elas deixavam de morar na borda do cartão.
3. **O fio do varal sumia**, por ser o cinza de divisória sobre preto.
4. **O ícone de girar precisou de duas rodadas** até o arco parar de parecer um
   gancho solto pendurado num retângulo.
5. **O ícone de cast precisou de duas**, pelo mesmo motivo.
6. **O relógio do player mentia** desde a véspera, com a tira apontando pra célula
   errada.

### O que ficou aberto, e é decisão do dono

| | |
|---|---|
| **o menu de legendas sem rolagem** | 16+ faixas, as de baixo inalcançáveis |
| **os saltos de 10s não acumulam** | trinta toques seguidos andaram 6 segundos: cada `seekTo` em HLS ainda buferiza quando o próximo chega |
| **o piso de 30s do `/api/continue`** | pedido pro servidor: um teco de 15s retoma pela ficha e não aparece na fileira |
| **o rótulo de áudio repete os canais** | `PT-BR 5.1 (5.1)` — é do servidor, e o `label` não se reescreve aqui |
| **faixa dupla de áudio nunca foi vista em `direct_play`** | o glifo e o menu estão fotografados, mas com o limiar baixado numa build descartável; num celular que decodifique eac3 o caso aparece sozinho |
| os quatro de antes | o borrão do «foco», a recuperação do token 401, a insígnia que rouba o toque, o botão de gerar sprites |

### O que foi escrito no acervo

Tudo na conta do `sam`, e quase tudo em **um** filme — *Família de Aluguel* virou o
banco de provas do dia:

- ele **não tinha marca nenhuma** antes de 06/08. A marca existe porque eu abri o
  filme pra ver as faixas de áudio, e depois ficou sendo o único transcodificado à
  mão pra reproduzir os defeitos de sessão. Terminou em **~55min**, com o `finished`
  ligado e desligado no meio do caminho;
- *Armas em Jogo* saiu de `faltam 40min` pra **38min**, e a deriva é de restaurações
  feitas na mão depois da falha de retomada;
- *007: A Serviço Secreto* saiu de `faltam 63min` pra **58min**;
- o exemplar `direto` do *007* e o *Cassino Royale* foram abertos por engano ao
  procurar o transcodificado, e ganharam marca.

⚠️ **Três minutos da deriva do 007 são meus e não do teste:** toques às cegas no
emulador caíram na tira e jogaram o filme pra `1:59`. Devolvi na hora, mas as
tentativas de reencostar no número original são elas mesmas escritas.

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
