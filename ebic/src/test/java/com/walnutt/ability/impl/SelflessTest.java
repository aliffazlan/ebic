package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class SelflessTest {

    @Test
    void redirectsAPortionOfAnAdjacentAllysDamageToItself() {
        Unit thaddeus = new BasicUnit("Thaddeus", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 850));
        thaddeus.addAbility(new Selfless(new AbilityDefinition("Selfless", "passive", "desc",
            Map.of("radius", 1.0, "redirect_dmg", 0.25))));
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 200));
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(thaddeus);
        p1.addUnit(ally);
        p2.addUnit(attacker);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(thaddeus, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 1)));

        ally.takeDamage(state, new DamageEvent(attacker, ally, 40));

        assertEquals(200 - 30, ally.getHealth()); // 75% of 40 = 30
        assertEquals(850 - 10, thaddeus.getHealth()); // 25% of 40 = 10 redirected
    }
}
