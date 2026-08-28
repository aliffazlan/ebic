package com.walnutt.combat;

import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.event.PreAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Pure combat mechanics, deliberately with NO move-cost/turn-flag checks. This is
 * what makes free/forced actions (Counterstrike's counter-attack, and later Duel's
 * forced mutual attack or Static Link's free attack) possible: ability/effect code
 * calls straight into here, bypassing the player's action economy, while the normal
 * Attack ability validates economy first and then calls the same entry point.
 */
public final class CombatEngine {
    private static final EncounterResolver RESOLVER = new EncounterResolver();

    private CombatEngine() {
    }

    /** Convenience for the common case of attributes already chosen (e.g. via InputHandler). */
    public static DamageEvent performAttack(GameState state, Unit attacker, Unit defender,
                                             Attribute attackerAttribute, Attribute defenderAttribute) {
        return performAttack(state, new NormalEncounter(attacker, defender, attackerAttribute, defenderAttribute));
    }

    public static DamageEvent performAttack(GameState state, Encounter encounter) {
        return performAttack(state, encounter, false);
    }

    /**
     * {@code chained} marks a follow-up hit inside one ongoing attack chain, so passives
     * that must fire once per cast can tell it apart from a fresh attack - see
     * PostAttackEvent. Only Timeless Strike passes true.
     */
    public static DamageEvent performAttack(GameState state, Encounter encounter, boolean chained) {
        PreAttackEvent preAttack = new PreAttackEvent(encounter.attacker(), encounter.defender());
        state.getEventBus().publish(state, preAttack);
        if (preAttack.isCancelled()) {
            return null;
        }

        DamageEvent event = RESOLVER.resolve(state, encounter);
        if (event.getCauseLabel() == null) {
            event.setCauseLabel("Attack");
        }
        encounter.defender().takeDamage(state, event);

        state.getEventBus().publish(state,
            new PostAttackEvent(encounter.attacker(), encounter.defender(), event, chained));
        return event;
    }

    public static void applyDamage(GameState state, Unit target, DamageEvent event) {
        target.takeDamage(state, event);
    }
}
