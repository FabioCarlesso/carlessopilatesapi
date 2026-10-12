package com.carlesso.pilatesapi.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.mock.web.MockHttpServletRequest;

@ExtendWith(OutputCaptureExtension.class)
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

        assertThat(resolver.resolver(request("::1", "2001:db8::1"))).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void entradaComPorta_removeAPortaAntesDeComparar() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolver(request(PROXY, CLIENTE + ":51234"))).isEqualTo(CLIENTE);
        assertThat(resolver.resolver(request(PROXY, "[2001:db8::1]:443"))).isEqualTo("2001:db8:0:0:0:0:0:1");
        assertThat(resolver.resolver(request(PROXY, "[2001:db8::1]"))).isEqualTo("2001:db8:0:0:0:0:0:1");
    }

    @Test
    void entradaComOctetoInvalido_naoEInterpretadaComoHost() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolver(request(PROXY, "999.1.1.1"))).isEqualTo(PROXY);
        assertThat(resolver.resolver(request(PROXY, "unknown"))).isEqualTo(PROXY);
    }

    @Test
    void ipv4MapeadoEmIpv6_eNormalizadoParaIpv4() {
        var resolver = new ClientIpResolver(PROXY);

        assertThat(resolver.resolver(request(PROXY, "::ffff:" + CLIENTE))).isEqualTo(CLIENTE);
    }

    @Test
    void resolverOrigem_ipv4_usaOEnderecoCompleto() {
        var resolver = new ClientIpResolver("");
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(CLIENTE);

        assertThat(resolver.resolverOrigem(request)).isEqualTo(CLIENTE);
    }

    @Test
    void resolverOrigem_ipv6_agrupaPeloPrefixo64() {
        var resolver = new ClientIpResolver("");
        var primeiro = new MockHttpServletRequest();
        primeiro.setRemoteAddr("2001:db8:aa:bb:1:2:3:4");
        var outroDoMesmoPrefixo = new MockHttpServletRequest();
        outroDoMesmoPrefixo.setRemoteAddr("2001:db8:aa:bb:ffff::9");
        var outroPrefixo = new MockHttpServletRequest();
        outroPrefixo.setRemoteAddr("2001:db8:aa:bc::1");

        assertThat(resolver.resolverOrigem(primeiro)).isEqualTo("2001:db8:aa:bb:0:0:0:0/64");
        assertThat(resolver.resolverOrigem(outroDoMesmoPrefixo)).isEqualTo(resolver.resolverOrigem(primeiro));
        assertThat(resolver.resolverOrigem(outroPrefixo)).isNotEqualTo(resolver.resolverOrigem(primeiro));
    }

    @Test
    void semProxyConfigurado_conexaoInternaComXForwardedFor_avisaUmaVezNoLog(CapturedOutput output) {
        var resolver = new ClientIpResolver("");

        resolver.resolver(request("172.18.0.5", CLIENTE));
        resolver.resolver(request("172.18.0.5", CLIENTE));

        assertThat(output.getOut().split("APP_SECURITY_TRUSTED_PROXIES", -1)).hasSize(2);
    }

    @Test
    void semProxyConfigurado_conexaoPublicaComXForwardedFor_naoAvisa(CapturedOutput output) {
        var resolver = new ClientIpResolver("");

        resolver.resolver(request(CLIENTE, "198.51.100.7"));

        assertThat(output.getOut()).doesNotContain("APP_SECURITY_TRUSTED_PROXIES");
    }

    private static MockHttpServletRequest request(String remoteAddr, String xForwardedFor) {
        var request = new MockHttpServletRequest();
        request.setRemoteAddr(remoteAddr);
        request.addHeader(ClientIpResolver.X_FORWARDED_FOR, xForwardedFor);
        return request;
    }
}
