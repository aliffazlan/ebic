package com.walnutt.effect.impl;

import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.effect.Effect;
import com.walnutt.event.DeathEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/**
 * One side of a Valor Duel. Each participant holds their own instance pointing at
 * the other as "opponent"; the two are linked via linkPartner so either side ending
 * early (separation) or resolving (a death) can force-expire both symmetrically.
 */
public class DuelEffect extends Effect {
    private final Unit opponent;
    private final double duelBonus;
    private final double duelHealPercent;
    private final double winMultiplier;
    private DuelEffect partner;
    private boolean resolved;

    public DuelEffect(Unit opponent, int duration, double duelBonus, double duelHealPercent, double winMultiplier) {
        super("Duel", duration);
        this.opponent = opponent;
        this.duelBonus = duelBonus;
        this.duelHealPercent = duelHealPercent;
        this.winMultiplier = winMultiplier;
        this.flags.add(StatusFlag.DUELING);
    }

    public void linkPartner(DuelEffect partner) {
        this.partner = partner;
    }

    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        if (resolved || isExpired() || getOwner() == null || event.team() != getOwner().getTeam()) {
            return;
        }
        if (getOwner().isDead() || opponent.isDead()) {
            return;
        }
        if (!state.getMap().areAdjacent(getOwner().getPosition(), opponent.getPosition())) {
            endEarly();
            return;
        }
        // No human choosing an attribute for a forced attack - weight the roll by each unit's own stats.
        CombatEngine.performAttack(state, new WeightedEncounter(getOwner(), opponent));
    }

    @Override
    public void onDeath(GameState state, DeathEvent event) {
        if (resolved || event.unit() != opponent) {
            return;
        }
        resolved = true;
        if (partner != null) {
            partner.resolved = true;
        }

        double multiplier = opponent.getUnitType() == UnitType.BASIC ? 1.0 : winMultiplier;
        Unit winner = getOwner();
        for (Stat stat : new Stat[] {Stat.STRENGTH, Stat.AGILITY, Stat.INTELLIGENCE}) {
            winner.addPermanentModifier(StatModifier.flat(stat, duelBonus * multiplier, this));
        }
        winner.heal(state, (int) Math.round(winner.getMaxHealth() * duelHealPercent));
        setRemainingTurns(0);
    }

    private void endEarly() {
        setRemainingTurns(0);
        if (partner != null) {
            partner.setRemainingTurns(0);
        }
    }
}
