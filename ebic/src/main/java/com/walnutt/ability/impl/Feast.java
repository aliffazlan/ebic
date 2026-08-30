package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.FeastEffect;
import com.walnutt.game.GameState;

/**
 * Grivath - enters a feeding frenzy: no leap, no self-root, just a free automatic
 * attack each turn against an adjacent enemy, healing off the damage and rooting
 * whoever gets bitten.
 */
public class Feast extends Ability {
    private int duration;
    private int attacks;
    private double lifesteal;
    private int rootDuration;

    public Feast(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 7));
        this.duration = definition.getInt("duration", 3);
        this.attacks = definition.getInt("attacks", 1);
        this.lifesteal = definition.getDouble("lifesteal", 0.25);
        this.rootDuration = definition.getInt("root_duration", 1);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !(target instanceof NoTarget)) {
            return false;
        }
        // Upgraded, the cast is NOTHING but the free attack - the lifesteal and the root are
        // already permanent. So with nothing adjacent to bite there is genuinely nothing for it
        // to do, and casting it would burn a seven-turn cooldown on empty air. The base form is
        // still castable anywhere: it opens a window, and prey can walk into it later.
        return !isUpgraded() || hasAdjacentPrey(state);
    }

    private boolean hasAdjacentPrey(GameState state) {
        return owner != null && owner.getPosition() != null
            && !state.getMap().getAdjacentUnits(owner.getPosition(),
                u -> u.getTeam() != owner.getTeam() && !u.isDead()).isEmpty();
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
        this.attacks = statInt("attacks", attacks);
        this.lifesteal = stat("lifesteal", lifesteal);
        this.rootDuration = statInt("root_duration", rootDuration);
        // The frenzy stops being a window and becomes what he simply is. Granted once, here,
        // rather than on every cast - so the cast below has only the free attack left to do.
        if (owner != null && owner.getActiveEffect(FeastEffect.class).isEmpty()) {
            owner.addEffect(new FeastEffect(Effect.PERMANENT, 0, lifesteal, rootDuration));
        }
    }

    @Override
    public void onUse(GameState state, Target target) {
        if (isUpgraded()) {
            // Everything the frenzy used to grant is already permanent, so a cast is now purely
            // the free attacks - which is what makes it still worth a cooldown.
            owner.getActiveEffect(FeastEffect.class)
                .ifPresent(feast -> feast.strike(state, attacks));
        } else {
            owner.addEffect(new FeastEffect(duration, attacks, lifesteal, rootDuration));
        }
        state.spendMoves(getMoveCost(state));
        resetToMax();
    }
}
