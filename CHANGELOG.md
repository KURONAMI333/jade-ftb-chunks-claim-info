# Changelog

## v0.2.0

- Show the owning player's skin face beside the claim name in Jade's tooltip. Personal claims use the player's UUID; party claims use the party owner. Skin resolution is asynchronous and cached, with a UUID-specific default face while it loads.

## v0.1.0

- Initial release: shows `Claimed: <team>` (plus a `[force-loaded]` marker) in Jade's tooltip for blocks inside FTB Chunks claims. Client-side only; private and hidden claims are never shown.
