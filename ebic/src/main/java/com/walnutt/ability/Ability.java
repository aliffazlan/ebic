package com.walnutt.ability;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.TriggerHandler;
import com.walnutt.ability.target.MultiTarget;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.status.Stat;
import com.walnutt.unit.ActionKind;
import com.walnutt.unit.Unit;

public abstract class Ability extends TriggerHandler {

    /**
     * Range for abilities that can be aimed anywhere on the map (Pylon, Manifestation,
     * Sprout). Distinct from a large number so the client can tell "reaches everywhere"
     * apart from "reaches 8 tiles" and skip drawing a misleading range band.
     */
    public static final int UNLIMITED_RANGE = -1;

    private final String name;
    private final String description;
    private final boolean isPassive;
    /**
     * The design_ideas/abilities/<unit>/<id>.json basename this ability was built from,
     * or null for one constructed directly in Java (Move, Attack, DroneAutoAttack, the
     * Pylon's internal kit). Set in exactly one place - {@link
     * com.walnutt.data.AbilityFactory#create} - which makes "was this built from a
     * definition" a reliable test with no list to maintain, and is what Joker's Mimic
     * uses both to decide an ability is copyable at all and to rebuild it from
     * GameState.getAbilityDefinitions().
     *
     * NOT the same string as web.dto.AbilitySnapshot.id, which is
     * Identifiers.normalize(getName()): the wire id has to exist for Move and Attack
     * too, and the two do not always agree anyway (Harbinger's "Sanity's Eclipse"
     * normalizes to sanity_s_eclipse, not its filename sanity_eclipse).
     */
    private String definitionId;
    protected Unit owner;
    private int maxCooldown;
    private int currentCooldown;
    private int range = 1;

    public Ability(String name, boolean isPassive) {
        this(name, "", isPassive);
    }

    public Ability(String name, String description, boolean isPassive) {
        this.name = name;
        this.description = description;
        this.isPassive = isPassive;
    }

    public String getDefinitionId() {
        return this.definitionId;
    }

    /** Called only by AbilityFactory, immediately after construction. */
    public void setDefinitionId(String definitionId) {
        this.definitionId = definitionId;
    }

    public Unit getOwner() {
        return this.owner;
    }

    public void setOwner(Unit unit) {
        this.owner = unit;
        if (unit != null) {
            onAttached(unit);
        }
    }

    /**
     * Called once, the moment this ability is attached to a unit (Unit.addAbility is
     * setOwner's only caller). Default no-op.
     *
     * Exists for abilities whose effect is on the OWNER rather than on anything the
     * ability itself does - Maxwell's Gyroscope grants permanent range modifiers here.
     * Doing it on attach rather than in the constructor is what makes such an ability
     * work when it's granted mid-match (Eureka constructs gadgets one at a time), not
     * just when it's built as part of a unit's starting kit.
     */
    protected void onAttached(Unit newOwner) {
    }

    public String getName() {
        return this.name;
    }

    public String getDescription() {
        return this.description;
    }

    public boolean isPassive() {
        return this.isPassive;
    }

    public int getMaxCooldown() {
        return maxCooldown;
    }

    public void setMaxCooldown(int maxCooldown) {
        this.maxCooldown = maxCooldown;
    }

    public int getCurrentCooldown() {
        return currentCooldown;
    }

    public boolean isReady() {
        return currentCooldown <= 0;
    }

    public void resetToMax() {
        currentCooldown = maxCooldown;
    }

    /** No ceiling - some abilities (e.g. Energy Break) push cooldowns past their max. */
    public void increaseCooldown(int amount) {
        currentCooldown += amount;
    }

    public void decreaseCooldown(int amount) {
        currentCooldown = Math.max(0, currentCooldown - amount);
    }

    /** Engine-owned bookkeeping, called once per owner's turn start - not authored per ability. */
    public void tick() {
        currentCooldown = Math.max(0, currentCooldown - 1);
    }

    /**
     * This ability's own range plus whatever cast-range bonus its owner carries
     * (Stat.CAST_RANGE - Gyroscope). UNLIMITED_RANGE stays unlimited: adding to
     * "everywhere" is meaningless, and the sentinel is what tells the client to skip
     * drawing a range band.
     */
    public int getRange() {
        if (range == UNLIMITED_RANGE || range == 0) {
            // 0 means a pure self-cast - there is no distance for a bonus to extend, and
            // the client reads 0 as "draw no range band".
            return range;
        }
        return range + castRangeBonus();
    }

    private int castRangeBonus() {
        return owner == null ? 0 : (int) owner.getEffective(Stat.CAST_RANGE);
    }

    public void setRange(int range) {
        this.range = range;
    }

    /**
     * Closest distance this ability can be aimed at; 0 for everything except a basic
     * Attack under a minimum-range effect. Exposed so the client can draw the castable
     * band without re-deriving the rule.
     */
    public int getMinRange() {
        return 0;
    }

    /**
     * Abilities this one is holding on the owner's behalf but which the owner is NOT
     * currently wielding - Joker's Mimic parks a copy here when a new one replaces it.
     * Empty for every other ability.
     *
     * Held abilities are deliberately absent from Unit.getAbilities() and
     * getAllTriggerHandlers(): they receive no events and are invisible to everything
     * that enumerates a unit's kit. That is exactly why Wei's Energy Break and Implosion
     * still only reach what a unit is actually wielding, with no change to either class.
     * The two things that DO see them are both cooldown bookkeeping - Unit.startTurn
     * ticks them, and Joker's Superior Mastery reduces them - because a parked copy is
     * required to keep cooling down while it waits.
     */
    public List<Ability> getHeldAbilities() {
        return List.of();
    }

    /**
     * Move points this cast spends. Zero for a passive, and zero when the owner has a
     * banked charge to pay with instead (Maxwell's Capacitor Bank) - the charge itself is
     * consumed afterwards, on the POST cast event, which keeps this a pure query.
     *
     * Move and Attack override this outright and so are never covered by a charge.
     */
    public int getMoveCost(GameState state) {
        if (isPassive) {
            return 0;
        }
        return owner != null && owner.hasFreeCastCharge() ? 0 : 1;
    }

    /**
     * Baseline economy/readiness checks shared by every active ability - including
     * the move-point cost, so subclasses no longer need their own separate
     * {@code state.canSpendMoves(getMoveCost(state))} check. Subclasses should call
     * {@code super.canUse(state, target)} and AND it with their own
     * target-shape/range checks.
     *
     * <p>Note Move and Attack deliberately do NOT call this - they roll their own checks,
     * which is what keeps them outside the per-ability restriction below (Joker's
     * Superior Mastery locks each ability to one cast per turn, but never his walk or
     * his swing).
     */
    public boolean canUse(GameState state, Target target) {
        return !isPassive && isReady() && owner != null && !owner.isBlockedFrom(ActionKind.ABILITY)
            && !owner.isAbilityRestricted(this)
            && targetIsSelectable(target)
            && state.canSpendMoves(getMoveCost(state));
    }

    /**
     * False when this target names a unit that is currently sealed off (Unit.isTargetable) -
     * a cloaked Evayne, a frozen unit, anything imprisoned. One check here covers every
     * ability that calls super.canUse; Attack repeats it, since it rolls its own checks.
     *
     * MultiTarget is unwrapped because its primary half is a unit (Translocation picks a
     * unit and then a tile) - ai.HintContext has to do the same unwrap for the same reason.
     */
    private static boolean targetIsSelectable(Target target) {
        Unit unit = switch (target) {
            case UnitTarget unitTarget -> unitTarget.getUnit();
            case MultiTarget multi when multi.primary() instanceof UnitTarget primary -> primary.getUnit();
            default -> null;
        };
        return unit == null || unit.isTargetable();
    }

    /** True if {@code position} is within this ability's range of its owner - the range check every targeted ability needs. */
    protected final boolean isInRange(GameState state, Position position) {
        int effectiveRange = getRange();
        return effectiveRange == UNLIMITED_RANGE
            || state.getMap().getDistance(owner.getPosition(), position) <= effectiveRange;
    }

    /**
     * Brute-force but correct-by-construction: tries every unit, every tile, and a
     * no-target candidate against this ability's own {@link #canUse}, rather than
     * duplicating each subclass's range/team/target-shape logic a second time.
     * The map is small (radius <= 8, so <= 217 tiles) and this is only called when
     * a caller (e.g. a frontend deciding what to highlight) actually needs the
     * full legal-target set, not on every turn - cheap enough to not warrant a
     * per-ability override, though one is always possible if a future ability's
     * canUse is expensive enough to need it.
     */
    public List<Target> getLegalTargets(GameState state) {
        List<Target> legal = new ArrayList<>();
        if (canUse(state, new NoTarget())) {
            legal.add(new NoTarget());
        }
        for (Unit unit : state.getAllActiveUnits()) {
            UnitTarget candidate = new UnitTarget(unit);
            if (canUse(state, candidate)) {
                legal.add(candidate);
            }
        }
        for (Tile tile : state.getMap().getTilesInRadius(new Position(0, 0), state.getMap().getRadius())) {
            TileTarget candidate = new TileTarget(tile);
            if (canUse(state, candidate)) {
                legal.add(candidate);
            }
        }
        return legal;
    }

    public abstract void onUse(GameState state, Target target);
}
