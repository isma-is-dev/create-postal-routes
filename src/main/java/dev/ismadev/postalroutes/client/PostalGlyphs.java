package dev.ismadev.postalroutes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Tiny procedurally drawn glyphs. The mod ships no textures for map symbols, which keeps
 * the jar small and the icons crisp at any map zoom.
 *
 * <p>Port icons are deliberately 3x2 so a dense postal network stays readable instead of
 * covering the terrain; the GUI button draws the same glyph at double scale.</p>
 */
public final class PostalGlyphs {

    private static final String[] ENVELOPE = {
        "111",
        "101"
    };

    private static final int ENVELOPE_WIDTH = 3;
    private static final int ENVELOPE_HEIGHT = 2;

    /** 1px translucent dark frame around the glyph. */
    private static final int FRAME_COLOR = 0xB0100C04;

    private PostalGlyphs() {
    }

    /**
     * Draws the compact envelope icon centred on the given map position (1 block = 1 unit).
     */
    public static void envelope(GuiGraphics graphics, double x, double y, int color) {
        envelope(graphics, x, y, color, 1);
    }

    /**
     * Same glyph at an arbitrary pixel scale, used by the map button.
     */
    public static void envelope(GuiGraphics graphics, double x, double y, int color, int scale) {
        int pixel = Math.max(1, scale);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x - ENVELOPE_WIDTH * pixel / 2.0D, y - ENVELOPE_HEIGHT * pixel / 2.0D, 0.0D);
        pose.scale(pixel, pixel, 1.0F);
        // A thin translucent frame instead of a solid card: it outlines the icon without
        // making it look wider than the block it sits on.
        graphics.fill(-1, -1, ENVELOPE_WIDTH + 1, ENVELOPE_HEIGHT + 1, FRAME_COLOR);
        for (int row = 0; row < ENVELOPE.length; row++) {
            String line = ENVELOPE[row];
            for (int col = 0; col < ENVELOPE_WIDTH; col++) {
                if (line.charAt(col) == '1') {
                    graphics.fill(col, row, col + 1, row + 1, color);
                }
            }
        }
        pose.popPose();
    }

    /**
     * Draws a package marker: a filled square with a darker rim.
     */
    public static void box(GuiGraphics graphics, double x, double y, int size, int fill, int outline) {
        int half = size / 2;
        int x0 = (int) Math.round(x) - half;
        int y0 = (int) Math.round(y) - half;
        graphics.fill(x0 - 1, y0 - 1, x0 + size + 1, y0 + size + 1, outline);
        graphics.fill(x0, y0, x0 + size, y0 + size, fill);
    }

    public static int width() {
        return ENVELOPE_WIDTH;
    }

    public static int height() {
        return ENVELOPE_HEIGHT;
    }
}
