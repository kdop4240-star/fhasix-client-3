package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.InventoryScreen;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.client.network.AbstractClientPlayerEntity;
import com.mojang.authlib.GameProfile;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/** FHASIX Cosmetics window. All major surfaces are rendered mathematically in code. */
public class CosmeticsScreen extends Screen {
    private final Screen parent;

    private static final Identifier EXIT_TEXTURE =
            Identifier.of("fhasixuitheme", "textures/gui/cosmetics/exit.png");
    private static final Identifier FIRST_OPTIX_TEXTURE =
            Identifier.of("fhasixuitheme", "textures/gui/store/first_optix_collab.png");
    private static final Identifier RED_CHARMS_TEXTURE =
            Identifier.of("fhasixuitheme", "textures/gui/store/red_charms.png");
    private static final int FIRST_OPTIX_TEX_W = 928;
    private static final int FIRST_OPTIX_TEX_H = 1536;
    private static final int RED_CHARMS_TEX_W = 1263;
    private static final int RED_CHARMS_TEX_H = 1536;
    private static final int EXIT_TEX_W = 1536;
    private static final int EXIT_TEX_H = 1402;

    private int panelX, panelY, panelW, panelH;
    private int contentX, contentY, contentW, contentH;
    private int tabsY;
    private static final int EXIT_Y_OFFSET = 8;
    private PreviewPlayerEntity previewPlayer;
    private SkinTextures previewSkin;

    private static final int[] PANEL_TL = {14, 17, 22};
    private static final int[] PANEL_BR = {9, 10, 14};
    private static final int[] FIELD_TL = {25, 29, 36};
    private static final int[] FIELD_BR = {15, 17, 21};
    private static final int[] CARD_TL = {16, 20, 25};
    private static final int[] CARD_BR = {11, 13, 17};
    private static final int[] TAB_TL = {24, 28, 35};
    private static final int[] TAB_BR = {14, 15, 19};
    private static final int[] TAB_HOVER_TL = {56, 67, 81};
    private static final int[] TAB_HOVER_BR = {18, 20, 26};

    private final String[] tabNames = {
            "Capes", "Wings", "Banners", "Emotes", "Hats", "Glasses", "Masks", "Shoes", "Particles", "Characters"
    };

    public CosmeticsScreen(Screen parent) {
        super(Text.literal("Cosmetics"));
        this.parent = parent;
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
        contentW = Math.round(panelW * 0.64f);
        contentH = panelH - Math.round(panelH * 0.10f);
        // A preview entity requires a real ClientWorld. The title screen has no
        // world, so never construct one with null (that causes class_742's
        // constructor to dereference the missing world and crash).
        if (client.player == null) {
            // PORTING NOTE: 1.21.8+ collapsed getSkinTexture()/getCapeTexture()/getModel()/etc.
            // into a single SkinTextures record; PlayerSkinProvider#loadSkin(GameProfile):Identifier
            // no longer exists, so the skin is fetched as a SkinTextures instead.
            GameProfile profile = new GameProfile(client.getSession().getUuidOrNull(), client.getSession().getUsername());
            previewSkin = client.getSkinProvider().supplySkinTextures(profile, false).get();
            if (client.world != null) {
                previewPlayer = new PreviewPlayerEntity(client.world, profile, previewSkin);
            }
        }
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        // Keep the existing FHASIX title screen visible behind the floating window.
        if (parent != null) {
            parent.render(context, mouseX, mouseY, delta);
        }

        // Soft dim over the existing menu, then the floating cosmetics panel.
        context.fill(0, 0, width, height, 0x42000000);
        a1.drawRoundedGradientRect(context, panelX, panelY, panelW, panelH,
                PANEL_TL, PANEL_BR, 248, 2);

        drawHeader(context);
        drawTabs(context, mouseX, mouseY);
        drawControls(context);
        drawCards(context, mouseX, mouseY);
        drawPlayerPreview(context);
        drawDecorativeScrollbar(context);
        drawExit(context, mouseX, mouseY);
    }

    private void drawHeader(DrawContext context) {
        int titleX = panelX - Math.round(panelW * 0.14f);
        int titleY = panelY - Math.round(height * 0.105f);
        context.drawText(textRenderer, "COSMETICS", titleX, titleY, 0xFFF0F0F0, false);
    }

    private void drawTabs(DrawContext context, int mouseX, int mouseY) {
        int tabSize = Math.max(38, Math.round(height * 0.06f));
        int gap = Math.max(8, Math.round(tabSize * 0.18f));
        int total = tabNames.length * tabSize + (tabNames.length - 1) * gap;
        int x = panelX;
        // Keep the row visually aligned with the reference; if the viewport is smaller,
        // compress spacing rather than changing the panel itself.
        if (total > panelW) {
            gap = Math.max(3, (panelW - tabNames.length * tabSize) / (tabNames.length - 1));
            total = tabNames.length * tabSize + (tabNames.length - 1) * gap;
        }
        x = panelX;

        for (int i = 0; i < tabNames.length; i++) {
            int tx = x + i * (tabSize + gap);
            boolean hovered = mouseX >= tx && mouseX < tx + tabSize && mouseY >= tabsY && mouseY < tabsY + tabSize;
            int[] tl = hovered ? TAB_HOVER_TL : TAB_TL;
            int[] br = hovered ? TAB_HOVER_BR : TAB_BR;
            a1.drawRoundedGradientRect(context, tx, tabsY, tabSize, tabSize, tl, br, 245);
            drawTabGlyph(context, tx + tabSize / 2, tabsY + tabSize / 2, i);
        }
    }

    private void drawTabGlyph(DrawContext context, int cx, int cy, int index) {
        int c = 0xFFEDEDED;
        int s = 7;
        switch (index) {
            case 0 -> { // cape
                context.fill(cx - s, cy - 5, cx + s + 1, cy - 3, c);
                context.fill(cx - s + 2, cy - 3, cx + s - 1, cy + 5, c);
            }
            case 1 -> { // wing
                context.fill(cx - 8, cy - 2, cx - 2, cy + 2, c);
                context.fill(cx + 2, cy - 2, cx + 8, cy + 2, c);
                context.fill(cx - 2, cy - 1, cx + 3, cy + 3, c);
            }
            case 2 -> { // banner
                context.fill(cx - 5, cy - 8, cx - 3, cy + 8, c);
                context.fill(cx - 3, cy - 7, cx + 6, cy + 1, c);
            }
            case 3 -> { // emote
                context.fill(cx - 7, cy - 5, cx + 7, cy + 5, c);
                context.fill(cx - 4, cy + 5, cx - 1, cy + 8, c);
            }
            case 4 -> { // hat
                context.fill(cx - 8, cy + 3, cx + 8, cy + 5, c);
                context.fill(cx - 5, cy - 5, cx + 5, cy + 3, c);
            }
            case 5 -> { // glasses
                context.fill(cx - 8, cy - 3, cx - 1, cy + 3, c);
                context.fill(cx + 1, cy - 3, cx + 8, cy + 3, c);
            }
            case 6 -> { // mask
                context.fill(cx - 7, cy - 5, cx + 7, cy + 5, c);
                context.fill(cx - 4, cy - 1, cx - 2, cy + 1, TAB_TL[0] << 16 | TAB_TL[1] << 8 | TAB_TL[2]);
                context.fill(cx + 2, cy - 1, cx + 4, cy + 1, TAB_TL[0] << 16 | TAB_TL[1] << 8 | TAB_TL[2]);
            }
            case 7 -> { // shoe
                context.fill(cx - 7, cy, cx + 5, cy + 5, c);
                context.fill(cx + 3, cy + 3, cx + 8, cy + 5, c);
            }
            case 8 -> { // particles
                context.fill(cx - 2, cy - 8, cx + 2, cy - 4, c);
                context.fill(cx - 8, cy - 1, cx - 4, cy + 3, c);
                context.fill(cx + 4, cy - 1, cx + 8, cy + 3, c);
                context.fill(cx - 2, cy + 4, cx + 2, cy + 8, c);
            }
            default -> { // character
                context.fill(cx - 4, cy - 8, cx + 4, cy, c);
                context.fill(cx - 7, cy + 1, cx + 7, cy + 6, c);
            }
        }
    }

    private void drawControls(DrawContext context) {
        int fieldY = contentY;
        int dropW = Math.round(contentW * 0.47f);
        int searchX = contentX + dropW + Math.round(contentW * 0.025f);
        int searchW = contentW - dropW - Math.round(contentW * 0.025f);
        int fieldH = Math.max(30, Math.round(panelH * 0.06f));

        a1.drawRoundedGradientRect(context, contentX, fieldY, dropW, fieldH, FIELD_TL, FIELD_BR, 245);
        context.drawText(textRenderer, "All Collections", contentX + 12, fieldY + (fieldH - textRenderer.fontHeight) / 2, 0xFFB9B9B9, false);
        context.drawText(textRenderer, "⌄", contentX + dropW - 16, fieldY + (fieldH - textRenderer.fontHeight) / 2, 0xFF8E8E8E, false);

        a1.drawRoundedGradientRect(context, searchX, fieldY, searchW, fieldH, FIELD_TL, FIELD_BR, 245);
        context.drawText(textRenderer, "Search...", searchX + 12, fieldY + (fieldH - textRenderer.fontHeight) / 2, 0xFF8F9399, false);
    }

    private void drawCards(DrawContext context, int mouseX, int mouseY) {
        int gridW = Math.round(contentW * 0.98f);
        int cols = 4;
        int gap = Math.max(8, Math.round(gridW * 0.018f));
        int cardW = (gridW - gap * (cols - 1)) / cols;
        int cardH = Math.round(cardW * 1.42f);
        int startY = contentY + Math.round(panelH * 0.105f);
        int rows = 3;

        context.enableScissor(contentX, startY, contentX + gridW, panelY + panelH - 14);
        for (int row = 0; row < rows; row++) {
            for (int col = 0; col < cols; col++) {
                int x = contentX + col * (cardW + gap);
                int y = startY + row * (cardH + gap);
                a1.drawRoundedGradientRect(context, x, y, cardW, cardH, CARD_TL, CARD_BR, 225);

                if (row == 0 && col == 0 && FhasixCosmetics.ownsFirstOptix()) {
                    drawCosmeticImage(context, FIRST_OPTIX_TEXTURE, FIRST_OPTIX_TEX_W, FIRST_OPTIX_TEX_H,
                            x, y, cardW, cardH);
                    drawFirstOptixControls(context, x, y, cardW, cardH, mouseX, mouseY);
                } else if (row == 0 && col == 1 && FhasixCosmetics.ownsSecondCape()) {
                    drawCosmeticImage(context, RED_CHARMS_TEXTURE, RED_CHARMS_TEX_W, RED_CHARMS_TEX_H,
                            x, y, cardW, cardH);
                    drawSecondCapeControls(context, x, y, cardW, cardH, mouseX, mouseY);
                } else {
                    // Remaining cosmetic slots stay empty until more cosmetics are delivered.
                    drawLock(context, x + cardW - 16, y + 12);
                }
            }
        }
        context.disableScissor();
    }

    private void drawCosmeticImage(DrawContext context, Identifier texture, int textureW, int textureH,
                                   int x, int y, int cardW, int cardH) {
        int pad = Math.max(8, Math.round(cardW * 0.08f));
        int imageW = cardW - pad * 2;
        int imageH = Math.round(imageW * (float) textureH / textureW);
        int maxImageH = Math.round(cardH * 0.52f);
        if (imageH > maxImageH) {
            imageH = maxImageH;
            imageW = Math.round(imageH * (float) textureW / textureH);
        }
        int imageX = x + (cardW - imageW) / 2;
        int imageY = y + Math.max(8, Math.round(cardH * 0.04f));
        context.drawTexture(RenderPipelines.GUI_TEXTURED, texture, imageX, imageY, 0, 0,
                imageW, imageH, textureW, textureH, textureW, textureH);
    }

    private void drawFirstOptixControls(DrawContext context, int x, int y, int cardW, int cardH,
                                        int mouseX, int mouseY) {
        int centerX = x + cardW / 2;
        int nameY = y + Math.round(cardH * 0.57f);
        context.drawCenteredTextWithShadow(textRenderer, "First Optix (Collab) cape", centerX, nameY, 0xFFE0E0E0);

        boolean equipped = FhasixCosmetics.isFirstOptixEquipped();
        if (equipped) {
            context.drawCenteredTextWithShadow(textRenderer, "Equipped", centerX, nameY + textRenderer.fontHeight + 2, 0xFFBFC2C6);
        }

        int buttonW = Math.max(78, Math.round(cardW * 0.58f));
        int buttonH = Math.max(22, Math.round(cardH * 0.13f));
        int buttonX = x + (cardW - buttonW) / 2;
        int buttonY = y + cardH - buttonH - Math.max(10, Math.round(cardH * 0.07f));
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonW
                && mouseY >= buttonY && mouseY < buttonY + buttonH;
        int[] tl = hovered ? TAB_HOVER_TL : TAB_TL;
        int[] br = hovered ? TAB_HOVER_BR : TAB_BR;
        a1.drawRoundedGradientRect(context, buttonX, buttonY, buttonW, buttonH, tl, br, 245, 5);
        context.drawCenteredTextWithShadow(textRenderer, equipped ? "UNEQUIP" : "EQUIP",
                centerX, buttonY + (buttonH - textRenderer.fontHeight) / 2, 0xFFF0F0F0);
    }

    private void drawSecondCapeControls(DrawContext context, int x, int y, int cardW, int cardH,
                                        int mouseX, int mouseY) {
        int centerX = x + cardW / 2;
        int nameY = y + Math.round(cardH * 0.57f);

        context.drawCenteredTextWithShadow(textRenderer, "Red Charms cape", centerX, nameY, 0xFFE0E0E0);
        boolean equipped = FhasixCosmetics.isSecondCapeEquipped();
        if (equipped) {
            context.drawCenteredTextWithShadow(textRenderer, "Equipped", centerX, nameY + textRenderer.fontHeight + 2, 0xFFBFC2C6);
        }

        int buttonW = Math.max(78, Math.round(cardW * 0.58f));
        int buttonH = Math.max(22, Math.round(cardH * 0.13f));
        int buttonX = x + (cardW - buttonW) / 2;
        int buttonY = y + cardH - buttonH - Math.max(10, Math.round(cardH * 0.07f));
        boolean hovered = mouseX >= buttonX && mouseX < buttonX + buttonW
                && mouseY >= buttonY && mouseY < buttonY + buttonH;
        int[] tl = hovered ? TAB_HOVER_TL : TAB_TL;
        int[] br = hovered ? TAB_HOVER_BR : TAB_BR;
        a1.drawRoundedGradientRect(context, buttonX, buttonY, buttonW, buttonH, tl, br, 245, 5);
        context.drawCenteredTextWithShadow(textRenderer, equipped ? "UNEQUIP" : "EQUIP",
                centerX, buttonY + (buttonH - textRenderer.fontHeight) / 2, 0xFFF0F0F0);
    }

    private void drawLock(DrawContext context, int x, int y) {
        int c = 0xFF85888C;
        context.fill(x - 4, y, x + 5, y + 7, c);
        context.fill(x - 2, y - 4, x + 3, y + 1, c);
        context.fill(x, y + 2, x + 2, y + 5, CARD_TL[0] << 16 | CARD_TL[1] << 8 | CARD_TL[2]);
    }

    private void drawPlayerPreview(DrawContext context) {
        int rightX = panelX + Math.round(panelW * 0.72f);
        int rightW = panelX + panelW - rightX;
        int titleY = contentY + Math.round(panelH * 0.05f);
        context.drawCenteredTextWithShadow(textRenderer, "YOUR PLAYER", rightX + rightW / 2, titleY,
                0xFFD0D0D0);

        MinecraftClient client = MinecraftClient.getInstance();
        net.minecraft.entity.LivingEntity preview = client.player != null ? client.player : previewPlayer;
        if (preview != null) {
            int entityX = rightX + rightW / 2;
            int entityY = panelY + Math.round(panelH * 0.70f);
            int size = Math.min(105, Math.round(panelH * 0.24f));
            // PORTING NOTE: InventoryScreen.drawEntity now takes a bounding box (x1,y1,x2,y2)
            // plus a separate scale factor instead of a single center point + pixel size.
            // This reconstructs the old "centered at (entityX, entityY), radius size" framing
            // as a square box of that size, scale left neutral at 1.0 - not verified in-game.
            InventoryScreen.drawEntity(context,
                    entityX - size, entityY - size * 2, entityX + size, entityY,
                    size, 1.0f,
                    entityX - 80.0f, entityY - 80.0f, preview);
        } else if (previewSkin != null) {
            // Title screen has no ClientWorld, so use the account skin safely
            // instead of constructing an AbstractClientPlayerEntity without a world.
            int skinSize = Math.min(96, Math.round(panelH * 0.22f));
            int skinX = rightX + (rightW - skinSize) / 2;
            int skinY = panelY + Math.round(panelH * 0.50f);
            // PORTING NOTE (v18.3): PlayerSkinDrawer.draw's SkinTextures overload takes no
            // trailing tint/color argument as of 1.21.9+ - the 6-arg call here previously
            // didn't match any real overload, which is a real compile error (not just a
            // logic issue), and is the most likely reason a build tool facing it gave up
            // and stripped this class's nested types instead of fixing it.
            PlayerSkinDrawer.draw(context, previewSkin, skinX, skinY, skinSize);
        }
    }

    private void drawDecorativeScrollbar(DrawContext context) {
        int x = contentX + contentW + Math.round(panelW * 0.006f);
        int y = contentY + Math.round(panelH * 0.095f);
        int h = Math.round(panelH * 0.72f);
        context.fill(x, y, x + 5, y + h, 0xFF171A1F);
        context.fill(x, y + 3, x + 5, y + Math.round(h * 0.25f), 0xFF73777C);
    }

    private void drawExit(DrawContext context, int mouseX, int mouseY) {
        int size = Math.max(42, Math.round(height * 0.065f));
        int x = panelX + panelW - size;
        int y = tabsY - EXIT_Y_OFFSET;
        // The supplied image is used directly, without filtering or alteration.
        context.drawTexture(RenderPipelines.GUI_TEXTURED, EXIT_TEXTURE, x, y, 0, 0,
                size, size, EXIT_TEX_W, EXIT_TEX_H, EXIT_TEX_W, EXIT_TEX_H);
    }

    // PORTING NOTE (v18.2): see StoreScreen.mouseClicked for why this signature had to change.
    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.buttonInfo().button();

        int size = Math.max(42, Math.round(height * 0.065f));
        int exitX = panelX + panelW - size;
        int exitY = tabsY - EXIT_Y_OFFSET;
        if (button == 0 && mouseX >= exitX && mouseX < exitX + size && mouseY >= exitY && mouseY < exitY + size) {
            client.setScreen(parent);
            return true;
        }

        int tabSize = Math.max(38, Math.round(height * 0.06f));
        int gap = Math.max(8, Math.round(tabSize * 0.18f));
        int total = tabNames.length * tabSize + (tabNames.length - 1) * gap;
        if (total > panelW) {
            gap = Math.max(3, (panelW - tabNames.length * tabSize) / (tabNames.length - 1));
        }
        int x = panelX;
        for (int i = 0; i < tabNames.length; i++) {
            int tx = x + i * (tabSize + gap);
            if (button == 0 && mouseX >= tx && mouseX < tx + tabSize && mouseY >= tabsY && mouseY < tabsY + tabSize) {
                client.setScreen(new ComingSoonScreen(this, tabNames[i]));
                return true;
            }
        }
        if (button == 0) {
            int gridW = Math.round(contentW * 0.98f);
            int cols = 4;
            int cardGap = Math.max(8, Math.round(gridW * 0.018f));
            int cardW = (gridW - cardGap * (cols - 1)) / cols;
            int cardH = Math.round(cardW * 1.42f);
            int startY = contentY + Math.round(panelH * 0.105f);

            int buttonW = Math.max(78, Math.round(cardW * 0.58f));
            int buttonH = Math.max(22, Math.round(cardH * 0.13f));
            int buttonY = startY + cardH - buttonH - Math.max(10, Math.round(cardH * 0.07f));

            if (FhasixCosmetics.ownsFirstOptix()) {
                int buttonX = contentX + (cardW - buttonW) / 2;
                if (mouseX >= buttonX && mouseX < buttonX + buttonW
                        && mouseY >= buttonY && mouseY < buttonY + buttonH) {
                    FhasixCosmetics.toggleFirstOptix();
                    return true;
                }
            }

            if (FhasixCosmetics.ownsSecondCape()) {
                int secondX = contentX + (cardW + cardGap);
                int buttonX = secondX + (cardW - buttonW) / 2;
                if (mouseX >= buttonX && mouseX < buttonX + buttonW
                        && mouseY >= buttonY && mouseY < buttonY + buttonH) {
                    FhasixCosmetics.toggleSecondCape();
                    return true;
                }
            }
        }

        return true;
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private static final class PreviewPlayerEntity extends AbstractClientPlayerEntity {
        private final SkinTextures skin;

        private PreviewPlayerEntity(ClientWorld world, GameProfile profile, SkinTextures skin) {
            super(world, profile);
            this.skin = skin;
            this.setYaw(180.0F);
            this.setBodyYaw(180.0F);
            this.setPitch(0.0F);
        }

        // PORTING NOTE: 1.21.8+ replaced the old separate getSkinTexture()/hasSkinTexture()/
        // getModel()/canRenderCapeTexture()/getCapeTexture()/canRenderElytraTexture()/
        // getElytraTexture() overrides with this single getSkinTextures() accessor - the
        // SkinTextures record's own capeTexture/elytraTexture fields being null (as passed
        // in from CosmeticsScreen) is what now suppresses the cape/elytra on the preview.
        public SkinTextures getSkinTextures() {
            return skin;
        }

        @Override
        public boolean isCreative() {
            return false;
        }

        @Override
        public boolean isSpectator() {
            return false;
        }
    }

    private static class ComingSoonScreen extends Screen {
        private final Screen parent;
        private final String name;

        protected ComingSoonScreen(Screen parent, String name) {
            super(Text.literal(name));
            this.parent = parent;
            this.name = name;
        }

        @Override
        public void render(DrawContext context, int mouseX, int mouseY, float delta) {
            parent.render(context, mouseX, mouseY, delta);
            context.fill(0, 0, width, height, 0x88000000);
            int w = Math.round(width * 0.38f);
            int h = Math.round(height * 0.22f);
            int x = (width - w) / 2;
            int y = (height - h) / 2 + Math.round(height * 0.08f);
            a1.drawRoundedGradientRect(context, x, y, w, h, PANEL_TL, PANEL_BR, 250);
            context.drawCenteredTextWithShadow(textRenderer, name + " are not available", width / 2, y + 30, 0xFFFFFFFF);
            context.drawCenteredTextWithShadow(textRenderer, "This is only accessible in world", width / 2, y + 58, 0xFFBFC2C6);
            context.drawCenteredTextWithShadow(textRenderer, "Click anywhere to return", width / 2, y + h - 28, 0xFF7F8388);
        }

        @Override
        public boolean mouseClicked(Click click, boolean doubled) {
            client.setScreen(parent);
            return true;
        }

        @Override
        public boolean keyPressed(KeyInput keyEvent) {
            if (keyEvent.key() == 256) {
                client.setScreen(parent);
                return true;
            }
            return super.keyPressed(keyEvent);
        }

        @Override
        public boolean shouldPause() {
            return false;
        }
    }
}
