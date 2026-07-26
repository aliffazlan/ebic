package com.walnutt.ui;

import java.util.List;

import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.map.GameMap;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.map.TileType;
import com.walnutt.unit.Unit;

public class TerminalRenderer implements Renderer {

    @Override
    public void render(GameState state) {
        GameMap map = state.getMap();
        int radius = map.getRadius();
        StringBuilder sb = new StringBuilder("\n");
        // One row per r; indent by |r| so rows shrink toward the top/bottom, giving
        // the printed grid a hexagonal (rather than rectangular) silhouette.
        for (int r = -radius; r <= radius; r++) {
            sb.append("  ".repeat(Math.abs(r)));
            int qMin = Math.max(-radius, -r - radius);
            int qMax = Math.min(radius, -r + radius);
            for (int q = qMin; q <= qMax; q++) {
                sb.append(symbolFor(map.getTile(new Position(q, r)))).append("  ");
            }
            sb.append('\n');
        }
        System.out.print(sb);

        for (Player player : state.getPlayers()) {
            System.out.println(player.getName() + " (" + player.getTeam() + "):");
            for (Unit unit : player.getUnits()) {
                if (unit.isDead()) {
                    continue;
                }
                System.out.printf("  %s [%s] HP %d/%d at %s%n",
                    unit.getName(), unit.getUnitType(), unit.getHealth(), unit.getMaxHealth(), unit.getPosition());
            }
        }
    }

    private String symbolFor(Tile tile) {
        if (tile.getType() == TileType.BLOCKED) {
            return "#";
        }
        Unit occupant = tile.getFirstOccupant();
        if (occupant != null) {
            String name = occupant.getName();
            String symbol = name.isEmpty() ? "?" : name.substring(0, 1);
            return tile.getOccupants().size() > 1 ? symbol + "*" : symbol;
        }
        return ".";
    }

    @Override
    public void renderMessage(String message) {
        System.out.println(message);
    }

    @Override
    public void renderGameOver(GameState state) {
        System.out.println();
        if (state.getWinner() != null) {
            System.out.println(state.getWinner().getName() + " wins!");
        } else {
            System.out.println("Game over.");
        }
    }

    @Override
    public void renderDraftRound(String roundLabel, Player playerOne, List<UnitDefinition> playerOneOptions,
                                  Player playerTwo, List<UnitDefinition> playerTwoOptions) {
        System.out.println();
        System.out.println("== Draft: " + roundLabel + " ==");
        printCandidates(playerOne, playerOneOptions);
        printCandidates(playerTwo, playerTwoOptions);
    }

    private void printCandidates(Player player, List<UnitDefinition> options) {
        System.out.println(player.getName() + "'s options:");
        for (UnitDefinition def : options) {
            System.out.printf("  - %s (%s) HP %d STR %d AGI %d INT %d%n",
                def.name(), def.type(), def.maxHp(), def.strength(), def.agility(), def.intelligence());
        }
    }
}
