package com.walnutt.data;

import java.util.List;
import java.util.Map;

/**
 * The optional "upgrade" block of design_ideas/abilities/&lt;unit&gt;/&lt;ability&gt;.json - the
 * second form an ability takes once Shawl's Hidden Potential unlocks it.
 *
 * Every text field except {@link #summary} is an OVERRIDE that falls back to the base
 * definition when absent, which is what keeps the common case (an upgrade that only moves
 * a number) down to two lines of JSON: the base sentence re-renders itself against the
 * merged stats and reads correctly with no restatement. Only an upgrade that makes the
 * base sentence untrue - Counterstrike countering for MORE damage rather than less -
 * writes its own {@code description}.
 *
 * {@link #stats} is a SPARSE override merged over the base stats rather than a
 * replacement, so an upgrade names only what it changes and may introduce keys the base
 * never had.
 *
 * @param summary     one line on what unlocking this buys, shown ONLY in Hidden Potential's
 *                    dialogue - deliberately never in a tooltip, so a player cannot read
 *                    an ability's upgrade off an enemy unit
 * @param description full replacement description once upgraded; null to re-render the base
 * @param type        "active"/"passive" when the upgrade changes which it is; null to keep
 * @param stats       sparse overrides merged over the base stats
 * @param details     full replacement bullet list once upgraded; null to re-render the base
 * @param tags        extra tags once upgraded; currently unused, reserved
 * @param repeatable  whether this ability may be upgraded more than once (Hidden Potential
 *                    alone, which pays out per upgrade granted rather than per upgrade taken)
 */
public record UpgradeDefinition(String summary, String description, String type,
                                 Map<String, Double> stats, List<String> details,
                                 List<String> tags, boolean repeatable) {

    /** Gson leaves absent JSON fields null; every accessor below is used on hand-edited drafts. */
    public Map<String, Double> statsOrEmpty() {
        return stats == null ? Map.of() : stats;
    }
}
