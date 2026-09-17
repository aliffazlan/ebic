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
  FavouriteUnitResponse,
  JoinMatchResponse,
  MatchInfo,
  PublicLobbiesResponse,
  StartLobbyResponse,
  UnitsResponse,
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

  /** The whole draftable roster as static design data - no match needed, so the codex can use it. */
  getUnits(): Promise<UnitsResponse> {
    return request<UnitsResponse>("/units");
  },

  /** null clears the favourite - that is what the "None" option sends. */
  setFavouriteUnit(definitionId: string | null): Promise<FavouriteUnitResponse> {
    return request<FavouriteUnitResponse>("/me/favourite", {
      method: "PUT",
      body: JSON.stringify({ definitionId }),
    });
  },

  createMatch(isPublic: boolean): Promise<CreateMatchResponse> {
    return request<CreateMatchResponse>("/matches", {
      method: "POST",
      body: JSON.stringify({ isPublic }),
    });
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

  /** Owner-only: flips a lobby from LOBBY to DRAFTING once both seats are filled. */
  startLobby(matchId: string): Promise<StartLobbyResponse> {
    return request<StartLobbyResponse>(`/matches/${encodeURIComponent(matchId)}/start`, { method: "POST" });
  },

  /** Best-effort: the owner leaving deletes the lobby, a joiner leaving frees their seat. */
  leaveLobby(matchId: string): Promise<void> {
    return request<void>(`/matches/${encodeURIComponent(matchId)}/leave`, { method: "POST" });
  },

  /** Open public lobbies for the Join Match browser - manual refresh only, no polling. */
  listPublicLobbies(): Promise<PublicLobbiesResponse> {
    return request<PublicLobbiesResponse>("/matches/public");
  },
};
