package com.walnutt.web;

import io.javalin.websocket.WsContext;

/** Thin adapter so ChannelHub/WebInputHandler/WebRenderer never touch Javalin types directly. */
public final class JavalinClientChannel implements ClientChannel {
    private final WsContext ctx;

    public JavalinClientChannel(WsContext ctx) {
        this.ctx = ctx;
    }

    @Override
    public void send(String json) {
        if (isOpen()) {
            ctx.send(json);
        }
    }

    @Override
    public boolean isOpen() {
        return ctx.session.isOpen();
    }
}
