package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.effect.impl.BlizzardEffect;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.SummonedUnit;
import com.walnutt.unit.Unit;

/** Snow Golem - a successful attack applies a short (damage-less) Blizzard root to the target. */
public class BlizzardFist extends PassiveAbility {
    private final int duration;

    public BlizzardFist(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.duration = definition.getInt("duration", 1);
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        if (event.attacker() != getOwner() || event.damageEvent().getDamage() <= 0) {
            return;
        }
        Unit defender = event.defender();
        if (defender == null || defender.isDead()) {
            return;
        }
        BlizzardEffect.applyOrExtend(defender, getOwner(), duration, 0, disarms());
    }

    /**
     * Whether the storm this raises disarms, which follows the SUMMONER's Blizzard rather than
     * anything of this ability's own - blizzard_fist.json and snow_blast.json are tagged
     * no_upgrade precisely because they inherit hers.
     *
     * A golem with no summoner (or one whose summoner has lost the ability) simply roots, which
     * is the un-upgraded behaviour.
     */
    private boolean disarms() {
        Unit self = getOwner();
        if (!(self instanceof SummonedUnit summon) || summon.getSummoner() == null) {
            return false;
        }
        return summon.getSummoner().getAbilities().stream()
            .anyMatch(ability -> ability instanceof Blizzard blizzard && blizzard.disarms());
    }
}
