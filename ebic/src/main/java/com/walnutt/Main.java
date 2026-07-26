package com.walnutt;

import com.walnutt.game.Game;
import com.walnutt.web.WebServer;

public class Main {
    public static void main(String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("web")) {
            WebServer.main(args);
            return;
        }
        boolean fullRoster = args.length > 0 && args[0].equalsIgnoreCase("full");
        Game game = fullRoster ? Game.newFullDraftMatch() : Game.newMinimalMatch();
        game.start();
    }
}
