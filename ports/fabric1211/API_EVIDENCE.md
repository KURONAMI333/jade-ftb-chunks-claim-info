# Fabric 1.21.1 API evidence

Inspection date: 2026-10-03. Minecraft target: 1.21.1. Host versions match the official CurseForge file observations in `../../../SUPPORT_MATRIX.json` (relative to this cell).

## Exact artifacts inspected

Direct compilation and test execution evidence is recorded in [`.evidence/JAVAC_RESULTS.md`](.evidence/JAVAC_RESULTS.md). It documents the post-inspection toolchain repair and the three earlier Gradle configuration attempts, which all stopped before `JavaCompile`.

| Artifact | Version | Source | SHA-256 |
|---|---|---|---|
| Jade Fabric | 15.10.6+fabric | Modrinth version `A416FkrB`, file `Jade-1.21.1-Fabric-15.10.6.jar` | `fb834cc77804a6f509690c54b4af56305ce7a7f3171f8616310710f0af1f673a` |
| FTB Chunks Fabric | 2101.1.22 | FTB Maven `dev/ftb/mods/ftb-chunks-fabric/2101.1.22/` | `6b783610069b64f7b28aa0675a707b3cf171364b6647e0c7b406cf6ca7674215` |
| FTB Teams Fabric | 2101.1.11 | FTB Maven `dev/ftb/mods/ftb-teams-fabric/2101.1.11/` | `39e34b438e0232a614a8cf1269fcff2705b85fde8b930a03d558bc66a03c7cf6` |
| FTB Library Fabric | 2101.1.37 | FTB Maven `dev/ftb/mods/ftb-library-fabric/2101.1.37/` | `399e62acd6c343dab0780ac34aa0e86b4acaa6861eb5d65e826adb03ce9ec6c1` |
| FTB Chunks common sources | 2101.1.22 | FTB Maven `dev/ftb/mods/ftb-chunks/2101.1.22/` sources artifact | See `DEPENDENCY_SHA256SUMS` |

FTB Chunks common sources jar SHA-256 is `75aff6cc777f59d95c9b0fb981d07bccd8b818f0048c4f34abd7b94fb4c0d814`; the Fabric source shim SHA-256 is `d7a88a4f9e1478c809ea96ab38287c4337acba4e91ea706090397a923dafb5db`. The official FTB Fabric metadata declares the versions and the runtime relationships: Chunks 2101.1.22 requires Fabric, Minecraft `~1.21.1`, Architectury `>=13.0.8`, Library `>=2101.1.34`, and Teams `>=2101.1.9`; Library 2101.1.37 and Teams 2101.1.11 both match 1.21.1. The selected stable versions are also captured in `raw-metadata/official-cf-observations.json`.

## Jade plugin discovery and config toggle

`Jade-1.21.1-Fabric-15.10.6.jar` `fabric.mod.json` exposes a `jade` entrypoint for Jade's own plugins. `javap -p -c snownee.jade.util.CommonProxy` shows Fabric's `FabricLoader.getEntrypointContainers("jade", IWailaPlugin.class)` and then reads `WailaPlugin` from each plugin class with `Class.getDeclaredAnnotation`. `javap -v snownee.jade.api.WailaPlugin` confirms `RetentionPolicy.RUNTIME`. Therefore the Fabric metadata names `ClaimsJadePlugin` under `entrypoints.jade`, and the version-matched `@WailaPlugin("ftbchunks")` annotation remains required. `ClaimComponentProvider#getUid` supplies the Jade config key and registration uses `registerBlockComponent`, preserving the normal plugin toggle.

## FTB Chunks client map API

`javap -p` on the actual Fabric jar confirms these exact members (raw jar class signatures use Fabric intermediary names for Minecraft types; Loom remaps them against official Mojang mappings at compile time):

- `MapManager.getInstance(): Optional<MapManager>`, public `lock`, `getDimensions()`.
- `MapDimension.getRegions()`.
- `MapRegion.getMapChunk(XZ)`.
- `MapChunk.getTeam(): Optional<Team>`, `getForceLoadedDate(): Optional<Date>`.
- `Team.getColoredName(): Component`.
- `XZ.regionFromBlock(int,int)` and `XZ.of(int,int)`.

This is the only FTB-internal API access and lives in `FtbClaimLookup`. It only traverses existing `dimensions`, `regions`, and chunk maps under `MapManager.lock`; it does not call generating/loading getters (`getDimension`, `getRegion`, `getDataBlocking`, or `getOrCreateMapChunk`).

## Private/hidden claim behavior and independent packets

The 2101.1.22 common source artifact was inspected directly:

- `SendChunkPacket.sendToAll` and `SendManyChunksPacket.sendToAll/sendToPlayer` send full claim records only to allies when `ChunkTeamData.shouldHideClaims()` is true. Other players receive a replacement packet with `Util.NIL_UUID` and `ChunkSyncInfo.hidden()`.
- `ChunkSyncInfo.hidden()` creates `claimed=false`, with force-load flags and time fields cleared. On the client, `MapChunk.updateFromServer` resolves the received team id through `FTBTeamsAPI` and stores `ChunkSyncInfo.getDateInfo(team != null, ...)`; the hidden replacement thus leaves no team and no claim/force-load date.

The add-on reads the resulting local client map only and declares no Fabric networking entrypoint, channel, or packet class.

## Behavior parity carried from the published source

- Same target block position and dimension selection.
- Same claim line and optional force-loaded marker, with FTB Teams' colored `Component` preserved.
- Same empty result for wilderness, hidden claims, unsynchronized chunks, or missing map records.
- Same one-warning, permanent in-process soft-disable on any thrown `Throwable`, including `LinkageError`.
- Fabric-only metadata is `environment: "client"`; Jade and FTB Chunks are required host dependencies, matching the NeoForge client-side metadata.

## Owner UUID and skin resolution added in 0.2.0 candidate

The exact FTB Teams Fabric 2101.1.11 host jar (`~/.gradle/caches/modules-2/files-2.1/dev.ftb.mods/ftb-teams-fabric/2101.1.11/9b07ff01ed801d40c5574d8b2c3dd3d7a00dc099/ftb-teams-fabric-2101.1.11.jar`, SHA-256 `39e34b438e0232a614a8cf1269fcff2705b85fde8b930a03d558bc66a03c7cf6`) exposes `Team#getId`, `getOwner`, `isPlayerTeam`, and `isPartyTeam`; `FTBTeamsAPI.API` exposes `isClientManagerLoaded/getClientManager`; `ClientTeamManager#getKnownPlayer(UUID)` returns an optional `KnownClientPlayer`, whose `profile()` is an Authlib `GameProfile`. Bytecode inspection of `PlayerTeam` and `PartyTeam` confirms the team identifier and owner are distinct concepts; the implementation selects personal owner UUID from the player team ID and party owner UUID from `getOwner()`, and omits server/NIL owners.

Minecraft 1.21.1's mapped client jar exposes `SkinManager#getOrLoad(GameProfile)`, `MinecraftSessionService#fetchProfile(UUID, boolean)`, `PlayerInfo#getSkin()`, and `PlayerFaceRenderer#draw(GuiGraphics, ResourceLocation, int, int, int, boolean, boolean)`. The last method draws the face and hat through vanilla face rendering. Offline profile requests are started asynchronously after the FTB lookup; the tooltip/render path never waits for them. Runtime behavior and opacity/fade rendering remain unverified.
