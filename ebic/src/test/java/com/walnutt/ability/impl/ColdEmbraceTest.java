package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class ColdEmbraceTest {

    private ColdEmbrace newAbility() {
        return new ColdEmbrace(new AbilityDefinition("Cold Embrace", "active", "desc",
            Map.of("cooldown", 6.0, "cast_range", 4.0, "duration", 3.0, "dmg_heal", 30.0)));
    }

    @Test
    void freezesEnemy_immuneToNormalAttacksButTakesPeriodicDamage() {
        Unit auroth = new BasicUnit("Auroth", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        ColdEmbrace ability = newAbility();
        auroth.addAbility(ability);
        Unit enemyAttacker = new BasicUnit("Attacker", Team.PLAYER_ONE, new UnitStats(999, 0, 0, 100));
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 200));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(auroth);
        p1.addUnit(enemyAttacker);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(auroth, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(0, 2)));
        map.moveUnit(enemyAttacker, map.getTile(new Position(0, 1)));

        ability.onUse(state, new UnitTarget(target));
        assertTrue(target.hasStatus(StatusFlag.FROZEN));
        assertTrue(target.hasStatus(StatusFlag.INVULNERABLE));

        // A normal attack (999 strength!) does nothing - INVULNERABLE blocks it entirely.
        CombatEngine.performAttack(state, enemyAttacker, target, Attribute.STRENGTH, Attribute.STRENGTH);
        assertEquals(200, target.getHealth());

        // But the freeze's own periodic damage still lands (bypasses invulnerability).
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
        assertEquals(170, target.getHealth());
    }

    @Test
    void healsAllyInsteadOfDamaging() {
        Unit auroth = new BasicUnit("Auroth", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        auroth.addAbility(newAbility());
        Unit ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 200));
        ally.getHealthPool().setCurrent(100);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        p1.addUnit(auroth);
        p1.addUnit(ally);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, new Player("P2", Team.PLAYER_TWO)), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(auroth, map.getTile(new Position(0, 0)));
        map.moveUnit(ally, map.getTile(new Position(0, 2)));

        ColdEmbrace ability = (ColdEmbrace) auroth.getAbilities().stream()
            .filter(a -> a instanceof ColdEmbrace).findFirst().orElseThrow();
        ability.onUse(state, new UnitTarget(ally));

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        assertEquals(130, ally.getHealth());
    }
}
