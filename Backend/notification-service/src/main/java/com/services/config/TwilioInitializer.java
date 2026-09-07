package com.services.config;

import com.twilio.Twilio;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Component;

/**
 * Twilio.init(...) is a one-time, global, static setup call the SDK requires before any
 * Message.creator(...).create() call succeeds. Deliberately kept separate from SmsService itself
 * (rather than in its constructor) so SmsService has no direct dependency on TwilioConstant or the
 * real Twilio env vars - see TwilioConstant for the full reasoning.
 */
@Component
public class TwilioInitializer {

    @PostConstruct
    public void init() {
        Twilio.init(TwilioConstant.ACCOUNT_SID, TwilioConstant.AUTH_TOKEN);
    }
}
