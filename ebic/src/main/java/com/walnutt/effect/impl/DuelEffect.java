package com.walnutt.effect.impl;

import com.walnutt.combat.CombatEngine;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.ability.Ability;
import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
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
    /**
     * Total damage multiplier the OWNER of this half takes from its opponent. A base-kit
     * effect (200% damage, i.e. double) regardless of upgrade state; only the enemy-facing
     * half (see Duel.onUse) rises further once upgraded, so the two halves are no longer
     * necessarily equal.
     */
    private double damageMultiplier;
    /** The ability to refresh on a win, when it is the upgraded form. Null otherwise. */
    private Ability refreshOnWin;
    private boolean resolved;

    public DuelEffect(Unit opponent, int duration, double duelBonus, double duelHealPercent, double winMultiplier) {
        super("Duel",
            "Locked in a forced duel: neither unit can act freely, and each is forced to attack the "
                + "other at the end of its turn. The winner permanently gains " + duelBonus + " to all "
                + "stats (x" + winMultiplier + " if the loser wasn't a Basic)"
                + (opponent.getUnitType() == UnitType.BASIC
                    ? ""
                    : ", and heals " + Math.round(duelHealPercent * 100) + "% of max health")
                + ". Separating the duelists early ends the duel with no reward.",
            duration);
        this.opponent = opponent;
        this.duelBonus = duelBonus;
        this.duelHealPercent = duelHealPercent;
        this.winMultiplier = winMultiplier;
        this.flags.add(StatusFlag.DUELING);
    }

    public void linkPartner(DuelEffect partner) {
        this.partner = partner;
    }

    public Unit getOpponent() {
        return opponent;
    }

    /**
     * Wiring for the total damage multiplier the OWNER of this half takes from the opponent.
     * Both halves get a non-zero rate from the moment Duel is cast (the base 200% mutual
     * amp); upgraded, Duel passes a higher rate to the enemy's half only, so the exchange
     * becomes asymmetric - see Duel.onUse for which half gets which value. Only Valor's own
     * Duel comes back up on a win, hence refreshOnWin being null on the enemy's half.
     */
    public void upgradeWith(double damageMultiplier, Ability refreshOnWin) {
        this.damageMultiplier = damageMultiplier;
        this.refreshOnWin = refreshOnWin;
    }

    /**
     * Each duellist takes damage from the OTHER at whatever total multiplier this half was
     * given - a third party wading in is unaffected, which is what keeps this a duel rather
     * than a general vulnerability.
     */
    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (isExpired() || getOwner() == null || damageMultiplier <= 0) {
            return;
        }
        if (event.getTarget() != getOwner() || event.getSource() != opponent || event.getDamage() <= 0) {
            return;
        }
        event.multiplyDamage(damageMultiplier);
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
            endEarly(state);
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

        boolean loserWasBasic = opponent.getUnitType() == UnitType.BASIC;
        double multiplier = loserWasBasic ? 1.0 : winMultiplier;
        Unit winner = getOwner();
        for (Stat stat : new Stat[] {Stat.STRENGTH, Stat.AGILITY, Stat.INTELLIGENCE}) {
            winner.addPermanentModifier(StatModifier.flat(stat, duelBonus * multiplier, this));
        }
        if (!loserWasBasic) {
            winner.heal(state, (int) Math.round(winner.getMaxHealth() * duelHealPercent));
        }
        // A duel won is a duel that can be started again at once - the reward for committing to
        // one, and the reason the upgraded form snowballs.
        if (refreshOnWin != null) {
            refreshOnWin.decreaseCooldown(refreshOnWin.getMaxCooldown());
        }
        // Remove now, on both halves, rather than leaving either visible to the frontend
        // until the winner's own next scheduled sweep - a full opponent turn away.
        expireNow(state);
        if (partner != null) {
            partner.expireNow(state);
        }
    }

    private void endEarly(GameState state) {
        expireNow(state);
        if (partner != null) {
            partner.expireNow(state);
        }
    }

    @Override
    public boolean references(Unit unit) {
        return super.references(unit) || (unit != null && unit == opponent);
    }
}
