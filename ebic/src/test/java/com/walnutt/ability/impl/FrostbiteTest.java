package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class FrostbiteTest {

    @Test
    void appliesNoHealingDebuff_andShattersTargetBelowThreshold_evenFromASmallHit() {
        Unit auroth = new BasicUnit("Auroth", Team.PLAYER_ONE, new UnitStats(40, 20, 0, 100));
        auroth.addAbility(new Frostbite(new AbilityDefinition("Frostbite", "passive", "desc",
            Map.of("duration", 2.0, "kill_threshold", 0.5))));
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(auroth);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(auroth, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(0, 1)));

        // STRENGTH beats INTELLIGENCE: damage = attacker's STRENGTH (40). Health 100 -> 60, above the 50% threshold.
        CombatEngine.performAttack(state, auroth, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        assertFalse(target.isDead());
        assertEquals(60, target.getHealth());
        assertTrue(target.hasStatus(StatusFlag.IMMUNE_TO_HEALING));

        target.heal(state, 1000); // blocked entirely by Frostbite
        assertEquals(60, target.getHealth());

        // AGILITY beats STRENGTH: only 20 damage (nowhere near lethal on its own), but it drops
        // health to 40 - below the 50-point threshold - so Frostbite instantly shatters the target.
        CombatEngine.performAttack(state, auroth, target, Attribute.AGILITY, Attribute.STRENGTH);
        assertTrue(target.isDead());
    }
}
