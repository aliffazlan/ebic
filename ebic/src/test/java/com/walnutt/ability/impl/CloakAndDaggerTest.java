package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Move;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.NormalEncounter;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Evayne stacks onto the target tile (occupied or not), ambushes whoever's caught
 * there with a weighted-attribute attack, stays hidden+invulnerable for the
 * duration, and relocates to the nearest free tile when it expires. Attacker and
 * target here each dump all their points into one attribute so the weighted roll
 * is deterministic (100% chance of that attribute), keeping damage assertions exact.
 */
class CloakAndDaggerTest {

    private CloakAndDagger newAbility() {
        return new CloakAndDagger(new AbilityDefinition("Cloak and Dagger", "active", "desc",
            Map.of("cooldown", 5.0, "cast_range", 2.0, "duration", 2.0, "dmg_penality", 0.4)));
    }

    @Test
    void castingOntoAnOccupiedTile_stacksAndAmbushesImmediately() {
        Unit evayne = new BasicUnit("Evayne", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        CloakAndDagger ability = newAbility();
        evayne.addAbility(ability);
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 50, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(evayne);
        p2.addUnit(enemy);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(evayne, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(2, 0)));

        TileTarget target = new TileTarget(map.getTile(new Position(2, 0))); // enemy's own tile
        assertTrue(ability.canUse(state, target));
        ability.onUse(state, target);

        assertEquals(new Position(2, 0), evayne.getPosition());
        assertEquals(2, map.getTile(new Position(2, 0)).getOccupants().size(), "stacked, not swapped");
        assertTrue(evayne.hasStatus(StatusFlag.HIDDEN));
        assertTrue(evayne.hasStatus(StatusFlag.INVULNERABLE));
        // STRENGTH (Evayne, 100% weighted) beats INTELLIGENCE (enemy, 100% weighted): 50 dmg * (1-0.4) = 30.
        assertEquals(200 - 30, enemy.getHealth());
    }

    @Test
    void anEnemyThatWalksOntoTheCloakedTileIsAmbushed() {
        Unit evayne = new BasicUnit("Evayne", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        evayne.addAbility(newAbility());
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 50, 200));
        enemy.addAbility(new Move());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(evayne);
        p2.addUnit(enemy);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(evayne, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(2, 0)));

        CloakAndDagger ability = (CloakAndDagger) evayne.getAbilities().stream()
            .filter(a -> a instanceof CloakAndDagger).findFirst().orElseThrow();
        ability.onUse(state, new TileTarget(map.getTile(new Position(1, 0)))); // empty tile, no immediate ambush
        assertEquals(200, enemy.getHealth());

        // The tile reads as walkable to the enemy - it doesn't know Evayne is hidden there.
        Move move = (Move) enemy.getAbilities().get(0);
        TileTarget evayneTile = new TileTarget(map.getTile(new Position(1, 0)));
        assertTrue(move.canUse(state, evayneTile));
        move.onUse(state, evayneTile);

        assertEquals(200 - 30, enemy.getHealth());
    }

    @Test
    void evayneIsInvulnerableWhileCloaked() {
        Unit evayne = new BasicUnit("Evayne", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        evayne.addAbility(newAbility());
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_TWO, new UnitStats(999, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(evayne);
        p2.addUnit(attacker);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(evayne, map.getTile(new Position(0, 0)));
        map.moveUnit(attacker, map.getTile(new Position(2, 0)));

        CloakAndDagger ability = (CloakAndDagger) evayne.getAbilities().stream()
            .filter(a -> a instanceof CloakAndDagger).findFirst().orElseThrow();
        ability.onUse(state, new TileTarget(map.getTile(new Position(1, 0))));

        CombatEngine.performAttack(state, new NormalEncounter(attacker, evayne, Attribute.STRENGTH, Attribute.STRENGTH));
        assertEquals(100, evayne.getHealth());
    }

    @Test
    void relocatesToNearestFreeTileWhenDurationExpires() {
        Unit evayne = new BasicUnit("Evayne", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        evayne.addAbility(newAbility());

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));
        state.setRemainingMoves(3);
        p1.addUnit(evayne);
        map.moveUnit(evayne, map.getTile(new Position(0, 0)));

        CloakAndDagger ability = (CloakAndDagger) evayne.getAbilities().stream()
            .filter(a -> a instanceof CloakAndDagger).findFirst().orElseThrow();
        ability.onUse(state, new TileTarget(map.getTile(new Position(1, 0))));

        Position cloakedAt = evayne.getPosition();
        assertTrue(evayne.hasStatus(StatusFlag.HIDDEN));

        evayne.endTurn(state);
        evayne.startTurn(state);
        evayne.endTurn(state);
        evayne.startTurn(state);

        assertFalse(evayne.hasStatus(StatusFlag.HIDDEN));
        assertFalse(evayne.hasStatus(StatusFlag.INVULNERABLE));
        assertNotEquals(cloakedAt, evayne.getPosition(), "should reappear elsewhere, not stay put");
        assertEquals(1, map.getDistance(cloakedAt, evayne.getPosition()));
    }
}
