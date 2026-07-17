package com.carlesso.pilatesapi.util;

/**
 * Máscaras de PII para uso em logs. Segue o mesmo padrão de ofuscação do script
 * de importação ({@code scripts/import_seufisio.py}): e-mail e CPF nunca aparecem
 * em claro nos logs, permitindo correlação sem expor o dado sensível.
 */
public final class LogMasker {

    private LogMasker() {}

    /**
     * Mascara um e-mail preservando a inicial do local e o domínio
     * (ex.: {@code joao@dominio.com} → {@code j***@dominio.com}).
     */
    public static String email(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int at = email.indexOf('@');
        if (at < 0) {
            return "***";
        }
        String local = email.substring(0, at);
        String domain = email.substring(at + 1);
        String inicial = local.isEmpty() ? "" : local.substring(0, 1);
        return inicial + "***@" + domain;
    }

    /**
     * Mascara um CPF preservando apenas os dois últimos dígitos
     * (ex.: {@code 11122233344} → {@code ***.***.***-44}).
     */
    public static String cpf(String cpf) {
        if (cpf == null || cpf.isBlank()) {
            return "***";
        }
        return cpf.length() >= 2 ? "***.***.***-" + cpf.substring(cpf.length() - 2) : "***";
    }
}
