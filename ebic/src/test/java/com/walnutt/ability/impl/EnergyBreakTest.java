package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
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

class EnergyBreakTest {

    @Test
    void increasesOpponentCooldowns_moreOnASuccessfulAttack_pastAnyNominalMax() {
        Unit wei = new BasicUnit("Wei", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 100));
        wei.addAbility(new EnergyBreak(new AbilityDefinition("Energy Break", "passive", "desc",
            Map.of("cooldown_increase", 1.0, "bonus_increase", 3.0))));
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));
        SoulRip enemyAbility = new SoulRip(new AbilityDefinition("Soul Rip", "active", "desc",
            Map.of("cooldown", 3.0, "cast_range", 2.0, "str_multiplier", 0.5)));
        enemy.addAbility(enemyAbility);
        enemyAbility.resetToMax(); // simulate an ability already sitting at its max cooldown (3)
        Move enemyMove = new Move();
        Attack enemyAttack = new Attack();
        enemy.addAbility(enemyMove);
        enemy.addAbility(enemyAttack);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(wei);
        p2.addUnit(enemy);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(wei, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 1)));

        // STRENGTH beats INTELLIGENCE: Wei successfully lands a hit -> bonus_increase (3) applies.
        CombatEngine.performAttack(state, wei, enemy, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        assertEquals(6, enemyAbility.getCurrentCooldown(), "3 (already at max) + 3 bonus = 6, past its nominal max");

        // A miss (defender wins the matchup) still applies the baseline cooldown_increase (1).
        CombatEngine.performAttack(state, wei, enemy, Attribute.STRENGTH, Attribute.AGILITY);
        assertEquals(7, enemyAbility.getCurrentCooldown());

        // Move/Attack are basic actions, not "abilities" in the balance sense - Energy Break must not touch them.
        assertEquals(0, enemyMove.getCurrentCooldown());
        assertEquals(0, enemyAttack.getCurrentCooldown());
    }
}
