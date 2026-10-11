package dev.cyberui.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import net.minecraft.resources.ResourceLocation;

/**
 * Swaps the faces of the title-screen panorama for one of the colour-graded
 * official panoramas bundled in the mod, picked at random.
 */
public final class Panoramas {

    private static final String[] NAMES = {
        "ocean", "village", "bees", "nether", "caves", "peaks", "swamp", "cherry", "trial_chambers", "pale_garden",
    };
    private static final String VANILLA_PATH = "textures/gui/title/background/panorama";
    private static final List<CubeMapAccess> CUBE_MAPS = new ArrayList<>();
    private static final Random RANDOM = new Random();
    private static int last = -1;

    private Panoramas() {
    }

    /** Called when a CubeMap is created; keeps the title panorama. */
    public static void track(ResourceLocation base, CubeMapAccess cubeMap) {
        if (base != null && VANILLA_PATH.equals(base.getPath())) {
            CUBE_MAPS.add(cubeMap);
        }
    }

    /** Points every tracked title panorama at a random bundled panorama. */
    public static void pickRandom() {
        if (CUBE_MAPS.isEmpty()) {
            return;
        }
        int pick = RANDOM.nextInt(NAMES.length);
        if (pick == last) {
            pick = (pick + 1 + RANDOM.nextInt(NAMES.length - 1)) % NAMES.length;
        }
        last = pick;
        List<ResourceLocation> sides = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            sides.add(ResourceLocation.fromNamespaceAndPath(
                    "cyberui", "textures/gui/panorama/" + NAMES[pick] + "/panorama_" + i + ".png"));
        }
        List<ResourceLocation> faces = List.copyOf(sides);
        for (CubeMapAccess cubeMap : CUBE_MAPS) {
            cubeMap.cyberui$setSides(faces);
        }
    }
}
