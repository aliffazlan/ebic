package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Move;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class PylonTest {

    private GameState newStateWithPylonDefinitions(GameMap map, Player p1, Player p2) {
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        UnitDefinition pylonDef = new UnitDefinition("Pylon", "basic", 200, 0, 0, 0, List.of("pylon_beam"));
        state.setUnitDefinitions(Map.of("zenith_pylon", pylonDef));
        state.setAbilityDefinitions(Map.of("pylon_beam",
            new AbilityDefinition("Pylon Orbital Beam", "passive", "desc", Map.of("radius", 1.0))));
        return state;
    }

    @Test
    void pylonDoesNotBlockItsTile_butIsStillAttackable_andMirrorsOrbitalBeam() {
        Unit zenith = new BasicUnit("Zenith", Team.PLAYER_ONE, new UnitStats(0, 0, 40, 100));
        PylonAbility pylonAbility = new PylonAbility(new AbilityDefinition("Pylon", "active", "desc",
            Map.of("cooldown", 4.0, "death_damage", 50.0, "death_duration", 1.0)));
        zenith.addAbility(pylonAbility);
        OrbitalBeam beam = new OrbitalBeam(new AbilityDefinition("Orbital Beam", "active", "desc",
            Map.of("cooldown", 2.0, "damage", 40.0, "cast_range", 5.0)));
        zenith.addAbility(beam);

        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 200));
        enemy.addAbility(new Move());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(zenith);
        p2.addUnit(enemy);
        GameMap map = new GameMap(5);
        GameState state = newStateWithPylonDefinitions(map, p1, p2);
        state.setRemainingMoves(3);
        map.moveUnit(zenith, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(2, 0)));

        Tile pylonTile = map.getTile(new Position(1, 0));
        pylonAbility.onUse(state, new TileTarget(pylonTile));
        Unit pylon = pylonTile.getFirstOccupant();

        // The tile still reads as walkable even with the (non-hidden) pylon on it.
        assertTrue(pylonTile.isWalkable());
        Move move = (Move) enemy.getAbilities().get(0);
        move.onUse(state, new TileTarget(pylonTile));
        assertEquals(new Position(1, 0), enemy.getPosition());
        assertEquals(2, pylonTile.getOccupants().size(), "pylon + enemy stacked, no collision");

        // Still directly attackable/targetable despite not occupying the tile.
        assertTrue(state.getMap().getUnitsInRadius(new Position(1, 0), 0).contains(pylon));

        // Casting Orbital Beam on the (now co-located) enemy also triggers the pylon's mirrored beam.
        beam.onUse(state, new UnitTarget(enemy));
        state.getEventBus().publish(state,
            new com.walnutt.event.AbilityCastEvent(zenith, beam, new UnitTarget(enemy),
                com.walnutt.event.AbilityCastEvent.Phase.POST));

        // enemy took the direct beam (40) plus the pylon's mirrored beam (40, only adjacent target is enemy itself).
        assertEquals(200 - 40 - 40, enemy.getHealth());
    }

    @Test
    void pylonDeathStunsAndDamagesAdjacentEnemies() {
        Unit zenith = new BasicUnit("Zenith", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        PylonAbility pylonAbility = new PylonAbility(new AbilityDefinition("Pylon", "active", "desc",
            Map.of("cooldown", 4.0, "death_damage", 50.0, "death_duration", 2.0)));
        zenith.addAbility(pylonAbility);

        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(zenith);
        p2.addUnit(enemy);
        GameMap map = new GameMap(5);
        GameState state = newStateWithPylonDefinitions(map, p1, p2);
        state.setRemainingMoves(3);
        map.moveUnit(zenith, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(2, 0)));

        // Pylon lands on its own empty tile, adjacent to (but not stacked with) the enemy.
        pylonAbility.onUse(state, new TileTarget(map.getTile(new Position(1, 0))));
        Unit pylon = map.getTile(new Position(1, 0)).getFirstOccupant();

        pylon.instantKill(state, zenith);

        assertEquals(200 - 50, enemy.getHealth());
        assertTrue(enemy.hasStatus(StatusFlag.STUNNED));
    }
}
