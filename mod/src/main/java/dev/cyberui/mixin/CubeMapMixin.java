package dev.cyberui.mixin;

import dev.cyberui.client.Panoramas;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Points the title panorama at the colour-graded panorama bundled in the mod.
 * The swap happens before the cube map texture is registered, so the vanilla
 * panorama is never loaded at all.
 */
@Mixin(CubeMap.class)
public abstract class CubeMapMixin {

    @Mutable
    @Shadow
    @Final
    private Identifier location;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cyberui$swap(Identifier base, CallbackInfo ci) {
        this.location = Panoramas.replace(base);
    }
}
