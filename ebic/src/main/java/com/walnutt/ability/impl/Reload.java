package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.PassiveProcEvent;
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
    /** Upgrade: casting an ability stops counting as acting. Walking and swinging still do. */
    private boolean abilitiesAreQuiet;
    private boolean acted;
    private boolean damaged;

    public Reload(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
    }

    /** TurnManager wraps every ability it runs - Move and Attack included - in this event. */
    @Override
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {
        if (event.phase() != AbilityCastEvent.Phase.POST || event.user() != getOwner()) {
            return;
        }
        // Upgraded, only a step or a swing breaks the reload; a cast is quiet enough to work
        // through. Being MOVED by something else is not a step - that never publishes this
        // event in the first place, so it costs nothing to honour.
        if (abilitiesAreQuiet && !(event.ability() instanceof Move) && !(event.ability() instanceof Attack)) {
            return;
        }
        acted = true;
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
            // Cosmetic-only - see PassiveProcEvent's own doc comment for why this isn't a
            // faked AbilityCastEvent.
            state.getEventBus().publish(state, new PassiveProcEvent(self, "Reload"));
            for (Ability ability : self.getAbilities()) {
                if (!ability.isPassive()) {
                    ability.decreaseCooldown(ability.getCurrentCooldown());
                }
            }
        }
        acted = false;
        damaged = false;
    }

    @Override
    protected void onUpgraded() {
        this.abilitiesAreQuiet = true;
    }
}
