// Copyright (c) Spirit Launcher
package com.spirit.launcher.auth;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Offline (cracked) account ka UUID, bilkul vanilla jaisa:
 * UUID v3 of "OfflinePlayer:<name>". Same naam = same UUID,
 * isliye cracked servers pe inventory/data wahi rehta hai.
 */
public final class OfflineUuid {
    private OfflineUuid() {}

    /** Dashes ke saath, jaise "5627dd98-e6be-3c21-b8a8-e92344183641". */
    public static String forName(String username) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + username).getBytes(StandardCharsets.UTF_8)).toString();
    }

    /** Bina dashes ke (launch args me --uuid ke liye aksar yahi chahiye). */
    public static String forNameNoDashes(String username) {
        return forName(username).replace("-", "");
    }
}
