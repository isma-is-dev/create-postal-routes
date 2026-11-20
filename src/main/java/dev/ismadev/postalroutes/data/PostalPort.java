package dev.ismadev.postalroutes.data;

import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * A Create package port (frogport or postbox) as seen on the map.
 *
 * <p>{@code dx/dy/dz} are offsets from the block corner and point at the exact chain
 * position the port is attached to, so the icon sits on the route it serves.</p>
 *
 * <p>Only slow-changing data lives here. Anything that changes per package would force a
 * full topology resend (and a texture rebuild) on every delivery, so it is left out on
 * purpose: the live package dots already show traffic.</p>
 */
public record PostalPort(BlockPos pos, float dx, float dy, float dz,
                         int kind, String address, boolean accepting) {

    public static final StreamCodec<FriendlyByteBuf, PostalPort> STREAM_CODEC =
        StreamCodec.of(PostalPort::write, PostalPort::read);

    private static void write(FriendlyByteBuf buf, PostalPort port) {
        BlockPos.STREAM_CODEC.encode(buf, port.pos);
        buf.writeFloat(port.dx);
        buf.writeFloat(port.dy);
        buf.writeFloat(port.dz);
        buf.writeByte(port.kind);
        buf.writeUtf(port.address, 128);
        buf.writeBoolean(port.accepting);
    }

    private static PostalPort read(FriendlyByteBuf buf) {
        return new PostalPort(
            BlockPos.STREAM_CODEC.decode(buf),
            buf.readFloat(), buf.readFloat(), buf.readFloat(),
            buf.readByte(),
            buf.readUtf(128),
            buf.readBoolean());
    }

    public static void writeList(FriendlyByteBuf buf, List<PostalPort> ports) {
        buf.writeVarInt(ports.size());
        for (PostalPort port : ports) {
            STREAM_CODEC.encode(buf, port);
        }
    }

    public static List<PostalPort> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<PostalPort> ports = new ArrayList<>(Math.min(size, 512));
        for (int i = 0; i < size; i++) {
            ports.add(STREAM_CODEC.decode(buf));
        }
        return ports;
    }

    public boolean isFrogport() {
        return kind == PortKind.FROGPORT;
    }

    public boolean isPostbox() {
        return kind == PortKind.POSTBOX;
    }

    public boolean isStation() {
        return kind == PortKind.STATION;
    }

    public double x() {
        return pos.getX() + dx;
    }

    public double y() {
        return pos.getY() + dy;
    }

    public double z() {
        return pos.getZ() + dz;
    }
}
