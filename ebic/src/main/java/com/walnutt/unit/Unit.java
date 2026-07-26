package com.walnutt.unit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import com.walnutt.TriggerHandler;
import com.walnutt.ability.Ability;
import com.walnutt.combat.Attribute;
import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.DeathEvent;
import com.walnutt.event.EventBus;
import com.walnutt.event.FatalDamageEvent;
import com.walnutt.event.HealEvent;
import com.walnutt.event.KillEvent;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.game.GameState;
import com.walnutt.game.RemovalReason;
import com.walnutt.game.Team;
import com.walnutt.map.Position;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.Stat;
import com.walnutt.status.StatModifier;
import com.walnutt.status.StatusFlag;
import com.walnutt.status.TickRate;

/**
 * Abstract base for all unit types. Owns data, forwards triggers to its abilities
 * and effects, and does basic coordination - it does not contain gameplay rules
 * itself (those live in the abilities/effects, per the trigger system).
 */
public abstract class Unit {
    private final String name;
    private final Team team;
    private final UnitType unitType;
    private final UnitStats baseStats;
    private final HealthPool healthPool;
    private Position position;
    private final List<Ability> abilities = new ArrayList<>();
    private final List<Effect> effects = new ArrayList<>();
    private final List<StatModifier> permanentModifiers = new ArrayList<>();
    private boolean hasMovedThisTurn;
    private boolean hasAttackedThisTurn;

    protected Unit(String name, Team team, UnitType unitType, UnitStats baseStats) {
        this(name, team, unitType, baseStats, new HealthPool(baseStats.maxHealth()));
    }

    /** Lets a summoned unit share its summoner's HealthPool instance (e.g. Shadow Image). */
    protected Unit(String name, Team team, UnitType unitType, UnitStats baseStats, HealthPool healthPool) {
        this.name = name;
        this.team = team;
        this.unitType = unitType;
        this.baseStats = baseStats;
        this.healthPool = healthPool;
    }

    public String getName() {
        return name;
    }

    public Team getTeam() {
        return team;
    }

    public UnitType getUnitType() {
        return unitType;
    }

    public Position getPosition() {
        return position;
    }

    public void setPosition(Position position) {
        this.position = position;
    }

    public UnitStats getBaseStats() {
        return baseStats;
    }

    public HealthPool getHealthPool() {
        return healthPool;
    }

    public int getHealth() {
        return healthPool.getCurrent();
    }

    public int getMaxHealth() {
        return (int) getEffective(Stat.MAX_HEALTH);
    }

    public boolean isDead() {
        return healthPool.isDepleted();
    }

    public void resetTurnFlags() {
        hasMovedThisTurn = false;
        hasAttackedThisTurn = false;
    }

    public boolean hasMovedThisTurn() {
        return hasMovedThisTurn;
    }

    public boolean hasAttackedThisTurn() {
        return hasAttackedThisTurn;
    }

    public void markMoved() {
        hasMovedThisTurn = true;
    }

    public void markAttacked() {
        hasAttackedThisTurn = true;
    }

    public void addAbility(Ability ability) {
        ability.setOwner(this);
        abilities.add(ability);
    }

    public List<Ability> getAbilities() {
        return Collections.unmodifiableList(abilities);
    }

    public List<Ability> getActiveAbilities() {
        return abilities.stream().filter(a -> !a.isPassive()).toList();
    }

    public void addEffect(Effect effect) {
        effect.setOwner(this);
        effects.add(effect);
    }

    public List<Effect> getEffects() {
        return Collections.unmodifiableList(effects);
    }

    public void removeExpiredEffects(GameState state) {
        Iterator<Effect> it = effects.iterator();
        while (it.hasNext()) {
            Effect effect = it.next();
            if (effect.isExpired()) {
                effect.onExpire(state);
                it.remove();
            }
        }
    }

    /** First active effect of the given type, if any - a typed alternative to scanning getEffects() by hand. */
    public <T extends Effect> Optional<T> getActiveEffect(Class<T> type) {
        for (Effect effect : effects) {
            if (!effect.isExpired() && type.isInstance(effect)) {
                return Optional.of(type.cast(effect));
            }
        }
        return Optional.empty();
    }

    /**
     * Force-expires every currently-active, dispellable DEBUFF (Holy Shield's clear
     * on barrier break). Reuses the normal expiry pathway (onExpire still fires) so
     * a dispelled effect behaves the same as one that ran out naturally.
     */
    public int dispelDebuffs(GameState state) {
        int count = 0;
        for (Effect effect : effects) {
            if (!effect.isExpired() && effect.isDispellable() && effect.getCategory() == EffectCategory.DEBUFF) {
                effect.setRemainingTurns(0);
                count++;
            }
        }
        if (count > 0) {
            removeExpiredEffects(state);
        }
        return count;
    }

    public void addPermanentModifier(StatModifier modifier) {
        permanentModifiers.add(modifier);
        if (modifier.stat() == Stat.MAX_HEALTH) {
            // Keep the HealthPool's actual ceiling in sync with the effective stat,
            // otherwise current health could never rise above the ORIGINAL max even
            // after a permanent bonus (Decay) raises the effective value.
            healthPool.setMax((int) getEffective(Stat.MAX_HEALTH));
        }
    }

    /** Base value layered with permanent modifiers and any active (non-expired) effect modifiers. */
    public double getEffective(Stat stat) {
        double base = switch (stat) {
            case STRENGTH -> baseStats.strength();
            case AGILITY -> baseStats.agility();
            case INTELLIGENCE -> baseStats.intelligence();
            case MAX_HEALTH -> baseStats.maxHealth();
        };

        double flatTotal = 0;
        double percentTotal = 0;
        for (StatModifier modifier : allModifiers()) {
            if (modifier.stat() != stat) {
                continue;
            }
            if (modifier.type() == StatModifier.ModifierType.FLAT) {
                flatTotal += modifier.amount();
            } else {
                percentTotal += modifier.amount();
            }
        }

        return Math.max(0, (base + flatTotal) * (1 + percentTotal));
    }

    public int getAttributeValue(Attribute attribute) {
        Stat stat = switch (attribute) {
            case STRENGTH -> Stat.STRENGTH;
            case AGILITY -> Stat.AGILITY;
            case INTELLIGENCE -> Stat.INTELLIGENCE;
        };
        return (int) getEffective(stat);
    }

    private List<StatModifier> allModifiers() {
        List<StatModifier> all = new ArrayList<>(permanentModifiers);
        for (Effect effect : effects) {
            if (!effect.isExpired()) {
                all.addAll(effect.getStatModifiers());
            }
        }
        return all;
    }

    public boolean hasStatus(StatusFlag flag) {
        for (Effect effect : effects) {
            if (!effect.isExpired() && effect.getStatusFlags().contains(flag)) {
                return true;
            }
        }
        return false;
    }

    public boolean isBlockedFrom(ActionKind kind) {
        for (StatusFlag flag : StatusFlag.values()) {
            if (!hasStatus(flag)) {
                continue;
            }
            boolean blocks = switch (kind) {
                case MOVE -> flag.blocksMovement();
                case ATTACK -> flag.blocksAttack();
                case ABILITY -> flag.blocksAbility();
            };
            if (blocks) {
                return true;
            }
        }
        return false;
    }

    public List<TriggerHandler> getAllTriggerHandlers() {
        List<TriggerHandler> handlers = new ArrayList<>(abilities);
        handlers.addAll(effects);
        return handlers;
    }

    /** Engine bookkeeping only - called directly by TurnManager for the acting player's units. */
    public void startTurn(GameState state) {
        resetTurnFlags();
        if (!hasStatus(StatusFlag.COOLDOWNS_PAUSED)) {
            for (Ability ability : abilities) {
                ability.tick();
            }
        }
        removeExpiredEffects(state);
    }

    /** Engine bookkeeping only - called directly by TurnManager for the acting player's units. */
    public void endTurn(GameState state) {
        boolean dilated = hasStatus(StatusFlag.TIME_DILATED);
        for (Effect effect : effects) {
            TickRate rate = TickRate.NORMAL;
            if (dilated) {
                rate = switch (effect.getCategory()) {
                    case BUFF -> TickRate.FAST;
                    case DEBUFF -> TickRate.SLOW;
                    case NEUTRAL -> TickRate.NORMAL;
                };
            }
            effect.tick(rate);
        }
        removeExpiredEffects(state);
    }

    /**
     * True (default) for ordinary units - false for structures like Zenith's
     * Pylons, which can be attacked but never block movement onto their tile.
     */
    public boolean occupiesTile() {
        return true;
    }

    /**
     * Full damage pipeline: publish mutable DamageEvent (mitigation/redirection),
     * then - if lethal - publish FatalDamageEvent so death-prevention hooks
     * (Objurgation) get a chance to intervene before HP/removal are finalized,
     * then apply, then publish PostDamageEvent (reactive hooks) and, if dead,
     * DeathEvent/KillEvent and check the win condition.
     */
    public void takeDamage(GameState state, DamageEvent event) {
        if (hasStatus(StatusFlag.INVULNERABLE) && !event.isBypassInvulnerability()) {
            return;
        }

        EventBus bus = state.getEventBus();
        bus.publish(state, event);
        if (event.isCancelled()) {
            return;
        }

        int finalDamage = Math.max(0, event.getDamage());
        int newHealth = getHealth() - finalDamage;

        if (newHealth <= 0 && !isDead()) {
            FatalDamageEvent fatal = new FatalDamageEvent(this, event);
            bus.publish(state, fatal);
            if (fatal.isPrevented()) {
                healthPool.setCurrent(fatal.getRevisedHealth());
                bus.publish(state, new PostDamageEvent(this, event));
                return;
            }
        }

        healthPool.setCurrent(Math.max(0, newHealth));
        bus.publish(state, new PostDamageEvent(this, event));

        if (isDead()) {
            bus.publish(state, new DeathEvent(this));
            bus.publish(state, new KillEvent(event.getSource(), this));
            state.removeUnit(this, RemovalReason.DEATH);
            state.checkWinCondition();
        }
    }

    /** Bypasses the normal damage pipeline entirely - for effects like Frostbite's shatter-kill. */
    public void instantKill(GameState state, Unit source) {
        if (isDead()) {
            return;
        }
        healthPool.setCurrent(0);
        EventBus bus = state.getEventBus();
        bus.publish(state, new DeathEvent(this));
        bus.publish(state, new KillEvent(source, this));
        state.removeUnit(this, RemovalReason.DEATH);
        state.checkWinCondition();
    }

    public void heal(GameState state, int amount) {
        if (hasStatus(StatusFlag.IMMUNE_TO_HEALING)) {
            return;
        }
        HealEvent event = new HealEvent(null, this, amount);
        state.getEventBus().publish(state, event);
        if (event.isCancelled()) {
            return;
        }
        healthPool.heal(event.getAmount());
    }
}
