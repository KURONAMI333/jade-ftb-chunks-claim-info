package com.kuronami.jadeftbclaims.jade;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade への登録口。Forge の mod scan が検出する {@code @WailaPlugin("ftbchunks")}
 * で連携先 mod を指定する。Jade は物理 client 上だけで {@code registerClient} を呼ぶ。
 */
@WailaPlugin(JadeFtbClaimsIds.FTB_CHUNKS_MOD_ID)
public final class ClaimsJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ClaimComponentProvider.INSTANCE, Block.class);
    }
}
