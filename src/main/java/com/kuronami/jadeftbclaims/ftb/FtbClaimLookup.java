package com.kuronami.jadeftbclaims.ftb;

import com.kuronami.jadeftbclaims.JadeFtbClaims;
import dev.ftb.mods.ftbchunks.client.map.MapChunk;
import dev.ftb.mods.ftbchunks.client.map.MapDimension;
import dev.ftb.mods.ftbchunks.client.map.MapManager;
import dev.ftb.mods.ftbchunks.client.map.MapRegion;
import dev.ftb.mods.ftblibrary.math.XZ;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.client.KnownClientPlayer;
import com.mojang.authlib.GameProfile;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Supplier;

/**
 * FTB Chunks の client 側 map データから、任意チャンクのクレームを読む。
 *
 * <p><b>このクラスだけが {@code dev.ftb.mods.ftbchunks.client.map.*}（FTB の
 * 内部層。api パッケージではない）に触れる。</b>クレーム参照用の公開 API は
 * FTBChunksClientAPI には存在しないため、ここは本家の {@code RegionMapPanel
 * #addMouseOverText} と同じ非生成 getter 経路を踏む:
 * {@code MapManager → getDimensions().get(dim) → getRegions().get(regionPos)
 * → getMapChunk(chunkPosInRegion) → getTeam()}。生成系 getter
 * （{@code getDimension} / {@code getRegion} / {@code getDataBlocking}）は
 * dirty 化・lazy ロード・ディスク書込みを誘発するため使わない。
 *
 * <p>内部層が将来の FTB Chunks で変わっても、障害が {@code Error}/{@code
 * Exception} として出た時点でこのクラスは永久に無効化し、以後 tooltip には
 * 何も出さない（クラッシュもしない）。その判定は {@link LookupGuard} が担う。
 */
public final class FtbClaimLookup {

    /** 一度でも内部呼出が失敗したら true。無効化は不可逆（次回も試さない）。 */
    private static volatile boolean disabled = false;
    /** 無効化時に warn を出したか。ログを1回に絞るためだけのフラグ。 */
    private static volatile boolean warned = false;

    private FtbClaimLookup() {}

    /**
     * チャンクのクレームを返す。同期未取得・未クレーム・hidden・FTB 内部層の
     * 破壊では全て {@code Optional.empty()}（呼び出し側は区別しない）。
     */
    public static Optional<ClaimInfo> lookup(ResourceKey<Level> dimension, int blockX, int blockZ) {
        return guard(() -> lookupInternal(dimension, blockX, blockZ));
    }

    /**
     * Throwable を飲んで {@code Optional.empty()} に潰すガード。
     * 内部層への参照が壊れた時（{@code NoSuchMethodError} 等の {@link
     * LinkageError} も含む）に一度だけ warn を出し、このクラスを永久に止める。
     * テストから同じ経路を叩けるよう {@code Supplier} 形で切り出してある。
     */
    static <T> Optional<T> guard(Supplier<Optional<T>> action) {
        if (disabled) return Optional.empty();
        try {
            return action.get();
        } catch (Throwable t) {
            disabled = true;
            if (!warned) {
                warned = true;
                JadeFtbClaims.LOGGER.warn(
                        "FTB Chunks client map lookup failed; disabling claim tooltip "
                                + "(FTB Chunks internals changed?). Cause: {}", t.toString());
            }
            return Optional.empty();
        }
    }

    /** テスト用フック: 無効化状態の確認。 */
    static boolean isDisabled() {
        return disabled;
    }

    /** テスト用フック: 無効化状態を戻す（次の JVM でも自動復帰しない設計）。 */
    static void resetForTest() {
        disabled = false;
        warned = false;
    }

    private static Optional<ClaimInfo> lookupInternal(ResourceKey<Level> dimension, int blockX, int blockZ) {
        Optional<MapManager> managerOpt = MapManager.getInstance();
        if (managerOpt.isEmpty()) return Optional.empty();

        MapManager manager = managerOpt.get();
        // MapChunk は MAP_EXECUTOR 側の lazy region read からも挿入されうるので、
        // chunks map の読み取りは本家と同じ lock の内側で行う。
        synchronized (manager.lock) {
            MapDimension mapDimension = manager.getDimensions().get(dimension);
            if (mapDimension == null) return Optional.empty();

            MapRegion region = mapDimension.getRegions().get(XZ.regionFromBlock(blockX, blockZ));
            if (region == null) return Optional.empty();

            MapChunk chunk = region.getMapChunk(XZ.of((blockX >> 4) & 31, (blockZ >> 4) & 31));
            if (chunk == null) return Optional.empty();

            Team team = chunk.getTeam().orElse(null);
            if (team == null) return Optional.empty();

            return Optional.of(new ClaimInfo(team.getColoredName(),
                    chunk.getForceLoadedDate().isPresent(), resolveOwnerProfile(team)));
        }
    }

    /**
     * Resolve the real player who owns this claim team. Personal teams use their
     * team ID as the player's UUID; their Team#getOwner is the NIL UUID. Party teams
     * instead expose their owner through Team#getOwner. Server teams have no player
     * owner and intentionally receive no face.
     */
    private static GameProfile resolveOwnerProfile(Team team) {
        try {
            UUID ownerId;
            if (team.isPlayerTeam()) {
                ownerId = team.getId();
            } else if (team.isPartyTeam()) {
                ownerId = team.getOwner();
            } else {
                return null;
            }
            if (ownerId == null || ownerId.equals(net.minecraft.Util.NIL_UUID)) return null;

            FTBTeamsAPI.API api = FTBTeamsAPI.api();
            if (!api.isClientManagerLoaded()) return new GameProfile(ownerId, "");
            return api.getClientManager().getKnownPlayer(ownerId)
                    .map(player -> {
                        GameProfile profile = player.profile();
                        return profile != null && ownerId.equals(profile.getId())
                                ? profile
                                : new GameProfile(ownerId, player.name());
                    })
                    .orElseGet(() -> new GameProfile(ownerId, ""));
        } catch (Throwable ignored) {
            // A missing owner profile hides only the face; the existing claim text remains.
            return null;
        }
    }
}
