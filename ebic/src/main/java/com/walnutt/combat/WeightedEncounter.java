package com.walnutt.combat;

import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Each side's attribute is picked at random, weighted by that unit's OWN attribute
 * values - e.g. a unit with 30 strength / 10 agility / 10 intelligence rolls
 * strength 60% of the time and agility or intelligence 20% each. Used by abilities
 * that make an automatic attack with no human choosing an attribute (Duel's forced
 * turn-end attack, Cloak and Dagger's ambush).
 */
public record WeightedEncounter(Unit attacker, Unit defender) implements Encounter {

    @Override
    public Attribute resolveAttackerAttribute(GameState state) {
        return weightedAttribute(state, attacker);
    }

    @Override
    public Attribute resolveDefenderAttribute(GameState state) {
        return weightedAttribute(state, defender);
    }

    private static Attribute weightedAttribute(GameState state, Unit unit) {
        int strength = unit.getAttributeValue(Attribute.STRENGTH);
        int agility = unit.getAttributeValue(Attribute.AGILITY);
        int intelligence = unit.getAttributeValue(Attribute.INTELLIGENCE);
        int total = strength + agility + intelligence;

        if (total <= 0) {
            Attribute[] values = Attribute.values();
            return values[state.getRandom().nextInt(values.length)];
        }

        int roll = state.getRandom().nextInt(total);
        if (roll < strength) {
            return Attribute.STRENGTH;
        }
        if (roll < strength + agility) {
            return Attribute.AGILITY;
        }
        return Attribute.INTELLIGENCE;
    }
}
