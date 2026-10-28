package com.fhasix.uitheme;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;

/**
 * Standalone banner screen used by the FHASIX title-screen top-bar buttons.
 * Kept outside the TitleScreen mixin so Mixin does not relocate a nested
 * screen class into TitleScreen and produce conflicting InnerClasses metadata.
 */
public final class WorldOnlyBannerScreen extends Screen {
    private final Screen parent;

    public WorldOnlyBannerScreen(Screen parent) {
        super(Text.literal("FHASIX"));
        this.parent = parent;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        parent.render(context, mouseX, mouseY, delta);
        context.fill(0, 0, width, height, 0x88000000);

        int w = Math.round(width * 0.38f);
        int h = Math.round(height * 0.22f);
        int x = (width - w) / 2;
        int y = (height - h) / 2;

        a1.drawRoundedGradientRect(context, x, y, w, h,
                new int[]{14, 17, 22}, new int[]{9, 10, 14}, 250);
        context.drawCenteredTextWithShadow(textRenderer,
                "This is only accessible in world", width / 2, y + 42, 0xFFFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
                "Click anywhere to return", width / 2, y + h - 28, 0xFF7F8388);
    }

    // PORTING NOTE (v18.2): 1.21.9 replaced Element's old mouseClicked(double,double,int)/
    // keyPressed(int,int,int) with mouseClicked(Click,boolean)/keyPressed(KeyEvent) - the old
    // signatures still compile (Screen/Element keep them as unrelated dead methods) but the
    // game no longer calls them, which is why every hand-built button in this mod stopped
    // responding to clicks after the 1.21.11 port even though it still rendered correctly.
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
