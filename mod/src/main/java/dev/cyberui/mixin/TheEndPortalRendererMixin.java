package dev.cyberui.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cyberui.client.BeamCarrier;
import dev.cyberui.client.PortalBeam;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the purple beam above the End exit portal. */
@Mixin(TheEndPortalRenderer.class)
public abstract class TheEndPortalRendererMixin implements BlockEntityRenderer<TheEndPortalBlockEntity, EndPortalRenderState> {

    @Inject(method = "submit(Lnet/minecraft/client/renderer/blockentity/state/EndPortalRenderState;Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;Lnet/minecraft/client/renderer/state/level/CameraRenderState;)V",
            at = @At("TAIL"))
    private void cyberui$beam(EndPortalRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
                              CameraRenderState camera, CallbackInfo ci) {
        PortalBeam.Snapshot beam = ((BeamCarrier) state).cyberui$beam();
        if (beam != null) {
            PortalBeam.submit(beam, poseStack, collector);
        }
    }

    /**
     * End portals are rendered globally (not frustum culled) so the beam keeps
     * drawing while the portal itself is out of view, e.g. looking up at the sky.
     */
    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    /** The beam is visible from far away; ordinary portal blocks keep the vanilla range. */
    @Override
    public boolean shouldRender(TheEndPortalBlockEntity be, Vec3 cameraPosition) {
        double range = PortalBeam.isAnchor(be) ? 256.0 : this.getViewDistance();
        return Vec3.atCenterOf(be.getBlockPos()).closerThan(cameraPosition, range);
    }
}
