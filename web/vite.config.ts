/// <reference types="vitest/config" />
import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { defineConfig, type Plugin } from "vite";

const CHANGELOG_VIRTUAL_ID = "virtual:changelog";
const CHANGELOG_RESOLVED_ID = "\0" + CHANGELOG_VIRTUAL_ID;

/**
 * Exposes ../CHANGELOG.md (repo root, one level above this project's root) as
 * an importable string. A plain `?raw` import can't reach outside the
 * project root under Vite's default module resolution - this virtual module
 * sidesteps that entirely by reading the file directly via Node's fs, which
 * works identically in dev and in a production build.
 */
function changelogPlugin(): Plugin {
  return {
    name: "ebic-changelog",
    resolveId(id) {
      if (id === CHANGELOG_VIRTUAL_ID) return CHANGELOG_RESOLVED_ID;
    },
    load(id) {
      if (id === CHANGELOG_RESOLVED_ID) {
        const text = readFileSync(resolve(__dirname, "../CHANGELOG.md"), "utf-8");
        return `export default ${JSON.stringify(text)};`;
      }
    },
  };
}

// Proxies /api and /ws to the Java backend (http://localhost:7070) so that
// in dev, browser requests are same-origin from the client's point of view —
// required for the ebic_session cookie to actually be set/sent (see
// API_CONTRACT.md's auth model).
export default defineConfig({
  plugins: [changelogPlugin()],
  server: {
    proxy: {
      "/api": {
        target: "http://localhost:7070",
        changeOrigin: true,
      },
      "/ws": {
        target: "ws://localhost:7070",
        ws: true,
      },
    },
  },
  test: {
    environment: "node",
  },
});
