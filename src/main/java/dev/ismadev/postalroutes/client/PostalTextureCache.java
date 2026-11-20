package dev.ismadev.postalroutes.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.ismadev.postalroutes.PostalConfig;
import dev.ismadev.postalroutes.PostalRoutes;
import dev.ismadev.postalroutes.data.PostalEdge;
import dev.ismadev.postalroutes.data.PostalPort;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FastColor.ABGR32;
import net.minecraft.util.Mth;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

/**
 * Rasterises postal routes into 128x128 dynamic textures, one per 128x128 block map
 * section. This is the same technique Create uses for its train map: it keeps the
 * overlay perfectly aligned with the map (one texture pixel is one block) and lets the
 * GPU draw thousands of route pixels for the cost of a handful of quads.
 */
public final class PostalTextureCache {

    public static final PostalTextureCache INSTANCE = new PostalTextureCache();

    public static final int TILE = 128;

    private final Map<Long, Tile> tiles = new HashMap<>();
    private int signature;
    private boolean built;
    private boolean warnedCap;

    private PostalTextureCache() {
    }

    // ------------------------------------------------------------------
    // Public API
    // ------------------------------------------------------------------

    public void ensure(net.minecraft.resources.ResourceKey<net.minecraft.world.level.Level> dimension,
                       PostalClientState.DimData data, double factor,
                       int minBuildHeight, boolean showRoutes, boolean showPorts,
                       PostalConfig.LineTheme theme) {
        int newSignature = Objects.hash(dimension, data.revision, data.hash, Double.hashCode(factor),
            theme.ordinal(), showRoutes, showPorts);
        if (built && newSignature == signature) {
            return;
        }
        signature = newSignature;
        built = true;

        try {
            rebuild(data, factor, minBuildHeight, showRoutes, showPorts, theme);
        } catch (Throwable t) {
            PostalRoutes.LOGGER.error("Failed to rasterise the postal map layer; disabling it for this session", t);
            clear();
            built = false;
        }
    }

    public void render(GuiGraphics graphics, Rect2i bounds, float alpha) {
        if (tiles.isEmpty()) {
            return;
        }
        RenderSystem.enableBlend();
        graphics.setColor(1.0F, 1.0F, 1.0F, alpha);
        for (Tile tile : tiles.values()) {
            int x = tile.sectionX * TILE;
            int z = tile.sectionZ * TILE;
            if (tile.isOutside(bounds)) {
                continue;
            }
            graphics.blit(tile.location, x, z, 0.0F, 0.0F, TILE, TILE, TILE, TILE);
        }
        graphics.setColor(1.0F, 1.0F, 1.0F, 1.0F);
        graphics.flush();
    }

    public void clear() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        for (Tile tile : tiles.values()) {
            tile.untouched = true;
            textureManager.release(tile.location);
        }
        tiles.clear();
    }

    // ------------------------------------------------------------------
    // Rasterisation
    // ------------------------------------------------------------------

    private void rebuild(PostalClientState.DimData data, double factor, int minBuildHeight,
                         boolean showRoutes, boolean showPorts, PostalConfig.LineTheme theme) {
        for (Tile tile : tiles.values()) {
            tile.untouched = true;
        }
        warnedCap = false;

        // Full strength here; the configured opacity is applied at draw time, so changing
        // it never requires rebuilding the textures.
        int baseAlpha = 255;

        int route = routeColor(theme);
        int shadow = ABGR32.color(Math.min(150, baseAlpha / 2), 14, 10, 4);

        if (showRoutes) {
            for (PostalEdge edge : data.edges) {
                drawEdge(edge, factor, minBuildHeight, baseAlpha, route, shadow);
            }
        }

        if (showPorts) {
            int portColor = portColor(theme);
            for (PostalPort port : data.ports) {
                int x = Mth.floor(port.x() / factor);
                int z = Mth.floor(port.z() / factor);
                setPixel(x, z, ABGR32.color(baseAlpha, 20, 14, 6));
                setPixel(x + 1, z, ABGR32.color(baseAlpha, 20, 14, 6));
                setPixel(x, z + 1, ABGR32.color(baseAlpha, 20, 14, 6));
                setPixel(x + 1, z + 1, ABGR32.color(baseAlpha, 20, 14, 6));
                setPixel(x, z, ABGR32.color(baseAlpha, portColor));
            }
        }

        prune();
    }

    private void drawEdge(PostalEdge edge, double factor, int minBuildHeight, int baseAlpha,
                          int route, int shadow) {
        double x1 = edge.startX() / factor;
        double z1 = edge.startZ() / factor;
        double x2 = edge.endX() / factor;
        double z2 = edge.endZ() / factor;

        // Height is encoded in opacity, exactly like Create's train map: the higher a
        // strand runs, the more solid it looks, so crossing routes stay readable.
        float height = (float) Mth.clamp((edge.startY() - minBuildHeight) / 320.0F, 0.0F, 1.0F);
        int alpha = Math.round(baseAlpha * (0.62F + 0.38F * height));
        int main = ABGR32.color(alpha, shade(route, 0.82F + 0.18F * height));

        boolean dashed = PostalConfig.dashedRoutes();
        rasterLine(x1, z1, x2, z2, shadow, false, true);
        rasterLine(x1, z1, x2, z2, main, dashed, false);
    }

    /**
     * Bresenham line in map pixels.
     *
     * @param color   packed colour
     * @param dashed  leave gaps so postal routes never read as train tracks
     * @param shadow  draw one row below, which is where the dark backdrop goes
     */
    private void rasterLine(double x1, double z1, double x2, double z2, int color,
                            boolean dashed, boolean shadow) {
        int ix1 = Mth.floor(x1);
        int iz1 = Mth.floor(z1);
        int ix2 = Mth.floor(x2);
        int iz2 = Mth.floor(z2);

        int dx = Math.abs(ix2 - ix1);
        int dz = Math.abs(iz2 - iz1);
        int sx = ix1 < ix2 ? 1 : -1;
        int sz = iz1 < iz2 ? 1 : -1;
        int err = dx - dz;
        int step = 0;

        while (true) {
            boolean draw = !dashed || (step % 4) != 3;
            if (draw) {
                setPixel(ix1, shadow ? iz1 + 1 : iz1, color);
            }
            if (ix1 == ix2 && iz1 == iz2) {
                break;
            }
            int e2 = 2 * err;
            if (e2 > -dz) {
                err -= dz;
                ix1 += sx;
            }
            if (e2 < dx) {
                err += dx;
                iz1 += sz;
            }
            step++;
        }
    }

    private void setPixel(int x, int z, int argb) {
        Tile tile = getOrCreate(Math.floorDiv(x, TILE), Math.floorDiv(z, TILE));
        if (tile == null) {
            return;
        }
        int localX = Math.floorMod(x, TILE);
        int localZ = Math.floorMod(z, TILE);
        NativeImage image = tile.image();
        if (image != null) {
            image.setPixelRGBA(localX, localZ, argb);
        }
    }

    private Tile getOrCreate(int sectionX, int sectionZ) {
        long key = ((long) sectionX << 32) ^ (sectionZ & 0xFFFFFFFFL);
        Tile existing = tiles.get(key);
        if (existing != null) {
            existing.untouched = false;
            return existing;
        }

        int maxSections = PostalConfig.maxMapSections();
        if (tiles.size() >= maxSections) {
            if (!warnedCap) {
                warnedCap = true;
                PostalRoutes.LOGGER.warn("Postal map layer hit the {} section cap; "
                    + "raise client.maxMapSections if your network is bigger than that.", maxSections);
            }
            return null;
        }

        Tile tile = new Tile(sectionX, sectionZ);
        tiles.put(key, tile);
        return tile;
    }

    private void prune() {
        TextureManager textureManager = Minecraft.getInstance().getTextureManager();
        tiles.values().removeIf(tile -> {
            if (!tile.untouched) {
                return false;
            }
            textureManager.release(tile.location);
            return true;
        });
    }

    private static int routeColor(PostalConfig.LineTheme theme) {
        return switch (theme) {
            case COPPER -> 0xD67C4A;
            case TEAL -> 0x60C4B4;
            default -> 0xE8AA44;
        };
    }

    private static int portColor(PostalConfig.LineTheme theme) {
        return switch (theme) {
            case COPPER -> 0xF0A070;
            case TEAL -> 0xA8F0E4;
            default -> 0xFFD98A;
        };
    }

    private static int shade(int rgb, float factor) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((rgb >> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((rgb & 0xFF) * factor));
        return (r << 16) | (g << 8) | b;
    }

    // ------------------------------------------------------------------
    // Tiles
    // ------------------------------------------------------------------

    private static final class Tile {

        private final int sectionX;
        private final int sectionZ;
        private final DynamicTexture texture;
        private final ResourceLocation location;
        private boolean untouched;

        private Tile(int sectionX, int sectionZ) {
            this.sectionX = sectionX;
            this.sectionZ = sectionZ;
            this.texture = new DynamicTexture(TILE, TILE, true);
            // Linear minification keeps the lines readable when zoomed out; nearest
            // magnification keeps them crisp when zoomed in.
            this.texture.setFilter(true, false);
            TextureManager textureManager = Minecraft.getInstance().getTextureManager();
            this.location = textureManager.register(
                "postalroutes_map/" + sectionX + "_" + sectionZ, texture);
            this.untouched = false;
        }

        private NativeImage image() {
            this.untouched = false;
            return texture.getPixels();
        }

        private boolean isOutside(Rect2i bounds) {
            int minX = sectionX * TILE;
            int minZ = sectionZ * TILE;
            return minX + TILE < bounds.getX()
                || minX > bounds.getX() + bounds.getWidth()
                || minZ + TILE < bounds.getY()
                || minZ > bounds.getY() + bounds.getHeight();
        }
    }
}
