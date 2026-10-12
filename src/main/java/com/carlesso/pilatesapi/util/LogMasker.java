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

    /**
     * Mascara um IP preservando só o prefixo de rede, o bastante para correlacionar
     * tentativas de uma mesma origem sem registrar o endereço do assinante
     * (ex.: {@code 203.0.113.10} → {@code 203.0.113.***};
     * {@code 2001:db8:0:0:0:0:0:0/64} → {@code 2001:db8:***}).
     */
    public static String ip(String ip) {
        if (ip == null || ip.isBlank()) {
            return "***";
        }
        if (ip.indexOf(':') >= 0) {
            String[] grupos = ip.split(":", -1);
            return grupos.length >= 3 ? grupos[0] + ":" + grupos[1] + ":***" : "***";
        }
        int ultimoPonto = ip.lastIndexOf('.');
        return ultimoPonto > 0 ? ip.substring(0, ultimoPonto) + ".***" : "***";
    }
}
