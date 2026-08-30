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

        // A drain costs exactly what it steals - 5 current and 5 maximum - so being caught by
        // both rots costs 10 of each, and by one costs 5.
        assertEquals(490, adjacentEnemy.getHealth(), "caught by both rots");
        assertEquals(490, adjacentEnemy.getMaxHealth());
        assertEquals(495, distantEnemy.getHealth(), "the outer rot alone");
        assertEquals(495, adjacentAlly.getHealth(), "the inner rot does not tell friend from foe");
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

    /**
     * Decay is a TRANSFER, not merely a drain. Dirge used to gain the maximum health without any
     * of the blood to fill it, so a long grind left him with a huge empty pool.
     */
    @Test
    void decayMovesCurrentHealthAcrossAsWellAsMaximum() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit dirge = f.heroWith("Dirge", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, false, "decay");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 1);
        dirge.getHealthPool().setCurrent(300);

        on(dirge, "decay").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(505, (int) dirge.getEffective(com.walnutt.status.Stat.MAX_HEALTH));
        assertEquals(305, dirge.getHealth(), "the 5 he took is 5 he now has");
        assertEquals(495, victim.getMaxHealth());
    }

    @Test
    void theWiderRotTransfersCurrentHealthTheSameWay() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit dirge = f.heroWith("Dirge", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, true, "decay");
        f.basic("Distant", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 2);
        dirge.getHealthPool().setCurrent(300);

        on(dirge, "decay").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(305, dirge.getHealth(), "the outer rot feeds him too");
    }

    /**
     * The rule in one place: a drain costs its victim exactly what it steals, from BOTH pools,
     * and hands exactly that much to Dirge. Both halves used to be wrong in opposite directions -
     * the victim was charged current health twice, and Dirge was given none of it.
     */
    @Test
    void aVictimLosesTheSameAmountOfCurrentAndMaximumHealth() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit dirge = f.heroWith("Dirge", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, false, "decay");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 1);
        dirge.getHealthPool().setCurrent(200);

        for (int turn = 1; turn <= 3; turn++) {
            on(dirge, "decay").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

            int stolen = 5 * turn;
            assertEquals(500 - stolen, victim.getHealth(), "current, after " + turn + " turn(s)");
            assertEquals(500 - stolen, victim.getMaxHealth(), "and the maximum, in step with it");
            assertEquals(200 + stolen, dirge.getHealth(), "everything it lost, he has");
            assertEquals(500 + stolen, dirge.getMaxHealth());
        }
    }

    /** A victim already below its ceiling is not topped up or pushed down twice by the drain. */
    @Test
    void aWoundedVictimLosesExactlyTheStealAndNoMore() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit dirge = f.heroWith("Dirge", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 500),
            0, 0, false, "decay");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 1);
        victim.getHealthPool().setCurrent(100);

        on(dirge, "decay").onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(95, victim.getHealth());
        assertEquals(495, victim.getMaxHealth(), "the ceiling still drops, well above where they are");
    }
}
