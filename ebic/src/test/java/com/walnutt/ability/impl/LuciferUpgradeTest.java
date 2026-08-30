package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.effect.impl.DoomEffect;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.Team;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Lucifer's unlocked abilities. */
class LuciferUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    @Test
    void upgradedDoomSpillsHalfOfEachTickOntoTheCursedUnitsNeighbours() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit lucifer = f.heroWith("Lucifer", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, true, "doom");
        Unit cursed = f.basic("Cursed", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 1);
        Unit neighbour = f.basic("Neighbour", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 2);

        on(lucifer, "doom").onUse(f.state(), new UnitTarget(cursed));
        cursed.getActiveEffect(DoomEffect.class).orElseThrow()
            .onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_TWO));

        assertEquals(480, cursed.getHealth(), "the full 20");
        assertEquals(490, neighbour.getHealth(), "half of it, spilled");
    }

    @Test
    void theBaseDoomTouchesOnlyItsHost() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit lucifer = f.heroWith("Lucifer", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, false, "doom");
        Unit cursed = f.basic("Cursed", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 1);
        Unit neighbour = f.basic("Neighbour", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 2);

        on(lucifer, "doom").onUse(f.state(), new UnitTarget(cursed));
        cursed.getActiveEffect(DoomEffect.class).orElseThrow()
            .onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_TWO));

        assertEquals(480, cursed.getHealth());
        assertEquals(500, neighbour.getHealth());
    }

    /**
     * The bonus is owed only to a brand that was ALREADY burning, so the swing that lays one
     * down never collects on itself.
     */
    @Test
    void upgradedInfernalBladeBurnsAndStunsOnlyOnAFollowUpStrike() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit lucifer = f.heroWith("Lucifer", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, true, "infernal_blade");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 1000), 0, 1);

        // STRENGTH beats INTELLIGENCE, so both swings land: 40 damage apiece.
        CombatEngine.performAttack(f.state(), lucifer, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        assertEquals(960, victim.getHealth(), "the branding swing collects nothing extra");
        assertFalse(victim.hasStatus(StatusFlag.STUNNED));

        CombatEngine.performAttack(f.state(), lucifer, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(890, victim.getHealth(), "40 for the swing, 30 more for the brand");
        assertTrue(victim.hasStatus(StatusFlag.STUNNED));
    }

    @Test
    void theStunRefreshesRatherThanPilingUpTheWayTheBrandDoes() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit lucifer = f.heroWith("Lucifer", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, true, "infernal_blade");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);

        for (int i = 0; i < 4; i++) {
            CombatEngine.performAttack(f.state(), lucifer, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        }

        long stuns = victim.getEffects().stream()
            .filter(e -> "Infernal Blade Stun".equals(e.getName())).count();
        assertEquals(1, stuns, "one stun, refreshed - not four stacked");
        assertEquals(1, victim.getEffects().stream()
            .filter(e -> "Infernal Blade Stun".equals(e.getName())).findFirst().orElseThrow()
            .getRemainingTurns());
    }
}
