package dev.cyberui.mixin;

import java.nio.file.Path;

import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;
import net.minecraft.client.resources.ClientPackSource;
import net.minecraft.server.packs.VanillaPackResourcesBuilder;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Serves the mod's own assets (cyberui:...) from the built-in vanilla pack.
 * Without Fabric API nothing else loads a mod's assets, and the boss bar
 * frames, the status panel and the panorama would all be missing.
 */
@Mixin(ClientPackSource.class)
public abstract class ClientPackSourceMixin {

    @Redirect(method = "createVanillaPackSource", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/server/packs/VanillaPackResourcesBuilder;pushJarResources()Lnet/minecraft/server/packs/VanillaPackResourcesBuilder;"))
    private static VanillaPackResourcesBuilder cyberui$addModAssets(VanillaPackResourcesBuilder builder) {
        builder.pushJarResources();
        builder.exposeNamespace("cyberui");
        ModContainer mod = FabricLoader.getInstance().getModContainer("cyberui").orElse(null);
        if (mod != null) {
            for (Path root : mod.getRootPaths()) {
                builder.pushUniversalPath(root);
            }
        }
        return builder;
    }
}
