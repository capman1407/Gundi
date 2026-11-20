package com.david.gundi.gui.studio;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.ThemeStyles.*;
import com.david.gundi.util.Colors;
import com.david.gundi.util.ErrorReporter;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.GuiThemes;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.tabs.builtin.GuiTab;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WButton;
import meteordevelopment.meteorclient.gui.widgets.pressable.WMinus;
import meteordevelopment.meteorclient.settings.EnumSetting;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtSizeTracker;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.Random;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Profile manager in the Theme Studio:
 * - Save the current theme (with all your tweaks) under a name, load it later, delete it.
 *   Profiles are files in .minecraft/meteor-client/gundi/gui-profiles, so you can also share them.
 * - "Surprise me": a random, matching color palette. "Remix": random palette AND random styles.
 * (Copy / paste of the whole theme as text is already built into Meteor's GUI tab: the two icons at the top.)
 */
public final class ThemeProfiles {
    private ThemeProfiles() {}

    private static final Random RANDOM = new Random();

    private static File folder() {
        return new File(MeteorClient.FOLDER, "gundi/gui-profiles");
    }

    public static WVerticalList build(GundiTheme theme) {
        WVerticalList root = theme.verticalList();

        WHorizontalList saveRow = root.add(theme.horizontalList()).expandX().widget();
        WTextBox name = saveRow.add(theme.textBox("", "Profile name")).minWidth(theme.scale(160)).expandX().widget();
        WButton save = saveRow.add(theme.button("Save")).widget();

        WHorizontalList funRow = root.add(theme.horizontalList()).expandX().widget();
        WButton surprise = funRow.add(theme.button("Surprise me")).expandX().widget();
        surprise.tooltip = "Random matching color palette for this theme.";
        WButton remix = funRow.add(theme.button("Remix")).expandX().widget();
        remix.tooltip = "Random palette AND random corners, borders, animations and backdrop.";

        WTable list = root.add(theme.table()).expandX().widget();
        fill(theme, list);

        save.action = () -> {
            String n = clean(name.get());
            if (n.isEmpty()) n = theme.name + " " + (count() + 1);
            if (save(theme, n)) {
                name.set("");
                fill(theme, list);
            }
        };
        // Reopening the GUI tab refreshes the color pickers and replays the open animation as a little reveal
        surprise.action = () -> {
            randomPalette(theme);
            reopen();
        };
        remix.action = () -> {
            randomPalette(theme);
            randomStyles(theme);
            reopen();
        };

        return root;
    }

    private static void fill(GundiTheme theme, WTable list) {
        list.clear();
        File[] files = folder().listFiles((dir, f) -> f.endsWith(".nbt"));
        if (files == null || files.length == 0) {
            WLabel empty = list.add(theme.label("No saved profiles yet.")).widget();
            empty.color = theme.textSecondaryColor();
            return;
        }

        Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
        for (File file : files) {
            NbtCompound tag = read(file);
            String profileName = file.getName().substring(0, file.getName().length() - 4);
            String themeName = tag != null ? tag.getString("theme", "?") : "broken";

            list.add(theme.label(profileName)).expandCellX();
            WLabel themeLabel = list.add(theme.label(themeName)).widget();
            themeLabel.color = theme.textSecondaryColor();

            WButton load = list.add(theme.button("Load")).widget();
            load.action = () -> load(file);

            WMinus delete = list.add(theme.confirmedMinus()).widget();
            delete.action = () -> {
                if (file.delete()) ErrorReporter.step("theme-studio", "deleted profile " + profileName);
                fill(theme, list);
            };
            list.row();
        }
    }

    // Files

    private static boolean save(GundiTheme theme, String name) {
        try {
            File dir = folder();
            if (!dir.exists() && !dir.mkdirs()) throw new IllegalStateException("could not create " + dir);
            NbtCompound tag = new NbtCompound();
            tag.putString("theme", theme.name);
            tag.put("data", theme.toTag());
            try (OutputStream out = new FileOutputStream(new File(dir, name + ".nbt"))) {
                NbtIo.writeCompressed(tag, out);
            }
            ErrorReporter.step("theme-studio", "saved profile " + name);
            return true;
        } catch (Exception e) {
            ErrorReporter.report("theme-studio", "save profile", e);
            return false;
        }
    }

    private static NbtCompound read(File file) {
        try (InputStream in = new FileInputStream(file)) {
            return NbtIo.readCompressed(in, NbtSizeTracker.ofUnlimitedBytes());
        } catch (Exception e) {
            ErrorReporter.step("theme-studio", "could not read profile " + file.getName() + ": " + e);
            return null;
        }
    }

    /** Switches to the profile's theme, applies its saved settings and reopens the GUI tab. */
    private static void load(File file) {
        try {
            NbtCompound tag = read(file);
            if (tag == null) return;
            String themeName = tag.getString("theme", "");

            GuiTheme target = null;
            for (GundiTheme g : GundiTheme.all()) if (g.name.equals(themeName)) target = g;
            if (target == null) {
                ErrorReporter.step("theme-studio", "profile uses unknown theme " + themeName);
                return;
            }

            GuiThemes.select(target.name);
            GuiThemes.get().fromTag(tag.getCompoundOrEmpty("data"));
            reopen();
            ErrorReporter.step("theme-studio", "loaded profile " + file.getName());
        } catch (Exception e) {
            ErrorReporter.report("theme-studio", "load profile", e);
        }
    }

    private static void reopen() {
        try {
            mc.setScreen(null);
            Tabs.get(GuiTab.class).openScreen(GuiThemes.get());
        } catch (Exception e) {
            ErrorReporter.report("theme-studio", "reopen gui", e);
        }
    }

    private static int count() {
        File[] files = folder().listFiles((dir, f) -> f.endsWith(".nbt"));
        return files == null ? 0 : files.length;
    }

    private static String clean(String s) {
        return s.replaceAll("[^a-zA-Z0-9 _\\-]", "").trim();
    }

    // Randomizers

    /** A random palette that still matches: one hue for the accent, a neighbour hue for accent-2, tinted darks. */
    static void randomPalette(GundiTheme t) {
        try {
            double h = RANDOM.nextDouble();
            double h2 = h + (RANDOM.nextBoolean() ? 1 : -1) * (0.08 + RANDOM.nextDouble() * 0.25);
            double sat = 0.6 + RANDOM.nextDouble() * 0.35;

            set(t.accent, hsv(h, sat, 1, 255));
            set(t.accent2, hsv(h2, sat, 1, 255));
            set(t.glowColor, hsv(h, sat, 1, 110));
            set(t.switchOn, hsv(h, sat * 0.9, 0.95, 255));
            set(t.switchOff, hsv(h, 0.25, 0.3, 255));
            set(t.plus, hsv(h2, sat, 1, 255));
            set(t.backdrop1, hsv(h, sat, 1, 130));
            set(t.backdrop2, hsv(h2, sat, 1, 130));

            set(t.window, hsv(h, 0.45, 0.10, 220));
            set(t.headerColor, hsv(h, 0.45, 0.16, 235));
            set(t.control, hsv(h, 0.35, 0.24, 200));
            set(t.controlHover, hsv(h, 0.35, 0.30, 215));
            set(t.controlPressed, hsv(h, 0.35, 0.38, 230));
            set(t.field, hsv(h, 0.4, 0.06, 220));
            set(t.separator, hsv(h, sat, 1, 40));
            set(t.outline, hsv(h, sat, 1, 70));

            set(t.text, hsv(h, 0.05, 0.97, 255));
            set(t.textSecondary, hsv(h, 0.18, 0.66, 255));
            set(t.titleText, t.header.get() == HeaderStyle.Accent ? hsv(h, 0.6, 0.15, 255) : hsv(h, 0.05, 0.97, 255));
            set(t.textHighlight, hsv(h, sat, 1, 90));
            set(t.loggedIn, hsv(h2, sat, 1, 255));
            ErrorReporter.step("theme-studio", "random palette, hue " + Math.round(h * 360));
        } catch (Exception e) {
            ErrorReporter.report("theme-studio", "surprise me", e);
        }
    }

    static void randomStyles(GundiTheme t) {
        try {
            pick(t.corners);
            pick(t.border);
            pick(t.surface);
            pick(t.header);
            pick(t.openAnim);
            pick(t.closeAnim);
            pick(t.moduleStyle);
            pick(t.hoverStyle);
            pick(t.clickEffect);
            pick(t.switchStyle);
            pick(t.backdrop);
            t.cornerRadius.set((double) Math.round(RANDOM.nextDouble() * 14));
            t.bounce.set(Math.round(RANDOM.nextDouble() * 6) / 10.0);
            t.glow.set(RANDOM.nextBoolean());
            t.vignette.set(RANDOM.nextBoolean());
            t.cursorGlow.set(RANDOM.nextBoolean());
            // Keep the title readable on an accent-filled title bar
            if (t.header.get() == HeaderStyle.Accent) {
                SettingColor a = t.accent.get();
                t.titleText.set(new SettingColor(a.r / 7, a.g / 7, a.b / 7, 255));
            } else {
                t.titleText.set(new SettingColor(t.text.get()));
            }
        } catch (Exception e) {
            ErrorReporter.report("theme-studio", "remix", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static <E extends Enum<E>> void pick(Setting<E> setting) {
        E[] values = (E[]) ((EnumSetting<E>) setting).get().getDeclaringClass().getEnumConstants();
        setting.set(values[RANDOM.nextInt(values.length)]);
    }

    private static void set(Setting<SettingColor> setting, SettingColor c) {
        setting.set(c);
    }

    private static SettingColor hsv(double h, double s, double v, int a) {
        Color c = Colors.hsv(h, s, v, a);
        return new SettingColor(c.r, c.g, c.b, c.a);
    }
}
