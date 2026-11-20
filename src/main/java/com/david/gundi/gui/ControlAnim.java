package com.david.gundi.gui;

import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;

/**
 * Hover + press animation shared by buttons and other "controls".
 * - Hover fades the background lighter.
 * - Press reacts the moment you click (not on release) and shrinks the button to 97%, like iOS/macOS.
 */
public class ControlAnim {
    public final Spring hover = new Spring(0);
    public final Spring press = new Spring(0);

    public void update(GundiTheme theme, boolean pressed, boolean mouseOver, double delta) {
        hover.update(mouseOver ? 1 : 0, delta, theme.response(0.2));
        theme.spring(press, pressed ? 1 : 0, delta, 0.1);
    }

    /** Background color for the current hover/press state. */
    public Color background(GundiTheme theme) {
        Color c = AppleWidget.mix(theme.control.get(), theme.controlHover.get(), hover.value);
        return AppleWidget.mix(c, theme.controlPressed.get(), press.value);
    }

    /** Draws the rounded background, shrunk slightly while pressed. */
    public void drawBackground(GuiRenderer renderer, GundiTheme theme, WWidget w, Color color) {
        double shrink = 1 - 0.03 * press.value;
        double bw = w.width * shrink, bh = w.height * shrink;
        double bx = w.x + (w.width - bw) / 2, by = w.y + (w.height - bh) / 2;
        RoundRender.rect(renderer, bx, by, bw, bh, theme.radius() * 0.75, color);
    }
}
