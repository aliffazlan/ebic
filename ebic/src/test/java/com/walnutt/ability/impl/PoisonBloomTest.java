package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.PoisonEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class PoisonBloomTest {

    @Test
    void durationTicksUpDuringGrowthWindowThenDown() {
        Unit spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        PoisonBloom bloom = new PoisonBloom(new AbilityDefinition("Poison Bloom", "active", "desc", Map.of(
            "cooldown", 6.0, "cast_range", 3.0, "duration", 2.0, "initial_poison", 10.0,
            "duration_increase", 1.0, "infect_radius", 1.0)));
        spitter.addAbility(bloom);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(spitter);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(spitter, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(0, 2)));

        bloom.onUse(state, new UnitTarget(target));

        PoisonEffect poison = findPoison(target);
        assertNotNull(poison);
        assertEquals(10, poison.getRemainingTurns());

        target.endTurn(state); // growthTurnsRemaining=2 -> ticks UP
        assertEquals(11, findPoison(target).getRemainingTurns());

        target.endTurn(state); // growthTurnsRemaining=1 -> ticks UP again
        assertEquals(12, findPoison(target).getRemainingTurns());

        target.endTurn(state); // growth window used up -> ticks DOWN normally now
        assertEquals(11, findPoison(target).getRemainingTurns());
    }

    @Test
    void burstsIntoNearbyEnemiesOnDeathBasedOnStacksAtDeath() {
        Unit spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        PoisonBloom bloom = new PoisonBloom(new AbilityDefinition("Poison Bloom", "active", "desc", Map.of(
            "cooldown", 6.0, "cast_range", 3.0, "duration", 0.0, "initial_poison", 6.0,
            "duration_increase", 1.0, "infect_radius", 1.0)));
        spitter.addAbility(bloom);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 5));
        Unit nearbyEnemy = new BasicUnit("Nearby", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(spitter);
        p2.addUnit(victim);
        p2.addUnit(nearbyEnemy);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(spitter, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 2)));
        map.moveUnit(nearbyEnemy, map.getTile(new Position(0, 1)));

        bloom.onUse(state, new UnitTarget(victim)); // no growth window (duration=0) -> stacks stay at 6

        victim.takeDamage(state, new DamageEvent(spitter, victim, 5)); // kills victim (hp 5)

        assertNotNull(findPoison(nearbyEnemy), "death burst should apply poison to the nearby enemy");
        assertEquals(6, findPoison(nearbyEnemy).getRemainingTurns());
    }

    private PoisonEffect findPoison(Unit unit) {
        for (Effect effect : unit.getEffects()) {
            if (effect instanceof PoisonEffect poisonEffect) {
                return poisonEffect;
            }
        }
        return null;
    }
}
