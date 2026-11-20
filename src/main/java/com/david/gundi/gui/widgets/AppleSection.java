package com.david.gundi.gui.widgets;

import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.pressable.WTriangle;

import java.util.Locale;

/**
 * Collapsible group of settings, iOS Settings style:
 *   GENERAL                                   v      <- small caps heading, click anywhere on it to fold
 *   ( rounded card with the settings rows inside )
 * The card fades and folds with the section; the heading highlights on hover.
 */
public class AppleSection extends WSection implements AppleWidget {
    private AppleSectionHeader sectionHeader;

    public AppleSection(String title, boolean expanded, WWidget headerWidget) {
        super(title, expanded, headerWidget);
    }

    @Override
    protected WHeader createHeader() {
        sectionHeader = new AppleSectionHeader(title);
        return sectionHeader;
    }

    /** Content sits inside the card, with some room above and below. */
    @Override
    public <T extends WWidget> Cell<T> add(T widget) {
        return super.add(widget).padTop(theme.scale(4)).padBottom(theme.scale(6));
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (sectionHeader == null || animProgress <= 0.001) return;
        GundiTheme t = theme();
        double top = sectionHeader.y + sectionHeader.height + t.scale(2);
        double h = y + height - top;
        if (h <= 1) return;

        double r = t.radius();
        RoundRender.rect(renderer, x, top, width, h, r, AppleWidget.alpha(t.text.get(), 0.035 * animProgress));
        RoundRender.border(renderer, x, top, width, h, r, Math.max(1, t.scale(1)), AppleWidget.alpha(t.separator.get(), 0.8 * animProgress));
    }

    protected class AppleSectionHeader extends WHeader {
        private WTriangle triangle;
        private final Spring hover = new Spring(0);

        public AppleSectionHeader(String title) {
            super(title);
        }

        @Override
        public void init() {
            WLabel label = add(theme.label(title.toUpperCase(Locale.ROOT))).expandCellX().centerY().padLeft(theme.scale(4)).widget();
            label.color = theme.textSecondaryColor();
            if (headerWidget != null) add(headerWidget).centerY();

            triangle = new Chevron();
            triangle.theme = theme;
            triangle.action = this::onClick;
            add(triangle).centerY().padRight(theme.scale(2));
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = (GundiTheme) theme;
            triangle.rotation = (1 - animProgress) * -90;
            hover.update(mouseOver ? 1 : 0, delta, t.response(0.15));
            if (hover.value > 0.01) {
                RoundRender.rect(renderer, x, y, width, height, t.radius() * 0.6, AppleWidget.alpha(t.text.get(), 0.05 * hover.value));
            }
        }
    }

    protected static class Chevron extends WTriangle implements AppleWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            renderer.rotatedQuad(x, y, width, height, rotation, GuiRenderer.TRIANGLE,
                mouseOver ? theme().text.get() : theme().textSecondary.get());
        }
    }
}
