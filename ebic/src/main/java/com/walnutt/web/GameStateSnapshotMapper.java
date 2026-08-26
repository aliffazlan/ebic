package com.walnutt.web;

import java.util.List;
import java.util.Map;

import com.walnutt.ability.Ability;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.status.Stat;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;
import com.walnutt.web.dto.AbilityPreviewSnapshot;
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
            ability.getDescription(),
            ability.isPassive(),
            ability.isReady(),
            ability.getCurrentCooldown(),
            ability.getMaxCooldown()
        );
    }

    public static String definitionId(Unit unit) {
        return unit.getUnitType() == UnitType.BASIC ? "basic" : Identifiers.normalize(unit.getName());
    }

    /**
     * `abilityDefs` is state.getAbilityDefinitions() from whichever GameState the
     * caller has in scope (WebInputHandler.choosePick receives it directly;
     * WebRenderer.renderDraftRound doesn't, so it's fed the same map separately -
     * see GameSession, which populates both from the one GameState right after
     * construction). Missing lookups (shouldn't happen - every drafted unit's own
     * abilities{} list is expected to resolve, per CLAUDE.md's "every unit's ability
     * ids wired into AbilityFactory") fall back to just the bare id, no crash.
     */
    public static UnitDefinitionSnapshot toDefinitionSnapshot(UnitDefinition def, Map<String, AbilityDefinition> abilityDefs) {
        List<AbilityPreviewSnapshot> abilities = def.abilities().stream()
            .map(id -> toAbilityPreviewSnapshot(id, abilityDefs.get(id)))
            .toList();
        return new UnitDefinitionSnapshot(
            Identifiers.normalize(def.name()),
            def.name(),
            def.type(),
            def.maxHp(),
            def.strength(),
            def.agility(),
            def.intelligence(),
            abilities
        );
    }

    private static AbilityPreviewSnapshot toAbilityPreviewSnapshot(String id, AbilityDefinition def) {
        if (def == null) {
            return new AbilityPreviewSnapshot(id, id, "", false, 0);
        }
        return new AbilityPreviewSnapshot(id, def.name(), def.formattedDescription(), def.isPassive(), def.getInt("cooldown", 0));
    }
}
