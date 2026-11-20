package com.david.gundi.gui.widgets;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.widgets.*;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.utils.render.color.Color;

/** The simple Apple widgets: text, separators, tooltip, scrollbar, top bar, account row, plain quad. */
public final class AppleBasics {
    private AppleBasics() {}

    public static class Label extends WLabel implements AppleWidget {
        public Label(String text, boolean title) {
            super(text, title);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (!text.isEmpty()) {
                GundiTheme t = theme();
                // title-text color is only for window title bars (it may be picked to sit on a colored bar)
                boolean windowTitle = title && parent instanceof AppleWindow.AppleHeader;
                renderer.text(text, x, y, color != null ? color : (windowTitle ? t.titleText.get() : t.text.get()), title);
            }
        }
    }

    public static class MultiLabel extends WMultiLabel implements AppleWidget {
        public MultiLabel(String text, boolean title, double maxWidth) {
            super(text, title, maxWidth);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            double h = theme.textHeight(title);
            Color defaultColor = theme().text.get();

            for (int i = 0; i < lines.size(); i++) {
                renderer.text(lines.get(i), x, y + h * i, color != null ? color : defaultColor, false);
            }
        }
    }

    /** Hairline, optionally with a small dim title in the middle (used for setting group names). */
    public static class HorizontalSeparator extends WHorizontalSeparator implements AppleWidget {
        public HorizontalSeparator(String text) {
            super(text);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            Color line = t.separator.get();
            double h = Math.max(1, t.scale(1));

            if (text == null) {
                renderer.quad(x, y + Math.round(height / 2.0), width, h, line);
                return;
            }

            double gap = t.scale(6);
            double textStart = Math.round(width / 2.0 - textWidth / 2.0 - gap);
            double textEnd = textStart + gap + textWidth + gap;
            double offsetY = Math.round(height / 2.0);

            renderer.quad(x, y + offsetY, textStart, h, line);
            renderer.text(text, x + textStart + gap, y, t.textSecondary.get(), false);
            renderer.quad(x + textEnd, y + offsetY, width - textEnd, h, line);
        }
    }

    public static class VerticalSeparator extends WVerticalSeparator implements AppleWidget {
        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            renderer.quad(x + Math.round(width / 2.0), y, Math.max(1, t.scale(1)), height, t.separator.get());
        }
    }

    public static class Tooltip extends WTooltip implements AppleWidget {
        public Tooltip(String text) {
            super(text);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            double r = t.radius() * 0.75;
            RoundRender.glassSurface(renderer, t, x, y, width, height, r, r, r, r, AppleWidget.alpha(t.headerColor.get(), 1.08));
            RoundRender.border(renderer, x, y, width, height, r, Math.max(1, t.scale(1)), t.outline.get());
        }
    }

    /** Scroll area: a thin rounded scrollbar that gets a bit brighter when you hover or drag it. */
    public static class View extends WView implements AppleWidget {
        private final Spring hover = new Spring(0);

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (!(canScroll && hasScrollBar)) return;

            GundiTheme t = theme();
            hover.update(focused || handleMouseOver ? 1 : 0, delta, t.response(0.2));

            double w = handleWidth();
            // Follows the text color, so it shows on both dark and light presets.
            Color c = AppleWidget.alpha(t.text.get(), 0.25 + 0.25 * hover.value);
            RoundRender.rect(renderer, handleX(), handleY(), w, handleHeight(), w / 2, c);
        }
    }

    /** The tab bar at the top (Modules, Config, GUI, HUD, ...). The open tab is drawn in the accent color. */
    public static class TopBar extends WTopBar implements AppleWidget {
        @Override
        protected Color getButtonColor(boolean pressed, boolean hovered) {
            GundiTheme t = theme();
            if (pressed) return t.accent.get();
            return hovered ? t.controlHover.get() : t.headerColor.get();
        }

        @Override
        protected Color getNameColor() {
            return theme().text.get();
        }
    }

    public static class AccountRow extends WAccount implements AppleWidget {
        public AccountRow(WidgetScreen screen, Account<?> account) {
            super(screen, account);
        }

        @Override
        protected Color loggedInColor() {
            return theme().loggedIn.get();
        }

        @Override
        protected Color accountTypeColor() {
            return theme().textSecondary.get();
        }
    }

    public static class Quad extends WQuad {
        public Quad(Color color) {
            super(color);
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            renderer.quad(x, y, width, height, color);
        }
    }
}
