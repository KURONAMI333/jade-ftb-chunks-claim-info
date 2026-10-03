package com.kuronami.jadeftbclaims.jade;

import com.kuronami.jadeftbclaims.client.ClaimTooltip;
import com.kuronami.jadeftbclaims.ftb.ClaimInfo;
import com.kuronami.jadeftbclaims.ftb.FtbClaimLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

import java.util.Optional;

/**
 * 注視先ブロックが属するチャンクの FTB Chunks クレームを tooltip に1行足す。
 *
 * <p>表示しない（空を返す）のは: 未クレーム・hidden team（サーバーが物理的に
 * 送ってこない）・同期未取得・FTB Chunks の map データ無し・内部層が壊れた時。
 * 「情報が無い」と「wilderness」を区別して何か出すことはしない。
 */
public final class ClaimComponentProvider implements IBlockComponentProvider {

    static final ClaimComponentProvider INSTANCE = new ClaimComponentProvider();

    private ClaimComponentProvider() {}

    @Override
    public ResourceLocation getUid() {
        return JadeFtbClaimsIds.CLAIM_INFO;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockPos pos = accessor.getPosition();
        if (pos == null || accessor.getLevel() == null) return;

        Optional<ClaimInfo> info =
                FtbClaimLookup.lookup(accessor.getLevel().dimension(), pos.getX(), pos.getZ());
        info.ifPresent(i -> ClaimTooltip.add(tooltip, i));
    }
}
