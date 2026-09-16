package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.effect.impl.ShadowLifespanEffect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import java.util.Optional;
import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Mercurial's unlocked abilities. */
class MercurialUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    @Test
    void theBaseDispersionIsPassiveAndCannotBeCast() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit mercurial = f.heroWith("Mercurial", Team.PLAYER_ONE, new UnitStats(40, 40, 30, 2000),
            0, 0, false, "dispersion");

        assertTrue(on(mercurial, "dispersion").isPassive());
        assertFalse(on(mercurial, "dispersion").canUse(f.state(), new NoTarget()));
    }

    @Test
    void upgradedDispersionBecomesCastableWithoutLosingItsPassive() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit mercurial = f.heroWith("Mercurial", Team.PLAYER_ONE, new UnitStats(40, 40, 30, 2000),
            0, 0, true, "dispersion");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 2000), 0, 1);
        Unit attacker = f.basic("Attacker", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 2000), 0, 2);

        assertFalse(on(mercurial, "dispersion").isPassive(), "it is an active now");
        assertEquals(6, on(mercurial, "dispersion").getMaxCooldown());

        // Passive share first: 30% of 100 goes back to the adjacent enemy.
        mercurial.takeDamage(f.state(), new DamageEvent(attacker, mercurial, 100));
        assertEquals(30, 2000 - enemy.getHealth());

        on(mercurial, "dispersion").onUse(f.state(), new NoTarget());
        mercurial.takeDamage(f.state(), new DamageEvent(attacker, mercurial, 100));

        assertEquals(180, 2000 - enemy.getHealth(), "150% of the blow while it is raised");
    }

    @Test
    void whatMercurialHimselfTakesIsUnchangedEitherWay() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit mercurial = f.heroWith("Mercurial", Team.PLAYER_ONE, new UnitStats(40, 40, 30, 2000),
            0, 0, true, "dispersion");
        f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 2000), 0, 1);
        Unit attacker = f.basic("Attacker", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 2000), 0, 2);

        on(mercurial, "dispersion").onUse(f.state(), new NoTarget());
        mercurial.takeDamage(f.state(), new DamageEvent(attacker, mercurial, 100));

        assertEquals(100, 2000 - mercurial.getHealth(), "only the outgoing share grows");
    }

    private static Optional<Unit> shadowIn(UpgradeFixture f) {
        return f.state().getPlayer(Team.PLAYER_ONE).getUnits().stream()
            .filter(u -> "Mercurial Shadow".equals(u.getName()) && !u.isDead()).findFirst();
    }

    /** Mercurial at (0,0), an enemy at (0,3), and a landing tile beside them at (0,2). */
    private static UpgradeFixture manifested(boolean upgraded, int currentHealth) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit mercurial = f.heroWith("Mercurial", Team.PLAYER_ONE, new UnitStats(40, 40, 30, 810),
            0, 0, upgraded, "manifestation");
        mercurial.getHealthPool().setCurrent(currentHealth);
        f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 810), 0, 3);

        mercurial.getAbilities().stream().filter(a -> "manifestation".equals(a.getDefinitionId()))
            .findFirst().orElseThrow()
            .onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));
        return f;
    }

    @Test
    void upgradedManifestationLeavesAShadowWhereHeVanishedFrom() {
        UpgradeFixture f = manifested(true, 500);
        Unit shadow = shadowIn(f).orElseThrow();

        assertEquals(new Position(0, 0), shadow.getPosition(), "where he was, not where he went");
        assertEquals(500, shadow.getHealth(), "his CURRENT health, not a full pool");
        assertEquals(810, shadow.getMaxHealth());
        assertTrue(shadow.hasStatus(StatusFlag.ROOTED), "it cannot walk");
        assertTrue(shadow.getAbilities().stream()
            .anyMatch(a -> "recall".equals(a.getDefinitionId())), "and it carries Recall");
        assertFalse(shadow.getAbilities().stream().anyMatch(a -> a instanceof com.walnutt.ability.Move));
    }

    @Test
    void theBaseManifestationLeavesNothingBehind() {
        UpgradeFixture f = manifested(false, 500);

        assertTrue(shadowIn(f).isEmpty());
    }

    @Test
    void recallPullsHimBackToTheShadowAndDispersesIt() {
        UpgradeFixture f = manifested(true, 500);
        Unit shadow = shadowIn(f).orElseThrow();
        Unit mercurial = f.state().getPlayer(Team.PLAYER_ONE).getUnits().stream()
            .filter(u -> "Mercurial".equals(u.getName())).findFirst().orElseThrow();
        assertEquals(new Position(0, 2), mercurial.getPosition());

        shadow.getAbilities().stream().filter(a -> "recall".equals(a.getDefinitionId()))
            .findFirst().orElseThrow().onUse(f.state(), new com.walnutt.ability.target.NoTarget());

        assertEquals(new Position(0, 0), mercurial.getPosition(), "back where the shadow stood");
        assertTrue(shadowIn(f).isEmpty(), "and the shadow is gone");
    }

    /**
     * Recall teleports him ONTO the shadow rather than to a remembered position, which is what
     * makes a shifted shadow a shifted escape route.
     */
    @Test
    void movingTheShadowMovesWhereTheRecallLands() {
        UpgradeFixture f = manifested(true, 500);
        Unit shadow = shadowIn(f).orElseThrow();
        Unit mercurial = f.state().getPlayer(Team.PLAYER_ONE).getUnits().stream()
            .filter(u -> "Mercurial".equals(u.getName())).findFirst().orElseThrow();

        f.map().moveUnit(shadow, f.map().getTile(new Position(2, 0)));
        shadow.getAbilities().stream().filter(a -> "recall".equals(a.getDefinitionId()))
            .findFirst().orElseThrow().onUse(f.state(), new com.walnutt.ability.target.NoTarget());

        assertEquals(new Position(2, 0), mercurial.getPosition());
    }

    @Test
    void theShadowFadesOnItsOwnClock() {
        UpgradeFixture f = manifested(true, 500);
        Unit shadow = shadowIn(f).orElseThrow();
        ShadowLifespanEffect life = shadow.getActiveEffect(ShadowLifespanEffect.class).orElseThrow();
        assertEquals(2, life.getRemainingTurns());

        life.onTurnEnd(f.state(), new TurnEndEvent(Team.PLAYER_ONE));
        assertTrue(shadowIn(f).isPresent(), "still standing after one turn");
        life.onTurnEnd(f.state(), new TurnEndEvent(Team.PLAYER_ONE));

        assertTrue(shadowIn(f).isEmpty());
    }

    /** Killing the shadow is the counterplay: no shadow, no recall. */
    @Test
    void aShadowThatIsKilledCannotCallHimBack() {
        UpgradeFixture f = manifested(true, 500);
        Unit shadow = shadowIn(f).orElseThrow();
        Unit killer = f.basic("Killer", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 1, 0);

        shadow.takeDamage(f.state(), new DamageEvent(killer, shadow, 9999));

        assertTrue(shadow.isDead());
        assertTrue(shadowIn(f).isEmpty());
    }
}
