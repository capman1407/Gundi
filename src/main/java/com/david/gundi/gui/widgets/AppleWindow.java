package com.david.gundi.gui.widgets;

import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import com.david.gundi.gui.ThemeStyles.BorderStyle;
import com.david.gundi.gui.ThemeStyles.CloseAnim;
import com.david.gundi.gui.ThemeStyles.OpenAnim;
import com.david.gundi.mixin.WidgetScreenAccessor;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.screen.Screen;

import java.lang.ref.WeakReference;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * A window (e.g. a module category). How it looks and moves comes from the theme:
 * - shadow, glow, body surface (frosted / solid / tinted / scanlines), border style and title bar style
 * - open animation (rise, cascade, drop, slide, unfold, iris, glitch) and close animation (fade, sink, lift,
 *   collapse, scatter, glitch)
 *
 * Animations only move the window while drawing it: clicks always use its real position.
 */
public class AppleWindow extends WWindow implements AppleWidget {
    // When the current GUI screen was opened. Shared by all windows so they animate together.
    // Weak reference: we don't want to keep a closed screen in memory.
    private static WeakReference<Screen> animScreen = new WeakReference<>(null);
    private static long openNanos;

    private final int seed; // per-window randomness for the glitch animation

    public AppleWindow(WWidget icon, String title) {
        super(icon, title);
        seed = title == null ? 0 : title.hashCode();
    }

    @Override
    protected WHeader header(WWidget icon) {
        return new AppleHeader(icon);
    }

    @Override
    public boolean render(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        GundiTheme t = theme();
        OpenAnim oa = t.openAnim.get();
        CloseAnim ca = t.closeAnim.get();

        WidgetScreen screen = mc.currentScreen instanceof WidgetScreen ws ? ws : null;
        double screenAlpha = screen != null ? screen.animProgress : 1;
        boolean closing = ca != CloseAnim.None && screen != null && ((WidgetScreenAccessor) screen).addon$isClosing();

        double W = Utils.getWindowWidth(), H = Utils.getWindowHeight();
        double response = t.response(baseResponse(oa));
        double damping = oa == OpenAnim.Drop ? Math.min(t.damping(), 0.6) : t.damping(); // a drop always lands with a bounce
        double time = openTime(t, oa, W, H);
        boolean opening = oa != OpenAnim.None && time < response * 3 / damping;

        if (!opening && !closing) return super.render(renderer, mouseX, mouseY, delta);

        double p = opening ? Spring.step(time, response, damping) : 1; // may overshoot 1 when bouncy
        double a = clamp01(p);
        double c = closing ? clamp01(1 - screenAlpha) : 0;

        double dx = 0, dy = 0, alpha = 1;
        double clipX = 0, clipY = 0, clipW = W, clipH = H;
        double m = t.scale(40); // room around the window for its shadow and glow when clipping
        boolean ghost = false;

        if (opening) {
            switch (oa) {
                case Rise -> { dy += (1 - p) * t.scale(28); alpha *= a; }
                case Cascade -> { dy += (1 - p) * t.scale(50); alpha *= a; }
                case Drop -> { dy -= (1 - p) * (y + height + t.scale(30)); alpha *= Math.min(1, a * 4); }
                case Slide -> {
                    boolean left = x + width / 2 < W / 2;
                    double dist = left ? x + width + t.scale(30) : W - x + t.scale(30);
                    dx += (left ? -1 : 1) * (1 - p) * dist;
                    alpha *= Math.min(1, a * 2);
                }
                case Unfold -> {
                    // Title bar first, then the body rolls down
                    alpha *= Math.min(1, a * 3);
                    clipX = x - m;
                    clipY = y - m;
                    clipW = width + m * 2;
                    clipH = m + header.height + (height - header.height + m) * a;
                }
                case Iris -> {
                    alpha *= a;
                    clipW = (width + m * 2) * a;
                    clipH = (height + m * 2) * a;
                    clipX = x + width / 2 - clipW / 2;
                    clipY = y + height / 2 - clipH / 2;
                }
                case Glitch -> {
                    double g = 1 - a;
                    long bucket = (long) (time * 30);
                    dx += (noise(seed, bucket) - 0.5) * 2 * t.scale(16) * g;
                    if (g > 0.2) alpha *= noise(seed + 7, bucket) > 0.35 ? 1 : 0.2;
                    ghost = g > 0.05;
                }
                default -> {}
            }
        }

        if (closing && c > 0) {
            switch (ca) {
                case Sink -> dy += c * t.scale(18);
                case Lift -> dy -= c * t.scale(26);
                case Collapse -> {
                    // Squeezes into a line through the window's middle
                    double ch = (height + m * 2) * (1 - c);
                    double cy = y + height / 2 - ch / 2;
                    double ny = Math.max(clipY, cy), nb = Math.min(clipY + clipH, cy + ch);
                    clipY = ny;
                    clipH = Math.max(0, nb - ny);
                }
                case Scatter -> {
                    boolean left = x + width / 2 < W / 2;
                    dx += (left ? -1 : 1) * c * c * W * 0.5;
                    dy += c * t.scale(10);
                }
                case Glitch -> {
                    long bucket = System.nanoTime() / 33_000_000L;
                    dx += (noise(seed, bucket) - 0.5) * 2 * t.scale(18) * c;
                    alpha *= noise(seed + 3, bucket) > 0.4 ? 1 : 0.3;
                    ghost = true;
                }
                default -> {}
            }
        }

        // Meteor draws all text in one go at the end of a "scissor" section, using the alpha set at that moment.
        // Giving this window its own section means its text fades (and gets clipped) together with its boxes.
        renderer.scissorStart(clipX, clipY, Math.max(0, clipW), Math.max(0, clipH));
        renderer.setAlpha(screenAlpha * alpha);

        // Whole pixels, so moving away and back always lands exactly where it started.
        int ix = (int) Math.round(dx), iy = (int) Math.round(dy);
        move(ix, iy);
        boolean result;
        try {
            result = super.render(renderer, mouseX, mouseY, delta);

            // Glitch: two colored "split" copies of the edge, like a broken signal
            if (ghost) {
                double off = t.scale(3) * (noise(seed + 11, System.nanoTime() / 40_000_000L) + 0.5);
                double line = Math.max(1, t.scale(1.5));
                RoundRender.border(renderer, x - off, y, width, height, t.radius(), line, AppleWidget.alpha(t.accent.get(), 0.8));
                RoundRender.border(renderer, x + off, y, width, height, t.radius(), line, AppleWidget.alpha(t.accent2.get(), 0.8));
            }
        } finally {
            move(-ix, -iy);
        }

        renderer.scissorEnd();
        renderer.setAlpha(screenAlpha);
        return result;
    }

    /** Seconds since this window's animation started (each window starts a little later, depending on the style). */
    private double openTime(GundiTheme t, OpenAnim oa, double W, double H) {
        Screen screen = mc.currentScreen;
        if (screen != animScreen.get()) {
            animScreen = new WeakReference<>(screen);
            openNanos = System.nanoTime();
        }

        double fx = clamp01(x / Math.max(1, W)), fy = clamp01(y / Math.max(1, H));
        double delay = switch (oa) {
            case Rise -> fx * 0.15;
            case Cascade -> fx * 0.3 + fy * 0.2;
            case Drop -> fx * 0.25;
            case Slide -> Math.abs(fx - 0.5) * 0.2;
            case Unfold -> fx * 0.2;
            case Iris -> Math.abs(fx - 0.5) * 0.12;
            case Glitch -> noise(seed, 1) * 0.25;
            default -> 0;
        };

        return (System.nanoTime() - openNanos) / 1e9 - delay / t.animationSpeed.get();
    }

    private static double baseResponse(OpenAnim oa) {
        return switch (oa) {
            case Cascade -> 0.6;
            case Drop -> 0.7;
            case Slide -> 0.55;
            case Unfold, Iris -> 0.45;
            case Glitch -> 0.35;
            default -> 0.5;
        };
    }

    /** Repeatable pseudo-random number 0..1 for a (seed, n) pair. */
    public static double noise(long seed, long n) {
        long h = seed * 0x9E3779B97F4A7C15L + n * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 33;
        h *= 0xFF51AFD7ED558CCDL;
        h ^= h >>> 33;
        return (h >>> 11) / (double) (1L << 53);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        GundiTheme t = theme();
        double r = t.radius();
        double line = t.line();

        // How tall the window currently looks (it shrinks into the title bar while collapsing).
        double vh = header.height + (height - header.height) * animProgress;

        if (t.shadows.get() && t.shadowStrength.get() > 0) {
            RoundRender.shadow(renderer, x, y, width, vh, r, t.scale(14), (int) (100 * t.shadowStrength.get()));
        }

        boolean glowing = t.glow.get() || t.border.get() == BorderStyle.Glow;
        if (glowing) {
            double size = t.scale(t.glow.get() ? t.glowSize.get() : 10);
            RoundRender.glow(renderer, x, y, width, vh, r, size, AppleWidget.alpha(t.glowColor.get(), t.glowAlpha()));
        }

        if (expanded || animProgress > 0) {
            // Body under the title bar, inset by the border width so the border stays visible.
            double bx = x + line, by = y + header.height, bw = width - line * 2, bh = height - header.height - line;
            double br = Math.max(r - line, 0);
            drawSurface(renderer, t, bx, by, bw, bh, br);
        }

        drawBorder(renderer, t, x, y, width, vh, r, line);
    }

    private static void drawSurface(GuiRenderer renderer, GundiTheme t, double bx, double by, double bw, double bh, double br) {
        Color body = t.window.get();

        switch (t.surface.get()) {
            case Frosted -> RoundRender.gradientRect(renderer, bx, by, bw, bh, 0, 0, br, br, lighten(body, 14, 0.75), lighten(body, 0, 0.95));
            case Solid -> RoundRender.rect(renderer, bx, by, bw, bh, 0, 0, br, br, body);
            case Tinted -> {
                Color a1 = t.accent.get(), a2 = t.accent2.get();
                Color top = RoundRender.mix(body, new Color(a1.r, a1.g, a1.b, body.a), 0.16);
                Color bottom = RoundRender.mix(body, new Color(a2.r, a2.g, a2.b, body.a), 0.10);
                RoundRender.gradientRect(renderer, bx, by, bw, bh, 0, 0, br, br, top, bottom);
            }
            case Scanlines -> {
                RoundRender.rect(renderer, bx, by, bw, bh, 0, 0, br, br, body);

                // CRT lines: a faint line every 3 pixels (skipping the rounded bottom so nothing pokes out)
                double step = Math.max(2, t.scale(3));
                double lh = Math.max(1, step / 3);
                Color lineColor = AppleWidget.alpha(t.text.get(), 0.035);
                for (double yy = by; yy + lh < by + bh - br; yy += step) renderer.quad(bx, yy, bw, lh, lineColor);

                // A soft bright band slowly sweeping down, like an old monitor refreshing
                double band = t.scale(50);
                double period = 4 / t.animationSpeed.get();
                double pos = ((GundiTheme.time() % period) / period) * (bh + band) - band;
                double top = Math.max(by, by + pos), bottom = Math.min(by + bh - br, by + pos + band);
                if (bottom > top) {
                    Color clear = AppleWidget.alpha(t.accent.get(), 0), glowC = AppleWidget.alpha(t.accent.get(), 0.07);
                    renderer.quad(bx, top, bw, bottom - top, clear, clear, glowC, glowC);
                }
            }
        }
    }

    private static void drawBorder(GuiRenderer renderer, GundiTheme t, double x, double y, double w, double h, double r, double line) {
        double hair = Math.max(1, t.scale(1));

        switch (t.border.get()) {
            case Hairline -> RoundRender.border(renderer, x, y, w, h, r, line, t.outline.get());
            case Double -> {
                double d = t.scale(3);
                RoundRender.border(renderer, x, y, w, h, r, line, t.outline.get());
                RoundRender.border(renderer, x + d, y + d, w - d * 2, h - d * 2, Math.max(r - d, 0), hair, AppleWidget.alpha(t.outline.get(), 0.5));
            }
            case Brackets -> {
                RoundRender.border(renderer, x, y, w, h, r, hair, AppleWidget.alpha(t.outline.get(), 0.6));
                RoundRender.brackets(renderer, x, y, w, h, t.scale(12), Math.max(line, t.scale(2)), t.accent.get());
            }
            case Glow -> RoundRender.border(renderer, x, y, w, h, r, line, AppleWidget.alpha(t.accent.get(), 0.7));
            case Gradient -> RoundRender.gradientBorder(renderer, x, y, w, h, r, line, t.accent.get(), t.accent2.get(),
                GundiTheme.time() * 1.6 * t.animationSpeed.get());
            default -> {}
        }
    }

    /** Lighter (adds `amount` to r/g/b) and/or more see-through (alpha times `alphaFactor`) copy of a color. */
    private static Color lighten(Color c, int amount, double alphaFactor) {
        return new Color(
            Math.min(255, c.r + amount),
            Math.min(255, c.g + amount),
            Math.min(255, c.b + amount),
            (int) Math.max(0, Math.min(255, c.a * alphaFactor))
        );
    }

    /** Public so labels can tell they're a window title (and use the title-text color). */
    public class AppleHeader extends WHeader {
        public AppleHeader(WWidget icon) {
            super(icon);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            double line = t.border.get() == BorderStyle.None ? 0 : t.line();
            double r = Math.max(t.radius() - line, 0);

            // Bottom corners are square while the window is open (the body continues below),
            // and round off as it collapses into just the title bar.
            double bottom = r * (1 - animProgress);
            double hx = x + line, hy = y + line, hw = width - line * 2, hh = height - line;
            Color c = t.headerColor.get();
            double hair = Math.max(1, t.scale(1));

            switch (t.header.get()) {
                case Glass -> {
                    if (t.frosted()) {
                        RoundRender.gradientRect(renderer, hx, hy, hw, hh, r, r, bottom, bottom, lighten(c, 18, 0.85), lighten(c, 4, 0.92));
                        // Rim light: light catching the top edge of the glass.
                        RoundRender.rimLight(renderer, hx + r, hy, hw - r * 2, hair, new Color(255, 255, 255, 70));
                    } else {
                        RoundRender.rect(renderer, hx, hy, hw, hh, r, r, bottom, bottom, c);
                    }
                    separator(renderer, t, hx, hw, hair);
                }
                case Accent -> {
                    RoundRender.hGradientRect(renderer, hx, hy, hw, hh, r, r, bottom, bottom, t.accent.get(), t.accent2.get());
                    RoundRender.rimLight(renderer, hx + r, hy, hw - r * 2, hair, new Color(255, 255, 255, 90));
                }
                case Underline -> {
                    RoundRender.rect(renderer, hx, hy, hw, hh, r, r, bottom, bottom, AppleWidget.alpha(c, 0.45));
                    double lw = Math.max(1, t.scale(1.5));
                    renderer.quad(hx, y + height - lw, hw, lw, t.accent.get(), t.accent2.get());
                }
                case Minimal -> separator(renderer, t, hx, hw, hair);
            }
        }

        /** Hairline between title bar and body, fades out when collapsed. */
        private void separator(GuiRenderer renderer, GundiTheme t, double hx, double hw, double hair) {
            if (animProgress > 0) renderer.quad(hx, y + height - hair, hw, hair, AppleWidget.alpha(t.separator.get(), animProgress));
        }
    }
}
