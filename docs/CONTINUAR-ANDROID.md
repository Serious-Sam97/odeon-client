# O app Android continua noutro computador

Escrito em **04/08/2026**, no `serious-server`, para quem for pegar o app Android
daqui em diante — provavelmente uma sessão sem nenhum contexto desta conversa.

Leia inteiro antes de escrever a primeira linha. Ele economiza um dia.

---

## 1. Por que este documento existe

O app Android foi escrito aqui até a fase 1 e **nunca foi visto rodando**. O
emulador segfalta nesta máquina, em sete configurações diferentes — o
diagnóstico completo está em [`../android/README.md`](../android/README.md).

O outro computador já tem emulador funcionando e está na mesma tailnet, e passa a
ser **o dono do app Android**.

E a razão de o corte valer a pena está na lição mais cara deste projeto: **o
screenshot achou defeitos que o código não denunciava.** Só numa rodada: um
pôster que mudou de destino e esqueceu a classe, uma barra de progresso duplicada
que já existia, e uma contagem que só apareceu errada numa foto.

Um app que compila, passa em 10 testes e passa no lint continua sendo um app que
ninguém viu.

---

## 1b. ⚠️ Quem mexe em quê — leia isto antes de editar qualquer arquivo

O corte é por **pasta**, não por tarefa, e é o que impede as duas máquinas de se
atropelarem.

| | você (o outro computador) | `serious-server` |
|---|---|---|
| `odeon-client/android/` | ✅ **é seu, inteiro** | não toca |
| `odeon-client/web/` | ❌ | ✅ |
| `odeon-client/clients/` (KMP) | ❌ parado, não apagar | ✅ |
| `odeon-server/` | ❌ **nada, nunca** | ✅ |
| o banco, a identificação, as migrações | ❌ | ✅ |

**Você mexe no app Android e mais nada.** O servidor, a interface web e o KMP
continuam sendo trabalho do `serious-server` — a máquina onde eles rodam, onde
está o Postgres com dados reais de três pessoas, e onde as migrações são
compiladas dentro do binário.

### E quando o app precisar de uma mudança no servidor?

Vai acontecer, e a espec já sabe de duas:

- **o CORS**, quando o Cast chegar — a origem do Chromecast não é o host do
  servidor, e provavelmente vai precisar de `ODEON_ALLOWED_ORIGINS` (§4c)
- **a porta padrão**, se decidirem que o app deve tentar 8085 em vez de 8080

Quando isso aparecer: **descreva o que precisa e devolva o pedido pro
`serious-server`.** Não abra o `odeon-server` pra "só ajustar uma linha" — as
migrações são embutidas no binário em tempo de compilação, o servidor está no ar
servindo três pessoas, e a identificação leva ~1h e morre se o processo
reiniciar.

Isso não é burocracia: é a regra 4 da casa aplicada a duas máquinas — **não
corrija sozinho, pergunte ou faça o que foi pedido.**

### O fluxo de trabalho

1. `git pull` antes de começar. O `serious-server` empurra `web/`, `clients/` e
   documentação pro mesmo `main`.
2. Trabalhe só dentro de `android/`.
3. Commite em português, sem atribuição a assistente, e **só quando o dono
   pedir** (ver §7).
4. Conflito em `android/` não deveria existir. Se aparecer, alguém saiu da raia.

---

## 2. O que é o Odeon

Servidor de mídia pessoal (tipo Jellyfin), rodando em casa. Backend em **Rust**
(axum + sqlx + Postgres/pgvector), interface em **React + TypeScript**, tudo em
Docker Compose. Acervo real: **17.930 obras**, **3 usuários** (`sam` é admin;
`rudney` e `gabriel` são `user`).

A tese, na frase do próprio README: **não é um catálogo de arquivos, é uma
biblioteca que te conhece.**

E tem uma camada social e de jogo por cima: locadora com caixas de VHS/DVD em
estantes 3D, empréstimo com escassez, conquistas, perfil, assistir junto.

### Os dois repositórios

Ambos **públicos**, sob **AGPL-3.0**, com CI verde.

| | no GitHub |
|---|---|
| servidor | `Serious-Sam97/odeon-server` |
| clientes (web, Android, KMP) | `Serious-Sam97/odeon-client` ← **você está aqui** |

O `docs/DESIGN.md` ficou **inteiro no servidor** (8.210 linhas, última seção
§70). É a alma do projeto: registra **por que** cada escolha foi feita, o que foi
medido, e os defeitos encontrados. As referências a `§NN` neste documento e no
código apontam pra lá.

→ https://github.com/Serious-Sam97/odeon-server/blob/main/docs/DESIGN.md

---

## 3. ⚠️ Como alcançar o servidor: o IPv4 do Tailscale

**Esta é a parte que mais custa tempo se estiver errada.**

O servidor Odeon roda no `serious-server`, e o outro computador chega nele **pela
tailnet**. Use o **IPv4 do Tailscale**, não o nome da máquina:

```
100.77.253.18
```

E a porta é **8085**:

```
http://100.77.253.18:8085
```

Ou seja, o que se digita na tela de login do app é:

```
100.77.253.18:8085
```

### Por que o IPv4 e não `serious-server`

Porque o MagicDNS depende de o resolvedor do sistema estar configurado, e dentro
de um emulador Android ele **não está** — o emulador tem o próprio DNS, e um
nome que o host resolve não é um nome que o convidado resolve. O IPv4 da tailnet
funciona nos dois.

### Por que 8085 e não a porta padrão do app

Porque `EnderecoDoServidor.kt` tem como padrão 8443 (https) e 8080 (http), que
são as portas do Odeon "de fábrica" — mas **este** servidor publica a API em
`0.0.0.0:8085`. Digitar só o IP faria o app tentar `https://…:8443` e
`http://…:8080`, e nenhuma das duas responde.

Com `100.77.253.18:8085` ele tenta `https://100.77.253.18:8085` primeiro (falha,
não há TLS), cai pra `http://100.77.253.18:8085` e entra. É por desenho: "qual
esquema?" não deveria ser pergunta pra quem só quer assistir.

**Fica em aberto** se o padrão do app deveria virar 8085. Não mude sem perguntar
— o KMP em `clients/` também usa 8080, e mudar aqui cria uma terceira convenção.

### Não use `10.0.2.2`

É o reflexo de todo mundo, e aqui está errado. `10.0.2.2` é como o emulador
alcança **a máquina que o roda** — e o servidor não está nela, está no
`serious-server`. Existe um teste com esse endereço em
`EnderecoDoServidorTest.kt`; ele testa a normalização, não este cenário.

### Confira antes de culpar o app

```bash
curl -s http://100.77.253.18:8085/api/auth/status    # {"needs_setup":false}
curl -s http://100.77.253.18:8085/api/health         # {"db":true,"status":"ok",…}
```

Se esses dois não responderem, o problema é rede ou o servidor está fora — não é
o app. O `:8443` está mapeado mas **não fala TLS**; ele não responder é o
esperado.

---

## 4. O estado do app, sem enfeite

Fica em `android/`, um projeto Gradle próprio, irmão de `web/` e `clients/`.

**A fase 1 está escrita: entrar e ver a biblioteca.**

- tela de login em dois tempos — endereço primeiro, conta depois
- grade de pôsteres paginada, vinda de `/api/library`
- `assembleDebug` ✅ · **10 testes** ✅ · `lintDebug` sem um achado ✅
- **nunca rodou**

O que está provado, e como:

| | |
|---|---|
| compila do zero | 4m02s, sem imagem, sem SDK, sem cache |
| os modelos batem com o servidor | conferidos campo a campo contra o Rust |
| `/api/auth/status` | `{"needs_setup":false}` — bate com `StatusDoServidor` |
| `/api/library` sem sessão | 401, como esperado |
| o `:8443` não responde | é o caso que o "https primeiro" existe pra cobrir |

O que **não** está provado, e é o seu primeiro trabalho:

- que o login funciona de ponta a ponta
- que a resposta real de `/api/library` desserializa nos modelos
- que qualquer coisa aparece na tela

---

## 5. As decisões já tomadas — **não re-perguntar**

A espec completa está em [`APP-ANDROID.md`](APP-ANDROID.md). Leia inteiro. O
resumo do que está fechado:

| | |
|---|---|
| plataforma | Android nativo, **do zero**. Kotlin + Compose. Sem KMP, sem iOS |
| alvo | **celular e tablet**. **Sem Android TV**, sem D-pad |
| player | **Media3/ExoPlayer** |
| `minSdk` | **26** (Android 8.0) — é onde mora o Picture-in-Picture |
| `compileSdk` / `targetSdk` | **37.1** |
| offline | **entra na v1** |
| fita vencida sem rede | **o prazo viaja com o arquivo** — para de tocar offline |
| Cast | **entra na v1** |
| pacote / módulo | `dev.odeon.android`, módulo `:app` |
| cliente HTTP | **OkHttp + Retrofit** |
| onde ficam os tokens | **DataStore, em claro** |
| o KMP em `clients/` | fica parado, **não apagar** |

### A sequência (§5 da espec)

1. **entrar e ver a biblioteca** ← escrita, não rodada
2. **assistir** (com PiP e sessão de mídia dentro)
3. continuar de onde parou
4. **Cast**
5. a locadora
6. baixar pra ver sem rede
7. para você

Mural, guia, ao vivo, perfil e admin ficam pra depois da v1.

### A decisão que vale desde a primeira linha do player

**A UI tem que ser escrita contra a interface `Player` do Media3, nunca contra
`ExoPlayer`.**

O `media3-cast` entrega um `CastPlayer` que implementa a mesma interface: escrito
assim, mandar pra TV (fase 4) é **trocar a instância**. Escrito contra
`ExoPlayer`, cada tela vira reescrita.

Está repetido em `android/gradle/libs.versions.toml`, ao lado das dependências do
Media3 — que é onde quem for buscá-las vai passar.

---

## 6. As três decisões da fase 1 que ficam caras de desfazer

**1. Um OkHttp só.** O Retrofit, o Coil e — na fase 2 — o Media3 usam a mesma
instância, amarrada em `OdeonApp.newImageLoader`. Um pool de conexões, um cache,
um lugar onde o `Authorization` é posto. Dois clientes fariam o player abrir
conexão própria, e aí o token de mídia teria duas contabilidades.

**2. O token de mídia não se renova sozinho.** Emitir um novo **aposenta o
anterior** (§43), e o anterior pode estar dentro de um player tocando — ou, na
fase 4, dentro do Chromecast, que morreria sem o celular perceber. O interceptor
deixa 401 subir como 401; quem decide renovar é quem sabe se há filme no ar.

**3. A tela de login é em dois tempos.** Os campos de conta só aparecem depois
que `/api/auth/status` respondeu naquele endereço. É o §53 — o produto não
oferece o que a validação vai negar — e evita mandar a senha da casa pra qualquer
coisa que atenda naquela porta.

---

## 7. As regras da casa, e elas mandam

**O projeto inteiro é em português**: documentação, comentários de código, nomes
de função, rotas (`/api/locadora/prateleira`) e endereços de tela
(`/biblioteca`, `/ao-vivo`). **Só os dois `README.md` da raiz são em inglês** —
são a porta de entrada dos repositórios públicos, e a versão portuguesa vive ao
lado em `README.pt-BR.md`. Mantenha.

Os comentários são **longos e argumentam**. Eles explicam *por quê*, não *o
quê* — e citam o número medido. Olhe qualquer arquivo em
`android/app/src/main/kotlin/` antes de escrever o seu.

Quatro regras que aparecem o tempo todo:

1. **Medir antes de desenhar.** Nada entra sem número tirado do acervo real.
2. **Não mentir com cara de metadado** (§18) — se o dado não existe, a tela omite
   em vez de chutar. Corolário (§24): linha vazia some, não vira "—".
3. **Errar em silêncio é o defeito** (§8b) — um clique que não faz nada é pior
   que um erro visível. E o §53: o produto **não oferece o que a validação vai
   negar**.
4. **Não corrija sozinho.** Quando a ideia do dono parecer "errada" pela régua de
   engenharia, **pergunte, ou faça o que foi pedido** — nunca entregue a versão
   sóbria por conta própria. Ele pediu explicitamente: *"sempre planejar
   comigo"*.

### Git

- Commits **sem atribuição ao assistente** — nada de `Co-Authored-By`, menção a
  Claude/Anthropic ou "Generated with".
- Mensagens **em português**, no estilo do projeto: o que mudou e **por quê**,
  com os números medidos. São longas e argumentadas de propósito.
- **Commite só quando ele pedir.** Empurrar, idem.

---

## 8. Como compilar

O outro computador tem Android Studio, então o caminho curto é abrir
`android/` nele e usar o `gradlew` de lá.

O que precisa combinar:

| | |
|---|---|
| JDK | **21**. O AGP 9 não aceita JDK 25 |
| AGP | 9.3.1 — **traz Kotlin embutido** |
| Gradle | 9.6.1 (o wrapper resolve) |
| plataforma | `platforms;android-37.1` + `build-tools;37.0.0` |

⚠️ **Não aplique o plugin `org.jetbrains.kotlin.android`.** O AGP 9 reprova:

```
The 'org.jetbrains.kotlin.android' plugin is no longer required for
Kotlin support since AGP 9.0.
```

⚠️ **Ao procurar plataforma no SDK, não procure `android-<inteiro>`.** O Android
tem versões menores agora — 36.1, 37.0, 37.1, 37.2-beta. Um levantamento que só
casa inteiros acha a 36 e conclui que ela é o teto; o AndroidX de hoje recusa
compilar contra menos que 37.

### E se quiser o container

Ele existe e funciona — compila, testa e passa o lint em 23 segundos, e é o mesmo
conjunto de pacotes que o CI usa:

```bash
docker compose up -d android
docker exec odeon-android ./gradlew :app:assembleDebug
docker exec odeon-android ./gradlew :app:testDebugUnitTest
docker exec odeon-android ./gradlew :app:lintDebug
```

⚠️ O `docker-entrypoint.sh` é **copiado pra imagem**. Editá-lo e dar `up` roda a
versão velha, calado. `docker compose build android` antes.

Ele **não** traz emulador, e é decisão: segfalta nesta máquina, e quem roda é
você.

---

## 9. O que fazer primeiro

1. Clonar `odeon-client`, abrir `android/`, compilar.
2. Instalar no emulador. O debug instala como `dev.odeon.android.debug` — ele
   convive com uma versão de verdade em vez de substituí-la.
3. Digitar **`100.77.253.18:8085`** na tela de login e entrar com uma conta real.
4. **Tirar screenshot da biblioteca e olhar com atenção.** É aqui que o valor
   está. Coisas que eu não pude ver:
   - a grade adaptativa em `GridCells.Adaptive(minSize = 108.dp)` dá quantas
     colunas num celular de verdade? E deitado?
   - os cartões sem pôster (**8.598 de 17.930**, quase metade) mostram o título
     sobre a cor da obra — isso é legível, ou é texto escuro sobre fundo escuro?
   - a `dominant_color` vem em `#RRGGBB`? O parser devolve `null` em silêncio se
     não vier, e aí a grade fica toda `fundoElevado`
   - a contagem "60 de 17.498" aparece? Ela só desenha quando o total existe
   - o teclado cobre o campo de senha? Tem `imePadding`, mas nunca foi visto
5. Só depois disso, a fase 2.

---

## 10. Armadilhas do servidor, que continuam valendo

O servidor fica no `serious-server` e você não vai mexer nele daí — mas estas
explicam sintomas que aparecem no app.

**A escassez está ligada.** Assistir exige empréstimo em aberto, **inclusive pro
admin** (§66). Perguntar `/api/locadora/liberadas` **antes** de desenhar o botão
de play, ou a tela oferece um 403. Isso morde na fase 2.

**O token de mídia é separado e curto (8h).** Vai em `?token=` na URL porque
`<img>` e o player não mandam header. **Emitir um novo aposenta o anterior**
(§43).

**O barramento é SSE.** `/api/events`, com o token na query. **Uma conexão pro
app inteiro** (§62).

**A cor dominante já existe.** **9.332 obras** têm `dominant_color` extraída pelo
servidor — o mesmo número das que têm pôster, porque a cor sai dele. A interface
se tinge com a obra e isso não custa backend.

**O preview de seek é folha de sprites**, uma imagem por arquivo. Baixa uma vez e
arrasta sem requisição nenhuma. Serve na fase 2.

**A identificação está pela metade.** Medido no banco em 04/08/2026, sobre as
17.930 obras:

| `match_state` | quantas |
|---|---|
| `auto` | 4.655 |
| `unmatched` | 4.415 |
| `confirmed` | 4.276 |
| `needs_review` | 3.350 |
| `ignored` | 1.234 |

E **8.598 não têm pôster** — 48%. Não bloqueia nada, mas se a biblioteca parecer
cheia de cartão sem capa, é isso, e não um defeito seu. Terminar a identificação
é trabalho de servidor (`POST /api/match`, ~1h), e é feito no `serious-server`.

---

## 11. ⚠️ Cuidado com dados de teste

**Você vai testar contra o banco de produção.** Não há cópia, não há ambiente de
teste: o `100.77.253.18:8085` é o Odeon que três pessoas de verdade usam, com os
empréstimos, notas, resenhas, posts e conquistas delas dentro.

E você **não alcança o banco** — só a API. Então a limpeza não é sua: ela é feita
no `serious-server`, e o padrão que funciona é conta descartável (`r47teste`),
fazer tudo com ela, e no fim `DELETE FROM app_user WHERE username = '…'`, que
pelo cascade limpa o resto.

Na prática, pra você:

- **peça uma conta descartável** antes de começar a testar coisa que escreve.
  Pra fase 1 — que só lê — entrar com uma conta real não deixa rastro além de um
  `last_login_at` e uma sessão nova.
- **anote o que você criou.** Todo empréstimo, nota ou post feito pra testar
  precisa ser dito, ou ele fica no perfil de alguém pra sempre.
- **não apague nada pela API achando que está limpando.** Apagar o empréstimo
  errado é apagar o empréstimo de uma pessoa.

Isso morde de verdade a partir da fase 5 (a locadora), onde pegar uma fita
emprestada é escrever no acervo de todo mundo — e já morde na fase 2, porque a
escassez (§66) exige um empréstimo em aberto pra assistir.

E o susto real do projeto: um backup `.env.antes-do-SAM` entrou num `git add` a
caminho de um repositório **público**, com a chave do TMDB, a do Groq e a senha
do Postgres dentro. Foi pego no crivo antes do push. **Confira o que vai no
commit.**

---

## 12. O que continua em aberto

- **O ícone.** O carretel em `ic_launcher_foreground.xml` é provisório; a espec
  (§7) deixou a identidade em aberto e só o nome foi fechado.
- **Publicação.** Play Store ou APK assinado por link (§7).
- **R8/minify no release.** Desligado, com o motivo escrito no
  `app/build.gradle.kts`.
- **A porta padrão do app.** 8443/8080 hoje; este servidor usa 8085. Ver §3.
- **Cast fora de casa.** Ele entra na v1 como recurso de rede local (§4c) — o
  Chromecast busca o vídeo sozinho e **não entra na tailnet**. Ou seja, a fase 4
  vai precisar ser verificada na rede de casa, não daí.
