package dev.cyberui.mixin;

import dev.cyberui.client.BeamCarrier;
import dev.cyberui.client.PortalBeam;
import net.minecraft.client.renderer.blockentity.state.EndPortalRenderState;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;

@Mixin(EndPortalRenderState.class)
public abstract class EndPortalRenderStateMixin implements BeamCarrier {

    @Unique
    private PortalBeam.@Nullable Snapshot cyberui$beam;

    @Override
    public PortalBeam.@Nullable Snapshot cyberui$beam() {
        return this.cyberui$beam;
    }

    @Override
    public void cyberui$setBeam(PortalBeam.@Nullable Snapshot beam) {
        this.cyberui$beam = beam;
    }
}
