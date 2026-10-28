package com.fhasix.uitheme;

import com.mojang.authlib.GameProfile;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.text.Text;
import net.minecraft.util.Util;

import java.nio.file.Path;

/**
 * FHASIX Skin pop-up - a small floating card anchored under the title-screen diamond icon,
 * deliberately NOT the big-panel layout Store/Cosmetics/Discover share. Inspired by the
 * account-panel reference (avatar + online dot, name, subtitle, soft button, divider, accent
 * button), reworked into a working skin manager.
 *
 * What it does now:
 *  - FIVE SLOTS (the round chips). Each slot holds one custom skin. Click a slot to equip it;
 *    an empty slot equips nothing (your account skin shows). Right-click a chip, or tap the
 *    little red x on the equipped one, to empty it.
 *  - ADDING SKINS: every device has a different file manager, so skins are simply copied into
 *    the fhasix_skins folder. While the pop-up is open the folder is watched: any 64x64 PNG
 *    dropped in lands in the selected slot (or the next empty one) within a second. The accent
 *    button is "Watch Tutorial" and opens the how-to video.
 *  - SKIN SETTINGS expands the card to pick the model for the selected slot: Default (Steve,
 *    wide arms) or Slim (Alex).
 *  - The equipped skin is applied to your own player in-game by mixin/skin.java (client-side).
 *
 * The animation system is unchanged in spirit: the card unfolds out of the icon with a spring
 * ease, content staggers in, the avatar pops/breathes, the wardrobe ring glides, picking a slot
 * squashes and rebounds the avatar, buttons press-squish with a ripple and a shine, the settings
 * section grows the card smoothly, and everything reverses on close.
 */
public class SkinPopupScreen extends Screen {

    private final Screen parent;
    private final int anchorX;
    private final int anchorY;
    private final int anchorSize;

    // ---- palette (account-panel reference theme + the mod's lime accent) ------------------
    private static final int C_SURFACE = 0xFF131419;
    private static final int C_BORDER = 0xFF23222A;
    private static final int C_AVATAR_BG = 0xFF1D1E26;
    private static final int C_BTN = 0xFF23222A;
    private static final int C_BTN_HOT = 0xFF2E2D37;
    private static final int C_BTN_TEXT = 0xFF9E9DA8;
    private static final int C_NAME = 0xFFE6E7EC;
    private static final int C_SUB = 0xFF9FA0A9;
    private static final int C_DIVIDER = 0xFF1F2027;
    private static final int C_LIME = 0xFFD9ED58;
    private static final int C_LIME_HI = 0xFFE7F77F;
    private static final int C_ON_LIME = 0xFF1B2108;
    private static final int C_ONLINE = 0xFF22F25E;
    private static final int C_DANGER = 0xFFE8735A;
    private static final int C_DANGER_BG = 0xFF4A241D;

    // ---- layout ------------------------------------------------------------------------------
    private static final int PAD = 16;
    private static final int AV = 48;
    private static final int CHIP = 30;
    private static final int CHIPS = FhasixSkins.SLOTS;
    private static final int BTN_H = 34;
    private static final int CARD_H = 76;

    /** The accent button opens this tutorial video (how to add skins to the fhasix_skins folder). */
    private static final String TUTORIAL_URL = "https://youtu.be/qmdAiPrwuNk?si=DuWUMiMcYkBcdUiL";

    private static final int UP_IDLE = 0;
    private static final int UP_PICKING = 1;
    private static final int UP_WAITING = 2;
    private static final int UP_SUCCESS = 3;
    private static final int UP_FAIL = 4;

    /** Drawn only if there is no skin at all (no slot skin and no account skin available). */
    private static final int[] FALLBACK_PALETTE =
            {0xFF4B2E1E, 0xFFC69C6D, 0xFFFFFFFF, 0xFF3B5BDB, 0xFFB5865A, 0xFF8A4B3A};

    /** 8x8 fallback face: 0 hair, 1 skin, 2 eye white, 3 eye, 4 shade, 5 mouth. */
    private static final int[][] FACE = {
            {0, 0, 0, 0, 0, 0, 0, 0},
            {0, 0, 0, 0, 0, 0, 0, 0},
            {0, 1, 1, 1, 1, 1, 1, 0},
            {1, 1, 1, 1, 1, 1, 1, 1},
            {1, 2, 3, 1, 1, 3, 2, 1},
            {1, 1, 1, 4, 4, 1, 1, 1},
            {1, 1, 5, 5, 5, 5, 1, 1},
            {1, 1, 1, 1, 1, 1, 1, 1}
    };

    /** Text that slides/fades from its old value to a new one whenever it changes. */
    private static final class SwapText {
        String prev = "";
        String cur = "";
        long start = 0L;

        void set(String s, long now) {
            if (s.equals(cur)) {
                return;
            }
            prev = cur;
            cur = s;
            start = now;
        }
    }

    // ---- geometry (final resting position; the animated rect is derived from it) -----------
    private int popX, popY, popW, popH, innerW;
    private int chipsRelY, softRelY, typeRelY, divRelY, accRelY;

    // ---- state -----------------------------------------------------------------------------
    private SkinTextures skin; // the account's own skin (fallback avatar)
    private String username = "";
    private int selected = 0;
    private int shown = 0;
    private float ringX = 0f;
    private final float[] chipHover = new float[CHIPS];
    private final float[] chipRaw = new float[CHIPS];
    private final long[] chipPulse = new long[CHIPS];

    private long openStart = 0L;
    private long closeStart = 0L;
    private boolean closing = false;
    private boolean dead = false;
    private float pAtClose = 0f;
    private long lastFrame = -1L;
    private int tickCounter = 0;

    private long swapStart = -1L;
    private final SwapText caption = new SwapText();
    private final SwapText subtitle = new SwapText();
    private final SwapText uploadLabel = new SwapText();

    private boolean settingsOpen = false;
    private float settingsT = 0f;
    private float typeRingT = 0f;
    private final float[] typeHover = new float[2];
    private long typePop = -1L;

    private int uploadState = UP_IDLE;
    private long uploadStateStart = 0L;
    private String uploadMsg = "";
    private int awaitingSlot = 0;
    private boolean noRoomShown = false;
    private float hintT = 0f;

    private float softHover = 0f;
    private float accHover = 0f;
    private float softPress = 0f;
    private float accPress = 0f;
    private boolean accWasHover = false;
    private long accShineStart = -1L;

    private int rippleBtn = -1;
    private int rippleX, rippleY;
    private long rippleStart = 0L;

    public SkinPopupScreen(Screen parent, int anchorX, int anchorY, int anchorSize) {
        super(Text.literal("Skins"));
        this.parent = parent;
        this.anchorX = anchorX;
        this.anchorY = anchorY;
        this.anchorSize = anchorSize;
    }

    // =========================================================================================
    // lifecycle
    // =========================================================================================

    @Override
    protected void init() {
        int fh = textRenderer.fontHeight;
        popW = Math.min(228, width - 16);
        innerW = popW - PAD * 2;

        chipsRelY = PAD + AV + 16 + fh + 8;
        softRelY = chipsRelY + CHIP + 16;
        typeRelY = softRelY + BTN_H + 8;
        int fullH = softRelY + BTN_H + 12 + (8 + CARD_H) + 1 + 12 + BTN_H + PAD;

        popX = clampInt(anchorX + anchorSize / 2 - (popW - 44), 8, Math.max(8, width - popW - 8));
        popY = anchorY + anchorSize + 12;
        if (popY + fullH > height - 8) {
            popY = Math.max(8, height - 8 - fullH); // sized for the expanded state so it never has to jump
        }
        updateLayout();

        if (openStart == 0L) {
            openStart = System.nanoTime();
            selected = FhasixSkins.selected(client);
            shown = selected;
            typeRingT = FhasixSkins.isSlim(client, selected) ? 1f : 0f;
            scanForDroppedSkins();
        }
        ringX = chipCx(selected);
        username = client.getSession().getUsername();
        refreshSkin();
    }

    /** Layout that follows the (animated) settings section: everything below it shifts down. */
    private void updateLayout() {
        int extra = Math.round((8 + CARD_H) * settingsT);
        divRelY = softRelY + BTN_H + 12 + extra;
        accRelY = divRelY + 1 + 12;
        popH = accRelY + BTN_H + PAD;
    }

    @Override
    public void tick() {
        if (closing && System.nanoTime() - closeStart > 240_000_000L) {
            client.setScreen(parent);
            return;
        }
        tickCounter++;
        if (tickCounter % 20 == 0) {
            refreshSkin();
        }
        // The folder is how skins get in (every device's file manager differs), so keep
        // watching it the whole time the pop-up is open.
        if (tickCounter % 15 == 0) {
            scanForDroppedSkins();
        }
    }

    @Override
    public void removed() {
        dead = true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void refreshSkin() {
        try {
            GameProfile profile = new GameProfile(client.getSession().getUuidOrNull(), client.getSession().getUsername());
            skin = client.getSkinProvider().supplySkinTextures(profile, false).get();
        } catch (Throwable ignored) {
            // no account skin available - the avatar falls back to the drawn face
        }
    }

    private void startClose(long now) {
        if (closing) {
            return;
        }
        closing = true;
        closeStart = now;
    }

    // =========================================================================================
    // uploading
    // =========================================================================================

    /** On open: pick up anything dropped into the folder while the pop-up was closed. */
    private void scanForDroppedSkins() {
        int target = -1;
        if (!FhasixSkins.hasSkin(client, selected)) {
            target = selected;
        } else {
            for (int i = 0; i < CHIPS; i++) {
                if (!FhasixSkins.hasSkin(client, i)) {
                    target = i;
                    break;
                }
            }
        }
        if (target < 0) {
            target = selected; // every slot is taken: still scan, so a dropped file can be reported as "no room"
        }
        FhasixSkins.importStray(client, target, false, this::onImported);
    }

    private void openTutorial() {
        try {
            Util.getOperatingSystem().open(TUTORIAL_URL);
        } catch (Throwable ignored) {
            // no browser available on this runtime - nothing to do
        }
    }

    private void startUpload(long now) {
        if (uploadState == UP_PICKING) {
            return;
        }
        final int slot = selected;
        uploadState = UP_PICKING;
        uploadStateStart = now;
        FhasixSkins.EXEC.execute(() -> {
            String chosen = null;
            boolean noDialog = false;
            try {
                chosen = FhasixSkins.chooseFile(client); // blocks until the dialog closes
            } catch (Throwable t) {
                noDialog = true;
            }
            final String path = chosen;
            final boolean fallback = noDialog;
            client.execute(() -> afterPicker(slot, path, fallback));
        });
    }

    private void afterPicker(int slot, String path, boolean noDialog) {
        if (noDialog) {
            if (dead) {
                return;
            }
            // Android-style runtime: open the folder and wait for a PNG to be dropped into it.
            FhasixSkins.openFolder(client);
            awaitingSlot = slot;
            uploadState = UP_WAITING;
            uploadStateStart = System.nanoTime();
            return;
        }
        if (path == null) {
            if (!dead) {
                uploadState = UP_IDLE; // dialog cancelled
            }
            return;
        }
        // Store it even if the pop-up was closed while the dialog was open.
        FhasixSkins.assign(client, slot, Path.of(path), result -> onAssigned(slot, result));
    }

    private void onAssigned(int slot, FhasixSkins.Result result) {
        if (dead) {
            return;
        }
        long now = System.nanoTime();
        if (result == FhasixSkins.Result.OK) {
            uploadSucceeded(slot, now);
        } else {
            uploadState = UP_FAIL;
            uploadStateStart = now;
            switch (result) {
                case WRONG_SIZE:
                    uploadMsg = "Needs a 64x64 skin";
                    break;
                case NOT_PNG:
                    uploadMsg = "Not a valid PNG";
                    break;
                default:
                    uploadMsg = "Couldn't read that file";
                    break;
            }
        }
    }

    private void onImported(FhasixSkins.ImportResult res) {
        if (dead) {
            return;
        }
        long now = System.nanoTime();
        if (!res.filled.isEmpty()) {
            uploadSucceeded(res.filled.get(0), now);
            for (int slot : res.filled) {
                chipPulse[slot] = now;
            }
        } else if (res.rejected > 0) {
            uploadState = UP_FAIL;
            uploadStateStart = now;
            uploadMsg = "Needs a 64x64 PNG";
        } else if (res.skipped > 0 && !noRoomShown) {
            noRoomShown = true; // say it once, not every time the folder is re-checked
            uploadState = UP_FAIL;
            uploadStateStart = now;
            uploadMsg = "All 5 slots are full";
        }
    }

    private void uploadSucceeded(int slot, long now) {
        uploadState = UP_SUCCESS;
        uploadStateStart = now;
        uploadMsg = "Added to Slot " + (slot + 1);
        chipPulse[slot] = now;
        if (slot == selected) {
            swapStart = now; // the avatar re-pops with the new skin
        }
    }

    // =========================================================================================
    // rendering
    // =========================================================================================

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float delta) {
        long now = System.nanoTime();
        float dt = lastFrame < 0L ? 0f : Math.min(0.1f, (now - lastFrame) / 1_000_000_000f);
        lastFrame = now;
        long nowMs = now / 1_000_000L;
        float ms = (now - openStart) / 1_000_000f;
        float closeMs = closing ? (now - closeStart) / 1_000_000f : 0f;
        float closeP = closing ? clamp01(closeMs / 220f) : 0f;

        settingsT += ((settingsOpen ? 1f : 0f) - settingsT) * (1f - (float) Math.exp(-11f * dt));
        if (Math.abs((settingsOpen ? 1f : 0f) - settingsT) < 0.002f) {
            settingsT = settingsOpen ? 1f : 0f;
        }
        updateLayout();

        if ((uploadState == UP_SUCCESS || uploadState == UP_FAIL) && now - uploadStateStart > 1_900_000_000L) {
            uploadState = UP_IDLE;
        }
        float hintTarget = uploadState == UP_WAITING && !closing ? 1f : 0f;
        hintT += (hintTarget - hintT) * (1f - (float) Math.exp(-10f * dt));

        // the title screen stays alive behind us, but gets no mouse so nothing under the card reacts
        if (parent != null) {
            parent.render(c, -1, -1, delta);
        }

        float p;
        if (closing) {
            p = pAtClose * (1f - easeInCubic(closeP));
        } else {
            p = easeOutBack(clamp01(ms / 380f), 1.3f);
            pAtClose = p;
        }
        float pc = Math.max(0f, p);

        float dim = closing ? (1f - closeP) : easeOutCubic(clamp01(ms / 320f));
        c.fill(0, 0, width, height, argb(Math.round(0x66 * dim), 0x05060A));

        // the card unfolds out of the icon it was opened from (and folds back into it on close)
        int rx = Math.round(lerp(anchorX, popX, pc));
        int ry = Math.round(lerp(anchorY, popY, pc));
        int rw = Math.max(2, Math.round(lerp(anchorSize, popW, pc)));
        int rh = Math.max(2, Math.round(lerp(anchorSize, popH, pc)));

        if (pc > 0.02f) {
            for (int k = 3; k >= 1; k--) {
                int infl = k * 4;
                roundRect(c, rx - infl, ry - infl + 3, rw + infl * 2, rh + infl * 2, 14 + infl,
                        argb(Math.round(26f / k * Math.min(1f, pc)), 0x000000));
            }
        }
        roundRect(c, rx - 1, ry - 1, rw + 2, rh + 2, 15, C_BORDER);
        roundRect(c, rx, ry, rw, rh, 14, C_SURFACE);

        if (!closing && ms > 240f) {
            drawCaret(c, easeOutCubic(clamp01((ms - 240f) / 200f)));
        }

        // lime "active" pill under the icon that opened this
        float pillT = closing ? 1f - closeP : easeOutBack(clamp01(ms / 320f), 1.6f);
        int pillW = Math.max(0, Math.round(16f * pillT));
        if (pillW > 1) {
            roundRect(c, anchorX + (anchorSize - pillW) / 2, anchorY + anchorSize + 1, pillW, 3, 1, C_LIME);
        }

        float ga = closing ? 1f - clamp01(closeMs / 130f) : 1f;
        if (pc > 0.2f && ga > 0.05f) {
            c.enableScissor(rx + 1, ry + 1, rx + rw - 1, ry + rh - 1);
            drawContent(c, mouseX, mouseY, dt, ms, ga, now, nowMs);
            c.disableScissor();
            drawHint(c, ga, nowMs);
        }
    }

    private void drawCaret(DrawContext c, float t) {
        int n = Math.round(7f * t);
        if (n <= 0) {
            return;
        }
        int cx = clampInt(anchorX + anchorSize / 2, popX + 24, popX + popW - 24);
        int tipY = popY - n;
        for (int r = 0; r < n; r++) {
            c.fill(cx - r - 1, tipY + r, cx + r + 2, tipY + r + 1, C_BORDER);
        }
        for (int r = 0; r < n; r++) {
            c.fill(cx - r, tipY + r, cx + r + 1, tipY + r + 1, C_SURFACE);
        }
    }

    private void drawContent(DrawContext c, int mouseX, int mouseY, float dt, float ms, float ga,
                             long now, long nowMs) {
        drawIdentity(c, ms, ga, now, nowMs);
        drawWardrobe(c, mouseX, mouseY, dt, ms, ga, now, nowMs);
        drawSettings(c, mouseX, mouseY, dt, ga, nowMs);

        float tD = ct(ms, 470f, 420f);
        int dw = Math.round(innerW * tD);
        if (dw > 0) {
            c.fill(popX + PAD, popY + divRelY, popX + PAD + dw, popY + divRelY + 1, withA(C_DIVIDER, ga));
        }

        drawButtons(c, mouseX, mouseY, dt, ms, ga, now, nowMs);
    }

    // ---- avatar, online dot, name + subtitle ----------------------------------------------------

    private void drawIdentity(DrawContext c, float ms, float ga, long now, long nowMs) {
        int fh = textRenderer.fontHeight;
        boolean filled = FhasixSkins.hasSkin(client, selected);

        int avX = popX + PAD;
        int avY = popY + PAD;
        int avCx = avX + AV / 2;
        int avCy = avY + AV / 2;
        float avIn = easeOutBack(clamp01((ms - 110f) / 420f), 1.7f);
        float swapScale = swapScale(now);
        float swapGlow = swapGlow(now);
        float breathe = 0.5f + 0.5f * (float) Math.sin(nowMs / 900.0);
        int floatY = Math.round((float) Math.sin(nowMs / 510.0));

        if (avIn > 0.05f) {
            float ringA = (0.08f + 0.12f * breathe + 0.5f * swapGlow) * ga * clamp01(avIn);
            fillCircle(c, avCx, avCy, AV + 8, argb(Math.round(255f * ringA), C_LIME));
            fillCircle(c, avCx, avCy, AV + 4, C_SURFACE);

            int size = evenAtLeast2(Math.round(AV * Math.max(0f, avIn) * swapScale));
            int ax = avCx - size / 2;
            int ay = avCy + floatY - size / 2;
            fillCircle(c, avCx, avCy + floatY, size, C_AVATAR_BG);
            SkinTextures face = FhasixSkins.preview(client, shown);
            drawFace(c, ax, ay, size, face != null ? face : skin, (nowMs % 4300L) < 140L);
            eraseOutsideCircle(c, ax, ay, size, C_SURFACE);
        }

        float dotT = easeOutBack(clamp01((ms - 260f) / 300f), 2.0f);
        int dd = evenAtLeast2(Math.round(12f * Math.max(0f, dotT)));
        if (dotT > 0.05f) {
            int dcx = avX + AV - 7;
            int dcy = avY + AV - 7 + floatY;
            float hp = (nowMs % 1800L) / 1800f;
            int hd = evenAtLeast2(Math.round(12f + 14f * easeOutCubic(hp)));
            fillCircle(c, dcx, dcy, hd, argb(Math.round(70f * (1f - hp) * ga), C_ONLINE));
            fillCircle(c, dcx, dcy, dd + 6, C_SURFACE);
            fillCircle(c, dcx, dcy, dd, C_ONLINE);
        }

        int nx = popX + PAD + AV + 14;
        int nameMaxW = popX + popW - PAD - nx;
        String nm = ellipsize(username, nameMaxW);
        int ny = popY + PAD + (AV - (fh * 2 + 4)) / 2;
        float tN = ct(ms, 170f, 380f);
        if (tN > 0.01f) {
            int wipe = Math.round(strW(nm) * tN) + 1;
            c.enableScissor(nx, ny - 3, nx + wipe, ny + fh + 3);
            drawStr(c, nm, nx, ny, withA(C_NAME, ga));
            c.disableScissor();
        }

        subtitle.set(filled
                ? "Slot " + (selected + 1) + "  \u00B7  " + (FhasixSkins.isSlim(client, selected) ? "Slim" : "Default")
                : "Account skin", now);
        float tS = ct(ms, 230f, 380f);
        drawSwap(c, subtitle, nx, ny + fh + 4 + Math.round((1f - tS) * 6f), 0, C_SUB, tS * ga, now);
    }

    // ---- wardrobe: five slot chips -----------------------------------------------------------

    private void drawWardrobe(DrawContext c, int mouseX, int mouseY, float dt, float ms, float ga,
                              long now, long nowMs) {
        boolean selFilled = FhasixSkins.hasSkin(client, selected);

        int labelY = popY + PAD + AV + 16;
        float tL = ct(ms, 250f, 360f);
        int labelSlide = Math.round((1f - tL) * 6f);
        drawFaded(c, "Wardrobe", popX + PAD, labelY + labelSlide, C_SUB, tL * ga);
        caption.set("Slot " + (selected + 1) + (selFilled ? "" : "  \u00B7  empty"), now);
        drawSwap(c, caption, popX + popW - PAD, labelY + labelSlide, 1, C_LIME, tL * ga, now);

        int chipCy = popY + chipsRelY + CHIP / 2;
        ringX += (chipCx(selected) - ringX) * (1f - (float) Math.exp(-13f * dt));
        for (int i = 0; i < CHIPS; i++) {
            int cx = chipCx(i);
            double ddx = mouseX - cx;
            double ddy = mouseY - chipCy;
            boolean hov = !closing && ddx * ddx + ddy * ddy <= (CHIP / 2.0) * (CHIP / 2.0);
            chipHover[i] += ((hov ? 1f : 0f) - chipHover[i]) * (1f - (float) Math.exp(-16f * dt));
            chipRaw[i] = clamp01((ms - (290f + 45f * i)) / 360f);
        }

        // expanding pulse behind a chip whose skin was just added
        for (int i = 0; i < CHIPS; i++) {
            if (chipPulse[i] == 0L) {
                continue;
            }
            float u = (now - chipPulse[i]) / 700_000_000f;
            if (u >= 1f) {
                chipPulse[i] = 0L;
                continue;
            }
            int d = evenAtLeast2(Math.round(CHIP + 22f * easeOutCubic(u)));
            fillCircle(c, chipCx(i), chipCy, d, argb(Math.round(110f * (1f - u) * ga), C_LIME));
        }

        for (int i = 0; i < CHIPS; i++) {
            float t = easeOutCubic(chipRaw[i]);
            if (t <= 0.02f) {
                continue;
            }
            int cy = chipCy + Math.round((1f - t) * 8f);
            int ringCol = lerpColor(C_BORDER, C_LIME, 0.55f * chipHover[i]);
            fillCircle(c, chipCx(i), cy, CHIP, argb(Math.round(255f * t * ga), ringCol));
        }
        float ringT = ct(ms, 760f, 300f);
        if (ringT > 0.02f) {
            fillCircle(c, Math.round(ringX), chipCy, CHIP, argb(Math.round(255f * ringT * ga), C_LIME));
        }

        for (int i = 0; i < CHIPS; i++) {
            float t = easeOutCubic(chipRaw[i]);
            if (t <= 0.02f) {
                continue;
            }
            int cx = chipCx(i);
            int cy = chipCy + Math.round((1f - t) * 8f);
            fillCircle(c, cx, cy, CHIP - 4, C_SURFACE);

            int base = 22 + 2 * Math.round(chipHover[i] * 1.2f);
            int d = evenAtLeast2(Math.round(base * easeOutBack(chipRaw[i], 1.7f)));
            SkinTextures face = FhasixSkins.preview(client, i);
            if (face != null) {
                if (d >= 2) {
                    drawFace(c, cx - d / 2, cy - d / 2, d, face, false);
                    eraseOutsideCircle(c, cx - d / 2, cy - d / 2, d, C_SURFACE);
                }
            } else if (FhasixSkins.isLoading(client, i)) {
                float pulse = 0.5f + 0.5f * (float) Math.sin(nowMs / 220.0 + i);
                fillCircle(c, cx, cy, evenAtLeast2(d), lerpColor(0xFF1A1B22, 0xFF2A2933, pulse));
            } else {
                int col = lerpColor(0xFF4A4A55, C_LIME, chipHover[i]);
                int arm = 3 + Math.round(chipHover[i]);
                c.fill(cx - arm, cy - 1, cx + arm, cy + 1, withA(col, t * ga));
                c.fill(cx - 1, cy - arm, cx + 1, cy + arm, withA(col, t * ga));
            }

            // little red x to empty a filled slot: always on the equipped one (touch has no hover)
            if (FhasixSkins.hasSkin(client, i) && (i == selected || chipHover[i] > 0.05f)) {
                float bt = i == selected ? 1f : chipHover[i];
                int bd = evenAtLeast2(Math.round(14f * Math.min(1f, bt) * t));
                if (bd >= 6) {
                    int bx = cx + 11;
                    int by = cy - 11;
                    fillCircle(c, bx, by, bd + 2, C_SURFACE);
                    fillCircle(c, bx, by, bd, C_DANGER_BG);
                    for (int k = -2; k <= 2; k++) {
                        c.fill(bx + k, by + k, bx + k + 1, by + k + 1, withA(C_DANGER, ga));
                        c.fill(bx + k, by - k, bx + k + 1, by - k + 1, withA(C_DANGER, ga));
                    }
                }
            }
        }
    }

    // ---- skin settings: Default (Steve) / Slim (Alex) ------------------------------------------------

    private int typeCardW() {
        return (innerW - 10) / 2;
    }

    private int typeCardX(int i) {
        return popX + PAD + i * (typeCardW() + 10);
    }

    private void drawSettings(DrawContext c, int mouseX, int mouseY, float dt, float ga, long nowMs) {
        if (settingsT <= 0.02f) {
            return;
        }
        int fh = textRenderer.fontHeight;
        int cw = typeCardW();
        int top = popY + typeRelY;
        boolean slim = FhasixSkins.isSlim(client, selected);
        typeRingT += ((slim ? 1f : 0f) - typeRingT) * (1f - (float) Math.exp(-12f * dt));

        float ringA = clamp01((settingsT - 0.3f) / 0.7f) * ga;
        int ringXpos = Math.round(popX + PAD + typeRingT * (cw + 10));
        roundRect(c, ringXpos - 1, top - 1, cw + 2, CARD_H + 2, 11, withA(C_LIME, ringA));

        float popK = typePop < 0L ? 0f : clamp01((System.nanoTime() - typePop) / 320_000_000f);
        int bob = typePop < 0L || popK >= 1f ? 0 : -Math.round((float) Math.sin(popK * Math.PI) * 3f);

        for (int i = 0; i < 2; i++) {
            float ti = easeOutCubic(clamp01((settingsT - 0.18f * i) / 0.7f));
            if (ti <= 0.02f) {
                continue;
            }
            int x0 = typeCardX(i);
            int y0 = top + Math.round((1f - ti) * 8f);
            boolean hov = !closing && inRect(mouseX, mouseY, x0, top, cw, CARD_H);
            typeHover[i] += ((hov ? 1f : 0f) - typeHover[i]) * (1f - (float) Math.exp(-16f * dt));
            float a = ti * ga;
            boolean isSel = (i == 1) == slim;

            roundRect(c, x0, y0, cw, CARD_H, 10, withA(lerpColor(C_BTN, C_BTN_HOT, typeHover[i]), a));

            int iconY = y0 + 8 + (isSel ? bob : 0) + Math.round(typeHover[i] * -1f);
            drawMiniChar(c, x0 + (cw - 16) / 2, iconY, i == 1, a);

            String name = i == 0 ? "Default" : "Slim";
            String who = i == 0 ? "Steve" : "Alex";
            int nameCol = lerpColor(C_SUB, C_NAME, Math.max(typeHover[i], isSel ? 1f : 0f));
            drawFaded(c, name, x0 + (cw - strW(name)) / 2, y0 + 8 + 32 + 6, nameCol, a);
            drawFaded(c, who, x0 + (cw - strW(who)) / 2, y0 + 8 + 32 + 6 + fh + 2, 0xFF6E6F7A, a);

            float sel = 1f - Math.min(1f, Math.abs(typeRingT - i) * 1.4f);
            int bd = evenAtLeast2(Math.round(12f * sel * ti));
            if (bd >= 6) {
                int bx = x0 + cw - 12;
                int by = y0 + 12;
                fillCircle(c, bx, by, bd, withA(C_LIME, ga));
                drawCheck(c, bx, by, withA(C_ON_LIME, ga));
            }
        }
    }

    // ---- buttons -----------------------------------------------------------------------------------

    private void drawButtons(DrawContext c, int mouseX, int mouseY, float dt, float ms, float ga,
                             long now, long nowMs) {
        int fh = textRenderer.fontHeight;
        int bx = popX + PAD;
        int softY = popY + softRelY;
        int accY = popY + accRelY;
        boolean softHov = !closing && inRect(mouseX, mouseY, bx, softY, innerW, BTN_H);
        boolean accHov = !closing && inRect(mouseX, mouseY, bx, accY, innerW, BTN_H);
        softHover += ((softHov ? 1f : 0f) - softHover) * (1f - (float) Math.exp(-16f * dt));
        accHover += ((accHov ? 1f : 0f) - accHover) * (1f - (float) Math.exp(-16f * dt));
        softPress -= softPress * (1f - (float) Math.exp(-11f * dt));
        accPress -= accPress * (1f - (float) Math.exp(-11f * dt));
        if (accHov && !accWasHover) {
            accShineStart = now;
        }
        accWasHover = accHov;

        // ---- Skin Settings (soft button) ----
        float tB1 = ct(ms, 380f, 380f);
        if (tB1 > 0.02f) {
            int yy = softY + Math.round((1f - tB1) * 10f);
            int inset = Math.round(softPress * 2f);
            float lit = Math.max(softHover, settingsT);
            int bg = lerpColor(C_BTN, C_BTN_HOT, lit);
            int fg = lerpColor(lerpColor(C_BTN_TEXT, C_NAME, softHover), C_LIME, settingsT);
            float a = tB1 * ga;
            roundRect(c, bx + inset, yy + inset, innerW - inset * 2, BTN_H - inset * 2, 10, withA(bg, a));
            drawRipple(c, 0, bx, yy, innerW, BTN_H, now);
            String label = "Skin Settings";
            int groupW = 14 + 8 + strW(label);
            int gx = bx + (innerW - groupW) / 2 + Math.round(softHover * -1f);
            drawGear(c, gx + 7, yy + BTN_H / 2, withA(fg, a), withA(bg, a));
            drawFaded(c, label, gx + 22, yy + (BTN_H - fh) / 2, fg, a);
            drawChevron(c, bx + innerW - 18, yy + BTN_H / 2, settingsT > 0.5f, withA(fg, a));
        }

        // ---- Upload Skin (accent button) ----
        float tB2 = ct(ms, 520f, 380f);
        if (tB2 > 0.02f) {
            long sinceState = now - uploadStateStart;
            int shake = 0;
            if (uploadState == UP_FAIL && sinceState < 500_000_000L) {
                float u = sinceState / 500_000_000f;
                shake = Math.round((float) Math.sin(u * 38.0) * 3f * (1f - u));
            }
            float flash = 0f;
            if (uploadState == UP_SUCCESS) {
                flash = 1f - clamp01(sinceState / 700_000_000f);
            }

            int yy = accY + Math.round((1f - tB2) * 10f);
            int inset = Math.round(accPress * 2f);
            int base = lerpColor(C_LIME, C_LIME_HI, accHover);
            if (uploadState == UP_FAIL) {
                base = lerpColor(base, C_DANGER, clamp01(sinceState / 120_000_000f));
            }
            int bg = lerpColor(lerpColor(base, 0xFFFFFFFF, accPress * 0.16f), 0xFFFFFFFF, flash * 0.35f);
            float a = tB2 * ga;
            int rx0 = bx + inset + shake;
            roundRect(c, rx0, yy + inset, innerW - inset * 2, BTN_H - inset * 2, 10, withA(bg, a));

            if (accShineStart >= 0L && uploadState == UP_IDLE) {
                float s = clamp01((now - accShineStart) / 560_000_000f);
                if (s < 1f) {
                    int sx = bx - 24 + Math.round((innerW + 48) * easeInOutCubic(s));
                    c.enableScissor(bx + 10, yy + 6, bx + innerW - 10, yy + BTN_H - 6);
                    for (int k = -8; k <= 8; k += 2) {
                        float w = 1f - Math.abs(k) / 9f;
                        c.fill(sx + k, yy, sx + k + 2, yy + BTN_H, argb(Math.round(70f * w * a), 0xFFFFFF));
                    }
                    c.disableScissor();
                }
            }
            drawRipple(c, 1, bx, yy, innerW, BTN_H, now);

            uploadLabel.set(uploadText(), now);
            int fg = uploadState == UP_FAIL ? 0xFF2B0E09 : C_ON_LIME;
            int groupW = 14 + 8 + strW(uploadLabel.cur);
            int gx = bx + (innerW - groupW) / 2 + shake + Math.round(accHover * -1f);
            int icx = gx + 7;
            int icy = yy + BTN_H / 2;
            switch (uploadState) {
                case UP_PICKING:
                case UP_WAITING:
                    drawSpinner(c, icx, icy, withA(fg, a), nowMs);
                    break;
                case UP_SUCCESS:
                    drawCheck(c, icx, icy, withA(fg, a));
                    break;
                case UP_FAIL:
                    c.fill(icx - 1, icy - 5, icx + 1, icy + 1, withA(fg, a));
                    c.fill(icx - 1, icy + 3, icx + 1, icy + 5, withA(fg, a));
                    break;
                default:
                    drawPlay(c, icx, icy, withA(fg, a));
                    break;
            }
            drawSwap(c, uploadLabel, gx + 22, yy + (BTN_H - fh) / 2, 0, fg, a, now);
        }
    }

    private String uploadText() {
        switch (uploadState) {
            case UP_PICKING:
                return "Choosing...";
            case UP_WAITING:
                return "Waiting for a PNG...";
            case UP_SUCCESS:
            case UP_FAIL:
                return uploadMsg;
            default:
                return "Watch Tutorial";
        }
    }

    /** Where to drop a PNG when there's no file dialog (drawn under the card, outside its clip). */
    private void drawHint(DrawContext c, float ga, long nowMs) {
        if (hintT < 0.02f) {
            return;
        }
        int fh = textRenderer.fontHeight;
        int h = 14 + fh * 3 + 8 + 6;
        int y = Math.min(popY + popH + 10, height - h - 6) + Math.round((1f - hintT) * 8f);
        float a = hintT * ga;
        roundRect(c, popX - 1, y - 1, popW + 2, h + 2, 11, withA(C_BORDER, a));
        roundRect(c, popX, y, popW, h, 10, withA(C_SURFACE, a));
        int tx = popX + 12;
        drawFaded(c, "Copy your skin PNG into this folder:", tx, y + 9, C_NAME, a);
        String path = FhasixSkins.dir(client).toAbsolutePath().toString();
        drawFaded(c, tail(path, popW - 24), tx, y + 9 + fh + 4, C_LIME, a);
        drawFaded(c, "64x64 PNG  \u00B7  it lands in Slot " + (awaitingSlot + 1) + " by itself", tx,
                y + 9 + (fh + 4) * 2, C_SUB, a);
    }

    private void drawRipple(DrawContext c, int btn, int x, int y, int w, int h, long now) {
        if (rippleBtn != btn) {
            return;
        }
        float t = (now - rippleStart) / 520_000_000f;
        if (t >= 1f) {
            rippleBtn = -1;
            return;
        }
        int d = evenAtLeast2(Math.round(12f + w * 1.1f * easeOutCubic(t)));
        int a = Math.round(46f * (1f - t));
        if (a <= 0) {
            return;
        }
        c.enableScissor(x + 10, y + 2, x + w - 10, y + h - 2);
        fillCircle(c, rippleX, rippleY, d, argb(a, 0xFFFFFF));
        c.disableScissor();
    }

    // ---- avatar swap animation ----------------------------------------------------------------

    /** Squash on the way out, swap the face at the low point, spring back with a rebound. */
    private float swapScale(long now) {
        if (swapStart < 0L) {
            return 1f;
        }
        float s = (now - swapStart) / 1_000_000f;
        if (s < 110f) {
            float u = s / 110f;
            return 1f - 0.22f * u * u;
        }
        if (shown != selected) {
            shown = selected;
        }
        float u = clamp01((s - 110f) / 300f);
        if (u >= 1f) {
            swapStart = -1L;
            return 1f;
        }
        return 0.78f + 0.22f * easeOutBack(u, 1.9f);
    }

    private float swapGlow(long now) {
        if (swapStart < 0L) {
            return 0f;
        }
        float s = (now - swapStart) / 1_000_000f;
        if (s < 110f) {
            return s / 110f;
        }
        return 1f - clamp01((s - 110f) / 300f);
    }

    /** The slot's skin face, else the account's, else a drawn fallback face. */
    private void drawFace(DrawContext c, int x, int y, int size, SkinTextures face, boolean blink) {
        if (face != null) {
            PlayerSkinDrawer.draw(c, face, x, y, size);
            return;
        }
        int[] pal = FALLBACK_PALETTE;
        for (int gy = 0; gy < 8; gy++) {
            int y0 = y + Math.round(gy * size / 8f);
            int y1 = y + Math.round((gy + 1) * size / 8f);
            for (int gx = 0; gx < 8; gx++) {
                int code = FACE[gy][gx];
                int col = pal[code];
                if (blink && gy == 4 && (code == 2 || code == 3)) {
                    col = pal[4];
                }
                c.fill(x + Math.round(gx * size / 8f), y0, x + Math.round((gx + 1) * size / 8f), y1, col);
            }
        }
    }

    private int chipCx(int i) {
        int gapC = (innerW - CHIPS * CHIP) / (CHIPS - 1);
        int total = CHIPS * CHIP + (CHIPS - 1) * gapC;
        int left = popX + PAD + (innerW - total) / 2;
        return left + i * (CHIP + gapC) + CHIP / 2;
    }

    // =========================================================================================
    // input
    // =========================================================================================

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (closing) {
            return true;
        }
        int button = click.buttonInfo().button();
        double mx = click.x();
        double my = click.y();
        long now = System.nanoTime();

        if (button != 0 && button != 1) {
            return true;
        }
        if (mx < popX || mx >= popX + popW || my < popY || my >= popY + popH) {
            if (button == 0) {
                startClose(now); // click anywhere outside (including the icon again) folds it away
            }
            return true;
        }

        int chipCy = popY + chipsRelY + CHIP / 2;
        for (int i = 0; i < CHIPS; i++) {
            double dx = mx - chipCx(i);
            double dy = my - chipCy;
            boolean inChip = dx * dx + dy * dy <= (CHIP / 2.0) * (CHIP / 2.0);
            if (button == 1) {
                if (inChip) {
                    clearSlot(i, now); // right-click empties a slot
                    return true;
                }
                continue;
            }
            double bx = mx - (chipCx(i) + 11);
            double by = my - (chipCy - 11);
            boolean onBadge = FhasixSkins.hasSkin(client, i) && (i == selected || chipHover[i] > 0.4f)
                    && bx * bx + by * by <= 8.0 * 8.0;
            if (onBadge) {
                clearSlot(i, now);
                return true;
            }
            if (inChip) {
                select(i, now);
                return true;
            }
        }
        if (button != 0) {
            return true;
        }

        int bx = popX + PAD;
        if (inRect(mx, my, bx, popY + softRelY, innerW, BTN_H)) {
            softPress = 1f;
            rippleBtn = 0;
            rippleX = (int) mx;
            rippleY = (int) my;
            rippleStart = now;
            settingsOpen = !settingsOpen;
            return true;
        }
        if (settingsT > 0.6f) {
            for (int i = 0; i < 2; i++) {
                if (inRect(mx, my, typeCardX(i), popY + typeRelY, typeCardW(), CARD_H)) {
                    boolean wantSlim = i == 1;
                    if (FhasixSkins.isSlim(client, selected) != wantSlim) {
                        FhasixSkins.setSlim(client, selected, wantSlim);
                        typePop = now;
                    }
                    return true;
                }
            }
        }
        if (inRect(mx, my, bx, popY + accRelY, innerW, BTN_H)) {
            accPress = 1f;
            rippleBtn = 1;
            rippleX = (int) mx;
            rippleY = (int) my;
            rippleStart = now;
            openTutorial();
            return true;
        }
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyEvent) {
        int key = keyEvent.key();
        long now = System.nanoTime();
        if (key == 256) { // ESC
            startClose(now);
            return true;
        }
        if (key == 263) {
            select(selected - 1, now);
            return true;
        }
        if (key == 262) {
            select(selected + 1, now);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    private void select(int i, long now) {
        if (i < 0 || i >= CHIPS || i == selected || closing) {
            return;
        }
        selected = i;
        FhasixSkins.select(client, i); // equips it (an empty slot = account skin)
        swapStart = now;
        typeRingT = FhasixSkins.isSlim(client, i) ? 1f : 0f;
    }

    private void clearSlot(int i, long now) {
        if (!FhasixSkins.hasSkin(client, i)) {
            return;
        }
        FhasixSkins.clear(client, i);
        if (i == selected) {
            swapStart = now;
        }
    }

    // =========================================================================================
    // helpers
    // =========================================================================================

    private void drawStr(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, s, x, y, color, false);
    }

    /** Text with alpha - skipped below ~4% because very low alpha would render fully opaque. */
    private void drawFaded(DrawContext c, String s, int x, int y, int color, float a01) {
        if (a01 < 0.04f) {
            return;
        }
        drawStr(c, s, x, y, withA(color, a01));
    }

    /** align: 0 = text starts at ax, 1 = text ends at ax. Old text slides up and out, new slides in. */
    private void drawSwap(DrawContext c, SwapText t, int ax, int y, int align, int color, float a01, long now) {
        float k = easeOutCubic(clamp01((now - t.start) / 260_000_000f));
        if (t.prev.length() > 0 && k < 1f) {
            int x = align == 1 ? ax - strW(t.prev) : ax;
            drawFaded(c, t.prev, x, y - Math.round(5f * k), color, (1f - k) * a01);
        }
        int x = align == 1 ? ax - strW(t.cur) : ax;
        drawFaded(c, t.cur, x, y + Math.round((1f - k) * 5f), color, k * a01);
    }

    private int strW(String s) {
        return textRenderer.getWidth(s);
    }

    private String ellipsize(String s, int maxW) {
        if (maxW <= 0) {
            return "";
        }
        if (strW(s) <= maxW) {
            return s;
        }
        int dotsW = strW("...");
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (strW(sb.toString() + s.charAt(i)) + dotsW > maxW) {
                break;
            }
            sb.append(s.charAt(i));
        }
        return sb + "...";
    }

    /** Keeps the END of a long path (the useful part), with a leading "...". */
    private String tail(String s, int maxW) {
        if (strW(s) <= maxW) {
            return s;
        }
        int dotsW = strW("...");
        int start = 0;
        while (start < s.length() - 1 && strW(s.substring(start)) + dotsW > maxW) {
            start++;
        }
        return "..." + s.substring(start);
    }

    private static boolean inRect(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx < x + w && my >= y && my < y + h;
    }

    private static int clampInt(int v, int lo, int hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static float clamp01(float v) {
        return Math.max(0f, Math.min(1f, v));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float easeOutCubic(float t) {
        float u = 1f - t;
        return 1f - u * u * u;
    }

    private static float easeInCubic(float t) {
        return t * t * t;
    }

    private static float easeInOutCubic(float t) {
        return t < 0.5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    /** Spring-style ease: overshoots 1 slightly then settles. c1 sets how hard it overshoots. */
    private static float easeOutBack(float t, float c1) {
        float c3 = c1 + 1f;
        float u = t - 1f;
        return 1f + c3 * u * u * u + c1 * u * u;
    }

    /** Eased 0..1 progress of a child that starts at delayMs and runs for durMs. */
    private static float ct(float ms, float delayMs, float durMs) {
        return easeOutCubic(clamp01((ms - delayMs) / durMs));
    }

    private static int evenAtLeast2(int n) {
        int v = Math.max(2, n);
        return v - (v & 1);
    }

    private static int argb(int alpha, int color) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int withA(int color, float a01) {
        return argb(Math.round(clamp01(a01) * 255f), color);
    }

    private static int lerpColor(int a, int b, float t) {
        t = clamp01(t);
        int aa = (a >>> 24) & 255;
        int ar = (a >> 16) & 255;
        int ag = (a >> 8) & 255;
        int ab = a & 255;
        int ba = (b >>> 24) & 255;
        int br = (b >> 16) & 255;
        int bg = (b >> 8) & 255;
        int bb = b & 255;
        int oa = Math.round(aa + (ba - aa) * t);
        int or = Math.round(ar + (br - ar) * t);
        int og = Math.round(ag + (bg - ag) * t);
        int ob = Math.round(ab + (bb - ab) * t);
        return (oa << 24) | (or << 16) | (og << 8) | ob;
    }

    /** Anti-aliased solid rounded rect (alpha honoured); corner rows only, run-batched fills. */
    private static void roundRect(DrawContext c, int x, int y, int w, int h, int radius, int argb) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int a0 = argb >>> 24;
        if (a0 == 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            c.fill(x, y, x + w, y + h, argb);
            return;
        }
        int rgb = argb & 0x00FFFFFF;
        c.fill(x, y + r, x + w, y + h - r, argb);
        for (int dy = 0; dy < r; dy++) {
            int spanStart = r;
            for (int dx = 0; dx < r; dx++) {
                float ddx = dx + 0.5f - r;
                float ddy = dy + 0.5f - r;
                float cov = r - (float) Math.sqrt(ddx * ddx + ddy * ddy) + 0.5f;
                if (cov >= 1f) {
                    spanStart = dx;
                    break;
                }
                if (cov <= 0f) {
                    continue;
                }
                int col = (Math.round(a0 * cov) << 24) | rgb;
                c.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, col);
                c.fill(x + w - 1 - dx, y + dy, x + w - dx, y + dy + 1, col);
                c.fill(x + dx, y + h - 1 - dy, x + dx + 1, y + h - dy, col);
                c.fill(x + w - 1 - dx, y + h - 1 - dy, x + w - dx, y + h - dy, col);
            }
            c.fill(x + spanStart, y + dy, x + w - spanStart, y + dy + 1, argb);
            c.fill(x + spanStart, y + h - 1 - dy, x + w - spanStart, y + h - dy, argb);
        }
    }

    /** d should be even so the circle lands exactly on pixel centres. */
    private static void fillCircle(DrawContext c, int cx, int cy, int d, int argb) {
        roundRect(c, cx - d / 2, cy - d / 2, d, d, d / 2, argb);
    }

    /**
     * Paints the square's corners (everything outside the inscribed circle) in the surface colour,
     * with the same anti-aliased edge as fillCircle - this is what turns a square skin face into
     * a clean round avatar without any texture masking.
     */
    private static void eraseOutsideCircle(DrawContext c, int x, int y, int d, int argb) {
        if (d < 2) {
            return;
        }
        int a0 = argb >>> 24;
        if (a0 == 0) {
            return;
        }
        int rgb = argb & 0x00FFFFFF;
        float r = d / 2f;
        int mid = d / 2;
        for (int dy = 0; dy < d; dy++) {
            float vy = dy + 0.5f - r;
            int dx = 0;
            while (dx < mid) {
                float ddx = dx + 0.5f - r;
                float cov = r - (float) Math.sqrt(ddx * ddx + vy * vy) + 0.5f;
                if (cov > 0f) {
                    break;
                }
                dx++;
            }
            if (dx > 0) {
                c.fill(x, y + dy, x + dx, y + dy + 1, argb);
                c.fill(x + d - dx, y + dy, x + d, y + dy + 1, argb);
            }
            while (dx < mid) {
                float ddx = dx + 0.5f - r;
                float cov = r - (float) Math.sqrt(ddx * ddx + vy * vy) + 0.5f;
                if (cov >= 1f) {
                    break;
                }
                int col = (Math.round(a0 * (1f - cov)) << 24) | rgb;
                c.fill(x + dx, y + dy, x + dx + 1, y + dy + 1, col);
                c.fill(x + d - 1 - dx, y + dy, x + d - dx, y + dy + 1, col);
                dx++;
            }
        }
    }

    // ---- tiny pixel icons ----------------------------------------------------------------------------

    private static void drawGear(DrawContext c, int cx, int cy, int col, int hole) {
        fillCircle(c, cx, cy, 10, col);
        c.fill(cx - 1, cy - 6, cx + 1, cy - 4, col);
        c.fill(cx - 1, cy + 4, cx + 1, cy + 6, col);
        c.fill(cx - 6, cy - 1, cx - 4, cy + 1, col);
        c.fill(cx + 4, cy - 1, cx + 6, cy + 1, col);
        c.fill(cx + 3, cy - 5, cx + 5, cy - 3, col);
        c.fill(cx - 5, cy - 5, cx - 3, cy - 3, col);
        c.fill(cx + 3, cy + 3, cx + 5, cy + 5, col);
        c.fill(cx - 5, cy + 3, cx - 3, cy + 5, col);
        fillCircle(c, cx, cy, 4, hole);
    }

    /** Right-pointing play triangle. */
    private static void drawPlay(DrawContext c, int cx, int cy, int col) {
        for (int k = 0; k < 6; k++) {
            c.fill(cx - 3 + k, cy - (5 - k), cx - 2 + k, cy + (5 - k) + 1, col);
        }
    }

    /** Arrow pointing up into a tray. */
    private static void drawUpload(DrawContext c, int cx, int cy, int col) {
        c.fill(cx - 1, cy - 4, cx + 1, cy + 2, col);
        for (int k = 0; k < 4; k++) {
            c.fill(cx - k - 1, cy - 4 + k, cx + k + 1, cy - 3 + k, col);
        }
        c.fill(cx - 6, cy + 4, cx + 6, cy + 6, col);
        c.fill(cx - 6, cy + 2, cx - 4, cy + 4, col);
        c.fill(cx + 4, cy + 2, cx + 6, cy + 4, col);
    }

    private static void drawCheck(DrawContext c, int cx, int cy, int col) {
        for (int k = 0; k < 3; k++) {
            c.fill(cx - 4 + k, cy + k - 1, cx - 3 + k, cy + k, col);
        }
        for (int k = 0; k < 5; k++) {
            c.fill(cx - 1 + k, cy + 1 - k, cx + k, cy + 2 - k, col);
        }
    }

    /** Eight dots with a brightness that chases around the ring. */
    private static void drawSpinner(DrawContext c, int cx, int cy, int col, long nowMs) {
        int base = (int) ((nowMs / 90L) % 8L);
        for (int i = 0; i < 8; i++) {
            double ang = i * Math.PI / 4.0;
            int px = cx + (int) Math.round(Math.cos(ang) * 5.0) - 1;
            int py = cy + (int) Math.round(Math.sin(ang) * 5.0) - 1;
            int age = ((base - i) % 8 + 8) % 8;
            int alpha = Math.round(((col >>> 24) & 255) * (1f - age / 8f));
            c.fill(px, py, px + 2, py + 2, argb(alpha, col));
        }
    }

    private static void drawChevron(DrawContext c, int cx, int cy, boolean up, int col) {
        for (int k = 0; k < 4; k++) {
            int y = up ? cy + 1 - k : cy - 2 + k;
            c.fill(cx - 4 + k, y, cx - 2 + k, y + 1, col);
            c.fill(cx + 2 - k, y, cx + 4 - k, y + 1, col);
        }
    }

    /** 16x32 mini character: Steve (wide 4px arms) or Alex (slim 3px arms). */
    private static void drawMiniChar(DrawContext c, int x, int y, boolean slim, float a) {
        int skinTone = withA(0xFFC69C6D, a);
        int hair = withA(slim ? 0xFFD9822B : 0xFF4B2E1E, a);
        int shirt = withA(slim ? 0xFF6BA84F : 0xFF2AA9B5, a);
        int pants = withA(slim ? 0xFF5B4636 : 0xFF3B4C9B, a);
        int eye = withA(0xFF3B3F55, a);
        c.fill(x + 4, y, x + 12, y + 8, skinTone);
        c.fill(x + 4, y, x + 12, y + 2, hair);
        c.fill(x + 5, y + 4, x + 7, y + 5, eye);
        c.fill(x + 9, y + 4, x + 11, y + 5, eye);
        c.fill(x + 4, y + 8, x + 12, y + 20, shirt);
        int armW = slim ? 3 : 4;
        c.fill(x + 4 - armW, y + 8, x + 4, y + 16, shirt);
        c.fill(x + 4 - armW, y + 16, x + 4, y + 20, skinTone);
        c.fill(x + 12, y + 8, x + 12 + armW, y + 16, shirt);
        c.fill(x + 12, y + 16, x + 12 + armW, y + 20, skinTone);
        c.fill(x + 4, y + 20, x + 8, y + 32, pants);
        c.fill(x + 8, y + 20, x + 12, y + 32, pants);
    }
}
