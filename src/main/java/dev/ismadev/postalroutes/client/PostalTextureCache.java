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
                       boolean showRoutes, boolean showPorts,
                       PostalConfig.LineTheme theme) {
        int newSignature = Objects.hash(dimension, data.revision, data.hash, Double.hashCode(factor),
            theme.ordinal(), showRoutes, showPorts);
        if (built && newSignature == signature) {
            return;
        }
        signature = newSignature;
        built = true;

        try {
            rebuild(data, factor, showRoutes, showPorts, theme);
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
            if (tile.isOutside(bounds)) {
                continue;
            }
            int x = tile.sectionX * TILE;
            int z = tile.sectionZ * TILE;
            tile.uploadIfNeeded();
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

    private void rebuild(PostalClientState.DimData data, double factor,
                         boolean showRoutes, boolean showPorts, PostalConfig.LineTheme theme) {
        for (Tile tile : tiles.values()) {
            tile.untouched = true;
        }
        warnedCap = false;

        int route = routeColor(theme);
        int main = abgr(255, route);
        int halo = abgr(170, 0x080E1A);

        if (showRoutes) {
            // Halo first for every edge, then the solid line on top: otherwise a later
            // edge's outline would bite into an earlier one at crossings.
            for (PostalEdge edge : data.edges) {
                rasterEdge(edge, factor, halo, true);
            }
            for (PostalEdge edge : data.edges) {
                rasterEdge(edge, factor, main, false);
            }
        }

        if (showPorts) {
            int portColor = abgr(255, portColor(theme));
            int portOutline = abgr(170, 0x080E1A);
            for (PostalPort port : data.ports) {
                int x = Mth.floor(port.x() / factor);
                int z = Mth.floor(port.z() / factor);
                // 2x2 marker so ports stay visible when the map is zoomed out.
                setPixel(x - 1, z - 1, portOutline);
                setPixel(x + 2, z - 1, portOutline);
                setPixel(x - 1, z + 2, portOutline);
                setPixel(x + 2, z + 2, portOutline);
                setPixel(x, z, portColor);
                setPixel(x + 1, z, portColor);
                setPixel(x, z + 1, portColor);
                setPixel(x + 1, z + 1, portColor);
            }
        }

        prune();
    }

    private void rasterEdge(PostalEdge edge, double factor, int color, boolean halo) {
        boolean dashed = PostalConfig.dashedRoutes();
        rasterLine(edge.startX() / factor, edge.startZ() / factor,
            edge.endX() / factor, edge.endZ() / factor, color, dashed, halo);
    }

    /**
     * Bresenham line in map pixels.
     *
     * @param color  packed colour
     * @param dashed leave gaps so postal routes never read as train tracks
     * @param halo   expand every pixel to a 3x3 block, which produces the dark border
     */
    private void rasterLine(double x1, double z1, double x2, double z2, int color,
                            boolean dashed, boolean halo) {
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
            // 5 pixels on, 3 off: long enough to read as a route, short enough to be
            // clearly different from Create's solid train lines.
            boolean draw = !dashed || (step % 8) < 5;
            if (draw) {
                if (halo) {
                    for (int ox = -1; ox <= 1; ox++) {
                        for (int oz = -1; oz <= 1; oz++) {
                            setPixel(ix1 + ox, iz1 + oz, color);
                        }
                    }
                } else {
                    setPixel(ix1, iz1, color);
                }
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

    /** Packs an 0xRRGGBB colour plus alpha into the ABGR layout NativeImage expects. */
    private static int abgr(int alpha, int rgb) {
        return ABGR32.fromArgb32((alpha << 24) | (rgb & 0xFFFFFF));
    }
    private static int routeColor(PostalConfig.LineTheme theme) {
        return switch (theme) {
            case AMBER -> 0xE8AA44;
            case TEAL -> 0x2FBFAE;
            default -> 0x3D7EFF;
        };
    }

    private static int portColor(PostalConfig.LineTheme theme) {
        return switch (theme) {
            case AMBER -> 0xFFD98A;
            case TEAL -> 0xA8F0E4;
            default -> 0x9FC0FF;
        };
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
        private boolean requiresUpload = true;

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
            this.requiresUpload = true;
            return texture.getPixels();
        }

        private void uploadIfNeeded() {
            if (requiresUpload) {
                requiresUpload = false;
                texture.upload();
            }
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
