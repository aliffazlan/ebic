package com.walnutt.ui;

import java.util.List;
import java.util.Scanner;

import com.walnutt.ability.Ability;
import com.walnutt.ability.Move;
import com.walnutt.ability.target.NoTarget;
import com.walnutt.ability.target.Target;
import com.walnutt.ability.target.TileTarget;
import com.walnutt.ability.target.UnitTarget;
import com.walnutt.combat.Attribute;
import com.walnutt.data.UnitDefinition;
import com.walnutt.game.GameState;
import com.walnutt.game.Player;
import com.walnutt.map.Position;
import com.walnutt.map.Tile;
import com.walnutt.unit.Unit;

/**
 * Scanner-driven terminal input. Target prompting is generic (Move -> adjacent
 * tiles, everything else -> units within the ability's range) so a brand-new
 * active ability is immediately usable from the terminal without any UI changes -
 * TurnManager's canUse() check rejects an inappropriate pick and re-prompts.
 */
public class TerminalInputHandler implements InputHandler {
    private final Scanner scanner;

    public TerminalInputHandler(Scanner scanner) {
        this.scanner = scanner;
    }

    @Override
    public ActionChoice chooseAction(GameState state, Player player) {
        List<Unit> living = player.getLivingUnits();
        System.out.println();
        System.out.println("-- " + player.getName() + "'s turn (moves left: " + state.getRemainingMoves() + ") --");
        for (int i = 0; i < living.size(); i++) {
            Unit u = living.get(i);
            System.out.printf("  [%d] %s (%s) HP %d/%d at %s%n",
                i, u.getName(), u.getUnitType(), u.getHealth(), u.getMaxHealth(), u.getPosition());
        }
        System.out.println("  [E] End turn");
        System.out.print("Choose a unit: ");

        String line = scanner.nextLine().trim();
        if (line.equalsIgnoreCase("E")) {
            return ActionChoice.endTurn();
        }

        int unitIndex = parseIndex(line, living.size());
        if (unitIndex < 0) {
            System.out.println("Invalid choice.");
            return chooseAction(state, player);
        }
        Unit unit = living.get(unitIndex);

        List<Ability> abilities = unit.getAbilities().stream().filter(a -> !a.isPassive()).toList();
        System.out.println("Actions for " + unit.getName() + ":");
        for (int i = 0; i < abilities.size(); i++) {
            Ability a = abilities.get(i);
            String status = a.isReady() ? "ready" : ("cooldown " + a.getCurrentCooldown());
            System.out.printf("  [%d] %s (%s)%n", i, a.getName(), status);
        }
        System.out.println("  [B] Back");
        System.out.print("Choose an action: ");
        String actionLine = scanner.nextLine().trim();
        if (actionLine.equalsIgnoreCase("B")) {
            return chooseAction(state, player);
        }
        int abilityIndex = parseIndex(actionLine, abilities.size());
        if (abilityIndex < 0) {
            System.out.println("Invalid choice.");
            return chooseAction(state, player);
        }
        Ability ability = abilities.get(abilityIndex);

        Target target = promptTarget(state, unit, ability);
        return new ActionChoice(unit, ability, target);
    }

    private Target promptTarget(GameState state, Unit unit, Ability ability) {
        if (ability instanceof Move) {
            List<Tile> tiles = state.getMap().getAdjacentTiles(unit.getPosition());
            System.out.println("Move to:");
            for (int i = 0; i < tiles.size(); i++) {
                Tile t = tiles.get(i);
                System.out.printf("  [%d] %s %s%n", i, t.getPosition(), t.isWalkable() ? "" : "(blocked)");
            }
            int index = parseIndex(scanner.nextLine().trim(), tiles.size());
            return index < 0 ? new NoTarget() : new TileTarget(tiles.get(index));
        }

        int range = Math.max(1, ability.getRange());
        List<Unit> candidates = state.getMap().getUnitsInRadius(unit.getPosition(), range).stream()
            .filter(u -> u != unit)
            .toList();
        if (candidates.isEmpty()) {
            return new NoTarget();
        }
        System.out.println("Target:");
        for (int i = 0; i < candidates.size(); i++) {
            Unit u = candidates.get(i);
            System.out.printf("  [%d] %s (%s, %s) HP %d/%d%n",
                i, u.getName(), u.getTeam(), u.getUnitType(), u.getHealth(), u.getMaxHealth());
        }
        int index = parseIndex(scanner.nextLine().trim(), candidates.size());
        return index < 0 ? new NoTarget() : new UnitTarget(candidates.get(index));
    }

    @Override
    public Attribute chooseAttribute(GameState state, Unit unit, Unit opponent) {
        List<Attribute> usable = unit.getUsableAttributes();
        if (usable.isEmpty()) {
            return null;
        }
        System.out.printf("%s (%s) vs %s (%s) - choose an attribute:",
            unit.getName(), unit.getTeam(), opponent.getName(), opponent.getTeam());
        for (int i = 0; i < usable.size(); i++) {
            System.out.printf(" [%d] %s", i, usable.get(i));
        }
        System.out.print(": ");

        int index = parseIndex(scanner.nextLine().trim(), usable.size());
        // An attribute the unit has none of was never on the menu, so any unparseable
        // answer falls back to its first usable one rather than to Strength.
        return usable.get(index < 0 ? 0 : index);
    }

    private int parseIndex(String input, int size) {
        try {
            int i = Integer.parseInt(input);
            return (i >= 0 && i < size) ? i : -1;
        } catch (NumberFormatException e) {
            return -1;
        }
    }

    /** Numbered menu, same shape as choosePick below. Re-prompts rather than accepting a bad index. */
    @Override
    public ChoiceOption chooseOption(GameState state, Unit unit, String title, List<ChoiceOption> options) {
        if (options.isEmpty()) {
            return null;
        }
        System.out.println();
        System.out.println(unit.getName() + " - " + title + ":");
        for (int i = 0; i < options.size(); i++) {
            ChoiceOption option = options.get(i);
            System.out.printf("  [%d] %s%s%s%n", i, option.name(),
                option.detail() == null ? "" : " (" + option.detail() + ")",
                option.enabled() ? "" : " - unavailable");
            System.out.println("      " + option.description());
        }
        System.out.println("  [c] Cancel");
        System.out.print("Your choice: ");

        String answer = scanner.nextLine().trim();
        if (answer.equalsIgnoreCase("c")) {
            return null;
        }
        int index = parseIndex(answer, options.size());
        if (index < 0 || !options.get(index).enabled()) {
            System.out.println("Invalid choice.");
            return chooseOption(state, unit, title, options);
        }
        return options.get(index);
    }

    @Override
    public UnitDefinition choosePick(GameState state, Player player, List<UnitDefinition> options) {
        System.out.println(player.getName() + ", choose your pick:");
        for (int i = 0; i < options.size(); i++) {
            UnitDefinition def = options.get(i);
            System.out.printf("  [%d] %s (%s) HP %d STR %d AGI %d INT %d%n",
                i, def.name(), def.type(), def.maxHp(), def.strength(), def.agility(), def.intelligence());
        }
        System.out.print("Your pick: ");
        int index = parseIndex(scanner.nextLine().trim(), options.size());
        if (index < 0) {
            System.out.println("Invalid choice.");
            return choosePick(state, player, options);
        }
        return options.get(index);
    }

    @Override
    public Tile choosePlacementTile(GameState state, Player player, Unit unitToPlace, List<Tile> candidates) {
        System.out.println(player.getName() + ", place " + unitToPlace.getName() + " (" + unitToPlace.getUnitType() + "):");
        System.out.println("Legal tiles: " + candidates.stream().map(t -> t.getPosition().toString()).toList());
        System.out.print("Enter q,r: ");
        String line = scanner.nextLine().trim();

        Position position = parsePosition(line);
        if (position == null) {
            System.out.println("Invalid format - enter coordinates as q,r.");
            return choosePlacementTile(state, player, unitToPlace, candidates);
        }

        for (Tile candidate : candidates) {
            if (candidate.getPosition().equals(position)) {
                return candidate;
            }
        }
        System.out.println("That tile isn't a legal choice for this placement.");
        return choosePlacementTile(state, player, unitToPlace, candidates);
    }

    private Position parsePosition(String input) {
        String[] parts = input.split(",");
        if (parts.length != 2) {
            return null;
        }
        try {
            int q = Integer.parseInt(parts[0].trim());
            int r = Integer.parseInt(parts[1].trim());
            return new Position(q, r);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
