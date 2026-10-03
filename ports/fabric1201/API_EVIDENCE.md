# Fabric 1.20.1 API evidence

Target intersection: the 2026-10-03 captured official-host matrix records Jade Fabric 11.13.3 and FTB Chunks Fabric 2001.3.8 for Minecraft 1.20.1. Exact host artifacts were fetched from the official FTB Maven and Modrinth Maven URLs and retained locally under ignored `.evidence/`; checksums are in `DEPENDENCY_SHA256SUMS`. FTB Maven metadata identifies the final 1.20.1 branch releases as Chunks 2001.3.8, Teams 2001.3.2, and Library 2001.2.13.

The published Fabric jars use intermediary Minecraft references. They were remapped locally with the cached Fabric TinyRemapper and the cached 1.20.1 Loom layered mapping `intermediary -> named` before `javap` and direct `javac` inspection. This was a direct light remap, not a Gradle build or the production `remapJar` task.

## Confirmed API surface

- FTB Chunks 2001.3.8: `MapManager.getInstance()`, `lock`, `getDimensions(): Map<ResourceKey<Level>, MapDimension>`, `MapDimension.getRegions()`, `MapRegion.getMapChunk(XZ)`, `MapChunk.getTeam()`, and `MapChunk.getForceLoadedDate()` exist with the signatures used by `FtbClaimLookup`.
- `javap -c` for this exact `MapRegion.getMapChunk(XZ)` is a direct `chunks.get(key)`. The separate `getOrCreateMapChunk(XZ)` uses `computeIfAbsent`; the addon does not call it. `MapManager.getDimensions()` returns the existing map under the manager lock.
- FTB Teams Fabric 2001.3.2 exposes `Team.getColoredName(): Component`, used for the team label.
- FTB Library Fabric 2001.2.13 exposes `XZ.regionFromBlock(int,int)` and `XZ.of(int,int)`, used for the region and local chunk coordinates.
- Jade Fabric 11.13.3 publishes Fabric Loader's `jade` entrypoint and `IWailaPlugin` API. Jade's `CommonProxy.loadComplete()` calls `FabricLoader.getEntrypointContainers("jade", IWailaPlugin.class)`. `fabric.mod.json` registers `ClaimsJadePlugin` there; its `@WailaPlugin("ftbchunks")` matches the addon integration id.
- Jade's exact `WailaClientRegistration.registerBlockComponent` bytecode calls `tryAddConfig`; that adds the provider UID to the plugin toggle config unless the provider is marked required. `ClaimComponentProvider.getUid()` and the existing `config.jade.plugin_jadeftbclaims.claim_info` language key retain the `Chunk claim info` toggle.
- Minecraft 1.20.1's cached Mojang-mapped jar exposes `ResourceLocation(String,String)`, used instead of the later `fromNamespaceAndPath` factory.

FTB Chunks Fabric 2001.3.8 `FTBChunks.loggedIn` bytecode collects server claims, groups them by dimension and team, then routes each group through `lambda$loggedIn$2`. That lambda calls `ChunkTeamDataImpl.canPlayerUse(player, FTBChunksProperties.CLAIM_VISIBILITY)` and sends `SendManyChunksPacket` only when it returns true. This exact Fabric host artifact therefore filters private claims before login sync to a player without access. This inspection does not prove every live-update path; no live Fabric client was run.

The implementation reads only FTB Chunks' local client map cache, uses no addon packet registration or request, holds the FTB map lock while reading its maps, and permanently soft-disables after a caught internal API failure. The upstream NeoForge source establishes intended behavior; this Fabric cell has not been launched. Private-claim login filtering is verified in the exact official host bytecode, but end-to-end Fabric runtime and live-update behavior remain unverified.

## Owner UUID and skin resolution added in 0.2.0 candidate

The exact FTB Teams Fabric 2001.3.2 Mojang-mapped host jar exposes `Team#getId/getOwner/isPlayerTeam/isPartyTeam`, `FTBTeamsAPI.API#isClientManagerLoaded/getClientManager`, `ClientTeamManager#getKnownPlayer(UUID)`, and `KnownClientPlayer#profile()`. `PlayerTeam` and `PartyTeam` bytecode confirms personal team identity and party owner are different fields; the implementation uses personal team ID or party `getOwner()` and omits server/NIL owners. The jar and hash are in `DEPENDENCY_SHA256SUMS`.

Minecraft 1.20.1's mapped client jar exposes `Minecraft#getMinecraftSessionService/getSkinManager`, `SkinManager#registerSkins(GameProfile, SkinTextureCallback, boolean)`, `MinecraftSessionService#fillProfileProperties(GameProfile, boolean)` from Authlib 4.0.43, `PlayerInfo#isSkinLoaded/getSkinLocation`, and `PlayerFaceRenderer#draw(GuiGraphics, ResourceLocation, int, int, int, boolean, boolean)`. Offline profile completion and skin registration are asynchronous and UUID-validated; requests are capped at 12 in flight, cached by owner UUID, and fall back to that UUID's vanilla default skin. Runtime behavior and Jade opacity/fade rendering remain unverified.
