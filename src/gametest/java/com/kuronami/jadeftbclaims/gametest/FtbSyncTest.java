package com.kuronami.jadeftbclaims.gametest;

import dev.architectury.impl.NetworkAggregator;
import dev.ftb.mods.ftbchunks.api.ClaimResult;
import dev.ftb.mods.ftbchunks.api.FTBChunksAPI;
import dev.ftb.mods.ftbchunks.api.FTBChunksProperties;
import dev.ftb.mods.ftbchunks.data.ChunkSyncInfo;
import dev.ftb.mods.ftbchunks.net.LoginDataPacket;
import dev.ftb.mods.ftbchunks.net.SendChunkPacket;
import dev.ftb.mods.ftbchunks.net.SendManyChunksPacket;
import dev.ftb.mods.ftbteams.api.FTBTeamsAPI;
import dev.ftb.mods.ftbteams.api.Team;
import dev.ftb.mods.ftbteams.api.property.PrivacyMode;
import dev.ftb.mods.ftblibrary.math.ChunkDimPos;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import net.minecraft.Util;
import net.minecraft.core.RegistryAccess;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.common.ClientboundCustomPayloadPacket;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;

/**
 * Verifies the data layer this mod is built on: which claim information FTB
 * Chunks actually pushes to which connected client, and when.
 *
 * <p>Every FTB-typed signature lives in this class so the
 * {@code @GameTestHolder} class loads even when the FTB jars are absent.
 */
final class FtbSyncTest {

    private FtbSyncTest() {}

    /** One flattened "the server told this client about this chunk" record. */
    record SyncedChunk(ResourceKey<Level> dimension, UUID teamId, ChunkSyncInfo info) {}

    /** Everything the fake client has received so far, drained once. */
    private record Captured(List<Object> all) {
        List<SyncedChunk> syncedChunks() {
            List<SyncedChunk> out = new ArrayList<>();
            for (Object o : all) {
                if (o instanceof SendManyChunksPacket p) {
                    p.chunks().forEach(c -> out.add(new SyncedChunk(p.dimension(), p.teamId(), c)));
                } else if (o instanceof SendChunkPacket p) {
                    out.add(new SyncedChunk(p.dimension(), p.teamId(), p.chunk()));
                }
            }
            return out;
        }

        boolean sawLoginData() {
            return all.stream().anyMatch(o -> o instanceof LoginDataPacket);
        }

        String describe() {
            return all.stream()
                    .map(o -> o.getClass().getSimpleName())
                    .collect(Collectors.groupingBy(s -> s, Collectors.counting()))
                    .toString();
        }
    }

    /**
     * Drains the outbound buffer. Custom payloads arrive as raw
     * {@link ClientboundCustomPayloadPacket} objects because a mock
     * connection has no encoder installed. Architectury wraps typed payloads
     * in {@code BufCustomPacketPayload} (serialized bytes under the original
     * packet id), so we decode the ones we care about with their stream
     * codecs — exactly what a real client's network thread would see.
     */
    private static Captured drain(EmbeddedChannel channel, ServerLevel level) {
        return drain(channel, level.registryAccess());
    }

    private static Captured drain(EmbeddedChannel channel, RegistryAccess access) {
        List<Object> out = new ArrayList<>();
        Object msg;
        while ((msg = channel.readOutbound()) != null) {
            if (msg instanceof ClientboundCustomPayloadPacket p) {
                out.add(unwrap(p.payload(), access));
            }
        }
        return new Captured(out);
    }

    private static Object unwrap(CustomPacketPayload payload, RegistryAccess access) {
        if (!(payload instanceof NetworkAggregator.BufCustomPacketPayload buf)) {
            return payload;
        }
        var id = payload.type().id();
        var rb = new RegistryFriendlyByteBuf(Unpooled.wrappedBuffer(buf.payload()), access);
        try {
            if (id.equals(SendManyChunksPacket.TYPE.id())) {
                return SendManyChunksPacket.STREAM_CODEC.decode(rb);
            }
            if (id.equals(SendChunkPacket.TYPE.id())) {
                return SendChunkPacket.STREAM_CODEC.decode(rb);
            }
            if (id.equals(LoginDataPacket.TYPE.id())) {
                return LoginDataPacket.STREAM_CODEC.decode(rb);
            }
            return "archbuf:" + id;
        } finally {
            rb.release();
        }
    }

    private static Team requireTeam(ServerPlayer player) {
        return FTBTeamsAPI.api().getManager().getTeamForPlayer(player)
                .orElseThrow(() -> new IllegalStateException(
                        "no FTB team for fake player " + player.getGameProfile().getName()));
    }

    private static void claim(ServerPlayer player, ResourceKey<Level> dim, ChunkPos pos, boolean checkOnly) {
        ClaimResult r = FTBChunksAPI.api().claimAsPlayer(player, dim, pos, checkOnly);
        if (!r.isSuccess()) {
            throw new IllegalStateException("claimAsPlayer failed: " + r.getResultId());
        }
    }

    /**
     * Removes any claim at {@code pos} left over from a previous GameTest
     * run. FTB Chunks persists claims under {@code world/ftbchunks/} across
     * runs, so fixed coordinates must be reset before use or every rerun
     * collides with stale claims ({@code already_claimed}). The second
     * argument of {@code ClaimedChunk#unclaim} is the sendUpdate flag:
     * {@code true} broadcasts the unclaimed form to connected clients.
     * Runs before this test's clients log in so it never pollutes captures.
     */
    private static void preclear(ServerLevel level, ResourceKey<Level> dim, ChunkPos pos) {
        var chunk = FTBChunksAPI.api().getManager().getChunk(new ChunkDimPos(dim, pos));
        if (chunk != null) {
            chunk.unclaim(level.getServer().createCommandSourceStack(), true);
        }
    }

    private static boolean hasChunk(Captured cap, ResourceKey<Level> dim, ChunkPos pos) {
        return cap.syncedChunks().stream().anyMatch(c ->
                c.dimension().equals(dim) && c.info().x() == pos.x && c.info().z() == pos.z);
    }

    // ---------------------------------------------------------------------

    /** A chunk claimed before login must arrive in the joining client's sync. */
    static void remoteClaimSyncsOnLogin(GameTestHelper helper, ChunkPos target) {
        ServerLevel level = helper.getLevel();
        preclear(level, level.dimension(), target);
        FakeClient owner = FakeClient.login(level, "owner_login");
        UUID ownerTeamId = requireTeam(owner.player()).getTeamId();
        claim(owner.player(), level.dimension(), target, false);
        drain(owner.channel(), level); // discard the owner's own traffic

        FakeClient viewer = FakeClient.login(level, "viewer_login");
        Captured cap = drain(viewer.channel(), level);

        if (!cap.sawLoginData()) {
            helper.fail("viewer got no LoginDataPacket; FTB client sync is not running at all");
        }
        boolean found = cap.syncedChunks().stream().anyMatch(c ->
                c.dimension().equals(level.dimension())
                        && c.info().x() == target.x && c.info().z() == target.z
                        && c.info().claimed() && c.teamId().equals(ownerTeamId));
        if (!found) {
            helper.fail("remote claimed chunk " + target + " missing from login sync. payloads: "
                    + cap.describe());
        }
        helper.succeed();
    }

    /** A claim made while a client is connected must reach it live. */
    static void liveClaimBroadcastsToConnectedClients(GameTestHelper helper, ChunkPos target) {
        ServerLevel level = helper.getLevel();
        preclear(level, level.dimension(), target);
        FakeClient owner = FakeClient.login(level, "owner_live");
        FakeClient viewer = FakeClient.login(level, "viewer_live");
        UUID ownerTeamId = requireTeam(owner.player()).getTeamId();
        drain(owner.channel(), level);
        drain(viewer.channel(), level);

        claim(owner.player(), level.dimension(), target, false);
        Captured cap = drain(viewer.channel(), level);

        boolean found = cap.syncedChunks().stream().anyMatch(c ->
                c.dimension().equals(level.dimension())
                        && c.info().x() == target.x && c.info().z() == target.z
                        && c.info().claimed() && c.teamId().equals(ownerTeamId));
        if (!found) {
            helper.fail("live claim of " + target + " not broadcast to connected client. payloads: "
                    + cap.describe());
        }
        helper.succeed();
    }

    /**
     * At login the server must not send private claims to a non-member at all
     * (the gate at FTBChunks#loggedIn drops the whole packet).
     */
    static void privateClaimsNotSyncedToNonMemberOnLogin(GameTestHelper helper, ChunkPos target) {
        ServerLevel level = helper.getLevel();
        preclear(level, level.dimension(), target);
        FakeClient owner = FakeClient.login(level, "owner_priv");
        Team ownerTeam = requireTeam(owner.player());
        ownerTeam.setProperty(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PRIVATE);
        claim(owner.player(), level.dimension(), target, false);
        drain(owner.channel(), level);

        FakeClient viewer = FakeClient.login(level, "viewer_priv");
        Captured cap = drain(viewer.channel(), level);

        if (!cap.sawLoginData()) {
            helper.fail("viewer got no LoginDataPacket; cannot tell 'hidden' from 'not synced'");
        }
        if (hasChunk(cap, level.dimension(), target)) {
            helper.fail("private claim at " + target + " leaked to non-member at login");
        }
        boolean sawOwnerTeam = cap.syncedChunks().stream()
                .anyMatch(c -> c.teamId().equals(ownerTeam.getTeamId()));
        if (sawOwnerTeam) {
            helper.fail("non-member received sync data tagged with the private team's id");
        }
        helper.succeed();
    }

    /**
     * A live claim by a private team must reach the member in full but reach
     * outsiders only in the hidden (NIL_UUID, claimed=false) form.
     */
    static void privateLiveClaimHiddenFromNonMember(GameTestHelper helper, ChunkPos target) {
        ServerLevel level = helper.getLevel();
        preclear(level, level.dimension(), target);
        FakeClient owner = FakeClient.login(level, "owner_lh");
        FakeClient viewer = FakeClient.login(level, "viewer_lh");
        Team ownerTeam = requireTeam(owner.player());
        ownerTeam.setProperty(FTBChunksProperties.CLAIM_VISIBILITY, PrivacyMode.PRIVATE);
        drain(owner.channel(), level);
        drain(viewer.channel(), level);

        claim(owner.player(), level.dimension(), target, false);
        Captured ownerCap = drain(owner.channel(), level);
        Captured viewerCap = drain(viewer.channel(), level);

        boolean ownerGotReal = ownerCap.syncedChunks().stream().anyMatch(c ->
                c.dimension().equals(level.dimension())
                        && c.info().x() == target.x && c.info().z() == target.z
                        && c.info().claimed() && c.teamId().equals(ownerTeam.getTeamId()));
        if (!ownerGotReal) {
            helper.fail("team member did not receive own live claim update");
        }

        List<SyncedChunk> atPos = viewerCap.syncedChunks().stream().filter(c ->
                c.dimension().equals(level.dimension())
                        && c.info().x() == target.x && c.info().z() == target.z).toList();
        boolean leaked = viewerCap.syncedChunks().stream().anyMatch(c ->
                c.teamId().equals(ownerTeam.getTeamId())
                        || atPos.stream().anyMatch(s -> s.info().claimed()));
        if (leaked) {
            helper.fail("private live claim leaked to non-member: " + atPos);
        }
        // The hidden packet (if sent at all) must be indistinguishable from an unclaim.
        for (SyncedChunk c : atPos) {
            if (c.info().claimed() || !Util.NIL_UUID.equals(c.teamId())) {
                helper.fail("hidden update for " + target + " still carried claim data: " + c);
            }
        }
        helper.succeed();
    }

    /** Unclaiming must broadcast claimed=false so stale claims disappear. */
    static void unclaimBroadcastsUnclaimed(GameTestHelper helper, ChunkPos target) {
        ServerLevel level = helper.getLevel();
        preclear(level, level.dimension(), target);
        FakeClient owner = FakeClient.login(level, "owner_uncl");
        FakeClient viewer = FakeClient.login(level, "viewer_uncl");
        claim(owner.player(), level.dimension(), target, false);
        drain(owner.channel(), level);
        drain(viewer.channel(), level);

        var chunk = FTBChunksAPI.api().getManager()
                .getChunk(new ChunkDimPos(level.dimension(), target));
        if (chunk == null) {
            helper.fail("server side lost the claim before unclaim test ran");
            return;
        }
        chunk.unclaim(owner.player().createCommandSourceStack(), true);
        Captured cap = drain(viewer.channel(), level);

        boolean unclaimed = cap.syncedChunks().stream().anyMatch(c ->
                c.dimension().equals(level.dimension())
                        && c.info().x() == target.x && c.info().z() == target.z
                        && !c.info().claimed());
        if (!unclaimed) {
            helper.fail("unclaim of " + target + " did not reach connected client");
        }
        helper.succeed();
    }

    /** force-load state must travel inside the same sync record. */
    static void forceLoadFlagSyncs(GameTestHelper helper, ChunkPos target) {
        ServerLevel level = helper.getLevel();
        preclear(level, level.dimension(), target);
        FakeClient owner = FakeClient.login(level, "owner_fl");
        FakeClient viewer = FakeClient.login(level, "viewer_fl");
        drain(owner.channel(), level);
        drain(viewer.channel(), level);

        // claimAsPlayer's last argument is checkOnly (dry-run), not forceLoad:
        // claim for real, then flip the flag through ChunkTeamData#forceLoad,
        // which fires AFTER_LOAD and rebroadcasts the chunk record.
        claim(owner.player(), level.dimension(), target, false);
        var chunk = FTBChunksAPI.api().getManager()
                .getChunk(new ChunkDimPos(level.dimension(), target));
        ClaimResult fl = chunk.getTeamData().forceLoad(
                owner.player().createCommandSourceStack(),
                new ChunkDimPos(level.dimension(), target), false, true);
        if (!fl.isSuccess()) {
            throw new IllegalStateException("forceLoad failed: " + fl.getResultId());
        }
        Captured cap = drain(viewer.channel(), level);

        boolean found = cap.syncedChunks().stream().anyMatch(c ->
                c.dimension().equals(level.dimension())
                        && c.info().x() == target.x && c.info().z() == target.z
                        && c.info().claimed() && c.info().forceLoaded());
        if (!found) {
            helper.fail("force-loaded claim of " + target + " arrived without forceLoaded flag");
        }
        helper.succeed();
    }

    /** Claims in other dimensions must be sent with their dimension key. */
    static void otherDimensionClaimSyncsWithDimensionKey(GameTestHelper helper, ChunkPos target) {
        ServerLevel level = helper.getLevel();
        preclear(level, Level.NETHER, target);
        FakeClient owner = FakeClient.login(level, "owner_dim");
        UUID ownerTeamId = requireTeam(owner.player()).getTeamId();
        claim(owner.player(), Level.NETHER, target, false);
        drain(owner.channel(), level);

        FakeClient viewer = FakeClient.login(level, "viewer_dim");
        Captured cap = drain(viewer.channel(), level);

        boolean found = cap.syncedChunks().stream().anyMatch(c ->
                c.dimension().equals(Level.NETHER)
                        && c.info().x() == target.x && c.info().z() == target.z
                        && c.info().claimed() && c.teamId().equals(ownerTeamId));
        if (!found) {
            helper.fail("nether claim missing or wrong dimension in login sync");
        }
        helper.succeed();
    }

    /**
     * Nothing must be sent for wilderness: a fresh client's sync must not
     * mention a coordinate nobody claimed. Combined with
     * {@code remoteClaimSyncsOnLogin} this shows "absent" == "unclaimed/unknown".
     */
    static void wildernessChunkAbsentFromSync(GameTestHelper helper, ChunkPos target) {
        ServerLevel level = helper.getLevel();
        preclear(level, level.dimension(), target);
        FakeClient viewer = FakeClient.login(level, "viewer_wild");
        Captured cap = drain(viewer.channel(), level);

        if (!cap.sawLoginData()) {
            helper.fail("viewer got no LoginDataPacket; cannot tell 'absent' from 'not synced'");
        }
        if (hasChunk(cap, level.dimension(), target)) {
            helper.fail("unclaimed wilderness chunk " + target + " appeared in login sync");
        }
        helper.succeed();
    }
}
