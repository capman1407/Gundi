package com.david.gundi.util;

import meteordevelopment.meteorclient.renderer.MeshBuilder;
import meteordevelopment.meteorclient.renderer.MeshRenderer;
import meteordevelopment.meteorclient.renderer.MeteorRenderPipelines;
import meteordevelopment.meteorclient.renderer.text.CustomTextRenderer;
import meteordevelopment.meteorclient.renderer.text.Font;
import meteordevelopment.meteorclient.renderer.Fonts;
import meteordevelopment.meteorclient.systems.hud.Hud;
import meteordevelopment.meteorclient.systems.hud.HudRenderer;
import meteordevelopment.meteorclient.utils.render.color.Color;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;

import static meteordevelopment.meteorclient.MeteorClient.mc;

/**
 * Sharp HUD text at any size.
 *
 * Why: Meteor's HudRenderer.text(..., scale) bakes the font at 18 * scale pixels and then stretches it by
 * scale again with smooth filtering, so anything above scale 1 looks blurry.
 * Here the font is baked at exactly the pixel size it's shown at, drawn 1:1, on whole pixels.
 *
 * - Uses the same font you picked in Meteor's settings.
 * - If "custom-font" is off in Meteor's HUD settings, uses Minecraft's pixel font at a whole-number
 *   scale instead, which is always sharp.
 *
 * "size" everywhere = text height in pixels.
 */
public final class CrispText {
    private static final int MAX_FONTS = 16; // each baked font is a 2048x2048 texture (~4 MB)

    // Fonts by pixel height, least recently used first (access order).
    private static final LinkedHashMap<Integer, Font> fonts = new LinkedHashMap<>(32, 0.75f, true);
    private static CustomTextRenderer fontsMadeFrom; // when the user picks another font in Meteor, rebuild
    private static MeshBuilder mesh;

    private CrispText() {}

    public static double width(HudRenderer r, String text, double size) {
        if (!useCustom()) return r.textWidth(text, false, vanillaScale(size));
        return font(r, size).getWidth(text, text.length());
    }

    public static double height(HudRenderer r, double size) {
        if (!useCustom()) return r.textHeight(false, vanillaScale(size));
        return font(r, size).getHeight();
    }

    /** Draws text with its top-left corner at x, y. Returns the x where the text ends. */
    public static double draw(HudRenderer r, String text, double x, double y, Color color, boolean shadow, double size) {
        // Whole pixels: a font texture drawn half a pixel off gets smeared by the GPU.
        double px = Math.round(x), py = Math.round(y);

        // Skip nearly invisible text: Minecraft's text renderer treats alpha < 4 as fully solid, which would flash.
        if (color.a < 5) return px + width(r, text, size);

        if (!useCustom()) return r.text(text, px, py, color, shadow, vanillaScale(size));

        Font font = font(r, size);
        Color c = new Color(color);
        int shadowOffset = Math.max(1, (int) Math.round(size / 18));
        Color shadowColor = new Color(0, 0, 0, (int) (c.a * 0.55));

        // Run after HudRenderer has drawn all the HUD's boxes and lines, so the text sits on top of our background.
        r.post(() -> {
            // This runs inside Meteor's HUD code, outside the HUD elements' own try/catch, so it needs its own:
            // without it, any problem here would crash the whole game instead of just skipping this text.
            try {
                if (mesh == null) mesh = new MeshBuilder(MeteorRenderPipelines.UI_TEXT);
                if (mesh.isBuilding()) mesh.end(); // left open by an earlier failure: reset it
                mesh.begin();
                if (shadow) font.render(mesh, text, px + shadowOffset, py + shadowOffset, shadowColor, 1);
                font.render(mesh, text, px, py, c, 1);
                mesh.end();

                MeshRenderer.begin()
                    .attachments(mc.getFramebuffer())
                    .pipeline(MeteorRenderPipelines.UI_TEXT)
                    .mesh(mesh)
                    .sampler("u_Texture", font.texture.getGlTextureView(), font.texture.getSampler())
                    .end();
            } catch (Throwable e) {
                ErrorReporter.report("crisp-text", "draw", e);
            }
        });

        return px + font.getWidth(text, text.length());
    }

    private static boolean useCustom() {
        return Hud.get().hasCustomFont() && Fonts.RENDERER != null;
    }

    /** Minecraft's pixel font is 9px tall and only sharp at whole multiples; HudRenderer doubles the scale it's given. */
    private static double vanillaScale(double size) {
        return Math.max(1, Math.round(size / 9)) / 2.0;
    }

    private static Font font(HudRenderer r, double size) {
        if (fontsMadeFrom != Fonts.RENDERER) {
            for (Font f : fonts.values()) closeLater(r, f);
            fonts.clear();
            fontsMadeFrom = Fonts.RENDERER;
        }

        // Nearby sizes share one font (whole pixels below 20px, steps of 2px above), so fewer fonts are needed.
        int px = (int) Math.round(size);
        if (px > 20) px = Math.round(px / 2f) * 2;
        px = Math.max(6, px);

        Font font = fonts.get(px);
        if (font == null) {
            try {
                font = new Font(Fonts.RENDERER.fontFace.readToDirectByteBuffer(), px);
            } catch (Exception e) {
                throw new RuntimeException("Could not load font " + Fonts.RENDERER.fontFace + " at " + px + "px", e);
            }
            fonts.put(px, font);

            // Too many fonts: forget the least recently used one.
            if (fonts.size() > MAX_FONTS) {
                Iterator<Map.Entry<Integer, Font>> it = fonts.entrySet().iterator();
                Font eldest = it.next().getValue();
                it.remove();
                closeLater(r, eldest);
            }
        }
        return font;
    }

    /**
     * Frees a font's texture, but only AFTER this frame's queued text has been drawn.
     * Closing it straight away crashed the game: text using that font was still waiting to be drawn
     * ("Texture view does not exist"). Tasks given to r.post() run in order, so this runs after them.
     */
    private static void closeLater(HudRenderer r, Font f) {
        r.post(() -> {
            try {
                f.texture.close();
            } catch (Throwable e) {
                ErrorReporter.report("crisp-text", "free font", e);
            }
        });
    }
}
