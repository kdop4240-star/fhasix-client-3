package com.fhasix.uitheme;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

/**
 * NEW (v30 UI rewrite, replaces the first version). The Mod Menu grid.
 *
 * Layout: every rectangle is taken from the reference screenshot (a 1394x847 design) and scaled
 * by ONE factor k = min(width/1394, height/847), then centred. So the menu keeps the reference's
 * exact proportions on any window size / GUI scale instead of stretching to the screen.
 *
 * Speed: no a1 gradients (one fill per pixel column) and no vanilla items. Everything is drawn with
 * NewUi.rrect (about 2r+1 fills per shape) plus smooth antialiased icons from one atlas texture.
 * Scrolling is eased. Hover uses the title-screen button colours (instant swap, bluish-grey).
 *
 * Opens from the title-screen grid icon (parent = TitleScreen) and from the in-world launcher
 * (parent = null). Working: FPS/CPS pills, hearts, tabs, search, favourites filter, scrolling.
 * Decorative: top icon row, gear buttons, grid-view button, placeholder mods' pills.
 */
public final class NewModMenuScreen extends Screen {

    private static final int DW = 1394;
    private static final int DH = 847;
    private static final int COLS = 4;
    private static final String[] TABS = {"All", "New", "HUD", "Hypixel", "PvP"};
    private static final String[] PERF_TABS = {"All", "World", "Video", "System"};
    private static final String[] TOP = {"gear", "chat", "gem", "archive", "list"};
    private static final Identifier[] TOP_TEX = new Identifier[TOP.length];

    static {
        for (int i = 0; i < TOP.length; i++) {
            TOP_TEX[i] = Identifier.of("fhasixuitheme", "textures/gui/icons/" + TOP[i] + ".png");
        }
    }

    // colours (ARGB)
    private static final int PANEL_T = NewUi.rgb(11, 12, 15);
    private static final int PANEL_B = NewUi.rgb(8, 9, 12);
    private static final int BTN_BORDER = NewUi.rgb(30, 33, 40);
    private static final int BTN_T = NewUi.rgb(20, 22, 28);
    private static final int BTN_B = NewUi.rgb(15, 17, 21);
    private static final int HOV_T = NewUi.rgb(54, 64, 78);
    private static final int HOV_B = NewUi.rgb(23, 26, 33);
    private static final int CARD_BORDER = NewUi.rgb(26, 29, 35);
    private static final int CARD_T = NewUi.rgb(16, 18, 22);
    private static final int CARD_B = NewUi.rgb(13, 14, 18);
    private static final int RED_T = NewUi.rgb(231, 60, 64);
    private static final int RED_B = NewUi.rgb(205, 46, 50);
    private static final int RED_HT = NewUi.rgb(240, 84, 88);
    private static final int RED_HB = NewUi.rgb(214, 58, 62);
    private static final int ON_T = NewUi.rgb(27, 78, 33);
    private static final int ON_B = NewUi.rgb(20, 60, 25);
    private static final int ON_HT = NewUi.rgb(36, 98, 43);
    private static final int ON_HB = NewUi.rgb(25, 72, 30);
    private static final int NEW_T = NewUi.rgb(46, 176, 66);
    private static final int NEW_B = NewUi.rgb(33, 146, 53);
    private static final int TXT = 0xFFFFFFFF;
    private static final int TXT_DIM = 0xFF8C8F96;
    private static final int TXT_TAB = 0xFFB4B7BE;
    private static final int TXT_ON = 0xFF3FBF4D;

    private static final int HIT_NONE = 0;
    private static final int HIT_HEART = 1;
    private static final int HIT_PILL = 2;
    private static final int HIT_GEAR = 3;
    private static final int HIT_CARD = 4;
    private static final int HIT_CHIP = 5;

    private static final class Hit {
        final int kind;
        final NewModSettings.ModDef mod;
        final int arg;

        Hit(int kind, NewModSettings.ModDef mod) {
            this(kind, mod, 0);
        }

        Hit(int kind, NewModSettings.ModDef mod, int arg) {
            this.kind = kind;
            this.mod = mod;
            this.arg = arg;
        }
    }

    private static final class Geo {
        int x, y, w, h;
        int hx, hy, hs;
        int gx, gy, gs;
        int px, py, pw, ph;
        int ix, iy, is;
        int nx, ny;
        /** Performance view: the Small / Medium / High value chips (first chip x, y, width, height, gap). */
        int cx, cy, cw, ch, cgap;
    }

    private final Screen parent;
    /** True = the Performance view: same menu, but the cards are the FPS modes of NewPerf instead of mods. */
    private final boolean perf;
    private TextFieldWidget searchField;
    private int tab = 0;
    private boolean favOnly = false;
    private boolean filterApplied = false;

    /** Eased 0..1 state of each card's Enabled pill (on/off) and its hover glow. */
    private final Map<String, Float> pillOn = new HashMap<>();
    private final Map<String, Float> pillHover = new HashMap<>();
    private long animLast = System.nanoTime();
    private float animDt = 0f;

    private float k;
    private int ox;
    private int oy;
    private int[] modTab;
    private final int[][] topBtn = new int[TOP.length][];
    private int[] closeBtn;
    private int[] panel;
    private final int[][] tabRect = new int[TABS.length][];
    private int[] searchRect;
    private int[] favBtn;
    private int[] gridBtn;
    private int clipTop;
    private int clipBottom;
    private int clipL;
    private int clipR;
    private int cardW;
    private int cardH;
    private int pitchX;
    private int pitchY;
    private int firstX;
    private int firstY;
    private final Geo[] geos = new Geo[NewModSettings.MODS.size()];

    private float scrollCur = 0f;
    private float scrollTarget = 0f;
    private long lastNanos = 0L;
    private boolean pressActive = false;
    private boolean dragScrolling = false;
    private double travel = 0.0;
    private Hit pressedHit = null;

    /** parent == null means "opened in a world": nothing to draw behind, closing returns to the game. */
    public NewModMenuScreen(Screen parent) {
        this(parent, false);
    }

    /** perf = true opens the Performance view (the gear in the top bar). */
    public NewModMenuScreen(Screen parent, boolean perf) {
        super(Text.literal(perf ? "PERFORMANCE" : "MOD MENU"));
        this.parent = parent;
        this.perf = perf;
    }

    private String[] tabs() {
        return perf ? PERF_TABS : TABS;
    }

    // ---------------------------------------------------------------- layout

    private int sx(int v) {
        return ox + Math.round(v * k);
    }

    private int sy(int v) {
        return oy + Math.round(v * k);
    }

    private int sl(int v) {
        return Math.max(1, Math.round(v * k));
    }

    private int[] rect(int x, int y, int w, int h) {
        int x0 = sx(x);
        int y0 = sy(y);
        return new int[]{x0, y0, sx(x + w) - x0, sy(y + h) - y0};
    }

    @Override
    protected void init() {
        k = NewUi.scale(width, height);
        ox = (width - Math.round(DW * k)) / 2;
        oy = (height - Math.round(DH * k)) / 2;

        modTab = rect(47, 30, 177, 63);
        for (int i = 0; i < TOP.length; i++) {
            topBtn[i] = rect(237 + 88 * i, 30, 76, 63);
        }
        closeBtn = rect(1305, 38, 46, 47);
        panel = rect(47, 110, 1305, 725);
        for (int i = 0; i < TABS.length; i++) {
            tabRect[i] = rect(70 + 120 * i, 133, 108, 39);
        }
        searchRect = rect(913, 133, 310, 39);
        favBtn = rect(1235, 133, 40, 39);
        gridBtn = rect(1288, 133, 40, 39);

        clipTop = sy(180);
        clipBottom = sy(829);
        clipL = sx(47) + 1;
        clipR = sx(1352) - 1;
        cardW = sx(72 + 304) - sx(72);
        cardH = Math.max(40, Math.round(208 * k));
        pitchX = Math.round(317 * k);
        pitchY = Math.round(222 * k);
        firstX = sx(72);
        firstY = sy(188);

        int padX = sl(14);
        searchField = new TextFieldWidget(textRenderer, searchRect[0] + padX,
                searchRect[1] + (searchRect[3] - textRenderer.fontHeight) / 2,
                Math.max(10, searchRect[2] - padX * 2), textRenderer.fontHeight + 2, Text.literal("Search"));
        searchField.setMaxLength(32);
        searchField.setDrawsBackground(false);
        try {
            searchField.setPlaceholder(Text.literal("Search"));
        } catch (Throwable ignored) {
            // placeholder API missing - the field still works
        }
        this.addDrawableChild(searchField);
    }

    /** Intentionally empty: the blur is applied from render() so it happens exactly once, in order. */

    /**
     * Vanilla background (panorama / blur / darkening) drawn once. The game allows only ONE blur per
     * frame and throws "Can only blur once per frame" if a launcher or the loading overlay has already
     * drawn one - that is caught here so it can never crash the game.
     */
    private void safeBackground(DrawContext context, int mouseX, int mouseY, float delta) {
        try {
            super.renderBackground(context, mouseX, mouseY, delta);
        } catch (IllegalStateException alreadyBlurred) {
            context.fill(0, 0, width, height, 0xB0000000);
        }
    }

    @Override
    public void renderBackground(DrawContext context, int mouseX, int mouseY, float delta) {
    }

    /** The screen behind this menu (null in a world). Used by the options screen. */
    Screen backdrop() {
        return parent;
    }

    private void closeMenu() {
        client.setScreen(parent);
    }

    private void layout(int count) {
        int scroll = Math.round(scrollCur);
        for (int i = 0; i < count; i++) {
            Geo g = geos[i];
            if (g == null) {
                g = new Geo();
                geos[i] = g;
            }
            g.x = firstX + (i % COLS) * pitchX;
            g.y = firstY + (i / COLS) * pitchY - scroll;
            g.w = cardW;
            g.h = cardH;
            g.hs = Math.max(10, sl(31));
            g.hx = g.x + g.w - sl(27) - g.hs;
            g.hy = g.y + sl(23);
            g.gs = Math.max(11, sl(40));
            g.gx = g.x + sl(24);
            g.gy = g.y + g.h - sl(23) - g.gs;
            g.px = g.x + sl(72);
            g.py = g.gy;
            g.pw = g.w - sl(72) - sl(27);
            g.ph = g.gs;
            g.is = Math.max(14, sl(64));
            g.ix = g.x + (g.w - g.is) / 2;
            g.iy = g.y + sl(97) - g.is / 2;
            g.nx = g.x + sl(24);
            g.ny = g.y + sl(34) - textRenderer.fontHeight / 2;
            if (perf) {
                // FPS-mode card: icon, the Small / Medium / High chips, then the Enabled pill across the bottom
                g.is = Math.max(14, sl(50));
                g.ix = g.x + (g.w - g.is) / 2;
                g.iy = g.y + sl(52);
                g.px = g.x + sl(24);
                g.pw = g.w - sl(48);
                g.ph = Math.max(12, sl(34));
                g.py = g.y + g.h - sl(20) - g.ph;
                g.cgap = Math.max(2, sl(6));
                g.cw = (g.w - sl(48) - 2 * g.cgap) / 3;
                g.ch = Math.max(11, sl(30));
                g.cy = g.py - sl(10) - g.ch;
                g.cx = g.x + sl(24);
            }
        }
    }

    private float maxScroll(int count) {
        int rows = (count + COLS - 1) / COLS;
        int content = (firstY - clipTop) + rows * pitchY - (pitchY - cardH) + sl(24);
        return Math.max(0, content - (clipBottom - clipTop));
    }

    // ---------------------------------------------------------------- data

    private List<NewModSettings.ModDef> visible() {
        String q = searchField == null ? "" : searchField.getText().trim().toLowerCase(Locale.ROOT);
        List<NewModSettings.ModDef> out = new ArrayList<>();
        if (perf) {
            for (NewModSettings.ModDef m : NewPerf.DEFS) {
                int want = tab == 1 ? NewModSettings.TAG_HUD : tab == 2 ? NewModSettings.TAG_HYPIXEL : tab == 3 ? NewModSettings.TAG_PVP : 0;
                if (want != 0 && (m.tags & want) == 0) {
                    continue;
                }
                if (favOnly && !NewModSettings.isFavorite(client, m.id)) {
                    continue;
                }
                if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q)) {
                    continue;
                }
                out.add(m);
            }
            return out;
        }
        for (NewModSettings.ModDef m : NewModSettings.MODS) {
            if (tab == 1 && !m.isNew) {
                continue;
            }
            if (tab == 2 && (m.tags & NewModSettings.TAG_HUD) == 0) {
                continue;
            }
            if (tab == 3 && (m.tags & NewModSettings.TAG_HYPIXEL) == 0) {
                continue;
            }
            if (tab == 4 && (m.tags & NewModSettings.TAG_PVP) == 0) {
                continue;
            }
            if (favOnly && !NewModSettings.isFavorite(client, m.id)) {
                continue;
            }
            if (!q.isEmpty() && !m.name.toLowerCase(Locale.ROOT).contains(q)) {
                continue;
            }
            out.add(m);
        }
        return out;
    }

    private boolean inClip(double mx, double my) {
        return NewUi.in(mx, my, clipL, clipTop, clipR - clipL, clipBottom - clipTop);
    }

    private Hit hitAt(double mx, double my) {
        if (!inClip(mx, my)) {
            return new Hit(HIT_NONE, null);
        }
        List<NewModSettings.ModDef> list = visible();
        layout(list.size());
        for (int i = 0; i < list.size(); i++) {
            Geo g = geos[i];
            if (!NewUi.in(mx, my, g.x, g.y, g.w, g.h)) {
                continue;
            }
            NewModSettings.ModDef m = list.get(i);
            if (NewUi.in(mx, my, g.hx, g.hy, g.hs, g.hs)) {
                return new Hit(HIT_HEART, m);
            }
            if (perf) {
                for (int j = 0; j < 3; j++) {
                    if (NewUi.in(mx, my, g.cx + j * (g.cw + g.cgap), g.cy, g.cw, g.ch)) {
                        return new Hit(HIT_CHIP, m, j);
                    }
                }
            } else if (NewUi.in(mx, my, g.gx, g.gy, g.gs, g.gs)) {
                return new Hit(HIT_GEAR, m);
            }
            if (NewUi.in(mx, my, g.px, g.py, g.pw, g.ph)) {
                return new Hit(HIT_PILL, m);
            }
            return new Hit(HIT_CARD, m);
        }
        return new Hit(HIT_NONE, null);
    }

    // ---------------------------------------------------------------- render

    @Override
    public void render(DrawContext c, int mx, int my, float delta) {
        long animNow = System.nanoTime();
        animDt = Math.min(0.1f, (animNow - animLast) / 1_000_000_000f);
        animLast = animNow;
        if (parent != null) {
            parent.render(c, -1000, -1000, delta); // far-away mouse: title buttons underneath stay calm
        } else {
            safeBackground(c, mx, my, delta); // in a world: vanilla blur + darkening (needs Menu Background Blur > 0)
        }
        c.fill(0, 0, width, height, parent != null ? 0x42000000 : 0x30000000);

        if (!filterApplied) {
            filterApplied = true;
        }

        List<NewModSettings.ModDef> list = visible();
        float max = maxScroll(list.size());
        scrollTarget = Math.max(0f, Math.min(max, scrollTarget));
        long now = System.nanoTime();
        float dt = lastNanos == 0L ? 0f : Math.min(0.1f, (now - lastNanos) / 1_000_000_000f);
        lastNanos = now;
        if (pressActive && dragScrolling) {
            scrollCur = scrollTarget;
        } else {
            scrollCur += (scrollTarget - scrollCur) * (1f - (float) Math.exp(-18f * dt));
            if (Math.abs(scrollTarget - scrollCur) < 0.3f) {
                scrollCur = scrollTarget;
            }
        }

        drawTopBar(c, mx, my);
        NewUi.rrect(c, panel[0], panel[1], panel[2], panel[3], Math.max(3, sl(12)), PANEL_T, PANEL_B);
        drawToolbar(c, mx, my);

        layout(list.size());
        c.enableScissor(clipL, clipTop, clipR, clipBottom);
        boolean over = inClip(mx, my);
        for (int i = 0; i < list.size(); i++) {
            Geo g = geos[i];
            if (g.y + g.h < clipTop - 4 || g.y > clipBottom) {
                continue;
            }
            drawCard(c, list.get(i), g, mx, my, over);
        }
        c.disableScissor();
        if (perf) {
            drawPerfHint(c, list, mx, my);
        }

        if (max > 0f) {
            int bw = Math.max(2, sl(5));
            int bx = sx(1345);
            int th = Math.max(12, Math.round((clipBottom - clipTop) * ((clipBottom - clipTop) / (clipBottom - clipTop + max))));
            int ty = clipTop + sl(6) + Math.round((clipBottom - clipTop - sl(12) - th) * (scrollCur / max));
            NewUi.rrect(c, bx, ty, bw, th, bw / 2, NewUi.rgb(96, 99, 106), NewUi.rgb(80, 83, 90));
        }

        super.render(c, mx, my, delta); // the search field
    }

    private void drawTopBar(DrawContext c, int mx, int my) {
        boolean hov = NewUi.in(mx, my, modTab[0], modTab[1], modTab[2], modTab[3]);
        NewUi.rrect(c, modTab[0], modTab[1], modTab[2], modTab[3], Math.max(3, sl(10)),
                hov ? RED_HT : RED_T, hov ? RED_HB : RED_B);
        Text label = Text.literal(perf ? "PERFORMANCE" : "MOD MENU").formatted(Formatting.BOLD);
        int ico = Math.max(9, sl(24));
        int tw = textRenderer.getWidth(label);
        boolean showIcon = modTab[2] >= ico + 4 + tw + 6;
        int total = (showIcon ? ico + 4 : 0) + tw;
        int sx0 = modTab[0] + (modTab[2] - total) / 2;
        if (showIcon) {
            NewUi.icon(c, NewUi.ICON_GRID, sx0, modTab[1] + (modTab[3] - ico) / 2, ico);
            sx0 += ico + 4;
        }
        c.drawText(textRenderer, label, sx0, modTab[1] + (modTab[3] - textRenderer.fontHeight) / 2, TXT, false);

        for (int i = 0; i < TOP.length; i++) {
            int[] r = topBtn[i];
            boolean h = NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
            if (perf && i == 0) {
                NewUi.frame(c, r[0], r[1], r[2], r[3], Math.max(3, sl(10)), RED_B, h ? RED_HT : RED_T, h ? RED_HB : RED_B);
            } else {
                NewUi.frame(c, r[0], r[1], r[2], r[3], Math.max(3, sl(10)), BTN_BORDER,
                        h ? HOV_T : BTN_T, h ? HOV_B : BTN_B);
            }
            int s = Math.max(8, Math.round(r[3] * 0.46f));
            NewUi.whole(c, TOP_TEX[i], r[0] + (r[2] - s) / 2, r[1] + (r[3] - s) / 2, s);
        }

        boolean ch = NewUi.in(mx, my, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3]);
        int red = NewUi.rgb(214, 48, 54);
        NewUi.frame(c, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3], Math.max(3, sl(9)), red,
                ch ? NewUi.rgb(78, 22, 26) : NewUi.rgb(38, 14, 17), ch ? NewUi.rgb(60, 17, 20) : NewUi.rgb(28, 11, 13));
        int cx = closeBtn[0] + closeBtn[2] / 2;
        int cy = closeBtn[1] + closeBtn[3] / 2;
        int s = Math.max(3, closeBtn[3] / 5);
        int xc = NewUi.rgb(236, 66, 72);
        for (int t = -s; t <= s; t++) {
            c.fill(cx + t, cy + t, cx + t + 2, cy + t + 1, xc);
            c.fill(cx + t, cy - t, cx + t + 2, cy - t + 1, xc);
        }
    }

    private void drawToolbar(DrawContext c, int mx, int my) {
        String[] tabNames = tabs();
        for (int i = 0; i < tabNames.length; i++) {
            int[] r = tabRect[i];
            boolean active = i == tab;
            boolean h = NewUi.in(mx, my, r[0], r[1], r[2], r[3]);
            int rad = Math.max(3, sl(8));
            if (active) {
                NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, h ? RED_HT : RED_T, h ? RED_HB : RED_B);
            } else {
                NewUi.rrect(c, r[0], r[1], r[2], r[3], rad, h ? HOV_T : BTN_T, h ? HOV_B : BTN_B);
            }
            c.drawText(textRenderer, tabNames[i], r[0] + (r[2] - textRenderer.getWidth(tabNames[i])) / 2,
                    r[1] + (r[3] - textRenderer.fontHeight) / 2, active ? TXT : TXT_TAB, false);
        }
        boolean sh = NewUi.in(mx, my, searchRect[0], searchRect[1], searchRect[2], searchRect[3]);
        NewUi.rrect(c, searchRect[0], searchRect[1], searchRect[2], searchRect[3], Math.max(3, sl(8)),
                sh ? HOV_T : BTN_T, sh ? HOV_B : BTN_B);

        boolean fh = NewUi.in(mx, my, favBtn[0], favBtn[1], favBtn[2], favBtn[3]);
        NewUi.rrect(c, favBtn[0], favBtn[1], favBtn[2], favBtn[3], Math.max(3, sl(8)),
                fh ? HOV_T : BTN_T, fh ? HOV_B : BTN_B);
        int hs = Math.max(8, Math.round(favBtn[3] * 0.5f));
        NewUi.icon(c, favOnly ? NewUi.ICON_HEART_ON : NewUi.ICON_HEART_OFF,
                favBtn[0] + (favBtn[2] - hs) / 2, favBtn[1] + (favBtn[3] - hs) / 2, hs);

        boolean gh = NewUi.in(mx, my, gridBtn[0], gridBtn[1], gridBtn[2], gridBtn[3]);
        NewUi.rrect(c, gridBtn[0], gridBtn[1], gridBtn[2], gridBtn[3], Math.max(3, sl(8)),
                gh ? HOV_T : BTN_T, gh ? HOV_B : BTN_B);
        // Performance view: this button opens the device selector (High End / Medium / Low End)
        int gridIcon = perf ? 34 + Math.max(0, NewPerf.device(client) - 1) : NewUi.ICON_GRID;
        NewUi.icon(c, gridIcon, gridBtn[0] + (gridBtn[2] - hs) / 2, gridBtn[1] + (gridBtn[3] - hs) / 2, hs);
    }

    /** Performance view: what the hovered mode does at its current value, in a small box next to the pointer. */
    private void drawPerfHint(DrawContext c, List<NewModSettings.ModDef> list, int mx, int my) {
        if (!inClip(mx, my)) {
            return;
        }
        for (int i = 0; i < list.size(); i++) {
            Geo g = geos[i];
            if (!NewUi.in(mx, my, g.x, g.y, g.w, g.h)) {
                continue;
            }
            int idx = NewPerf.indexOf(list.get(i).id);
            int w = Math.min(sl(340), width - 12);
            List<OrderedText> lines = textRenderer.wrapLines(Text.literal(NewPerf.DESC[idx][NewPerf.level(client, idx)]), w - sl(24));
            int fh = textRenderer.fontHeight;
            int h = sl(12) + fh + 4 + lines.size() * (fh + 2) + sl(12);
            int x = Math.max(4, Math.min(width - w - 6, mx + 12));
            int y = Math.max(4, Math.min(height - h - 6, my + 14));
            NewUi.frame(c, x, y, w, h, Math.max(3, sl(8)), CARD_BORDER, PANEL_T, PANEL_B);
            c.drawText(textRenderer, Text.literal(NewPerf.LABELS[idx]).formatted(Formatting.BOLD), x + sl(12), y + sl(12), TXT, false);
            for (int l = 0; l < lines.size(); l++) {
                c.drawText(textRenderer, lines.get(l), x + sl(12), y + sl(12) + fh + 4 + l * (fh + 2), TXT_DIM, false);
            }
            return;
        }
    }

    /** Moves a stored 0..1 value towards target (frame-rate independent); a first-seen card snaps, no animation on open. */
    private float easeTo(Map<String, Float> map, String id, float target, float speed) {
        if (NewPerf.animationsOff()) { // HUD Optimizer: no UI animations
            map.put(id, target);
            return target;
        }
        float cur = map.getOrDefault(id, target);
        cur += (target - cur) * Math.min(1f, animDt * speed);
        if (Math.abs(target - cur) < 0.004f) {
            cur = target;
        }
        map.put(id, cur);
        return cur;
    }

    private void drawCard(DrawContext c, NewModSettings.ModDef m, Geo g, int mx, int my, boolean over) {
        int perfIdx = perf ? NewPerf.indexOf(m.id) : -1;
        boolean enabled = perf ? NewPerf.on(client, perfIdx) : m.functional && NewModSettings.isEnabled(client, m.id);
        boolean hov = over && NewUi.in(mx, my, g.x, g.y, g.w, g.h);
        int cr = Math.max(3, sl(10));

        NewUi.frame(c, g.x, g.y, g.w, g.h, cr, hov ? NewUi.rgb(44, 52, 64) : CARD_BORDER,
                hov ? HOV_T : CARD_T, hov ? HOV_B : CARD_B);

        // title (trimmed to leave room for the heart)
        int nameMax = g.hx - g.nx - 3;
        String name = m.name;
        if (textRenderer.getWidth(name) > nameMax) {
            name = textRenderer.trimToWidth(name, Math.max(0, nameMax - textRenderer.getWidth(".."))) + "..";
        }
        c.drawText(textRenderer, name, g.nx, g.ny, enabled ? TXT : TXT_DIM, false);

        // heart
        boolean hh = over && NewUi.in(mx, my, g.hx, g.hy, g.hs, g.hs);
        NewUi.rrect(c, g.hx, g.hy, g.hs, g.hs, Math.max(3, sl(7)), hh ? HOV_T : BTN_T, hh ? HOV_B : BTN_B);
        int hi = Math.max(8, Math.round(g.hs * 0.55f));
        NewUi.icon(c, NewModSettings.isFavorite(client, m.id) ? NewUi.ICON_HEART_ON : NewUi.ICON_HEART_OFF,
                g.hx + (g.hs - hi) / 2, g.hy + (g.hs - hi) / 2, hi);

        // icon, dimmed while the mod is off
        NewUi.icon(c, m.icon, g.ix, g.iy, g.is);
        if (!enabled && !hov) {
            c.fill(g.ix, g.iy, g.ix + g.is, g.iy + g.is, 0x8C0F1115);
        }

        // NEW! badge
        if (m.isNew) {
            int bw = textRenderer.getWidth("NEW!") + 6;
            int bh = textRenderer.fontHeight + 3;
            int bx = g.x - sl(6);
            int by = g.y - sl(3);
            NewUi.rrect(c, bx, by, bw, bh, 2, NEW_T, NEW_B);
            c.drawText(textRenderer, "NEW!", bx + 3, by + 2, TXT, false);
        }

        if (perf) {
            // value chips: Small / Medium / High (High = most FPS)
            int lvl = NewPerf.level(client, perfIdx);
            for (int j = 0; j < 3; j++) {
                int chx = g.cx + j * (g.cw + g.cgap);
                boolean chh = over && NewUi.in(mx, my, chx, g.cy, g.cw, g.ch);
                int crad = Math.max(2, sl(7));
                if (j == lvl && enabled) {
                    NewUi.rrect(c, chx, g.cy, g.cw, g.ch, crad, chh ? RED_HT : RED_T, chh ? RED_HB : RED_B);
                } else if (j == lvl) {
                    NewUi.rrect(c, chx, g.cy, g.cw, g.ch, crad, HOV_T, HOV_B);
                } else {
                    NewUi.rrect(c, chx, g.cy, g.cw, g.ch, crad, chh ? HOV_T : BTN_T, chh ? HOV_B : BTN_B);
                }
                String cl = NewPerf.LEVELS[j];
                c.drawText(textRenderer, cl, chx + (g.cw - textRenderer.getWidth(cl)) / 2,
                        g.cy + (g.ch - textRenderer.fontHeight) / 2, j == lvl || chh ? TXT : TXT_DIM, false);
            }
        } else {
            // gear
            boolean gh = over && NewUi.in(mx, my, g.gx, g.gy, g.gs, g.gs);
            NewUi.rrect(c, g.gx, g.gy, g.gs, g.gs, Math.max(3, sl(8)), gh ? HOV_T : BTN_T, gh ? HOV_B : BTN_B);
            int gi = Math.max(8, Math.round(g.gs * 0.6f));
            NewUi.icon(c, NewUi.ICON_GEAR, g.gx + (g.gs - gi) / 2, g.gy + (g.gs - gi) / 2, gi);
        }

        // Enabled / Disabled pill
        boolean ph = over && m.functional && NewUi.in(mx, my, g.px, g.py, g.pw, g.ph);
        // animated pill: the green fills in from the left when switched on (and drains when off),
        // a light edge sweeps along while it moves, and the hover glow fades in / out
        float t = easeTo(pillOn, m.id, enabled ? 1f : 0f, 9f);
        float e = t * t * (3f - 2f * t);
        float hv = easeTo(pillHover, m.id, ph ? 1f : 0f, 14f);
        int rad = Math.max(3, sl(8));
        NewUi.rrect(c, g.px, g.py, g.pw, g.ph, rad, NewUi.lerp(BTN_T, HOV_T, hv), NewUi.lerp(BTN_B, HOV_B, hv));
        int fillW = Math.round(g.pw * e);
        if (fillW > 0) {
            NewUi.rrect(c, g.px, g.py, fillW, g.ph, rad, NewUi.lerp(ON_T, ON_HT, hv), NewUi.lerp(ON_B, ON_HB, hv));
        }
        if (t > 0.02f && t < 0.98f) {
            int edge = Math.max(2, sl(3));
            int ex = g.px + Math.max(0, Math.min(g.pw - edge, fillW - edge / 2));
            c.fill(ex, g.py + 2, ex + edge, g.py + g.ph - 2, 0x40FFFFFF);
        }
        String label = e >= 0.5f ? "Enabled" : "Disabled";
        c.drawText(textRenderer, label, g.px + (g.pw - textRenderer.getWidth(label)) / 2,
                g.py + (g.ph - textRenderer.fontHeight) / 2, NewUi.lerp(TXT_DIM, TXT_ON, e), false);
    }

    // ---------------------------------------------------------------- input

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) {
            return true;
        }
        if (click.buttonInfo().button() != 0) {
            return true;
        }
        double mx = click.x();
        double my = click.y();
        if (NewUi.in(mx, my, closeBtn[0], closeBtn[1], closeBtn[2], closeBtn[3])) {
            closeMenu();
            return true;
        }
        if (NewUi.in(mx, my, topBtn[0][0], topBtn[0][1], topBtn[0][2], topBtn[0][3])
                || (perf && NewUi.in(mx, my, modTab[0], modTab[1], modTab[2], modTab[3]))) {
            // top bar gear: Mod Menu <-> Performance
            client.setScreen(new NewModMenuScreen(parent, !perf));
            return true;
        }
        if (perf && NewUi.in(mx, my, gridBtn[0], gridBtn[1], gridBtn[2], gridBtn[3])) {
            client.setScreen(new NewDeviceSelectorScreen(this));
            return true;
        }
        for (int i = 0; i < tabs().length; i++) {
            int[] r = tabRect[i];
            if (NewUi.in(mx, my, r[0], r[1], r[2], r[3])) {
                tab = i;
                scrollTarget = 0f;
                return true;
            }
        }
        if (NewUi.in(mx, my, favBtn[0], favBtn[1], favBtn[2], favBtn[3])) {
            favOnly = !favOnly;
            scrollTarget = 0f;
            return true;
        }
        if (inClip(mx, my)) {
            // A tap acts on release; a drag scrolls (works with a finger too).
            pressActive = true;
            dragScrolling = false;
            travel = 0.0;
            pressedHit = hitAt(mx, my);
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (pressActive) {
            travel += Math.abs(offsetX) + Math.abs(offsetY);
            if (!dragScrolling && travel > 5.0) {
                dragScrolling = true;
            }
            if (dragScrolling) {
                scrollTarget -= (float) offsetY;
            }
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (pressActive) {
            pressActive = false;
            Hit pressed = pressedHit;
            pressedHit = null;
            if (!dragScrolling && pressed != null && pressed.kind != HIT_NONE) {
                Hit now = hitAt(click.x(), click.y());
                if (now.kind == pressed.kind && now.mod == pressed.mod && now.arg == pressed.arg) {
                    if (pressed.kind == HIT_HEART) {
                        NewModSettings.toggleFavorite(client, pressed.mod.id);
                    } else if (pressed.kind == HIT_PILL && perf) {
                        int pi = NewPerf.indexOf(pressed.mod.id);
                        NewPerf.setOn(client, pi, !NewPerf.on(client, pi));
                    } else if (pressed.kind == HIT_CHIP) {
                        NewPerf.setLevel(client, NewPerf.indexOf(pressed.mod.id), pressed.arg);
                    } else if (pressed.kind == HIT_PILL) {
                        NewModSettings.toggle(client, pressed.mod);
                    }
                    else if (pressed.kind == HIT_GEAR && pressed.mod.functional
                            && !NewModOptions.forMod(pressed.mod.id).isEmpty()) {
                        client.setScreen(new NewModOptionsScreen(this, pressed.mod));
                    }
                    // HIT_CARD: decorative for now
                }
            }
            dragScrolling = false;
            return true;
        }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scrollTarget -= (float) verticalAmount * 28f;
        return true;
    }

    @Override
    public boolean keyPressed(KeyInput keyEvent) {
        if (keyEvent.key() == 256) {
            closeMenu();
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }
}
