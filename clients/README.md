# Odeon — clientes

Quatro alvos, dois codebases.

| alvo | módulo | estado |
|---|---|---|
| Celular Android | `composeApp` | ✅ APK compila e roda |
| Android TV | `tv` | ✅ APK compila |
| iOS / iPad | `composeApp` (framework) + `iosApp` | ✅ projeto pronto, compila pro simulador |
| Web | `../web` | ✅ (M0–M3) |

## Como está organizado

```
shared/       modelos + cliente HTTP (Ktor) + repositório   ← sem UI nenhuma
composeApp/   Compose Multiplatform: celular Android + iOS
tv/           Android TV: UI própria, foco por D-pad
iosApp/       casca Swift que hospeda o Compose
```

O `shared` é o que torna "4 alvos, 2 codebases" sustentável: modelos, chamadas
HTTP, estado da biblioteca e reporte de progresso são um código só. O que
diverge fica isolado:

- **player** — `expect fun VideoPlayer` com Media3 no Android e AVPlayer no iOS
- **navegação de TV** — foco por D-pad é outro paradigma, não celular esticado
- **preferências** — `SharedPreferences` no Android, `NSUserDefaults` no iOS
- **URL padrão** — `10.0.2.2` no emulador Android, `localhost` no iOS

## Rodar

Precisa do JDK do Android Studio (o do sistema é mais novo do que o AGP 8.7 aceita):

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
```

Celular:

```bash
./gradlew :composeApp:installDebug
```

TV:

```bash
./gradlew :tv:installDebug
```

Testes do módulo compartilhado:

```bash
./gradlew :shared:testDebugUnitTest
```

### Endereço do servidor

Basta o **host**: digite `rog` ou `10.0.2.2` e o app descobre o resto. Ele tenta
`https://host:8443` e cai pra `http://host:8080` — "qual esquema?" não deveria
ser pergunta pra quem só quer assistir.

Escrevendo o esquema explicitamente (`http://rog:8080`), a escolha é respeitada
e o outro não é tentado.

Fica salvo por aparelho; digita-se uma vez.

## iOS

O `.xcodeproj` está no repositório e compila. Pela linha de comando:

```bash
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
cd iosApp
xcodebuild build -scheme iosApp -sdk iphonesimulator -destination 'generic/platform=iOS Simulator'
```

Ou simplesmente abra `iosApp/iosApp.xcodeproj` no Xcode e dê play.

### Como está montado

O Xcode compila duas telas em Swift (`iOSApp.swift`, `ContentView.swift`); todo
o resto é o framework Kotlin/Compose. A build phase **"Compilar framework
Kotlin"** roda antes de "Compile Sources" e chama:

```sh
./gradlew :composeApp:embedAndSignAppleFrameworkForXcode
```

Esse script detecta o JBR do Android Studio sozinho — o Xcode não herda o
ambiente do shell de login, então `JAVA_HOME` chega vazio, e o JDK 24 do sistema
não serve pro AGP 8.7.

### Rodar em aparelho

O projeto vem com assinatura desligada (`CODE_SIGNING_ALLOWED = NO`), que é o
suficiente pro simulador e evita exigir conta de desenvolvedor pra compilar.
Pra instalar num iPhone de verdade: abra no Xcode, aba **Signing & Capabilities**,
escolha seu time, e o Xcode religa a assinatura.

### Nota sobre o simulador nesta máquina

Não há **runtime** de simulador iOS instalado aqui (`xcrun simctl list runtimes`
volta vazio) — o que existe é o **SDK**, que basta pra compilar e linkar. Pra
*rodar*, baixe um runtime em Xcode → Settings → Components.
