package com.walnutt.ability.impl;

import java.util.List;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.TurnEndEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * The aura carried by each of Branch's Branchlings - damages adjacent enemies, heals
 * adjacent allies, once per round.
 *
 * Driven off a turn event rather than an effect duration on purpose: registered summons
 * never get startTurn/endTurn from the turn loop, so their effects never tick, but the
 * event bus still reaches them. Stacking is free - six Branchlings around one ally each
 * run their own copy of this.
 */
public class BranchlingAura extends PassiveAbility {
    private final int radius;
    private final int branchDamage;
    private final int branchHeal;

    public BranchlingAura(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.radius = definition.getInt("radius", 1);
        this.branchDamage = definition.getInt("branch_dmg", 10);
        this.branchHeal = definition.getInt("branch_heal", 8);
    }

    @Override
    public void onTurnEnd(GameState state, TurnEndEvent event) {
        Unit self = getOwner();
        if (self == null || self.isDead() || self.getPosition() == null) {
            return;
        }
        // Fire at the end of its own controller's turn only, so it lands exactly once
        // per round and after that side has finished moving around it.
        if (event.team() != self.getTeam()) {
            return;
        }
        for (Unit unit : List.copyOf(state.getMap().getUnitsInRadius(self.getPosition(), radius))) {
            if (unit == self || unit.isDead()) {
                continue;
            }
            if (unit.getTeam() == self.getTeam()) {
                if (branchHeal > 0) {
                    unit.heal(state, branchHeal);
                }
            } else if (branchDamage > 0) {
                DamageEvent thorns = new DamageEvent(self, unit, branchDamage);
                thorns.setCauseLabel("Branchling");
                unit.takeDamage(state, thorns);
            }
        }
    }
}
