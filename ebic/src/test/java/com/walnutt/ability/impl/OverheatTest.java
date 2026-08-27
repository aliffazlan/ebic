package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.BurnEffect;
import com.walnutt.effect.impl.OverheatTrackerEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class OverheatTest {

    private Unit ember;
    private Unit victim;
    private Unit neighbour;
    private Unit ally;
    private GameState state;

    private GameState setUpBoard() {
        ember = new BasicUnit("Ember", Team.PLAYER_ONE, new UnitStats(10, 10, 90, 970));
        ember.addAbility(new Overheat(new AbilityDefinition("Overheat", "passive", "desc",
            Map.of("threshold", 50.0, "radius", 1.0, "burn_stacks", 1.0))));
        victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 100_000));
        neighbour = new BasicUnit("Neighbour", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 100_000));
        ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100_000));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(ember);
        p1.addUnit(ally);
        p2.addUnit(victim);
        p2.addUnit(neighbour);
        GameMap map = new GameMap(4);
        state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(ember, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 2)));
        map.moveUnit(neighbour, map.getTile(new Position(0, 3)));  // adjacent to victim
        map.moveUnit(ally, map.getTile(new Position(1, 2)));       // also adjacent to victim
        return state;
    }

    private void hit(int damage) {
        DamageEvent event = new DamageEvent(ember, victim, damage);
        event.setCauseLabel("Fireblast");
        victim.takeDamage(state, event);
    }

    private int heat() {
        return victim.getActiveEffect(OverheatTrackerEffect.class).orElseThrow().getAccumulated();
    }

    private static int burnStacks(Unit unit) {
        return unit.getEffects().stream()
            .filter(e -> e instanceof BurnEffect && !e.isExpired())
            .mapToInt(e -> ((BurnEffect) e).getStacks())
            .sum();
    }

    @Test
    void heatBelowTheThresholdDoesNothing() {
        setUpBoard();
        hit(30);

        assertEquals(30, heat());
        assertEquals(0, burnStacks(neighbour), "nothing should be alight yet");
    }

    /** The brief's first example: 60 damage procs once and leaves 10 banked. */
    @Test
    void crossingTheThresholdProcsOnceAndCarriesTheRemainderOver() {
        setUpBoard();
        hit(60);

        assertEquals(10, heat(), "60 - 50 threshold = 10 carried toward the next proc");
        assertEquals(1, burnStacks(neighbour), "the adjacent enemy catches fire");
    }

    /** The brief's second example: 130 damage banks 80, which fires again next turn. */
    @Test
    void oneBigHitProcsOnlyOnce_theBankedRemainderFiresOnALaterTurn() {
        setUpBoard();
        hit(130);

        assertEquals(80, heat(), "only one threshold is consumed per turn");
        assertEquals(1, burnStacks(neighbour));

        // New turn: the latch clears and the banked heat cashes in without any fresh damage.
        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));

        assertEquals(30, heat(), "80 - 50 = 30");
        assertEquals(2, burnStacks(neighbour), "a second stack from the banked heat");
    }

    @Test
    void theOverheatingUnitDoesNotBurnItself() {
        setUpBoard();
        hit(100);

        assertEquals(0, burnStacks(victim), "Overheat sets neighbours alight, never its own host");
        assertEquals(1, burnStacks(neighbour));
    }

    @Test
    void alliesStandingNextToAnOverheatingEnemyAreUnharmed() {
        setUpBoard();
        hit(60);

        assertEquals(0, burnStacks(ally), "Ember's own side never catches fire");
    }

    @Test
    void damageFromSomeoneOtherThanEmberDoesNotBuildHeat() {
        setUpBoard();
        DamageEvent event = new DamageEvent(neighbour, victim, 200);
        victim.takeDamage(state, event);

        assertTrue(victim.getActiveEffect(OverheatTrackerEffect.class).isEmpty(),
            "only Ember's own damage is tracked");
    }

    /**
     * Burn ticks are sourced from Ember, so they feed the very gauge that produces more
     * Burn. The once-per-turn cap is what keeps that loop from compounding.
     */
    @Test
    void theBurnFeedbackLoopStaysBoundedAcrossManyTurns() {
        setUpBoard();
        hit(50);

        for (int turn = 0; turn < 20; turn++) {
            state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));
            state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_ONE));
        }

        int stacks = burnStacks(neighbour);
        assertTrue(stacks <= 25, "growth must stay linear, not runaway - got " + stacks + " stacks");
        assertFalse(victim.isDead() && neighbour.isDead(), "the loop should not wipe the board on its own");
    }

    @Test
    void twoSeparateEmbersTrackTheirOwnHeatIndependently() {
        setUpBoard();
        Unit otherEmber = new BasicUnit("Ember II", Team.PLAYER_ONE, new UnitStats(10, 10, 90, 970));
        otherEmber.addAbility(new Overheat(new AbilityDefinition("Overheat", "passive", "desc",
            Map.of("threshold", 50.0, "radius", 1.0, "burn_stacks", 1.0))));
        state.getPlayers().get(0).addUnit(otherEmber);
        state.getMap().moveUnit(otherEmber, state.getMap().getTile(new Position(2, 0)));

        hit(40);
        DamageEvent other = new DamageEvent(otherEmber, victim, 40);
        victim.takeDamage(state, other);

        List<Effect> trackers = victim.getEffects().stream()
            .filter(e -> e instanceof OverheatTrackerEffect)
            .toList();
        assertEquals(2, trackers.size(), "one gauge per Ember, not a shared pool");
        assertEquals(0, burnStacks(neighbour), "neither reached 50 on its own");
    }
}
