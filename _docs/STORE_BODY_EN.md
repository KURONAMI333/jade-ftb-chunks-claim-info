Jade's tooltip answers "whose claim is this?" — look at a block in a claimed chunk and the owning team's name appears as one extra line, no map screen needed.

FTB Chunks already knows who owns every claimed chunk and syncs it to your client for its own map. This addon reads that same client-side data and surfaces it where you are already looking: the Jade tooltip.

- **Claimed chunks** show `Claimed: <team>` using the team's coloured name from FTB Teams.
- **Force-loaded chunks** get a `[force-loaded]` marker appended to the line.
- **Unclaimed chunks stay quiet** — no "Unclaimed" spam on wilderness.
- **Private and hidden claims are never shown.** The line only reads what FTB Chunks has already sent to your client; claims the server hides from you never arrive, so they cannot leak into the tooltip.

The provider appears in Jade's plugin config screen like any other and can be toggled off there.

## Dependencies

- [Jade](https://www.curseforge.com/minecraft/mc-mods/jade) — required; this addon adds a line to its tooltip
- [FTB Chunks](https://www.curseforge.com/minecraft/mc-mods/ftb-chunks-forge) — required; the claim data source (FTB Library and FTB Teams come with it)
- Client-side only — the server does not need it

## Compatibility and limits

Verified against FTB Chunks 2101.1.22. The claim lookup relies on FTB Chunks internals that are not a stable API; if a future update changes them, the line stops appearing and a warning is logged once — no crash, no data loss.

<p><a href="https://www.patreon.com/KURONAMI333"><img src="https://raw.githubusercontent.com/KURONAMI333/music-disc-maker/6a0a895769575a1a58fd1fb6dfb15e259bcefccb/_docs/support/patreon.png" width="440" height="156" alt="Support my mods on Patreon"></a> <a href="https://x.com/kuronami333"><img src="https://raw.githubusercontent.com/KURONAMI333/music-disc-maker/6a0a895769575a1a58fd1fb6dfb15e259bcefccb/_docs/support/x.png" width="300" height="156" alt="Follow @kuronami333 on X"></a></p>

All Rights Reserved. Free to put in any modpack, on any platform, monetised or not - no permission needed, no credit required. Source is published so you can read exactly what it does.

Source: https://github.com/KURONAMI333/jade-ftb-chunks-claim-info
For bugs and questions, comment on the CurseForge page or DM [@kuronami333 on X](https://x.com/kuronami333).
