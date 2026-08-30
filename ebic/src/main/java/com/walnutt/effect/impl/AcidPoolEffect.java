package com.walnutt.effect.impl;

import java.util.List;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Shawl's Acidic Brew: a pool of acid on the board. Lives on the CASTER while remembering a
 * board position, the same shape BurningGroundEffect uses and for the same reason - effects
 * only tick on units the turn loop runs, and a tile is not one.
 *
 * Two halves, deliberately hooked differently:
 *
 * - The amplification is {@link #onIncomingDamage}, checked against the victim's CURRENT
 *   tile at the moment it is hit, rather than a debuff applied on entry and stripped on
 *   exit. That is what makes stepping out mid-turn actually get you out of it, with no
 *   bookkeeping on every move, and it means the acid cannot be cleansed off a unit standing
 *   in it - there is nothing on the unit to cleanse.
 * - The tick is {@link #onTurnEnd}, filtered by the OCCUPANTS' team rather than the event's
 *   caster team, so a unit is burned once per round on its own turn end - exactly the
 *   trigger BurningGroundEffect documents.
 *
 * A radius of 0 is the single splashed tile the base ability spills; the upgrade widens it.
 */
public class AcidPoolEffect extends Effect {
    private final Unit caster;
    private final Position centre;
    private final int radius;
    private final int tickDamage;
    private final int bonusDamage;

    public AcidPoolEffect(Unit caster, Position centre, int radius, int duration, int tickDamage,
                           int bonusDamage) {
        super("Acidic Brew",
            "A pool of acid. Enemies standing in it take " + bonusDamage
                + " extra damage from every source, and " + tickDamage + " on ending their turn in it.",
            duration);
        this.caster = caster;
        this.centre = centre;
        this.radius = Math.max(0, radius);
        this.tickDamage = tickDamage;
        this.bonusDamage = bonusDamage;
        this.category = EffectCategory.NEUTRAL;
    }

    public Position getCentre() {
        return centre;
    }

    public int getRadius() {
        return radius;
    }

    /** Every pool currently on the board - one definition shared by the rules and the overlay. */
    public static List<AcidPoolEffect> activePools(GameState state) {
        return Effect.activeInstances(state, AcidPoolEffect.class);
    }

    /** True when {@code position} is inside this pool. */
    public boolean covers(GameState state, Position position) {
        return position != null && state.getMap().getDistance(centre, position) <= radius;
    }

    /**
     * Enemies standing in the acid take more damage from everything. Not additive across
     * overlapping pools of the same caster in practice - a second brew replaces nothing, so
     * two pools over one tile really do both apply, which matches how Overgrowth's
     * Branchlings already stack.
     */
    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        Unit victim = event.getTarget();
        if (isExpired() || caster == null || caster.isDead() || victim == null || victim.isDead()) {
            return;
        }
        if (victim.getTeam() == caster.getTeam() || event.getDamage() <= 0) {
            return;
        }
        if (covers(state, victim.getPosition())) {
            event.modifyDamage(bonusDamage);
        }
    }

    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        if (isExpired() || caster == null || caster.isDead()) {
            return;
        }
        for (Tile tile : state.getMap().getTilesInRadius(centre, radius)) {
            for (Unit occupant : List.copyOf(tile.getOccupants())) {
                // Only the side whose turn just ended, so nobody is scalded twice a round.
                if (occupant.getTeam() != event.team() || occupant.isDead()) {
                    continue;
                }
                // Shawl's own side wades through it unharmed.
                if (occupant.getTeam() == caster.getTeam()) {
                    continue;
                }
                DamageEvent tick = new DamageEvent(caster, occupant, tickDamage);
                tick.setCauseLabel("Acidic Brew");
                occupant.takeDamage(state, tick);
            }
        }
    }
}
