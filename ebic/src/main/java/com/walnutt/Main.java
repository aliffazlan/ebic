package com.walnutt;

import com.walnutt.game.Game;

public class Main {
    public static void main(String[] args) {
        boolean fullRoster = args.length > 0 && args[0].equalsIgnoreCase("full");
        Game game = fullRoster ? Game.newFullDraftMatch() : Game.newMinimalMatch();
        game.start();
    }
}
