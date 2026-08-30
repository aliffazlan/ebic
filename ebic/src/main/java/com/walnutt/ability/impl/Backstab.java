package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.game.GameState;

/** Evayne - "Attacks deal bonus damage per point of agility." */
public class Backstab extends PassiveAbility {
    private double damageBonusPerAgility;
    /** Upgrade: the share of the bonus a DEFENDED attack still delivers. 0 until upgraded. */
    private double failMultiplier;

    public Backstab(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.damageBonusPerAgility = definition.getDouble("dmg_bonus", 0.4);
    }

    @Override
    protected void onUpgraded() {
        this.damageBonusPerAgility = stat("dmg_bonus", damageBonusPerAgility);
        this.failMultiplier = stat("fail_multiplier", 0);
    }

    @Override
    public void onIncomingDamage(GameState state, DamageEvent event) {
        if (event.getSource() != getOwner()) {
            return;
        }
        double bonus = damageBonusPerAgility * getOwner().getAttributeValue(Attribute.AGILITY);

        // A defended attack used to carry the whole bonus, which quietly made a failed swing
        // one of Evayne's better ones and left the upgrade below with nothing to give. It now
        // carries none of it - and, upgraded, a share.
        if (wasDefended(event)) {
            bonus *= failMultiplier;
        }
        int rounded = (int) Math.round(bonus);
        if (rounded != 0) {
            event.modifyDamage(rounded);
        }
    }

    /**
     * True when this damage came from an attack the defender won outright. Read from the
     * matchup rather than from the damage number, the same test Cripple uses: a mirrored
     * matchup can deal 0 without being a block.
     */
    private static boolean wasDefended(DamageEvent event) {
        Attribute attacker = event.getAttackerAttribute();
        Attribute defender = event.getDefenderAttribute();
        return attacker != null && defender != null && defender.beats(attacker);
    }
}
