package com.geekup.eventticketbookingservice;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.BufferedWriter;
import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Standalone Java Generator for 50,000 JMeter User Tokens.
 * Can be run via: java jmeter/data/GenerateTestData.java
 */
public class GenerateTestData {

    private static final String DEFAULT_SECRET_KEY = "404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970";
    private static final int TOTAL_USERS = 50000;
    private static final int USER_ID_OFFSET = 4;
    private static final String OUTPUT_CSV = "jmeter/data/users_tokens.csv";

    public static void main(String[] args) throws Exception {
        String secretKey = System.getenv("JWT_SECRET_KEY");
        if (secretKey == null || secretKey.isBlank()) {
            secretKey = DEFAULT_SECRET_KEY;
        }

        System.out.println("=== Generating " + TOTAL_USERS + " Test User Tokens (Java) ===");
        long start = System.currentTimeMillis();

        Base64.Encoder encoder = Base64.getUrlEncoder().withoutPadding();
        String headerJson = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String headerB64 = encoder.encodeToString(headerJson.getBytes(StandardCharsets.UTF_8));

        Mac hmacSha256 = Mac.getInstance("HmacSHA256");
        SecretKeySpec secretKeySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        hmacSha256.init(secretKeySpec);

        long nowSec = System.currentTimeMillis() / 1000L;
        long expSec = nowSec + (30L * 24 * 60 * 60);

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(OUTPUT_CSV, StandardCharsets.UTF_8))) {
            writer.write("user_id,email,token\n");

            for (int i = 1; i <= TOTAL_USERS; i++) {
                int userId = USER_ID_OFFSET + i - 1;
                String email = "perf_user_" + i + "@perf.com";

                String payloadJson = "{\"sub\":\"" + email + "\",\"role\":\"CUSTOMER\",\"id\":" + userId + ",\"iat\":" + nowSec + ",\"exp\":" + expSec + "}";
                String payloadB64 = encoder.encodeToString(payloadJson.getBytes(StandardCharsets.UTF_8));

                String signingInput = headerB64 + "." + payloadB64;
                byte[] signature = hmacSha256.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
                String sigB64 = encoder.encodeToString(signature);

                String token = signingInput + "." + sigB64;
                writer.write(userId + "," + email + "," + token + "\n");

                if (i % 10000 == 0) {
                    System.out.println("Progress: " + i + " / " + TOTAL_USERS + " tokens generated...");
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        System.out.println("✅ Finished generating " + TOTAL_USERS + " tokens in " + (elapsed / 1000.0) + "s!");
        System.out.println("Saved to: " + OUTPUT_CSV);
    }
}
