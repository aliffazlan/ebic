package com.walnutt.web.dto;

public record AbilitySnapshot(
    String id,
    String name,
    boolean passive,
    boolean ready,
    int currentCooldown,
    int maxCooldown
) {
}
