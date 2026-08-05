# O app Android continua noutra sessão

Escrito em **04/08/2026**, no fim de uma sessão longa, para quem pegar isto sem
nenhum contexto. Leia inteiro antes da primeira linha de código.

> Existe um traspasse anterior, [`../../docs/CONTINUAR-ANDROID.md`](../../docs/CONTINUAR-ANDROID.md),
> e ele continua valendo — as regras da casa, o endereço do servidor, as
> armadilhas. **Este aqui é o que mudou depois dele**, mais a tarefa que está em
> aberto agora.

---

## 0. A TAREFA: o guia está errado, e o dono já disse por quê

**É a primeira coisa a fazer.** Está em aberto, foi diagnosticada, e não foi
consertada.

### O que aconteceu

A aba **guia** foi construída nesta sessão contra a rota `GET /api/guia`, que
devolve `GuiaEixos` — direção, elenco, trilha, gêneros, décadas, países. Um
**índice** do acervo.

O dono olhou e disse:

> «Você não pegou a maior essência do Guia, ele ter informações legais igual
> temos no web com o **gênero da semana** e **Em cartaz essa semana**.»

E ele está certo. O guia da web não é um índice: é uma **revista semanal**. A
rota é outra — `GET /api/guia/revista` (`web/src/api.ts:1740`) —, e nenhuma
linha do app a chama.

### O que a tela da web tem, na ordem

Do screenshot que o dono mandou:

```
GÊNERO DA SEMANA                                            até segunda
Romance                                        ← letreiro serifado, enorme

O que estes filmes têm em comum é a variedade de décadas em que foram
produzidos, desde os anos 80 até 2013. Aladdin, de 1992, é um clássico da
animação romântica, enquanto Sim Senhor, de 2008, traz um toque de comédia…

ESCRITO POR LLAMA-3.3-70B-VERSATILE             ← o selo, em versalete

[Juno] [Aladdin] [Será Que?] [Sid & Nancy] [O Virgem de 40 Anos] …
2007 · Jason Reitman                            ← ano · diretor sob cada um

┌──────────────────────────────────────────────────────────────────┐
│ [pôster]   EM CARTAZ ESTA SEMANA                                  │
│            Juno                                                   │
│            Termine até segunda pra participar.                    │
└──────────────────────────────────────────────────────────────────┘
```

### O contrato, já levantado — não precisa caçar

```kotlin
// GET /api/guia/revista        (web/src/api.ts:826)
Revista(
    semana_de: String,
    vira_em: String,
    eixo: String,               // "genero" | "decada" | "pais" | "diretor" | "saga"
    tema: String,               // "Romance"
    filmes: List<FilmeDaCapa>,
    ensaio: String?,
    ensaio_por: String?,
    evento: EventoDaSemana?,
)

// api.ts:805
FilmeDaCapa(
    id: String, titulo: String, ano: Int?, poster: String?, diretor: String?,
    visto: Boolean,             // "a única coisa da capa que é sua"
)

// api.ts:815
EventoDaSemana(
    tipo: String,               // "obra" | "saga"
    id: String, titulo: String, poster: String?,
    obras: Int, suas: Int,
    participou: Boolean,
    participantes: List<String>,
)
```

### Os dois comentários da folha que **são regra**, e não sugestão

Copie-os pro Kotlin, porque eles carregam decisão:

> **`ensaio`** — «`null` quando não há chave do LLM ou o texto ainda não foi
> gerado. A tela **omite a seção** — não mostra "carregando" nem inventa prosa
> (§18, §24).»

> **`ensaio_por`** — «O selo. Quem lê tem direito de saber que aquele parágrafo
> **não foi escrito por gente** — a mesma regra do crédito `WIKIPÉDIA` das
> curiosidades (§32).»

O segundo não é enfeite: é obrigação editorial do projeto. Texto de máquina sai
sempre creditado.

### O que fazer com o que já existe

`ui/guia/TelaDoGuia.kt` desenha os eixos e **funciona** — com dado real: gêneros,
décadas, países, e as fileiras de direção/elenco/trilha com «7 de 23». Não jogue
fora. A revista vai **em cima**, e os eixos viram o que vem depois dela — a
revista é a capa, os eixos são o índice.

Sugestão de ordem na tela: revista (tema + ensaio + filmes) → em cartaz → os
eixos que já estão lá.

⚠️ **Duas rotas, duas falhas independentes.** Siga o que a locadora já faz
(`ModeloDaLocadora`): as duas em `async` paralelo, e cada uma falhando devolve
vazio em vez de derrubar a tela. Revista fora do ar não pode apagar os eixos.

---

## 1. Onde você está, em uma tela

Um app Android nativo (Kotlin + Compose) para o **Odeon**, um servidor de mídia
pessoal que roda na casa do dono. **10.673 linhas de Kotlin.**

| | |
|---|---|
| a espec | [`../../docs/APP-ANDROID.md`](../../docs/APP-ANDROID.md) — as decisões e o porquê |
| o traspasse velho | [`../../docs/CONTINUAR-ANDROID.md`](../../docs/CONTINUAR-ANDROID.md) — regras da casa, servidor, armadilhas |
| o redesenho | [`REDESENHO.md`](REDESENHO.md) — as nove fases, todas feitas, mais o segundo redesenho |
| pedidos pendentes | [`PEDIDOS-AO-SERVIDOR.md`](PEDIDOS-AO-SERVIDOR.md) — dois, no formato da §1b |
| como rodar | [`../README.md`](../README.md) |

**Tudo está commitado e empurrado.** Quinze commits, `main` limpo.

### Compilar nesta máquina

O JDK do sistema é 24 e o AGP 9 não aceita. Use o do Android Studio:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

```bash
cd android && ./gradlew :app:assembleDebug :app:testDebugUnitTest :app:lintDebug
```

O emulador (`Medium_Phone`, android-37.1) funciona aqui e já está com o app
instalado e com sessão aberta. O servidor é `100.77.253.18:8085`, pela tailnet.

**Estado verde de referência:** `assembleDebug` ✅ · **29 testes** ✅ · lint com
**8 avisos, todos pré-existentes** (`EmptySuperCall`×2, `PictureInPictureIssue`,
`GradleDependency`, `ExportedService`, `ObsoleteSdkInt`, `UseKtx`×2). Se
aparecer um nono, é seu.

---

## 2. O que esta sessão fez

A v1 (sete fases) já existia e não estava commitada. Foi commitada, e depois:

| | |
|---|---|
| **redesenho R1–R9** | tipografia serifada, rótulos com régua, pílulas, o cartaz como objeto, a caixa de fita com lombada, transição compartilhada, marquise, giroscópio, borda a borda, widget, atalhos |
| **segundo redesenho** | «dar vida» — o dourado deixou de ser tinta e virou luz |
| **a vitrine** | a locadora virou loja de verdade, com estantes |
| **o menu inferior** | virou **facho de projetor** |
| **mural e guia** | duas abas novas |

### A ideia que atravessa o segundo redesenho

Medido: o app usava `destaque` 26 vezes contra 210 da web, e **dourado como luz
— sombra, halo, gradiente — zero vezes contra 19**. O `Tema.kt` chamava os três
tons de «filamento aceso», «topo da luz» e «filamento apagado» desde a fase 1 —
nomes que o código nunca honrou.

`ui/Luz.kt` é onde isso mora agora: **frio é contorno, aceso é corpo, quente é
onde a luz bate**. Nenhum valor novo entrou na paleta; o que entrou foi
gradiente, sombra colorida e alfa.

### Os arquivos novos de `ui/` que valem conhecer

```
Luz.kt        o dourado como luz — pegaLuz(), acesa(), acendePorBaixo()
Facho.kt      o menu inferior: cone de projetor, poeira, lente, piscada
Chegada.kt    Modifier.chega(indice) — as coisas caem no lugar, escalonadas
Marquise.kt   as lâmpadas com a luz correndo
Inclinacao.kt a paralaxe por acelerômetro (com a chave de acessibilidade)
Pilula.kt     PilulaDeFiltro (escolha) e PilulaDeEtiqueta (fato)
Rotulo.kt     RotuloDeSecao — versalete, régua em gradiente, número
Moldura.kt    MolduraDoCartaz — a transição compartilhada
Cor.kt        corDeHex — o parser da dominant_color
Grao.kt       o grão de película, TESTADO E REPROVADO (fica desligado)
```

---

## 3. As armadilhas que eu paguei nesta sessão

Custaram tempo. Nenhuma está nos documentos velhos.

**`padding` negativo derruba o app.** `padding(horizontal = (-16).dp)` compila,
passa no lint, e lança `IllegalArgumentException: Padding must be non-negative`
em tempo de execução. Pra sangrar pra fora da margem, use `Modifier.layout` —
mede com folga e posiciona deslocado. Está feito em `TelaDaLocadora.Tabua`.

**`resValue` não funciona no AGP 9 sem `buildFeatures { resValues = true }`.** O
erro é de configuração, não de compilação, e não diz o que fazer.

**`launchMode="singleTop"` é obrigatório pra atalho funcionar com o app
aberto.** Sem ele o `onNewIntent` **nunca** é chamado, e o `am start` responde
«intent has been delivered to currently running top-most instance», que lê como
sucesso. O que foi entregue é a ordem de trazer pra frente, não o extra.

**`provideGlance` do widget é `suspend` mas não é `suspend` na IO.** Chamada de
rede bloqueante ali vira `NetworkOnMainThreadException` engolida por
`runCatching` — e o sintoma é dado faltando, não erro. Use
`withContext(Dispatchers.IO)`.

**`graphicsLayer` gira o que já foi medido.** Texto girado com `rotationZ` é
medido **antes**, no espaço do pai — use `requiredWidth`, senão ele corta.

**O `NavigationBar` do Material pintava a cápsula do item selecionado com
`secondaryContainer`, que nunca foi definido no esquema** — caía no lilás de
fábrica `#4A4458`. Se algum dia voltar a usar componente de navegação do
Material, defina esse campo ou zere o `indicatorColor`.

**`adb input swipe` de 900ms não dispara `detectVerticalDragGestures`; 1600ms
dispara sempre.** O gesto precisa vencer o `touchSlop` **e** andar a distância;
rápido demais os dois chegam numa rajada. **Isto me fez declarar um recurso como
quebrado quando ele funcionava.**

**`screencap` devolve quadro preto de vez em quando neste emulador**, enquanto o
`uiautomator dump` mostra a árvore inteira renderizada. Um preto lê exatamente
como tela quebrada — confira com `uiautomator` antes de acreditar. Me custou
dois alarmes falsos.

---

## 4. As regras da casa, e elas mandam

Do traspasse velho, e continuam valendo inteiras:

1. **Medir antes de desenhar.** Nada entra sem número tirado do acervo real.
2. **Não mentir com cara de metadado** (§18). Dado que não existe, a tela omite.
   Corolário (§24): linha vazia **some**, não vira "—".
3. **Errar em silêncio é o defeito** (§8b). E o §53: o produto **não oferece o
   que a validação vai negar**.
4. **Não corrija sozinho** — pergunte, ou faça o que foi pedido. O dono pediu
   explicitamente: *«sempre planejar comigo»*.

E as que esta sessão aprendeu:

5. **O contrato da API vem da web.** `web/src/api.ts` é a outra cópia à mão do
   mesmo contrato e mora neste repositório. **Quatro vezes** nesta sessão o
   servidor já mandava o dado e o app não pegava: `height`, `size_bytes`, `tags`,
   as estantes da locadora — e agora a revista do guia. **Antes de pedir coisa ao
   servidor, leia a `api.ts`.**
6. **Compilar e passar no lint não é ter visto.** O screenshot achou defeito em
   praticamente toda leva desta sessão.

### Git

- Commits **em inglês** (o dono pediu explicitamente, contra a §7 do documento
  velho, que dizia português), **sem nenhuma menção a assistente**.
- **Commite e empurre só quando ele pedir.**
- Confira o que vai no commit. Já houve um `.env` com senha a caminho de um repo
  público.

### A sua raia

Você mexe em `android/` e **mais nada**. `web/`, `clients/` e o `odeon-server`
são do dono — o `odeon-server` nem se abre. Precisando de algo do servidor, o
formato do pedido está na §1b do traspasse velho, e há dois exemplos prontos em
[`PEDIDOS-AO-SERVIDOR.md`](PEDIDOS-AO-SERVIDOR.md).

---

## 5. O que está em aberto

**Do dono, e não seu:**

- Levar os dois pedidos de [`PEDIDOS-AO-SERVIDOR.md`](PEDIDOS-AO-SERVIDOR.md):
  o `403` ao pegar uma fita que a tela oferece, e as duas entradas do mesmo filme
  com resoluções diferentes (816p × 818p — são rips distintos, não duplicatas).
- **Cast** nunca foi verificado: precisa da rede de casa, com o app apontado pro
  IP de LAN. É a única parte da v1 sem verificação.

**Seu, e por ordem de valor:**

1. **O guia como revista** — a §0 deste documento.
2. **A régua de fps não decide nada no emulador.** A variância entre execuções é
   maior que a diferença entre versões. Precisa de aparelho real ou de um módulo
   `androidx.benchmark`. Há bastante enfeite dependendo dela.
3. **Baixados virar filtro** em vez de atalho — uma pílula «no aparelho» na
   grade, e a tela deixa de existir. Hoje é um `TextButton` no cabeçalho.
4. **O guia ligar na biblioteca**: `FaixaDoGuia.chave` (`genre:Terror`) é
   exatamente o que iria pro filtro, e a grade não tem filtro. Nada no guia é
   clicável por isso — toque que não leva a lugar nenhum seria §8b.
5. **O mural só lê.** Faltam escrever post, comentar, e as salas do «junto» —
   outras oito rotas.
6. **A `estante-respira`** da web: o esqueleto da prateleira pulsando enquanto a
   vitrine carrega. Hoje são ~12 segundos de indicador girando.
7. **O atalho dinâmico** de «continuar assistindo *Drive*» com capa. Os três
   estáticos cobrem lugares; esse cobriria obras.

**Três propostas de menu ficaram «na agulha»** — o filamento, a marquise e a
película. O facho foi o escolhido e está feito. Se ele for revisto, as outras
estão desenhadas em maquete.

---

## 6. Duas coisas sobre dado de teste

O ambiente é o **banco de produção**, com três pessoas de verdade dentro. O dono
confirmou que pegar e devolver fita com a conta `sam` está liberado.

- Nesta sessão foram pegas e devolvidas **quatro fitas**. Todas devolvidas, e
  conferido: «nenhuma caixa fora da estante», limite de volta em 3. **O rastro
  disso aparece no mural** — «você devolveu…», com o detalhe «rebobinada».
- Um **download de ~2 GB** continua no emulador, deixado de propósito pra testar
  reprodução sem rede. «apagar» na tela de baixados resolve quando não servir
  mais.

---

## 7. A frase que resume o projeto

> **O screenshot acha defeito que o código não denuncia.**

Nesta sessão ele achou: a barra inferior comendo 21% da tela em paisagem, uma
linha de metadados truncada prometendo dado inalcançável, o grão virando sujeira
sobre neve, uma caixa que virava tira ao girar, uma lombada mostrando três
letras, o banding num degradê, a poeira em grade, o herói saindo cinza, e uma
tela de locadora que dizia «está tudo guardado» desenhando um vazio.

Nenhum deles aparece em teste. Todos apareceram numa foto.
