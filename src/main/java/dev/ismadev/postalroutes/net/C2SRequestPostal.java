package dev.ismadev.postalroutes.net;

import dev.ismadev.postalroutes.PostalRoutes;
import dev.ismadev.postalroutes.server.PostalServerEvents;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -&gt; server heartbeat sent while the world map is open. It carries the
 * dimension currently being viewed, so the server only streams relevant data.
 */
public record C2SRequestPostal(ResourceKey<Level> dimension) implements CustomPacketPayload {

    public static final Type<C2SRequestPostal> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(PostalRoutes.MODID, "request"));

    public static final StreamCodec<RegistryFriendlyByteBuf, C2SRequestPostal> STREAM_CODEC =
        StreamCodec.composite(
            ResourceKey.streamCodec(Registries.DIMENSION), C2SRequestPostal::dimension,
            C2SRequestPostal::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(C2SRequestPostal payload, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            PostalServerEvents.onRequest(player, payload.dimension());
        }
    }
}
