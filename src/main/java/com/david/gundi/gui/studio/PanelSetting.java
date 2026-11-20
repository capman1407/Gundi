package com.david.gundi.gui.studio;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.util.ErrorReporter;
import meteordevelopment.meteorclient.gui.utils.SettingsWidgetFactory;
import meteordevelopment.meteorclient.settings.Setting;
import net.minecraft.nbt.NbtCompound;

/**
 * Not a real setting: a placeholder that puts a custom panel (the theme gallery or the profile manager) into
 * the theme's settings list, which Meteor shows in its GUI tab. It stores nothing.
 */
public class PanelSetting extends Setting<Boolean> {
    public enum Kind { Gallery, Profiles }

    public final Kind kind;
    private static boolean registered;

    public PanelSetting(String name, String description, Kind kind) {
        super(name, description, false, null, null, null);
        this.kind = kind;
        register();
    }

    /** Tells Meteor's settings screen how to draw this setting (once, for all themes). */
    private static void register() {
        if (registered) return;
        registered = true;

        SettingsWidgetFactory.registerCustomFactory(PanelSetting.class, theme -> (table, setting) -> {
            try {
                if (!(theme instanceof GundiTheme gundi)) return;
                PanelSetting panel = (PanelSetting) setting;
                if (panel.kind == Kind.Gallery) table.add(new ThemeGallery()).expandX();
                else table.add(ThemeProfiles.build(gundi)).expandX();
            } catch (Exception e) {
                ErrorReporter.report("theme-studio", "build panel", e);
            }
        });
    }

    @Override
    protected Boolean parseImpl(String str) {
        return false;
    }

    @Override
    protected boolean isValueValid(Boolean value) {
        return true;
    }

    @Override
    protected NbtCompound save(NbtCompound tag) {
        return tag;
    }

    @Override
    protected Boolean load(NbtCompound tag) {
        return false;
    }
}
