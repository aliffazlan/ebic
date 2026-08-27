package com.walnutt.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.walnutt.game.Team;

/**
 * Coverage no hand-built test provides: each match here runs a real draft, a real
 * placement, and dozens of turns of whatever abilities the draft happened to produce -
 * which is where crashes in rarely-combined kits actually live.
 *
 * Turn caps are kept low deliberately. A match played to a natural finish takes roughly
 * eighty turns, which is fine for a tuning run but far too slow for the build; these
 * assert the machinery works, and real strength measurement is SelfPlayRunner's own
 * main() (see CLAUDE.md for the baselines it prints).
 */
class SelfPlayRunnerTest {

    @Test
    void aBotVersusBotMatchRunsWithoutErrorAndBothSidesActuallyFight() {
        SelfPlayRunner runner = new SelfPlayRunner(25);
        BotConfig config = BotConfig.standard();

        SelfPlayRunner.MatchResult result = runner.playMatch(config, config, 42L);

        assertEquals(25, result.turns(), "the match should have run to the cap without aborting");
        assertTrue(result.playerOneStrength() > 0, "player one should still have an army");
        assertTrue(result.playerTwoStrength() > 0, "player two should still have an army");
    }

    /**
     * Guards a whole class of silent failure: a bot that never engaged - because it could
     * not path toward anything, or scored every action below its own threshold - would
     * still produce a clean, error-free match with both armies at full health.
     *
     * Calibrated against itself rather than a hardcoded total: the same seed produces the
     * same draft and placement, so a long match must have strictly less army left than a
     * short one. That holds whatever the rosters happen to be worth.
     */
    @Test
    void theArmiesActuallyEngageRatherThanStandingStill() {
        BotConfig config = BotConfig.standard();
        SelfPlayRunner.MatchResult early = new SelfPlayRunner(2).playMatch(config, config, 7L);
        SelfPlayRunner.MatchResult late = new SelfPlayRunner(40).playMatch(config, config, 7L);

        double earlyTotal = early.playerOneStrength() + early.playerTwoStrength();
        double lateTotal = late.playerOneStrength() + late.playerTwoStrength();

        assertTrue(lateTotal < earlyTotal,
            "no army was worn down between turn 2 (" + earlyTotal + ") and turn 40 (" + lateTotal
                + ") - the bots are probably not engaging at all");
    }

    /** Same seed, same configs, same outcome - otherwise A/B tuning results mean nothing. */
    @Test
    void aSeededMatchIsReproducible() {
        SelfPlayRunner runner = new SelfPlayRunner(20);
        BotConfig config = BotConfig.standard();

        SelfPlayRunner.MatchResult first = runner.playMatch(config, config, 99L);
        SelfPlayRunner.MatchResult second = runner.playMatch(config, config, 99L);

        assertEquals(first.playerOneStrength(), second.playerOneStrength(), 1e-9);
        assertEquals(first.playerTwoStrength(), second.playerTwoStrength(), 1e-9);
    }

    /**
     * The tuning loop's own sanity check, in miniature. If taking the free attacks did not
     * measurably help, either the scoring is wrong or the harness is not measuring
     * anything - and every conclusion drawn from it afterwards would be worthless.
     */
    @Test
    void takingFreeAttacksBeatsIgnoringThem() {
        SelfPlayRunner runner = new SelfPlayRunner(60);
        BotConfig standard = BotConfig.standard();

        SelfPlayRunner.SeriesResult series =
            runner.playSeries(standard, standard.withTakeFreeAttacks(false), 8, 300L);

        assertTrue(series.playerOneWins() > series.playerTwoWins(),
            "the config that takes its free attacks should win the series, but it went " + series);
    }

    @Test
    void aCappedMatchWithNoKillIsStillScoredOnArmyStrength() {
        SelfPlayRunner.MatchResult drawn =
            new SelfPlayRunner.MatchResult(null, 10, 5000, 4000);
        assertEquals(Team.PLAYER_ONE, drawn.effectiveWinner());

        SelfPlayRunner.MatchResult decided =
            new SelfPlayRunner.MatchResult(Team.PLAYER_TWO, 10, 9999, 1);
        assertEquals(Team.PLAYER_TWO, decided.effectiveWinner(), "a real kill outranks army strength");

        assertNotNull(new SelfPlayRunner.MatchResult(null, 10, 100, 100));
        assertEquals(null, new SelfPlayRunner.MatchResult(null, 10, 100, 100).effectiveWinner());
    }
}
