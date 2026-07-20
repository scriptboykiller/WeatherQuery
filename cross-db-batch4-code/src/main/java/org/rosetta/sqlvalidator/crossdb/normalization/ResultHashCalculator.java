package org.rosetta.sqlvalidator.crossdb.normalization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class ResultHashCalculator {

    public String hash(NormalizedResult result) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            update(digest, result.columnSignature());
            for (String row : result.normalizedRows()) {
                update(digest, row);
            }
            return HexFormat.of().formatHex(digest.digest());
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }

    private void update(MessageDigest digest, String value) {
        digest.update(value.getBytes(StandardCharsets.UTF_8));
        digest.update((byte) '\n');
    }
}
