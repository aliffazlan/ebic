package com.walnutt.effect;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.walnutt.TriggerHandler;
import com.walnutt.ability.Ability;
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
    private boolean dispelled;
    private boolean slowTickPending;

    public Effect(String name, int remainingTurns) {
        this(name, "", remainingTurns);
    }

    public Effect(String name, String description, int remainingTurns) {
        this.name = name;
        this.description = description;
        this.remainingTurns = remainingTurns;
    }

    /**
     * Every unexpired effect of a given kind currently in play, on any living unit. Some
     * effects describe something that isn't attached to their owner at all - a patch of
     * burning ground, a pending orb, a missile in flight - and the only way to find those
     * is to sweep the units carrying them. A dead unit keeps its effects (see
     * GameState.removeUnit), so the dead are skipped.
     */
    public static <T extends Effect> List<T> activeInstances(GameState state, Class<T> type) {
        List<T> found = new ArrayList<>();
        for (Unit unit : state.getAllActiveUnits()) {
            if (unit.isDead()) {
                continue;
            }
            for (Effect effect : unit.getEffects()) {
                if (!effect.isExpired() && type.isInstance(effect)) {
                    found.add(type.cast(effect));
                }
            }
        }
        return found;
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
     * True if this effect was cleared early by a dispel rather than running out of
     * duration. Both paths funnel through the same onExpire hook (dispelDebuffs just
     * force-expires), so an effect whose payload should only fire on a natural finish -
     * Poison Bloom's spread - needs this to tell them apart.
     */
    public boolean wasDispelled() {
        return dispelled;
    }

    /** Called by Unit.dispelDebuffs immediately before force-expiring this effect. */
    public void markDispelled() {
        this.dispelled = true;
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
     * How many casts this effect will pay for in place of a move point (Maxwell's Capacitor
     * Bank). Default 0, so no existing effect changes anything.
     *
     * Read by Unit.hasFreeCastCharge and consulted in Ability.getMoveCost. Move and Attack
     * override getMoveCost outright, so they are outside this by construction - the same
     * property that keeps Joker's Superior Mastery off them.
     */
    public int freeCastCharges() {
        return 0;
    }

    /**
     * True if this effect forbids its owner from using one SPECIFIC ability right now,
     * as opposed to a StatusFlag's blanket "no abilities at all". Default false, so no
     * existing effect changes behaviour.
     *
     * The only user today is Joker's Superior Mastery, which locks each of his abilities
     * to one cast per turn. Aggregated by Unit.isAbilityRestricted and checked in
     * Ability.canUse, so a locked ability also reports no legal targets - the client
     * greys it out and the bot skips it without either of them learning a new rule.
     */
    public boolean restrictsAbility(Ability ability) {
        return false;
    }

    /**
     * Minimum distance this effect forces its owner's basic attacks to keep (Artemis's
     * Steady Focus can't shoot anything closer than 3 tiles). 0 means no constraint.
     *
     * Deliberately not a {@link com.walnutt.status.Stat}: Unit.getEffective SUMS flat
     * modifiers, but overlapping minimums must resolve to the STRICTEST one, so
     * Unit.getMinAttackRange aggregates these by max instead.
     */
    public int getMinAttackRange() {
        return 0;
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
