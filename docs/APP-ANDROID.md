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

## 4. As decisões que são suas, e ainda não foram tomadas

| | |
|---|---|
| **player** | Media3/ExoPlayer, ou um sobre `MediaCodec`? |
| **offline** | baixar para assistir sem rede entra na v1? Muda o banco local inteiro |
| **TV** | o mesmo app com foco por D-pad, ou dois APKs? |
| **mínimo suportado** | qual `minSdk`? Decide o que dá pra usar de Compose e de Media3 |
| **o KMP** | fica parado, ou é apagado quando este app rodar? |

**Proposto para o player: Media3.** Ele já resolve HLS, Range, legendas WebVTT e
troca de faixa — que é exatamente o que a API serve. Escrever sobre `MediaCodec`
é assumir o trabalho que o §M6 descreveu como *"o maior sumidouro de
complexidade do projeto"*, do lado do cliente desta vez.

**Proposto para offline: fora da v1.** Ele exige banco local, fila de download,
e uma resposta para "a fita venceu enquanto você estava sem rede" — e essa
última é uma pergunta de produto, não de engenharia (a escassez da R29 já
decide o acesso pelo empréstimo em aberto).

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
| **5** | **para você** | recomendação com motivo, que é a tese do projeto numa tela só |

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
- **As três perguntas do §4**, que são de quem decide.
