package com.walnutt.ability;

import com.walnutt.ability.target.Target;
import com.walnutt.game.GameState;

public abstract class PassiveAbility extends Ability {

    public PassiveAbility(String name) {
        super(name, true);
    }

    public PassiveAbility(String name, String description) {
        super(name, description, true);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        return false;
    }

    @Override
    public void onUse(GameState state, Target target) {
        throw new UnsupportedOperationException("Passive abilities cannot be used directly.");
    }
}
