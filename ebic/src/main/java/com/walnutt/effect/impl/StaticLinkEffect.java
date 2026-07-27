package com.walnutt.effect.impl;

import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.effect.Effect;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Discharge's Static Link: lives until broken (not on a fixed duration - see
 * PERMANENT), stealing more damage each of Discharge's own turns via a pair of
 * linked DamageDealtModifierEffects (one buffing him, one debuffing the target),
 * and granting a free attack at turn end. Breaks if not adjacent at turn end,
 * at which point the buff/debuff pair lingers on its own for a while longer.
 */
public class StaticLinkEffect extends Effect {
    private final Unit caster;
    private final Unit target;
    private final int damageStealPerTurn;
    private final int lingerDuration;
    private DamageDealtModifierEffect casterBuff;
    private DamageDealtModifierEffect targetDebuff;

    public StaticLinkEffect(Unit caster, Unit target, int damageStealPerTurn, int lingerDuration) {
        super("Static Link",
            "A persistent link to the target: steals a growing amount of damage from it and grants a "
                + "free attack against it at the end of each of Discharge's turns, until he ends a turn "
                + "no longer adjacent to it (the drain/buff then lingers " + lingerDuration
                + " more turn(s) before fading).",
            Effect.PERMANENT);
        this.caster = caster;
        this.target = target;
        this.damageStealPerTurn = damageStealPerTurn;
        this.lingerDuration = lingerDuration;
    }

    public Unit getTarget() {
        return target;
    }

    @Override
    public boolean isExpired() {
        return super.isExpired() || target.isDead();
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
        if (!state.getMap().areAdjacent(getOwner().getPosition(), target.getPosition())) {
            breakLink();
            return;
        }
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
