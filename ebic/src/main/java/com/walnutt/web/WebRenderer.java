package com.walnutt.web;

import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.ui.Renderer;
import com.walnutt.web.dto.CombatLogBatch;
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
    // Set once by GameSession right after the match's GameState is created (this
    // renderer is constructed before that, so it can't be a constructor param) -
    // only renderDraftRound needs it, for the legacy hotseat/terminal DraftFlow
    // path (see GameStateSnapshotMapper.toDefinitionSnapshot's own doc comment).
    private Map<String, AbilityDefinition> abilityDefinitions = Map.of();

    public WebRenderer(ChannelHub hub, GameStateSnapshotMapper mapper, VfxCollector vfx, Consumer<GameState> onGameOver) {
        this.hub = hub;
        this.mapper = mapper;
        this.vfx = vfx;
        this.onGameOver = onGameOver;
    }

    public void setAbilityDefinitions(Map<String, AbilityDefinition> abilityDefinitions) {
        this.abilityDefinitions = abilityDefinitions;
    }

    @Override
    public void render(GameState state) {
        List<VfxEvent> events = vfx.drain();
        GameStateSnapshot snapshot = mapper.toSnapshot(state);
        if (!events.isEmpty()) {
            hub.broadcast(JsonSupport.envelope("vfx", events));
        }
        // Recorded on every tick, even when events is empty: a round can complete on a turn
        // with no vfx at all (a plain move), and commitCombatLog still needs that tick's call
        // client-side to open a new page at the right boundary - recording only non-empty
        // batches would silently drop those page breaks for a spectator replaying this history.
        hub.recordCombatLogBatch(JsonSupport.envelope("combat_log_batch", new CombatLogBatch(events, snapshot.currentTeam())));
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
        List<UnitDefinitionSnapshot> p1 = playerOneOptions.stream()
            .map(def -> GameStateSnapshotMapper.toDefinitionSnapshot(def, abilityDefinitions)).toList();
        List<UnitDefinitionSnapshot> p2 = playerTwoOptions.stream()
            .map(def -> GameStateSnapshotMapper.toDefinitionSnapshot(def, abilityDefinitions)).toList();

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
