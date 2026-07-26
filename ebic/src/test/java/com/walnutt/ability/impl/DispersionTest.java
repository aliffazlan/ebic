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

class DispersionTest {

    @Test
    void reflectsAPortionOfDamageTakenToEveryAdjacentEnemy() {
        Unit mercurial = new BasicUnit("Mercurial", Team.PLAYER_ONE, new UnitStats(40, 40, 30, 810));
        mercurial.addAbility(new Dispersion(new AbilityDefinition("Dispersion", "passive", "desc",
            Map.of("radius", 1.0, "dmg_reflect", 0.25))));
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 200));
        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 200));
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(mercurial);
        p1.addUnit(ally);
        p2.addUnit(attacker);
        p2.addUnit(bystander);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(mercurial, map.getTile(new Position(0, 0)));
        map.moveUnit(attacker, map.getTile(new Position(1, 0)));
        map.moveUnit(bystander, map.getTile(new Position(0, 1)));
        map.moveUnit(ally, map.getTile(new Position(-1, 0)));

        mercurial.takeDamage(state, new DamageEvent(attacker, mercurial, 40));

        assertEquals(200 - 10, attacker.getHealth(), "adjacent enemy reflects 25% of the 40 damage taken");
        assertEquals(200 - 10, bystander.getHealth(), "every adjacent enemy is hit, not just the attacker");
        assertEquals(200, ally.getHealth(), "adjacent allies are untouched by the reflect");
        assertEquals(810 - 40, mercurial.getHealth(), "Mercurial still takes the full original hit");
    }
}
