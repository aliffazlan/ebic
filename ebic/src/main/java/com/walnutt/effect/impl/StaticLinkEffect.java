package com.walnutt.effect.impl;

import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.effect.Effect;
import com.walnutt.event.DeathEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Discharge's Static Link: lives until broken (not on a fixed duration - see
 * PERMANENT), stealing more damage each of Discharge's own turns via a pair of
 * linked DamageDealtModifierEffects (one buffing him, one debuffing the target),
 * and granting a free attack at turn end. Breaks if not adjacent at turn end, or if
 * either party dies, at which point the buff/debuff pair lingers on its own for a while
 * longer.
 *
 * The death case needs its own hook rather than falling out of the tick loop: a dead unit
 * keeps its position, stays in Player.getUnits() and keeps receiving turn hooks, so a dead
 * Discharge still read as adjacent to his target and went on draining it and firing free
 * attacks from beyond the grave.
 */
public class StaticLinkEffect extends Effect {
    private final Unit caster;
    private final Unit target;
    private final int damageStealPerTurn;
    private final int lingerDuration;
    /** How far the link stretches before it snaps. 1 is the base form's adjacency. */
    private final int linkRange;
    private DamageDealtModifierEffect casterBuff;
    private DamageDealtModifierEffect targetDebuff;

    public StaticLinkEffect(Unit caster, Unit target, int damageStealPerTurn, int lingerDuration) {
        this(caster, target, damageStealPerTurn, lingerDuration, 1);
    }

    public StaticLinkEffect(Unit caster, Unit target, int damageStealPerTurn, int lingerDuration,
                             int linkRange) {
        super("Static Link",
            "A persistent link to the target: steals a growing amount of damage from it and grants a "
                + "free attack against it at the end of each of Discharge's turns, until he ends a turn "
                + "more than " + linkRange + " tile(s) from it (the drain/buff then lingers "
                + lingerDuration + " more turn(s) before fading).",
            Effect.PERMANENT);
        this.caster = caster;
        this.target = target;
        this.damageStealPerTurn = damageStealPerTurn;
        this.lingerDuration = lingerDuration;
        this.linkRange = Math.max(1, linkRange);
    }

    public Unit getTarget() {
        return target;
    }

    @Override
    public boolean isExpired() {
        return super.isExpired() || target.isDead() || (getOwner() != null && getOwner().isDead());
    }

    /**
     * Either party dying ends the link. Routing this through breakLink (rather than just
     * letting isExpired go true) is what gives the buff/debuff pair their finite linger
     * duration - they are created PERMANENT, so without it they would sit on both units
     * for the rest of the match.
     */
    @Override
    public void onDeath(GameState state, DeathEvent event) {
        if (event.unit() == getOwner() || event.unit() == target) {
            breakLink();
        }
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() == null || isExpired() || event.team() != getOwner().getTeam()) {
            return;
        }
        if (casterBuff == null) {
            casterBuff = new DamageDealtModifierEffect("Static Link Charge",
                "Increases Discharge's outgoing damage while Static Link drains the target; "
                    + "grows every one of his turns and lingers briefly after the link breaks.",
                Effect.PERMANENT, 0);
            targetDebuff = new DamageDealtModifierEffect("Static Link Drain",
                "Reduces the linked target's outgoing damage while Static Link is active; "
                    + "grows every one of Discharge's turns and lingers briefly after the link breaks.",
                Effect.PERMANENT, 0);
            caster.addEffect(casterBuff);
            target.addEffect(targetDebuff);
        }
        casterBuff.addStack(damageStealPerTurn);
        targetDebuff.addStack(-damageStealPerTurn);
    }

    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        if (getOwner() == null || isExpired() || event.team() != getOwner().getTeam()) {
            return;
        }
        if (state.getMap().getDistance(getOwner().getPosition(), target.getPosition()) > linkRange) {
            breakLink();
            return;
        }
        // The free attack goes straight through CombatEngine, which has no range check of its
        // own - so "it lands even beyond normal attack range" has always been true, and the
        // upgrade's real change is how far the link itself will stretch.
        CombatEngine.performAttack(state, new WeightedEncounter(getOwner(), target));
    }

    private void breakLink() {
        if (casterBuff != null) {
            casterBuff.setRemainingTurns(lingerDuration);
        }
        if (targetDebuff != null) {
            targetDebuff.setRemainingTurns(lingerDuration);
        }
        setRemainingTurns(0);
    }
}
