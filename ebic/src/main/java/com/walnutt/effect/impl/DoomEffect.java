package com.walnutt.effect.impl;

import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.KillEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Lucifer's Doom curse: silences the target and deals escalating damage at the
 * start of every one of its own turns. Has no natural duration (Effect.PERMANENT)
 * - the only way out is landing a kill, checked via onKill against the owner itself.
 */
public class DoomEffect extends Effect {
    private final Unit source;
    private final int damageIncrease;
    /** Upgrade: the share of each tick that spills onto enemies beside the host. 0 when not upgraded. */
    private final double splashFraction;
    private int currentDamage;

    public DoomEffect(Unit source, int baseDamage, int damageIncrease) {
        this(source, baseDamage, damageIncrease, 0);
    }

    public DoomEffect(Unit source, int baseDamage, int damageIncrease, double splashFraction) {
        super("Doom",
            "A curse that silences its target and deals " + baseDamage + " damage at the start of each "
                + "of its turns, increasing by " + damageIncrease + " every turn. Lasts until the "
                + "cursed unit lands a kill.",
            Effect.PERMANENT);
        this.source = source;
        this.currentDamage = baseDamage;
        this.damageIncrease = damageIncrease;
        this.splashFraction = splashFraction;
        this.flags.add(StatusFlag.SILENCED);
        this.category = EffectCategory.DEBUFF;
    }

    public int getCurrentDamage() {
        return currentDamage;
    }

    @Override
    public String getExtraInfo() {
        return "Next hit: " + currentDamage + " damage";
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit owner = getOwner();
        if (owner == null || isExpired() || owner.isDead() || event.team() != owner.getTeam()) {
            return;
        }
        DamageEvent damageEvent = new DamageEvent(source, owner, currentDamage);
        damageEvent.setCauseLabel("Doom");
        owner.takeDamage(state, damageEvent);

        // Splashed from the tick's nominal damage rather than what actually landed, so a
        // barrier on the cursed unit shields them without also shielding their neighbours.
        int splash = (int) Math.round(currentDamage * splashFraction);
        if (splash > 0) {
            for (Unit neighbour : state.getMap().getUnitsInRadius(owner.getPosition(), 1)) {
                // Enemies of the CASTER, not of the host. The host's own neighbours are exactly
                // who this is meant to catch - comparing against the host's team would spare
                // them and hit Lucifer's own side instead.
                if (neighbour == owner || neighbour.isDead() || neighbour.getTeam() == source.getTeam()) {
                    continue;
                }
                DamageEvent spill = new DamageEvent(source, neighbour, splash);
                spill.setCauseLabel("Doom");
                neighbour.takeDamage(state, spill);
            }
        }
        currentDamage += damageIncrease;
    }

    @Override
    public void onKill(GameState state, KillEvent event) {
        if (getOwner() != null && event.killer() == getOwner()) {
            // Remove now rather than leaving this visible to the frontend until the owner's
            // next scheduled sweep - a kill can land on either team's turn.
            expireNow(state);
        }
    }
}
