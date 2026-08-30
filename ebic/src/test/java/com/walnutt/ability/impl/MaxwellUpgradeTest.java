package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.walnutt.ability.target.TileTarget;
import com.walnutt.effect.impl.DroneLifespanEffect;
import com.walnutt.effect.impl.NanobotsEffect;
import com.walnutt.effect.impl.PoisonEffect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.map.Position;
import static org.junit.jupiter.api.Assertions.assertFalse;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.HomingMissileEffect;
import com.walnutt.status.StatusFlag;
import java.util.List;
import com.walnutt.combat.Attribute;
import org.junit.jupiter.api.Test;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.effect.impl.EnergyShieldEffect;
import com.walnutt.effect.impl.EnergyShieldPassiveEffect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.Team;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Maxwell's unlocked abilities. */
class MaxwellUpgradeTest {

    private static Ability on(Unit unit, String id) {
        return unit.getAbilities().stream()
            .filter(a -> id.equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    private static void cast(UpgradeFixture f, Unit maxwell, Ability ability) {
        on(maxwell, "eureka").onAbilityUsed(f.state(),
            new AbilityCastEvent(maxwell, ability, new NoTarget(), AbilityCastEvent.Phase.POST));
    }

    @Test
    void upgradedEurekaPaysInspirationBackForEveryAbilityCast() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "eureka");
        Eureka eureka = (Eureka) on(maxwell, "eureka");
        assertEquals(0, eureka.getInspiration());

        cast(f, maxwell, UpgradeFixture.ability("plasma_cannon"));
        cast(f, maxwell, UpgradeFixture.ability("nanobots"));

        assertEquals(2, eureka.getInspiration(), "once per ability, several times a turn");
    }

    @Test
    void walkingAndSwingingStillPayNothing() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "eureka");

        cast(f, maxwell, new Move());
        cast(f, maxwell, new Attack());

        assertEquals(0, ((Eureka) on(maxwell, "eureka")).getInspiration());
    }

    @Test
    void theBaseEurekaPaysNothingBackAtAll() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, false, "eureka");

        cast(f, maxwell, UpgradeFixture.ability("plasma_cannon"));

        assertEquals(0, ((Eureka) on(maxwell, "eureka")).getInspiration());
    }

    @Test
    void upgradedEnergyShieldWearsASecondBarrierThatMendsItself() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "energy_shield");
        EnergyShieldPassiveEffect plating =
            maxwell.getActiveEffect(EnergyShieldPassiveEffect.class).orElseThrow();
        assertEquals(0, plating.getRemainingBarrierHp(), "it grows rather than arriving full");

        for (int i = 0; i < 3; i++) {
            plating.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        }
        assertEquals(60, plating.getRemainingBarrierHp(), "20 a turn");

        for (int i = 0; i < 10; i++) {
            plating.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        }
        assertEquals(80, plating.getRemainingBarrierHp(), "and never past its ceiling");
    }

    /** Shattering the plating must not remove it - it is part of the unit, not a spent cooldown. */
    @Test
    void theSelfRepairingBarrierMendsBackFromNothing() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "energy_shield");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 1);
        EnergyShieldPassiveEffect plating =
            maxwell.getActiveEffect(EnergyShieldPassiveEffect.class).orElseThrow();
        plating.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        maxwell.takeDamage(f.state(), new DamageEvent(enemy, maxwell, 100));
        assertEquals(0, plating.getRemainingBarrierHp());
        assertEquals(800 - 80, maxwell.getHealth(), "20 was absorbed, the rest got through");

        plating.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertEquals(20, plating.getRemainingBarrierHp(), "it is still there, mending");
    }

    @Test
    void theCastShieldAndThePlatingAreSeparateAndBothAbsorb() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "energy_shield");
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 500), 0, 1);
        EnergyShieldPassiveEffect plating =
            maxwell.getActiveEffect(EnergyShieldPassiveEffect.class).orElseThrow();
        for (int i = 0; i < 4; i++) {
            plating.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        }
        on(maxwell, "energy_shield").onUse(f.state(), new UnitTarget(maxwell));

        assertTrue(maxwell.getActiveEffect(EnergyShieldEffect.class).isPresent());
        maxwell.takeDamage(f.state(), new DamageEvent(enemy, maxwell, 160));

        assertEquals(800, maxwell.getHealth(), "80 of plating and 80 of cast shield covered all of it");
    }

    @Test
    void theBaseEnergyShieldGrantsNoPlating() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, false, "energy_shield");

        assertTrue(maxwell.getActiveEffect(EnergyShieldPassiveEffect.class).isEmpty());
    }

    @Test
    void upgradedKillerDronesHaveNoPowerCellToRunDown() {
        for (boolean upgraded : new boolean[] { false, true }) {
            UpgradeFixture f = UpgradeFixture.create();
            Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
                0, 0, upgraded, "killer_drone");

            on(maxwell, "killer_drone").onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));
            Unit drone = f.state().getPlayer(Team.PLAYER_ONE).getUnits().stream()
                .filter(u -> "Drone".equals(u.getName())).findFirst().orElseThrow();

            assertEquals(!upgraded, drone.getActiveEffect(DroneLifespanEffect.class).isPresent(),
                "upgraded=" + upgraded);
        }
    }

    @Test
    void upgradedReloadSurvivesACastButNotASwing() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "reload", "plasma_cannon");
        Ability reload = on(maxwell, "reload");
        Ability cannon = on(maxwell, "plasma_cannon");
        cannon.increaseCooldown(4);

        reload.onAbilityUsed(f.state(),
            new AbilityCastEvent(maxwell, cannon, new NoTarget(), AbilityCastEvent.Phase.POST));
        reload.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(0, cannon.getCurrentCooldown(), "a cast is quiet enough to work through");

        cannon.increaseCooldown(4);
        reload.onAbilityUsed(f.state(),
            new AbilityCastEvent(maxwell, new Move(), new NoTarget(), AbilityCastEvent.Phase.POST));
        reload.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(4, cannon.getCurrentCooldown(), "but a step still breaks it");
    }

    @Test
    void theBaseReloadIsBrokenByAnyAction() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, false, "reload", "plasma_cannon");
        Ability reload = on(maxwell, "reload");
        Ability cannon = on(maxwell, "plasma_cannon");
        cannon.increaseCooldown(4);

        reload.onAbilityUsed(f.state(),
            new AbilityCastEvent(maxwell, cannon, new NoTarget(), AbilityCastEvent.Phase.POST));
        reload.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertEquals(4, cannon.getCurrentCooldown());
    }

    /** The bots go dormant when the healing runs out, and spend themselves on the next debuff. */
    @Test
    void upgradedNanobotsWaitAroundToStripOneMoreDebuff() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "nanobots");
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 800), 0, 1);
        Unit enemy = f.basic("Enemy", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 2);

        on(maxwell, "nanobots").onUse(f.state(), new UnitTarget(ally));
        NanobotsEffect bots = ally.getActiveEffect(NanobotsEffect.class).orElseThrow();

        // Run the healing window out.
        for (int i = 0; i < 5; i++) {
            bots.tick();
        }
        assertFalse(bots.isExpired(), "dormant rather than gone");

        PoisonEffect.applyOrExtend(ally, enemy, 4, 5);
        bots.onTurnStart(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertTrue(PoisonEffect.on(ally) == null, "the waiting bots stripped it");
        assertTrue(bots.isExpired(), "and burned out doing so");
    }

    @Test
    void theBaseNanobotsSimplyRunOut() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, false, "nanobots");
        Unit ally = f.basic("Ally", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 800), 0, 1);

        on(maxwell, "nanobots").onUse(f.state(), new UnitTarget(ally));
        NanobotsEffect bots = ally.getActiveEffect(NanobotsEffect.class).orElseThrow();
        for (int i = 0; i < 5; i++) {
            bots.tick();
        }

        assertTrue(bots.isExpired());
    }

    @Test
    void upgradedTranslocationReachesAnyUnitOnTheMap() {
        UpgradeFixture f = UpgradeFixture.create(6, 1);
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "translocation");
        Unit distant = f.basic("Distant", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 6);

        assertEquals(Ability.UNLIMITED_RANGE, on(maxwell, "translocation").getRange());
        assertTrue(on(maxwell, "translocation").canUse(f.state(), new MultiTarget(
            new UnitTarget(distant), new TileTarget(f.map().getTile(new Position(1, 5))))));
    }

    @Test
    void theBaseTranslocationCannotReachThatFar() {
        UpgradeFixture f = UpgradeFixture.create(6, 1);
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, false, "translocation");
        Unit distant = f.basic("Distant", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 6);

        assertFalse(on(maxwell, "translocation").canUse(f.state(), new MultiTarget(
            new UnitTarget(distant), new TileTarget(f.map().getTile(new Position(1, 5))))));
    }

    @Test
    void upgradedAimingAtAnOccupiedTileTradesTheTwoUnits() {
        UpgradeFixture f = UpgradeFixture.create(6, 1);
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "translocation");
        Unit subject = f.basic("Subject", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 1);
        Unit partner = f.basic("Partner", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 2);

        on(maxwell, "translocation").onUse(f.state(), new MultiTarget(
            new UnitTarget(subject), new TileTarget(f.map().getTile(new Position(0, 2)))));

        assertEquals(new Position(0, 2), subject.getPosition());
        assertEquals(new Position(0, 1), partner.getPosition(), "they traded places");
    }

    @Test
    void theBaseTranslocationRefusesAnOccupiedDestination() {
        UpgradeFixture f = UpgradeFixture.create(6, 1);
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, false, "translocation");
        Unit subject = f.basic("Subject", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 1);
        f.basic("Partner", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 2);

        assertFalse(on(maxwell, "translocation").canUse(f.state(), new MultiTarget(
            new UnitTarget(subject), new TileTarget(f.map().getTile(new Position(0, 2))))));
    }

    private static List<Effect> locksOn(Unit unit) {
        return unit.getEffects().stream()
            .filter(e -> e instanceof HomingMissileEffect && !e.isExpired()).toList();
    }

    @Test
    void upgradedHomingMissileLocksTwoWithTheStunnerArrivingFirst() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "homing_missile");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 3);

        on(maxwell, "homing_missile").onUse(f.state(), new UnitTarget(victim));

        List<Effect> locks = locksOn(victim);
        assertEquals(2, locks.size(), "a warhead and a stunner");
        assertEquals(1, locks.stream().filter(e -> e.getRemainingTurns() == 1).count(),
            "the light one arrives after a turn");
        assertEquals(1, locks.stream().filter(e -> e.getRemainingTurns() == 2).count(),
            "and the warhead a turn behind it");
    }

    @Test
    void theStunningMissilePlaysNoFavouritesAndStunsEveryoneItCatches() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "homing_missile");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 3);
        Unit neighbour = f.basic("Neighbour", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 4);

        on(maxwell, "homing_missile").onUse(f.state(), new UnitTarget(victim));
        Effect stunner = locksOn(victim).stream()
            .filter(e -> e.getRemainingTurns() == 1).findFirst().orElseThrow();
        stunner.setRemainingTurns(0);
        stunner.onExpire(f.state());

        assertEquals(10, 800 - victim.getHealth(), "no bonus for being the one aimed at");
        assertEquals(10, 800 - neighbour.getHealth(), "and the same for a neighbour");
        assertTrue(victim.hasStatus(StatusFlag.STUNNED));
        assertTrue(neighbour.hasStatus(StatusFlag.STUNNED));
    }

    @Test
    void theWarheadStillHitsHarderThanItsSplash() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "homing_missile");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 3);
        Unit neighbour = f.basic("Neighbour", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 4);

        on(maxwell, "homing_missile").onUse(f.state(), new UnitTarget(victim));
        Effect warhead = locksOn(victim).stream()
            .filter(e -> e.getRemainingTurns() == 2).findFirst().orElseThrow();
        warhead.setRemainingTurns(0);
        warhead.onExpire(f.state());

        assertEquals(50, 800 - victim.getHealth());
        assertEquals(20, 800 - neighbour.getHealth());
    }

    /** One cleanse takes whatever is still in the air, since both locks are ordinary debuffs. */
    @Test
    void cleansingTheLockShootsDownBothMissiles() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, true, "homing_missile");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 3);

        on(maxwell, "homing_missile").onUse(f.state(), new UnitTarget(victim));
        victim.dispelDebuffs(f.state());

        assertTrue(locksOn(victim).isEmpty());
        assertEquals(800, victim.getHealth(), "and neither one went off");
    }

    @Test
    void theBaseMissileLocksOnlyOnce() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, false, "homing_missile");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 0, 800), 0, 3);

        on(maxwell, "homing_missile").onUse(f.state(), new UnitTarget(victim));

        assertEquals(1, locksOn(victim).size());
        assertEquals(1, locksOn(victim).get(0).getRemainingTurns());
    }

    /**
     * Shrink Ray stacks onto an existing shrink, and the new cut must be taken at the CURRENT
     * percentage - see Effect.extendDuration. It used to re-use the percentage the FIRST cast was
     * made at, so unlocking it changed nothing for anything already shrunk.
     */
    @Test
    void unlockingShrinkRayDeepensAnExistingShrinkAtTheNewPercentage() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit maxwell = f.heroWith("Maxwell", Team.PLAYER_ONE, new UnitStats(30, 30, 60, 800),
            0, 0, false, "shrink_ray");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(100, 100, 100, 1000), 0, 2);

        on(maxwell, "shrink_ray").onUse(f.state(), new UnitTarget(victim));
        assertEquals(80, victim.getAttributeValue(Attribute.STRENGTH), "20% off 100");

        on(maxwell, "shrink_ray").upgrade();
        on(maxwell, "shrink_ray").decreaseCooldown(99);
        f.state().setRemainingMoves(5);
        on(maxwell, "shrink_ray").onUse(f.state(), new UnitTarget(victim));

        assertEquals(48, victim.getAttributeValue(Attribute.STRENGTH), "40% off 80, not another 20%");
    }
}
