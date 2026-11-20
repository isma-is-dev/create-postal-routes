package dev.ismadev.postalroutes.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * One chain conveyor link, already resolved to the two chain strands Create renders
 * in world space.
 *
 * <p>Coordinates are stored as offsets from {@link #from}'s block corner. That keeps
 * them small (and therefore float-precise) even for bases far from the world origin.</p>
 */
public record PostalEdge(BlockPos from, BlockPos to,
                         float sx, float sy, float sz,
                         float ex, float ey, float ez) {

    public static final StreamCodec<FriendlyByteBuf, PostalEdge> STREAM_CODEC =
        StreamCodec.of(PostalEdge::write, PostalEdge::read);

    private static void write(FriendlyByteBuf buf, PostalEdge edge) {
        BlockPos.STREAM_CODEC.encode(buf, edge.from);
        BlockPos.STREAM_CODEC.encode(buf, edge.to);
        buf.writeFloat(edge.sx);
        buf.writeFloat(edge.sy);
        buf.writeFloat(edge.sz);
        buf.writeFloat(edge.ex);
        buf.writeFloat(edge.ey);
        buf.writeFloat(edge.ez);
    }

    private static PostalEdge read(FriendlyByteBuf buf) {
        return new PostalEdge(
            BlockPos.STREAM_CODEC.decode(buf),
            BlockPos.STREAM_CODEC.decode(buf),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void writeList(FriendlyByteBuf buf, List<PostalEdge> edges) {
        buf.writeVarInt(edges.size());
        for (PostalEdge edge : edges) {
            STREAM_CODEC.encode(buf, edge);
        }
    }

    public static List<PostalEdge> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<PostalEdge> edges = new ArrayList<>(Math.min(size, 512));
        for (int i = 0; i < size; i++) {
            edges.add(STREAM_CODEC.decode(buf));
        }
        return edges;
    }

    public double startX() {
        return from.getX() + sx;
    }

    public double startY() {
        return from.getY() + sy;
    }

    public double startZ() {
        return from.getZ() + sz;
    }

    public double endX() {
        return from.getX() + ex;
    }

    public double endY() {
        return from.getY() + ey;
    }

    public double endZ() {
        return from.getZ() + ez;
    }
}
