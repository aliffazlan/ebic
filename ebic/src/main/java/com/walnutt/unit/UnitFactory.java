package com.walnutt.unit;

import java.util.Map;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.Team;

public final class UnitFactory {

    private UnitFactory() {
    }

    /**
     * Every unit ships Move + Attack, plus whichever of its JSON-listed abilities
     * already has a hand-written implementation registered in AbilityFactory.
     * Abilities not yet implemented are silently skipped - the unit still functions
     * with its base kit while the rest of the roster catches up.
     */
    public static Unit createFromDefinition(UnitDefinition def, Team team, Map<String, AbilityDefinition> abilityDefs) {
        UnitStats stats = new UnitStats(def.strength(), def.agility(), def.intelligence(), def.maxHp(),
            def.effectiveAttackRange());
        UnitType type = parseType(def.type());
        Unit unit = switch (type) {
            case CHAMPION -> new ChampionUnit(def.name(), team, stats);
            case ELITE -> new EliteUnit(def.name(), team, stats);
            case BASIC -> new BasicUnit(def.name(), team, stats);
        };

        unit.addAbility(new Move());
        unit.addAbility(new Attack());

        for (String abilityId : def.abilities()) {
            AbilityDefinition abilityDef = abilityDefs.get(abilityId);
            if (abilityDef == null || !AbilityFactory.isImplemented(abilityId)) {
                continue;
            }
            Ability ability = AbilityFactory.create(abilityId, abilityDef);
            unit.addAbility(ability);
        }

        return unit;
    }

    /** Basics are generic/statless per design - no JSON backing, no special abilities. */
    public static Unit createBasic(String name, Team team) {
        UnitStats stats = new UnitStats(20, 20, 20, 300);
        Unit unit = new BasicUnit(name, team, stats);
        unit.addAbility(new Move());
        unit.addAbility(new Attack());
        return unit;
    }

    /** Shared with summon builders (Branchlings, Pylons) that construct a Unit from a prototype definition. */
    public static UnitType parseType(String type) {
        return switch (type.toLowerCase()) {
            case "champion" -> UnitType.CHAMPION;
            case "elite" -> UnitType.ELITE;
            case "basic" -> UnitType.BASIC;
            default -> throw new IllegalArgumentException("Unknown unit type: " + type);
        };
    }
}
