package com.carlesso.pilatesapi.util;

import java.util.Locale;

public final class EmailNormalizer {

    private EmailNormalizer() {
    }

    public static String normalizar(String email) {
        return email.toLowerCase(Locale.ROOT);
    }
}
