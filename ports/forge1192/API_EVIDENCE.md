# Forge 1.19.2 API and compatibility evidence

Observed 2026-10-03. The source target is Minecraft 1.19.2 / Forge 43.4.5 / Java 17. The 43.4.5 Forge value comes from the exact FTB Skies and FTB Skies Expert manifests retained at `../OLDER_MAJOR_PACK_EVIDENCE.json`; Genesis uses 43.3.13. It is not inferred from the `1.19.2` label. The installed local mapped Forge API cache is 43.5.2, so the direct source compile below checks the same MC line against that cached patch, not the exact 43.4.5 distribution. A build on the allocated Windows environment remains required.

## Actual host artifacts inspected

| Host | Exact artifact inspected | Evidence |
|---|---|---|
| Jade | Forge 8.9.2, Modrinth version `kp0HjPre`, file `Jade-1.19.1-forge-8.9.2.jar` | Its official version metadata lists Minecraft 1.19.2 + Forge and a release. The Modrinth Maven `8.9.2` jar has the same SHA-256 as that exact CDN file. `IWailaPlugin.registerClient`, `IBlockComponentProvider.appendTooltip`, and `WailaPlugin` exist. Jade 8.9.2’s `Jade.loadComplete` scans Forge annotation data for `@WailaPlugin`, creates each plugin, and calls `registerClient` only when `PlatformProxy.isPhysicallyClient()` is true. |
| FTB Chunks | Forge `1902.4.7-build.420` | Its `mods.toml` says JavaFML `[43,)`, Minecraft `[1.19.2,)`, Forge `[43.2.8,)`, FTB Library `>=1902.4.1-build.236`, and FTB Teams `>=1902.2.12-build.91`. Its POM confirms the latter two runtime versions. |
| FTB Teams | Forge `1902.2.14-build.165` | Current Maven metadata’s last `1902.2.14` build. `ClientTeam` extends `TeamBase`; `TeamBase.getColoredName(): Component` exists. Its `mods.toml` requires FTB Library `>=1902.3.10-build.159`. |
| FTB Library | Forge `1902.4.2-build.701` | Maven metadata and POM confirm the selected 1.19.2 Forge artifact and Architectury dependency. `XZ.regionFromBlock(int,int)` and `XZ.of(int,int)` exist. |

All dependency jars, POMs, metadata, origin URLs, and SHA-256 values are in `DEPENDENCY_SHA256SUMS`; raw jars are in ignored `.evidence/`. The selected FTB versions satisfy the exact FTB Chunks TOML minimums. Jade is version 8.9.2, not a guessed file ID.

## Lookup and privacy behavior

The exact FTB Chunks jar exposes `MapManager.inst`, `lock`, `invalid`, and `getDimensions()`. The inspected `RegionMapPanel.addMouseOverText` takes the same non-generating path used here: dimension map → `XZ.regionFromBlock` → region map → `getMapChunk(XZ.of(...))` → `MapChunk.getTeam()`. `MapRegion.getMapChunk` bytecode is exactly `chunks.get(key)`; `getOrCreateMapChunk` instead calls `computeIfAbsent`. FTB’s `MapChunk.updateFrom` sets its team from the packet team UUID (zero/unknown becomes null), so the hidden-claim scrub also clears any previously cached team attribution. This implementation does not call `getOrCreateMapChunk`, `getDataBlocking`, or a network API. In this Forge artifact `getTeam()` returns `ClientTeam` (not the newer `Team` API type), and `forceLoadedDate` is a public `Date` field. `ClientTeam.isValid()` and inherited `getColoredName()` exist.

Privacy is enforced by the host before these client maps are populated. In exact FTB Chunks `1902.4.7-build.420`, `ChunkSendingUtils.sendManyChunksToPlayer` checks `FTBChunksTeamData.shouldHideClaims()`. For a hidden claim, it sends the full packet only to an ally; for other recipients it constructs entries containing only chunk coordinates, with zero team UUID and no `ClaimedChunk`. Public claims use the regular full packet. The addon adds no packet, request, or server-side lookup, so it displays only attribution already disclosed by that host sync.

Jade registers `ClaimComponentProvider` for `Block.class`; its `getUid()` supplies the existing `claim_info` toggle and the original English key “Chunk claim info”. Team names use FTB Teams’ colored Component. Force-loaded chunks append the original `[force-loaded]` marker. Any `Throwable` in the internal map lookup disables future lookup attempts for that client session and logs once. Empty map data remains blank; it is not labeled wilderness.

The cached official Minecraft 1.19.2 client `version.json` (SHA-256 recorded in the lead facts file and copied into `.evidence/`) reports Java 17, resource pack format 9, data pack format 10, and world DataVersion 3120. This cell contains resource assets only, so its `pack.mcmeta` uses resource format 9; data-pack format 10 is not applicable to its resources. The cached Forge 43.5.2 jar has a packaged `pack.mcmeta` with Forge-specific values 8/9/10, but that is a loader artifact and was not used as the addon’s metadata. The earlier draft copied that value and has been corrected. The addon has no NBT read/write path or structure NBT file, so it does not stamp an addon NBT `DataVersion`. The inspected `ServerPlayer` class has the 1.19.2 constructor signature shown in raw evidence; addon source does not reference `ServerPlayer` or any server API.

## Source-only validation

A direct `javac --release 17 -proc:none` of every main Java source passed against the cached Minecraft 1.19.2 / Forge 43.5.2 mapped jar and the exact Jade/FTB/Architectury jars above. One deprecation warning remains: Minecraft’s `ResourceLocation(String,String)` constructor is deprecated in the cached patch; that constructor is required by this older API line and exists in the inspected 1.19.2 bytecode. Raw invocation, stderr, stdout, and exit code are in `.evidence/raw-javac-main-*`. This does not establish ForgeGradle compilation, a production remapped jar, runtime, tooltip rendering, or client/server distribution behavior.

Pure JUnit tests are prepared for the lookup guard in `src/test/.../FtbClaimLookupTest.java`; they were not run. Windows ForgeGradle build and runtime allocation are still pending.


## Owner face candidate API check (0.2.0)

The target FTB Teams Forge jar `1902.2.14-build.165` exposes `ClientTeam#getType/getOwnerID/getId`, `TeamType#isPlayer/isParty/isServer`, `FTBTeamsAPI#isClientManagerLoaded/getClientManager`, `ClientTeamManager#getKnownPlayer(UUID)`, and `KnownClientPlayer#getProfile`. Bytecode shows the base `Team#getOwner()` is `Util.NIL_UUID`, `PartyTeam#getOwner()` returns its owner field, and a player team must use its player UUID (`ClientTeam#getId`) instead. The implementation follows that split and skips server/NIL owners.

The target Jade 8.9.2 Forge API provides `ITooltip.add(List<IElement>)` and Jade `Element.render(PoseStack, float, float, float, float)`. The mapped 1.19.2 Minecraft API provides `PlayerFaceRenderer.draw(PoseStack, x, y, size, hat, upsideDown)`, `SkinManager#registerSkins`, `PlayerInfo#isSkinLoaded/getSkinLocation`, and `MinecraftSessionService#fillProfileProperties`. A direct source compile was repeated on this candidate with Java 17 release targeting, 256 MiB heap, and a 13-entry classpath (exact host jars plus Authlib 3.11.49 and Guava 31.1-jre); it passed against the cached Forge 43.5.2 mapped jar. Packaging and runtime remain pending; this does not verify the 43.4.5 remap or rendered screenshot.
