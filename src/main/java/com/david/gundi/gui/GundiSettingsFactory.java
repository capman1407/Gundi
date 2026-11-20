package com.david.gundi.gui;

import meteordevelopment.meteorclient.gui.DefaultSettingsWidgetFactory;
import meteordevelopment.meteorclient.gui.GuiTheme;
import meteordevelopment.meteorclient.gui.widgets.WWidget;
import meteordevelopment.meteorclient.gui.widgets.containers.WVerticalList;
import meteordevelopment.meteorclient.settings.Settings;

/**
 * Meteor's settings builder, with more breathing room between groups. Used for every settings list shown
 * with a Gundi theme (modules, HUD elements, the theme itself). Each group becomes an AppleSection card
 * and each card's rows an AppleTable, which do the rest of the styling.
 */
public class GundiSettingsFactory extends DefaultSettingsWidgetFactory {
    public GundiSettingsFactory(GuiTheme theme) {
        super(theme);
    }

    @Override
    public WWidget create(GuiTheme theme, Settings settings, String filter) {
        WWidget widget = super.create(theme, settings, filter);
        if (widget instanceof WVerticalList list) list.spacing = 8;
        return widget;
    }
}
