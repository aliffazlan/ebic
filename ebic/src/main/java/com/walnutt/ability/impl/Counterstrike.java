package com.walnutt.ability.impl;

import com.walnutt.ability.Attack;
import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.combat.Encounter;
import com.walnutt.combat.EncounterResolver;
import com.walnutt.combat.NormalEncounter;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/**
 * Valor - free counter-attack on being hit, at reduced damage but with lifesteal.
 *
 * Hooks onPostAttack (not the generic damage-taken hook) so this only reacts to
 * actual attacks, not incidental damage (poison, Soul Rip, etc). The counter is
 * applied directly via defender.takeDamage rather than CombatEngine.performAttack,
 * so it does not re-publish PreAttackEvent/PostAttackEvent - this is what stops a
 * mirror matchup from counter-countering each other forever.
 */
public class Counterstrike extends PassiveAbility {
    private static final EncounterResolver RESOLVER = new EncounterResolver();

    private double damageReduction;
    private double lifesteal;
    /** Upgrade: replaces the reduction outright rather than offsetting it. 0 until upgraded. */
    private double damageBonus;

    public Counterstrike(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.damageReduction = definition.getDouble("damage_reduction", 0.5);
        this.lifesteal = definition.getDouble("lifesteal", 1.0);
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
        counterDamage.multiplyDamage(damageBonus > 0 ? 1 + damageBonus : 1 - damageReduction);

        int finalDamage = counterDamage.getDamage();
        attacker.takeDamage(state, counterDamage);

        int healed = (int) Math.round(finalDamage * lifesteal);
        if (healed > 0) {
            getOwner().heal(state, healed);
        }
    }

    @Override
    protected void onUpgraded() {
        this.damageReduction = stat("damage_reduction", damageReduction);
        this.lifesteal = stat("lifesteal", lifesteal);
        this.damageBonus = stat("damage_bonus", 0);
    }
}
