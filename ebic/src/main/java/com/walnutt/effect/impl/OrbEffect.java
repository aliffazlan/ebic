package com.walnutt.effect.impl;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.combat.Attribute;
import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;

/**
 * Harbinger's Sanity's Eclipse: a delayed orb owned by the caster (not tied to any
 * tile-occupying unit), detonating at the START of Harbinger's next turn.
 *
 * The ordinary duration machinery gets that timing wrong, and did so in shipped play:
 * effects tick in Unit.endTurn, so a 1-turn delay expired the moment Harbinger ended the
 * turn he cast on - the orb went off before the opponent had a single chance to walk out
 * of it. So tick() is a no-op and the countdown runs from onTurnStart instead.
 * SproutEffect hit exactly this and is the shape copied here; InfernalBladeEffect uses
 * the same "opt out of tick(), drive it from a turn hook" trick for its own reasons.
 *
 * Explicitly bypasses INVULNERABLE, per the ability's own description ("affects units
 * under Oblivion Confinement") - note that is about DAMAGE, not targeting: the orb picks
 * no target, it detonates on a remembered position and sweeps whoever is standing there.
 */
public class OrbEffect extends Effect {
    private final Unit caster;
    private final Position targetPosition;
    private final int radius;
    private final double intDiffMultiplier;
    private boolean resolved;

    public OrbEffect(Unit caster, Position targetPosition, int delay, int radius, double intDiffMultiplier) {
        super("Sanity's Eclipse (pending)",
            "A delayed psionic orb that detonates at the start of Harbinger's next turn, dealing "
                + "damage to everyone in its blast radius equal to the difference between his "
                + "intelligence and each victim's. Bypasses invulnerability.",
            delay);
        this.caster = caster;
        this.targetPosition = targetPosition;
        this.radius = radius;
        this.intDiffMultiplier = intDiffMultiplier;
    }

    public Position getTargetPosition() {
        return targetPosition;
    }

    public int getRadius() {
        return radius;
    }

    /**
     * Every orb still in the air. Unlike most effects this one is not about the unit it
     * sits on - it remembers a patch of board - so the client needs it flattened onto
     * tiles the same way burning ground is. A detonated-but-not-yet-removed orb is
     * skipped: the blast has already happened.
     */
    public static List<OrbEffect> activeOrbs(GameState state) {
        List<OrbEffect> orbs = new ArrayList<>();
        for (OrbEffect orb : Effect.activeInstances(state, OrbEffect.class)) {
            if (!orb.resolved && !orb.caster.isDead()) {
                orbs.add(orb);
            }
        }
        return orbs;
    }

    @Override
    public String getExtraInfo() {
        int turns = getRemainingTurns();
        return "Detonates in " + turns + " turn" + (turns == 1 ? "" : "s");
    }

    /** Deliberately empty - see the class comment; onTurnStart drives the countdown. */
    @Override
    public void tick() {
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (resolved || owner == null || isExpired() || event.team() != owner.getTeam()) {
            return;
        }
        setRemainingTurns(getRemainingTurns() - 1);
        if (getRemainingTurns() <= 0) {
            detonate(state);
        }
    }

    /** Safety net for an orb removed some other way; the latch stops it going off twice. */
    @Override
    public void onExpire(GameState state) {
        detonate(state);
    }

    private void detonate(GameState state) {
        if (resolved) {
            return;
        }
        resolved = true;
        if (caster.isDead()) {
            return;
        }
        int casterIntelligence = caster.getAttributeValue(Attribute.INTELLIGENCE);
        for (Unit unit : state.getMap().getUnitsInRadius(targetPosition, radius)) {
            if (unit.getTeam() == caster.getTeam() || unit.isDead()) {
                continue;
            }
            int diff = casterIntelligence - unit.getAttributeValue(Attribute.INTELLIGENCE);
            int damage = (int) Math.round(Math.max(0, diff) * intDiffMultiplier);
            if (damage <= 0) {
                continue;
            }
            DamageEvent event = new DamageEvent(caster, unit, damage);
            event.setBypassInvulnerability(true);
            event.setCauseLabel("Sanity's Eclipse");
            unit.takeDamage(state, event);
        }
    }
}
