package com.walnutt.ai;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.walnutt.game.DefaultArrangement;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.game.Team;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.status.Stat;
import com.walnutt.unit.BasicUnit;
import com.walnutt.unit.ChampionUnit;
import com.walnutt.unit.EliteUnit;
import com.walnutt.unit.Unit;
import com.walnutt.unit.UnitStats;

/**
 * These pin down a bug that only self-play surfaced: on the full match's elongated map,
 * ordering tiles purely by distance from the enemy anchor produces large ties - the
 * corner (7,0) and the midfield tile (4,3) are both exactly 14 from (-7,0) - and the
 * arbitrary winner of that tie was placing one champion at the very front of its own
 * army while the other sat correctly at the back. Every ordering here breaks ties on
 * distance from the placing player's OWN anchor, which resolves them.
 */
class BotPlacementTest {
    /** The real full-match shape - the trim is what creates the ties, so a regular hexagon would not reproduce this. */
    private static final int RADIUS = 7;
    private static final int ROW_LIMIT = 5;

    @Test
    void theElongatedMapReallyDoesTieOnDistanceFromTheEnemyAnchor() {
        // Guards the premise of the other tests: if this stops being true the tie-break
        // is no longer load-bearing and these tests would pass for the wrong reason.
        GameMap map = new GameMap(RADIUS, ROW_LIMIT);
        Position enemyAnchor = new Position(-RADIUS, 0);
        int cornerDistance = map.getDistance(new Position(RADIUS, 0), enemyAnchor);

        long tiedTiles = map.getTilesInRadius(new Position(0, 0), RADIUS * 2).stream()
            .filter(tile -> map.getDistance(tile.getPosition(), enemyAnchor) == cornerDistance)
            .count();

        assertTrue(tiedTiles > 1,
            "expected several tiles to tie at max distance from the enemy anchor, found " + tiedTiles);
    }

    @Test
    void theChampionIsPlacedAtItsOwnAnchorNotMerelySomewhereFarFromTheEnemy() {
        GameState state = fullRosterState();
        Player player = state.getPlayer(Team.PLAYER_TWO);
        Position anchor = DefaultArrangement.anchorFor(state, player);

        Map<Unit, Position> arrangement = BotPlacement.arrange(state, player);

        assertEquals(anchor, arrangement.get(player.getChampion().orElseThrow()),
            "the champion must sit on the anchor - it is the win condition, and any other "
                + "tile at the same distance from the enemy is closer to the fighting");
    }

    /** The sequential placement path had the same tie problem and its own fix. */
    @Test
    void theSequentialPlacementPathAlsoPutsTheChampionOnTheAnchor() {
        GameState state = fullRosterState();
        Player player = state.getPlayer(Team.PLAYER_TWO);
        Position anchor = DefaultArrangement.anchorFor(state, player);
        List<Tile> candidates = state.getMap().getEmptyTilesInRadius(anchor, 3);

        Unit champion = player.getChampion().orElseThrow();
        Tile chosen = new BotHandler(BotConfig.standard().withoutThinkDelay())
            .choosePlacementTile(state, player, champion, candidates);

        assertEquals(anchor, chosen.getPosition());
    }

    @Test
    void longRangeUnitsAreKeptBehindTheMeleeLine() {
        GameState state = fullRosterState();
        Player player = state.getPlayer(Team.PLAYER_ONE);
        Position enemyAnchor = DefaultArrangement.anchorFor(state, state.getPlayer(Team.PLAYER_TWO));

        Map<Unit, Position> arrangement = BotPlacement.arrange(state, player);

        Unit archer = player.getUnits().stream()
            .filter(u -> u.getEffective(Stat.ATTACK_RANGE) >= 4).findFirst().orElseThrow();
        Unit brawler = player.getUnits().stream()
            .filter(u -> u.getEffective(Stat.ATTACK_RANGE) <= 1 && u != player.getChampion().orElse(null))
            .findFirst().orElseThrow();

        int archerDistance = state.getMap().getDistance(arrangement.get(archer), enemyAnchor);
        int brawlerDistance = state.getMap().getDistance(arrangement.get(brawler), enemyAnchor);

        assertTrue(archerDistance > brawlerDistance,
            "a range-4 unit should sit behind a melee one, but was " + archerDistance
                + " from the enemy versus the brawler's " + brawlerDistance);
    }

    @Test
    void everyUnitGetsItsOwnRealTileInsideTheArrangement() {
        GameState state = fullRosterState();
        Player player = state.getPlayer(Team.PLAYER_ONE);

        Map<Unit, Position> arrangement = BotPlacement.arrange(state, player);

        assertEquals(player.getUnits().size(), arrangement.size(), "every unit must be placed");
        assertEquals(arrangement.size(), List.copyOf(arrangement.values()).stream().distinct().count(),
            "two units were given the same tile");
        for (Position position : arrangement.values()) {
            // The trimmed map means a coordinate inside the radius is not necessarily a tile.
            assertNotEquals(null, state.getMap().getTile(position),
                "placed a unit on " + position + ", which is not a tile on this map");
        }
    }

    /** Champion + 3 elites + 10 basics per side, mirroring a real drafted roster. */
    private GameState fullRosterState() {
        GameMap map = new GameMap(RADIUS, ROW_LIMIT);
        List<Player> players = new ArrayList<>();
        for (Team team : List.of(Team.PLAYER_ONE, Team.PLAYER_TWO)) {
            Player player = new Player(team.name(), team);
            player.addUnit(new ChampionUnit("Champ-" + team, team, new UnitStats(60, 50, 60, 1100)));
            // One deliberately long-ranged elite, so the ordering has something to sort.
            player.addUnit(new EliteUnit("Archer-" + team, team, new UnitStats(28, 55, 50, 440, 4)));
            player.addUnit(new EliteUnit("Brawler-" + team, team, new UnitStats(80, 30, 20, 800)));
            player.addUnit(new EliteUnit("Support-" + team, team, new UnitStats(30, 9, 20, 850)));
            for (int i = 1; i <= 10; i++) {
                player.addUnit(new BasicUnit("Basic-" + team + "-" + i, team, new UnitStats(20, 20, 20, 300)));
            }
            players.add(player);
        }
        return new GameState(map, players, new Random(11));
    }
}
