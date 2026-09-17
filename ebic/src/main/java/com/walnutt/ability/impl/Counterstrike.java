package com.walnutt.ability.impl;

import com.walnutt.ability.Attack;
import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.Encounter;
import com.walnutt.combat.EncounterResolver;
import com.walnutt.combat.NormalEncounter;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.CounterstrikeBarrierEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Valor - free counter-attack on being hit, at reduced damage but granting a barrier
 * off the damage it deals.
 *
 * Hooks onPostAttack (not the generic damage-taken hook) so this only reacts to
 * actual attacks, not incidental damage (poison, Soul Rip, etc). The counter is
 * applied directly via defender.takeDamage rather than CombatEngine.performAttack,
 * so it does not re-publish PreAttackEvent/PostAttackEvent - this is what stops a
 * mirror matchup from counter-countering each other forever.
 */
public class Counterstrike extends PassiveAbility {
    private static final EncounterResolver RESOLVER = new EncounterResolver();

    private double damageMultiplier;
    private double lifesteal;
    private int barrierDuration;
    /** Upgrade: replaces the multiplier outright rather than offsetting it. 0 until upgraded. */
    private double damageBonus;

    public Counterstrike(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.damageMultiplier = definition.getDouble("damage_multiplier", 0.5);
        this.lifesteal = definition.getDouble("lifesteal", 1.0);
        this.barrierDuration = definition.getInt("barrier_duration", 3);
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        // One counter per attack cast. A chain (Timeless Strike) republishes a
        // PostAttackEvent per hit, which would otherwise buy a free counter for each.
        if (event.chained()) {
            return;
        }
        if (event.defender() != getOwner() || getOwner().isDead()) {
            return;
        }
        Unit attacker = event.attacker();
        if (attacker == null || attacker.isDead() || attacker == getOwner()) {
            return;
        }
        // "You counter if you could have attacked them" - follows this unit's own attack
        // range rather than assuming melee, so a ranged counter-attacker works correctly.
        if (!Attack.canReach(state, getOwner(), attacker)) {
            return;
        }

        Attribute counterAttackerAttribute = event.damageEvent().getDefenderAttribute();
        Attribute counterDefenderAttribute = event.damageEvent().getAttackerAttribute();
        if (counterAttackerAttribute == null || counterDefenderAttribute == null) {
            return;
        }

        Encounter counter = new NormalEncounter(getOwner(), attacker, counterAttackerAttribute, counterDefenderAttribute);
        DamageEvent counterDamage = RESOLVER.resolve(state, counter);
        counterDamage.setCauseLabel("Counterstrike");
        // Upgraded, the counter hits HARDER than the swing that provoked it, so the two are
        // alternatives rather than a sum - applying both would leave it at 96% of normal.
        counterDamage.multiplyDamage(damageBonus > 0 ? 1 + damageBonus : damageMultiplier);

        int finalDamage = counterDamage.getDamage();
        attacker.takeDamage(state, counterDamage);

        int barrierAmount = (int) Math.round(finalDamage * lifesteal);
        if (barrierAmount > 0) {
            getOwner().getActiveEffect(CounterstrikeBarrierEffect.class).ifPresentOrElse(
                existing -> {
                    existing.addBarrier(barrierAmount);
                    existing.setRemainingTurns(barrierDuration);
                },
                () -> getOwner().addEffect(new CounterstrikeBarrierEffect(barrierAmount, barrierDuration)));
        }
    }

    @Override
    protected void onUpgraded() {
        this.damageMultiplier = stat("damage_multiplier", damageMultiplier);
        this.lifesteal = stat("lifesteal", lifesteal);
        this.barrierDuration = statInt("barrier_duration", barrierDuration);
        this.damageBonus = stat("damage_bonus", 0);
    }
}
