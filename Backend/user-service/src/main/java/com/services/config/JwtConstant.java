package com.services.config;

public class JwtConstant {

    public static final String SECRET_KEY = requireSecret();

    private JwtConstant() {}

    private static String requireSecret() {
        String secret = System.getenv("JWT_SECRET");
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET environment variable is not set");
        }
        return secret;
    }
}