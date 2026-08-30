package com.walnutt.effect.impl;

import com.walnutt.combat.Encounter;
import com.walnutt.combat.EncounterResolver;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.effect.Effect;
import com.walnutt.effect.StatusEffect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Evayne's Cloak and Dagger: she genuinely disappears from the game for the
 * duration - hidden, untargetable, immune, and unable to act - occupying whatever tile
 * she was cast on (possibly stacked with whoever was already standing there; other units can also
 * still walk onto that tile without knowing she's there, since Tile.isWalkable()
 * tolerates HIDDEN occupants). Any enemy that ends up on her exact tile - at cast
 * time or later - is automatically ambushed with a weighted-attribute attack at
 * reduced damage. When the effect naturally expires, she reappears on the nearest
 * fully-free tile rather than staying stacked.
 *
 * Known simplification: the JSON also describes Backstab dealing extra bonus damage
 * while hidden (backstab_bonus) - that would require Backstab to know about a
 * value owned by a different ability instance, which isn't wired up here.
 */
public class CloakEffect extends Effect {
    private static final EncounterResolver RESOLVER = new EncounterResolver();

    private final double damagePenalty;
    /** Upgrade: turns of root + silence a LANDED ambush leaves behind. 0 until upgraded. */
    private int ambushControlDuration;

    public CloakEffect(int duration, double damagePenalty) {
        super("Cloak and Dagger",
            "Evayne vanishes into the shadows - untouchable, but unable to move, attack or cast while "
                + "she is gone. Any enemy caught on the exact tile she cast this on, now or later, is "
                + "ambushed for reduced damage; she reappears on the nearest free tile once it ends.",
            duration);
        this.damagePenalty = damagePenalty;
        this.flags.add(StatusFlag.HIDDEN);
        this.flags.add(StatusFlag.INVULNERABLE);
        // She is out of the game, not merely hard to kill: STUNNED blocks all three action
        // kinds through the existing Unit.isBlockedFrom machinery. The ambush is unaffected -
        // it fires from onMove below, not through her action economy.
        this.flags.add(StatusFlag.STUNNED);
        this.category = EffectCategory.BUFF;
    }

    @Override
    public void onMove(GameState state, PostMoveEvent event) {
        if (getOwner() == null || isExpired()) {
            return;
        }
        Unit mover = event.unit();
        if (mover == getOwner() || mover.isDead() || mover.getTeam() == getOwner().getTeam()) {
            return;
        }
        if (event.to().equals(getOwner().getPosition())) {
            ambushAttack(state, mover);
        }
    }

    public void setAmbushControlDuration(int turns) {
        this.ambushControlDuration = turns;
    }

    public void ambushAttack(GameState state, Unit target) {
        Encounter encounter = new WeightedEncounter(getOwner(), target);
        DamageEvent event = RESOLVER.resolve(state, encounter);
        event.setCauseLabel("Cloak and Dagger");
        event.multiplyDamage(1 - damagePenalty);
        target.takeDamage(state, event);

        // Only an ambush that actually landed pins them; one the victim turned aside does not.
        if (ambushControlDuration > 0 && event.getDamage() > 0 && !target.isDead()) {
            target.addEffect(new StatusEffect("Ambushed", ambushControlDuration,
                StatusFlag.ROOTED, StatusFlag.SILENCED));
        }
    }

    @Override
    public void onExpire(GameState state) {
        Unit owner = getOwner();
        if (owner == null || owner.isDead()) {
            return;
        }
        Position current = owner.getPosition();
        state.getMap().findNearestFreeTile(current, state.getMap().getRadius() * 2)
            .ifPresent(tile -> state.getMap().moveUnit(owner, tile));
    }
}
