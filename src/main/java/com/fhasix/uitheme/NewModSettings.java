package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

/**
 * NEW (v30). The list of mods shown in the Mod Menu grid plus the small amount of state that
 * has to survive a restart: which mods are enabled, which are favourited, and where the
 * movable HUD elements (FPS / CPS) were dragged to.
 *
 * Saved next to skins.properties style files: <game dir>/fhasix_mods.properties. Positions
 * are stored as FRACTIONS of the scaled window (0..1), so they stay put when the GUI scale
 * or window size changes.
 *
 * Only FPS Display and CPS Display are "functional". Every other card is a placeholder:
 * it is shown in the grid, but its Enabled/Disabled pill is inert (always Disabled).
 */
public final class NewModSettings {

    public static final int TAG_HUD = 1;
    public static final int TAG_HYPIXEL = 2;
    public static final int TAG_PVP = 4;

    public static final class ModDef {
        public final String id;
        public final String name;
        public final int tags;
        public final boolean isNew;
        public final boolean functional;
        /** Cell index in the icon atlas (textures/gui/new_mod_icons.png). */
        public final int icon;

        ModDef(String id, String name, int tags, boolean isNew, boolean functional, int icon) {
            this.id = id;
            this.name = name;
            this.tags = tags;
            this.isNew = isNew;
            this.functional = functional;
            this.icon = icon;
        }
    }

    /** Card order: the working mods first, then the placeholders. */
    public static final List<ModDef> MODS = List.of(
            new ModDef("fps_display", "FPS Display", TAG_HUD | TAG_PVP, false, true, 0),
            new ModDef("cps_display", "CPS Display", TAG_HUD | TAG_PVP, false, true, 1),
            new ModDef("armor_status", "Armor Status", TAG_HUD | TAG_PVP, false, true, 7),
            new ModDef("armor_bar", "Armor Bar", TAG_HUD, false, true, 6),
            new ModDef("block_indicator", "Block Indicator", TAG_HUD, false, true, 9),
            new ModDef("block_overlay", "Block Overlay", TAG_PVP, false, true, 12),
            new ModDef("attack_indicator", "Attack Indicator", TAG_HUD | TAG_PVP, false, true, 8),
            new ModDef("autohide_hud", "Autohide HUD", TAG_HUD, true, true, 2),
            new ModDef("camera", "Camera", 0, true, true, 3),
            new ModDef("uhc_overlay", "UHC Overlay", TAG_HYPIXEL | TAG_PVP, true, true, 4),
            new ModDef("animations", "Animations", TAG_PVP, false, true, 5),
            new ModDef("no_crash", "No Crash", 0, true, true, 10),
            new ModDef("no_boss_bar", "No Boss Bar", TAG_HUD, true, true, 13),
            new ModDef("custom_crosshair", "Custom Crosshair", TAG_HUD | TAG_PVP, true, true, 18),
            new ModDef("custom_fog", "Custom Fog", 0, true, true, 19),
            new ModDef("damage_indicator", "Health Indicator", TAG_PVP, true, true, 20),
            new ModDef("direction", "Direction", TAG_HUD, true, true, 21),
            new ModDef("coordinates", "Coordinates", TAG_HUD, true, true, 23),
            new ModDef("potion_effects", "Potion Effects", TAG_HUD, true, true, 24),
            new ModDef("hitbox", "Hitbox", TAG_PVP, true, true, 25),
            new ModDef("keystrokes", "Keystrokes", TAG_HUD | TAG_PVP, true, true, 26),
            new ModDef("shulker_tooltips", "Shulker Tooltips", 0, true, true, 27),
            new ModDef("no_scoreboard", "No Scoreboard", TAG_HUD, true, true, 28),
            new ModDef("full_bright", "Full Bright", 0, true, true, 29),
            new ModDef("fov_changer", "FOV Changer", 0, true, true, 30),
            new ModDef("time_changer", "Time Changer", 0, true, true, 31),
            new ModDef("weather_changer", "Weather Changer", 0, true, true, 32),
            new ModDef("tnt_timer", "TNT Timer", TAG_PVP, true, true, 33),
            new ModDef("dark_mode", "Dark Mode", 0, true, true, 22),
            new ModDef("auto_text", "Auto Text", TAG_HYPIXEL, false, false, 11)
    );

    public static ModDef find(String id) {
        for (ModDef m : MODS) {
            if (m.id.equals(id)) {
                return m;
            }
        }
        return null;
    }

    private static final String ID_DARK = "dark_mode";
    private static final Set<String> ENABLED = new HashSet<>();
    private static final Set<String> FAVORITES = new HashSet<>();
    private static final Map<String, float[]> POSITIONS = new HashMap<>();
    /** Per-mod option values, key "modId.optionKey" (toggle 0/1, slider value, choice index, colour index). */
    private static final Map<String, Integer> OPTS = new HashMap<>();
    private static boolean loaded = false;

    private NewModSettings() {
    }

    private static Path file(MinecraftClient c) {
        return c.runDirectory.toPath().resolve("fhasix_mods.properties");
    }

    private static void ensureLoaded(MinecraftClient c) {
        if (loaded) {
            return;
        }
        loaded = true;
        Path f = file(c);
        if (!Files.isRegularFile(f)) {
            return;
        }
        Properties p = new Properties();
        try (InputStream in = Files.newInputStream(f)) {
            p.load(in);
        } catch (IOException ignored) {
            return; // unreadable - start from defaults
        }
        for (String name : p.stringPropertyNames()) {
            if (name.startsWith("opt.")) {
                try {
                    OPTS.put(name.substring(4), Integer.parseInt(p.getProperty(name).trim()));
                } catch (NumberFormatException ignored) {
                    // bad value - option falls back to its default
                }
            }
        }
        for (ModDef m : MODS) {
            if (m.functional && "true".equals(p.getProperty("enabled." + m.id))) {
                ENABLED.add(m.id);
            }
            if ("true".equals(p.getProperty("fav." + m.id))) {
                FAVORITES.add(m.id);
            }
            String pos = p.getProperty("pos." + m.id);
            if (pos != null) {
                String[] parts = pos.split(",");
                if (parts.length == 2) {
                    try {
                        POSITIONS.put(m.id, new float[]{
                                Float.parseFloat(parts[0].trim()), Float.parseFloat(parts[1].trim())});
                    } catch (NumberFormatException ignored) {
                        // bad value - fall back to the default position
                    }
                }
            }
        }
    }

    public static void save(MinecraftClient c) {
        ensureLoaded(c);
        Properties p = new Properties();
        for (ModDef m : MODS) {
            p.setProperty("enabled." + m.id, String.valueOf(ENABLED.contains(m.id)));
            p.setProperty("fav." + m.id, String.valueOf(FAVORITES.contains(m.id)));
            float[] pos = POSITIONS.get(m.id);
            if (pos != null) {
                p.setProperty("pos." + m.id, pos[0] + "," + pos[1]);
            }
        }
        for (Map.Entry<String, Integer> e : OPTS.entrySet()) {
            p.setProperty("opt." + e.getKey(), String.valueOf(e.getValue()));
        }
        try (OutputStream out = Files.newOutputStream(file(c))) {
            p.store(out, "Fhasix mod menu state");
        } catch (IOException ignored) {
            // not being able to remember the state must never break anything
        }
    }

    public static boolean isEnabled(MinecraftClient c, String id) {
        ensureLoaded(c);
        return ENABLED.contains(id);
    }

    /** Flips a mod on/off. Placeholder (non-functional) mods can't be switched on. */
    public static void toggle(MinecraftClient c, ModDef m) {
        ensureLoaded(c);
        if (!m.functional) {
            return;
        }
        if (!ENABLED.remove(m.id)) {
            ENABLED.add(m.id);
        }
        save(c);
        if (ID_DARK.equals(m.id)) {
            NewDarkMode.onToggled(c, ENABLED.contains(m.id)); // builds / enables or disables the dark resource pack
        }
    }

    public static boolean isFavorite(MinecraftClient c, String id) {
        ensureLoaded(c);
        return FAVORITES.contains(id);
    }

    public static void toggleFavorite(MinecraftClient c, String id) {
        ensureLoaded(c);
        if (!FAVORITES.remove(id)) {
            FAVORITES.add(id);
        }
        save(c);
    }

    /** Top-left of a HUD element as fractions of the scaled window. */
    public static float[] position(MinecraftClient c, String id, float defX, float defY) {
        ensureLoaded(c);
        float[] p = POSITIONS.get(id);
        return p != null ? p : new float[]{defX, defY};
    }

    /** In-memory only - call save() when the drag ends. */
    public static void setPosition(MinecraftClient c, String id, float x, float y) {
        ensureLoaded(c);
        POSITIONS.put(id, new float[]{x, y});
    }

    /** Current value of an option (its default when never changed). */
    public static int opt(MinecraftClient c, String mod, String key) {
        ensureLoaded(c);
        Integer v = OPTS.get(mod + "." + key);
        if (v != null) {
            return v;
        }
        NewModOptions.Opt o = NewModOptions.find(mod, key);
        return o == null ? 0 : o.def;
    }

    public static boolean optBool(MinecraftClient c, String mod, String key) {
        return opt(c, mod, key) != 0;
    }

    /** In-memory only - call save() when the change is finished (e.g. mouse released). */
    public static void setOpt(MinecraftClient c, String mod, String key, int value) {
        ensureLoaded(c);
        OPTS.put(mod + "." + key, value);
    }
}
