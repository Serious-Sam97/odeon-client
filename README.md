# Odeon — interface

[![build](https://github.com/Serious-Sam97/odeon-client/actions/workflows/build.yml/badge.svg)](https://github.com/Serious-Sam97/odeon-client/actions/workflows/build.yml)

> 🇧🇷 **Versão em português: [README.pt-BR.md](README.pt-BR.md)** — and that one
> is the original. The project is written in Portuguese: code comments, function
> names, screen addresses (`/biblioteca`, `/ao-vivo`, `/locadora`) and the design
> document. This README is the front door, translated.

The interface for [Odeon](https://github.com/Serious-Sam97/odeon-server): React
+ TypeScript + Vite, plus the Kotlin Multiplatform clients for phone, TV and iOS.

**This is not a server.** It doesn't talk to a database, it doesn't identify
films, it can't see your media — it consumes `/api/…` and nothing else. To have
Odeon running you need the server repository; this is a client of it, like any
other.

---

## Two repositories

| | | |
|---|---|---|
| **[odeon-server](https://github.com/Serious-Sam97/odeon-server)** | the server | Rust · axum + sqlx + Postgres/pgvector · and the whole `docs/DESIGN.md` |
| **[odeon-client](https://github.com/Serious-Sam97/odeon-client)** | **you are here** | the web interface and the Kotlin clients |

They talk over HTTP and nothing else: no shared types, no generated code, no
cross imports. Splitting them cost one `docker-compose` service on each side.

**`web/src/api.ts` is the only copy of the contract that exists** — and it was
already a copy before the repositories split. That is the debt the separation
buys: a route that changes shape on the server has nothing left to warn this
side. It was bought knowingly, and it's written down in `DESIGN.md` §67.

---

## Running it

Prerequisites: Docker, and **an Odeon server already running** somewhere.

```bash
cp .env.example .env
```

Point `VITE_API_URL` at your server. Leaving it empty also works when the
interface is served from the same machine: it then derives the API from the page
itself — same host, same scheme, port 8080 (or 8443 under HTTPS).

```bash
docker compose up -d --build
```

The interface lands on `http://localhost:5174`. And even with nothing
configured it stays usable: the login screen lets you type a server by hand.

One detail that bites: **an HTTPS page cannot call an HTTP API** — the browser
blocks it as mixed content, and that includes `<video>`. This interface detects
that combination and explains it, instead of looking like the server went down.

---

## What's here

```
web/              React + TS + Vite — the eleven screens
  src/api.ts      the contract with the server, written by hand
clients/          Kotlin Multiplatform (see clients/README.md)
  shared/         models + Ktor + repository, no UI
  composeApp/     Compose MP: Android phone + iOS
  tv/             Android TV, D-pad focus
```

Each of the eleven screens has an address, in Portuguese like the rest of the
project — `/biblioteca`, `/colecoes`, `/locadora`, `/guia`, `/ao-vivo`,
`/mural`, `/perfil`, `/revisao`, `/pastas`, `/admin`, plus `/p/<name>` for
someone's profile. The root is "para você", the screen that answers *what do I
watch now*.

The Kotlin clients are parked at M2 and consume 10 of roughly 90 routes. That is
why CI doesn't build them — when they start moving again, they go in.

---

## The why behind every choice

It isn't here. The server repository's `docs/DESIGN.md` is 7,900 lines recording
what was measured, what was decided and the defects found along the way —
**including this interface's**: the 3D video-store shelf, the DVD menu, the
player, watch-together, the top bar.

It stayed whole on one side on purpose. The document's value is the seam — its
sections argue about both halves at once — and splitting it by subject would
have destroyed exactly that.

→ https://github.com/Serious-Sam97/odeon-server/blob/main/docs/DESIGN.md

It is in Portuguese.

---

## Licence

**AGPL-3.0**, the same as the server. See `LICENSE`.
