package com.walnutt.web;

import java.util.List;
import java.util.function.Consumer;

import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.ui.Renderer;
import com.walnutt.web.dto.DraftRoundSnapshot;
import com.walnutt.web.dto.GameStateSnapshot;
import com.walnutt.web.dto.UnitDefinitionSnapshot;
import com.walnutt.web.dto.VfxEvent;

/**
 * Renderer backed by the WS bridge. Broadcasts a "vfx" message (if any VFX
 * accumulated since the last render - see VfxCollector) immediately before the
 * "state" it reflects, per API_CONTRACT.md. renderDraftRound sends each player
 * their own perspective (yourOptions/opponentOptions are swapped per recipient),
 * not one shared broadcast.
 */
public final class WebRenderer implements Renderer {
    private final ChannelHub hub;
    private final GameStateSnapshotMapper mapper;
    private final VfxCollector vfx;
    private final Consumer<GameState> onGameOver;

    public WebRenderer(ChannelHub hub, GameStateSnapshotMapper mapper, VfxCollector vfx, Consumer<GameState> onGameOver) {
        this.hub = hub;
        this.mapper = mapper;
        this.vfx = vfx;
        this.onGameOver = onGameOver;
    }

    @Override
    public void render(GameState state) {
        List<VfxEvent> events = vfx.drain();
        if (!events.isEmpty()) {
            hub.broadcast(JsonSupport.envelope("vfx", events));
        }
        GameStateSnapshot snapshot = mapper.toSnapshot(state);
        String json = JsonSupport.envelope("state", snapshot);
        hub.cacheState(json);
        hub.broadcast(json);
    }

    @Override
    public void renderMessage(String message) {
        hub.broadcast(JsonSupport.messageEnvelope(message));
    }

    @Override
    public void renderGameOver(GameState state) {
        Player winner = state.getWinner();
        String winnerTeam = winner == null ? null : winner.getTeam().name();
        String winnerName = winner == null ? null : winner.getName();
        hub.broadcast(JsonSupport.envelope("game_over", new WinnerPayload(winnerTeam, winnerName)));
        onGameOver.accept(state);
    }

    private record WinnerPayload(String winnerTeam, String winnerName) {
    }

    @Override
    public void renderDraftRound(String roundLabel, Player playerOne, List<UnitDefinition> playerOneOptions,
                                  Player playerTwo, List<UnitDefinition> playerTwoOptions) {
        List<UnitDefinitionSnapshot> p1 = playerOneOptions.stream().map(GameStateSnapshotMapper::toDefinitionSnapshot).toList();
        List<UnitDefinitionSnapshot> p2 = playerTwoOptions.stream().map(GameStateSnapshotMapper::toDefinitionSnapshot).toList();

        sendDraftRound(Team.PLAYER_ONE, roundLabel, p1, p2);
        sendDraftRound(Team.PLAYER_TWO, roundLabel, p2, p1);
    }

    private void sendDraftRound(Team team, String roundLabel, List<UnitDefinitionSnapshot> yours,
                                 List<UnitDefinitionSnapshot> opponents) {
        DraftRoundSnapshot snapshot = new DraftRoundSnapshot(roundLabel, yours, opponents);
        String json = JsonSupport.envelope("draft_round", snapshot);
        hub.cacheDraftRound(team, json);
        hub.sendTo(team, json);
    }
}
