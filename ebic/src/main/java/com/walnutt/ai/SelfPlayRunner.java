package com.walnutt.ai;

import java.util.List;
import java.util.Random;

import com.walnutt.game.Game;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.game.TurnManager;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.Unit;

/**
 * Plays bot against bot, headless, so a scoring change can be judged by whether it
 * actually wins more games.
 *
 * This exists because heuristic weights cannot be tuned by eye: any two of them look
 * equally plausible on paper, and a single watched match is far too noisy to tell them
 * apart. Running both configs over many seeded matches is the only cheap way to know
 * whether a change was an improvement or a placebo.
 *
 * It doubles as coverage no unit test provides - every match exercises real drafting,
 * placement, and dozens of turns of arbitrary ability interactions, which is where
 * crashes in rarely-combined kits actually surface.
 *
 * Matches are capped rather than played to a natural finish: with champion health in
 * the thousands, an evenly matched pair can run far longer than a tuning run wants to
 * wait. A capped match with no winner is scored on remaining army strength instead.
 */
public final class SelfPlayRunner {

    public record MatchResult(Team winner, int turns, double playerOneStrength, double playerTwoStrength) {
        /** The winner, or on a capped match whoever has more army left. Null only on a true tie. */
        public Team effectiveWinner() {
            if (winner != null) {
                return winner;
            }
            if (playerOneStrength > playerTwoStrength) {
                return Team.PLAYER_ONE;
            }
            return playerTwoStrength > playerOneStrength ? Team.PLAYER_TWO : null;
        }
    }

    public record SeriesResult(int matches, int playerOneWins, int playerTwoWins, int draws,
                                int decisiveWins, double averageTurns) {
        public double playerOneWinRate() {
            return matches == 0 ? 0 : (double) playerOneWins / matches;
        }

        @Override
        public String toString() {
            return String.format(
                "%d matches | P1 %d (%.0f%%) - P2 %d (%.0f%%) - draws %d | %d ended in a real kill | avg %.1f turns",
                matches, playerOneWins, playerOneWinRate() * 100,
                playerTwoWins, matches == 0 ? 0 : 100.0 * playerTwoWins / matches,
                draws, decisiveWins, averageTurns);
        }
    }

    private final int maxTurns;

    public SelfPlayRunner(int maxTurns) {
        this.maxTurns = maxTurns;
    }

    /** One match. Both configs get their think delay stripped - a tuning run must not sleep. */
    public MatchResult playMatch(BotConfig playerOneConfig, BotConfig playerTwoConfig, long seed) {
        BotHandler one = new BotHandler(playerOneConfig.withoutThinkDelay());
        BotHandler two = new BotHandler(playerTwoConfig.withoutThinkDelay());
        TeamRoutingHandler router = TeamRoutingHandler.of(one, two);
        Renderer renderer = new SilentRenderer();

        Game game = Game.newFullDraftMatch(router, renderer, new Random(seed));
        GameState state = game.getState();

        // Driven here rather than through Game.start() purely so the turn cap can apply.
        TurnManager turnManager = new TurnManager();
        int turns = 0;
        while (!state.isGameOver() && turns < maxTurns) {
            turnManager.takeTurn(state, state.getInputHandler(), renderer);
            turns++;
        }

        Team winner = state.getWinner() == null ? null : state.getWinner().getTeam();
        return new MatchResult(winner, turns,
            armyStrength(state, Team.PLAYER_ONE), armyStrength(state, Team.PLAYER_TWO));
    }

    /** A series of seeded matches. Seeds are consecutive from {@code firstSeed} so a run is reproducible. */
    public SeriesResult playSeries(BotConfig playerOneConfig, BotConfig playerTwoConfig, int matches, long firstSeed) {
        int oneWins = 0;
        int twoWins = 0;
        int draws = 0;
        int decisive = 0;
        long totalTurns = 0;

        for (int i = 0; i < matches; i++) {
            MatchResult result = playMatch(playerOneConfig, playerTwoConfig, firstSeed + i);
            totalTurns += result.turns();
            if (result.winner() != null) {
                decisive++;
            }
            Team winner = result.effectiveWinner();
            if (winner == Team.PLAYER_ONE) {
                oneWins++;
            } else if (winner == Team.PLAYER_TWO) {
                twoWins++;
            } else {
                draws++;
            }
        }
        return new SeriesResult(matches, oneWins, twoWins, draws, decisive,
            matches == 0 ? 0 : (double) totalTurns / matches);
    }

    /** Surviving army, weighted the same way the evaluator weights it, so "ahead" means the same thing everywhere. */
    private double armyStrength(GameState state, Team team) {
        PositionEvaluator evaluator = new PositionEvaluator(BotConfig.standard());
        Player player = state.getPlayer(team);
        double total = 0;
        for (Unit unit : List.copyOf(player.getUnits())) {
            if (!unit.isDead()) {
                total += evaluator.unitValue(unit);
            }
        }
        return total;
    }

    /**
     * Tuning entry point: {@code java ... SelfPlayRunner [matches] [maxTurns] [seed]}.
     * Runs the standard config against itself (a sanity baseline - it should land near
     * 50%) and then against a version that ignores its free attacks.
     */
    public static void main(String[] args) {
        int matches = args.length > 0 ? Integer.parseInt(args[0]) : 10;
        int maxTurns = args.length > 1 ? Integer.parseInt(args[1]) : 60;
        long seed = args.length > 2 ? Long.parseLong(args[2]) : 1L;

        SelfPlayRunner runner = new SelfPlayRunner(maxTurns);
        BotConfig standard = BotConfig.standard();

        System.out.println("mirror (expect ~50%):");
        System.out.println("  " + runner.playSeries(standard, standard, matches, seed));

        System.out.println("standard vs. random play (expect standard to dominate):");
        System.out.println("  " + runner.playSeries(standard, standard.withBlunderRate(1.0), matches, seed));

        System.out.println("standard vs. no-free-attacks (expect standard to win clearly):");
        System.out.println("  " + runner.playSeries(standard, standard.withTakeFreeAttacks(false), matches, seed));
    }
}
