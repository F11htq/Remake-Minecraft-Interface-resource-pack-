package dev.cyberui.client;

import net.minecraft.resources.Identifier;

/**
 * Points the title-screen panorama at the colour-graded panorama bundled in the
 * mod, so the title screen keeps the CYBER//UI look even without the resource pack.
 */
public final class Panoramas {

    private static final String VANILLA_PATH = "textures/gui/title/background/panorama";
    private static final Identifier CYBER =
            Identifier.fromNamespaceAndPath("cyberui", "textures/gui/panorama/autumn/panorama");

    private Panoramas() {
    }

    /** The cube map base to load instead of {@code base} (faces are base + "_0.png" .. "_5.png"). */
    public static Identifier replace(Identifier base) {
        if (base != null && Identifier.DEFAULT_NAMESPACE.equals(base.getNamespace()) && VANILLA_PATH.equals(base.getPath())) {
            return CYBER;
        }
        return base;
    }
}
