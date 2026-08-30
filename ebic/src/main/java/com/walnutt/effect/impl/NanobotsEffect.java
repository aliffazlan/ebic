package com.walnutt.effect.impl;

import java.util.List;

import com.walnutt.effect.Effect;
import com.walnutt.event.StatusAppliedEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.unit.Unit;

/**
 * Maxwell's Nanobots - repeated cleanse and heal.
 *
 * dispelDebuffs already honours Effect.dispellable and only touches DEBUFFs, so a
 * deliberately-permanent curse (Lucifer's Doom marks itself undispellable) survives this
 * while ordinary debuffs are stripped. Nothing extra is needed here.
 */
public class NanobotsEffect extends Effect {
    private final int healPerPulse;
    /** Upgrade: the bots linger after the healing is done, to cleanse once more. */
    private final boolean lingers;
    /** True once the healing window is over and the bots are only waiting. */
    private boolean dormant;

    public NanobotsEffect(int duration, int healPerPulse) {
        this(duration, healPerPulse, false);
    }

    public NanobotsEffect(int duration, int healPerPulse, boolean lingers) {
        super("Nanobots",
            "Medical nanobots strip away debuffs and repair " + healPerPulse
                + " health at the start of each of this unit's turns."
                + (lingers ? " Afterwards they stay dormant in the host, stripping one more "
                    + "cleansable debuff before burning out." : ""),
            duration);
        this.healPerPulse = healPerPulse;
        this.lingers = lingers;
        this.category = EffectCategory.BUFF;
    }

    @Override
    public String getExtraInfo() {
        return dormant ? "Dormant - will strip the next cleansable debuff" : null;
    }

    /**
     * Upgraded, the healing running out puts the bots to sleep rather than ending them. They
     * take over the duration themselves, so the ordinary expiry never fires while they wait.
     */
    @Override
    public void tick() {
        if (dormant) {
            return;
        }
        super.tick();
        if (lingers && isExpired()) {
            dormant = true;
            setRemainingTurns(Effect.PERMANENT);
        }
    }

    /**
     * The one thing dormant bots are still watching for: a cleansable debuff arriving on the
     * host. StatusAppliedEvent names a StatusFlag rather than the effect that carried it, so
     * this looks the host over rather than trusting the event to hand the culprit across.
     */
    @Override
    public void onStatusApplied(GameState state, StatusAppliedEvent event) {
        if (event.unit() == getOwner()) {
            stripOne(state);
        }
    }

    /**
     * Strips one cleansable debuff and burns out doing so. A no-op unless the bots are dormant
     * and there is actually something to strip.
     *
     * Called from the turn hook as well as from onStatusApplied because not every debuff
     * carries a StatusFlag - a plain poison publishes no status event at all - and the bots
     * are meant to catch the next one either way, not only the noisy ones.
     */
    private void stripOne(GameState state) {
        Unit host = getOwner();
        if (!dormant || host == null || host.isDead()) {
            return;
        }
        for (Effect effect : List.copyOf(host.getEffects())) {
            if (effect == this || effect.isExpired() || !effect.isDispellable()
                || effect.getCategory() != EffectCategory.DEBUFF) {
                continue;
            }
            effect.markDispelled();
            effect.setRemainingTurns(0);
            dormant = false;
            setRemainingTurns(0);
            host.removeExpiredEffects(state);
            return;
        }
    }

    /**
     * One cleanse-and-heal. Called directly by the ability for the on-cast pulse, and by
     * this effect's own turn-start hook thereafter.
     *
     * Mutating the owner's effect list from inside an event dispatch is safe: EventBus
     * copies each unit's handler list before dispatching to it.
     */
    public void pulse(GameState state) {
        Unit target = getOwner();
        if (target == null || target.isDead()) {
            return;
        }
        target.dispelDebuffs(state);
        target.heal(state, healPerPulse);
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        if (getOwner() == null || isExpired() || event.team() != getOwner().getTeam()) {
            return;
        }
        if (dormant) {
            stripOne(state);
            return;
        }
        pulse(state);
    }
}
