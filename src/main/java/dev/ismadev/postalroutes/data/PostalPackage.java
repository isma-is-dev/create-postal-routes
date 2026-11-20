package dev.ismadev.postalroutes.data;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * A live package. Positions are sent as eighths of a block in absolute world
 * coordinates, which is plenty for a 1 pixel = 1 block map and keeps the packet small.
 */
public record PostalPackage(int id, int x8, int y8, int z8, int kind, String address) {

    public static final int KIND_CHAIN = 0;
    public static final int KIND_ENTITY = 1;

    public static final StreamCodec<FriendlyByteBuf, PostalPackage> STREAM_CODEC =
        StreamCodec.of(PostalPackage::write, PostalPackage::read);

    private static void write(FriendlyByteBuf buf, PostalPackage pkg) {
        buf.writeVarInt(pkg.id);
        buf.writeInt(pkg.x8);
        buf.writeInt(pkg.y8);
        buf.writeInt(pkg.z8);
        buf.writeByte(pkg.kind);
        buf.writeUtf(pkg.address, 128);
    }

    private static PostalPackage read(FriendlyByteBuf buf) {
        return new PostalPackage(
            buf.readVarInt(),
            buf.readInt(), buf.readInt(), buf.readInt(),
            buf.readByte(),
            buf.readUtf(128));
    }

    public static void writeList(FriendlyByteBuf buf, List<PostalPackage> packages) {
        buf.writeVarInt(packages.size());
        for (PostalPackage pkg : packages) {
            STREAM_CODEC.encode(buf, pkg);
        }
    }

    public static List<PostalPackage> readList(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<PostalPackage> packages = new ArrayList<>(Math.min(size, 1024));
        for (int i = 0; i < size; i++) {
            packages.add(STREAM_CODEC.decode(buf));
        }
        return packages;
    }

    public double x() {
        return x8 / 8.0D;
    }

    public double y() {
        return y8 / 8.0D;
    }

    public double z() {
        return z8 / 8.0D;
    }
}
