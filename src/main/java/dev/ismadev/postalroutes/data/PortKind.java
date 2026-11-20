package dev.ismadev.postalroutes.data;

/**
 * Port categories used on the wire and for icon selection.
 */
public final class PortKind {

    public static final int FROGPORT = 0;
    public static final int POSTBOX = 1;
    public static final int STATION = 2;

    private PortKind() {
    }

    public static String translationKey(int kind) {
        return switch (kind) {
            case POSTBOX -> "postalroutes.port.postbox";
            case STATION -> "postalroutes.port.station";
            default -> "postalroutes.port.frogport";
        };
    }
}
