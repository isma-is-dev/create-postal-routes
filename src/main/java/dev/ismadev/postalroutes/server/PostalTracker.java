package dev.ismadev.postalroutes.server;

import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorBlockEntity;
import com.simibubi.create.content.kinetics.chainConveyor.ChainConveyorPackage;
import com.simibubi.create.content.logistics.box.PackageEntity;
import com.simibubi.create.content.logistics.box.PackageItem;
import com.simibubi.create.content.logistics.packagePort.PackagePortBlockEntity;
import com.simibubi.create.content.logistics.packagePort.PackagePortTarget;
import com.simibubi.create.content.logistics.packagePort.postbox.PostboxBlockEntity;
import dev.ismadev.postalroutes.PostalConfig;
import dev.ismadev.postalroutes.PostalRoutes;
import dev.ismadev.postalroutes.data.PortKind;
import dev.ismadev.postalroutes.data.PostalEdge;
import dev.ismadev.postalroutes.data.PostalPackage;
import dev.ismadev.postalroutes.data.PostalPort;
import dev.ismadev.postalroutes.data.PostalSnapshot;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Tracks every postal block of one server level.
 *
 * <p>Only <em>loaded</em> chunks are indexed: block entity state lives nowhere else, so
 * a postal network that is not loaded simply cannot be observed. Chunks are indexed when
 * they load and dropped when they unload, and a placement/removal marks its chunk for a
 * cheap rescan on the next tick. Nothing here walks the world.</p>
 */
public final class PostalTracker {

    private final ServerLevel level;

    private final Map<ChunkPosKey, Set<BlockPos>> byChunk = new HashMap<>();
    private final Set<BlockPos> positions = new HashSet<>();
    private final Set<ChunkPosKey> dirty = new HashSet<>();

    private PostalSnapshot cachedSnapshot;
    private long cachedTick = Long.MIN_VALUE;
    private boolean seeded;
    private int ambientTicks;

    public PostalTracker(ServerLevel level) {
        this.level = level;
    }

    public ServerLevel level() {
        return level;
    }

    public int trackedBlocks() {
        return positions.size();
    }

    // ------------------------------------------------------------------
    // Discovery
    // ------------------------------------------------------------------

    public void onChunkLoad(LevelChunk chunk) {
        if (chunk.getLevel() != level) {
            return;
        }
        scan(chunk);
    }

    public void onChunkUnload(LevelChunk chunk) {
        ChunkPosKey key = ChunkPosKey.of(chunk.getPos());
        Set<BlockPos> old = byChunk.remove(key);
        if (old != null) {
            positions.removeAll(old);
        }
        dirty.remove(key);
        invalidate();
    }

    public void markDirty(BlockPos pos) {
        dirty.add(ChunkPosKey.of(pos));
        invalidate();
    }

    /** Rescans a bounded number of chunks whose blocks changed. */
    public void processDirty() {
        ambientTicks++;
        if (ambientTicks >= 20) {
            ambientTicks = 0;
            // Self-healing sweep: catches blocks placed by commands, worldgen mods or
            // anything else that does not fire a player placement event.
            rescanAroundPlayers(3);
        }
        if (dirty.isEmpty()) {
            return;
        }
        int budget = PostalConfig.maxDirtyChunksPerTick();
        Iterator<ChunkPosKey> iterator = dirty.iterator();
        while (iterator.hasNext() && budget-- > 0) {
            ChunkPosKey key = iterator.next();
            iterator.remove();
            LevelChunk chunk = level.getChunkSource().getChunkNow(key.x(), key.z());
            if (chunk != null) {
                scan(chunk);
            }
        }
    }

    /**
     * Re-indexes every loaded chunk within {@code radius} of a player.
     *
     * @return number of chunks examined
     */
    public int rescanAroundPlayers(int radius) {
        int scanned = 0;
        try {
            for (ServerPlayer player : level.players()) {
                scanned += rescanArea(player.chunkPosition(), radius);
            }
        } catch (Throwable t) {
            PostalRoutes.LOGGER.debug("Could not rescan postal chunks in {}", level.dimension().location(), t);
        }
        return scanned;
    }

    /**
     * Re-indexes every loaded chunk in a square around a chunk position.
     *
     * @return number of chunks examined
     */
    public int rescanArea(ChunkPos center, int radius) {
        int scanned = 0;
        try {
            for (int dx = -radius; dx <= radius; dx++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    LevelChunk chunk = level.getChunkSource().getChunkNow(center.x + dx, center.z + dz);
                    if (chunk != null) {
                        scan(chunk);
                        scanned++;
                    }
                }
            }
        } catch (Throwable t) {
            PostalRoutes.LOGGER.debug("Could not rescan postal chunks in {}", level.dimension().location(), t);
        }
        return scanned;
    }

    private void scan(LevelChunk chunk) {
        Set<BlockPos> found = null;
        for (Map.Entry<BlockPos, BlockEntity> entry : chunk.getBlockEntities().entrySet()) {
            if (isPostalBlock(entry.getValue())) {
                if (found == null) {
                    found = new HashSet<>();
                }
                found.add(entry.getKey().immutable());
            }
        }

        ChunkPosKey key = ChunkPosKey.of(chunk.getPos());
        Set<BlockPos> old = byChunk.remove(key);
        if (old != null) {
            positions.removeAll(old);
        }
        if (found != null) {
            byChunk.put(key, found);
            positions.addAll(found);
        }
        invalidate();
    }

    private static boolean isPostalBlock(@Nullable BlockEntity be) {
        return be instanceof ChainConveyorBlockEntity || be instanceof PackagePortBlockEntity;
    }

    private void invalidate() {
        cachedTick = Long.MIN_VALUE;
        cachedSnapshot = null;
    }

    // ------------------------------------------------------------------
    // Snapshot
    // ------------------------------------------------------------------

    /** Builds (or returns this tick's) snapshot. Call on the server thread only. */
    public PostalSnapshot snapshot() {
        long tick = level.getServer() == null ? level.getGameTime() : level.getServer().getTickCount();
        if (cachedSnapshot != null && cachedTick == tick) {
            return cachedSnapshot;
        }

        seedIfNeeded();

        List<PostalEdge> edges = new ArrayList<>();
        List<PostalPort> ports = new ArrayList<>();
        List<PostalPackage> packages = new ArrayList<>();
        int maxNodes = PostalConfig.maxNodes();
        int maxPackages = PostalConfig.maxPackages();

        // Deterministic order: HashSet iteration order changes when the set changes, and a
        // cap on collected entries would then make routes flicker in and out.
        List<BlockPos> ordered = new ArrayList<>(positions.size());
        ordered.addAll(positions);
        ordered.sort(Comparator.comparingLong(BlockPos::asLong));

        int visited = 0;
        for (BlockPos pos : ordered) {
            // Never touch unloaded chunks: getBlockEntity would force a synchronous load.
            if (!level.isLoaded(pos)) {
                continue;
            }

            BlockEntity be = level.getBlockEntity(pos);
            if (be == null) {
                positions.remove(pos);
                continue;
            }
            if (be instanceof ChainConveyorBlockEntity conveyor) {
                if (visited++ < maxNodes) {
                    collectConveyor(conveyor, edges, packages, maxPackages);
                }
            } else if (be instanceof PackagePortBlockEntity port) {
                if (visited++ < maxNodes) {
                    collectPort(port, ports);
                }
            } else {
                positions.remove(pos);
            }
        }

        if (PostalConfig.maxEntityPackages() > 0 && packages.size() < maxPackages) {
            collectLoosePackages(packages, Math.min(maxPackages,
                packages.size() + PostalConfig.maxEntityPackages()));
        }

        int hash = hash(edges, ports);
        PostalSnapshot snapshot = new PostalSnapshot(level.dimension(), hash, List.copyOf(edges),
            List.copyOf(ports), List.copyOf(packages));
        cachedSnapshot = snapshot;
        cachedTick = tick;
        return snapshot;
    }

    /**
     * Safety net for a tracker created after its level already had chunks loaded (for
     * example after a dimension reload). Scans the chunks around players once.
     */
    private void seedIfNeeded() {
        if (seeded) {
            return;
        }
        seeded = true;
        rescanAroundPlayers(12);
    }

    private void collectConveyor(ChainConveyorBlockEntity conveyor,
                                 List<PostalEdge> edges,
                                 List<PostalPackage> packages,
                                 int maxPackages) {
        BlockPos origin = conveyor.getBlockPos();

        try {
            conveyor.prepareStats();
        } catch (Throwable t) {
            PostalRoutes.LOGGER.debug("Skipping misbehaving chain conveyor at {}", origin, t);
            return;
        }

        Map<BlockPos, ChainConveyorBlockEntity.ConnectionStats> stats = conveyor.connectionStats;
        if (stats != null) {
            for (BlockPos offset : conveyor.connections) {
                ChainConveyorBlockEntity.ConnectionStats connection = stats.get(offset);
                if (connection == null) {
                    continue;
                }
                Vec3 start = connection.start();
                Vec3 end = connection.end();
                if (start == null || end == null) {
                    continue;
                }
                edges.add(new PostalEdge(origin, origin.offset(offset),
                    (float) (start.x - origin.getX()),
                    (float) (start.y - origin.getY()),
                    (float) (start.z - origin.getZ()),
                    (float) (end.x - origin.getX()),
                    (float) (end.y - origin.getY()),
                    (float) (end.z - origin.getZ())));
            }
        }

        if (packages.size() >= maxPackages) {
            return;
        }

        for (Map.Entry<BlockPos, List<ChainConveyorPackage>> entry : conveyor.getTravellingPackages().entrySet()) {
            for (ChainConveyorPackage box : entry.getValue()) {
                addChainPackage(conveyor, box, entry.getKey(), packages, maxPackages);
            }
        }
        for (ChainConveyorPackage box : conveyor.getLoopingPackages()) {
            addChainPackage(conveyor, box, null, packages, maxPackages);
        }
    }

    private void addChainPackage(ChainConveyorBlockEntity conveyor, ChainConveyorPackage box,
                                 @Nullable BlockPos travelTarget, List<PostalPackage> packages, int maxPackages) {
        if (packages.size() >= maxPackages || box == null) {
            return;
        }
        Vec3 position = box.worldPosition;
        if (position == null) {
            try {
                position = conveyor.getPackagePosition(box.chainPosition, travelTarget);
            } catch (Throwable t) {
                return;
            }
        }
        if (position == null) {
            return;
        }
        packages.add(pack(packageId(conveyor, box), position, PostalPackage.KIND_CHAIN,
            PackageItem.getAddress(box.item)));
    }

    private void collectPort(PackagePortBlockEntity port, List<PostalPort> ports) {
        BlockPos pos = port.getBlockPos();
        // Anchor ports to their own block. A chain frogport's target is a point on the
        // chain one block away, which reads as "misplaced" on a map where every other
        // marker sits on a block.
        double x = pos.getX() + 0.5D;
        double y = pos.getY() + 0.5D;
        double z = pos.getZ() + 0.5D;

        int kind = kindOf(port);
        String address = port.addressFilter == null ? "" : port.addressFilter;

        ports.add(new PostalPort(pos,
            (float) (x - pos.getX()), (float) (y - pos.getY()), (float) (z - pos.getZ()),
            kind, address, port.acceptsPackages));
    }

    private static int kindOf(PackagePortBlockEntity port) {
        if (port instanceof PostboxBlockEntity) {
            return PortKind.POSTBOX;
        }
        if (port.target instanceof PackagePortTarget.TrainStationFrogportTarget) {
            return PortKind.STATION;
        }
        return PortKind.FROGPORT;
    }
    private static int packageId(ChainConveyorBlockEntity conveyor, ChainConveyorPackage box) {
        // netId is unique per level while alive; mixing the origin keeps ids stable and
        // collision free even across dimensions/saves where netId restarts. Chain packages
        // use the low 30 bits, loose entities the high bit (see collectLoosePackages).
        int id = (conveyor.getBlockPos().hashCode() * 31) ^ box.netId;
        return id & 0x3FFFFFFF;
    }

    private void collectLoosePackages(List<PostalPackage> packages, int limit) {
        try {
            for (Entity entity : level.getAllEntities()) {
                if (packages.size() >= limit) {
                    return;
                }
                if (entity instanceof PackageEntity parcel) {
                    String address = parcel.getAddress();
                    // High bit marks loose entities so their ids can never collide with the
                    // chain package ids produced by packageId().
                    int id = 0x40000000 | (entity.getId() & 0x3FFFFFFF);
                    packages.add(pack(id, parcel.position(), PostalPackage.KIND_ENTITY, address));
                }
            }
        } catch (Throwable t) {
            PostalRoutes.LOGGER.debug("Could not enumerate loose packages", t);
        }
    }

    private static PostalPackage pack(int id, Vec3 position, int kind, @Nullable String address) {
        return new PostalPackage(id,
            (int) Math.round(position.x * 8.0D),
            (int) Math.round(position.y * 8.0D),
            (int) Math.round(position.z * 8.0D),
            kind,
            address == null ? "" : address);
    }

    private static int hash(List<PostalEdge> edges, List<PostalPort> ports) {
        // Order independent: the tracked set is unordered, so XOR keeps the hash stable
        // between ticks even when iteration order changes.
        int hash = 1;
        for (PostalEdge edge : edges) {
            hash ^= 31 * hashPos(edge.from()) + hashPos(edge.to())
                + Float.floatToIntBits(edge.sx()) + Float.floatToIntBits(edge.ex());
        }
        for (PostalPort port : ports) {
            hash ^= 17 * hashPos(port.pos()) + port.kind() + port.address().hashCode()
                + (port.accepting() ? 1 : 0);
        }
        return hash;
    }

    private static int hashPos(BlockPos pos) {
        return pos.getX() * 73856093 ^ pos.getY() * 19349663 ^ pos.getZ() * 83492791;
    }

    /** Chunk position without pulling in the mutable fastutil class in maps. */
    public record ChunkPosKey(int x, int z) {

        public static ChunkPosKey of(net.minecraft.world.level.ChunkPos pos) {
            return new ChunkPosKey(pos.x, pos.z);
        }

        public static ChunkPosKey of(BlockPos pos) {
            return new ChunkPosKey(pos.getX() >> 4, pos.getZ() >> 4);
        }
    }
}
