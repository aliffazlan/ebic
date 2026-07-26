package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class SoulRipTest {

    private SoulRip newSoulRip() {
        return new SoulRip(new AbilityDefinition("Soul Rip", "active", "desc",
            Map.of("cooldown", 3.0, "cast_range", 2.0, "str_multiplier", 0.5)));
    }

    private GameState newState(Unit caster, Unit other) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", other.getTeam());
        p1.addUnit(caster);
        p2.addUnit(other);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(other, map.getTile(new Position(0, 2))); // distance 2, within range
        return state;
    }

    @Test
    void damagesEnemyProportionalToStrengthDifference_andGoesOnCooldown() {
        Unit caster = new BasicUnit("Dirge", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        SoulRip soulRip = newSoulRip();
        caster.addAbility(soulRip);
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 0, 0, 100));

        GameState state = newState(caster, enemy);
        UnitTarget target = new UnitTarget(enemy);

        assertTrue(soulRip.canUse(state, target));
        soulRip.onUse(state, target);

        assertEquals(100 - 20, enemy.getHealth()); // 0.5 * (50 - 10) = 20
        assertFalse(soulRip.isReady());
        assertFalse(soulRip.canUse(state, target));
    }

    @Test
    void healsAllyProportionalToStrengthDifference() {
        Unit caster = new BasicUnit("Dirge", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        SoulRip soulRip = newSoulRip();
        caster.addAbility(soulRip);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 0, 0, 100));
        ally.getHealthPool().setCurrent(50);

        GameState state = newState(caster, ally);
        UnitTarget target = new UnitTarget(ally);

        soulRip.onUse(state, target);

        assertEquals(50 + 20, ally.getHealth()); // 0.5 * (50 - 10) = 20 healed
    }
}
