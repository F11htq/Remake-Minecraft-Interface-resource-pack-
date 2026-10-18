package dev.cyberui.mixin;

import dev.cyberui.client.PortalBeam;
import net.minecraft.client.Camera;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.BlockGetter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Shakes the view during the earthquake when the End portal beam strikes. */
@Mixin(Camera.class)
public abstract class CameraMixin {

    @Shadow
    public abstract float getXRot();

    @Shadow
    public abstract float getYRot();

    @Shadow
    protected abstract void setRotation(float yRot, float xRot);

    @Inject(method = "setup", at = @At("TAIL"))
    private void cyberui$quake(BlockGetter level, Entity entity, boolean detached, boolean thirdPersonReverse,
                               float partialTick, CallbackInfo ci) {
        float[] shake = PortalBeam.quake(entity, partialTick);
        if (shake != null) {
            this.setRotation(this.getYRot() + shake[0], this.getXRot() + shake[1]);
        }
    }
}
