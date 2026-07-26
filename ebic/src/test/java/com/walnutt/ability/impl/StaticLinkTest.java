package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class StaticLinkTest {

    @Test
    void stacksDamageStealEachTurn_grantsFreeAttack_breaksOnNonAdjacencyWithLingeringBuff() {
        // Pure STRENGTH vs pure INTELLIGENCE makes both sides' WeightedEncounter rolls
        // 100% deterministic (STRENGTH always beats INTELLIGENCE), so the free attack's
        // damage is never a coin flip.
        Unit discharge = new BasicUnit("Discharge", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 200));
        StaticLink link = new StaticLink(new AbilityDefinition("Static Link", "active", "desc",
            Map.of("cooldown", 7.0, "cast_range", 1.0, "dmg_steal", 5.0, "buff_linger_duration", 2.0)));
        discharge.addAbility(link);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 50, 1000));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(discharge);
        p2.addUnit(target);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(discharge, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));

        link.onUse(state, new UnitTarget(target));

        // Turn 1: steal stacks to 5, then a free attack lands at turn end using the
        // buffed/debuffed damage-dealt modifiers (Discharge +5, target -5 - target
        // deals 0 to itself so only Discharge's side is observable via the free hit).
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));
        int healthAfterTurn1 = target.getHealth();
        assertTrue(healthAfterTurn1 < 1000, "the free attack at turn end should have dealt some damage");

        // Turn 2: steal stacks again (now +10/-10 total) - still adjacent, another free attack.
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));
        int healthAfterTurn2 = target.getHealth();
        assertTrue(healthAfterTurn2 < healthAfterTurn1, "damage should have grown/continued on the second linked turn");

        // Now separate them - the link should break at this turn's end instead of attacking.
        map.moveUnit(target, map.getTile(new Position(4, 0)));
        int healthBeforeBreak = target.getHealth();
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));
        assertEquals(healthBeforeBreak, target.getHealth(), "no free attack once the link has broken");

        // The link itself is now inactive - a further turn should not attempt anything (no exception, no more attacks).
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));
        assertEquals(healthBeforeBreak, target.getHealth());
    }

    @Test
    void canUseRequiresAdjacentEnemyWithinRange() {
        Unit discharge = new BasicUnit("Discharge", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 200));
        StaticLink link = new StaticLink(new AbilityDefinition("Static Link", "active", "desc",
            Map.of("cooldown", 7.0, "cast_range", 1.0, "dmg_steal", 5.0, "buff_linger_duration", 2.0)));
        discharge.addAbility(link);
        Unit farEnemy = new BasicUnit("Far", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000));
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(discharge);
        p1.addUnit(ally);
        p2.addUnit(farEnemy);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(discharge, map.getTile(new Position(0, 0)));
        map.moveUnit(farEnemy, map.getTile(new Position(3, 0)));
        map.moveUnit(ally, map.getTile(new Position(1, 0)));

        assertFalse(link.canUse(state, new UnitTarget(farEnemy)), "out of range");
        assertFalse(link.canUse(state, new UnitTarget(ally)), "must target an enemy");
    }
}
