package dev.cyberui.mixin;

import java.util.List;

import dev.cyberui.client.CubeMapAccess;
import dev.cyberui.client.Panoramas;
import net.minecraft.client.renderer.CubeMap;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Mutable;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CubeMap.class)
public abstract class CubeMapMixin implements CubeMapAccess {

    @Mutable
    @Shadow
    @Final
    private List<ResourceLocation> sides;

    @Inject(method = "<init>", at = @At("TAIL"))
    private void cyberui$track(ResourceLocation base, CallbackInfo ci) {
        Panoramas.track(base, this);
    }

    @Override
    public void cyberui$setSides(List<ResourceLocation> newSides) {
        this.sides = newSides;
    }
}
