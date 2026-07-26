package com.walnutt.web;

import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.JsonObject;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.InputHandler;
import com.walnutt.unit.Unit;

/**
 * InputHandler backed by the WS bridge instead of a terminal Scanner. Every
 * chooseX method blocks the calling (dedicated match) thread on a per-team queue
 * fed by incoming WS messages until a *validly-shaped, referentially-correct*
 * response for that prompt arrives - see API_CONTRACT.md: "the client never
 * computes legality... the same prompt is effectively re-issued." Legality itself
 * (canUse/economy checks) stays TurnManager/Ability's job; this class only checks
 * that ids in the message actually resolve to something real for the right team.
 */
public final class WebInputHandler implements InputHandler {
    private final ChannelHub hub;
    private final UnitIdRegistry ids;
    private final Map<Team, BlockingQueue<JsonObject>> inbox = Map.of(
        Team.PLAYER_ONE, new LinkedBlockingQueue<>(),
        Team.PLAYER_TWO, new LinkedBlockingQueue<>()
    );

    public WebInputHandler(ChannelHub hub, UnitIdRegistry ids) {
        this.hub = hub;
        this.ids = ids;
    }

    /** Called by the WS message handler thread once a message has been parsed as valid JSON. */
    public void offer(Team team, JsonObject message) {
        inbox.get(team).offer(message);
    }

    @Override
    public ActionChoice chooseAction(GameState state, Player player) {
        Team team = player.getTeam();
        sendPrompt(team, promptOf("action", team));

        while (true) {
            JsonObject msg = awaitTyped(team, "action");
            if (msg == null) {
                continue;
            }
            String kind = optString(msg, "kind");
            if ("end_turn".equals(kind)) {
                hub.clearPrompt(team);
                return ActionChoice.endTurn();
            }
            if (!"ability".equals(kind)) {
                hub.sendTo(team, JsonSupport.messageEnvelope("Unrecognized action kind."));
                continue;
            }

            Unit unit = ids.resolve(optString(msg, "unitId"));
            if (unit == null || unit.getTeam() != team) {
                hub.sendTo(team, JsonSupport.messageEnvelope("That unit isn't yours to control."));
                continue;
            }
            Ability ability = findAbility(unit, optString(msg, "abilityId"));
            if (ability == null) {
                hub.sendTo(team, JsonSupport.messageEnvelope("That unit doesn't have that ability."));
                continue;
            }
            Target target = resolveTarget(state, msg);
            if (target == null) {
                hub.sendTo(team, JsonSupport.messageEnvelope("Invalid target."));
                continue;
            }

            hub.clearPrompt(team);
            return new ActionChoice(unit, ability, target);
        }
    }

    @Override
    public Attribute chooseAttribute(GameState state, Unit unit) {
        Team team = unit.getTeam();
        JsonObject prompt = promptOf("attribute", team);
        prompt.addProperty("unitId", ids.idFor(unit));
        sendPrompt(team, prompt);

        while (true) {
            JsonObject msg = awaitTyped(team, "attribute");
            if (msg == null) {
                continue;
            }
            String value = optString(msg, "value");
            try {
                Attribute attribute = Attribute.valueOf(value);
                hub.clearPrompt(team);
                return attribute;
            } catch (IllegalArgumentException | NullPointerException e) {
                hub.sendTo(team, JsonSupport.messageEnvelope("Choose STRENGTH, AGILITY, or INTELLIGENCE."));
            }
        }
    }

    @Override
    public UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options) {
        Team team = player.getTeam();
        sendPrompt(team, promptOf("pick", team));

        while (true) {
            JsonObject msg = awaitTyped(team, "pick");
            if (msg == null) {
                continue;
            }
            String definitionId = optString(msg, "definitionId");
            for (UnitDefinition option : options) {
                if (Identifiers.normalize(option.name()).equals(definitionId)) {
                    hub.clearPrompt(team);
                    return option;
                }
            }
            hub.sendTo(team, JsonSupport.messageEnvelope("That isn't one of your current draft options."));
        }
    }

    @Override
    public Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace, List<Tile> candidates) {
        Team team = player.getTeam();
        JsonObject prompt = promptOf("placement", team);
        prompt.addProperty("unitId", ids.idFor(unitToPlace));
        com.google.gson.JsonArray candidateArray = new com.google.gson.JsonArray();
        for (Tile tile : candidates) {
            JsonObject t = new JsonObject();
            t.addProperty("q", tile.getPosition().getQ());
            t.addProperty("r", tile.getPosition().getR());
            candidateArray.add(t);
        }
        prompt.add("candidates", candidateArray);
        sendPrompt(team, prompt);

        while (true) {
            JsonObject msg = awaitTyped(team, "placement");
            if (msg == null) {
                continue;
            }
            Integer q = optInt(msg, "q");
            Integer r = optInt(msg, "r");
            if (q == null || r == null) {
                hub.sendTo(team, JsonSupport.messageEnvelope("Placement requires q and r."));
                continue;
            }
            Position position = new Position(q, r);
            for (Tile candidate : candidates) {
                if (candidate.getPosition().equals(position)) {
                    hub.clearPrompt(team);
                    return candidate;
                }
            }
            hub.sendTo(team, JsonSupport.messageEnvelope("That tile isn't a legal placement."));
        }
    }

    private void sendPrompt(Team team, JsonObject payload) {
        String json = JsonSupport.envelope("prompt", payload);
        hub.cachePrompt(team, json);
        hub.sendTo(team, json);
    }

    private JsonObject promptOf(String kind, Team team) {
        JsonObject payload = new JsonObject();
        payload.addProperty("kind", kind);
        payload.addProperty("team", team.name());
        return payload;
    }

    /** Blocks for the next message from this team; returns null (caller loops) if it's not the expected type. */
    private JsonObject awaitTyped(Team team, String expectedType) {
        try {
            JsonObject msg = inbox.get(team).take();
            String type = optString(msg, "type");
            if (!expectedType.equals(type)) {
                hub.sendTo(team, JsonSupport.messageEnvelope("Not expecting a '" + type + "' message right now."));
                return null;
            }
            return msg;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for player input", e);
        }
    }

    private Ability findAbility(Unit unit, String abilityId) {
        if (abilityId == null) {
            return null;
        }
        for (Ability ability : unit.getAbilities()) {
            if (Identifiers.normalize(ability.getName()).equals(abilityId)) {
                return ability;
            }
        }
        return null;
    }

    private Target resolveTarget(GameState state, JsonObject msg) {
        String kind = optString(msg, "targetKind");
        if (kind == null) {
            return new NoTarget();
        }
        return switch (kind) {
            case "none" -> new NoTarget();
            case "unit" -> {
                Unit target = ids.resolve(optString(msg, "targetUnitId"));
                yield target == null ? null : new UnitTarget(target);
            }
            case "tile" -> {
                Integer q = optInt(msg, "q");
                Integer r = optInt(msg, "r");
                if (q == null || r == null) {
                    yield null;
                }
                Tile tile = state.getMap().getTile(new Position(q, r));
                yield tile == null ? null : new TileTarget(tile);
            }
            default -> null;
        };
    }

    private String optString(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsString();
        } catch (RuntimeException e) {
            return null;
        }
    }

    private Integer optInt(JsonObject obj, String key) {
        if (obj == null || !obj.has(key) || obj.get(key).isJsonNull()) {
            return null;
        }
        try {
            return obj.get(key).getAsInt();
        } catch (RuntimeException e) {
            return null;
        }
    }
}
