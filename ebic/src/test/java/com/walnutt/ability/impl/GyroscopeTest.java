package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Map;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.Team;
import com.walnutt.status.Stat;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class GyroscopeTest {

    private static Gyroscope gyroscope() {
        return new Gyroscope(new AbilityDefinition("Gyroscope", "passive", "desc", Map.of(
            "cast_range_boost", 1.0, "attack_range_boost", 1.0)));
    }

    private static PlasmaCannon plasmaCannon() {
        return new PlasmaCannon(new AbilityDefinition("Plasma Cannon", "active", "desc", Map.of(
            "cooldown", 4.0, "cast_range", 2.0, "damage", 60.0, "duration", 2.0)));
    }

    @Test
    void liftsBothAttackRangeAndTheRangeOfAnAbilityAlreadyOwned() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Attack attack = new Attack();
        PlasmaCannon cannon = plasmaCannon();
        maxwell.addAbility(attack);
        maxwell.addAbility(cannon);
        assertEquals(2, attack.getRange());
        assertEquals(2, cannon.getRange());

        maxwell.addAbility(gyroscope());

        assertEquals(3, (int) maxwell.getEffective(Stat.ATTACK_RANGE));
        assertEquals(3, attack.getRange(), "the basic attack reaches one tile further");
        assertEquals(3, cannon.getRange(), "so does an ability owned before the gyroscope");
    }

    /**
     * The ordering that matters, and the reason the bonus lives on the unit rather than on
     * each ability: Eureka hands out gadgets one at a time, so a gadget built after the
     * gyroscope has to inherit the bonus with no bookkeeping when it arrives.
     */
    @Test
    void liftsTheRangeOfAGadgetConstructedAfterIt() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        maxwell.addAbility(gyroscope());

        PlasmaCannon cannon = plasmaCannon();
        maxwell.addAbility(cannon);

        assertEquals(3, cannon.getRange());
    }

    /**
     * Movement is always one tile, so the cast-range bonus must not touch it. This was a
     * real bug: Move never overrode getRange(), so the client drew a two-tile band while
     * canUse still refused anything non-adjacent.
     */
    @Test
    void leavesMoveAtOneTileAndOnlyWidensAttackByItsOwnStat() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Move move = new Move();
        Attack attack = new Attack();
        maxwell.addAbility(move);
        maxwell.addAbility(attack);

        maxwell.addAbility(gyroscope());

        assertEquals(1, move.getRange(), "a Move is always to an adjacent tile, bonus or not");
        assertEquals(3, attack.getRange(), "the attack widens, but through ATTACK_RANGE only");
    }

    /** A self-cast has no distance to extend, and the client reads range 0 as "draw no band". */
    @Test
    void leavesPureSelfCastsAtZero() {
        Unit maxwell = new EliteUnit("Maxwell", Team.PLAYER_ONE, new UnitStats(18, 12, 84, 510, 2));
        Eureka eureka = new Eureka(new AbilityDefinition("Eureka", "active", "desc", Map.of(
            "cooldown", 1.0, "passive_inspiration", 2.0, "bonus_inspiration", 1.0,
            "cost", 6.0, "cost_increase", 10.0)));
        maxwell.addAbility(eureka);
        maxwell.addAbility(gyroscope());

        assertEquals(0, eureka.getRange());
    }
}
