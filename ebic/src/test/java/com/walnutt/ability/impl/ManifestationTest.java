package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class ManifestationTest {

    @Test
    void teleportsAndDebuffsNewlyAdjacentEnemies_silencedAndReducedDamage() {
        Unit mercurial = new BasicUnit("Mercurial", Team.PLAYER_ONE, new UnitStats(40, 40, 30, 810));
        Manifestation manifestation = new Manifestation(new AbilityDefinition("Manifestation", "active", "desc",
            Map.of("cooldown", 5.0, "dmg_reduction", 0.5, "duration", 2.0)));
        mercurial.addAbility(manifestation);
        Unit enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(50, 0, 0, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(mercurial);
        p2.addUnit(enemy);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(mercurial, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(3, 0)));

        manifestation.onUse(state, new TileTarget(map.getTile(new Position(2, 0))));

        assertEquals(new Position(2, 0), mercurial.getPosition());
        assertTrue(enemy.hasStatus(StatusFlag.SILENCED));

        // Enemy's outgoing damage is halved by the debuff.
        Unit dummy = new BasicUnit("Dummy", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 200));
        Player p1b = state.getPlayers().get(0);
        p1b.addUnit(dummy);
        map.moveUnit(dummy, map.getTile(new Position(3, 1)));

        CombatEngine.performAttack(state, enemy, dummy, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        assertEquals(200 - 25, dummy.getHealth()); // 50 * 0.5 = 25
    }
}
