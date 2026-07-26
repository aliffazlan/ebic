// WebSocket client for GET /ws/matches/:matchId (see API_CONTRACT.md).
// The cookie carries auth at handshake time automatically; no token needed.
// Dispatches incoming messages by `type` via a plain callback.

import type { ClientMessage, ServerMessage } from "../types/contract";

export interface GameSocketHandlers {
  onMessage(msg: ServerMessage): void;
  onOpen?(): void;
  onClose?(event: CloseEvent): void;
  onError?(event: Event): void;
}

export class GameSocket {
  private ws: WebSocket | null = null;
  private readonly matchId: string;
  private readonly handlers: GameSocketHandlers;

  constructor(matchId: string, handlers: GameSocketHandlers) {
    this.matchId = matchId;
    this.handlers = handlers;
  }

  connect(): void {
    const protocol = location.protocol === "https:" ? "wss:" : "ws:";
    const url = `${protocol}//${location.host}/ws/matches/${encodeURIComponent(this.matchId)}`;
    const ws = new WebSocket(url);

    ws.onopen = () => this.handlers.onOpen?.();
    ws.onclose = (event) => this.handlers.onClose?.(event);
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
    this.ws?.close();
    this.ws = null;
  }
}
