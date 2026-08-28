package com.walnutt.event;

import com.walnutt.unit.Unit;

/**
 * @param chained true when this is an engine-generated follow-up hit within one ongoing
 *                attack chain (Chronos's Timeless Strike) rather than a separate attack.
 *                Reactive passives that should fire once per attack *cast* - Energy Break,
 *                Counterstrike - check this, because Timeless Strike republishes a
 *                PostAttackEvent per chained hit and would otherwise proc them up to nine
 *                times off a single click.
 *
 *                Deliberately NOT set by Duel's forced turn-end attack, Feast's free attack
 *                or Static Link's free attack: each of those is a genuinely separate
 *                once-per-turn attack, not a repeat of one cast, so they should keep
 *                proccing normally.
 */
public record PostAttackEvent(Unit attacker, Unit defender, DamageEvent damageEvent, boolean chained)
    implements GameEvent {

    /** An ordinary, non-chained attack - the overwhelmingly common case. */
    public PostAttackEvent(Unit attacker, Unit defender, DamageEvent damageEvent) {
        this(attacker, defender, damageEvent, false);
    }
}
