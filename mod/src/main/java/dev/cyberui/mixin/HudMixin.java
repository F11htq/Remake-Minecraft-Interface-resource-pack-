package dev.cyberui.mixin;

import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import dev.cyberui.client.HotbarFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** The animated hotbar: see {@link HotbarFx}. */
@Mixin(Hud.class)
public abstract class HudMixin {

    @Shadow
    protected abstract @Nullable Player getCameraPlayer();

    @Unique
    private @Nullable Player cyberui$hotbarPlayer;
    @Unique
    private boolean cyberui$lifted;

    @Inject(method = "extractItemHotbar", at = @At("HEAD"))
    private void cyberui$hotbarStart(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
        this.cyberui$hotbarPlayer = this.getCameraPlayer();
        if (this.cyberui$hotbarPlayer != null) {
            HotbarFx.begin(g, this.cyberui$hotbarPlayer);
        }
    }

    @Inject(method = "extractItemHotbar", at = @At("RETURN"))
    private void cyberui$hotbarEnd(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (this.cyberui$hotbarPlayer != null) {
            HotbarFx.end(g, this.cyberui$hotbarPlayer);
            this.cyberui$hotbarPlayer = null;
        }
    }

    /** The selection sprite is drawn by HotbarFx at its gliding position; other sprites pass through. */
    @Redirect(method = "extractItemHotbar", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void cyberui$selection(GuiGraphicsExtractor g, RenderPipeline pipeline, Identifier sprite, int x, int y,
                                   int w, int h) {
        if (HotbarFx.SELECTION.equals(sprite) && this.cyberui$hotbarPlayer != null) {
            HotbarFx.selection(g, x, y, w, h);
        } else {
            g.blitSprite(pipeline, sprite, x, y, w, h);
        }
    }

    @Inject(method = "extractSlot", at = @At("HEAD"))
    private void cyberui$liftStart(GuiGraphicsExtractor g, int x, int y, DeltaTracker deltaTracker, Player player,
                                   ItemStack stack, int seed, CallbackInfo ci) {
        float lift = this.cyberui$hotbarPlayer != null ? HotbarFx.itemLift(x, g.guiWidth()) : 0.0F;
        this.cyberui$lifted = lift > 0.01F;
        if (this.cyberui$lifted) {
            g.pose().pushMatrix();
            g.pose().translate(0.0F, -lift);
        }
    }

    @Inject(method = "extractSlot", at = @At("RETURN"))
    private void cyberui$liftEnd(GuiGraphicsExtractor g, int x, int y, DeltaTracker deltaTracker, Player player,
                                 ItemStack stack, int seed, CallbackInfo ci) {
        if (this.cyberui$lifted) {
            g.pose().popMatrix();
            this.cyberui$lifted = false;
        }
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"))
    private void cyberui$hotbarOverlay(GuiGraphicsExtractor g, DeltaTracker deltaTracker, CallbackInfo ci) {
        HotbarFx.overlay(g);
    }

    @Redirect(method = "extractSelectedItemName", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void cyberui$namePlate(GuiGraphicsExtractor g, Font font, Component name, int x, int y, int width, int color) {
        HotbarFx.namePlate(g, font, name, x, y, width, color);
    }
}
