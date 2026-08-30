package com.walnutt.ability.impl;

import com.walnutt.ability.PassiveAbility;
import com.walnutt.combat.Attribute;
import com.walnutt.data.AbilityDefinition;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostAttackEvent;
import com.walnutt.game.GameState;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/**
 * Grivath - attacks drain the target rather than merely damaging it. A landed attack
 * transfers a point of every attribute (plus a bonus on whichever attribute the target
 * defended with) and a chunk of max/current HP from the target to Grivath; a defended
 * attack still shaves a single point off the attribute that blocked it.
 *
 * Hooks onPostAttack rather than onIncomingDamage: this no longer modifies damage, and
 * onPostAttack is both scoped to real attacks and handed the attribute matchup directly.
 * It also means the HP transfer below can safely go through takeDamage - onPostAttack is
 * published only by CombatEngine.performAttack, so a plain DamageEvent cannot re-enter
 * this hook.
 */
public class Cripple extends PassiveAbility {
    private int statSteal;
    private int defendedBonus;
    private int hpSteal;
    private double nonBasicMultiplier;

    public Cripple(AbilityDefinition definition) {
        super(definition.name(), definition.formattedDescription());
        this.statSteal = definition.getInt("stat_steal", 1);
        this.defendedBonus = definition.getInt("defended_bonus", 3);
        this.hpSteal = definition.getInt("hp_steal", 5);
        this.nonBasicMultiplier = definition.getDouble("non_basic_multiplier", 2);
    }

    @Override
    public void onPostAttack(GameState state, PostAttackEvent event) {
        Unit grivath = getOwner();
        if (grivath == null || grivath.isDead() || event.attacker() != grivath) {
            return;
        }
        Unit victim = event.defender();
        if (victim == null || victim.isDead()) {
            return;
        }

        DamageEvent damage = event.damageEvent();
        Attribute attackerAttribute = damage.getAttackerAttribute();
        Attribute defendedAttribute = damage.getDefenderAttribute();
        if (attackerAttribute == null || defendedAttribute == null) {
            return;
        }

        // Doubled against anything that isn't a generic Basic - the same "was this a real
        // unit" test DuelEffect uses for its win bonus.
        double multiplier = victim.getUnitType() == UnitType.BASIC ? 1.0 : nonBasicMultiplier;

        // A "successful defense" is specifically the defender's attribute beating the
        // attacker's. A mirrored matchup can deal 0 damage without being a block, so this
        // reads the matchup rather than the damage number.
        // Upgraded, a block is worth exactly as much as a hit - so the branch below, which
        // shaves a single point and drains no health, simply stops applying.
        boolean defended = defendedAttribute.beats(attackerAttribute) && !isUpgraded();
        if (defended) {
            transferStat(grivath, victim, toStat(defendedAttribute), scale(statSteal, multiplier));
            return;
        }

        for (Attribute attribute : Attribute.values()) {
            int amount = statSteal + (attribute == defendedAttribute ? defendedBonus : 0);
            transferStat(grivath, victim, toStat(attribute), scale(amount, multiplier));
        }
        transferHealth(state, grivath, victim, scale(hpSteal, multiplier));
    }

    private int scale(int amount, double multiplier) {
        return (int) Math.round(amount * multiplier);
    }

    /** Moves `amount` points of one stat off the victim and onto Grivath, permanently. */
    private void transferStat(Unit grivath, Unit victim, Stat stat, int amount) {
        if (amount <= 0) {
            return;
        }
        victim.addPermanentModifier(StatModifier.flat(stat, -amount, this));
        grivath.addPermanentModifier(StatModifier.flat(stat, amount, this));
    }

    /**
     * Moves both max and current HP. Order matters on each side: the victim takes the
     * damage BEFORE its ceiling drops, so the drop doesn't additionally clamp current and
     * charge it twice; Grivath's ceiling rises BEFORE he heals, since HealthPool.setMax
     * never raises current on its own.
     */
    private void transferHealth(GameState state, Unit grivath, Unit victim, int amount) {
        if (amount <= 0) {
            return;
        }
        DamageEvent drain = new DamageEvent(grivath, victim, amount);
        drain.setCauseLabel("Cripple");
        victim.takeDamage(state, drain);
        victim.addPermanentModifier(StatModifier.flat(Stat.MAX_HEALTH, -amount, this));

        grivath.addPermanentModifier(StatModifier.flat(Stat.MAX_HEALTH, amount, this));
        grivath.heal(state, amount);
    }

    private static Stat toStat(Attribute attribute) {
        return switch (attribute) {
            case STRENGTH -> Stat.STRENGTH;
            case AGILITY -> Stat.AGILITY;
            case INTELLIGENCE -> Stat.INTELLIGENCE;
        };
    }

    @Override
    protected void onUpgraded() {
        this.statSteal = statInt("stat_steal", statSteal);
        this.defendedBonus = statInt("defended_bonus", defendedBonus);
        this.hpSteal = statInt("hp_steal", hpSteal);
        this.nonBasicMultiplier = stat("non_basic_multiplier", nonBasicMultiplier);
    }
}
