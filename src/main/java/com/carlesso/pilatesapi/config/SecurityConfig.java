package com.carlesso.pilatesapi.config;

import com.carlesso.pilatesapi.exception.TipoProblema;
import com.carlesso.pilatesapi.security.JwtAuthenticationFilter;
import com.carlesso.pilatesapi.security.MetricsScrapeTokenFilter;
import com.carlesso.pilatesapi.security.ProblemaHttpWriter;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    static final String ERRO_NAO_AUTENTICADO = "Autenticação necessária para acessar este recurso";
    static final String ERRO_ACESSO_NEGADO = "Acesso negado";

    // Grupos de papéis por área (#131). ADMIN entra em todos; PACIENTE em nenhum.
    private static final String[] EQUIPE = {"ADMIN", "PROFISSIONAL", "RECEPCAO", "FINANCEIRO"};
    private static final String[] CLINICA = {"ADMIN", "PROFISSIONAL"};
    private static final String[] AGENDA = {"ADMIN", "PROFISSIONAL", "RECEPCAO"};
    private static final String[] COBRANCA = {"ADMIN", "RECEPCAO", "FINANCEIRO"};
    private static final String[] FISCAL = {"ADMIN", "FINANCEIRO"};

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            JwtAuthenticationFilter jwtAuthenticationFilter,
            AuthenticationProvider authenticationProvider,
            CorsConfigurationSource corsConfigurationSource,
            @Value("${app.metrics.scrape-token:}") String metricsScrapeToken,
            ObjectMapper objectMapper)
            throws Exception {
        ProblemaHttpWriter problemaWriter = new ProblemaHttpWriter(objectMapper);
        return http.csrf(csrf -> csrf.disable())
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                // Autorização é por URL, então 401 e 403 nascem no filter chain e nunca
                // chegam ao GlobalExceptionHandler; escreve aqui o mesmo ProblemDetail
                // (application/problem+json) para manter o contrato de erro da API.
                .exceptionHandling(exception -> exception
                        .authenticationEntryPoint((request, response, authException) -> problemaWriter.escrever(
                                request, response, TipoProblema.NAO_AUTENTICADO, ERRO_NAO_AUTENTICADO))
                        .accessDeniedHandler((request, response, accessDeniedException) -> problemaWriter.escrever(
                                request, response, TipoProblema.ACESSO_NEGADO, ERRO_ACESSO_NEGADO)))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authenticationProvider(authenticationProvider)
                .authorizeHttpRequests(auth -> auth.requestMatchers("/auth/**")
                        .permitAll()
                        .requestMatchers("/error")
                        .permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info")
                        .permitAll()
                        // Métricas (inclusive /actuator/prometheus) expõem detalhes internos
                        // da aplicação: restritas a ADMIN, nunca públicas. O scrape também aceita
                        // a credencial dedicada do MetricsScrapeTokenFilter (ROLE_METRICS).
                        .requestMatchers(MetricsScrapeTokenFilter.PATH)
                        .hasAnyRole("ADMIN", MetricsScrapeTokenFilter.ROLE)
                        .requestMatchers("/actuator/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/api-docs/**", "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html")
                        .permitAll()
                        .requestMatchers("/users/me", "/users/me/senha", "/users/me/preferencias")
                        .authenticated()
                        .requestMatchers("/users/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/admin/**")
                        .hasRole("ADMIN")
                        // Cadastro de bloqueios é tela de ADMIN, mas a consulta precisa ficar
                        // aberta à equipe: é ela que alimenta o aviso de feriado nas telas de
                        // agenda e de sessão — a ordem importa, o matcher de leitura precisa vir
                        // antes do geral.
                        // HEAD entra junto: o Spring MVC o resolve pelo handler de GET, mas a
                        // autorização não faz essa equivalência, e sem citá-lo aqui um HEAD do
                        // mesmo usuário que acabou de fazer GET cairia na regra de ADMIN e daria 403.
                        .requestMatchers(HttpMethod.GET, "/bloqueios", "/bloqueios/**")
                        .hasAnyRole(EQUIPE)
                        .requestMatchers(HttpMethod.HEAD, "/bloqueios", "/bloqueios/**")
                        .hasAnyRole(EQUIPE)
                        .requestMatchers("/bloqueios", "/bloqueios/**")
                        .hasRole("ADMIN")
                        // Listagem global de pagamentos e contatos de cobrança são da gestão de inadimplência,
                        // tela de ADMIN (#196, #197), assim como a exportação dela em XLSX (#232). Só GET/HEAD em
                        // /pagamentos exato e em /pagamentos/xlsx: o POST de criação e as demais rotas de
                        // /pagamentos/** seguem a regra financeira mais abaixo.
                        .requestMatchers(HttpMethod.GET, "/pagamentos", "/pagamentos/xlsx")
                        .hasRole("ADMIN")
                        .requestMatchers(HttpMethod.HEAD, "/pagamentos", "/pagamentos/xlsx")
                        .hasRole("ADMIN")
                        .requestMatchers("/pagamentos/*/cobrancas", "/pagamentos/*/cobrancas/**")
                        .hasRole("ADMIN")
                        // Indicadores históricos são gestão do negócio, tela de ADMIN (#202). O /dashboard/resumo
                        // segue aberto à equipe: alimenta o painel inicial de todos os perfis.
                        .requestMatchers("/dashboard/indicadores", "/dashboard/aulas-por-profissional")
                        .hasRole("ADMIN")
                        .requestMatchers("/dashboard/resumo")
                        .hasAnyRole(EQUIPE)
                        // Papéis de negócio (#131): prontuário só para quem atende, agenda para quem
                        // atende e para a recepção, dinheiro para recepção e financeiro, NFSe só para
                        // o financeiro. O cadastro de pacientes é a base comum de toda a equipe.
                        .requestMatchers(
                                "/anamneses/**",
                                "/avaliacoes-fisioterapeuticas/**",
                                "/avaliacoes-posturais/**",
                                "/evolucoes-sessao/**",
                                "/reavaliacoes/**",
                                "/planos-tratamento/**",
                                // Catálogo de opções da anamnese/avaliação (#248): só quem preenche o prontuário.
                                "/avaliacoes/opcoes")
                        .hasAnyRole(CLINICA)
                        // Atendimento da aula (#246): marca a aula realizada (agenda) e registra a evolução
                        // (prontuário). Fica com a interseção das duas áreas — a recepção realiza a aula por
                        // PATCH /aulas/{id}/realizar, mas não lê nem escreve evolução. Antes da regra de /aulas/**.
                        .requestMatchers("/aulas/*/atendimento")
                        .hasAnyRole(CLINICA)
                        // Dados clínicos que moram debaixo de /pacientes: o histórico de pontos de dor (#249) e o
                        // prontuário em PDF (#250). Antes da regra de /pacientes/**, que é da equipe toda e liberaria a
                        // recepção e o financeiro. A evolução avulsa (/evolucoes-sessao/{id}/pdf) já cai na regra
                        // clínica.
                        .requestMatchers(
                                "/pacientes/*/pontos-dor", "/pacientes/*/prontuario", "/pacientes/*/prontuario/**")
                        .hasAnyRole(CLINICA)
                        .requestMatchers("/pacientes", "/pacientes/**")
                        .hasAnyRole(EQUIPE)
                        // Tomadores da NFSE (#229): o formulário de paciente escolhe o tomador numa lista, então a
                        // leitura é da equipe; o cadastro é de quem cuida de cobrança e nota. HEAD junto com GET pelo
                        // mesmo motivo dos bloqueios.
                        .requestMatchers(HttpMethod.GET, "/tomadores", "/tomadores/**")
                        .hasAnyRole(EQUIPE)
                        .requestMatchers(HttpMethod.HEAD, "/tomadores", "/tomadores/**")
                        .hasAnyRole(EQUIPE)
                        .requestMatchers("/tomadores", "/tomadores/**")
                        .hasAnyRole(COBRANCA)
                        .requestMatchers(
                                "/aulas", "/aulas/**", "/sessoes", "/sessoes/**", "/lista-espera", "/lista-espera/**")
                        .hasAnyRole(AGENDA)
                        .requestMatchers("/planos", "/planos/**", "/pagamentos", "/pagamentos/**")
                        .hasAnyRole(COBRANCA)
                        .requestMatchers("/api/nfse-emitidas/**", "/api/relatorios/nfse", "/api/relatorios/nfse/**")
                        .hasAnyRole(FISCAL)
                        // Relatório financeiro consolidado (#108): receita e repasses de todos os profissionais,
                        // mesma área do relatório de repasse individual.
                        .requestMatchers("/api/relatorios/financeiro", "/api/relatorios/financeiro/**")
                        .hasAnyRole(FISCAL)
                        // Relatório de pacientes (#230): cadastro, contato e pendências — da recepção, que completa os
                        // cadastros; não traz dado clínico nem financeiro além do valor do plano.
                        .requestMatchers("/api/relatorios/pacientes", "/api/relatorios/pacientes/**")
                        .hasAnyRole("ADMIN", "RECEPCAO")
                        // Recebimentos do período (#231): a lista por trás da receita do relatório financeiro, na
                        // mesma área dele.
                        .requestMatchers("/api/relatorios/recebimentos", "/api/relatorios/recebimentos/**")
                        .hasAnyRole(FISCAL)
                        // Frequência e faltas por paciente (#233): acompanhamento da recepção, que age antes de o
                        // aluno desistir; só contagem de aulas, sem dado clínico nem financeiro.
                        .requestMatchers("/api/relatorios/frequencia", "/api/relatorios/frequencia/**")
                        .hasAnyRole("ADMIN", "RECEPCAO")
                        // Aulas realizadas sem evolução (#234): controle do prontuário por profissional, só do ADMIN.
                        // O profissional não vê nem as próprias: User e Profissional ainda não têm vínculo.
                        .requestMatchers("/api/relatorios/aulas-sem-evolucao", "/api/relatorios/aulas-sem-evolucao/**")
                        .hasRole("ADMIN")
                        // Ocupação da grade (#235): alunos e vagas por horário, para a recepção vender horários e
                        // usar a lista de espera; não traz dado financeiro nem clínico.
                        .requestMatchers("/api/relatorios/ocupacao", "/api/relatorios/ocupacao/**")
                        .hasAnyRole("ADMIN", "RECEPCAO")
                        // A agenda e o financeiro escolhem profissional numa lista, então a leitura é da
                        // equipe; o relatório de repasse é do financeiro e o cadastro, do ADMIN.
                        .requestMatchers(
                                "/profissionais/*/relatorio-pagamento", "/profissionais/*/relatorio-pagamento/**")
                        .hasAnyRole(FISCAL)
                        .requestMatchers(HttpMethod.GET, "/profissionais", "/profissionais/*")
                        .hasAnyRole(EQUIPE)
                        .requestMatchers(HttpMethod.HEAD, "/profissionais", "/profissionais/*")
                        .hasAnyRole(EQUIPE)
                        // Disponibilidade semanal (#216): mesmo recorte do cadastro de profissionais. A leitura
                        // (grade e verificação de horário) alimenta o aviso nas telas de agenda e sessão; a escrita
                        // — POST aqui e PUT/DELETE em /disponibilidades/{id} — é do ADMIN.
                        .requestMatchers(
                                HttpMethod.GET,
                                "/profissionais/*/disponibilidades",
                                "/profissionais/*/disponibilidades/verificar")
                        .hasAnyRole(EQUIPE)
                        .requestMatchers(
                                HttpMethod.HEAD,
                                "/profissionais/*/disponibilidades",
                                "/profissionais/*/disponibilidades/verificar")
                        .hasAnyRole(EQUIPE)
                        .requestMatchers("/profissionais", "/profissionais/**")
                        .hasRole("ADMIN")
                        .requestMatchers("/disponibilidades/**")
                        .hasRole("ADMIN")
                        // Menor privilégio: rota que ninguém citou acima é negada, inclusive para
                        // ADMIN — controller novo precisa entrar aqui com o papel certo.
                        .anyRequest()
                        .denyAll())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .addFilterAfter(new MetricsScrapeTokenFilter(metricsScrapeToken), JwtAuthenticationFilter.class)
                .build();
    }

    @Bean
    public AuthenticationProvider authenticationProvider(
            UserDetailsService userDetailsService, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        return provider;
    }

    @Bean
    public AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${app.cors.allowed-origins:http://localhost:4200}") String allowedOrigins) {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of(allowedOrigins.split(",")));
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        configuration.setExposedHeaders(List.of("Location", "Content-Disposition", "X-Request-Id"));
        // Sem allowCredentials: a autenticação é Bearer no header Authorization, não cookie,
        // então o navegador não tem credencial ambiente a enviar entre origens.

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
