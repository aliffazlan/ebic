// Thin REST client for the HTTP endpoints in API_CONTRACT.md.
// Session auth is cookie-based (`ebic_session`, HttpOnly), so every request
// needs credentials:'include'; Vite's dev proxy (vite.config.ts) makes /api
// same-origin so the cookie actually gets set/sent in dev.

import type {
  ApiErrorBody,
  AuthUser,
  BotLevel,
  CreateBotMatchResponse,
  CreateMatchResponse,
  JoinMatchResponse,
  MatchInfo,
} from "../types/contract";

export class ApiError extends Error {
  readonly status: number;

  constructor(message: string, status: number) {
    super(message);
    this.name = "ApiError";
    this.status = status;
  }
}

async function request<T>(path: string, options: RequestInit = {}): Promise<T> {
  const res = await fetch(`/api${path}`, {
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(options.headers ?? {}),
    },
    ...options,
  });

  if (!res.ok) {
    let message = `Request failed (${res.status})`;
    try {
      const body = (await res.json()) as ApiErrorBody;
      if (body && typeof body.error === "string") {
        message = body.error;
      }
    } catch {
      // response wasn't JSON (e.g. a proxy/network error page) - keep the generic message
    }
    throw new ApiError(message, res.status);
  }

  if (res.status === 204) {
    return undefined as T;
  }
  return (await res.json()) as T;
}

export const api = {
  register(username: string, password: string): Promise<AuthUser> {
    return request<AuthUser>("/register", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  },

  login(username: string, password: string): Promise<AuthUser> {
    return request<AuthUser>("/login", {
      method: "POST",
      body: JSON.stringify({ username, password }),
    });
  },

  logout(): Promise<void> {
    return request<void>("/logout", { method: "POST" });
  },

  me(): Promise<AuthUser> {
    return request<AuthUser>("/me");
  },

  createMatch(): Promise<CreateMatchResponse> {
    return request<CreateMatchResponse>("/matches", { method: "POST" });
  },

  /** Starts a match against the computer. No join code and nobody to wait for - it is playable immediately. */
  createBotMatch(level: BotLevel): Promise<CreateBotMatchResponse> {
    return request<CreateBotMatchResponse>("/matches/bot", {
      method: "POST",
      body: JSON.stringify({ level }),
    });
  },

  joinMatch(joinCode: string): Promise<JoinMatchResponse> {
    return request<JoinMatchResponse>("/matches/join", {
      method: "POST",
      body: JSON.stringify({ joinCode }),
    });
  },

  getMatch(matchId: string): Promise<MatchInfo> {
    return request<MatchInfo>(`/matches/${encodeURIComponent(matchId)}`);
  },
};
