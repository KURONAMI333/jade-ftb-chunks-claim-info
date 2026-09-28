package com.kuronami.jadeftbclaims.gametest;

import com.mojang.logging.LogUtils;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.ChunkPos;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

/**
 * Headless verification of the data layer this mod relies on: fake clients
 * driven through the real login path, real FTB Teams team creation, real
 * {@code FTBChunksAPI#claimAsPlayer}, and the actual S2C packets drained off
 * the connection — asserting which claim data reaches which client and what
 * the privacy boundary looks like on the wire.
 *
 * <p>All FTB-typed code lives in {@link FtbSyncTest}: this class must stay
 * free of FTB references so it still loads when FTB Chunks is absent — the
 * GameTest registration scan does {@code Class.forName} on every holder.
 *
 * <p>Each test uses its own far-away chunk and unique player names; claimed
 * chunk state persists for the whole GameTest run, so tests never share
 * coordinates.
 */
@GameTestHolder("jadeftbclaims")
public class JadeFtbClaimsGameTests {

    private static boolean ftbReady() {
        return ModList.get().isLoaded("ftbchunks") && ModList.get().isLoaded("ftbteams");
    }

    private static boolean skipIfNoFtb(GameTestHelper helper, String name) {
        if (ftbReady()) return false;
        LogUtils.getLogger().warn("{} skipped: FTB Chunks/Teams not loaded", name);
        helper.succeed();
        return true;
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty5x5x5", timeoutTicks = 600)
    public static void remoteClaimSyncsOnLogin(GameTestHelper helper) {
        if (skipIfNoFtb(helper, "remoteClaimSyncsOnLogin")) return;
        FtbSyncTest.remoteClaimSyncsOnLogin(helper, new ChunkPos(400, 300));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty5x5x5", timeoutTicks = 600)
    public static void liveClaimBroadcastsToConnectedClients(GameTestHelper helper) {
        if (skipIfNoFtb(helper, "liveClaimBroadcastsToConnectedClients")) return;
        FtbSyncTest.liveClaimBroadcastsToConnectedClients(helper, new ChunkPos(410, 300));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty5x5x5", timeoutTicks = 600)
    public static void privateClaimsNotSyncedToNonMemberOnLogin(GameTestHelper helper) {
        if (skipIfNoFtb(helper, "privateClaimsNotSyncedToNonMemberOnLogin")) return;
        FtbSyncTest.privateClaimsNotSyncedToNonMemberOnLogin(helper, new ChunkPos(420, 300));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty5x5x5", timeoutTicks = 600)
    public static void privateLiveClaimHiddenFromNonMember(GameTestHelper helper) {
        if (skipIfNoFtb(helper, "privateLiveClaimHiddenFromNonMember")) return;
        FtbSyncTest.privateLiveClaimHiddenFromNonMember(helper, new ChunkPos(430, 300));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty5x5x5", timeoutTicks = 600)
    public static void unclaimBroadcastsUnclaimed(GameTestHelper helper) {
        if (skipIfNoFtb(helper, "unclaimBroadcastsUnclaimed")) return;
        FtbSyncTest.unclaimBroadcastsUnclaimed(helper, new ChunkPos(440, 300));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty5x5x5", timeoutTicks = 600)
    public static void forceLoadFlagSyncs(GameTestHelper helper) {
        if (skipIfNoFtb(helper, "forceLoadFlagSyncs")) return;
        FtbSyncTest.forceLoadFlagSyncs(helper, new ChunkPos(450, 300));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty5x5x5", timeoutTicks = 600)
    public static void otherDimensionClaimSyncsWithDimensionKey(GameTestHelper helper) {
        if (skipIfNoFtb(helper, "otherDimensionClaimSyncsWithDimensionKey")) return;
        FtbSyncTest.otherDimensionClaimSyncsWithDimensionKey(helper, new ChunkPos(460, 300));
    }

    @PrefixGameTestTemplate(false)
    @GameTest(template = "empty5x5x5", timeoutTicks = 600)
    public static void wildernessChunkAbsentFromSync(GameTestHelper helper) {
        if (skipIfNoFtb(helper, "wildernessChunkAbsentFromSync")) return;
        FtbSyncTest.wildernessChunkAbsentFromSync(helper, new ChunkPos(470, 300));
    }
}
