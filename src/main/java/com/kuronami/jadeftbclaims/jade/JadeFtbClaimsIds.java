package com.kuronami.jadeftbclaims.jade;

import com.kuronami.jadeftbclaims.JadeFtbClaims;
import net.minecraft.resources.ResourceLocation;

/** modid・uid の一元管理。 */
final class JadeFtbClaimsIds {

    static final String FTB_CHUNKS_MOD_ID = "ftbchunks";

    static final ResourceLocation CLAIM_INFO =
            ResourceLocation.fromNamespaceAndPath(JadeFtbClaims.MOD_ID, "claim_info");

    private JadeFtbClaimsIds() {}
}
