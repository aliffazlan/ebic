package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.InfernalBladeEffect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class InfernalBladeTest {

    @Test
    void stacksDisarmDuration_andPausesTheCountdownWhileAdjacentToLucifer() {
        Unit lucifer = new BasicUnit("Lucifer", Team.PLAYER_ONE, new UnitStats(80, 30, 20, 800));
        lucifer.addAbility(new InfernalBlade(new AbilityDefinition("Infernal Blade", "passive", "desc",
            Map.of("duration", 2.0))));
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 50, 1000));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(lucifer);
        p2.addUnit(victim);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(lucifer, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(1, 0)));

        // STRENGTH (Lucifer's only stat) beats INTELLIGENCE (victim's only stat) deterministically.
        CombatEngine.performAttack(state, lucifer, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        assertTrue(victim.hasStatus(StatusFlag.DISARMED));
        InfernalBladeEffect effect = findCurse(victim);
        assertNotNull(effect);
        assertEquals(2, effect.getRemainingTurns());

        // A second successful hit stacks duration instead of refreshing it.
        CombatEngine.performAttack(state, lucifer, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        assertEquals(4, effect.getRemainingTurns(), "duration 2 + 2 = 4, not refreshed back to 2");

        // Victim ends its turn adjacent to Lucifer - countdown is paused.
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_TWO));
        assertEquals(4, effect.getRemainingTurns(), "adjacent to the source - duration doesn't tick");

        // Victim moves away and ends its turn again - countdown now progresses normally.
        map.moveUnit(victim, map.getTile(new Position(2, 0)));
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_TWO));
        assertEquals(3, effect.getRemainingTurns(), "no longer adjacent - duration ticks down");
        assertTrue(victim.hasStatus(StatusFlag.DISARMED), "still active, duration hasn't run out");
    }

    private InfernalBladeEffect findCurse(Unit unit) {
        for (Effect effect : unit.getEffects()) {
            if (effect instanceof InfernalBladeEffect curse) {
                return curse;
            }
        }
        return null;
    }
}
