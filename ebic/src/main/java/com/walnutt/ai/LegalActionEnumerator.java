package com.walnutt.ai;

import java.util.ArrayList;
import java.util.List;

import com.walnutt.ability.Ability;
import com.walnutt.ability.target.Target;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.unit.Unit;

/**
 * Every action a player could legally take right now, as {@link Candidate}s.
 *
 * This deliberately owns no rules of its own: legality comes entirely from each
 * ability's own {@link Ability#getLegalTargets}, which brute-forces every unit/tile/
 * no-target candidate through that ability's own {@code canUse}. That is what makes
 * the bot automatically able to play abilities that don't exist yet - a new ability
 * becomes legally playable by the bot the day it is registered, with no bot change.
 *
 * The web bridge's target highlighting enumerates the same way (see
 * WebInputHandler.buildLegalTargets), so what the bot considers and what a human is
 * offered cannot drift apart.
 */
public final class LegalActionEnumerator {
    private LegalActionEnumerator() {
    }

    public static List<Candidate> enumerate(GameState state, Player player) {
        List<Candidate> candidates = new ArrayList<>();
        // Snapshot: scoring never mutates, but a summon expiring elsewhere could,
        // and a ConcurrentModificationException here would abort the whole match.
        for (Unit unit : List.copyOf(player.getUnits())) {
            if (unit.isDead()) {
                continue;
            }
            for (Ability ability : unit.getActiveAbilities()) {
                if (!ability.isReady()) {
                    continue;
                }
                for (Target target : ability.getLegalTargets(state)) {
                    candidates.add(new Candidate(unit, ability, target));
                }
            }
        }
        return candidates;
    }
}
