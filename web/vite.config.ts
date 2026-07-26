/// <reference types="vitest/config" />
import { defineConfig } from "vite";

// Proxies /api and /ws to the Java backend (http://localhost:7070) so that
// in dev, browser requests are same-origin from the client's point of view —
// required for the ebic_session cookie to actually be set/sent (see
// API_CONTRACT.md's auth model).
export default defineConfig({
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
