package dev.ismadev.postalroutes.client;

import dev.ismadev.postalroutes.PostalConfig;
import dev.ismadev.postalroutes.PostalRoutes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

/**
 * Small toggle panel drawn on Xaero's World Map, right below Create's own train map
 * toggle (which lives at 3,30 and is 33x14).
 */
public final class PostalMapWidget {

    public static final int X = 3;
    public static final int Y = 48;
    public static final int WIDTH = 58;
    public static final int HEIGHT = 14;

    private static final int BACKGROUND_ON = 0xE01C1408;
    private static final int BACKGROUND_OFF = 0xE0181818;
    private static final int BORDER_ON = 0xFFE8AA44;
    private static final int BORDER_OFF = 0xFF5E5E5E;
    private static final int TEXT_ON = 0xFFFFE7B8;
    private static final int TEXT_OFF = 0xFF9A9A9A;

    private PostalMapWidget() {
    }

    public static boolean isEnabled() {
        try {
            return PostalConfig.overlayEnabled();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public static void render(GuiGraphics graphics, int mouseX, int mouseY, int packageCount) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        boolean enabled = isEnabled();
        boolean hovered = isHovered(mouseX, mouseY);

        int background = enabled ? BACKGROUND_ON : BACKGROUND_OFF;
        int border = enabled ? (hovered ? 0xFFFFD27A : BORDER_ON) : (hovered ? 0xFF8A8A8A : BORDER_OFF);

        graphics.fill(X, Y, X + WIDTH, Y + HEIGHT, background);
        graphics.fill(X, Y, X + WIDTH, Y + 1, border);
        graphics.fill(X, Y + HEIGHT - 1, X + WIDTH, Y + HEIGHT, border);
        graphics.fill(X, Y, X + 1, Y + HEIGHT, border);
        graphics.fill(X + WIDTH - 1, Y, X + WIDTH, Y + HEIGHT, border);

        PostalGlyphs.envelope(graphics, X + 9, Y + 7,
            enabled ? 0xFFFFD98A : 0xFF7A7A7A, enabled ? 0xFF2A1C08 : 0xFF101010);

        Component label = Component.translatable("postalroutes.widget.label");
        graphics.drawString(font, label, X + 14, Y + 3, enabled ? TEXT_ON : TEXT_OFF, false);

        if (enabled && packageCount > 0) {
            String count = packageCount > 99 ? "99+" : Integer.toString(packageCount);
            int width = font.width(count);
            graphics.drawString(font, count, X + WIDTH - width - 4, Y + 3, 0xFFE8AA44, false);
        }

        if (hovered) {
            graphics.renderComponentTooltip(font, tooltip(enabled), mouseX, mouseY);
        }
    }

    private static List<Component> tooltip(boolean enabled) {
        return List.of(
            Component.translatable("postalroutes.widget.title").withStyle(ChatFormatting.GOLD),
            Component.translatable(enabled ? "postalroutes.widget.on" : "postalroutes.widget.off")
                .withStyle(enabled ? ChatFormatting.GREEN : ChatFormatting.GRAY),
            Component.translatable("postalroutes.widget.hint").withStyle(ChatFormatting.DARK_GRAY)
        );
    }

    public static boolean isHovered(double mouseX, double mouseY) {
        return mouseX >= X && mouseX < X + WIDTH && mouseY >= Y && mouseY < Y + HEIGHT;
    }

    /** @return true if the click was consumed by the widget. */
    public static boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button != 0 || !isHovered(mouseX, mouseY)) {
            return false;
        }
        toggle();
        return true;
    }

    public static void toggle() {
        try {
            boolean value = !PostalConfig.overlayEnabled();
            PostalConfig.CLIENT.enabled.set(value);
        } catch (Throwable t) {
            PostalRoutes.LOGGER.warn("Could not toggle the postal overlay", t);
        }
    }
}
