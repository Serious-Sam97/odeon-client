# Odeon — o app Android

Kotlin + Jetpack Compose, nativo, só Android. Um projeto Gradle próprio, irmão
de `web/` e `clients/`.

> **A espec está em [`../docs/APP-ANDROID.md`](../docs/APP-ANDROID.md).** Ela tem
> as decisões todas, com o porquê de cada uma e a sequência em sete fases. Este
> arquivo é só como rodar.
>
> **Os pedidos pendentes ao servidor estão em
> [`docs/PEDIDOS-AO-SERVIDOR.md`](docs/PEDIDOS-AO-SERVIDOR.md)**, no formato da
> §1b — prontos pro dono levar. São dois, e nenhum bloqueia o app.
>
> **O redesenho está em [`docs/REDESENHO.md`](docs/REDESENHO.md)** — a proposta
> de fases pra o app deixar de ser funcional-e-plano e passar a se parecer com o
> Odeon, com o que só dá pra fazer no celular. É proposta, não plano aprovado.

**Estado: a v1 inteira está feita, o redesenho R1–R9 entrou, e o segundo
redesenho — «dar vida» — entrou nas três levas.**

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

⚠️ **A régua de fps da R4 ficou sem resposta.** Ela manda tirar o enfeite se a
rolagem sair de 60fps no emulador, e o emulador não segura 60fps nesta grade
**com ou sem** enfeite — a variância entre execuções ficou maior que a diferença
entre as versões. Precisa de aparelho de verdade ou de `androidx.benchmark`. Os
números estão no `docs/REDESENHO.md` §0 e no comentário do `Cartaz`.

`assembleDebug` ✅ · **29 testes** ✅ · `lintDebug` limpo ✅

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

## O que tem aqui

```
app/src/main/kotlin/dev/odeon/android/
  OdeonApp.kt               o Application: guarda o Cofre e o Repositório
  AtividadePrincipal.kt     a única Activity, e a intenção é que continue sendo
  dados/
    EnderecoDoServidor.kt   "rog" vira https://rog:8443, depois http://rog:8080
    Cofre.kt                DataStore: o servidor e os dois tokens
    Modelos.kt              o contrato, escrito à mão e conferido contra o Rust
    OdeonApi.kt             as 5 rotas (de 113) que a fase 1 fala
    Rede.kt                 UM OkHttp, e o porquê disso
    RepositorioOdeon.kt     procurar servidor, entrar, listar
  ui/
    Tema.kt                 a paleta e a escala tipográfica, ambas da web
    Rotulo.kt               RotuloDeSecao: versalete, régua, número à direita
    AppOdeon.kt             a raiz: as quatro abas, e quem fica fora delas
    login/                  tela + modelo
    biblioteca/             tela + modelo
app/src/main/res/font/      a serifada de display, embutida (ver abaixo)
app/src/main/assets/        a licença OFL, que a fonte exige viajar junto
app/src/test/…              os testes do EnderecoDoServidor
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
