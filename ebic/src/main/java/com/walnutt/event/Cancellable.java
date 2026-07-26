package com.walnutt.event;

public interface Cancellable {
    boolean isCancelled();

    void cancel();
}
