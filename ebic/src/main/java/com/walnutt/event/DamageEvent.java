package com.walnutt.event;

import java.util.ArrayList;
import java.util.List;
import com.walnutt.combat.Attribute;
import com.walnutt.unit.Unit;

/**
 * Mutable: mitigation/redirection listeners (Backstab, Dispersion, Refraction, ...)
 * modify this in place before it is applied to the target's HealthPool.
 */
public final class DamageEvent implements GameEvent, Cancellable {
    private final Unit source;
    private final Unit target;
    private int damage;
    private boolean cancelled;
    private Attribute attackerAttribute;
    private Attribute defenderAttribute;
    private boolean bypassInvulnerability;
    private final List<String> logMessages = new ArrayList<>();
    private String causeLabel;

    public DamageEvent(Unit source, Unit target, int damage) {
        this.source = source;
        this.target = target;
        this.damage = damage;
    }

    public Unit getSource() {
        return source;
    }

    public Unit getTarget() {
        return target;
    }

    public int getDamage() {
        return damage;
    }

    public void setDamage(int damage) {
        this.damage = damage;
    }

    public void modifyDamage(int amount) {
        this.damage += amount;
    }

    public void multiplyDamage(double multiplier) {
        this.damage = (int) Math.round(this.damage * multiplier);
    }

    public Attribute getAttackerAttribute() {
        return attackerAttribute;
    }

    public void setAttackerAttribute(Attribute attackerAttribute) {
        this.attackerAttribute = attackerAttribute;
    }

    public Attribute getDefenderAttribute() {
        return defenderAttribute;
    }

    public void setDefenderAttribute(Attribute defenderAttribute) {
        this.defenderAttribute = defenderAttribute;
    }

    public boolean isBypassInvulnerability() {
        return bypassInvulnerability;
    }

    /** Lets a source (Sanity's Eclipse, Cold Embrace/Frostbite's own DOT) punch through INVULNERABLE. */
    public void setBypassInvulnerability(boolean bypassInvulnerability) {
        this.bypassInvulnerability = bypassInvulnerability;
    }

    /** Human-readable source of this damage (e.g. "Attack", "Poison", "Counterstrike") for the combat log. Null if unset. */
    public String getCauseLabel() {
        return causeLabel;
    }

    public void setCauseLabel(String causeLabel) {
        this.causeLabel = causeLabel;
    }

    public List<String> getLogMessages() {
        return logMessages;
    }

    public void log(String message) {
        logMessages.add(message);
    }

    @Override
    public boolean isCancelled() {
        return cancelled;
    }

    @Override
    public void cancel() {
        this.cancelled = true;
    }
}
