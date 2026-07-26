package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class CounterstrikeTest {

    @Test
    void freeCounterAttack_reusesOriginalAttributeChoices_reducedDamageWithLifesteal() {
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_ONE, new UnitStats(30, 0, 0, 100));
        Unit counterer = new BasicUnit("Valor", Team.PLAYER_TWO, new UnitStats(50, 0, 0, 200));
        counterer.addAbility(new Counterstrike(new AbilityDefinition(
            "Counterstrike", "passive", "desc", Map.of("damage_reduction", 0.5, "lifesteal", 1.0))));
        counterer.getHealthPool().setCurrent(100);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(attacker);
        p2.addUnit(counterer);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(attacker, map.getTile(new Position(0, 0)));
        map.moveUnit(counterer, map.getTile(new Position(1, 0)));

        // Same-attribute encounter: original damage = max(0, 30 - 50) = 0, so the only
        // damage change we observe comes from the automatic counter-attack.
        CombatEngine.performAttack(state, attacker, counterer, Attribute.STRENGTH, Attribute.STRENGTH);

        // Counter: max(0, 50 - 30) = 20, reduced 50% -> 10 dealt, 100% lifesteal -> 10 healed.
        assertEquals(100 - 10, attacker.getHealth());
        assertEquals(100 + 10, counterer.getHealth());
    }
}
