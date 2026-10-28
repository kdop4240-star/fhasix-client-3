package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.ConnectScreen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.network.ServerAddress;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * FHASIX Discover window (formerly the placeholder "Partners" button). Reuses the exact
 * same floating-panel geometry, dim overlay and exit button as CosmeticsScreen/StoreScreen -
 * only the center content differs: a 3-per-row grid of server cards, a real search field,
 * and a category filter dropdown instead of the cosmetics grid / store banners.
 *
 * Live ping + server icons come from FhasixServerPinger (plain socket + Gson, no Minecraft
 * networking API). The one remaining vanilla-networking touchpoint is joinServer()'s call to
 * ConnectScreen.connect - if the build fails there, paste the compiler error back.
 *
 * Scrolling works three ways so it also works on touch (Android): mouse wheel, dragging the
 * grid itself (with a short fling), and dragging / clicking the scrollbar. A tap on a card
 * joins on release, so a swipe never joins a server by accident.
 */
public class DiscoverScreen extends Screen {

    private final Screen parent;

    private static final Identifier EXIT_TEXTURE =
            Identifier.of("fhasixuitheme", "textures/gui/cosmetics/exit.png");
    private static final int EXIT_TEX_W = 1536;
    private static final int EXIT_TEX_H = 1402;
    private static final int EXIT_Y_OFFSET = 8;

    // Shared default background for every card (the "GILLIGAN" village screenshot).
    private static final Identifier CARD_BG_TEXTURE =
            Identifier.of("fhasixuitheme", "textures/gui/discover/card_background.png");
    private static final int CARD_BG_TEX_W = 1200;
    private static final int CARD_BG_TEX_H = 558;

    private static final int[] PANEL_TL = {14, 17, 22};
    private static final int[] PANEL_BR = {9, 10, 14};
    private static final int[] FIELD_TL = {25, 29, 36};
    private static final int[] FIELD_BR = {15, 17, 21};
    private static final int[] PING_PILL_TL = {15, 17, 20};
    private static final int[] PING_PILL_BR = {10, 11, 14};

    /** Overlay alpha at rest (~80%, per the original request) and at full hover-zoom (~35%). */
    private static final int OVERLAY_ALPHA_IDLE = 0xCC;
    private static final int OVERLAY_ALPHA_HOVER = 0x59;

    private static final float ZOOM_HOVERED = 1.14f;
    private static final float ZOOM_IDLE = 1.0f;
    /** Exponential-smoothing rate applied per real elapsed second (see CardEntry.updateZoom). */
    private static final float ZOOM_SPEED = 4.5f;

    /**
     * "Recommended" is the sentinel meaning "no filter, show everything" - it is not a real
     * per-server tag. Every other entry is a tag a server can carry (a server can carry
     * several, e.g. "Practice, BedWars, PvP", and shows up under each of them).
     */
    private static final String[] CATEGORIES = {
            "Recommended", "PvP", "Lifesteal", "BedWars", "Practice", "BoxPvP", "KitPvP",
            "Duels", "FFA", "CrystalPvP", "Survival", "Anarchy", "Economy", "Other"
    };

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int tabsY;
    private int dropX, dropY, dropW, fieldH;
    private int searchX, searchW;
    private int scrollOffset = 0;
    private long lastFrameNanos = -1;
    private static boolean cardBgFilterApplied = false;

    // Scrolling. scrollOffset is the whole-pixel position everything draws with; scrollPrecise
    // keeps the fractional part so slow drags and the fling don't stall between pixels.
    private float scrollPrecise = 0f;
    private float flingVelocity = 0f;   // px/s, carries on after a finger/mouse drag is released
    private boolean pressActive = false; // a left press that began inside the card grid
    private double pressX, pressY, dragTravel;
    private boolean dragScrolling = false;
    private CardEntry pressedCard = null;
    private long lastDragNanos = 0L;
    private float dragVelocity = 0f;
    private boolean thumbDragging = false;
    private double thumbGrab = 0.0;

    private String selectedCategory = "Recommended";
    private boolean categoryMenuOpen = false;
    private TextFieldWidget searchField;
    private String lastQuery = "";

    private final List<CardEntry> servers = new ArrayList<>();
    private boolean pingsStarted = false;

    public DiscoverScreen(Screen parent) {
        super(Text.literal("Discover"));
        this.parent = parent;
        addServer("ALWTO.FUN", "play.alwto.fun", "Other");
        addServer("GamerTee", "gamertee.net", "Other");
        addServer("PlumSMP", "fun.plumsmp.net", "Survival");
        addServer("CrazyMC", "play.crazymc.net", "Other");
        addServer("PvP Paradise", "pvpparadise.net", "PvP");
        addServer("FireMC", "play.firemc.fun", "Other");
        addServer("BlueMC", "play.bluemc.fun", "Other");
        addServer("WaterMC", "play.watermc.in", "Other");

        addServer("AyorMC", "mc.ayormc.net", "Practice, BedWars, PvP");
        addServer("BlockMC", "play.blockmc.fun", "Lifesteal, PvP");
        addServer("KhelMC", "play.khelmc.online", "FFA, Lifesteal");
        addServer("CryzorMC", "play.cryzormc.in", "Practice, BoxPvP, Lifesteal");
        addServer("SkyZenMC", "play.skyzenmc.qzz.io", "Lifesteal, BedWars");
        addServer("RixxyMC", "play.rixxymc.in", "Practice, BedWars, Lifesteal");
        addServer("Pookie SMP", "pookiesmp.in", "Practice, Lifesteal, BedWars");
        addServer("Valocraft", "play.valocraft.fun", "Duels, FFA, PvP");
        addServer("XyrixGames", "play.xyrixgames.fun", "Duels, CrystalPvP, Lifesteal");
        addServer("RuleX MC", "rulexmc.mcsh.io", "Practice, PvP, Lifesteal");
        addServer("Don Network", "play.donnetwork.in", "Practice, BedWars, Lifesteal");
        addServer("TwilightSMP", "play.twilightsmp.in:25567", "Duels, FFA, BoxPvP");
        addServer("StarNetwork", "starnetwork.cbu.net:19136", "PvP, BoxPvP, Lifesteal");
        addServer("ThunderMC", "play.thundermcog.fun:25570", "KitPvP, Lifesteal, PvP");
        addServer("SnifferMC", "play.sniffermc.fun:25567", "Survival, PvP");
        addServer("MCGOLD", "play.mcgold.fun", "Lifesteal, PvP");
        addServer("MomoMC", "momomc.net", "PvP, Economy");
        addServer("Mantra SMP", "play.worldofangara.in", "PvP, BedWars, Survival");
        addServer("Ultimate SMP", "play.ultimateplayz.fun", "PvP, Survival");
        // Aloo SMP (PvP, Survival, Minigames) is intentionally not listed yet: no address was
        // given. Add it here once there is one, and add "Minigames" to CATEGORIES.
        addServer("BharatMC", "play.bharatmc.net", "Lifesteal, PvP");
        addServer("SpaceMC", "play.spacemc.lol", "BoxPvP, KitPvP, Lifesteal");
        addServer("ND Network", "play.ndnetwork.in", "KitPvP, Lifesteal");
        addServer("BlitzMC", "blitzmc.fun", "PvP, Lifesteal, Anarchy");
        addServer("PrimeX Anarchy", "play.primexanarchy.net", "PvP, Anarchy");
        addServer("LOL SMP", "play.shivxtreme.fun", "KitPvP, PvP, Lifesteal");
        addServer("Wsmp", "play.wsmp.fun", "KitPvP, PvP, Lifesteal");
        addServer("CoolMC", "play.coolmc.fun", "BoxPvP");
        addServer("LalaMC", "play.lalamc.fun", "PvP, Lifesteal");
        addServer("MCNP Network", "play.mcnpnetwork.com", "BedWars, Lifesteal");
    }

    /** tagsCsv is a comma-separated tag list, e.g. "Practice, BedWars, PvP". */
    private void addServer(String name, String address, String tagsCsv) {
        servers.add(new CardEntry(name, address, tagsCsv));
    }

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
        contentH = panelH - Math.round(panelH * 0.10f);

        dropX = contentX;
        dropY = contentY;
        dropW = Math.round(contentW * 0.28f);
        fieldH = Math.max(30, Math.round(panelH * 0.06f));
        searchX = contentX + dropW + Math.round(contentW * 0.02f);
        searchW = contentW - dropW - Math.round(contentW * 0.02f);

        // A slim field vertically centered inside the taller decorative pill, rather than
        // handing TextFieldWidget the full pill height - its own internal text-centering
        // doesn't know about our custom pill height and was rendering noticeably high
        // inside it.
        int searchFieldH = Math.max(14, textRenderer.fontHeight + 6);
        int searchFieldY = dropY + (fieldH - searchFieldH) / 2;
        searchField = new TextFieldWidget(textRenderer, searchX + 8, searchFieldY, searchW - 16, searchFieldH,
                Text.literal("Search"));
        searchField.setMaxLength(48);
        searchField.setDrawsBackground(false);
        try {
            searchField.setPlaceholder(Text.literal("Search..."));
        } catch (Throwable ignored) {
            // Older/renamed API for placeholder text - harmless if unavailable, the field
            // still works for typing, it just won't show a hint when empty.
        }
        this.addDrawableChild(searchField);

        // Linear filtering (no mipmaps) so the hover-zoom crop - which stretches a smaller
        // sampled region back up to the card's full size - stays smooth instead of blocky.
        if (!cardBgFilterApplied) {
            cardBgFilterApplied = true;
        }

        lastFrameNanos = System.nanoTime();
        // pingAsync() only queues work on FhasixServerPinger's own thread pool, so this never
        // blocks the render thread (the freeze-on-open lesson). init() also re-runs on window
        // resize / rotation, hence the once-per-screen guard - a rotation must not re-ping
        // every server.
        if (!pingsStarted) {
            pingsStarted = true;
            for (CardEntry entry : servers) {
                entry.startPing(client);
            }
        }
    }

    @Override
    public void removed() {
        for (CardEntry entry : servers) {
            entry.close();
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        if (parent != null) {
            parent.render(context, mouseX, mouseY, delta);
        }

        context.fill(0, 0, width, height, 0x42000000);
        a1.drawRoundedGradientRect(context, panelX, panelY, panelW, panelH, PANEL_TL, PANEL_BR, 248, 2);

        long now = System.nanoTime();
        float dt = lastFrameNanos < 0 ? 0f : (now - lastFrameNanos) / 1_000_000_000f;
        lastFrameNanos = now;

        updateFling(dt);

        drawHeader(context);
        drawControls(context, mouseX, mouseY);
        drawCards(context, mouseX, mouseY, dt);
        drawScrollbar(context);
        drawExit(context, mouseX, mouseY);

        // Renders searchField (added via addDrawableChild) on top of the pill we already
        // drew for it in drawControls() - same order StoreScreen's own code-entry field uses.
        super.render(context, mouseX, mouseY, delta);

        if (categoryMenuOpen) {
            drawCategoryMenu(context, mouseX, mouseY);
        }
    }

    private void drawHeader(DrawContext context) {
        int titleX = panelX - Math.round(panelW * 0.14f);
        int titleY = panelY - Math.round(height * 0.105f);
        context.drawText(textRenderer, "DISCOVER", titleX, titleY, 0xFFF0F0F0, false);
    }

    private void drawControls(DrawContext context, int mouseX, int mouseY) {
        // Same instant hover swap b2 (Singleplayer/Multiplayer/Discover, etc.) already uses -
        // NAVY_HOVER_* colors on hover, FIELD colors otherwise.
        boolean dropHovered = !categoryMenuOpen
                && withinRect(mouseX, mouseY, dropX, dropY, dropW, fieldH);
        int[] dropTL = dropHovered ? a1.NAVY_HOVER_TOP_LEFT : FIELD_TL;
        int[] dropBR = dropHovered ? a1.NAVY_HOVER_BOTTOM_RIGHT : FIELD_BR;
        a1.drawRoundedGradientRect(context, dropX, dropY, dropW, fieldH, dropTL, dropBR, 245);
        context.drawText(textRenderer, selectedCategory, dropX + 12,
                dropY + (fieldH - textRenderer.fontHeight) / 2, 0xFFB9B9B9, false);
        context.drawText(textRenderer, categoryMenuOpen ? "\u2303" : "\u2304", dropX + dropW - 16,
                dropY + (fieldH - textRenderer.fontHeight) / 2, 0xFF8E8E8E, false);

        boolean searchHovered = withinRect(mouseX, mouseY, searchX, dropY, searchW, fieldH)
                || (searchField != null && searchField.isFocused());
        int[] searchTL = searchHovered ? a1.NAVY_HOVER_TOP_LEFT : FIELD_TL;
        int[] searchBR = searchHovered ? a1.NAVY_HOVER_BOTTOM_RIGHT : FIELD_BR;
        a1.drawRoundedGradientRect(context, searchX, dropY, searchW, fieldH, searchTL, searchBR, 245);
    }

    private static boolean withinRect(int mouseX, int mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    /**
     * Menu geometry as {rowH, columns, rowsPerColumn}. With 14 categories the old fixed 20px
     * rows can run past the bottom of the panel on small windows, so rows shrink to fit (never
     * below 14px) and the list splits into two columns if even that isn't enough.
     */
    private int[] menuLayout() {
        int menuY = dropY + fieldH + 4;
        int avail = Math.max(40, (panelY + panelH - 8) - menuY);
        int n = CATEGORIES.length;
        int rowH = Math.max(14, Math.min(20, avail / n));
        int cols = rowH * n > avail ? 2 : 1;
        int perCol = (n + cols - 1) / cols;
        if (cols == 2) {
            rowH = Math.max(14, Math.min(20, avail / perCol));
        }
        return new int[]{rowH, cols, perCol};
    }

    private void drawCategoryMenu(DrawContext context, int mouseX, int mouseY) {
        int[] lay = menuLayout();
        int rowH = lay[0];
        int cols = lay[1];
        int perCol = lay[2];
        int menuY = dropY + fieldH + 4;

        a1.drawRoundedGradientRect(context, dropX, menuY, dropW * cols, rowH * perCol, FIELD_TL, FIELD_BR, 250, 8);
        for (int i = 0; i < CATEGORIES.length; i++) {
            int col = i / perCol;
            int rowY = menuY + (i % perCol) * rowH;
            int colX = dropX + col * dropW;
            boolean hovered = mouseX >= colX && mouseX < colX + dropW && mouseY >= rowY && mouseY < rowY + rowH;
            if (hovered) {
                context.fill(colX + 4, rowY + 1, colX + dropW - 4, rowY + rowH - 1, 0x33FFFFFF);
            }
            boolean selected = CATEGORIES[i].equals(selectedCategory);
            int color = selected ? 0xFF7FD1FF : 0xFFD8D8D8;
            context.drawText(textRenderer, CATEGORIES[i], colX + 12,
                    rowY + (rowH - textRenderer.fontHeight) / 2, color, false);
        }
    }

    private boolean handleCategoryMenuClick(double mouseX, double mouseY) {
        int[] lay = menuLayout();
        int rowH = lay[0];
        int cols = lay[1];
        int perCol = lay[2];
        int menuY = dropY + fieldH + 4;
        if (mouseX >= dropX && mouseX < dropX + dropW * cols && mouseY >= menuY && mouseY < menuY + rowH * perCol) {
            int col = (int) ((mouseX - dropX) / dropW);
            int row = (int) ((mouseY - menuY) / rowH);
            int idx = col * perCol + row;
            if (col < cols && row < perCol && idx >= 0 && idx < CATEGORIES.length) {
                selectedCategory = CATEGORIES[idx];
                setScroll(0f);
            }
        }
        categoryMenuOpen = false;
        return true;
    }

    /** Servers matching the current search text + category (tag) filter, in stable list order. */
    private List<CardEntry> visibleServers() {
        String query = searchField != null ? searchField.getText().trim().toLowerCase() : "";
        List<CardEntry> out = new ArrayList<>();
        for (CardEntry entry : servers) {
            if (!query.isEmpty() && !entry.name.toLowerCase().contains(query)) continue;
            if (!"Recommended".equals(selectedCategory) && !entry.hasTag(selectedCategory)) continue;
            out.add(entry);
        }
        return out;
    }

    // ---- scrolling helpers --------------------------------------------------------------------

    /** Total scrollable range for the currently visible cards. */
    private int maxScroll() {
        int cols = cardCols();
        int rows = (visibleServers().size() + cols - 1) / cols;
        int cardH = cardHeight(cardWidth());
        int totalHeight = rows * cardH + Math.max(0, rows - 1) * cardGap();
        int visibleHeight = (panelY + panelH - 14) - gridStartY();
        return Math.max(0, totalHeight - visibleHeight);
    }

    /** Single place that moves the grid: clamps, and keeps the whole-pixel and precise values in sync. */
    private void setScroll(float value) {
        scrollPrecise = Math.max(0f, Math.min(maxScroll(), value));
        scrollOffset = Math.round(scrollPrecise);
    }

    /** Momentum after a drag ends: decays smoothly and stops dead at either end. */
    private void updateFling(float dt) {
        if (Math.abs(flingVelocity) < 8f || pressActive || thumbDragging) {
            if (!pressActive) {
                flingVelocity = 0f;
            }
            return;
        }
        float before = scrollPrecise;
        setScroll(scrollPrecise + flingVelocity * dt);
        if (scrollPrecise == before) {
            flingVelocity = 0f; // hit the top or bottom
            return;
        }
        flingVelocity *= (float) Math.exp(-4.2f * dt);
    }

    private int cardCols() {
        return 3;
    }

    private int cardGap() {
        return Math.max(10, Math.round(contentW * 0.018f));
    }

    private int cardWidth() {
        int cols = cardCols();
        int gap = cardGap();
        return (contentW - gap * (cols - 1)) / cols;
    }

    private int cardHeight(int cardW) {
        return Math.round(cardW * 0.56f);
    }

    private int gridStartY() {
        return contentY + Math.round(panelH * 0.105f);
    }

    private void drawCards(DrawContext context, int mouseX, int mouseY, float dt) {
        String query = searchField != null ? searchField.getText() : "";
        if (!query.equals(lastQuery)) {
            lastQuery = query;
            setScroll(0f);
        }

        List<CardEntry> visible = visibleServers();
        int cols = cardCols();
        int gap = cardGap();
        int cardW = cardWidth();
        int cardH = cardHeight(cardW);
        int startY = gridStartY();
        int viewBottom = panelY + panelH - 14;

        if (visible.isEmpty()) {
            context.drawCenteredTextWithShadow(textRenderer, "No servers match that search",
                    contentX + contentW / 2, startY + 20, 0xFF9A9EA4);
            return;
        }

        context.enableScissor(contentX, startY, contentX + contentW, viewBottom);
        for (int i = 0; i < visible.size(); i++) {
            int row = i / cols;
            int col = i % cols;
            int x = contentX + col * (cardW + gap);
            int y = startY + row * (cardH + gap) - scrollOffset;
            if (y + cardH < startY || y > viewBottom) {
                continue;
            }

            CardEntry entry = visible.get(i);
            boolean hovered = mouseX >= x && mouseX < x + cardW && mouseY >= y && mouseY < y + cardH
                    && mouseY >= startY && mouseY < viewBottom;
            entry.updateZoom(hovered, dt);
            drawCard(context, entry, x, y, cardW, cardH);
        }
        context.disableScissor();
    }

    /**
     * Same track/thumb look as Store's drawDecorativeScrollbar(), but actually driven by
     * scrollOffset - Store's version is fixed decoration and never moves, this one does.
     */
    private void drawScrollbar(DrawContext context) {
        if (visibleServers().isEmpty()) return;
        int[] g = scrollbarGeometry();
        if (g == null) return;

        context.fill(g[0], g[1], g[0] + 5, g[1] + g[2], 0xFF171A1F);
        // g[5] == 0 means everything already fits: full-height thumb, nothing to scroll.
        int thumbColor = thumbDragging ? 0xFFA3A7AC : 0xFF73777C;
        context.fill(g[0], g[3], g[0] + 5, g[3] + g[4], thumbColor);
    }

    /** {trackX, trackY, trackH, thumbY, thumbH, maxScroll}, or null if the panel has no room for a track. */
    private int[] scrollbarGeometry() {
        int trackY = gridStartY();
        int trackH = (panelY + panelH - 14) - trackY;
        if (trackH <= 0) return null;
        int trackX = contentX + contentW + Math.round(panelW * 0.006f);
        int max = maxScroll();
        if (max <= 0) {
            return new int[]{trackX, trackY, trackH, trackY, trackH, 0};
        }
        int totalHeight = max + trackH;
        int thumbH = Math.max(20, Math.round(trackH * (trackH / (float) totalHeight)));
        int thumbY = trackY + Math.round((trackH - thumbH) * (scrollOffset / (float) max));
        return new int[]{trackX, trackY, trackH, thumbY, thumbH, max};
    }

    /** Moves the grid so the scrollbar thumb sits under the pointer (minus where it was grabbed). */
    private void dragThumbTo(double mouseY) {
        int[] g = scrollbarGeometry();
        if (g == null || g[5] <= 0) return;
        double travel = g[2] - g[4];
        if (travel <= 0) return;
        double frac = Math.max(0.0, Math.min(1.0, ((mouseY - thumbGrab) - g[1]) / travel));
        setScroll((float) (frac * g[5]));
    }

    /** The card under a point inside the grid viewport, or null (gap, or outside the grid). */
    private CardEntry cardAt(double mouseX, double mouseY) {
        int startY = gridStartY();
        int viewBottom = panelY + panelH - 14;
        if (mouseY < startY || mouseY >= viewBottom) return null;

        List<CardEntry> visible = visibleServers();
        int cols = cardCols();
        int gap = cardGap();
        int cardW = cardWidth();
        int cardH = cardHeight(cardW);
        for (int i = 0; i < visible.size(); i++) {
            int cx = contentX + (i % cols) * (cardW + gap);
            int cy = startY + (i / cols) * (cardH + gap) - scrollOffset;
            if (mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + cardH) {
                return visible.get(i);
            }
        }
        return null;
    }

    private void drawCard(DrawContext context, CardEntry entry, int x, int y, int cardW, int cardH) {
        // Kept modest on purpose - corner-erase cost scales with radius^2 per card.
        int radius = Math.max(6, Math.min(12, Math.round(cardH * 0.05f)));

        float zoom = entry.zoom;
        float cropSize = 1f / zoom;
        float cropU = (1f - cropSize) / 2f;
        float cropV = (1f - cropSize) / 2f;

        // Overlay fades out in step with the same eased hover-zoom progress, instead of
        // sitting at a fixed 80% regardless of hover state.
        float hoverT = a1.clamp((zoom - ZOOM_IDLE) / (ZOOM_HOVERED - ZOOM_IDLE), 0f, 1f);
        int overlayAlpha = Math.round(OVERLAY_ALPHA_IDLE + (OVERLAY_ALPHA_HOVER - OVERLAY_ALPHA_IDLE) * hoverT);
        int overlayColor = overlayAlpha << 24;

        a1.drawRoundedTexture(context, CARD_BG_TEXTURE, x, y, cardW, cardH,
                cropU, cropV, cropSize, cropSize, CARD_BG_TEX_W, CARD_BG_TEX_H,
                overlayColor, radius, panelX, panelY, panelW, panelH, PANEL_TL, PANEL_BR);

        drawIcon(context, entry, x, y, cardH);
        drawPing(context, entry, x, y, cardW);
        drawName(context, entry, x, y, cardW, cardH);
    }

    private void drawIcon(DrawContext context, CardEntry entry, int x, int y, int cardH) {
        int size = Math.max(20, Math.round(cardH * 0.22f));
        int ix = x + Math.round(cardH * 0.06f);
        int iy = y + Math.round(cardH * 0.06f);

        Identifier favicon = entry.faviconTexture;
        if (favicon != null) {
            context.drawTexture(RenderPipelines.GUI_TEXTURED, favicon, ix, iy, 0, 0,
                    size, size, 64, 64, 64, 64);
        } else {
            a1.drawRoundedGradientRect(context, ix, iy, size, size,
                    entry.badgeColorTop, entry.badgeColorBottom, 255, Math.round(size * 0.22f));
            context.drawCenteredTextWithShadow(textRenderer, entry.initials,
                    ix + size / 2, iy + (size - textRenderer.fontHeight) / 2, 0xFFF2F2F2);
        }
    }

    private void drawPing(DrawContext context, CardEntry entry, int x, int y, int cardW) {
        String label = entry.pingLabel();
        int dotColor = entry.pingColor();
        int textW = textRenderer.getWidth(label);
        int pillW = textW + 22;
        int pillH = 16;
        int px = x + cardW - pillW - 8;
        int py = y + 8;

        a1.drawRoundedGradientRect(context, px, py, pillW, pillH, PING_PILL_TL, PING_PILL_BR, 220, pillH / 2);
        int dotCx = px + 10;
        int dotCy = py + pillH / 2;
        context.fill(dotCx - 3, dotCy - 3, dotCx + 3, dotCy + 3, 0xFF000000 | dotColor);
        context.drawText(textRenderer, label, px + 18, py + (pillH - textRenderer.fontHeight) / 2, 0xFFE6E6E6, false);
    }

    private void drawName(DrawContext context, CardEntry entry, int x, int y, int cardW, int cardH) {
        int scrimH = Math.round(cardH * 0.34f);
        context.fillGradient(x, y + cardH - scrimH, x + cardW, y + cardH, 0x00000000, 0xB0000000);
        context.drawText(textRenderer, entry.name, x + 10, y + cardH - textRenderer.fontHeight - 8, 0xFFFFFFFF, true);
    }

    private void drawExit(DrawContext context, int mouseX, int mouseY) {
        int size = Math.max(42, Math.round(height * 0.065f));
        int x = panelX + panelW - size;
        int y = tabsY - EXIT_Y_OFFSET;
        context.drawTexture(RenderPipelines.GUI_TEXTURED, EXIT_TEXTURE, x, y, 0, 0,
                size, size, EXIT_TEX_W, EXIT_TEX_H, EXIT_TEX_W, EXIT_TEX_H);
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.buttonInfo().button();

        if (categoryMenuOpen) {
            return handleCategoryMenuClick(mouseX, mouseY);
        }

        if (button == 0 && mouseX >= dropX && mouseX < dropX + dropW && mouseY >= dropY && mouseY < dropY + fieldH) {
            categoryMenuOpen = true;
            return true;
        }

        if (super.mouseClicked(click, doubled)) {
            return true;
        }

        int size = Math.max(42, Math.round(height * 0.065f));
        int exitX = panelX + panelW - size;
        int exitY = tabsY - EXIT_Y_OFFSET;
        if (button == 0 && mouseX >= exitX && mouseX < exitX + size && mouseY >= exitY && mouseY < exitY + size) {
            client.setScreen(parent);
            return true;
        }

        if (button == 0) {
            // Scrollbar: grab the thumb, or click the track to jump there and keep dragging.
            // The hit zone is much wider than the 5px bar so it works with a finger.
            int[] sg = scrollbarGeometry();
            if (sg != null && sg[5] > 0 && mouseX >= sg[0] - 10 && mouseX < sg[0] + 15
                    && mouseY >= sg[1] && mouseY < sg[1] + sg[2]) {
                boolean onThumb = mouseY >= sg[3] && mouseY < sg[3] + sg[4];
                thumbGrab = onThumb ? mouseY - sg[3] : sg[4] / 2.0;
                thumbDragging = true;
                flingVelocity = 0f;
                if (!onThumb) {
                    dragThumbTo(mouseY);
                }
                return true;
            }

            // Grid: only START a press here. Whether it was a tap (join) or the beginning of a
            // swipe (scroll) isn't known until release/drag - joining on press is what made
            // touch scrolling impossible, since every swipe began by joining a server.
            if (mouseX >= contentX && mouseX < contentX + contentW
                    && mouseY >= gridStartY() && mouseY < panelY + panelH - 14) {
                pressActive = true;
                pressX = mouseX;
                pressY = mouseY;
                dragTravel = 0.0;
                dragScrolling = false;
                dragVelocity = 0f;
                flingVelocity = 0f;
                lastDragNanos = System.nanoTime();
                pressedCard = cardAt(mouseX, mouseY);
                return true;
            }
        }
        return true;
    }

    @Override
    public boolean mouseDragged(Click click, double offsetX, double offsetY) {
        if (thumbDragging) {
            dragThumbTo(click.y());
            return true;
        }
        if (pressActive) {
            dragTravel += Math.abs(offsetX) + Math.abs(offsetY);
            if (!dragScrolling && dragTravel > 8.0) {
                dragScrolling = true; // moved too far to be a tap - it's a scroll gesture now
            }
            if (dragScrolling) {
                setScroll(scrollPrecise - (float) offsetY);
                long now = System.nanoTime();
                float dtSeconds = Math.max(1f / 240f, (now - lastDragNanos) / 1_000_000_000f);
                lastDragNanos = now;
                // smoothed finger speed, handed to updateFling() on release
                dragVelocity = dragVelocity * 0.55f + ((float) -offsetY / dtSeconds) * 0.45f;
            }
            return true;
        }
        return super.mouseDragged(click, offsetX, offsetY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (thumbDragging) {
            thumbDragging = false;
            return true;
        }
        if (pressActive) {
            pressActive = false;
            CardEntry tapped = pressedCard;
            pressedCard = null;
            if (dragScrolling) {
                dragScrolling = false;
                // Lifted while still moving: keep gliding. Lifted after pausing: stop dead.
                boolean stillMoving = System.nanoTime() - lastDragNanos < 90_000_000L;
                flingVelocity = stillMoving ? Math.max(-3000f, Math.min(3000f, dragVelocity)) : 0f;
                return true;
            }
            // A tap: join only if it also ENDS on the card it started on.
            if (tapped != null && click.buttonInfo().button() == 0
                    && cardAt(click.x(), click.y()) == tapped) {
                joinServer(tapped);
            }
            return true;
        }
        return super.mouseReleased(click);
    }

    // mouseScrolled keeps the classic (double, double, double, double) shape - checked against
    // the 1.21.11 mappings, where only click/release/drag moved onto the Click record.
    // Wheel scrolling is the desktop path; touch uses the drag handling above.
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        flingVelocity = 0f;
        setScroll(scrollPrecise - (float) verticalAmount * 24f);
        return true;
    }

    private void joinServer(CardEntry entry) {
        // See the class-level PORTING NOTE - ConnectScreen.connect's parameter list has
        // grown (quick-play/transfer support) across versions on top of this original
        // (parent, client, address, info, boolean, transferData) shape. This is the same
        // call vanilla's own multiplayer "Join Server" button makes.
        ServerInfo info = new ServerInfo(entry.name, entry.address, ServerInfo.ServerType.OTHER);
        ServerAddress address = ServerAddress.parse(entry.address);
        ConnectScreen.connect(parent, client, address, info, false, null);
    }

    @Override
    public boolean keyPressed(KeyInput keyEvent) {
        if (keyEvent.key() == 256) {
            if (categoryMenuOpen) {
                categoryMenuOpen = false;
                return true;
            }
            client.setScreen(parent);
            return true;
        }
        return super.keyPressed(keyEvent);
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    /** One server card's static info plus its own live ping/favicon state and hover-zoom animation. */
    private static final class CardEntry {
        final String name;
        final String address;
        final String[] tags;
        final String initials;
        final int[] badgeColorTop;
        final int[] badgeColorBottom;

        float zoom = ZOOM_IDLE;

        volatile int ping = -1;
        volatile boolean pingFailed = false;
        volatile Identifier faviconTexture;
        /** Set when the screen closes so a late ping result doesn't register textures for nobody. */
        private volatile boolean closed = false;

        CardEntry(String name, String address, String tagsCsv) {
            this.name = name;
            this.address = address;
            this.tags = tagsCsv.split("\\s*,\\s*");
            this.initials = initials(name);
            int hue = Math.floorMod(name.hashCode(), 360);
            this.badgeColorTop = hsvToRgb(hue, 0.55f, 0.5f);
            this.badgeColorBottom = hsvToRgb(hue, 0.65f, 0.28f);
        }

        private static String initials(String name) {
            String[] parts = name.replace(".", " ").trim().split("\\s+");
            StringBuilder sb = new StringBuilder();
            for (String part : parts) {
                if (!part.isEmpty() && sb.length() < 2) {
                    sb.append(Character.toUpperCase(part.charAt(0)));
                }
            }
            return sb.length() > 0 ? sb.toString() : "?";
        }

        private static int[] hsvToRgb(int hueDeg, float sat, float val) {
            float c = val * sat;
            float x = c * (1 - Math.abs((hueDeg / 60f) % 2 - 1));
            float m = val - c;
            float r, g, b;
            if (hueDeg < 60) { r = c; g = x; b = 0; }
            else if (hueDeg < 120) { r = x; g = c; b = 0; }
            else if (hueDeg < 180) { r = 0; g = c; b = x; }
            else if (hueDeg < 240) { r = 0; g = x; b = c; }
            else if (hueDeg < 300) { r = x; g = 0; b = c; }
            else { r = c; g = 0; b = x; }
            return new int[]{
                    Math.round((r + m) * 255),
                    Math.round((g + m) * 255),
                    Math.round((b + m) * 255)
            };
        }

        /**
         * Exponential smoothing scaled by real elapsed time (System.nanoTime()-based dt),
         * not by tick or frame count - this is what keeps the hover zoom looking equally
         * smooth whether the client is rendering at 60fps or 120fps+, rather than advancing
         * in fixed steps that would look chunkier the faster the game renders.
         */
        void updateZoom(boolean hovered, float dt) {
            float target = hovered ? ZOOM_HOVERED : ZOOM_IDLE;
            float t = 1f - (float) Math.exp(-ZOOM_SPEED * Math.max(0f, dt));
            zoom += (target - zoom) * t;
        }

        /** True if this server carries the given tag (case-insensitive). */
        boolean hasTag(String tag) {
            for (String t : tags) {
                if (t.equalsIgnoreCase(tag)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Queues a real server-list ping on FhasixServerPinger's pool; returns immediately.
         * A reachable server always ends up with its round-trip time shown, and "offline" is
         * only ever set when the connection/handshake itself failed - nothing can overwrite a
         * successful result afterwards.
         */
        void startPing(MinecraftClient client) {
            FhasixServerPinger.pingAsync(address, result -> {
                if (closed) {
                    return;
                }
                if (!result.online) {
                    pingFailed = true;
                    return;
                }
                ping = result.pingMs;
                pingFailed = false;
                if (result.favicon != null) {
                    loadFavicon(client, result.favicon);
                }
            });
        }

        private void loadFavicon(MinecraftClient client, byte[] png) {
            try {
                NativeImage image = NativeImage.read(new ByteArrayInputStream(png));
                String safeName = address.toLowerCase().replaceAll("[^a-z0-9]", "_");
                Identifier id = Identifier.of("fhasixuitheme", "discover_favicon_" + safeName);
                client.execute(() -> {
                    if (closed) {
                        image.close();
                        return;
                    }
                    // 1.21.11: the one-argument NativeImageBackedTexture(NativeImage) is gone;
                    // it takes a name supplier first.
                    client.getTextureManager().registerTexture(id,
                            new NativeImageBackedTexture(() -> "fhasix_favicon", image));
                    faviconTexture = id;
                });
            } catch (Exception ignored) {
                // Malformed/missing favicon - the initials badge fallback stays in place.
            }
        }

        void close() {
            closed = true;
        }

        String pingLabel() {
            if (pingFailed) return "offline";
            if (ping < 0) return "...";
            return ping + "ms";
        }

        int pingColor() {
            if (pingFailed) return 0xE05B5B;
            if (ping < 0) return 0x8A8F96;
            if (ping < 150) return 0x5BD16A;
            if (ping < 300) return 0xE0C15B;
            return 0xE05B5B;
        }
    }
}
