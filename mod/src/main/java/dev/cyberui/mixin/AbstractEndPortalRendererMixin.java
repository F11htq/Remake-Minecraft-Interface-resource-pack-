package dev.cyberui.mixin;

import dev.cyberui.client.BeamCarrier;
import dev.cyberui.client.PortalBeam;
import net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Captures the beam animation state while the level can still be read (extract phase). */
@Mixin(AbstractEndPortalRenderer.class)
public abstract class AbstractEndPortalRendererMixin {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/level/block/entity/TheEndPortalBlockEntity;Lnet/minecraft/client/renderer/blockentity/state/EndPortalRenderState;FLnet/minecraft/world/phys/Vec3;Lnet/minecraft/client/renderer/feature/ModelFeatureRenderer$CrumblingOverlay;)V",
            at = @At("TAIL"))
    private void cyberui$extractBeam(TheEndPortalBlockEntity be, EndPortalRenderState state, float partialTicks,
                                     Vec3 cameraPosition, ModelFeatureRenderer.@Nullable CrumblingOverlay breakProgress,
                                     CallbackInfo ci) {
        if ((Object) this instanceof TheEndPortalRenderer && PortalBeam.isAnchor(be)) {
            ((BeamCarrier) state).cyberui$setBeam(PortalBeam.extract(be, partialTicks, cameraPosition));
        }
    }
}
