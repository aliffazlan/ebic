package com.walnutt.game;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.function.Predicate;

import com.walnutt.data.UnitDefinition;
import com.walnutt.ui.InputHandler;
import com.walnutt.ui.Renderer;
import com.walnutt.unit.UnitFactory;

/**
 * Interactive pick phase: champion round, then 3 elite rounds, each offering both
 * players a random pair of candidates to choose from. Units are "burned once
 * offered" - both halves of a pair leave the pool the moment they're revealed,
 * whether picked or not, so the whole pool is consumed with no repeats (4
 * champions / 2 players and 12 elites / (2 players x 3 rounds) both divide evenly
 * into pairs). DraftService itself is untouched; this reuses its id lists and
 * calls UnitFactory directly since picks arrive incrementally rather than as one
 * batch.
 */
public class DraftFlow {
    private static final int ELITE_ROUNDS = 3;
    private static final int BASIC_COUNT = 10;
    /** See ConcurrentSetupFlow.REROLL_ATTEMPTS. */
    private static final int REROLL_ATTEMPTS = 8;

    public void run(GameState state, InputHandler input, Renderer renderer) {
        Map<String, UnitDefinition> unitDefs = state.getUnitDefinitions();
        DraftService draftService = new DraftService();
        Player p1 = state.getPlayers().get(0);
        Player p2 = state.getPlayers().get(1);

        List<UnitDefinition> championPool = shuffled(draftService.getAvailableChampions(unitDefs), unitDefs, state.getRandom());
        runRound(state, input, renderer, "Champion", p1, p2, championPool);

        List<UnitDefinition> elitePool = shuffled(draftService.getAvailableElites(unitDefs), unitDefs, state.getRandom());
        for (int round = 1; round <= ELITE_ROUNDS; round++) {
            runRound(state, input, renderer, "Elite " + round + "/" + ELITE_ROUNDS, p1, p2, elitePool);
        }

        for (Player player : List.of(p1, p2)) {
            for (int i = 1; i <= BASIC_COUNT; i++) {
                player.addUnit(UnitFactory.createBasic(player.getName() + " Basic " + i, player.getTeam()));
            }
        }
    }

    private void runRound(GameState state, InputHandler input, Renderer renderer, String roundLabel,
                           Player p1, Player p2, List<UnitDefinition> pool) {
        // Dealt before renderDraftRound below, so a seat that refuses both halves (the bot -
        // see InputHandler.refusesToDraft) gets a different pair before anyone has seen one.
        List<UnitDefinition> p1Options = drawTwoFor(pool, refusalFor(input, p1), state.getRandom());
        List<UnitDefinition> p2Options = drawTwoFor(pool, refusalFor(input, p2), state.getRandom());

        renderer.renderDraftRound(roundLabel, p1, p1Options, p2, p2Options);

        UnitDefinition p1Pick = input.choosePick(state, p1, p1Options);
        p1.addUnit(UnitFactory.createFromDefinition(p1Pick, p1.getTeam(), state.getAbilityDefinitions()));

        UnitDefinition p2Pick = input.choosePick(state, p2, p2Options);
        p2.addUnit(UnitFactory.createFromDefinition(p2Pick, p2.getTeam(), state.getAbilityDefinitions()));
    }

    private List<UnitDefinition> drawTwo(List<UnitDefinition> pool) {
        if (pool.size() < 2) {
            throw new IllegalStateException("Not enough units left in the pool to offer a pick");
        }
        return List.of(pool.remove(0), pool.remove(0));
    }

    private static Predicate<UnitDefinition> refusalFor(InputHandler input, Player player) {
        return definition -> input.refusesToDraft(player, definition);
    }

    /**
     * As ConcurrentSetupFlow.drawTwoFor, and bounded for the same reason - self-play runs two
     * refusing seats against a pool that can hold three refused heroes. Kept here rather than
     * shared because the two flows deal from their pools at different moments (this one round
     * by round, that one all up front) and neither owns the other.
     */
    private List<UnitDefinition> drawTwoFor(List<UnitDefinition> pool, Predicate<UnitDefinition> refused,
                                             Random random) {
        for (int attempt = 0; attempt < REROLL_ATTEMPTS; attempt++) {
            List<UnitDefinition> pair = drawTwo(pool);
            if (pair.stream().anyMatch(definition -> !refused.test(definition))) {
                return pair;
            }
            pool.addAll(pair);
            Collections.shuffle(pool, random);
        }
        List<UnitDefinition> pair = new ArrayList<>(drawTwo(pool));
        for (int i = 0; i < pool.size(); i++) {
            if (!refused.test(pool.get(i))) {
                pool.add(pair.remove(1));
                pair.add(pool.remove(i));
                Collections.shuffle(pair, random);
                break;
            }
        }
        return List.copyOf(pair);
    }

    private List<UnitDefinition> shuffled(List<String> ids, Map<String, UnitDefinition> unitDefs, Random random) {
        List<UnitDefinition> defs = new ArrayList<>(ids.stream().map(unitDefs::get).toList());
        Collections.shuffle(defs, random);
        return defs;
    }
}
