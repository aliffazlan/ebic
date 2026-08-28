package com.walnutt.effect.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Two barriers on one unit must absorb one after the other, not each independently.
 *
 * This already worked - each BarrierEffect reads the live event.getDamage() and writes
 * back through modifyDamage, and EventBus dispatches a unit's effects sequentially - but
 * nothing pinned it, so a future change to either the barrier or the dispatch order could
 * silently turn one hit into two absorptions.
 */
class BarrierStackingTest {

    private record Fixture(GameState state, Unit target, Unit attacker) {
    }

    private static Fixture fixture() {
        Unit target = new BasicUnit("Target", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 500));
        Unit attacker = new BasicUnit("Attacker", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 500));
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(target);
        p2.addUnit(attacker);
        GameState state = new GameState(new GameMap(3), List.of(p1, p2), new Random(1));
        return new Fixture(state, target, attacker);
    }

    @Test
    void oneHitIsAbsorbedOnceInTotal_notOncePerBarrier() {
        Fixture f = fixture();
        BarrierEffect first = new BarrierEffect("Shield A", "desc", 5, 50);
        BarrierEffect second = new BarrierEffect("Shield B", "desc", 5, 50);
        f.target().addEffect(first);
        f.target().addEffect(second);

        f.target().takeDamage(f.state(), new DamageEvent(f.attacker(), f.target(), 10));

        assertEquals(500, f.target().getHealth(), "nothing should reach health");
        assertEquals(90, first.getRemainingBarrierHp() + second.getRemainingBarrierHp(),
            "10 damage must cost 10 barrier in total across both, not 10 from each");
        assertEquals(40, first.getRemainingBarrierHp(), "the first barrier takes it");
        assertEquals(50, second.getRemainingBarrierHp(), "the second is untouched");
    }

    @Test
    void overflowFromABrokenBarrierContinuesIntoTheNextOne() {
        Fixture f = fixture();
        BarrierEffect nearlyGone = new BarrierEffect("Shield A", "desc", 5, 2);
        BarrierEffect fresh = new BarrierEffect("Shield B", "desc", 5, 50);
        f.target().addEffect(nearlyGone);
        f.target().addEffect(fresh);

        f.target().takeDamage(f.state(), new DamageEvent(f.attacker(), f.target(), 10));

        assertEquals(0, nearlyGone.getRemainingBarrierHp(), "the 2 HP barrier breaks");
        assertEquals(42, fresh.getRemainingBarrierHp(), "and the remaining 8 lands on the next one");
        assertEquals(500, f.target().getHealth(), "still nothing reaches health");
    }

    @Test
    void damageBeyondEveryBarrierReachesHealth() {
        Fixture f = fixture();
        f.target().addEffect(new BarrierEffect("Shield A", "desc", 5, 2));
        f.target().addEffect(new BarrierEffect("Shield B", "desc", 5, 3));

        f.target().takeDamage(f.state(), new DamageEvent(f.attacker(), f.target(), 10));

        assertEquals(495, f.target().getHealth(), "5 absorbed between them, 5 through");
    }
}
