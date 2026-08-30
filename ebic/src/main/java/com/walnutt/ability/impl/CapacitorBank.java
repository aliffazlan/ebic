package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Attack;
import com.walnutt.ability.Move;
import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.CapacitorChargeEffect;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.TurnStartEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Maxwell gadget - banks a charge every turn, and each charge pays for one ability in
 * place of an action.
 *
 * This is the payoff for how weak Maxwell starts. One charge a turn is a free gadget cast
 * every turn; a full bank is three of them at once, on top of a full slate of move points,
 * so a game that drags on hands him turns nobody else can have.
 *
 * The charge is banked at the START of his turn, unlike Eureka's Inspiration, which is
 * awarded at the end. The two look inconsistent side by side and the difference is
 * deliberate: Inspiration is deferred so the number showing when a turn begins is exactly
 * what can be spent during it, whereas a charge is meant to be spent on the very turn it
 * arrives.
 *
 * Ability.getMoveCost reports 0 while a charge is banked; the charge is consumed here
 * afterwards, on the POST cast. Splitting it that way keeps getMoveCost a pure query, and
 * the two cannot disagree: a charge present when canUse ran is still present at onUse.
 */
public class CapacitorBank extends PassiveAbility {
    private int chargePerTurn;
    private int maxCharges;

    public CapacitorBank(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.chargePerTurn = definition.getInt("charge_per_turn", 1);
        this.maxCharges = definition.getInt("max_charges", 3);
    }

    /** Created on attach, not lazily, so the bank is visible the moment it is constructed. */
    @Override
    protected void onAttached(Unit newOwner) {
        if (newOwner.getActiveEffect(CapacitorChargeEffect.class).isEmpty()) {
            newOwner.addEffect(new CapacitorChargeEffect(
                "Each banked charge pays for one ability in place of an action.", maxCharges));
        }
    }

    private CapacitorChargeEffect bank() {
        return owner == null ? null : owner.getActiveEffect(CapacitorChargeEffect.class).orElse(null);
    }

    @Override
    public void onTurnStart(GameState state, TurnStartEvent event) {
        Unit self = getOwner();
        if (self == null || self.isDead() || event.team() != self.getTeam()) {
            return;
        }
        CapacitorChargeEffect charges = bank();
        if (charges != null) {
            charges.add(chargePerTurn); // clamped at capacity by the effect itself
        }
    }

    /**
     * Move and Attack are excluded because they never draw on a charge in the first place -
     * they override getMoveCost - so spending one on them would silently burn the bank for
     * nothing. Passives are never cast at all.
     */
    @Override
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {
        if (event.phase() != AbilityCastEvent.Phase.POST || event.user() != getOwner()) {
            return;
        }
        Ability cast = event.ability();
        if (cast == null || cast.isPassive() || cast instanceof Move || cast instanceof Attack) {
            return;
        }
        CapacitorChargeEffect charges = bank();
        if (charges != null) {
            charges.spend(1);
        }
    }

    /** Upgrade: banks more per turn and holds more. */
    @Override
    protected void onUpgraded() {
        this.chargePerTurn = statInt("charge_per_turn", chargePerTurn);
        this.maxCharges = statInt("max_charges", maxCharges);
    }
}
