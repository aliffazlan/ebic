package com.walnutt.ai;

import com.walnutt.ability.Attack;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.status.Stat;
import com.walnutt.status.StatusFlag;
import com.walnutt.unit.ActionKind;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitType;

/**
 * "How good is this position for me?", as a single number - positive favours the
 * given team.
 *
 * This is the durable half of the bot. The way the bot *chooses* is expected to be
 * replaced (greedy now, search later - see {@link BotStrategy}), but what counts as a
 * good position does not change when the search does, so this class is where the real
 * game understanding accumulates. A future search implementation calls exactly this at
 * its leaves.
 *
 * Everything here reads runtime observables - effective stats, current health, status
 * flags, live distances - and never hardcodes per-hero knowledge, so a balance pass
 * that changes unit numbers does not invalidate it.
 */
public final class PositionEvaluator {
    /** Dominates every other term: the match is over, nothing else is worth weighing. */
    public static final double WIN = 1_000_000;

    private final BotConfig config;

    public PositionEvaluator(BotConfig config) {
        this.config = config;
    }

    public double evaluate(GameState state, Team team) {
        Player self = state.getPlayer(team);
        Player enemy = opponentOf(state, team);

        if (enemy.isDefeated()) {
            return WIN;
        }
        if (self.isDefeated()) {
            return -WIN;
        }

        return (material(self) - material(enemy)) + positioning(state, self, enemy);
    }

    /** Total worth of a player's surviving army. */
    private double material(Player player) {
        double total = 0;
        for (Unit unit : player.getUnits()) {
            if (!unit.isDead()) {
                total += unitValue(unit);
            }
        }
        return total;
    }

    /**
     * What one unit is worth, in the same "expected damage" currency the rest of the
     * weights use. Health carries most of it - a unit's contribution is roughly how
     * long it survives - scaled by how much its type matters. The champion's weight is
     * high because losing it loses the match outright, so trading it for anything else
     * is a mistake regardless of health totals.
     */
    public double unitValue(Unit unit) {
        return unit.getHealth() * typeWeight(unit.getUnitType());
    }

    private double typeWeight(UnitType type) {
        return switch (type) {
            case CHAMPION -> config.championTargetWeight();
            case ELITE -> config.eliteTargetWeight();
            case BASIC -> config.basicTargetWeight();
        };
    }

    /**
     * How dangerous a unit is right now: what it could hit for, discounted if something
     * is stopping it from acting. Used to decide which enemy is worth removing first -
     * a disarmed or frozen unit is not a present problem however large its stats.
     */
    public double threat(Unit unit) {
        if (unit.isDead()) {
            return 0;
        }
        double best = Math.max(unit.getEffective(Stat.STRENGTH),
            Math.max(unit.getEffective(Stat.AGILITY), unit.getEffective(Stat.INTELLIGENCE)));
        if (unit.isBlockedFrom(ActionKind.ATTACK)) {
            best *= 0.2;
        }
        if (unit.hasStatus(StatusFlag.INVULNERABLE)) {
            best *= 1.5;
        }
        return best;
    }

    /**
     * Small positional term: reward closing on the enemy champion and having units in
     * position to actually swing. Deliberately kept an order of magnitude below the
     * material term - position is a tiebreaker between otherwise equal armies, and a
     * bot that traded units for ground would be playing badly.
     */
    private double positioning(GameState state, Player self, Player enemy) {
        Unit enemyChampion = enemy.getChampion().orElse(null);
        if (enemyChampion == null || enemyChampion.getPosition() == null) {
            return 0;
        }

        double total = 0;
        for (Unit unit : self.getUnits()) {
            if (unit.isDead() || unit.getPosition() == null) {
                continue;
            }
            int distance = state.getMap().getDistance(unit.getPosition(), enemyChampion.getPosition());
            total -= distance * 0.25;
            for (Unit target : enemy.getUnits()) {
                if (!target.isDead() && Attack.canReach(state, unit, target)) {
                    total += 1.0;
                    break;
                }
            }
        }
        return total;
    }

    private Player opponentOf(GameState state, Team team) {
        for (Player player : state.getPlayers()) {
            if (player.getTeam() != team) {
                return player;
            }
        }
        throw new IllegalStateException("No opponent for team " + team);
    }
}
