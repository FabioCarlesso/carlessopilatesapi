package com.carlesso.pilatesapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class ClientIpResolverTest {

    private static final String CLIENTE = "203.0.113.10";
    private static final String PROXY = "10.0.0.2";

    @Test
    void semProxiesConfiaveis_ignoraXForwardedFor() {
        var resolver = new ClientIpResolver("");

        assertThat(resolver.resolver(request(CLIENTE, "198.51.100.7"))).isEqualTo(CLIENTE);
    }

    @Test
    void conexaoDeOrigemNaoConfiavel_ignoraXForwardedFor() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolver(request(CLIENTE, "198.51.100.7"))).isEqualTo(CLIENTE);
    }

    @Test
    void conexaoDeProxyConfiavel_usaEnderecoAcrescentadoPeloProxy() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolver(request(PROXY, CLIENTE))).isEqualTo(CLIENTE);
    }

    @Test
    void conexaoDeProxyConfiavel_descartaEnderecosForjadosPeloClienteAEsquerda() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolver(request(PROXY, "1.2.3.4, 5.6.7.8, " + CLIENTE)))
                .isEqualTo(CLIENTE);
    }

    @Test
    void cadeiaDeProxiesConfiaveis_pulaTodosOsSaltosConfiaveisPorCidr() {
        var resolver = new ClientIpResolver(" 10.0.0.0/8 , 172.18.0.0/16 ");

        assertThat(resolver.resolver(request(PROXY, "1.2.3.4, " + CLIENTE + ", 172.18.0.5")))
                .isEqualTo(CLIENTE);
    }

    @Test
    void cabecalhoRepetido_consideraAsLinhasNaOrdemDosSaltos() {
        var resolver = new ClientIpResolver(PROXY);
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, "1.2.3.4");
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, CLIENTE);

        assertThat(resolver.resolver(request)).isEqualTo(CLIENTE);
    }

    @Test
    void proxyConfiavelSemCabecalho_usaEnderecoDaConexao() {
        var resolver = new ClientIpResolver(PROXY);
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(PROXY);

        assertThat(resolver.resolver(request)).isEqualTo(PROXY);
    }

    @Test
    void todosOsSaltosConfiaveis_usaOrigemMaisDistante() {
        var resolver = new ClientIpResolver("10.0.0.0/8");

        assertThat(resolver.resolver(request(PROXY, "10.0.0.7, 10.0.0.9"))).isEqualTo("10.0.0.7");
    }

    @Test
    void entradaQueNaoEIp_usaEnderecoDaConexao() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolver(request(PROXY, "nao-e-um-ip"))).isEqualTo(PROXY);
    }

    @Test
    void enderecoIpv6_eResolvidoPeloProxyConfiavel() {
        var resolver = new ClientIpResolver("::1");

        assertThat(resolver.resolver(request("::1", "2001:db8::1"))).isEqualTo("2001:db8::1");
    }

    private static MockHttpServletRequest request(String remoteAddr, String xForwardedFor) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, xForwardedFor);
        return request;
    }
}
