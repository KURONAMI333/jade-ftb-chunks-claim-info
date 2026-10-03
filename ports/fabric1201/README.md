# Jade: FTB Chunks Claim Info — Fabric 1.20.1

Client-only Fabric cell for the Jade + FTB Chunks addon. It reads the claim data that FTB Chunks has already synchronized into the local map cache and adds `Claimed: <team>` with the owning player’s Minecraft skin face immediately beside the team name, and retains `[force-loaded]` when applicable. Offline owners resolve asynchronously from the UUID already synchronized by FTB Teams; results are cached and unresolved owners use their UUID-specific default face. It registers no packet, asks no server for claim data, and never reads server claim storage.

Requires Jade 11.13.3+, FTB Chunks Fabric 2001.3.8+, FTB Teams Fabric 2001.3.2+, and FTB Library Fabric 2001.2.13+.

The FTB client map classes are internal implementation APIs. Any `Throwable` during lookup permanently disables the provider for that client session and logs once. This fallback is not a guarantee against every future binary or behavior change.
