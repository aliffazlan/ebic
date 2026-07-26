package com.walnutt.ability.impl;

import com.walnutt.ability.Ability;
import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;

/** Wei - every encounter he's in burns the opponent's cooldowns; more if he lands a hit as attacker. */
public class EnergyBreak extends PassiveAbility {
    private final int cooldownIncrease;
    private final int bonusIncrease;

    public EnergyBreak(AbilityDefinition definition) {
        super(definition.name(), definition.description());
        this.cooldownIncrease = definition.getInt("cooldown_increase", 1);
        this.bonusIncrease = definition.getInt("bonus_increase", 3);
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() == getOwner()) {
            int increase = event.damageEvent().getDamage() > 0 ? bonusIncrease : cooldownIncrease;
            increaseCooldowns(event.defender(), increase);
        } else if (event.defender() == getOwner()) {
            increaseCooldowns(event.attacker(), cooldownIncrease);
        }
    }

    private void increaseCooldowns(Unit unit, int amount) {
        for (Ability ability : unit.getAbilities()) {
            if (!ability.isPassive()) {
                ability.increaseCooldown(amount);
            }
        }
    }
}
