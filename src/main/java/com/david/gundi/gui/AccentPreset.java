package com.david.gundi.gui;

/**
 * macOS-style accent colors: changes only the highlight color (active modules, sliders, focus rings,
 * text selection) and keeps the rest of your colors. "Custom" leaves your accent alone.
 */
public enum AccentPreset {
    Custom(-1, -1, -1),
    Blue(10, 132, 255),
    Purple(191, 90, 242),
    Pink(255, 55, 95),
    Red(255, 69, 58),
    Orange(255, 159, 10),
    Yellow(255, 214, 10),
    Green(48, 209, 88),
    Graphite(142, 142, 147);

    private final int r, g, b;

    AccentPreset(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    public void apply(GundiTheme t) {
        if (this == Custom) return;

        t.accent.get().set(r, g, b, 255);
        t.accent.onChanged();

        // Selected text uses a see-through version of the accent; keep its current transparency.
        int a = t.textHighlight.get().a;
        t.textHighlight.get().set(r, g, b, a);
        t.textHighlight.onChanged();
    }
}
