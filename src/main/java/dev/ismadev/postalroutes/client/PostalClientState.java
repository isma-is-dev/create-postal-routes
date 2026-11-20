package dev.ismadev.postalroutes.client;

import dev.ismadev.postalroutes.data.PostalEdge;
import dev.ismadev.postalroutes.data.PostalPackage;
import dev.ismadev.postalroutes.data.PostalPort;
import dev.ismadev.postalroutes.net.S2CPackageDots;
import dev.ismadev.postalroutes.net.S2CTopologyPart;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side mirror of the postal network plus package interpolation.
 *
 * <p>All mutations happen on the client thread (payload handlers are enqueued by
 * NeoForge), and rendering only reads immutable snapshots, so no locking is needed.</p>
 */
public final class PostalClientState {

    /** Dimension currently displayed by Xaero's world map, updated by the overlay. */
    @Nullable
    public static ResourceKey<Level> viewedDimension;

    private static final Map<ResourceKey<Level>, DimData> DIMENSIONS = new HashMap<>();
    private static final Map<ResourceKey<Level>, PendingTopology> PENDING = new HashMap<>();

    private PostalClientState() {
    }

    public static DimData get(ResourceKey<Level> dimension) {
        return DIMENSIONS.computeIfAbsent(dimension, key -> new DimData());
    }

    @Nullable
    public static DimData getIfPresent(ResourceKey<Level> dimension) {
        return DIMENSIONS.get(dimension);
    }

    public static void clear() {
        DIMENSIONS.clear();
        PENDING.clear();
    }

    // ------------------------------------------------------------------
    // Payload application
    // ------------------------------------------------------------------

    public static void acceptTopology(S2CTopologyPart part) {
        ResourceKey<Level> dimension = part.dimension();
        PendingTopology pending = PENDING.get(dimension);
        if (pending == null || pending.requestId != part.requestId() || pending.parts != part.parts()) {
            pending = new PendingTopology(part.requestId(), part.parts());
            PENDING.put(dimension, pending);
        }

        pending.edges.addAll(part.edges());
        pending.ports.addAll(part.ports());
        pending.received++;

        if (pending.received >= pending.parts) {
            DimData data = get(dimension);
            data.hash = part.topologyHash();
            data.edges = List.copyOf(pending.edges);
            data.ports = List.copyOf(pending.ports);
            data.revision++;
            PENDING.remove(dimension);
        }
    }

    public static void acceptPackages(S2CPackageDots packet) {
        DimData data = get(packet.dimension());
        long now = System.currentTimeMillis();

        // Interpolate over the *measured* gap between packets, not the configured one.
        // Network and tick jitter make the configured value wrong often enough that dots
        // would finish their glide, sit still and then jump when the next packet landed.
        int duration;
        if (data.lastPacketMillis == 0L) {
            duration = Math.max(60, packet.intervalMs());
        } else {
            duration = (int) Math.max(60L, Math.min(1200L, now - data.lastPacketMillis));
        }

        Set<Integer> seen = new HashSet<>();
        for (PostalPackage pkg : packet.packages()) {
            if (!seen.add(pkg.id())) {
                continue;
            }
            Dot dot = data.dots.get(pkg.id());
            if (dot == null) {
                dot = new Dot(pkg.x(), pkg.y(), pkg.z(), pkg.kind(), pkg.address(), now, duration);
                data.dots.put(pkg.id(), dot);
            } else {
                dot.moveTo(pkg.x(), pkg.y(), pkg.z(), now, duration);
                dot.kind = pkg.kind();
                dot.address = pkg.address();
            }
        }

        // Anything that is not in the packet has arrived, been picked up or left the area.
        data.dots.keySet().retainAll(seen);
        data.lastPacketMillis = now;
        data.lastGameTime = packet.gameTime();
        data.intervalMs = duration;
    }

    // ------------------------------------------------------------------
    // Data holders
    // ------------------------------------------------------------------

    public static final class PendingTopology {
        final int requestId;
        final int parts;
        int received;
        final List<PostalEdge> edges = new ArrayList<>();
        final List<PostalPort> ports = new ArrayList<>();

        PendingTopology(int requestId, int parts) {
            this.requestId = requestId;
            this.parts = Math.max(1, parts);
        }
    }

    public static final class DimData {
        public int hash;
        public List<PostalEdge> edges = List.of();
        public List<PostalPort> ports = List.of();
        public final Map<Integer, Dot> dots = new LinkedHashMap<>();
        public long lastPacketMillis;
        public long lastGameTime;
        public int intervalMs = 500;
        /** Incremented every time the topology is replaced; used to invalidate map textures. */
        public int revision;

        public boolean isEmpty() {
            return edges.isEmpty() && ports.isEmpty() && dots.isEmpty();
        }
    }

    /** A package with the previous and current server position, so it can be lerped. */
    public static final class Dot {
        double px;
        double py;
        double pz;
        double x;
        double y;
        double z;
        long timestamp;
        int durationMs;
        int kind;
        String address;

        Dot(double x, double y, double z, int kind, String address, long now, int durationMs) {
            this.px = x;
            this.py = y;
            this.pz = z;
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
            this.address = address == null ? "" : address;
            this.timestamp = now;
            this.durationMs = durationMs;
        }

        void moveTo(double nx, double ny, double nz, long now, int durationMs) {
            this.px = this.x;
            this.py = this.y;
            this.pz = this.z;
            this.x = nx;
            this.y = ny;
            this.z = nz;
            this.timestamp = now;
            this.durationMs = durationMs;
        }

        public Vec3 sample(long now) {
            double t = (double) (now - this.timestamp) / Math.max(60, this.durationMs);
            t = t < 0.0D ? 0.0D : Math.min(t, 1.0D);
            return new Vec3(
                this.px + (this.x - this.px) * t,
                this.py + (this.y - this.py) * t,
                this.pz + (this.z - this.pz) * t);
        }

        public int kind() {
            return kind;
        }

        public String address() {
            return address;
        }
    }
}
