package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;
import com.walnutt.unit.UnitType;

class KillerDroneTest {

    private record Fixture(GameState state, GameMap map, KillerDrone ability, Unit maxwell,
                           Player playerOne, Unit enemy) {
    }

    /**
     * The drone's own attributes are all-Strength and the victim's all-Intelligence, so the
     * WeightedEncounter can only roll a matchup Strength wins - otherwise a "deterministic"
     * damage assertion would be flaky (see CLAUDE.md's WeightedEncounter gotcha).
     */
    private static Fixture fixture() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        KillerDrone ability = new KillerDrone(new AbilityDefinition("Killer Drone", "active", "desc", Map.of(
            "cooldown", 4.0, "cast_range", 5.0, "duration", 3.0, "strike_range", 1.0)));
        maxwell.addAbility(ability);
        Unit enemy = new EliteUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 40, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        p2.addUnit(enemy);
        GameMap map = new GameMap(4);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        state.setUnitDefinitions(Map.of("maxwell_drone",
            new UnitDefinition("Drone", "basic", 100, 30, 0, 0, 1, List.of())));
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));
        return new Fixture(state, map, ability, maxwell, p1, enemy);
    }

    private static Unit deployedDrone(Fixture f) {
        return f.playerOne().getUnits().stream().filter(u -> u.getName().equals("Drone")).findFirst().orElse(null);
    }

    @Test
    void deploysABasicThatJoinsTheRosterCanMoveAndHasNoAttack() {
        Fixture f = fixture();

        f.ability().onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));
        Unit drone = deployedDrone(f);

        assertNotNull(drone, "the drone joins the roster, which is what makes it selectable");
        assertEquals(UnitType.BASIC, drone.getUnitType(),
            "typed from its own prototype - inheriting Maxwell's ELITE would make it worth farming");
        assertFalse(drone.occupiesTile(), "drones never block movement");
        assertTrue(drone.getAbilities().stream().anyMatch(a -> a instanceof Move), "drones can be moved");
        assertTrue(drone.getAbilities().stream().noneMatch(a -> a instanceof Attack),
            "but have no attack on command");
    }

    @Test
    void strikesAnAdjacentEnemyAtTheEndOfItsOwnControllersTurn() {
        Fixture f = fixture();
        f.ability().onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));
        Unit drone = deployedDrone(f);

        // The opponent's turn ending must not make it fire.
        for (Ability ability : drone.getAbilities()) {
            ability.onTurnEnd(f.state(), new TurnEndEvent(Team.PLAYER_TWO));
        }
        assertEquals(500, f.enemy().getHealth());

        for (Ability ability : drone.getAbilities()) {
            ability.onTurnEnd(f.state(), new TurnEndEvent(Team.PLAYER_ONE));
        }
        assertEquals(470, f.enemy().getHealth(), "30 Strength beats 40 Intelligence for full damage");
    }

    /** Tiles hold more than one unit, and an enemy standing ON a drone is very much in reach. */
    @Test
    void countsAnEnemyStandingOnItsOwnTile() {
        Fixture f = fixture();
        f.ability().onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));
        Unit drone = deployedDrone(f);
        f.map().moveUnit(f.enemy(), f.map().getTile(new Position(0, 1)));

        for (Ability ability : drone.getAbilities()) {
            ability.onTurnEnd(f.state(), new TurnEndEvent(Team.PLAYER_ONE));
        }

        assertEquals(470, f.enemy().getHealth(), "distance 0 is within a strike range of 1");
    }

    @Test
    void despawnsWhenItsPowerCellRunsOutAndLeavesTheRoster() {
        Fixture f = fixture();
        f.ability().onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));
        Unit drone = deployedDrone(f);
        assertTrue(f.map().getTile(new Position(0, 1)).getOccupants().contains(drone));

        // Duration 3, ticked by the drone's own turns - which it only gets because it is
        // in the roster rather than the summon registry.
        for (int i = 0; i < 3; i++) {
            drone.startTurn(f.state());
            drone.endTurn(f.state());
        }

        assertFalse(f.playerOne().getUnits().contains(drone), "gone from the roster");
        assertFalse(f.map().getTile(new Position(0, 1)).getOccupants().contains(drone), "and off the board");
    }
}
