# FHASIX standalone - ALL-IN-ONE source (Minecraft 1.21.11, Fabric)

This folder is the COMPLETE project with every update already applied. Build ONLY from this folder
(gradle clean build). There are no extra update files to apply.

What is in it (on top of the original Fhasix UI Theme):
- Title-screen grid icon opens the Mod Menu; Right Shift (in a world) opens the launcher.
- Mod Menu + per-mod settings screens; blurred background; redesigned launcher.
- Working mods: FPS Display, CPS Display, Armor Status, Armor Bar, Block Indicator, Block Overlay,
  Attack Indicator (vanilla by default, optional meter), Autohide HUD, Camera (freelook + offsets),
  UHC Overlay (bigger dropped items + ore outlines), Animations (block-hit), No Crash, No Boss Bar,
  Custom Crosshair, Custom Fog, Health Indicator (was Damage Indicator), Direction, Dark Mode.
- Custom font removed (vanilla font).
- Dark Mode bundles the "Default Dark Mode" resource pack by nebulr (CC BY-NC-SA 4.0, non-commercial):
  src/main/resources/resourcepacks/dark_mode (keep LICENSE.txt + CREDITS.txt).
- Removed from the project root: the stale New_*.java copies and the duplicate cape_optix.png.

All Fhasix mod code is in src/main/java/com/fhasix/uitheme (files named New*.java + mixin/New*Mixin.java).
The Mixins use MixinExtras (@WrapOperation / @WrapMethod / @Local), which ships with Fabric Loader, and every
injection of the new mods has require = 0 (a target that no longer exists is skipped, not a crash).

NEVER COMPILED - likely small compile fixes (each file has a note at the top):
- NewHud.java          HudElementRegistry / VanillaHudElements (fallback: HudRenderCallback)
- NewBlockOverlay.java, NewOreHighlight.java   world-render event + VertexRendering / RenderLayer calls
- mixin/NewHandMixin.java, NewItemEntityMixin.java, NewFogMixin.java   target names
- NewDarkMode.java     ResourceManagerHelper.registerBuiltinResourcePack (newer name: ResourceLoader.registerBuiltinPack)
- NewAutohide.java / NewNoBossBar.java / NewCrosshair.java   HudElementRegistry.replaceElement
- NewCamera.java       Perspective / options calls
Fix with the smallest change and keep behavior.


## Update: fixes + new mods
- Custom Fog: sky no longer tinted (NewFog.skyEnd / NewFogMixin ordinal 4); full colour picker for the fog colour.
- Health Indicator (was Damage Indicator): health bar + number above players and mobs (NewDamageIndicator, HUD projection). DELETE mixin/NewNameTagMixin.java.
- Attack Indicator: bow / crossbow / trident charge bar under the crosshair (Vanilla crosshair style).
- New mods: Coordinates, Potion Effects, Keystrokes (Background + Border options), Hitbox (colour + size), Shulker Tooltips (no options).
- Mod Menu: animated Enabled pill. Icon atlas extended to 640x768 (cells 23-27).

## Update 2
- New mods: No Scoreboard (no settings), Full Bright (Light level), FOV Changer (30-180), Time Changer (time of day), Weather Changer (Clear / Rain / Thunderstorm), TNT Timer (no settings). Icon atlas now 640x896 (cells 28-33).
- Health Indicator: the bar is now a row of real vanilla hearts.
- New mixin: SimpleOptionAccessor (lets Full Bright / FOV Changer go past the vanilla slider limits).

## Update 3
- NewScreenLogo: Fhasix logo (title-screen logo) in the bottom-right corner of vanilla screens; not on the title screen, chat or Fhasix screens.

## Update 4 (startx patch)
- Custom Fog: only active in a world; fog is forced ON while the mod is on (values that mean "fog off" are replaced by real distances); default Fog distance 50%; sky end still pushed out so the sky keeps its colour.
- GUI scale lock: Fhasix screens (title, mod menu, store, cosmetics, discover, screenshots, skin pop-up) always use GUI scale 2 (NewGuiScale + NewSetScreenMixin + NewWindowMixin). HUD editor, crash screen and world-only banner are not locked.
- Own name tag: replaced by NewOwnTag, a HUD overlay (see Update 5).
- Dark Mode resource pack hidden from the Resource Packs screen (NewPackListMixin).

## Update 5 (performance)
- Mod Menu top-bar gear -> NewPerformanceScreen (FPS modes: Particles, Culling, Lazy Chunk Loading, Render Distance, Simulation Distance, Entity Detail, Visual Details; each On/Off + Small/Medium/High).
- First launch: NewDeviceSelectorScreen over the title screen (High End / Medium / Low End, ripple Confirm button). NewPerf.applyDevice sets Minecraft's video options + the FPS modes. Re-openable from Performance > Device.
- Mixins: NewParticleMixin, NewEntityCullMixin, NewChunkDataMixin. Icon atlas 640x1024 (cells 34-36 = device icons).
- Own name tag: NewOwnTag overlay (F logo + your name above your head in third person, always on). NewOwnNameTagMixin and assets/minecraft/font/default.json are removed.

## Update 6 (performance rework)
- Performance is now the Mod Menu itself (NewModMenuScreen perf view, top-bar gear toggles Mod Menu <-> Performance); NewPerformanceScreen is deleted (it crashed in-world: double background blur). Device selector no longer blurs twice either.
- New FPS modes: Frame Rate, Effects & Animations, HUD Optimizer, Background Limiter (11 modes total). Icon atlas 640x1280 (cells 37-47).
- Particles High now also stops block break / crack particles (ParticleManager.addParticle(Particle) hook).
- Render Distance High caps at 2 chunks.

## Update 7 (merged with perf2)
- Own name tag is the REAL vanilla tag again (NewOwnNameTagMixin on LivingEntityRenderer: hasLabel forced true for you, F logo glyph from assets/minecraft/font/default.json in front of the name). NewOwnTag overlay deleted.
- Two more FPS modes: Texture Detail (mipmap levels), Menu FPS Saver (frame cap while a menu is open). 13 modes. Icon cells 48-49.

## Update 8
- Crash fix: "Can only blur once per frame" (IllegalStateException) while the loading overlay renders the device selector on some launchers. Every Fhasix screen now draws its vanilla background through safeBackground(), which catches that exception instead of crashing.

## Update 9 (crash shield)
- NewSafe: every Fhasix tick handler, HUD element, screen hook and mixin entry point is wrapped; an error is logged once and swallowed, and a feature that fails 5 times is switched off for the session.
- NewNoCrash now also catches errors that come from Fhasix code even when the No Crash mod is off.
- NewPerf: video options are never changed while the game is still loading (c.getOverlay() != null); Lazy Chunk Loading never holds chunks until the player has been in the world 5 s and drops held chunks of another world; per-option try/catch in device profiles.
- Own name tag only sets the tag when the game gave it a position; HUD elements are isolated one by one (one broken element cannot hide the rest).
- The crash in the log (Can only blur once per frame in NewPerformanceScreen) is from the old build; the current source has no such screen and all backgrounds go through safeBackground().

## Update 10
- Also shielded: title screen (f6) init/render, local skin (skin), cape, custom loading screen. A skin/cosmetics error can no longer stop you joining a world.
- No Crash: when a Fhasix screen itself fails, only that screen is closed (max 3 times per 10 s) and you stay in the world.

## Update 11 (overhead check)
- Crash-shield wrappers no longer allocate anything per frame (State kept per wrapper, inline try/catch on hot paths, HUD element keys built once).
- Performance mode checks in the particle / entity-culling hot paths read a cached int array (refreshed every tick and on change) instead of building strings and looking up settings every call - cheaper than before.
