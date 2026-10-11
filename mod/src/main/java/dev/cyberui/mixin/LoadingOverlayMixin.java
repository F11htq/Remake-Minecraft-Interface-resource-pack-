package dev.cyberui.mixin;

import java.util.function.Function;

import dev.cyberui.client.Draw;
import dev.cyberui.client.LoadingScreen;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.LoadingOverlay;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
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
    private int[] cyberui$bar;
    @Unique
    private float cyberui$barAlpha = 1.0F;

    /** Background fills (used while fading) become dark instead of red. */
    @ModifyArg(method = "render", index = 5, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;fill(Lnet/minecraft/client/renderer/RenderType;IIIII)V"))
    private int cyberui$darkBackground(int color) {
        return (color & 0xFF000000) | Draw.BG;
    }

    /** Skip both halves of the Mojang Studios logo. */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphics;blit(Ljava/util/function/Function;Lnet/minecraft/resources/ResourceLocation;IIFFIIIIIII)V"))
    private void cyberui$noMojangLogo(GuiGraphics g, Function<ResourceLocation, RenderType> renderType,
                                      ResourceLocation texture, int x, int y, float u, float v, int w, int h,
                                      int uw, int vh, int texW, int texH, int color) {
    }

    /** Remember where vanilla wanted the bar; it is drawn in our pass. */
    @Inject(method = "drawProgressBar", at = @At("HEAD"), cancellable = true)
    private void cyberui$captureBar(GuiGraphics g, int x0, int y0, int x1, int y1, float opacity, CallbackInfo ci) {
        this.cyberui$bar = new int[]{x0, y0, x1, y1};
        this.cyberui$barAlpha = opacity;
        ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cyberui$render(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        long now = Util.getMillis();
        float fadeOut = this.fadeOutStart > -1L ? (now - this.fadeOutStart) / 1000.0F : -1.0F;
        float fadeInT = this.fadeInStart > -1L ? (now - this.fadeInStart) / 500.0F : -1.0F;
        float alpha;
        boolean opaqueBg;
        if (fadeOut >= 1.0F) {
            alpha = 1.0F - Draw.clamp(fadeOut - 1.0F, 0.0F, 1.0F);
            opaqueBg = false;
        } else if (this.fadeIn) {
            alpha = Draw.clamp(fadeInT, 0.0F, 1.0F);
            opaqueBg = false;
        } else {
            // vanilla just clears the screen to the brand colour here
            alpha = 1.0F;
            opaqueBg = true;
        }
        float barAlpha = this.cyberui$bar != null ? this.cyberui$barAlpha : 0.0F;
        LoadingScreen.render(g, alpha, opaqueBg, this.currentProgress, this.cyberui$bar, barAlpha);
        this.cyberui$bar = null;
    }
}
