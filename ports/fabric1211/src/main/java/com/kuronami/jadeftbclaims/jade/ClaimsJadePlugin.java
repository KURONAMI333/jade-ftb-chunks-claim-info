package com.kuronami.jadeftbclaims.jade;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Fabric の {@code jade} entrypoint として登録する Jade プラグイン。
 * {@code @WailaPlugin("ftbchunks")} は Jade がプラグインを識別するために使う。
 */
@WailaPlugin(JadeFtbClaimsIds.FTB_CHUNKS_MOD_ID)
public final class ClaimsJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ClaimComponentProvider.INSTANCE, Block.class);
    }
}
