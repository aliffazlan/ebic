package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.walnutt.ability.Ability;
import com.walnutt.ai.BotConfig;
import com.walnutt.ai.BotHandler;
import com.walnutt.ai.SilentRenderer;
import com.walnutt.ai.TeamRoutingHandler;
import com.walnutt.data.AbilityFactory;
import com.walnutt.game.Game;
import com.walnutt.game.GameState;
import com.walnutt.game.Team;
import com.walnutt.game.TurnManager;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.Unit;

/**
 * Plays real matches with EVERY implemented upgrade unlocked on both sides.
 *
 * The per-ability tests each check one upgrade in a hand-built board; this checks the far more
 * dangerous thing they cannot, which is what happens when a couple of dozen of them run against
 * each other for forty turns. Interaction crashes - a reflected hit re-entering a damage hook, a
 * summon despawning inside another ability's iteration - only ever show up here.
 *
 * Seeded and capped, so it is deterministic and cheap. Several seeds, because a single draft only
 * ever exercises the eight heroes it happened to deal.
 */
class UpgradedMatchSmokeTest {

    /** Unlocks everything on the board that has an implemented upgrade, both teams. */
    private static int unlockEverything(GameState state) {
        int unlocked = 0;
        for (Unit unit : new ArrayList<>(state.getAllActiveUnits())) {
            for (Ability ability : new ArrayList<>(unit.getAbilities())) {
                String id = ability.getDefinitionId();
                if (id != null && AbilityFactory.isUpgradeImplemented(id) && ability.canUpgrade()) {
                    ability.upgrade();
                    unlocked++;
                }
            }
        }
        return unlocked;
    }

    private static int playFullyUpgraded(long seed, int maxTurns) {
        BotConfig config = BotConfig.standard().withoutThinkDelay();
        TeamRoutingHandler router = TeamRoutingHandler.of(new BotHandler(config), new BotHandler(config));
        Renderer renderer = new SilentRenderer();

        Game game = Game.newFullDraftMatch(router, renderer, new Random(seed));
        GameState state = game.getState();
        int unlocked = unlockEverything(state);

        TurnManager turnManager = new TurnManager();
        int turns = 0;
        while (!state.isGameOver() && turns < maxTurns) {
            turnManager.takeTurn(state, state.getInputHandler(), renderer);
            // Gadgets and copies arrive mid-match, so keep unlocking as the kits grow.
            unlockEverything(state);
            turns++;
        }
        return unlocked;
    }

    @Test
    @Timeout(120)
    void everyImplementedUpgradeSurvivesRealMatchesRunningAgainstEachOther() {
        List<Long> seeds = List.of(1L, 7L, 42L, 99L, 2024L);
        int totalUnlocked = 0;
        for (long seed : seeds) {
            totalUnlocked += playFullyUpgraded(seed, 40);
        }

        // Eight drafted heroes a match across five drafts, so this should be well into double
        // figures - a zero here would mean the smoke test silently upgraded nothing.
        assertTrue(totalUnlocked > 20,
            "expected the drafts to have unlocked plenty of abilities, got " + totalUnlocked);
    }

    /**
     * The same again with the bots taking every free attack and never blundering, which pushes
     * far more combat through the upgraded passives per turn.
     */
    @Test
    @Timeout(60)
    void anAggressiveMatchWithEverythingUnlockedAlsoSurvives() {
        BotConfig config = BotConfig.standard().withoutThinkDelay()
            .withTakeFreeAttacks(true).withBlunderRate(0);
        TeamRoutingHandler router = TeamRoutingHandler.of(new BotHandler(config), new BotHandler(config));
        Renderer renderer = new SilentRenderer();

        Game game = Game.newFullDraftMatch(router, renderer, new Random(5L));
        GameState state = game.getState();
        unlockEverything(state);

        TurnManager turnManager = new TurnManager();
        for (int i = 0; i < 40 && !state.isGameOver(); i++) {
            turnManager.takeTurn(state, state.getInputHandler(), renderer);
            unlockEverything(state);
        }

        assertTrue(state.getPlayer(Team.PLAYER_ONE).getUnits().size()
            + state.getPlayer(Team.PLAYER_TWO).getUnits().size() > 0, "the match still has units");
    }
}
