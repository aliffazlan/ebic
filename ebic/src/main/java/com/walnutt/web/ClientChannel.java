package com.walnutt.web;

/**
 * Thin wrapper around a live client connection, so the bridge (WebInputHandler /
 * WebRenderer / ChannelHub) stays testable without a real Jetty socket - tests use
 * a small recording/scripted implementation instead of standing up a server.
 */
public interface ClientChannel {
    void send(String json);

    boolean isOpen();
}
