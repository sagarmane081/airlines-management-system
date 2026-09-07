package com.services.config;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Hashes a raw JWT down to a fixed-length Redis key, so the revoked-tokens blocklist never has to
 * store a working bearer token in plaintext. Deliberately duplicated in api-gateway rather than
 * shared via common-lib - common-lib is DTOs only, same reasoning already applied to Feign clients,
 * exceptions, and ownership checks elsewhere in this codebase. Both copies MUST stay identical, or
 * a token revoked here won't match the key api-gateway checks on every request.
 */
public final class TokenHasher {

    private static final String REVOKED_TOKEN_KEY_PREFIX = "revoked-tokens::";

    private TokenHasher() {}

    public static String revokedTokenKey(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return REVOKED_TOKEN_KEY_PREFIX + HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
