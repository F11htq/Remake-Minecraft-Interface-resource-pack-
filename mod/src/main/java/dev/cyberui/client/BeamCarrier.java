package dev.cyberui.client;

import org.jspecify.annotations.Nullable;

/** Added to EndPortalRenderState: the beam to draw for this portal block, if any. */
public interface BeamCarrier {

    PortalBeam.@Nullable Snapshot cyberui$beam();

    void cyberui$setBeam(PortalBeam.@Nullable Snapshot beam);
}
