package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BurnEffect;
import com.walnutt.effect.impl.BurningGroundEffect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class EmberTest {

    private Unit ember;
    private Unit enemy;
    private Unit ally;
    private GameState state;
    private GameMap map;

    private void setUpBoard() {
        ember = new ChampionUnit("Ember", Team.PLAYER_ONE, new UnitStats(35, 50, 94, 970));
        enemy = new BasicUnit("Enemy", Team.PLAYER_TWO, new UnitStats(10, 10, 10, 100_000));
        ally = new BasicUnit("Ally", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 100_000));
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(ember);
        p1.addUnit(ally);
        p2.addUnit(enemy);
        map = new GameMap(5);
        state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(ember, map.getTile(new Position(0, 0)));
        map.moveUnit(enemy, map.getTile(new Position(0, 2)));
        map.moveUnit(ally, map.getTile(new Position(1, 0)));
    }

    private static int stacksOn(Unit unit) {
        return unit.getEffects().stream()
            .filter(e -> e instanceof BurnEffect && !e.isExpired())
            .mapToInt(e -> ((BurnEffect) e).getStacks())
            .sum();
    }

    @Test
    void burnRefreshesItsDurationRatherThanExtendingIt() {
        setUpBoard();
        BurnEffect.apply(state, enemy, ember, 2);
        BurnEffect burn = enemy.getActiveEffect(BurnEffect.class).orElseThrow();
        int fullDuration = burn.getRemainingTurns();

        burn.tick();
        assertEquals(fullDuration - 1, burn.getRemainingTurns());

        BurnEffect.apply(state, enemy, ember, 1);

        assertEquals(3, stacksOn(enemy), "stacks accumulate");
        assertEquals(fullDuration, burn.getRemainingTurns(),
            "the timer resets to full rather than stacking more turns on");
    }

    @Test
    void burnDealsDamagePerStackOnTheOwnersTurn() {
        setUpBoard();
        BurnEffect.apply(state, enemy, ember, 3);

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));

        assertEquals(3 * 10, 100_000 - enemy.getHealth(), "3 stacks x 10 damage");
    }

    @Test
    void fireblastDamagesAndIgnites() {
        setUpBoard();
        Fireblast fireblast = new Fireblast(new AbilityDefinition("Fireblast", "active", "desc",
            Map.of("cooldown", 2.0, "cast_range", 3.0, "damage", 24.0, "burn_stacks", 3.0)));
        ember.addAbility(fireblast);

        assertTrue(fireblast.canUse(state, new UnitTarget(enemy)));
        fireblast.onUse(state, new UnitTarget(enemy));

        assertEquals(24, 100_000 - enemy.getHealth());
        assertEquals(3, stacksOn(enemy));
    }

    @Test
    void fireblastCannotBeAimedAtAllies() {
        setUpBoard();
        Fireblast fireblast = new Fireblast(new AbilityDefinition("Fireblast", "active", "desc",
            Map.of("cooldown", 2.0, "cast_range", 3.0, "damage", 24.0, "burn_stacks", 3.0)));
        ember.addAbility(fireblast);

        assertFalse(fireblast.canUse(state, new UnitTarget(ally)));
    }

    private Eruption newEruption() {
        return new Eruption(new AbilityDefinition("Eruption", "active", "desc", Map.of(
            "cooldown", 3.0, "duration", 7.0, "cast_range", 3.0,
            "burn_stacks_init", 2.0, "burn_stacks", 1.0)));
    }

    @Test
    void eruptionIgnitesWhoeverIsStandingOnTheTileImmediately() {
        setUpBoard();
        Eruption eruption = newEruption();
        ember.addAbility(eruption);

        eruption.onUse(state, new TileTarget(map.getTile(new Position(0, 2))));

        assertEquals(2, stacksOn(enemy), "burn_stacks_init applied on cast");
        assertTrue(ember.getActiveEffect(BurningGroundEffect.class).isPresent());
    }

    @Test
    void enemiesEndingTheirTurnOnBurningGroundCatchFire() {
        setUpBoard();
        Eruption eruption = newEruption();
        ember.addAbility(eruption);
        // Ignite an empty tile, then walk the enemy onto it.
        eruption.onUse(state, new TileTarget(map.getTile(new Position(0, 3))));
        assertEquals(0, stacksOn(enemy));

        map.moveUnit(enemy, map.getTile(new Position(0, 3)));
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_TWO));

        assertEquals(1, stacksOn(enemy), "one stack for ending a turn in the flames");
    }

    /** Ember's own side walks through the fire unharmed. */
    @Test
    void alliesEndingTheirTurnOnBurningGroundAreUnaffected() {
        setUpBoard();
        Eruption eruption = newEruption();
        ember.addAbility(eruption);
        eruption.onUse(state, new TileTarget(map.getTile(new Position(0, 3))));

        map.moveUnit(ally, map.getTile(new Position(0, 3)));
        state.getEventBus().publish(state, new TurnEndEvent(Team.PLAYER_ONE));

        assertEquals(0, stacksOn(ally), "burning ground never harms Ember's own team");
    }

    /**
     * Re-lighting ground that is already alight used to be refused. As of the v0.3.0 rebalance
     * it refreshes instead - ground about to go out is worth a cooldown - and refreshing rather
     * than stacking is what stops one tile burning its occupant twice a round.
     */
    @Test
    void eruptionCanRelightBurningGround_refreshingItRatherThanStackingASecondFire() {
        setUpBoard();
        Eruption eruption = newEruption();
        ember.addAbility(eruption);
        TileTarget burning = new TileTarget(map.getTile(new Position(0, 2)));

        assertTrue(eruption.canUse(state, burning));
        eruption.onUse(state, burning);
        BurningGroundEffect ground = ember.getActiveEffect(BurningGroundEffect.class).orElseThrow();
        ground.setRemainingTurns(2);

        // Cooldown would block it anyway, so clear that to isolate the rule under test.
        eruption.decreaseCooldown(99);
        state.setRemainingMoves(3);

        // Re-lighting used to be refused outright. It now refreshes: ground that is about to go
        // out is worth a cooldown, and a second patch on one tile would burn its occupant twice
        // a round rather than once.
        assertTrue(eruption.canUse(state, burning), "burning ground can be lit again");
        eruption.onUse(state, burning);

        assertEquals(1, BurningGroundEffect.activeGrounds(state).size(), "one fire, not two");
        assertEquals(7, BurningGroundEffect.activeGrounds(state).get(0).getRemainingTurns(),
            "and its clock is back to full");
    }

    /** A fire that has actually gone out is lit fresh, not refreshed - there is nothing to refresh. */
    @Test
    void aBurntOutTileIsLitFreshRatherThanRefreshed() {
        setUpBoard();
        Eruption eruption = newEruption();
        ember.addAbility(eruption);
        TileTarget tile = new TileTarget(map.getTile(new Position(0, 2)));

        eruption.onUse(state, tile);
        eruption.decreaseCooldown(99);
        state.setRemainingMoves(3);

        BurningGroundEffect ground = ember.getActiveEffect(BurningGroundEffect.class).orElseThrow();
        ground.setRemainingTurns(0);
        ember.removeExpiredEffects(state);

        assertTrue(eruption.canUse(state, tile), "the fire is out, so it can be lit again");
        eruption.onUse(state, tile);
        assertEquals(1, BurningGroundEffect.activeGrounds(state).size());
    }
}
