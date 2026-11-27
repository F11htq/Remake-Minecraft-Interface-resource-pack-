<!-- Modrinth project: CYBER//UI (Mod, Fabric, client side only)
Summary (short description field):
Cyberpunk title screen, custom loading screen, animated hotbar and smooth inventories for Fabric 26.3. Animated button column, glitching logo, screen transitions and gliding slot highlights.
-->

# CYBER//UI — mod

The companion mod for the **CYBER//UI** resource pack. It adds what a resource pack cannot change: the layout of the title screen, the loading screen and animations. Client side only, and it only needs Fabric Loader (Fabric API is not required).

## Title screen

- Buttons in a column on the left that slide in one after another when the menu opens. Mod Menu's button and the language and accessibility buttons are included.
- The **MINECRAFT / JAVA EDITION** logo moves to the left and comes alive:
  - assembles itself from sliding slices when the menu opens;
  - pulsing neon glow;
  - red and cyan chromatic split;
  - glitch bursts every few seconds;
  - a scan line sweep and a gentle float.
- A **SYS//STAT** HUD panel slides in on the right, and a hazard strip runs along the bottom.
- The 26.3 autumn forest panorama turned into a night scene (violet sky, neon sun, a city glow on the horizon), even without the resource pack.
- An **animated hotbar**: it boots up when you join a world or respawn (rises into place, slots come online one by one behind a scan line), the selection frame glides between slots with afterimages and a red/cyan split, the selected item lifts and its slot number flashes, picked-up items flash their slot, nearly broken tools pulse red, and the item name is typed out on a name plate in its rarity colour.

## Loading screen

Replaces the red Mojang Studios screen, **including the very first one at game start**:

- dark grid background with a sweeping scan bar;
- the logo drops in from above and glitches now and then;
- a yellow striped progress bar showing real loading progress, plus a spinner and a percentage;
- a scrolling boot log ("LOADING TEXTURES… LINKING SHADERS…").

## Screen transitions

On menu screens (title, options, world and server selection, pause menu) a dark scanline shutter covers the new screen and slides up, its glowing yellow and red edge sweeping across the menu. Inventories are never affected.

## Smooth inventories

- The slot hover frame **glides** from slot to slot instead of jumping, with targeting brackets that tighten as it settles.
- Inventories and containers fade in softly with a scan line when opened.
- Creative inventory: smooth scroll bar and a quick sweep when switching tabs.

## Boss bars

Every boss gets its own ornate animated bar: a spear-shaped frame with the boss emblem in the centre and the boss name on a neon plate.

- **Ender Dragon**: dragon wings and head, magenta fill.
- **Wither**: three wither heads and floating skulls, blue fill.
- **Raids**: illager axe and banner.
- **Strong mobs without a vanilla boss bar** get one too: **Warden**, **Iron Golem**, **Ravager**, **Evoker** and **Guardian**. Their bar appears once they take damage near you; the Warden, Ravager, Evoker and Elder Guardian also show it when they come within 16 blocks.
- **Other bosses** (including modded ones): cyberpunk tech frame.

All bars have a damage trail, hit shake and flash, a low-health pulse, glitches and themed particles (sparks, bubbles, leaves, magic orbs...). When a boss dies (or a raid is won), a spinning 3D **GG** appears with a shockwave and the name of the defeated boss. The Ender Dragon's bar first blows apart: it overloads, glitches and shatters into flying pieces with sparks and a shockwave.

## End exit portal beam

When the exit portal opens, a white-hot needle drops out of the sky and pierces the island. On impact it bursts into a thick purple energy beam as wide as the bedrock rim, reaching up into the sky and down into the void below the island: a white-hot core inside streaming energy layers and a soft halo. A shock front rolls out across the island with a thunderclap, and the ground shakes.

## Installation

1. Install Fabric Loader (0.20 or newer) for Minecraft 26.3. The game needs Java 25.
2. Put the jar into your `mods` folder.
3. Recommended: also enable the **CYBER//UI** resource pack.

Do not use it together with FancyMenu layouts for the title screen or with Drippy Loading Screen: they change the same screens.

## License

Code and original textures: MIT. The bundled panorama and loading-screen logo are re-coloured Minecraft textures and remain the property of Mojang AB / Microsoft.

**Not an official Minecraft product. Not approved by or associated with Mojang or Microsoft.**
