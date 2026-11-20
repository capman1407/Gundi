package com.david.gundi.gui.widgets;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.ControlAnim;
import com.david.gundi.gui.RoundRender;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.utils.render.color.Color;

/** Dropdown (e.g. for mode settings): rounded button with a chevron, and a rounded list that opens below it. */
public class AppleDropdown<T> extends WDropdown<T> implements AppleWidget {
    private final ControlAnim anim = new ControlAnim();

    public AppleDropdown(T[] values, T value) {
        super(values, value);
    }

    @Override
    protected WDropdownRoot createRootWidget() {
        return new Root();
    }

    @Override
    protected WDropdownValue createValueWidget() {
        return new Value();
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        GundiTheme t = theme();
        double pad = pad();
        double s = theme.textHeight();

        anim.update(t, pressed, mouseOver, delta);
        anim.drawBackground(renderer, t, this, anim.background(t));

        String text = get().toString();
        double w = theme.textWidth(text);
        renderer.text(text, x + pad + maxValueWidth / 2 - w / 2, y + pad, t.text.get(), false);
        renderer.rotatedQuad(x + pad + maxValueWidth + pad, y + pad, s, s, 0, GuiRenderer.TRIANGLE, t.textSecondary.get());
    }

    /** The open list's background. */
    private static class Root extends WDropdownRoot implements AppleWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            double r = t.radius() * 0.75;
            RoundRender.glassSurface(renderer, t, x, y, width, height, 0, 0, r, r, AppleWidget.alpha(t.headerColor.get(), 1.1));
        }
    }

    /** One option in the open list. Hover = accent highlight. */
    private class Value extends WDropdownValue implements AppleWidget {
        @Override
        protected void onCalculateSize() {
            double pad = pad();
            width = pad + theme.textWidth(value.toString()) + pad;
            height = pad + theme.textHeight() + pad;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();

            if (mouseOver || pressed) {
                double inset = t.scale(2);
                Color c = AppleWidget.alpha(t.accent.get(), pressed ? 1 : 0.85);
                RoundRender.rect(renderer, x + inset, y + inset / 2, width - inset * 2, height - inset, t.radius() * 0.5, c);
            }

            String text = value.toString();
            renderer.text(text, x + width / 2 - theme.textWidth(text) / 2, y + pad(), t.text.get(), false);
        }
    }
}
