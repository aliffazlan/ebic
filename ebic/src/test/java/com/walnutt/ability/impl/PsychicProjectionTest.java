package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.TileTarget;
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

class PsychicProjectionTest {

    @Test
    void summonsAPlayerControllableInvulnerableClone_stunningTheCasterMeanwhile() {
        Unit lanaya = new BasicUnit("Lanaya", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100));
        PsychicProjection ability = new PsychicProjection(new AbilityDefinition("Psychic Projection", "active", "desc",
            Map.of("cooldown", 6.0, "cast_range", 4.0, "duration", 2.0)));
        lanaya.addAbility(ability);
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_TWO, new UnitStats(999, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(lanaya);
        p2.addUnit(attacker);
        GameMap map = new GameMap(6);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(lanaya, map.getTile(new Position(0, 0)));
        map.moveUnit(attacker, map.getTile(new Position(3, 3)));

        TileTarget target = new TileTarget(map.getTile(new Position(1, 0)));
        assertTrue(ability.canUse(state, target));
        int rosterSizeBefore = p1.getUnits().size();
        ability.onUse(state, target);

        assertTrue(lanaya.hasStatus(StatusFlag.STUNNED));
        assertEquals(rosterSizeBefore + 1, p1.getUnits().size());

        Unit clone = p1.getUnits().get(p1.getUnits().size() - 1);
        assertTrue(clone.hasStatus(StatusFlag.INVULNERABLE));

        // The clone genuinely takes no damage even from a huge attack.
        CombatEngine.performAttack(state, attacker, clone, Attribute.STRENGTH, Attribute.STRENGTH);
        assertEquals(clone.getMaxHealth(), clone.getHealth());

        // After the duration elapses (2 of Lanaya's own turn-ends), the clone is removed and Lanaya is freed.
        lanaya.endTurn(state);
        lanaya.startTurn(state);
        lanaya.endTurn(state);
        lanaya.startTurn(state);

        assertFalse(p1.getUnits().contains(clone));
        assertFalse(lanaya.hasStatus(StatusFlag.STUNNED));
    }
}
