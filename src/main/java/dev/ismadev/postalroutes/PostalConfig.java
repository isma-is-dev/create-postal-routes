package dev.ismadev.postalroutes;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Configuration split in two files:
 *
 * <ul>
 *     <li>{@code postalroutes-client.toml} &mdash; purely visual, per player.</li>
 *     <li>{@code postalroutes-server.toml} &mdash; network budget, authoritative for
 *     what the server is willing to send.</li>
 * </ul>
 */
public final class PostalConfig {

    public static final Client CLIENT;
    public static final ModConfigSpec CLIENT_SPEC;

    public static final Server SERVER;
    public static final ModConfigSpec SERVER_SPEC;

    static {
        Pair<Client, ModConfigSpec> client = new ModConfigSpec.Builder().configure(Client::new);
        CLIENT = client.getLeft();
        CLIENT_SPEC = client.getRight();

        Pair<Server, ModConfigSpec> server = new ModConfigSpec.Builder().configure(Server::new);
        SERVER = server.getLeft();
        SERVER_SPEC = server.getRight();
    }

    private PostalConfig() {
    }

    // Safe accessors: a config value can be read before its file is loaded (for example
    // from a render callback during startup), and NeoForge throws in that case.

    private static <T> T safe(ModConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            T loaded = value.get();
            return loaded == null ? fallback : loaded;
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    public static boolean overlayEnabled() {
        return safe(CLIENT.enabled, true);
    }

    public static boolean showRoutes() {
        return safe(CLIENT.showRoutes, true);
    }

    public static boolean showPorts() {
        return safe(CLIENT.showPorts, true);
    }

    public static boolean showPackages() {
        return safe(CLIENT.showPackages, true);
    }

    public static boolean showEntityPackages() {
        return safe(CLIENT.showEntityPackages, true);
    }

    public static boolean dashedRoutes() {
        return safe(CLIENT.dashedRoutes, true);
    }

    public static LineTheme lineTheme() {
        return safe(CLIENT.lineTheme, LineTheme.AMBER);
    }

    public static int routeOpacity() {
        return Math.max(0, Math.min(100, safe(CLIENT.routeOpacity, 85)));
    }

    public static int maxMapSections() {
        return Math.max(16, safe(CLIENT.maxMapSections, 256));
    }

    public static int syncIntervalTicks() {
        return Math.max(1, safe(SERVER.syncIntervalTicks, 10));
    }

    public static int maxPackages() {
        return Math.max(32, safe(SERVER.maxPackages, 1024));
    }

    public static int maxEntityPackages() {
        return Math.max(0, safe(SERVER.maxEntityPackages, 128));
    }

    public static int maxNodes() {
        return Math.max(64, safe(SERVER.maxNodes, 8192));
    }

    public static int maxDirtyChunksPerTick() {
        return Math.max(1, safe(SERVER.maxDirtyChunksPerTick, 4));
    }

    public enum LineTheme {
        /** Warm postal amber, the default. */
        AMBER,
        /** Copper/Brass, closer to Create's palette. */
        COPPER,
        /** Cool teal, for players that prefer less orange on the map. */
        TEAL
    }

    public static final class Client {

        public final ModConfigSpec.BooleanValue enabled;
        public final ModConfigSpec.BooleanValue showRoutes;
        public final ModConfigSpec.BooleanValue showPorts;
        public final ModConfigSpec.BooleanValue showPackages;
        public final ModConfigSpec.BooleanValue showEntityPackages;
        public final ModConfigSpec.EnumValue<LineTheme> lineTheme;
        public final ModConfigSpec.IntValue routeOpacity;
        public final ModConfigSpec.IntValue maxMapSections;
        public final ModConfigSpec.BooleanValue dashedRoutes;

        Client(ModConfigSpec.Builder builder) {
            builder.comment("Visual settings for the postal layer drawn on Xaero's World Map.").push("visuals");

            this.enabled = builder
                .comment("Master switch for the postal overlay. Can also be toggled from the map widget.")
                .define("enabled", true);

            this.showRoutes = builder
                .comment("Draw chain conveyor routes (the postal lines).")
                .define("showRoutes", true);

            this.showPorts = builder
                .comment("Draw frogports, postboxes and station mail ports as icons.")
                .define("showPorts", true);

            this.showPackages = builder
                .comment("Draw packages currently travelling on chain conveyors.")
                .define("showPackages", true);

            this.showEntityPackages = builder
                .comment("Also draw loose package entities (thrown, dropped or in flight).")
                .define("showEntityPackages", true);

            this.lineTheme = builder
                .comment("Colour palette for postal routes.")
                .defineEnum("lineTheme", LineTheme.AMBER);

            this.routeOpacity = builder
                .comment("Opacity of postal routes, 0-100.")
                .defineInRange("routeOpacity", 85, 0, 100);

            this.dashedRoutes = builder
                .comment("Draw routes as dashed lines, which keeps them clearly distinct from Create's train map.")
                .define("dashedRoutes", true);

            this.maxMapSections = builder
                .comment("Maximum number of 128x128 map sections kept in memory. Each one is 64 KiB of VRAM.",
                    "Raise it only if you have very large postal networks and enough VRAM.")
                .defineInRange("maxMapSections", 256, 16, 4096);

            builder.pop();
        }
    }

    public static final class Server {

        public final ModConfigSpec.IntValue syncIntervalTicks;
        public final ModConfigSpec.IntValue maxPackages;
        public final ModConfigSpec.IntValue maxEntityPackages;
        public final ModConfigSpec.IntValue maxNodes;
        public final ModConfigSpec.IntValue maxDirtyChunksPerTick;

        Server(ModConfigSpec.Builder builder) {
            builder.comment("Server side limits. These control how much data is collected and sent to map viewers.")
                .push("sync");

            this.syncIntervalTicks = builder
                .comment("How often (in ticks) package positions are sent while a player has the map open.")
                .defineInRange("syncIntervalTicks", 10, 1, 100);

            this.maxPackages = builder
                .comment("Maximum number of packages reported per update.")
                .defineInRange("maxPackages", 1024, 32, 16384);

            this.maxEntityPackages = builder
                .comment("Maximum number of loose package entities reported per update.")
                .defineInRange("maxEntityPackages", 128, 0, 2048);

            this.maxNodes = builder
                .comment("Maximum number of tracked chain conveyors/ports per dimension.")
                .defineInRange("maxNodes", 8192, 64, 65536);

            this.maxDirtyChunksPerTick = builder
                .comment("Chunks rescanned per tick after a block placement or removal.")
                .defineInRange("maxDirtyChunksPerTick", 4, 1, 32);

            builder.pop();
        }
    }
}
