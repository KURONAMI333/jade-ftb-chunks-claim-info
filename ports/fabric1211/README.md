# Jade x FTB Chunks Claim Info — Fabric 1.21.1

Client-only Fabric cell ported from published NeoForge base commit `9d50811114a07c83e298364a6eb9499e87ad114a`.

Looking at a block in a claimed chunk adds `Claimed: <team name>` to Jade's tooltip, with the owning player’s Minecraft skin face immediately beside the team name; force-loaded claims retain `[force-loaded]`. The owner face uses the online player skin when available and asynchronously resolves offline owners by their FTB-synchronized owner UUID. Skin results are cached; unresolved owners use a UUID-specific default face. The provider reads only FTB Chunks' already synchronized client map. It creates no packet, asks no server question, and never falls back to a generated/loading getter. FTB's hidden-claim sync sends outsiders an unclaimed record with the nil team id, so this lookup has no private team/claim data to reveal.

The plugin is declared in Fabric's `jade` entrypoint and carries Jade's runtime-retained `@WailaPlugin("ftbchunks")` annotation. Its Jade provider UID supplies the normal Jade plugin-config toggle. Fabric metadata restricts the mod to the client environment and requires Jade 15.10.6+ and FTB Chunks 2101.1.22+.

FTB's map classes are an internal API. Any thrown exception or linkage error permanently disables this lookup for the current client process after one warning. The lightweight JUnit tests cover absent data, successful results, and this failure guard.

## Build

This cell uses Fabric Loom 1.17.21, Gradle 9.5.0 (pinned by the official distribution SHA-256), Java 21, and official Mojang mappings. Loom 1.17.21 accepts the selected FTB host jar metadata built with Loom 1.17.491. The Gradle source/test check is `./gradlew compileJava compileTestJava test`.

## Evidence and hashes

See [API_EVIDENCE.md](API_EVIDENCE.md), [direct compiler and test evidence](.evidence/JAVAC_RESULTS.md), [SOURCE_SHA256SUMS](SOURCE_SHA256SUMS), and [DEPENDENCY_SHA256SUMS](DEPENDENCY_SHA256SUMS). The host jars were inspected from their official CurseForge-linked artifacts and FTB/Modrinth artifact endpoints; no host jar is bundled into this cell.
