package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.effect.impl.PoisonBloomEffect;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.effect.impl.PoisonEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Spitter's unlocked abilities. */
class SpitterUpgradeTest {

    private record Setup(UpgradeFixture fixture, Unit spitter, Unit victim) {
    }

    private static Setup poisoned(boolean upgraded) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit spitter = f.heroWith("Spitter", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 1000),
            0, 0, upgraded, "poison_sting");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 5000), 0, 1);
        // STRENGTH beats INTELLIGENCE, so the sting lands and the poison goes on.
        CombatEngine.performAttack(f.state(), spitter, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);
        return new Setup(f, spitter, victim);
    }

    @Test
    void upgradedPoisonSoftensTheVictimByTwoPerTurnStillOnIt() {
        Setup s = poisoned(true);
        PoisonEffect poison = PoisonEffect.on(s.victim);
        assertNotNull(poison);
        assertEquals(2, poison.getRemainingTurns());

        int before = s.victim.getHealth();
        s.victim.takeDamage(s.fixture.state(), new DamageEvent(s.spitter, s.victim, 100));

        assertEquals(104, before - s.victim.getHealth(), "100, plus 2 for each of the 2 turns left");
    }

    @Test
    void theSofteningDecaysInStepWithThePoisonItBelongsTo() {
        Setup s = poisoned(true);
        PoisonEffect poison = PoisonEffect.on(s.victim);
        poison.setRemainingTurns(1);

        int before = s.victim.getHealth();
        s.victim.takeDamage(s.fixture.state(), new DamageEvent(s.spitter, s.victim, 100));

        assertEquals(102, before - s.victim.getHealth(), "one turn left is worth 2, not 4");
    }

    @Test
    void theBasePoisonOnlyTicks() {
        Setup s = poisoned(false);
        int before = s.victim.getHealth();

        s.victim.takeDamage(s.fixture.state(), new DamageEvent(s.spitter, s.victim, 100));

        assertEquals(100, before - s.victim.getHealth());
        assertEquals(0, PoisonEffect.on(s.victim).getVulnerabilityPerTurn());
    }

    /**
     * A host that DIES under the bloom passes the bloom itself on, not merely the poison - so
     * the burst chains rather than stopping with the first victim.
     */
    @Test
    void upgradedTheBloomItselfSpreadsWhenItsHostDies() {
        for (boolean upgraded : new boolean[] { false, true }) {
            UpgradeFixture f = UpgradeFixture.create();
            Unit spitter = f.heroWith("Spitter", Team.PLAYER_ONE, new UnitStats(40, 20, 20, 1000),
                0, 0, upgraded, "poison_bloom", "poison_sting");
            Unit host = f.basic("Host", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 100), 0, 2);
            Unit neighbour = f.basic("Neighbour", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 1000), 0, 3);

            Ability bloom = spitter.getAbilities().stream()
                .filter(a -> "poison_bloom".equals(a.getDefinitionId())).findFirst().orElseThrow();
            bloom.onUse(f.state(), new UnitTarget(host));
            host.takeDamage(f.state(), new DamageEvent(spitter, host, 500));

            assertTrue(host.isDead());
            assertNotNull(PoisonEffect.on(neighbour), "the poison always spreads");
            if (upgraded) {
                assertTrue(neighbour.getActiveEffect(PoisonBloomEffect.class).isPresent(),
                    "and so does the bloom");
            } else {
                assertFalse(neighbour.getActiveEffect(PoisonBloomEffect.class).isPresent());
            }
        }
    }
}
