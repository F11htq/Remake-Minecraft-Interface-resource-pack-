package dev.cyberui.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import dev.cyberui.client.PortalBeam;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.TheEndPortalRenderer;
import net.minecraft.world.level.block.entity.TheEndPortalBlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Draws the purple beam above the End exit portal. */
@Mixin(TheEndPortalRenderer.class)
public abstract class TheEndPortalRendererMixin implements BlockEntityRenderer<TheEndPortalBlockEntity> {

    @Inject(method = "render(Lnet/minecraft/world/level/block/entity/TheEndPortalBlockEntity;FLcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/MultiBufferSource;II)V",
            at = @At("TAIL"))
    private void cyberui$beam(TheEndPortalBlockEntity be, float partialTick, PoseStack poseStack,
                              MultiBufferSource buffers, int light, int overlay, CallbackInfo ci) {
        PortalBeam.render(be, partialTick, poseStack, buffers);
    }

    /** Keep drawing the beam while the portal itself is out of view (looking up at the sky). */
    @Override
    public boolean shouldRenderOffScreen(TheEndPortalBlockEntity be) {
        return PortalBeam.isAnchor(be);
    }

    @Override
    public int getViewDistance() {
        return 256;
    }
}
