package dev.cyberui.mixin;

import dev.cyberui.client.InventoryFx;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
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
    protected @Nullable Slot hoveredSlot;
    @Shadow
    protected int leftPos;
    @Shadow
    protected int topPos;
    @Shadow
    @Final
    protected int imageWidth;
    @Shadow
    @Final
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

    /** The vanilla highlight jumps from slot to slot; ours is drawn in place of the front one. */
    @Inject(method = "extractSlotHighlightBack", at = @At("HEAD"), cancellable = true)
    private void cyberui$noVanillaBack(GuiGraphicsExtractor g, CallbackInfo ci) {
        ci.cancel();
    }

    /** Drawn after the slots, in panel coordinates, so it sits over the items and under tooltips. */
    @Inject(method = "extractSlotHighlightFront", at = @At("HEAD"), cancellable = true)
    private void cyberui$glide(GuiGraphicsExtractor g, CallbackInfo ci) {
        Slot slot = this.hoveredSlot;
        boolean hovered = slot != null && slot.isActive() && slot.isHighlightable();
        this.cyberui$glide.render(g, hovered, hovered ? slot.x : 0, hovered ? slot.y : 0);
        ci.cancel();
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void cyberui$reveal(GuiGraphicsExtractor g, int mouseX, int mouseY, float a, CallbackInfo ci) {
        InventoryFx.reveal(g, this.leftPos, this.topPos, this.imageWidth, this.imageHeight, this.cyberui$openedAt);
    }
}
