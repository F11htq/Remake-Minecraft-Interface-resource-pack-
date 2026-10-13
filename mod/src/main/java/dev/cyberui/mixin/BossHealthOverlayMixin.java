package dev.cyberui.mixin;

import java.util.Map;
import java.util.UUID;

import dev.cyberui.client.BossBarFx;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Replaces the vanilla boss bars with CYBER//UI frames, effects and the GG animation. */
@Mixin(BossHealthOverlay.class)
public abstract class BossHealthOverlayMixin {

    @Shadow
    @Final
    Map<UUID, LerpingBossEvent> events;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void cyberui$render(GuiGraphics g, CallbackInfo ci) {
        BossBarFx.render(g, this.events);
        ci.cancel();
    }
}
