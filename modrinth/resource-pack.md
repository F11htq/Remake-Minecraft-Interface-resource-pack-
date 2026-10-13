<!-- Modrinth project: CYBER//UI (Resource Pack)
Summary (short description field):
A black, yellow and red cyberpunk redesign of every Minecraft menu and inventory. Animated buttons, glitching tooltips, targeting slot highlights. The world and HUD stay vanilla.
-->

# CYBER//UI

A cyberpunk redesign of **menus and interfaces only** for Minecraft 1.21.4. Blocks, mobs, the world and the in-game HUD (hotbar, hearts, XP bar, crosshair) stay exactly as they are; only boss health bars get the CYBER//UI look.

Black panels, acid-yellow frames, neon-red accents and a bit of glitch.

## What changes

| Element | Effect |
|---|---|
| Buttons | Dark plates with a cut corner and a yellow marker; on hover a red glow, yellow frame and glitch flashes |
| Sliders | Tick-marked track, hazard-striped handle that crawls on hover |
| Text fields | Yellow frame and blinking red corners when focused |
| Checkboxes, tabs, scroll bars | Same style |
| Tooltips | Black scanline glass, yellow frame, an occasional colour glitch |
| Slot hover | Targeting brackets lock onto the slot |
| Inventory and all containers | Dark panels, yellow and red top line, yellow titles; the vanilla layout is kept |
| Creative inventory, recipe book, advancements | Same palette |
| Menu backgrounds, separators | Near-black background with scanlines, yellow and red separators |
| Boss bars | Dark chamfered casing with a neon striped fill that crawls (all 7 colours), tick-mark notches |
| Title logo | Yellow and red with a chromatic "double image" |
| Title panorama | The 1.21.4 panorama in a night-city colour grade with scanlines and a vignette |
| Loading screen | Black instead of red when resources are reloaded |

Container titles are drawn in a hard-coded dark grey that would be unreadable on dark panels. A small core shader recolours **only that colour and only on menu screens**, so text in the world is never affected.

## Want more?

Add the **CYBER//UI mod** (Fabric) for things a resource pack cannot do: an animated title screen with a left-aligned button column, a glitching logo, a custom loading screen that also replaces the very first startup screen, screen transitions and gliding slot highlights in inventories.

## Installation

1. Download the zip and put it, **without unpacking**, into your `resourcepacks` folder (Options → Resource Packs → Open Pack Folder).
2. Enable **CYBER//UI** in the resource pack list.

## Notes

- The very first loading screen at game start stays red: Minecraft shows it before resource packs are loaded. The CYBER//UI mod replaces it.
- Works with Sodium and other performance mods.

## License

Code and original textures: MIT. Re-coloured Minecraft textures (container screens, logo, panorama) remain the property of Mojang AB / Microsoft.

**Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.**
