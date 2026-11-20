package dev.ismadev.postalroutes.client;

import dev.ismadev.postalroutes.net.S2CPackageDots;
import dev.ismadev.postalroutes.net.S2CTopologyPart;

/**
 * Client-only payload sink. Kept separate so that the payload classes themselves can be
 * loaded on a dedicated server without touching client classes.
 */
public final class PostalClientPayloadHandler {

    private PostalClientPayloadHandler() {
    }

    public static void handleTopology(S2CTopologyPart payload) {
        PostalClientState.acceptTopology(payload);
    }

    public static void handlePackages(S2CPackageDots payload) {
        PostalClientState.acceptPackages(payload);
    }
}
