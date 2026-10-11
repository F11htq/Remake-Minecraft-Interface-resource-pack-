package dev.cyberui.client;

import java.util.List;

import net.minecraft.resources.ResourceLocation;

/** Implemented on CubeMap by CubeMapMixin: lets the mod swap the panorama faces. */
public interface CubeMapAccess {

    void cyberui$setSides(List<ResourceLocation> sides);
}
