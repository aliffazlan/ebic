package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.effect.impl.BurningGroundEffect;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Ember's unlocked abilities. */
class EmberUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    private static MultiTarget twoTiles(UpgradeFixture f, Position first, Position second) {
        return new MultiTarget(new TileTarget(f.map().getTile(first)),
            new TileTarget(f.map().getTile(second)));
    }

    @Test
    void upgradedEruptionSetsTwoTilesAlightWithOneCast() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit ember = f.heroWith("Ember", Team.PLAYER_ONE, new UnitStats(20, 20, 90, 970),
            0, 0, true, "eruption");

        on(ember, "eruption").onUse(f.state(), twoTiles(f, new Position(0, 2), new Position(0, 3)));

        List<BurningGroundEffect> fires = BurningGroundEffect.activeGrounds(f.state());
        assertEquals(2, fires.size());
        assertTrue(BurningGroundEffect.isBurning(f.state(), new Position(0, 2)));
        assertTrue(BurningGroundEffect.isBurning(f.state(), new Position(0, 3)));
    }

    @Test
    void bothTilesIgniteWhoeverIsStandingOnThem() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit ember = f.heroWith("Ember", Team.PLAYER_ONE, new UnitStats(20, 20, 90, 970),
            0, 0, true, "eruption");
        Unit first = f.basic("First", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 2);
        Unit second = f.basic("Second", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 3);

        on(ember, "eruption").onUse(f.state(), twoTiles(f, new Position(0, 2), new Position(0, 3)));

        assertFalse(first.getEffects().isEmpty(), "caught on the first tile");
        assertFalse(second.getEffects().isEmpty(), "and on the second");
    }

    /**
     * Both halves on one tile would spend a cooldown doing a single tile's work, now that
     * re-lighting merely refreshes - so it is refused rather than silently wasted.
     */
    @Test
    void theTwoTilesMustBeDifferent() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit ember = f.heroWith("Ember", Team.PLAYER_ONE, new UnitStats(20, 20, 90, 970),
            0, 0, true, "eruption");

        assertFalse(on(ember, "eruption").canUse(f.state(), twoTiles(f, new Position(0, 2), new Position(0, 2))));
        assertTrue(on(ember, "eruption").canUse(f.state(), twoTiles(f, new Position(0, 2), new Position(0, 3))));
    }

    @Test
    void theBaseEruptionTakesOneTileAndRefusesAPair() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit ember = f.heroWith("Ember", Team.PLAYER_ONE, new UnitStats(20, 20, 90, 970),
            0, 0, false, "eruption");

        assertTrue(on(ember, "eruption").canUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2)))));
        assertFalse(on(ember, "eruption").canUse(f.state(), twoTiles(f, new Position(0, 2), new Position(0, 3))));
    }

    /**
     * The default enumeration only builds single-shape candidates, so without the override an
     * upgraded Eruption would report no legal targets at all and the client would highlight
     * nothing.
     */
    @Test
    void upgradedItEnumeratesTilePairsForTheClientToHighlight() {
        UpgradeFixture f = UpgradeFixture.create(5, 1);
        Unit ember = f.heroWith("Ember", Team.PLAYER_ONE, new UnitStats(20, 20, 90, 970),
            0, 0, true, "eruption");

        List<Target> legal = on(ember, "eruption").getLegalTargets(f.state());

        assertFalse(legal.isEmpty());
        assertTrue(legal.stream().allMatch(t -> t instanceof MultiTarget), "every candidate is a pair");
        assertTrue(legal.stream().allMatch(t -> {
            MultiTarget pair = (MultiTarget) t;
            return pair.primary() instanceof TileTarget && pair.secondary() instanceof TileTarget;
        }), "and both halves are tiles");
    }
}
