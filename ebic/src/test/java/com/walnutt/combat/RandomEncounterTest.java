package com.walnutt.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class RandomEncounterTest {

    @Test
    void ignoresUnitStats_roughlyUniformAcrossManyRolls() {
        // Deliberately lopsided stats - a RandomEncounter must not favor STRENGTH just because it's huge.
        Unit unit = new BasicUnit("Skewed", Team.PLAYER_ONE, new UnitStats(1000, 1, 1, 100));
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        p1.addUnit(unit);
        GameState state = new GameState(new GameMap(2), List.of(p1, new Player("P2", Team.PLAYER_TWO)),
            new Random(42));

        RandomEncounter encounter = new RandomEncounter(unit, unit);
        Map<Attribute, Integer> counts = new EnumMap<>(Attribute.class);
        for (Attribute a : Attribute.values()) {
            counts.put(a, 0);
        }

        int trials = 30_000;
        for (int i = 0; i < trials; i++) {
            Attribute picked = encounter.resolveAttackerAttribute(state);
            counts.merge(picked, 1, Integer::sum);
        }

        double expected = trials / 3.0;
        for (Attribute a : Attribute.values()) {
            double actual = counts.get(a);
            assertTrue(Math.abs(actual - expected) < expected * 0.1,
                a + " count " + actual + " too far from uniform expectation " + expected);
        }
    }
}
