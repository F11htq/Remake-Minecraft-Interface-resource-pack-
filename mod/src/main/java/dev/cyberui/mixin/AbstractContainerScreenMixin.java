package dev.cyberui.mixin;

import dev.cyberui.client.InventoryFx;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.Slot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Inventories and containers: the hover frame glides smoothly between slots
 * instead of jumping, and the panel is revealed softly when it opens.
 */
@Mixin(AbstractContainerScreen.class)
public abstract class AbstractContainerScreenMixin extends Screen {

    @Shadow
    protected Slot hoveredSlot;
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    protected int imageWidth;
    @Shadow
    protected int imageHeight;

    @Unique
    private final InventoryFx.Glide cyberui$glide = new InventoryFx.Glide();
    @Unique
    private long cyberui$openedAt = 0L;

    protected AbstractContainerScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cyberui$opened(CallbackInfo ci) {
        if (this.cyberui$openedAt == 0L) {
            this.cyberui$openedAt = Util.getMillis();
        }
    }

    /** The vanilla highlight jumps from slot to slot; ours is drawn in render(). */
    @Inject(method = "renderSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void cyberui$noVanillaBack(GuiGraphics g, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "renderSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void cyberui$noVanillaFront(GuiGraphics g, CallbackInfo ci) {
        ci.cancel();
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cyberui$smooth(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        Slot slot = this.hoveredSlot;
        boolean hovered = slot != null && slot.isActive() && slot.isHighlightable();
        int tx = hovered ? this.leftPos + slot.x : 0;
        int ty = hovered ? this.topPos + slot.y : 0;
        this.cyberui$glide.render(g, hovered, tx, ty);
        InventoryFx.reveal(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, this.cyberui$openedAt);
    }
}
