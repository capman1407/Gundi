package com.david.gundi.gui.widgets;

import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;

import java.util.List;

/**
 * Table used for everything, but inside a settings group (AppleSection) it becomes a clean list:
 * more space between rows, a faint hairline between them, and a soft highlight on the row under your mouse.
 * Anywhere else it's a plain table.
 */
public class AppleTable extends WTable implements AppleWidget {
    private final Spring hoverY = new Spring(-1), hoverH = new Spring(0), hoverA = new Spring(0);

    private boolean inSection() {
        return parent instanceof AppleSection;
    }

    @Override
    protected double verticalSpacing() {
        return theme.scale(inSection() ? 8 : verticalSpacing);
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (!inSection()) return;
        GundiTheme t = theme();
        double gap = verticalSpacing();
        double pad = t.scale(4);
        double hair = Math.max(1, t.scale(1));
        boolean overTable = mouseX >= x - pad && mouseX < x + width + pad;

        double prevBottom = Double.NaN;
        double hoverTop = -1, hoverHeight = 0;
        for (int i = 0; ; i++) {
            List<Cell<?>> row = getRow(i);
            if (row == null) break;
            if (row.isEmpty()) continue;

            double top = Double.MAX_VALUE, bottom = -Double.MAX_VALUE;
            for (Cell<?> c : row) {
                top = Math.min(top, c.widget().y);
                bottom = Math.max(bottom, c.widget().y + c.widget().height);
            }

            // Hairline halfway between this row and the one above
            if (!Double.isNaN(prevBottom)) {
                double ly = Math.round((prevBottom + top) / 2);
                renderer.quad(x, ly, width, hair, AppleWidget.alpha(t.separator.get(), 0.7));
            }

            if (overTable && mouseY >= top - gap / 2 && mouseY < bottom + gap / 2) {
                hoverTop = top - gap / 2 + hair;
                hoverHeight = bottom - top + gap - hair;
            }
            prevBottom = bottom;
        }

        // Highlight glides between rows instead of jumping
        if (hoverTop >= 0) {
            if (hoverY.value < 0 || hoverA.value < 0.05) {
                hoverY.snap(hoverTop - y);
                hoverH.snap(hoverHeight);
            }
            hoverY.update(hoverTop - y, delta, t.response(0.15));
            hoverH.update(hoverHeight, delta, t.response(0.15));
        }
        hoverA.update(hoverTop >= 0 ? 1 : 0, delta, t.response(0.15));

        double a = Math.max(0, Math.min(1, hoverA.value));
        if (a > 0.01) {
            RoundRender.rect(renderer, x - pad, y + hoverY.value, width + pad * 2, hoverH.value, t.radius() * 0.6,
                AppleWidget.alpha(t.text.get(), 0.045 * a));
        }
    }
}
