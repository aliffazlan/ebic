package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.PoisonBloomEffect;
import com.walnutt.effect.impl.PoisonEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * Poison and Poison Bloom are two independent effects: Poison is the status that deals
 * damage, the Bloom is a separate thing that feeds it and bursts. These tests are written
 * against that separation - the Bloom used to BE a poison (it subclassed PoisonEffect),
 * which is why casting it produced a single chip and Poison Sting could not stack onto a
 * bloomed target at all.
 */
class PoisonBloomTest {

    private Unit spitter;
    private Unit host;
    private Unit bystander;
    private GameState state;
    private GameMap map;

    private void setUpBoard() {
        spitter = new BasicUnit("Spitter", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 100));
        host = new BasicUnit("Host", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1_000_000));
        bystander = new BasicUnit("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1_000_000));
        Player p1 = new Player("P1", Team.PLAYER_ONE);
        Player p2 = new Player("P2", Team.PLAYER_TWO);
        p1.addUnit(spitter);
        p2.addUnit(host);
        p2.addUnit(bystander);
        map = new GameMap(4);
        state = new GameState(map, List.of(p1, p2), new Random(1));
        state.setRemainingMoves(3);
        map.moveUnit(spitter, map.getTile(new Position(0, 0)));
        map.moveUnit(host, map.getTile(new Position(0, 2)));
        map.moveUnit(bystander, map.getTile(new Position(0, 3)));
    }

    private PoisonBloom newBloom() {
        return new PoisonBloom(new AbilityDefinition("Poison Bloom", "active", "desc", Map.of(
            "cooldown", 6.0, "cast_range", 3.0, "duration", 3.0, "initial_poison", 4.0,
            "duration_increase", 1.0, "infect_radius", 1.0)));
    }

    private static PoisonEffect poisonOn(Unit unit) {
        return PoisonEffect.on(unit);
    }

    private static PoisonBloomEffect bloomOn(Unit unit) {
        return unit.getActiveEffect(PoisonBloomEffect.class).orElse(null);
    }

    @Test
    void castingItAppliesBothAPoisonAndASeparateBloom() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);

        bloom.onUse(state, new UnitTarget(host));

        assertEquals(2, host.getEffects().size(), "poison and bloom are two distinct effects");
        assertNotNull(poisonOn(host), "the damage-dealing poison");
        assertNotNull(bloomOn(host), "and the bloom feeding it");
        assertEquals(4, poisonOn(host).getRemainingTurns(), "initial_poison stacks land on the poison");
        assertEquals(3, bloomOn(host).getRemainingTurns(), "the bloom lasts its own duration");
    }

    /** All poison damage comes from the Poison effect; the Bloom deals none of its own. */
    @Test
    void onlyThePoisonDealsDamage() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);
        bloom.onUse(state, new UnitTarget(host));

        state.getEventBus().publish(state, new TurnStartEvent(Team.PLAYER_TWO));

        // 4 stacks x the default 4 damage-per-remaining-turn = 16, counted once, not twice.
        assertEquals(4 * 4, 1_000_000 - host.getHealth(), "exactly one source of poison damage");
    }

    @Test
    void theBloomGrowsThePoisonInsteadOfLettingItFade() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);
        bloom.onUse(state, new UnitTarget(host));
        assertEquals(4, poisonOn(host).getRemainingTurns());

        host.endTurn(state);
        assertEquals(5, poisonOn(host).getRemainingTurns(), "net +1 a turn while the bloom holds");
        host.endTurn(state);
        assertEquals(6, poisonOn(host).getRemainingTurns());

        // Third tick expires the bloom; from then on the poison decays normally.
        host.endTurn(state);
        assertNull(bloomOn(host), "the bloom has run its 3 turns");
        int afterBurst = poisonOn(host).getRemainingTurns();
        host.endTurn(state);
        assertEquals(afterBurst - 1, poisonOn(host).getRemainingTurns(), "and now it fades");
    }

    /**
     * With the effects independent, a Spitter attack extends the poison through Poison
     * Sting AND feeds the bloom's own bonus - that stacking is the point of pairing them.
     */
    @Test
    void aCasterHitStacksBothThePassiveAndTheBloomBonus() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);
        PoisonSting sting = new PoisonSting(new AbilityDefinition("Poison Sting", "passive", "desc",
            Map.of("duration", 2.0, "dmg_per_duration", 5.0)));
        spitter.addAbility(sting);
        bloom.onUse(state, new UnitTarget(host));
        int before = poisonOn(host).getRemainingTurns();

        PostAttackEvent hit = new PostAttackEvent(spitter, host, new DamageEvent(spitter, host, 10));
        sting.onPostAttack(state, hit);
        bloomOn(host).onPostAttack(state, hit);

        assertEquals(before + 3, poisonOn(host).getRemainingTurns(), "+2 from the sting, +1 from the bloom");
        assertEquals(2, host.getEffects().size(), "and still just the two effects");
    }

    /** Poison Sting used to be a no-op against a bloomed target. */
    @Test
    void poisonStingWorksNormallyOnABloomedTarget() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);
        PoisonSting sting = new PoisonSting(new AbilityDefinition("Poison Sting", "passive", "desc",
            Map.of("duration", 2.0, "dmg_per_duration", 5.0)));
        spitter.addAbility(sting);
        bloom.onUse(state, new UnitTarget(host));
        int before = poisonOn(host).getRemainingTurns();

        sting.onPostAttack(state, new PostAttackEvent(spitter, host, new DamageEvent(spitter, host, 10)));

        assertEquals(before + 2, poisonOn(host).getRemainingTurns());
    }

    @Test
    void burstsIntoNearbyEnemiesWhenTheBloomEnds() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);
        bloom.onUse(state, new UnitTarget(host));
        assertNull(poisonOn(bystander), "not yet");

        for (int turn = 0; turn < 3; turn++) {
            host.endTurn(state);
        }

        assertNull(bloomOn(host), "the bloom has ended");
        assertNotNull(poisonOn(bystander), "and spread its poison to the adjacent enemy");
    }

    @Test
    void burstsWhenTheHostDiesUnderIt() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);
        bloom.onUse(state, new UnitTarget(host));

        host.takeDamage(state, new DamageEvent(spitter, host, 9_999_999));
        assertTrue(host.isDead());

        assertNotNull(poisonOn(bystander), "the death burst still spreads");
    }

    @Test
    void doesNotBurstWhenCleansedOffEarly() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);
        bloom.onUse(state, new UnitTarget(host));

        // dispelDebuffs is the cleanse path; it force-expires through the same onExpire
        // hook a natural finish uses, so the two must stay distinguishable.
        host.dispelDebuffs(state);

        assertNull(bloomOn(host), "the bloom is gone");
        assertNull(poisonOn(bystander), "cleansing must deny the burst entirely");
    }

    @Test
    void burstsOnlyOnceEvenIfTheHostDiesAndTheBloomLaterExpires() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);
        bloom.onUse(state, new UnitTarget(host));

        host.takeDamage(state, new DamageEvent(spitter, host, 9_999_999));
        int afterDeath = poisonOn(bystander).getRemainingTurns();

        // A dead unit stays in Player.getUnits() and keeps ticking, so expiry still comes.
        for (int turn = 0; turn < 4; turn++) {
            host.endTurn(state);
        }

        assertEquals(afterDeath, poisonOn(bystander).getRemainingTurns(),
            "the death burst and the expiry burst must not both land");
    }

    @Test
    void theTwoEffectsReadAsSeparateThingsInTheSidebar() {
        setUpBoard();
        PoisonBloom bloom = newBloom();
        spitter.addAbility(bloom);
        bloom.onUse(state, new UnitTarget(host));

        assertEquals("Poison", poisonOn(host).getName());
        assertEquals("Poison Bloom", bloomOn(host).getName());
        assertNotNull(poisonOn(host).getExtraInfo());
        assertNotNull(bloomOn(host).getExtraInfo());
        // The bloom is no longer a PoisonEffect subclass, so a poison lookup finds only the
        // real poison - which is what makes them two independent chips rather than one.
        assertEquals(1, host.getEffects().stream().filter(e -> e instanceof PoisonEffect).count(),
            "exactly one poison on the unit, and the bloom is not it");
    }
}
