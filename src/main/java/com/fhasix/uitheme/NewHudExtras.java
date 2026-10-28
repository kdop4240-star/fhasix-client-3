package com.fhasix.uitheme;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.effect.StatusEffectInstance;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;

/**
 * NEW. The three extra movable HUD mods: Coordinates, Potion Effects and Keystrokes. They plug into
 * NewHud's element list (same dragging / saving as FPS and CPS). Their only settings are
 * "Background" and "Border".
 */
final class NewHudExtras {

    static final String COORDINATES = "coordinates";
    static final String POTION_EFFECTS = "potion_effects";
    static final String KEYSTROKES = "keystrokes";

    private static final int BG_T = 0xB00C0D11;
    private static final int BG_B = 0xB008090C;
    private static final int LINE = 0xFF3A3F4A;
    private static final int DIM = 0xFF8C8F96;

    private static long lastNs = System.nanoTime();

    private NewHudExtras() {
    }

    /** Seconds since the previous call, capped, so animations are frame-rate independent. */
    private static float frameDt() {
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - lastNs) / 1_000_000_000f);
        lastNs = now;
        return dt;
    }

    /** Rounded panel honouring the mod's "Background" and "Border" settings. */
    private static void panel(DrawContext g, MinecraftClient c, String id, int x, int y, int w, int h, int r) {
        boolean bg = NewModSettings.optBool(c, id, "bg");
        boolean border = NewModSettings.optBool(c, id, "border");
        if (bg && border) {
            NewUi.frame(g, x, y, w, h, r, LINE, BG_T, BG_B);
        } else if (bg) {
            NewUi.rrect(g, x, y, w, h, r, BG_T, BG_B);
        } else if (border) {
            g.fill(x, y, x + w, y + 1, LINE);
            g.fill(x, y + h - 1, x + w, y + h, LINE);
            g.fill(x, y + 1, x + 1, y + h - 1, LINE);
            g.fill(x + w - 1, y + 1, x + w, y + h - 1, LINE);
        }
    }

    // ------------------------------------------------------------------ Coordinates

    static final class Coordinates extends NewHud.El {
        Coordinates(float defX, float defY) {
            super(COORDINATES, defX, defY);
        }

        private String[] lines(MinecraftClient c) {
            double x = c.player == null ? 0 : c.player.getX();
            double y = c.player == null ? 64 : c.player.getY();
            double z = c.player == null ? 0 : c.player.getZ();
            return new String[]{
                    "X  " + (int) Math.floor(x),
                    "Y  " + (int) Math.floor(y),
                    "Z  " + (int) Math.floor(z)
            };
        }

        @Override
        int[] size(MinecraftClient c, boolean preview) {
            int w = 0;
            for (String s : lines(c)) {
                w = Math.max(w, c.textRenderer.getWidth(s));
            }
            return new int[]{Math.max(46, w + 14), c.textRenderer.fontHeight * 3 + 4 * 2 + 8};
        }

        @Override
        void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview) {
            panel(g, c, id, x, y, w, h, 4);
            String[] l = lines(c);
            String[] axis = {"X", "Y", "Z"};
            int[] col = {0xFFE73C40, 0xFF3FBF4D, 0xFF3B82F6};
            int ty = y + 6;
            for (int i = 0; i < 3; i++) {
                g.drawText(c.textRenderer, axis[i], x + 7, ty, col[i], false);
                g.drawText(c.textRenderer, l[i].substring(3), x + 7 + c.textRenderer.getWidth("X") + 6, ty, 0xFFFFFFFF, false);
                ty += c.textRenderer.fontHeight + 4;
            }
        }
    }

    // ------------------------------------------------------------------ Potion Effects

    static final class PotionEffects extends NewHud.El {
        private static final String[] ROMAN = {"", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X"};

        PotionEffects(float defX, float defY) {
            super(POTION_EFFECTS, defX, defY);
        }

        /** {label, time, colour} per active effect; a sample pair in the launcher preview when none are active. */
        private List<Object[]> rows(MinecraftClient c, boolean preview) {
            List<Object[]> out = new ArrayList<>();
            if (c.player != null) {
                Collection<StatusEffectInstance> effects = c.player.getStatusEffects();
                for (StatusEffectInstance e : effects) {
                    int amp = e.getAmplifier() + 1;
                    String name = e.getEffectType().value().getName().getString();
                    String label = amp > 1 ? name + " " + (amp < ROMAN.length ? ROMAN[amp] : String.valueOf(amp)) : name;
                    String time = e.isInfinite() ? "**" : fmt(e.getDuration());
                    out.add(new Object[]{label, time, 0xFF000000 | e.getEffectType().value().getColor(), e.getDuration()});
                }
            }
            if (out.isEmpty() && preview) {
                out.add(new Object[]{"Speed II", "1:30", 0xFF7CAFC6, 1800});
                out.add(new Object[]{"Strength", "0:45", 0xFF932423, 900});
            }
            return out;
        }

        private static String fmt(int ticks) {
            int s = Math.max(0, ticks / 20);
            return String.format(Locale.ROOT, "%d:%02d", s / 60, s % 60);
        }

        @Override
        int[] size(MinecraftClient c, boolean preview) {
            List<Object[]> rows = rows(c, preview);
            if (rows.isEmpty()) {
                return null;
            }
            int w = 0;
            for (Object[] r : rows) {
                w = Math.max(w, c.textRenderer.getWidth((String) r[0]) + 12 + c.textRenderer.getWidth((String) r[1]));
            }
            int rowH = c.textRenderer.fontHeight + 6;
            return new int[]{w + 26, rows.size() * rowH + 6};
        }

        @Override
        void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview) {
            panel(g, c, id, x, y, w, h, 4);
            int rowH = c.textRenderer.fontHeight + 6;
            int ty = y + 6;
            for (Object[] r : rows(c, preview)) {
                int col = (Integer) r[2];
                NewUi.rrect(g, x + 7, ty + 1, 5, c.textRenderer.fontHeight - 1, 2, col, col);
                g.drawText(c.textRenderer, (String) r[0], x + 17, ty, 0xFFFFFFFF, false);
                int ticks = (Integer) r[3];
                String time = (String) r[1];
                g.drawText(c.textRenderer, time, x + w - 7 - c.textRenderer.getWidth(time), ty,
                        ticks <= 200 && !"**".equals(time) ? 0xFFE73C40 : DIM, false);
                ty += rowH;
            }
        }
    }

    // ------------------------------------------------------------------ Keystrokes

    static final class Keystrokes extends NewHud.El {
        private static final int K = 22;
        private static final int GAP = 2;
        private static final int SPACE_H = 14;
        /** W, A, S, D, LMB, RMB, Space - eased 0..1 "pressed" amount, so keys fade instead of snapping. */
        private final float[] press = new float[7];

        Keystrokes(float defX, float defY) {
            super(KEYSTROKES, defX, defY);
        }

        @Override
        int[] size(MinecraftClient c, boolean preview) {
            return new int[]{K * 3 + GAP * 2, K * 3 + GAP * 3 + SPACE_H};
        }

        private boolean down(MinecraftClient c, int i) {
            if (c.currentScreen != null) {
                return false;
            }
            switch (i) {
                case 0: return c.options.forwardKey.isPressed();
                case 1: return c.options.leftKey.isPressed();
                case 2: return c.options.backKey.isPressed();
                case 3: return c.options.rightKey.isPressed();
                case 4: return c.options.attackKey.isPressed();
                case 5: return c.options.useKey.isPressed();
                default: return c.options.jumpKey.isPressed();
            }
        }

        private void key(DrawContext g, MinecraftClient c, int i, int x, int y, int w, int h, String label) {
            boolean bg = NewModSettings.optBool(c, id, "bg");
            boolean border = NewModSettings.optBool(c, id, "border");
            float t = press[i];
            int r = 4;
            if (bg && border) {
                NewUi.frame(g, x, y, w, h, r, LINE, BG_T, BG_B);
            } else if (bg) {
                NewUi.rrect(g, x, y, w, h, r, BG_T, BG_B);
            } else if (border) {
                g.fill(x, y, x + w, y + 1, LINE);
                g.fill(x, y + h - 1, x + w, y + h, LINE);
                g.fill(x, y + 1, x + 1, y + h - 1, LINE);
                g.fill(x + w - 1, y + 1, x + w, y + h - 1, LINE);
            }
            if (t > 0.01f) {
                int a = Math.round(0xE0 * t);
                NewUi.rrect(g, x, y, w, h, r, (a << 24) | 0xF3F4F7, (a << 24) | 0xC9CCD3);
            }
            int textCol = NewUi.lerp(0xFFFFFFFF, 0xFF101216, t);
            if (label == null) {
                int lw = w / 3;
                g.fill(x + (w - lw) / 2, y + h / 2, x + (w + lw) / 2, y + h / 2 + 1, textCol);
            } else {
                g.drawText(c.textRenderer, label, x + (w - c.textRenderer.getWidth(label)) / 2,
                        y + (h - c.textRenderer.fontHeight) / 2 + 1, textCol, false);
            }
        }

        @Override
        void draw(DrawContext g, MinecraftClient c, int x, int y, int w, int h, boolean preview) {
            float dt = frameDt();
            float ease = NewPerf.animationsOff() ? 1f : Math.min(1f, dt * 20f);
            for (int i = 0; i < press.length; i++) {
                press[i] += ((down(c, i) ? 1f : 0f) - press[i]) * ease;
            }
            int step = K + GAP;
            key(g, c, 0, x + step, y, K, K, "W");
            key(g, c, 1, x, y + step, K, K, "A");
            key(g, c, 2, x + step, y + step, K, K, "S");
            key(g, c, 3, x + step * 2, y + step, K, K, "D");
            int mw = (w - GAP) / 2;
            key(g, c, 4, x, y + step * 2, mw, K, "LMB");
            key(g, c, 5, x + w - mw, y + step * 2, mw, K, "RMB");
            key(g, c, 6, x, y + step * 3, w, SPACE_H, null);
        }
    }
}
