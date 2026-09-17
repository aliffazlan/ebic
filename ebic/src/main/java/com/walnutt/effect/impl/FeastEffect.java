package com.walnutt.effect.impl;

import java.util.Optional;

import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.effect.Effect;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.DeathEvent;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Grivath's Feast: makes several automatic lifesteal attacks each of his own turns, against a
 * random adjacent enemy in the base form.
 *
 * Upgraded, casting it on an enemy instead latches this same effect onto that unit
 * ({@code latchedTarget} non-null): Grivath shares its tile, is untargetable, invulnerable and
 * HIDDEN - marking him as the tile's secondary occupant for rendering/priority purposes, the same
 * flag Evayne's Cloak and Dagger uses - automatically follows it (onMove) for as long as the
 * latch holds, and every strike targets it directly at the higher latch-mode attack count rather
 * than a random adjacent enemy. While latched he is also DISARMED and SILENCED - unable to attack
 * or cast anything else - but deliberately NOT rooted or stunned: MOVE is the one action left
 * open, and choosing to walk off the shared tile (onMove, the owner-is-the-mover branch) ends the
 * latch early on the spot.
 *
 * Grivath pops off - untargetable/invulnerable/disarmed/silenced all drop, strike() reverts to
 * the base form's random-adjacent behavior for whatever duration remains - the moment any of:
 * the latched unit dies (onDeath), it simply stops being a valid target (onTurnStart, e.g. it
 * gets imprisoned or cloaks itself away), or Grivath himself chooses to move away from it
 * (onMove). None of those three paths repositions him: the death case is already handled by the
 * tile freeing up on its own, the still-mid-duration untargetable case deliberately leaves him
 * where he is rather than risk stranding him on the nearest free tile away from everyone else,
 * and the move-away case needs no repositioning by definition - he's already exactly where his
 * own chosen move put him. Only a latch that instead runs out its own duration while still
 * valid (onExpire) steps him off the tile - and only if it's still actually shared - since at
 * that point the ability is over regardless of where he ends up.
 */
public class FeastEffect extends Effect {
    private final int attacksPerTurn;
    private final int latchAttacksPerTurn;
    private final double lifestealPercent;
    private final int rootDuration;
    private Unit latchedTarget;

    public FeastEffect(int duration, int attacksPerTurn, double lifestealPercent, int rootDuration) {
        this(duration, attacksPerTurn, 0, lifestealPercent, rootDuration, null);
    }

    public FeastEffect(int duration, int attacksPerTurn, int latchAttacksPerTurn, double lifestealPercent,
                        int rootDuration, Unit latchedTarget) {
        super("Feast", describeText(attacksPerTurn, latchAttacksPerTurn, lifestealPercent, rootDuration, latchedTarget),
            duration);
        this.attacksPerTurn = attacksPerTurn;
        this.latchAttacksPerTurn = latchAttacksPerTurn;
        this.lifestealPercent = lifestealPercent;
        this.rootDuration = rootDuration;
        this.latchedTarget = latchedTarget;
        if (latchedTarget != null) {
            this.flags.add(StatusFlag.UNTARGETABLE);
            this.flags.add(StatusFlag.INVULNERABLE);
            this.flags.add(StatusFlag.HIDDEN);
            this.flags.add(StatusFlag.DISARMED);
            this.flags.add(StatusFlag.SILENCED);
        }
        this.category = EffectCategory.BUFF;
    }

    private static String describeText(int attacksPerTurn, int latchAttacksPerTurn, double lifestealPercent,
                                        int rootDuration, Unit latchedTarget) {
        if (latchedTarget != null) {
            return "Latched onto " + latchedTarget.getName() + ": untargetable, invulnerable, and following "
                + "it wherever it goes. Disarmed and silenced for the duration - moving is the only action "
                + "left - and moving away from it ends the latch early. Makes " + latchAttacksPerTurn
                + " free attack(s) against it immediately and again each of Grivath's turns, healing for "
                + Math.round(lifestealPercent * 100) + "% of the damage dealt and rooting it for " + rootDuration
                + " turn(s) afterward. If it dies, or is no longer a valid target, Grivath pops off and this "
                + "acts like the base ability for whatever duration remains.";
        }
        return "Automatically makes " + attacksPerTurn + " free attack(s) against a random adjacent enemy "
            + "each of Grivath's turns, healing for " + Math.round(lifestealPercent * 100)
            + "% of the damage dealt and rooting each victim for " + rootDuration + " turn(s) "
            + "afterward. Grivath moves and acts freely throughout.";
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (owner == null || isExpired() || event.team() != owner.getTeam()) {
            return;
        }
        // A latched target that's gone untargetable mid-latch (Oblivion Confinement, Evayne's
        // own Cloak and Dagger, ...) can never actually be hit again - pop off here, before the
        // attack count below is chosen, so this same turn already reverts to the base rate too.
        // No repositioning (unlike onExpire): the target is still alive and the duration isn't
        // over, so forcibly bumping Grivath to the nearest free tile could strand him away from
        // every other enemy instead of letting him keep biting whoever's actually adjacent.
        if (latchedTarget != null && !latchedTarget.isDead() && !latchedTarget.isTargetable()) {
            clearLatch();
        }
        strike(state, latchedTarget != null ? latchAttacksPerTurn : attacksPerTurn);
    }

    /** The frenzy's bite, on demand - also called directly from Feast.onUse for the immediate latch-cast proc. */
    public void strike(GameState state, int attacks) {
        Unit owner = getOwner();
        if (owner == null || owner.isDead()) {
            return;
        }
        for (int i = 0; i < attacks; i++) {
            Unit target;
            if (latchedTarget != null && !latchedTarget.isDead()) {
                target = latchedTarget;
            } else {
                Optional<Unit> victim = state.getMap().randomAdjacentUnit(owner.getPosition(),
                    u -> u.getTeam() != owner.getTeam() && !u.isDead(), state.getRandom());
                if (victim.isEmpty()) {
                    break;
                }
                target = victim.get();
            }
            DamageEvent damage = CombatEngine.performAttack(state, new WeightedEncounter(owner, target));
            if (damage != null && damage.getDamage() > 0) {
                int healed = (int) Math.round(damage.getDamage() * lifestealPercent);
                if (healed > 0) {
                    owner.heal(state, healed);
                }
            }
            if (!target.isDead()) {
                target.addEffect(new StatusEffect("Feast Root", rootDuration, EffectCategory.DEBUFF, StatusFlag.ROOTED));
            }
        }
    }

    /**
     * Two moves this reacts to: the latched unit's (drag Grivath along, keeping them stacked
     * on one tile) and Grivath's own (a chosen Move, since MOVE is the one action DISARMED and
     * SILENCED leave open - if it lands him anywhere but the latched unit's tile, the latch
     * ends right there). Grivath's own forced self-relocations - the initial latch-on move in
     * Feast.onUse, and the follow-move just below - always land him ON the latched unit's tile,
     * so they fall through the second branch as a no-op; the natural-expiry unstack move never
     * reaches here at all, since it runs after latchedTarget is already null.
     */
    @Override
    public void onMove(GameState state, PostMoveEvent event) {
        Unit owner = getOwner();
        if (owner == null || isExpired() || latchedTarget == null) {
            return;
        }
        if (event.unit() == latchedTarget) {
            Position from = owner.getPosition();
            state.getMap().moveUnit(owner, state.getMap().getTile(event.to()));
            // Itself a forced relocation - published for the same reason every other one in the
            // game now is: nothing else should be blind to this unit's real position.
            state.getEventBus().publish(state, new PostMoveEvent(owner, from, event.to()));
        } else if (event.unit() == owner && !event.to().equals(latchedTarget.getPosition())) {
            clearLatch();
        }
    }

    /**
     * The latched unit dying pops Grivath off - untargetable/invulnerable drop, strike() reverts
     * to random-adjacent. Deliberately no repositioning here: Unit.takeDamage publishes DeathEvent
     * BEFORE removing the corpse from the tile, so checking occupancy at this exact instant would
     * misread the about-to-vanish corpse as "still shared" - removeUnit frees the tile immediately
     * after, in the same call, so there is nothing left to step off of by the time anyone looks.
     */
    @Override
    public void onDeath(GameState state, DeathEvent event) {
        if (latchedTarget == null || event.unit() != latchedTarget) {
            return;
        }
        clearLatch();
    }

    /** A latch that runs out of duration naturally un-stacks Grivath, if the tile is still shared. */
    @Override
    public void onExpire(GameState state) {
        if (latchedTarget != null) {
            clearLatchAndUnstackIfStillShared(state);
        }
    }

    /** Drops every latch-only flag and forgets the target - shared by every pop-off. */
    private void clearLatch() {
        flags.remove(StatusFlag.UNTARGETABLE);
        flags.remove(StatusFlag.INVULNERABLE);
        flags.remove(StatusFlag.DISARMED);
        flags.remove(StatusFlag.SILENCED);
        latchedTarget = null;
    }

    /**
     * As {@link #clearLatch()}, but also steps off the shared tile if - and only if - it is
     * still actually shared with someone else. NOT used from onDeath: see its own comment for why
     * that path never needs to check.
     */
    private void clearLatchAndUnstackIfStillShared(GameState state) {
        clearLatch();
        Unit owner = getOwner();
        if (owner == null || owner.isDead() || owner.getPosition() == null) {
            return;
        }
        Tile current = state.getMap().getTile(owner.getPosition());
        boolean stillShared = current.getOccupants().stream().anyMatch(u -> u != owner);
        if (!stillShared) {
            return;
        }
        Position from = owner.getPosition();
        state.getMap().findNearestFreeTile(owner.getPosition(), state.getMap().getRadius() * 2)
            .ifPresent(tile -> {
                state.getMap().moveUnit(owner, tile);
                state.getEventBus().publish(state, new PostMoveEvent(owner, from, tile.getPosition()));
            });
    }
}
