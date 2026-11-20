package com.david.gundi.gui;

import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

/**
 * Ready-made color combinations for the Apple theme. Picking one fills in all the theme's colors;
 * you can still tweak single colors afterwards. "Custom" leaves your colors alone.
 *
 * The dark presets are all built the same way from one "base" tint: the title bar, buttons, hover and
 * pressed states are that tint stepped lighter, the text field a bit darker. That keeps every preset consistent.
 */
public enum ColorPreset {
    Custom,
    Midnight,
    Graphite,
    Ocean,
    Sunset,
    Forest,
    Rose,
    Lavender,
    Light;

    public void apply(GundiTheme t) {
        switch (this) {
            case Custom -> {} // keep whatever colors you have
            case Midnight -> dark(t, rgb(10, 132, 255), rgb(48, 209, 88), 28, 28, 30, rgb(245, 245, 247), rgb(152, 152, 157));
            case Graphite -> dark(t, rgb(152, 152, 157), rgb(142, 142, 147), 32, 32, 32, rgb(240, 240, 240), rgb(150, 150, 150));
            case Ocean -> dark(t, rgb(100, 210, 255), rgb(90, 200, 250), 14, 28, 44, rgb(235, 245, 255), rgb(140, 170, 200));
            case Sunset -> dark(t, rgb(255, 159, 10), rgb(255, 159, 10), 36, 22, 22, rgb(255, 244, 236), rgb(200, 160, 150));
            case Forest -> dark(t, rgb(48, 209, 88), rgb(48, 209, 88), 16, 32, 22, rgb(236, 250, 240), rgb(150, 185, 160));
            case Rose -> dark(t, rgb(255, 55, 95), rgb(255, 55, 95), 36, 18, 28, rgb(255, 240, 245), rgb(200, 150, 170));
            case Lavender -> dark(t, rgb(191, 90, 242), rgb(191, 90, 242), 26, 20, 38, rgb(246, 242, 255), rgb(165, 155, 190));
            case Light -> light(t);
        }
    }

    private static void dark(GundiTheme t, int[] accent, int[] switchOn, int r, int g, int b, int[] text, int[] secondary) {
        set(t.accent, accent, 255);
        set(t.switchOn, switchOn, 255);
        set(t.switchOff, tint(r, g, b, 44), 255);

        set(t.window, tint(r, g, b, 0), 215);
        set(t.headerColor, tint(r, g, b, 16), 235);
        set(t.control, tint(r, g, b, 30), 190);
        set(t.controlHover, tint(r, g, b, 44), 210);
        set(t.controlPressed, tint(r, g, b, 71), 230);
        set(t.field, tint(r, g, b, -10), 200);
        set(t.separator, rgb(255, 255, 255), 28);
        set(t.outline, rgb(255, 255, 255), 22);

        set(t.text, text, 255);
        set(t.textSecondary, secondary, 255);
        set(t.titleText, text, 255);
        set(t.textHighlight, accent, 90);
        set(t.placeholder, rgb(255, 255, 255), 60);
    }

    /** Apple's light mode: white glass, dark text. */
    private static void light(GundiTheme t) {
        set(t.accent, rgb(0, 122, 255), 255);
        set(t.switchOn, rgb(52, 199, 89), 255);
        set(t.switchOff, rgb(209, 209, 214), 255);

        set(t.window, rgb(242, 242, 247), 215);
        set(t.headerColor, rgb(255, 255, 255), 225);
        set(t.control, rgb(229, 229, 234), 200);
        set(t.controlHover, rgb(216, 216, 222), 215);
        set(t.controlPressed, rgb(199, 199, 204), 230);
        set(t.field, rgb(255, 255, 255), 220);
        set(t.separator, rgb(0, 0, 0), 28);
        set(t.outline, rgb(0, 0, 0), 30);

        set(t.text, rgb(28, 28, 30), 255);
        set(t.textSecondary, rgb(99, 99, 102), 255);
        set(t.titleText, rgb(28, 28, 30), 255);
        set(t.textHighlight, rgb(0, 122, 255), 70);
        set(t.placeholder, rgb(0, 0, 0), 70);
    }

    /** Base tint stepped lighter (positive) or darker (negative), kept within 0-255. */
    private static int[] tint(int r, int g, int b, int step) {
        return new int[] {clamp(r + step), clamp(g + step), clamp(b + step)};
    }

    private static int[] rgb(int r, int g, int b) {
        return new int[] {r, g, b};
    }

    /**
     * Changes the color in place (instead of swapping in a new color object), so color widgets that are
     * already open show the new color straight away, then tells Meteor the setting changed (so it gets saved).
     */
    private static void set(Setting<SettingColor> setting, int[] rgb, int alpha) {
        setting.get().set(rgb[0], rgb[1], rgb[2], alpha);
        setting.onChanged();
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }
}
