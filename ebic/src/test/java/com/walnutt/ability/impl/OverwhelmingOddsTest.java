package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.NoTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class OverwhelmingOddsTest {

    private OverwhelmingOdds newAbility() {
        return new OverwhelmingOdds(new AbilityDefinition("Overwhelming Odds", "active", "desc",
            Map.of("cooldown", 5.0, "radius", 2.0, "diff_dmg", 20.0, "diff_heal", 20.0)));
    }

    @Test
    void damagesEnemiesWhenAlliesOutnumberThem() {
        Unit caster = new BasicUnit("Valor", Team.PLAYER_ONE, new UnitStats(10, 0, 0, 100));
        OverwhelmingOdds ability = newAbility();
        caster.addAbility(ability);
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 0, 0, 100));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p1.addUnit(ally);
        p2.addUnit(enemy);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(2, 2)));
        map.moveUnit(ally, map.getTile(new Position(2, 3)));
        map.moveUnit(enemy, map.getTile(new Position(2, 1)));

        // 2 allies vs 1 enemy in radius -> diff 1 -> 20 damage to the enemy.
        ability.onUse(state, new NoTarget());

        assertEquals(80, enemy.getHealth());
        assertEquals(100, ally.getHealth());
    }

    @Test
    void healsAlliesWhenOutnumbered() {
        Unit caster = new BasicUnit("Valor", Team.PLAYER_ONE, new UnitStats(10, 0, 0, 100));
        caster.getHealthPool().setCurrent(50);
        OverwhelmingOdds ability = newAbility();
        caster.addAbility(ability);
        Unit enemyOne = new BasicUnit("E1", Team.PLAYER_TWO, new UnitStats(10, 0, 0, 100));
        Unit enemyTwo = new BasicUnit("E2", Team.PLAYER_TWO, new UnitStats(10, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p2.addUnit(enemyOne);
        p2.addUnit(enemyTwo);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(2, 2)));
        map.moveUnit(enemyOne, map.getTile(new Position(2, 3)));
        map.moveUnit(enemyTwo, map.getTile(new Position(2, 1)));

        // 1 ally vs 2 enemies -> diff -1 -> heal caster for 20.
        ability.onUse(state, new NoTarget());

        assertEquals(70, caster.getHealth());
    }
}
