package com.david.gundi.gui.studio;

import com.david.gundi.gui.AppleWidget;
import com.david.gundi.gui.GundiTheme;
import com.david.gundi.gui.RoundRender;
import com.david.gundi.gui.Spring;
import com.david.gundi.gui.ThemeStyles.ClickEffect;
import com.david.gundi.util.ErrorReporter;
import meteordevelopment.meteorclient.gui.renderer.GuiRenderer;
import meteordevelopment.meteorclient.gui.tabs.TabScreen;
import meteordevelopment.meteorclient.gui.tabs.Tabs;
import meteordevelopment.meteorclient.gui.utils.Cell;
import meteordevelopment.meteorclient.gui.widgets.WLabel;
import meteordevelopment.meteorclient.gui.widgets.containers.WHorizontalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WTable;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.gui.widgets.containers.WView;
import meteordevelopment.meteorclient.gui.widgets.containers.WWindow;
import meteordevelopment.meteorclient.gui.widgets.input.WTextBox;
import meteordevelopment.meteorclient.gui.widgets.pressable.WPressable;
import meteordevelopment.meteorclient.renderer.text.TextRenderer;
import meteordevelopment.meteorclient.systems.config.Config;
import meteordevelopment.meteorclient.systems.modules.Category;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.systems.modules.Modules;
import meteordevelopment.meteorclient.utils.Utils;
import meteordevelopment.meteorclient.utils.misc.NbtUtils;
import meteordevelopment.meteorclient.utils.render.color.Color;
import net.minecraft.client.gui.Click;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import static meteordevelopment.meteorclient.MeteorClient.mc;
import static org.lwjgl.glfw.GLFW.*;

/**
 * "Spotlight" layout for the Modules tab (theme setting layout = Spotlight).
 * One big window instead of a window per category:
 *   [ search                                   12 on / 140 ]
 *   [ All       ][ card ][ card ][ card ][ card ]
 *   [ Favorites ][ card ][ card ][ card ][ card ]
 *   [ Combat    ][ ...                          ]
 * - The rail on the left picks a category; a highlight glides to it.
 * - Cards show the name, a one-line description, keybind, favorite star and an on/off light.
 *   Left click toggles, right click opens settings, middle click (un)favorites.
 * - Typing searches every module (names, then settings), like Meteor's search window.
 * - When the list changes, cards pop in one after another.
 */
public class SpotlightScreen extends TabScreen {
    private static String selected = "All"; // remembered while the game runs

    private WTextBox search;
    private WTable grid;
    private WLabel stats;
    private Rail rail;
    private int columns = 4;
    private long builtNanos;

    public SpotlightScreen(GundiTheme theme) {
        super(theme, Tabs.get().getFirst());
    }

    private GundiTheme t() {
        return (GundiTheme) theme;
    }

    @Override
    public void initWidgets() {
        try {
            build();
        } catch (Exception e) {
            ErrorReporter.report("spotlight", "build", e);
        }
    }

    private void build() {
        GundiTheme t = t();
        double W = Utils.getWindowWidth(), H = Utils.getWindowHeight();

        WWindow window = add(t.window("Modules")).center().widget();
        window.id = "gundi-spotlight";
        window.view.scrollOnlyWhenMouseOver = true;

        // Columns that fit about 70% of the screen next to the rail
        double cardW = ModuleCard.cardWidth(t) + t.scale(6);
        columns = (int) Math.max(2, Math.min(6, Math.floor((W * 0.7 - t.scale(150)) / cardW)));

        // Top: search + stats
        WHorizontalList top = window.add(t.horizontalList()).expandX().widget();
        search = top.add(t.textBox("", "Search modules and settings...")).minWidth(t.scale(220)).expandX().widget();
        search.setFocused(true);
        search.action = this::fillGrid;
        stats = top.add(t.label("")).widget();
        stats.color = t.textSecondaryColor();

        // Body: rail | cards
        WHorizontalList body = window.add(t.horizontalList()).expandX().widget();
        rail = body.add(new Rail()).top().widget();
        rail.add(new RailItem("All", Items.COMPASS.getDefaultStack(), visibleModules().size())).expandX();
        long favs = visibleModules().stream().filter(m -> m.favorite).count();
        if (favs > 0) rail.add(new RailItem("Favorites", Items.NETHER_STAR.getDefaultStack(), (int) favs)).expandX();
        for (Category c : Modules.loopCategories()) {
            int count = (int) Modules.get().getGroup(c).stream().filter(this::visible).count();
            if (count > 0) rail.add(new RailItem(c.name, c.icon, count)).expandX();
        }

        WView view = body.add(t.view()).top().widget();
        view.maxHeight = H * 0.62;
        view.scrollOnlyWhenMouseOver = true;
        grid = view.add(t.table()).widget();

        WLabel hint = window.add(t.label("Left click: toggle   Right click: settings   Middle click: favorite")).widget();
        hint.color = t.textSecondaryColor();

        fillGrid();
    }

    /** Rebuilds the module cards for the current category or search text. */
    private void fillGrid() {
        try {
            grid.clear();
            builtNanos = System.nanoTime();

            List<Module> list = new ArrayList<>();
            String query = search.get().trim();
            if (!query.isEmpty()) {
                for (var p : Modules.get().searchTitles(query)) if (visible(p.getLeft()) && !list.contains(p.getLeft())) list.add(p.getLeft());
                for (Module m : Modules.get().searchSettingTitles(query)) if (visible(m) && !list.contains(m)) list.add(m);
            } else if (selected.equals("All")) {
                list.addAll(visibleModules());
                list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title));
            } else if (selected.equals("Favorites")) {
                for (Module m : visibleModules()) if (m.favorite) list.add(m);
                list.sort((a, b) -> String.CASE_INSENSITIVE_ORDER.compare(a.title, b.title));
            } else {
                for (Category c : Modules.loopCategories()) {
                    if (!c.name.equals(selected)) continue;
                    for (Module m : Modules.get().getGroup(c)) if (visible(m)) list.add(m);
                }
            }

            if (list.isEmpty()) {
                WLabel none = grid.add(theme.label(query.isEmpty() ? "Nothing here yet." : "No modules match \"" + query + "\".")).widget();
                none.color = theme.textSecondaryColor();
            }

            for (int i = 0; i < list.size(); i++) {
                grid.add(new ModuleCard(list.get(i), i)).pad(t().scale(3));
                if ((i + 1) % columns == 0) grid.row();
            }

            long on = list.stream().filter(Module::isActive).count();
            stats.set(on + " on / " + list.size());
        } catch (Exception e) {
            ErrorReporter.report("spotlight", "fill", e);
        }
    }

    private List<Module> visibleModules() {
        List<Module> out = new ArrayList<>();
        for (Module m : Modules.get().getAll()) if (visible(m)) out.add(m);
        return out;
    }

    private boolean visible(Module m) {
        return !Config.get().hiddenModules.get().contains(m);
    }

    @Override
    public boolean toClipboard() {
        return NbtUtils.toClipboard(Modules.get());
    }

    @Override
    public boolean fromClipboard() {
        return NbtUtils.fromClipboard(Modules.get());
    }

    @Override
    public void reload() {
    }

    // Widgets

    /** Category list with a highlight that glides to the selected item. */
    private class Rail extends WVerticalList {
        private final Spring hy = new Spring(-1), hh = new Spring(0), show = new Spring(1);

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = t();
            RailItem target = null;
            for (Cell<?> c : cells) if (c.widget() instanceof RailItem ri && ri.key.equals(selected)) target = ri;
            boolean searching = !search.get().isEmpty();
            show.update(target != null && !searching ? 1 : 0, delta, t.response(0.2));
            if (target == null) return;

            double ty = target.y - y;
            if (hy.value < 0) {
                hy.snap(ty);
                hh.snap(target.height);
            }
            t.spring(hy, ty, delta, 0.3);
            t.spring(hh, target.height, delta, 0.3);

            double a = Math.max(0, Math.min(1, show.value));
            if (a < 0.01) return;
            double r = t.radius() * 0.75;
            if (t.glow.get()) RoundRender.glow(renderer, x, y + hy.value, width, hh.value, r, t.scale(6), AppleWidget.alpha(t.accent.get(), 0.5 * a));
            RoundRender.hGradientRect(renderer, x, y + hy.value, width, hh.value, r, r, r, r,
                AppleWidget.alpha(t.accent.get(), a), AppleWidget.alpha(AppleWidget.mix(t.accent.get(), t.accent2.get(), 0.6), a));
        }
    }

    private class RailItem extends WPressable implements AppleWidget {
        final String key;
        private final ItemStack icon;
        private final String count;
        private final Spring hover = new Spring(0);

        RailItem(String key, ItemStack icon, int count) {
            this.key = key;
            this.icon = icon;
            this.count = String.valueOf(count);
        }

        @Override
        protected void onCalculateSize() {
            double pad = theme.scale(6);
            width = Math.max(theme.scale(130), pad * 3 + theme.textHeight() + theme.textWidth(key) + theme.scale(8) + theme.textWidth(count));
            height = pad * 2 + theme.textHeight();
        }

        @Override
        protected void onPressed(int button) {
            if (selected.equals(key) && search.get().isEmpty()) return;
            selected = key;
            search.set("");
            fillGrid();
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            hover.update(mouseOver ? 1 : 0, delta, t.response(0.18));
            boolean sel = key.equals(selected) && search.get().isEmpty();
            double pad = t.scale(6), th = t.textHeight();

            if (!sel && hover.value > 0.01) RoundRender.rect(renderer, x, y, width, height, t.radius() * 0.75, AppleWidget.alpha(t.text.get(), 0.07 * hover.value));

            double shift = t.scale(3) * hover.value;
            if (icon != null && !icon.isEmpty()) {
                float scale = (float) (th / 16);
                renderer.item(icon, (int) (x + pad + shift), (int) (y + pad), scale, false);
            }
            Color c = sel ? t.text.get() : AppleWidget.mix(t.textSecondary.get(), t.text.get(), hover.value);
            renderer.text(key, x + pad * 2 + th + shift, y + pad, c, false);
            renderer.text(count, x + width - pad - t.textWidth(count), y + pad,
                sel ? AppleWidget.alpha(t.text.get(), 0.75) : AppleWidget.alpha(t.textSecondary.get(), 0.8), false);
        }
    }

    /** One module as a card: name, description, keybind, favorite star and an on/off light. */
    private class ModuleCard extends WPressable implements AppleWidget {
        private final Module module;
        private final int index;
        private String desc = "", name = "", key = "";
        private String layoutFor; // keybind + favorite the cached text was fitted for; refit when they change
        private static final double KEY_SCALE = 0.72; // keybind labels are drawn at 72% text size
        private final Spring hover = new Spring(0), active, press = new Spring(0);
        private boolean wasPressed;
        private double clickX, clickY;
        private long clickNanos;

        ModuleCard(Module module, int index) {
            this.module = module;
            this.index = index;
            this.tooltip = module.description;
            this.active = new Spring(module.isActive() ? 1 : 0);
        }

        static double cardWidth(GundiTheme t) {
            return t.scale(168);
        }

        @Override
        protected void onCalculateSize() {
            GundiTheme t = theme();
            double pad = t.scale(8);
            width = cardWidth(t);
            height = pad * 2 + t.textHeight() * 2 + t.scale(4);
            layout();
        }

        /**
         * Fits the text into the card:
         *   Name ......................... [star] (light)
         *   Description that gets cut... [ RSHIFT ]
         * The keybind is a small chip on the description line (72% text size, short key names), so long names
         * like "Right Shift" or "Ctrl + Mouse 4" never squeeze the module name, and nothing can overlap.
         */
        private void layout() {
            GundiTheme t = theme();
            double pad = t.scale(8), gap = t.scale(6);
            double inner = width - pad * 2;

            key = module.keybind.isSet() ? shortKey(module.keybind.toString()) : "";
            double chipW = key.isEmpty() ? 0 : Math.min(inner * 0.45, keyWidth(t, key) + t.scale(8));
            if (!key.isEmpty() && keyWidth(t, key) + t.scale(8) > chipW) key = fitKey(t, key, chipW - t.scale(8));

            double light = t.scale(6), star = module.favorite ? t.textHeight() * 0.9 + t.scale(6) : 0;
            name = fit(module.title, inner - light - gap - star);
            String d = module.description == null ? "" : module.description.replace('\n', ' ');
            desc = fit(d, inner - (chipW > 0 ? chipW + gap : 0));
            layoutFor = module.keybind.toString() + module.favorite;
        }

        private double keyWidth(GundiTheme t, String s) {
            return t.textWidth(s) * KEY_SCALE;
        }

        private String fitKey(GundiTheme t, String s, double max) {
            int n = s.length();
            while (n > 1 && keyWidth(t, s.substring(0, n) + "..") > max) n--;
            return s.substring(0, n) + "..";
        }

        /** "Right Shift" -> "RSHIFT", "Left Control" -> "LCTRL", "Mouse 4" -> "M4", "Ctrl + K" -> "^K". */
        private static String shortKey(String k) {
            String s = k.replace("Ctrl + ", "^").replace("Cmd + ", "Cmd+").replace("Alt + ", "Alt+").replace("Shift + ", "Sh+")
                .replace("Caps Lock + ", "Caps+").replace("Num Lock + ", "Num+")
                .replace("Right ", "R").replace("Left ", "L").replace("Control", "Ctrl").replace("Mouse ", "M")
                .replace("Keypad ", "Num").replace("Backspace", "Bksp").replace("Delete", "Del").replace("Insert", "Ins")
                .replace("Page Up", "PgUp").replace("Page Down", "PgDn").replace("Caps Lock", "Caps").replace("Escape", "Esc");
            return s.toUpperCase(Locale.ROOT);
        }

        private String fit(String s, double max) {
            if (theme.textWidth(s) <= max) return s;
            String dots = "...";
            int n = s.length();
            while (n > 0 && theme.textWidth(s.substring(0, n) + dots) > max) n--;
            return s.substring(0, n).trim() + dots;
        }

        /** Meteor's buttons only react to left/right click; cards also take middle click (favorite). */
        @Override
        public boolean onMouseClicked(Click click, boolean doubled) {
            if (mouseOver && click.button() == GLFW_MOUSE_BUTTON_MIDDLE) {
                pressed = true;
                return true;
            }
            return super.onMouseClicked(click, doubled);
        }

        @Override
        protected void onPressed(int button) {
            if (button == GLFW_MOUSE_BUTTON_LEFT) module.toggle();
            else if (button == GLFW_MOUSE_BUTTON_RIGHT) mc.setScreen(theme.moduleScreen(module));
            else if (button == GLFW_MOUSE_BUTTON_MIDDLE) module.favorite = !module.favorite;
        }

        @Override
        protected void onRender(GuiRenderer renderer, double mouseX, double mouseY, double delta) {
            GundiTheme t = theme();
            double pad = t.scale(8), th = t.textHeight();

            // Pop in one after another when the list is (re)built
            double time = (System.nanoTime() - builtNanos) / 1e9 - index * 0.018 / t.animationSpeed.get();
            double p = Spring.step(time, t.response(0.45), t.damping());
            double fa = Math.max(0, Math.min(1, p));
            if (fa <= 0.001) return;

            t.spring(hover, mouseOver ? 1 : 0, delta, 0.2);
            t.spring(active, module.isActive() ? 1 : 0, delta, 0.3);
            t.spring(press, pressed ? 1 : 0, delta, 0.1);
            if (pressed && !wasPressed) {
                clickX = mouseX;
                clickY = mouseY;
                clickNanos = System.nanoTime();
            }
            wasPressed = pressed;

            double h = Math.max(0, Math.min(1, hover.value)), a = Math.max(0, Math.min(1, active.value));
            double shrink = t.clickEffect.get() == ClickEffect.Shrink ? 0.04 * press.value : 0;
            double cw = width * (1 - shrink), ch = height * (1 - shrink);
            double cx = x + (width - cw) / 2;
            double cy = y + (height - ch) / 2 + (1 - p) * t.scale(12) - t.scale(2) * h;
            double r = t.radius();
            Color accent = t.accent.get();

            // Card
            if (h > 0.01 || (a > 0.01 && t.glow.get())) {
                RoundRender.glow(renderer, cx, cy, cw, ch, r, t.scale(8), AppleWidget.alpha(accent, (0.35 * h + 0.35 * a * (t.glow.get() ? 1 : 0)) * fa));
            }
            Color bg = AppleWidget.mix(t.control.get(), t.controlHover.get(), h);
            RoundRender.rect(renderer, cx, cy, cw, ch, r, AppleWidget.alpha(bg, fa));
            if (a > 0.01) {
                RoundRender.hGradientRect(renderer, cx, cy, cw, ch, r, r, r, r,
                    AppleWidget.alpha(accent, 0.28 * a * fa), AppleWidget.alpha(t.accent2.get(), 0.12 * a * fa));
                RoundRender.border(renderer, cx, cy, cw, ch, r, Math.max(1, t.scale(1)), AppleWidget.alpha(accent, a * fa));
            }

            // Click effects
            double since = (System.nanoTime() - clickNanos) / 1e9 * t.animationSpeed.get();
            if (clickNanos != 0 && since < 0.5) {
                double k = since / 0.5;
                if (t.clickEffect.get() == ClickEffect.Ripple) {
                    double rr = Math.hypot(cw, ch) * (0.1 + 0.9 * (1 - Math.pow(1 - k, 3)));
                    renderer.scissorStart(cx, cy, cw, ch);
                    renderer.quad(clickX - rr, clickY - rr, rr * 2, rr * 2, GuiRenderer.CIRCLE, AppleWidget.alpha(t.text.get(), 0.2 * (1 - k)));
                    renderer.scissorEnd();
                } else if (t.clickEffect.get() == ClickEffect.Flash) {
                    RoundRender.rect(renderer, cx, cy, cw, ch, r, AppleWidget.alpha(t.accent2.get(), 0.5 * (1 - k) * (1 - k)));
                }
            }

            // On/off light (top right)
            double d = t.scale(6);
            double lx = cx + cw - pad - d, ly = cy + pad + th / 2 - d / 2;
            if (a > 0.01) renderer.quad(lx - d, ly - d, d * 3, d * 3, GuiRenderer.CIRCLE, AppleWidget.alpha(accent, 0.35 * a * fa));
            renderer.quad(lx, ly, d, d, GuiRenderer.CIRCLE, AppleWidget.alpha(AppleWidget.mix(t.switchOff.get(), accent, a), fa));

            // Keybind or favorite changed while open: refit the text
            if (!(module.keybind.toString() + module.favorite).equals(layoutFor)) layout();

            // Favorite star, left of the light
            if (module.favorite) {
                double st = th * 0.9;
                renderer.quad(lx - t.scale(6) - st, cy + pad + (th - st) / 2, st, st, GuiRenderer.FAVORITE_YES, AppleWidget.alpha(t.favorite.get(), fa));
            }

            // Name + description
            double descY = cy + pad + th + t.scale(4);
            Color nameColor = AppleWidget.mix(t.text.get(), AppleWidget.mix(t.text.get(), accent, 0.35), a);
            renderer.text(name, cx + pad, cy + pad, AppleWidget.alpha(nameColor, fa), false);
            renderer.text(desc, cx + pad, descY, AppleWidget.alpha(t.textSecondary.get(), 0.9 * fa), false);

            // Keybind chip: small label on the right of the description line, vertically centered on it
            if (!key.isEmpty()) {
                double kh = th * KEY_SCALE, kw = keyWidth(t, key) + t.scale(8), ch2 = kh + t.scale(4);
                double kx = cx + cw - pad - kw, ky = descY + (th - ch2) / 2;
                RoundRender.rect(renderer, kx, ky, kw, ch2, Math.min(ch2 / 2, t.radius() * 0.5), AppleWidget.alpha(t.field.get(), 0.85 * fa));
                RoundRender.border(renderer, kx, ky, kw, ch2, Math.min(ch2 / 2, t.radius() * 0.5), Math.max(1, t.scale(1)), AppleWidget.alpha(t.separator.get(), fa));
                Color kc = AppleWidget.alpha(t.textSecondary.get(), fa);
                String k = key;
                double tx = kx + t.scale(4), tyy = ky + (ch2 - kh) / 2;
                // GUI text is always full size, so the small label is drawn straight with the text renderer at the end of this section
                renderer.post(() -> {
                    TextRenderer tr = t.textRenderer();
                    tr.begin(t.scale(KEY_SCALE));
                    tr.render(k, tx, tyy, kc, false);
                    tr.end();
                });
            }
        }
    }
}
