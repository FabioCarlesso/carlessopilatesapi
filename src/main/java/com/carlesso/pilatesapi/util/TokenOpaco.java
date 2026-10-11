package com.carlesso.pilatesapi.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Tokens opacos de uso único (redefinição de senha, refresh token): 32 bytes
 * aleatórios em Base64 URL-safe para o cliente e apenas o hash SHA-256 em hex
 * para o banco, de modo que um vazamento da tabela não revele tokens válidos.
 */
public final class TokenOpaco {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private TokenOpaco() {}

    public static String gerar() {
        byte[] bytes = new byte[32];
        SECURE_RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public static String hash(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de hash indisponível", e);
        }
    }
}
