# A caixa de ferramentas do Android.
#
# ## Por que um container, e não o SDK no host
#
# É o padrão da casa, e ele não foi escolhido por gosto: nem `cargo` nem `npx`
# estão no PATH desta máquina. O servidor compila dentro do `odeon-api`, a
# interface dentro do `odeon-web`, e o Android não teria por que ser a exceção
# que obriga alguém a instalar 3 GB de SDK pra clonar o repositório.
#
# ## Por que o JDK está pinado, e por que em 21
#
# Medido no host em 04/08/2026: `openjdk 25.0.4`. **Nenhum AGP aceita ele.**
# Isso não é novidade aqui — o `clients/README.md` já mandava exportar o
# `JAVA_HOME` do Android Studio pelo mesmo motivo, e essa instrução é o sintoma
# de um problema que este arquivo resolve de vez.
#
# 21 e não 17: é a LTS que o AGP 9 pede e que o Gradle 9 suporta, então ela
# atende tanto o app novo quanto o KMP parado em `clients/` (AGP 8.7), se um dia
# alguém quiser compilar os dois com a mesma caixa.
FROM eclipse-temurin:21-jdk-noble

# `unzip` é do sdkmanager (os pacotes vêm zipados). `git` porque o Gradle o
# invoca sozinho em alguns plugins. `curl` pra buscar o cmdline-tools abaixo.
RUN apt-get update \
 && apt-get install -y --no-install-recommends unzip git curl ca-certificates \
 && rm -rf /var/lib/apt/lists/*

# ## Não há bibliotecas de emulador aqui, e isso foi medido
#
# Houve, por uma tarde: ~200 MB de X11, ALSA, D-Bus e Mesa, pra rodar o emulador
# sem janela. **O emulador segfalta nesta máquina**, em sete configurações
# diferentes — o diagnóstico inteiro está no `README.md` ao lado.
#
# Saíram porque quem roda o app agora é outro computador, e uma caixa de
# ferramentas que carrega 200 MB pra um programa que não liga é uma caixa que
# mente sobre o que sabe fazer. Esta aqui **compila, testa e passa o lint** — e
# faz as três em 23 segundos.

# ------------------------------------------------------------ o cmdline-tools
#
# Ele fica FORA do $ANDROID_HOME de propósito.
#
# O SDK propriamente dito mora num volume nomeado (ver o docker-compose), pra
# que instalar um pacote a mais não custe reconstruir a imagem inteira. Mas um
# volume **mascara** o que a imagem escreveu naquele caminho — se o
# cmdline-tools estivesse lá dentro, ele sumiria no primeiro `up`.
#
# Então: a ferramenta é da imagem, o acervo de pacotes é do volume, e o
# `sdkmanager` é chamado com `--sdk_root` apontando de um pro outro.
#
# A versão está pinada e foi medida no `repository2-3.xml` do Google em
# 04/08/2026: `cmdline-tools;latest` era a **22.0**, zip `15859902`. Pinar é o
# ponto — "latest" numa imagem faz o build de amanhã ser outro build.
ARG CMDLINE_TOOLS_ZIP=commandlinetools-linux-15859902_latest.zip
RUN mkdir -p /opt/cmdline-tools \
 && curl -fsSL -o /tmp/ct.zip "https://dl.google.com/android/repository/${CMDLINE_TOOLS_ZIP}" \
 && unzip -q /tmp/ct.zip -d /tmp/ct \
 && mv /tmp/ct/cmdline-tools /opt/cmdline-tools/latest \
 && rm -rf /tmp/ct.zip /tmp/ct

ENV ANDROID_HOME=/opt/android-sdk \
    ANDROID_SDK_ROOT=/opt/android-sdk \
    GRADLE_USER_HOME=/home/gradle/.gradle \
    PATH="/opt/cmdline-tools/latest/bin:/opt/android-sdk/platform-tools:${PATH}"

# ------------------------------------------------------------------- o usuário
#
# UID 1000 é o do dono no host. Sem isso o Gradle escreve `android/build/` e
# `android/.gradle/` como root, e o editor no host não consegue mais apagar —
# um estorvo que só aparece depois, e sempre no pior momento.
#
# ## O UID 1000 já está ocupado, e isso custou um build
#
# A `eclipse-temurin:21-jdk-noble` vem com um usuário `ubuntu` **já em 1000**.
# A primeira versão deste bloco tinha um `|| true` no `useradd`, o conflito
# passou calado, e o container morreu no `up` com "unable to find user gradle".
#
# É o §8b do DESIGN.md acontecendo aqui dentro: **errar em silêncio é o
# defeito.** Sem o `|| true` o build teria quebrado na hora, apontando a linha.
# Então agora o conflito é tratado, e nada é engolido.
ARG UID=1000
ARG GID=1000
RUN set -eux; \
    if ocupante="$(getent passwd "${UID}" | cut -d: -f1)" && [ -n "${ocupante}" ]; then \
        userdel -r "${ocupante}" 2>/dev/null || userdel "${ocupante}"; \
    fi; \
    if getent group "${GID}" > /dev/null; then \
        groupmod -n gradle "$(getent group "${GID}" | cut -d: -f1)"; \
    else \
        groupadd -g "${GID}" gradle; \
    fi; \
    useradd -u "${UID}" -g "${GID}" -m -s /bin/bash gradle; \
    mkdir -p /opt/android-sdk /home/gradle/.gradle; \
    chown -R "${UID}:${GID}" /opt/android-sdk /opt/cmdline-tools /home/gradle; \
    id gradle

COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

USER gradle
WORKDIR /app

ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]

# O container não serve nada — ele existe pra receber `docker exec`, igual ao
# `odeon-api` recebe `cargo test`. Fica de pé sem gastar CPU.
CMD ["sleep", "infinity"]
