package com.walnutt.web;

import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.data.UnitDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.status.Stat;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;
import com.walnutt.web.dto.AbilitySnapshot;
import com.walnutt.web.dto.EffectSnapshot;
import com.walnutt.web.dto.GameStateSnapshot;
import com.walnutt.web.dto.UnitDefinitionSnapshot;
import com.walnutt.web.dto.UnitSnapshot;

/**
 * Hand-written GameState -> DTO mapping. Deliberately never gson.toJson()s a raw
 * engine object - GameState embeds an InputHandler, an EventBus, and unit<->effect
 * back-references that would either fail to serialize or dump far more than the
 * client needs.
 */
public final class GameStateSnapshotMapper {
    private final UnitIdRegistry ids;

    public GameStateSnapshotMapper(UnitIdRegistry ids) {
        this.ids = ids;
    }

    public GameStateSnapshot toSnapshot(GameState state) {
        List<UnitSnapshot> units = state.getAllActiveUnits().stream()
            .map(this::toUnitSnapshot)
            .toList();

        return new GameStateSnapshot(
            state.getCurrentPlayer().getTeam().name(),
            state.getRemainingMoves(),
            state.isGameOver(),
            state.getMap().getRadius(),
            units
        );
    }

    public UnitSnapshot toUnitSnapshot(Unit unit) {
        Position pos = unit.getPosition();
        List<AbilitySnapshot> abilities = unit.getAbilities().stream()
            .map(this::toAbilitySnapshot)
            .toList();
        List<String> statusFlags = java.util.Arrays.stream(StatusFlag.values())
            .filter(unit::hasStatus)
            .map(Enum::name)
            .toList();
        List<EffectSnapshot> effects = unit.getEffects().stream()
            .map(this::toEffectSnapshot)
            .toList();

        return new UnitSnapshot(
            ids.idFor(unit),
            unit.getName(),
            definitionId(unit),
            unit.getTeam().name(),
            unit.getUnitType().name(),
            pos == null ? 0 : pos.getQ(),
            pos == null ? 0 : pos.getR(),
            unit.getHealth(),
            unit.getMaxHealth(),
            (int) unit.getEffective(Stat.STRENGTH),
            (int) unit.getEffective(Stat.AGILITY),
            (int) unit.getEffective(Stat.INTELLIGENCE),
            unit.isDead(),
            unit.hasMovedThisTurn(),
            unit.hasAttackedThisTurn(),
            statusFlags,
            abilities,
            effects
        );
    }

    private EffectSnapshot toEffectSnapshot(Effect effect) {
        boolean permanent = effect.getRemainingTurns() >= Effect.PERMANENT;
        return new EffectSnapshot(
            effect.getName(),
            effect.getDescription(),
            effect.getCategory().name(),
            permanent,
            permanent ? 0 : effect.getRemainingTurns(),
            effect.getStatusFlags().stream().map(Enum::name).toList(),
            effect.getExtraInfo()
        );
    }

    public AbilitySnapshot toAbilitySnapshot(Ability ability) {
        return new AbilitySnapshot(
            Identifiers.normalize(ability.getName()),
            ability.getName(),
            ability.isPassive(),
            ability.isReady(),
            ability.getCurrentCooldown(),
            ability.getMaxCooldown()
        );
    }

    public static String definitionId(Unit unit) {
        return unit.getUnitType() == UnitType.BASIC ? "basic" : Identifiers.normalize(unit.getName());
    }

    public static UnitDefinitionSnapshot toDefinitionSnapshot(UnitDefinition def) {
        return new UnitDefinitionSnapshot(
            Identifiers.normalize(def.name()),
            def.name(),
            def.type(),
            def.maxHp(),
            def.strength(),
            def.agility(),
            def.intelligence(),
            def.abilities()
        );
    }
}
