package com.kuronami.jadeftbclaims.jade;

import net.minecraft.world.level.block.Block;
import snownee.jade.api.IWailaClientRegistration;
import snownee.jade.api.IWailaPlugin;
import snownee.jade.api.WailaPlugin;

/**
 * Jade への登録口。{@code @WailaPlugin("ftbchunks")} は FTB Chunks が
 * 存在する時だけ Jade がこのクラスをロードする印（NeoForge では annotation
 * スキャン側が modid で絞る）。mods.toml でも ftbchunks/jade を required に
 * してあるので、実運用でここへ来る時点では両方揃っている。
 */
@WailaPlugin(JadeFtbClaimsIds.FTB_CHUNKS_MOD_ID)
public final class ClaimsJadePlugin implements IWailaPlugin {

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registration.registerBlockComponent(ClaimComponentProvider.INSTANCE, Block.class);
    }
}
