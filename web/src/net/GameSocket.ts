// WebSocket client for GET /ws/matches/:matchId (see API_CONTRACT.md).
// The cookie carries auth at handshake time automatically; no token needed.
// Dispatches incoming messages by `type` via a plain callback.
//
// Also owns two pieces of connection-resilience that didn't exist before and
// were the root cause of a real bug (both players randomly getting stuck on
// "Disconnected from match." with no way back in - see CLAUDE.md's Gotchas):
//  - A periodic heartbeat ping, purely to keep the connection's traffic
//    non-idle during a quiet stretch (a slow turn, an attribute encounter
//    waiting on the other player) - the server's WS idle timeout is
//    generous but finite (see WebServer.WS_IDLE_TIMEOUT), and with zero
//    app-level traffic in either direction it could still be reached.
//  - Auto-reconnect with backoff on any *unexpected* close (server idle
//    timeout, network blip, laptop sleep) - never on a deliberate close()
//    call (leaving the match / unmounting). The server already caches and
//    replays the last state/prompt/draft_round/placement_state to any
//    newly-registering channel for a team (ChannelHub.register), so a
//    reconnect looks identical to a fresh connect from the server's side -
//    this class just needed to actually attempt one.

import type { ClientMessage, ServerMessage } from "../types/contract";

export interface GameSocketHandlers {
  onMessage(msg: ServerMessage): void;
  onOpen?(): void;
  onClose?(event: CloseEvent): void;
  onError?(event: Event): void;
  /** Fired each time a reconnect attempt is scheduled after an unexpected close. */
  onReconnecting?(attempt: number, delayMs: number): void;
  /** Fired instead of onClose+reconnect when the server closes with an application-level
   * code (see FATAL_CLOSE_CODE_MIN below) - the match is gone or we're no longer a valid
   * participant, so retrying would just loop forever against the same rejection. */
  onFatalClose?(reason: string): void;
}

/** WebServer's own closeSession(...) calls (match finished/gone, not a participant - see
 * WebServer.java's onWsConnect/authorizeWsUpgrade) use codes >=4000, the range reserved for
 * private/application use; every other close (idle timeout, network blip, tab sleep) uses an
 * ordinary <4000 code and is exactly what auto-reconnect exists to recover from. */
const FATAL_CLOSE_CODE_MIN = 4000;

const HEARTBEAT_INTERVAL_MS = 30_000;
const RECONNECT_BASE_DELAY_MS = 1_000;
const RECONNECT_MAX_DELAY_MS = 15_000;

export class GameSocket {
  private ws: WebSocket | null = null;
  private readonly matchId: string;
  private readonly handlers: GameSocketHandlers;
  /** Only set for spectating a private match by code - the WS upgrade re-validates it
   * server-side, since a spectator is never a seated participant. */
  private readonly spectateCode?: string;
  private heartbeatTimer: number | null = null;
  private reconnectTimer: number | null = null;
  private reconnectAttempt = 0;
  // Set only by close() - distinguishes "the app asked us to disconnect"
  // (leaving the match, unmounting the screen) from "the socket dropped out
  // from under us," which is the only case that should auto-reconnect.
  private intentionalClose = false;

  constructor(matchId: string, handlers: GameSocketHandlers, spectateCode?: string) {
    this.matchId = matchId;
    this.handlers = handlers;
    this.spectateCode = spectateCode;
  }

  connect(): void {
    this.intentionalClose = false;
    this.openSocket();
  }

  private openSocket(): void {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const codeParam = this.spectateCode ? `?code=${encodeURIComponent(this.spectateCode)}` : "";
    const url = `${protocol}//${location.host}/ws/matches/${encodeURIComponent(this.matchId)}${codeParam}`;
    const ws = new WebSocket(url);

    ws.onopen = () => {
      this.reconnectAttempt = 0;
      this.startHeartbeat();
      this.handlers.onOpen?.();
    };
    ws.onclose = (event) => {
      this.stopHeartbeat();
      if (!this.intentionalClose && event.code >= FATAL_CLOSE_CODE_MIN) {
        this.handlers.onFatalClose?.(event.reason || "This match is no longer available.");
        return;
      }
      this.handlers.onClose?.(event);
      if (!this.intentionalClose) {
        this.scheduleReconnect();
      }
    };
    ws.onerror = (event) => this.handlers.onError?.(event);
    ws.onmessage = (event) => {
      let parsed: ServerMessage;
      try {
        parsed = JSON.parse(event.data as string) as ServerMessage;
      } catch (err) {
        console.error("GameSocket: failed to parse server message", err, event.data);
        return;
      }
      this.handlers.onMessage(parsed);
    };

    this.ws = ws;
  }

  private scheduleReconnect(): void {
    if (this.reconnectTimer !== null) return;
    this.reconnectAttempt += 1;
    const delay = Math.min(
      RECONNECT_BASE_DELAY_MS * 2 ** (this.reconnectAttempt - 1),
      RECONNECT_MAX_DELAY_MS,
    );
    this.handlers.onReconnecting?.(this.reconnectAttempt, delay);
    this.reconnectTimer = window.setTimeout(() => {
      this.reconnectTimer = null;
      this.openSocket();
    }, delay);
  }

  private startHeartbeat(): void {
    this.stopHeartbeat();
    this.heartbeatTimer = window.setInterval(() => this.send({ type: "ping" }), HEARTBEAT_INTERVAL_MS);
  }

  private stopHeartbeat(): void {
    if (this.heartbeatTimer !== null) {
      window.clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  send(msg: ClientMessage): void {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) {
      console.warn("GameSocket: dropped message, socket not open", msg);
      return;
    }
    this.ws.send(JSON.stringify(msg));
  }

  get isOpen(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  close(): void {
    this.intentionalClose = true;
    if (this.reconnectTimer !== null) {
      window.clearTimeout(this.reconnectTimer);
      this.reconnectTimer = null;
    }
    this.stopHeartbeat();
    this.ws?.close();
    this.ws = null;
  }
}
