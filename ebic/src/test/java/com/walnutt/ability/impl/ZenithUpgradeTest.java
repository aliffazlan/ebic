package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walnutt.status.StatusFlag;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Zenith's unlocked abilities. */
class ZenithUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    /**
     * The extra beams are aimed at random, so the only stable assertion with one enemy on the
     * board is the total: the direct beam plus one per pylon, all landing on the same unit.
     */
    @Test
    void upgradedOrbitalBeamAddsOneGlobalBeamPerPylon() {
        UpgradeFixture f = UpgradeFixture.create(5, 7);
        Unit zenith = f.heroWith("Zenith", Team.PLAYER_ONE, new UnitStats(30, 20, 60, 2000),
            0, 0, true, "orbital_beam", "pylon");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 5000), 0, 3);

        // Two pylons, both far from the enemy so their own short-range volley never fires and
        // every point of damage below is either the direct beam or a global one.
        on(zenith, "pylon").onUse(f.state(), new TileTarget(f.map().getTile(new Position(4, 0))));
        on(zenith, "pylon").decreaseCooldown(99);
        on(zenith, "pylon").onUse(f.state(), new TileTarget(f.map().getTile(new Position(3, 0))));

        on(zenith, "orbital_beam").onUse(f.state(), new UnitTarget(enemy));

        assertEquals(300, 5000 - enemy.getHealth(), "100 aimed, plus 100 from each of two pylons");
    }

    @Test
    void withNoPylonsStandingItIsJustTheOneBeam() {
        UpgradeFixture f = UpgradeFixture.create(5, 7);
        Unit zenith = f.heroWith("Zenith", Team.PLAYER_ONE, new UnitStats(30, 20, 60, 2000),
            0, 0, true, "orbital_beam");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 5000), 0, 3);

        on(zenith, "orbital_beam").onUse(f.state(), new UnitTarget(enemy));

        assertEquals(100, 5000 - enemy.getHealth());
    }

    @Test
    void theBaseBeamIsUnchangedByPylonsBeyondTheirOwnVolley() {
        UpgradeFixture f = UpgradeFixture.create(5, 7);
        Unit zenith = f.heroWith("Zenith", Team.PLAYER_ONE, new UnitStats(30, 20, 60, 2000),
            0, 0, false, "orbital_beam", "pylon");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 5000), 0, 3);

        on(zenith, "pylon").onUse(f.state(), new TileTarget(f.map().getTile(new Position(4, 0))));
        on(zenith, "orbital_beam").onUse(f.state(), new UnitTarget(enemy));

        assertEquals(60, 5000 - enemy.getHealth(), "the rebalanced base damage, and nothing else");
        assertTrue(on(zenith, "orbital_beam").getStats().get("damage") == 60.0);
    }

    /** Upgraded, the draw hurts the pylon rather than consuming it - but it detonates either way. */
    @Test
    void upgradedDislocationLeavesThePylonStandingAndStillDetonatesIt() {
        UpgradeFixture f = UpgradeFixture.create(5, 7);
        Unit zenith = f.heroWith("Zenith", Team.PLAYER_ONE, new UnitStats(30, 20, 60, 2000),
            0, 0, true, "pylon", "dislocation");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 4, 1);

        on(zenith, "pylon").onUse(f.state(), new TileTarget(f.map().getTile(new Position(4, 0))));
        Unit pylon = f.state().getAllActiveUnits().stream()
            .filter(u -> "Pylon".equals(u.getName())).findFirst().orElseThrow();

        on(zenith, "dislocation").onUse(f.state(), new UnitTarget(pylon));

        assertFalse(pylon.isDead(), "150 health, 50 drawn out of it");
        assertEquals(150, pylon.getHealth());
        assertEquals(950, enemy.getHealth(), "it detonated anyway");
        assertTrue(enemy.hasStatus(StatusFlag.STUNNED));
    }

    @Test
    void theBaseDislocationConsumesThePylonOutright() {
        UpgradeFixture f = UpgradeFixture.create(5, 7);
        Unit zenith = f.heroWith("Zenith", Team.PLAYER_ONE, new UnitStats(30, 20, 60, 2000),
            0, 0, false, "pylon", "dislocation");

        on(zenith, "pylon").onUse(f.state(), new TileTarget(f.map().getTile(new Position(4, 0))));
        Unit pylon = f.state().getAllActiveUnits().stream()
            .filter(u -> "Pylon".equals(u.getName())).findFirst().orElseThrow();

        on(zenith, "dislocation").onUse(f.state(), new UnitTarget(pylon));

        assertTrue(pylon.isDead());
    }
}
