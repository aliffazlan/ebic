package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class SanityEclipseTest {

    /**
     * One full round: the caster ends their turn, the opponent takes a whole turn, and the
     * caster's next turn begins. Written out rather than hidden in a helper because the
     * TIMING is the thing several of these tests are about - the orb used to detonate at
     * the first endTurn below, before the opponent had any chance to walk out of it.
     */
    private static void passARound(GameState state, Unit caster, Unit opponent) {
        caster.endTurn(state);
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
        opponent.startTurn(state);
        opponent.endTurn(state);
        caster.startTurn(state);
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
    }

    @Test
    void explodesAfterDelay_dealingIntDiffDamage_bypassingInvulnerability() {
        Unit caster = new BasicUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(0, 0, 80, 100));
        SanityEclipse ability = new SanityEclipse(new AbilityDefinition("Sanity's Eclipse", "active", "desc",
            Map.of("cooldown", 9.0, "cast_range", 4.0, "delay", 1.0, "radius", 1.0, "int_diff_dmg", 1.0)));
        caster.addAbility(ability);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 20, 200));
        target.addEffect(new StatusEffect("Imprisoned", 5, StatusFlag.INVULNERABLE));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p2.addUnit(target);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(3, 0)));

        TileTarget tileTarget = new TileTarget(map.getTile(target.getPosition()));
        assertTrue(ability.canUse(state, tileTarget));
        ability.onUse(state, tileTarget);

        // Not yet exploded before the delay elapses.
        assertEquals(200, target.getHealth());

        // The bug this pins: the orb used to go off HERE, at the end of the very turn it
        // was cast on, giving the opponent no turn at all to react.
        caster.endTurn(state);
        assertEquals(200, target.getHealth(), "must not detonate on the caster's own turn");

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
        target.startTurn(state);
        target.endTurn(state);
        assertEquals(200, target.getHealth(), "nor during the opponent's turn");

        caster.startTurn(state);
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(200 - 60, target.getHealth()); // (80 - 20) * 1.0 = 60, despite target being invulnerable
    }

    /**
     * The point of the delay, from the victim's side: a full turn to walk out of the blast.
     * Impossible under the old timing, which is what made this worth fixing.
     */
    @Test
    void aTargetThatWalksClearBeforeItLandsTakesNothing() {
        Unit caster = new BasicUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(0, 0, 80, 100));
        SanityEclipse ability = new SanityEclipse(new AbilityDefinition("Sanity's Eclipse", "active", "desc",
            Map.of("cooldown", 9.0, "cast_range", 4.0, "delay", 1.0, "radius", 1.0, "int_diff_dmg", 1.0)));
        caster.addAbility(ability);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 20, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p2.addUnit(target);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(3, 0)));

        ability.onUse(state, new TileTarget(map.getTile(target.getPosition())));
        caster.endTurn(state);

        // The opponent's turn: step well outside the blast radius.
        map.moveUnit(target, map.getTile(new Position(0, 3)));

        caster.startTurn(state);
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(200, target.getHealth(), "the orb detonates on a position, not on a unit");
    }

    @Test
    void doesNotDamageAlliesCaughtInTheBlast() {
        Unit caster = new BasicUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(0, 0, 80, 100));
        SanityEclipse ability = new SanityEclipse(new AbilityDefinition("Sanity's Eclipse", "active", "desc",
            Map.of("cooldown", 9.0, "cast_range", 4.0, "delay", 1.0, "radius", 1.0, "int_diff_dmg", 1.0)));
        caster.addAbility(ability);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 20, 200));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 20, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p1.addUnit(ally);
        p2.addUnit(enemy);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(3, 0)));
        map.moveUnit(enemy, map.getTile(new Position(3, 1))); // adjacent to ally, both in blast radius

        TileTarget tileTarget = new TileTarget(map.getTile(ally.getPosition()));
        assertTrue(ability.canUse(state, tileTarget));
        ability.onUse(state, tileTarget);
        passARound(state, caster, enemy);

        assertEquals(200, ally.getHealth(), "allies caught in the blast must take no damage");
        assertEquals(200 - 60, enemy.getHealth(), "the enemy in the same blast still takes the int-diff damage");
    }
}
