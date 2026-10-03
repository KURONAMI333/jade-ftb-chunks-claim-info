package com.kuronami.jadeftbclaims;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.common.Mod;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Jade x FTB Chunks Claim Info. */
@Mod(JadeFtbClaims.MOD_ID)
public final class JadeFtbClaims {
    public static final String MOD_ID = "jadeftbclaims";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public JadeFtbClaims() {
        // 1.19.2 @Mod has no dist selector. Keep the client entrypoint isolated so
        // dedicated servers never resolve client map classes from FTB Chunks.
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> ClientEntrypoint::init);
    }
}
