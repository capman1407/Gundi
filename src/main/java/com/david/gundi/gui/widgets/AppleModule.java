package com.david.gundi.gui.widgets;

import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import com.david.gundi.gui.ThemeStyles.ClickEffect;
import com.david.gundi.gui.ThemeStyles.HoverStyle;
import com.david.gundi.gui.ThemeStyles.ModuleStyle;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.render.color.Color;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT;
import static org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_RIGHT;

/**
 * One module in the ClickGUI list. The theme decides how it looks:
 * - active-module: Pill, Bar (accent stripe), Gradient, Outline, Glow or just colored Text
 * - hover-effect: Fill, Bar, Underline, Glow or Shift (name slides right)
 * - click-effect: Shrink, Ripple (a wave from where you clicked) or Flash
 * Left click toggles, right click opens its settings (same as Meteor).
 */
public class AppleModule extends WPressable implements AppleWidget {
    private final Module module;
    private final String title;
    private double titleWidth;

    private final Spring hover = new Spring(0);
    private final Spring active;
    private final Spring press = new Spring(0);

    // Click effect state
    private boolean wasPressed;
    private double clickX, clickY;
    private long clickNanos;

    public AppleModule(Module module, String title) {
        this.module = module;
        this.title = title;
        this.tooltip = module.description;
        this.active = new Spring(module.isActive() ? 1 : 0);
    }

    @Override
    public double pad() {
        return theme.scale(5);
    }

    @Override
    protected void onCalculateSize() {
        double pad = pad();
        if (titleWidth == 0) titleWidth = theme.textWidth(title);
        width = pad + titleWidth + pad;
        height = pad + theme.textHeight() + pad;
    }

    @Override
    protected void onPressed(int button) {
        if (button == GLFW_MOUSE_BUTTON_LEFT) module.toggle();
        else if (button == GLFW_MOUSE_BUTTON_RIGHT) mc.setScreen(theme.moduleScreen(module));
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        GundiTheme t = theme();
        double pad = pad();

        t.spring(hover, mouseOver ? 1 : 0, delta, 0.18);
        t.spring(active, module.isActive() ? 1 : 0, delta, 0.3);
        t.spring(press, pressed ? 1 : 0, delta, 0.1);

        if (pressed && !wasPressed) {
            clickX = mouseX;
            clickY = mouseY;
            clickNanos = System.nanoTime();
        }
        wasPressed = pressed;

        double h = clamp01(hover.value), a = Math.max(0, active.value); // active may overshoot a bit when bouncy
        double a1 = clamp01(a);

        // Pill area: a little inset from the window edges, shrinks while pressed with the Shrink click effect.
        double inset = t.scale(2);
        ClickEffect click = t.clickEffect.get();
        double shrink = click == ClickEffect.Shrink ? 1 - 0.04 * press.value : 1;
        double pw = (width - inset * 2) * shrink, ph = (height - inset) * shrink;
        double px = x + (width - pw) / 2, py = y + (height - ph) / 2;
        double radius = t.radius() * 0.75;
        Color accent = t.accent.get(), accent2 = t.accent2.get();

        // Hover (drawn under the active look)
        HoverStyle hs = t.hoverStyle.get();
        if (h > 0.01) {
            switch (hs) {
                case Fill, Shift -> RoundRender.rect(renderer, px, py, pw, ph, radius, AppleWidget.alpha(t.text.get(), 0.07 * h));
                case Glow -> {
                    RoundRender.glow(renderer, px, py, pw, ph, radius, t.scale(6), AppleWidget.alpha(accent, 0.45 * h));
                    RoundRender.rect(renderer, px, py, pw, ph, radius, AppleWidget.alpha(accent, 0.12 * h));
                }
                case Bar -> {
                    double bh = ph * h;
                    renderer.quad(px, py + (ph - bh) / 2, Math.max(1, t.scale(2)), bh, AppleWidget.alpha(accent, 0.8));
                    RoundRender.rect(renderer, px, py, pw, ph, radius, AppleWidget.alpha(accent, 0.06 * h));
                }
                case Underline -> {} // drawn under the text below
            }
        }

        // Active look
        if (a1 > 0.01) {
            switch (t.moduleStyle.get()) {
                case Pill -> RoundRender.rect(renderer, px, py, pw, ph, radius, AppleWidget.alpha(accent, a1));
                case Gradient -> RoundRender.hGradientRect(renderer, px, py, pw, ph, radius, radius, radius, radius,
                    AppleWidget.alpha(accent, a1), AppleWidget.alpha(accent2, a1));
                case Bar -> {
                    RoundRender.rect(renderer, px, py, pw, ph, radius, AppleWidget.alpha(accent, 0.16 * a1));
                    double bh = ph * Math.min(a, 1.15); // grows from the middle (and overshoots with bounce)
                    double bw = Math.max(2, t.scale(3));
                    renderer.quad(px, py + (ph - bh) / 2, bw, bh, accent);
                }
                case Outline -> {
                    RoundRender.rect(renderer, px, py, pw, ph, radius, AppleWidget.alpha(accent, 0.08 * a1));
                    RoundRender.border(renderer, px, py, pw, ph, radius, Math.max(1, t.scale(1)), AppleWidget.alpha(accent, a1));
                }
                case Glow -> {
                    RoundRender.glow(renderer, px, py, pw, ph, radius, t.scale(7), AppleWidget.alpha(accent, 0.7 * a1 * t.glowAlpha()));
                    RoundRender.rect(renderer, px, py, pw, ph, radius, AppleWidget.alpha(accent, 0.35 * a1));
                }
                case Text -> {}
            }
        }

        // Click effects
        double since = (System.nanoTime() - clickNanos) / 1e9 * t.animationSpeed.get();
        if (clickNanos != 0 && since < 0.5) {
            double k = since / 0.5;
            if (click == ClickEffect.Ripple) {
                // A soft circle growing from the click point, clipped to the row
                double maxR = Math.hypot(Math.max(clickX - px, px + pw - clickX), ph);
                double rr = maxR * (0.15 + 0.85 * easeOut(k));
                renderer.scissorStart(px, py, pw, ph);
                renderer.quad(clickX - rr, clickY - rr, rr * 2, rr * 2, GuiRenderer.CIRCLE, AppleWidget.alpha(t.text.get(), 0.22 * (1 - k)));
                renderer.scissorEnd();
            } else if (click == ClickEffect.Flash) {
                RoundRender.rect(renderer, px, py, pw, ph, radius, AppleWidget.alpha(accent2, 0.55 * (1 - k) * (1 - k)));
            }
        }

        // Text
        double shift = hs == HoverStyle.Shift ? t.scale(4) * hover.value : 0;
        double textX = this.x + pad + shift;
        double w = width - pad * 2;
        if (t.moduleAlignment.get() == AlignmentX.Center) textX += w / 2 - titleWidth / 2;
        else if (t.moduleAlignment.get() == AlignmentX.Right) textX += w - titleWidth - shift * 2;

        Color textColor = AppleWidget.mix(t.textSecondary.get(), t.text.get(), Math.max(h, a1));
        ModuleStyle ms = t.moduleStyle.get();
        if (ms == ModuleStyle.Text || ms == ModuleStyle.Bar || ms == ModuleStyle.Outline) {
            textColor = AppleWidget.mix(textColor, accent, a1);
        }
        renderer.text(title, textX, y + pad, textColor, false);

        if (hs == HoverStyle.Underline && h > 0.01) {
            double uw = titleWidth * easeOut(h);
            renderer.quad(textX, y + pad + theme.textHeight() + t.scale(1), uw, Math.max(1, t.scale(1)), AppleWidget.alpha(accent, h));
        }
    }

    private static double easeOut(double k) {
        k = clamp01(k);
        return 1 - (1 - k) * (1 - k) * (1 - k);
    }

    private static double clamp01(double v) {
        return Math.max(0, Math.min(1, v));
    }
}
