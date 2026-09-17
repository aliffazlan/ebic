package com.walnutt.web;

import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.time.Duration;
import java.util.Set;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import io.javalin.Javalin;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import io.javalin.websocket.WsCloseContext;
import io.javalin.websocket.WsConnectContext;
import io.javalin.websocket.WsMessageContext;

import com.walnutt.ai.BotLevel;
import com.walnutt.game.Team;
import com.walnutt.web.auth.AuthService;
import com.walnutt.web.db.Database;
import com.walnutt.web.match.MatchService;

/**
 * Wires Javalin: HTTP routes (auth + match lobby) and the WS game bridge
 * (/ws/matches/{matchId}). See API_CONTRACT.md for the exact shapes. Run with
 * `java -cp ... com.walnutt.web.WebServer` or `java -cp ... com.walnutt.Main
 * web`.
 */
public final class WebServer {
    private static final Logger LOG = System.getLogger(WebServer.class.getName());
    private static final String SESSION_COOKIE = "ebic_session";
    private static final int SESSION_MAX_AGE_SECONDS = 60 * 60 * 24 * 30;
    /**
     * Vite's default dev port. Adjust here (single source of truth) if the
     * frontend's dev port changes.
     */
    private static final Set<String> ALLOWED_ORIGINS = Set.of(
            "http://localhost:5173",
            "https://ebic.walnutt.net");
    /**
     * A real match can sit idle for a while (a player thinking through a turn, an
     * attribute encounter waiting on the other side, someone stepping away) with no
     * WS traffic in either direction - Jetty's own default WS idle timeout is far
     * shorter than that and was silently closing sockets mid-match (client saw
     * "Disconnected from match." with no way back in, since nothing sent traffic to
     * reset the timer). Set generously long here as defense in depth; the client
     * also now sends a periodic heartbeat (see GameSocket.ts) so idle timeout
     * shouldn't be hit in practice at all during a connected, healthy session -
     * this is the backstop for whatever traffic pattern doesn't anticipate.
     */
    private static final Duration WS_IDLE_TIMEOUT = Duration.ofMinutes(20);

    private final AuthService auth;
    private final MatchService matches;
    private final UnitCatalog catalog = new UnitCatalog();
    private final GameSessionManager sessions;
    private final Javalin app;

    public WebServer(Database db) {
        this.auth = new AuthService(db);
        this.matches = new MatchService(db);
        this.matches.purgeStaleMatches();
        this.sessions = new GameSessionManager(matches, auth, catalog);
        this.app = Javalin.create(cfg -> {
            cfg.showJavalinBanner = false;
            cfg.jetty.modifyWebSocketServletFactory(factory -> factory.setIdleTimeout(WS_IDLE_TIMEOUT));
        });
        registerRoutes();
    }

    public WebServer start(int port) {
        app.start(port);
        return this;
    }

    public void stop() {
        app.stop();
    }

    private void registerRoutes() {
        app.exception(ApiException.class, (e, ctx) -> sendError(ctx, e.getStatus(), e.getMessage()));
        app.exception(Exception.class, (e, ctx) -> {
            LOG.log(Level.ERROR, "Unhandled exception on " + ctx.method() + " " + ctx.path(), e);
            sendError(ctx, 500, "internal server error");
        });

        app.post("/api/register", this::handleRegister);
        app.post("/api/login", this::handleLogin);
        app.post("/api/logout", this::handleLogout);
        app.get("/api/me", this::handleMe);
        app.put("/api/me/favourite", this::handleSetFavouriteUnit);
        app.get("/api/units", this::handleUnits);
        app.post("/api/matches", this::handleCreateMatch);
        app.post("/api/matches/bot", this::handleCreateBotMatch);
        app.post("/api/matches/join", this::handleJoinMatch);
        app.get("/api/matches/public", this::handleListPublicMatches);
        app.get("/api/matches/{matchId}", this::handleMatchStatus);
        app.post("/api/matches/{matchId}/start", this::handleStartLobby);
        app.post("/api/matches/{matchId}/leave", this::handleLeaveLobby);
        app.post("/api/matches/{matchId}/visibility", this::handleSetVisibility);
        app.post("/api/matches/{matchId}/join", this::handleJoinPublicLobby);

        app.wsBeforeUpgrade("/ws/matches/{matchId}", this::authorizeWsUpgrade);
        app.ws("/ws/matches/{matchId}", ws -> {
            ws.onConnect(this::onWsConnect);
            ws.onMessage(this::onWsMessage);
            ws.onClose(this::onWsClose);
        });
    }

    // ---- HTTP handlers ----

    private void handleRegister(Context ctx) {
        JsonObject body = readJsonBody(ctx);
        String username = JsonSupport.optString(body, "username");
        String password = JsonSupport.optString(body, "password");
        AuthService.SessionResult result = auth.register(username, password);
        setSessionCookie(ctx, result.token());
        JsonObject payload = new JsonObject();
        payload.addProperty("userId", result.user().userId());
        payload.addProperty("username", result.user().username());
        sendJson(ctx, 201, payload);
    }

    private void handleLogin(Context ctx) {
        JsonObject body = readJsonBody(ctx);
        String username = JsonSupport.optString(body, "username");
        String password = JsonSupport.optString(body, "password");
        AuthService.SessionResult result = auth.login(username, password, clientIp(ctx));
        setSessionCookie(ctx, result.token());
        JsonObject payload = new JsonObject();
        payload.addProperty("userId", result.user().userId());
        payload.addProperty("username", result.user().username());
        sendJson(ctx, 200, payload);
    }

    private void handleLogout(Context ctx) {
        String token = ctx.cookie(SESSION_COOKIE);
        auth.logout(token);
        ctx.removeCookie(SESSION_COOKIE);
        ctx.status(204);
    }

    private void handleMe(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        JsonObject payload = new JsonObject();
        payload.addProperty("userId", user.userId());
        payload.addProperty("username", user.username());
        payload.addProperty("favouriteUnit", auth.getFavouriteUnit(user.userId()));
        sendJson(ctx, 200, payload);
    }

    /**
     * A null/absent definitionId clears the favourite, which is what the client's
     * "None"
     * option sends. Validated against the same catalogue the info page serves, so a
     * hero
     * nobody can be dealt (Shawl, a summon prototype) can never be stored either.
     */
    private void handleSetFavouriteUnit(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        String definitionId = JsonSupport.optString(readJsonBody(ctx), "definitionId");
        if (definitionId != null && definitionId.isBlank()) {
            definitionId = null;
        }
        if (definitionId != null && !catalog.isDraftable(definitionId)) {
            throw new ApiException(400, "no such draftable unit");
        }
        auth.setFavouriteUnit(user.userId(), definitionId);
        JsonObject payload = new JsonObject();
        payload.addProperty("favouriteUnit", definitionId);
        sendJson(ctx, 200, payload);
    }

    /**
     * The whole draftable roster as static design data - stats plus ability text,
     * no match needed.
     */
    private void handleUnits(Context ctx) {
        requireAuth(ctx);
        JsonObject payload = new JsonObject();
        payload.add("units", JsonSupport.GSON.toJsonTree(catalog.draftableUnits()));
        sendJson(ctx, 200, payload);
    }

    private void handleCreateMatch(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        JsonObject body = readJsonBody(ctx);
        boolean isPublic = body.has("isPublic") && body.get("isPublic").getAsBoolean();
        MatchService.MatchSummary summary = matches.createMatch(user.userId(), isPublic);
        JsonObject payload = new JsonObject();
        payload.addProperty("matchId", summary.matchId());
        payload.addProperty("joinCode", summary.joinCode());
        payload.addProperty("status", summary.status());
        payload.addProperty("isPublic", summary.isPublic());
        sendJson(ctx, 201, payload);
    }

    private void handleStartLobby(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        String matchId = ctx.pathParam("matchId");
        MatchService.MatchSummary summary = matches.startLobby(user.userId(), matchId);
        JsonObject payload = new JsonObject();
        payload.addProperty("matchId", summary.matchId());
        payload.addProperty("status", summary.status());
        sendJson(ctx, 200, payload);
    }

    private void handleLeaveLobby(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        String matchId = ctx.pathParam("matchId");
        matches.leaveLobby(user.userId(), matchId);
        ctx.status(204);
    }

    private void handleSetVisibility(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        String matchId = ctx.pathParam("matchId");
        JsonObject body = readJsonBody(ctx);
        boolean isPublic = body.has("isPublic") && body.get("isPublic").getAsBoolean();
        matches.setVisibility(user.userId(), matchId, isPublic);
        JsonObject payload = new JsonObject();
        payload.addProperty("matchId", matchId);
        payload.addProperty("isPublic", isPublic);
        sendJson(ctx, 200, payload);
    }

    private void handleJoinPublicLobby(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        String matchId = ctx.pathParam("matchId");
        MatchService.MatchSummary summary = matches.joinPublicLobby(user.userId(), matchId);
        JsonObject payload = new JsonObject();
        payload.addProperty("matchId", summary.matchId());
        payload.addProperty("status", summary.status());
        sendJson(ctx, 200, payload);
    }

    /**
     * The display label passes the raw status straight through as its own value - LOBBY,
     * DRAFTING, and IN_PROGRESS (rendered "IN PROGRESS") are all shown distinctly rather than
     * collapsing DRAFTING into "IN PROGRESS". `full` is reported separately so the client can
     * grey out a full-but-unstarted LOBBY without mislabeling it as already underway.
     */
    private void handleListPublicMatches(Context ctx) {
        requireAuth(ctx);
        JsonObject payload = new JsonObject();
        com.google.gson.JsonArray lobbies = new com.google.gson.JsonArray();
        for (MatchService.PublicLobbySummary lobby : matches.listPublicLobbies()) {
            JsonObject row = new JsonObject();
            row.addProperty("matchId", lobby.matchId());
            row.addProperty("joinCode", lobby.joinCode());
            String status = lobby.status();
            row.addProperty("status", MatchService.Status.IN_PROGRESS.name().equals(status) ? "IN PROGRESS" : status);
            row.addProperty("playerOneName", lobby.playerOneName());
            row.addProperty("full", lobby.full());
            lobbies.add(row);
        }
        payload.add("lobbies", lobbies);
        sendJson(ctx, 200, payload);
    }

    /**
     * Starts a match against the computer. The level is accepted and validated even
     * though
     * STANDARD is currently the only value, so introducing another difficulty later
     * is a
     * new enum constant rather than a change to this contract or to the client.
     */
    private void handleCreateBotMatch(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        JsonObject body = readJsonBody(ctx);
        String requested = JsonSupport.optString(body, "level");
        BotLevel level;
        try {
            level = BotLevel.parse(requested);
        } catch (IllegalArgumentException e) {
            throw new ApiException(400, e.getMessage());
        }
        MatchService.MatchSummary summary = matches.createBotMatch(user.userId(), level.name());
        JsonObject payload = new JsonObject();
        payload.addProperty("matchId", summary.matchId());
        payload.addProperty("status", summary.status());
        payload.addProperty("level", level.name());
        sendJson(ctx, 201, payload);
    }

    private void handleJoinMatch(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        JsonObject body = readJsonBody(ctx);
        String joinCode = JsonSupport.optString(body, "joinCode");
        MatchService.MatchSummary summary = matches.joinMatch(user.userId(), joinCode);
        JsonObject payload = new JsonObject();
        payload.addProperty("matchId", summary.matchId());
        payload.addProperty("status", summary.status());
        sendJson(ctx, 200, payload);
    }

    private void handleMatchStatus(Context ctx) {
        AuthService.AuthedUser user = requireAuth(ctx);
        String matchId = ctx.pathParam("matchId");
        MatchService.MatchStatusView view = matches.getStatus(user.userId(), matchId);
        JsonObject payload = new JsonObject();
        payload.addProperty("matchId", view.matchId());
        payload.addProperty("status", view.status());
        payload.addProperty("playerOneName", view.playerOneName());
        payload.addProperty("playerTwoName", view.playerTwoName());
        payload.addProperty("yourTeam", view.yourTeam());
        payload.addProperty("winnerName", view.winnerName());
        payload.addProperty("isPublic", view.isPublic());
        sendJson(ctx, 200, payload);
    }

    // ---- WS handlers ----

    /**
     * Registered via wsBeforeUpgrade (NOT a plain before() filter - confirmed
     * empirically that a plain before() on the WS path does not stop the handshake;
     * Javalin still returns 101 Switching Protocols and only closes the socket
     * afterwards). wsBeforeUpgrade runs on the plain HTTP upgrade request itself,
     * so
     * an invalid session or non-participant is rejected with a real HTTP 401/403
     * *before* the WS handshake completes, per API_CONTRACT.md ("reject the upgrade
     * with HTTP 401"). Note: Javalin's WsContext does NOT reliably inherit
     * ctx.attribute(...) set here either (also confirmed empirically) - so this is
     * purely the fail-fast HTTP-level gate; onWsConnect independently re-resolves
     * the session from the cookie rather than trusting anything stashed here.
     */
    private void authorizeWsUpgrade(Context ctx) {
        String origin = ctx.header("Origin");
        if (origin != null && !ALLOWED_ORIGINS.contains(origin)) {
            throw new ApiException(403, "origin not allowed");
        }
        AuthService.AuthedUser user = requireAuth(ctx);
        String matchId = ctx.pathParam("matchId");
        MatchService.MatchParticipants participants = matches.getParticipants(matchId)
                .orElseThrow(() -> new ApiException(401, "not a participant in this match"));
        if (user.userId() != participants.playerOneId() && user.userId() != participants.playerTwoId()) {
            throw new ApiException(401, "not a participant in this match");
        }
    }

    private void onWsConnect(WsConnectContext ctx) {
        AuthService.AuthedUser user = auth.resolveSession(ctx.cookie(SESSION_COOKIE)).orElse(null);
        String matchId = ctx.pathParam("matchId");
        if (user == null) {
            ctx.closeSession(4401, "unauthorized");
            return;
        }
        GameSession session;
        try {
            session = sessions.getOrCreate(matchId);
        } catch (ApiException e) {
            ctx.closeSession(4409, e.getMessage());
            return;
        }
        Team team = session.teamFor(user.userId());
        if (team == null) {
            ctx.closeSession(4401, "not a participant");
            return;
        }
        ClientChannel channel = new JavalinClientChannel(ctx);
        session.registerChannel(team, channel);
        ctx.attribute("session", session);
        ctx.attribute("team", team);
        ctx.attribute("channel", channel);
    }

    private void onWsMessage(WsMessageContext ctx) {
        GameSession session = ctx.attribute("session");
        Team team = ctx.attribute("team");
        if (session == null || team == null) {
            return;
        }
        JsonElement parsed;
        try {
            parsed = JsonSupport.parse(ctx.message());
        } catch (JsonSyntaxException e) {
            ctx.send(JsonSupport.messageEnvelope("Malformed message - could not parse JSON."));
            return;
        }
        if (!parsed.isJsonObject()) {
            ctx.send(JsonSupport.messageEnvelope("Malformed message - expected a JSON object."));
            return;
        }
        JsonObject obj = parsed.getAsJsonObject();
        // Client heartbeat (see GameSocket.ts) - purely to keep the connection's
        // traffic non-idle during quiet stretches (see WS_IDLE_TIMEOUT above).
        // Handled here, before it ever reaches WebInputHandler's per-team queue,
        // since that queue only expects real game messages - offering a "ping"
        // onto it would make whichever chooseX call is currently blocked reject
        // it as "Not expecting a 'ping' message right now." for no reason.
        if (obj.has("type") && obj.get("type").isJsonPrimitive() && "ping".equals(obj.get("type").getAsString())) {
            return;
        }
        try {
            session.handleMessage(team, obj);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "Error handling WS message for match " + session.getMatchId(), e);
            ctx.send(JsonSupport.messageEnvelope("That message could not be processed."));
        }
    }

    private void onWsClose(WsCloseContext ctx) {
        GameSession session = ctx.attribute("session");
        Team team = ctx.attribute("team");
        ClientChannel channel = ctx.attribute("channel");
        if (session != null && team != null && channel != null) {
            session.unregisterChannel(team, channel);
        }
    }

    // ---- helpers ----

    private AuthService.AuthedUser requireAuth(Context ctx) {
        String token = ctx.cookie(SESSION_COOKIE);
        return auth.resolveSession(token).orElseThrow(() -> new ApiException(401, "not authenticated"));
    }

    private void setSessionCookie(Context ctx, String token) {
        ctx.cookie(new Cookie(SESSION_COOKIE, token, "/", SESSION_MAX_AGE_SECONDS, false, 0, true, null, null,
                SameSite.LAX));
    }

    private String clientIp(Context ctx) {
        String forwarded = ctx.header("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return ctx.ip();
    }

    private JsonObject readJsonBody(Context ctx) {
        try {
            JsonElement parsed = JsonSupport.parse(ctx.body());
            if (!parsed.isJsonObject()) {
                throw new ApiException(400, "expected a JSON object body");
            }
            return parsed.getAsJsonObject();
        } catch (JsonSyntaxException e) {
            throw new ApiException(400, "malformed JSON body");
        }
    }

    private void sendJson(Context ctx, int status, JsonObject payload) {
        ctx.status(status).contentType("application/json").result(JsonSupport.GSON.toJson(payload));
    }

    private void sendError(Context ctx, int status, String message) {
        JsonObject payload = new JsonObject();
        payload.addProperty("error", message);
        sendJson(ctx, status, payload);
    }

    public static void main(String[] args) {
        Database db = new Database("ebic.db");
        db.migrate();
        new WebServer(db).start(7070);
        LOG.log(Level.INFO, "EBIC web server listening on http://localhost:7070");
    }
}
