package com.david.gundi.gui.widgets;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WContainer;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.util.math.MathHelper;

/**
 * Text field: dark rounded field with an accent "focus ring" that fades in while you type.
 * The typing logic is Meteor's; only the look is changed (rendering code follows Meteor's own text box).
 */
public class AppleTextBox extends WTextBox implements AppleWidget {
    private boolean cursorVisible;
    private double cursorTimer;
    private double cursorAnim;
    private final Spring focus = new Spring(0);

    public AppleTextBox(String text, String placeholder, CharFilter filter, Class<? extends Renderer> renderer) {
        super(text, placeholder, filter, renderer);
    }

    @Override
    protected WContainer createCompletionsRootWidget() {
        return new WVerticalList() {
            @Override
            protected void onRender(GuiRenderer renderer1, double mouseX, double mouseY, double delta) {
                GundiTheme t = theme();
                double r = t.radius() * 0.75;
                RoundRender.glassSurface(renderer1, t, x, y, width, height, 0, 0, r, r, AppleWidget.alpha(t.headerColor.get(), 1.1));
            }
        };
    }

    @SuppressWarnings("unchecked")
    @Override
    protected <T extends WWidget & ICompletionItem> T createCompletionsValueWidth(String completion, boolean selected) {
        return (T) new CompletionItem(completion, false, selected);
    }

    private static class CompletionItem extends AppleBasics.Label implements ICompletionItem {
        private boolean selected;

        public CompletionItem(String text, boolean title, boolean selected) {
            super(text, title);
            this.selected = selected;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            if (selected) renderer.quad(this, AppleWidget.alpha(theme().accent.get(), 0.6));
            super.onRender(renderer, mouseX, mouseY, delta);
        }

        @Override
        public boolean isSelected() {
            return selected;
        }

        @Override
        public void setSelected(boolean selected) {
            this.selected = selected;
        }

        @Override
        public String getCompletion() {
            return text;
        }
    }

    @Override
    protected void onCursorChanged() {
        cursorVisible = true;
        cursorTimer = 0;
    }

    @Override
    protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
        if (cursorTimer >= 1) {
            cursorVisible = !cursorVisible;
            cursorTimer = 0;
        } else {
            cursorTimer += delta * 1.75;
        }

        GundiTheme t = theme();
        double pad = pad();
        double overflowWidth = getOverflowWidthForRender();
        double radius = t.radius() * 0.75;

        // Field + focus ring
        focus.update(focused ? 1 : 0, delta, t.response(0.25));
        RoundRender.rect(renderer, x, y, width, height, radius, t.field.get());
        RoundRender.border(renderer, x, y, width, height, radius, Math.max(1, t.scale(1)),
            AppleWidget.mix(t.outline.get(), t.accent.get(), focus.value));

        renderer.scissorStart(x + pad, y + pad, width - pad * 2, height - pad * 2);

        // Text content
        if (!text.isEmpty()) {
            this.renderer.render(renderer, x + pad - overflowWidth, y + pad, text, t.text.get());
        } else if (placeholder != null) {
            this.renderer.render(renderer, x + pad - overflowWidth, y + pad, placeholder, t.placeholder.get());
        }

        // Text highlighting
        if (focused && (cursor != selectionStart || cursor != selectionEnd)) {
            double selStart = x + pad + getTextWidth(selectionStart) - overflowWidth;
            double selEnd = x + pad + getTextWidth(selectionEnd) - overflowWidth;
            renderer.quad(selStart, y + pad, selEnd - selStart, theme.textHeight(), t.textHighlight.get());
        }

        // Cursor (fades in and out instead of blinking hard)
        cursorAnim += delta * 10 * (focused && cursorVisible ? 1 : -1);
        cursorAnim = MathHelper.clamp(cursorAnim, 0, 1);

        if ((focused && cursorVisible) || cursorAnim > 0) {
            renderer.setAlpha(cursorAnim);
            Color cursorColor = t.accent.get();
            renderer.quad(x + pad + getTextWidth(cursor) - overflowWidth, y + pad, Math.max(1, t.scale(1.5)), theme.textHeight(), cursorColor);
            renderer.setAlpha(1);
        }

        renderer.scissorEnd();
    }
}
