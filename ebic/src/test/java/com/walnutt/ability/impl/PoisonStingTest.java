package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.PoisonEffect;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class PoisonStingTest {

    @Test
    void stacksDurationInsteadOfRefreshing_andTicksDamageDownWithRemainingTurns() {
        Unit attacker = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(10, 0, 0, 100));
        attacker.addAbility(new PoisonSting(new AbilityDefinition(
            "Poison Sting", "passive", "desc", Map.of("duration", 3.0, "dmg_per_duration", 4.0))));
        Unit defender = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(50, 0, 0, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(attacker);
        p2.addUnit(defender);
        GameState state = new GameState(new GameMap(3), List.of(p1, p2), new Random(1));

        // AGILITY(0) beats STRENGTH: base damage is always 0, isolating poison's own damage.
        CombatEngine.performAttack(state, attacker, defender, Attribute.STRENGTH, Attribute.AGILITY);

        PoisonEffect poison = findPoison(defender);
        assertNotNull(poison);
        assertEquals(3, poison.getRemainingTurns());

        state.getEventBus().publish(state, new TurnStartEvent(defender.getTeam()));
        assertEquals(200 - 12, defender.getHealth()); // 4 dmg * 3 turns left

        defender.endTurn(state); // engine bookkeeping: ticks poison 3 -> 2
        assertEquals(2, findPoison(defender).getRemainingTurns());

        // Attacked again while already poisoned: duration stacks instead of refreshing (2 + 3 = 5).
        CombatEngine.performAttack(state, attacker, defender, Attribute.STRENGTH, Attribute.AGILITY);
        assertEquals(5, findPoison(defender).getRemainingTurns());

        state.getEventBus().publish(state, new TurnStartEvent(defender.getTeam()));
        assertEquals(200 - 12 - 20, defender.getHealth()); // 4 dmg * 5 turns left
    }

    private PoisonEffect findPoison(Unit unit) {
        for (Effect effect : unit.getEffects()) {
            if (effect instanceof PoisonEffect poisonEffect) {
                return poisonEffect;
            }
        }
        return null;
    }
}
