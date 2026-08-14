#!/usr/bin/env bash
#
# A bancada: mede descarte de quadro na TV, repetidamente, sem controle remoto.
#
# ## Por que ela existe
#
# A investigação do «lagadinho» (docs/REDESENHO-TV.md §26) travou não por falta
# de hipótese, mas por falta de **repetibilidade**: cada medida custava uma
# navegação por D-pad que caía na tela errada metade das vezes, e cada corrida
# começava com um salto de retomada que descarta quadro por definição. As duas
# coisas juntas davam de 0,11 a 4,21 descartes por segundo no mesmo arquivo, com
# o mesmo binário — ruído maior que qualquer efeito que se quisesse medir.
#
# ## O que ela conserta
#
#   1. **Sem navegação**: um `Intent` de debug (`dev.odeon.android.tv.BANCADA`)
#      abre a obra e manda tocar. Nada de setinha.
#   2. **Sem salto**: começa em `--em` (0 por padrão), então não há retomada.
#   3. **Sem o arranque**: os primeiros [ASSENTAMENTO] segundos são descartados
#      da conta. Todo início tem enchimento de buffer e negociação de codec, e
#      contá-los é medir o arranque, não o filme.
#
# ## Uso
#
#   ferramentas/bancada.sh --busca majestade --indice 0 --corridas 3
#   ferramentas/bancada.sh --obra <id> --em 600 --duracao 60
#
# A saída é uma linha por corrida com **descartes por segundo em regime**, que é
# o número que vale — e a mediana no fim.
set -euo pipefail

APP=dev.odeon.android.tv.debug
ATIVIDADE=$APP/dev.odeon.android.tv.AtividadeDaTv
ACAO=dev.odeon.android.tv.BANCADA

# Quanto do começo não conta. Ver o cabeçalho.
ASSENTAMENTO=10

BUSCA=""; OBRA=""; INDICE=0; EM=0; DURACAO=40; CORRIDAS=3; APARELHO=""

while [ $# -gt 0 ]; do
  case "$1" in
    --busca) BUSCA="$2"; shift 2 ;;
    --obra) OBRA="$2"; shift 2 ;;
    --indice) INDICE="$2"; shift 2 ;;
    --em) EM="$2"; shift 2 ;;
    --duracao) DURACAO="$2"; shift 2 ;;
    --corridas) CORRIDAS="$2"; shift 2 ;;
    --aparelho) APARELHO="$2"; shift 2 ;;
    *) echo "argumento desconhecido: $1" >&2; exit 2 ;;
  esac
done

if [ -z "$BUSCA" ] && [ -z "$OBRA" ]; then
  echo "é preciso --busca <termo> ou --obra <id>" >&2; exit 2
fi

# ⚠️ Sem `--aparelho`, escolhe o **primeiro** da lista. Com um emulador ligado
# junto isso mede a coisa errada em silêncio, então o nome escolhido é impresso.
if [ -z "$APARELHO" ]; then
  APARELHO=$(adb devices | awk 'NR>1 && $2=="device" {print $1; exit}')
fi
[ -n "$APARELHO" ] || { echo "nenhum aparelho conectado" >&2; exit 1; }
echo "aparelho: $APARELHO"

SAIDA=$(mktemp -d)
trap 'rm -rf "$SAIDA"' EXIT

for n in $(seq 1 "$CORRIDAS"); do
  adb -s "$APARELHO" shell am force-stop $APP >/dev/null
  sleep 3
  adb -s "$APARELHO" logcat -c

  # ⚠️ O componente vai explícito (`-n`) **e** a ação junto (`-a`). Sem o
  # componente o sistema procuraria quem declara a ação, e a atividade não a
  # declara no manifesto de propósito: porta de debug não se anuncia.
  if [ -n "$OBRA" ]; then
    adb -s "$APARELHO" shell am start -n $ATIVIDADE -a $ACAO \
      --es obra "$OBRA" --ed em "$EM" >/dev/null
  else
    adb -s "$APARELHO" shell am start -n $ATIVIDADE -a $ACAO \
      --es busca "$BUSCA" --ei indice "$INDICE" --ed em "$EM" >/dev/null
  fi

  # ⚠️ O log é **gravado em fluxo**, não despejado no fim.
  #
  # A primeira versão fazia `logcat -d` depois do `sleep`, e numa corrida de três
  # minutos o anel do logcat girou: as linhas do começo — inclusive a que diz
  # **qual obra** a bancada abriu — tinham sido comidas, e a corrida saiu «SEM
  # SUJEITO» sem nada de errado com ela. O `MediaCodec` deste aparelho escreve
  # duas linhas por segundo por conta própria; janela longa e buffer de anel não
  # convivem.
  adb -s "$APARELHO" logcat > "$SAIDA/corrida_$n.txt" 2>&1 &
  GRAVANDO=$!
  sleep "$DURACAO"
  kill $GRAVANDO 2>/dev/null || true
  wait $GRAVANDO 2>/dev/null || true
done

python3 - "$SAIDA" "$CORRIDAS" "$ASSENTAMENTO" <<'PY'
import re, sys, statistics
saida, corridas, assentamento = sys.argv[1], int(sys.argv[2]), float(sys.argv[3])

def seg(t):
    h, m, s = t.split(":")
    return int(h) * 3600 + int(m) * 60 + float(s)

taxas = []
for n in range(1, corridas + 1):
    txt = open(f"{saida}/corrida_{n}.txt", errors="ignore").read()
    # ⚠️ **Sem sujeito não há medida.** A primeira versão contava qualquer
    # decodificação, e a primeira corrida de verdade mediu a **prévia do herói da
    # home** — que decodifica vídeo sozinha, sem ninguém pedir — e imprimiu 0,00
    # como se fosse o filme. Um número com o sujeito errado é pior que nenhum.
    abriu = re.search(r"^\d\d-\d\d (\d\d:\d\d:\d\d\.\d+).*bancada: obra=(\S+) em=(\S+)", txt, re.M)
    if not abriu:
        print(f"corrida {n}: SEM SUJEITO — a bancada não abriu obra nenhuma "
              f"(o termo não achou nada, ou o app não tinha sessão)")
        continue
    desde = seg(abriu.group(1))

    # ⚠️ E só conta o que veio **depois** de a bancada abrir. É o que separa o
    # filme da prévia da home, que já estava decodificando quando ela chegou.
    # O `ROB` do MediaCodec é o batimento do decodificador: sem ele não houve vídeo.
    quadros = [t for t in (seg(m) for m in re.findall(r"^\d\d-\d\d (\d\d:\d\d:\d\d\.\d+).*ROB\]V, ROB:", txt, re.M)) if t > desde]
    quedas = [t for t in (seg(m) for m in re.findall(r"^\d\d-\d\d (\d\d:\d\d:\d\d\.\d+).*drop frame", txt, re.M)) if t > desde]
    if not quadros:
        print(f"corrida {n}: SEM VÍDEO — a bancada abriu {abriu.group(2)[:8]} mas nada decodificou")
        continue
    inicio, fim = min(quadros), max(quadros)
    janela = fim - (inicio + assentamento)
    if janela <= 1:
        print(f"corrida {n}: janela curta demais ({janela:.0f}s) — aumente --duracao")
        continue
    regime = [q for q in quedas if q > inicio + assentamento]
    taxa = len(regime) / janela
    taxas.append(taxa)
    # ⚠️ O id vai **inteiro**. Cortado em 8 ele serve pra ler e não serve pra
    # repetir: passar o pedaço em `--obra` abre uma ficha que não carrega, e a
    # corrida sai «SEM VÍDEO» sem dizer por quê. Repetir é o ponto da bancada.
    quem = abriu.group(2)
    # ⚠️ A obra **não** identifica o que tocou: nesta casa há obra com dois
    # arquivos (o mesmo filme dublado e legendado), e eles medem diferente. Quem
    # identifica é o arquivo do plano.
    arq = re.search(r"plano\(([0-9a-f-]{36})\)", txt[txt.find(abriu.group(0)):])
    print(f"corrida {n}: obra={quem}\n           arquivo={arq.group(1) if arq else '?'} | "
          f"{len(quedas)} descartes no total, "
          f"{len(regime)} em regime ({janela:.0f}s) = {taxa:.2f}/s")

if taxas:
    print(f"\nmediana em regime: {statistics.median(taxas):.2f} descartes/s "
          f"(de {len(taxas)} corridas)")
PY
