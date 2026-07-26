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
        super("Static Link", Effect.PERMANENT);
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
            casterBuff = new DamageDealtModifierEffect("Static Link Charge", Effect.PERMANENT, 0);
            targetDebuff = new DamageDealtModifierEffect("Static Link Drain", Effect.PERMANENT, 0);
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
