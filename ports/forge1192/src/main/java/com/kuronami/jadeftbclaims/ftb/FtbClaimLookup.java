package com.kuronami.jadeftbclaims.ftb;

import com.kuronami.jadeftbclaims.JadeFtbClaims;
import com.mojang.authlib.GameProfile;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftbteams.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.data.ClientTeam;
import dev.ftb.mods.ftbteams.data.ClientTeamManager;
import dev.ftb.mods.ftbteams.data.KnownClientPlayer;
import dev.ftb.mods.ftbteams.data.TeamType;
import dev.ftb.mods.ftblibrary.math.XZ;
import net.minecraft.Util;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/** Reads the already-synchronized client map without creating missing entries. */
public final class FtbClaimLookup {
    private static volatile boolean disabled;
    private static volatile boolean warned;

    private FtbClaimLookup() {}

    public static Optional<ClaimInfo> lookup(ResourceKey<Level> dimension, int blockX, int blockZ) {
        return guard(() -> lookupInternal(dimension, blockX, blockZ));
    }

    static <T> Optional<T> guard(Supplier<Optional<T>> action) {
        if (disabled) return Optional.empty();
        try {
            return action.get();
        } catch (Throwable error) {
            disabled = true;
            if (!warned) {
                warned = true;
                JadeFtbClaims.LOGGER.warn("FTB Chunks client map lookup failed; disabling claim tooltip", error);
            }
            return Optional.empty();
        }
    }

    static boolean isDisabled() { return disabled; }
    static void resetForTest() { disabled = false; warned = false; }

    private static Optional<ClaimInfo> lookupInternal(ResourceKey<Level> dimension, int blockX, int blockZ) {
        MapManager manager = MapManager.inst;
        if (manager == null || manager.invalid) return Optional.empty();
        synchronized (manager.lock) {
            Map<ResourceKey<Level>, MapDimension> dimensions = manager.getDimensions();
            MapDimension mapDimension = dimensions.get(dimension);
            if (mapDimension == null) return Optional.empty();
            MapRegion region = mapDimension.getRegions().get(XZ.regionFromBlock(blockX, blockZ));
            if (region == null) return Optional.empty();
            MapChunk chunk = region.getMapChunk(XZ.of((blockX >> 4) & 31, (blockZ >> 4) & 31));
            if (chunk == null) return Optional.empty();
            ClientTeam team = chunk.getTeam();
            if (team == null || !team.isValid()) return Optional.empty();
            return Optional.of(new ClaimInfo(team.getColoredName(), chunk.forceLoadedDate != null,
                    resolveOwnerProfile(team)));
        }
    }

    /** In 1902, personal ClientTeam#getOwnerID is NIL; use its player-team ID instead. */
    private static GameProfile resolveOwnerProfile(ClientTeam team) {
        try {
            TeamType type = team.getType();
            UUID ownerId;
            if (type.isPlayer()) ownerId = team.getId();
            else if (type.isParty()) ownerId = team.getOwnerID();
            else return null;
            if (ownerId == null || ownerId.equals(Util.NIL_UUID)) return null;

            if (!FTBTeamsAPI.isClientManagerLoaded()) return new GameProfile(ownerId, "");
            ClientTeamManager manager = FTBTeamsAPI.getClientManager();
            KnownClientPlayer known = manager == null ? null : manager.getKnownPlayer(ownerId);
            if (known == null) return new GameProfile(ownerId, "");
            GameProfile profile = known.getProfile();
            return profile != null && ownerId.equals(profile.getId())
                    ? profile : new GameProfile(ownerId, known.name);
        } catch (Throwable ignored) {
            // Owner resolution is optional; preserve the claim label on any profile error.
            return null;
        }
    }
}
