package com.fhasix.uitheme.mixin;

import com.fhasix.uitheme.c3;
import com.fhasix.uitheme.d4;
import com.fhasix.uitheme.CosmeticsScreen;
import com.fhasix.uitheme.DiscoverScreen;
import com.fhasix.uitheme.StoreScreen;
import com.fhasix.uitheme.b2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.RenderPipelines;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.TitleScreen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;
import net.minecraft.client.gui.screen.option.OptionsScreen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rebuilds the whole title screen:
 *  - Singleplayer / Multiplayer / Discover / Cosmetics / Store, stacked,
 *    positioned as fractions of the screen's scaled width/height (correct
 *    at GUI Scale 2 and every other scale).
 *  - a top-right row of 6 small square icon buttons (grid/chat/gear/gem/
 *    archive/list), same navy theme, sized+spaced off the reference image,
 *    rendering pre-made anti-aliased icon glyphs (see c3) at 70%
 *    opacity - button size unchanged, only the icon glyph inside is smaller.
 *  - an Account pill directly left of the icon row showing just the
 *    player's username (no skin head), same navy theme, also 70% opacity.
 *  - a translucent red Quit Game button below Store, with a soft animated
 *    red fade-in-on-hover (see d4) - the one button that still animates.
 *  - a custom background image instead of the vanilla panorama.
 *  - no Minecraft logo, no splash text (see fhasix$renderCustomScreen).
 */
@Mixin(TitleScreen.class)
public abstract class f6 extends Screen {

    private static final float BUTTON_WIDTH_FRAC = 0.19f;
    private static final float BUTTON_HEIGHT_FRAC = 0.06f;
    private static final float BUTTON_GAP_FRAC = 0.016f;
    private static final float FIRST_BUTTON_TOP_FRAC = 0.383f;
    /** Flat trim requested on top of the width fraction - buttons were "a bit wide." */
    private static final int BUTTON_WIDTH_TRIM_PX = 4;

    private static final float ICON_TOP_FRAC = 0.064f;
    private static final float ICON_RIGHT_MARGIN_FRAC = 0.063f;
    /** "100% opacity to 70%" on the top-bar widgets (icon row + Account pill). */
    private static final int TOPBAR_ALPHA = 179;
    /** Horizontal padding inside the Account pill, as a fraction of its height. */
    private static final float ACCOUNT_PADDING_FRACTION = 0.6f;
    private static final c3.Icon[] ICON_ROW = {
            c3.Icon.GRID, c3.Icon.CHAT, c3.Icon.GEAR,
            c3.Icon.GEM, c3.Icon.ARCHIVE, c3.Icon.LIST
    };

    /** Gap between Store and Quit is much bigger than the inter-button gap - measured off the reference. */
    private static final float QUIT_TOP_GAP_FRAC = 0.12f;
    /** Quit is narrower than the main buttons - measured off the reference (~79% of main width). */
    private static final float QUIT_WIDTH_SCALE = 0.79f;

    private static final Identifier BACKGROUND_TEXTURE =
            Identifier.of("fhasixuitheme", "textures/gui/title/background.png");
    private static final Identifier FHASIX_LOGO =
            Identifier.of("fhasixuitheme", "textures/gui/title/fhasix_logo.png");
    private static final int BACKGROUND_TEX_W = 1584;
    private static final int BACKGROUND_TEX_H = 672;
    private static final int LOGO_TEX_W = 1251;
    private static final int LOGO_TEX_H = 323;

    private static boolean backgroundFilterApplied = false;
    /** Runtime UI mode. False = FHASIX, true = vanilla Minecraft title screen. */
    private static boolean normalMinecraftUI = false;

    protected f6(Text title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void fhasix$rebuildTitleScreen(CallbackInfo ci) {
        if (com.fhasix.uitheme.NewSafe.disabled("title_init")) {
            return;
        }
        try {
            fhasix$rebuildTitleScreenImpl(ci);
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("title_init", t); // the vanilla title screen stays as it is
        }
    }

    private void fhasix$rebuildTitleScreenImpl(CallbackInfo ci) {
        TitleScreen self = (TitleScreen) (Object) this;

        // Normal Minecraft mode: leave every vanilla title-screen element untouched
        // and add only the small FHASIX switch-back button at the top.
        if (normalMinecraftUI) {
            int switchWidth = Math.max(120, Math.round(this.width * 0.16f));
            int switchHeight = Math.max(20, Math.round(this.height * 0.04f));
            int switchX = (this.width - switchWidth) / 2;
            int switchY = Math.max(6, Math.round(this.height * 0.025f));
            this.addDrawableChild(ButtonWidget.builder(Text.literal("Switch to FHASIX"),
                    press -> fhasix$switchToFhasix())
                    .dimensions(switchX, switchY, switchWidth, switchHeight)
                    .build());
            return;
        }

        // Hide (and disable) every vanilla button init() just added.
        for (Element child : this.children()) {
            if (child instanceof ButtonWidget button) {
                button.visible = false;
                button.active = false;
            }
        }

        int width = Math.round(this.width * BUTTON_WIDTH_FRAC) - BUTTON_WIDTH_TRIM_PX;
        int height = Math.round(this.height * BUTTON_HEIGHT_FRAC);
        int gap = Math.round(this.height * BUTTON_GAP_FRAC);
        int x = this.width / 2 - width / 2;
        int startY = Math.round(this.height * FIRST_BUTTON_TOP_FRAC);
        int step = height + gap;

        this.addDrawableChild(new b2(
                x, startY, width, height, Text.translatable("menu.singleplayer"),
                press -> this.client.setScreen(new SelectWorldScreen(self))
        ));
        this.addDrawableChild(new b2(
                x, startY + step, width, height, Text.translatable("menu.multiplayer"),
                press -> this.client.setScreen(new MultiplayerScreen(self))
        ));
        this.addDrawableChild(new b2(
                x, startY + step * 2, width, height, Text.literal("Discover"),
                press -> this.client.setScreen(new DiscoverScreen(self))
        ));
        this.addDrawableChild(new b2(
                x, startY + step * 3, width, height, Text.literal("Cosmetics"),
                press -> this.client.setScreen(new CosmeticsScreen(self))
        ));
        this.addDrawableChild(new b2(
                x, startY + step * 4, width, height, Text.literal("Store"),
                press -> this.client.setScreen(new StoreScreen(self)),
                b2.RED_NORMAL_LEFT, b2.RED_NORMAL_RIGHT,
                b2.RED_HOVER_LEFT, b2.RED_HOVER_RIGHT
        ));

        int quitWidth = Math.round(width * QUIT_WIDTH_SCALE);
        int quitX = this.width / 2 - quitWidth / 2;
        int quitY = startY + step * 4 + height + Math.round(this.height * QUIT_TOP_GAP_FRAC);
        this.addDrawableChild(new d4(
                quitX, quitY, quitWidth, height, Text.literal("Quit Game"),
                press -> this.client.scheduleStop()
        ));

        // Top-right icon row - same theme, square, sized/spaced off the reference.
        // Button size matches the main buttons' height (unchanged from v4) - only the
        // icon *glyph* drawn inside is smaller now (see c3's padding), and the
        // whole widget is drawn at 70% opacity per the aesthetic correction.
        int iconSize = height;
        int iconGap = gap;
        int rowWidth = ICON_ROW.length * iconSize + (ICON_ROW.length - 1) * iconGap;
        int iconStartX = Math.round(this.width - this.width * ICON_RIGHT_MARGIN_FRAC) - rowWidth;
        int iconY = Math.round(this.height * ICON_TOP_FRAC);

        // Account pill - username only, no skin head - sits directly left of the icon row.
        String username = this.client.getSession().getUsername();
        int accountPadding = Math.round(iconSize * ACCOUNT_PADDING_FRACTION);
        int accountTextWidth = this.client.textRenderer.getWidth(username);
        int accountWidth = accountTextWidth + accountPadding * 2;
        int accountX = iconStartX - iconGap - accountWidth;
        this.addDrawableChild(new b2(
                accountX, iconY, accountWidth, iconSize, Text.literal(username),
                press -> { /* not wired up yet */ },
                b2.NAVY_NORMAL_TOP_LEFT, b2.NAVY_NORMAL_BOTTOM_RIGHT,
                b2.NAVY_HOVER_TOP_LEFT, b2.NAVY_HOVER_BOTTOM_RIGHT,
                TOPBAR_ALPHA
        ));

        for (int i = 0; i < ICON_ROW.length; i++) {
            int iconX = iconStartX + i * (iconSize + iconGap);
            final c3.Icon icon = ICON_ROW[i];
            this.addDrawableChild(new c3(
                    iconX, iconY, iconSize, icon,
                    press -> {
                        if (icon == c3.Icon.LIST) {
                            fhasix$switchToMinecraft();
                        } else if (icon == c3.Icon.GEAR) {
                            client.setScreen(new OptionsScreen(self, client.options));
                        } else if (icon == c3.Icon.CHAT) {
                            client.setScreen(new com.fhasix.uitheme.ScreenshotsScreen(self));
                        } else if (icon == c3.Icon.GEM) {
                            client.setScreen(new com.fhasix.uitheme.SkinPopupScreen(self, iconX, iconY, iconSize));
                        } else if (icon == c3.Icon.GRID) {
                            // v30: opens the Mod Menu grid (the launcher itself is in-world only)
                            client.setScreen(new com.fhasix.uitheme.NewModMenuScreen(self));
                        } else {
                            client.setScreen(new com.fhasix.uitheme.WorldOnlyBannerScreen(self));
                        }
                    }
            ));
        }
    }

    private void fhasix$switchToMinecraft() {
        normalMinecraftUI = true;
        com.fhasix.uitheme.NewGuiScale.titleFhasix = false;
        client.setScreen(new TitleScreen());
    }

    private void fhasix$switchToFhasix() {
        normalMinecraftUI = false;
        com.fhasix.uitheme.NewGuiScale.titleFhasix = true;
        client.setScreen(new TitleScreen());
    }

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void fhasix$renderCustomScreen(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (com.fhasix.uitheme.NewSafe.disabled("title_render")) {
            return; // vanilla draws the title screen instead
        }
        try {
            fhasix$renderCustomScreenImpl(context, mouseX, mouseY, delta, ci);
        } catch (Exception | LinkageError t) {
            com.fhasix.uitheme.NewSafe.failed("title_render", t);
        }
    }

    private void fhasix$renderCustomScreenImpl(DrawContext context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (normalMinecraftUI) {
            return;
        }

        MinecraftClient client = MinecraftClient.getInstance();
        if (!backgroundFilterApplied) {
            backgroundFilterApplied = true;
        }

        context.drawTexture(RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, 0, 0, 0, 0,
                this.width, this.height, BACKGROUND_TEX_W, BACKGROUND_TEX_H, BACKGROUND_TEX_W, BACKGROUND_TEX_H);

        int buttonWidth = Math.round(this.width * BUTTON_WIDTH_FRAC) - BUTTON_WIDTH_TRIM_PX;
        int buttonHeight = Math.round(this.height * BUTTON_HEIGHT_FRAC);
        int logoWidth = Math.round(buttonWidth * 0.56f);
        int logoHeight = Math.round(logoWidth * (float) LOGO_TEX_H / LOGO_TEX_W);
        int logoX = (this.width - logoWidth) / 2;
        int logoY = Math.round(this.height * FIRST_BUTTON_TOP_FRAC) - logoHeight - Math.round(this.height * 0.025f);
        context.drawTexture(RenderPipelines.GUI_TEXTURED, FHASIX_LOGO, logoX, logoY, 0, 0,
                logoWidth, logoHeight, LOGO_TEX_W, LOGO_TEX_H, LOGO_TEX_W, LOGO_TEX_H);

        for (Element child : this.children()) {
            if (child instanceof Drawable drawable) {
                drawable.render(context, mouseX, mouseY, delta);
            }
        }

        ci.cancel();
    }


}
