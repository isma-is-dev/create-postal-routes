package dev.ismadev.postalroutes;

import dev.ismadev.postalroutes.net.PostalNetwork;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Create: Postal Routes.
 *
 * <p>Client-side visualisation of Create's package logistics on Xaero's World Map:
 * chain conveyor routes, frogports, postboxes and live package traffic. The mod adds
 * no blocks or items &mdash; it only reads Create's block entity data on the server and
 * mirrors it to the client for rendering.</p>
 */
@Mod(PostalRoutes.MODID)
public final class PostalRoutes {

    public static final String MODID = "postalroutes";
    public static final Logger LOGGER = LoggerFactory.getLogger("Create: Postal Routes");

    public PostalRoutes(IEventBus modBus, ModContainer container) {
        container.registerConfig(ModConfig.Type.CLIENT, PostalConfig.CLIENT_SPEC);
        container.registerConfig(ModConfig.Type.SERVER, PostalConfig.SERVER_SPEC);
        PostalNetwork.init(modBus);
    }
}
