package com.david.gundi.gui;

import com.david.gundi.gui.ThemeStyles.*;
import com.david.gundi.gui.studio.PanelSetting;
import com.david.gundi.gui.studio.SpotlightScreen;
import com.david.gundi.gui.widgets.*;
import com.david.gundi.util.ErrorReporter;
import com.david.gundi.util.SettingsCompat;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.tabs.builtin.GuiTab;
import java.util.Map;
import meteordevelopment.meteorclient.MeteorClient;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.WidgetScreen;
import meteordevelopment.meteorclient.gui.renderer.packer.GuiTexture;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.utils.AlignmentX;
import meteordevelopment.meteorclient.gui.utils.CharFilter;
import meteordevelopment.meteorclient.gui.widgets.*;
import meteordevelopment.meteorclient.gui.widgets.containers.WSection;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WDropdown;
import meteordevelopment.meteorclient.gui.widgets.input.WSlider;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.*;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.accounts.Account;
import meteordevelopment.meteorclient.systems.hud.screens.HudEditorScreen;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.systems.modules.render.Blur;
import meteordevelopment.meteorclient.utils.render.color.Color;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.util.MacWindowUtil;
import net.minecraft.nbt.NbtCompound;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * The Gundi theme engine. One class, many personalities: every theme (Apple, Neon, Terminal, Noir, Aurora,
 * Arcade) is an instance of this built from a different ThemeKit, so each shows up separately in Meteor's
 * theme dropdown, keeps its own saved settings, and can be reshaped completely in its settings.
 *
 * Structure follows Meteor's own theme (MeteorGuiTheme): settings, then one factory method per widget type.
 */
public class GundiTheme extends GuiTheme {
    /** Every Gundi theme that was registered, so the theme gallery can show them all. */
    public static final List<GundiTheme> ALL = new ArrayList<>();

    public final ThemeKit kit;

    private final SettingGroup sgStudio = settings.createGroup("Theme Studio");
    private final SettingGroup sgGeneral = settings.createGroup("General");
    private final SettingGroup sgShape = settings.createGroup("Shape", false);
    private final SettingGroup sgMotion = settings.createGroup("Motion", false);
    private final SettingGroup sgWidgets = settings.createGroup("Widgets", false);
    private final SettingGroup sgBackdrop = settings.createGroup("Backdrop", false);
    private final SettingGroup sgHud = settings.createGroup("HUD", false);
    private final SettingGroup sgColors = settings.createGroup("Palette", false);
    private final SettingGroup sgSurfaces = settings.createGroup("Surfaces", false);
    private final SettingGroup sgText = settings.createGroup("Text", false);

    // Theme Studio: gallery of all themes (click one to switch) and saved profiles
    public final Setting<Boolean> gallery = sgStudio.add(new PanelSetting("gallery", "All Gundi themes. Click one to switch to it.", PanelSetting.Kind.Gallery));
    public final Setting<Boolean> profiles = sgStudio.add(new PanelSetting("profiles", "Save your look as a profile, load it later, or let Gundi surprise you.", PanelSetting.Kind.Profiles));

    // General

    public final Setting<Double> scale;
    public final Setting<Layout> layout;
    public final Setting<Boolean> autoBlur;
    public final Setting<AlignmentX> moduleAlignment;
    public final Setting<Boolean> categoryIcons;
    public final Setting<Boolean> hideHUD;

    // Shape
    public final Setting<CornerStyle> corners;
    public final Setting<Double> cornerRadius;
    public final Setting<BorderStyle> border;
    public final Setting<Double> borderWidth;
    public final Setting<SurfaceStyle> surface;
    public final Setting<HeaderStyle> header;
    public final Setting<Boolean> shadows;
    public final Setting<Double> shadowStrength;
    public final Setting<Boolean> glow;
    public final Setting<Double> glowSize;
    public final Setting<Boolean> glowPulse;

    // Motion
    public final Setting<OpenAnim> openAnim;
    public final Setting<CloseAnim> closeAnim;
    public final Setting<Double> animationSpeed;
    public final Setting<Double> bounce;

    // Widgets
    public final Setting<ModuleStyle> moduleStyle;
    public final Setting<HoverStyle> hoverStyle;
    public final Setting<ClickEffect> clickEffect;
    public final Setting<SwitchStyle> switchStyle;

    // Backdrop
    public final Setting<Backdrop> backdrop;
    public final Setting<Double> backdropOpacity;
    public final Setting<Double> backdropSpeed;
    public final Setting<Double> backdropDensity;
    public final Setting<SettingColor> backdrop1;
    public final Setting<SettingColor> backdrop2;
    public final Setting<Boolean> vignette;
    public final Setting<Boolean> cursorGlow;

    // HUD (applies to every Gundi HUD element that has use-theme on)
    public final Setting<Double> hudOpacity;
    public final Setting<Boolean> hudGlow;

    // Colors
    public final Setting<SettingColor> accent, accent2, glowColor, switchOn, switchOff, plus, minus, favorite;
    // Surfaces
    public final Setting<SettingColor> window, headerColor, control, controlHover, controlPressed, field, separator, outline;
    // Text
    public final Setting<SettingColor> text, textSecondary, titleText, textHighlight, placeholder, loggedIn;

    // Presets, shown at the top of the Palette group (see addPresets)
    public Setting<ColorPreset> colorPreset;
    public Setting<AccentPreset> accentPreset;

    private boolean loading; // true while Meteor loads saved settings, see fromTag()

    // Starscript code colors (Xcode dark style, not worth a setting each)
    private static final Color SS_BRACE = new Color(152, 152, 157);
    private static final Color SS_STRING = new Color(252, 106, 93);
    private static final Color SS_NUMBER = new Color(208, 191, 105);
    private static final Color SS_KEYWORD = new Color(252, 95, 163);
    private static final Color SS_OBJECT = new Color(103, 183, 164);

    // Auto-blur: remembers if WE turned Blur on, so we only turn it off again if it was our doing.
    private boolean blurEnabledByUs;

    public GundiTheme(ThemeKit k) {
        super(k.name);
        this.kit = k;

        Runnable rebuild = () -> {
            if (mc.currentScreen instanceof WidgetScreen screen) screen.invalidate();
        };

        // General
        scale = sgGeneral.add(new DoubleSetting.Builder().name("scale").description("Scale of the GUI.")
            .defaultValue(1).min(0.75).sliderRange(0.75, 4).onSliderRelease().onChanged(v -> rebuild.run()).build());
        layout = sgGeneral.add(new EnumSetting.Builder<Layout>().name("layout")
            .description("Windows = Meteor's floating category windows. Spotlight = one big window with a category rail, search and module cards. Reopen the GUI after changing.")
            .defaultValue(k.layout).build());
        autoBlur = sgGeneral.add(new BoolSetting.Builder().name("auto-blur")
            .description("Turns on Meteor's Blur module while this GUI is open (and off again when you close it).")
            .defaultValue(k.autoBlur).build());
        moduleAlignment = sgGeneral.add(new EnumSetting.Builder<AlignmentX>().name("module-alignment")
            .description("How module names are aligned.").defaultValue(AlignmentX.Left).build());
        categoryIcons = sgGeneral.add(new BoolSetting.Builder().name("category-icons")
            .description("Adds item icons to module categories.").defaultValue(false).build());
        hideHUD = sgGeneral.add(new BoolSetting.Builder().name("hide-HUD").description("Hide HUD when in GUI.")
            .defaultValue(false).onChanged(v -> {
                if (mc.currentScreen instanceof WidgetScreen) mc.options.hudHidden = v;
            }).build());

        // Shape
        corners = sgShape.add(new EnumSetting.Builder<CornerStyle>().name("corner-style")
            .description("Round, cut (Chamfer), sharp (Square) or stepped (Pixel) corners on everything.")
            .defaultValue(k.corners).build());
        cornerRadius = sgShape.add(new DoubleSetting.Builder().name("corner-radius")
            .description("How big the corners are.").defaultValue(k.radius).range(0, 20).build());
        border = sgShape.add(new EnumSetting.Builder<BorderStyle>().name("border-style")
            .description("Window edge: thin line, double line, sci-fi corner brackets, glow, or an animated gradient.")
            .defaultValue(k.border).build());
        borderWidth = sgShape.add(new DoubleSetting.Builder().name("border-width")
            .description("Thickness of window borders.").defaultValue(k.borderWidth).range(0.5, 4).build());
        surface = sgShape.add(new EnumSetting.Builder<SurfaceStyle>().name("surface")
            .description("Window body: frosted glass, solid, tinted with the accent, or CRT scanlines.")
            .defaultValue(k.surface).build());
        header = sgShape.add(new EnumSetting.Builder<HeaderStyle>().name("title-bar")
            .description("Window title bar: glass, filled with the accent gradient, an accent underline, or nothing.")
            .defaultValue(k.header).build());
        shadows = sgShape.add(new BoolSetting.Builder().name("shadows")
            .description("Soft shadows under windows.").defaultValue(k.shadows).build());
        shadowStrength = sgShape.add(new DoubleSetting.Builder().name("shadow-strength")
            .description("How dark the shadows are.").defaultValue(k.shadowStrength).range(0, 2.5)
            .visible(shadows::get).build());
        glow = sgShape.add(new BoolSetting.Builder().name("glow")
            .description("Colored light around windows and active controls.").defaultValue(k.glow).build());
        glowSize = sgShape.add(new DoubleSetting.Builder().name("glow-size")
            .description("How far the glow reaches.").defaultValue(k.glowSize).range(2, 30).visible(glow::get).build());
        glowPulse = sgShape.add(new BoolSetting.Builder().name("glow-pulse")
            .description("The glow slowly breathes.").defaultValue(k.glowPulse).visible(glow::get).build());

        // Motion
        openAnim = sgMotion.add(new EnumSetting.Builder<OpenAnim>().name("open-style")
            .description("How windows appear: rise, cascade, drop from the sky, slide in from the side, unfold, iris open, or glitch in.")
            .defaultValue(k.open).build());
        closeAnim = sgMotion.add(new EnumSetting.Builder<CloseAnim>().name("close-style")
            .description("How windows leave when you close the GUI.").defaultValue(k.close).build());
        animationSpeed = sgMotion.add(new DoubleSetting.Builder().name("animation-speed")
            .description("Speed of every animation. 1 = normal.").defaultValue(k.animSpeed).range(0.25, 4).build());
        bounce = sgMotion.add(new DoubleSetting.Builder().name("bounce")
            .description("0 = smooth Apple-style springs, 1 = very bouncy and playful.").defaultValue(k.bounce).range(0, 1).build());

        // Widgets
        moduleStyle = sgWidgets.add(new EnumSetting.Builder<ModuleStyle>().name("active-module")
            .description("How a turned-on module looks.").defaultValue(k.module).build());
        hoverStyle = sgWidgets.add(new EnumSetting.Builder<HoverStyle>().name("hover-effect")
            .description("What happens under your mouse.").defaultValue(k.hover).build());
        clickEffect = sgWidgets.add(new EnumSetting.Builder<ClickEffect>().name("click-effect")
            .description("What happens when you click a module.").defaultValue(k.click).build());
        switchStyle = sgWidgets.add(new EnumSetting.Builder<SwitchStyle>().name("switch-style")
            .description("How on/off settings look.").defaultValue(k.switches).onChanged(v -> rebuild.run()).build());

        // Backdrop
        backdrop = sgBackdrop.add(new EnumSetting.Builder<Backdrop>().name("backdrop")
            .description("Animated background behind the windows.").defaultValue(k.backdrop).build());
        backdropOpacity = sgBackdrop.add(new DoubleSetting.Builder().name("opacity")
            .description("How strong the backdrop is.").defaultValue(k.backdropOpacity).range(0, 1).build());
        backdropSpeed = sgBackdrop.add(new DoubleSetting.Builder().name("speed")
            .description("How fast the backdrop moves.").defaultValue(k.backdropSpeed).range(0, 4).build());
        backdropDensity = sgBackdrop.add(new DoubleSetting.Builder().name("density")
            .description("How many particles, stars, drops or lines.").defaultValue(k.backdropDensity).range(0.2, 3).build());
        backdrop1 = color(sgBackdrop, "backdrop-1", "First backdrop color.", k.bg1);
        backdrop2 = color(sgBackdrop, "backdrop-2", "Second backdrop color.", k.bg2);
        vignette = sgBackdrop.add(new BoolSetting.Builder().name("vignette")
            .description("Darkens the screen edges so the windows stand out.").defaultValue(k.vignette).build());
        cursorGlow = sgBackdrop.add(new BoolSetting.Builder().name("cursor-light")
            .description("A soft light follows your mouse behind the windows.").defaultValue(k.cursorGlow).build());

        // HUD
        hudOpacity = sgHud.add(new DoubleSetting.Builder().name("hud-opacity")
            .description("How solid HUD panels are, compared to GUI windows. Applies to every HUD element that follows the theme.")
            .defaultValue(0.85).range(0.1, 1).build());
        hudGlow = sgHud.add(new BoolSetting.Builder().name("hud-glow")
            .description("HUD panels glow too when this theme has glow on.").defaultValue(true).build());

        // Palette: presets first, then the colors they set
        addPresets();
        accent = color(sgColors, "accent", "Main highlight color (active modules, sliders, focus).", k.accent);
        accent2 = color(sgColors, "accent-2", "Second highlight color, used for gradients.", k.accent2);
        glowColor = color(sgColors, "glow", "Color of the glow.", k.glowColor);
        switchOn = color(sgColors, "switch-on", "Color of a switch that's on.", k.switchOn);
        switchOff = color(sgColors, "switch-off", "Color of a switch that's off (keep it fully solid).", k.switchOff);
        plus = color(sgColors, "plus", "Color of plus buttons.", k.plus);
        minus = color(sgColors, "minus", "Color of minus/delete buttons.", k.minus);
        favorite = color(sgColors, "favorite", "Color of a checked favorite star.", k.favorite);

        // Surfaces
        window = color(sgSurfaces, "window", "Window background.", k.window);
        headerColor = color(sgSurfaces, "header", "Window title bar.", k.headerColor);
        control = color(sgSurfaces, "control", "Buttons and fields.", k.control);
        controlHover = color(sgSurfaces, "control-hover", "Buttons and fields when hovered.", k.controlHover);
        controlPressed = color(sgSurfaces, "control-pressed", "Buttons while held down.", k.controlPressed);
        field = color(sgSurfaces, "field", "Text box background.", k.field);
        separator = color(sgSurfaces, "separator", "Hairline separators.", k.separator);
        outline = color(sgSurfaces, "outline", "Thin border around windows.", k.outline);

        // Text
        text = color(sgText, "text", "Main text.", k.text);
        textSecondary = color(sgText, "text-secondary", "Dimmer text (inactive modules, hints).", k.textSecondary);
        titleText = color(sgText, "title-text", "Window titles.", k.titleText);
        textHighlight = color(sgText, "text-highlight", "Selected text in text boxes.", k.textHighlight);
        placeholder = color(sgText, "placeholder", "Placeholder text in empty text boxes.", k.placeholder);
        loggedIn = color(sgText, "logged-in", "Name of the logged in account.", k.loggedIn);

        settingsFactory = new GundiSettingsFactory(this);
        MeteorClient.EVENT_BUS.subscribe(this);
        ALL.add(this);
    }

    /** Ready-made palettes, shown at the top of the Palette group (they change the colors below them). */
    private void addPresets() {
        colorPreset = sgColors.add(new EnumSetting.Builder<ColorPreset>().name("color-preset")
            .description("Ready-made color combinations. Picking one changes all colors; Custom keeps your own.")
            .defaultValue(ColorPreset.Custom)
            .onChanged(preset -> {
                // Meteor also runs this when it loads saved settings: don't re-apply then, or your own
                // color tweaks would be overwritten every time the game starts.
                if (loading) return;
                try {
                    preset.apply(this);
                    ErrorReporter.step("gui-theme", "applied color preset " + preset);
                } catch (Exception e) {
                    ErrorReporter.report("gui-theme", "color preset", e);
                }
            }).build());
        accentPreset = sgColors.add(new EnumSetting.Builder<AccentPreset>().name("accent-preset")
            .description("macOS-style accent color: only changes the highlight color, keeps your background.")
            .defaultValue(AccentPreset.Custom)
            .onChanged(preset -> {
                if (loading) return;
                try {
                    preset.apply(this);
                    ErrorReporter.step("gui-theme", "applied accent preset " + preset);
                } catch (Exception e) {
                    ErrorReporter.report("gui-theme", "accent preset", e);
                }
            }).build());
    }

    public static List<GundiTheme> all() {
        return Collections.unmodifiableList(ALL);
    }

    /** Loading saved settings: mark it, so the color presets aren't re-applied over your saved colors. */
    @Override
    public GuiTheme fromTag(NbtCompound tag) {
        loading = true;
        try {
            // Settings were regrouped over time (e.g. presets moved into Palette): keep saved values
            return super.fromTag(SettingsCompat.regroupOwner(tag, settings, Map.of()));
        } finally {
            loading = false;
        }
    }

    /** Meteor calls this every frame while a GUI screen using this theme is drawn. */
    @Override
    public void beforeRender() {
        super.beforeRender();
        RoundRender.corners = corners.get(); // every rounded shape drawn this frame uses this theme's corners
        refreshHiddenSettings();

        try {
            // Never blur the HUD editor: you need to see the HUD clearly there.
            if (!autoBlur.get() || mc.currentScreen instanceof HudEditorScreen) return;
            Blur blur = Modules.get().get(Blur.class);
            if (blur != null && !blur.isActive()) {
                blur.enable();
                blurEnabledByUs = true;
                ErrorReporter.step("gui-theme", "auto-blur turned Blur on");
            }
        } catch (Exception e) {
            ErrorReporter.report("gui-theme", "auto-blur", e);
        }
    }

    /**
     * Meteor's GUI tab never re-checks which theme settings should be visible (module screens do), so turning
     * on e.g. "glow" didn't show "glow-size" until you reopened the tab. When any setting's visibility changes
     * while the GUI tab is open, rebuild it after this frame.
     */
    private void refreshHiddenSettings() {
        try {
            if (!(mc.currentScreen instanceof WidgetScreen screen) || !Tabs.get(GuiTab.class).isScreen(screen)) return;
            boolean changed = false;
            for (SettingGroup g : settings) {
                for (Setting<?> s : g) {
                    boolean v = s.isVisible();
                    if (v != s.lastWasVisible) changed = true;
                    s.lastWasVisible = v;
                }
            }
            if (changed) screen.taskAfterRender = screen::reload;
        } catch (Exception e) {
            ErrorReporter.report("gui-theme", "refresh settings", e);
        }
    }

    /** Once the GUI is closed, turn Blur back off if we were the ones who turned it on. */
    @EventHandler
    private void onTick(TickEvent.Post event) {
        try {
            boolean guiOpen = mc.currentScreen instanceof WidgetScreen && !(mc.currentScreen instanceof HudEditorScreen);
            if (!blurEnabledByUs || guiOpen) return;

            Blur blur = Modules.get().get(Blur.class);
            if (blur != null && blur.isActive()) blur.disable();
            blurEnabledByUs = false;
            ErrorReporter.step("gui-theme", "auto-blur turned Blur off");
        } catch (Exception e) {
            ErrorReporter.report("gui-theme", "auto-blur", e);
        }
    }

    private Setting<SettingColor> color(SettingGroup group, String name, String description, SettingColor color) {
        return group.add(new ColorSetting.Builder()
            .name(name + "-color")
            .description(description)
            .defaultValue(new SettingColor(color))
            .build());
    }

    // Helpers for the widgets

    /** Corner radius in pixels, at the current GUI scale. */
    public double radius() {
        return scale(cornerRadius.get());
    }

    /** Border thickness in pixels, at least 1. */
    public double line() {
        return Math.max(1, scale(borderWidth.get()));
    }

    /** Spring response time, adjusted by the animation-speed setting. */
    public double response(double seconds) {
        return seconds / animationSpeed.get();
    }

    /** Spring damping from the bounce setting: 1 = no bounce, lower = overshoots and wobbles. */
    public double damping() {
        return 1 - 0.7 * bounce.get();
    }

    /** Moves a spring towards target with this theme's speed and bounce. */
    public double spring(Spring s, double target, double delta, double seconds) {
        return s.update(target, delta, response(seconds), damping());
    }

    public boolean frosted() {
        return surface.get() == SurfaceStyle.Frosted;
    }

    /** Seconds since the game started, for looping animations (pulses, gradients, backdrops). */
    public static double time() {
        return System.nanoTime() / 1e9;
    }

    /** Glow strength right now (breathes slowly if glow-pulse is on). */
    public double glowAlpha() {
        if (!glowPulse.get()) return 1;
        return 0.7 + 0.3 * Math.sin(time() * 2.2 * animationSpeed.get());
    }

    /**
     * How fast Meteor fades GUI screens in/out (used by WidgetScreenMixin). Meteor's own value is 14 (~0.07s).
     * With a close animation we use ~0.22s so it has time to play.
     */
    public float screenFadeSpeed() {
        if (closeAnim.get() == CloseAnim.None) return 14;
        return (float) (4.5 * animationSpeed.get());
    }

    // Screens

    @Override
    public TabScreen modulesScreen() {
        if (layout.get() == Layout.Spotlight) {
            try {
                return new SpotlightScreen(this);
            } catch (Exception e) {
                ErrorReporter.report("spotlight", "open", e);
            }
        }
        return super.modulesScreen();
    }

    @Override
    public boolean isModulesScreen(Screen screen) {
        return screen instanceof SpotlightScreen || super.isModulesScreen(screen);
    }

    // Widgets

    @Override
    public WWindow window(WWidget icon, String title) {
        return w(new AppleWindow(icon, title));
    }

    @Override
    public WLabel label(String text, boolean title, double maxWidth) {
        if (maxWidth == 0 && !text.contains("\n")) return w(new AppleBasics.Label(text, title));
        return w(new AppleBasics.MultiLabel(text, title, maxWidth));
    }

    @Override
    public WHorizontalSeparator horizontalSeparator(String text) {
        return w(new AppleBasics.HorizontalSeparator(text));
    }

    @Override
    public WVerticalSeparator verticalSeparator() {
        return w(new AppleBasics.VerticalSeparator());
    }

    @Override
    protected WButton button(String text, GuiTexture texture) {
        return w(new ApplePressables.Button(text, texture));
    }

    @Override
    protected WConfirmedButton confirmedButton(String text, String confirmText, GuiTexture texture) {
        return w(new ApplePressables.ConfirmedButton(text, confirmText, texture));
    }

    @Override
    public WMinus minus() {
        return w(new ApplePressables.Minus());
    }

    @Override
    public WConfirmedMinus confirmedMinus() {
        return w(new ApplePressables.ConfirmedMinus());
    }

    @Override
    public WPlus plus() {
        return w(new ApplePressables.Plus());
    }

    @Override
    public WCheckbox checkbox(boolean checked) {
        return w(new ApplePressables.Switch(checked));
    }

    @Override
    public WSlider slider(double value, double min, double max) {
        return w(new AppleSlider(value, min, max));
    }

    @Override
    public WTextBox textBox(String text, String placeholder, CharFilter filter, Class<? extends WTextBox.Renderer> renderer) {
        return w(new AppleTextBox(text, placeholder, filter, renderer));
    }

    @Override
    public <T> WDropdown<T> dropdown(T[] values, T value) {
        return w(new AppleDropdown<>(values, value));
    }

    @Override
    public WTriangle triangle() {
        return w(new ApplePressables.Triangle());
    }

    @Override
    public WTooltip tooltip(String text) {
        return w(new AppleBasics.Tooltip(text));
    }

    @Override
    public WTable table() {
        return w(new AppleTable());
    }

    @Override
    public WView view() {
        return w(new AppleBasics.View());
    }

    @Override
    public WSection section(String title, boolean expanded, WWidget headerWidget) {
        return w(new AppleSection(title, expanded, headerWidget));
    }

    @Override
    public WAccount account(WidgetScreen screen, Account<?> account) {
        return w(new AppleBasics.AccountRow(screen, account));
    }

    @Override
    public WWidget module(Module module, String title) {
        return w(new AppleModule(module, title));
    }

    @Override
    public WQuad quad(Color color) {
        return w(new AppleBasics.Quad(color));
    }

    @Override
    public WTopBar topBar() {
        return w(new AppleBasics.TopBar());
    }

    @Override
    public WFavorite favorite(boolean checked) {
        return w(new ApplePressables.Favorite(checked));
    }

    // Colors

    @Override
    public Color textColor() {
        return text.get();
    }

    @Override
    public Color textSecondaryColor() {
        return textSecondary.get();
    }

    @Override
    public Color starscriptTextColor() {
        return text.get(); // follows the text color so it's readable on light presets too
    }

    @Override
    public Color starscriptBraceColor() {
        return SS_BRACE;
    }

    @Override
    public Color starscriptParenthesisColor() {
        return SS_BRACE;
    }

    @Override
    public Color starscriptDotColor() {
        return SS_BRACE;
    }

    @Override
    public Color starscriptCommaColor() {
        return SS_BRACE;
    }

    @Override
    public Color starscriptOperatorColor() {
        return text.get();
    }

    @Override
    public Color starscriptStringColor() {
        return SS_STRING;
    }

    @Override
    public Color starscriptNumberColor() {
        return SS_NUMBER;
    }

    @Override
    public Color starscriptKeywordColor() {
        return SS_KEYWORD;
    }

    @Override
    public Color starscriptAccessedObjectColor() {
        return SS_OBJECT;
    }

    // Other

    @Override
    public TextRenderer textRenderer() {
        return TextRenderer.get();
    }

    @Override
    public double scale(double value) {
        double scaled = value * scale.get();

        // Same fix Meteor's own theme uses for Retina screens on Mac.
        if (MacWindowUtil.IS_MAC) {
            scaled /= (double) mc.getWindow().getWidth() / mc.getWindow().getFramebufferWidth();
        }

        return scaled;
    }

    @Override
    public boolean categoryIcons() {
        return categoryIcons.get();
    }

    @Override
    public boolean hideHUD() {
        return hideHUD.get();
    }
}
