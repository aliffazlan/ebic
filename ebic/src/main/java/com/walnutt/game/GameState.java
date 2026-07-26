package com.walnutt.game;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.walnutt.data.AbilityDefinition;
import com.walnutt.data.UnitDefinition;
import com.walnutt.event.EventBus;
import com.walnutt.map.GameMap;
import com.walnutt.map.Tile;
import com.walnutt.ui.InputHandler;
import com.walnutt.unit.Unit;

/**
 * The entire game world. Passed explicitly into gameplay methods rather than
 * accessed through a singleton/global - keeps testing, AI simulation, and future
 * save/load/replay simple.
 */
public class GameState {
    private final GameMap map;
    private final List<Player> players;
    private final EventBus eventBus = new EventBus();
    private final List<Unit> summonedUnits = new ArrayList<>();
    private final Random random;
    private InputHandler inputHandler;
    private Map<String, UnitDefinition> unitDefinitions = Map.of();
    private Map<String, AbilityDefinition> abilityDefinitions = Map.of();

    private Player currentPlayer;
    private int remainingMoves;
    private boolean gameOver;
    private Player winner;

    public GameState(GameMap map, List<Player> players, Random random) {
        this.map = map;
        this.players = players;
        this.random = random;
        this.currentPlayer = players.get(0);
    }

    public GameMap getMap() {
        return map;
    }

    public List<Player> getPlayers() {
        return players;
    }

    public Player getPlayer(Team team) {
        return players.stream().filter(p -> p.getTeam() == team).findFirst()
            .orElseThrow(() -> new IllegalArgumentException("No player for team " + team));
    }

    public Player getCurrentPlayer() {
        return currentPlayer;
    }

    public int getRemainingMoves() {
        return remainingMoves;
    }

    public boolean canSpendMoves(int cost) {
        return remainingMoves >= cost;
    }

    public void spendMoves(int cost) {
        remainingMoves = Math.max(0, remainingMoves - cost);
    }

    public void setRemainingMoves(int moves) {
        this.remainingMoves = moves;
    }

    public boolean isGameOver() {
        return gameOver;
    }

    public void setGameOver(boolean gameOver) {
        this.gameOver = gameOver;
    }

    public Player getWinner() {
        return winner;
    }

    public void setWinner(Player winner) {
        this.winner = winner;
    }

    public EventBus getEventBus() {
        return eventBus;
    }

    public Random getRandom() {
        return random;
    }

    public InputHandler getInputHandler() {
        return inputHandler;
    }

    public void setInputHandler(InputHandler inputHandler) {
        this.inputHandler = inputHandler;
    }

    /**
     * Lets an ability that summons a full unit from the same JSON pool (Yuki's Snow
     * Golem, Zenith's Pylon) build it via UnitFactory.createFromDefinition exactly
     * like a drafted unit, instead of needing the definitions injected into the
     * ability's own constructor. Empty maps by default - only Game.java's real
     * matches populate these; unit tests that build units by hand don't need them.
     */
    public Map<String, UnitDefinition> getUnitDefinitions() {
        return unitDefinitions;
    }

    public void setUnitDefinitions(Map<String, UnitDefinition> unitDefinitions) {
        this.unitDefinitions = unitDefinitions;
    }

    public Map<String, AbilityDefinition> getAbilityDefinitions() {
        return abilityDefinitions;
    }

    public void setAbilityDefinitions(Map<String, AbilityDefinition> abilityDefinitions) {
        this.abilityDefinitions = abilityDefinitions;
    }

    public void checkWinCondition() {
        if (gameOver) {
            return;
        }
        for (Player player : players) {
            if (player.isDefeated()) {
                gameOver = true;
                winner = players.stream().filter(p -> p != player).findFirst().orElse(null);
                return;
            }
        }
    }

    public void switchCurrentPlayer() {
        int index = players.indexOf(currentPlayer);
        currentPlayer = players.get((index + 1) % players.size());
    }

    public void registerSummon(Unit unit) {
        summonedUnits.add(unit);
    }

    public List<Unit> getAllActiveUnits() {
        List<Unit> all = new ArrayList<>();
        for (Player player : players) {
            all.addAll(player.getUnits());
        }
        all.addAll(summonedUnits);
        return all;
    }

    /**
     * Map/registry cleanup only. DeathEvent/KillEvent/checkWinCondition are
     * published by Unit.takeDamage itself (it has the triggering DamageEvent on
     * hand); this just detaches the unit from the board.
     */
    public void removeUnit(Unit unit, RemovalReason reason) {
        Tile tile = unit.getPosition() == null ? null : map.getTile(unit.getPosition());
        if (tile != null) {
            tile.removeOccupant(unit);
        }
        summonedUnits.remove(unit);
    }
}
