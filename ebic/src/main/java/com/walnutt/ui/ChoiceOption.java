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
 * @param detail      optional secondary line, e.g. "Cooldown: 4 turns" or "Passive".
 *                    For a disabled option this is the REASON, which is the whole point
 *                    of listing it rather than hiding it.
 * @param enabled     false for an option shown but not choosable - Shawl's dialogue lists
 *                    an ally's abilities that are already unlocked, or whose upgrade is not
 *                    implemented yet, so the player can see why rather than wonder where
 *                    they went. A handler must refuse a disabled id.
 */
public record ChoiceOption(String id, String name, String description, String detail, boolean enabled) {

    public ChoiceOption(String id, String name, String description, String detail) {
        this(id, name, description, detail, true);
    }

    public ChoiceOption(String id, String name, String description) {
        this(id, name, description, null, true);
    }

    /** Shown greyed out, with {@code reason} in place of the usual secondary line. */
    public static ChoiceOption disabled(String id, String name, String description, String reason) {
        return new ChoiceOption(id, name, description, reason, false);
    }
}
