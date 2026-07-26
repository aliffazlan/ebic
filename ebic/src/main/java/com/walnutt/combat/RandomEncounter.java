package com.walnutt.combat;

import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Each side's attribute is picked uniformly at random, independently. Not used by any ability yet. */
public record RandomEncounter(Unit attacker, Unit defender) implements Encounter {

    @Override
    public Attribute resolveAttackerAttribute(GameState state) {
        return randomAttribute(state);
    }

    @Override
    public Attribute resolveDefenderAttribute(GameState state) {
        return randomAttribute(state);
    }

    private static Attribute randomAttribute(GameState state) {
        Attribute[] values = Attribute.values();
        return values[state.getRandom().nextInt(values.length)];
    }
}
