package com.walnutt.ability.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.effect.StatusEffect;
import com.walnutt.effect.impl.FeastEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.map.Position;
import com.walnutt.status.StatusFlag;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;
import org.junit.jupiter.api.Test;

import com.walnutt.combat.Attribute;
import com.walnutt.combat.CombatEngine;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.Team;
import com.walnutt.unit.ActionKind;
import com.walnutt.unit.HealthPool;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/** Grivath's unlocked abilities. */
class GrivathUpgradeTest {

    private record Outcome(int agilityLost, int strengthLost, int healthLost) {
    }

    /** Grivath swings and LOSES the matchup - INTELLIGENCE into STRENGTH. */
    private static Outcome defendedSwing(boolean upgraded) {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(10, 10, 10, 1000),
            0, 0, upgraded, "cripple");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(50, 50, 50, 1000), 0, 1);

        CombatEngine.performAttack(f.state(), grivath, victim, Attribute.INTELLIGENCE, Attribute.STRENGTH);

        return new Outcome(
            50 - victim.getAttributeValue(Attribute.AGILITY),
            50 - victim.getAttributeValue(Attribute.STRENGTH),
            1000 - victim.getHealth());
    }

    @Test
    void theBaseOnlyShavesTheDefendingAttributeWhenAnAttackFails() {
        Outcome base = defendedSwing(false);

        assertEquals(0, base.agilityLost, "untouched attributes stay untouched");
        assertEquals(1, base.strengthLost, "one point off whatever blocked it");
        assertEquals(0, base.healthLost, "and no health at all");
    }

    @Test
    void upgradedAFailedAttackDrainsExactlyAsMuchAsALandedOne() {
        Outcome upgraded = defendedSwing(true);

        // A basic unit, so nothing is doubled: 1 from every attribute, 3 more from the one that
        // defended, and the health on top.
        assertEquals(1, upgraded.agilityLost);
        assertEquals(4, upgraded.strengthLost, "1 as an attribute, plus the 3 defended bonus");
        // Cripple deliberately damages BEFORE dropping the ceiling, so the drop does not
        // clamp current health a second time and charge for it twice.
        assertEquals(5, upgraded.healthLost);
    }

    private static Ability feastOn(Unit unit) {
        return unit.getAbilities().stream()
            .filter(a -> "feast".equals(a.getDefinitionId())).findFirst().orElseThrow();
    }

    @Test
    void theBaseFeastOnlyHealsAndRootsInsideItsFrenzy() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, false, "feast");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        grivath.getHealthPool().setCurrent(1000);

        assertFalse(grivath.getActiveEffect(FeastEffect.class).isPresent());
        CombatEngine.performAttack(f.state(), grivath, victim, Attribute.STRENGTH, Attribute.INTELLIGENCE);

        assertEquals(1000, grivath.getHealth(), "an ordinary attack heals nothing");
        assertFalse(victim.hasStatus(StatusFlag.ROOTED));
    }

    /** Upgraded, casting on an adjacent enemy is legal; on a distant one it is not - Feast has a real range now. */
    @Test
    void upgradedFeastAcceptsAnAdjacentEnemyButNotADistantOne() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit adjacent = f.basic("Adjacent", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        Unit distant = f.basic("Distant", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 3);

        assertTrue(feastOn(grivath).canUse(f.state(), new UnitTarget(adjacent)));
        assertFalse(feastOn(grivath).canUse(f.state(), new UnitTarget(distant)));
    }

    /** The upgrade's cast_range stat bumps latch-on range to 2, not just 1 - distance 2 is now legal too. */
    @Test
    void upgradedFeastCastRangeIsTwoTiles() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit twoAway = f.basic("TwoAway", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 2);
        Unit threeAway = f.basic("ThreeAway", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 3);

        assertTrue(feastOn(grivath).canUse(f.state(), new UnitTarget(twoAway)), "distance 2 is in range once upgraded");
        assertFalse(feastOn(grivath).canUse(f.state(), new UnitTarget(threeAway)), "distance 3 is still out of range");
    }

    /** Self-targeting is always legal, even with nothing nearby - it's the base ability with extra steps. */
    @Test
    void upgradedFeastCanAlwaysTargetItself() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");

        assertTrue(feastOn(grivath).canUse(f.state(), new UnitTarget(grivath)));
        assertFalse(feastOn(grivath).canUse(f.state(), new NoTarget()), "no longer a no-target cast once upgraded");
    }

    /** The base form still opens a window anywhere - prey can walk into it over the next few turns. */
    @Test
    void theBaseFeastIsStillCastableWithNothingNearby() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, false, "feast");
        f.basic("Distant", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 3);

        assertTrue(feastOn(grivath).canUse(f.state(), new NoTarget()));
        assertFalse(feastOn(grivath).canUse(f.state(), new UnitTarget(grivath)), "still no-target, not upgraded yet");
    }

    /** Targeting self, upgraded, behaves exactly like the base ability - no latch. */
    @Test
    void upgradedFeastCastOnSelfActsLikeTheBaseAbility() {
        UpgradeFixture f = UpgradeFixture.create();
        // STRENGTH-only vs the victim's INTELLIGENCE-only below: STRENGTH beats INTELLIGENCE,
        // so the weighted-random attribute pick is deterministic (always STRENGTH) and always lands.
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 2000),
            0, 0, true, "feast");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);

        feastOn(grivath).onUse(f.state(), new UnitTarget(grivath));

        assertFalse(grivath.hasStatus(StatusFlag.UNTARGETABLE));
        assertFalse(grivath.hasStatus(StatusFlag.INVULNERABLE));
        assertEquals(new Position(0, 0), grivath.getPosition(), "no latch, so no forced move");

        f.state().getEventBus().publish(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertTrue(victim.getHealth() < 2000, "still bites a random adjacent enemy each turn");
    }

    /** Targeting an enemy latches Grivath onto it: shares its tile, untargetable, invulnerable. */
    @Test
    void upgradedFeastLatchesOntoTheTargetedEnemy() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);

        feastOn(grivath).onUse(f.state(), new UnitTarget(victim));

        assertEquals(victim.getPosition(), grivath.getPosition(), "occupies the same tile");
        assertTrue(grivath.hasStatus(StatusFlag.UNTARGETABLE));
        assertTrue(grivath.hasStatus(StatusFlag.INVULNERABLE));
    }

    /** Latched, Grivath can only move - attacking and casting anything else is locked out. */
    @Test
    void latchedFeastDisarmsAndSilencesGrivathButLeavesMoveOpen() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);

        feastOn(grivath).onUse(f.state(), new UnitTarget(victim));

        assertTrue(grivath.isBlockedFrom(ActionKind.ATTACK), "disarmed - the automatic bites are the only attacks");
        assertTrue(grivath.isBlockedFrom(ActionKind.ABILITY), "silenced - can't cast anything else while latched");
        assertFalse(grivath.isBlockedFrom(ActionKind.MOVE), "move is the one action deliberately left open");
    }

    /** Casting the upgraded latch procs its free attacks immediately, not just from the next turn start. */
    @Test
    void latchedFeastProcsImmediatelyOnCast() {
        UpgradeFixture f = UpgradeFixture.create();
        // STRENGTH-only vs INTELLIGENCE-only below: STRENGTH beats INTELLIGENCE, so the immediate
        // proc is deterministic and always lands.
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 2000),
            0, 0, true, "feast");
        Unit victim = f.basic("Victim", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);

        feastOn(grivath).onUse(f.state(), new UnitTarget(victim));

        // Two free attacks at 50 each, before any TurnStartEvent has fired.
        assertEquals(2000 - 100, victim.getHealth(), "both latch attacks already landed on cast");
        assertTrue(victim.hasStatus(StatusFlag.ROOTED), "and rooted from the immediate proc too");
    }

    /** Grivath choosing to walk off the shared tile ends the latch early instead of dragging for the full duration. */
    @Test
    void movingAwayFromTheLatchCancelsItEarly() {
        UpgradeFixture f = UpgradeFixture.create();
        // STRENGTH-only vs INTELLIGENCE-only below: STRENGTH beats INTELLIGENCE, so every strike
        // in this test - the immediate proc and the post-cancel base-ability bite - is deterministic.
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 2000),
            0, 0, true, "feast");
        Unit latched = f.basic("Latched", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        grivath.addAbility(new Move());

        feastOn(grivath).onUse(f.state(), new UnitTarget(latched));

        Move move = grivath.getAbilities().stream().filter(Move.class::isInstance)
            .map(Move.class::cast).findFirst().orElseThrow();
        move.onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 0))));

        assertEquals(new Position(0, 0), grivath.getPosition(), "stayed exactly where his own move put him");
        assertFalse(grivath.hasStatus(StatusFlag.UNTARGETABLE), "the latch ended the moment he walked off");
        assertFalse(grivath.hasStatus(StatusFlag.INVULNERABLE));
        assertFalse(grivath.isBlockedFrom(ActionKind.ATTACK), "disarmed lifted along with the rest of the latch");
        assertFalse(grivath.isBlockedFrom(ActionKind.ABILITY));

        // "latched" is now merely adjacent rather than co-located, and the only enemy in range -
        // a deterministic candidate for the reverted base ability's random-adjacent bite.
        f.state().getEventBus().publish(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertTrue(latched.getHealth() < 2000, "reverted to biting whoever's adjacent, like the base ability");
    }

    /** While latched, the bite always lands on the latched unit - twice a turn - even with other prey adjacent. */
    @Test
    void latchedFeastAlwaysBitesTheLatchedUnitTwicePerTurn() {
        UpgradeFixture f = UpgradeFixture.create();
        // STRENGTH-only vs INTELLIGENCE-only targets below: STRENGTH beats INTELLIGENCE, so the
        // weighted-random attribute pick is deterministic (always STRENGTH) and deals full damage.
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 2000),
            0, 0, true, "feast");
        Unit latched = f.basic("Latched", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 5000), 0, 1);
        // A second candidate adjacent to Grivath's post-latch tile (0,1) that a base-form
        // bite could pick instead - the latch must ignore it entirely.
        f.basic("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 5000), 1, 0);

        feastOn(grivath).onUse(f.state(), new UnitTarget(latched));
        f.state().getEventBus().publish(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        // Four strikes total - 2 proc immediately on cast, 2 more from the turn-start tick -
        // STRENGTH beats INTELLIGENCE so each lands the full 50, all on "latched".
        assertEquals(5000 - 200, latched.getHealth(), "both rounds of free attacks landed on the latched unit");
    }

    /** The latched unit's own move drags Grivath along with it. */
    @Test
    void latchedFeastFollowsTheTargetWhenItMoves() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit latched = f.basic("Latched", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        latched.addAbility(new Move());

        feastOn(grivath).onUse(f.state(), new UnitTarget(latched));

        Move move = latched.getAbilities().stream().filter(Move.class::isInstance)
            .map(Move.class::cast).findFirst().orElseThrow();
        move.onUse(f.state(), new TileTarget(f.map().getTile(new Position(0, 2))));

        assertEquals(new Position(0, 2), latched.getPosition());
        assertEquals(new Position(0, 2), grivath.getPosition(), "Grivath followed onto the new tile");
    }

    /** The latched unit dying pops Grivath off - flags drop, and the remaining duration bites randomly instead. */
    @Test
    void latchedFeastPopsOffWhenTheTargetDies() {
        UpgradeFixture f = UpgradeFixture.create();
        // STRENGTH-only vs INTELLIGENCE-only "other" below: STRENGTH beats INTELLIGENCE, so the
        // post-pop-off random-adjacent bite is deterministic and always lands.
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 2000),
            0, 0, true, "feast");
        // 600 health: survives the immediate on-cast proc (2 x 50 = 100), leaving exactly enough
        // for the manual 500-damage hit below to be the one that actually kills it.
        Unit latched = f.basic("Latched", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 600), 0, 1);
        // Adjacent to Grivath's post-latch tile (0,1), so it's a valid random-bite candidate
        // once he pops off and falls back to the base ability's behavior.
        Unit other = f.basic("Other", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 1, 0);

        feastOn(grivath).onUse(f.state(), new UnitTarget(latched));
        assertEquals(600 - 100, latched.getHealth(), "took the immediate on-cast proc but is still alive");
        latched.takeDamage(f.state(), new DamageEvent(grivath, latched, 500));

        assertTrue(latched.isDead());
        assertFalse(grivath.hasStatus(StatusFlag.UNTARGETABLE), "pops off on the latched unit's death");
        assertFalse(grivath.hasStatus(StatusFlag.INVULNERABLE));
        assertFalse(grivath.isBlockedFrom(ActionKind.ATTACK), "disarmed lifts too, along with the rest of the latch");
        assertFalse(grivath.isBlockedFrom(ActionKind.ABILITY), "and silenced");

        f.state().getEventBus().publish(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertTrue(other.getHealth() < 2000, "reverted to biting whoever's adjacent, like the base ability");
    }

    /** A latch that runs out its duration naturally steps Grivath off the still-shared tile. */
    @Test
    void latchedFeastNaturallyExpiringUnstacksGrivathWhenTheTileIsStillShared() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit latched = f.basic("Latched", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);

        feastOn(grivath).onUse(f.state(), new UnitTarget(latched));
        assertEquals(latched.getPosition(), grivath.getPosition(), "latched onto the same tile");

        FeastEffect feast = grivath.getActiveEffect(FeastEffect.class).orElseThrow();
        feast.setRemainingTurns(0);
        grivath.removeExpiredEffects(f.state());

        assertFalse(grivath.getPosition().equals(latched.getPosition()), "stepped off the still-occupied tile");
    }

    /**
     * GameMap.findNearestFreeTile always excludes the unit's OWN tile from its candidates, so a
     * naive "always relocate" on expiry would needlessly bump Grivath even when he's already
     * alone - this proves the occupancy check actually gates the move.
     */
    @Test
    void latchedFeastNaturallyExpiringDoesNotMoveGrivathWhenAlreadyAlone() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit latched = f.basic("Latched", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);

        feastOn(grivath).onUse(f.state(), new UnitTarget(latched));
        Position sharedTile = grivath.getPosition();

        // Simulates the teleport-tracking gap (e.g. Cloak and Dagger's own raw moveUnit call,
        // which never publishes PostMoveEvent) without needing to fix it: the latched unit ends
        // up elsewhere without Grivath's onMove ever firing.
        f.state().getMap().moveUnit(latched, f.map().getTile(new Position(0, 3)));
        assertEquals(sharedTile, grivath.getPosition(), "onMove never fired for this raw move");

        FeastEffect feast = grivath.getActiveEffect(FeastEffect.class).orElseThrow();
        feast.setRemainingTurns(0);
        grivath.removeExpiredEffects(f.state());

        assertEquals(sharedTile, grivath.getPosition(), "already alone, so there was nothing to step off of");
    }

    /** A latched target going untargetable mid-latch (imprisoned, cloaked, ...) pops Grivath off too. */
    @Test
    void latchedFeastPopsOffWhenTheTargetGoesInvulnerable() {
        UpgradeFixture f = UpgradeFixture.create();
        // STRENGTH-only vs INTELLIGENCE-only targets below: STRENGTH beats INTELLIGENCE, so the
        // post-pop-off random-adjacent bite is deterministic and always lands.
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 0, 0, 2000),
            0, 0, true, "feast");
        Unit latched = f.basic("Latched", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        // Adjacent to Grivath's post-latch tile (0,1) - the only valid random-bite candidate once
        // the latch drops, since a co-located unit (the now-invulnerable "latched") never counts
        // as adjacent in the first place.
        Unit bystander = f.basic("Bystander", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 1, 0);

        feastOn(grivath).onUse(f.state(), new UnitTarget(latched));
        latched.addEffect(new StatusEffect("Imprisoned", 5, StatusFlag.INVULNERABLE));

        f.state().getEventBus().publish(f.state(), new TurnStartEvent(Team.PLAYER_ONE));

        assertFalse(grivath.hasStatus(StatusFlag.UNTARGETABLE), "pops off once the latched unit is untargetable");
        assertFalse(grivath.hasStatus(StatusFlag.INVULNERABLE));
        assertFalse(grivath.isBlockedFrom(ActionKind.ATTACK), "disarmed lifts too, along with the rest of the latch");
        assertFalse(grivath.isBlockedFrom(ActionKind.ABILITY), "and silenced");
        // Took the immediate on-cast proc (2 x 50) before Imprisoned landed, but nothing more
        // once it did - the turn-start tick pops the latch and bites "bystander" instead.
        assertEquals(2000 - 100, latched.getHealth(), "no damage beyond the immediate proc from before it turned invulnerable");
        assertTrue(bystander.getHealth() < 2000, "bit whoever's adjacent instead, like the base ability");
    }

    /** Follows the latched unit through its own forced teleport (Cloak and Dagger), landing on an occupied tile. */
    @Test
    void latchedFeastFollowsThroughCloakAndDaggerOntoAnOccupiedTile() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit evayne = f.basic("Evayne", Team.PLAYER_TWO, new UnitStats(50, 0, 0, 500), 0, 1);
        Ability cloak = UpgradeFixture.ability("cloak_and_dagger");
        evayne.addAbility(cloak);
        // Already standing on the destination, so the forced stack there is real.
        Position destination = new Position(2, -1); // distance 2 from Evayne's (0,1) - within cast_range
        f.basic("Bystander", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 2000), destination.getQ(), destination.getR());

        feastOn(grivath).onUse(f.state(), new UnitTarget(evayne));
        assertEquals(evayne.getPosition(), grivath.getPosition(), "latched onto Evayne's tile");

        cloak.onUse(f.state(), new TileTarget(f.map().getTile(destination)));

        assertEquals(destination, evayne.getPosition());
        assertEquals(destination, grivath.getPosition(), "followed Evayne's forced teleport");

        f.state().getEventBus().publish(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertFalse(grivath.hasStatus(StatusFlag.UNTARGETABLE), "popped off once her own cloak made her invulnerable");
        assertFalse(grivath.hasStatus(StatusFlag.INVULNERABLE));
    }

    /** As above, but the destination is empty - Grivath still follows, and pops off in place afterward. */
    @Test
    void latchedFeastFollowsThroughCloakAndDaggerOntoAnEmptyTileAndPopsOffInPlace() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit evayne = f.basic("Evayne", Team.PLAYER_TWO, new UnitStats(50, 0, 0, 500), 0, 1);
        Ability cloak = UpgradeFixture.ability("cloak_and_dagger");
        evayne.addAbility(cloak);

        feastOn(grivath).onUse(f.state(), new UnitTarget(evayne));

        Position destination = new Position(1, 1); // empty, distance 1 from Evayne's (0,1)
        cloak.onUse(f.state(), new TileTarget(f.map().getTile(destination)));
        assertEquals(destination, grivath.getPosition(), "followed onto the empty destination too");

        f.state().getEventBus().publish(f.state(), new TurnStartEvent(Team.PLAYER_ONE));
        assertFalse(grivath.hasStatus(StatusFlag.UNTARGETABLE));
        assertEquals(destination, grivath.getPosition(), "popped off in place, not relocated elsewhere");
    }

    /** Follows the latched unit even when a THIRD party forces the move (Maxwell's Translocation). */
    @Test
    void latchedFeastFollowsWhenTheLatchedUnitIsTranslocatedByAThirdParty() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit latched = f.basic("Latched", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        Unit maxwell = f.basic("Maxwell", Team.PLAYER_ONE, new UnitStats(0, 0, 0, 2000), 0, 2);
        Ability translocation = UpgradeFixture.ability("translocation");
        maxwell.addAbility(translocation);

        feastOn(grivath).onUse(f.state(), new UnitTarget(latched));

        Position destination = new Position(0, 3); // distance 2 from latched's (0,1) - within enemy_range
        translocation.onUse(f.state(),
            new MultiTarget(new UnitTarget(latched), new TileTarget(f.map().getTile(destination))));

        assertEquals(destination, latched.getPosition());
        assertEquals(destination, grivath.getPosition(), "followed the enemy's forced Translocation");
    }

    /** Follows even a "pull the summoner onto me" relocation (Mercurial's shadow's Recall). */
    @Test
    void latchedFeastFollowsWhenTheLatchedUnitIsPulledBackByRecall() {
        UpgradeFixture f = UpgradeFixture.create();
        Unit grivath = f.heroWith("Grivath", Team.PLAYER_ONE, new UnitStats(50, 20, 20, 2000),
            0, 0, true, "feast");
        Unit mercurial = f.basic("Mercurial", Team.PLAYER_TWO, new UnitStats(0, 0, 10, 2000), 0, 1);
        Unit shadow = new SummonedUnit("Mercurial (Shadow)", Team.PLAYER_TWO,
            new UnitStats(0, 0, 0, 1), new HealthPool(1), mercurial, false);
        Ability recall = UpgradeFixture.ability("recall");
        shadow.addAbility(recall);
        f.place(shadow, Team.PLAYER_TWO, 0, 3);

        feastOn(grivath).onUse(f.state(), new UnitTarget(mercurial));

        recall.onUse(f.state(), new NoTarget());

        assertEquals(shadow.getPosition(), mercurial.getPosition(), "Mercurial recalled onto the shadow's tile");
        assertEquals(mercurial.getPosition(), grivath.getPosition(), "and Grivath followed the recall");
    }
}
