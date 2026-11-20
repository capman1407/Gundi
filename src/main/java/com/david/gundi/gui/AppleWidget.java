package com.david.gundi.gui;

import meteordevelopment.meteorclient.gui.utils.BaseWidget;
import com.david.gundi.util.Colors;
import meteordevelopment.meteorclient.utils.render.color.Color;

/** Gives every Apple widget easy access to the Apple theme and a couple of color helpers. */
public interface AppleWidget extends BaseWidget {
    default GundiTheme theme() {
        return (GundiTheme) getTheme();
    }

    /** Color between a and b. t = 0 gives a, t = 1 gives b. */
    static Color mix(Color a, Color b, double t) {
        return Colors.mix(a, b, t);
    }

    /** Copy of a color with its transparency multiplied by f (0 = invisible, 1 = unchanged). */
    static Color alpha(Color c, double f) {
        return Colors.alpha(c, f);
    }
}
