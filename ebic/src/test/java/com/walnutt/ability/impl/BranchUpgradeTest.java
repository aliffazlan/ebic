package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.effect.impl.BranchiggaLifespanEffect;
import com.walnutt.effect.impl.OvergrowthEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Branch's unlocked abilities. */
class BranchUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    private static List<Unit> branchiggas(UpgradeFixture f) {
        return f.state().getPlayer(Team.PLAYER_ONE).getUnits().stream()
            .filter(u -> "Branchigga".equals(u.getName()) && !u.isDead()).toList();
    }

    private static List<Unit> branchlings(UpgradeFixture f) {
        return f.state().getAllActiveUnits().stream()
            .filter(u -> "Branchling".equals(u.getName()) && !u.isDead()).toList();
    }

    private record Grove(UpgradeFixture fixture, Unit branch, Unit killer) {
    }

    private static Grove grove(boolean upgraded) {
        UpgradeFixture f = UpgradeFixture.create(5, 3);
        Unit branch = f.heroWith("Branch", Team.PLAYER_ONE, new UnitStats(40, 20, 30, 1000),
            0, 0, upgraded, "overgrowth", "sprout");
        Unit killer = f.basic("Killer", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 3, 0);

        on(branch, "overgrowth").onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 3))));
        return new Grove(f, branch, killer);
    }

    @Test
    void upgradedAKilledBranchlingGrowsABranchiggaWhereItFell() {
        Grove g = grove(true);
        Unit victim = branchlings(g.fixture).get(0);
        Position fell = victim.getPosition();

        victim.takeDamage(g.fixture.state(), new DamageEvent(g.killer, victim, 9999));

        assertTrue(victim.isDead());
        List<Unit> grown = branchiggas(g.fixture);
        assertEquals(1, grown.size());
        assertEquals(fell, grown.get(0).getPosition(), "where it fell");
        assertTrue(grown.get(0).getAbilities().stream().anyMatch(a -> a instanceof Move),
            "a real unit that moves and attacks");
        assertTrue(grown.get(0).getAbilities().stream()
            .anyMatch(a -> "branchling_aura".equals(a.getDefinitionId())), "and carries the aura");
    }

    @Test
    void theBaseOvergrowthGrowsNothingFromAKill() {
        Grove g = grove(false);
        Unit victim = branchlings(g.fixture).get(0);

        victim.takeDamage(g.fixture.state(), new DamageEvent(g.killer, victim, 9999));

        assertTrue(victim.isDead());
        assertTrue(branchiggas(g.fixture).isEmpty());
    }

    /**
     * A Branchling that simply withered was removed with RemovalReason.DESPAWN, which publishes
     * no DeathEvent at all - so the "only when killed" rule needs no check of its own.
     */
    @Test
    void aWitheredBranchlingLeavesNothingBehind() {
        Grove g = grove(true);
        assertEquals(6, branchlings(g.fixture).size());

        OvergrowthEffect grove = g.branch.getActiveEffect(OvergrowthEffect.class).orElseThrow();
        grove.setRemainingTurns(0);
        g.branch.removeExpiredEffects(g.fixture.state());

        assertTrue(branchlings(g.fixture).isEmpty(), "the grove withered");
        assertTrue(branchiggas(g.fixture).isEmpty(), "and grew nothing");
    }

    /** Sprout's Branchling is not one of Overgrowth's, so killing it grows nothing either. */
    @Test
    void sproutsOwnBranchlingNeverGrowsABranchigga() {
        UpgradeFixture f = UpgradeFixture.create(5, 3);
        Unit branch = f.heroWith("Branch", Team.PLAYER_ONE, new UnitStats(40, 20, 30, 1000),
            0, 0, true, "overgrowth", "sprout");
        Unit killer = f.basic("Killer", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 3, 0);

        on(branch, "sprout").onUse(f.state(), new TileTarget(f.map().getTile(new Position(2, 0))));
        Unit planted = branchlings(f).get(0);
        planted.takeDamage(f.state(), new DamageEvent(killer, planted, 9999));

        assertTrue(planted.isDead());
        assertTrue(branchiggas(f).isEmpty());
    }

    /** Its own clock, started when it sprouted - not the Overgrowth's. */
    @Test
    void aBranchiggaOutlivesTheOvergrowthThatSeededIt() {
        Grove g = grove(true);
        Unit victim = branchlings(g.fixture).get(0);
        victim.takeDamage(g.fixture.state(), new DamageEvent(g.killer, victim, 9999));
        Unit grown = branchiggas(g.fixture).get(0);
        BranchiggaLifespanEffect life = grown.getActiveEffect(BranchiggaLifespanEffect.class).orElseThrow();
        assertEquals(6, life.getRemainingTurns());

        OvergrowthEffect grove = g.branch.getActiveEffect(OvergrowthEffect.class).orElseThrow();
        grove.setRemainingTurns(0);
        g.branch.removeExpiredEffects(g.fixture.state());

        assertFalse(branchiggas(g.fixture).isEmpty(), "the grove withering does not touch it");

        for (int i = 0; i < 6; i++) {
            life.onTurnEnd(g.fixture.state(), new TurnEndEvent(Team.PLAYER_ONE));
        }
        assertTrue(branchiggas(g.fixture).isEmpty(), "but its own clock does");
    }

    /** A Branchigga is not a Branchling, so killing one seeds nothing further. */
    @Test
    void aBranchiggasOwnDeathGrowsNothing() {
        Grove g = grove(true);
        Unit victim = branchlings(g.fixture).get(0);
        victim.takeDamage(g.fixture.state(), new DamageEvent(g.killer, victim, 9999));
        Unit grown = branchiggas(g.fixture).get(0);

        grown.takeDamage(g.fixture.state(), new DamageEvent(g.killer, grown, 9999));

        assertTrue(grown.isDead());
        assertTrue(branchiggas(g.fixture).isEmpty(), "no second generation");
    }
}
