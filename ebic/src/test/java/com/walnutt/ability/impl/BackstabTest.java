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
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class BackstabTest {

    @Test
    void addsFlatBonusPerAgilityOnAttackerHits() {
        Unit attacker = new BasicUnit("Evayne", Team.PLAYER_ONE, new UnitStats(50, 20, 10, 100));
        attacker.addAbility(new Backstab(new AbilityDefinition("Backstab", "passive", "desc", Map.of("dmg_bonus", 0.4))));
        Unit defender = new BasicUnit("Dummy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(attacker);
        p2.addUnit(defender);
        GameState state = new GameState(new GameMap(3), List.of(p1, p2), new Random(1));

        // STRENGTH beats INTELLIGENCE: base damage = attacker's STRENGTH (50). Backstab adds 0.4 * 20 agility = 8.
        CombatEngine.performAttack(state, attacker, defender, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(100 - 58, defender.getHealth());
    }
}
