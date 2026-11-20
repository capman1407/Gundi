package com.david.gundi.gui.studio;

import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import com.david.gundi.gui.ThemeStyles.*;
import com.david.gundi.util.ErrorReporter;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.tabs.builtin.GuiTab;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;

import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Live previews of every Gundi theme, side by side. Each card is a tiny window drawn with that theme's own
 * current colors, corner style, borders, title bar and active-module look (so your edits show up here too).
 * Click a card to switch to that theme.
 */
public class ThemeGallery extends WWidget implements AppleWidget {
    private static final int COLUMNS = 3;

    private Spring[] hover = new Spring[0];
    private double cardW, cardH, gap, labelH;

    @Override
    protected void onCalculateSize() {
        List<GundiTheme> themes = GundiTheme.all();
        cardW = theme.scale(124);
        cardH = theme.scale(78);
        gap = theme.scale(8);
        labelH = theme.textHeight() + theme.scale(6);

        int rows = (themes.size() + COLUMNS - 1) / COLUMNS;
        width = COLUMNS * cardW + (COLUMNS - 1) * gap;
        height = rows * (cardH + labelH) + Math.max(0, rows - 1) * gap;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        try {
            GundiTheme current = theme();
            List<GundiTheme> themes = GundiTheme.all();
            if (hover.length != themes.size()) {
                hover = new Spring[themes.size()];
                for (int i = 0; i < hover.length; i++) hover[i] = new Spring(0);
            }

            for (int i = 0; i < themes.size(); i++) {
                double cx = x + (i % COLUMNS) * (cardW + gap);
                double cy = y + (i / COLUMNS) * (cardH + labelH + gap);
                boolean over = mouseX >= cx && mouseX < cx + cardW && mouseY >= cy && mouseY < cy + cardH + labelH;
                current.spring(hover[i], over ? 1 : 0, delta, 0.25);
                drawCard(renderer, current, themes.get(i), cx, cy, Math.max(0, hover[i].value));
            }
        } catch (Exception e) {
            ErrorReporter.report("theme-studio", "gallery", e);
        }
    }

    private void drawCard(GuiRenderer r, GundiTheme current, GundiTheme g, double cx, double cy, double h) {
        boolean selected = GuiThemes.get() == g;
        double lift = current.scale(3) * h;
        cy -= lift;

        double radius = current.radius();
        if (h > 0.01) RoundRender.glow(r, cx, cy, cardW, cardH, radius, current.scale(8), AppleWidget.alpha(g.accent.get(), 0.5 * h));
        if (selected) RoundRender.border(r, cx - current.scale(3), cy - current.scale(3), cardW + current.scale(6), cardH + current.scale(6),
            radius + current.scale(3), Math.max(1, current.scale(2)), current.accent.get());

        // Everything inside the card uses the previewed theme's own corner style
        CornerStyle saved = RoundRender.corners;
        RoundRender.corners = g.corners.get();
        try {
            drawPreview(r, g, cx, cy, h);
        } finally {
            RoundRender.corners = saved;
        }

        // Name under the card (+ a dot on the one in use)
        double ty = cy + cardH + current.scale(4);
        Color textColor = selected ? current.text.get() : AppleWidget.mix(current.textSecondary.get(), current.text.get(), h);
        double tw = current.textWidth(g.name);
        double tx = cx + (cardW - tw) / 2;
        r.text(g.name, tx, ty, textColor, false);
        if (selected) {
            double d = current.scale(5);
            r.quad(tx - d - current.scale(4), ty + current.textHeight() / 2 - d / 2, d, d, GuiRenderer.CIRCLE, current.accent.get());
        }
    }

    /** A tiny window in the previewed theme: backdrop colors, title bar, three module rows and a switch. */
    private void drawPreview(GuiRenderer r, GundiTheme g, double cx, double cy, double h) {
        double s = theme.scale(1);
        double cr = Math.min(g.cornerRadius.get(), 10) * s;

        // Card background: the theme's backdrop colors over black
        RoundRender.rect(r, cx, cy, cardW, cardH, cr, new Color(8, 8, 10, 230));
        double p = GundiTheme.time() * 0.6;
        Color b1 = AppleWidget.alpha(g.backdrop1.get(), 0.55), b2 = AppleWidget.alpha(g.backdrop2.get(), 0.55);
        RoundRender.hGradientRect(r, cx, cy, cardW, cardH, cr, cr, cr, cr,
            AppleWidget.mix(b1, b2, 0.5 + 0.5 * Math.sin(p)), AppleWidget.mix(b1, b2, 0.5 + 0.5 * Math.cos(p)));

        // Mini window
        double wx = cx + 12 * s, wy = cy + 9 * s, ww = cardW - 24 * s, wh = cardH - 18 * s;
        double wr = Math.min(g.cornerRadius.get() * 0.6, 7) * s;
        double hh = 9 * s;
        if (g.glow.get()) RoundRender.glow(r, wx, wy, ww, wh, wr, 5 * s, AppleWidget.alpha(g.glowColor.get(), g.glowAlpha()));
        RoundRender.rect(r, wx, wy, ww, wh, wr, g.window.get());

        switch (g.header.get()) {
            case Accent -> RoundRender.hGradientRect(r, wx, wy, ww, hh, wr, wr, 0, 0, g.accent.get(), g.accent2.get());
            case Underline -> r.quad(wx, wy + hh - Math.max(1, s), ww, Math.max(1, s), g.accent.get(), g.accent2.get());
            case Glass -> RoundRender.rect(r, wx, wy, ww, hh, wr, wr, 0, 0, g.headerColor.get());
            case Minimal -> {}
        }
        // Title "text"
        r.quad(wx + ww / 2 - 12 * s, wy + hh / 2 - 1.5 * s, 24 * s, 3 * s,
            g.header.get() == HeaderStyle.Accent ? g.titleText.get() : AppleWidget.alpha(g.titleText.get(), 0.8));

        double line = Math.max(1, s);
        switch (g.border.get()) {
            case Hairline, Glow -> RoundRender.border(r, wx, wy, ww, wh, wr, line, g.border.get() == BorderStyle.Glow ? g.accent.get() : g.outline.get());
            case Double -> {
                RoundRender.border(r, wx, wy, ww, wh, wr, line, g.outline.get());
                RoundRender.border(r, wx + 2 * s, wy + 2 * s, ww - 4 * s, wh - 4 * s, Math.max(0, wr - 2 * s), line, AppleWidget.alpha(g.outline.get(), 0.5));
            }
            case Brackets -> RoundRender.brackets(r, wx, wy, ww, wh, 7 * s, Math.max(1, 1.5 * s), g.accent.get());
            case Gradient -> RoundRender.gradientBorder(r, wx, wy, ww, wh, wr, line, g.accent.get(), g.accent2.get(), GundiTheme.time() * 1.6);
            default -> {}
        }

        // Module rows: first one active (in the theme's active-module style), two inactive
        double rx = wx + 5 * s, rw = ww - 10 * s, rh = 9 * s, ry = wy + hh + 4 * s;
        double rr = wr * 0.6;
        Color acc = g.accent.get(), acc2 = g.accent2.get();
        switch (g.moduleStyle.get()) {
            case Pill -> RoundRender.rect(r, rx, ry, rw, rh, rr, acc);
            case Gradient -> RoundRender.hGradientRect(r, rx, ry, rw, rh, rr, rr, rr, rr, acc, acc2);
            case Bar -> {
                RoundRender.rect(r, rx, ry, rw, rh, rr, AppleWidget.alpha(acc, 0.18));
                r.quad(rx, ry, 2 * s, rh, acc);
            }
            case Outline -> RoundRender.border(r, rx, ry, rw, rh, rr, line, acc);
            case Glow -> {
                RoundRender.glow(r, rx, ry, rw, rh, rr, 4 * s, AppleWidget.alpha(acc, 0.7));
                RoundRender.rect(r, rx, ry, rw, rh, rr, AppleWidget.alpha(acc, 0.35));
            }
            case Text -> {}
        }
        boolean coloredText = switch (g.moduleStyle.get()) { case Text, Bar, Outline -> true; default -> false; };
        r.quad(rx + 4 * s, ry + rh / 2 - 1.5 * s, 30 * s, 3 * s, coloredText ? acc : g.text.get());
        for (int k = 1; k <= 2; k++) {
            double yy = ry + k * (rh + 2 * s);
            r.quad(rx + 4 * s, yy + rh / 2 - 1.5 * s, (22 + k * 6) * s, 3 * s, AppleWidget.alpha(g.textSecondary.get(), 0.9));
        }

        // Switch in the bottom right, flips back and forth while hovered
        double on = h > 0.5 ? 0.5 + 0.5 * Math.sin(GundiTheme.time() * 4) : 1;
        double sw = 14 * s, sh = 8 * s, sx = wx + ww - sw - 5 * s, sy = wy + wh - sh - 4 * s;
        Color track = AppleWidget.mix(g.switchOff.get(), g.switchOn.get(), on);
        RoundRender.rect(r, sx, sy, sw, sh, sh / 2, track);
        double ks = sh - 2 * s;
        RoundRender.rect(r, sx + s + (sw - ks - 2 * s) * on, sy + s, ks, ks, ks / 2, new Color(255, 255, 255));
    }

    @Override
    public boolean onMouseClicked(Click click, boolean doubled) {
        List<GundiTheme> themes = GundiTheme.all();
        for (int i = 0; i < themes.size(); i++) {
            double cx = x + (i % COLUMNS) * (cardW + gap);
            double cy = y + (i / COLUMNS) * (cardH + labelH + gap);
            if (click.x() >= cx && click.x() < cx + cardW && click.y() >= cy && click.y() < cy + cardH + labelH) {
                select(themes.get(i));
                return true;
            }
        }
        return false;
    }

    /** Switch theme and reopen the GUI tab with it (same as picking it in Meteor's theme dropdown). */
    static void select(GundiTheme g) {
        try {
            if (GuiThemes.get() == g) return;
            GuiThemes.select(g.name);
            mc.setScreen(null);
            Tabs.get(GuiTab.class).openScreen(GuiThemes.get());
            ErrorReporter.step("theme-studio", "switched to theme " + g.name);
        } catch (Exception e) {
            ErrorReporter.report("theme-studio", "switch theme", e);
        }
    }
}
