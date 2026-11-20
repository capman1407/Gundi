package com.david.gundi;

import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.ThemeKit;
import meteordevelopment.meteorclient.gui.GuiThemes;
import com.david.gundi.hud.ArmorRingsHud;
import com.david.gundi.hud.CompassBarHud;
import com.david.gundi.hud.PickupFeedHud;
import com.david.gundi.hud.GlassClockHud;
import com.david.gundi.hud.RadarHud;
import com.david.gundi.hud.SpiralScoutHud;
import com.david.gundi.hud.GundiChatHud;
import com.david.gundi.modules.GlowTrail;
import com.david.gundi.modules.HitMarker;
import com.david.gundi.modules.AppleNametags;
import com.david.gundi.modules.Crosshair;
import com.david.gundi.modules.SpiralScout;
import com.david.gundi.hud.AreaTitleHud;
import com.david.gundi.util.CombatTracker;
import meteordevelopment.meteorclient.MeteorClient;
import com.david.gundi.hud.DynamicIslandHud;
import com.david.gundi.hud.GlassCoordsHud;
import com.david.gundi.hud.SpeedGraphHud;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.modules.Wings;
import com.mojang.logging.LogUtils;
import meteordevelopment.meteorclient.addons.MeteorAddon;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudGroup;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Modules;
import org.slf4j.Logger;

public class Gundi extends MeteorAddon {
    public static final Logger LOG = LogUtils.getLogger();
    public static final Category CATEGORY = new Category("Gundi");
    public static final HudGroup HUD_GROUP = new HudGroup("Gundi");

    @Override
    public void onInitialize() {
        LOG.info("Initializing Gundi");

        // Modules
        load("wings", () -> Modules.get().add(new Wings()));
        load("glow-trail", () -> Modules.get().add(new GlowTrail()));
        load("combat-tracker", () -> MeteorClient.EVENT_BUS.subscribe(CombatTracker.INSTANCE));
        load("hit-marker", () -> Modules.get().add(new HitMarker()));
        load("apple-nametags", () -> Modules.get().add(new AppleNametags()));
        load("crosshair", () -> Modules.get().add(new Crosshair()));
        load("spiral-scout", () -> Modules.get().add(new SpiralScout()));

        // HUD
        load("speed-graph", () -> Hud.get().register(SpeedGraphHud.INFO));
        load("dynamic-island", () -> Hud.get().register(DynamicIslandHud.INFO));
        load("glass-coords", () -> Hud.get().register(GlassCoordsHud.INFO));
        load("area-titles", () -> Hud.get().register(AreaTitleHud.INFO));
        load("armor-rings", () -> Hud.get().register(ArmorRingsHud.INFO));
        load("compass-bar", () -> Hud.get().register(CompassBarHud.INFO));
        load("pickup-feed", () -> Hud.get().register(PickupFeedHud.INFO));
        load("glass-clock", () -> Hud.get().register(GlassClockHud.INFO));
        load("radar", () -> Hud.get().register(RadarHud.INFO));
        load("spiral-scout-hud", () -> Hud.get().register(SpiralScoutHud.INFO));
        load("gundi-chat", () -> Hud.get().register(GundiChatHud.INFO));

        // One Gundi theme engine, six personalities. Each shows up in the GUI tab's theme dropdown.
        for (ThemeKit kit : ThemeKit.all()) {
            load("theme-" + kit.name.toLowerCase(), () -> GuiThemes.add(new GundiTheme(kit)));
        }

        LOG.info("Gundi finished loading");
    }

    /**
     * Registers one feature. Prints a line before and after, so if the game crashes during startup
     * the terminal shows exactly which feature it was loading. If one feature fails, the rest still load.
     */
    private void load(String feature, Runnable register) {
        ErrorReporter.step(feature, "loading...");
        try {
            register.run();
            ErrorReporter.step(feature, "loaded");
        } catch (Throwable e) {
            ErrorReporter.report(feature, "startup", e);
        }
    }

    @Override
    public void onRegisterCategories() {
        Modules.registerCategory(CATEGORY);
    }

    @Override
    public String getPackage() {
        return "com.david.gundi";
    }
}
