package dev.ismadev.postalroutes.data;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * Immutable view of one dimension's postal network for a single server tick.
 *
 * @param topologyHash order independent hash of everything that changes the drawing
 *                     (routes and ports). Used by the client to know when to rebuild
 *                     its map textures. Package positions are excluded on purpose.
 */
public record PostalSnapshot(ResourceKey<Level> dimension,
                             int topologyHash,
                             List<PostalEdge> edges,
                             List<PostalPort> ports,
                             List<PostalPackage> packages) {

    public boolean isEmpty() {
        return edges.isEmpty() && ports.isEmpty();
    }
}
