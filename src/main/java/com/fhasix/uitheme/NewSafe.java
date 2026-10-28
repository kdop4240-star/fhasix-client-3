package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

/**
 * Crash shield. Every Fhasix tick handler, HUD element, screen hook and mixin entry point runs through
 * here: if one of them throws (a different Minecraft build, another mod, an odd launcher, bad data),
 * the error is logged ONCE and swallowed instead of closing the game. A feature that fails
 * LIMIT times is switched off for the rest of the session so it cannot spam or slow the game.
 * Only real JVM problems (out of memory, stack overflow, ...) are left alone.
 */
public final class NewSafe {

    private static final Logger LOG = LoggerFactory.getLogger("fhasixuitheme");
    private static final int LIMIT = 5;
    private static final Map<String, State> STATES = new ConcurrentHashMap<>();

    /** Failure counter of one feature. Wrappers that run every frame keep their State, so no lookup or allocation per call. */
    static final class State {
        final AtomicInteger fails = new AtomicInteger();

        boolean off() {
            return fails.get() >= LIMIT;
        }
    }

    private static State state(String feature) {
        State s = STATES.get(feature);
        return s != null ? s : STATES.computeIfAbsent(feature, k -> new State());
    }

    private NewSafe() {
    }

    public static boolean disabled(String feature) {
        return state(feature).off();
    }

    /** Records a failure of a feature (logged on the first one and when the feature is switched off). */
    public static void failed(String feature, Throwable t) {
        int n = state(feature).fails.incrementAndGet();
        if (n == 1) {
            LOG.error("Fhasix feature '{}' failed and was skipped: {}", feature, String.valueOf(t), t);
        } else if (n == LIMIT) {
            LOG.error("Fhasix feature '{}' failed {} times - switched off until the game is restarted", feature, LIMIT);
        }
    }

    public static void run(String feature, Runnable r) {
        if (disabled(feature)) {
            return;
        }
        try {
            r.run();
        } catch (Exception | LinkageError t) {
            failed(feature, t);
        }
    }

    public static <T> T call(String feature, Supplier<T> s, T fallback) {
        if (disabled(feature)) {
            return fallback;
        }
        try {
            return s.get();
        } catch (Exception | LinkageError t) {
            failed(feature, t);
            return fallback;
        }
    }

    /** A client tick handler that can never throw. */
    public static ClientTickEvents.EndTick tick(String feature, ClientTickEvents.EndTick handler) {
        State st = state(feature);
        return client -> {
            if (st.off()) {
                return;
            }
            try {
                handler.onEndTick(client);
            } catch (Exception | LinkageError t) {
                failed(feature, t);
            }
        };
    }

    /** A HUD element that can never throw. */
    public static HudElement hud(String feature, HudElement element) {
        State st = state(feature);
        return (context, tickCounter) -> {
            if (st.off()) {
                return;
            }
            try {
                element.render(context, tickCounter);
            } catch (Exception | LinkageError t) {
                failed(feature, t);
            }
        };
    }

    /** A replacement for a vanilla HUD element: if ours fails, the vanilla one is drawn instead. */
    public static HudElement replace(String feature, HudElement original, HudElement custom) {
        State st = state(feature);
        return (context, tickCounter) -> {
            if (!st.off()) {
                try {
                    custom.render(context, tickCounter);
                    return;
                } catch (Exception | LinkageError t) {
                    failed(feature, t);
                }
            }
            original.render(context, tickCounter);
        };
    }
}
