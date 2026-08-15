# Onde a coisa parou — 14/08/2026

Passagem de bastão. Quem chega agora não tem contexto nenhum, então isto começa
do começo e termina no que está aberto.

---

## 1. O que é isto

**Odeon**: servidor de mídia da casa, «Netflix particular de uma família só».
Este repositório é o cliente. Três frentes: `web/`, e dentro de `android/` os
módulos `:app` (celular) e `:tv` (Android TV), com `:core` (não desenha) e
`:cenario` (desenha, sem saber se é TV ou celular).

O trabalho recente é o **redesenho da TV**, na branch `redesenho-tv`.

### Regras da casa, e elas não são negociáveis

1. **Tudo em português** — comentário, nome de função, de arquivo, de variável.
   `dados` e não data, `Cores` e não Colors, `TelaDoPlayer` e não PlayerScreen.
   Código novo segue o que já está lá.
2. **Comentário explica o porquê, com folga.** Este projeto documenta decisão,
   não mecânica. Um `⚠️` marca o que morde quem mexer sem ler.
3. **O lint tem `abortOnError` e é portão de verdade.** Exceção vai no
   `lint.xml`, escrita e justificada — nunca desligada no atacado.
4. **A regra mais cara de todas:** _ver na TV antes de escrever que funciona, e
   nunca deixar um comentário afirmar comportamento que ninguém assistiu._
   Compilar, passar no lint e passar nos testes **não** dizem nada sobre foco,
   layout em painel de verdade ou quadro perdido.
5. Se algo na doc parecer errado, **diga antes de construir**.

### O portão, antes de qualquer commit

```bash
cd android
export JAVA_HOME="/Applications/Android Studio.app/Contents/jbr/Contents/Home"
./gradlew :tv:compileReleaseKotlin :tv:assembleDebug \
  :app:lintDebug :tv:lintDebug :core:lintDebug :cenario:lintDebug testDebugUnitTest
```

São **164 testes**, 0 falhas. O `compileReleaseKotlin` está na lista porque há
código trancado em `BuildConfig.DEBUG` (ver §4) e ele precisa sumir no release.

---

## 2. Como falar com a TV — é a primeira pedra do caminho

A TCL (MediaTek mt5896, Android 14, painel 4K **60 Hz e só isso**) fica no
`adb` **pela rede**. Não há cabo.

```bash
export PATH="$PATH:$HOME/Library/Android/sdk/platform-tools"
adb connect 100.75.58.17:5555
adb devices          # deve listar 100.75.58.17:5555  device
```

⚠️ **Se der `No route to host`, não insista: é pareamento.** A autorização cai
quando a depuração sem fio é desligada, e nenhuma varredura de porta resolve —
já tentei. O código só existe na tela da TV, então **peça ao dono**:

> Configurações → Sistema → Opções do desenvolvedor → Depuração sem fio →
> «Parear dispositivo com código de pareamento». Ele mostra um `IP:porta` e um
> código de 6 dígitos.

```bash
adb pair <IP>:<PORTA_DE_PAREAMENTO> <CODIGO>
adb connect 100.75.58.17:5555
```

⚠️ E **não rode `adb kill-server`**: o servidor novo pode subir sem rede e todo
comando passa a mentir «No route to host» com a TV ali do lado.

### Mecânica que custou tempo pra descobrir

| | |
|---|---|
| pacote | `dev.odeon.android.tv.debug` |
| atividade | `dev.odeon.android.tv.AtividadeDaTv` |
| abrir | `adb shell monkey -p dev.odeon.android.tv.debug -c android.intent.category.LEANBACK_LAUNCHER 1` |
| a TV dorme | `adb shell input keyevent KEYCODE_WAKEUP` |
| captura | `adb shell screencap -p /sdcard/t.png && adb pull /sdcard/t.png` |
| ⚠️ `◀` na raiz | **sai do app** pro launcher da Google |
| ⚠️ navegar por D-pad | erra de tela metade das vezes — o destino do app é salvo e muda de lugar entre execuções. **Use a bancada** (§4) |

---

## 3. O que foi feito nesta rodada

Do mais recente pra trás — `git log` na `redesenho-tv` conta o resto.

- **Busca nossa** (`TelaDaBuscaDaTv` + `ModeloDaBusca` no `:core`). O botão
  `BUSCAR` do trilho abria a busca do sistema, e nesta TCL isso caía **no
  assistente da Google**. Agora é teclado alfabético desenhado por nós.
  - ⚠️ Achado no caminho: a `TelaInicialDaTv` punha
    `focusProperties { left = focoDoTrilho }` no `Box` do conteúdo, e isso
    **desce pros descendentes** — o `◀` pulava pro menu de qualquer lugar, até
    do segundo cartaz da biblioteca. Removido; o `exit` sozinho faz o certo.
- **Modal de faixas** no player: legenda e áudio na mesma tela, centrada, com o
  foco preso dentro dela. Antes era uma tirinha espremida na quina de baixo.
- **A bancada** (§4), que é o que importa daqui pra frente.

### ⚠️ Defeitos conhecidos e **não** consertados

- Na **tela de busca**, `◀` na primeira coluna do teclado **não** volta pro
  trilho, e `BACK` sai do app em vez de voltar pra biblioteca.
- No **guia** e no **perfil**, o `rolavelComOControle` da coluna engole o foco: o
  `◀` rola a página em vez de chegar no trilho.
- O botão `voltar` da ficha não tem véu atrás — some sobre cena clara.
- Fim de arquivo fora de um canal ainda é tela preta.

---

## 4. A bancada — leia antes de medir qualquer coisa

`android/ferramentas/bancada.sh`. Ela existe porque a investigação do §26 travou
por **falta de repetibilidade**: o mesmo arquivo, no mesmo binário, dava de 0,11
a 4,21 quadros descartados por segundo.

```bash
ferramentas/bancada.sh --obra <id inteiro> --em 1500 --corridas 3 --duracao 60
ferramentas/bancada.sh --obra <id> --legenda pt-BR --corridas 3
ferramentas/bancada.sh --busca bond --indice 1 --corridas 1
```

O que ela conserta: **sem navegação** (uma porta de `Intent` trancada no
`BuildConfig.DEBUG` abre a obra e manda tocar), **sem salto de retomada**
(começa em `--em`), **sem o arranque** (os 10 s iniciais saem da conta) e ela
**diz qual obra e qual arquivo** mediu.

⚠️ Ela mentiu três vezes no primeiro uso, e os três consertos estão dentro:
mediu a prévia do herói da home achando que era o filme; morreu no meio da
busca por eu apagar o pedido antes da chamada de rede; e perdeu o começo do log
numa janela de 3 min porque o anel do `logcat` girou (hoje grava em fluxo).

⚠️ **`--indice` não é identidade** — a ordem da busca muda entre corridas. Pra
repetir, use `--obra` com o **id inteiro**; cortado em 8 ele abre uma ficha que
não carrega.

Os dois arquivos do caso em estudo:

| | obra | arquivo |
|---|---|---|
| 007 pt-BR (mkv, ac3) | `eddbfd12…` | `a2274591-541d-4e83-bbe3-6f1b35b6cc6a` |
| 007 inglês (mp4, aac) | `a950f840-f6f7-4390-b023-94eb14e59abd` | `2531ac55-1f33-4252-a1d8-c4e878fbb757` |

---

## 5. O caso aberto: quadro perdido na TV

Leia **`REDESENHO-TV.md` §26 e §27** inteiros antes de propor qualquer coisa. O
resumo honesto:

**O que está de pé:**
- O defeito existe: medido à mão, **0,51 a 4,21 descartes/s** no arquivo em
  inglês, enquanto o **Jellyfin, na mesma TV e no mesmo arquivo, dá 0,00–0,03**.
  A diferença é nossa.
- O painel é **60 Hz e só**, então todo filme de 23,976 tem pulldown 3:2 com um
  soluço a cada ~17 s. Isso é físico, existe igual no Jellyfin, e **não tem
  conserto do nosso lado**. Não confunda com o descarte.

**O que já morreu, com medida:**
- «O arquivo declara 12 fps» — o `ffprobe` do servidor mostrou 23,976 CFR nos
  dois, e o conserto que escrevi não mudou nada. Desfeito.
- «O liso é o que transcodifica» — o servidor respondeu que o arquivo em inglês
  é `direct_play` nos quatro perfis, e **nenhum plano recodifica vídeo**.
- «É a legenda ligada» — medido com `--legenda`, e conferido na tela que ela
  aparecia: 0,00 nas três corridas.
- «É o arquivo, ou o ponto do filme» — **dezessete corridas** entre 0,00 e 0,03:
  os dois arquivos, do começo e aos 25 min, com e sem legenda, até 165 s.

**O que sobra, e é a pista mais forte:**

⚠️ A TV está **de pé há 15 dias**, com **load average 22** em 4 núcleos e 185 MB
livres de 2,3 GB. Máquina assim perde quadro **às vezes** — e «às vezes» é a
forma do relato, e é o que faz dezessete corridas limpas não provarem nada. O
dono relatou hoje que melhorou; **ninguém consertou** — o defeito só não estava
presente na hora. Espere ele voltar.

O caminho por onde eu iria: fazer a bancada rodar **15–20 minutos numa corrida
só**, e correlacionar o descarte com carga e memória do aparelho medidas ao
mesmo tempo. Se casar, a conversa muda de «nosso decodificador» pra «o que mais
está rodando nesta TV».

⚠️ E há uma queixa nova, sem resposta: **«a qualidade dos filmes caiu?»**.
Objetivamente o decodificador recebe `1920x832 / pic 1920x816 / 8 bits`, BT709,
plano direto — idêntico a todas as capturas. O app não entrega menos pixel. Mas
o único ponto onde falamos com o processamento de imagem da TV é o
`stepSetPQFormat`, e hoje ele **recebeu de nós `FrameRate 12000`**. Não está
provado que mexa em nitidez; é o primeiro lugar a olhar se a queixa voltar.

---

## 6. Como o dono trabalha

Ele é preciso, repara em detalhe e corrige direto. Ele **vê na TV** e não aceita
«deve funcionar». Quando um número seu não bate com o que ele vê, o número é que
está errado até prova em contrário — foi assim três vezes hoje, e as três vezes
ele estava certo.
