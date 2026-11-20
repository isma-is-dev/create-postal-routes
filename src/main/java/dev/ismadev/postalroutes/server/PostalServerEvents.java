package dev.ismadev.postalroutes.server;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import dev.ismadev.postalroutes.PostalConfig;
import dev.ismadev.postalroutes.PostalRoutes;
import dev.ismadev.postalroutes.data.PostalEdge;
import dev.ismadev.postalroutes.data.PostalPort;
import dev.ismadev.postalroutes.data.PostalSnapshot;
import dev.ismadev.postalroutes.net.S2CPackageDots;
import dev.ismadev.postalroutes.net.S2CTopologyPart;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.level.LevelEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Server side glue: keeps one {@link PostalTracker} per dimension and streams snapshots
 * to players who have the map open.
 *
 * <p>Everything is demand driven: if nobody is looking at the map, the mod collects
 * nothing beyond cheap chunk indexing.</p>
 */
@EventBusSubscriber(modid = PostalRoutes.MODID)
public final class PostalServerEvents {

    /** How long a client request keeps the stream alive, in ticks (5 seconds). */
    private static final int REQUEST_TIMEOUT_TICKS = 100;
    /** Upper bound of edges+ports per topology packet, which keeps them around 10 KiB. */
    private static final int ENTRIES_PER_PART = 192;

    private static final Map<ResourceKey<Level>, PostalTracker> TRACKERS = new HashMap<>();
    private static final Map<UUID, Request> REQUESTS = new HashMap<>();
    private static final Map<UUID, SentState> SENT = new HashMap<>();
    /** Players that already received a non-empty package packet; lets us send a final empty one. */
    private static final Set<UUID> PACKAGE_SENT = new HashSet<>();

    private static int nextRequestId = 1;

    private PostalServerEvents() {
    }

    private record Request(ResourceKey<Level> dimension, int tick) {
    }

    private record SentState(ResourceKey<Level> dimension, int hash) {
    }

    // ------------------------------------------------------------------
    // World indexing
    // ------------------------------------------------------------------

    public static PostalTracker trackerFor(ServerLevel level) {
        return TRACKERS.computeIfAbsent(level.dimension(), key -> new PostalTracker(level));
    }

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            trackerFor(level).onChunkLoad(chunk);
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            PostalTracker tracker = TRACKERS.get(level.dimension());
            if (tracker != null) {
                tracker.onChunkUnload(chunk);
            }
        }
    }

    @SubscribeEvent
    public static void onBlockPlaced(BlockEvent.EntityPlaceEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            trackerFor(level).markDirty(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onBlockBroken(BlockEvent.BreakEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            trackerFor(level).markDirty(event.getPos());
        }
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            TRACKERS.remove(level.dimension());
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TRACKERS.clear();
        REQUESTS.clear();
        SENT.clear();
        PACKAGE_SENT.clear();
    }

    // ------------------------------------------------------------------
    // Streaming
    // ------------------------------------------------------------------

    public static void onRequest(ServerPlayer player, ResourceKey<Level> dimension) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        REQUESTS.put(player.getUUID(), new Request(dimension, server.getTickCount()));
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServer server = event.getServer();
        int now = server.getTickCount();

        if (!REQUESTS.isEmpty()) {
            REQUESTS.entrySet().removeIf(entry -> now - entry.getValue().tick() > REQUEST_TIMEOUT_TICKS);
        }
        if (REQUESTS.isEmpty()) {
            SENT.clear();
            PACKAGE_SENT.clear();
            return;
        }

        int interval = Math.max(1, PostalConfig.syncIntervalTicks());
        if (now % interval != 0) {
            return;
        }

        for (UUID viewer : new ArrayList<>(REQUESTS.keySet())) {
            Request request = REQUESTS.get(viewer);
            if (request == null) {
                continue;
            }
            ServerPlayer player = server.getPlayerList().getPlayer(viewer);
            if (player == null || player.connection == null) {
                continue;
            }

            ResourceKey<Level> dimension = request.dimension();
            ServerLevel level = server.getLevel(dimension);
            if (level == null) {
                continue;
            }

            PostalTracker tracker = trackerFor(level);
            tracker.processDirty();
            PostalSnapshot snapshot = tracker.snapshot();

            SentState sent = SENT.get(viewer);
            if (sent == null || sent.hash() != snapshot.topologyHash() || sent.dimension() != dimension) {
                sendTopology(player, snapshot);
                SENT.put(viewer, new SentState(dimension, snapshot.topologyHash()));
            }

            if (!snapshot.packages().isEmpty() || PACKAGE_SENT.contains(viewer)) {
                PacketDistributor.sendToPlayer(player,
                    new S2CPackageDots(dimension, level.getGameTime(), interval * 50, snapshot.packages()));
                PACKAGE_SENT.add(viewer);
            }
        }
    }

    private static void sendTopology(ServerPlayer player, PostalSnapshot snapshot) {
        List<PostalEdge> edges = snapshot.edges();
        List<PostalPort> ports = snapshot.ports();
        int total = edges.size() + ports.size();
        int parts = Math.max(1, (total + ENTRIES_PER_PART - 1) / ENTRIES_PER_PART);
        int requestId = nextRequestId++;

        int cursor = 0;
        for (int part = 0; part < parts; part++) {
            List<PostalEdge> partEdges = new ArrayList<>();
            List<PostalPort> partPorts = new ArrayList<>();
            while (cursor < total && partEdges.size() + partPorts.size() < ENTRIES_PER_PART) {
                if (cursor < edges.size()) {
                    partEdges.add(edges.get(cursor));
                } else {
                    partPorts.add(ports.get(cursor - edges.size()));
                }
                cursor++;
            }
            PacketDistributor.sendToPlayer(player, new S2CTopologyPart(requestId, part, parts,
                snapshot.topologyHash(), snapshot.dimension(), partEdges, partPorts));
        }
    }

    // ------------------------------------------------------------------
    // Diagnostics
    // ------------------------------------------------------------------

    public static Component describe(MinecraftServer server) {
        StringBuilder builder = new StringBuilder();
        builder.append("Postal Routes — ").append(countTrackedBlocks()).append(" tracked postal blocks, ")
            .append(REQUESTS.size()).append(" map viewer(s)");

        for (Map.Entry<ResourceKey<Level>, PostalTracker> entry : TRACKERS.entrySet()) {
            PostalTracker tracker = entry.getValue();
            PostalSnapshot snapshot;
            try {
                snapshot = tracker.snapshot();
            } catch (Throwable t) {
                PostalRoutes.LOGGER.warn("Failed to snapshot postal network in {}", entry.getKey().location(), t);
                continue;
            }
            builder.append('\n')
                .append(entry.getKey().location())
                .append(": ")
                .append(tracker.trackedBlocks()).append(" blocks, ")
                .append(snapshot.edges().size()).append(" routes, ")
                .append(snapshot.ports().size()).append(" ports, ")
                .append(snapshot.packages().size()).append(" packages");
        }

        return Component.literal(builder.toString()).withStyle(ChatFormatting.GRAY);
    }

    private static int countTrackedBlocks() {
        int total = 0;
        for (PostalTracker tracker : TRACKERS.values()) {
            total += tracker.trackedBlocks();
        }
        return total;
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal("postalroutes")
            .requires(source -> source.hasPermission(2));

        root.then(Commands.literal("stats").executes(context -> {
            context.getSource().sendSuccess(() -> describe(context.getSource().getServer()), false);
            return 1;
        }));

        root.then(Commands.literal("rescan").executes(context -> {
            MinecraftServer server = context.getSource().getServer();
            ChunkPos center = new ChunkPos(BlockPos.containing(context.getSource().getPosition()));
            int chunks = 0;
            for (Map.Entry<ResourceKey<Level>, PostalTracker> entry : TRACKERS.entrySet()) {
                ServerLevel level = server.getLevel(entry.getKey());
                if (level != null) {
                    if (level.players().isEmpty()) {
                        chunks += entry.getValue().rescanArea(center, 12);
                    } else {
                        chunks += entry.getValue().rescanAroundPlayers(12);
                    }
                }
            }
            int scanned = chunks;
            context.getSource().sendSuccess(() -> Component.literal(
                "Postal Routes rescanned " + scanned + " loaded chunks").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }));

        root.executes(context -> {
            context.getSource().sendSuccess(() -> describe(context.getSource().getServer()), false);
            return 1;
        });

        event.getDispatcher().register(root);
    }
}
