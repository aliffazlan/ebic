package com.walnutt.web;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.DefaultArrangement;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.ui.ActionChoice;
import com.walnutt.ui.ChoiceOption;
import com.walnutt.ui.ConcurrentSetupHandler;
import com.walnutt.ui.InputHandler;
import com.walnutt.unit.Unit;
import com.walnutt.web.dto.PlacementStateSnapshot;
import com.walnutt.web.dto.PlacementUnitSnapshot;
import com.walnutt.web.dto.PlayerDraftRoundSnapshot;
import com.walnutt.web.dto.TilePosition;
import com.walnutt.web.dto.UnitDefinitionSnapshot;

/**
 * InputHandler backed by the WS bridge instead of a terminal Scanner. Every
 * chooseX method blocks the calling (dedicated match) thread on a per-team queue
 * fed by incoming WS messages until a *validly-shaped, referentially-correct*
 * response for that prompt arrives - see API_CONTRACT.md: "the client never
 * computes legality... the same prompt is effectively re-issued." Legality itself
 * (canUse/economy checks) stays TurnManager/Ability's job; this class only checks
 * that ids in the message actually resolve to something real for the right team.
 *
 * Also implements ConcurrentSetupHandler (see game/ConcurrentSetupFlow) - the
 * draft-pick and placement-arrangement side of the newer, per-player-paced
 * setup flow. choosePick/arrangePlacement are called from TWO DIFFERENT
 * THREADS concurrently (one per team - ConcurrentSetupFlow runs each player's
 * setup on its own dedicated thread), but since every per-team queue in
 * `inbox` is only ever touched by that team's own thread plus the WS message
 * thread offering into it, no extra synchronization is needed here beyond
 * what the InputHandler methods already rely on.
 */
public final class WebInputHandler implements InputHandler, ConcurrentSetupHandler {
    /** See computeLegalPlacementTiles's javadoc for why this value specifically. */
    private static final int PLACEMENT_ZONE_RADIUS = 6;

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
        JsonObject prompt = promptOf("action", team);
        prompt.add("legalTargets", buildLegalTargets(state, player));
        sendPrompt(team, prompt);

        while (true) {
            JsonObject msg = awaitTyped(team, "action");
            if (msg == null) {
                continue;
            }
            String kind = JsonSupport.optString(msg, "kind");
            if ("end_turn".equals(kind)) {
                hub.clearPrompt(team);
                return ActionChoice.endTurn();
            }
            if (!"ability".equals(kind)) {
                hub.sendTo(team, JsonSupport.messageEnvelope("Unrecognized action kind."));
                continue;
            }

            Unit unit = ids.resolve(JsonSupport.optString(msg, "unitId"));
            if (unit == null || unit.getTeam() != team) {
                hub.sendTo(team, JsonSupport.messageEnvelope("That unit isn't yours to control."));
                continue;
            }
            Ability ability = findAbility(unit, JsonSupport.optString(msg, "abilityId"));
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
    public Attribute chooseAttribute(GameState state, Unit unit, Unit opponent) {
        List<Attribute> usable = unit.getUsableAttributes();
        if (usable.isEmpty()) {
            return null; // nothing to bring to the encounter - never prompt for a non-choice
        }
        Team team = unit.getTeam();
        JsonObject prompt = promptOf("attribute", team);
        prompt.addProperty("unitId", ids.idFor(unit));
        prompt.addProperty("opponentUnitId", ids.idFor(opponent));
        // The client greys out anything not in here rather than deriving the rule from the
        // unit's stats itself - same "the client never computes legality" principle as
        // legalTargets.
        JsonArray selectable = new JsonArray();
        for (Attribute attribute : usable) {
            selectable.add(attribute.name());
        }
        prompt.add("selectableAttributes", selectable);
        sendPrompt(team, prompt);

        while (true) {
            JsonObject msg = awaitTyped(team, "attribute");
            if (msg == null) {
                continue;
            }
            String value = JsonSupport.optString(msg, "value");
            Attribute attribute = null;
            try {
                attribute = Attribute.valueOf(value);
            } catch (IllegalArgumentException | NullPointerException e) {
                attribute = null;
            }
            if (attribute == null || !usable.contains(attribute)) {
                hub.sendTo(team, JsonSupport.messageEnvelope(
                    "This unit can only attack or defend with: " + describe(usable) + "."));
                continue;
            }
            hub.clearPrompt(team);
            return attribute;
        }
    }

    private static String describe(List<Attribute> attributes) {
        return attributes.stream().map(Attribute::name).collect(java.util.stream.Collectors.joining(", "));
    }

    /**
     * Requests both sides' attribute picks concurrently (one thread per team) instead
     * of the InputHandler default's sequential behavior - so both players see the
     * encounter strobe/modal at the same moment rather than the defender only finding
     * out after the attacker has already answered. Safe by the same reasoning as
     * ConcurrentSetupFlow's thread-per-player setup: attacker and defender are always
     * on different teams (Attack.canUse rejects same-team targets), so the two
     * chooseAttribute calls only ever touch their own team's queue/channel.
     */
    @Override
    public Attribute[] chooseAttributePair(GameState state, Unit attacker, Unit defender) {
        Attribute[] results = new Attribute[2];
        Thread attackerThread = new Thread(() -> results[0] = chooseAttribute(state, attacker, defender));
        Thread defenderThread = new Thread(() -> results[1] = chooseAttribute(state, defender, attacker));
        attackerThread.start();
        defenderThread.start();
        try {
            attackerThread.join();
            defenderThread.join();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while waiting for attribute choices", e);
        }
        return results;
    }

    /**
     * The generic option dialogue, raised from inside an ability's own onUse (Maxwell's
     * Eureka). Blocks this match's thread on the acting team's queue exactly like every
     * other prompt, so a mid-cast dialogue behaves the same as an attribute encounter -
     * including reconnect, since ChannelHub caches the outstanding prompt and replays it.
     */
    @Override
    public ChoiceOption chooseOption(GameState state, Unit unit, String title, List<ChoiceOption> options) {
        if (options.isEmpty()) {
            return null;
        }
        Team team = unit.getTeam();
        JsonObject prompt = promptOf("choice", team);
        prompt.addProperty("unitId", ids.idFor(unit));
        prompt.addProperty("title", title);
        JsonArray optionArray = new JsonArray();
        for (ChoiceOption option : options) {
            JsonObject entry = new JsonObject();
            entry.addProperty("id", option.id());
            entry.addProperty("name", option.name());
            entry.addProperty("description", option.description());
            entry.addProperty("detail", option.detail());
            optionArray.add(entry);
        }
        prompt.add("options", optionArray);
        sendPrompt(team, prompt);

        while (true) {
            JsonObject msg = awaitTyped(team, "choice");
            if (msg == null) {
                continue;
            }
            String optionId = JsonSupport.optString(msg, "optionId");
            for (ChoiceOption option : options) {
                if (option.id().equals(optionId)) {
                    hub.clearPrompt(team);
                    return option;
                }
            }
            hub.sendTo(team, JsonSupport.messageEnvelope("That isn't one of the options offered."));
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
            String definitionId = JsonSupport.optString(msg, "definitionId");
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
        JsonArray candidateArray = new JsonArray();
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
            Integer q = JsonSupport.optInt(msg, "q");
            Integer r = JsonSupport.optInt(msg, "r");
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

    // ---- ConcurrentSetupHandler ----

    /**
     * One player's own draft round - see API_CONTRACT.md's rewritten
     * "draft_round" shape ({roundLabel, options}, no opponentOptions since
     * each player now drafts at their own pace). Receiving this message
     * doubles as the prompt to pick; the client responds with the same
     * "pick" message shape the old flow used.
     */
    @Override
    public UnitDefinition choosePick(GameState state, Player player, String roundLabel,
                                      List<UnitDefinition> options, List<UnitDefinition> opponentOptions) {
        Team team = player.getTeam();
        List<UnitDefinitionSnapshot> optionSnapshots = options.stream()
            .map(def -> GameStateSnapshotMapper.toDefinitionSnapshot(def, state.getAbilityDefinitions()))
            .toList();
        List<UnitDefinitionSnapshot> opponentOptionSnapshots = opponentOptions.stream()
            .map(def -> GameStateSnapshotMapper.toDefinitionSnapshot(def, state.getAbilityDefinitions()))
            .toList();
        String json = JsonSupport.envelope("draft_round",
            new PlayerDraftRoundSnapshot(roundLabel, optionSnapshots, opponentOptionSnapshots));
        hub.cacheDraftRound(team, json);
        hub.sendTo(team, json);

        while (true) {
            JsonObject msg = awaitTyped(team, "pick");
            if (msg == null) {
                continue;
            }
            String definitionId = JsonSupport.optString(msg, "definitionId");
            for (UnitDefinition option : options) {
                if (Identifiers.normalize(option.name()).equals(definitionId)) {
                    return option;
                }
            }
            hub.sendTo(team, JsonSupport.messageEnvelope("That isn't one of your current draft options."));
        }
    }

    /**
     * Shows this player their default arrangement, then loops accepting
     * swap/move edits (each pushing a fresh placement_state) until a confirm
     * arrives, at which point a final placement_state (confirmed: true) is
     * pushed and the working copy is returned. Deliberately never touches
     * GameMap/Tile occupancy - ConcurrentSetupFlow commits the returned map
     * to the real board itself once this call returns. The working copy is a
     * local, mutable, per-call Map, so despite this method being invoked
     * concurrently for both teams, there's no shared mutable state between
     * the two calls beyond the (per-team) queue/channel plumbing already
     * safe for concurrent use.
     */
    @Override
    public Map<Unit, Position> arrangePlacement(GameState state, Player player, Map<Unit, Position> defaultArrangement) {
        Team team = player.getTeam();
        Map<Unit, Position> working = new LinkedHashMap<>(defaultArrangement);
        List<TilePosition> legalTiles = computeLegalPlacementTiles(state, player);
        pushPlacementState(team, player, working, false, legalTiles);

        while (true) {
            JsonObject msg = awaitTyped(team, "placement_edit");
            if (msg == null) {
                continue;
            }
            String kind = JsonSupport.optString(msg, "kind");
            if (kind == null) {
                kind = "";
            }
            switch (kind) {
                case "swap" -> {
                    if (applyPlacementSwap(team, working, msg)) {
                        pushPlacementState(team, player, working, false, legalTiles);
                    }
                }
                case "move" -> {
                    if (applyPlacementMove(state, player, team, working, msg)) {
                        pushPlacementState(team, player, working, false, legalTiles);
                    }
                }
                case "confirm" -> {
                    pushPlacementState(team, player, working, true, legalTiles);
                    return new LinkedHashMap<>(working);
                }
                default -> hub.sendTo(team, JsonSupport.messageEnvelope("Unrecognized placement edit kind."));
            }
        }
    }

    /**
     * Every walkable tile within PLACEMENT_ZONE_RADIUS of this player's own
     * DefaultArrangement anchor - the bound a "move" edit must stay inside, and
     * what the client highlights. Generous relative to what DefaultArrangement
     * itself actually uses (empirically its 14-unit layout never exceeds
     * radius 3 from the anchor on a radius-8 map) so there's real room to
     * rearrange, while still being unambiguously "this player's own corner" -
     * opposite-corner anchors are 2x map radius apart - 14 on the current board - so
     * two radius-6 zones (12 combined) still cannot overlap, with 2 tiles to spare.
     */
    private List<TilePosition> computeLegalPlacementTiles(GameState state, Player player) {
        Position anchor = DefaultArrangement.anchorFor(state, player);
        List<TilePosition> tiles = new ArrayList<>();
        for (Tile tile : state.getMap().getTilesInRadius(anchor, PLACEMENT_ZONE_RADIUS)) {
            if (tile.isWalkable()) {
                tiles.add(new TilePosition(tile.getPosition().getQ(), tile.getPosition().getR()));
            }
        }
        return tiles;
    }

    /** Exchanges two of this player's own working-copy positions. Returns false (and sends a rejection) if invalid. */
    private boolean applyPlacementSwap(Team team, Map<Unit, Position> working, JsonObject msg) {
        Unit unit = ids.resolve(JsonSupport.optString(msg, "unitId"));
        Unit target = ids.resolve(JsonSupport.optString(msg, "targetUnitId"));
        if (unit == null || target == null || !working.containsKey(unit) || !working.containsKey(target)) {
            hub.sendTo(team, JsonSupport.messageEnvelope("That swap isn't valid - both units must be your own already-placed units."));
            return false;
        }
        Position unitPos = working.get(unit);
        Position targetPos = working.get(target);
        working.put(unit, targetPos);
        working.put(target, unitPos);
        return true;
    }

    /**
     * Relocates one of this player's own working-copy units to any real, walkable
     * tile within PLACEMENT_ZONE_RADIUS of this player's own anchor, not already
     * occupied by one of this player's OTHER working-copy units. The occupancy
     * check is against the working copy's own position set, not real GameMap
     * occupancy - nothing has been committed to the real map yet at this point in
     * the flow, so real-map occupancy would be meaningless here.
     */
    private boolean applyPlacementMove(GameState state, Player player, Team team, Map<Unit, Position> working, JsonObject msg) {
        Unit unit = ids.resolve(JsonSupport.optString(msg, "unitId"));
        if (unit == null || !working.containsKey(unit)) {
            hub.sendTo(team, JsonSupport.messageEnvelope("That unit isn't yours to place."));
            return false;
        }
        Integer q = JsonSupport.optInt(msg, "q");
        Integer r = JsonSupport.optInt(msg, "r");
        if (q == null || r == null) {
            hub.sendTo(team, JsonSupport.messageEnvelope("Placement move requires q and r."));
            return false;
        }
        Position target = new Position(q, r);
        Tile tile = state.getMap().getTile(target);
        if (tile == null || !tile.isWalkable()) {
            hub.sendTo(team, JsonSupport.messageEnvelope("That tile isn't a legal placement target."));
            return false;
        }
        Position anchor = DefaultArrangement.anchorFor(state, player);
        if (state.getMap().getDistance(anchor, target) > PLACEMENT_ZONE_RADIUS) {
            hub.sendTo(team, JsonSupport.messageEnvelope("That tile is outside your placement area."));
            return false;
        }
        for (Map.Entry<Unit, Position> entry : working.entrySet()) {
            if (entry.getKey() != unit && target.equals(entry.getValue())) {
                hub.sendTo(team, JsonSupport.messageEnvelope("That tile is already occupied by one of your own units."));
                return false;
            }
        }
        working.put(unit, target);
        return true;
    }

    /** Pushes this player's own current working arrangement - fog of war, only ever this player's units. */
    private void pushPlacementState(Team team, Player player, Map<Unit, Position> working, boolean confirmed, List<TilePosition> legalTiles) {
        List<PlacementUnitSnapshot> unitSnapshots = new ArrayList<>();
        for (Unit unit : player.getUnits()) {
            Position pos = working.get(unit);
            unitSnapshots.add(new PlacementUnitSnapshot(
                ids.idFor(unit),
                unit.getName(),
                GameStateSnapshotMapper.definitionId(unit),
                unit.getUnitType().name(),
                pos.getQ(),
                pos.getR()
            ));
        }
        String json = JsonSupport.envelope("placement_state", new PlacementStateSnapshot(unitSnapshots, confirmed, legalTiles));
        hub.cachePlacementState(team, json);
        hub.sendTo(team, json);
    }

    private void sendPrompt(Team team, JsonObject payload) {
        String json = JsonSupport.envelope("prompt", payload);
        hub.cachePrompt(team, json);
        hub.sendTo(team, json);
    }

    /**
     * Per unitId, per abilityId: which tiles/units are actually legal to click, so the
     * client can highlight them instead of just accept-then-reject on a bad click. Scoped
     * to this player's own units only (the opponent's units aren't actionable this turn
     * anyway) and to ready active abilities only (a passive or on-cooldown ability has
     * nothing worth highlighting). See Ability.getLegalTargets - this is the one place
     * that consumes it.
     */
    private JsonObject buildLegalTargets(GameState state, Player player) {
        JsonObject byUnit = new JsonObject();
        for (Unit unit : player.getUnits()) {
            if (unit.isDead()) {
                continue;
            }
            JsonObject byAbility = new JsonObject();
            for (Ability ability : unit.getActiveAbilities()) {
                if (!ability.isReady()) {
                    continue;
                }
                List<Target> targets = ability.getLegalTargets(state);
                if (targets.isEmpty()) {
                    continue;
                }
                byAbility.add(Identifiers.normalize(ability.getName()), targetsToJson(targets));
            }
            if (byAbility.size() > 0) {
                byUnit.add(ids.idFor(unit), byAbility);
            }
        }
        return byUnit;
    }

    private JsonObject targetsToJson(List<Target> targets) {
        JsonObject obj = new JsonObject();
        boolean noTarget = false;
        JsonArray unitIds = new JsonArray();
        JsonArray tiles = new JsonArray();
        // Two-part candidates are grouped by their subject, so the client can highlight
        // the pickable units first and then only that unit's own legal destinations.
        // LinkedHashMap keeps the enumeration order stable between pushes.
        Map<String, JsonArray> destinationsByPrimary = new LinkedHashMap<>();

        for (Target target : targets) {
            if (target instanceof NoTarget) {
                noTarget = true;
            } else if (target instanceof UnitTarget unitTarget) {
                unitIds.add(ids.idFor(unitTarget.getUnit()));
            } else if (target instanceof TileTarget tileTarget) {
                tiles.add(tileJson(tileTarget));
            } else if (target instanceof MultiTarget multi
                && multi.primary() instanceof UnitTarget primary
                && multi.secondary() instanceof TileTarget destination) {
                destinationsByPrimary
                    .computeIfAbsent(ids.idFor(primary.getUnit()), key -> new JsonArray())
                    .add(tileJson(destination));
            }
        }

        obj.addProperty("noTarget", noTarget);
        obj.add("unitIds", unitIds);
        obj.add("tiles", tiles);
        if (!destinationsByPrimary.isEmpty()) {
            JsonObject multi = new JsonObject();
            JsonArray primaryUnitIds = new JsonArray();
            JsonObject destinations = new JsonObject();
            destinationsByPrimary.forEach((unitId, tileArray) -> {
                primaryUnitIds.add(unitId);
                destinations.add(unitId, tileArray);
            });
            multi.add("primaryUnitIds", primaryUnitIds);
            multi.add("destinationsByPrimary", destinations);
            obj.add("multi", multi);
        }
        return obj;
    }

    private static JsonObject tileJson(TileTarget tileTarget) {
        JsonObject t = new JsonObject();
        t.addProperty("q", tileTarget.getTile().getPosition().getQ());
        t.addProperty("r", tileTarget.getTile().getPosition().getR());
        return t;
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
            String type = JsonSupport.optString(msg, "type");
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
        String kind = JsonSupport.optString(msg, "targetKind");
        if (kind == null) {
            return new NoTarget();
        }
        return switch (kind) {
            case "none" -> new NoTarget();
            case "unit" -> {
                Unit target = ids.resolve(JsonSupport.optString(msg, "targetUnitId"));
                yield target == null ? null : new UnitTarget(target);
            }
            case "tile" -> {
                Tile tile = resolveTile(state, msg);
                yield tile == null ? null : new TileTarget(tile);
            }
            // A two-part cast (Translocation): a unit AND a destination tile, carried in
            // the same message rather than over two round trips - the client has both by
            // the time it submits, having picked them from the prompt's own `multi` block.
            case "multi" -> {
                Unit subject = ids.resolve(JsonSupport.optString(msg, "targetUnitId"));
                Tile tile = resolveTile(state, msg);
                yield subject == null || tile == null
                    ? null
                    : new MultiTarget(new UnitTarget(subject), new TileTarget(tile));
            }
            default -> null;
        };
    }

    private Tile resolveTile(GameState state, JsonObject msg) {
        Integer q = JsonSupport.optInt(msg, "q");
        Integer r = JsonSupport.optInt(msg, "r");
        if (q == null || r == null) {
            return null;
        }
        return state.getMap().getTile(new Position(q, r));
    }
}
