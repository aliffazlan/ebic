package com.walnutt.ability.impl;

import java.util.List;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.BurnEffect;
import com.walnutt.effect.impl.OverheatTrackerEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Ember - damage she deals to an enemy builds heat on it; enough heat makes it overheat
 * and strike its neighbours.
 *
 * Two deliberate limits keep this from running away, since the overheat's own damage would
 * otherwise feed the very gauge that produces more overheats: the overheating unit does NOT
 * strike itself (only other adjacent enemies), and each unit can overheat at most once per
 * turn. Together those make stack growth linear rather than exponential. The overheat's own
 * damage is also tagged so it never counts toward anyone's gauge - see the "Overheat" cause
 * label check below.
 *
 * Hooks PostDamageEvent rather than the pre-mitigation DamageEvent so the gauge counts
 * damage that actually landed, not what a barrier absorbed.
 */
public class Overheat extends PassiveAbility {
    private static final String CAUSE_LABEL = "Overheat";

    private int threshold;
    private int radius;
    private int damage;
    private int burnStacks;

    public Overheat(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.threshold = definition.getInt("threshold", 50);
        this.radius = definition.getInt("radius", 1);
        this.damage = definition.getInt("damage", 30);
        this.burnStacks = definition.getInt("burn_stacks", 1);
    }

    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        Unit ember = getOwner();
        if (ember == null || ember.isDead()) {
            return;
        }
        if (event.damageEvent().getSource() != ember) {
            return;
        }
        // The overheat proc's own damage is tagged with CAUSE_LABEL so it never re-feeds a
        // victim's gauge - otherwise a proc that hits several enemies at once could chain
        // into more procs the same turn.
        if (CAUSE_LABEL.equals(event.damageEvent().getCauseLabel())) {
            return;
        }
        Unit victim = event.damageEvent().getTarget();
        if (!isEnemy(ember, victim)) {
            return;
        }
        int dealt = event.damageEvent().getDamage();
        if (dealt <= 0) {
            return;
        }
        OverheatTrackerEffect tracker = trackerFor(victim, ember);
        tracker.add(dealt);
        if (tracker.consumeProc()) {
            overheat(state, ember, victim);
        }
    }

    /**
     * Heat banked past a threshold fires on a later turn without needing a fresh hit -
     * the once-per-turn latch resets at turn start, so leftover heat cashes in then.
     */
    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit ember = getOwner();
        if (ember == null || ember.isDead()) {
            return;
        }
        for (Unit unit : List.copyOf(state.getAllActiveUnits())) {
            if (!isEnemy(ember, unit) || unit.getTeam() != event.team()) {
                continue;
            }
            for (Effect effect : List.copyOf(unit.getEffects())) {
                if (!(effect instanceof OverheatTrackerEffect tracker) || tracker.getSource() != ember) {
                    continue;
                }
                tracker.beginTurn();
                if (tracker.consumeProc()) {
                    overheat(state, ember, unit);
                }
            }
        }
    }

    /**
     * Strikes every OTHER enemy around the overheating unit - never the unit itself, never
     * Ember's own side. Un-upgraded, this is direct damage; upgraded, it spreads Burn instead.
     */
    private void overheat(GameState state, Unit ember, Unit victim) {
        if (victim.getPosition() == null) {
            return;
        }
        for (Unit near : List.copyOf(state.getMap().getUnitsInRadius(victim.getPosition(), radius))) {
            if (near == victim || !isEnemy(ember, near)) {
                continue;
            }
            if (isUpgraded()) {
                BurnEffect.apply(state, near, ember, burnStacks);
            } else {
                DamageEvent damageEvent = new DamageEvent(ember, near, damage);
                damageEvent.setCauseLabel(CAUSE_LABEL);
                near.takeDamage(state, damageEvent);
            }
        }
    }

    private static boolean isEnemy(Unit ember, Unit other) {
        return other != null && other != ember && !other.isDead() && other.getTeam() != ember.getTeam();
    }

    private OverheatTrackerEffect trackerFor(Unit victim, Unit ember) {
        for (Effect effect : victim.getEffects()) {
            if (!effect.isExpired() && effect instanceof OverheatTrackerEffect tracker
                && tracker.getSource() == ember) {
                return tracker;
            }
        }
        OverheatTrackerEffect tracker = new OverheatTrackerEffect(ember, threshold);
        victim.addEffect(tracker);
        return tracker;
    }

    @Override
    protected void onUpgraded() {
        this.threshold = statInt("threshold", threshold);
        this.radius = statInt("radius", radius);
        this.damage = statInt("damage", damage);
        this.burnStacks = statInt("burn_stacks", burnStacks);
    }
}
