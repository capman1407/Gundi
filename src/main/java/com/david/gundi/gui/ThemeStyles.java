package com.david.gundi.gui;

/**
 * All the "personality" choices a Gundi theme can make. Every theme picks its own defaults
 * (see ThemeKit), and every one of them can be changed in the theme's settings.
 */
public final class ThemeStyles {
    private ThemeStyles() {}

    /** Shape of every corner: windows, buttons, modules, switches. */
    public enum CornerStyle { Round, Chamfer, Square, Pixel }

    /** Window border. */
    public enum BorderStyle { None, Hairline, Double, Brackets, Glow, Gradient }

    /** Window body fill. */
    public enum SurfaceStyle { Frosted, Solid, Tinted, Scanlines }

    /** Window title bar. */
    public enum HeaderStyle { Glass, Accent, Underline, Minimal }

    /** How windows appear when a GUI screen opens. */
    public enum OpenAnim { None, Rise, Cascade, Drop, Slide, Unfold, Iris, Glitch }

    /** How windows leave when the GUI closes. */
    public enum CloseAnim { None, Fade, Sink, Lift, Collapse, Scatter, Glitch }

    /** How an active (turned on) module looks. */
    public enum ModuleStyle { Pill, Bar, Gradient, Outline, Glow, Text }

    /** What happens when you hover a module or button. */
    public enum HoverStyle { Fill, Bar, Underline, Glow, Shift }

    /** What happens when you click a module. */
    public enum ClickEffect { None, Shrink, Ripple, Flash }

    /** How on/off settings look. */
    public enum SwitchStyle { Switch, Checkbox, LED }

    /** Animated background behind the windows. */
    public enum Backdrop { None, Dim, Gradient, Aurora, Grid, Particles, Constellation, Starfield, Rain, Embers, Waves }

    /** How the Modules tab is laid out. */
    public enum Layout { Windows, Spotlight }
}
