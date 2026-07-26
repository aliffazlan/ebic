package com.walnutt.web;

import java.util.ArrayList;
import java.util.List;

/**
 * Test double for ClientChannel - records everything sent to it instead of
 * touching a real socket, the same "scripted double" pattern the engine tests
 * already use for InputHandler/Renderer (see DraftFlowTest, TurnManagerIntegrationTest).
 */
public final class RecordingChannel implements ClientChannel {
    private final List<String> sent = new ArrayList<>();
    private volatile boolean open = true;

    @Override
    public synchronized void send(String json) {
        sent.add(json);
    }

    @Override
    public boolean isOpen() {
        return open;
    }

    public void close() {
        open = false;
    }

    public synchronized List<String> getSent() {
        return new ArrayList<>(sent);
    }

    public synchronized String last() {
        return sent.isEmpty() ? null : sent.get(sent.size() - 1);
    }
}
