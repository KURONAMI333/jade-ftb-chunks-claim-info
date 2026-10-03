# Jade x FTB Chunks Claim Info — Forge 1.20.1

Client-only Forge candidate for Minecraft 1.20.1. A claimed chunk adds the unchanged colored team label to Jade with the owning player’s Minecraft skin face beside the team name. The force-loaded marker remains at the end of the row.

Online owners use the current player skin. Offline owners are resolved asynchronously from the owner UUID already synchronized by FTB Teams; results are cached by UUID with a bounded request queue and a vanilla default-skin fallback. Claim lookup reads the existing FTB Chunks client map only and does not ask the server for claim information.

Product JAR, game runtime, dedicated-server classloading, and tooltip screenshots await parent integration.
