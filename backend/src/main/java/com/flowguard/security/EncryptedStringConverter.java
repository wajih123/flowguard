package com.flowguard.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter that transparently encrypts/decrypts String fields
 * using AES-256-GCM with a random 12-byte IV per value.
 *
 * <p>Stored format (Base64): {@code <12-byte-IV><encrypted-bytes>}
 *
 * <p>Usage on entity fields:
 * <pre>
 *   &#64;Convert(converter = EncryptedStringConverter.class)
 *   private String bridgeAccessToken;
 * </pre>
 *
 * <p>Env var: {@code ENCRYPTION_KEY} — 32-byte Base64-encoded AES key.
 * Generate with: {@code openssl rand -base64 32}
 */
@Converter
public class EncryptedStringConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTES = 12;
    private static final int GCM_TAG_LENGTH_BITS = 128;

    // Resolved once at startup — avoids CDI in a JPA converter
    private static volatile SecretKeySpec KEY;

    static {
        String raw = System.getenv("ENCRYPTION_KEY");
        if (raw == null || raw.isBlank()) {
            // Fallback for local dev ONLY — production MUST set ENCRYPTION_KEY
            raw = "M1324CXxoTaqeVFo0EDTloJiJAzefgLQJF7j98w9blw=";
        }
        byte[] keyBytes = Base64.getDecoder().decode(raw);
        if (keyBytes.length != 32) {
            throw new IllegalStateException(
                "ENCRYPTION_KEY must be a 32-byte (256-bit) Base64-encoded string. " +
                "Generate with: openssl rand -base64 32"
            );
        }
        KEY = new SecretKeySpec(keyBytes, "AES");
    }

    @Override
    public String convertToDatabaseColumn(String plaintext) {
        if (plaintext == null) return null;
        try {
            byte[] iv = new byte[IV_LENGTH_BYTES];
            new SecureRandom().nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] encrypted = cipher.doFinal(plaintext.getBytes(java.nio.charset.StandardCharsets.UTF_8));

            // Prepend IV to ciphertext, then Base64-encode the whole thing
            byte[] combined = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, combined, 0, iv.length);
            System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

            return Base64.getEncoder().encodeToString(combined);
        } catch (Exception e) {
            throw new RuntimeException("Encryption failed", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String ciphertext) {
        if (ciphertext == null) return null;
        try {
            byte[] combined = Base64.getDecoder().decode(ciphertext);

            byte[] iv = new byte[IV_LENGTH_BYTES];
            byte[] encrypted = new byte[combined.length - IV_LENGTH_BYTES];
            System.arraycopy(combined, 0, iv, 0, IV_LENGTH_BYTES);
            System.arraycopy(combined, IV_LENGTH_BYTES, encrypted, 0, encrypted.length);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, KEY, new GCMParameterSpec(GCM_TAG_LENGTH_BITS, iv));
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, java.nio.charset.StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Decryption failed — check ENCRYPTION_KEY env var", e);
        }
    }
}
