package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class ImplosionTest {

    @Test
    void damagesTargetAndAdjacentEnemies_proportionalToTheirSummedCooldowns() {
        Unit wei = new BasicUnit("Wei", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        Implosion implosion = new Implosion(new AbilityDefinition("Implosion", "active", "desc",
            Map.of("cooldown", 5.0, "cast_range", 2.0, "dmg_per_cooldown", 12.0, "radius", 1.0)));
        wei.addAbility(implosion);

        Unit primary = new BasicUnit("Primary", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500));
        SoulRip primaryAbility = new SoulRip(new AbilityDefinition("Soul Rip", "active", "desc",
            Map.of("cooldown", 3.0, "cast_range", 2.0, "str_multiplier", 0.5)));
        primary.addAbility(primaryAbility);
        primaryAbility.resetToMax(); // cooldown = 3

        Unit splash = new BasicUnit("Splash", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500));
        SoulRip splashAbility = new SoulRip(new AbilityDefinition("Soul Rip", "active", "desc",
            Map.of("cooldown", 3.0, "cast_range", 2.0, "str_multiplier", 0.5)));
        splash.addAbility(splashAbility);
        splashAbility.increaseCooldown(2); // cooldown = 2

        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 500)); // same team as Wei - not splashed

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(wei);
        p1.addUnit(ally);
        p2.addUnit(primary);
        p2.addUnit(splash);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(wei, map.getTile(new Position(0, 0)));
        map.moveUnit(primary, map.getTile(new Position(2, 0)));
        map.moveUnit(splash, map.getTile(new Position(3, 0)));
        map.moveUnit(ally, map.getTile(new Position(2, 1)));

        UnitTarget target = new UnitTarget(primary);
        assertTrue(implosion.canUse(state, target));
        implosion.onUse(state, target);

        assertEquals(500 - 36, primary.getHealth()); // 3 cooldown * 12 = 36
        assertEquals(500 - 24, splash.getHealth());   // 2 cooldown * 12 = 24 (adjacent to primary, splash radius 1)
        assertEquals(500, ally.getHealth());          // same team as Wei - never touched
    }
}
