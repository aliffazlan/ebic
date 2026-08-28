package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * The three cases that decide whether the debuff tracks what it TOOK rather than a
 * multiplier it re-applies backwards. All three come straight from the design brief.
 */
class ShrinkRayTest {

    private record Fixture(GameState state, ShrinkRay shrinkRay, Unit maxwell, Unit victim) {
    }

    private static Fixture fixture(UnitStats victimStats) {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        ShrinkRay shrinkRay = new ShrinkRay(new AbilityDefinition("Shrink Ray", "active", "desc", Map.of(
            "cooldown", 6.0, "cast_range", 2.0, "duration", 4.0,
            "stat_reduction", 0.2, "hp_reduction", 0.1)));
        maxwell.addAbility(shrinkRay);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, victimStats);

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(maxwell);
        p2.addUnit(victim);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(maxwell, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 1)));
        return new Fixture(state, shrinkRay, maxwell, victim);
    }

    /** Runs the victim's own turn to completion, which is what ticks and then expires the debuff. */
    private static void passVictimTurns(Fixture f, int turns) {
        for (int i = 0; i < turns; i++) {
            f.victim().startTurn(f.state());
            f.victim().endTurn(f.state());
        }
    }

    /**
     * The case a multiplier gets wrong. 100 -> 80 by the beam, then something else steals
     * 10 (Grivath's Cripple does exactly this) -> 70. Expiry must return the 20 that was
     * taken, landing on 90 - not "divide by 0.8" and land on 87 or 100.
     */
    @Test
    void expiryReturnsWhatItTook_notAMultiplier_evenAfterSomethingElseDrainedTheSameStat() {
        Fixture f = fixture(new UnitStats(100, 100, 100, 500));

        f.shrinkRay().onUse(f.state(), new UnitTarget(f.victim()));
        assertEquals(80, (int) f.victim().getEffective(Stat.STRENGTH), "20% of 100 taken");

        // A separate, permanent theft while the victim is shrunk.
        f.victim().addPermanentModifier(StatModifier.flat(Stat.STRENGTH, -10, "Cripple"));
        assertEquals(70, (int) f.victim().getEffective(Stat.STRENGTH));

        passVictimTurns(f, 4);
        assertEquals(90, (int) f.victim().getEffective(Stat.STRENGTH),
            "expiry should hand back exactly the 20 the beam took, leaving the other theft in place");
    }

    /** Recasting shrinks from the CURRENT size (80 -> 64), and expiry still restores the original. */
    @Test
    void recastingStacksFromTheNewSizeAndStillRestoresTheOriginalOnExpiry() {
        Fixture f = fixture(new UnitStats(100, 100, 100, 500));

        f.shrinkRay().onUse(f.state(), new UnitTarget(f.victim()));
        assertEquals(80, (int) f.victim().getEffective(Stat.STRENGTH));

        f.shrinkRay().onUse(f.state(), new UnitTarget(f.victim()));
        assertEquals(64, (int) f.victim().getEffective(Stat.STRENGTH),
            "the second cast takes 20% of 80, not another 20% of 100");

        passVictimTurns(f, 4);
        assertEquals(100, (int) f.victim().getEffective(Stat.STRENGTH),
            "both cuts tracked, so the victim returns to its original size");
        assertEquals(100, (int) f.victim().getEffective(Stat.AGILITY));
        assertEquals(100, (int) f.victim().getEffective(Stat.INTELLIGENCE));
    }

    /**
     * Health is the awkward one: maximum health and HealthPool's real cap are two separate
     * numbers, and both current health and the fraction of the cap are meant to drop.
     * 1000/1000 -> 810/900 -> 900/1000.
     */
    @Test
    void healthLosesBothTheCapAndTheProportion_andExpiryRestoresTheCapKeepingThePercentage() {
        Fixture f = fixture(new UnitStats(100, 100, 100, 1000));
        assertEquals(1000, f.victim().getHealth());
        assertEquals(1000, f.victim().getMaxHealth());

        f.shrinkRay().onUse(f.state(), new UnitTarget(f.victim()));
        assertEquals(900, f.victim().getMaxHealth(), "10% off the maximum");
        assertEquals(900, f.victim().getHealthPool().getMax(), "the real cap must move with the stat");
        assertEquals(810, f.victim().getHealth(), "90% of the new maximum, not a full 900/900");

        passVictimTurns(f, 4);
        assertEquals(1000, f.victim().getMaxHealth(), "the cap comes back");
        assertEquals(1000, f.victim().getHealthPool().getMax());
        assertEquals(900, f.victim().getHealth(), "still on 90% health, now of the restored maximum");
    }
}
