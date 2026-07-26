package com.walnutt.ability;

import com.walnutt.TriggerHandler;
import com.walnutt.ability.target.Target;
import com.walnutt.game.GameState;
import com.walnutt.unit.ActionKind;
import com.walnutt.unit.Unit;

public abstract class Ability extends TriggerHandler {

    private final String name;
    private final String description;
    private final boolean isPassive;
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

    public Unit getOwner() {
        return this.owner;
    }

    public void setOwner(Unit unit) {
        this.owner = unit;
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

    public int getRange() {
        return range;
    }

    public void setRange(int range) {
        this.range = range;
    }

    public int getMoveCost(GameState state) {
        return isPassive ? 0 : 1;
    }

    /**
     * Baseline economy/readiness checks shared by every active ability. Subclasses
     * should call {@code super.canUse(state, target)} and AND it with their own
     * target-shape/range checks.
     */
    public boolean canUse(GameState state, Target target) {
        return !isPassive && isReady() && owner != null && !owner.isBlockedFrom(ActionKind.ABILITY);
    }

    public abstract void onUse(GameState state, Target target);
}
