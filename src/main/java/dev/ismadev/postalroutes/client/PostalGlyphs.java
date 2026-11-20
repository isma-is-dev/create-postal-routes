package dev.ismadev.postalroutes.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.gui.GuiGraphics;

/**
 * Tiny procedurally drawn glyphs. The mod ships no textures for map symbols, which keeps
 * the jar small and the icons crisp at any map zoom.
 */
public final class PostalGlyphs {

    private static final String[] ENVELOPE = {
        "1111111",
        "1100011",
        "1010101",
        "1001001",
        "1111111"
    };

    private static final int ENVELOPE_WIDTH = 7;
    private static final int ENVELOPE_HEIGHT = 5;

    private PostalGlyphs() {
    }

    /**
     * Draws a small envelope icon with a dark backing card so it stays readable over any
     * terrain colour. Coordinates are in map space (1 block = 1 unit).
     */
    public static void envelope(GuiGraphics graphics, double x, double y, int color, int outline) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x - 0.5D, y - 0.5D, 0.0D);
        graphics.fill(-1, -1, ENVELOPE_WIDTH + 1, ENVELOPE_HEIGHT + 1, outline);
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
