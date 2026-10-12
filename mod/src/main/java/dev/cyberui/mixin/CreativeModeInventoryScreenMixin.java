package dev.cyberui.mixin;

import dev.cyberui.client.InventoryFx;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.item.CreativeModeTab;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Creative inventory: the scroll bar thumb glides to its position and the
 * item grid fades in with a sweep when switching tabs.
 */
@Mixin(CreativeModeInventoryScreen.class)
public abstract class CreativeModeInventoryScreenMixin {

    @Shadow
    private float scrollOffs;

    @Unique
    private float cyberui$shownScroll = -1.0F;
    @Unique
    private float cyberui$realScroll;
    @Unique
    private long cyberui$tabChangedAt = 0L;
    @Unique
    private long cyberui$lastFrame = 0L;

    @Inject(method = "selectTab", at = @At("TAIL"))
    private void cyberui$tabChanged(CreativeModeTab tab, CallbackInfo ci) {
        this.cyberui$tabChangedAt = Util.getMillis();
    }

    /** Draw the scroll thumb at a smoothed position, then restore the real value. */
    @Inject(method = "renderBg", at = @At("HEAD"))
    private void cyberui$smoothScrollStart(GuiGraphics g, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        long now = Util.getMillis();
        float dt = this.cyberui$lastFrame == 0L ? 0.016F : Math.min(0.1F, (now - this.cyberui$lastFrame) / 1000.0F);
        this.cyberui$lastFrame = now;
        this.cyberui$realScroll = this.scrollOffs;
        if (this.cyberui$shownScroll < 0.0F) {
            this.cyberui$shownScroll = this.scrollOffs;
        }
        float k = 1.0F - (float) Math.exp(-dt * 18.0F);
        this.cyberui$shownScroll += (this.scrollOffs - this.cyberui$shownScroll) * k;
        this.scrollOffs = this.cyberui$shownScroll;
    }

    @Inject(method = "renderBg", at = @At("RETURN"))
    private void cyberui$smoothScrollEnd(GuiGraphics g, float partialTick, int mouseX, int mouseY, CallbackInfo ci) {
        this.scrollOffs = this.cyberui$realScroll;
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cyberui$tabFade(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        if (this.cyberui$tabChangedAt != 0L) {
            AbstractContainerScreenAccessor self = (AbstractContainerScreenAccessor) this;
            // item grid: 9 x 5 slots starting at (9, 18) inside the panel
            InventoryFx.fade(g, self.cyberui$leftPos() + 8, self.cyberui$topPos() + 17, 164, 92,
                    this.cyberui$tabChangedAt, 160L);
        }
    }
}
