package dev.lrxh.neptune.game.arena;

import org.bukkit.Bukkit;

/**
 * FastAsyncWorldEdit presence check that is deliberately free of any WorldEdit
 * imports.
 * <p>
 * {@link ArenaDuplicator} references WorldEdit types ({@code CuboidRegion},
 * {@code Clipboard}, ...) in its method signatures, so merely <em>calling</em>
 * a method on it forces the JVM to load and verify the class — which throws
 * {@link NoClassDefFoundError} when FastAsyncWorldEdit is not installed. That
 * previously crashed plugin startup on servers without FAWE. Routing the
 * availability check through this class keeps ArenaDuplicator unloaded until
 * FAWE is confirmed present.
 */
public final class FaweSupport {
    private FaweSupport() {
    }

    public static boolean isAvailable() {
        return Bukkit.getPluginManager().getPlugin("FastAsyncWorldEdit") != null;
    }
}
