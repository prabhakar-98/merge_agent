package com.helpagent.action.security;

import org.apache.commons.codec.digest.HmacAlgorithms;
import org.apache.commons.codec.digest.HmacUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.security.MessageDigest;

/**
 * Verifies GitHub webhook payload signatures using HMAC-SHA256.
 * Ensures requests genuinely originate from GitHub.
 */
@Component
public class WebhookSignatureVerifier {

    private static final Logger log = LoggerFactory.getLogger(WebhookSignatureVerifier.class);
    private static final String SIGNATURE_PREFIX = "sha256=";

    @Value("${github.webhook-secret}")
    private String webhookSecret;

    /**
     * Verifies the X-Hub-Signature-256 header against the raw request body.
     *
     * @param signatureHeader the value of the X-Hub-Signature-256 header
     * @param payload         the raw request body bytes
     * @return true if the signature is valid
     */
    public boolean isValid(String signatureHeader, byte[] payload) {
        if (signatureHeader == null || signatureHeader.isBlank()) {
            log.warn("Missing webhook signature header");
            return false;
        }

        if (webhookSecret == null || webhookSecret.isBlank()) {
            log.warn("Webhook secret not configured — skipping verification (NOT recommended for production)");
            return true;
        }

        if (!signatureHeader.startsWith(SIGNATURE_PREFIX)) {
            log.warn("Invalid signature format — expected sha256= prefix");
            return false;
        }

        String expectedSignature = signatureHeader.substring(SIGNATURE_PREFIX.length());
        String computedSignature = new HmacUtils(HmacAlgorithms.HMAC_SHA_256, webhookSecret)
                .hmacHex(payload);

        // Use constant-time comparison to prevent timing attacks
        boolean valid = MessageDigest.isEqual(
                expectedSignature.getBytes(),
                computedSignature.getBytes()
        );

        if (!valid) {
            log.warn("Webhook signature verification failed");
        }

        return valid;
    }
}
