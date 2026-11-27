#!/usr/bin/env bash
# Fails when a Minecraft method or field the mixins rely on is missing from the
# 26.3 jar (catches renames before they silently disable an effect in game).
JAR=$(find ~/.gradle/caches/fabric-loom -name "minecraft-merged-*26.3.jar" | grep -v sources | head -n 1)
echo "Checking against $JAR"
FAIL=0
check() { if ! javap -p -cp "$JAR" "$1" | grep -qE "$2"; then echo "MISSING in $1: $2"; FAIL=1; fi; }
check net.minecraft.client.gui.screens.Screen "extractRenderStateWithTooltipAndSubtitles\("
check net.minecraft.client.gui.Gui "void setScreen\(|Screen screen\(\)"
check net.minecraft.client.gui.Hud "extractItemHotbar\(|extractSlot\(|extractSelectedItemName\(|extractHotbarAndDecorations\(|getCameraPlayer\("
check net.minecraft.client.gui.GuiGraphicsExtractor "void blitSprite\(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.resources.Identifier, int, int, int, int\)"
check net.minecraft.client.gui.GuiGraphicsExtractor "void textWithBackdrop\(net.minecraft.client.gui.Font, net.minecraft.network.chat.Component, int, int, int, int\)"
check net.minecraft.client.gui.GuiGraphicsExtractor "void blit\(com.mojang.renderpearl.api.pipeline.RenderPipeline, net.minecraft.resources.Identifier, int, int, float, float, int, int, int, int, int, int, int\)"
check net.minecraft.client.gui.components.BossHealthOverlay "events;|void extractRenderState\("
check net.minecraft.client.gui.components.LogoRenderer "void extractRenderState\(net.minecraft.client.gui.GuiGraphicsExtractor, int, float\)"
check net.minecraft.client.gui.components.SplashRenderer "void extractRenderState\(net.minecraft.client.gui.GuiGraphicsExtractor, int, net.minecraft.client.gui.Font, float\)"
check net.minecraft.client.gui.screens.TitleScreen "void extractRenderState\(|void init\(\)"
check net.minecraft.client.gui.screens.LoadingOverlay "extractProgressBar\(|currentProgress;|fadeOutStart;|fadeInStart;|boolean fadeIn;"
check net.minecraft.client.gui.screens.inventory.AbstractContainerScreen "extractSlotHighlightBack\(|extractSlotHighlightFront\(|hoveredSlot;|leftPos;|topPos;|imageWidth;|imageHeight;"
check net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen "selectTab\(|scrollOffs;|void extractBackground\("
check net.minecraft.client.renderer.CubeMap "Identifier location;"
check net.minecraft.client.resources.ClientPackSource "createVanillaPackSource\("
check net.minecraft.server.packs.VanillaPackResourcesBuilder "pushJarResources\(|exposeNamespace\(|pushUniversalPath\("
check net.minecraft.client.Camera "alignWithEntity\(float\)|setRotation\(float, float\)|float xRot\(\)|float yRot\(\)|Entity entity\(\)"
check net.minecraft.client.renderer.blockentity.TheEndPortalRenderer "void submit\(net.minecraft.client.renderer.blockentity.state.EndPortalRenderState"
check net.minecraft.client.renderer.blockentity.AbstractEndPortalRenderer "void extractRenderState\(T, S, float, net.minecraft.world.phys.Vec3"
check net.minecraft.client.renderer.blockentity.BlockEntityRenderer "shouldRenderOffScreen\(\)|shouldRender\(T, net.minecraft.world.phys.Vec3\)"
check net.minecraft.client.renderer.rendertype.RenderTypes "beaconBeam\(|lightning\(\)"
check net.minecraft.client.renderer.SubmitNodeCollector "submitCustomGeometry|interface|extends"
exit $FAIL
