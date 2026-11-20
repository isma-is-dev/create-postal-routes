package dev.ismadev.postalroutes.net;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public final class PostalNetwork {

    /** Bumped whenever a payload layout changes. */
    private static final String PROTOCOL_VERSION = "1";

    private PostalNetwork() {
    }

    public static void init(IEventBus modBus) {
        modBus.addListener(PostalNetwork::registerPayloads);
    }

    private static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(PROTOCOL_VERSION);
        registrar.playToServer(C2SRequestPostal.TYPE, C2SRequestPostal.STREAM_CODEC, C2SRequestPostal::handle);
        registrar.playToClient(S2CTopologyPart.TYPE, S2CTopologyPart.STREAM_CODEC, S2CTopologyPart::handle);
        registrar.playToClient(S2CPackageDots.TYPE, S2CPackageDots.STREAM_CODEC, S2CPackageDots::handle);
    }

    public static String protocolVersion() {
        return PROTOCOL_VERSION;
    }
}
