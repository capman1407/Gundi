package com.david.gundi.mixin;

import meteordevelopment.meteorclient.gui.WidgetScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Lets us read Meteor's private "closing" flag, so windows know when to play their closing animation. */
@Mixin(value = WidgetScreen.class, remap = false)
public interface WidgetScreenAccessor {
    @Accessor("closing")
    boolean addon$isClosing();
}
