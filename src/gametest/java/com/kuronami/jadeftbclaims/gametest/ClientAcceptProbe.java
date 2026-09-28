package com.kuronami.jadeftbclaims.gametest;

import com.kuronami.jadeftbclaims.JadeFtbClaims;
import com.kuronami.jadeftbclaims.ftb.FtbClaimLookup;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import snownee.jade.gui.PluginsConfigScreen;
import snownee.jade.impl.config.PluginConfig;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 実クライアント受入用の file-driver プローブ（client のみ）。
 *
 * <p>ゲームディレクトリの {@code probe-command.txt} に1行1コマンドを書くと、
 * client tick で消費して実行する。OS レベルのキー/マウス注入が使えない環境でも
 * 実 framebuffer の証拠取得と実コマンドパケット送信ができるようにするためのもの。
 * gametest sourceSet 属 = 出荷 jar には入らない検証専用コード。
 *
 * <p>コマンド:
 * <ul>
 *   <li>{@code status} — 照準の当たっているブロック・座標の claim lookup・
 *       provider 設定値を latest.log に記録</li>
 *   <li>{@code screenshot <name>} — {@code Screenshot.grab} で実 framebuffer を
 *       {@code screenshots/} へ PNG 保存</li>
 *   <li>{@code look <x> <y> <z>} — 視線を指定座標（ブロック中心）へ向ける</li>
 *   <li>{@code cmd <command>} — 実コマンドパケットを送信（例: {@code ftbchunks claim}）</li>
 *   <li>{@code jade on|off} — Jade の plugin 設定本体（GUI が書く同一オブジェクト
 *       {@code PluginConfig.INSTANCE}）経由で本 provider を切替え、save() で
 *       {@code config/jade/plugins.json} へ書き出す</li>
 *   <li>{@code screen} — Jade の実 PluginsConfigScreen を開く（設定画面証拠用）</li>
 *   <li>{@code unscreen} — 開いた画面を閉じる</li>
 *   <li>{@code quit} — クライアントを正常終了</li>
 * </ul>
 */
@EventBusSubscriber(modid = JadeFtbClaims.MOD_ID, value = Dist.CLIENT)
public final class ClientAcceptProbe {

    private static final ResourceLocation PROVIDER_UID =
            ResourceLocation.fromNamespaceAndPath(JadeFtbClaims.MOD_ID, "claim_info");
    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss");

    private ClientAcceptProbe() {}

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) return;

        File file = new File(mc.gameDirectory, "probe-command.txt");
        if (!file.isFile()) return;

        List<String> lines;
        try {
            lines = Files.readAllLines(file.toPath(), StandardCharsets.UTF_8);
            Files.delete(file.toPath());
        } catch (IOException e) {
            log("command file read failed: " + e);
            return;
        }
        for (String line : lines) {
            String cmd = line.trim();
            if (cmd.isEmpty() || cmd.startsWith("#")) continue;
            try {
                execute(mc, cmd);
            } catch (Throwable t) {
                log("command failed: '" + cmd + "' -> " + t);
            }
        }
    }

    private static void execute(Minecraft mc, String cmd) throws Exception {
        String[] parts = cmd.split(" ", 2);
        String op = parts[0];
        String arg = parts.length > 1 ? parts[1].trim() : "";

        switch (op) {
            case "status" -> logStatus(mc);
            case "screenshot" -> {
                String name = (arg.isEmpty() ? "shot" : arg);
                String fileName = "accept-" + name + "-" + LocalDateTime.now().format(TS) + ".png";
                Screenshot.grab(mc.gameDirectory, fileName, mc.getMainRenderTarget(),
                        component -> log("screenshot saved: screenshots/" + fileName));
            }
            case "look" -> {
                String[] a = arg.split(" ");
                Vec3 target = new Vec3(Double.parseDouble(a[0]),
                        Double.parseDouble(a[1]), Double.parseDouble(a[2]));
                mc.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
                log(String.format("look -> (%.2f, %.2f, %.2f) yRot=%.1f xRot=%.1f",
                        target.x, target.y, target.z, mc.player.getYRot(), mc.player.getXRot()));
            }
            case "cmd" -> {
                if (!arg.isEmpty()) {
                    mc.player.connection.sendCommand(arg);
                    log("command packet sent: /" + arg);
                }
            }
            case "jade" -> {
                boolean enable = arg.equalsIgnoreCase("on");
                PluginConfig.INSTANCE.set(PROVIDER_UID, enable);
                PluginConfig.INSTANCE.save();
                log("provider " + PROVIDER_UID + " set -> " + enable
                        + " (readback=" + PluginConfig.INSTANCE.get(PROVIDER_UID)
                        + ", file=" + PluginConfig.INSTANCE.getFile() + ")");
            }
            case "screen" -> {
                try {
                    mc.setScreen(new PluginsConfigScreen(mc.screen));
                    log("PluginsConfigScreen opened");
                } catch (Throwable t) {
                    log("PluginsConfigScreen open failed: " + t);
                }
            }
            case "unscreen" -> {
                mc.setScreen(null);
                log("screen closed");
            }
            case "quit" -> {
                log("quit requested");
                mc.stop();
            }
            default -> log("unknown command: " + cmd);
        }
    }

    private static void logStatus(Minecraft mc) {
        LocalPlayer player = mc.player;
        StringBuilder sb = new StringBuilder();
        sb.append("status: player=").append(player.getGameProfile().getName());
        sb.append(" pos=").append(String.format("%.1f,%.1f,%.1f",
                player.getX(), player.getY(), player.getZ()));
        sb.append(" chunk=[").append(player.chunkPosition().x)
                .append(',').append(player.chunkPosition().z).append(']');
        HitResult hit = mc.hitResult;
        if (hit instanceof BlockHitResult bhr) {
            BlockPos bp = bhr.getBlockPos();
            BlockState state = mc.level.getBlockState(bp);
            Block block = state.getBlock();
            sb.append(" target=").append(bp.toShortString())
                    .append(' ').append(block.getDescriptionId());
            FtbClaimLookup.lookup(mc.level.dimension(), bp.getX(), bp.getZ())
                    .ifPresentOrElse(
                            info -> sb.append(" claim=").append(info.teamName().getString())
                                    .append(" forceLoaded=").append(info.forceLoaded()),
                            () -> sb.append(" claim=<none>"));
        } else {
            sb.append(" target=<none>");
        }
        sb.append(" providerEnabled=").append(PluginConfig.INSTANCE.get(PROVIDER_UID));
        log(sb.toString());
    }

    private static void log(String msg) {
        JadeFtbClaims.LOGGER.info("[ACCEPTPROBE] {}", Component.literal(msg).getString());
    }
}
