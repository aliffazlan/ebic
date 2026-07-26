package com.walnutt.combat;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class WeightedEncounterTest {

    @Test
    void rollsAttributesProportionalToTheUnitsOwnStats() {
        // The exact example from the spec: 30 str / 10 agi / 10 int -> 60% / 20% / 20%.
        Unit unit = new BasicUnit("Skewed", Team.PLAYER_ONE, new UnitStats(30, 10, 10, 100));
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        p1.addUnit(unit);
        GameState state = new GameState(new GameMap(2), List.of(p1, new Player("P2", Team.PLAYER_TWO)),
            new Random(7));

        WeightedEncounter encounter = new WeightedEncounter(unit, unit);
        int strength = 0;
        int agility = 0;
        int intelligence = 0;
        int trials = 50_000;
        for (int i = 0; i < trials; i++) {
            switch (encounter.resolveAttackerAttribute(state)) {
                case STRENGTH -> strength++;
                case AGILITY -> agility++;
                case INTELLIGENCE -> intelligence++;
            }
        }

        assertWithinTolerance(strength, trials * 0.6);
        assertWithinTolerance(agility, trials * 0.2);
        assertWithinTolerance(intelligence, trials * 0.2);
    }

    @Test
    void fallsBackToUniformRandom_whenAllAttributesAreZero() {
        Unit unit = new BasicUnit("Statless", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        GameState state = new GameState(new GameMap(2),
            List.of(new Player("P1", Team.PLAYER_ONE), new Player("P2", Team.PLAYER_TWO)), new Random(1));

        WeightedEncounter encounter = new WeightedEncounter(unit, unit);
        // Should not throw (no division by zero) and should return a valid attribute every time.
        for (int i = 0; i < 100; i++) {
            Attribute result = encounter.resolveAttackerAttribute(state);
            assertTrue(result == Attribute.STRENGTH || result == Attribute.AGILITY || result == Attribute.INTELLIGENCE);
        }
    }

    private void assertWithinTolerance(int actual, double expected) {
        assertTrue(Math.abs(actual - expected) < expected * 0.1,
            "actual " + actual + " too far from expected " + expected);
    }
}
