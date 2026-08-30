package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.effect.impl.SuperiorMasteryEffect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Joker's unlocked abilities. */
class JokerUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    /**
     * The free cast reuses the same currency Maxwell's Capacitor Bank spends, which
     * Ability.getMoveCost already reads - so the assertion is simply that a cast stops costing
     * a move point.
     */
    @Test
    void upgradedSuperiorMasteryMakesTheFirstAbilityEachTurnFree() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit joker = f.heroWith("Joker", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 2000),
            0, 0, true, "superior_mastery", "perplexing_shot");
        Ability shot = on(joker, "perplexing_shot");

        assertEquals(0, shot.getMoveCost(f.state()), "the first one costs nothing");

        // Spend it, the way a real cast does.
        joker.getEffects().forEach(e -> e.onAbilityUsed(f.state(),
            new AbilityCastEvent(joker, shot, new NoTarget(), AbilityCastEvent.Phase.POST)));

        assertEquals(1, shot.getMoveCost(f.state()), "and the next one costs an action again");
    }

    @Test
    void theAllowanceComesBackEachTurn() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit joker = f.heroWith("Joker", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 2000),
            0, 0, true, "superior_mastery", "perplexing_shot");
        Ability shot = on(joker, "perplexing_shot");
        SuperiorMasteryEffect mastery = joker.getActiveEffect(SuperiorMasteryEffect.class).orElseThrow();

        joker.getEffects().forEach(e -> e.onAbilityUsed(f.state(),
            new AbilityCastEvent(joker, shot, new NoTarget(), AbilityCastEvent.Phase.POST)));
        assertEquals(1, shot.getMoveCost(f.state()));

        mastery.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(0, shot.getMoveCost(f.state()));
    }

    @Test
    void theBaseSuperiorMasteryGrantsNoFreeCasts() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit joker = f.heroWith("Joker", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 2000),
            0, 0, false, "superior_mastery", "perplexing_shot");

        assertEquals(1, on(joker, "perplexing_shot").getMoveCost(f.state()));
    }

    /**
     * A copy arrives unlocked whether or not the unit it was taken from had unlocked it, and
     * never expires on its own - only being replaced gives one up.
     */
    @Test
    void upgradedMimicTakesCopiesForGoodAndAlreadyUnlocked() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit joker = f.heroWith("Joker", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 2000),
            0, 0, true, "mimic");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 2000), 0, 2);
        Ability theirs = UpgradeFixture.ability("plasma_cannon");
        victim.addAbility(theirs);

        Mimic mimic = (Mimic) on(joker, "mimic");
        // PRE, not POST: Mimic remembers the moment an enemy commits to a cast.
        mimic.onAbilityUsed(f.state(),
            new AbilityCastEvent(victim, theirs, new NoTarget(), AbilityCastEvent.Phase.PRE));
        mimic.onUse(f.state(), new com.walnutt.ability.target.UnitTarget(victim));

        Ability copy = joker.getAbilities().stream()
            .filter(a -> "plasma_cannon".equals(a.getDefinitionId())).findFirst().orElseThrow();
        assertTrue(copy.isUpgraded(), "it arrives unlocked");
        assertEquals(120, copy.getStats().get("damage").intValue());
        assertFalse(theirs.isUpgraded(), "the original is untouched");
    }

    @Test
    void theBaseMimicTakesAnOrdinaryCopyOnALease() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit joker = f.heroWith("Joker", Team.PLAYER_ONE, new UnitStats(40, 40, 40, 2000),
            0, 0, false, "mimic");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(20, 20, 20, 2000), 0, 2);
        Ability theirs = UpgradeFixture.ability("plasma_cannon");
        victim.addAbility(theirs);

        Mimic mimic = (Mimic) on(joker, "mimic");
        mimic.onAbilityUsed(f.state(),
            new AbilityCastEvent(victim, theirs, new NoTarget(), AbilityCastEvent.Phase.PRE));
        mimic.onUse(f.state(), new com.walnutt.ability.target.UnitTarget(victim));

        Ability copy = joker.getAbilities().stream()
            .filter(a -> "plasma_cannon".equals(a.getDefinitionId())).findFirst().orElseThrow();
        assertFalse(copy.isUpgraded());
        assertEquals(60, copy.getStats().get("damage").intValue());
    }
}
