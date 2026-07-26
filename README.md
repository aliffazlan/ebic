# EBIC — Turn-Based Hex Tactics

A 2-player turn-based tactics game: hex-grid movement, rock-paper-scissors combat (Strength beats Intelligence beats Agility beats Strength), a drafted roster of 15 named heroes, and 39 abilities. Java 21 engine + web server, browser client.

This doc is the "get it running" guide. For architecture/design details, see [`CLAUDE.md`](CLAUDE.md); for the exact HTTP/WebSocket wire format, see [`API_CONTRACT.md`](API_CONTRACT.md).

## Prerequisites

- **Java 21** and **Maven** (`java -version`, `mvn -version`)
- **Node.js 20+** and npm

> **On WSL specifically**: check `which node` actually resolves to a Linux binary (e.g. `/usr/bin/node` or an `nvm`-installed path), not something under `/mnt/c/...`. A Windows-side `npm`/`node` on PATH will silently run everything under Windows `node.exe`, which breaks the dev server's hot-reload. If you hit this, install a Linux-native Node via [nvm](https://github.com/nvm-sh/nvm):
> ```bash
> curl -fsSL https://raw.githubusercontent.com/nvm-sh/nvm/v0.40.1/install.sh | bash
> export NVM_DIR="$HOME/.nvm" && . "$NVM_DIR/nvm.sh"
> nvm install 20
> ```

## Quick start

Two processes, two terminals.

**Terminal 1 — backend (Java, port 7070)**
```bash
cd ebic
mvn -q package -DskipTests
CP="target/classes:$(mvn -q dependency:build-classpath -Dmdep.outputFile=/dev/stdout)"
java -cp "$CP" com.walnutt.web.WebServer
```
You should see `EBIC web server listening on http://localhost:7070`. It creates a local SQLite file (`ebic.db`) in `ebic/` on first run — this holds accounts/sessions/matches and is gitignored.

**Terminal 2 — frontend (Vite dev server, port 5173)**
```bash
cd web
npm install       # first time only
npm run dev
```
You should see `VITE ... ready` with a `Local: http://localhost:5173/` URL. The dev server proxies `/api` and `/ws` through to the backend on :7070, so open the app at **http://localhost:5173**, not :7070.

## Playing a match

You need two accounts (two players). Easiest way: open the app in two browser tabs/windows (or two different browsers) so each gets its own session cookie.

1. **Register** an account in each tab (username 3-20 chars letters/digits/underscore, password 8+ chars).
2. In tab 1, click **Create match** — you'll see a 6-character join code.
3. In tab 2, enter that code under **Join a match**.
4. Both tabs drop into the **draft**: a champion round, then 3 elite rounds. Each round shows both players' option pairs (transparency by design); click one of your two to pick.
5. Then **placement**: click a tile inside your highlighted zone to place each of your 14 units (champion → elites → 10 basics) in turn.
6. Then the match itself: click a unit → click an ability → click a target (legal tiles/units highlight green) → or **End Turn**. Combat prompts you to pick Strength/Agility/Intelligence when an attack lands.

First playthrough will be long — champions have hundreds of HP.

## Running the tests

```bash
cd ebic && mvn test     # Java: engine + web server (98 tests)
cd web && npm test      # TS: hex math (5 tests)
cd web && npm run build # TS: type-check + production build
```

## Troubleshooting

- **`npm run dev` starts but the browser shows a blank page / endless `504 Outdated Optimize Dep` errors**: stale Vite dependency cache, usually after switching Node versions or a long-running dev server surviving several source edits. Fix:
  ```bash
  cd web && rm -rf node_modules/.vite && npm run dev
  ```
- **Login/register button does nothing, no network request fires**: check the browser console — usernames over 20 characters get rejected client-side before any request is sent.
- **Port already in use** (`EADDRINUSE` / Vite's `strictPort` error): something's already listening.
  ```bash
  lsof -ti:5173 -sTCP:LISTEN | xargs -r kill   # frontend
  lsof -ti:7070 -sTCP:LISTEN | xargs -r kill   # backend
  ```
- **Want a clean slate** (wipe accounts/matches): stop the backend and delete `ebic/ebic.db`, then restart it — the schema is recreated automatically.

## Command-line mode (no browser)

The engine also runs standalone in a terminal, no server/DB/browser needed — useful for quickly sanity-checking engine changes:
```bash
cd ebic
mvn compile
GSON=$(find ~/.m2 -name 'gson-*.jar' | head -1)
java -cp target/classes:$GSON com.walnutt.Main         # minimal instant match
java -cp target/classes:$GSON com.walnutt.Main full    # full interactive draft + placement + match
```
