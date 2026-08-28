package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.SuperiorMasteryEffect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.Unit;

/**
 * Joker - reach, momentum, and the cost of both.
 *
 * Three parts: a permanent cast-range bonus (the Gyroscope pattern - a modifier on the
 * OWNER, so every ability he holds is lifted, copies included, with no bookkeeping when
 * one arrives); a cooldown cut applied to everything else each time he casts; and a
 * once-per-turn lock on each ability, which lives in SuperiorMasteryEffect.
 *
 * Without the lock the cut would loop: a 1-cooldown ability cast, then a second ability
 * cast to refund it, and the first is ready again inside the same turn.
 */
public class SuperiorMastery extends PassiveAbility {
    private final int castRangeBonus;
    private final int cooldownReduction;

    public SuperiorMastery(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.castRangeBonus = definition.getInt("cast_range_bonus", 2);
        this.cooldownReduction = definition.getInt("cooldown_reduction", 1);
    }

    @Override
    protected void onAttached(Unit newOwner) {
        if (castRangeBonus != 0) {
            newOwner.addPermanentModifier(StatModifier.flat(Stat.CAST_RANGE, castRangeBonus, this));
        }
        if (newOwner.getActiveEffect(SuperiorMasteryEffect.class).isEmpty()) {
            newOwner.addEffect(new SuperiorMasteryEffect(getName()));
        }
    }

    /**
     * PRE, deliberately: an ability puts itself on cooldown inside its own onUse, so at
     * this point the one being cast is still at 0 and cannot be refunded by its own cast.
     * The explicit skip below says so rather than leaving it to that coincidence.
     */
    @Override
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {
        if (event.phase() != AbilityCastEvent.Phase.PRE || event.user() != getOwner()) {
            return;
        }
        if (!SuperiorMasteryEffect.isRealAbility(event.ability())) {
            return; // walking and swinging are not casts
        }
        for (Ability ability : getOwner().getAbilities()) {
            if (ability != event.ability() && SuperiorMasteryEffect.isRealAbility(ability)) {
                ability.decreaseCooldown(cooldownReduction);
            }
            // Reaches abilities parked off-roster too - a copy Mimic is holding but not
            // wielding still sharpens. Wei's Energy Break deliberately does NOT: it reads
            // getAbilities() alone, so it can only burn what a unit is actually wielding.
            for (Ability held : ability.getHeldAbilities()) {
                if (SuperiorMasteryEffect.isRealAbility(held)) {
                    held.decreaseCooldown(cooldownReduction);
                }
            }
        }
    }
}
