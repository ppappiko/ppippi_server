package dev.pipi.life;

import java.util.Set;
import org.bukkit.Material;

/**
 * The PipiFacilities gathering (farming) blocks players must be able to break.
 *
 * <p>Blocks listed here are excluded from region protection (wild regions and admin boxes): players break
 * them with no op or permission, and {@link ResourceNodes} handles them instead (break -&gt; drop -&gt; regrow).
 * Add new gathering block ids here; this is the single list used by region protection and ResourceNodes.
 */
final class GatherBlocks {
    private GatherBlocks() {}

    /** PipiFacilities mod block ids (without the {@code PIPIFACILITIES_} prefix), upper case. */
    private static final Set<String> IDS = Set.of(
        "AMBER_ROCK", "REDWOOD_TREE_LOG", "REDWOOD_TREE_LEAVES", "CABBAGE_CROP");

    private static final String PREFIX = "PIPIFACILITIES_";

    /** True when {@code type} is one of the PipiFacilities gathering blocks (a missing mod block is never one). */
    static boolean is(Material type) {
        if (type == null) return false;
        String n = type.name();
        return n.startsWith(PREFIX) && IDS.contains(n.substring(PREFIX.length()));
    }
}