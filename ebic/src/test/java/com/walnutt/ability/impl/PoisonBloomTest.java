package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.BloomingPoisonEffect;
import com.walnutt.effect.impl.PoisonEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

class PoisonBloomTest {

    @Test
    void durationTicksUpDuringGrowthWindowThenDown() {
        Unit spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        PoisonBloom bloom = new PoisonBloom(new AbilityDefinition("Poison Bloom", "active", "desc", Map.of(
            "cooldown", 6.0, "cast_range", 3.0, "duration", 2.0, "initial_poison", 10.0,
            "duration_increase", 1.0, "infect_radius", 1.0)));
        spitter.addAbility(bloom);
        Unit target = new BasicUnit("Target", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(spitter);
        p2.addUnit(target);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(spitter, map.getTile(new Position(0, 0)));
        map.moveUnit(target, map.getTile(new Position(0, 2)));

        bloom.onUse(state, new UnitTarget(target));

        PoisonEffect poison = findPoison(target);
        assertNotNull(poison);
        assertEquals(10, poison.getRemainingTurns());

        target.endTurn(state); // growthTurnsRemaining=2 -> ticks UP
        assertEquals(11, findPoison(target).getRemainingTurns());

        target.endTurn(state); // growthTurnsRemaining=1 -> ticks UP again
        assertEquals(12, findPoison(target).getRemainingTurns());

        target.endTurn(state); // growth window used up -> ticks DOWN normally now
        assertEquals(11, findPoison(target).getRemainingTurns());
    }

    @Test
    void burstsIntoNearbyEnemiesOnDeathBasedOnStacksAtDeath() {
        Unit spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        PoisonBloom bloom = new PoisonBloom(new AbilityDefinition("Poison Bloom", "active", "desc", Map.of(
            "cooldown", 6.0, "cast_range", 3.0, "duration", 0.0, "initial_poison", 6.0,
            "duration_increase", 1.0, "infect_radius", 1.0)));
        spitter.addAbility(bloom);
        Unit victim = new BasicUnit("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 5));
        Unit nearbyEnemy = new BasicUnit("Nearby", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500));

        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(spitter);
        p2.addUnit(victim);
        p2.addUnit(nearbyEnemy);
        GameMap map = new GameMap(3);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(spitter, map.getTile(new Position(0, 0)));
        map.moveUnit(victim, map.getTile(new Position(0, 2)));
        map.moveUnit(nearbyEnemy, map.getTile(new Position(0, 1)));

        bloom.onUse(state, new UnitTarget(victim)); // no growth window (duration=0) -> stacks stay at 6

        victim.takeDamage(state, new DamageEvent(spitter, victim, 5)); // kills victim (hp 5)

        assertNotNull(findPoison(nearbyEnemy), "death burst should apply poison to the nearby enemy");
        assertEquals(6, findPoison(nearbyEnemy).getRemainingTurns());
    }

    private PoisonEffect findPoison(Unit unit) {
        for (Effect effect : unit.getEffects()) {
            if (effect instanceof PoisonEffect poisonEffect) {
                return poisonEffect;
            }
        }
        return null;
    }

    /** Helper: Spitter at (0,0), victim adjacent to the bloom host so it can catch the spread. */
    private static GameState scenario(Unit spitter, Unit host, Unit bystander) {
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(spitter);
        p2.addUnit(host);
        p2.addUnit(bystander);
        GameMap map = new GameMap(4);
        GameState state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(spitter, map.getTile(new Position(0, 0)));
        map.moveUnit(host, map.getTile(new Position(0, 2)));
        map.moveUnit(bystander, map.getTile(new Position(0, 3)));
        return state;
    }

    private static BloomingPoisonEffect bloomOn(Unit host) {
        return host.getActiveEffect(BloomingPoisonEffect.class).orElseThrow();
    }

    @Test
    void spreadsToNearbyEnemiesWhenTheBloomRunsItsFullCourse() {
        Unit spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        Unit host = new BasicUnit("Host", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100000));
        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100000));
        GameState state = scenario(spitter, host, bystander);

        host.addEffect(new BloomingPoisonEffect(spitter, 3, 0, 4, 1, 1));

        // Run it down to nothing the way the engine would, then let expiry fire.
        BloomingPoisonEffect bloom = bloomOn(host);
        while (!bloom.isExpired()) {
            bloom.tick();
        }
        host.removeExpiredEffects(state);

        assertNotNull(bystander.getActiveEffect(PoisonEffect.class).orElse(null),
            "a bloom that finished naturally should have spread to the adjacent enemy");
    }

    @Test
    void doesNotSpreadWhenCleansedOffEarly() {
        Unit spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        Unit host = new BasicUnit("Host", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100000));
        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100000));
        GameState state = scenario(spitter, host, bystander);

        host.addEffect(new BloomingPoisonEffect(spitter, 5, 0, 4, 1, 1));

        // dispelDebuffs is the cleanse path (Holy Shield); it force-expires through the
        // same onExpire hook a natural finish uses, so the two must stay distinguishable.
        host.dispelDebuffs(state);

        assertTrue(bystander.getActiveEffect(PoisonEffect.class).isEmpty(),
            "cleansing the bloom off must deny the spread entirely");
        assertTrue(host.getActiveEffect(BloomingPoisonEffect.class).isEmpty(), "the bloom itself is gone");
    }

    @Test
    void spreadsOnlyOnceEvenIfTheHostDiesAndTheEffectLaterExpires() {
        Unit spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        Unit host = new BasicUnit("Host", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 10));
        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100000));
        GameState state = scenario(spitter, host, bystander);

        host.addEffect(new BloomingPoisonEffect(spitter, 4, 0, 4, 1, 1));
        host.takeDamage(state, new DamageEvent(spitter, host, 999)); // dies under the bloom

        int afterDeath = bystander.getActiveEffect(PoisonEffect.class).orElseThrow().getRemainingTurns();

        // A dead unit stays in Player.getUnits() and keeps ticking, so expiry still comes.
        BloomingPoisonEffect bloom = bloomOn(host);
        while (!bloom.isExpired()) {
            bloom.tick();
        }
        host.removeExpiredEffects(state);

        assertEquals(afterDeath, bystander.getActiveEffect(PoisonEffect.class).orElseThrow().getRemainingTurns(),
            "the death burst and the expiry burst must not both land");
    }

    /**
     * The bloom used to inherit PoisonEffect's name verbatim, so a 6-cooldown signature
     * cast produced a sidebar chip identical to the passive's ordinary poison - it looked
     * like nothing had happened.
     */
    @Test
    void presentsItselfUnderItsOwnNameRatherThanPlainPoison() {
        Unit spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        Unit host = new BasicUnit("Host", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100000));
        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100000));
        GameState state = scenario(spitter, host, bystander);

        host.addEffect(new BloomingPoisonEffect(spitter, 4, 3, 5, 1, 1));
        PoisonEffect.applyOrExtend(bystander, spitter, 2, 5);

        assertEquals("Poison Bloom", host.getEffects().get(0).getName());
        assertEquals("Poison", bystander.getEffects().get(0).getName());
        assertNotNull(host.getEffects().get(0).getExtraInfo());
    }

    /**
     * A Spitter attack on a blooming target should add exactly the bloom's own
     * duration_increase. It used to add that PLUS Poison Sting's duration, because
     * applyOrExtend matched the bloom as a PoisonEffect subclass and extended it too.
     */
    @Test
    void aCasterHitExtendsTheBloomOnlyOnce() {
        Unit spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        Unit host = new BasicUnit("Host", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100000));
        Unit bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100000));
        GameState state = scenario(spitter, host, bystander);

        host.addEffect(new BloomingPoisonEffect(spitter, 4, 3, 5, 1, 1));
        int before = bloomOn(host).getRemainingTurns();

        // Both paths a real Spitter attack triggers: Poison Sting's applyOrExtend and the
        // bloom's own onPostAttack.
        PoisonEffect.applyOrExtend(host, spitter, 2, 5);
        bloomOn(host).onPostAttack(state, new PostAttackEvent(spitter, host, new DamageEvent(spitter, host, 10)));

        assertEquals(before + 1, bloomOn(host).getRemainingTurns(),
            "only the bloom's own duration_increase should apply");
        assertEquals(1, host.getEffects().size(), "and no second poison instance is stacked alongside it");
    }
}
