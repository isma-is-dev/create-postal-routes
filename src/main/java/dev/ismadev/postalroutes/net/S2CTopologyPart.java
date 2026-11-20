package dev.ismadev.postalroutes.net;

import dev.ismadev.postalroutes.PostalRoutes;
import dev.ismadev.postalroutes.client.PostalClientPayloadHandler;
import dev.ismadev.postalroutes.data.PostalEdge;
import dev.ismadev.postalroutes.data.PostalPort;
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
 * One part of a postal topology snapshot. Large networks are split over several
 * packets ({@link #part} of {@link #parts}) to stay well below the vanilla payload
 * size limit and to avoid one huge allocation on the client.
 */
public record S2CTopologyPart(int requestId, int part, int parts, int topologyHash,
                              ResourceKey<Level> dimension,
                              List<PostalEdge> edges, List<PostalPort> ports)
    implements CustomPacketPayload {

    public static final Type<S2CTopologyPart> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath(PostalRoutes.MODID, "topology"));

    public static final StreamCodec<RegistryFriendlyByteBuf, S2CTopologyPart> STREAM_CODEC = StreamCodec.of(
        (buf, payload) -> {
            buf.writeVarInt(payload.requestId);
            buf.writeVarInt(payload.part);
            buf.writeVarInt(payload.parts);
            buf.writeInt(payload.topologyHash);
            ResourceKey.streamCodec(Registries.DIMENSION).encode(buf, payload.dimension);
            PostalEdge.writeList(buf, payload.edges);
            PostalPort.writeList(buf, payload.ports);
        },
        buf -> new S2CTopologyPart(
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readVarInt(),
            buf.readInt(),
            ResourceKey.streamCodec(Registries.DIMENSION).decode(buf),
            PostalEdge.readList(buf),
            PostalPort.readList(buf))
    );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(S2CTopologyPart payload, IPayloadContext context) {
        context.enqueueWork(() -> PostalClientPayloadHandler.handleTopology(payload));
    }
}
