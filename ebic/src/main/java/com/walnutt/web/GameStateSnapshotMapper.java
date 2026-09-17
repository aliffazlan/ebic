package com.walnutt.web;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.Map;

import com.walnutt.ability.Ability;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.AcidPoolEffect;
import com.walnutt.effect.impl.BarrierEffect;
import com.walnutt.effect.impl.BurningGroundEffect;
import com.walnutt.effect.impl.DuelEffect;
import com.walnutt.effect.impl.HomingMissileEffect;
import com.walnutt.effect.impl.OrbEffect;
import com.walnutt.effect.impl.StaticLinkEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.status.Stat;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;
import com.walnutt.web.dto.AbilityPreviewSnapshot;
import com.walnutt.web.dto.AbilitySnapshot;
import com.walnutt.web.dto.EffectSnapshot;
import com.walnutt.web.dto.GameStateSnapshot;
import com.walnutt.web.dto.TileEffectSnapshot;
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
            .map(unit -> toUnitSnapshot(unit, state))
            .toList();

        return new GameStateSnapshot(
            state.getCurrentPlayer().getTeam().name(),
            state.getRemainingMoves(),
            state.isGameOver(),
            state.getMap().getRadius(),
            state.getMap().getRowLimit(),
            units,
            collectTileEffects(state)
        );
    }

    /**
     * Tile-bound effects are stored on the unit that created them (see
     * BurningGroundEffect), so this sweeps every active unit rather than the map.
     *
     * Two of these describe something that has not happened yet rather than something
     * already on the ground - a pending Sanity's Eclipse and a missile in flight. Both
     * exist purely so the delay they advertise is something a player can actually see and
     * react to; a delayed blast nobody can locate is just a surprise.
     */
    private static List<TileEffectSnapshot> collectTileEffects(GameState state) {
        List<TileEffectSnapshot> tileEffects = new ArrayList<>();
        for (BurningGroundEffect ground : BurningGroundEffect.activeGrounds(state)) {
            tileEffects.add(new TileEffectSnapshot(
                ground.getTile().getQ(),
                ground.getTile().getR(),
                "burning",
                ground.getName(),
                ground.getRemainingTurns()));
        }
        for (OrbEffect orb : OrbEffect.activeOrbs(state)) {
            // One entry per real tile in the blast, asked of the map rather than derived
            // from the coordinates: the match board is row-trimmed, so hexDistance <= radius
            // does not imply a tile exists there.
            for (Tile tile : state.getMap().getTilesInRadius(orb.getTargetPosition(), orb.getRadius())) {
                tileEffects.add(new TileEffectSnapshot(
                    tile.getPosition().getQ(),
                    tile.getPosition().getR(),
                    "eclipse",
                    orb.getName(),
                    orb.getRemainingTurns()));
            }
        }
        for (AcidPoolEffect acid : AcidPoolEffect.activePools(state)) {
            // Same reason as the eclipse below: the board is row-trimmed, so the tiles a
            // radius actually covers have to be asked of the map rather than derived.
            for (Tile tile : state.getMap().getTilesInRadius(acid.getCentre(), acid.getRadius())) {
                tileEffects.add(new TileEffectSnapshot(
                    tile.getPosition().getQ(),
                    tile.getPosition().getR(),
                    "acid",
                    acid.getName(),
                    acid.getRemainingTurns()));
            }
        }
        // Deduped by impact tile: upgraded Homing Missile puts TWO locks on one victim, and two
        // identical reticles stacked on the same hex just render as one slightly darker one.
        Set<Position> markedImpacts = new java.util.HashSet<>();
        for (HomingMissileEffect missile : HomingMissileEffect.activeLocks(state)) {
            Position impact = missile.getOwner() == null ? null : missile.getOwner().getPosition();
            if (impact == null || !markedImpacts.add(impact)) {
                continue;
            }
            tileEffects.add(new TileEffectSnapshot(
                impact.getQ(),
                impact.getR(),
                "missile",
                missile.getName(),
                missile.getRemainingTurns()));
        }
        return tileEffects;
    }

    /**
     * Convenience for callers with no GameState to hand (the mapper's own tests). The state
     * is only ever passed on to Ability.getMoveCost, which no implementation actually reads
     * it in, so a null is safe here - but the real path threads the real state through.
     */
    public UnitSnapshot toUnitSnapshot(Unit unit) {
        return toUnitSnapshot(unit, null);
    }

    public UnitSnapshot toUnitSnapshot(Unit unit, GameState state) {
        Position pos = unit.getPosition();
        List<AbilitySnapshot> abilities = unit.getAbilities().stream()
            .map(ability -> toAbilitySnapshot(ability, state))
            .toList();
        List<String> statusFlags = java.util.Arrays.stream(StatusFlag.values())
            .filter(unit::hasStatus)
            .map(Enum::name)
            .toList();
        List<EffectSnapshot> effects = unit.getEffects().stream()
            .map(this::toEffectSnapshot)
            .toList();
        int currentBarrierHp = unit.getEffects().stream()
            .filter(e -> !e.isExpired() && e instanceof BarrierEffect)
            .mapToInt(e -> ((BarrierEffect) e).getRemainingBarrierHp())
            .sum();
        int maxBarrierHp = unit.getEffects().stream()
            .filter(e -> !e.isExpired() && e instanceof BarrierEffect)
            .mapToInt(e -> ((BarrierEffect) e).getMaxBarrierHp())
            .sum();

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
            currentBarrierHp,
            maxBarrierHp,
            (int) unit.getEffective(Stat.STRENGTH),
            (int) unit.getEffective(Stat.AGILITY),
            (int) unit.getEffective(Stat.INTELLIGENCE),
            (int) unit.getEffective(Stat.ATTACK_RANGE),
            unit.getMinAttackRange(),
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
        String partnerUnitId = null;
        if (effect instanceof DuelEffect duel && duel.getOpponent() != null) {
            partnerUnitId = ids.idFor(duel.getOpponent());
        } else if (effect instanceof StaticLinkEffect link && link.getTarget() != null) {
            partnerUnitId = ids.idFor(link.getTarget());
        }
        return new EffectSnapshot(
            effect.getName(),
            effect.getDescription(),
            effect.getCategory().name(),
            permanent,
            permanent ? 0 : effect.getRemainingTurns(),
            effect.getStatusFlags().stream().map(Enum::name).toList(),
            effect.getExtraInfo(),
            partnerUnitId
        );
    }

    public AbilitySnapshot toAbilitySnapshot(Ability ability, GameState state) {
        return new AbilitySnapshot(
            Identifiers.normalize(ability.getName()),
            ability.getName(),
            ability.getDescription(),
            ability.getDetails(),
            ability.getStats(),
            ability.isPassive(),
            ability.isUpgraded(),
            ability.isReady(),
            // Read off the ability's own owner rather than a passed-in unit: an ability
            // always knows who holds it, and this keeps the public single-argument
            // signature every existing caller and test uses.
            ability.getOwner() != null && ability.getOwner().isAbilityRestricted(ability),
            ability.getCurrentCooldown(),
            ability.getMaxCooldown(),
            ability.getMoveCost(state),
            ability.getRange(),
            ability.getMinRange()
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
            // Uppercased to match the contract's "CHAMPION" | "ELITE" - the JSON itself
            // spells these lowercase, and passing that straight through was a live
            // mismatch with both API_CONTRACT.md and the client's own type.
            def.type() == null ? null : def.type().toUpperCase(java.util.Locale.ROOT),
            def.maxHp(),
            def.strength(),
            def.agility(),
            def.intelligence(),
            def.effectiveAttackRange(),
            abilities
        );
    }

    private static AbilityPreviewSnapshot toAbilityPreviewSnapshot(String id, AbilityDefinition def) {
        if (def == null) {
            return new AbilityPreviewSnapshot(id, id, "", List.of(), Map.of(), false, 0);
        }
        return new AbilityPreviewSnapshot(id, def.name(), def.formattedDescription(), def.formattedDetails(),
            def.stats() == null ? Map.of() : def.stats(), def.isPassive(), def.getInt("cooldown", 0));
    }
}
