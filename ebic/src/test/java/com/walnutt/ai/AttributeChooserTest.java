package com.walnutt.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumMap;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.EncounterResolver;
import com.walnutt.combat.NormalEncounter;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class AttributeChooserTest {

    /**
     * The payoff matrix is a second statement of a rule the engine already owns, so it can
     * drift from EncounterResolver's. This pins the two together by resolving every one of
     * the nine attribute pairings through the real resolver and comparing.
     */
    @Test
    void thePayoffMatrixMatchesWhatTheRealEncounterResolverProduces() {
        Unit attacker = unit("Attacker", Team.PLAYER_ONE, new UnitStats(37, 12, 25, 500));
        Unit defender = unit("Defender", Team.PLAYER_TWO, new UnitStats(19, 44, 8, 500));
        GameState state = stateWith(attacker, defender);

        double[][] matrix = AttributeChooser.payoffMatrix(attacker, defender);
        EncounterResolver resolver = new EncounterResolver();
        Attribute[] attributes = Attribute.values();

        for (int i = 0; i < attributes.length; i++) {
            for (int j = 0; j < attributes.length; j++) {
                int actual = resolver
                    .resolve(state, new NormalEncounter(attacker, defender, attributes[i], attributes[j]))
                    .getDamage();
                assertEquals(actual, (int) matrix[i][j],
                    "matrix disagrees with the engine for " + attributes[i] + " vs " + attributes[j]);
            }
        }
    }

    /**
     * The point of solving rather than guessing: whatever mixture the bot plays, no fixed
     * attribute an opponent could commit to beats the game's value. This is what makes the
     * choice unexploitable rather than merely reasonable.
     */
    @Test
    void noFixedCounterStrategyBeatsTheBotsMixture() {
        Unit attacker = unit("Attacker", Team.PLAYER_ONE, new UnitStats(30, 10, 10, 500));
        Unit defender = unit("Defender", Team.PLAYER_TWO, new UnitStats(10, 30, 10, 500));

        double[][] payoff = AttributeChooser.payoffMatrix(attacker, defender);
        MatrixGameSolver.Solution solution = MatrixGameSolver.solve(payoff);
        double value = solution.value();

        // Defender commits to one attribute: the attacker's mixture still earns at least the value.
        for (int j = 0; j < 3; j++) {
            double earned = 0;
            for (int i = 0; i < 3; i++) {
                earned += solution.rowStrategy()[i] * payoff[i][j];
            }
            assertTrue(earned >= value - 1e-6,
                "a defender always picking " + Attribute.values()[j] + " would beat the attacker's mixture");
        }
        // Attacker commits to one attribute: the defender's mixture still concedes at most the value.
        for (int i = 0; i < 3; i++) {
            double conceded = 0;
            for (int j = 0; j < 3; j++) {
                conceded += payoff[i][j] * solution.columnStrategy()[j];
            }
            assertTrue(conceded <= value + 1e-6,
                "an attacker always picking " + Attribute.values()[i] + " would beat the defender's mixture");
        }
    }

    @Test
    void samplingFollowsTheSolvedMixtureAndBothRolesAreRepresented() {
        // 30/10/10 vs 10/30/10 has a genuinely mixed solution, so a chooser that quietly
        // collapsed to one attribute would show up here.
        Unit attacker = unit("Attacker", Team.PLAYER_ONE, new UnitStats(30, 10, 10, 500));
        Unit defender = unit("Defender", Team.PLAYER_TWO, new UnitStats(10, 30, 10, 500));
        Random random = new Random(7L);

        Map<Attribute, Integer> attackerPicks = new EnumMap<>(Attribute.class);
        Map<Attribute, Integer> defenderPicks = new EnumMap<>(Attribute.class);
        int trials = 30_000;
        for (int i = 0; i < trials; i++) {
            attackerPicks.merge(
                AttributeChooser.choose(attacker, defender, AttributeChooser.Role.ATTACKER, random), 1, Integer::sum);
            defenderPicks.merge(
                AttributeChooser.choose(defender, attacker, AttributeChooser.Role.DEFENDER, random), 1, Integer::sum);
        }

        MatrixGameSolver.Solution expected =
            MatrixGameSolver.solve(AttributeChooser.payoffMatrix(attacker, defender));
        for (int i = 0; i < 3; i++) {
            Attribute attribute = Attribute.values()[i];
            assertEquals(expected.rowStrategy()[i],
                attackerPicks.getOrDefault(attribute, 0) / (double) trials, 0.02,
                "attacker sampled " + attribute + " at the wrong rate");
            assertEquals(expected.columnStrategy()[i],
                defenderPicks.getOrDefault(attribute, 0) / (double) trials, 0.02,
                "defender sampled " + attribute + " at the wrong rate");
        }
    }

    /**
     * The case that most justifies solving the encounter instead of using a rule of thumb.
     *
     * A pure-Strength attacker meets a pure-Intelligence defender. "Attack with your best
     * attribute" looks obviously right and is close to the worst available choice: the
     * defender simply answers Agility, which beats Strength, and takes nothing. The
     * solved answer leans almost entirely on Intelligence - punishing the Agility defence
     * the defender is forced toward - while keeping just enough Strength that committing
     * to Agility stops being safe.
     */
    @Test
    void theSolvedAnswerBeatsTheObviousOneWhenTheBestAttributeIsTheExploitableOne() {
        Unit attacker = unit("Attacker", Team.PLAYER_ONE, new UnitStats(50, 5, 5, 500));
        Unit defender = unit("Defender", Team.PLAYER_TWO, new UnitStats(5, 5, 50, 500));

        double[][] payoff = AttributeChooser.payoffMatrix(attacker, defender);
        MatrixGameSolver.Solution solution = MatrixGameSolver.solve(payoff);

        assertTrue(solution.rowStrategy()[2] > solution.rowStrategy()[0],
            "attacker should lean Intelligence, not its much larger Strength");
        assertTrue(solution.columnStrategy()[1] > 0.5,
            "defender should lean Agility, the attribute that blanks the attacker's Strength");

        // The gain is in the worst case, not the average one. Against an equilibrium
        // defender every attribute in the mixture's support earns exactly the value, so
        // pure Strength ties there - what separates them is what a defender who has
        // *worked out* the attacker's habit can do about it. Committing to Strength can
        // be held to nothing by answering Agility; the mixture cannot be held below the
        // game's value by any defence at all. That guarantee is the whole point.
        double worstCaseForPureStrength = Math.min(payoff[0][0], Math.min(payoff[0][1], payoff[0][2]));
        assertEquals(0.0, worstCaseForPureStrength, 1e-9,
            "a defender expecting Strength should be able to blank it entirely");
        assertTrue(solution.value() > worstCaseForPureStrength,
            "the mixture must guarantee more than the best single attribute can be held to");
    }

    @Test
    void expectedDamageIsTheValueOfTheEncounter() {
        Unit attacker = unit("Attacker", Team.PLAYER_ONE, new UnitStats(20, 20, 20, 300));
        Unit defender = unit("Defender", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 300));

        // Mirror-image basics: the classic symmetric case, uniform play, one third of the
        // time the attacker's attribute wins outright for its full value.
        assertEquals(20 / 3.0, AttributeChooser.expectedDamage(attacker, defender), 1e-6);
    }

    private Unit unit(String name, Team team, UnitStats stats) {
        return new BasicUnit(name, team, stats);
    }

    private GameState stateWith(Unit one, Unit two) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        p1.addUnit(one);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p2.addUnit(two);
        return new GameState(new GameMap(3), java.util.List.of(p1, p2), new Random(1));
    }
}
