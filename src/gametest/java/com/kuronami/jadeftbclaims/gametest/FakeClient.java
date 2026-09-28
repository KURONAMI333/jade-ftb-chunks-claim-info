package com.kuronami.jadeftbclaims.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import java.util.UUID;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.neoforged.neoforge.network.registration.NetworkRegistry;

/**
 * A real {@link ServerPlayer} put through the full login path
 * ({@code PlayerList#placeNewPlayer}) on an {@link EmbeddedChannel}.
 *
 * <p>Going through {@code placeNewPlayer} (rather than a bare constructor)
 * registers the player in the player list and level, adds them to
 * {@code level.players()}, and fires {@code PlayerLoggedInEvent} — which is
 * what FTB Teams listens to when it auto-creates a player team, and in turn
 * what makes FTB Chunks run its login synchronisation for this connection.
 *
 * <p>Packets written to the connection land in the channel's outbound buffer
 * as raw {@link net.minecraft.network.protocol.Packet} objects — no encoder
 * is installed on a mock connection — so tests can drain and inspect exactly
 * what a real client would have received.
 */
record FakeClient(ServerPlayer player, EmbeddedChannel channel) {

    static FakeClient login(ServerLevel level, String name) {
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), name), false);
        ServerPlayer player = new ServerPlayer(
                level.getServer(), level, cookie.gameProfile(), cookie.clientInformation());
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        EmbeddedChannel channel = new EmbeddedChannel(connection);
        // Pretend a fully negotiated NeoForge client, or the login path throws
        // "Payload may not be sent to the client" when mods sync their data.
        NetworkRegistry.configureMockConnection(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return new FakeClient(player, channel);
    }
}
