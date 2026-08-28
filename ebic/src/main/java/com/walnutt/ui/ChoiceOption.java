package com.walnutt.ui;

/**
 * One selectable entry in an {@link InputHandler#chooseOption} dialogue.
 *
 * Deliberately plain display data rather than a reference to whatever the option
 * stands for: the caller looks the chosen {@code id} back up in its own terms
 * (Eureka maps it to an ability id), which keeps this reusable for any future
 * "pick one of these" mechanic without the UI layer learning about game objects.
 *
 * @param id          caller-defined key, echoed back by the chosen option
 * @param name        headline text
 * @param description body text
 * @param detail      optional secondary line, e.g. "Cooldown: 4 turns" or "Passive"
 */
public record ChoiceOption(String id, String name, String description, String detail) {

    public ChoiceOption(String id, String name, String description) {
        this(id, name, description, null);
    }
}
