# Odeon — interface

[![build](https://github.com/Serious-Sam97/odeon-client/actions/workflows/build.yml/badge.svg)](https://github.com/Serious-Sam97/odeon-client/actions/workflows/build.yml)

> 🇬🇧 **English version: [README.md](README.md)** — esta é a versão em
> português, e ela é a original.

A interface do [Odeon](https://github.com/Serious-Sam97/odeon-server): React +
TypeScript + Vite, mais os clientes Kotlin Multiplatform de celular, TV e iOS.

**Ela não é um servidor.** Não fala com banco, não identifica filme, não enxerga
mídia — consome `/api/...` e mais nada. Pra ter um Odeon rodando você precisa do
repositório do servidor; este aqui é um cliente dele, como qualquer outro.

---

## Dois repositórios

| | | |
|---|---|---|
| **[odeon-server](https://github.com/Serious-Sam97/odeon-server)** | o servidor | Rust · axum + sqlx + Postgres/pgvector · e o `docs/DESIGN.md` inteiro |
| **[odeon-client](https://github.com/Serious-Sam97/odeon-client)** | **você está aqui** | a interface web e os clientes Kotlin |

Eles conversam por HTTP e mais nada. Separá-los custou um serviço de
`docker-compose` de cada lado.

---

## Subir

Pré-requisitos: Docker, e **um servidor Odeon já rodando** em algum lugar.

```bash
cp .env.example .env
```

Aponte `VITE_API_URL` pro seu servidor. Vazio também funciona quando a interface
é servida pela mesma máquina: aí ela deduz da própria página (mesmo host, mesmo
esquema, porta 8080 ou 8443).

```bash
docker compose up -d --build
```

A interface fica em `http://localhost:5174`. E mesmo sem configurar nada, a tela
de login deixa trocar de servidor à mão.

---

## O que tem aqui

```
web/              React + TS + Vite — as onze telas
  src/api.ts      o contrato com o servidor, escrito à mão
clients/          Kotlin Multiplatform (ver clients/README.md)
  shared/         modelos + Ktor + repositório, sem UI
  composeApp/     Compose MP: celular Android + iOS
  tv/             Android TV, foco por D-pad
```

**`web/src/api.ts` é a única cópia de contrato que existe** — e ela já era uma
cópia antes de os repositórios se separarem. Não há tipo compartilhado, não há
código gerado, não há import cruzado: a separação custou um arquivo de
`docker-compose`.

Os clientes Kotlin estão parados no M2 e consomem 10 das ~90 rotas.

---

## O porquê de cada escolha

Não está aqui. O `docs/DESIGN.md` do repositório do servidor tem 7.900 linhas
registrando o que foi medido, o que foi decidido e os defeitos que apareceram —
**inclusive os desta interface**: a estante 3D da locadora, o menu de DVD, o
player, o assistir junto, a barra de cima.

Ele ficou inteiro de um lado de propósito. O documento vale pela costura — as
seções argumentam sobre as duas metades ao mesmo tempo —, e rachá-lo perderia
exatamente isso.

→ https://github.com/Serious-Sam97/odeon-server/blob/main/docs/DESIGN.md

---

## Licença

**AGPL-3.0**, a mesma do servidor. Ver `LICENSE`.
