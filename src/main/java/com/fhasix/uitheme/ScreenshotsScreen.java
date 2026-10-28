package com.fhasix.uitheme;

import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.Util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * FHASIX Screenshot Manager (the chat icon in the title-screen icon row). Same floating-panel
 * geometry, dim overlay, header slot and exit button as DiscoverScreen - only the center
 * differs, and the center carries the "Lime" theme (dark olive surfaces, lime accent) sampled
 * from the theme picker screenshot.
 *
 * What it does:
 *  - lists every PNG in <game dir>/screenshots (the same folder the "Take Screenshot" key and
 *    vanilla F2 write to), grouped under Today / Yesterday / date headers
 *  - thumbnails are decoded + downscaled on background threads and uploaded on the render
 *    thread, only for cards that are actually on screen, with an LRU cap on how many stay
 *    resident (this is the lesson from Discover's open-freeze: nothing blocking on the
 *    render thread)
 *  - click a card for a full-size viewer (prev/next, delete with confirm), quick-delete chip on
 *    hover, sort toggle, open-folder, smooth scrolling + clickable scrollbar track
 *
 * API NOTES (checked against the 1.21.11 Yarn javadocs): NativeImageBackedTexture's
 * (NativeImage) constructor is gone in 1.21.11 - it is (Supplier<String>, NativeImage) - and
 * NativeImage.read(InputStream) / resizeSubRectTo(...) / getWidth() / close() are as used here.
 * Util.getOperatingSystem().open(File) is confirmed on 1.21.8 and assumed unchanged.
 */
public class ScreenshotsScreen extends Screen {

    private final Screen parent;

    // ---- chrome shared with Discover/Store/Cosmetics --------------------------------------
    private static final Identifier EXIT_TEXTURE =
            Identifier.of("fhasixuitheme", "textures/gui/cosmetics/exit.png");
    private static final int EXIT_TEX_W = 1536;
    private static final int EXIT_TEX_H = 1402;
    private static final int EXIT_Y_OFFSET = 8;

    // ---- Lime theme (sampled from the "Choose your theme" screenshot) ----------------------
    private static final int[] PANEL_TL = {19, 20, 25};
    private static final int[] PANEL_BR = {19, 20, 25};
    private static final int[] VIEW_TL = {19, 20, 25};
    private static final int[] VIEW_BR = {19, 20, 25};

    private static final int C_PANEL_BORDER = 0xFF23222A;
    private static final int C_CARD = 0xFF23222A;
    private static final int C_CARD_HOT = 0xFF2C2B35;
    private static final int C_BORDER = 0xFF31392C;
    // screenshot box + sort button, sampled from the reference image (#131419 background,
    // #23222A button fill, #9E9DA8 button text)
    private static final int C_CARD_BORDER = 0xFF2E2D37;
    private static final int C_CARD_TEXT = 0xFFE6E7EC;
    private static final int C_CARD_META = 0xFF9FA0A9;
    private static final int C_BTN = 0xFF23222A;
    private static final int C_BTN_HOT = 0xFF2E2D37;
    private static final int C_BTN_TEXT = 0xFF9E9DA8;
    private static final int C_BTN_TEXT_HOT = 0xFFE6E7EC;
    private static final int C_LIME = 0xFFD9ED58;
    private static final int C_LIME_HI = 0xFFE7F77F;
    private static final int C_ON_LIME = 0xFF1B2108;
    private static final int C_TEXT = 0xFFDDDFD3;
    private static final int C_TEXT_SUB = 0xFFAEB6A1;
    private static final int C_MUTED = 0xFF858D79;
    private static final int C_CHIP = 0xFF2A3224;
    private static final int C_CHIP_HOT = 0xFF36422D;
    private static final int C_DANGER = 0xFFE8735A;
    private static final int C_DANGER_BG = 0xFF4A241D;
    private static final int C_SCRIM = 0x0E1209;

    // ---- layout constants ------------------------------------------------------------------
    private static final int COLS = 3;
    private static final int PAD = 6;
    private static final int HEADER_H = 26;
    private static final int TOOLBAR_H = 30;
    private static final int THUMB_W = 480;
    private static final int MAX_THUMBS = 48;

    // chip / button animation slots
    private static final int CH_SORT = 0;
    private static final int CH_FOLDER = 1;
    private static final int CH_PREV = 2;
    private static final int CH_NEXT = 3;
    private static final int CH_DELETE = 4;
    private static final int CH_CLOSE = 5;

    private static final int ICON_NONE = 0;
    private static final int ICON_FOLDER = 1;
    private static final int ICON_SORT_DESC = 2;
    private static final int ICON_SORT_ASC = 3;
    private static final int ICON_TRASH = 4;
    private static final int ICON_CLOSE = 5;

    private static final int STYLE_NORMAL = 0;
    private static final int STYLE_PRIMARY = 1;
    private static final int STYLE_DANGER = 2;
    private static final int STYLE_SOFT = 3;

    private static final ExecutorService EXEC = Executors.newFixedThreadPool(2, r -> {
        Thread t = new Thread(r, "fhasix-screenshots-io");
        t.setDaemon(true);
        return t;
    });

    private static final Pattern NAME_TIME =
            Pattern.compile("^(\\d{4})-(\\d{2})-(\\d{2})_(\\d{2})\\.(\\d{2})\\.(\\d{2})");
    private static final DateTimeFormatter DAY_FMT =
            DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.getDefault());
    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final Comparator<Shot> NEWEST = (a, b) -> {
        int c = b.time.compareTo(a.time);
        return c != 0 ? c : b.name.compareTo(a.name);
    };
    private static final Comparator<Shot> OLDEST = NEWEST.reversed();

    // kept for the whole game session so the choice survives closing/reopening the menu
    private static boolean newestFirst = true;
    private static int idCounter = 0;

    // ---- geometry --------------------------------------------------------------------------
    private int panelX, panelY, panelW, panelH, tabsY;
    private int contentX, contentY, contentW;
    private int gridTop, gridBottom;
    private int gap, cardW, cardH, thumbH;

    // ---- state -----------------------------------------------------------------------------
    private final List<Shot> shots = new ArrayList<>();
    private final List<Row> rows = new ArrayList<>();
    private int totalContentH = 0;
    private long totalBytes = 0;
    private boolean scanned = false;
    private volatile boolean scanning = false;
    private volatile boolean dead = false;
    private int tickCounter = 0;

    private float scrollCur = 0f;
    private float scrollTarget = 0f;
    private long lastFrameNanos = -1;
    private long frame = 0;
    private int loadedThumbs = 0;
    private final float[] chipAnim = new float[8];

    private Path armedPath = null;
    private long armedUntilNanos = 0;

    // viewer
    private int viewerIndex = -1;
    private long viewerOpenNanos = 0;
    private Identifier fullId = null;
    private int fullW, fullH;
    private volatile int fullState = 0; // 0 none, 1 loading, 2 ready, 3 failed
    private volatile int fullToken = 0;
    private int[] rClose = new int[4];
    private int[] rDelete = new int[4];
    private int[] rPrev = new int[4];
    private int[] rNext = new int[4];
    private int[] rImg = new int[4];

    public ScreenshotsScreen(Screen parent) {
        super(Text.literal("Screenshots"));
        this.parent = parent;
    }

    // =========================================================================================
    // lifecycle
    // =========================================================================================

    @Override
    protected void init() {
        panelW = Math.round(width * 0.68f);
        panelH = Math.round(height * 0.82f);
        panelX = (width - panelW) / 2;
        panelY = Math.round(height * 0.13f);
        tabsY = panelY - Math.round(height * 0.05f);

        contentX = panelX + Math.round(panelW * 0.02f);
        contentY = panelY + Math.round(panelH * 0.055f);
        contentW = Math.round(panelW * 0.96f);

        gridTop = contentY + TOOLBAR_H + 10;
        gridBottom = panelY + panelH - 14;

        gap = Math.max(8, Math.round(contentW * 0.016f));
        cardW = (contentW - gap * (COLS - 1)) / COLS;
        thumbH = Math.round((cardW - PAD * 2) * 9f / 16f);
        cardH = PAD + thumbH + 7 + textRenderer.fontHeight * 2 + 4 + PAD;

        lastFrameNanos = System.nanoTime();
        rebuildLayout();
        if (!scanned && !scanning) {
            scanAsync();
        }
    }

    @Override
    public void tick() {
        tickCounter++;
        // pick up screenshots written while the menu is open (the save itself is async)
        if (tickCounter % 40 == 0 && scanned && !scanning) {
            scanAsync();
        }
    }

    @Override
    public void removed() {
        dead = true;
        for (Shot s : shots) {
            releaseShot(s);
        }
        releaseFull();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    // =========================================================================================
    // scanning / thumbnails / viewer image loading (all file IO + decoding off the render thread)
    // =========================================================================================

    private void scanAsync() {
        scanning = true;
        final Path dir = ScreenshotManager.screenshotsDir(client);
        EXEC.execute(() -> {
            final List<Shot> found = scanDirectory(dir);
            client.execute(() -> {
                scanning = false;
                if (!dead) {
                    applyScan(found);
                }
            });
        });
    }

    private static List<Shot> scanDirectory(Path dir) {
        List<Shot> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*.png")) {
            for (Path p : stream) {
                try {
                    BasicFileAttributes a = Files.readAttributes(p, BasicFileAttributes.class);
                    if (a.isRegularFile()) {
                        out.add(new Shot(p, a.lastModifiedTime().toMillis(), a.size()));
                    }
                } catch (IOException ignored) {
                    // unreadable entry - skip it
                }
            }
        } catch (IOException ignored) {
            // folder vanished mid-scan - treat as empty
        }
        return out;
    }

    private void applyScan(List<Shot> found) {
        Shot viewing = (viewerIndex >= 0 && viewerIndex < shots.size()) ? shots.get(viewerIndex) : null;

        Map<String, Shot> existing = new HashMap<>();
        for (Shot s : shots) {
            existing.put(s.path.toString(), s);
        }

        List<Shot> merged = new ArrayList<>(found.size());
        for (Shot f : found) {
            String key = f.path.toString();
            Shot old = existing.get(key);
            if (old != null && old.modified == f.modified && old.size == f.size) {
                merged.add(old); // unchanged: keep it, and its already-loaded thumbnail
                existing.remove(key);
            } else {
                merged.add(f);
            }
        }
        for (Shot gone : existing.values()) {
            releaseShot(gone); // deleted or rewritten on disk
        }
        merged.sort(newestFirst ? NEWEST : OLDEST);

        boolean same = merged.size() == shots.size();
        if (same) {
            for (int i = 0; i < merged.size(); i++) {
                if (merged.get(i) != shots.get(i)) {
                    same = false;
                    break;
                }
            }
        }

        long bytes = 0;
        for (Shot s : merged) {
            bytes += s.size;
        }
        totalBytes = bytes;

        if (!same || !scanned) {
            shots.clear();
            shots.addAll(merged);
            rebuildLayout();
            if (viewing != null) {
                int idx = shots.indexOf(viewing);
                if (idx < 0) {
                    closeViewer();
                } else {
                    viewerIndex = idx;
                }
            }
        }
        scanned = true;
    }

    private void requestThumb(Shot s) {
        if (s.state != 0 || s.discarded) {
            return;
        }
        s.state = 1;
        final Path p = s.path;
        EXEC.execute(() -> {
            if (dead || s.discarded) {
                s.state = 0;
                return;
            }
            NativeImage full = null;
            NativeImage thumb = null;
            try (InputStream in = Files.newInputStream(p)) {
                full = NativeImage.read(in);
                final int fw = full.getWidth();
                final int fh = full.getHeight();
                int tw = Math.min(THUMB_W, fw);
                int th = Math.max(1, Math.round(tw * (fh / (float) fw)));
                thumb = new NativeImage(NativeImage.Format.RGBA, tw, th, false);
                full.resizeSubRectTo(0, 0, fw, fh, thumb);
                final NativeImage ready = thumb;
                thumb = null; // ownership moves to the render thread
                client.execute(() -> installThumb(s, ready, fw, fh));
            } catch (Throwable t) {
                s.state = 3;
            } finally {
                if (full != null) {
                    full.close();
                }
                if (thumb != null) {
                    thumb.close();
                }
            }
        });
    }

    private void installThumb(Shot s, NativeImage img, int fullWidth, int fullHeight) {
        if (dead || s.discarded) {
            img.close();
            return;
        }
        final int w = img.getWidth();
        final int h = img.getHeight();
        Identifier id = Identifier.of("fhasixuitheme", "screenshots/thumb_" + (idCounter++));
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "fhasix_screenshot_thumb", img);
        client.getTextureManager().registerTexture(id, tex);
        s.thumb = id;
        s.thumbW = w;
        s.thumbH = h;
        s.imgW = fullWidth;
        s.imgH = fullHeight;
        s.fade = 0f;
        s.state = 2;
        loadedThumbs++;
    }

    private void evictThumbs() {
        if (loadedThumbs <= MAX_THUMBS) {
            return;
        }
        List<Shot> loaded = new ArrayList<>();
        for (Shot s : shots) {
            if (s.thumb != null && s.lastDraw < frame - 1) {
                loaded.add(s);
            }
        }
        loaded.sort((a, b) -> Long.compare(a.lastDraw, b.lastDraw));
        for (Shot s : loaded) {
            if (loadedThumbs <= MAX_THUMBS) {
                break;
            }
            freeThumb(s);
        }
    }

    private void freeThumb(Shot s) {
        if (s.thumb != null) {
            client.getTextureManager().destroyTexture(s.thumb);
            s.thumb = null;
            loadedThumbs = Math.max(0, loadedThumbs - 1);
        }
        s.state = 0;
        s.fade = 0f;
    }

    private void releaseShot(Shot s) {
        s.discarded = true;
        freeThumb(s);
    }

    private void loadFull(Shot s) {
        final int token = ++fullToken;
        fullState = 1;
        if (s.state == 0) {
            requestThumb(s); // so there's a low-res placeholder while the full image decodes
        }
        final Path p = s.path;
        EXEC.execute(() -> {
            try (InputStream in = Files.newInputStream(p)) {
                final NativeImage img = NativeImage.read(in);
                client.execute(() -> installFull(token, img));
            } catch (Throwable t) {
                client.execute(() -> {
                    if (token == fullToken) {
                        fullState = 3;
                    }
                });
            }
        });
    }

    private void installFull(int token, NativeImage img) {
        if (dead || token != fullToken) {
            img.close();
            return;
        }
        final int w = img.getWidth();
        final int h = img.getHeight();
        Identifier id = Identifier.of("fhasixuitheme", "screenshots/full_" + (idCounter++));
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "fhasix_screenshot_full", img);
        client.getTextureManager().registerTexture(id, tex);
        fullId = id;
        fullW = w;
        fullH = h;
        fullState = 2;
    }

    private void releaseFull() {
        fullToken++; // invalidates any decode still in flight
        if (fullId != null) {
            client.getTextureManager().destroyTexture(fullId);
            fullId = null;
        }
        fullState = 0;
    }

    // =========================================================================================
    // layout
    // =========================================================================================

    private void rebuildLayout() {
        rows.clear();
        int y = 0;
        int i = 0;
        while (i < shots.size()) {
            LocalDate day = shots.get(i).time.toLocalDate();
            int j = i;
            while (j < shots.size() && shots.get(j).time.toLocalDate().equals(day)) {
                j++;
            }
            Row header = new Row();
            header.header = true;
            header.label = dayLabel(day);
            header.count = j - i;
            header.y = y;
            header.h = HEADER_H;
            rows.add(header);
            y += HEADER_H;

            for (int k = i; k < j; k += COLS) {
                Row r = new Row();
                r.start = k;
                r.end = Math.min(j, k + COLS);
                r.y = y;
                r.h = cardH + gap;
                rows.add(r);
                y += r.h;
            }
            i = j;
        }
        totalContentH = Math.max(0, y - gap);
        float max = maxScroll();
        scrollTarget = clampF(scrollTarget, 0f, max);
        scrollCur = clampF(scrollCur, 0f, max);
    }

    private int maxScroll() {
        return Math.max(0, totalContentH - (gridBottom - gridTop));
    }

    // =========================================================================================
    // rendering
    // =========================================================================================

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.render(context, mouseX, mouseY, delta);
        }
        context.fill(0, 0, width, height, 0x42000000);

        long now = System.nanoTime();
        float dt = lastFrameNanos < 0 ? 0f : Math.min(0.1f, (now - lastFrameNanos) / 1_000_000_000f);
        lastFrameNanos = now;
        long nowMs = now / 1_000_000L;
        frame++;

        if (armedPath != null && now > armedUntilNanos) {
            armedPath = null;
        }

        // smooth scrolling toward the wheel's target
        scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-14f * dt));
        if (Math.abs(scrollTarget - scrollCur) < 0.05f) {
            scrollCur = scrollTarget;
        }

        roundRect(context, panelX - 1, panelY - 1, panelW + 2, panelH + 2, 3, C_PANEL_BORDER);
        a1.drawRoundedGradientRect(context, panelX, panelY, panelW, panelH, PANEL_TL, PANEL_BR, 248, 2);

        drawHeader(context);

        boolean viewerOpen = viewerIndex >= 0;
        int mx = viewerOpen ? -10000 : mouseX; // keep the grid from reacting underneath the viewer
        int my = viewerOpen ? -10000 : mouseY;

        drawToolbar(context, mx, my, dt);
        if (!scanned) {
            drawSkeleton(context, nowMs);
        } else if (shots.isEmpty()) {
            drawEmpty(context, nowMs);
        } else {
            drawGrid(context, mx, my, dt, nowMs);
        }
        drawScrollbar(context);
        drawExit(context);

        if (viewerOpen) {
            drawViewer(context, mouseX, mouseY, dt, now, nowMs);
        }
    }

    private void drawHeader(DrawContext c) {
        int titleX = panelX - Math.round(panelW * 0.14f);
        int titleY = panelY - Math.round(height * 0.105f);
        c.drawText(textRenderer, "SCREENSHOTS", titleX, titleY, 0xFFF0F0F0, false);
    }

    private void drawExit(DrawContext c) {
        int size = Math.max(42, Math.round(height * 0.065f));
        int x = panelX + panelW - size;
        int y = tabsY - EXIT_Y_OFFSET;
        c.drawTexture(RenderPipelines.GUI_TEXTURED, EXIT_TEXTURE, x, y, 0, 0,
                size, size, EXIT_TEX_W, EXIT_TEX_H, EXIT_TEX_W, EXIT_TEX_H);
    }

    // ---- toolbar ---------------------------------------------------------------------------

    private int[] chipRect(int which) {
        int chipH = 22;
        int chipY = contentY + (TOOLBAR_H - chipH) / 2;
        int folderW = strW("Open Folder") + 40;
        int folderX = contentX + contentW - folderW;
        if (which == CH_FOLDER) {
            return new int[]{folderX, chipY, folderW, chipH};
        }
        int sortW = strW(newestFirst ? "Newest first" : "Oldest first") + 40;
        return new int[]{folderX - 8 - sortW, chipY, sortW, chipH};
    }

    private void drawToolbar(DrawContext c, int mx, int my, float dt) {
        int y0 = contentY;
        String title = "Screenshots";
        drawStr(c, title, contentX, y0 + 1, C_TEXT);

        int titleW = strW(title);
        int pillH = textRenderer.fontHeight + 4;
        String count = String.valueOf(shots.size());
        int pillW = strW(count) + 12;
        roundRect(c, contentX + titleW + 8, y0, pillW, pillH, pillH / 2, withAlpha(C_LIME, 0x2E));
        drawStr(c, count, contentX + titleW + 14, y0 + 2, C_LIME);

        int[] sortR = chipRect(CH_SORT);
        int[] folderR = chipRect(CH_FOLDER);

        int avail = sortR[0] - 14 - contentX;
        if (avail > 60) {
            String sub = humanSize(totalBytes) + "  \u00B7  Press " + ScreenshotManager.keyLabel() + " in-game to capture";
            drawStr(c, ellipsize(sub, avail), contentX, y0 + textRenderer.fontHeight + 7, C_MUTED);
        }

        drawChip(c, sortR, CH_SORT, hit(sortR, mx, my), STYLE_SOFT, false,
                newestFirst ? "Newest first" : "Oldest first",
                newestFirst ? ICON_SORT_DESC : ICON_SORT_ASC, dt);
        drawChip(c, folderR, CH_FOLDER, hit(folderR, mx, my), STYLE_PRIMARY, false,
                "Open Folder", ICON_FOLDER, dt);
    }

    private void drawChip(DrawContext c, int[] r, int slot, boolean hovered, int style, boolean armed,
                          String label, int icon, float dt) {
        chipAnim[slot] += ((hovered ? 1f : 0f) - chipAnim[slot]) * (1f - (float) Math.exp(-16f * dt));
        float t = chipAnim[slot];

        int bg;
        int fg;
        if (style == STYLE_PRIMARY) {
            bg = lerpColor(C_LIME, C_LIME_HI, t);
            fg = C_ON_LIME;
        } else if (style == STYLE_SOFT) {
            bg = lerpColor(C_BTN, C_BTN_HOT, t);
            fg = lerpColor(C_BTN_TEXT, C_BTN_TEXT_HOT, t);
        } else if (style == STYLE_DANGER) {
            bg = armed ? C_DANGER : lerpColor(C_CHIP, C_DANGER_BG, t);
            fg = armed ? 0xFF2B0E09 : lerpColor(C_TEXT_SUB, C_DANGER, t);
        } else {
            bg = lerpColor(C_CHIP, C_CHIP_HOT, t);
            fg = lerpColor(C_TEXT_SUB, C_TEXT, t);
        }

        if (style != STYLE_PRIMARY && t > 0.01f) {
            int ring = style == STYLE_DANGER ? C_DANGER : C_LIME;
            roundRect(c, r[0] - 1, r[1] - 1, r[2] + 2, r[3] + 2, r[3] / 2 + 1, withAlpha(ring, Math.round(70 * t)));
        }
        roundRect(c, r[0], r[1], r[2], r[3], r[3] / 2, bg);

        int cy = r[1] + r[3] / 2;
        if (icon != ICON_NONE) {
            drawIcon(c, icon, r[0] + 16, cy, fg);
            drawStr(c, label, r[0] + 28, r[1] + (r[3] - textRenderer.fontHeight) / 2, fg);
        } else {
            drawStr(c, label, r[0] + (r[2] - strW(label)) / 2, r[1] + (r[3] - textRenderer.fontHeight) / 2, fg);
        }
    }

    // ---- grid ------------------------------------------------------------------------------

    private void drawGrid(DrawContext c, int mx, int my, float dt, long nowMs) {
        int scroll = Math.round(scrollCur);
        c.enableScissor(contentX, gridTop, contentX + contentW, gridBottom);
        for (Row r : rows) {
            int ry = gridTop + r.y - scroll;
            if (ry + r.h < gridTop || ry > gridBottom) {
                continue;
            }
            if (r.header) {
                drawSectionHeader(c, r, ry);
                continue;
            }
            for (int k = r.start; k < r.end; k++) {
                int x = contentX + (k - r.start) * (cardW + gap);
                Shot s = shots.get(k);
                boolean over = mx >= x && mx < x + cardW && my >= ry && my < ry + cardH
                        && my >= gridTop && my < gridBottom;
                if (s.state == 0) {
                    requestThumb(s);
                }
                drawCard(c, s, x, ry, over, mx, my, dt, nowMs);
            }
        }
        c.disableScissor();
        evictThumbs();
    }

    private void drawSectionHeader(DrawContext c, Row r, int ry) {
        roundRect(c, contentX, ry + 7, 3, 11, 1, C_LIME);
        int x = contentX + 9;
        drawStr(c, r.label, x, ry + 8, C_TEXT);
        int lw = strW(r.label);
        String cnt = r.count + (r.count == 1 ? " shot" : " shots");
        drawStr(c, cnt, x + lw + 8, ry + 8, C_MUTED);
        int lineX = x + lw + 8 + strW(cnt) + 10;
        c.fill(lineX, ry + 13, contentX + contentW, ry + 14, 0xFF2A3223);
    }

    private void drawCard(DrawContext c, Shot s, int x, int y, boolean over, int mx, int my, float dt, long nowMs) {
        s.drawX = x;
        s.drawY = y;
        s.lastDraw = frame;
        s.hover += ((over ? 1f : 0f) - s.hover) * (1f - (float) Math.exp(-12f * dt));
        float h = s.hover;

        roundRect(c, x, y, cardW, cardH, 7, lerpColor(C_CARD_BORDER, C_LIME, h));
        int body = lerpColor(C_CARD, C_CARD_HOT, h);
        roundRect(c, x + 1, y + 1, cardW - 2, cardH - 2, 6, body);

        int tx = x + PAD;
        int ty = y + PAD;
        int tw = cardW - PAD * 2;
        drawThumb(c, s, tx, ty, tw, thumbH, h, body, dt, nowMs);

        int fy = ty + thumbH + 7;
        drawStr(c, s.timeLabel, tx, fy, C_CARD_TEXT);
        String meta = (s.imgW > 0 ? s.imgW + " x " + s.imgH + "  \u00B7  " : "") + humanSize(s.size);
        drawStr(c, ellipsize(meta, tw), tx, fy + textRenderer.fontHeight + 2, C_CARD_META);

        // quick-delete chip: fades in on hover, turns into a red "Delete?" confirm on first click
        boolean armedHere = isArmed(s);
        if (h > 0.02f || armedHere) {
            int alpha = armedHere ? 255 : Math.round(235 * h);
            int[] tr = trashRect(tx, ty, tw, armedHere);
            boolean overTrash = hit(tr, mx, my);
            int bg = armedHere ? C_DANGER_BG : (overTrash ? 0xFF3A4631 : 0xFF1A2015);
            roundRect(c, tr[0], tr[1], tr[2], tr[3], 7, withAlpha(bg, alpha));
            if (armedHere) {
                drawStr(c, "Delete?", tr[0] + 9, tr[1] + (tr[3] - textRenderer.fontHeight) / 2, C_DANGER);
            } else {
                drawIcon(c, ICON_TRASH, tr[0] + tr[2] / 2, tr[1] + tr[3] / 2,
                        withAlpha(overTrash ? C_DANGER : C_TEXT, alpha));
            }
        }
    }

    private int[] trashRect(int tx, int ty, int tw, boolean armed) {
        int w = armed ? strW("Delete?") + 18 : 22;
        return new int[]{tx + tw - 6 - w, ty + 6, w, 20};
    }

    private void drawThumb(DrawContext c, Shot s, int tx, int ty, int tw, int th, float h, int body,
                           float dt, long nowMs) {
        if (s.state == 2 && s.thumb != null) {
            s.fade = Math.min(1f, s.fade + dt / 0.28f);

            // cover-crop the thumbnail into the 16:9 box, then slowly push in on hover
            float boxAspect = tw / (float) th;
            float imgAspect = s.thumbW / (float) s.thumbH;
            float cropW = 1f;
            float cropH = 1f;
            if (imgAspect > boxAspect) {
                cropW = boxAspect / imgAspect;
            } else {
                cropH = imgAspect / boxAspect;
            }
            float zoom = 1f + 0.07f * h;
            cropW /= zoom;
            cropH /= zoom;
            float cu = (1f - cropW) / 2f;
            float cv = (1f - cropH) / 2f;

            int dim = Math.round(0x50 * (1f - h)); // lifts as the card is hovered
            int fadeIn = Math.round((1f - s.fade) * 255f);
            int overlay = (Math.max(dim, fadeIn) << 24) | C_SCRIM;
            int[] bodyRgb = {(body >> 16) & 255, (body >> 8) & 255, body & 255};
            a1.drawRoundedTexture(c, s.thumb, tx, ty, tw, th, cu, cv, cropW, cropH,
                    s.thumbW, s.thumbH, overlay, 5, tx, ty, tw, th, bodyRgb, bodyRgb);
        } else if (s.state == 3) {
            roundRect(c, tx, ty, tw, th, 5, 0xFF1A1B22);
            String msg = "Can't read this image";
            drawStr(c, msg, tx + (tw - strW(msg)) / 2, ty + (th - textRenderer.fontHeight) / 2, C_MUTED);
        } else {
            roundRect(c, tx, ty, tw, th, 5, shimmerColor(nowMs, tx));
        }
    }

    private void drawSkeleton(DrawContext c, long nowMs) {
        for (int i = 0; i < COLS; i++) {
            int x = contentX + i * (cardW + gap);
            roundRect(c, x, gridTop, cardW, cardH, 7, C_CARD_BORDER);
            roundRect(c, x + 1, gridTop + 1, cardW - 2, cardH - 2, 6, C_CARD);
            roundRect(c, x + PAD, gridTop + PAD, cardW - PAD * 2, thumbH, 5, shimmerColor(nowMs, x));
        }
    }

    private static int shimmerColor(long nowMs, int seed) {
        float t = ((nowMs + seed * 7L) % 1400L) / 1400f;
        float pulse = 0.5f + 0.5f * (float) Math.sin(t * Math.PI * 2.0);
        return lerpColor(0xFF1A1B22, 0xFF2A2933, pulse);
    }

    private void drawEmpty(DrawContext c, long nowMs) {
        int cx = contentX + contentW / 2;
        int areaH = gridBottom - gridTop;
        int cy = gridTop + Math.round(areaH * 0.36f);

        int bob = Math.round((float) Math.sin(nowMs / 700.0) * 2f);
        drawCamera(c, cx, cy + bob);

        String t1 = "No screenshots yet";
        drawStr(c, t1, cx - strW(t1) / 2, cy + 34, C_TEXT);

        String p1 = "Press ";
        String key = ScreenshotManager.keyLabel();
        String p2 = " in-game to capture your first one";
        int kw = strW(key) + 14;
        int total = strW(p1) + kw + strW(p2);
        int sx = cx - total / 2;
        int sy = cy + 34 + textRenderer.fontHeight + 9;
        drawStr(c, p1, sx, sy + 2, C_TEXT_SUB);
        roundRect(c, sx + strW(p1), sy - 2, kw, 16, 5, C_LIME);
        drawStr(c, key, sx + strW(p1) + 7, sy + 2, C_ON_LIME);
        drawStr(c, p2, sx + strW(p1) + kw, sy + 2, C_TEXT_SUB);
    }

    private static void drawCamera(DrawContext c, int cx, int cy) {
        roundRect(c, cx - 9, cy - 22, 18, 9, 3, C_BORDER);
        roundRect(c, cx - 26, cy - 15, 52, 36, 8, C_BORDER);
        roundRect(c, cx - 25, cy - 14, 50, 34, 7, 0xFF20281B);
        roundRect(c, cx - 12, cy - 9, 24, 24, 12, C_LIME);
        roundRect(c, cx - 9, cy - 6, 18, 18, 9, 0xFF161C11);
        roundRect(c, cx - 5, cy - 2, 10, 10, 5, withAlpha(C_LIME, 0x55));
        roundRect(c, cx - 3, cy - 1, 4, 4, 2, withAlpha(C_LIME_HI, 0xC0));
        roundRect(c, cx + 14, cy - 10, 7, 4, 2, C_LIME);
    }

    // ---- scrollbar -------------------------------------------------------------------------

    private int scrollTrackX() {
        return contentX + contentW + Math.round(panelW * 0.006f);
    }

    private void drawScrollbar(DrawContext c) {
        int trackH = gridBottom - gridTop;
        if (trackH <= 0) {
            return;
        }
        int trackX = scrollTrackX();
        roundRect(c, trackX, gridTop, 5, trackH, 2, 0xFF1C231A);

        int max = maxScroll();
        if (max <= 0) {
            roundRect(c, trackX, gridTop, 5, trackH, 2, withAlpha(C_LIME, 0x40));
            return;
        }
        int thumbLen = Math.max(24, Math.round(trackH * (trackH / (float) totalContentH)));
        int thumbY = gridTop + Math.round((trackH - thumbLen) * (scrollCur / max));
        roundRect(c, trackX, thumbY, 5, thumbLen, 2, withAlpha(C_LIME, 0xC8));
    }

    // ---- viewer ----------------------------------------------------------------------------

    private void drawViewer(DrawContext c, int mouseX, int mouseY, float dt, long now, long nowMs) {
        if (viewerIndex < 0 || viewerIndex >= shots.size()) {
            viewerIndex = -1;
            return;
        }
        Shot s = shots.get(viewerIndex);

        float openT = Math.min(1f, (now - viewerOpenNanos) / 220_000_000f);
        float ease = 1f - (1f - openT) * (1f - openT);

        a1.drawRoundedGradientRect(c, panelX, panelY, panelW, panelH, VIEW_TL, VIEW_BR, Math.round(252 * ease), 2);

        // top bar: file info on the left, actions on the right
        int top = panelY + 14;
        int chipH = 22;
        boolean armed = isArmed(s);

        int closeW = strW("Close") + 40;
        rClose = new int[]{panelX + panelW - 16 - closeW, top, closeW, chipH};
        String delLabel = armed ? "Click to confirm" : "Delete";
        int delW = strW(delLabel) + 40;
        rDelete = new int[]{rClose[0] - 8 - delW, top, delW, chipH};

        int infoX = panelX + 18;
        int infoW = rDelete[0] - 12 - infoX;
        drawStr(c, ellipsize(s.name, infoW), infoX, top, C_TEXT);
        String meta = dayLabel(s.time.toLocalDate()) + "  \u00B7  " + s.timeLabel
                + (s.imgW > 0 ? "  \u00B7  " + s.imgW + " x " + s.imgH : "")
                + "  \u00B7  " + humanSize(s.size);
        drawStr(c, ellipsize(meta, infoW), infoX, top + textRenderer.fontHeight + 3, C_MUTED);

        drawChip(c, rDelete, CH_DELETE, hit(rDelete, mouseX, mouseY), STYLE_DANGER, armed,
                delLabel, ICON_TRASH, dt);
        drawChip(c, rClose, CH_CLOSE, hit(rClose, mouseX, mouseY), STYLE_NORMAL, false,
                "Close", ICON_CLOSE, dt);

        // image area
        int gutter = 40;
        int ax = panelX + 16 + gutter;
        int aw = panelW - 2 * (16 + gutter);
        int ay = top + chipH + 14;
        int ah = panelY + panelH - 42 - ay;

        Identifier tex = null;
        int texW = 0;
        int texH = 0;
        if (fullState == 2 && fullId != null) {
            tex = fullId;
            texW = fullW;
            texH = fullH;
        } else if (s.state == 2 && s.thumb != null) {
            tex = s.thumb;
            texW = s.thumbW;
            texH = s.thumbH;
        }

        if (tex != null && texW > 0 && texH > 0 && aw > 0 && ah > 0) {
            float scale = Math.min(aw / (float) texW, ah / (float) texH);
            int dw = Math.max(1, Math.round(texW * scale));
            int dh = Math.max(1, Math.round(texH * scale));
            int dx = ax + (aw - dw) / 2;
            int dy = ay + (ah - dh) / 2;
            rImg = new int[]{dx, dy, dw, dh};

            roundRect(c, dx - 1, dy - 1, dw + 2, dh + 2, 9, withAlpha(C_LIME, 0x55));
            int overlay = (Math.round(255f * (1f - ease)) << 24) | C_SCRIM;
            a1.drawRoundedTexture(c, tex, dx, dy, dw, dh, 0f, 0f, 1f, 1f, texW, texH,
                    overlay, 8, panelX, panelY, panelW, panelH, VIEW_TL, VIEW_BR);
        } else {
            int bw = Math.max(10, Math.round(aw * 0.6f));
            int bh = Math.max(10, Math.round(ah * 0.6f));
            int bx = ax + (aw - bw) / 2;
            int by = ay + (ah - bh) / 2;
            rImg = new int[]{bx, by, bw, bh};
            boolean failed = fullState == 3 || s.state == 3;
            roundRect(c, bx, by, bw, bh, 9, failed ? 0xFF1A1B22 : shimmerColor(nowMs, bx));
            String msg = failed ? "Couldn't load this screenshot" : "Loading...";
            drawStr(c, msg, bx + (bw - strW(msg)) / 2, by + (bh - textRenderer.fontHeight) / 2, C_TEXT_SUB);
        }

        // prev / next
        if (shots.size() > 1) {
            int bs = 30;
            int by = ay + ah / 2 - bs / 2;
            rPrev = new int[]{panelX + 16 + (gutter - bs) / 2, by, bs, bs};
            rNext = new int[]{panelX + panelW - 16 - gutter + (gutter - bs) / 2, by, bs, bs};
            drawArrow(c, rPrev, CH_PREV, hit(rPrev, mouseX, mouseY), true, dt);
            drawArrow(c, rNext, CH_NEXT, hit(rNext, mouseX, mouseY), false, dt);
        } else {
            rPrev = new int[4];
            rNext = new int[4];
        }

        // counter + key hints along the bottom
        String counter = (viewerIndex + 1) + " / " + shots.size();
        int cw = strW(counter) + 20;
        int cy = panelY + panelH - 30;
        roundRect(c, panelX + (panelW - cw) / 2, cy, cw, 18, 9, withAlpha(C_LIME, 0x2E));
        drawStr(c, counter, panelX + (panelW - cw) / 2 + 10, cy + 5, C_LIME);
        drawStr(c, "\u2190 \u2192 browse  \u00B7  Del delete  \u00B7  Esc back", panelX + 18, cy + 5, C_MUTED);
    }

    private void drawArrow(DrawContext c, int[] r, int slot, boolean hovered, boolean left, float dt) {
        chipAnim[slot] += ((hovered ? 1f : 0f) - chipAnim[slot]) * (1f - (float) Math.exp(-16f * dt));
        float t = chipAnim[slot];
        roundRect(c, r[0], r[1], r[2], r[3], r[3] / 2, lerpColor(C_CHIP, C_LIME, t));
        drawChevron(c, r[0] + r[2] / 2, r[1] + r[3] / 2, left, lerpColor(C_TEXT_SUB, C_ON_LIME, t));
    }

    // =========================================================================================
    // input
    // =========================================================================================

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mx = click.x();
        double my = click.y();
        int button = click.buttonInfo().button();
        if (button != 0) {
            return true;
        }

        int size = Math.max(42, Math.round(height * 0.065f));
        int exitX = panelX + panelW - size;
        int exitY = tabsY - EXIT_Y_OFFSET;
        if (mx >= exitX && mx < exitX + size && my >= exitY && my < exitY + size) {
            client.setScreen(parent);
            return true;
        }

        if (viewerIndex >= 0) {
            clickViewer(mx, my);
            return true;
        }

        if (hit(chipRect(CH_SORT), mx, my)) {
            newestFirst = !newestFirst;
            shots.sort(newestFirst ? NEWEST : OLDEST);
            scrollTarget = 0f;
            rebuildLayout();
            return true;
        }
        if (hit(chipRect(CH_FOLDER), mx, my)) {
            openFolder();
            return true;
        }

        // clicking the scrollbar track jumps there
        int trackX = scrollTrackX();
        if (mx >= trackX - 4 && mx < trackX + 9 && my >= gridTop && my < gridBottom && maxScroll() > 0) {
            float frac = (float) ((my - gridTop) / (gridBottom - gridTop));
            scrollTarget = clampF(frac * maxScroll(), 0f, maxScroll());
            return true;
        }

        if (my >= gridTop && my < gridBottom) {
            for (int i = 0; i < shots.size(); i++) {
                Shot s = shots.get(i);
                if (s.lastDraw != frame) {
                    continue;
                }
                if (mx >= s.drawX && mx < s.drawX + cardW && my >= s.drawY && my < s.drawY + cardH) {
                    boolean armedHere = isArmed(s);
                    if (s.hover > 0.3f || armedHere) {
                        int[] tr = trashRect(s.drawX + PAD, s.drawY + PAD, cardW - PAD * 2, armedHere);
                        if (hit(tr, mx, my)) {
                            armOrDelete(s);
                            return true;
                        }
                    }
                    openViewer(i);
                    return true;
                }
            }
        }
        return true;
    }

    private void clickViewer(double mx, double my) {
        if (viewerIndex < 0 || viewerIndex >= shots.size()) {
            return;
        }
        if (hit(rClose, mx, my)) {
            closeViewer();
        } else if (hit(rDelete, mx, my)) {
            armOrDelete(shots.get(viewerIndex));
        } else if (shots.size() > 1 && hit(rPrev, mx, my)) {
            stepViewer(-1);
        } else if (shots.size() > 1 && hit(rNext, mx, my)) {
            stepViewer(1);
        } else if (!hit(rImg, mx, my)) {
            closeViewer(); // clicking the dark backdrop dismisses it
        }
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (viewerIndex >= 0) {
            stepViewer(verticalAmount > 0 ? -1 : 1);
            return true;
        }
        scrollTarget = clampF(scrollTarget - (float) verticalAmount * 44f, 0f, maxScroll());
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyEvent) {
        int key = keyEvent.key();
        if (key == 256) { // ESC
            if (viewerIndex >= 0) {
                closeViewer();
            } else {
                client.setScreen(parent);
            }
            return true;
        }
        if (viewerIndex >= 0) {
            if (key == 263) {
                stepViewer(-1);
            } else if (key == 262) {
                stepViewer(1);
            } else if (key == 261 && viewerIndex < shots.size()) {
                armOrDelete(shots.get(viewerIndex));
            }
            return true;
        }
        int page = Math.max(40, gridBottom - gridTop - 24);
        if (key == 264) {
            scrollTarget = clampF(scrollTarget + 48f, 0f, maxScroll());
            return true;
        } else if (key == 265) {
            scrollTarget = clampF(scrollTarget - 48f, 0f, maxScroll());
            return true;
        } else if (key == 267) {
            scrollTarget = clampF(scrollTarget + page, 0f, maxScroll());
            return true;
        } else if (key == 266) {
            scrollTarget = clampF(scrollTarget - page, 0f, maxScroll());
            return true;
        } else if (key == 268) {
            scrollTarget = 0f;
            return true;
        } else if (key == 269) {
            scrollTarget = maxScroll();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    // =========================================================================================
    // actions
    // =========================================================================================

    private void openViewer(int index) {
        viewerIndex = index;
        viewerOpenNanos = System.nanoTime();
        armedPath = null;
        releaseFull();
        loadFull(shots.get(index));
    }

    private void closeViewer() {
        viewerIndex = -1;
        armedPath = null;
        releaseFull();
    }

    private void stepViewer(int dir) {
        int n = shots.size();
        if (n <= 1 || viewerIndex < 0) {
            return;
        }
        viewerIndex = ((viewerIndex + dir) % n + n) % n;
        armedPath = null;
        releaseFull();
        loadFull(shots.get(viewerIndex));
    }

    private boolean isArmed(Shot s) {
        return armedPath != null && armedPath.equals(s.path) && System.nanoTime() < armedUntilNanos;
    }

    /** Two-step delete: the first click arms it (shows "Delete?"), the second one within 2.5s does it. */
    private void armOrDelete(Shot s) {
        if (isArmed(s)) {
            deleteShot(s);
        } else {
            armedPath = s.path;
            armedUntilNanos = System.nanoTime() + 2_500_000_000L;
        }
    }

    private void deleteShot(Shot s) {
        try {
            Files.deleteIfExists(s.path);
        } catch (IOException e) {
            return; // couldn't delete - leave it listed
        }
        int idx = shots.indexOf(s);
        shots.remove(s);
        totalBytes = Math.max(0L, totalBytes - s.size);
        releaseShot(s);
        armedPath = null;
        rebuildLayout();
        if (viewerIndex >= 0) {
            if (shots.isEmpty()) {
                closeViewer();
            } else {
                viewerIndex = Math.max(0, Math.min(idx, shots.size() - 1));
                releaseFull();
                loadFull(shots.get(viewerIndex));
            }
        }
    }

    private void openFolder() {
        try {
            Path dir = ScreenshotManager.screenshotsDir(client);
            Files.createDirectories(dir);
            Util.getOperatingSystem().open(dir.toFile());
        } catch (Throwable ignored) {
            // no desktop shell to open it with (e.g. some Android launchers) - nothing to do
        }
    }

    // =========================================================================================
    // drawing helpers
    // =========================================================================================

    private void drawStr(DrawContext c, String s, int x, int y, int color) {
        c.drawText(textRenderer, s, x, y, color, false);
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

    private static boolean hit(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    private static float clampF(float v, float lo, float hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static int withAlpha(int color, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (color & 0x00FFFFFF);
    }

    private static int lerpColor(int a, int b, float t) {
        t = clampF(t, 0f, 1f);
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

    /**
     * Anti-aliased rounded rectangle, solid color (alpha honoured). Only the corner rows are
     * touched pixel-by-pixel and each row's fully-covered run goes out as ONE fill() - the same
     * run-batching that fixed Discover's framerate - so a card costs a few dozen draw calls
     * instead of r^2 per corner.
     */
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

        c.fill(x, y + r, x + w, y + h - r, argb); // straight middle band

        for (int dy = 0; dy < r; dy++) {
            int spanStart = r; // first column of this row that is fully covered
            for (int dx = 0; dx < r; dx++) {
                float ddx = dx + 0.5f - r;
                float ddy = dy + 0.5f - r;
                float dist = (float) Math.sqrt(ddx * ddx + ddy * ddy);
                float cov = r - dist + 0.5f;
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

    private static void drawIcon(DrawContext c, int kind, int cx, int cy, int col) {
        switch (kind) {
            case ICON_FOLDER:
                c.fill(cx - 5, cy - 4, cx - 1, cy - 3, col);
                c.fill(cx - 5, cy - 3, cx + 5, cy - 2, col);
                c.fill(cx - 5, cy + 3, cx + 5, cy + 4, col);
                c.fill(cx - 5, cy - 2, cx - 4, cy + 3, col);
                c.fill(cx + 4, cy - 2, cx + 5, cy + 3, col);
                break;
            case ICON_SORT_DESC:
                c.fill(cx - 5, cy - 4, cx + 5, cy - 3, col);
                c.fill(cx - 5, cy - 1, cx + 2, cy, col);
                c.fill(cx - 5, cy + 2, cx - 1, cy + 3, col);
                break;
            case ICON_SORT_ASC:
                c.fill(cx - 5, cy - 4, cx - 1, cy - 3, col);
                c.fill(cx - 5, cy - 1, cx + 2, cy, col);
                c.fill(cx - 5, cy + 2, cx + 5, cy + 3, col);
                break;
            case ICON_TRASH:
                c.fill(cx - 2, cy - 5, cx + 3, cy - 4, col);
                c.fill(cx - 4, cy - 4, cx + 5, cy - 3, col);
                c.fill(cx - 3, cy - 2, cx - 2, cy + 5, col);
                c.fill(cx + 3, cy - 2, cx + 4, cy + 5, col);
                c.fill(cx - 3, cy + 4, cx + 4, cy + 5, col);
                c.fill(cx, cy - 1, cx + 1, cy + 3, col);
                break;
            case ICON_CLOSE:
                for (int t = -3; t <= 3; t++) {
                    c.fill(cx + t, cy + t, cx + t + 2, cy + t + 1, col);
                    c.fill(cx + t, cy - t, cx + t + 2, cy - t + 1, col);
                }
                break;
            default:
                break;
        }
    }

    private static void drawChevron(DrawContext c, int cx, int cy, boolean left, int col) {
        for (int t = 0; t <= 5; t++) {
            int x = left ? cx - 3 + t : cx + 1 - t;
            c.fill(x, cy - t, x + 2, cy - t + 1, col);
            c.fill(x, cy + t, x + 2, cy + t + 1, col);
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        double kb = bytes / 1024.0;
        if (kb < 1024.0) {
            return String.format(Locale.ROOT, "%.0f KB", kb);
        }
        double mb = kb / 1024.0;
        if (mb < 1024.0) {
            return String.format(Locale.ROOT, "%.1f MB", mb);
        }
        return String.format(Locale.ROOT, "%.2f GB", mb / 1024.0);
    }

    private static String dayLabel(LocalDate day) {
        LocalDate today = LocalDate.now();
        if (day.equals(today)) {
            return "Today";
        }
        if (day.equals(today.minusDays(1))) {
            return "Yesterday";
        }
        return day.format(DAY_FMT);
    }

    // =========================================================================================
    // data
    // =========================================================================================

    private static final class Row {
        boolean header;
        String label = "";
        int count;
        int start;
        int end;
        int y;
        int h;
    }

    private static final class Shot {
        final Path path;
        final String name;
        final long modified;
        final long size;
        final LocalDateTime time;
        final String timeLabel;

        volatile int state; // 0 idle, 1 loading, 2 ready, 3 failed
        volatile boolean discarded;
        Identifier thumb;
        int thumbW;
        int thumbH;
        int imgW;
        int imgH;

        float hover;
        float fade;
        long lastDraw = -1;
        int drawX;
        int drawY;

        Shot(Path path, long modified, long size) {
            this.path = path;
            this.name = path.getFileName().toString();
            this.modified = modified;
            this.size = size;
            this.time = parseTime(name, modified);
            this.timeLabel = this.time.format(TIME_FMT);
        }

        /** Vanilla names files yyyy-MM-dd_HH.mm.ss(.png); anything else falls back to its modified time. */
        private static LocalDateTime parseTime(String fileName, long modified) {
            Matcher m = NAME_TIME.matcher(fileName);
            if (m.find()) {
                try {
                    return LocalDateTime.of(
                            Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3)),
                            Integer.parseInt(m.group(4)), Integer.parseInt(m.group(5)), Integer.parseInt(m.group(6)));
                } catch (RuntimeException ignored) {
                    // impossible date in the name - use the file time instead
                }
            }
            return LocalDateTime.ofInstant(Instant.ofEpochMilli(modified), ZoneId.systemDefault());
        }
    }
}
