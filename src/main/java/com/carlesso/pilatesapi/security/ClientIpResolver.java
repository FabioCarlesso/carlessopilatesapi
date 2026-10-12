package com.carlesso.pilatesapi.security;

import jakarta.servlet.http.HttpServletRequest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Resolve o IP de origem usado como chave do rate limit por IP.
 *
 * <p>O {@code X-Forwarded-For} é escrito pelo cliente e só merece crédito quando a
 * conexão chega de um proxy confiável ({@code app.security.trusted-proxies}). Sem
 * proxies configurados o cabeçalho é ignorado e vale o endereço da conexão — do
 * contrário bastaria variar o cabeçalho a cada requisição para escapar do limite.
 *
 * <p>Com proxy confiável, a lista é lida da direita para a esquerda: cada proxy
 * acrescenta ao fim o endereço de quem o chamou, então o primeiro endereço não
 * confiável a partir da direita é o cliente real. Tudo o que estiver à esquerda dele
 * pode ter sido forjado pelo próprio cliente e é descartado.
 */
@Component
public class ClientIpResolver {

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private final List<IpAddressMatcher> proxiesConfiaveis;

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.proxiesConfiaveis = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(proxy -> !proxy.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    public String resolver(HttpServletRequest request) {
        String remoteAddr = request.getRemoteAddr();
        if (!confiavel(remoteAddr)) {
            return remoteAddr;
        }
        List<String> encaminhados = enderecosEncaminhados(request);
        for (int i = encaminhados.size() - 1; i >= 0; i--) {
            String endereco = encaminhados.get(i);
            try {
                if (!confiavel(endereco)) {
                    return endereco;
                }
            } catch (IllegalArgumentException e) {
                // Entrada que não é um IP literal: não há cliente confiável a extrair dela,
                // e usá-la como chave deixaria o limite ser contornado trocando o texto.
                return remoteAddr;
            }
        }
        // Todos os saltos são proxies confiáveis (ou não há cabeçalho): o mais à
        // esquerda é a origem mais distante conhecida.
        return encaminhados.isEmpty() ? remoteAddr : encaminhados.get(0);
    }

    private boolean confiavel(String endereco) {
        return proxiesConfiaveis.stream().anyMatch(proxy -> proxy.matches(endereco));
    }

    private static List<String> enderecosEncaminhados(HttpServletRequest request) {
        List<String> enderecos = new ArrayList<>();
        // Vários proxies podem repetir o cabeçalho em vez de concatenar: a ordem das
        // linhas preserva a ordem dos saltos.
        for (String linha : Collections.list(request.getHeaders(X_FORWARDED_FOR))) {
            for (String endereco : linha.split(",")) {
                if (!endereco.isBlank()) {
                    enderecos.add(endereco.trim());
                }
            }
        }
        return enderecos;
    }
}
