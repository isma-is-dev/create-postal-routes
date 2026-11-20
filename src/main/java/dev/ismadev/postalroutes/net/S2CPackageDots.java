package dev.ismadev.postalroutes.net;

import dev.ismadev.postalroutes.PostalRoutes;
import dev.ismadev.postalroutes.client.PostalClientPayloadHandler;
import dev.ismadev.postalroutes.data.PostalPackage;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.List;

/**
 * Lightweight, high frequency packet: where every package is right now.
 *
 * @param intervalMs how long the server waits between updates, used by the client to
 *                   interpolate dots instead of making them jump.
 */
public record S2CPackageDots(ResourceKey<Level> dimension, long gameTime, int intervalMs,
                             List<PostalPackage> packages) implements CustomPacketPayload {

    public static final Type<S2CPackageDots> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(PostalRoutes.MODID, "packages"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CPackageDots> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, payload.dimension);
            buf.writeVarLong(payload.gameTime);
            buf.writeVarInt(payload.intervalMs);
            PostalPackage.writeList(buf, payload.packages);
        },
        buf -> new S2CPackageDots(
            ResourceKey.streamCodec(Registries.DIMENSION).decode(buf),
            buf.readVarLong(),
            buf.readVarInt(),
            PostalPackage.readList(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CPackageDots payload, IPayloadContext context) {
        context.enqueueWork(() -> PostalClientPayloadHandler.handlePackages(payload));
    }
}
