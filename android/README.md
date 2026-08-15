# Odeon — o app Android

Kotlin + Jetpack Compose, nativo, só Android. Um projeto Gradle próprio, irmão
de `web/` e `clients/`.

> **A espec está em [`../docs/APP-ANDROID.md`](../docs/APP-ANDROID.md).** Ela tem
> as decisões todas, com o porquê de cada uma e a sequência em sete fases. Este
> arquivo é só como rodar.
>
> **⚠️ Pegando o projeto agora? Comece por
> [`docs/CONTINUAR-2.md`](docs/CONTINUAR-2.md)** — ele é o traspasse mais
> recente, e a primeira seção dele é a tarefa que está em aberto: o guia foi
> construído contra a rota errada e precisa virar a **revista semanal** que a
> web tem.
>
> **Os pedidos pendentes ao servidor estão em
> [`docs/PEDIDOS-AO-SERVIDOR.md`](docs/PEDIDOS-AO-SERVIDOR.md)**, no formato da
> §1b — prontos pro dono levar. São **quatro** (dois deles do `:tv`), e nenhum
> bloqueia o app.
>
> **O redesenho da TV está em [`docs/REDESENHO-TV.md`](docs/REDESENHO-TV.md)** —
> escrito em 12/08/2026 depois de o `:tv` rodar na TCL e de o dono mostrar cinco
> telas do celular. Sete levas, da cabine de projeção ao mural. A **T0** (o
> módulo `:cenario`), a **T1** (o trilho vira cabine), a **T2** (o player), a
> **T3** (a locadora) e a **T4** (a biblioteca) estão feitas e conferidas na
> TCL; **T5 e T6 seguem proposta**, uma a uma.
>
> **O redesenho do celular está em [`docs/REDESENHO.md`](docs/REDESENHO.md)** — a proposta
> de fases pra o app deixar de ser funcional-e-plano e passar a se parecer com o
> Odeon, com o que só dá pra fazer no celular. É proposta, não plano aprovado.

**Estado: a v1 inteira está feita, o redesenho R1–R9 entrou, o segundo
redesenho — «dar vida» — entrou nas três levas, e em 12/08/2026 nasceram o
`:core`, o `:tv` — o Odeon numa TCL com Google TV — e o `:cenario`, que é a
**T0** do redesenho da TV.**

O `:tv` foi **visto rodando numa TCL Smart TV Pro**, tela a tela, no mesmo dia
em que nasceu: login, as seis abas com dado de verdade, a ficha, e um filme
tocando. Ver a seção [«A sala»](#a-sala-o-tv-numa-tcl-com-google-tv) — inclusive
os **oito** defeitos que só o aparelho encontrou.

O segundo é sobre o dourado: o app usava a cor da casa como **tinta** (zero
brilhos, halos ou gradientes) e a web usa como **luz** (dezenove). Ver a §0b do
`docs/REDESENHO.md`.

As sete fases da §5 da espec foram escritas **e vistas rodando em aparelho**,
tela a tela — não só compiladas. O Cast é a única com ressalva: ele está escrito,
mas só dá pra verificar na rede de casa, porque o Chromecast não entra na tailnet
(§4c).

Depois delas entraram as **levas 1 a 4 do [`docs/REDESENHO.md`](docs/REDESENHO.md)** — todas vistas em aparelho:

| | |
|---|---|
| a decisão da §6 | os destinos saíram do cabeçalho da biblioteca e viraram **barra de navegação adaptativa** — barra embaixo em pé, trilho lateral em paisagem e tablet |
| R1 | entrou uma **serifada de display** (Noto Serif SemiBold, embutida), e título de tela e de obra viraram letreiro |
| R2 | o **rótulo de seção** com versalete espaçado e régua em gradiente, medido no `.strip` da web |
| R3 | as **pílulas** — o filtro de tempo do para-você e as etiquetas da ficha |
| R4 | o cartaz vira **objeto**: barra de progresso dentro do pôster, linha de metadados, e afundar ao toque |
| R5 | a **fita vira coisa**: caixa em três quartos com lombada e verniz, e o háptico com dois pesos |
| R7 | a **transição compartilhada** — o pôster tocado cresce e vira o pôster da ficha |
| R6 | o **herói** do para-você, com as lâmpadas da marquise. O grão foi testado e **reprovou** |
| R8 | o **corpo do aparelho**: paralaxe por acelerômetro na ficha, ficha borda a borda, e o detente háptico no seek |

| R9 | o app **sai do app**: arte e título no controle de mídia, atalhos ao segurar o ícone, e o **widget** de continuar assistindo |

## As cinco abas

`biblioteca · locadora · mural · guia · para você`, com o menu inferior desenhado
como **facho de projetor** — a luz nasce fora da barra, abre sobre o destino
escolhido, e **pisca como lâmpada de arco** ao trocar.

⚠️ **Baixados saiu da barra** e virou o atalho «no aparelho ›» no cabeçalho da
biblioteca. A conta: com mural e guia entrando seriam seis abas, e a seis cada
uma fica com 68,5dp — «biblioteca» ocupa 61dp a 12sp, ou seja não cabe. E o
corte foi nele porque baixados nunca foi um **lugar**, é um **estado** do
acervo.

**Mural e guia são versões primeiras e honestas.** O mural da web tem 811 linhas
e o guia 647; aqui cada um é um `GET` desenhado. O que falta está escrito nos
comentários das telas — escrever post, comentar e as salas do "junto" no mural;
e no guia, tocar num eixo ainda não filtra a biblioteca, porque a biblioteca não
tem filtro.

⚠️ **A régua de fps da R4 ficou sem resposta.** Ela manda tirar o enfeite se a
rolagem sair de 60fps no emulador, e o emulador não segura 60fps nesta grade
**com ou sem** enfeite — a variância entre execuções ficou maior que a diferença
entre as versões. Precisa de aparelho de verdade ou de `androidx.benchmark`. Os
números estão no `docs/REDESENHO.md` §0 e no comentário do `Cartaz`.

`assembleDebug` ✅ · **155 testes** (105 no `:core`, 22 no `:cenario`, 28 no
`:app`) ✅ · `lintDebug` limpo nos **quatro** módulos, com **zero** exceções em
`lint.xml` ✅

⚠️ A conta mudou de lugar, não de tamanho: os testes de `Projecao` e de
`Insignia` foram junto com as peças pro `:cenario` na T0. Teste que fica onde o
código não está é teste que ninguém roda quando mexe no código.

⚠️ O parágrafo que estava aqui até 04/08/2026 dizia «fase 1 escrita, não rodada
em aparelho — nenhuma tela foi vista rodando». Ficou desatualizado por seis fases
sem ninguém voltar pra corrigi-lo, e é o tipo de linha que faz quem chega
recomeçar trabalho já feito.

---

## Rodar

Não é preciso instalar nada além de Docker — **nem JDK, nem Gradle, nem o SDK do
Android**. É o padrão da casa: o servidor compila dentro do `odeon-api`, a
interface dentro do `odeon-web`, e este app dentro do `odeon-android`.

```bash
cd ..                              # a raiz do odeon-client
docker compose up -d android       # a primeira vez baixa o SDK (~600 MB)
```

Depois disso:

```bash
docker exec odeon-android ./gradlew :app:assembleDebug
docker exec odeon-android ./gradlew :app:testDebugUnitTest
docker exec odeon-android ./gradlew :app:lintDebug
```

O APK sai em `app/build/outputs/apk/debug/app-debug.apk`.

Do zero absoluto — sem imagem, sem SDK, sem cache do Gradle — os três comandos
acima levaram **4m02s**, medido em 04/08/2026.

### ⚠️ Editar o `docker-entrypoint.sh` **não** basta

Ele é copiado pra dentro da imagem no `docker build`. Mudar o arquivo e dar
`up` faz o container rodar a **versão velha**, calado — e o sintoma é um SDK com
os pacotes errados, que só aparece quando o build reclama de plataforma.

Aconteceu aqui: o entrypoint passou a pedir `platforms;android-37.1`, o `up`
instalou `android-36` do mesmo jeito, e a diferença só apareceu no log.

```bash
docker compose build android && docker compose up -d android
```

É irmã da armadilha das migrações do servidor, onde o `.sql` está embutido no
binário: **o arquivo que você editou não é o arquivo que está rodando.**

### Por que o JDK está pinado no container

O host desta máquina tem **openjdk 25**, e nenhum AGP aceita. Não é novidade: o
`clients/README.md` já mandava exportar o `JAVA_HOME` do Android Studio pelo
mesmo motivo. O container resolve de vez — ele traz JDK 21, que é o que o AGP 9
pede e o Gradle 9 suporta.

### Instalar no aparelho

O container roda em `network_mode: host`, então o `adb` dele enxerga a mesma rede
que você. **Nunca foi exercitado com um aparelho de verdade** — o `adb` sobe e
responde, mas nenhum celular foi plugado aqui.

```bash
# por USB, com depuração USB ligada no aparelho
docker exec odeon-android adb devices
docker exec odeon-android ./gradlew :app:installDebug

# por rede
docker exec odeon-android adb connect 192.168.0.42:5555
```

O debug instala como `dev.odeon.android.debug`, com sufixo — ele **convive** com
uma versão de verdade no mesmo aparelho em vez de substituí-la.

---

## A sala: o `:tv`, numa TCL com Google TV

**Estado: instalado e rodando numa TCL Smart TV Pro (Android 14), conferido tela
a tela em 12/08/2026 — login, as seis abas com o acervo de verdade, a ficha da
obra, e um filme tocando em `direct_play`.**

O que falta está na última seção, e é curto: HLS, a fileira na home aparecendo, a
busca por voz, e a troca de faixa.

### ⚠️ O que o aparelho encontrou em 20 minutos, e o build não encontrou nunca

Esta é a seção mais útil deste arquivo, e ela existe pra justificar a régua do
projeto — «vistas rodando em aparelho, tela a tela, não só compiladas». Os três
defeitos abaixo passaram por `assembleDebug`, por `lintDebug` e pelos 155 testes
sem um aviso:

| | o que se viu na TCL | onde estava |
|---|---|---|
| **o D-pad não saía do primeiro campo** | ▼▼▲ e o foco parado no mesmo retângulo. Usuário, senha e o botão **inalcançáveis** — a tela de login não dava pra usar | `BasicTextField` consome as setas verticais pro cursor, mesmo em `singleLine`. Conserto em `ui/Campo.kt` |
| **o teclado cobria a senha** | o IME da TCL ocupa a metade de baixo; o campo ficava atrás dele — e o comentário do arquivo **afirmava o contrário** | `imePadding()` + `verticalScroll`, mais `adjustResize` e `setDecorFitsSystemWindows(false)` |
| **o letreiro quebrava no meio da palavra** | `o acerv / o da / casa` — a coluna tinha 184dp e a fonte, 56sp | largura fixa e as quebras escritas à mão, em `TelaDeLoginDaTv.kt` |
| **o foco ficava no endereço depois de achar** | a procura dava certo e o passo seguinte custava fechar o teclado, descer e ele reabrir | um `LaunchedEffect(servidorConfirmado)` que salta pro usuário |

O primeiro é o que importa: ele não é um defeito de aparência, é o app **sem
saída**. E é invisível num celular por um motivo estrutural — lá não há setas, o
dedo escolhe o campo. Nenhum teste de unidade tem como encontrá-lo, e nenhuma
leitura de código encontrou.

Cada um está anotado no arquivo onde foi consertado, com o número medido junto.

### A segunda leva de defeitos, com sessão de verdade

Com o login feito, o app inteiro abriu — e o aparelho cobrou mais seis. Nenhum
deles aparece num build verde:

| | o que se viu na TCL | conserto |
|---|---|---|
| **o trilho era inalcançável** | ◀ no primeiro cartão ia pro **último da mesma fileira** e travava lá. Cinco das seis telas não tinham como ser abertas pelo controle | desvio explícito no item mais à esquerda — `saidaPraEsquerda`, em `ui/Pecas.kt` |
| **a home da TV ficava vazia** | `SecurityException: Selection not allowed for content://android.media.tv/preview_program` — o `TvProvider` recusa cláusula `WHERE` | apagar por URI de canal e linha a linha, em `home/CanalDaHome.kt` |
| **o título invadia o pôster** | a escala de foco crescia **só a arte**, 12% por cima das duas linhas de texto logo abaixo | a escala passou pro cartão inteiro |
| **os ícones do trilho saíam errados** | `◈ ✦ ☺` viraram triângulos e um arco cortado: a fonte da TCL não tem esses glifos | vetores — os mesmos `ic_aba_*.xml` do celular |
| **a sinopse saía do véu** | a coluna de texto era `1050dp` numa tela de **960dp** — mais larga que a TV | fração única, dividida com o degradê |
| **«voltar» na ficha saía do app** | relatado pelo dono: a tecla caía na home da Google TV em vez de voltar pra biblioteca | havia **um** `BackHandler` no módulo inteiro, no player. Agora são quatro |

O último merece nota à parte porque **não foi achado por mim**: foi o dono
apertando «voltar» numa ficha. Eu tinha percorrido as seis telas e o player sem
apertar aquela tecla fora do player — e o comentário do `AtividadeDaTv` afirmava
que ela era «tratada por `BackHandler` em cada tela, uma por uma», o que nunca
foi verdade. A pilha certa está escrita lá agora:

    episódios ▸ biblioteca ▸ ficha ▸ casa ▸ (aba ≠ biblioteca) ▸ sair

⚠️ O da home é o mais instrutivo dos seis: ele **só foi visto porque o
`publicar` embrulha tudo num `runCatching` que loga**. Sem aquela linha, a
fileira simplesmente não existiria e não haveria nada no `logcat` — §8b pago com
juros.

E o do trilho é o mais caro: duas correções «óbvias» falharam antes da que
funcionou, e as duas estão registradas em `ui/Pecas.kt` porque **parecem
certas** — `focusProperties { exit }` num `focusGroup`, e `left` no contêiner.

### O que já foi conferido na TCL

| | |
|---|---|
| instala, abre, não quebra | ✅ primeiro quadro em 1,7s |
| o D-pad percorre o formulário | ✅ servidor → usuário → senha → botão, conferido por `uiautomator` |
| a descoberta do servidor | ✅ contra o **servidor de verdade**, `https://odeon-api.serious-sam.dev` — a dica voltou «achei em …» |
| o esquema explícito é respeitado | ✅ escrito `https://`, ele não tentou o http, como o `EnderecoDoServidor` promete |
| a rede do `:core` na TV | ✅ OkHttp, Retrofit e TLS atravessaram inteiros |
| **as seis abas** | ✅ biblioteca (8.316 obras), locadora, mural, guia, para você, perfil — todas com dado de verdade |
| o D-pad no conteúdo | ✅ trilho ⇄ grade ⇄ fileiras, conferido por `uiautomator` |
| a ficha da obra | ✅ selo `direto`, etiquetas, cenas, e «continuar de 2min» vindo do `ondeContinuar` |
| **o filme toca** | ✅ `direct_play`, retomado em 2min, `state=PLAYING`, sem erro |
| o cromo do player | ✅ some sozinho, e ◀▶ pulam 10s **sem** acender a interface |
| a sessão de mídia | ✅ o sistema achou (`findMediaButtonSession OK`) — o ▶ do controle chega ao player |
| `fazQuantoTempo` | ✅ o mural escreveu «há 3 dias» com o dado real |
| a home do Google TV | ⚠️ publica sem erro depois do conserto; a fileira em si **não foi vista** — o canal precisa de um aval do sistema que se dá com o controle |

### ⚠️ O custo de desenho da caixa 3D na TCL — medido

A §10 do redesenho dizia «não sei o custo de desenho na TCL». Agora sabe, e o
número reprova **a prateleira**, não a peça.

A prateleira da locadora do `:tv` foi trocada por `CaixaEm3D` em tamanho de sala
e medida com `dumpsys gfxinfo` — catorze `▶` a 0,45s dentro de **uma fileira
só**, pra não medir carregamento de imagem. O mesmo gesto na biblioteca, que tem
cartaz plano, serviu de controle:

| | quadros | jank | **50º percentil** |
|---|---|---|---|
| biblioteca — cartaz plano | 47 | 44,7% | **42ms** |
| estante — caixa 3D | 37 | **100%** | **200ms** |

200ms é 5 fps. E `Slow bitmap uploads` deu **zero** contra `Slow UI thread` 37 de
37: não é rede nem pôster, é composição — seis faces por caixa, cada uma com um
`BoxWithConstraints`, vezes quatro caixas na tela.

⚠️ **Isso não é argumento contra o `:cenario`.** Uma cópia da caixa dentro do
`:tv` seria exatamente igual de lenta: o custo é da peça, não da fronteira de
módulo. A prateleira voltou ao cartaz plano, e a caixa 3D reaparece na T3 **no
palco**, onde é uma só na tela — número que ainda não foi medido.

O que **funcionou** é igualmente medido: a caixa a três metros ficou boa, e a
lombada passou a ser legível de verdade — título, tarja dourada, miniatura e
`2024 · DVD`. O comentário do `TelaDaLocadoraDaTv` afirmava o contrário («a
distância de três metros achata») e nunca tinha sido visto. Está corrigido lá.

### A T1: o trilho virou a cabine de projeção — 12/08/2026

A leva 1 do redesenho da TV, vista rodando na TCL. O trilho deixou de ser seis
ícones com uma barrinha dourada:

| | |
|---|---|
| **o retrato**, no topo | avatar, anel de progresso e selo do nível — a `Insignia` do `:cenario`, a **mesma** que o celular desenha |
| **a busca**, como ícone | abre a busca do sistema. §5.1: «digitar com D-pad é soletrar» |
| **a lente** | o destino escolhido ganhou um disco de luz com halo no lugar da barrinha de 3dp — o mesmo objeto que corre sobre a película do player |
| **o feixe** | abre da lente pra direita, com poeira em suspensão, atrás do conteúdo |
| **a piscada** | os dez quadros do arco, do `:cenario`, disparados pela **troca de destino** e não pelo foco |

⚠️ **O que o aparelho cobrou está na §10.3 do redesenho, e são quatro coisas** —
o retrato nasceu vazio, a poeira virou um campo de estrelas sobre os pôsteres, o
perfil escolhido ficava sem lente nenhuma, e a busca não foi vista funcionando.
As três primeiras estão consertadas; a quarta está isolada e é do aparelho.

O D-pad foi percorrido item a item com `uiautomator`: os **sete** alcançáveis, e
para nas duas pontas. Não é dedução de build verde — é o defeito mais caro que
este módulo já teve, e ele ganhou dois itens novos nesta leva.

### A T2: a cortina abre a sessão — 12/08/2026

A leva 2, vista rodando na TCL:

| | |
|---|---|
| **a cortina** | as lâmpadas piscam, o pano vermelho aparece com o nome do filme, e abre. A §6.1 chama de «a peça de maior retorno do documento», e no escuro com a tela inteira ela é o que o documento prometeu |
| **a película** | a `Tira` do tamanho da sala — cenas de verdade, perfurações, o já visto em cor cheia, a lente sobre o ponto atual |
| **o 10/30** | era ±10; agora é assimétrico, como o celular |
| **o ponto do plano** | a pílula «transcodificando» virou um ponto colorido ao lado do título |

O cromo foi refeito depois que o dono o viu: fileira centralizada, o ▶/⏸ clássico
com o disco dourado do `:cenario`, o `10s` à esquerda do play e o `sair` no canto
do rodapé. Junto entraram um defeito consertado — passar o foco pelo botão de
salto **já buscava**, e o foco nem andava — e a película que aparece sozinha ao
buscar com o cromo apagado, pra acompanhar sem tapar o filme. Ver §10.5.

⚠️ **Uma coisa da §6.2 não saiu: a lente ficar parada no centro com a película
correndo por baixo.** Duas tentativas foram medidas na TCL e as duas falharam —
a primeira porque `Modifier.width` é preferência e não ordem (a lente saiu 240px
fora do centro), a segunda sem diagnóstico fechado. Fazer sair pede mudar a
`Tira` por dentro, e a T0 decidiu que as peças se movem e não se reescrevem.
Está na §10.4 do redesenho como pendência **medida**.

### A T3: a locadora virou uma loja — 12/08/2026

| | |
|---|---|
| **a fachada** | arandela de latão com facho, `locadora` em serifada acesa, `ACERVO DA CASA` em versalete — e **nenhum título de tela**, que é o ponto da §5.2 |
| **as plaquinhas** | penduradas por pino e fio, tortas pra lados opostos, com a contagem escrita à mão |
| **as estantes** | madeira com lábio iluminado, etiqueta de papel colorido presa com fita, `8 de 179` no canto |
| **a caixa no palco** | escolher **pega a caixa na mão**; `◀ ▶` giram, `OK` abre |
| **o trilho** | vira silhueta na locadora, e a marquise da loja assume a luz |

O `Cenografia.kt` do celular atravessou inteiro pro `:cenario` — 402 linhas, um
import de `material3`, medido antes de mover. A `Tabua` veio junto, promovida de
`private`: uma prateleira de madeira não é do celular nem da TV, é do lugar.

⚠️ **O custo da caixa no palco foi medido**: 97ms por quadro girando, contra 200
das oito na prateleira e 42 do cartaz plano. O dono viu e aprovou o giro. Está na
§10.9 com o número e o que ele não resolve.

### A T4: a biblioteca ganhou teto e primeira dobra — 13/08/2026

| | |
|---|---|
| **a marquise** | fileira de lâmpadas douradas no **teto da tela**, borda a borda — a terceira aparição do projetor |
| **o herói** | arte 16:9 na primeira dobra, título em serifada, `faltam 24min`, barra de progresso — e ele **encolhe** de 306dp pra 104 ao descer, virando cabeçalho fino em vez de sumir |
| **as contagens** | `60 de 8.316`, carregado dourado e total apagado; `CONTINUAR ─── 17` com o número na ponta da régua |

⚠️ **A marquise tem outro compasso na sala.** No celular a faixa de luz corre sem
parar; aqui ela pisca **uma vez** na entrada — com os dez quadros do arco, os
mesmos do facho — e depois respira. A §5.1 chama a alternativa de «epilepsia, não
identidade», e a fileira aqui atravessa 1920px numa tela que fica aberta.

⚠️ Dois erros meus, os dois pegos em foto: o separador de milhar saía do `Locale`
do aparelho e virou `8,316` numa TV em inglês — em português isso se lê «oito
vírgula três» —, e o herói era item da grade e **rolava pra fora** em vez de
encolher. Estão na §10.12 do redesenho.

### O que ainda falta ver em aparelho

- **HLS**. O que foi visto tocar foi `direct_play`. O caminho de transcodificação
  — e com ele o `deslocamentoMs`, que é a parte mais sutil do `ModeloDoPlayer` —
  ainda não passou por uma TV. Precisa de uma obra que o servidor recuse servir
  direto.
- **a fileira na home**. Ela publica sem erro; falta ver o cartão aparecendo na
  primeira tela, e o aval que a Google TV pede pra deixar um canal visível.
- **a busca por voz**. O provedor está no manifesto e responde; ninguém segurou o
  microfone do controle ainda.
- **troca de faixa de áudio e legenda**. O filme conferido tem uma faixa só, e
  por isso o botão de áudio nem nasceu (§53) — o que é o comportamento certo, e
  também significa que aquele caminho não foi exercitado.

### Como está dividido, e por que agora são **quatro** módulos

```
:core    → o que não desenha: `dados/`, os dez `Modelo*.kt`, a paleta, a Serifada
:cenario → o que desenha e não é de nenhum aparelho: a caixa 3D, a película,
           a cortina, o facho, a projeção, as tintas da capa
:app     → o celular e o tablet: toque, PiP, download, Cast, haptics
:tv      → a sala: D-pad, foco explícito, dez pés, e a home do Google TV
```

O `:core` é a promessa que o `settings.gradle.kts` fazia por escrito — «quando
houver um `:tv`, o `:core` nasce dessa necessidade e não da previsão dela» —
sendo paga. E ela saiu barata porque foi esperada: medido **antes** de mover,
`dados/` e os dez `Modelo*.kt` tinham **zero** imports de Compose de UI. Foi um
`git mv` e uma linha de dependência.

O `:app` inteiro sobreviveu à mudança com **duas** linhas alteradas, e as duas
pelo mesmo motivo — o Kotlin recusa *smart cast* de propriedade pública vinda de
outro módulo. Está anotado em `TelaDoPlayer.kt`.

#### O `:cenario`, a segunda extração — 12/08/2026

Ele é a **T0** do [`docs/REDESENHO-TV.md`](docs/REDESENHO-TV.md), e nasceu de um
alvo concreto: o `:tv` precisa da caixa 3D, da película, da cortina e do facho, e
os quatro moravam no `:app`.

A divisão anterior separou por **quem desenha**. Esta separa por **pra quem se
desenha** — e o achado é que há coisa que desenha e não é de nenhum dos dois
aparelhos. Uma caixa de VHS em três quartos é a mesma caixa a trinta centímetros
e a três metros; o que muda é o tamanho e como se aponta pra ela.

⚠️ **Ela saiu mais cara que a primeira, e o número está medido.** A do `:core`
foi `git mv` e uma linha. Esta foi:

| | |
|---|---|
| 11 arquivos movidos inteiros | `CaixaEm3D`, `Palco`, `Contracapa`, `TintasDaCapa`, `Projecao`, `Tira`, `Cortina`, `Insignia`, `Animacao`, `Chegada`, `Midia` |
| 1 arquivo **partido** | `Facho.kt` — a luz foi pro `Arco.kt`; a `BarraDoFacho`, que é a barra de baixo do celular, ficou |
| 1 função **extraída** | `FaceDaCaixa` (+ dois ajudantes), 580 linhas que estavam `internal` dentro da tela da locadora |
| 25 chamadas de `material3` | quase todas `Text`, resolvidas por um invólucro (`Texto.kt`) e não por 21 reescritas |

A `Serifada` desceu junto pro `:core`, e com ela morreu a `SerifadaDaSala` do
`:tv` — um segundo `FontFamily` sobre o **mesmo** `.ttf`, que o comentário dele
já temia em voz alta. O `Tipo` **não** desceu, e é decisão: 11sp de rótulo a três
metros não é um rótulo discreto, é um rótulo ilegível.

⚠️ **O celular atravessou sem uma diferença de desenho**, e isso foi conferido
por comparação de pixel e não por leitura: locadora, palco e perfil, antes e
depois, subtraídos. Palco **0**, perfil **0**, locadora 3.448 — dos quais 3.414
são o selo mostrando `3` em vez de `2`, porque o nível subiu durante a sessão.

O caminho até esse zero está na §10.2 do redesenho, e vale a leitura: **quatro**
erros seguidos no invólucro de texto, todos com build verde, 155 testes passando
e lint limpo. Os quatro foram achados subtraindo screenshots.

### Compilar e instalar

```bash
docker exec odeon-android ./gradlew :tv:assembleDebug
docker exec odeon-android ./gradlew :core:testDebugUnitTest :tv:lintDebug
```

O APK sai em `tv/build/outputs/apk/debug/tv-debug.apk` — **18,7 MB**, contra 22,9
do celular. A diferença é o que a sala não carrega: Glance, Cast e o Media3 de
download.

⚠️ **O Palette saiu desta lista em 12/08/2026**, e a linha dizia o contrário até
então. Ele entrou no `:cenario` junto com a `TintasDaCapa`, que é quem tira as
duas cores da lombada da capa — sem ele a caixa da estante volta a ser cinza de
interface. São ~60 KB, e é o que fez o APK ir de 18 para 18,7.

Numa TCL não há porta USB pra depuração, então o caminho é **adb pela rede**:

1. na TV, `Ajustes › Sistema › Sobre` e aperte **sete vezes** em «Versão do
   build» — é o que liga as opções de desenvolvedor
2. `Ajustes › Sistema › Opções do desenvolvedor` → ligue **Depuração por USB**
   (o nome mente: numa TV é ela que abre a porta 5555 da rede)
3. anote o IP em `Ajustes › Rede`

```bash
docker exec odeon-android adb connect 192.168.0.50:5555
# a TV mostra um diálogo de autorização — aceite nela, com o controle
docker exec odeon-android ./gradlew :tv:installDebug
```

O app aparece na fileira de apps da home como **Odeon**, com o banner deitado —
e não na gaveta de apps do celular, porque o que ele declara é
`LEANBACK_LAUNCHER` e não `LAUNCHER`.

O debug instala como `dev.odeon.android.tv.debug`. O id de produção é
`dev.odeon.android.tv`, e **não** `dev.odeon.tv`: esse último já é do app de TV
do `clients/`, o do Kotlin Multiplatform, e reusá-lo faria os dois brigarem pela
mesma instalação.

### O que a sala tem que o celular não tem

| | |
|---|---|
| **a fileira na home** | «continuar assistindo» aparece na **primeira tela** da TV, junto do que o resto dos apps deixou pela metade. É o `WatchNext` do sistema, não uma fileira nossa |
| **o canal do app** | uma fileira «Odeon» que a pessoa pode fixar |
| **a busca por voz** | segurar o microfone do controle e falar «Alien» acha no acervo — sem digitar, que numa TV é o ponto inteiro |
| **o controle remoto** | play, pause e avanço do controle chegam ao player pela sessão de mídia |

### O que ela deliberadamente não tem

| | por quê |
|---|---|
| **baixados** | a TV está sempre na rede que serve o filme. Uma aba que abre sempre vazia é pior que aba nenhuma (§24) |
| **Cast** | mandar pra TV, **da** TV |
| **a caixa em três quartos da locadora** | ela existe pra ser **pegada** — o dedo a levanta e o aparelho vibra. Sem mão e sem giroscópio, sobra um desenho bonito que a distância de três metros achata |
| **PiP** | é o motivo de o `minSdk` ser 26 no celular, e não tem equivalente aqui |

### ⚠️ A dívida que já se sabe

As artes da fileira da home carregam `?token=` na URL, e quem baixa a imagem
**não é este app** — é o processo do launcher, dias depois. Quando o token de
mídia rodar, as artes já publicadas passam a devolver 401 e a fileira fica com os
retângulos vazios.

O paliativo está feito: republicar a cada abertura do app reescreve as URLs com o
token da vez. O conserto de verdade é do servidor — uma rota de arte com token
longo, de leitura só — e está em `docs/PEDIDOS-AO-SERVIDOR.md`.

---

## O que tem aqui

São **três** módulos desde 12/08/2026 — ver «A sala» acima pro porquê. A árvore
abaixo é a do `:app`; o que dela foi pro `:core` está marcado com `→ :core`.

```
core/src/main/kotlin/dev/odeon/android/    tudo que não desenha
  dados/                    o contrato, a rede, o cofre, o barramento
  ui/Cores.kt               a paleta da casa, dividida com a TV
  ui/{Medida,Virada,Cor}.kt as contas que as duas telas fazem igual
  ui/**/Modelo*.kt          os dez ViewModel, inclusive as 914 linhas do player
core/src/main/res/font/     a serifada de display, embutida (ver abaixo)

tv/src/main/kotlin/dev/odeon/android/tv/   a sala
  OdeonTv.kt                o Application da TV
  AtividadeDaTv.kt          a única Activity, e o `when` de onde se está
  ui/Sala.kt                overscan, escala de foco, a tipografia de 10 pés
  ui/Foco.kt                o vocabulário do D-pad: escala, halo, texto que acende
  telas/                    login, trilho, as seis abas, a ficha
  player/                   o cromo por D-pad, sobre o mesmo ModeloDoPlayer
  home/                     o canal e o «continuar» na home do Google TV
  busca/                    o Odeon dentro da busca por voz do sistema

app/src/main/kotlin/dev/odeon/android/     o celular e o tablet
  OdeonApp.kt               o Application: guarda o Cofre e o Repositório
  AtividadePrincipal.kt     a única Activity, e a intenção é que continue sendo
  dados/
    EnderecoDoServidor.kt   "rog" vira https://rog:8443, depois http://rog:8080
    Cofre.kt                DataStore: o servidor e os dois tokens
    Modelos.kt              o contrato, escrito à mão e conferido contra o Rust
    OdeonApi.kt             as 5 rotas (de 113) que a fase 1 fala
    Rede.kt                 UM OkHttp, e o porquê disso
    RepositorioOdeon.kt     procurar servidor, entrar, listar
                            ⬆ os cinco foram todos pro `:core`
  ui/
    Tema.kt                 a escala tipográfica do celular (a paleta → :core)
    Rotulo.kt               RotuloDeSecao: versalete, régua, número à direita
    AppOdeon.kt             a raiz: as quatro abas, e quem fica fora delas
    login/                  tela + modelo
    biblioteca/             tela + modelo
app/src/test/…              os testes das telas que ficaram aqui
app/lint.xml                o que o lint pode calar, e por quê
gradle/libs.versions.toml   as versões, todas medidas
Dockerfile                  a caixa de ferramentas
docker-entrypoint.sh        instala o SDK no primeiro `up`
```

### As três decisões da fase 1 que ficam caras de desfazer

**1. Um OkHttp só.** O Retrofit, o Coil e — na fase 2 — o Media3 usam a mesma
instância. Um pool de conexões, um cache, um lugar onde o `Authorization` é
posto. Dois clientes fariam o player abrir conexão própria, e aí o token de
mídia teria duas contabilidades — que é onde o §43 morde.

**2. O token de mídia não se renova sozinho.** Emitir um novo aposenta o
anterior, e o anterior pode estar dentro de um player tocando. O interceptor
deixa 401 subir como 401; quem decide renovar é quem sabe se há filme no ar.

**3. A tela de login é em dois tempos.** Os campos de conta só aparecem depois
que `/api/auth/status` respondeu naquele endereço. É o §53 — o produto não
oferece o que a validação vai negar —, e de quebra evita mandar a senha da casa
pra qualquer coisa que atenda naquela porta.

---

## A fonte embutida, e o que ela custou de verdade

O `docs/REDESENHO.md` estimou «~200 KB por peso». Medido em 04/08/2026:

| | |
|---|---|
| o `.ttf` no disco | **739.428 bytes** — Noto Serif SemiBold, Latino+Grego+Cirílico, hinted |
| o mesmo arquivo **dentro do APK** | **360.349 bytes** (o DEFLATE tira 51%) |
| o APK de depuração inteiro | 21.569.757 bytes |
| ou seja | **1,7%** do APK |

O número que importa é o do meio, não o do disco — e a estimativa errou por
quase o dobro nos dois sentidos ao mesmo tempo: o arquivo é 3,7× maior do que o
chute, e o custo real é 1,8× o chute.

Se um dia incomodar, o caminho é recortar o charset pra Latin-1 (`pyftsubset`),
que derruba pra ~100 KB. Não foi feito porque 1,7% não paga a ferramenta a mais
no build — e porque um título em cirílico cairia na sem-serifa sem avisar.

⚠️ **Um peso só.** Cada peso adicional é outro arquivo inteiro. A fonte variável
resolveria em um, mas `FontVariation` é de API 26 — exatamente o `minSdk` deste
app —, e nascer colado no piso da versão é onde mora o defeito que só aparece no
aparelho mais velho.

---

## As versões, e como remedi-las

Tudo em `gradle/libs.versions.toml` foi **medido** em 04/08/2026, não lembrado:

| | |
|---|---|
| AGP | 9.3.1 |
| Kotlin | embutido no AGP 9 |
| Gradle | 9.6.1 |
| `compileSdk` / `targetSdk` | **37.1** |
| `minSdk` | **26** — decidido na espec (§4): é onde mora o PiP |
| Media3 | 1.10.1 (declarado, ainda não ligado) |

Pra remedir:

```bash
# AGP, Kotlin, Media3, AndroidX
curl -s https://dl.google.com/dl/android/maven2/com/android/tools/build/gradle/maven-metadata.xml \
  | grep -o '<version>[^<]*</version>' | tail -5

# Gradle
curl -s https://services.gradle.org/versions/current

# as plataformas e ferramentas do SDK
docker exec odeon-android sh -c 'sdkmanager --sdk_root=$ANDROID_HOME --list' | grep platforms
```

⚠️ **Ao procurar plataforma, não procure `android-<inteiro>`.** O Android tem
versões menores agora — 36.1, 37.0, 37.1, 37.2-beta. Um levantamento que só
casa inteiros acha a 36 e conclui que ela é o teto. Foi o que aconteceu aqui, e
quem denunciou foi o build: o AndroidX de hoje recusa compilar contra menos que
37.

---

## Três coisas que já custaram um build cada

Estão documentadas no arquivo onde acontecem; ficam listadas aqui porque são o
tipo de coisa que se repete.

**1. `--` dentro de comentário XML é proibido.** As variáveis do CSS
(`--bg`, `--accent`) escritas ao natural fazem o `mergeResources` falhar com um
`SAXParseException`. Em `res/values/colors.xml` elas aparecem sem os hifens.

**2. Declarar `leanback`, mesmo com `required="false"`, diz que o app suporta
TV** — e o lint então cobra a Activity de TV que a espec proibiu. Pra não ser
app de TV, o certo é o silêncio: não declarar nada. Ver o comentário no
manifesto.

**3. O conselho do lint sobre `mipmap-anydpi-v26` não compila.** Ele manda tirar
o `-v26`; sem ele o merge de recursos descarta a pasta e o AAPT reprova com
`resource mipmap/ic_launcher not found`. Silenciado por caminho em
`app/lint.xml`, com o experimento descrito.

---

## A decisão que vale antes da primeira linha do player

**A UI se escreve contra a interface `Player` do Media3, nunca contra
`ExoPlayer`.**

O `media3-cast` entrega um `CastPlayer` que implementa a mesma interface:
escrito assim, mandar pra TV (fase 4) é trocar a instância. Escrito contra
`ExoPlayer`, cada tela que toca o player vira reescrita.

Espec §4c. Está repetido no `libs.versions.toml`, ao lado das dependências do
Media3, que é onde quem for buscá-las vai passar.

---

## O que ainda está em aberto

- **O ícone.** O carretel em `res/drawable/ic_launcher_foreground.xml` é
  provisório — a espec (§7) deixou a identidade em aberto e só o nome foi
  fechado.
- **Publicação.** Play Store ou APK assinado por link (§7).
- **R8/minify no release.** Desligado, com o motivo escrito em
  `app/build.gradle.kts`.
- **Rodar.** Nenhuma tela deste app foi vista por olho humano. Ver a seção
  abaixo.

  O que está provado da fase 1: compila, 10 testes passam, lint sem achado, e o
  contrato foi conferido contra o servidor de verdade onde dá sem senha
  (`/api/auth/status` devolve `{"needs_setup":false}`, `/api/library` sem sessão
  nega com 401, e o `:8443` não responde — que é o caso que o "https primeiro"
  existe pra cobrir).

  O que **não** está provado: que o login funciona de ponta a ponta, e que a
  resposta real de `/api/library` desserializa nos modelos. As duas coisas
  precisam de uma senha, e nenhuma delas é verificável sem rodar.

---

## ⚠️ O emulador não sobe no `serious-server`

Registrado para quem tentar de novo não refazer o caminho.

O emulador **37.1.11** morre com `Segmentation fault (core dumped)` sempre no
mesmo ponto: ~30–40s depois de `Emulator is performing a full startup`, com o
boot do Android já em curso. Sete configurações, mesmo resultado:

| variação | resultado |
|---|---|
| `-gpu swiftshader_indirect` | segfault |
| `-gpu swiftshader` | segfault |
| `-gpu guest` | segfault |
| imagem `android-37.1;google_apis_ps16k;x86_64` | segfault |
| imagem `android-36;google_atd;x86_64` (a de CI, enxuta) | segfault |
| `--security-opt seccomp=unconfined` | segfault |
| Xvfb com display virtual `:99` | segfault (chegou a criar a janela) |

**O que foi descartado, com medida:**

- **não é OOM** — `oom_kill 0` no cgroup do container, e ele roda sem limite de
  memória
- **não é KVM** — `/dev/kvm` existe no host em `crw-rw-rw-`, o processador tem
  `vmx`, e o dispositivo chega gravável dentro do container
- **não é a imagem de sistema** — duas falharam igual, incluindo a ATD, que é
  feita exatamente pra emulador headless de CI
- **não é a GPU** — três modos, incluindo o que renderiza dentro do convidado
- **não é o seccomp** — testado num container com o perfil desligado

O que sobra é o emulador contra este ambiente: kernel **7.1.5**, que é bem novo.
Sem `dmesg` acessível não deu pra ir além.

### Dois defeitos que apareceram no caminho, e que valem para qualquer container

**1. O `avdmanager` escreve o caminho da imagem errado aqui.** Ele deduz a raiz
do SDK subindo dois níveis do próprio binário — e neste `Dockerfile` o
`cmdline-tools` mora **fora** do `ANDROID_HOME` (senão o volume o mascara), em
`/opt/cmdline-tools/latest/bin`. Ele conclui que a raiz é `/opt` e escreve
`image.sysdir.1=android-sdk/system-images/…`, que o emulador resolve como
`/opt/android-sdk/android-sdk/…`:

```
FATAL | Broken AVD system path. Check your ANDROID_SDK_ROOT value
```

O conserto é reescrever a linha no `config.ini` depois de criar o AVD.

**2. `nohup … &` dentro de `docker exec` não sobrevive.** O Docker derruba a
árvore de processos quando a sessão do `exec` termina; `nohup` protege de
SIGHUP, não disso. O sintoma engana: o emulador loga "full startup", o comando
volta com código 0, e meio minuto depois não há processo nenhum, sem nada no log
dizendo que morreu. `setsid`, ou `docker exec -d`.

**E o segfault ficou escondido três rodadas** porque ele sai no stderr do shell,
não no log formatado do emulador — quem lê só o `emulador.log` vê a última linha
"full startup" e conclui que ele ainda está subindo.

### Quem roda, então

Outro computador, que já tem emulador funcionando e está na mesma tailnet. Ver
[`../docs/CONTINUAR-ANDROID.md`](../docs/CONTINUAR-ANDROID.md).

Esta máquina continua compilando, testando e passando o lint — e faz as três em
23 segundos. É o que o container faz bem, e ele não carrega mais nada de
emulador por isso.

---

## CI

O `.github/workflows/build.yml` tem dois trabalhos: `typecheck-e-build` pra
`web/` e **`app-android`** pra este projeto, rodando `assembleDebug`,
`testDebugUnitTest` e `lintDebug` a cada push e PR.

Quando o lint reprova, o relatório HTML sobe como artefato — é onde está o
trecho de código, que o log não traz.

O `clients/` continua fora: está parado no M2, e montar Android e iOS a cada
push custaria esteira pra guardar código que não muda.
