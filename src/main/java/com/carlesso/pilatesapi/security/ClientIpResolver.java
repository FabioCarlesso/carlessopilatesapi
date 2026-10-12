package com.carlesso.pilatesapi.security;

import com.carlesso.pilatesapi.util.LogMasker;
import jakarta.servlet.http.HttpServletRequest;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.web.util.matcher.IpAddressMatcher;
import org.springframework.stereotype.Component;

/**
 * Resolve a origem do cliente usada como chave do rate limit por IP.
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
 *
 * <p>A resolução é feita aqui, e não pelo {@code RemoteIpValve} do Tomcat
 * ({@code server.forward-headers-strategy=native}), para não mudar o
 * {@code getRemoteAddr()} da aplicação inteira por causa do rate limit, para aceitar
 * proxies em CIDR em vez de regex e para que o comportamento seja exercitado pelos
 * testes via MockMvc, que não passam pelas válvulas do Tomcat.
 */
@Component
public class ClientIpResolver {

    private static final Logger log = LoggerFactory.getLogger(ClientIpResolver.class);

    static final String X_FORWARDED_FOR = "X-Forwarded-For";

    private static final Pattern IPV4 =
            Pattern.compile("^(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)(\\.(25[0-5]|2[0-4]\\d|1\\d\\d|[1-9]?\\d)){3}$");
    private static final Pattern IPV4_COM_PORTA = Pattern.compile("^(\\d{1,3}(?:\\.\\d{1,3}){3}):\\d{1,5}$");
    private static final Pattern IPV6_ENTRE_COLCHETES = Pattern.compile("^\\[([0-9a-fA-F:.]+)](?::\\d{1,5})?$");
    private static final Pattern IPV6 = Pattern.compile("^[0-9a-fA-F:][0-9a-fA-F:.]*$");

    /** Faixas de rede privada/loopback: conexões delas com X-Forwarded-For indicam um proxy na frente. */
    private static final List<IpAddressMatcher> REDES_INTERNAS =
            List.of("10.0.0.0/8", "172.16.0.0/12", "192.168.0.0/16", "127.0.0.0/8", "::1", "fc00::/7").stream()
                    .map(IpAddressMatcher::new)
                    .toList();

    private final List<IpAddressMatcher> proxiesConfiaveis;
    private final AtomicBoolean avisoProxyNaoConfiguradoEmitido = new AtomicBoolean();

    public ClientIpResolver(@Value("${app.security.trusted-proxies:}") String trustedProxies) {
        this.proxiesConfiaveis = Arrays.stream(trustedProxies.split(","))
                .map(String::trim)
                .filter(proxy -> !proxy.isEmpty())
                .map(IpAddressMatcher::new)
                .toList();
    }

    /**
     * Chave de origem para o rate limit: o IP do cliente, com endereços IPv6 agrupados
     * pelo prefixo {@code /64}. Um assinante IPv6 costuma receber o {@code /64} inteiro,
     * e contar por endereço completo deixaria rotacionar entre 2^64 endereços para nunca
     * atingir o limite.
     */
    public String resolverOrigem(HttpServletRequest request) {
        String ip = resolver(request);
        InetAddress endereco = literal(ip);
        if (!(endereco instanceof Inet6Address)) {
            return ip;
        }
        byte[] bytes = endereco.getAddress();
        Arrays.fill(bytes, 8, 16, (byte) 0);
        try {
            return InetAddress.getByAddress(bytes).getHostAddress() + "/64";
        } catch (UnknownHostException e) {
            throw new IllegalStateException("Endereço IPv6 de 16 bytes sempre é válido", e);
        }
    }

    /** IP do cliente em forma canônica. */
    public String resolver(HttpServletRequest request) {
        String remoteAddr = Objects.requireNonNullElse(canonico(request.getRemoteAddr()), request.getRemoteAddr());
        List<String> encaminhados = enderecosEncaminhados(request);
        if (!confiavel(remoteAddr)) {
            avisarSeHaProxyNaoConfigurado(remoteAddr, encaminhados);
            return remoteAddr;
        }
        for (int i = encaminhados.size() - 1; i >= 0; i--) {
            String endereco = canonico(normalizar(encaminhados.get(i)));
            if (endereco == null) {
                // Entrada que não é um IP literal (ex.: "unknown"): não há cliente confiável a
                // extrair dela, e usá-la como chave deixaria o limite ser contornado trocando o texto.
                log.warn(
                        "X-Forwarded-For com entrada que não é IP vinda do proxy {}; usando o endereço da conexão",
                        remoteAddr);
                return remoteAddr;
            }
            if (!confiavel(endereco)) {
                return endereco;
            }
        }
        // Todos os saltos são proxies confiáveis (ou não há cabeçalho): o mais à
        // esquerda é a origem mais distante conhecida.
        return encaminhados.isEmpty() ? remoteAddr : canonico(normalizar(encaminhados.get(0)));
    }

    private boolean confiavel(String endereco) {
        return endereco != null && proxiesConfiaveis.stream().anyMatch(proxy -> proxy.matches(endereco));
    }

    /**
     * Sem proxy confiável configurado, uma conexão de rede interna trazendo X-Forwarded-For é
     * quase sempre um proxy reverso na frente da aplicação. Nesse cenário todos os clientes
     * dividem o IP do proxy e o limite por IP vira um bloqueio global; avisa uma vez no log.
     */
    private void avisarSeHaProxyNaoConfigurado(String remoteAddr, List<String> encaminhados) {
        if (!proxiesConfiaveis.isEmpty() || encaminhados.isEmpty() || remoteAddr == null) {
            return;
        }
        if (REDES_INTERNAS.stream().anyMatch(rede -> rede.matches(remoteAddr))
                && avisoProxyNaoConfiguradoEmitido.compareAndSet(false, true)) {
            log.warn(
                    "Requisição com X-Forwarded-For vinda de rede interna ({}) sem APP_SECURITY_TRUSTED_PROXIES "
                            + "configurado: se a aplicação está atrás de um proxy reverso, todos os clientes "
                            + "compartilham o mesmo limite de tentativas por IP. Configure o IP/CIDR do proxy.",
                    LogMasker.ip(remoteAddr));
        }
    }

    /** Remove porta e colchetes que alguns proxies/balanceadores incluem na entrada. */
    private static String normalizar(String entrada) {
        var ipv6 = IPV6_ENTRE_COLCHETES.matcher(entrada);
        if (ipv6.matches()) {
            return ipv6.group(1);
        }
        var ipv4 = IPV4_COM_PORTA.matcher(entrada);
        if (ipv4.matches()) {
            return ipv4.group(1);
        }
        return entrada;
    }

    /** Forma canônica do IP ({@code ::ffff:1.2.3.4} vira {@code 1.2.3.4}), ou {@code null} se não for IP. */
    private static String canonico(String entrada) {
        InetAddress endereco = literal(entrada);
        return endereco == null ? null : endereco.getHostAddress();
    }

    /**
     * Interpreta um IP literal sem nunca cair em resolução DNS: o
     * {@link InetAddress#getByName} só é chamado para textos que já têm forma de IPv4
     * válido ou contêm {@code ':'} (que ele trata sempre como literal IPv6).
     */
    private static InetAddress literal(String entrada) {
        if (entrada == null) {
            return null;
        }
        boolean ipv4 = IPV4.matcher(entrada).matches();
        boolean ipv6 = entrada.indexOf(':') >= 0 && IPV6.matcher(entrada).matches();
        if (!ipv4 && !ipv6) {
            return null;
        }
        try {
            return InetAddress.getByName(entrada);
        } catch (UnknownHostException | IllegalArgumentException e) {
            return null;
        }
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
