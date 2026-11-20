package dev.ismadev.postalroutes.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import dev.ismadev.postalroutes.PostalConfig;
import dev.ismadev.postalroutes.PostalRoutes;
import dev.ismadev.postalroutes.data.PortKind;
import dev.ismadev.postalroutes.data.PostalPackage;
import dev.ismadev.postalroutes.data.PostalPort;
import dev.ismadev.postalroutes.mixin.GuiMapAccessor;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.core.Registry;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.dimension.DimensionType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;
import xaero.map.world.MapDimension;
import xaero.map.world.MapWorld;

import java.util.ArrayList;
import java.util.List;

/**
 * Draws the postal layer on Xaero's World Map.
 *
 * <p>The transform replicates exactly what Xaero does for the map camera (screen centre,
 * map scale, camera offset, dimension scaling), which is what keeps routes and packages
 * glued to the terrain at every zoom level instead of drifting.</p>
 */
public final class PostalMapOverlay {

    private static final double PICK_RADIUS = 4.0D;

    private static boolean failed;

    private PostalMapOverlay() {
    }

    public static void onRender(GuiGraphics graphics, GuiMap map, int mouseX, int mouseY, float partialTicks) {
        if (failed) {
            return;
        }
        try {
            GuiMapAccessor accessor = (GuiMapAccessor) map;
            MapProcessor processor = accessor.postal$getMapProcessor();
            MapWorld mapWorld = processor == null ? null : processor.getMapWorld();
            MapDimension mapDimension = mapWorld == null ? null : mapWorld.getCurrentDimension();
            if (mapDimension == null) {
                return;
            }

            ResourceKey<Level> dimension = mapDimension.getDimId();
            if (dimension == null) {
                return;
            }
            PostalClientState.viewedDimension = dimension;

            PostalClientState.DimData data = PostalClientState.getIfPresent(dimension);
            int packageCount = data == null ? 0 : data.dots.size();
            PostalMapWidget.render(graphics, mouseX, mouseY, packageCount);

            if (!PostalMapWidget.isEnabled()) {
                return;
            }
            if (data == null || data.isEmpty()) {
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            double cameraX = accessor.postal$getCameraX();
            double cameraZ = accessor.postal$getCameraZ();
            double mapScale = accessor.postal$getScale();
            if (!Double.isFinite(mapScale) || mapScale <= 0.0D) {
                return;
            }

            // Xaero's `scale` is window pixels per block. GUI space divides that by the
            // GUI scale, which is what the pose below works in. Using getGuiScale()
            // instead of framebuffer/window ratios matters: those are 0 while the window
            // is minimised, which used to turn the whole transform into NaN.
            double guiScale = mc.getWindow().getGuiScale();
            if (!Double.isFinite(guiScale) || guiScale <= 0.0D) {
                guiScale = 1.0D;
            }
            double scale = mapScale / guiScale;
            if (!Double.isFinite(scale) || scale <= 0.0D) {
                return;
            }
            double factor = dimensionDivisor(processor, mapDimension, mc);
            if (!Double.isFinite(factor) || factor < 1.0E-4D) {
                factor = 1.0D;
            }

            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate((float) map.width / 2.0F, (float) map.height / 2.0F, 0.0F);
            pose.scale((float) scale, (float) scale, 1.0F);
            pose.translate(-cameraX, -cameraZ, 0.0D);

            Rect2i bounds = visibleBounds(map.width, map.height, scale, cameraX, cameraZ);
            double mouseMapX = (mouseX - (double) map.width / 2.0D) / scale + cameraX;
            double mouseMapZ = (mouseY - (double) map.height / 2.0D) / scale + cameraZ;

            int opacity = Mth.clamp(PostalConfig.routeOpacity(), 0, 100);
            float alpha = opacity / 100.0F;

            // Always run ensure(), even when every layer is off: that is what clears the
            // cached raster. Otherwise the last drawn routes would stay on screen.
            PostalTextureCache.INSTANCE.ensure(dimension, data, factor,
                PostalConfig.showRoutes(), PostalConfig.showPorts(), PostalConfig.lineTheme());
            PostalTextureCache.INSTANCE.render(graphics, bounds, alpha);

            RenderSystem.enableBlend();
            Picked picked = null;
            if (PostalConfig.showPorts()) {
                picked = drawPorts(graphics, data, factor, scale, bounds, mouseMapX, mouseMapZ, picked);
            }
            if (PostalConfig.showPackages()) {
                picked = drawPackages(graphics, data, factor, scale, bounds, mouseMapX, mouseMapZ, picked);
            }

            pose.popPose();
            graphics.flush();

            if (picked != null) {
                List<Component> tooltip = tooltipFor(picked);
                if (!tooltip.isEmpty()) {
                    graphics.renderComponentTooltip(mc.font, tooltip, mouseX, mouseY);
                }
            }
        } catch (Throwable t) {
            failed = true;
            PostalRoutes.LOGGER.error("Disabling the postal map overlay after an unexpected error "
                + "(this never crashes the game, but the overlay will stay off until restart)", t);
        }
    }

    // ------------------------------------------------------------------
    // Transform helpers
    // ------------------------------------------------------------------

    private static Rect2i visibleBounds(int width, int height, double scale, double cameraX, double cameraZ) {
        int margin = 32;
        int x = Mth.floor((double) (-width) / 2.0D / scale + cameraX) - margin;
        int z = Mth.floor((double) (-height) / 2.0D / scale + cameraZ) - margin;
        int w = Mth.floor((double) width / scale) + margin * 2;
        int h = Mth.floor((double) height / scale) + margin * 2;
        return new Rect2i(x, z, w, h);
    }

    /**
     * Xaero stores map coordinates divided by the ratio between the viewed dimension's
     * coordinate scale and the player's. Using Xaero's own calculation keeps the Nether
     * correct instead of drifting by a factor of eight.
     */
    private static double dimensionDivisor(MapProcessor processor, MapDimension mapDimension, Minecraft mc) {
        try {
            Registry<DimensionType> registry = processor.getWorldDimensionTypeRegistry();
            if (registry != null && mc.player != null) {
                double div = mapDimension.calculateDimDiv(registry, mc.player.level().dimensionType());
                if (div > 1.0E-4D) {
                    return div;
                }
            }
        } catch (Throwable ignored) {
            // Fall through to the identity; better to draw unshifted than not at all.
        }
        return 1.0D;
    }

    // ------------------------------------------------------------------
    // Symbols
    // ------------------------------------------------------------------

    @Nullable
    private static Picked drawPorts(GuiGraphics graphics, PostalClientState.DimData data, double factor,
                                    double scale, Rect2i bounds, double mouseMapX, double mouseMapZ,
                                    @Nullable Picked picked) {
        Picked result = picked;
        boolean drawIcons = scale >= 0.35D;

        for (PostalPort port : data.ports) {
            double x = port.x() / factor;
            double z = port.z() / factor;
            if (!bounds.contains(Mth.floor(x), Mth.floor(z))) {
                continue;
            }

            int color = portColor(port);
            if (drawIcons) {
                PostalGlyphs.envelope(graphics, x, z, color);
            } else {
                PostalGlyphs.box(graphics, x, z, 2, color, 0x90000000);
            }

            if (result == null
                && Math.abs(mouseMapX - x) <= PICK_RADIUS
                && Math.abs(mouseMapZ - z) <= PICK_RADIUS) {
                result = new Picked(port, null);
            }
        }
        return result;
    }

    @Nullable
    private static Picked drawPackages(GuiGraphics graphics, PostalClientState.DimData data, double factor,
                                       double scale, Rect2i bounds, double mouseMapX, double mouseMapZ,
                                       @Nullable Picked picked) {
        Picked result = picked;
        long now = System.currentTimeMillis();
        // If the server stopped streaming (map reopened, server lagging) the last known
        // positions are stale; better to show nothing than dots frozen in the wrong place.
        if (now - data.lastPacketMillis > 5_000L) {
            return result;
        }
        boolean showEntities = PostalConfig.showEntityPackages();
        int size = Mth.clamp((int) Math.round(2.6D / Math.max(scale, 0.05D)), 2, 9);

        for (PostalClientState.Dot dot : data.dots.values()) {
            if (dot.kind() == PostalPackage.KIND_ENTITY && !showEntities) {
                continue;
            }
            Vec3 position = dot.sample(now);
            double x = position.x / factor;
            double z = position.z / factor;
            if (!bounds.contains(Mth.floor(x), Mth.floor(z))) {
                continue;
            }

            int fill = dot.kind() == PostalPackage.KIND_ENTITY ? 0xFFFFFFFF : 0xFFFFCE6A;
            PostalGlyphs.box(graphics, x, z, size, fill, 0xC0100C04);

            if (result == null
                && Math.abs(mouseMapX - x) <= PICK_RADIUS
                && Math.abs(mouseMapZ - z) <= PICK_RADIUS) {
                result = new Picked(null, dot);
            }
        }

        return result;
    }

    private static int portColor(PostalPort port) {
        return switch (port.kind()) {
            case PortKind.POSTBOX -> 0xFF7FD8C6;
            case PortKind.STATION -> 0xFFE8F0FF;
            default -> 0xFFF0B44A;
        };
    }

    // ------------------------------------------------------------------
    // Tooltips
    // ------------------------------------------------------------------

    private static List<Component> tooltipFor(Picked picked) {
        List<Component> lines = new ArrayList<>(4);

        if (picked.port() != null) {
            PostalPort port = picked.port();
            lines.add(Component.translatable(PortKind.translationKey(port.kind())).withStyle(ChatFormatting.GOLD));
            if (!port.address().isBlank()) {
                lines.add(Component.literal("#" + port.address()).withStyle(ChatFormatting.YELLOW));
            } else {
                lines.add(Component.translatable("postalroutes.tooltip.no_address")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
            lines.add(Component.translatable(port.accepting()
                    ? "postalroutes.tooltip.accepting"
                    : "postalroutes.tooltip.rejecting")
                .withStyle(port.accepting() ? ChatFormatting.GREEN : ChatFormatting.RED));
            return lines;
        }

        PostalClientState.Dot dot = picked.packageDot();
        if (dot != null) {
            boolean entity = dot.kind() == PostalPackage.KIND_ENTITY;
            lines.add(Component.translatable(entity
                    ? "postalroutes.tooltip.loose_package"
                    : "postalroutes.tooltip.package")
                .withStyle(ChatFormatting.GOLD));
            if (!dot.address().isBlank()) {
                lines.add(Component.literal("-> #" + dot.address()).withStyle(ChatFormatting.YELLOW));
            } else {
                lines.add(Component.translatable("postalroutes.tooltip.no_address")
                    .withStyle(ChatFormatting.DARK_GRAY));
            }
        }

        return lines;
    }

    private record Picked(@Nullable PostalPort port, @Nullable PostalClientState.Dot packageDot) {
    }
}
