package com.walnutt.game.sandbox;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.walnutt.ability.Ability;
import com.walnutt.data.UnitDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.game.DraftService;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.RemovalReason;
import com.walnutt.game.Team;
import com.walnutt.map.Tile;
import com.walnutt.map.TileType;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitFactory;

/**
 * The sandbox's tools, as pure game-state edits. Called from TurnManager between actions
 * (never mid-cast or mid-encounter), so nothing here has to worry about a half-resolved
 * ability holding onto a unit it is about to lose.
 *
 * Removal is deliberately NOT death: no DeathEvent, no KillEvent, no on-death passives - the
 * unit, its summons, and every effect that refers to it simply leave the game.
 */
public final class SandboxController {
    /** Pseudo definition id for the generic drafted-ten basic, which has no JSON of its own. */
    public static final String BASIC_ID = "basic";
    /** Same allowance TurnManager grants at the start of every turn. */
    private static final int MOVES_PER_TURN = 3;

    private SandboxController() {
    }

    /** Applies one tool use. Returns null on success, or a player-facing reason nothing happened. */
    public static String apply(GameState state, SandboxCommand command) {
        return switch (command) {
            case SandboxCommand.Spawn spawn -> spawn(state, spawn);
            case SandboxCommand.Remove remove -> remove(state, remove.unit());
            case SandboxCommand.Clear clear -> {
                clear(state);
                yield null;
            }
            case SandboxCommand.RefillMoves refill -> {
                refreshTeam(state, state.getCurrentPlayer());
                yield null;
            }
            case SandboxCommand.ResetCooldowns reset -> {
                resetCooldowns(state);
                yield null;
            }
            case SandboxCommand.Heal heal -> heal(state, heal.unit());
            case SandboxCommand.SwitchTeam switchTeam -> {
                state.switchCurrentPlayer();
                refreshTeam(state, state.getCurrentPlayer());
                yield null;
            }
        };
    }

    /** Every definition id the picker may offer besides {@link #BASIC_ID} - the draftable pool. */
    public static List<String> spawnableDefinitionIds(Map<String, UnitDefinition> unitDefinitions) {
        DraftService draft = new DraftService();
        List<String> ids = new ArrayList<>(draft.getAvailableChampions(unitDefinitions));
        ids.addAll(draft.getAvailableElites(unitDefinitions));
        return ids;
    }

    /**
     * Not blocked terrain, and nothing on it that takes up the tile - stacking on a Pylon or a
     * Drone is fine. Unlike Tile.isWalkable, a HIDDEN unit still counts: the sandbox player
     * controls both teams and so can always see it.
     */
    public static boolean canSpawnOn(Tile tile) {
        if (tile == null || tile.getType() == TileType.BLOCKED) {
            return false;
        }
        for (Unit occupant : tile.getOccupants()) {
            if (occupant.occupiesTile()) {
                return false;
            }
        }
        return true;
    }

    public static List<Tile> spawnTiles(GameState state) {
        return state.getMap().getAllTiles().stream().filter(SandboxController::canSpawnOn).toList();
    }

    private static String spawn(GameState state, SandboxCommand.Spawn spawn) {
        Tile tile = spawn.position() == null ? null : state.getMap().getTile(spawn.position());
        if (!canSpawnOn(tile)) {
            return "You can't spawn a unit there.";
        }
        Unit unit;
        if (BASIC_ID.equals(spawn.definitionId())) {
            unit = UnitFactory.createBasic(nextBasicName(state, spawn.team()), spawn.team());
        } else if (spawnableDefinitionIds(state.getUnitDefinitions()).contains(spawn.definitionId())) {
            unit = UnitFactory.createFromDefinition(state.getUnitDefinitions().get(spawn.definitionId()),
                spawn.team(), state.getAbilityDefinitions());
        } else {
            return "That unit can't be spawned.";
        }
        state.getPlayer(spawn.team()).addUnit(unit);
        state.getMap().moveUnit(unit, tile);
        unit.resetTurnFlags();
        return null;
    }

    private static String remove(GameState state, Unit unit) {
        if (!isOnBoard(state, unit)) {
            return "That unit isn't on the board.";
        }
        detach(state, unit);
        return null;
    }

    private static String heal(GameState state, Unit unit) {
        if (!isOnBoard(state, unit)) {
            return "That unit isn't on the board.";
        }
        unit.getHealthPool().setCurrent(unit.getHealthPool().getMax());
        return null;
    }

    /** Dead roster units included - a cleared board starts from nothing. */
    private static void clear(GameState state) {
        for (Unit unit : List.copyOf(state.getAllActiveUnits())) {
            if (isInGame(state, unit)) {
                detach(state, unit);
            }
        }
    }

    private static void resetCooldowns(GameState state) {
        for (Unit unit : state.getAllActiveUnits()) {
            for (Ability ability : unit.getAbilities()) {
                ability.decreaseCooldown(ability.getCurrentCooldown());
                for (Ability held : ability.getHeldAbilities()) {
                    held.decreaseCooldown(held.getCurrentCooldown());
                }
            }
        }
    }

    /** A fresh allowance and fresh per-unit flags, without any of startTurn's ticking. */
    private static void refreshTeam(GameState state, Player player) {
        state.setRemainingMoves(MOVES_PER_TURN);
        for (Unit unit : player.getUnits()) {
            unit.resetTurnFlags();
        }
    }

    /**
     * Takes a unit out of the game along with everything hanging off it, in an order that
     * never revisits a unit: off the board first, so the summon sweep below can't find it
     * again even if two units somehow refer to each other.
     */
    private static void detach(GameState state, Unit unit) {
        state.removeUnit(unit, RemovalReason.DESPAWN);
        state.getPlayer(unit.getTeam()).removeUnit(unit);

        for (Effect effect : List.copyOf(unit.getEffects())) {
            unit.stripEffect(state, effect);
        }
        for (Unit other : state.getAllActiveUnits()) {
            for (Effect effect : List.copyOf(other.getEffects())) {
                if (effect.references(unit)) {
                    other.stripEffect(state, effect);
                }
            }
        }
        for (Unit summon : state.getSummonsOf(unit)) {
            if (isInGame(state, summon)) {
                detach(state, summon);
            }
        }

        // Some abilities keep their own list of what they raised (Snow Golem resummons by
        // killing every golem it remembers). A removed unit left alive would be "killed"
        // again from off the board, so it leaves depleted - unless its HP is shared with a
        // unit still in play, which must not be touched.
        if (!sharesHealthWithAnyoneInPlay(state, unit)) {
            unit.getHealthPool().setCurrent(0);
        }
    }

    private static boolean sharesHealthWithAnyoneInPlay(GameState state, Unit unit) {
        for (Unit other : state.getAllActiveUnits()) {
            if (other != unit && other.getHealthPool() == unit.getHealthPool()) {
                return true;
            }
        }
        return false;
    }

    /** Standing on a tile right now - what a click on the board can reach. */
    private static boolean isOnBoard(GameState state, Unit unit) {
        return unit != null && !unit.isDead() && unit.getPosition() != null && isInGame(state, unit);
    }

    private static boolean isInGame(GameState state, Unit unit) {
        return unit != null && state.getAllActiveUnits().contains(unit);
    }

    private static String nextBasicName(GameState state, Team team) {
        String prefix = state.getPlayer(team).getName() + " Basic ";
        int n = 1;
        while (hasUnitNamed(state, prefix + n)) {
            n++;
        }
        return prefix + n;
    }

    private static boolean hasUnitNamed(GameState state, String name) {
        return state.getAllActiveUnits().stream().anyMatch(u -> u.getName().equals(name));
    }
}
