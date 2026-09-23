package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UpgradeDefinition;
import com.walnutt.effect.impl.HighNoonMarkEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class DoubleDrawTest {

    private static final UpgradeDefinition UPGRADE = new UpgradeDefinition(
        "summary", null, null, Map.of("damage", 0.4), List.of(), List.of(), false);

    private static DoubleDraw newDoubleDraw() {
        AbilityDefinition definition =
            new AbilityDefinition("Double Draw", "passive", "desc", Map.of(), List.of(), List.of(), UPGRADE);
        DoubleDraw doubleDraw = new DoubleDraw(definition);
        // AbilityFactory.create is what normally stamps this on - canUpgrade() needs it set.
        doubleDraw.setDefinition(definition);
        return doubleDraw;
    }

    /** flint has STRENGTH dominant, and equal AGILITY/INTELLIGENCE - so whichever of the two
     *  unused attributes Double Draw randomly picks, the resulting damage is the same number. */
    private static Unit newFlint(int unusedAttributeValue) {
        Unit flint = new EliteUnit("Flint", Team.PLAYER_ONE,
            new UnitStats(999, unusedAttributeValue, unusedAttributeValue, 560));
        flint.addAbility(newDoubleDraw());
        return flint;
    }

    private static GameState scenario(Unit flint, Unit... enemies) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(flint);
        for (Unit enemy : enemies) {
            p2.addUnit(enemy);
        }
        GameMap map = new GameMap(20);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(flint, map.getTile(new Position(0, 0)));
        for (Unit enemy : enemies) {
            map.moveUnit(enemy, map.getTile(new Position(1, 0)));
        }
        return state;
    }

    /** Applies a (fixed, small - not derived from flint's real stats) primary hit and publishes
     *  the matching PostAttackEvent, exactly as CombatEngine.performAttack's own sequence does. */
    private static void primaryHit(GameState state, Unit flint, Unit target, int damage) {
        DamageEvent damageEvent = new DamageEvent(flint, target, damage);
        damageEvent.setAttackerAttribute(Attribute.STRENGTH);
        target.takeDamage(state, damageEvent);
        state.getEventBus().publish(state, new PostAttackEvent(flint, target, damageEvent, false));
    }

    @Test
    void successfulAttackDrawsASecondShotForOneOfTheTwoUnusedAttributes() {
        Unit flint = newFlint(15);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(flint, target);

        primaryHit(state, flint, target, 10);

        // Primary hit: 10. Second shot: 15 (either unused attribute - both equal).
        assertEquals(500 - 10 - 15, target.getHealth());
    }

    @Test
    void failedAttackDrawsNothingUnlessUpgraded() {
        Unit flint = newFlint(15);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(flint, target);

        primaryHit(state, flint, target, 0);

        assertEquals(500, target.getHealth(), "a miss draws nothing before the upgrade");
    }

    @Test
    void upgradedFailedAttackStillDrawsASecondShotAtReducedDamage() {
        Unit flint = newFlint(20);
        DoubleDraw doubleDraw = (DoubleDraw) flint.getAbilities().get(0);
        doubleDraw.upgrade();
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(flint, target);

        primaryHit(state, flint, target, 0);

        // 20 * 40% = 8.
        assertEquals(500 - 8, target.getHealth());
    }

    @Test
    void aSecondShotNeverDrawsAThirdShot() {
        Unit flint = newFlint(15);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 5000));
        GameState state = scenario(flint, target);

        primaryHit(state, flint, target, 10);

        // Exactly one second shot: 10 (primary) + 15 (second), never a third.
        assertEquals(5000 - 10 - 15, target.getHealth());
    }

    @Test
    void aSecondShotLandingOnAMarkedTargetConsumesTheMark() {
        Unit flint = newFlint(15);
        // Out of Flint's attack range, so Double Draw's candidate scan never considers it -
        // the second shot has only one legal target to land on.
        Unit outOfRange = new BasicUnit("Faraway", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        Unit marked = new BasicUnit("Marked", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        marked.addEffect(new HighNoonMarkEffect(flint, 3, 2.5));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(flint);
        p2.addUnit(outOfRange);
        p2.addUnit(marked);
        GameMap map = new GameMap(20);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        map.moveUnit(flint, map.getTile(new Position(0, 0)));
        map.moveUnit(marked, map.getTile(new Position(1, 0)));
        map.moveUnit(outOfRange, map.getTile(new Position(10, 0)));

        primaryHit(state, flint, outOfRange, 10);

        // The second shot's 15 damage, critted to 2.5x by the consumed mark: 15 * 2.5 = 37.5 -> 38.
        assertEquals(500 - 38, marked.getHealth());
        assertTrue(marked.getActiveEffect(HighNoonMarkEffect.class).isEmpty(), "the mark was consumed");
    }

    @Test
    void aChainedAttackOutsideAHighNoonBarrageDrawsNothing() {
        Unit flint = newFlint(15);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 30, 500));
        GameState state = scenario(flint, target);

        DamageEvent damageEvent = new DamageEvent(flint, target, 10);
        damageEvent.setAttackerAttribute(Attribute.STRENGTH);
        target.takeDamage(state, damageEvent);
        state.getEventBus().publish(state, new PostAttackEvent(flint, target, damageEvent, true));

        assertEquals(500 - 10, target.getHealth());
    }
}
