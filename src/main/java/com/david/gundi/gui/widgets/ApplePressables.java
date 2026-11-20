package com.david.gundi.gui.widgets;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.ControlAnim;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import com.david.gundi.gui.ThemeStyles;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.widgets.pressable.*;
import meteordevelopment.meteorclient.utils.render.color.Color;

/** Everything you click: buttons, plus/minus, the iOS-style switch, favorite star and chevrons. */
public final class ApplePressables {
    private ApplePressables() {}

    public static class Button extends WButton implements AppleWidget {
        private final ControlAnim anim = new ControlAnim();

        public Button(String text, GuiTexture texture) {
            super(text, texture);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            double pad = pad();

            anim.update(t, pressed, mouseOver, delta);

            if (text != null) {
                anim.drawBackground(renderer, t, this, anim.background(t));
                renderer.text(text, x + width / 2 - textWidth / 2, y + pad, t.text.get(), false);
            } else {
                // Icon buttons (like the reset arrow next to every setting) stay quiet until you hover them,
                // so long settings lists aren't a column of boxes.
                double h = Math.max(0, Math.min(1, anim.hover.value));
                anim.drawBackground(renderer, t, this, AppleWidget.alpha(anim.background(t), 0.15 + 0.85 * h));
                double ts = theme.textHeight();
                renderer.quad(x + width / 2 - ts / 2, y + pad, ts, ts, texture, AppleWidget.mix(t.textSecondary.get(), t.text.get(), h));
            }
        }
    }

    /** Button that needs two clicks (e.g. delete). After the first click it turns red to ask "are you sure?". */
    public static class ConfirmedButton extends WConfirmedButton implements AppleWidget {
        private final ControlAnim anim = new ControlAnim();
        private final Spring confirm = new Spring(0);

        public ConfirmedButton(String text, String confirmText, GuiTexture texture) {
            super(text, confirmText, texture);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            double pad = pad();

            anim.update(t, pressed, mouseOver, delta);
            confirm.update(pressedOnce ? 1 : 0, delta, t.response(0.25));
            anim.drawBackground(renderer, t, this, AppleWidget.mix(anim.background(t), t.minus.get(), confirm.value));

            String text = getText();
            if (text != null) {
                renderer.text(text, x + width / 2 - textWidth / 2, y + pad, t.text.get(), false);
            } else {
                double ts = theme.textHeight();
                renderer.quad(x + width / 2 - ts / 2, y + pad, ts, ts, texture, t.text.get());
            }
        }
    }

    public static class Minus extends WMinus implements AppleWidget {
        private final ControlAnim anim = new ControlAnim();

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            anim.update(t, pressed, mouseOver, delta);
            anim.drawBackground(renderer, t, this, anim.background(t));
            bar(renderer, t, this.x, this.y, width, height, pad(), t.minus.get(), false);
        }
    }

    public static class ConfirmedMinus extends WConfirmedMinus implements AppleWidget {
        private final ControlAnim anim = new ControlAnim();
        private final Spring confirm = new Spring(0);

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            anim.update(t, pressed, mouseOver, delta);
            confirm.update(pressedOnce ? 1 : 0, delta, t.response(0.25));

            // Turns red with a white minus when it's waiting for the second click.
            anim.drawBackground(renderer, t, this, AppleWidget.mix(anim.background(t), t.minus.get(), confirm.value));
            bar(renderer, t, this.x, this.y, width, height, pad(), AppleWidget.mix(t.minus.get(), t.text.get(), confirm.value), false);
        }
    }

    public static class Plus extends WPlus implements AppleWidget {
        private final ControlAnim anim = new ControlAnim();

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            anim.update(t, pressed, mouseOver, delta);
            anim.drawBackground(renderer, t, this, anim.background(t));
            bar(renderer, t, this.x, this.y, width, height, pad(), t.plus.get(), true);
        }
    }

    /** Rounded minus (or plus when vertical = true) symbol in the middle of a button. */
    private static void bar(GuiRenderer renderer, GundiTheme t, double x, double y, double width, double height,
                            double pad, Color color, boolean vertical) {
        double s = t.scale(2.5);
        double len = Math.min(width, height) - pad * 2;
        double cx = x + width / 2, cy = y + height / 2;
        RoundRender.rect(renderer, cx - len / 2, cy - s / 2, len, s, s / 2, color);
        if (vertical) RoundRender.rect(renderer, cx - s / 2, cy - len / 2, s, len, s / 2, color);
    }

    /**
     * iOS-style switch used for every on/off setting (replaces Meteor's checkbox).
     * The knob slides with a spring, and stretches a little while you hold it, just like on an iPhone.
     */
    public static class Switch extends WCheckbox implements AppleWidget {
        private final Spring on;
        private final Spring hover = new Spring(0);
        private final Spring press = new Spring(0);

        public Switch(boolean checked) {
            super(checked);
            on = new Spring(checked ? 1 : 0);
        }

        @Override
        protected void onCalculateSize() {
            double pad = pad();
            double s = theme.textHeight();
            height = pad + s + pad;
            width = theme().switchStyle.get() == ThemeStyles.SwitchStyle.Checkbox ? height : height * 1.7;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            t.spring(on, checked ? 1 : 0, delta, 0.3);
            hover.update(mouseOver ? 1 : 0, delta, t.response(0.2));
            press.update(pressed ? 1 : 0, delta, t.response(0.12));

            switch (t.switchStyle.get()) {
                case Checkbox -> renderCheckbox(renderer, t);
                case LED -> renderLed(renderer, t);
                default -> renderSwitch(renderer, t);
            }
        }

        /** iOS switch. With non-round corner styles the knob follows the corner style too. */
        private void renderSwitch(GuiRenderer renderer, GundiTheme t) {
            double v = Math.max(0, Math.min(1, on.value));
            double pos = Math.max(-0.1, Math.min(1.1, on.value)); // the knob may overshoot a little when bouncy

            // Track: fills with the on color. Slightly lighter on hover.
            double inset = t.scale(1);
            double tx = x + inset, ty = y + inset, tw = width - inset * 2, th = height - inset * 2;
            Color off = AppleWidget.mix(t.switchOff.get(), t.text.get(), hover.value * 0.1);
            double trackR = Math.min(th / 2, Math.max(t.radius(), t.scale(3)));
            boolean round = RoundRender.corners == ThemeStyles.CornerStyle.Round;
            if (round) trackR = th / 2;
            if (t.glow.get() && v > 0.01) RoundRender.glow(renderer, tx, ty, tw, th, trackR, t.scale(5), AppleWidget.alpha(t.switchOn.get(), 0.5 * v));
            RoundRender.rect(renderer, tx, ty, tw, th, trackR, AppleWidget.mix(off, t.switchOn.get(), v));

            // Knob: white with a soft shadow. Stretches by up to 25% while pressed and stays inside the track.
            double gap = t.scale(2);
            double ks = th - gap * 2;
            double kw = ks + ks * 0.25 * press.value;
            double kx = tx + gap + (tw - gap * 2 - kw) * Math.max(0, Math.min(1, pos)) + (pos - Math.max(0, Math.min(1, pos))) * ks;
            double ky = ty + gap;

            if (round) {
                RoundRender.pill(renderer, kx, ky + t.scale(1), kw, ks, new Color(0, 0, 0, 70));
                RoundRender.pill(renderer, kx, ky, kw, ks, new Color(255, 255, 255));
            } else {
                double kr = Math.max(0, trackR - gap);
                RoundRender.rect(renderer, kx, ky + t.scale(1), kw, ks, kr, new Color(0, 0, 0, 70));
                RoundRender.rect(renderer, kx, ky, kw, ks, kr, new Color(255, 255, 255));
            }
        }

        /** Square box that fills from the middle, with a check mark drawn in. */
        private void renderCheckbox(GuiRenderer renderer, GundiTheme t) {
            double v = Math.max(0, Math.min(1, on.value));
            double inset = t.scale(1);
            double s = height - inset * 2;
            double bx = x + inset, by = y + inset;
            double r = Math.min(s / 2, t.radius() * 0.6);
            double line = Math.max(1, t.scale(1.5));

            Color frame = AppleWidget.mix(AppleWidget.mix(t.switchOff.get(), t.text.get(), hover.value * 0.15), t.switchOn.get(), v);
            RoundRender.rect(renderer, bx, by, s, s, r, AppleWidget.alpha(t.field.get(), 0.9));
            if (t.glow.get() && v > 0.01) RoundRender.glow(renderer, bx, by, s, s, r, t.scale(5), AppleWidget.alpha(t.switchOn.get(), 0.5 * v));

            // Fill grows from the center (overshoots a bit when bouncy)
            double f = s * Math.max(0, Math.min(1.08, on.value)) * (1 - 0.1 * press.value);
            if (f > 0.5) RoundRender.rect(renderer, bx + (s - f) / 2, by + (s - f) / 2, f, f, r * f / s, t.switchOn.get());
            RoundRender.border(renderer, bx, by, s, s, r, line, frame);

            // Check mark: two strokes, the second one drawn in after the first
            if (v > 0.05) {
                double thick = Math.max(1.5, t.scale(2));
                double ax = bx + s * 0.24, ay = by + s * 0.52;
                double mx = bx + s * 0.42, my = by + s * 0.70;
                double ex = bx + s * 0.76, ey = by + s * 0.32;
                Color mark = new Color(255, 255, 255, (int) (255 * v));
                double k1 = Math.min(1, v * 2), k2 = Math.max(0, v * 2 - 1);
                RoundRender.line(renderer, ax, ay, ax + (mx - ax) * k1, ay + (my - ay) * k1, thick, mark);
                if (k2 > 0) RoundRender.line(renderer, mx, my, mx + (ex - mx) * k2, my + (ey - my) * k2, thick, mark);
            }
        }

        /** Dark capsule with a light that slides over and lights up (with a glow) when on. */
        private void renderLed(GuiRenderer renderer, GundiTheme t) {
            double v = Math.max(0, Math.min(1, on.value));
            double inset = t.scale(1);
            double tx = x + inset, ty = y + inset, tw = width - inset * 2, th = height - inset * 2;
            double r = Math.min(th / 2, Math.max(t.radius() * 0.6, t.scale(2)));
            double line = Math.max(1, t.scale(1));

            Color on = t.switchOn.get();
            RoundRender.rect(renderer, tx, ty, tw, th, r, AppleWidget.alpha(t.field.get(), 0.95));
            RoundRender.border(renderer, tx, ty, tw, th, r, line, AppleWidget.mix(AppleWidget.alpha(t.switchOff.get(), 1), on, v));

            double gap = t.scale(3);
            double lw = (tw - gap * 2) * 0.45, lh = th - gap * 2;
            double lx = tx + gap + (tw - gap * 2 - lw) * Math.max(-0.05, Math.min(1.05, this.on.value));
            double ly = ty + gap;
            double lr = Math.max(0, r - gap);
            Color light = AppleWidget.mix(t.switchOff.get(), on, v);
            if (v > 0.01) RoundRender.glow(renderer, lx, ly, lw, lh, lr, t.scale(6), AppleWidget.alpha(on, 0.8 * v));
            RoundRender.rect(renderer, lx, ly, lw, lh, lr, AppleWidget.mix(light, new Color(255, 255, 255), 0.15 * hover.value));
        }
    }

    public static class Favorite extends WFavorite implements AppleWidget {
        public Favorite(boolean checked) {
            super(checked);
        }

        @Override
        protected Color getColor() {
            return checked ? theme().favorite.get() : theme().textSecondary.get();
        }
    }

    /** Chevron used to collapse windows and sections. Dim, brightens on hover. */
    public static class Triangle extends WTriangle implements AppleWidget {
        private final Spring hover = new Spring(0);

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            hover.update(mouseOver ? 1 : 0, delta, t.response(0.2));
            Color c = AppleWidget.mix(t.textSecondary.get(), t.text.get(), hover.value);
            renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE, c);
        }
    }
}
