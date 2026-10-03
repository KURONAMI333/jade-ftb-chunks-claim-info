# Forge 1.20.1 owner-face candidate evidence

The read-only Forge source reference is `/Users/kura/dev/work/swe2-expansion-20260924/addon-jade-ftb-forge-1201-port-20260929`. Its build uses ForgeGradle 6.0.x, Java 17, Parchment 2023.09.03, `src/main/java`, and a separate `gametest` source set; this candidate preserves that main/gametest layout and the `clientSideOnly=true` metadata boundary.

A bounded direct `javac --release 17 -proc:none -J-Xmx256m` passed all eight main Java sources against the reference's 109-entry Forge 47.4.13 client classpath plus exact mapped Jade 11.13.3, FTB Chunks 2001.3.8, FTB Teams 2001.3.2, and FTB Library 2001.2.13 jars. The added mapped host jars were required because the ForgeGradle client classpath does not itself include mod compile-only APIs.

The 1.20.1 mapped client API exposes `PlayerFaceRenderer.draw(GuiGraphics, ResourceLocation, x, y, size, hat, upsideDown)`, `Minecraft#getMinecraftSessionService/getSkinManager`, `SkinManager#registerSkins`, `PlayerInfo#isSkinLoaded/getSkinLocation`, and Authlib 4.0.43 `MinecraftSessionService#fillProfileProperties`. FTB Teams 2001.3.2 owner selection follows the same personal-team-ID / party-owner split as the separately inspected Fabric 2001.3.2 API. The project marks the mod client-only in `mods.toml`; claim map access runs only through Jade's client plugin.

No ForgeGradle task, jar packaging, game launch, dedicated-server check, or visual screenshot was run by the source author.
