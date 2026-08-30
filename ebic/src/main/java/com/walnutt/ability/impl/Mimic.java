package com.walnutt.ability.impl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.MimicEffect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Joker - watches what enemies cast near him and takes one for himself.
 *
 * Two halves. The passive half watches: every enemy cast inside Mimic's own range is
 * remembered against that caster for a few turns, most recent only. The active half
 * spends a cast on one of those enemies to copy what they last did, adding a real,
 * ordinary Ability to Joker's kit for a while.
 *
 * The copy is a fresh instance built by AbilityFactory, not a clone of the enemy's - that
 * sidesteps the whole per-ability mutable-state problem (see CLAUDE.md's deep-copy notes)
 * and means an ability granted this way needs no plumbing anywhere else, exactly as
 * Maxwell's gadgets proved.
 *
 * Instances are never thrown away once built. A copy Joker no longer wields is parked in
 * {@link #getHeldAbilities()}, where the engine keeps ticking its cooldown, so stealing
 * something back later returns it in the state he left it rather than fresh.
 */
public class Mimic extends Ability {

    /** What Joker saw one enemy cast, and how much longer he remembers it. */
    private static final class Observed {
        private final String definitionId;
        private int turnsLeft;

        private Observed(String definitionId, int turnsLeft) {
            this.definitionId = definitionId;
            this.turnsLeft = turnsLeft;
        }
    }

    private int duration;
    private final int stealWindow;

    /** Most recent copyable cast per enemy unit. Only the last one counts, by design. */
    private final Map<Unit, Observed> observed = new HashMap<>();
    /** Every copy ever built, by definition id - kept forever so cooldowns survive being parked. */
    private final Map<String, Ability> retained = new LinkedHashMap<>();
    private Ability equipped;

    public Mimic(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 2));
        setRange(definition.getInt("cast_range", 3));
        this.duration = definition.getInt("duration", 10);
        this.stealWindow = definition.getInt("steal_window", 3);
    }

    @Override
    public List<Ability> getHeldAbilities() {
        List<Ability> parked = new ArrayList<>();
        for (Ability ability : retained.values()) {
            if (ability != equipped) {
                parked.add(ability);
            }
        }
        return parked;
    }

    /** The copy Joker is wielding right now, or null. */
    public Ability getEquippedCopy() {
        return equipped;
    }

    /**
     * Watches enemy casts. PRE rather than POST so the range check reads where the caster
     * was when they cast, before anything the ability itself moves them with - Chronos
     * dashing away with Backtrack was still seen doing it.
     */
    @Override
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {
        if (event.phase() != AbilityCastEvent.Phase.PRE || owner == null) {
            return;
        }
        Unit caster = event.user();
        if (caster == null || caster.getTeam() == owner.getTeam()) {
            return;
        }
        String id = copyableId(state, event.ability());
        if (id == null || !isInRange(state, caster.getPosition())) {
            return;
        }
        observed.put(caster, new Observed(id, stealWindow));
    }

    /**
     * Null unless this ability can be rebuilt on someone else. Three gates, in order of
     * how they fail: no definition id means it was hand-built in Java rather than from a
     * JSON file (Move, Attack, a summon's internal kit); no registry entry or definition
     * means there is nothing to rebuild from; and the no_copy tag is the design-side
     * escape hatch for abilities whose machinery assumes it stays where it was built.
     */
    private String copyableId(GameState state, Ability ability) {
        if (ability == null || ability.isPassive()) {
            return null;
        }
        String id = ability.getDefinitionId();
        if (id == null || !AbilityFactory.isImplemented(id)) {
            return null;
        }
        AbilityDefinition definition = state.getAbilityDefinitions().get(id);
        if (definition == null || definition.hasTag(AbilityDefinition.NO_COPY)) {
            return null;
        }
        return id;
    }

    /**
     * Memories fade at the END of Joker's turn, so a cast seen during the enemy's turn is
     * fully available on each of the next {@code steal_window} turns of his rather than
     * losing one to the tick that happens before he can act.
     */
    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        if (owner == null || event.team() != owner.getTeam()) {
            return;
        }
        observed.entrySet().removeIf(entry -> {
            entry.getValue().turnsLeft--;
            return entry.getValue().turnsLeft <= 0 || entry.getKey().isDead();
        });
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit victim = unitTarget.getUnit();
        return !victim.isDead()
            && victim.getTeam() != owner.getTeam()
            && isInRange(state, victim.getPosition())
            && resolveStealable(state, victim) != null;
    }

    /** The definition this victim is currently offering, or null if there is nothing to take. */
    private AbilityDefinition resolveStealable(GameState state, Unit victim) {
        Observed record = observed.get(victim);
        if (record == null || record.turnsLeft <= 0) {
            return null;
        }
        AbilityDefinition definition = state.getAbilityDefinitions().get(record.definitionId);
        return definition == null || !AbilityFactory.isImplemented(record.definitionId) ? null : definition;
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit victim = ((UnitTarget) target).getUnit();
        Observed record = observed.get(victim);
        AbilityDefinition definition = resolveStealable(state, victim);
        if (definition == null) {
            return;
        }

        unequipCurrent();
        // Built once and kept: a second theft of the same ability hands back the same
        // instance, still carrying whatever cooldown it accumulated while parked.
        Ability copy = retained.computeIfAbsent(record.definitionId,
            id -> AbilityFactory.create(id, definition));
        owner.addAbility(copy);
        equipped = copy;
        // Upgraded, a copy never expires on its own - only being replaced gives one up - and it
        // arrives already unlocked, whether or not the unit it was taken from had unlocked it.
        if (isUpgraded()) {
            copy.upgrade();
        }
        owner.addEffect(new MimicEffect(getName(), this, copy,
            isUpgraded() ? Effect.PERMANENT : duration));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /** Called by MimicEffect when its duration runs out; ignored if a newer copy replaced it. */
    public void releaseIfCurrent(Ability copy) {
        if (equipped == copy) {
            unequipCurrent();
        }
    }

    private void unequipCurrent() {
        if (equipped == null) {
            return;
        }
        owner.removeAbility(equipped);
        // Expire the sidebar entry that was tracking it, so a replaced copy doesn't leave
        // a stale "Copied: ..." tooltip counting down beside the new one. Its own onExpire
        // still fires and still no-ops, since `equipped` no longer matches.
        for (Effect effect : owner.getEffects()) {
            if (!effect.isExpired() && effect instanceof MimicEffect mimicEffect
                && mimicEffect.getCopy() == equipped) {
                mimicEffect.setRemainingTurns(0);
            }
        }
        // The instance stays in `retained` - it becomes a held ability, still ticking.
        equipped = null;
    }

    @Override
    protected void onUpgraded() {
        this.duration = statInt("duration", duration);
    }
}
