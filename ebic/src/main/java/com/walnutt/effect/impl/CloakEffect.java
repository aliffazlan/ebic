package com.walnutt.effect.impl;

import com.walnutt.combat.Encounter;
import com.walnutt.combat.EncounterResolver;
import com.walnutt.combat.WeightedEncounter;
import com.walnutt.effect.Effect;
import com.walnutt.event.DamageEvent;
import com.walnutt.event.PostMoveEvent;
import com.walnutt.game.GameState;
import com.walnutt.map.Position;
import com.walnutt.status.EffectCategory;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.Unit;

/**
 * Evayne's Cloak and Dagger: she effectively disappears from the game for the
 * duration - hidden AND invulnerable, occupying whatever tile she was cast on
 * (possibly stacked with whoever was already standing there; other units can also
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

    public CloakEffect(int duration, double damagePenalty) {
        super("Cloak and Dagger", duration);
        this.damagePenalty = damagePenalty;
        this.flags.add(StatusFlag.HIDDEN);
        this.flags.add(StatusFlag.INVULNERABLE);
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

    public void ambushAttack(GameState state, Unit target) {
        Encounter encounter = new WeightedEncounter(getOwner(), target);
        DamageEvent event = RESOLVER.resolve(state, encounter);
        event.multiplyDamage(1 - damagePenalty);
        target.takeDamage(state, event);
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
