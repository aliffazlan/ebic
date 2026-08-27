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
}
