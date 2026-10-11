package dev.cyberui.mixin;

import dev.cyberui.client.Transition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
public abstract class MinecraftMixin {

    @Shadow
    public Screen screen;

    @Inject(method = "setScreen", at = @At("TAIL"))
    private void cyberui$onSetScreen(Screen requested, CallbackInfo ci) {
        // the game may swap the requested screen (e.g. null -> title screen)
        Transition.onScreenSet(this.screen);
    }
}
