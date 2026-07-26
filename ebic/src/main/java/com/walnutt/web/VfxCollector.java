package com.walnutt.web;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import com.walnutt.TriggerHandler;
import com.walnutt.event.AbilityCastEvent;
import com.walnutt.event.DeathEvent;
import com.walnutt.event.HealEvent;
import com.walnutt.event.PostDamageEvent;
import com.walnutt.event.StatusAppliedEvent;
import com.walnutt.game.GameState;
import com.walnutt.unit.Unit;
import com.walnutt.web.dto.VfxEvent;

/**
 * Registered as a GameState.getEventBus() global listener (see EventBus's
 * addGlobalListener) for exactly one match. Turns onAbilityUsed/onDamageTaken/
 * onDeath/onStatusApplied/onHeal into VfxEvent DTOs and buffers them - WebRenderer
 * drains the buffer and sends it as a "vfx" message immediately before the next
 * "state" push, per API_CONTRACT.md. Runs entirely on the single dedicated game
 * thread (EventBus.publish is only ever called from there), so a plain ArrayList
 * is safe with no extra synchronization.
 */
public final class VfxCollector extends TriggerHandler {
    private final UnitIdRegistry ids;
    private final List<VfxEvent> buffered = new ArrayList<>();

    public VfxCollector(UnitIdRegistry ids) {
        this.ids = ids;
    }

    /** Only fire on the POST phase - PRE is before the ability has actually done anything yet. */
    @Override
    public void onAbilityUsed(GameState state, AbilityCastEvent event) {
        if (event.phase() != AbilityCastEvent.Phase.POST) {
            return;
        }
        String targetId = switch (event.target()) {
            case com.walnutt.ability.target.UnitTarget unitTarget -> ids.idFor(unitTarget.getUnit());
            default -> null;
        };
        buffered.add(new VfxEvent(
            "ability_used",
            Identifiers.normalize(event.ability().getName()),
            event.user() == null ? null : ids.idFor(event.user()),
            targetId,
            null
        ));
    }

    @Override
    public void onDamageTaken(GameState state, PostDamageEvent event) {
        Unit source = event.damageEvent().getSource();
        buffered.add(new VfxEvent(
            "damage",
            null,
            source == null ? null : ids.idFor(source),
            ids.idFor(event.target()),
            event.damageEvent().getDamage()
        ));
    }

    @Override
    public void onDeath(GameState state, DeathEvent event) {
        buffered.add(new VfxEvent("death", null, null, ids.idFor(event.unit()), null));
    }

    @Override
    public void onStatusApplied(GameState state, StatusAppliedEvent event) {
        String sourceId = event.source() instanceof Unit sourceUnit ? ids.idFor(sourceUnit) : null;
        buffered.add(new VfxEvent("status_applied", null, sourceId, ids.idFor(event.unit()), null));
    }

    @Override
    public void onHeal(GameState state, HealEvent event) {
        buffered.add(new VfxEvent(
            "heal",
            null,
            event.getSource() == null ? null : ids.idFor(event.getSource()),
            ids.idFor(event.getTarget()),
            event.getAmount()
        ));
    }

    /** Returns and clears the buffered events since the last drain. */
    public List<VfxEvent> drain() {
        if (buffered.isEmpty()) {
            return List.of();
        }
        List<VfxEvent> copy = new ArrayList<>(buffered);
        buffered.clear();
        return Collections.unmodifiableList(copy);
    }
}
