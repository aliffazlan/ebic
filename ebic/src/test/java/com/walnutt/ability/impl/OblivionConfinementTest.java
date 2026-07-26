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

class OblivionConfinementTest {

    @Test
    void imprisonsAndStealsIntelligenceOnCastAndOnEscape() {
        Unit caster = new BasicUnit("Harbinger", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        OblivionConfinement ability = new OblivionConfinement(new AbilityDefinition(
            "Oblivion Confinement", "active", "desc",
            Map.of("cooldown", 3.0, "cast_range", 2.0, "duration", 1.0, "int_steal", 0.2)));
        caster.addAbility(ability);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 50, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p2.addUnit(victim);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 2)));

        UnitTarget target = new UnitTarget(victim);
        assertTrue(ability.canUse(state, target));
        ability.onUse(state, target);

        assertTrue(victim.hasStatus(StatusFlag.STUNNED));
        assertTrue(victim.hasStatus(StatusFlag.INVULNERABLE));
        assertEquals(40, victim.getAttributeValue(Attribute.INTELLIGENCE)); // 50 - 20% = 40
        assertEquals(10, caster.getAttributeValue(Attribute.INTELLIGENCE)); // stole 10

        // Duration was 1 turn - end/start the victim's controller's turn to expire it ("escape").
        victim.endTurn(state);
        victim.startTurn(state);

        assertFalse(victim.hasStatus(StatusFlag.STUNNED));
        assertEquals(32, victim.getAttributeValue(Attribute.INTELLIGENCE)); // 40 - 20% of 40 = 32
        assertEquals(18, caster.getAttributeValue(Attribute.INTELLIGENCE)); // 10 + 8
    }
}
