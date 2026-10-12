package dev.cyberui.mixin;

import java.util.ArrayList;
import java.util.List;

import dev.cyberui.client.LogoFx;
import dev.cyberui.client.Panoramas;
import dev.cyberui.client.TitleLayout;
import net.minecraft.Util;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.LogoRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(TitleScreen.class)
public abstract class TitleScreenMixin extends Screen {

    @Unique
    private List<TitleLayout.Placed> cyberui$placed = new ArrayList<>();
    @Unique
    private long cyberui$openedAt = 0L;
    @Unique
    private int cyberui$childCount = -1;

    protected TitleScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void cyberui$layout(CallbackInfo ci) {
        if (this.cyberui$openedAt == 0L) {
            // first time this title screen is shown: new panorama, play the slide-in
            this.cyberui$openedAt = Util.getMillis();
            Panoramas.pickRandom();
        }
        this.cyberui$relayout();
    }

    @Unique
    private void cyberui$relayout() {
        this.cyberui$placed = TitleLayout.apply(this.children(), this.width, this.height);
        this.cyberui$childCount = this.children().size();
    }

    @Inject(method = "render", at = @At("HEAD"))
    private void cyberui$animate(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        // other mods (e.g. Mod Menu) may add buttons after our init hook ran
        if (this.children().size() != this.cyberui$childCount) {
            this.cyberui$relayout();
        }
        TitleLayout.animate(this.cyberui$placed, this.cyberui$openedAt);
    }

    @Inject(method = "render", at = @At("TAIL"))
    private void cyberui$decor(GuiGraphics g, int mouseX, int mouseY, float partialTick, CallbackInfo ci) {
        TitleLayout.renderDecor(g, this.width, this.height, this.cyberui$openedAt);
    }

    /** Draw the logo at the left, with glow, chromatic split, glitches and an intro. */
    @Redirect(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/LogoRenderer;renderLogo(Lnet/minecraft/client/gui/GuiGraphics;IF)V"))
    private void cyberui$logoFx(LogoRenderer logo, GuiGraphics g, int screenWidth, float alpha) {
        LogoFx.render(logo, g, alpha, this.cyberui$openedAt);
    }

    /** Keep the splash text next to the moved logo. */
    @ModifyArg(method = "render", index = 1, at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/SplashRenderer;render(Lnet/minecraft/client/gui/GuiGraphics;ILnet/minecraft/client/gui/Font;I)V"))
    private int cyberui$splashAtLeft(int screenWidth) {
        return TitleLayout.LOGO_FAKE_WIDTH;
    }
}
