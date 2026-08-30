package com.walnutt.ability.impl;

import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.AbilityFactory;
import com.walnutt.data.UnitDefinition;
import com.walnutt.effect.impl.ManifestationDebuffEffect;
import com.walnutt.effect.impl.ShadowLifespanEffect;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.status.Stat;
import com.walnutt.unit.HealthPool;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitFactory;
import com.walnutt.unit.UnitStats;
import com.walnutt.unit.UnitType;

/**
 * Mercurial - teleports to a free tile anywhere on the map, debuffing whoever's now
 * adjacent.
 *
 * The destination must border an enemy: the range is still global, but it's a strike
 * rather than a general-purpose escape, so it can only land where it actually does
 * something.
 */
public class Manifestation extends Ability {
    private static final String SHADOW_DEFINITION_ID = "mercurial_shadow";

    private double damageReduction;
    private int duration;
    /** Upgrade: turns the shadow stands for. 0 until upgraded, when no shadow is left at all. */
    private int shadowDuration;

    public Manifestation(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 5));
        setRange(UNLIMITED_RANGE); // castable anywhere on the map - no distance check in canUse
        this.damageReduction = definition.getDouble("dmg_reduction", 0.5);
        this.duration = definition.getInt("duration", 2);
    }

    @Override
    protected void onUpgraded() {
        this.damageReduction = stat("dmg_reduction", damageReduction);
        this.duration = statInt("duration", duration);
        this.shadowDuration = statInt("shadow_duration", 0);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof TileTarget tileTarget)) {
            return false;
        }
        Tile destination = tileTarget.getTile();
        return destination.isWalkable() && hasAdjacentEnemy(state, destination);
    }

    /** Checks the DESTINATION's neighbours, not the caster's - this runs before the teleport. */
    private boolean hasAdjacentEnemy(GameState state, Tile destination) {
        return !state.getMap().getAdjacentUnits(destination.getPosition(),
            u -> u.getTeam() != owner.getTeam() && !u.isDead()).isEmpty();
    }

    @Override
    public void onUse(GameState state, Target target) {
        Tile destination = ((TileTarget) target).getTile();
        // Read before the teleport: the shadow is left where he VANISHED FROM, which is the
        // whole point of it as an escape route.
        Tile vacated = state.getMap().getTile(owner.getPosition());

        state.getMap().moveUnit(owner, destination);

        for (Unit enemy : state.getMap().getAdjacentUnits(owner.getPosition(),
                u -> u.getTeam() != owner.getTeam() && !u.isDead())) {
            enemy.addEffect(new ManifestationDebuffEffect(duration, damageReduction));
        }
        if (shadowDuration > 0 && vacated != null) {
            leaveShadow(state, vacated);
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    /**
     * The upgrade's shadow: Mercurial's own statline and CURRENT health, standing where he was.
     *
     * Built by hand rather than through UnitFactory, which would attach Move and Attack to it -
     * a shadow cannot walk, and its only ability is Recall. Its stats are copied live rather
     * than read from mercurial_shadow.json (which mirrors his statline exactly) because "same hp,
     * current and max" is not something a prototype file can express; the JSON supplies the name
     * and the ability list.
     *
     * It joins the player's ROSTER rather than the summon registry, the KillerDrone pattern -
     * that is what makes it selectable, and the only way its lifespan effect ticks at all.
     */
    private void leaveShadow(GameState state, Tile tile) {
        UnitDefinition definition = state.getUnitDefinitions().get(SHADOW_DEFINITION_ID);
        String name = definition == null ? "Mercurial Shadow" : definition.name();
        UnitType type = definition == null ? UnitType.BASIC : UnitFactory.parseType(definition.type());

        // His live stats, so a Mercurial who has been buffed or drained casts a shadow to match.
        UnitStats stats = new UnitStats(
            (int) owner.getEffective(Stat.STRENGTH),
            (int) owner.getEffective(Stat.AGILITY),
            (int) owner.getEffective(Stat.INTELLIGENCE),
            owner.getMaxHealth(),
            (int) owner.getEffective(Stat.ATTACK_RANGE));
        HealthPool health = new HealthPool(owner.getMaxHealth());
        health.setCurrent(owner.getHealth());

        Unit shadow = new SummonedUnit(name, owner.getTeam(), type, stats, health, owner, false, true);
        // Attack but no Move: it fights whoever comes to it and stands where it was left.
        shadow.addAbility(new Attack());
        for (String abilityId : definition == null ? List.<String>of() : definition.abilities()) {
            AbilityDefinition abilityDef = state.getAbilityDefinitions().get(abilityId);
            if (abilityDef != null && AbilityFactory.isImplemented(abilityId)) {
                shadow.addAbility(AbilityFactory.create(abilityId, abilityDef));
            }
        }
        shadow.addEffect(new ShadowLifespanEffect(shadowDuration));

        state.getMap().moveUnit(shadow, tile);
        state.getPlayer(owner.getTeam()).addUnit(shadow);
    }
}
