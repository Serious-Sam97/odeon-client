# Odeon para iPhone e iPad — o plano

Escrito em **14/08/2026**, depois de o Android estar pronto e a TV redesenhada.

O pedido do dono, na íntegra, porque ele contém a restrição que decide tudo:

> «falta uma versão para iOS (iPhone e iPad). Quero para iOS **nativo**, mas como
> Android já tem toda lógica para telas pequenas e médias usando o dedo feitas,
> seria maneiro… não to falando pra usar o código do Android, to falando **se só
> ver as decisões** lá.»

⚠️ **Reaproveitar decisão, não código.** Isto não é um port, não é Compose
Multiplatform e não é webview. É SwiftUI escrito do zero, obedecendo a um
documento de decisões colhidas do Android.

---

## 1. As três decisões já tomadas

| | |
|---|---|
| **escopo da v1** | **paridade com o celular Android** — as dez telas, não uma espinha |
| **contrato da API** | **terceira cópia à mão**, comentada como as outras duas |
| **onde mora** | **`ios/`, neste repositório**, ao lado de `web/` e `android/` |

⚠️ O `clients/` **fica parado onde está**. Ele tem um `iosApp` de Compose
Multiplatform que compila pro simulador, e não serve aqui por dois motivos: ele
não é nativo (é a UI do Compose desenhada numa tela de iPhone, exatamente o que o
pedido exclui) e o `shared/Models.kt` dele tem **166 linhas** contra as **1.423**
do contrato de hoje. O README de lá já o declara superado em 12/08/2026, e a
espec (§4 do `docs/APP-ANDROID.md`) registrou que «o KMP fica parado onde está».
Não apagar, não reaproveitar.

⚠️ Sobre a terceira cópia do contrato: ela é **dívida assumida com os olhos
abertos**. O `Modelos.kt` abre dizendo que não há tipo compartilhado nem código
gerado entre os clientes, e chama isso de «a dívida que a separação dos
repositórios comprou». Escolhemos aumentá-la de duas para três cópias em vez de
bloquear o iOS num pedido ao servidor. O preço aparece no dia em que um campo
mudar de nome: serão três lugares para consertar, e o terceiro é o que ninguém
lembra. **Quem mexer no contrato mexe nos três.**

---

## 2. O que se herda — e é mais do que parece

O ativo deste projeto não é o Kotlin: é a densidade de decisão escrita. Estas são
as camadas que atravessam a plataforma inteira.

### As regras de produto

| | o que manda |
|---|---|
| **§18** | sem dado, a tela **omite** — não inventa, não escreve «—», não sorteia cor |
| **§24** | linha vazia **some** inteira, e não fica reservada |
| **§53** | não oferecer o que a validação vai negar — botão que leva a 403 é defeito |
| **§8b** | erro **visível e legível**: «HTTP 403» é visível sem ser legível |

### As regras de reprodução, que custaram caro

- **`ondeContinuar`** com piso de **5 segundos**, e o porquê: o piso era 30s como o
  da web, e o dono o derrubou («a pessoa pode assistir um teco e voltar, isso já
  deve salvar o progresso dela»). Um filme terminado retoma do **zero** — a
  posição de quem viu até o fim *é* o fim, e retomar lá deixava o filme
  impossível de reabrir.
- **O rótulo de faixa vem pronto do servidor.** Montar «Português - AC3 5.1» no
  cliente é a terceira redação da mesma frase — e agora seria a quarta.
- **`und` não é idioma.** É *undetermined* em ISO 639, o contêiner dizendo que
  não sabe. Mostrá-lo é mostrar dado de contêiner com cara de idioma.
- **As faixas de áudio vêm do `plan`, não do player.** Perguntar ao player sempre
  responde «uma»: em transcodificação o ffmpeg põe uma só na playlist, e o dual
  audio sumia exatamente nos arquivos que o têm.

### As armadilhas já pagas

- ⚠️ **Emitir um token de mídia novo aposenta o anterior.** Renovar no meio de um
  filme derruba o próprio player.
- ⚠️ **Só o 404 quer dizer «não há sprite».** Mascarar o 401 fez a web achar que
  o acervo inteiro não tinha preview de seek.
- ⚠️ **`work_count` não soma o acervo** — ele conta só o que foi identificado.
- ⚠️ **Uma obra pode ter mais de um arquivo, e um filme mais de uma obra.** São
  coisas diferentes: a ficha escolhe entre arquivos, a grade escolhe entre obras
  (§28 do `REDESENHO-TV.md`).

### As telas, e o que cada uma responde

O `REDESENHO.md` (703 linhas) é o redesenho **do celular** — é a doc que serve.
O `REDESENHO-TV.md`, apesar de ser a maior, é quase toda inaplicável: 3.920
linhas de D-pad, foco, trilho e sala de dez pés.

---

## 3. O que **não** se herda

| | por quê |
|---|---|
| navegação por foco, trilho, `saidaEsquerda`, `BackHandler` | não há D-pad; o dedo não tem foco |
| contornos de Media3, `MediaCodec`, `stepSetPQFormat` | são do decodificador da TCL |
| `TvProvider`, canal da home, `LEANBACK_LAUNCHER` | são do Android TV |
| DataStore, Retrofit/OkHttp, Coil, Palette | trocam por Keychain, `URLSession`, `AsyncImage` e extração de cor própria |
| o idioma Compose inteiro | `@Composable` não tem equivalente; SwiftUI tem as próprias regras |

---

## 4. ⚠️ Três coisas que precisam de resposta antes de escrever tela

### 4.1 O iOS não lê Matroska — e isso muda o custo do servidor

Já está escrito no `CapacidadesDoAparelho.kt`, sobre a web:

> «É a diferença mais visível pra web, que não põe `mkv` na lista — o navegador
> realmente não abre Matroska, e o Android abre. Ou seja: este app vai ganhar
> `direct_play` onde a web recebia remux.»

**O AVPlayer está do lado da web, não do Android.** Onde o Android toca direto, o
iPhone vai pedir remux. O caminho existe e é barato (`video=copy`, a web usa há
tempo, §26.9), mas ele acende ffmpeg na máquina da casa para mais um cliente.

### ⚠️ Medido em 14/08/2026 — e corrige a metade errada da pergunta

A frase acima era **afirmação herdada**: um comentário do Android *sobre a web*.
Agora está medida no próprio iOS, perguntando ao `AVURLAsset` em vez de listar
(`CapacidadesDoAparelho.swift`, travado por `CapacidadesTests`):

```
contêineres: mp4, mov, m4v          ← sem mkv, sem webm
vídeo:       h264, hevc, av1
áudio:       aac, ac3, eac3, alac, opus, flac
hls:         true
```

**A parte do contêiner se confirmou:** não há Matroska. O iOS está do lado da
web, como previsto.

⚠️ **Mas a parte do áudio estava errada, e a favor.** Eu escrevi «quantos desses
têm áudio que o iOS não decodifica», supondo que o ac3 fosse problema — o 007 em
pt-BR é `mkv` + `ac3` 6ch. **O aparelho declara `ac3` e `eac3`.** Ou seja:

> o que falta no iOS é **só a casca**. Vídeo e áudio já são decodificáveis; o que
> o servidor precisa fazer é **remux** — `video=copy` **e** `audio=copy` — e não
> re-encodar nada.

Isso muda o custo de «ffmpeg re-encodando pra mais um cliente» para «ffmpeg
copiando fluxo pra outro contêiner», que é barato e é o que a web já recebe.

⚠️ E uma incerteza honesta: **`mp3` não apareceu na lista**, e isso pode ser a
minha pergunta estar errada (o MIME que usei pode não ser o que a API espera) e
não o aparelho recusando. Não tratar como «iOS não toca mp3» sem medir de novo.

⚠️ E o `hevc em hardware` saiu `false` — mas **medido no simulador, que usa o
silício do Mac**. Isso é indício, não achado. Vale no iPhone do dono e em nenhum
outro lugar.

### ⚠️ 4.1b — «Perguntar, não listar» não bastou. A regra virou **provar, não perguntar**

O servidor mediu o acervo e devolveu: matroska 55%, mov/mp4 29,8%, **avi 14,7%**.
Com esse dado, ampliei a consulta ao aparelho e ele respondeu que abre `avi` e
decodifica `mpeg4` — o que tiraria **2.265 arquivos** de transcodificação.

**Era mentira, e a prova é de um arquivo de verdade:**

| arquivo | `isPlayable` | faixa lida | **quadro decodificado** |
|---|---|---|---|
| mp4/aac (controle) | `true` | 1920×816 @ 23,976 | ✅ **saiu** |
| avi/mpeg4+mp3 | `false` | 704×396 @ 25 | ❌ «Não é possível abrir» |

O AVFoundation **lê a casca do avi** — acha a faixa, a resolução, os fps, a
duração — e não decodifica o MPEG-4 Parte 2 lá dentro.

⚠️ **E a declaração errada é pior que a ausência dela.** Enquanto `avi`+`mpeg4`
estiveram declarados, o servidor mandava `direct_play` nesses 2.265 arquivos, que
**antes transcodificavam e funcionavam**. Declarar de menos custa ffmpeg à toa;
declarar de mais entrega arquivo que não abre — e esse o usuário não contorna.

**A diferença estrutural pro Android, e ela importa:** lá o `MediaCodecList`
**enumera os decodificadores que existem no aparelho** e é autoridade. Aqui o
`isPlayableExtendedMIMEType` é palpite otimista, da mesma família do
`canPlayType` da web — que a §3 da espec já chamava de «um palpite em texto».

⚠️ **Regra nova, imposta em `CapacidadesDoAparelho`:** codec só entra na lista
depois de um arquivo do acervo **produzir quadro** (`SondaDeReproducao`). `avi` e
`mpeg4` foram removidos, e medido depois: o servidor voltou a `transcode` neles.

### O que ainda falta

⚠️ **O `mp3` está declarado com base no mesmo oráculo que mentiu**, e **576
arquivos** dependem dele. A sonda procurou um `mov/mp4 + h264 + mp3` em seis
pontos do acervo e não achou — então ele segue **não provado**. Falta um id de
arquivo com essa combinação (o servidor contou 63 em mov) pra decodificar um
quadro e fechar.

### 4.2 Não há runtime de simulador nesta máquina

```
Xcode 26.6 ✓   SDK iOS 26.5 ✓   runtimes de simulador: ZERO   devices: ZERO
```

Compila e **não roda**. A regra mais cara da casa é *ver na tela antes de
escrever que funciona*, e sem runtime ela fica bloqueada desde o primeiro dia. O
conserto é um download grande, e é do dono:

```bash
xcodebuild -downloadPlatform iOS
```

⚠️ Enquanto isso não existir, **nada neste plano pode ser declarado pronto** —
só «compila».

### 4.3 A cenografia é 6.250 linhas, e ela é o produto

O `:cenario` não é enfeite: é a cortina, a marquise, a caixa de VHS em 3D, a
projeção, a fita e o verso da caixa. É o que faz o Odeon ser uma locadora e não
um catálogo. Em SwiftUI isso é `Canvas`, `TimelineView`, transições e um pouco de
3D — trabalho de verdade, e é onde «herdar decisão» mais paga, porque cada peça
tem escrito o **porquê** de ser como é.

---

## 5. O tamanho real da paridade

| | arquivos | linhas |
|---|---|---|
| `:core` — modelos, rede, doze `Modelo*` de tela | 33 | 7.213 |
| `:cenario` — a cenografia | 18 | 6.250 |
| `:app` — dez telas mais o player | 34 | 13.810 |

São **~27 mil linhas de Kotlin** para espelhar em Swift. O número não traduz
direto (SwiftUI é mais curto em layout e mais longo em estado), mas dá a ordem de
grandeza: **isto não é uma semana.**

As dez telas: login, biblioteca, obra, player, locadora, mural, guia, perfil,
para-você e baixados — mais busca e ao vivo, que têm modelo próprio no `:core`.

---

## 6. As fases

⚠️ **A ordem não é «telas primeiro».** Mesmo com paridade escolhida, o player vem
cedo de propósito: se o acervo não tocar no iPhone, toda tela construída antes
disso foi construída sobre uma suposição não medida. É a lição do §26 — hipótese
sem medida custa o dia.

| | o que entrega | pronto quando |
|---|---|---|
| **F0** | `ios/docs/DECISOES-HERDADAS.md` — a colheita do §2 deste plano, em regra acionável | o documento existe e cada regra aponta pra origem no Android |
| **F1** | capacidades do iOS + a medida do §4.1 respondida pelo servidor | sabemos quantos arquivos precisam de remux |
| **F2** | esqueleto: projeto Xcode, endereço do servidor, login, sessão no Keychain, camada de rede | login funciona **visto no simulador** |
| **F3** | **o player**, com AVPlayer: HLS, direto, faixas, progresso, retomada | um filme mkv e um mp4 tocam do começo e do meio, vistos na tela |
| **F4** | biblioteca + ficha, com a escolha de versões do §28 | grade rola, ficha abre, versões escolhem |
| **F5** | a cenografia — cortina, marquise, caixa, projeção | a locadora parece uma locadora |
| **F6** | locadora, mural, guia, perfil, para-você, baixados, busca, ao vivo | paridade fechada |
| **F7** | iPad — `NavigationSplitView` e classes de tamanho | as duas telas grandes não são o iPhone esticado |

---

## 7. Como se mede que está pronto

A mesma régua da casa, traduzida:

1. **Ver no simulador**, com captura — e no aparelho do dono quando houver.
2. Compilar e passar em teste **não diz nada** sobre layout, gesto ou quadro
   perdido.
3. Comentário não afirma comportamento que ninguém assistiu.
4. Se a doc parecer errada, **dizer antes de construir**.

---

## 7b. Onde a coisa está — 15/08/2026

| | |
|---|---|
| **F0** ✅ | `DECISOES-HERDADAS.md` escrito |
| **F1** ✅ | as duas metades medidas: as capacidades declaradas saíram de sonda, e o servidor contou o acervo por contêiner |
| **F2** ✅ | projeto, endereço, login, Keychain e rede |
| **F3** ✅ | o player: `direct_play`, `direct_stream`, faixas de áudio, legenda, progresso e retomada |
| **F4** ✅ | biblioteca (8.273), ficha, e a escolha de versões do §28 |
| **F5** ◐ | a caixa de VHS existe e gira; cortina, marquise e leque vêm com as telas que os pedem |
| **F6** ✅ | locadora, mural, para-você, perfil, guia e **baixados**. ⚠️ **«Ao vivo» saiu do escopo** — ver abaixo |
| **F7** ✅ | iPad — a grade e a locadora **medidas na tela de 13"**, e o que estava errado era o mesmo erro nas duas |

### ⚠️ «Ao vivo» não é paridade — foi erro meu de leitura

A F6 deste plano listava «ao vivo», e a §6 também. **O celular Android não tem
essa tela.** Conferido em 15/08/2026 no `AppOdeon.kt`: as telas de lá são login,
biblioteca, locadora, baixados, perfil, mural, guia, para-você, obra e player —
dez, e nenhuma é ao vivo. O que existe em `app/aovivo/` é só o agendamento de
lembretes; a tela mora na **TV** (`TelaAoVivoDaTv.kt`) e na web.

A escolha do escopo foi **paridade com o celular**, e paridade com o celular está
fechada. «Ao vivo» no iPhone seria produto novo, não paridade — e produto novo é
decisão de quem manda no produto, não minha.

**O que foi conferido na tela**, com captura, e não deduzido: login; a grade das
8.273 entradas; a escolha entre as duas versões de um 007; a ficha; um filme
tocando por `direct_play` **e** por `direct_stream`; o menu de áudio com `por
(5.1)` e `eng (5.1)`; a legenda desenhada por cima do vídeo; «para você» com o
motivo escrito; a locadora com as caixas de pé; o mural com as três pessoas da
casa; o perfil — nível 3, 384 XP, 9 de 80 conquistas; e o guia — a revista da
semana («diretor da semana», com o ensaio creditado ao llama-3.3-70b), o evento
em cartaz, os eixos, e o toque em «Drama» levando à grade filtrada com o ✕ que a
desfaz; e os baixados — «guardar» só na ficha `direto`, os 2,23 GB de «007 Contra
a Chantagem Atômica» descendo, o cartão com `1965 · 130min · 2,23 GB`, e o leão da
MGM tocando **do disco**.

⚠️ **O que NÃO foi conferido:** o ramo `cold_start` do «para você», porque este perfil já tem vetor.

### O que ficou aberto do outro lado

Nada disto trava a paridade, e nenhum é meu pra consertar:

1. **O guia conta rips; a biblioteca conta grupos.** A pílula do guia promete
   «Drama 228» e a grade abre com **216**. Medido em 15/08/2026 e a conta fecha
   exata: 216 grupos + 12 rips a mais = 228. **Não é o `kind`** — é o
   agrupamento de versões de 14/08, que o `/api/guia` ainda não aplica. Como quem
   conta é o servidor, os quatro clientes erram igual. Está escrito como Pedido 3
   no `PEDIDOS-AO-SERVIDOR.md`, com a medida junto. ⚠️ **Nenhum cliente deve
   consertar na tela**: seria a quarta cópia de uma regra de contagem.
2. **Os rótulos de áudio chegam como `por` e `eng`**, e não «Português». Traduzir
   no cliente seria a mesma tabela escrita **quatro** vezes — TV, celular, web e
   iOS —, e a quarta cópia é a que envelhece calada. É pedido de servidor.
3. **`emitir_token_de_midia` apaga o token dos outros clientes.** Com quatro
   clientes vivos, isso derruba a reprodução de quem estiver no meio do filme. O
   servidor aceitou; a prioridade é dele.


### As duas armadilhas do iOS que já custaram tempo

Elas não vieram do Android — são desta plataforma, e ficam escritas pelo mesmo
motivo que as outras.

**1. `INFOPLIST_KEY_*` não aceita chave de dicionário, e falha em silêncio.**
`INFOPLIST_KEY_NSAppTransportSecurity_NSAllowsArbitraryLoads` foi escrito, o
Xcode **ignorou sem avisar**, o app compilou, e a primeira requisição morreu com
«sem resposta do servidor» na tela. Só o `PlistBuddy` no `Info.plist` gerado
contou a verdade. ⚠️ **Compilar não prova nada** — é a regra da casa aparecendo
no primeiro dia de iOS.

**2. `Info.plist` dentro da pasta sincronizada quebra o build.** Ele é copiado
como recurso **e** usado como Info.plist, e o build morre com «Multiple commands
produce». Ele mora **fora** de `Odeon/`, e o `.pbxproj` diz por quê.

**3. `ENABLE_TESTABILITY` não vem de graça em projeto escrito à mão.** O Xcode a
liga sozinho no Debug de projeto criado por ele; aqui faltava, e o sintoma foi
`unable to resolve Swift module dependency to a compatible module: 'Odeon'` — que
não diz «faltou testabilidade» em lugar nenhum.

### Os testes

**56 testes, 9 suítes, 0 falhas**, em `OdeonTests/`. Eles cobrem o que dá pra
provar sem servidor:

| | o que trava |
|---|---|
| `EnderecoDoServidorTests` | a **quarta** cópia da regra de endereço. Cópia sem teste é cópia que diverge em silêncio |
| `ContratoTests` | a tolerância do `Codable` (chave ausente, tipo trocado, versão sem id) e a conta de retomada |
| `CapacidadesTests` | que a lista declarada só contenha o que a sonda provou — foi ela que pegou `avi`, `mpeg4` e `mp3` declarados a mais |
| `ProvaDeMp3` | o caso que custou caro: mp3 toca em `.mov` e é **invisível** em `.mp4`, e o acervo tem 64 `.mp4` com mp3 |
| `LegendaTests` | o relógio do WebVTT — `SS.mmm`, `MM:SS.mmm`, `HH:MM:SS.mmm` e vírgula decimal |
| `PerfilTests` | a única conta que a tela do perfil faz sozinha: a fração da barra de nível, e a faixa de largura zero que viraria 100% |
| `GuiaTests` | as duas formas do `chave` do eixo (etiqueta e década), o `kind=movie` que impede a pílula de mentir, e o eixo desconhecido que **não** vira rótulo inventado |
| `ViradaTests` | o relógio que a locadora e a revista dividem — com o fuso **fixado**, porque a borda desta função é o fuso |
| `BaixadosTests` | a extensão do arquivo guardado, que é o que decide se o filme abre, e o nome no disco ser o id e não a URL |

⚠️ E há as **sondas** (`SondaDeCapacidades`, `SondaDoAcervo`, `SondaDeReproducao`,
`SondaDeSegmento`, `SondaDeDualAudio`, `SondaDeLegendas`, `SondaDaContagem`,
`SondaDeDownload`, `SondaDeTranscode`, `SondaDaPlaylist`), que falam com o
servidor de verdade. Elas **não são testes** — são investigação com carcaça de
teste, e é por isso que não entram na contagem: uma delas falhar é notícia sobre
o servidor, não sobre este código. Elas rodam em série (`@Suite(.serialized)`)
porque em paralelo cada uma renovava o token de mídia e matava as outras.

⚠️ Eles são de **lógica pura**, como os do Android — nenhum olha pixel, e por isso
sobrevivem a redesenho. E eles **não** provam o outro lado: o JSON dos testes foi
escrito aqui, não capturado do servidor.

---

## 7c. F3 e F4 — o que está de pé, e o que não — 14/08/2026

### Visto na tela, com captura

| | |
|---|---|
| login → sessão → biblioteca | ✅ **8.273 entradas**, capas reais, paginação |
| escolha de versão | ✅ «Português» nomeada, o inglês em «versão 2», mais adiantada primeiro |
| ficha | ✅ selo do plano, «continuar de 1h23», «do começo», escolha de arquivo |
| player em `direct_play` | ✅ **filme na tela**, retomado, progresso marcado |
| **29 testes** | 0 falhas |

⚠️ **`direct_stream` não toca — e são 53,4% do acervo.** Tudo acima passou pelos
29,4% de `direct_play`.

### O que a tela pegou e o build não pegaria

- **«parou em 0min»** na escolha de versão. O piso de retomada é 5 s, o
  `duracaoCompacta` não tem segundos, e tudo entre 6 e 59 s virava zero. Uma
  linha que diz «0min» não diz nada. A frase mudou («parou no começo»); **a régua
  não** — o piso de 5 s é decisão do dono e continua.
- **O player falhava em silêncio.** A sessão abria, a URL montava, o `AVPlayer`
  nascia — e a tela mostrava o triângulo riscado do AVKit sem uma palavra. O erro
  mora no `AVPlayerItem`, não no `AVPlayer`, e só aparece em `.failed`.

### ⚠️ A saga do HLS, e o que ela ensinou

Quatro causas candidatas, testadas em ordem. **As três primeiras eram minhas.**

| hipótese | veredito |
|---|---|
| token não chega nos segmentos | ✅ era verdade — o servidor passou a carimbar |
| `/api/hls/` não aceita token na query | ❌ **falso** — aceita desde sempre |
| o token que eu tinha estava **morto** | ✅ **era isso** — e o app nunca sairia sozinho |
| descasamento do ponto de retomada | ❌ `start=0` e `start=1500` falham igual |

⚠️ **O defeito do meu lado era a metade que faltava da regra herdada.** «Emitir um
token de mídia novo aposenta o anterior» estava implementado — mas «o que fazer
quando o token que tenho já não vale» não estava, e o `garantirTokenDeMidia` só
pede quando **não há** token. Com um cadáver guardado, o app ficaria sem mídia pra
sempre. Agora há `renovarTokenDeMidia`, chamado **só depois de o token provar que
morreu** (`-16840` no log de erro do item), **uma vez por abertura**.

⚠️ **E a bancada criou defeito que ela mesma media**: os testes rodavam em
paralelo, cada um renovando o token, cada um matando o do vizinho. É o §27 do
Android de novo, noutra linguagem. A suíte é `.serialized` agora.

**O que sobrou, e é do servidor:** o AVPlayer transfere **390 MB** de segmentos e
buferiza **zero** — sem erro, sem stall, com a duração lida corretamente. Ele
busca, recebe e descarta.

### ⚠️ A regra que endureceu duas vezes

1. **Perguntar, não listar** (herdada do Android).
2. **Provar, não perguntar** — o `isPlayableExtendedMIMEType` mentiu sobre `avi`,
   `mpeg4` e `mp3`. Codec só entra depois de um arquivo do acervo **produzir
   quadro** ou **produzir PCM**.

O `mp3` ficou fora por decisão de dado: o servidor contou **64 arquivos `.mp4`
com mp3 e zero `.mov`**. O iOS não enumera faixa mp3 dentro de mp4 — eles tocariam
**mudos**, que é o defeito que o usuário não consegue diagnosticar. Perde-se 212
mkv que funcionariam; evita-se 64 filmes mudos.

⚠️ E declarar `alac` — que era **verdade** sobre o aparelho — expôs um defeito do
servidor: «desconhecido» casava com «desconhecido» no mapa de codecs, e 78
arquivos eram servidos com áudio que o iPhone não decodifica. Corrigido lá.

---

## 8. O que este plano propõe **não** fazer

- **Não** reaproveitar `clients/iosApp` — ver §1.
- **Não** compartilhar código com o Android por KMP. Foi pedido nativo, e meia
  medida aqui entrega o pior dos dois: um app que não é nativo e um módulo
  compartilhado que ninguém mantém.
- **Não** portar o `:tv`. O iPad não é uma TV, e o Apple TV não foi pedido.
- **Não** mexer no `web/` nem no `android/` por causa do iOS. Se algo tiver que
  mudar nos dois, é pedido separado e escrito.

---

## 8. Os quatro defeitos que só a tela pegou — 15/08/2026

Nenhum destes aparece em compilação, em lint ou em teste. Todos vieram de olhar,
e três deles estavam **mentindo com cara de funcionar** — que é a família cara.

### 1. O botão que não era tocável

O «voltar» do perfil não respondia. A sonda descartou a hipótese óbvia: a folha
remontava certo (`folha montada com naCasa=perfil`), mas o `print` dentro da ação
**nunca saía** — o toque não chegava.

A causa é o alvo. Um `Button` cujo rótulo é texto de 14pt tem área de toque do
tamanho do desenho da letra: ~36 × 17pt, contra os 44 × 44 que a Apple pede. E o
mesmo botão no mural **funcionava** — por sorte de alguns pontos, que é pior que
não funcionar: o defeito fica intermitente e cada pessoa acha que errou o dedo.

Virou `CabecalhoDaCasa`, com `contentShape` — sem ele o `padding` só afasta o
desenho e a área de toque continua colada na letra.

### 2. A arte que engolia o texto

No cartão dos baixados, o título saía cortado ao meio e a linha `1965 · 130min ·
2,23 GB` não aparecia. O mesmo arranjo (`ZStack` + `frame(height:)`) funciona no
cartão do «para você».

A diferença é a `HStack`: lá o cartão manda na largura, aqui ele divide a linha
com a lixeira, e uma arte deitada com largura proposta e altura livre reivindica a
**altura da imagem original**. O `frame` recorta o meio, e o texto ancorado no
rodapé fica fora do recorte. Conserto: a arte foi pra `background`, que por
definição não decide o tamanho do que está na frente.

### 3. O filme de 2 GB que não abria

Baixou inteiro, o tamanho batia — e o player deu **`AVFoundationErrorDomain
-11828`**. O arquivo estava gravado como `{id}.filme`, e o AVFoundation escolhe o
demuxer de arquivo local **pela extensão**: não olha os bytes, não fareja o
`ftyp`. Um mp4 perfeito com o nome errado é, pra ele, formato desconhecido. A
extensão passou a vir do `filename` do servidor.

### 4. O 401 gravado com nome de filme

Consertada a extensão, veio **`-11829`** — agora ele reconhecia o formato e não
conseguia parsear. No disco havia **54 bytes**:

```
{"error":"credenciais inválidas ou sessão expirada"}
```

⚠️ **O `URLSessionDownloadTask` não sabe o que é erro.** Um `401` chega no
`didFinishDownloadingTo` exatamente como um `200`: com um arquivo temporário na
mão, «pronto». E nada na tela era falso — o cartão dizia o que o disco tinha. O
defeito só apareceu no player, três telas depois de onde nasceu.

Agora o status é conferido antes de o arquivo virar filme, e um `401` renova o
token de mídia e tenta **uma** vez — com trava, porque cada renovação aposenta o
token dos outros aparelhos da casa.

### E um quinto, que a desinstalação revelou

A sessão mora no **Keychain** e o endereço do servidor no **UserDefaults**, e os
dois não morrem juntos. Visto: o app abriu **já logado** — sem pedir senha — e
todas as abas disseram «sem resposta do servidor», para sempre, com a tela de
login (onde mora o campo do endereço) inalcançável.

Não dá pra consertar adivinhando: este app **não tem palpite padrão** de endereço,
de propósito. Uma sessão que não sabe com quem falar não é uma sessão. A `Raiz`
agora exige as duas coisas.

---

## 9. O iPad — F7, 15/08/2026

O bloqueio caiu sozinho: o simulador do iPad **tinha sessão**. E a primeira
captura respondeu a pergunta da fase inteira.

### ⚠️ `.adaptive` responde «quantos cabem», não «quão grandes»

A grade usava `GridItem(.adaptive(minimum: 108, maximum: 150))`, com um
comentário que estava certo pelo motivo certo: «um cartaz tem largura mínima
legível; quantos cabem é conta da tela, não minha».

Só que num iPad de 1032pt isso dá **sete colunas**, e sete cartazes numa tela de
13 polegadas são selos postais. `.adaptive` sempre empacota o máximo de colunas
que couber, então **o mínimo vira o real** — em toda tela grande.

É exatamente o que a §7 deste plano previa citando o Android: «o padrão de uma
biblioteca não é a decisão do produto». O componente fez o que promete; faltava
alguém dizer qual é o cartaz certo pra cada tamanho de sala.

### O mesmo erro, duas vezes

A locadora tinha a mesma doença com outra roupa: `CaixaDeVHS(largura: 104)`,
medida pro iPhone. Numa prateleira de 1376pt aquilo é miniatura de vitrine, não
fita que se pega — e aqui dói mais, porque **a caixa é o produto** (§1.3: «um
catálogo de arquivos lista linhas; uma locadora tem caixas que se pegam»).

| | compact | regular |
|---|---|---|
| cartaz da grade | 108–150 | **158–210** |
| caixa da locadora | 104 | **148** |

⚠️ E a régua é a **classe de tamanho**, não o modelo do aparelho: um iPad com o
app em meia tela é `compact`, e ali o cartaz do iPhone é o certo. Quem decide é o
espaço que se tem, não o metal.

### O que **não** precisou mudar, e por quê

Ficha, mural, perfil e baixados já limitavam a coluna em 620pt, e o login em 460
— então nenhum deles vira linha de mil pontos no iPad. A casa é uma folha, e no
iPad a folha já é um cartão de ~700pt, o que constrange o guia por tabela.

⚠️ O `maxWidth: 640` que pus no ensaio da revista **não mudou nada na captura**,
porque a folha já o limitava. Fica como cinto: no dia em que o guia sair da
folha, o único texto corrido do app não passa a correr 1032pt.

### Conferido na tela do iPad

Grade com cartazes legíveis; ficha centrada; locadora com as caixas de VHS
grandes o bastante pra se ler «THE LAST WITCH» na capa e a lombada de lado; a
casa como cartão centrado com as três portas; e a revista da semana com o ensaio
em medida de leitura. E o iPhone conferido depois, para provar que o caminho
`compact` não regrediu: três colunas, igual a antes.

---

## 10. O terceiro modo, e o teto que ele revelou — 15/08/2026

`direct_play` e `direct_stream` estavam conferidos com filme rodando. O
**`transcode`** — 17,2% do acervo — nunca tinha sido aberto, e ficava escrito
aqui como não conferido. Ficou.

### O que o transcode é, neste acervo

A sonda achou três, e os três dão o mesmo motivo: **«o cliente não toca áudio em
mp3»**. É a decisão do `ProvaDeMp3` voltando pelo outro lado — mp3 toca em `.mov`
e é **invisível** em `.mp4`, o acervo tem 64 `.mp4` com mp3, e por isso o codec
ficou fora da lista declarada. O servidor lê essa lista e recodifica.

⚠️ E o vídeo sai `copy`: **só o áudio é recodificado**. «Transcode» aqui é mais
barato do que o nome sugere — o servidor gasta um encoder de áudio, não um de
vídeo.

**Visto na tela**: «007: Cassino Royale», selo `transcodificando` em laranja, sem
botão de guardar (correto — não é `direct_play`), retomado de 11min. Preto por ~4
segundos enquanto o ffmpeg arranca, e depois filme. O diagnóstico interno
acompanhou: `t+0s carregado=0.0s` → `t+4s status=1 carregado=17.9s`.

### ⚠️ E aí a barra do player disse «4:44 AM»

Num filme de 2006. Não `11:03 / 2:24:00` — **um horário de relógio**, com
marcador de borda ao vivo. O `duracao=indefinida` do diagnóstico era isto.

Medido, e não suposto: a playlist sai com **`#EXT-X-PLAYLIST-TYPE:EVENT`**, sem
`#EXT-X-ENDLIST`, com três segmentos listados e crescendo. A RFC 8216 é explícita
— sem `ENDLIST`, o cliente **não sabe onde o conteúdo termina** e é obrigado a
tratar como transmissão. Não há opção do lado de cá que faça o AVPlayer mostrar
uma duração que a lista não declara.

Perde-se: arrastar até o minuto 90, saber quanto falta, e o relógio da tela vira
mentira legível. Vale para `direct_stream` **e** `transcode` — **~70% do acervo**,
nos quatro clientes.

Está escrito como **Pedido 4** no `PEDIDOS-AO-SERVIDOR.md`, com a playlist
inteira colada. ⚠️ E não se conserta aqui: dá pra desenhar uma barra própria que
saiba a duração da ficha e busque reabrindo a sessão — e seriam quatro barras à
mão para contornar um fato que mora num lugar só.

---

## 11. O emulador do Android, e o erro que ele pegou — 15/08/2026

O dono pediu: «abra o emulador de Android, vá tirando print das telas e
comparando; ambos devem estar iguais». Aberto, a primeira captura já respondia.

### ⚠️ A planta do app estava errada, e o erro é de método

O iOS tinha **quatro** abas — biblioteca, locadora, para-você e «continuar» — com
mural, guia, perfil e baixados atrás de um ícone de **casa que eu inventei**. O
Android tem outra planta:

| | Android | o que eu tinha feito |
|---|---|---|
| abas | biblioteca · locadora · **mural** · **guia** · para você | biblioteca · locadora · para você · **continuar** |
| perfil | rosto no canto com anel de nível → `perfil` / `sair` | dentro da «casa» |
| mural, guia | **abas** | dentro da «casa» |
| baixados | chip `↓ N no aparelho` na barra de filtros | dentro da «casa» |
| continuar | **herói + seção** no topo da biblioteca | aba própria |
| título da tela | serifa minúscula grande | versalete espaçado com filete |

⚠️ **A causa não foi desatenção, foi fonte errada.** Construí contra o
`REDESENHO.md` §6 — que é um documento de **proposta**, começa com «a decisão que
precisa vir antes da R1», lista três opções e fala em «quatro destinos». O app
seguiu adiante e ninguém voltou pra reescrever a proposta.

A régua desta casa é **ver na tela antes de escrever que funciona**. Eu a apliquei
com rigor ao meu trabalho — cada tela deste iOS foi conferida com captura — e não
a apliquei à minha **referência**. Um app inteiro construído contra um plano de
seis semanas atrás passa em todo teste e erra o produto.

### O que foi corrigido

- **Cinco abas**, com os ícones e a ordem do Android.
- `CabecalhoDaTela`: o nome da tela em serifa minúscula grande com «60 **de**
  8.273» em dois pesos, e o **rosto com anel de nível** no canto — o anel fecha na
  proporção do nível, e não desenha quando não há fração (§18).
- `Insignia`: uma chamada de `/api/perfil` pro app inteiro. «A insígnia do canto e
  a tela do perfil saem da mesma resposta.»
- A `TelaDeContinuar` **deixou de existir**: virou o herói do topo da biblioteca
  (`faltam 143min`) e a fileira `CONTINUAR` logo abaixo.
- Chips na biblioteca: a ordem, o filtro do guia com ✕, e `↓ N no aparelho`.
- Mural: seção `ESTA SEMANA` com a contagem dos últimos sete dias — o campo
  `quando` já chegava em toda linha e ninguém o lia — e o `detalhe` **sem** as
  guilhemetas que eu tinha posto: ele é o estado da fita, não fala de gente.
- Para você: as **seis** faixas de tempo do Android sob o rótulo `TENHO`, herói no
  topo, e a razão **sem** o rótulo «porque» que eu inventara — a frase do servidor
  já começa com «você costuma…».

### ⚠️ O que ainda **não** está igual, e é declarado

1. ~~A cenografia da locadora.~~ **Feita** — ver §12.
2. **O chip `filtros ▾`.** Não entra enquanto não houver painel de filtros — um
   chip que abre nada é o §8b. O `Filtros` daqui é a fatia que o guia pede, não as
   doze do Android.
3. ~~«Ao vivo» continua fora.~~ **Feito** — e é a primeira vez que este cliente
   passa à frente do celular Android. Ver §16.

---

## 12. A cenografia da locadora — F5, 15/08/2026

A comparação com o emulador mostrou que o iOS tinha a informação certa e
**nenhuma matéria**: um rótulo de seção e caixas sobre um filete de 2pt, onde o
Android tem uma loja.

As peças vieram do `cenario/…/Cenografia.kt`, e a origem delas é o dono: pediu «o
maior feel possível de locadora, aquela nostalgia», olhou vinte conceitos e
escolheu um — **as prateleiras ficam**, e o resto da tela vira matéria.

| | o que é |
|---|---|
| `Arandela` | a meia-cúpula de latão e o facho que ela joga na parede |
| `EtiquetaPendurada` | as contagens da porta, em papel por barbante, com ilhós |
| `PlaquinhaDaEstante` | o nome da estante em papel colado com fita, na madeira |
| `TabuaDaPrateleira` | a tábua onde as caixas repousam, com a quina iluminada |

### As decisões que vieram junto, e que valem mais que o desenho

- **O facho é um círculo, não um retângulo.** O Android registra a foto que provou
  isso: um `drawRect` com pincel radial vira uma **tarja** — o gradiente morre
  fora do quadro e sobram duas arestas retas atravessando o topo.
- **O letreiro não tem luz própria.** O halo dele é o facho da arandela; por isso
  os dois andam juntos e nesta ordem.
- **Sem espaço entre a caixa e a tábua.** Da web, palavra por palavra: «a tábua
  encosta na base das caixas; com folga embaixo o conjunto lê como cartão, não
  como objeto». O `spacing: 0` daquela `VStack` é essa frase.
- **A tábua sangra pras laterais.** Prateleira de loja não acaba onde acaba a
  fileira — ela atravessa a parede.
- **Os ângulos das etiquetas são constantes.** Sorteados, o papel tremeria a cada
  redesenho da tela.
- **A cor do papel vem do nome da estante**, e não de sorteio: a mesma estante tem
  sempre o mesmo papel, em toda abertura e em todo aparelho. É assim que se acha
  «Terror» de relance; um papel que muda de cor a cada visita não é etiqueta, é
  piscar. É o §18 aplicado a enfeite.
- «Aberta até meia-noite» **não entra**: a loja não fecha de verdade, e horário
  inventado é mentira com cara de metadado. «Acervo da casa» é o que ela é.

### ⚠️ O que ainda falta nesta tela

- A seção **`COMIGO`** — as fitas que estão com você agora, com o selo de prazo
  (`7 DIAS`). Ela precisa do que o Android chama de `Prateleira`, que este cliente
  ainda não mapeia.
- A segunda etiqueta diz **«no acervo»** onde o Android diz «nesta semana»: a
  contagem semanal não vem na resposta que este app pede.

---

## 13. A varredura final, e o campo que eu tinha apagado por dedução

Comparando o **perfil** lado a lado, apareceu a segunda falha do mesmo tipo da
§11 — e desta vez com o dedo no gatilho.

### ⚠️ Eu removi a `camada` das conquistas por raciocínio, e o raciocínio estava certo pela metade

O comentário que escrevi dizia: «os pontos já dizem a dificuldade, e `+10` ao lado
de `+150` diz melhor; uma segunda escala com as mesmas três posições seria a mesma
coisa escrita duas vezes». Isso é verdade **se** a camada fosse uma escala.

Ela não é: é o **agrupador da tela**. O Android desenha `FÁCEIS · 9 de 12`,
`MÉDIAS`, `DIFÍCEIS` como seções, cada uma com a própria contagem — a pergunta
«quanto falta desta prateleira», que o total de 80 não responde.

A régua «não declare contrato que ninguém lê» continua certa. O que estava errado
era a outra metade: **eu decidi que ninguém lia sem abrir a tela que lê.** É a
mesma falha de método da §11, num campo em vez de num app inteiro.

### O que mais o perfil ganhou

- A **capa de ponta a ponta e sem cantos**: ela é a parede atrás de tudo. Na minha
  versão era um cartão arredondado com margem — e ali virava enfeite.
- O placar **`VOCÊ E SEUS AMIGOS`**, que eu não tinha mapeado. A doc da web diz
  que a comparação com os amigos «foi pedida e nunca existiu» até esta tela: um XP
  sozinho é um número; ao lado de duas pessoas da mesma casa, é um placar.
- `@usuário` embaixo do nome, no lugar do título conquistado que eu tinha posto.
- **Uma barra e uma linha** no lugar do cartão com «nv 3» em corpo 30: o nível já
  está no anel e na medalha do rosto logo acima, e repeti-lo era dizer a mesma
  coisa três vezes na mesma tela.
- `+10 **XP**`, com a unidade.

### ⚠️ E um defeito meu que só a tela pegou, de novo

Reescrevendo o corpo desta tela eu levei junto o `.task` que a carrega. A folha
abriu **vazia** — só o «voltar» sobre o preto. Compilou, passou nos 56 testes, e
não tinha conteúdo.

É a régua da casa pela enésima vez, agora contra mim numa refatoração: **compilar
e passar nos testes não diz nada sobre o que aparece.**

---

## 14. A ficha vira fachada de cinema — 15/08/2026

Comparada lado a lado, a ficha do Android era outra coisa. A minha tinha título,
uma pílula de metadados e duas cápsulas; a de lá é a **entrada de uma sessão**.

| peça | o que é |
|---|---|
| a **fachada** | o fotograma de ponta a ponta, com o «‹ biblioteca» por cima |
| a **marquise** | a moldura de lâmpadas, com o título, `Thunderball · 1965 · 2h10` e o selo do plano **com o motivo** |
| o **varal** | três fotogramas em Polaroid, presos por prendedores numa corda pendurada na marquise |
| as **etiquetas** | `country Reino Unido`, `genre Ação` — namespace apagado, valor em negrito |
| o **bilhete** | o botão de tocar em forma de ingresso, com picote e mordidas |

### O que eu não estava lendo

- **`original_title`** — «Thunderball». Chegava e ficava no chão.
- **`reasons` do plano** — «codecs e container batem: vai o arquivo original». Eu
  desenhava só a palavra «direto»; o motivo é o que explica por que este filme
  abre num toque e o outro faz o servidor trabalhar.
- **`tags`** — as sete etiquetas da obra.
- **`/api/works/{id}/cenas`** — a rota inteira. ⚠️ Ela custa ~4 s (doze extrações
  de ffmpeg), e por isso é pedida em separado: a ficha nunca espera pelo varal.

⚠️ E o **varal é navegação**: cada foto é um instante, e tocar nela entra ali.

### ⚠️ Três defeitos, e dois deles escondiam um ao outro

**1. A arte inteira vinha 401.** Subi o app do Android no emulador pra comparar, e
o servidor **apagou o token de mídia deste cliente** — é o que ele faz a cada
emissão, e está aberto como pedido. A ficha desenhou marquise, varal e
prendedores com três Polaroids **pretas**.

⚠️ E a arte é o único lugar do app **sem recuperação**: o player renova em
`-16840`, o download confere o status antes de gravar, e o `AsyncImage` recebe 54
bytes de erro, não decodifica nada e **desenha o vazio** — que é o que uma obra
sem pôster também desenha. Defeito e estado normal do mundo, indistinguíveis.

O conserto é `conferirTokenDeMidia(comArte:)`: uma requisição de uma arte que a
tela já ia pedir, **uma vez por abertura do app**, e renova só se ela voltar 401.
⚠️ Renovar sempre na abertura seria uma linha e **derrubaria o filme de quem está
na sala** — trocar um defeito visível meu por um invisível na TV de outra pessoa
não é conserto.

**2. `scaledToFill` sem limite de largura, duas vezes.** Consertado o token, a
ficha **vazou pelos dois lados**: título, sinopse e etiquetas cortados. Uma imagem
carregada com `scaledToFill` reivindica a largura **do arquivo**, e
`maxWidth: .infinity` não limita — só estica. Eram três fotogramas de 640pt no
varal pedindo ~1920pt, e a coluna inteira adotou isso.

⚠️ **Eu já sabia**: está escrito no `TelaDosBaixados`, consertado hoje de manhã
com `background`, que por definição não deixa o que está atrás decidir o tamanho
do que está na frente. Reescrevi o mesmo defeito em duas telas no mesmo dia.

⚠️ E os dois se escondiam: **com as fotos em 401 não havia imagem, não havia
tamanho intrínseco, e o layout parecia certo.** Consertar o token revelou um
defeito que já estava lá.

**3. Um `Layout` que respondia «sou infinita».** O `FlowLayout` das etiquetas
fazia `proposal.width ?? .infinity`. Um layout que responde infinito não está
dizendo «me dê o que puder»; está dizendo «eu ocupo tudo», e quem pergunta
acredita. (Este não era a causa do vazamento — mas era um segundo caminho pro
mesmo estrago.)

### O que **não** entrou, e é decisão

O Android tem «pegar a fita na locadora» na ficha. Aqui não entra, e o motivo está
escrito na `TelaDaLocadora` desde o começo: o servidor recusa algumas obras com
**403**, a locadora tem 600 caixas sobre 17.498 obras, e o cliente **não tem como
prever qual**. É o Pedido 1, ainda aberto. Um botão que leva a 403 é defeito.

---

## 15. O player deixa de ser o da Apple — 15/08/2026

Última tela da comparação, e a mais diferente. A minha era o `VideoPlayer` do
AVKit — os controles do sistema, de graça. A do Android é inteira dela: barra de
cima com um **ponto colorido do plano**, uma **tira de filme 35 mm** no lugar da
barra de progresso, ⟲10 · pausa dourada · ⟳30, e o relógio dizendo `0:47` e
**`faltam 2:09:35`**.

### ⚠️ E a tira de filme é o que conserta o «4:44 AM»

O §10 registrou o defeito: a playlist de HLS sai como `EVENT` sem
`#EXT-X-ENDLIST`, o AVPlayer é obrigado a tratá-la como transmissão, e a barra do
sistema mostrava **um horário de relógio** num filme de 2006. Vale pros ~70% do
acervo que passam por HLS.

O Android nunca teve esse problema **porque nunca perguntou ao player onde o filme
acaba**: a duração vem da ficha, e a barra é dele. É a mesma saída aqui —
`duracaoDaSessao` prefere o `duration_seconds` do arquivo e só cai no item quando
ele é finito.

⚠️ O Pedido 4 continua de pé: uma playlist `VOD` conserta na origem, pros quatro
clientes. Mas a barra própria não é contorno — é o que a tira exige de qualquer
jeito.

### As decisões da tira

- **O quadro atual é o último que já começou**, não o mais próximo: no minuto 47
  você está *dentro* da cena que começou aos 44. Arredondar faria a moldura pular
  antes de a cena chegar.
- Os outros quadros ficam **apagados, não escondidos**: a tira inteira é o mapa, e
  só o «você está aqui» acende.
- Os **furos de arrasto** não são enfeite: sem eles é uma fileira de miniaturas.
- ⚠️ **Ela não rola.** A primeira montagem era um `ScrollView` horizontal com
  quadros de 78pt — cinco e meio à vista, o resto atrás de um arrasto. O Android
  cabe os doze de uma vez, e a diferença não é cosmética: o cabeçalho do arquivo
  diz que ela é «a barra **e** o mapa», e se é preciso arrastar pra ver o resto,
  ela deixou de ser barra. «Onde eu estou no filme» é pergunta sobre o **todo**, e
  um todo que não cabe na tela não responde. Nenhuma barra de progresso rola.

### ⚠️ Três tentativas até o toque funcionar

O player ficou **mudo** — os controles sumiam sozinhos em 4 s e não voltavam. Um
player sem pausa e sem saída, só com o botão físico.

| onde o gesto estava | o que aconteceu |
|---|---|
| na `CamadaDeVideo` | a `UIView` tem `isUserInteractionEnabled = true` e **absorve** o toque antes de o SwiftUI ver o gesto |
| no `ZStack` inteiro | continuou sem disparar — um `contentShape` num empilhamento com `UIViewRepresentable` dentro não é a região que parece ser |
| no **fundo preto** | funciona: ele preenche a tela por definição, e é a única camada de que isso é verdade |

⚠️ E o que separou «o gesto não dispara» de «os controles não desenham» foi
**desligar o auto-esconder por um build**. Com ele ligado, as duas hipóteses
produzem exatamente a mesma foto — tela sem controles. Foi o mesmo método da sonda
do `voltar` do perfil: quando duas causas dão a mesma imagem, mude uma delas de
propósito antes de consertar qualquer coisa.

---

## 16. Ao vivo — a primeira vez que o iOS vai na frente

O celular Android não tem esta tela; a TV e a web têm. O dono confirmou que foi
**esquecimento**, não decisão — então ela existe aqui, com as decisões do
`NoArAgora.kt` e da `TelaAoVivoDaTv`, que já as pagaram.

### ⚠️ Onde ela mora, e por que não é a sexta aba

O iOS colapsa em «More» a partir da sexta, que é exatamente o defeito que o
cabeçalho do `TelaDoMural` descreve. Com cinco abas ocupadas, a sexta viraria uma
gaveta.

Ela entra pelo **guia**, e não é arranjo: o guia é a tela da programação — «por
onde eu entro» —, e «o que está no ar» é a mesma pergunta feita ao **relógio** em
vez de ao acervo. A da TV é literalmente um guia.

### As decisões herdadas

- **O relógio é o do servidor** (`grade.agora`), não o do aparelho. Perguntar «o
  que está no ar» com um relógio minutos fora escolhe o programa errado
  exatamente nas viradas — que é quando a conta é feita.
- **«No ar» é `começa <= agora < termina`**, e o `<` no fim importa: com `<=` dos
  dois lados, no segundo da virada dois programas seriam elegíveis e a tela
  piscaria entre eles uma vez por programa.
- **Canal de fora sem EPG entra assim mesmo**, com «sem programação». Ele existe e
  está no ar; o que não se sabe é o que passa, e as duas coisas são diferentes.
  ⚠️ Este foi o defeito que o dono relatou no Android — «cadê os outros canais?».
  Eles **estavam sendo buscados** e a tela desenhava só a grade da casa: o dado
  chegava e ninguém olhava. Sem erro, sem tela vazia, com build verde.

### ⚠️ «Sintonizar» são duas coisas, e mandar a errada deu 400

Visto na tela: tocar no Odeon 1 respondeu **`o servidor respondeu 400`**. A causa
era minha — eu mandava `POST /api/live/{id}/watch` pra qualquer canal, e essa rota
é dos **de fora**.

Um canal da casa não é um fluxo que alguém transmite: é uma **grade sobre o
acervo**. Sintonizar nele é abrir o filme no minuto em que ele já está — o mesmo
arquivo que a ficha abriria, com outro `comecarEm`. Não há stream pra pedir porque
não há stream: há um horário.

É por isso que a tela tem **duas seções** e não uma lista com um selo: o que o
toque faz muda, e o §53 diz que o produto não pode oferecer o mesmo gesto pra duas
coisas que respondem diferente.

### E dois defeitos que a captura seguinte pegou

**Sair do canal caía na ficha do filme.** Sintonizei o Odeon 1, o filme tocou, e
ao sair apareceu a ficha de «O Lagosta» — uma tela que eu nunca abri, sobre um
filme que eu não escolhi. Escolhi um **canal**. O `Alvo` ganhou um `doAoVivo`, e
sair devolve à lista.

**O «‹ biblioteca» some sobre fotograma claro.** O fundo de «O Lagosta» é quase
branco, e dourado sobre branco ficou no limite do ilegível — a mesma família do
contraste de **1,02:1** que este projeto já mediu com legenda branca sobre cena
clara. Sombra ajuda e não basta; virou cápsula escura, que **não depende da
imagem**.

### Conferido na tela

**21 canais** — 3 da casa e 18 de fora, com «a seguir», barra de andamento e
«faltam». Sintonizado o Odeon 1: «O Lagosta» abriu no ponto da grade. Outro canal
da casa: «Sim Senhor» em **34:28**, com a tira de filme e o «faltam 1:09:54».

### ⚠️ O ramo do `/watch` **não é alcançável neste acervo**, e isso é uma medida

Procurei um canal que caísse nele e não achei. Abertos: Odeon 1 («O Lagosta»),
outro canal da casa («Sim Senhor», 34:28) e um do ErsatzTV («Indie Game: O
Filme», 42:10) — **os três foram pro player**, com tira de filme e ponto verde.

O motivo: `podeTocarDireto` não pergunta se o canal é da casa, pergunta se **há
obra e arquivo atrás** — e é assim de propósito, porque é assim que a TV decide.
Neste acervo o ErsatzTV serve o próprio acervo com EPG casado, então todo canal
tem obra.

⚠️ Então a `TelaDoCanal` está escrita, compila, e **nunca roda aqui**. Não é
código morto — é o caminho de um canal que seja um fluxo de verdade, e a casa não
tem nenhum hoje. Fica anotado como **não exercitado**, e não como «funciona»: a
régua desta casa não deixa escrever a segunda coisa sem ter visto a primeira.

## §17 · O menu do disco

O último pedaço do `:cenario`. Abre **só pela locadora** e **só em DVD**: a caixa
vai pra mão, a metade direita abre, o disco sai — e **tocar no disco é pôr o
disco**. Não virou botão porque o gesto já existia no objeto; a dica de baixo
troca pra «toque no disco pra pôr no aparelho», senão o gesto não existiria pra
quem olha. Na fita, `aoPorNoAparelho` é `nil`: a fita não tem menu, tem rebobinar
(§14.4).

A tabela de climas veio **inteira** do Android — os doze índices, as cores e as
quatro formas de vinheta. O índice é o contrato: é a posição na lista `ESTANTES`
do servidor. A trilha sintetizada continua vetada, e o fundo é o backdrop com
deriva em vez do filme rodando (uma sessão de HLS pra desenhar um enfeite).

### Visto na tela, no iPad Pro 13"

«A Serbian Film» (2010, clima 0 · Terror): a vinheta desenha o **risco** na cor
do clima com o nome centrado, o menu abre com `2010 · Terror` na tinta, e os
itens são `Continuar 3:49` · `Do começo` · `Capítulos 12`. **Sem «Legendas»** —
esta cópia não tem nenhuma, e o item não existe em vez de existir vazio (§24). A
grade diz «divididos pelo relógio», que é a verdade aqui: `capitulos` vem vazia e
as cenas são `regular`. Tocado o capítulo de `35:59`, o filme abriu **naquele
quadro** — o mesmo da miniatura.

### Dois defeitos que só a tela mostrou

**A vinheta anunciava o clima errado.** A primeira versão corria a busca e os
2,5s em paralelo, pra a vinheta não custar espera — mas o clima vem **na
resposta**. Ela começava no 11 (Drama, marrom, onda) e trocava no meio do
caminho. Uma vinheta que anuncia o clima errado durante metade da própria duração
não anuncia nada, e o clima é a única coisa que ela diz. Agora ela espera o disco,
como um leitor espera o disco.

**As molduras da grade saíram desiguais** — 185, 220, 225, 225 e 240pt numa mesma
linha. Numa grade adaptativa as colunas são iguais por construção; quem as
desigualou foi o conteúdo reivindicando a largura da imagem original. É o
**quinto** lugar deste app onde escrevi este defeito: cartão dos baixados,
fachada da ficha, polaroide do varal, capa da caixa, e aqui. A correção é sempre a
mesma — `Color.clear` aceita qualquer proposta, e o que está atrás nunca decide o
tamanho do que está na frente.

⚠️ A vinheta não coube em nenhum screenshot pelo caminho normal: ela dura 2,5s e o
ciclo tocar→fotografar é mais lento que isso. Pra não escrever «funciona» sem ter
visto, subi a duração pra 20s numa compilação descartável, medi os pixels da cor
do clima — faixa de 3px na meia-altura, indo de x=0 a x=1084 de 2064, ou seja o
sweep pela metade — e devolvi os 2,5s. **86 testes em 23 suítes** passam, sonda de
HLS incluída.

## §18 · O ao vivo não conta

Pedido do dono, e a história é a especificação:

> «eu mesmo acabei dormindo no ao vivo e quando vi o app registrou que eu vi um
> monte de filme»

**Biblioteca e locadora registram; o ao vivo não.** O que se escolhe alimenta o
«continuar», o histórico e o «para você»; o que a grade empurrou na frente de uma
tela ligada, não. O critério é **por onde se entrou**: o mesmo filme, aberto pela
biblioteca, conta normalmente.

⚠️ O estrago passava da fileira: o mesmo registro alimenta a curadoria, e uma
noite de sono no canal de terror ensina um gosto que ninguém tem.

### Onde coube, aqui

O `Alvo.doAoVivo` já existia e servia só pra **saber pra onde voltar** — foi o
conserto de «sintonizei um canal e ao sair caí na ficha de um filme que eu não
escolhi». Agora ele diz também o que **não** registrar.

⚠️ A guarda virou uma propriedade, `ModeloDoPlayer.registraProgresso`, e não dois
`if` iguais: esta tela marca em **dois** lugares distantes — o relógio de 20s e a
saída — e guardas repetidas são a receita pra uma delas ser esquecida no dia em
que aparecer a terceira.

⚠️ A posição **local** continua sendo anotada; o que não sobe é o registro no
servidor.

### ⚠️ E as pílulas da ficha estavam em inglês aqui

O double check pegou de carona um defeito que não era deste assunto: a ficha do
iOS mostrava `country Reino Unido`, `format filme`, `genre Ação`, `lang inglês`.

É o mesmo defeito que o Android corrigiu em 16/08 — e ele **não atravessou**. A
`Etiqueta.rotulo` de lá virou `EtiquetaDaObra.rotulo` aqui, com a tabela igual
entrada por entrada: divergir significaria a mesma etiqueta lida de dois jeitos em
dois aparelhos da mesma casa. Namespace desconhecido devolve `nil` e a pílula
omite o qualificador em vez de imprimir a chave.

Visto na tela: `país Reino Unido` · `formato filme` · `gênero Ação` ·
`idioma inglês`.

### ⚠️ A web já fazia certo, e isso diz o que aconteceu

O `PlayerAoVivo` da web nunca chamou `api.progress`. Os dois clientes nativos
**copiaram o player de filme para o canal** e levaram junto o que não devia ir —
inclusive este, que tinha a informação em mãos desde o dia em que o `doAoVivo`
nasceu.

### Conferido nos dois clientes, nos dois sentidos

No **Android**: canal com ~2min tocando não entra no «continuar»; filme aberto
pela biblioteca entra («007 Contra Octopussy · faltam 129min»).

No **iOS**, medido com dois `print` temporários no próprio caminho:

```
biblioteca  relogio: posicao=280.0  doAoVivo=false  registra=true
biblioteca  saida:                  doAoVivo=false  registra=true
canal       relogio: posicao=0.0    doAoVivo=true   registra=false
canal       relogio: posicao=20.0   doAoVivo=true   registra=false
```

E na tela: depois de tocar «007 Contra GoldenEye» pela biblioteca, a ficha passou
a oferecer **«continuar · 5min»**.

### ⚠️ A fileira «continuar» **não serve de prova** neste cliente

Quase escrevi que o conserto tinha quebrado o registro da biblioteca. O raciocínio
era: toquei um filme, o filme não apareceu na fileira, logo não gravou.

Ele não apareceu porque **a fileira do iOS não ordena por recência** — são 29
itens num carrossel e eu estava olhando os cinco primeiros. O filme estava lá
atrás, e a ficha provou com o «continuar · 5min».

É a terceira vez nesta rodada em que o instrumento apontou pro lugar errado, e a
regra vale de novo: **medida que acusa defeito pede uma segunda fonte antes de
virar conserto**. Aqui a segunda fonte foi instrumentar o caminho em vez de ler o
sintoma.

## §19 · A locadora do iOS passou a agir

Ela «contava e não agia», e a folha dizia por quê: devolver e pedir escrevem no
acervo de três pessoas, e o §11 pede quem confirme na tela. A confirmação chegou.

| ação | gesto | por quê |
|---|---|---|
| **devolver** | dois toques | escreve, e desfazer custa pegar de novo |
| **pedir de volta** | um toque | ⚠️ **não encurta prazo de ninguém** — põe um recado na caixa de quem está com a fita. O efeito é um aviso, não uma perda, e confirmar pra avisar é cerimônia |
| **levar pra casa** | dois toques | escreve, e cria empréstimo no perfil de alguém |

### O «levar pra casa» voltou aos dois clientes

Ele esteve fora do produto inteiro por causa do §53 — o servidor recusava com 403
imprevisível. A causa saiu da investigação de lá e **não era permissão**: o mesmo
filme existe duas vezes (44 casos), e a locadora trancava por `work_id` enquanto a
biblioteca desenhava um cartão por grupo.

Com `caixa_ids` a conta virou local:

| situação | na tela |
|---|---|
| livre | botão «levar pra casa · N restam» |
| já é sua | «esta já está com você» |
| com outro | «está com o serious-sam» |
| no limite | «você está no limite de fitas» |

⚠️ **Só a primeira é botão.** As outras três são respostas, e um botão
desabilitado convida ao toque que não responde (§8b). Os quatro casos têm os
mesmos nomes do Android — divergir faria a mesma caixa dizer coisas diferentes em
dois aparelhos da mesma casa.

### ⚠️ O que ele desfez, e vale contar

Verificar o «levar» no Android criou um empréstimo real («Independence Day»), e lá
devolver é **gesto de segurar** — que o `adb` não reproduz. O empréstimo ficou.

O «devolver» que este arquivo acabou de ganhar é **botão**, e foi por ele que a
fita voltou: COMIGO de 2 pra 1, prateleira de 38 pra 40. A funcionalidade nova
desfez o efeito que a verificação da outra tinha deixado.

### O que continua faltando: o painel de filtros

O `filtros ▾` **não é defeito**: ele não existe aqui porque o painel não existe, e
isso está declarado desde sempre — «um chip que abre nada é o §8b». Construí-lo é
funcionalidade nova, do tamanho da `BarraDeFiltros` do Android (doze espaços de
etiqueta, faixa de ano, duração, identificação), e não cabia no fim deste turno
sem virar meia-entrega.

## §20 · O painel de filtros — construído, **não visto**

O `filtros ▾` era a última ausência declarada: «um chip que abre nada é o §8b».
O painel existe agora, e com ele o chip.

### Como ele é

⚠️ **Os grupos vêm do servidor.** `GET /api/tag-namespaces` manda o rótulo
(«Gênero») e a posição; `GET /api/tags` manda as etiquetas com quantas obras cada
uma alcança. A tela não traduz `genre` nem decide a ordem — um namespace novo
nasce sozinho, com o nome que o servidor deu. É a mesma lição do `country` cru na
ficha, do lado certo.

⚠️ **Namespace sem rótulo não some**: cai num grupo com o próprio namespace de
título. Descartá-lo seria o app decidir que uma etiqueta do acervo não existe
porque a tabela do servidor está incompleta.

⚠️ **Década e formato são montados aqui**, e não são etiquetas: o servidor filtra
ano por `anoDe`/`anoAte`, não por `decade:1980`. É a mesma decisão que o guia já
tomava.

⚠️ **Trinta por grupo**, e a razão é o país: são 40 e a cauda tem uma obra cada.
Uma lista que rola dez telas até «África do Sul · 1» não é filtro, é censo — e a
ordem por quantidade põe no começo o que se procura.

⚠️ **O painel empurra a grade**, não flutua por cima: quem filtra quer ver o
resultado mudando, e uma folha cobrindo a grade esconde justamente o que o toque
acabou de fazer.

⚠️ E o painel oferece **só o que este modelo sabe carregar** — etiqueta, década,
tipo. As doze faixas do Android incluem campos que a busca daqui ignora, e um chip
que ligasse um deles seria o §8b outra vez, com outra cara.

### ⚠️ O que **não** foi verificado, e por quê

Não vi o painel abrir. O mapeamento de toque do simulador ficou não confiável no
fim desta sessão: dois toques bem mirados no chip não fizeram nada, o mesmo toque
no «em destaque ▾» também não, e um terceiro — na coordenada que deveria ser o
chip — abriu a **ficha de um filme**. Ou seja, o instrumento está entregando
toques noutro lugar.

Compila, está ligado (`painelAberto` na tela, o chip que alterna, o painel abaixo
dele) e **não foi visto na tela**. Pela régua desta casa isso é «não exercitado», e
é o que fica escrito — a primeira coisa a fazer no próximo turno é abrir esse
painel com o dedo e olhar.
