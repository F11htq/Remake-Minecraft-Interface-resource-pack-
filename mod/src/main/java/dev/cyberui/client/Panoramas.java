package dev.cyberui.client;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.resources.ResourceLocation;

/**
 * Points the title-screen panorama at the colour-graded 1.21.4 panorama bundled
 * in the mod, so the title screen keeps the CYBER//UI look even without the
 * resource pack.
 */
public final class Panoramas {

    private static final String NAME = "pale_garden";
    private static final String VANILLA_PATH = "textures/gui/title/background/panorama";
    private static final List<CubeMapAccess> CUBE_MAPS = new ArrayList<>();

    private Panoramas() {
    }

    /** Called when a CubeMap is created; keeps the title panorama. */
    public static void track(ResourceLocation base, CubeMapAccess cubeMap) {
        if (base != null && VANILLA_PATH.equals(base.getPath())) {
            CUBE_MAPS.add(cubeMap);
        }
    }

    /** Applies the bundled panorama to every tracked title panorama. */
    public static void apply() {
        List<ResourceLocation> sides = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            sides.add(ResourceLocation.fromNamespaceAndPath(
                    "cyberui", "textures/gui/panorama/" + NAME + "/panorama_" + i + ".png"));
        }
        List<ResourceLocation> faces = List.copyOf(sides);
        for (CubeMapAccess cubeMap : CUBE_MAPS) {
            cubeMap.cyberui$setSides(faces);
        }
    }
}
