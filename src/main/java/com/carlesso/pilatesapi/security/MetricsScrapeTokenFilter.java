package com.carlesso.pilatesapi.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Credencial fixa e dedicada ao scrape do Prometheus ({@code APP_METRICS_SCRAPE_TOKEN}).
 * O access token JWT expira em minutos, então não serve de credencial estática no
 * {@code prometheus.yml}. Este filtro só age em {@value #PATH} e concede apenas
 * {@code ROLE_METRICS}, que nenhuma outra rota aceita: vazar o token expõe
 * métricas, nunca dados de negócio. Sem a variável configurada, o filtro fica
 * inerte e o endpoint segue exigindo um JWT de ADMIN.
 *
 * <p>Não é {@code @Component} de propósito: o Spring Boot registraria o filtro
 * também na cadeia do servlet. Ele é instanciado no {@code SecurityConfig}.
 */
public class MetricsScrapeTokenFilter extends OncePerRequestFilter {

    public static final String PATH = "/actuator/prometheus";
    public static final String ROLE = "METRICS";
    static final int TAMANHO_MINIMO = 32;

    private final byte[] token;

    public MetricsScrapeTokenFilter(String token) {
        if (token == null || token.isBlank()) {
            this.token = null;
            return;
        }
        if (token.length() < TAMANHO_MINIMO) {
            throw new IllegalStateException(
                    "APP_METRICS_SCRAPE_TOKEN deve ter ao menos " + TAMANHO_MINIMO + " caracteres");
        }
        this.token = token.getBytes(StandardCharsets.UTF_8);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String caminho =
                request.getRequestURI().substring(request.getContextPath().length());
        return token == null || !PATH.equals(caminho);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null
                && authHeader.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null
                && MessageDigest.isEqual(token, authHeader.substring(7).getBytes(StandardCharsets.UTF_8))) {
            var authentication = new UsernamePasswordAuthenticationToken(
                    "prometheus-scrape", null, List.of(new SimpleGrantedAuthority("ROLE_" + ROLE)));
            SecurityContextHolder.getContext().setAuthentication(authentication);
        }
        filterChain.doFilter(request, response);
    }
}
