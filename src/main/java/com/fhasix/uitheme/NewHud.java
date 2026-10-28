package com.fhasix.uitheme;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry;
import net.fabricmc.fabric.api.client.rendering.v1.hud.VanillaHudElements;
import net.minecraft.block.Block;
import net.minecraft.component.type.ItemEnchantmentsComponent;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.AttackIndicator;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.BowItem;
import net.minecraft.item.CrossbowItem;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.Identifier;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * The in-game side of the Mod Menu:
 *  - registers the Right Shift key (opens NewLauncherScreen while in a world),
 *  - draws the HUD mods: FPS Display, CPS Display, Armor Status, Armor Bar, Block Indicator,
 *  - counts left clicks for CPS,
 *  - starts the Block Overlay (world rendering lives in NewBlockOverlay).
 *
 * Every HUD mod has the same "Shadow" options (a rounded dark box behind it: on/off, roundness,
 * opacity) plus its own; see NewModOptions. HUD text has NO text shadow unless the player turns
 * "Text shadow" on. Positions are dragged in the launcher and saved by NewModSettings.
 */
public final class NewHud {

    public static final String FPS = "fps_display";
    public static final String CPS = "cps_display";
    public static final String ARMOR_STATUS = "armor_status";
    public static final String ARMOR_BAR = "armor_bar";
    public static final String BLOCK_INDICATOR = "block_indicator";
    public static final String ATTACK_INDICATOR = "attack_indicator";
    public static final String DIRECTION = "direction";

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };
    private static final int GREEN = 0xFF3FBF4D;
    private static final int YELLOW = 0xFFFFD43B;
    private static final int RED = 0xFFE73C40;

    private static KeyBinding openKey;
    /** Shared by every Fhasix key binding (Right Shift launcher, Freelook). */
    static KeyBinding.Category CATEGORY;
    private static final ArrayDeque<Long> CLICKS = new ArrayDeque<>();
    private static boolean leftWasDown = false;

    private NewHud() {
    }

    // ------------------------------------------------------------------ elements

    /** One HUD mod: knows its size and how to draw itself at a position. */
    abstract static class El {
        final String id;
        final String safeKey;
        final float defX;
        final float defY;

        El(String id, float defX, float defY) {
            this.id = id;
            this.safeKey = "hud_" + id;
            this.defX = defX;
            this.defY = defY;
        }

        /** {width, height}, or null when there is nothing to draw right now. */
        abstract int[] size(MinecraftClient c, boolean preview);

        abstract void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview);
    }

    /** One drawn HUD element: its rectangle (scaled GUI pixels) and the element that draws it. */
    public static final class Box {
        public final String id;
        public final int x;
        public final int y;
        public final int w;
        public final int h;
        final El el;

        Box(El el, int x, int y, int w, int h) {
            this.id = el.id;
            this.el = el;
            this.x = x;
            this.y = y;
            this.w = w;
            this.h = h;
        }

        public boolean contains(double mx, double my) {
            return mx >= x && mx < x + w && my >= y && my < y + h;
        }
    }

    // --- shared style helpers

    private static void shadowBox(DrawContext g, MinecraftClient c, String id, int x, int y, int w, int h) {
        if (!NewModSettings.optBool(c, id, "bg")) {
            return;
        }
        int a = Math.round(255f * NewModSettings.opt(c, id, "bgalpha") / 100f) & 255;
        NewUi.rrect(g, x, y, w, h, NewModSettings.opt(c, id, "round"),
                (a << 24) | 0x0C0D11, (a << 24) | 0x08090C);
    }

    private static int textColor(MinecraftClient c, String id) {
        return NewModOptions.paletteColor(NewModSettings.opt(c, id, "color"));
    }

    private static boolean textShadow(MinecraftClient c, String id) {
        return NewModSettings.optBool(c, id, "tshadow");
    }

    private static int durabilityColor(float frac) {
        frac = Math.max(0f, Math.min(1f, frac));
        return frac > 0.5f ? NewUi.lerp(YELLOW, GREEN, (frac - 0.5f) * 2f) : NewUi.lerp(RED, YELLOW, frac * 2f);
    }

    private static float durabilityFraction(ItemStack s) {
        int max = s.getMaxDamage();
        return max <= 0 ? 1f : (max - s.getDamage()) / (float) max;
    }

    private static List<ItemStack> sampleArmor;

    /** Worn armor pieces (in slot order); the sample set in preview (launcher open) when none are worn. */
    private static List<ItemStack> armorPieces(MinecraftClient c, boolean preview, boolean keepEmpty) {
        List<ItemStack> out = new ArrayList<>(4);
        boolean any = false;
        if (c.player != null) {
            for (EquipmentSlot s : ARMOR) {
                ItemStack st = c.player.getEquippedStack(s);
                if (!st.isEmpty()) {
                    any = true;
                }
                if (keepEmpty || !st.isEmpty()) {
                    out.add(st);
                }
            }
        }
        if (!any && preview) {
            if (sampleArmor == null) {
                sampleArmor = new ArrayList<>(4);
                Item[] items = {Items.DIAMOND_HELMET, Items.DIAMOND_CHESTPLATE, Items.DIAMOND_LEGGINGS, Items.DIAMOND_BOOTS};
                int[] damage = {60, 140, 90, 20};
                for (int i = 0; i < items.length; i++) {
                    ItemStack st = new ItemStack(items[i]);
                    st.setDamage(damage[i]);
                    sampleArmor.add(st);
                }
            }
            return sampleArmor;
        }
        return any ? out : new ArrayList<>();
    }

    // --- FPS / CPS

    private static final class Counter extends El {
        private final boolean fps;

        Counter(String id, boolean fps, float defX, float defY) {
            super(id, defX, defY);
            this.fps = fps;
        }

        private String text(MinecraftClient c) {
            String n = fps ? String.valueOf(c.getCurrentFps()) : String.valueOf(cps());
            if (NewModSettings.optBool(c, id, "label")) {
                return n + (fps ? " FPS" : " CPS");
            }
            return n;
        }

        @Override
        int[] size(MinecraftClient c, boolean preview) {
            return new int[]{c.textRenderer.getWidth(text(c)) + 10, c.textRenderer.fontHeight + 6};
        }

        @Override
        void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            g.drawText(c.textRenderer, text(c), x + 5, y + 3, textColor(c, id), textShadow(c, id));
        }
    }

    // --- Armor Status: worn pieces (+ held weapon/tool) with durability, percentage bar, enchantments

    private static ItemStack sampleSword;

    private static final class ArmorStatus extends El {
        ArmorStatus(float defX, float defY) {
            super(ARMOR_STATUS, defX, defY);
        }

        private boolean wearingArmor(MinecraftClient c) {
            if (c.player == null) {
                return false;
            }
            for (EquipmentSlot s : ARMOR) {
                if (!c.player.getEquippedStack(s).isEmpty()) {
                    return true;
                }
            }
            return false;
        }

        private List<ItemStack> stacks(MinecraftClient c, boolean preview) {
            List<ItemStack> l = new ArrayList<>(armorPieces(c, preview, false));
            if (NewModSettings.optBool(c, id, "held")) {
                ItemStack held = c.player != null ? c.player.getMainHandStack() : ItemStack.EMPTY;
                if (!held.isEmpty() && held.getMaxDamage() > 0) {
                    l.add(held);
                } else if (preview && !wearingArmor(c)) {
                    if (sampleSword == null) {
                        sampleSword = new ItemStack(Items.DIAMOND_SWORD);
                        sampleSword.setDamage(100);
                    }
                    l.add(sampleSword);
                }
            }
            return l;
        }

        /** Short enchantment list like "Prot IV Unbr III" (up to 3). */
        private String enchantments(ItemStack s) {
            ItemEnchantmentsComponent comp = EnchantmentHelper.getEnchantments(s);
            StringBuilder sb = new StringBuilder();
            int n = 0;
            for (RegistryEntry<Enchantment> e : comp.getEnchantments()) {
                if (n++ >= 3) {
                    break;
                }
                String[] words = Enchantment.getName(e, comp.getLevel(e)).getString().split(" ");
                String name = words[0].length() > 4 ? words[0].substring(0, 4) : words[0];
                String level = words.length > 1 ? " " + words[words.length - 1] : "";
                sb.append(sb.length() == 0 ? "" : "  ").append(name).append(level);
            }
            return sb.toString();
        }

        private String label(MinecraftClient c, ItemStack s) {
            int max = s.getMaxDamage();
            String dur;
            if (max <= 0) {
                dur = "-";
            } else {
                int left = max - s.getDamage();
                dur = NewModSettings.opt(c, id, "mode") == 1 ? Math.round(100f * left / max) + "%" : String.valueOf(left);
            }
            if (NewModSettings.optBool(c, id, "ench")) {
                String e = enchantments(s);
                if (!e.isEmpty()) {
                    return dur + "  " + e;
                }
            }
            return dur;
        }

        private int rowH(MinecraftClient c) {
            return NewModSettings.optBool(c, id, "pbar") ? 21 : 18;
        }

        @Override
        int[] size(MinecraftClient c, boolean preview) {
            List<ItemStack> l = stacks(c, preview);
            if (l.isEmpty()) {
                return null;
            }
            boolean icons = NewModSettings.optBool(c, id, "icons");
            int maxW = 0;
            for (ItemStack s : l) {
                maxW = Math.max(maxW, c.textRenderer.getWidth(label(c, s)));
            }
            return new int[]{8 + (icons ? 20 : 0) + maxW, 8 + l.size() * rowH(c) - 2};
        }

        @Override
        void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            boolean icons = NewModSettings.optBool(c, id, "icons");
            boolean byDur = NewModSettings.optBool(c, id, "colordur");
            boolean pbar = NewModSettings.optBool(c, id, "pbar");
            int base = textColor(c, id);
            boolean ts = textShadow(c, id);
            int rowH = rowH(c);
            int yy = y + 4;
            for (ItemStack s : stacks(c, preview)) {
                if (icons) {
                    g.drawItem(s, x + 4, yy);
                }
                float frac = durabilityFraction(s);
                int col = byDur && s.getMaxDamage() > 0 ? durabilityColor(frac) : base;
                g.drawText(c.textRenderer, label(c, s), x + 4 + (icons ? 20 : 0), yy + 4, col, ts);
                if (pbar && s.getMaxDamage() > 0) {
                    g.fill(x + 4, yy + 17, x + w - 4, yy + 19, 0x66000000);
                    g.fill(x + 4, yy + 17, x + 4 + Math.round((w - 8) * frac), yy + 19, durabilityColor(frac));
                }
                yy += rowH;
            }
        }
    }

    // --- Armor Bar: compact row/column of the four slots with a thin durability bar

    private static final class ArmorBar extends El {
        ArmorBar(float defX, float defY) {
            super(ARMOR_BAR, defX, defY);
        }

        @Override
        int[] size(MinecraftClient c, boolean preview) {
            List<ItemStack> l = armorPieces(c, preview, true);
            boolean any = false;
            for (ItemStack s : l) {
                any |= !s.isEmpty();
            }
            if (!any) {
                return null;
            }
            int slotH = NewModSettings.optBool(c, id, "bar") ? 24 : 20;
            if (NewModSettings.opt(c, id, "layout") == 1) {
                return new int[]{8 + 20, 8 + 4 * slotH + 3 * 2};
            }
            return new int[]{8 + 4 * 20 + 3 * 2, 8 + slotH};
        }

        @Override
        void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            boolean bar = NewModSettings.optBool(c, id, "bar");
            boolean vertical = NewModSettings.opt(c, id, "layout") == 1;
            int slotH = bar ? 24 : 20;
            List<ItemStack> l = armorPieces(c, preview, true);
            for (int i = 0; i < l.size(); i++) {
                ItemStack s = l.get(i);
                int sx = vertical ? x + 4 : x + 4 + i * 22;
                int sy = vertical ? y + 4 + i * (slotH + 2) : y + 4;
                if (s.isEmpty()) {
                    g.fill(sx + 2, sy + 2, sx + 18, sy + 18, 0x14FFFFFF);
                    continue;
                }
                g.drawItem(s, sx + 2, sy + 2);
                if (bar && s.getMaxDamage() > 0) {
                    g.fill(sx + 2, sy + 19, sx + 18, sy + 21, 0x66000000);
                    int fw = Math.round(16f * durabilityFraction(s));
                    g.fill(sx + 2, sy + 19, sx + 2 + fw, sy + 21, durabilityColor(durabilityFraction(s)));
                }
            }
        }
    }

    // --- Block Indicator: name (and icon) of the block you are looking at

    private static final class BlockIndicator extends El {
        private Block lastBlock;
        private ItemStack lastStack = ItemStack.EMPTY;
        private String lastName = "";
        private ItemStack dirt;

        BlockIndicator(float defX, float defY) {
            super(BLOCK_INDICATOR, defX, defY);
        }

        /** Fills lastName / lastStack. False when there is nothing to show. */
        private boolean resolve(MinecraftClient c, boolean preview) {
            if (c.world != null && c.crosshairTarget instanceof BlockHitResult bhr
                    && bhr.getType() == HitResult.Type.BLOCK) {
                Block b = c.world.getBlockState(bhr.getBlockPos()).getBlock();
                if (b != lastBlock) {
                    lastBlock = b;
                    lastName = b.getName().getString();
                    lastStack = new ItemStack(b.asItem());
                }
                return true;
            }
            if (preview) {
                if (dirt == null) {
                    dirt = new ItemStack(Items.DIRT);
                }
                lastBlock = null;
                lastName = "Dirt Block";
                lastStack = dirt;
                return true;
            }
            return false;
        }

        @Override
        int[] size(MinecraftClient c, boolean preview) {
            if (!resolve(c, preview)) {
                return null;
            }
            boolean icon = NewModSettings.optBool(c, id, "icon") && !lastStack.isEmpty();
            return new int[]{8 + (icon ? 20 : 0) + c.textRenderer.getWidth(lastName), icon ? 24 : c.textRenderer.fontHeight + 6};
        }

        @Override
        void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview) {
            if (!resolve(c, preview)) {
                return;
            }
            shadowBox(g, c, id, x, y, w, h);
            boolean icon = NewModSettings.optBool(c, id, "icon") && !lastStack.isEmpty();
            if (icon) {
                g.drawItem(lastStack, x + 4, y + (h - 16) / 2);
            }
            g.drawText(c.textRenderer, lastName, x + 4 + (icon ? 20 : 0), y + (h - c.textRenderer.fontHeight) / 2 + 1,
                    textColor(c, id), textShadow(c, id));
        }
    }

    // --- Attack Indicator: by default the NORMAL vanilla crosshair indicator is used (this element
    // draws nothing). Choosing "Fhasix meter" in the settings shows this movable bar instead and
    // hides the vanilla indicator (and restores it when you switch back / disable the mod).

    private static final class AttackIndicatorEl extends El {
        AttackIndicatorEl(float defX, float defY) {
            super(ATTACK_INDICATOR, defX, defY);
        }

        /** Charge of the item being used (bow, crossbow, trident, eating...) or the weapon cooldown. */
        private float progress(MinecraftClient c) {
            if (c.player == null) {
                return 1f;
            }
            if (NewModSettings.optBool(c, id, "charge") && c.player.isUsingItem()) {
                ItemStack s = c.player.getActiveItem();
                int t = c.player.getItemUseTime();
                if (s.isOf(Items.BOW)) {
                    return BowItem.getPullProgress(t);
                }
                if (s.isOf(Items.CROSSBOW)) {
                    return Math.min(1f, t / (float) Math.max(1, CrossbowItem.getPullTime(s, c.player)));
                }
                if (s.isOf(Items.TRIDENT)) {
                    return Math.min(1f, t / 10f);
                }
                int max = s.getMaxUseTime(c.player);
                if (max > 0 && max < 72000) {
                    return Math.min(1f, t / (float) max);
                }
            }
            return Math.max(0f, Math.min(1f, c.player.getAttackCooldownProgress(0f)));
        }

        @Override
        int[] size(MinecraftClient c, boolean preview) {
            if (NewModSettings.opt(c, id, "style") != 1) {
                return null; // vanilla crosshair indicator is in use
            }
            boolean bar = NewModSettings.optBool(c, id, "bar");
            boolean pct = NewModSettings.optBool(c, id, "percent");
            if (!bar && !pct) {
                bar = true;
            }
            int bw = bar ? NewModSettings.opt(c, id, "width") : 0;
            int tw = pct ? c.textRenderer.getWidth("100%") : 0;
            return new int[]{10 + bw + (bar && pct ? 6 : 0) + tw, c.textRenderer.fontHeight + 6};
        }

        @Override
        void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            boolean bar = NewModSettings.optBool(c, id, "bar");
            boolean pct = NewModSettings.optBool(c, id, "percent");
            if (!bar && !pct) {
                bar = true;
            }
            float p = progress(c);
            int cx = x + 5;
            if (bar) {
                int bw = NewModSettings.opt(c, id, "width");
                int by = y + (h - 6) / 2;
                int col = NewModOptions.paletteColor(NewModSettings.opt(c, id, p >= 0.999f ? "fullcolor" : "color"));
                g.fill(cx, by, cx + bw, by + 6, 0x66000000);
                g.fill(cx, by, cx + Math.round(bw * p), by + 6, col);
                cx += bw + 6;
            }
            if (pct) {
                g.drawText(c.textRenderer, Math.round(p * 100f) + "%", cx, y + 3, 0xFFFFFFFF, false);
            }
        }
    }

    /**
     * Charge of a bow / crossbow / trident being drawn (0..1), or -1 when none is. Used for the small
     * bar under the crosshair, which works like the vanilla sword cooldown indicator.
     */
    private static float weaponCharge(MinecraftClient c) {
        if (c.player == null || !c.player.isUsingItem()) {
            return -1f;
        }
        ItemStack s = c.player.getActiveItem();
        int t = c.player.getItemUseTime();
        if (s.isOf(Items.BOW)) {
            return BowItem.getPullProgress(t);
        }
        if (s.isOf(Items.CROSSBOW)) {
            return Math.min(1f, t / (float) Math.max(1, CrossbowItem.getPullTime(s, c.player)));
        }
        if (s.isOf(Items.TRIDENT)) {
            return Math.min(1f, t / 10f);
        }
        return -1f;
    }

    /** Attack Indicator, vanilla-crosshair style: bow / crossbow / trident charge bar under the crosshair. */
    private static void drawCrosshairCharge(DrawContext g, MinecraftClient c) {
        if (c.player == null || c.currentScreen != null || !c.options.getPerspective().isFirstPerson()
                || !NewModSettings.isEnabled(c, ATTACK_INDICATOR)
                || NewModSettings.opt(c, ATTACK_INDICATOR, "style") != 0
                || !NewModSettings.optBool(c, ATTACK_INDICATOR, "charge")) {
            return;
        }
        float p = weaponCharge(c);
        if (p < 0f) {
            return;
        }
        int cx = g.getScaledWindowWidth() / 2;
        int cy = g.getScaledWindowHeight() / 2;
        int col = NewModOptions.paletteColor(NewModSettings.opt(c, ATTACK_INDICATOR, p >= 0.999f ? "fullcolor" : "color"));
        g.fill(cx - 9, cy + 8, cx + 9, cy + 13, 0xAA000000);
        g.fill(cx - 8, cy + 9, cx + 8, cy + 12, 0xFF3A3A3A);
        g.fill(cx - 8, cy + 9, cx - 8 + Math.round(16f * Math.max(0f, Math.min(1f, p))), cy + 12, col);
    }

    /**
     * Keeps the vanilla crosshair attack indicator OFF while the Fhasix meter is the chosen style, and
     * puts the player's original setting back afterwards. The original value is remembered in the mod
     * settings (key "_prev", ordinal + 1) so it survives a restart.
     */
    private static void syncVanillaIndicator(MinecraftClient c) {
        if (c.options == null) {
            return;
        }
        boolean meter = NewModSettings.isEnabled(c, ATTACK_INDICATOR) && NewModSettings.opt(c, ATTACK_INDICATOR, "style") == 1;
        int prev = NewModSettings.opt(c, ATTACK_INDICATOR, "_prev");
        if (meter && prev == 0) {
            AttackIndicator current = c.options.getAttackIndicator().getValue();
            if (current != AttackIndicator.OFF) {
                NewModSettings.setOpt(c, ATTACK_INDICATOR, "_prev", current.ordinal() + 1);
                NewModSettings.save(c);
                c.options.getAttackIndicator().setValue(AttackIndicator.OFF);
                c.options.write();
            }
        } else if (!meter && prev > 0) {
            AttackIndicator[] all = AttackIndicator.values();
            c.options.getAttackIndicator().setValue(all[Math.min(all.length - 1, prev - 1)]);
            c.options.write();
            NewModSettings.setOpt(c, ATTACK_INDICATOR, "_prev", 0);
            NewModSettings.save(c);
        }
    }

    // --- Direction: compass direction you are facing (N, NE, E ...) and optionally the bearing

    private static final class Direction extends El {
        private static final String[] NAMES = {"N", "NE", "E", "SE", "S", "SW", "W", "NW"};

        Direction(float defX, float defY) {
            super(DIRECTION, defX, defY);
        }

        private String text(MinecraftClient c) {
            float yaw = c.player == null ? 0f : c.player.getYaw();
            float bearing = ((yaw + 180f) % 360f + 360f) % 360f; // Minecraft yaw 0 = south, so +180 makes 0 = north
            String s = NAMES[Math.round(bearing / 45f) % 8];
            if (NewModSettings.optBool(c, id, "degrees")) {
                s += " " + Math.round(bearing) + "\u00B0";
            }
            return s;
        }

        @Override
        int[] size(MinecraftClient c, boolean preview) {
            return new int[]{c.textRenderer.getWidth(text(c)) + 10, c.textRenderer.fontHeight + 6};
        }

        @Override
        void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview) {
            shadowBox(g, c, id, x, y, w, h);
            g.drawText(c.textRenderer, text(c), x + 5, y + 3, textColor(c, id), textShadow(c, id));
        }
    }

    private static final El[] ELEMENTS = {
            new Counter(FPS, true, 0.012f, 0.02f),
            new Counter(CPS, false, 0.012f, 0.085f),
            new ArmorStatus(0.012f, 0.17f),
            new ArmorBar(0.30f, 0.80f),
            new BlockIndicator(0.44f, 0.06f),
            new AttackIndicatorEl(0.44f, 0.56f),
            new Direction(0.46f, 0.012f),
            new NewHudExtras.Coordinates(0.012f, 0.78f),
            new NewHudExtras.PotionEffects(0.80f, 0.30f),
            new NewHudExtras.Keystrokes(0.86f, 0.62f)
    };

    // ------------------------------------------------------------------ registration

    /** Call once from the client entrypoint. */
    public static void init() {
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("fhasixuitheme", "modmenu"));
        CATEGORY = category;
        openKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.fhasixuitheme.modmenu",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                category
        ));

        ClientTickEvents.END_CLIENT_TICK.register(NewSafe.tick("hud_tick", client -> {
            boolean fire = false;
            while (openKey.wasPressed()) {
                fire = true;
            }
            if (fire && client.player != null && client.currentScreen == null) {
                client.setScreen(new NewLauncherScreen());
            }
            syncVanillaIndicator(client);
        }));

        // If HudElementRegistry/VanillaHudElements don't resolve on this Fabric API build, the
        // older equivalent is: HudRenderCallback.EVENT.register((context, tickCounter) -> render(context));
        HudElementRegistry.attachElementAfter(
                VanillaHudElements.MISC_OVERLAYS,
                Identifier.of("fhasixuitheme", "mod_hud"),
                NewSafe.hud("hud_elements", (context, tickCounter) -> render(context))
        );

        HudElementRegistry.attachElementAfter(
                VanillaHudElements.CROSSHAIR,
                Identifier.of("fhasixuitheme", "charge_indicator"),
                NewSafe.hud("charge_indicator", (context, tickCounter) -> drawCrosshairCharge(context, MinecraftClient.getInstance()))
        );

        NewBlockOverlay.init();
        NewCamera.init(category);
        NewAutohide.init();
        NewOreHighlight.init();
        NewNoBossBar.init();
        NewCrosshair.init();
        NewDamageIndicator.init();
        NewHitbox.init();
        NewShulkerTooltips.init();
        NewScreenLogo.init();
        NewPerf.init();
        NewNoScoreboard.init();
        NewFullBright.init();
        NewFovChanger.init();
        NewTimeChanger.init();
        NewWeatherChanger.init();
        NewTntTimer.init();
        NewDarkMode.init();
    }

    private static void render(DrawContext context) {
        MinecraftClient c = MinecraftClient.getInstance();
        pollClicks(c);
        if (c.player == null) {
            return;
        }
        for (Box b : boxes(c, context.getScaledWindowWidth(), context.getScaledWindowHeight())) {
            drawBox(context, c, b);
        }
    }

    public static void drawBox(DrawContext context, MinecraftClient c, Box b) {
        if (NewSafe.disabled(b.el.safeKey)) {
            return;
        }
        try {
            b.el.draw(context, c, b.x, b.y, b.w, b.h, c.currentScreen instanceof NewLauncherScreen);
        } catch (Exception | LinkageError t) {
            NewSafe.failed(b.el.safeKey, t);
        }
    }

    /** The currently ENABLED HUD elements that have something to show, with their rectangles. */
    public static List<Box> boxes(MinecraftClient c, int sw, int sh) {
        List<Box> out = new ArrayList<>(ELEMENTS.length);
        boolean preview = c.currentScreen instanceof NewLauncherScreen;
        for (El el : ELEMENTS) {
            if (!NewModSettings.isEnabled(c, el.id)) {
                continue;
            }
            int[] size = null;
            if (!NewSafe.disabled(el.safeKey)) {
                try {
                    size = el.size(c, preview);
                } catch (Exception | LinkageError t) {
                    NewSafe.failed(el.safeKey, t);
                }
            }
            if (size == null) {
                continue;
            }
            float[] p = NewModSettings.position(c, el.id, el.defX, el.defY);
            int x = Math.max(0, Math.min(Math.max(0, sw - size[0]), Math.round(p[0] * sw)));
            int y = Math.max(0, Math.min(Math.max(0, sh - size[1]), Math.round(p[1] * sh)));
            out.add(new Box(el, x, y, size[0], size[1]));
        }
        return out;
    }

    // ------------------------------------------------------------------ CPS

    private static void pollClicks(MinecraftClient c) {
        long now = System.currentTimeMillis();
        boolean down = GLFW.glfwGetMouseButton(c.getWindow().getHandle(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !leftWasDown && c.currentScreen == null && c.player != null) {
            CLICKS.addLast(now);
        }
        leftWasDown = down;
        prune(now);
    }

    private static void prune(long now) {
        long cutoff = now - 1000L;
        while (!CLICKS.isEmpty() && CLICKS.peekFirst() < cutoff) {
            CLICKS.pollFirst();
        }
    }

    /** Left clicks in the last second. */
    public static int cps() {
        prune(System.currentTimeMillis());
        return CLICKS.size();
    }
}
