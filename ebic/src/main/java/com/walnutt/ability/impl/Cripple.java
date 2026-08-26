package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;

/** Grivath - deals less damage, but converts the reduction into permanent removal of the defender's chosen attribute. */
public class Cripple extends PassiveAbility {
    private final double damageReduction;
    private final int dmgToStat;

    public Cripple(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.damageReduction = definition.getDouble("damage_reduction", 0.5);
        this.dmgToStat = definition.getInt("dmg_to_stat", 3);
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (event.getSource() != getOwner() || event.getDamage() <= 0) {
            return;
        }
        int reduced = (int) Math.round(event.getDamage() * damageReduction);
        if (reduced <= 0) {
            return;
        }
        event.modifyDamage(-reduced);

        Attribute defended = event.getDefenderAttribute();
        if (defended == null || dmgToStat <= 0) {
            return;
        }
        int statsToRemove = reduced / dmgToStat;
        if (statsToRemove <= 0) {
            return;
        }
        Stat stat = switch (defended) {
            case STRENGTH -> Stat.STRENGTH;
            case AGILITY -> Stat.AGILITY;
            case INTELLIGENCE -> Stat.INTELLIGENCE;
        };
        event.getTarget().addPermanentModifier(StatModifier.flat(stat, -statsToRemove, this));
    }
}
