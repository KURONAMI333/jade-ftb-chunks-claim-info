package com.kuronami.jadeftbclaims;

/** Dedicated-server-safe side boundary for the client-only Jade integration. */
final class ClientEntrypoint {
    private ClientEntrypoint() {}

    static void init() {
        // Jade discovers ClaimsJadePlugin through @WailaPlugin on the client.
    }
}
