package com.walnutt.web.auth;

import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.util.Base64;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;

/**
 * PBKDF2WithHmacSHA256, 210,000 iterations (OWASP 2023 guidance), per-user random
 * salt - per API_CONTRACT.md. Deliberately javax.crypto only, no new dependency,
 * and deliberately NOT plain SHA-256/MD5.
 */
public final class PasswordHasher {
    private static final int ITERATIONS = 210_000;
    private static final int KEY_LENGTH_BITS = 256;
    private static final int SALT_LENGTH_BYTES = 16;
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {
    }

    public record HashResult(String saltBase64, String hashBase64) {
    }

    public static HashResult hash(char[] password) {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        RANDOM.nextBytes(salt);
        byte[] hash = pbkdf2(password, salt);
        return new HashResult(
            Base64.getEncoder().encodeToString(salt),
            Base64.getEncoder().encodeToString(hash)
        );
    }

    public static boolean verify(char[] password, String saltBase64, String expectedHashBase64) {
        byte[] salt = Base64.getDecoder().decode(saltBase64);
        byte[] actual = pbkdf2(password, salt);
        byte[] expected = Base64.getDecoder().decode(expectedHashBase64);
        return constantTimeEquals(actual, expected);
    }

    private static byte[] pbkdf2(char[] password, byte[] salt) {
        PBEKeySpec spec = new PBEKeySpec(password, salt, ITERATIONS, KEY_LENGTH_BITS);
        try {
            SecretKeyFactory factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("PBKDF2WithHmacSHA256 unavailable on this JVM", e);
        } finally {
            spec.clearPassword();
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int diff = 0;
        for (int i = 0; i < a.length; i++) {
            diff |= a[i] ^ b[i];
        }
        return diff == 0;
    }
}
