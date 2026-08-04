#!/usr/bin/env bash
#
# Popula o SDK no primeiro `up`, e sai do caminho em todos os outros.
#
# ## Por que aqui e não no Dockerfile
#
# O SDK mora num volume nomeado. Instalá-lo durante o `docker build` seria
# escrever num caminho que o volume vai mascarar no `up` seguinte — o download
# aconteceria, e o resultado sumiria.
#
# Aqui ele acontece uma vez, fica no volume, e sobrevive a `docker compose
# build --no-cache`. Instalar um pacote a mais depois é um `docker exec ...
# sdkmanager "..."`, sem reconstruir imagem nenhuma.
#
# ## ⚠️ MAS ESTE ARQUIVO É COPIADO PRA IMAGEM
#
# Editá-lo e dar `docker compose up` roda a versão VELHA, sem avisar. Aconteceu
# em 04/08/2026: a lista de pacotes abaixo passou a pedir `platforms;android-37.1`
# e o `up` instalou `android-36` do mesmo jeito.
#
# É irmã da armadilha das migrações do servidor, onde o `.sql` está embutido no
# binário — o arquivo que você editou não é o arquivo que está rodando.
#
#     docker compose build android && docker compose up -d android
set -euo pipefail

# Os pacotes da base. As versões foram medidas no repositório do Google em
# 04/08/2026 e estão pinadas pelo mesmo motivo do cmdline-tools: um SDK que se
# atualiza sozinho faz o build de amanhã não ser o build de hoje.
#
#   platform-tools     → o `adb`, que é como o APK chega no aparelho
#   platforms;37.1     → a plataforma estável mais nova
#   build-tools;37.0.0 → o que o AGP 9.3.1 pede pra ela
#
# ## O `37.1` tem ponto, e o ponto quase passou despercebido
#
# O Android ganhou **versões menores** de plataforma: existem 36, 36.1, 37.0,
# 37.1 e 37.2-beta. Um levantamento que procura `android-<inteiro>` acha só a 36
# e conclui que ela é o teto — foi o que aconteceu na primeira versão deste
# arquivo, e o build denunciou: o AndroidX de hoje recusa compilar contra menos
# que 37.
PACOTES=(
  "platform-tools"
  "platforms;android-37.1"
  "build-tools;37.0.0"
)

MARCA="${ANDROID_HOME}/.odeon-sdk-instalado"

if [ ! -f "${MARCA}" ]; then
  echo "[odeon-android] SDK ausente em ${ANDROID_HOME} — instalando a base."
  echo "[odeon-android] Isso baixa algumas centenas de MB e acontece UMA vez."

  # As licenças. Aceitá-las por script é o equivalente do que o Android Studio
  # faz com um diálogo; não há como instalar pacote nenhum sem isso.
  yes | sdkmanager --sdk_root="${ANDROID_HOME}" --licenses > /dev/null 2>&1 || true

  sdkmanager --sdk_root="${ANDROID_HOME}" "${PACOTES[@]}"

  touch "${MARCA}"
  echo "[odeon-android] SDK pronto:"
  sdkmanager --sdk_root="${ANDROID_HOME}" --list_installed || true
else
  echo "[odeon-android] SDK já instalado (${MARCA})."
fi

exec "$@"
