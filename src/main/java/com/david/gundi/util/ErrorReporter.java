package com.david.gundi.util;

import com.david.gundi.Gundi;
import meteordevelopment.meteorclient.utils.player.ChatUtils;

import java.util.HashSet;
import java.util.Set;

/**
 * Shared error checker for every feature in the addon.
 *
 * Everything is printed to the terminal (IntelliJ's Run window, also saved in run/logs/latest.log)
 * FIRST, so even if the game crashes right after, the last lines tell you what happened.
 *
 * Usage:
 * <pre>
 * ErrorReporter.step("speed-graph", "registering");   // breadcrumb: "I'm about to do this"
 * try { ... } catch (Exception e) { ErrorReporter.report("speed-graph", "render", e); }
 * </pre>
 */
public final class ErrorReporter {
    private static final Set<String> reported = new HashSet<>();

    private ErrorReporter() {}

    /** Prints a breadcrumb to the terminal. If the game crashes, the last step printed is where it died. */
    public static void step(String feature, String message) {
        Gundi.LOG.info("[{}] {}", feature, message);
    }

    /**
     * Prints the full error to the terminal, then a short red message in chat.
     * Each feature + place is reported once per game session so render code (runs every frame) can't flood you.
     */
    public static void report(String feature, String where, Throwable e) {
        if (!reported.add(feature + "/" + where)) return;

        // 1. Terminal first: this always works, even on the title screen or right before a crash.
        Gundi.LOG.error("==================== ADDON ERROR ====================");
        Gundi.LOG.error("Feature: {} | Where: {}", feature, where);
        Gundi.LOG.error("Full stack trace (look for lines starting with com.david.gundi):", e);
        Gundi.LOG.error("=====================================================");

        // 2. Chat second, in its own try so a broken chat can never turn an error report into a crash.
        try {
            String reason = e.getMessage() != null ? e.getClass().getSimpleName() + ": " + e.getMessage() : e.getClass().getSimpleName();
            // Passed as %s arguments because ChatUtils runs the text through String.format.
            ChatUtils.errorPrefix("Gundi", "%s broke in %s: %s (full details in the terminal)", feature, where, reason);
        } catch (Throwable chatError) {
            Gundi.LOG.error("[{}] could not show the error in chat", feature, chatError);
        }
    }
}
