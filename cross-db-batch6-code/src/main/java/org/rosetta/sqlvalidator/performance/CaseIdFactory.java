package org.rosetta.sqlvalidator.performance;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class CaseIdFactory {

    public String create(PerformanceSourceRow row) {
        String base = row.effectiveSqlId();
        if (base == null || base.isBlank()) {
            base = "SQL";
        }
        String safe = base.replaceAll("[^A-Za-z0-9._-]", "_");
        if (safe.length() > 64) {
            safe = safe.substring(0, 64);
        }
        return safe + "-" + shortHash(row.sqlGroupKey());
    }

    private String shortHash(String value) {
        try {
            byte[] hash = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash).substring(0, 8);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }
}
