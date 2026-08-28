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

    /**
     * The whole turn boundary as TurnManager runs it: every roster unit's endTurn, and only
     * then the TurnEndEvent. Tests that call onTurnEnd directly cannot see an ordering bug
     * between those two, and there was one - the drone used to be dismantled by the first
     * half before the second half could fire its last strike.
     */
    private static void endTurnFor(Fixture f, Team team) {
        for (Player player : f.state().getPlayers()) {
            if (player.getTeam() != team) {
                continue;
            }
            for (Unit unit : List.copyOf(player.getUnits())) {
                unit.endTurn(f.state());
            }
        }
        f.state().getEventBus().publish(f.state(), new TurnEndEvent(team));
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

        // The opponent's turn ending must not make it fire.
        endTurnFor(f, Team.PLAYER_TWO);
        assertEquals(500, f.enemy().getHealth());

        endTurnFor(f, Team.PLAYER_ONE);
        assertEquals(470, f.enemy().getHealth(), "30 Strength beats 40 Intelligence for full damage");
    }

    /** Tiles hold more than one unit, and an enemy standing ON a drone is very much in reach. */
    @Test
    void countsAnEnemyStandingOnItsOwnTile() {
        Fixture f = fixture();
        f.ability().onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));
        f.map().moveUnit(f.enemy(), f.map().getTile(new Position(0, 1)));

        endTurnFor(f, Team.PLAYER_ONE);

        assertEquals(470, f.enemy().getHealth(), "distance 0 is within a strike range of 1");
    }

    @Test
    void despawnsWhenItsPowerCellRunsOutAndLeavesTheRoster() {
        Fixture f = fixture();
        f.ability().onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));
        Unit drone = deployedDrone(f);
        assertTrue(f.map().getTile(new Position(0, 1)).getOccupants().contains(drone));

        // Duration 3, counted down by the TurnEndEvent hook rather than the ordinary tick -
        // see DroneLifespanEffect for why that ordering matters.
        for (int i = 0; i < 3; i++) {
            endTurnFor(f, Team.PLAYER_ONE);
        }

        assertFalse(f.playerOne().getUnits().contains(drone), "gone from the roster");
        assertFalse(f.map().getTile(new Position(0, 1)).getOccupants().contains(drone), "and off the board");
    }

    /**
     * The bug this pins: expiry used to run inside TurnManager's endTurn loop, which is
     * before TurnEndEvent is published, so on the drone's last turn it was already off the
     * board when its strike would have fired - it only ever got duration-1 strikes.
     */
    @Test
    void stillStrikesOnTheVeryTurnItsPowerCellRunsOut() {
        Fixture f = fixture();
        f.ability().onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));

        for (int i = 0; i < 3; i++) {
            endTurnFor(f, Team.PLAYER_ONE);
        }

        assertEquals(410, f.enemy().getHealth(), "three turns of life is three strikes, not two");
        assertEquals(null, deployedDrone(f), "and it is dismantled afterwards, not before");
    }

    /** A drone occupies no tile, so an enemy standing on the target one is no reason to refuse. */
    @Test
    void canBeDeployedOntoATileAnEnemyIsStandingOn() {
        Fixture f = fixture();

        assertTrue(f.ability().canUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2)))),
            "the enemy's own tile is a legal deployment");

        f.ability().onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));
        Unit drone = deployedDrone(f);
        assertNotNull(drone);
        assertTrue(f.map().getTile(new Position(0, 2)).getOccupants().contains(drone), "it stacks onto them");
    }

    /** Same rule one level down: nothing on a tile blocks a unit that takes up no space itself. */
    @Test
    void canMoveThroughATileAnEnemyIsStandingOn() {
        Fixture f = fixture();
        f.ability().onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 1))));
        Unit drone = deployedDrone(f);
        f.state().setRemainingMoves(3);

        Ability move = drone.getAbilities().stream().filter(a -> a instanceof Move).findFirst().orElseThrow();
        assertTrue(move.canUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2)))),
            "the enemy's tile is adjacent and the drone flies over it");

        move.onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));
        assertEquals(new Position(0, 2), drone.getPosition());
    }
}
