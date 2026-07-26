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
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class DuelTest {

    private Duel newDuel() {
        return new Duel(new AbilityDefinition("Duel", "active", "desc", Map.of(
            "cooldown", 10.0, "cast_range", 1.0, "duration", 4.0,
            "duel_bonus", 10.0, "duel_heal", 0.5, "win_multiplier", 3.0)));
    }

    @Test
    void locksBothUnitsAndForcesMutualAttacksUntilOneDies() {
        Unit caster = new BasicUnit("Valor", Team.PLAYER_ONE, new UnitStats(80, 0, 0, 200));
        Duel duel = newDuel();
        caster.addAbility(duel);
        Unit victim = new BasicUnit("Weakling", Team.PLAYER_TWO, new UnitStats(5, 0, 0, 30));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p2.addUnit(victim);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(1, 0)));

        UnitTarget target = new UnitTarget(victim);
        assertTrue(duel.canUse(state, target));
        duel.onUse(state, target);

        assertTrue(caster.hasStatus(StatusFlag.DUELING));
        assertTrue(victim.hasStatus(StatusFlag.DUELING));

        // Forced attack at the end of caster's turn: caster (str 80) crushes victim (hp 30).
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));

        assertTrue(victim.isDead());
        // Winner reward: +10 to all stats (non-basic multiplier doesn't apply, victim is BASIC) and 50% max HP heal.
        assertEquals(90, caster.getAttributeValue(com.walnutt.combat.Attribute.STRENGTH));
        assertFalse(caster.hasStatus(StatusFlag.DUELING)); // duel resolved, no longer locked
    }

    @Test
    void endsEarlyIfParticipantsAreSeparated() {
        Unit caster = new BasicUnit("Valor", Team.PLAYER_ONE, new UnitStats(80, 0, 0, 200));
        caster.addAbility(newDuel());
        Unit victim = new BasicUnit("Runner", Team.PLAYER_TWO, new UnitStats(5, 0, 0, 30));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(caster);
        p2.addUnit(victim);
        GameMap map = new GameMap(5);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(caster, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(1, 0)));

        Duel duel = (Duel) caster.getAbilities().stream().filter(a -> a instanceof Duel).findFirst().orElseThrow();
        duel.onUse(state, new UnitTarget(victim));
        assertTrue(victim.hasStatus(StatusFlag.DUELING));

        // Simulate external separation (e.g. a future knockback ability) directly via the map.
        map.moveUnit(victim, map.getTile(new Position(0, 4)));

        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));

        assertFalse(victim.isDead());
        assertFalse(caster.hasStatus(StatusFlag.DUELING));
        assertFalse(victim.hasStatus(StatusFlag.DUELING));
    }
}
