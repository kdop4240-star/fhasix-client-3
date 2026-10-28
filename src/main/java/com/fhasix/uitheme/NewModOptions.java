package com.fhasix.uitheme;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * NEW. What can be customised for each working mod. The values themselves live in
 * NewModSettings (saved in fhasix_mods.properties); this class only describes the options and
 * their defaults, and NewModOptionsScreen turns the description into controls.
 */
final class NewModOptions {

    static final int TOGGLE = 0;
    static final int SLIDER = 1;
    static final int CHOICE = 2;
    static final int COLOR = 3;
    /** Full colour picker (saturation/brightness square + hue bar); stores a packed 0xRRGGBB. */
    static final int PICKER = 4;

    /** Colour choices (ARGB, fully opaque). Colour options store an index into this array. */
    static final int[] PALETTE = {
            0xFFFFFFFF, // White
            0xFFE73C40, // Red
            0xFFFF8A1F, // Orange
            0xFFFFD43B, // Yellow
            0xFF3FBF4D, // Green
            0xFF22D3EE, // Cyan
            0xFF3B82F6, // Blue
            0xFF8B5CF6, // Purple
            0xFFEC4899, // Pink
            0xFF000000  // Black
    };

    static final class Opt {
        final String key;
        final String label;
        final int type;
        final int def;
        final int min;
        final int max;
        final int step;
        final String[] choices;
        final String suffix;

        Opt(String key, String label, int type, int def, int min, int max, int step, String[] choices, String suffix) {
            this.key = key;
            this.label = label;
            this.type = type;
            this.def = def;
            this.min = min;
            this.max = max;
            this.step = step;
            this.choices = choices;
            this.suffix = suffix;
        }
    }

    private static final Map<String, List<Opt>> BY_MOD = new HashMap<>();

    private static Opt toggle(String key, String label, boolean def) {
        return new Opt(key, label, TOGGLE, def ? 1 : 0, 0, 1, 1, null, "");
    }

    private static Opt slider(String key, String label, int def, int min, int max, int step, String suffix) {
        return new Opt(key, label, SLIDER, def, min, max, step, null, suffix);
    }

    private static Opt choice(String key, String label, int def, String... choices) {
        return new Opt(key, label, CHOICE, def, 0, choices.length - 1, 1, choices, "");
    }

    private static Opt color(String key, String label, int def) {
        return new Opt(key, label, COLOR, def, 0, PALETTE.length - 1, 1, null, "");
    }

    private static Opt picker(String key, String label, int rgb) {
        return new Opt(key, label, PICKER, rgb & 0xFFFFFF, 0, 0xFFFFFF, 1, null, "");
    }

    /** The "shadow" (rounded dark box behind a HUD element) options shared by every HUD mod. */
    private static void backdrop(List<Opt> l) {
        l.add(toggle("bg", "Shadow", true));
        l.add(slider("round", "Shadow roundness", 3, 0, 10, 1, ""));
        l.add(slider("bgalpha", "Shadow opacity", 50, 0, 100, 5, "%"));
    }

    private static void textStyle(List<Opt> l) {
        l.add(color("color", "Text color", 0));
        l.add(toggle("tshadow", "Text shadow", false));
    }

    static {
        List<Opt> fps = new ArrayList<>();
        fps.add(toggle("label", "Show \"FPS\" label", true));
        backdrop(fps);
        textStyle(fps);
        BY_MOD.put("fps_display", fps);

        List<Opt> cps = new ArrayList<>();
        cps.add(toggle("label", "Show \"CPS\" label", true));
        backdrop(cps);
        textStyle(cps);
        BY_MOD.put("cps_display", cps);

        List<Opt> as = new ArrayList<>();
        as.add(choice("mode", "Durability", 0, "Value", "Percent"));
        as.add(toggle("colordur", "Color by durability", true));
        as.add(toggle("icons", "Show item icons", true));
        as.add(toggle("held", "Show held weapon / tool", true));
        as.add(toggle("pbar", "Percentage bar", true));
        as.add(toggle("ench", "Show enchantments", false));
        backdrop(as);
        textStyle(as);
        BY_MOD.put("armor_status", as);

        List<Opt> ab = new ArrayList<>();
        ab.add(choice("layout", "Layout", 0, "Horizontal", "Vertical"));
        ab.add(toggle("bar", "Durability bar", true));
        backdrop(ab);
        BY_MOD.put("armor_bar", ab);

        List<Opt> bi = new ArrayList<>();
        bi.add(toggle("icon", "Show block icon", true));
        backdrop(bi);
        textStyle(bi);
        BY_MOD.put("block_indicator", bi);

        List<Opt> bo = new ArrayList<>();
        bo.add(choice("mode", "Highlight", 2, "Border only", "Whole block", "Both"));
        bo.add(color("bcolor", "Border color", 0));
        bo.add(slider("balpha", "Border opacity", 90, 10, 100, 5, "%"));
        bo.add(color("fcolor", "Overlay color", 1));
        bo.add(slider("falpha", "Overlay opacity", 25, 5, 80, 5, "%"));
        BY_MOD.put("block_overlay", bo);

        List<Opt> ai = new ArrayList<>();
        ai.add(choice("style", "Indicator", 0, "Vanilla crosshair", "Fhasix meter"));
        ai.add(toggle("charge", "Show bow / crossbow / trident charge", true));
        ai.add(toggle("bar", "Show bar", true));
        ai.add(toggle("percent", "Show percentage", true));
        ai.add(slider("width", "Bar width", 60, 20, 160, 5, "px"));
        ai.add(color("color", "Charging color", 2));
        ai.add(color("fullcolor", "Full color", 4));
        backdrop(ai);
        BY_MOD.put("attack_indicator", ai);

        List<Opt> ah = new ArrayList<>();
        ah.add(choice("mode", "Hide", 0, "During combat", "When idle"));
        ah.add(slider("delay", "Combat timer", 3, 1, 10, 1, " s"));
        ah.add(toggle("ondamage", "Getting hurt counts as combat", true));
        ah.add(toggle("oncombat", "Attacking counts as combat", true));
        ah.add(toggle("chat", "Hide chat", true));
        ah.add(toggle("scoreboard", "Hide scoreboard", true));
        ah.add(toggle("effects", "Hide potion effects", true));
        ah.add(toggle("hotbar", "Hide hotbar", false));
        BY_MOD.put("autohide_hud", ah);

        List<Opt> nc = new ArrayList<>();
        nc.add(toggle("details", "Show error details", true));
        BY_MOD.put("no_crash", nc);

        List<Opt> cam = new ArrayList<>();
        cam.add(toggle("freelook", "Freelook (hold Left Alt)", true));
        cam.add(toggle("third", "Third person while freelooking", true));
        cam.add(slider("distance", "Camera distance", 100, 50, 300, 10, "%"));
        cam.add(slider("shoulder", "Shoulder offset", 0, -100, 100, 5, ""));
        cam.add(slider("height", "Height offset", 0, -100, 100, 5, ""));
        BY_MOD.put("camera", cam);

        List<Opt> uhc = new ArrayList<>();
        uhc.add(toggle("apples", "Bigger dropped apples", true));
        uhc.add(toggle("gold", "Bigger dropped gold ingots", true));
        uhc.add(toggle("heads", "Bigger dropped player heads", true));
        uhc.add(slider("scale", "Dropped item size", 170, 100, 400, 10, "%"));
        uhc.add(toggle("ores", "Ore outlines", true));
        uhc.add(toggle("o_diamond", "Outline diamond ore", true));
        uhc.add(toggle("o_gold", "Outline gold ore", true));
        uhc.add(toggle("o_emerald", "Outline emerald ore", false));
        uhc.add(toggle("o_iron", "Outline iron ore", false));
        uhc.add(slider("oalpha", "Outline opacity", 90, 20, 100, 5, "%"));
        uhc.add(slider("range", "Outline range", 16, 8, 24, 4, " blocks"));
        BY_MOD.put("uhc_overlay", uhc);

        List<Opt> an = new ArrayList<>();
        an.add(toggle("block", "Block-hitting (sword)", true));
        BY_MOD.put("animations", an);

        List<Opt> xh = new ArrayList<>();
        xh.add(choice("style", "Style", 0, "Cross", "Plus", "Dot", "Circle"));
        xh.add(slider("size", "Size", 5, 2, 20, 1, "px"));
        xh.add(slider("gap", "Gap", 2, 0, 10, 1, "px"));
        xh.add(slider("thick", "Thickness", 1, 1, 4, 1, "px"));
        xh.add(slider("opacity", "Opacity", 70, 10, 100, 5, "%"));
        xh.add(color("color", "Color", 0));
        xh.add(toggle("indicator", "Indicator (shrinks on entities)", false));
        BY_MOD.put("custom_crosshair", xh);

        List<Opt> fog = new ArrayList<>();
        fog.add(toggle("color", "Custom color", true));
        fog.add(picker("rgb", "Fog color", 0x7896C8));
        fog.add(slider("dist", "Fog distance", 50, 10, 300, 10, "%"));
        fog.add(toggle("off", "Remove fog", false));
        BY_MOD.put("custom_fog", fog);

        List<Opt> di = new ArrayList<>();
        di.add(toggle("bar", "Show heart bar", true));
        di.add(choice("format", "Show", 0, "Health (20)", "Hearts (10)"));
        di.add(toggle("colored", "Color by health", true));
        di.add(toggle("players", "Players", true));
        di.add(toggle("mobs", "Mobs / animals", true));
        di.add(slider("range", "Max distance", 24, 8, 64, 4, " blocks"));
        BY_MOD.put("damage_indicator", di);

        for (String hudId : new String[]{"coordinates", "potion_effects", "keystrokes"}) {
            List<Opt> l = new ArrayList<>();
            l.add(toggle("bg", "Background", true));
            l.add(toggle("border", "Border", true));
            BY_MOD.put(hudId, l);
        }

        List<Opt> hb = new ArrayList<>();
        hb.add(picker("rgb", "Hitbox color", 0xFFFFFF));
        hb.add(slider("size", "Hitbox size", 100, 50, 200, 5, "%"));
        BY_MOD.put("hitbox", hb);

        List<Opt> fb = new ArrayList<>();
        fb.add(slider("level", "Light level", 15, 1, 15, 1, ""));
        BY_MOD.put("full_bright", fb);

        List<Opt> fv = new ArrayList<>();
        fv.add(slider("fov", "Field of view", 110, 30, 180, 1, "\u00b0"));
        BY_MOD.put("fov_changer", fv);

        List<Opt> tc = new ArrayList<>();
        tc.add(slider("time", "Time of day", 6000, 0, 24000, 250, ""));
        BY_MOD.put("time_changer", tc);

        List<Opt> wc = new ArrayList<>();
        wc.add(choice("weather", "Weather", 0, "Clear", "Rain", "Thunderstorm"));
        BY_MOD.put("weather_changer", wc);

        List<Opt> dir = new ArrayList<>();
        dir.add(toggle("degrees", "Show degrees", true));
        backdrop(dir);
        textStyle(dir);
        BY_MOD.put("direction", dir);
    }

    private NewModOptions() {
    }

    static List<Opt> forMod(String modId) {
        return BY_MOD.getOrDefault(modId, List.of());
    }

    static Opt find(String modId, String key) {
        for (Opt o : forMod(modId)) {
            if (o.key.equals(key)) {
                return o;
            }
        }
        return null;
    }

    static int paletteColor(int index) {
        return PALETTE[Math.max(0, Math.min(PALETTE.length - 1, index))];
    }
}
