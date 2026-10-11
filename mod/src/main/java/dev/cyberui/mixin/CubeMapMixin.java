package dev.cyberui.mixin;

import dev.cyberui.client.Panoramas;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CubeMap.class)
public abstract class CubeMapMixin {

    @Shadow
    @Final
    private ResourceLocation[] images;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cyberui$track(ResourceLocation base, CallbackInfo ci) {
        Panoramas.track(base, this.images);
    }
}
