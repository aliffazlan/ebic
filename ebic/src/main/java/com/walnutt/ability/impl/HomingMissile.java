package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.Effect;
import com.walnutt.effect.impl.HomingMissileEffect;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Maxwell gadget - a long-range missile that lands a turn later, wherever its target has gone. */
public class HomingMissile extends Ability {
    private int delay;
    private int damage;
    private int aoeDamage;
    /** The upgrade's second, lighter missile. All 0 until upgraded. */
    private int secondMissileDamage;
    private int secondMissileDelay;
    private int secondMissileStun;

    public HomingMissile(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 6));
        setRange(definition.getInt("cast_range", 8));
        this.delay = Math.max(1, definition.getInt("delay", 1));
        this.damage = definition.getInt("damage", 70);
        this.aoeDamage = definition.getInt("aoe_damage", 20);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target) || !(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit enemy = unitTarget.getUnit();
        return !enemy.isDead() && enemy.getTeam() != owner.getTeam() && isInRange(state, enemy.getPosition());
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit enemy = ((UnitTarget) target).getUnit();

        // A second lock on an already-tracked target refreshes rather than stacking - two
        // missiles chasing one unit is not what the ability describes, and the 6-turn
        // cooldown makes it a rare case anyway.
        refreshOrLock(enemy, "Missile Lock", delay, damage, aoeDamage, 0);
        // Upgraded, a second and much lighter missile is launched with it, arriving a turn ahead
        // of the warhead. It plays no favourites - the locked target takes exactly what its
        // neighbours take - which is why the same number is passed for both.
        if (secondMissileDamage > 0) {
            refreshOrLock(enemy, "Missile Lock (stunning)", secondMissileDelay,
                secondMissileDamage, secondMissileDamage, secondMissileStun);
        }

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.delay = Math.max(1, statInt("delay", delay));
        this.damage = statInt("damage", damage);
        this.aoeDamage = statInt("aoe_damage", aoeDamage);
        this.secondMissileDamage = statInt("second_missile_damage", 0);
        this.secondMissileDelay = Math.max(1, statInt("second_missile_delay", 1));
        this.secondMissileStun = statInt("second_missile_stun", 0);
    }

    /**
     * One lock, matched by NAME rather than by type: upgraded, two HomingMissileEffects sit on
     * the same victim with different delays, and getActiveEffect would only ever find the first
     * of them. A repeat cast refreshes each in place rather than stacking a third.
     */
    private void refreshOrLock(Unit enemy, String name, int lockDelay, int impact, int splash, int stun) {
        for (Effect effect : enemy.getEffects()) {
            if (effect instanceof HomingMissileEffect && name.equals(effect.getName())
                && !effect.isExpired()) {
                effect.setRemainingTurns(lockDelay);
                return;
            }
        }
        enemy.addEffect(new HomingMissileEffect(owner, lockDelay, impact, splash, stun));
    }
}
