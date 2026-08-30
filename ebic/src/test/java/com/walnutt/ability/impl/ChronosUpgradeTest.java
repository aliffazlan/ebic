package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.atomic.AtomicInteger;
import com.walnutt.TriggerHandler;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.effect.impl.DilationEffect;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.walnutt.ability.target.NoTarget;
import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Chronos's unlocked abilities. */
class ChronosUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    @Test
    void upgradedBacktrackReachesFurther() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit chronos = f.heroWith("Chronos", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 1000),
            0, 0, false, "backtrack");
        assertEquals(3, on(chronos, "backtrack").getRange());

        on(chronos, "backtrack").upgrade();

        assertEquals(4, on(chronos, "backtrack").getRange());
    }

    @Test
    void upgradedBacktrackStrikesEveryEnemyBesideWhereItLands() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit chronos = f.heroWith("Chronos", Team.PLAYER_ONE, new UnitStats(60, 60, 60, 1000),
            0, 0, true, "backtrack");
        Unit near = f.basic("Near", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 3);
        Unit alsoNear = f.basic("AlsoNear", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 1, 2);
        Unit far = f.basic("Far", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 4);
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 1000), 1, 1);

        // Land on (0,2): adjacent to both (0,3) and (1,2), two away from (0,4).
        on(chronos, "backtrack").onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));

        assertTrue(near.getHealth() < 1000, "struck on arrival");
        assertTrue(alsoNear.getHealth() < 1000, "every adjacent enemy, not just one");
        assertEquals(1000, far.getHealth(), "out of reach of the landing");
        assertEquals(1000, ally.getHealth(), "and never his own side");
    }

    @Test
    void theBaseBacktrackArrivesQuietly() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit chronos = f.heroWith("Chronos", Team.PLAYER_ONE, new UnitStats(60, 60, 60, 1000),
            0, 0, false, "backtrack");
        Unit near = f.basic("Near", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 3);

        on(chronos, "backtrack").onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));

        assertEquals(1000, near.getHealth());
    }

    /**
     * Counts how many swings one opening attack turned into.
     *
     * Chronos brings only STRENGTH and the target only AGILITY, so every WEIGHTED roll - which
     * is what a chained attack uses - comes out AGILITY over STRENGTH and misses. The opening
     * attack is forced with explicit attributes so it lands and starts a chain at all.
     */
    private static int swingsFromOneAttack(boolean upgraded) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit chronos = f.heroWith("Chronos", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 5000),
            0, 0, upgraded, "timeless_strike");
        Unit target = f.basic("Target", Team.PLAYER_TWO, new UnitStats(0, 50, 0, 5000), 0, 1);

        AtomicInteger swings = new AtomicInteger();
        f.state().getEventBus().addGlobalListener(new TriggerHandler() {
            @Override
            public void onPostAttack(GameState state, PostAttackEvent event) {
                if (event.attacker() == chronos) {
                    swings.incrementAndGet();
                }
            }
        });

        CombatEngine.performAttack(f.state(), chronos, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        return swings.get();
    }

    @Test
    void theBaseChainStopsAtTheFirstMiss() {
        assertEquals(2, swingsFromOneAttack(false), "the opener, and one chained swing that missed");
    }

    @Test
    void upgradedTheFirstChainedSwingGetsASecondRollWhenItMisses() {
        assertEquals(3, swingsFromOneAttack(true), "the opener, the miss, and the reroll");
    }

    /**
     * Ability.canUse begins with !isPassive, and the base class flips that flag from the JSON's
     * upgrade.type - so becoming a passive is what makes it uncastable, with no code of its own.
     */
    @Test
    void upgradedDilationBecomesAFieldHeSimplyCarries() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit chronos = f.heroWith("Chronos", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 1000),
            0, 0, true, "dilation");

        assertTrue(on(chronos, "dilation").isPassive());
        assertFalse(on(chronos, "dilation").canUse(f.state(), new NoTarget()));
        DilationEffect field = chronos.getActiveEffect(DilationEffect.class).orElseThrow();
        assertTrue(field.getRemainingTurns() > 100, "no clock on it at all");
    }

    @Test
    void theBaseDilationIsASpellWithADuration() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit chronos = f.heroWith("Chronos", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 1000),
            0, 0, false, "dilation");

        assertFalse(on(chronos, "dilation").isPassive());
        assertTrue(chronos.getActiveEffect(DilationEffect.class).isEmpty(), "nothing until it is cast");

        on(chronos, "dilation").onUse(f.state(), new NoTarget());

        assertEquals(4, chronos.getActiveEffect(DilationEffect.class).orElseThrow().getRemainingTurns());
    }
}
