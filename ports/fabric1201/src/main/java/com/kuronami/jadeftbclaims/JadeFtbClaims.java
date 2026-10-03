package com.kuronami.jadeftbclaims;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Shared addon identity and logger. The only runtime entrypoint is Jade's client plugin. */
public final class JadeFtbClaims {
    public static final String MOD_ID = "jadeftbclaims";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    private JadeFtbClaims() {}
}
