package com.walnutt.combat;

import java.util.List;

import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Each side's attribute is picked uniformly at random from the ones it can actually use,
 * independently. Not used by any ability yet, but it must not contradict the rule the
 * other two encounter types follow: a unit never brings an attribute it has none of, and
 * one with every attribute at 0 brings nothing (null - see EncounterResolver).
 */
public record RandomEncounter(Unit attacker, Unit defender) implements Encounter {

    @Override
    public Attribute resolveAttackerAttribute(GameState state) {
        return randomUsableAttribute(state, attacker);
    }

    @Override
    public Attribute resolveDefenderAttribute(GameState state) {
        return randomUsableAttribute(state, defender);
    }

    private static Attribute randomUsableAttribute(GameState state, Unit unit) {
        List<Attribute> usable = unit.getUsableAttributes();
        if (usable.isEmpty()) {
            return null;
        }
        return usable.get(state.getRandom().nextInt(usable.size()));
    }
}
