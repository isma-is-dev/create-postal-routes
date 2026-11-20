package dev.ismadev.postalroutes.mixin;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import xaero.map.MapProcessor;
import xaero.map.gui.GuiMap;

/**
 * Read-only access to the private camera state of Xaero's world map.
 *
 * <p>Only applied when Xaero's World Map is installed (see {@link PostalMixinPlugin}).</p>
 */
@Mixin(value = GuiMap.class, remap = false)
public interface GuiMapAccessor {

    @Accessor("cameraX")
    double postal$getCameraX();

    @Accessor("cameraZ")
    double postal$getCameraZ();

    @Accessor("scale")
    double postal$getScale();

    @Accessor("mapProcessor")
    MapProcessor postal$getMapProcessor();
}
