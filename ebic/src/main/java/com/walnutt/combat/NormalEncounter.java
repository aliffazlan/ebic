package com.walnutt.combat;

import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Attributes already chosen (by a player via InputHandler, or reused/derived by an ability). */
public record NormalEncounter(Unit attacker, Unit defender, Attribute attackerAttribute, Attribute defenderAttribute)
    implements Encounter {

    @Override
    public Attribute resolveAttackerAttribute(GameState state) {
        return attackerAttribute;
    }

    @Override
    public Attribute resolveDefenderAttribute(GameState state) {
        return defenderAttribute;
    }
}
