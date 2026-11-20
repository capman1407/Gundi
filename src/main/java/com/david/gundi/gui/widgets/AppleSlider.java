package com.david.gundi.gui.widgets;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.utils.render.color.Color;

/** macOS-style slider: thin rounded track filled with the accent color, white round knob with a soft shadow. */
public class AppleSlider extends WSlider implements AppleWidget {
    private final Spring hover = new Spring(0);

    public AppleSlider(double value, double min, double max) {
        super(value, min, max);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        GundiTheme t = theme();
        hover.update(handleMouseOver || dragging ? 1 : 0, delta, t.response(0.2));

        double valueWidth = valueWidth();
        double s = handleSize();

        // Track
        double th = t.scale(4);
        double tx = x + s / 2, ty = y + height / 2 - th / 2, tw = width - s;
        RoundRender.rect(renderer, tx, ty, tw, th, th / 2, AppleWidget.alpha(t.text.get(), 0.18)); // shows on dark and light
        if (valueWidth > 0) RoundRender.rect(renderer, tx, ty, valueWidth, th, th / 2, t.accent.get());

        // Knob: grows a little when hovered or dragged
        double ks = s * (0.8 + 0.15 * hover.value);
        double kx = x + valueWidth + (s - ks) / 2, ky = y + (height - ks) / 2;
        renderer.quad(kx, ky + t.scale(1), ks, ks, GuiRenderer.CIRCLE, new Color(0, 0, 0, 80));
        renderer.quad(kx, ky, ks, ks, GuiRenderer.CIRCLE, new Color(255, 255, 255));
    }
}
