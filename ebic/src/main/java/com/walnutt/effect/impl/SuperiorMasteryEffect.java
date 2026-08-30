package com.walnutt.effect.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.effect.Effect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;

/**
 * Joker's Superior Mastery, seen from the other side: the price of cutting a turn off
 * every other cooldown is that no ability may be cast twice in the same turn, however
 * quickly it comes back up.
 *
 * Lives as an Effect rather than as state on the ability for two reasons: Effect already
 * has the per-ability veto hook the rule needs ({@link #restrictsAbility}, aggregated by
 * Unit.isAbilityRestricted and read in Ability.canUse), and every Effect is rendered in
 * the sidebar, so the player can see what is spent rather than only meeting a greyed-out
 * button. PERMANENT/NEUTRAL/non-dispellable for the same reasons ResourceEffect is - it
 * is part of the kit, not a status anyone should be able to cleanse or dilate.
 *
 * Ability INSTANCES are recorded, not names or ids, which is what lets a copy Joker's
 * Mimic parked and later re-equipped carry its used-this-turn state correctly.
 */
public class SuperiorMasteryEffect extends Effect {
    private final Set<Ability> usedThisTurn = new LinkedHashSet<>();
    /** Upgrade: abilities each turn that cost no action. 0 until Superior Mastery is unlocked. */
    private int freeCastsPerTurn;
    private int freeCastsRemaining;

    public SuperiorMasteryEffect(String name) {
        super(name, "Each of this unit's abilities can only be cast once per turn.", Effect.PERMANENT);
        this.category = EffectCategory.NEUTRAL;
        this.dispellable = false;
    }

    /**
     * Move and basic Attack are exempt: they are not "abilities" for this rule, and each
     * is already limited to once per turn by the unit's own turn flags.
     */
    public static boolean isRealAbility(Ability ability) {
        return ability != null && !ability.isPassive() && !(ability instanceof Move) && !(ability instanceof Attack);
    }

    /**
     * Granted by upgraded Superior Mastery. Takes effect immediately as well as from the next
     * turn, since the unlock can land part way through one.
     */
    public void setFreeCasts(int perTurn) {
        this.freeCastsPerTurn = Math.max(0, perTurn);
        this.freeCastsRemaining = Math.max(this.freeCastsRemaining, this.freeCastsPerTurn);
    }

    /**
     * The same currency Maxwell's Capacitor Bank spends - Ability.getMoveCost already reads it
     * through Unit.hasFreeCastCharge, so the upgrade needs no economy code of its own.
     *
     * Joker can never hold both this and a Capacitor Bank (it is passive, and Mimic only copies
     * actives), so there is no question of one cast spending two different charges.
     */
    @Override
    public int freeCastCharges() {
        return freeCastsRemaining;
    }

    @Override
    public boolean restrictsAbility(Ability ability) {
        return usedThisTurn.contains(ability);
    }

    @Override
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {
        // POST, not PRE: the latch records what was actually cast. The cooldown cut that
        // is the other half of this passive fires at PRE instead - see SuperiorMastery.
        if (event.phase() != AbilityCastEvent.Phase.POST || event.user() != getOwner()) {
            return;
        }
        if (isRealAbility(event.ability())) {
            usedThisTurn.add(event.ability());
            if (freeCastsRemaining > 0) {
                freeCastsRemaining--;
            }
        }
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() != null && event.team() == getOwner().getTeam()) {
            usedThisTurn.clear();
            freeCastsRemaining = freeCastsPerTurn;
        }
    }

    @Override
    public String getExtraInfo() {
        if (usedThisTurn.isEmpty()) {
            return freeCastsRemaining > 0 ? "Free casts left: " + freeCastsRemaining : null;
        }
        List<String> names = new ArrayList<>();
        for (Ability ability : usedThisTurn) {
            names.add(ability.getName());
        }
        return "Spent this turn: " + String.join(", ", names);
    }
}
