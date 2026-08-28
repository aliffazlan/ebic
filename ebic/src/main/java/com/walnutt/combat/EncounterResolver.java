package com.walnutt.combat;

import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Resolves the rock-paper-scissors encounter into a DamageEvent.
 * Strength beats Intelligence, Intelligence beats Agility, Agility beats Strength.
 */
public class EncounterResolver {

    public DamageEvent resolve(GameState state, Encounter encounter) {
        Attribute attackerAttribute = encounter.resolveAttackerAttribute(state);
        Attribute defenderAttribute = encounter.resolveDefenderAttribute(state);

        int damage = calculateDamage(encounter.attacker(), encounter.defender(), attackerAttribute, defenderAttribute);

        DamageEvent event = new DamageEvent(encounter.attacker(), encounter.defender(), damage);
        event.setAttackerAttribute(attackerAttribute);
        event.setDefenderAttribute(defenderAttribute);
        return event;
    }

    /**
     * A null attribute means that side had none it could use (every value at 0) - see
     * Unit.getUsableAttributes. An attacker with nothing to swing deals no damage; a
     * defender with nothing to block takes the attacker's value in full, since there is
     * no matchup to win. (Both readings agree on that number: treating the missing
     * defence as a 0-valued mirror gives max(0, attackerValue - 0) either way.)
     */
    private int calculateDamage(Unit attacker, Unit defender, Attribute attackAttr, Attribute defendAttr) {
        if (attackAttr == null) {
            return 0;
        }
        if (defendAttr == null) {
            return attacker.getAttributeValue(attackAttr);
        }
        if (attackAttr.beats(defendAttr)) {
            return attacker.getAttributeValue(attackAttr);
        }
        if (defendAttr.beats(attackAttr)) {
            return 0;
        }
        int attackerValue = attacker.getAttributeValue(attackAttr);
        int defenderValue = defender.getAttributeValue(defendAttr);
        return Math.max(0, attackerValue - defenderValue);
    }
}
