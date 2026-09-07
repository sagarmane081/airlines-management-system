package com.services.config;

public class TwilioConstant {

    // Real secrets only - the from-phone-number isn't one, and is threaded via a normal Spring
    // @Value placeholder instead (config-repo/notification-service.yml's twilio.from-phone-number),
    // specifically so SmsService's constructor never has to touch this class directly and
    // SmsServiceTest can construct a real SmsService with a literal string, no env vars required -
    // same reasoning AuthServiceTest already established for keeping JwtConstant out of a test's
    // construction path.
    public static final String ACCOUNT_SID = requireEnv("TWILIO_ACCOUNT_SID");
    public static final String AUTH_TOKEN = requireEnv("TWILIO_AUTH_TOKEN");

    private TwilioConstant() {}

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(name + " environment variable is not set");
        }
        return value;
    }
}
