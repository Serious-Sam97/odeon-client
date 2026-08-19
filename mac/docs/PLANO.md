# Odeon para Mac — o plano

Escrito em **19/08/2026**, no fim de um dia em que o celular, a TV e o web foram
mexidos e medidos. O pedido do dono, na íntegra, porque as duas metades dele
puxam para lados diferentes:

> «Fazer uma versão nativa em Swift para Mac se baseando **inteiramente no web**,
> mas bem mais melhorados e **ousados** pq o Mac suporta beemm»

⚠️ **«Basear no web» é herdar decisão, não código.** Não é port, não é Catalyst,
não é webview, e não é o `web/` empacotado. É SwiftUI escrito do zero obedecendo
a um documento de decisões colhidas do cliente que hoje sabe mais — e desobedecendo
a ele exatamente onde o navegador é a limitação, que é onde entra a segunda metade
do pedido.

---

## 1. As decisões já tomadas

| | |
|---|---|
| **escopo da v1** | o que o **web** faz hoje, relido na régua do Mac — não uma espinha |
| **referência** | `web/`, e não `ios/`: o Mac herda **mouse, teclado e janela grande** |
| **contrato da API** | **quarta cópia à mão**, comentada como as outras três |
| **onde mora** | `mac/`, neste repositório, ao lado de `web/`, `android/` e `ios/` |
| **o que não muda** | o servidor. Nenhuma rota nova sem passar pelo `PEDIDOS-AO-SERVIDOR.md` |

---

## 2. Por que o web é a referência certa

O `web/` é o cliente **mais completo** que existe hoje: dezessete telas contra as
dez do celular, e as únicas com locadora, mural, guia, revisão, pastas e admin.
Ele também é o único pensado para **ponteiro e teclado** — que é o que um Mac tem.

| | linhas |
|---|---|
| `api.ts` (o contrato à mão) | 2.664 |
| `Locadora.tsx` | 2.044 |
| `App.tsx` (o casco e a biblioteca) | 1.761 |
| `AoVivo.tsx` | 1.493 |
| `Details.tsx` · `Player.tsx` · `MenuDVD.tsx` | 1.090 · 900 · 677 |

⚠️ **E ele traz junto os limites do navegador**, que não são decisões — são
concessões. Separar as duas coisas é metade do trabalho deste plano, e está na
§4.

---

## 3. O que se herda — e cada linha aqui já custou caro

### As regras de produto

| | |
|---|---|
| **o ao vivo não registra progresso** | nem «continuar», nem histórico, nem algoritmo. Regra do dono, 17/08 |
| **erro ≠ acervo vazio** | três telas do web diziam «afrouxe os filtros» quando o pedido falhava. Consertado em 19/08 — §18 |
| **a tela não afirma o que não sabe** | «não tem arquivo no acervo» ≠ «não sei qual obra é esta» |
| **um gesto, um destino** | no celular virou «um toque e já vai»; no Mac, **um clique e já vai** — sem selecionar-depois-confirmar |
| **`continuar` colapsa por série** | um cartão por série, não por episódio |
| **filmes e séries são abas separadas** | `?tags=format:série` de um lado, `?tags_not=format:série,format:anime` do outro |
| **cada aba continua o que ela guarda** | a fileira de `continuar` filtra pela aba — série na aba das séries |

### As regras de reprodução, que custaram mais caro ainda

| | |
|---|---|
| **quem decide o caminho é o servidor** | `POST /api/playback/{id}/plan` com as capacidades **declaradas** pelo cliente → `direct_play` · `direct_stream` (remux) · `transcode` |
| **`hevc8` / `hevc10`, nunca `hevc` puro** | a profundidade entrou no vocabulário em 18/08, e estreitar `hevc` teria custado 5.319 recodificações |
| **dois tokens** | sessão (longo, header) e **mídia** (8h, `?token=` em `/artwork`, `/scrub`, `/api/stream`). ⚠️ Segmento de HLS **exige header** — `?token=` na playlist não desce pros segmentos |
| **HLS é `EVENT`** | a duração **cresce** enquanto o ffmpeg escreve; `#EXT-X-ENDLIST` só no fim. A duração real vem do `duration_seconds` da obra |
| **tempo de sessão ≠ tempo de arquivo** | a sessão abre em `start=N`; o segundo zero do player é o segundo N do filme |
| **fechar encerra a sessão** | senão o ffmpeg fica vivo até o ceifador passar |

### O ao vivo, e a regra que custou uma tarde

| tipo de canal | como abre |
|---|---|
| **externo** (ErsatzTV, M3U) — tem `programme_id` | `sintonizar` → stream pronto |
| **do Odeon** — `programme_id == null` | **não existe stream**; abre o arquivo no ponto calculado. `sintonizar` devolve HTTP 400 |

⚠️ **O discriminador é o tipo de canal, não o casamento com uma obra.** Unificar
os dois caminhos quebrou o que funcionava, em 18/08.

⚠️ E `work_id` × `collection_id` são **mutuamente exclusivos** — medido em 19/08:
de 255 programas da grade, 96 só com obra, **59 só com coleção**, zero com os dois.
Episódio de série chega com `work_id: null`.

### As armadilhas pagas em 19/08, que valem para qualquer cliente

| | |
|---|---|
| **superfície de vídeo criada tarde** | `PlayerView` inserido numa tela já desenhada nascia sem superfície: **17 de 17 canais pretos**, com áudio tocando e o codec desenhando no vazio |
| **`fetch` sem prazo não falha — só não volta** | uma aba ficou presa em «acendendo a ilha…» com o servidor de pé |
| **ver na árvore ≠ tocar** | o cartão do próximo episódio aparecia, era `clickable=true` no `uiautomator`, e o dedo não chegava nele: uma camada acima comia o toque |

---

## 4. O que **não** se herda — porque é gambiarra de navegador, não decisão

| o web faz | porque não tem escolha | o Mac faz |
|---|---|---|
| `hls.js` em JavaScript | `<video>` não fala HLS fora do Safari | **AVPlayer fala HLS nativo**, com buffer e ABR do sistema |
| caixa de DVD em CSS 3D | não há profundidade real no DOM | **RealityKit/SceneKit/Metal** — a caixa vira objeto, não ilusão |
| cromo some por `mousemove` + `setTimeout` | não há noção de cursor ocioso | `NSTrackingArea`, `NSCursor.setHiddenUntilMouseMoves` |
| modal como `div` sobre a página | não há janela | **janela de verdade**, `sheet`, ou painel destacável |
| `?token=` como única credencial | `<img>` e `<video>` não mandam header | **URLSession com header sempre**, token no **Keychain** |
| `localStorage` | é o que existe | Keychain (segredo) + `UserDefaults`/SwiftData (preferência) |
| `EventSource` | idem | `URLSession.bytes` ou WebSocket, com reconexão própria |
| grão e facho por `background-image` | não há shader | **Metal** |
| uma janela para tudo | idem | player em janela, biblioteca noutra, sala numa terceira |

---

## 5. ⚠️ A pergunta que decide o player — e precisa de resposta antes da primeira tela

**AVFoundation não lê Matroska.** Isto não é suposição herdada: foi **medido no
iOS** em 14/08, perguntando ao `AVURLAsset` em vez de listar —

```
contêineres: mp4, mov, m4v          ← sem mkv, sem webm
vídeo:       h264, hevc, av1
áudio:       aac, ac3, eac3, alac, opus, flac
```

Ou seja: **falta só a casca**. Vídeo e áudio já são decodificáveis; o servidor
precisa **remuxar** (`video=copy` **e** `audio=copy`), que é barato — é o que a web
já recebe. Mas é ffmpeg aceso para mais um cliente.

No Mac existe uma segunda saída que o iPhone não tem barato:

| | (a) AVPlayer + remux do servidor | (b) motor embarcado (libmpv / VLCKit) |
|---|---|---|
| toca `.mkv` direto | não | **sim** — zero ffmpeg |
| PiP, Now Playing, teclas de mídia | **de graça** | reimplementar |
| HDR/EDR, Dolby Vision | **de graça** no display certo | depende do motor |
| dependência | nenhuma | uma biblioteca C no bundle |
| paridade de comportamento com web/iOS | **sim** | diverge |

**A recomendação deste plano**: começar em **(a)**, porque é a que herda o
comportamento já medido em três clientes — e abrir **(b) como motor alternativo**
numa fase própria, decidida por **número**: quantos % do acervo evitariam ffmpeg.
A medição é uma consulta: quantos arquivos são `mkv` com vídeo e áudio que o
AVFoundation já declara.

⚠️ **Não escrever tela de player antes desta resposta.** Ela muda a arquitetura da
camada de reprodução inteira, e trocar depois é reescrever.

---

## 6. As apostas do Mac — o «ousado», por valor ÷ custo

Cada uma com **como se mede**, porque aposta sem medida vira enfeite.

| | aposta | por que só aqui | como se mede |
|---|---|---|---|
| 1 | **a janela é o player** — filme em janela própria, redimensionável, com PiP do sistema e tela cheia em espaço próprio | o web tem uma janela só e um `position: fixed` | assistir num monitor enquanto a biblioteca fica no outro |
| 2 | **HDR / EDR** | `<video>` não entrega faixa estendida | um HDR num XDR: o branco especular passa do branco do sistema |
| 3 | **Now Playing, teclas de mídia, Control Center** | o navegador só finge | pausar pela tecla F8 com o app no fundo |
| 4 | **⌘K, a paleta de comando** — buscar obra, série, canal e **ação** no mesmo campo | teclado é cidadão de segunda no navegador | achar «Arcane S01E03» e dar play sem tocar no mouse |
| 5 | **a locadora em 3D de verdade** — caixa com espessura, peso e luz | CSS 3D não compõe hierarquia em Z; foi anotado como «não faremos» no Android | girar a caixa e ler a lombada sem que o desenho quebre |
| 6 | **grão e facho em Metal** | shader de verdade contra `background-image` | o grão sobre um preto chapado, sem banding |
| 7 | **Spotlight** — o acervo indexado no sistema (`CoreSpotlight`) | não existe no navegador | digitar «Goldfinger» no Spotlight e abrir no Odeon |
| 8 | **Atalhos / App Intents / Siri** — «tocar o próximo episódio» | idem | um atalho na barra de menus que retoma o último filme |
| 9 | **widget de `continuar`** e **notificação de lembrete do ao vivo** | o web depende da aba aberta | o lembrete toca com o app fechado |
| 10 | **baixados de verdade** — retomada, pausa, e arquivo visível no Finder | a web baixa para o sandbox do navegador | tirar a rede no meio e continuar |
| 11 | **descoberta do servidor** (Bonjour) + Keychain | o web pede endereço digitado | abrir o app na LAN de casa e ele já saber onde é |
| 12 | **Handoff com o iPhone** | o iOS já existe neste repositório | parar no iPhone e retomar no Mac pelo Dock |

⚠️ **Nenhuma delas entra antes da paridade.** Um app com Spotlight e sem ficha de
obra é uma demonstração, não um produto — e a régua da casa é a tela, não a lista
de recursos.

---

## 7. A arquitetura proposta

```
mac/
  Nucleo/     contrato da API, modelos, regras (sem UI, testável)
  Cena/       as peças visuais reutilizáveis (cortina, tira, caixa 3D, grão)
  Odeon/      o app: janelas, telas, comandos, menus
  docs/       este arquivo, e o que vier
```

| | |
|---|---|
| linguagem | **Swift 6**, concorrência estrita ligada desde o dia zero |
| UI | **SwiftUI**, com AppKit onde ele ainda ganha (janelas, menus, tracking) |
| estado | `@Observable`; repositório como **`actor`** |
| rede | `URLSession` + `Codable`, contrato **escrito à mão** (a quarta cópia) |
| vídeo | `AVKit`/`AVFoundation` — ver §5 |
| cache | decidir na fase dos baixados; nada de SwiftData «por via das dúvidas» |
| testes | XCTest para as regras (as mesmas que Android e iOS já travam) + XCUITest com captura |

⚠️ **Nada de webview, em nenhum canto** — nem para o admin, que é a tentação
óbvia por ser a tela mais chata.

---

## 8. As telas: o mapa `web` → Mac

| web | Mac | o que muda |
|---|---|---|
| **para você** | tela inicial da janela principal | fileiras com foco de teclado; o herói vira `NavigationSplitView` detail |
| **filmes** · **séries** | uma biblioteca com barra lateral | a barra lateral do Mac substitui as sete abas do topo |
| **coleções** | dentro da barra lateral | — |
| **ficha da obra** | painel de detalhe, e **janela própria** com ⌘I | o web abre modal |
| **player** | **janela própria**, PiP, tela cheia | a tira de película vira `Canvas`/Metal; janela do projetor mantém |
| **menu de DVD** | mantém, com áudio real | o web sintetiza som em Web Audio; aqui é `AVAudioEngine` |
| **locadora** | a aposta 5 | a cenografia **é** o produto — 6.250 linhas no web |
| **ao vivo** | ilha + grade de 12h, com a regra do §3 | a grade ganha rolagem horizontal com trackpad |
| **guia** | mantém | — |
| **mural** · **perfil** | mantém | notificação de sistema no lugar do badge |
| **junto (sala)** | **janela lateral** ao player | o web põe a conversa ao lado; aqui é janela |
| **admin** · **revisão** · **pastas** · **convites** | uma janela de **Ajustes** (⌘,) | é onde o Mac espera coisa de administração |

---

## 9. As fases — cada uma com o que ela **prova**

| | o que entra | prova |
|---|---|---|
| **R0** | casca, login, Keychain, descoberta do servidor, contrato mínimo | entra e lista **3.187** filmes com capa |
| **R1** | biblioteca + ficha + **player** (§5 respondido) | um filme abre, continua de onde parou e marca progresso |
| **R2** | séries: temporadas, episódios, **próximo episódio** | terminar o S01E01 e cair no S01E02 |
| **R3** | ao vivo, com a regra do tipo de canal e **sem transporte** | um canal de fora e um do Odeon, abertos pelos caminhos certos |
| **R4** | locadora + cenografia 3D | pegar uma caixa, girar, ler a lombada, levar pra casa |
| **R5** | mural, perfil, guia, coleções, sala | uma sessão a dois entre Mac e celular |
| **R6** | as apostas de sistema (7 a 12) | Spotlight acha, atalho toca, lembrete notifica |

⚠️ **R0 não tem tela bonita, e é de propósito.** O que ela prova é o contrato: se
o login, o token de mídia e a arte não estiverem certos, tudo que vier depois
mente.

---

## 10. Como se mede que está pronto

A régua é a do dono, e não muda: **ver na tela antes de escrever que funciona.**
Compilar, passar no lint e nos testes não dizem nada sobre quadro perdido.

O que o Mac deixa automatizar, e vale montar cedo:

- **XCUITest com captura** por fase — a mesma disciplina da varredura de 21 canais
  que achou os 17 pretos no Android;
- **medição de contraste** nas telas com arte por trás (o «voltar» da ficha de
  série media 1,33:1 e ninguém tinha olhado);
- **um roteiro de captura** por fase, guardado em `mac/docs/`, com o antes e o
  depois de cada mudança de desenho.

---

## 11. O que este plano propõe **não** fazer

| | por quê |
|---|---|
| **Catalyst** | é o iPad num Mac; o pedido é o contrário — mouse, teclado e janela |
| **compartilhar SwiftUI com o `ios/`** | o iOS é dedo e tela pequena; a régua daqui é o web. Compartilhar o desenho é herdar a decisão errada |
| **webview em qualquer tela** | inclusive no admin |
| **gerar o contrato** a partir do servidor | as três cópias à mão existem porque cada cliente lê o que precisa e comenta o porquê |
| **tocar no servidor sem pedido escrito** | `PEDIDOS-AO-SERVIDOR.md`, e o dono leva |
| **Cast** | o Mac tem AirPlay; Cast é problema do Android |

---

## 12. As perguntas que precisam de você, antes de escrever código

1. **O motor do player** (§5): AVPlayer + remux, ou medir libmpv antes? — decide a
   camada de reprodução inteira.
2. **macOS mínimo**: 15 (mais API, menos máquinas) ou 14?
3. **O admin entra na v1?** Ele é a metade menos usada e a mais chata; dá pra
   ficar no web sem prejuízo.
4. **A locadora 3D**: RealityKit (mais alto nível, menos controle) ou Metal puro
   (o inverso)? É a tela que mais define o produto.
5. **Offline**: o Mac guarda filme? Se sim, quanto — e onde, com o Finder olhando?
