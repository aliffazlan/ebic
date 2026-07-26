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

class CrippleTest {

    @Test
    void reducesDamage_convertingTheReductionIntoPermanentRemovalOfTheDefendedAttribute() {
        Unit grivath = new BasicUnit("Grivath", Team.PLAYER_ONE, new UnitStats(60, 0, 0, 750));
        grivath.addAbility(new Cripple(new AbilityDefinition("Cripple", "passive", "desc",
            Map.of("damage_reduction", 0.5, "dmg_to_stat", 3.0))));
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 40, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(grivath);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(grivath, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(1, 0)));

        // STRENGTH beats INTELLIGENCE: base damage = 60, halved to 30 by Cripple, and
        // the 30-point reduction removes floor(30/3) = 10 from whatever the defender
        // defended with (INTELLIGENCE here) permanently.
        CombatEngine.performAttack(state, grivath, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(500 - 30, target.getHealth());
        assertEquals(30, target.getAttributeValue(Attribute.INTELLIGENCE)); // 40 - 10
    }
}
