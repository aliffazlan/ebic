package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Maxwell gadget - a quiet round refreshes every cooldown.
 *
 * "Took damage" is read off PostDamageEvent, which carries the damage AFTER mitigation:
 * BarrierEffect has already subtracted what it absorbed during the earlier
 * onIncomingDamage phase, so a hit a barrier ate entirely arrives here as 0 and doesn't
 * count - which is the intended rule, with no barrier-specific code. Damage refused
 * outright by INVULNERABLE publishes no events at all, so it likewise doesn't count.
 */
public class Reload extends PassiveAbility {
    private boolean acted;
    private boolean damaged;

    public Reload(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
    }

    /** TurnManager wraps every ability it runs - Move and Attack included - in this event. */
    @Override
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {
        if (event.phase() == AbilityCastEvent.Phase.POST && event.user() == getOwner()) {
            acted = true;
        }
    }

    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        if (event.target() == getOwner() && event.damageEvent().getDamage() > 0) {
            damaged = true;
        }
    }

    /**
     * Unit.startTurn ticks every cooldown down for the whole roster before TurnManager
     * publishes TurnStartEvent, so this necessarily lands last and isn't undone.
     */
    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit self = getOwner();
        if (self == null || event.team() != self.getTeam()) {
            return;
        }
        if (!acted && !damaged && !self.isDead()) {
            for (Ability ability : self.getAbilities()) {
                if (!ability.isPassive()) {
                    ability.decreaseCooldown(ability.getCurrentCooldown());
                }
            }
        }
        acted = false;
        damaged = false;
    }
}
