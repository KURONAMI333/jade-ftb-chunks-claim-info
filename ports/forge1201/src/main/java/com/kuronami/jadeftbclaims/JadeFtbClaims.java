package com.kuronami.jadeftbclaims;

import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Jade x FTB Chunks Claim Info.
 *
 * <p>注視先ブロックが属するチャンクのクレーム所有 team（と force-load 状態）を
 * Jade の tooltip に1行表示する client-only MOD。
 * 自分でパケット・mixin・server 側フックは持たず、FTB Chunks が既に client へ
 * 同期済みの map データを読むだけに留める。
 *
 * <p>Forge 1.20.1 では client-only 宣言は mods.toml の {@code clientSideOnly=true}
 * が担う（{@code @Mod} に dist 引数は無い）。server ではこの jar の class は
 * そもそもロードされない。
 */
@Mod(JadeFtbClaims.MOD_ID)
public final class JadeFtbClaims {

    public static final String MOD_ID = "jadeftbclaims";
    public static final Logger LOGGER = LoggerFactory.getLogger(JadeFtbClaims.class);

    public JadeFtbClaims() {
        // Jade 側の登録は @WailaPlugin 経由（ClaimsJadePlugin）で行われるため、
        // ここでは何もしない。
    }
}
