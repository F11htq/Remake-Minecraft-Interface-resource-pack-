package dev.cyberui.mixin;

import dev.cyberui.client.PortalBeam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shakes the view during the earthquake when the End portal beam strikes. */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    public abstract float xRot();

    @Shadow
    public abstract float yRot();

    @Shadow
    public abstract @Nullable Entity entity();

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    /** Runs before the culling frustum is built, so culling follows the shaken view. */
    @Inject(method = "alignWithEntity", at = @At("TAIL"))
    private void cyberui$quake(float partialTicks, CallbackInfo ci) {
        float[] shake = PortalBeam.quake(this.entity(), partialTicks);
        if (shake != null) {
            this.setRotation(this.yRot() + shake[0], this.xRot() + shake[1]);
        }
    }
}
