package com.govlyx.AI.security;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * JPA AttributeConverter for encrypting/decrypting sensitive emails at rest with AES-256-GCM.
 * Prepends a random 12-byte IV to the ciphertext and Base64 encodes the result.
 */
@Converter
@Component
@Slf4j
public class AesGcmEmailConverter implements AttributeConverter<String, String> {

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int IV_LENGTH_BYTE = 12;
    private static final int TAG_LENGTH_BIT = 128;

    private static SecretKey secretKey;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    public AesGcmEmailConverter(
            @Value("${govlyx.security.aes-key:1KneeS70HLdW2vW/8hLKnKcG+K34jHfSlCyfty51gMU=}") String base64Key) {
        setStaticKey(base64Key);
    }

    private static synchronized void setStaticKey(String base64Key) {
        if (secretKey == null && base64Key != null && !base64Key.isBlank()) {
            byte[] decodedKey = Base64.getDecoder().decode(base64Key.trim());
            secretKey = new SecretKeySpec(decodedKey, "AES");
        }
    }

    @Override
    public String convertToDatabaseColumn(String plainEmail) {
        if (plainEmail == null || plainEmail.isBlank()) {
            return null;
        }
        try {
            ensureKey();
            byte[] iv = new byte[IV_LENGTH_BYTE];
            SECURE_RANDOM.nextBytes(iv);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] cipherText = cipher.doFinal(plainEmail.trim().getBytes(StandardCharsets.UTF_8));

            ByteBuffer byteBuffer = ByteBuffer.allocate(iv.length + cipherText.length);
            byteBuffer.put(iv);
            byteBuffer.put(cipherText);

            return Base64.getEncoder().encodeToString(byteBuffer.array());
        } catch (Exception e) {
            log.error("Failed to encrypt email with AES-GCM: {}", e.getMessage());
            throw new IllegalStateException("Email encryption failure", e);
        }
    }

    @Override
    public String convertToEntityAttribute(String encryptedEmail) {
        if (encryptedEmail == null || encryptedEmail.isBlank()) {
            return null;
        }
        // If it still contains '@' and no Base64 payload, it is a plaintext email during migration
        if (encryptedEmail.contains("@") && !isLikelyBase64(encryptedEmail)) {
            return encryptedEmail;
        }
        try {
            ensureKey();
            byte[] decoded = Base64.getDecoder().decode(encryptedEmail);
            if (decoded.length < IV_LENGTH_BYTE) {
                return encryptedEmail; // Fallback
            }

            ByteBuffer byteBuffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[IV_LENGTH_BYTE];
            byteBuffer.get(iv);

            byte[] cipherText = new byte[byteBuffer.remaining()];
            byteBuffer.get(cipherText);

            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, secretKey, new GCMParameterSpec(TAG_LENGTH_BIT, iv));
            byte[] plainText = cipher.doFinal(cipherText);

            return new String(plainText, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.warn("Failed to decrypt email (might be plaintext during migration): {}", e.getMessage());
            return encryptedEmail;
        }
    }

    private void ensureKey() {
        if (secretKey == null) {
            throw new IllegalStateException("AES key has not been initialized in AesGcmEmailConverter");
        }
    }

    private boolean isLikelyBase64(String str) {
        return str.length() > 30 && !str.contains(" ");
    }
}
