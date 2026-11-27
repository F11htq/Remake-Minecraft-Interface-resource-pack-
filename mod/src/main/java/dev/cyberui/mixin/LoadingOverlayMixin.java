package dev.cyberui.mixin;

import java.util.function.IntSupplier;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cyberui.client.Draw;
import dev.cyberui.client.LoadingScreen;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the Mojang Studios loading screen: dark background instead of red,
 * no Mojang logo, and the CYBER//UI logo, progress bar and boot log on top.
 * The vanilla timing (fades, closing the overlay) is left untouched.
 */
@Mixin(LoadingOverlay.class)
public abstract class LoadingOverlayMixin {

    @Shadow
    private float currentProgress;
    @Shadow
    private long fadeOutStart;
    @Shadow
    private long fadeInStart;
    @Shadow
    @Final
    private boolean fadeIn;

    @Unique
    private boolean cyberui$barShown;
    @Unique
    private float cyberui$barAlpha = 1.0F;

    /** The brand colour (screen clear colour and the fade fills) becomes the dark CYBER//UI background. */
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE", target = "Ljava/util/function/IntSupplier;getAsInt()I"))
    private int cyberui$darkBackground(IntSupplier brand) {
        return 0xFF000000 | Draw.BG;
    }

    /** Skip both halves of the Mojang Studios logo. */
    @Redirect(method = "extractRenderState", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blit(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIFFIIIIIII)V"))
    private void cyberui$noMojangLogo(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier texture, int x, int y,
                                      float u, float v, int w, int h, int srcW, int srcH, int texW, int texH, int color) {
    }

    /** Remember whether vanilla wanted the bar; ours is drawn in our pass. */
    @Inject(method = "extractProgressBar", at = @At("HEAD"), cancellable = true)
    private void cyberui$captureBar(GuiGraphicsExtractor g, int x0, int y0, int x1, int y1, float fade, CallbackInfo ci) {
        this.cyberui$barShown = true;
        this.cyberui$barAlpha = fade;
        ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cyberui$render(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
        long now = Util.getMillis();
        float fadeOut = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / 1000.0F : -1.0F;
        float fadeInT = this.fadeInStart > -1L ? (now - this.fadeInStart) / 500.0F : -1.0F;
        float alpha;
        if (fadeOut >= 1.0F) {
            alpha = 1.0F - Draw.clamp(fadeOut - 1.0F, 0.0F, 1.0F);
        } else if (this.fadeIn) {
            alpha = Draw.clamp(fadeInT, 0.0F, 1.0F);
        } else {
            alpha = 1.0F;
        }
        float barAlpha = this.cyberui$barShown ? this.cyberui$barAlpha : 0.0F;
        LoadingScreen.render(g, alpha, this.currentProgress, barAlpha);
        this.cyberui$barShown = false;
    }
}
