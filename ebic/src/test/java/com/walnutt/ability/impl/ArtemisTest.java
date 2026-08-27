package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.Attack;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.status.Stat;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class ArtemisTest {

    private Unit artemis;
    private Unit target;
    private GameState state;
    private GameMap map;

    private void setUpBoard(int distance) {
        artemis = new EliteUnit("Artemis", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 440, 4));
        target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 1, 100_000));
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(artemis);
        p2.addUnit(target);
        map = new GameMap(8);
        state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(artemis, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(0, distance)));
    }

    private static Longshot newLongshot() {
        return new Longshot(new AbilityDefinition("Longshot", "passive", "desc", Map.of("dmg_increase", 0.2)));
    }

    private static SteadyFocus newSteadyFocus() {
        return new SteadyFocus(new AbilityDefinition("Steady Focus", "active", "desc",
            Map.of("cooldown", 5.0, "duration", 2.0, "bonus_range", 3.0, "min_range", 3.0)));
    }

    @Test
    void longshotScalesDamageWithDistance() {
        setUpBoard(4);
        artemis.addAbility(newLongshot());

        CombatEngine.performAttack(state, artemis, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        // 50 base, +20% per tile at 4 tiles = 50 * 1.8 = 90.
        assertEquals(90, 100_000 - target.getHealth());
    }

    @Test
    void longshotAddsNothingAtPointBlank() {
        setUpBoard(1);
        artemis.addAbility(newLongshot());

        CombatEngine.performAttack(state, artemis, target, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(60, 100_000 - target.getHealth(), "50 base * 1.2 at one tile");
    }

    @Test
    void steadyFocusRootsAndExtendsAttackRange() {
        setUpBoard(1);
        SteadyFocus focus = newSteadyFocus();
        artemis.addAbility(focus);

        assertEquals(4, (int) artemis.getEffective(Stat.ATTACK_RANGE));
        focus.onUse(state, new NoTarget());

        assertEquals(7, (int) artemis.getEffective(Stat.ATTACK_RANGE), "4 base + 3 bonus");
        assertTrue(artemis.hasStatus(StatusFlag.ROOTED), "the trade-off is being rooted");
        assertEquals(3, artemis.getMinAttackRange());
    }

    @Test
    void steadyFocusBlocksAttacksOnAnythingTooClose() {
        setUpBoard(2);
        Attack attack = new Attack();
        artemis.addAbility(attack);
        SteadyFocus focus = newSteadyFocus();
        artemis.addAbility(focus);

        assertTrue(attack.canUse(state, new UnitTarget(target)), "2 tiles is fine normally");

        focus.onUse(state, new NoTarget());
        state.setRemainingMoves(3);

        assertFalse(attack.canUse(state, new UnitTarget(target)),
            "while focused, anything closer than 3 tiles is off limits");
    }

    @Test
    void steadyFocusStillAllowsAttacksOutAtTheNewRange() {
        setUpBoard(6);
        Attack attack = new Attack();
        artemis.addAbility(attack);
        SteadyFocus focus = newSteadyFocus();
        artemis.addAbility(focus);

        assertFalse(attack.canUse(state, new UnitTarget(target)), "6 tiles is beyond the base range of 4");

        focus.onUse(state, new NoTarget());
        state.setRemainingMoves(3);

        assertTrue(attack.canUse(state, new UnitTarget(target)), "and within reach once focused");
    }

    /** Minimums resolve to the strictest value rather than summing the way stat modifiers do. */
    @Test
    void overlappingMinimumsTakeTheStrictestNotTheSum() {
        setUpBoard(1);
        artemis.addAbility(newSteadyFocus());
        artemis.addEffect(new com.walnutt.effect.impl.SteadyFocusEffect(2, 3, 3));
        artemis.addEffect(new com.walnutt.effect.impl.SteadyFocusEffect(2, 3, 2));

        assertEquals(3, artemis.getMinAttackRange(), "max(3, 2) = 3, not 5");
    }
}
