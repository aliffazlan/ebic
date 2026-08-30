package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Dirge's unlocked abilities. */
class DirgeUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    @Test
    void upgradedSoulRipTearsStrengthAcrossBeforeWorkingOutTheDamage() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit dirge = f.heroWith("Dirge", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 500),
            0, 0, true, "soul_rip");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(10, 0, 0, 500), 0, 2);

        on(dirge, "soul_rip").onUse(f.state(), new UnitTarget(enemy));

        // 2 strength crosses first (50/10 becomes 52/8), so the damage is half of 44, not of 40.
        assertEquals(52, dirge.getAttributeValue(Attribute.STRENGTH));
        assertEquals(8, enemy.getAttributeValue(Attribute.STRENGTH));
        assertEquals(500 - 22, enemy.getHealth());
    }

    @Test
    void upgradedSoulRipGivesAnAllyStrengthAfterHealingThem() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit dirge = f.heroWith("Dirge", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 500),
            0, 0, true, "soul_rip");
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(10, 0, 0, 500), 0, 2);
        ally.getHealthPool().setCurrent(100);

        on(dirge, "soul_rip").onUse(f.state(), new UnitTarget(ally));

        // The heal is reckoned from the untouched gap (50 - 10), and the gift lands afterwards.
        assertEquals(120, ally.getHealth());
        assertEquals(12, ally.getAttributeValue(Attribute.STRENGTH));
        assertEquals(50, dirge.getAttributeValue(Attribute.STRENGTH), "a gift, not a transfer");
    }

    @Test
    void upgradedDecayAddsAWiderEnemiesOnlyRotThatStacksWithTheInnerOne() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit dirge = f.heroWith("Dirge", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, true, "decay");
        Unit adjacentEnemy = f.basic("Near", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 1);
        Unit distantEnemy = f.basic("Far", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 2);
        Unit adjacentAlly = f.basic("Friend", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 500), 1, 0);

        on(dirge, "decay").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        // Each drain lowers the maximum first, which clamps current health with it, and only
        // then deals its damage - so one rot costs 10, not 5.
        assertEquals(485, adjacentEnemy.getHealth(), "caught by both rots");
        assertEquals(490, distantEnemy.getHealth(), "the outer rot alone");
        assertEquals(490, adjacentAlly.getHealth(), "the inner rot does not tell friend from foe");
    }

    @Test
    void theBaseDecayReachesOnlyItsNeighbours() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit dirge = f.heroWith("Dirge", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, false, "decay");
        Unit distantEnemy = f.basic("Far", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 2);

        on(dirge, "decay").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(500, distantEnemy.getHealth());
        assertTrue(on(dirge, "decay").getStats().containsKey("radius"));
    }
}
