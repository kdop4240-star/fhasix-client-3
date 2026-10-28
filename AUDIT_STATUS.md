# FHASIX 1.21.11 source audit status

Target configuration retained: Minecraft 1.21.11, Yarn 1.21.11+build.4, Fabric Loader 0.18.4, Fabric API 0.141.5+1.21.11, Java 21.

## Changes made from the latest build report
- Corrected particle option enum references to `ParticlesMode`, which is the type exposed by `GameOptions` in Yarn 1.21.11.
- Ported block-outline rendering to the 1.21.11 `VertexRendering.drawOutline` API, `RenderLayers` line factory, current camera access, and the current outline callback signature.
- Ported ore highlighting to `VertexRendering.drawOutline`, current camera access, and a full-cube voxel shape. The feature still only scans exposed ores, so it does not draw through solid walls.
- Added a Mixin invoker for the package-private 1.21.11 line render-layer factory.
- Corrected cape model transform construction, cape state field references, and texture layer lookup through the player model.
- Removed an invalid `@Override` annotation from the cosmetic preview player's skin-texture accessor.

## Important verification note
This source ZIP has been edited against the reported compiler diagnostics and the published Yarn/Fabric 1.21.11 API documentation, but a complete Gradle build has **not** been run in this environment. It must not be described as a verified successful build until GitHub Actions or a local Gradle build passes.

## Known behavior limitation
The custom block overlay now emits filled shape quads through Minecraft's debug-filled-box layer and uses the 1.21.11 shape-outline renderer for borders. Visual behavior still needs an in-game check, particularly transparency and line thickness.
