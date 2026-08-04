# Odeon — o app Android

## Sobre este arquivo

Mesma regra de autoria das rodadas do `DESIGN.md`:

- **Decidido** — palavra de quem decide, dita explicitamente. Não se mexe sem
  perguntar.
- **Proposto** — sugestão de quem escreve, esperando confirmação. Pode ser
  vetada sem discussão.
- **Medido** — número tirado do código ou do acervo, hoje.

Onde não houver marca, é fato de código.

E a regra de trabalho continua valendo: **quando a ideia parecer "errada" pela
régua de engenharia, perguntar ou fazer o que foi pedido — nunca entregar a
versão sóbria por conta própria.**

---

## 0. O estado, medido em 04/08/2026

| | |
|---|---|
| rotas na API | **113** |
| telas na web | 11, com endereço próprio |
| obras no acervo | 17.930 · 3 usuários |
| clientes Kotlin existentes | `shared` 638 linhas · `composeApp` 7 arquivos · `tv` 3 |
| rotas que os clientes Kotlin consomem | **10 de 113** |

As dez que o KMP já fala: `auth/login`, `auth/me`, `auth/setup`, `auth/status`,
`collections/tree`, `continue`, `events`, `health`, `tags`, `works`.

As áreas da API, por volume de rotas:

```
works 22 · auth 11 · locadora 9 · live 9 · junto 8 · maintenance 7
collections 6 · curation 5 · media 3 · guia 3 · convites 3 · … 
```

---

## 1. A decisão que abre este documento

**Decidido: um app Android nativo, do zero.** Kotlin + Jetpack Compose, só
Android — sem Kotlin Multiplatform, sem compartilhar com iOS.

### O que isso custa, dito de frente

O `clients/` tem um KMP que **compila e roda**: `shared` com Ktor, modelos e
repositório, `composeApp` para celular e iOS, `tv` para Android TV. São 638
linhas de `shared` que já falam dez rotas, e um projeto iOS que já abre no
simulador.

Começar do zero joga isso fora. Não parcialmente: o `shared` é `commonMain`, e um
app nativo não importa `commonMain` sem arrastar o KMP de volta.

**O que se ganha em troca:** um alvo só. Sem `expect/actual`, sem alinhar
Gradle de três módulos, sem a camada de abstração que existe para o iOS
conseguir usar o mesmo código. Media3/ExoPlayer entra direto em vez de atrás de
uma interface. Quem for mexer no app mexe em Kotlin de Android e mais nada.

**É a decisão de quem decide, e está registrada como tal.** O KMP fica onde
está — parado, compilando — até alguém decidir apagá-lo. Este documento não
propõe apagá-lo.

---

## 2. O que a web faz, e que o app vai ter que responder

Onze telas, em ordem de tamanho no código:

| tela | linhas | o que ela é |
|---|---|---|
| `locadora` | 1.812 | 600 caixas de VHS/DVD em estantes 3D, que voam e giram |
| `ao vivo` | 1.493 | IPTV + canais que o Odeon programa do acervo |
| `ficha` | 1.104 | o cartaz da obra, com elenco, curiosidades, avaliações |
| `player` | 824 | timeline com buffer, atalhos, selo do modo de reprodução |
| `mural` | 811 | posts, mensagens, salas de assistir junto |
| `admin` | 789 | pessoas, aparelhos, trabalhos, manutenções |
| `para você` | 720 | recomendação com motivo legível |
| `menu de DVD` | 677 | o menu do disco, com trilha sintetizada |
| `guia` | 647 | wiki de cinema cruzada com o seu acervo |
| `perfil` | 614 | o perfil tipo Steam, com enfeites e conquistas |
| `coleções` | 467 | franquia → série → temporada |

**Proposto: o app não é a web.** Traduzir onze telas de uma vez é o caminho de
um app que fica dois anos sem sair. A sequência do §5 escolhe por outro
critério: *o que só faz sentido no celular*.

---

## 3. O que NÃO se traduz, e por quê

Esta seção existe para o documento não prometer o que a plataforma não dá.

### A estante 3D da locadora

Ela é **CSS 3D** — `perspective`, `transform-style: preserve-3d`, `translateZ`.
O Compose não tem equivalente: `graphicsLayer` faz rotação e câmera, mas não
compõe uma hierarquia 3D com filhos em profundidade.

**Proposto:** no app, a locadora é uma prateleira **2D** com a arte das caixas,
e o gesto de girar a caixa vira um `flip` de duas faces. O objeto continua
sendo objeto — o que se perde é a profundidade da cena, não a metáfora.

Se a estante 3D for requisito, o caminho é uma superfície OpenGL/Compose com
render próprio, e isso é um projeto dentro do projeto. **Fica em aberto.**

### A trilha sintetizada do menu de DVD

É Web Audio API gerando som em tempo real (§47). No Android o equivalente é
`AudioTrack` com buffer PCM — existe, mas é reescrever o sintetizador.
**Proposto: fica de fora da primeira versão.**

### O `<video>` e a negociação de capacidade

A web pergunta ao navegador com `canPlayType` e manda a resposta pro servidor,
que decide Direct Play / Remux / Transcode. **No Android quem responde é o
`MediaCodecList`**, e ele responde melhor: dá perfis e níveis, não um "probably".

Isso não é perda — é a mesma pergunta com resposta mais exata. Mas é código
novo: `/api/transcode/capabilities` recebe hoje um formato que a web monta.

---

## 4. As decisões, tomadas

| | |
|---|---|
| **player** | **Media3/ExoPlayer** |
| **offline** | **entra na v1** |
| **formato** | **celular e tablet**. Sem TV |
| **`minSdk`** | **26** (Android 8.0) |
| **o KMP** | fica parado, e não é apagado |

### Media3, e a liberdade que a pergunta escondia

Duas liberdades se confundem aqui, e separá-las é o que decidiu:

| | quem manda |
|---|---|
| **como o player parece** — timeline, selo do modo, preview de seek, gestos | **você, sempre.** O Media3 é motor; a UI é inteira sua, em Compose |
| **como os bytes viram imagem** — buffer, sincronia A/V, decodificador, seek | o Media3 |

Escrever do zero compra a segunda, que é a que o Odeon **menos precisa**: o
servidor já negocia Direct Play / Remux / Transcode, serve HLS com segmentos de
4s e keyframes alinhados, e entrega legenda como faixa WebVTT nativa. O cliente
só toca o que chegou.

E o que se compraria junto são **as manhas de decodificador de cada aparelho** —
onde moram os anos de gambiarra acumulada do ExoPlayer, e nenhuma delas aparece
em teste até um aparelho específico travar.

O Media3 não é caixa-preta: `Renderer`, `DataSource`, `MediaSource` e o seletor
de faixa são substituíveis. Faltando alguma coisa, troca-se **a peça**.

### Offline na v1, e a pergunta que ele abre

**Decidido.** E é a decisão que mais muda este documento: puxa banco local
(Room), fila de download com retomada, e política de espaço.

**E abre uma pergunta que não é técnica, e continua em aberto:**

> *O que acontece com um filme baixado quando o empréstimo vence e o aparelho
> está sem rede?*

A escassez (§66) decide o acesso pelo empréstimo **em aberto**, e o servidor
revoga no mesmo instante em que a fita volta — sem revogação em separado, porque
`devolvido_em IS NULL` é a autorização inteira. Um arquivo no disco do celular
não tem como saber disso.

As saídas não são equivalentes, e nenhuma é neutra:

| | |
|---|---|
| o download carrega um prazo e para de tocar sozinho | honra a escassez, e o aparelho vira juiz de uma regra que é do servidor |
| o baixado toca até você reconectar | honesto sobre quem manda, e abre uma janela em que a fita "voltou" e continua tocando |
| só baixa o que está emprestado, e apaga na devolução | exige rede pra apagar — o que é o mesmo problema com outro nome |

**Fica pra quem decide.** É a mesma classe da decisão do §66 (a regra vale pro
admin), e por isso não é minha.

### Sem TV, e é decisão

**Decidido: o app é de celular e tablet.** Nada de Android TV, nada de D-pad,
nada de layout de três metros.

Isso não foi um recuo — foi trocar de alvo. Um app que serve celular e TV ao
mesmo tempo paga em dois lugares: o Compose de TV é outro conjunto de artefatos
(`androidx.tv:tv-foundation`, `androidx.tv:tv-material`), com componentes de
foco próprios, e misturá-lo com Material3 de celular no mesmo módulo é briga
constante. E toda tela nova sairia duas vezes.

**O que se ganha:** poder usar o celular como celular. Gesto, PiP, notificação
de mídia, download, haptics, `Cast` — coisas que ou não existem na TV, ou
existem de outro jeito.

Se um dia entrar TV, o caminho já está claro e não é este app: `:core`
compartilhado, mais um módulo `:tv` com UI própria. A separação da R51 mostrou
o custo de dividir cedo demais; dividir **quando houver alvo** é outra coisa.

### `minSdk` 26, e agora quem decide é o que se quer usar

Antes o número estava preso ao aparelho de TV mais velho que importava. Sem TV,
ele passa a ser escolhido pelo que o app quer fazer — e **26 é onde mora o
Picture-in-Picture**, que num app de vídeo não é enfeite: é assistir enquanto se
procura a próxima coisa.

Adaptive icon e canal de notificação também são de 26, e os dois entram sem
alternativa.

**E o resto do que é moderno não precisa de `minSdk` alto** — precisa de
`targetSdk` alto e de degradação por versão:

| | pede | sem ele |
|---|---|---|
| cor dinâmica (Material You) | 31 | cai na paleta da casa, que já existe |
| voltar preditivo | 33 | volta normal |
| idioma por app | 33 | o do sistema |

Ou seja: `minSdk` **26**, `targetSdk` no mais novo, e o que for de 31+ entra
atrás de uma checagem. Ninguém fica de fora, e nada moderno fica de fora.

---

## 4b. O que "Android de verdade" quer dizer aqui

**Proposto**, e é a seção que separa este app de um site embrulhado. Cada item
existe porque **o servidor já dá o dado** — nenhum pede backend novo.

| | o que é | o que já existe pra isso |
|---|---|---|
| **PiP** | o filme encolhe pro canto e você continua navegando | `minSdk 26`, e o Media3 entrega o `Player` que o PiP precisa |
| **sessão de mídia** | controle na tela de bloqueio, no fone, no carro, e áudio seguindo com a tela apagada | `MediaSession` do Media3, de graça |
| **download de verdade** | fila com retomada, pausa e limite de rede — não um `GET` gigante | `DownloadService` do Media3, e o `/api/stream` já fala Range |
| **cor da tela sai do pôster** | a interface se tinge com a obra que você está olhando | **9.332 obras já têm `dominant_color` extraída** no servidor (§M3) — a web usa isso desde o redesenho |
| **preview de seek sem rede** | arrastar a timeline mostra o quadro daquele instante | a folha de sprites do `/api/media/{id}/scrub`: **uma imagem por arquivo**, baixada uma vez |
| **continuar em qualquer lugar** | parou na TV, continua no ônibus | `/api/continue` e o barramento SSE, que já sincroniza entre aparelhos |
| **atalhos e widget** | segurar o ícone → "continuar assistindo" | mesma rota, sem tela |

**Proposto: `Cast` fica de fora da v1.** Ele é a resposta certa pra "quero ver na
TV" — e como não haverá app de TV, ele volta a ser tentador. Mas mandar pro
Chromecast exige que o servidor seja alcançável pelo Chromecast, e isso é uma
conversa de rede que merece seção própria.

---

---

## 5. Sequência proposta

O critério **não** é "traduzir a web em ordem de tamanho". É: *o que um celular
faz que um navegador no sofá não faz*.

| | o que | por quê aqui |
|---|---|---|
| **1** | **entrar e ver a biblioteca** | sem isto não há app. `auth/*` e `works` são 33 das 113 rotas, e o KMP prova que elas bastam pra uma tela |
| **2** | **assistir** | é o produto. Plano de reprodução, Direct Play, e o selo dizendo por quê |
| **3** | **continuar de onde parou** | é o que o celular faz melhor que tudo: você parou na TV e continua no ônibus. `/api/continue` + `playback` |
| **4** | **a locadora** | é a alma do produto, e é onde a v1 deixa de ser "um Jellyfin bonito" |
| **5** | **baixar pra ver sem rede** | decidido pra v1, e é o que mais separa este app da web. Depende da resposta sobre a fita vencida |
| **6** | **para você** | recomendação com motivo, que é a tese do projeto numa tela só |

Dentro da fase 2 (**assistir**) entram PiP e sessão de mídia. Eles não são
extras de depois: um player de Android que não faz os dois é um `<video>` com
mais passos, e é justamente o que este app não é pra ser.

**Proposto:** mural, guia, ao vivo, perfil e admin ficam para depois da v1. Não
por serem menores — o mural tem 811 linhas — mas porque nenhum deles responde
"o que eu assisto agora", que é a pergunta que se faz com o telefone na mão.

---

## 6. O que o servidor já dá de graça, e o app não deve reinventar

Medido, e vale saber antes de escrever a primeira tela:

| | |
|---|---|
| **o endereço da API** | a web deduz da página; no app é o usuário digitando o host, e os clientes Kotlin **já tentam https antes de http** |
| **o token de mídia** | é separado do de sessão e curto (8h). E **emitir um novo aposenta o anterior** (§43) — um app que renova no meio de um filme derruba o próprio player |
| **o barramento** | `/api/events` é SSE, e o token vai na query porque `EventSource` não manda header. **Uma conexão pro app inteiro** (§62) |
| **a escassez** | com ela ligada, assistir exige empréstimo em aberto — inclusive para o admin (§66). O app tem que perguntar `locadora/liberadas` antes de oferecer play, ou vai mostrar um botão que leva 403 |
| **o preview de seek** | é uma folha de sprites, uma imagem por arquivo. O app baixa uma vez e arrasta sem nenhuma requisição |

---

## 7. O que continua em aberto

- **Nome e identidade do módulo.** `dev.odeon.android`? E o ícone?
- **Onde o projeto mora.** Dentro de `odeon-client`, ao lado de `clients/`, ou
  em repositório próprio? A separação da R51 mostrou o custo de dividir: o
  contrato da API vira uma cópia a mais.
- **Publicação.** Play Store exige conta, política de privacidade e revisão. Um
  APK assinado distribuído por link não exige nada disso.
- **A fita vencida sem rede** (§4). É a única pergunta do §4 que sobrou, e é a
  que trava a fase 5.
- **`Cast`, e a rede que ele exige.** Sem app de TV, é ele que responde "quero
  ver na sala" — e depende de o servidor ser alcançável pelo aparelho de Cast,
  que hoje vive numa tailnet.
