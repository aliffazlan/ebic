package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BarrierEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Tile;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;

/** Zenith - teleports to (and destroys) one of its own pylons, refunds cooldowns, and gains a barrier. */
public class Dislocation extends Ability {
    private int barrierHp;
    private int barrierDuration;
    /** Upgrade: damage drawn out of the pylon instead of consuming it. 0 until upgraded. */
    private int pylonDamage;

    public Dislocation(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription(), false);
        setMaxCooldown(definition.getInt("cooldown", 1));
        this.barrierHp = definition.getInt("barrier_hp", 50);
        this.barrierDuration = definition.getInt("barrier_duration", 2);
    }

    @Override
    public boolean canUse(GameState state, Target target) {
        if (!super.canUse(state, target)) {
            return false;
        }
        if (!(target instanceof UnitTarget unitTarget)) {
            return false;
        }
        Unit pylon = unitTarget.getUnit();
        if (pylon.isDead() || !(pylon instanceof SummonedUnit summoned) || summoned.getSummoner() != owner) {
            return false;
        }
        Tile tile = state.getMap().getTile(pylon.getPosition());
        if (tile == null) {
            return false;
        }
        for (Unit occupant : tile.getOccupants()) {
            if (occupant != pylon) {
                return false; // "cannot teleport to a pylon with another unit on the same tile"
            }
        }
        return true;
    }

    @Override
    public void onUse(GameState state, Target target) {
        Unit pylon = ((UnitTarget) target).getUnit();
        Tile destination = state.getMap().getTile(pylon.getPosition());

        // Upgraded, the draw costs the pylon health rather than its life - but it detonates
        // either way, so the burst is fired by hand when the pylon survives it.
        if (pylonDamage > 0) {
            DamageEvent draw = new DamageEvent(owner, pylon, pylonDamage);
            draw.setCauseLabel("Dislocation");
            pylon.takeDamage(state, draw);
            if (!pylon.isDead()) {
                for (Ability ability : pylon.getAbilities()) {
                    if (ability instanceof PylonDeathBurst burst) {
                        burst.detonate(state);
                    }
                }
            }
        } else {
            pylon.instantKill(state, owner);
        }
        state.getMap().moveUnit(owner, destination);

        for (Ability ability : owner.getAbilities()) {
            if (!ability.isPassive()) {
                ability.decreaseCooldown(1);
            }
        }
        owner.addEffect(new BarrierEffect("Dislocation Barrier",
            "Absorbs up to " + barrierHp + " damage before it reaches Zenith's health, gained after "
                + "teleporting to and destroying a pylon.",
            barrierDuration, barrierHp));

        state.spendMoves(getMoveCost(state));
        resetToMax();
    }

    @Override
    protected void onUpgraded() {
        this.barrierHp = statInt("barrier_hp", barrierHp);
        this.barrierDuration = statInt("barrier_duration", barrierDuration);
        this.pylonDamage = statInt("pylon_damage", 0);
    }
}
