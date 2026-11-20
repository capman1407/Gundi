package com.david.gundi.gui;

import com.david.gundi.gui.ThemeStyles.*;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.List;

/**
 * The default look of one Gundi theme. GundiTheme builds all its settings from these values,
 * so "Reset Colors" in Meteor's GUI tab brings a theme back to exactly this.
 *
 * Every field starts at the Apple look; each theme below only changes what makes it different.
 */
public class ThemeKit {
    public String name = "Apple";
    public String tagline = "Frosted glass, soft springs.";

    // Shape
    public CornerStyle corners = CornerStyle.Round;
    public double radius = 8;
    public BorderStyle border = BorderStyle.Hairline;
    public double borderWidth = 1;
    public boolean shadows = true;
    public double shadowStrength = 1;
    public boolean glow = false;
    public double glowSize = 10;
    public boolean glowPulse = false;

    // Surfaces
    public SurfaceStyle surface = SurfaceStyle.Frosted;
    public HeaderStyle header = HeaderStyle.Glass;

    // Motion
    public OpenAnim open = OpenAnim.Rise;
    public CloseAnim close = CloseAnim.Sink;
    public double animSpeed = 1;
    public double bounce = 0;

    // Widgets
    public ModuleStyle module = ModuleStyle.Pill;
    public HoverStyle hover = HoverStyle.Fill;
    public ClickEffect click = ClickEffect.Shrink;
    public SwitchStyle switches = SwitchStyle.Switch;
    public Layout layout = Layout.Windows;

    // Backdrop
    public Backdrop backdrop = Backdrop.None;
    public double backdropOpacity = 1;
    public double backdropSpeed = 1;
    public double backdropDensity = 1;
    public boolean vignette = false;
    public boolean cursorGlow = false;
    public boolean autoBlur = true;

    // Colors
    public SettingColor accent = c(10, 132, 255, 255);
    public SettingColor accent2 = c(94, 92, 230, 255);
    public SettingColor glowColor = c(10, 132, 255, 120);
    public SettingColor bg1 = c(10, 132, 255, 70);
    public SettingColor bg2 = c(94, 92, 230, 70);
    public SettingColor switchOn = c(48, 209, 88, 255);
    public SettingColor switchOff = c(72, 72, 74, 255);
    public SettingColor plus = c(48, 209, 88, 255);
    public SettingColor minus = c(255, 69, 58, 255);
    public SettingColor favorite = c(255, 214, 10, 255);

    public SettingColor window = c(28, 28, 30, 215);
    public SettingColor headerColor = c(44, 44, 46, 235);
    public SettingColor control = c(58, 58, 60, 190);
    public SettingColor controlHover = c(72, 72, 74, 210);
    public SettingColor controlPressed = c(99, 99, 102, 230);
    public SettingColor field = c(18, 18, 20, 200);
    public SettingColor separator = c(255, 255, 255, 28);
    public SettingColor outline = c(255, 255, 255, 22);

    public SettingColor text = c(245, 245, 247, 255);
    public SettingColor textSecondary = c(152, 152, 157, 255);
    public SettingColor titleText = c(245, 245, 247, 255);
    public SettingColor textHighlight = c(10, 132, 255, 90);
    public SettingColor placeholder = c(255, 255, 255, 60);
    public SettingColor loggedIn = c(48, 209, 88, 255);

    static SettingColor c(int r, int g, int b, int a) {
        return new SettingColor(r, g, b, a);
    }

    /** Every theme Gundi registers, in the order they show up. */
    public static List<ThemeKit> all() {
        return List.of(apple(), neon(), terminal(), noir(), aurora(), arcade());
    }

    // The themes

    /** The original: macOS dark mode glass. Kept as "Apple" so its saved settings survive. */
    public static ThemeKit apple() {
        return new ThemeKit();
    }

    /** Cyberpunk: cut corners, pink-to-cyan neon edges, synthwave grid, glitchy entrances. */
    public static ThemeKit neon() {
        ThemeKit k = new ThemeKit();
        k.name = "Neon";
        k.tagline = "Synthwave grid, glitch, glow.";
        k.corners = CornerStyle.Chamfer;
        k.radius = 7;
        k.border = BorderStyle.Gradient;
        k.borderWidth = 1.5;
        k.glow = true;
        k.glowSize = 12;
        k.glowPulse = true;
        k.surface = SurfaceStyle.Tinted;
        k.header = HeaderStyle.Underline;
        k.open = OpenAnim.Glitch;
        k.close = CloseAnim.Glitch;
        k.bounce = 0.1;
        k.module = ModuleStyle.Gradient;
        k.hover = HoverStyle.Glow;
        k.click = ClickEffect.Flash;
        k.switches = SwitchStyle.LED;
        k.backdrop = Backdrop.Grid;
        k.vignette = true;
        k.cursorGlow = true;

        k.accent = c(255, 43, 214, 255);
        k.accent2 = c(0, 229, 255, 255);
        k.glowColor = c(255, 43, 214, 110);
        k.bg1 = c(255, 43, 214, 150);
        k.bg2 = c(0, 229, 255, 150);
        k.switchOn = c(0, 229, 255, 255);
        k.switchOff = c(60, 40, 90, 255);
        k.plus = c(0, 229, 255, 255);
        k.minus = c(255, 60, 110, 255);
        k.favorite = c(255, 230, 0, 255);
        k.window = c(14, 8, 28, 225);
        k.headerColor = c(26, 12, 48, 235);
        k.control = c(44, 22, 74, 190);
        k.controlHover = c(70, 30, 110, 210);
        k.controlPressed = c(110, 40, 150, 230);
        k.field = c(8, 4, 18, 220);
        k.separator = c(255, 43, 214, 45);
        k.outline = c(255, 43, 214, 80);
        k.text = c(244, 238, 255, 255);
        k.textSecondary = c(160, 140, 200, 255);
        k.titleText = c(255, 255, 255, 255);
        k.textHighlight = c(255, 43, 214, 90);
        k.placeholder = c(200, 160, 255, 70);
        k.loggedIn = c(0, 229, 255, 255);
        return k;
    }

    /** Sci-fi terminal: square corners, corner brackets, scanlines, digital rain, panels that unfold. */
    public static ThemeKit terminal() {
        ThemeKit k = new ThemeKit();
        k.name = "Terminal";
        k.tagline = "Scanlines, brackets, digital rain.";
        k.corners = CornerStyle.Square;
        k.radius = 0;
        k.border = BorderStyle.Brackets;
        k.shadowStrength = 0.6;
        k.glow = false;
        k.surface = SurfaceStyle.Scanlines;
        k.header = HeaderStyle.Underline;
        k.open = OpenAnim.Unfold;
        k.close = CloseAnim.Collapse;
        k.animSpeed = 1.2;
        k.module = ModuleStyle.Bar;
        k.hover = HoverStyle.Bar;
        k.click = ClickEffect.Flash;
        k.switches = SwitchStyle.Checkbox;
        k.backdrop = Backdrop.Rain;
        k.backdropOpacity = 0.8;
        k.vignette = true;

        k.accent = c(0, 255, 140, 255);
        k.accent2 = c(0, 190, 255, 255);
        k.glowColor = c(0, 255, 140, 90);
        k.bg1 = c(0, 255, 140, 160);
        k.bg2 = c(0, 120, 70, 120);
        k.switchOn = c(0, 255, 140, 255);
        k.switchOff = c(30, 70, 45, 255);
        k.plus = c(0, 255, 140, 255);
        k.minus = c(255, 80, 60, 255);
        k.favorite = c(255, 210, 60, 255);
        k.window = c(3, 12, 7, 225);
        k.headerColor = c(6, 22, 13, 235);
        k.control = c(10, 40, 24, 190);
        k.controlHover = c(16, 60, 36, 210);
        k.controlPressed = c(24, 90, 52, 230);
        k.field = c(0, 8, 4, 220);
        k.separator = c(0, 255, 140, 40);
        k.outline = c(0, 255, 140, 50);
        k.text = c(190, 255, 215, 255);
        k.textSecondary = c(90, 160, 120, 255);
        k.titleText = c(0, 255, 140, 255);
        k.textHighlight = c(0, 255, 140, 80);
        k.placeholder = c(0, 255, 140, 60);
        k.loggedIn = c(0, 255, 140, 255);
        return k;
    }

    /** Dark and elegant: near-black, thin gold double lines, slow drifting gold dust. */
    public static ThemeKit noir() {
        ThemeKit k = new ThemeKit();
        k.name = "Noir";
        k.tagline = "Black and gold, quiet motion.";
        k.corners = CornerStyle.Round;
        k.radius = 3;
        k.border = BorderStyle.Double;
        k.shadowStrength = 1.4;
        k.surface = SurfaceStyle.Solid;
        k.header = HeaderStyle.Underline;
        k.open = OpenAnim.Rise;
        k.close = CloseAnim.Fade;
        k.animSpeed = 0.7;
        k.module = ModuleStyle.Outline;
        k.hover = HoverStyle.Underline;
        k.click = ClickEffect.None;
        k.switches = SwitchStyle.Switch;
        k.backdrop = Backdrop.Embers;
        k.backdropDensity = 0.8;
        k.vignette = true;
        k.cursorGlow = true;

        k.accent = c(212, 175, 55, 255);
        k.accent2 = c(255, 228, 160, 255);
        k.glowColor = c(212, 175, 55, 80);
        k.bg1 = c(212, 175, 55, 160);
        k.bg2 = c(120, 80, 20, 100);
        k.switchOn = c(212, 175, 55, 255);
        k.switchOff = c(54, 50, 44, 255);
        k.plus = c(212, 175, 55, 255);
        k.minus = c(200, 70, 60, 255);
        k.favorite = c(255, 214, 120, 255);
        k.window = c(10, 10, 12, 235);
        k.headerColor = c(16, 15, 14, 240);
        k.control = c(30, 28, 26, 200);
        k.controlHover = c(44, 40, 34, 215);
        k.controlPressed = c(64, 56, 40, 230);
        k.field = c(6, 6, 7, 220);
        k.separator = c(212, 175, 55, 35);
        k.outline = c(212, 175, 55, 90);
        k.text = c(236, 230, 218, 255);
        k.textSecondary = c(140, 132, 120, 255);
        k.titleText = c(232, 200, 120, 255);
        k.textHighlight = c(212, 175, 55, 80);
        k.placeholder = c(236, 230, 218, 55);
        k.loggedIn = c(212, 175, 55, 255);
        return k;
    }

    /** Experimental and soft: very round glass floating over flowing northern lights, bouncy springs. */
    public static ThemeKit aurora() {
        ThemeKit k = new ThemeKit();
        k.name = "Aurora";
        k.tagline = "Northern lights, bouncy glass.";
        k.corners = CornerStyle.Round;
        k.radius = 14;
        k.border = BorderStyle.Glow;
        k.glow = true;
        k.glowSize = 14;
        k.surface = SurfaceStyle.Frosted;
        k.header = HeaderStyle.Glass;
        k.open = OpenAnim.Cascade;
        k.close = CloseAnim.Lift;
        k.bounce = 0.35;
        k.module = ModuleStyle.Gradient;
        k.hover = HoverStyle.Shift;
        k.click = ClickEffect.Ripple;
        k.switches = SwitchStyle.Switch;
        k.backdrop = Backdrop.Aurora;
        k.cursorGlow = true;

        k.accent = c(64, 224, 208, 255);
        k.accent2 = c(167, 139, 250, 255);
        k.glowColor = c(120, 200, 255, 90);
        k.bg1 = c(64, 224, 160, 110);
        k.bg2 = c(167, 100, 250, 110);
        k.switchOn = c(64, 224, 208, 255);
        k.switchOff = c(70, 72, 100, 255);
        k.plus = c(64, 224, 208, 255);
        k.minus = c(255, 110, 140, 255);
        k.favorite = c(255, 220, 120, 255);
        k.window = c(20, 22, 44, 165);
        k.headerColor = c(34, 36, 66, 200);
        k.control = c(60, 64, 104, 160);
        k.controlHover = c(78, 82, 130, 185);
        k.controlPressed = c(100, 104, 160, 210);
        k.field = c(14, 14, 32, 190);
        k.separator = c(255, 255, 255, 30);
        k.outline = c(180, 220, 255, 45);
        k.text = c(246, 246, 255, 255);
        k.textSecondary = c(170, 170, 210, 255);
        k.titleText = c(246, 246, 255, 255);
        k.textHighlight = c(64, 224, 208, 90);
        k.placeholder = c(255, 255, 255, 60);
        k.loggedIn = c(64, 224, 208, 255);
        return k;
    }

    /** Gaming: pixel corners, chunky borders, bright colors, windows that drop in and bounce, warp-speed stars. */
    public static ThemeKit arcade() {
        ThemeKit k = new ThemeKit();
        k.name = "Arcade";
        k.tagline = "Pixel corners, warp speed, bounce.";
        k.corners = CornerStyle.Pixel;
        k.radius = 6;
        k.border = BorderStyle.Hairline;
        k.borderWidth = 2;
        k.shadowStrength = 1.2;
        k.surface = SurfaceStyle.Solid;
        k.header = HeaderStyle.Accent;
        k.open = OpenAnim.Drop;
        k.close = CloseAnim.Scatter;
        k.animSpeed = 1.1;
        k.bounce = 0.6;
        k.module = ModuleStyle.Pill;
        k.hover = HoverStyle.Shift;
        k.click = ClickEffect.Shrink;
        k.switches = SwitchStyle.Checkbox;
        k.backdrop = Backdrop.Starfield;
        k.autoBlur = false;

        k.accent = c(255, 196, 0, 255);
        k.accent2 = c(255, 70, 110, 255);
        k.glowColor = c(255, 196, 0, 100);
        k.bg1 = c(255, 255, 255, 200);
        k.bg2 = c(120, 200, 255, 200);
        k.switchOn = c(80, 230, 90, 255);
        k.switchOff = c(70, 60, 120, 255);
        k.plus = c(80, 230, 90, 255);
        k.minus = c(255, 70, 110, 255);
        k.favorite = c(255, 196, 0, 255);
        k.window = c(26, 20, 60, 235);
        k.headerColor = c(40, 30, 90, 245);
        k.control = c(54, 44, 110, 220);
        k.controlHover = c(72, 60, 140, 230);
        k.controlPressed = c(96, 80, 170, 240);
        k.field = c(14, 10, 36, 230);
        k.separator = c(255, 255, 255, 35);
        k.outline = c(120, 100, 230, 255);
        k.text = c(255, 255, 255, 255);
        k.textSecondary = c(170, 160, 220, 255);
        k.titleText = c(28, 18, 50, 255);
        k.textHighlight = c(255, 196, 0, 90);
        k.placeholder = c(255, 255, 255, 60);
        k.loggedIn = c(80, 230, 90, 255);
        return k;
    }
}
