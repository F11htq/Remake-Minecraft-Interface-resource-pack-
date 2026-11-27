package dev.cyberui.mixin;

import dev.cyberui.client.Transition;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Screen.class)
public abstract class ScreenMixin {

    /** After tooltips and everything else, so the shutter covers the whole screen. */
    @Inject(method = "extractRenderStateWithTooltipAndSubtitles", at = @At("TAIL"))
    private void cyberui$transition(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
        Transition.render((Screen) (Object) this, g);
    }
}
