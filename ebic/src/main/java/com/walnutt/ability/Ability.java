package com.walnutt.ability;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.walnutt.TriggerHandler;
import com.walnutt.data.AbilityDefinition;
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
    private String description;
    private boolean isPassive;
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
    /**
     * The long-form bullet points and the raw tuning numbers behind {@link
     * #getDescription()}, both stamped alongside definitionId in {@link
     * com.walnutt.data.AbilityFactory#create} and both empty for anything constructed
     * directly in Java (Move, Attack, DroneAutoAttack, the Pylon's internal kit). The
     * client shows them only when the player asks for the verbose tooltip, which is what
     * lets the description itself stay to one or two sentences.
     */
    private List<String> details = List.of();
    private Map<String, Double> stats = Map.of();
    /**
     * The whole definition this was built from, kept so {@link #upgrade()} can re-read it.
     * Null for anything constructed directly in Java (Move, Attack, DroneAutoAttack, the
     * Pylon's internal kit), which is exactly what makes those un-upgradeable with no list
     * to maintain - the same trick getDefinitionId already plays for copyability.
     */
    private AbilityDefinition definition;
    /**
     * How many times this has been upgraded. A boolean everywhere except Hidden Potential,
     * whose upgrade is repeatable and pays out per upgrade Shawl has granted - see
     * AbilityDefinition.isUpgradeRepeatable.
     */
    private int upgradeCount;
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

    public List<String> getDetails() {
        return this.details;
    }

    public Map<String, Double> getStats() {
        return this.stats;
    }

    /** Called only by AbilityFactory, immediately after construction - see the field comment. */
    public void setDefinition(AbilityDefinition definition) {
        this.definition = definition;
        this.details = definition == null ? List.of() : definition.formattedDetails();
        this.stats = definition == null || definition.stats() == null
            ? Map.of() : Map.copyOf(definition.stats());
    }

    public AbilityDefinition getDefinition() {
        return this.definition;
    }

    public boolean isUpgraded() {
        return upgradeCount > 0;
    }

    /** Only ever above 1 for Hidden Potential, whose upgrade is repeatable. */
    public int getUpgradeCount() {
        return upgradeCount;
    }

    /**
     * Whether this instance may be upgraded right now: the design has an upgrade block, and
     * either it has not been taken yet or it is the repeatable one.
     *
     * Deliberately NOT the same question as "should Hidden Potential offer this" - that also
     * requires the behaviour to be implemented (AbilityFactory.isUpgradeImplemented), which
     * is a property of the engine rather than of this instance.
     */
    public boolean canUpgrade() {
        return definition != null && definition.isUpgradeable()
            && (upgradeCount == 0 || definition.isUpgradeRepeatable());
    }

    /**
     * Unlocks this ability's upgraded form, permanently. Final so no subclass can forget to
     * re-apply the shared half; a subclass that caches its own tuning numbers overrides
     * {@link #onUpgraded()} instead.
     *
     * Everything the base class already owns - text, stats, cooldown, range, passive-ness -
     * is re-applied here, which is why an ability whose upgrade only moves its cooldown, its
     * cast range, or its wording needs no code of its own at all.
     */
    public final void upgrade() {
        if (!canUpgrade()) {
            return;
        }
        upgradeCount++;
        applyUpgradedDefinition();
        onUpgraded();
    }

    /**
     * Re-read whatever this ability cached from its definition at construction. Default
     * no-op, correct for anything that reads its numbers straight off {@link #getStats()}.
     *
     * Called after the shared half has already been re-applied, so {@link #stat} inside an
     * override already sees the upgraded values.
     */
    protected void onUpgraded() {
    }

    /** This ability's current tuning value for {@code key} - the upgraded one once upgraded. */
    protected double stat(String key, double fallback) {
        Double value = stats.get(key);
        return value == null ? fallback : value;
    }

    /** Rounded {@link #stat}, for the many tuning numbers that are whole. */
    protected int statInt(String key, int fallback) {
        return (int) Math.round(stat(key, fallback));
    }

    private void applyUpgradedDefinition() {
        this.stats = Map.copyOf(definition.mergedStats());
        this.description = definition.upgradedDescription();
        this.details = definition.upgradedDetails();
        // An upgrade that flips active <-> passive (Dilation, Dispersion) also needs its own
        // impl to stop refusing casts - PassiveAbility hard-codes that refusal - so this flag
        // drives the snapshot and the UI, not the rules on its own.
        this.isPassive = definition.isUpgradedPassive();
        // Cooldown and range are only touched when the UPGRADE ITSELF names them. The base
        // cannot know which key an impl read its range from (Backtrack uses "range", most use
        // "cast_range", Sprout uses none and is unlimited), so guessing would quietly break
        // the abilities that set a range unrelated to their stats.
        Map<String, Double> overrides = definition.upgrade().statsOrEmpty();
        if (overrides.containsKey("cooldown")) {
            setMaxCooldown(statInt("cooldown", getMaxCooldown()));
        }
        if (overrides.containsKey("cast_range")) {
            setRange(statInt("cast_range", getRange()));
        } else if (overrides.containsKey("range")) {
            setRange(statInt("range", getRange()));
        }
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
