package com.walnutt.effect.impl;

import com.walnutt.combat.Attribute;
import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.unit.Unit;

/**
 * Harbinger's Sanity's Eclipse: a delayed orb owned by the caster (not tied to any
 * tile-occupying unit). Ticks down like a normal effect; when it expires, it
 * explodes at the remembered position instead of doing nothing. Explicitly bypasses
 * INVULNERABLE, per the ability's own description ("affects units under Oblivion
 * Confinement").
 */
public class OrbEffect extends Effect {
    private final Unit caster;
    private final Position targetPosition;
    private final int radius;
    private final double intDiffMultiplier;

    public OrbEffect(Unit caster, Position targetPosition, int delay, int radius, double intDiffMultiplier) {
        super("Sanity's Eclipse (pending)",
            "A delayed psionic orb that detonates after " + delay + " turn(s), dealing damage to "
                + "everyone in its blast radius equal to the difference between Harbinger's "
                + "intelligence and each victim's. Bypasses invulnerability.",
            delay);
        this.caster = caster;
        this.targetPosition = targetPosition;
        this.radius = radius;
        this.intDiffMultiplier = intDiffMultiplier;
    }

    @Override
    public void onExpire(GameState state) {
        if (caster.isDead()) {
            return;
        }
        int casterIntelligence = caster.getAttributeValue(Attribute.INTELLIGENCE);
        for (Unit unit : state.getMap().getUnitsInRadius(targetPosition, radius)) {
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
