# chat-multiplatform

[English](./README.md) | [简体中文](./README.zh-CN.md)

> One Kotlin codebase for chat and collaborative writing, running on Android, desktop and the browser.

[![CI](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/ci.yml/badge.svg)](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/ci.yml)
[![Docker Test](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/docker-test.yml/badge.svg)](https://github.com/woods-marshes/chat-multiplatform/actions/workflows/docker-test.yml)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.4.20-7F52FF?logo=kotlin&logoColor=white)](https://kotlinlang.org)
[![License](https://img.shields.io/github/license/woods-marshes/chat-multiplatform)](./LICENSE)

A single repository containing **three Compose clients** (Android, Desktop, and Compose Web on JS + Wasm), a **Kotlin/JS + React web client**, a **Ktor server**, and a **Node.js collaboration service**. Messaging runs over WebSocket. The article feature ships one Tiptap editor to two very different hosts: a React UMD bundle for the web client, and a self-contained HTML bundle loaded by a native WebView. Multi-user editing is powered by Yjs + Hocuspocus.

To decide whether you can run it: **no code changes required** — the server defaults to an in-memory H2 database and the clients default to `127.0.0.1:9051`.

<!-- TODO: drop in a short screen recording or screenshot of the chat and of two people editing the same article (5-10s). This is the biggest remaining gap. -->

## Quick start

Requires JDK 25 (the Gradle toolchain can provision it). Both the server and the clients start with zero configuration:

```bash
# 1. Start the server (in-memory H2, listens on http://127.0.0.1:9051)
./gradlew :server:run

# 2. In another terminal, start the desktop client
./gradlew :desktopApp:run
```

Other ways to run it:

```bash
./gradlew :androidApp:installDebug -PenableAndroid=true   # Android (emulator or device)
./gradlew :webApp:jsBrowserDevelopmentRun                 # Compose Web (JS)
./gradlew :web:jsBrowserDevelopmentRun                   # Web client (Kotlin/JS + React)
docker compose up -d                                     # server + PostgreSQL + collaboration service
```

In development the server also serves Swagger UI at `http://127.0.0.1:9051/openapi`.

## Features

**Messaging**
- One-to-one and group chats, realtime over WebSocket, typing indicators, revoke, threaded replies
- Conversation list, create and join groups, pin, mute
- Contacts (friend requests, A-Z index, presence), search across contacts and conversations
- Offline-first: messages are written to the local database (SQLDelight) first, so you can keep typing without a network; an outbox resends them on reconnect

**Articles**
- Tiptap WYSIWYG editor with drafts, publishing and soft delete
- List paging with a UUIDv7 cursor, backed by a RemoteMediator over the offline cache
- Realtime collaboration: Yjs incremental sync, persisted with a 2s debounce
- The same editor runs both in a WebView (Android / Desktop) and in the Kotlin/JS web client

**Everything else**
- Adaptive layouts: single pane or list-detail depending on the window size
- Theming (light/dark plus brand accent), Lyricist for i18n, persisted login state
- JWT auth with BCrypt password hashing; the WebSocket carries its token in the query string (browser API limitation)

## Architecture

```
androidApp / desktopApp / webApp   <- platform entry points, bootstrap only
            |
        composeApp                  <- shared UI wiring + Koin startup
            |
     features/*  +  core/*         <- feature modules / infrastructure
            |
   +--------+---------+------------------+
 web (Kotlin/JS)   tiptap-bridge     server (Ktor)
 React client       React library     REST + WebSocket
                    |- UMD  -> web     Exposed + PostgreSQL/H2
                    `- HTML -> WebView
                          |
                   collab-server (Node.js)
                   Yjs / Hocuspocus persistence
```

| Module | Contents |
|---|---|
| `core/model` | Domain models, sealed `DomainError` hierarchy, UI models (shared by client and server) |
| `core/network`, `core/network-api` | Ktor client, WebSocket, DTOs, Ktor Resources route definitions (one declaration used by both sides) |
| `core/data` | Repository interfaces and implementations, offline-first orchestration, RemoteMediator, outbox retries |
| `core/database` | SQLDelight (7 tables), DAOs, `QueryPagingSource` cursor paging |
| `core/ui`, `core/navigation` | Shared Compose components, Material 3 Adaptive, Navigation 3 routes |
| `features/*` | `auth` `chat` `contacts` `conversations` `article` `article-editor` `profile` `search` `settings` |
| `web` | Kotlin/JS + React client, reuses `core/*` through Koin |
| `tiptap-bridge` | Tiptap React library with two artifacts: UMD (web) and self-contained HTML (native WebView) |
| `server` | Ktor + Netty: REST API, WebSocket, file uploads, OpenAPI |
| `collab-server` | Node.js + Hocuspocus, Yjs document persistence, authenticates by calling the server's `/v1/auth/verify` |

One design note worth calling out: REST speaks JSON and the WebSocket speaks Protobuf, but **both share the same DTOs** (annotated with `@ProtoNumber` in `core/model`), so adding a field means editing one place.

## Tech stack

| Area | Choice |
|---|---|
| Language / build | Kotlin 2.4.20, Gradle 9.7.1 (version catalog + `build-logic` convention plugins) |
| UI | Compose Multiplatform 1.12.0, Material 3 Adaptive, Miuix theming, Navigation 3 |
| DI | Koin 4.2.2 |
| Network | Ktor 3.6.0 (client + server), Ktor Resources, WebSocket with kotlinx.serialization Protobuf |
| Client storage | SQLDelight 2.4.0, DataStore, Paging 3.5.1 |
| Server storage | Exposed 1.5.0 + PostgreSQL 17 (production) / H2 (development default) |
| Collaboration | Tiptap 3.27.1, Yjs 13.6, Hocuspocus 4.3 |
| Web | Kotlin/JS, React 19, Vite 8, Tailwind CSS 4 |
| CI | GitHub Actions (client compile matrix, server tests, Docker build), Dependabot |

Versions are not maintained in this file: `gradle/libs.versions.toml` is the single source of truth.

## API

The server listens on `9051` by default. Routes are declared in `core/network-api/.../api/V1.kt` and shared with the client:

```bash
TOKEN=$(curl -s -X POST http://127.0.0.1:9051/v1/auth/register \
  -H "Content-Type: application/json" \
  -d '{"username":"alice","email":"alice@example.com","password":"Passw0rd!"}' | jq -r '.accessToken')

# Authenticated request
curl -s http://127.0.0.1:9051/v1/users/me -H "Authorization: Bearer $TOKEN"

# Article list, UUIDv7 cursor paging
curl -s "http://127.0.0.1:9051/v1/articles?limit=20"
curl -s "http://127.0.0.1:9051/v1/articles?beforeId=<last id of the previous page>"
```

## Collaborative editing

```bash
./gradlew :server:run                    # terminal 1: REST + JWT
cd collab-server && npm install && \
  PORT=1234 \
  DATABASE_URL=postgres://postgres:postgres@localhost:5432/chat_db \
  KTOR_AUTH_URL=http://127.0.0.1:9051/v1/auth/verify \
  node index.js                          # terminal 2: Yjs collaboration service
```

Open an article editor and the client connects to `ws://127.0.0.1:1234`. Concurrent edits sync as Yjs updates and are written to the `yjs_documents` table after a 2s debounce. Non-authors get a read-only room, and unpublished drafts are not readable at all.

`docker compose up -d` brings up the server, PostgreSQL and the collaboration service together.

## Development

```bash
./gradlew :server:test       # server tests
./gradlew check              # all tests
./gradlew :desktopApp:compileKotlin   # client compile check

cd tiptap-bridge && npm ci && npm run build && npm run build:webview   # only after editing the editor sources
```

Commit format: `<scope>: <summary>`. See [CONTRIBUTING.md](./CONTRIBUTING.md) before opening a change; deeper module notes live in [`notes/`](./notes) and [`CLAUDE.md`](./CLAUDE.md).

## License

MIT
