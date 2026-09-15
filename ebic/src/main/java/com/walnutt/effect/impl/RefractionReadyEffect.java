package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;

/**
 * Cosmetic-only marker: present exactly while this unit has an unspent Refraction charge
 * this turn, so the frontend can show a "ready to refract" overlay. Carries no game logic
 * of its own - Refraction.java is the source of truth for the charge count and simply
 * attaches/expires this alongside it.
 */
public class RefractionReadyEffect extends Effect {
    public RefractionReadyEffect() {
        super("Refraction Ready", "Has an unused Refraction charge this turn.", Effect.PERMANENT);
        this.category = EffectCategory.NEUTRAL;
        this.dispellable = false;
    }

    /** Exposes the protected {@link Effect#expireNow} to Refraction, which lives in another package. */
    public void spend(GameState state) {
        expireNow(state);
    }
}
