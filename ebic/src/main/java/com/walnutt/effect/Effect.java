package com.walnutt.effect;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.walnutt.TriggerHandler;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatModifier;
import com.walnutt.status.StatusFlag;
import com.walnutt.status.TickRate;
import com.walnutt.unit.Unit;

/**
 * Temporary status condition / temporary trigger listener. Unlike a PassiveAbility
 * (permanent part of a unit's kit), an Effect has a duration and expires.
 */
public abstract class Effect extends TriggerHandler {
    /** Effectively-permanent duration for stacking effects that never naturally expire (Eye of the Storm). */
    public static final int PERMANENT = Integer.MAX_VALUE / 2;

    private final String name;
    private final String description;
    protected Unit owner;
    private int remainingTurns;
    protected final Set<StatusFlag> flags = EnumSet.noneOf(StatusFlag.class);
    protected final List<StatModifier> modifiers = new ArrayList<>();
    protected EffectCategory category = EffectCategory.NEUTRAL;
    protected boolean dispellable = true;
    private boolean slowTickPending;

    public Effect(String name, int remainingTurns) {
        this(name, "", remainingTurns);
    }

    public Effect(String name, String description, int remainingTurns) {
        this.name = name;
        this.description = description;
        this.remainingTurns = remainingTurns;
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public Unit getOwner() {
        return owner;
    }

    public void setOwner(Unit owner) {
        this.owner = owner;
    }

    public int getRemainingTurns() {
        return remainingTurns;
    }

    public void setRemainingTurns(int remainingTurns) {
        this.remainingTurns = remainingTurns;
    }

    /** Stacking/refreshing effects (Poison Sting, Poison Bloom) use this instead of replacing the instance. */
    public void extendDuration(int amount) {
        this.remainingTurns += amount;
    }

    /**
     * Engine-owned bookkeeping, called once per owner's turn end - not final, since a
     * handful of effects (Poison Bloom) tick UP instead of down for a window.
     */
    public void tick() {
        this.remainingTurns = Math.max(0, this.remainingTurns - 1);
    }

    /**
     * Rate-aware tick, used when the owner is under a tick-rate-modulating aura
     * (Chronos's Dilation): FAST ticks twice, SLOW ticks every other call (an
     * alternating skip - the closest sane approximation of "half speed" for an
     * integer turn counter), NORMAL is just tick().
     */
    public final void tick(TickRate rate) {
        switch (rate) {
            case FAST -> {
                tick();
                tick();
            }
            case SLOW -> {
                slowTickPending = !slowTickPending;
                if (slowTickPending) {
                    tick();
                }
            }
            case NORMAL -> tick();
        }
    }

    public boolean isExpired() {
        return remainingTurns <= 0;
    }

    public EffectCategory getCategory() {
        return category;
    }

    public boolean isDispellable() {
        return dispellable;
    }

    /**
     * Called once, right before this effect is removed for actually running out of
     * duration (Unit.removeExpiredEffects). Default no-op; a handful of effects
     * (Oblivion Confinement's "escape" steal, Sanity's Eclipse's delayed orb) do
     * their real work here instead of on apply.
     */
    public void onExpire(GameState state) {
    }

    public Set<StatusFlag> getStatusFlags() {
        return flags;
    }

    /**
     * Optional player-facing summary of this effect's current *dynamic* state -
     * things that change turn to turn and so can't be baked into the static
     * {@link #getDescription()} text (Doom's next hit growing, Eye of the Storm's
     * accumulated vulnerability stack, a barrier's remaining HP, ...). Default
     * null (nothing extra to show); only effects with real per-instance runtime
     * state worth surfacing override this - a plain duration/flag effect like
     * Stunned has nothing dynamic to add beyond what's already shown.
     */
    public String getExtraInfo() {
        return null;
    }

    public List<StatModifier> getStatModifiers() {
        return modifiers;
    }
}
