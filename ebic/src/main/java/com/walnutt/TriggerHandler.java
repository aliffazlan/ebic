package com.walnutt;

import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.CooldownChangedEvent;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.DeathEvent;
import com.walnutt.event.FatalDamageEvent;
import com.walnutt.event.GameEvent;
import com.walnutt.event.HealEvent;
import com.walnutt.event.KillEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.event.PreAttackEvent;
import com.walnutt.event.PreMoveEvent;
import com.walnutt.event.StatusAppliedEvent;
import com.walnutt.event.StatusExpiredEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;

/**
 * Shared base for Ability and Effect. Both are "trigger listeners": they own no
 * gameplay loop of their own, they just react to events forwarded by the unit that
 * owns them. Default implementations do nothing, so a new subclass only overrides
 * the handful of hooks it actually cares about.
 *
 * onGameEvent is a catch-all fired for every event regardless of type, for the rare
 * mechanic that doesn't fit one of the named hooks (or listens for a CustomEvent).
 */
public abstract class TriggerHandler {
    public void onTurnStart(GameState state, TurnStartEvent event) {}
    public void onTurnEnd(GameState state, TurnEndEvent event) {}
    public void onPreMove(GameState state, PreMoveEvent event) {}
    public void onMove(GameState state, PostMoveEvent event) {}
    public void onPreAttack(GameState state, PreAttackEvent event) {}
    public void onPostAttack(GameState state, PostAttackEvent event) {}

    /**
     * Fired for EVERY DamageEvent in the game, before it is applied to the target's
     * HealthPool. Mutable/cancellable - this is where mitigation, redirection, and
     * damage-dealt bonuses (Backstab) live. Check event.getSource()/getTarget()
     * against your owner to determine whether this instance is relevant to you.
     */
    public void onIncomingDamage(GameState state, DamageEvent event) {}

    public void onFatalDamage(GameState state, FatalDamageEvent event) {}

    /**
     * Fired once damage has actually landed (post-mitigation). This is the reactive
     * hook (Counterstrike, etc.) - check event.damageEvent().getTarget()/getSource()
     * against your owner.
     */
    public void onDamageTaken(GameState state, PostDamageEvent event) {}
    public void onDeath(GameState state, DeathEvent event) {}
    public void onKill(GameState state, KillEvent event) {}
    public void onHeal(GameState state, HealEvent event) {}
    public void onStatusApplied(GameState state, StatusAppliedEvent event) {}
    public void onStatusExpired(GameState state, StatusExpiredEvent event) {}
    public void onCooldownChanged(GameState state, CooldownChangedEvent event) {}
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {}
    public void onGameEvent(GameState state, GameEvent event) {}

    public final void dispatch(GameState state, GameEvent event) {
        switch (event) {
            case TurnStartEvent e -> onTurnStart(state, e);
            case TurnEndEvent e -> onTurnEnd(state, e);
            case PreMoveEvent e -> onPreMove(state, e);
            case PostMoveEvent e -> onMove(state, e);
            case PreAttackEvent e -> onPreAttack(state, e);
            case PostAttackEvent e -> onPostAttack(state, e);
            case DamageEvent e -> onIncomingDamage(state, e);
            case FatalDamageEvent e -> onFatalDamage(state, e);
            case PostDamageEvent e -> onDamageTaken(state, e);
            case DeathEvent e -> onDeath(state, e);
            case KillEvent e -> onKill(state, e);
            case HealEvent e -> onHeal(state, e);
            case StatusAppliedEvent e -> onStatusApplied(state, e);
            case StatusExpiredEvent e -> onStatusExpired(state, e);
            case CooldownChangedEvent e -> onCooldownChanged(state, e);
            case AbilityCastEvent e -> onAbilityUsed(state, e);
            default -> {}
        }
        onGameEvent(state, event);
    }
}
