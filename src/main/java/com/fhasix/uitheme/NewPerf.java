package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.client.option.ParticlesMode;
import net.minecraft.entity.Entity;
import net.minecraft.entity.ExperienceOrbEntity;
import net.minecraft.entity.decoration.AbstractDecorationEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.ItemEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.particle.ParticleEffect;
import net.minecraft.particle.ParticleType;
import net.minecraft.particle.ParticleTypes;
import net.minecraft.util.math.ChunkPos;

import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * NEW. Performance: the FPS modes (Particles, Culling, Lazy Chunk Loading, Render Distance,
 * Simulation Distance, Entity Detail, Visual Details) and the device profiles (High / Medium / Low end)
 * chosen on first launch. Every mode has an On/Off switch and a value: Small, Medium or High, where
 * High = the most FPS. State lives in the normal mod settings ("perf.<mode>.on" / "perf.<mode>.lvl").
 *
 * Modes that change a Minecraft video option remember the player's own value and put it back when
 * the mode is switched off. Particles / Culling / Lazy chunks are checked live by small mixins.
 */
public final class NewPerf {

    static final String MOD = "perf";

    static final int PARTICLES = 0;
    static final int CULLING = 1;
    static final int LAZY = 2;
    static final int RENDER = 3;
    static final int SIM = 4;
    static final int ENTITY = 5;
    static final int VISUAL = 6;
    static final int FRAME = 7;
    static final int EFFECTS = 8;
    static final int HUD = 9;
    static final int BACKGROUND = 10;
    static final int TEXTURE = 11;
    static final int MENU = 12;
    static final int COUNT = 13;

    static final String[] IDS = {"particles", "culling", "lazy", "render", "sim", "entity", "visual",
            "frame", "effects", "hud", "background", "texture", "menu"};
    static final String[] LABELS = {"Particles", "Culling", "Lazy Chunk Loading", "Render Distance",
            "Simulation Distance", "Entity Detail", "Visual Details", "Frame Rate", "Effects & Animations",
            "HUD Optimizer", "Background Limiter", "Texture Detail", "Menu FPS Saver"};
    static final String[] LEVELS = {"Small", "Medium", "High"};
    static final String[][] DESC = {
            {"Hides potion and spell effect particles.",
                    "Also hides water, lava, smoke and fire particles.",
                    "Hides every single particle in the game. Most FPS."},
            {"Fast leaves: leaf faces you cannot see are skipped.",
                    "Also skips entities far away and far dropped items.",
                    "Skips every far entity, armor stand, item frame and item. Most FPS."},
            {"New chunks show up about 3 seconds later than normal.",
                    "New chunks show up about 6 seconds later than normal.",
                    "Far chunks wait until you move into the next chunk. Most FPS."},
            {"Caps the render distance at 10 chunks.",
                    "Caps the render distance at 6 chunks.",
                    "Caps the render distance at 2 chunks. Most FPS."},
            {"Caps the simulation distance at 10 chunks.",
                    "Caps the simulation distance at 6 chunks.",
                    "Caps the simulation distance at 5 chunks. Most FPS."},
            {"Entities are only drawn a bit closer.",
                    "Entities only nearby, no entity shadows.",
                    "Very short entity distance, no shadows. Most FPS."},
            {"Fast clouds and softer biome blending.",
                    "Fast clouds, no smooth lighting, little biome blending.",
                    "No clouds, no smooth lighting, no biome blending. Most FPS."},
            {"Turns VSync off.",
                    "VSync off and no frame limit.",
                    "VSync off and no frame limit. Most FPS."},
            {"No view bobbing.",
                    "No bobbing, FOV effects, distortion effects or menu blur.",
                    "Everything from Medium, plus no enchant glint animation. Most FPS."},
            {"Fhasix overlays (hitboxes, health, TNT timer) draw fewer things, no UI fade animations.",
                    "Fhasix overlays draw very few things and over a short range.",
                    "Every extra Fhasix overlay is switched off. Most FPS."},
            {"Lowers the priority of loading and download threads a little.",
                    "Lowers background loading threads a lot so the game thread comes first.",
                    "Background loading gets the lowest priority there is. Most FPS."},
            {"Texture mipmap level 2.",
                    "Texture mipmap level 1.",
                    "No mipmaps: the cheapest textures. Textures reload once when you change this. Most FPS."},
            {"Caps the frame rate at 60 while a menu is open, so menus use less power.",
                    "Caps the frame rate at 30 while a menu is open.",
                    "Caps the frame rate at 15 while a menu is open. Gameplay FPS is not touched."}
    };
    /** Which tab of the Performance screen a mode is on: 1 = World, 2 = Video, 3 = System. */
    static final int[] TAB = {1, 1, 1, 1, 1, 1, 2, 2, 2, 3, 3, 2, 3};
    static final int[] ICON = {37, 38, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49};
    static final boolean[] NEWBADGE = {false, false, false, false, false, false, false, true, true, true, true, true, true};

    /** One card per mode in the Performance view of the Mod Menu (ids are "perf_" + mode id). */
    static final java.util.List<NewModSettings.ModDef> DEFS = new java.util.ArrayList<>();

    static {
        for (int i = 0; i < COUNT; i++) {
            int tag = TAB[i] == 1 ? NewModSettings.TAG_HUD : TAB[i] == 2 ? NewModSettings.TAG_HYPIXEL : NewModSettings.TAG_PVP;
            DEFS.add(new NewModSettings.ModDef("perf_" + IDS[i], LABELS[i], tag, NEWBADGE[i], true, ICON[i]));
        }
    }

    static int indexOf(String perfId) {
        for (int i = 0; i < COUNT; i++) {
            if (("perf_" + IDS[i]).equals(perfId)) {
                return i;
            }
        }
        return -1;
    }

    static final int DEVICE_NONE = 0;
    static final int DEVICE_HIGH = 1;
    static final int DEVICE_MEDIUM = 2;
    static final int DEVICE_LOW = 3;
    static final String[] DEVICE_NAMES = {"Not chosen", "High End", "Medium", "Low End"};

    private NewPerf() {
    }

    // ------------------------------------------------------------------ state

    /**
     * Cached state per mode: -1 = off, 0..2 = on at that value. Refreshed every tick and on every change, so the
     * per-particle / per-entity checks are plain array reads (no settings lookup, no string building).
     */
    private static final int[] CACHE = {-1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1};

    private static void refreshCache(MinecraftClient c) {
        for (int m = 0; m < COUNT; m++) {
            CACHE[m] = on(c, m) ? level(c, m) : -1;
        }
    }

    static boolean on(MinecraftClient c, int mode) {
        return NewModSettings.optBool(c, MOD, IDS[mode] + ".on");
    }

    static int level(MinecraftClient c, int mode) {
        return Math.max(0, Math.min(2, NewModSettings.opt(c, MOD, IDS[mode] + ".lvl")));
    }

    static void setOn(MinecraftClient c, int mode, boolean v) {
        NewModSettings.setOpt(c, MOD, IDS[mode] + ".on", v ? 1 : 0);
        NewModSettings.save(c);
        refreshCache(c);
        tick(c);
    }

    static void setLevel(MinecraftClient c, int mode, int lvl) {
        NewModSettings.setOpt(c, MOD, IDS[mode] + ".lvl", Math.max(0, Math.min(2, lvl)));
        NewModSettings.save(c);
        refreshCache(c);
        tick(c);
    }

    static boolean setupDone(MinecraftClient c) {
        return NewModSettings.optBool(c, MOD, "setup");
    }

    static int device(MinecraftClient c) {
        return Math.max(0, Math.min(3, NewModSettings.opt(c, MOD, "device")));
    }

    // ------------------------------------------------------------------ init

    private static boolean selectorShown = false;

    static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("perf", NewPerf::tick));
        // first launch: ask what device this is, over the title screen
        ScreenEvents.AFTER_INIT.register((client, screen, w, h) -> {
            NewSafe.run("perf_selector", () -> {
                if (screen instanceof TitleScreen && !selectorShown && !setupDone(client)) {
                    selectorShown = true;
                    client.execute(() -> client.setScreen(new NewDeviceSelectorScreen(null)));
                }
            });
        });
    }

    // ------------------------------------------------------------------ live video-option overrides

    private static final int[] applied = new int[COUNT];

    static {
        java.util.Arrays.fill(applied, -1);
    }

    private static Integer origView;
    private static Integer origSim;
    private static Double origEntityDist;
    private static Boolean origShadows;
    private static Boolean origAo;
    private static Integer origBlend;
    private static CloudRenderMode origClouds;
    private static GraphicsMode origGraphics;
    private static Boolean origVsync;
    private static Integer origMaxFps;
    private static Boolean origBob;
    private static Double origFovFx;
    private static Double origDistort;
    private static Integer origMenuBlur;
    private static Double origGlintStr;
    private static Double origGlintSpeed;
    private static Integer origMip;
    private static Integer menuPrev;
    private static int tickCount = 0;

    static void tick(MinecraftClient c) {
        if (c == null || c.options == null) {
            return;
        }
        refreshCache(c);
        // never touch video options while the game is still loading (changing them starts a reload)
        if (c.getOverlay() != null) {
            return;
        }
        for (int mode : OVERRIDES) {
            int sig = on(c, mode) ? 1 + level(c, mode) : 0;
            if (sig != applied[mode]) {
                applied[mode] = sig;
                final int fm = mode;
                NewSafe.run("perf_" + IDS[mode], () -> apply(c, fm, sig));
            }
        }
        NewSafe.run("perf_lazy_release", () -> releaseHeld(c));
        NewSafe.run("perf_menu", () -> menuSaver(c));
        if (++tickCount % 100 == 0 || tickCount == 1) {
            NewSafe.run("perf_background", () -> limitBackground(c));
        }
    }

    private static final int[] OVERRIDES = {CULLING, RENDER, SIM, ENTITY, VISUAL, FRAME, EFFECTS, TEXTURE};

    private static void apply(MinecraftClient c, int mode, int sig) {
        GameOptions o = c.options;
        int lvl = sig - 1;
        switch (mode) {
            case CULLING:
                if (sig > 0) {
                    if (origGraphics == null) {
                        origGraphics = o.getPreset().getValue();
                    }
                    o.getPreset().setValue(GraphicsMode.FAST); // fast leaves
                } else if (origGraphics != null) {
                    o.getPreset().setValue(origGraphics);
                    origGraphics = null;
                }
                break;
            case RENDER:
                if (sig > 0) {
                    if (origView == null) {
                        origView = o.getViewDistance().getValue();
                    }
                    o.getViewDistance().setValue(Math.min(origView, new int[]{10, 6, 2}[lvl]));
                } else if (origView != null) {
                    o.getViewDistance().setValue(origView);
                    origView = null;
                }
                break;
            case SIM:
                if (sig > 0) {
                    if (origSim == null) {
                        origSim = o.getSimulationDistance().getValue();
                    }
                    o.getSimulationDistance().setValue(Math.min(origSim, new int[]{10, 6, 5}[lvl]));
                } else if (origSim != null) {
                    o.getSimulationDistance().setValue(origSim);
                    origSim = null;
                }
                break;
            case ENTITY:
                if (sig > 0) {
                    if (origEntityDist == null) {
                        origEntityDist = o.getEntityDistanceScaling().getValue();
                        origShadows = o.getEntityShadows().getValue();
                    }
                    o.getEntityDistanceScaling().setValue(Math.min(origEntityDist, new double[]{0.75, 0.5, 0.5}[lvl]));
                    o.getEntityShadows().setValue(lvl == 0 ? origShadows : Boolean.FALSE);
                } else if (origEntityDist != null) {
                    o.getEntityDistanceScaling().setValue(origEntityDist);
                    o.getEntityShadows().setValue(origShadows);
                    origEntityDist = null;
                    origShadows = null;
                }
                break;
            case VISUAL:
                if (sig > 0) {
                    if (origClouds == null) {
                        origClouds = o.getCloudRenderMode().getValue();
                        origAo = o.getAo().getValue();
                        origBlend = o.getBiomeBlendRadius().getValue();
                    }
                    CloudRenderMode clouds = lvl == 2 ? CloudRenderMode.OFF
                            : (origClouds == CloudRenderMode.OFF ? CloudRenderMode.OFF : CloudRenderMode.FAST);
                    o.getCloudRenderMode().setValue(clouds);
                    o.getAo().setValue(lvl == 0 ? origAo : Boolean.FALSE);
                    o.getBiomeBlendRadius().setValue(Math.min(origBlend, new int[]{3, 1, 0}[lvl]));
                } else if (origClouds != null) {
                    o.getCloudRenderMode().setValue(origClouds);
                    o.getAo().setValue(origAo);
                    o.getBiomeBlendRadius().setValue(origBlend);
                    origClouds = null;
                    origAo = null;
                    origBlend = null;
                }
                break;
            case FRAME:
                if (sig > 0) {
                    if (origVsync == null) {
                        origVsync = o.getEnableVsync().getValue();
                        origMaxFps = o.getMaxFps().getValue();
                    }
                    o.getEnableVsync().setValue(Boolean.FALSE);
                    if (lvl >= 1) {
                        o.getMaxFps().setValue(260); // 260 = unlimited
                        if (menuPrev != null) {
                            menuPrev = 260;
                        }
                    }
                } else if (origVsync != null) {
                    o.getEnableVsync().setValue(origVsync);
                    o.getMaxFps().setValue(origMaxFps);
                    if (menuPrev != null) {
                        menuPrev = origMaxFps;
                    }
                    origVsync = null;
                    origMaxFps = null;
                }
                break;
            case EFFECTS:
                if (sig > 0) {
                    if (origBob == null) {
                        origBob = o.getBobView().getValue();
                        origFovFx = o.getFovEffectScale().getValue();
                        origDistort = o.getDistortionEffectScale().getValue();
                        origMenuBlur = o.getMenuBackgroundBlurriness().getValue();
                        origGlintStr = o.getGlintStrength().getValue();
                        origGlintSpeed = o.getGlintSpeed().getValue();
                    }
                    o.getBobView().setValue(Boolean.FALSE);
                    o.getFovEffectScale().setValue(lvl >= 1 ? 0.0 : origFovFx);
                    o.getDistortionEffectScale().setValue(lvl >= 1 ? 0.0 : origDistort);
                    o.getMenuBackgroundBlurriness().setValue(lvl >= 1 ? 0 : origMenuBlur);
                    o.getGlintStrength().setValue(lvl >= 2 ? 0.0 : origGlintStr);
                    o.getGlintSpeed().setValue(lvl >= 2 ? 0.0 : origGlintSpeed);
                } else if (origBob != null) {
                    o.getBobView().setValue(origBob);
                    o.getFovEffectScale().setValue(origFovFx);
                    o.getDistortionEffectScale().setValue(origDistort);
                    o.getMenuBackgroundBlurriness().setValue(origMenuBlur);
                    o.getGlintStrength().setValue(origGlintStr);
                    o.getGlintSpeed().setValue(origGlintSpeed);
                    origBob = null;
                    origFovFx = null;
                    origDistort = null;
                    origMenuBlur = null;
                    origGlintStr = null;
                    origGlintSpeed = null;
                }
                break;
            case TEXTURE:
                if (sig > 0) {
                    if (origMip == null) {
                        origMip = o.getMipmapLevels().getValue();
                    }
                    o.getMipmapLevels().setValue(new int[]{2, 1, 0}[lvl]);
                } else if (origMip != null) {
                    o.getMipmapLevels().setValue(origMip);
                    origMip = null;
                }
                break;
            default:
                break;
        }
    }

    /** Menu FPS Saver: caps the frame rate while any screen is open, then puts the previous cap back. */
    private static void menuSaver(MinecraftClient c) {
        GameOptions o = c.options;
        boolean want = on(c, MENU) && c.currentScreen != null;
        if (want) {
            int cap = new int[]{60, 30, 15}[level(c, MENU)];
            if (menuPrev == null) {
                menuPrev = o.getMaxFps().getValue();
            }
            if (o.getMaxFps().getValue() != cap) {
                o.getMaxFps().setValue(cap);
            }
        } else if (menuPrev != null) {
            o.getMaxFps().setValue(menuPrev);
            menuPrev = null;
        }
    }

    /** Gives the player's own video options back and forgets what was applied. */
    private static void resetOverrides(MinecraftClient c) {
        for (int mode : OVERRIDES) {
            apply(c, mode, 0);
            applied[mode] = -1;
        }
    }

    // ------------------------------------------------------------------ device profiles

    /** One video option failing (a different Minecraft build, another mod) must not stop the rest. */
    private static void safe(Runnable r) {
        NewSafe.run("perf_option", r);
    }

    /**
     * High end: Minecraft's default video settings, FPS modes Small. Medium: render distance 6 and
     * lighter settings, FPS modes Medium. Low end: the lowest of everything (best for Pojav / Mojo
     * launcher and phones), every FPS mode on High.
     */
    static void applyDevice(MinecraftClient c, int device) {
        safe(() -> resetOverrides(c));
        GameOptions o = c.options;
        switch (device) {
            case DEVICE_HIGH:
                safe(() -> o.getViewDistance().setValue(12));
                safe(() -> o.getSimulationDistance().setValue(12));
                safe(() -> o.getEntityDistanceScaling().setValue(1.0));
                safe(() -> o.getEntityShadows().setValue(Boolean.TRUE));
                safe(() -> o.getAo().setValue(Boolean.TRUE));
                safe(() -> o.getBiomeBlendRadius().setValue(2));
                safe(() -> o.getCloudRenderMode().setValue(CloudRenderMode.FANCY));
                safe(() -> o.getParticles().setValue(ParticlesMode.ALL));
                safe(() -> o.getPreset().setValue(GraphicsMode.FANCY));
                break;
            case DEVICE_MEDIUM:
                safe(() -> o.getViewDistance().setValue(6));
                safe(() -> o.getSimulationDistance().setValue(6));
                safe(() -> o.getEntityDistanceScaling().setValue(0.75));
                safe(() -> o.getEntityShadows().setValue(Boolean.FALSE));
                safe(() -> o.getBiomeBlendRadius().setValue(1));
                safe(() -> o.getCloudRenderMode().setValue(CloudRenderMode.FAST));
                safe(() -> o.getParticles().setValue(ParticlesMode.DECREASED));
                break;
            case DEVICE_LOW:
                safe(() -> o.getViewDistance().setValue(2));
                safe(() -> o.getSimulationDistance().setValue(5));
                safe(() -> o.getEntityDistanceScaling().setValue(0.5));
                safe(() -> o.getEntityShadows().setValue(Boolean.FALSE));
                safe(() -> o.getAo().setValue(Boolean.FALSE));
                safe(() -> o.getBiomeBlendRadius().setValue(0));
                safe(() -> o.getCloudRenderMode().setValue(CloudRenderMode.OFF));
                safe(() -> o.getParticles().setValue(ParticlesMode.MINIMAL));
                safe(() -> o.getPreset().setValue(GraphicsMode.FAST));
                break;
            default:
                break;
        }
        safe(o::write);
        for (int m = 0; m < COUNT; m++) {
            boolean enable;
            int lvl;
            if (device == DEVICE_HIGH) {
                enable = m <= LAZY; // particles, culling, lazy chunks - small
                lvl = 0;
            } else if (device == DEVICE_MEDIUM) {
                enable = true;
                lvl = 1;
            } else {
                enable = true;
                lvl = 2;
            }
            NewModSettings.setOpt(c, MOD, IDS[m] + ".on", enable ? 1 : 0);
            NewModSettings.setOpt(c, MOD, IDS[m] + ".lvl", lvl);
        }
        NewModSettings.setOpt(c, MOD, "device", device);
        NewModSettings.setOpt(c, MOD, "setup", 1);
        NewModSettings.save(c);
        refreshCache(c);
        tick(c);
    }

    // ------------------------------------------------------------------ HUD optimizer / background limiter

    /** HUD Optimizer level (0..2), or -1 when the mode is off. */
    static int hudLevel(MinecraftClient c) {
        return CACHE[HUD];
    }

    /** How many things a Fhasix overlay may draw: normal when the HUD Optimizer is off, fewer when on, 0 at High. */
    public static int overlayCap(int normal) {
        int l = hudLevel(MinecraftClient.getInstance());
        return l < 0 ? normal : l == 0 ? Math.max(1, normal / 2) : l == 1 ? Math.max(1, normal / 4) : 0;
    }

    /** Overlay range in blocks: shorter when the HUD Optimizer is on. */
    public static double overlayRange(double normal) {
        int l = hudLevel(MinecraftClient.getInstance());
        return l < 0 ? normal : l == 0 ? normal * 0.7 : normal * 0.4;
    }

    /** True at HUD Optimizer High: the extra Fhasix overlays are not drawn at all. */
    public static boolean overlaysOff() {
        return hudLevel(MinecraftClient.getInstance()) >= 2;
    }

    /** True while the HUD Optimizer is on: Fhasix UI fade animations jump straight to their end value. */
    public static boolean animationsOff() {
        return hudLevel(MinecraftClient.getInstance()) >= 0;
    }

    private static boolean bgApplied = false;

    /** Background Limiter: lowers the priority of the game's background worker threads. */
    private static void limitBackground(MinecraftClient c) {
        boolean wanted = on(c, BACKGROUND);
        if (!wanted && !bgApplied) {
            return;
        }
        int prio = !wanted ? Thread.NORM_PRIORITY : new int[]{4, 2, 1}[level(c, BACKGROUND)];
        try {
            ThreadGroup root = Thread.currentThread().getThreadGroup();
            while (root.getParent() != null) {
                root = root.getParent();
            }
            Thread[] threads = new Thread[root.activeCount() + 32];
            int n = root.enumerate(threads, true);
            for (int i = 0; i < n; i++) {
                Thread t = threads[i];
                String name = t.getName();
                if (name != null && (name.startsWith("Worker-Main") || name.startsWith("Download-")
                        || name.startsWith("IO-Worker") || name.startsWith("Server-Worker"))
                        && t.getPriority() != prio) {
                    t.setPriority(prio);
                }
            }
        } catch (RuntimeException ignored) {
            // not allowed on this platform - the mode just does nothing
        }
        bgApplied = wanted;
    }

    // ------------------------------------------------------------------ particles

    private static Set<ParticleType<?>> small;
    private static Set<ParticleType<?>> medium;

    private static void buildSets() {
        small = Set.<ParticleType<?>>of(ParticleTypes.EFFECT, ParticleTypes.ENTITY_EFFECT,
                ParticleTypes.INSTANT_EFFECT, ParticleTypes.WITCH);
        medium = Set.<ParticleType<?>>of(ParticleTypes.EFFECT, ParticleTypes.ENTITY_EFFECT,
                ParticleTypes.INSTANT_EFFECT, ParticleTypes.WITCH, ParticleTypes.SPLASH, ParticleTypes.BUBBLE,
                ParticleTypes.BUBBLE_POP, ParticleTypes.FALLING_WATER, ParticleTypes.DRIPPING_WATER,
                ParticleTypes.DRIPPING_LAVA, ParticleTypes.FALLING_LAVA, ParticleTypes.LANDING_LAVA,
                ParticleTypes.LAVA, ParticleTypes.RAIN, ParticleTypes.UNDERWATER, ParticleTypes.SMOKE,
                ParticleTypes.LARGE_SMOKE, ParticleTypes.CAMPFIRE_COSY_SMOKE, ParticleTypes.FLAME);
    }

    /** True at Particles High: no particle at all, including block break / block crack particles. */
    public static boolean blockAllParticles() {
        return CACHE[PARTICLES] >= 2;
    }

    /** True = do not spawn this particle. */
    public static boolean blockParticle(ParticleEffect effect) {
        int lvl = CACHE[PARTICLES];
        if (lvl < 0) {
            return false;
        }
        if (lvl >= 2) {
            return true;
        }
        try {
            if (small == null) {
                buildSets();
            }
            return (lvl == 0 ? small : medium).contains(effect.getType());
        } catch (Exception | LinkageError t) {
            NewSafe.failed("perf_particles", t);
            return false;
        }
    }

    // ------------------------------------------------------------------ culling

    /** True = skip drawing this entity. Never the player, never the player's ride. */
    public static boolean cullEntity(Entity e) {
        int lvl = CACHE[CULLING];
        if (lvl < 1) {
            return false; // off, or Small (Small only changes the leaves)
        }
        try {
            return cullEntityImpl(e, lvl);
        } catch (Exception | LinkageError t) {
            NewSafe.failed("perf_culling", t);
            return false;
        }
    }

    private static boolean cullEntityImpl(Entity e, int lvl) {
        MinecraftClient c = MinecraftClient.getInstance();
        if (c == null || c.player == null || e == c.player || e == c.player.getVehicle()) {
            return false;
        }
        double d = e.squaredDistanceTo(c.player);
        boolean item = e instanceof ItemEntity || e instanceof ExperienceOrbEntity;
        if (lvl == 1) {
            return item ? d > 24 * 24 : (!(e instanceof PlayerEntity) && d > 48 * 48);
        }
        if (e instanceof ArmorStandEntity) {
            return d > 12 * 12;
        }
        if (e instanceof AbstractDecorationEntity) {
            return d > 16 * 16;
        }
        if (item) {
            return d > 12 * 12;
        }
        return e instanceof PlayerEntity ? d > 48 * 48 : d > 24 * 24;
    }

    // ------------------------------------------------------------------ lazy chunk loading

    private static final class Held {
        final ClientPlayNetworkHandler handler;
        final ChunkDataS2CPacket packet;
        final int cx;
        final int cz;
        final long due;
        final int anchorX;
        final int anchorZ;
        final net.minecraft.client.world.ClientWorld world;

        Held(ClientPlayNetworkHandler handler, ChunkDataS2CPacket packet, long due, int anchorX, int anchorZ,
             net.minecraft.client.world.ClientWorld world) {
            this.world = world;
            this.handler = handler;
            this.packet = packet;
            this.cx = packet.getChunkX();
            this.cz = packet.getChunkZ();
            this.due = due;
            this.anchorX = anchorX;
            this.anchorZ = anchorZ;
        }
    }

    private static final ConcurrentLinkedQueue<Held> HELD = new ConcurrentLinkedQueue<>();
    private static boolean bypass = false;

    /** True = this chunk packet was taken and will be applied later. Chunks near the player always load at once. */
    public static boolean holdChunk(ClientPlayNetworkHandler handler, ChunkDataS2CPacket packet) {
        return NewSafe.call("perf_lazy", () -> holdChunkImpl(handler, packet), Boolean.FALSE);
    }

    private static boolean holdChunkImpl(ClientPlayNetworkHandler handler, ChunkDataS2CPacket packet) {
        MinecraftClient c = MinecraftClient.getInstance();
        // never hold chunks while joining: the player has to be in the world for a few seconds first,
        // otherwise the spawn chunks (far from the temporary 0,0 position) would never load
        if (bypass || c == null || !c.isOnThread() || c.player == null || c.world == null || !on(c, LAZY)
                || c.player.age < 100) {
            return false;
        }
        ChunkPos pc = c.player.getChunkPos();
        int dist = Math.max(Math.abs(packet.getChunkX() - pc.x), Math.abs(packet.getChunkZ() - pc.z));
        if (dist <= 2) {
            return false;
        }
        int lvl = level(c, LAZY);
        long due = lvl == 0 ? System.currentTimeMillis() + 3500L
                : lvl == 1 ? System.currentTimeMillis() + 6000L : Long.MAX_VALUE;
        HELD.add(new Held(handler, packet, due, pc.x, pc.z, c.world));
        return true;
    }

    /** The server unloaded a chunk we are still holding: forget it instead of loading it later. */
    public static void dropHeld(int cx, int cz) {
        NewSafe.run("perf_lazy", () -> HELD.removeIf(h -> h.cx == cx && h.cz == cz));
    }

    private static void releaseHeld(MinecraftClient c) {
        if (HELD.isEmpty()) {
            return;
        }
        if (c.world == null || c.player == null || c.getNetworkHandler() == null) {
            HELD.clear();
            return;
        }
        boolean lazy = on(c, LAZY);
        int lvl = level(c, LAZY);
        ChunkPos pc = c.player.getChunkPos();
        long now = System.currentTimeMillis();
        Iterator<Held> it = HELD.iterator();
        while (it.hasNext()) {
            Held h = it.next();
            int dist = Math.max(Math.abs(h.cx - pc.x), Math.abs(h.cz - pc.z));
            boolean crossed = lvl == 2 && (pc.x != h.anchorX || pc.z != h.anchorZ);
            if (!lazy || now >= h.due || dist <= 2 || crossed) {
                it.remove();
                if (h.handler == c.getNetworkHandler() && h.world == c.world) {
                    bypass = true;
                    try {
                        h.handler.onChunkData(h.packet);
                    } finally {
                        bypass = false;
                    }
                }
            }
        }
    }
}
