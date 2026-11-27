package com.carlesso.pilatesapi.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carlesso.pilatesapi.entity.Anamnese;
import com.carlesso.pilatesapi.entity.AvaliacaoFisioterapeutica;
import com.carlesso.pilatesapi.entity.EvolucaoSessao;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.PlanoTratamento;
import com.carlesso.pilatesapi.entity.Reavaliacao;
import com.carlesso.pilatesapi.entity.SessaoPilates;
import com.carlesso.pilatesapi.entity.User;
import com.carlesso.pilatesapi.entity.enums.Role;
import com.carlesso.pilatesapi.entity.enums.StatusSessao;
import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import com.carlesso.pilatesapi.repository.UserRepository;
import com.carlesso.pilatesapi.service.JwtService;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import jakarta.persistence.EntityManager;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * Prontuário em PDF (#250) de ponta a ponta: autorização real (SecurityConfig + JWT), recorte pelo período nas
 * queries do Postgres e o {@code Content-Disposition} do contrato. Transacional: os dados e usuários criados aqui são
 * desfeitos ao fim de cada teste e não vazam para o container compartilhado.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ProntuarioControllerIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtService jwtService;

    private Paciente paciente;
    private EvolucaoSessao evolucaoDeMarco;

    @BeforeEach
    void setUp() {
        paciente = new Paciente();
        paciente.setNome("Ana Lúcia Silva");
        paciente.setCpf("12345678900");
        paciente.setDataNascimento(LocalDate.of(1985, 4, 15));
        entityManager.persist(paciente);

        Anamnese anamnese = new Anamnese();
        anamnese.setPaciente(paciente);
        anamnese.setQueixaPrincipal("Dor lombar ao sentar");
        anamnese.setObjetivos("Voltar a correr");
        entityManager.persist(anamnese);

        avaliacao(LocalDate.of(2025, 6, 1), "Diagnóstico antigo");
        avaliacao(LocalDate.of(2025, 12, 1), "Lombalgia mecânica");
        avaliacao(LocalDate.of(2026, 8, 1), "Diagnóstico futuro");

        reavaliacao(LocalDate.of(2025, 11, 1), "Reavaliação fora do período");
        reavaliacao(LocalDate.of(2026, 5, 1), "Dor caiu de 6 para 2");

        plano(LocalDate.of(2025, 1, 1), LocalDate.of(2025, 6, 30), "Plano encerrado");
        plano(LocalDate.of(2025, 12, 1), null, "Fortalecer core");

        evolucao(LocalDate.of(2025, 12, 20), "350544-F", "Evolução de dezembro");
        evolucaoDeMarco = evolucao(LocalDate.of(2026, 3, 10), "350544-F", "Ponte e alongamento");
        evolucao(LocalDate.of(2026, 4, 2), null, "Registro sem CREFITO");
        entityManager.flush();
    }

    @Test
    void prontuario_comPeriodo_recortaEvolucoesReavaliacoesEAvaliacoesVigentes() throws Exception {
        byte[] pdf = mvc.perform(get("/pacientes/{id}/prontuario/pdf", paciente.getId())
                        .param("inicio", "2026-01-01")
                        .param("fim", "2026-06-30")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.PROFISSIONAL)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"prontuario-ana-lucia-silva-2026-01-01-a-2026-06-30.pdf\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        String texto = texto(pdf);
        assertThat(texto)
                .contains("Ana Lúcia Silva")
                .contains("Dor lombar ao sentar")
                // A avaliação de 12/2025 ainda valia em 01/01/2026; a de 06/2025 já tinha sido substituída.
                .contains("Lombalgia mecânica")
                .doesNotContain("Diagnóstico antigo")
                .doesNotContain("Diagnóstico futuro")
                .contains("Fortalecer core")
                .doesNotContain("Plano encerrado")
                .contains("Dor caiu de 6 para 2")
                .doesNotContain("Reavaliação fora do período")
                .contains("Ponte e alongamento")
                .contains("Fisioterapeuta: Paula Mendes — CREFITO nº 350544-F")
                .contains("Fisioterapeuta: Paula Mendes — CREFITO não informado")
                .doesNotContain("Evolução de dezembro");
    }

    @Test
    void prontuario_semPeriodo_trazTudoENomeCompleto() throws Exception {
        byte[] pdf = mvc.perform(get("/pacientes/{id}/prontuario/pdf", paciente.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"prontuario-ana-lucia-silva-completo.pdf\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(texto(pdf))
                .contains("Diagnóstico antigo")
                .contains("Diagnóstico futuro")
                .contains("Reavaliação fora do período")
                .contains("Evolução de dezembro")
                .contains("Registro sem CREFITO");
    }

    @Test
    void prontuario_soComUmLadoDoPeriodo_usaInicioOuHojeNoNome() throws Exception {
        mvc.perform(get("/pacientes/{id}/prontuario/pdf", paciente.getId())
                        .param("inicio", "2026-01-01")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.PROFISSIONAL)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"prontuario-ana-lucia-silva-2026-01-01-a-hoje.pdf\""));
        mvc.perform(get("/pacientes/{id}/prontuario/pdf", paciente.getId())
                        .param("fim", "2026-06-30")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.PROFISSIONAL)))
                .andExpect(status().isOk())
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"prontuario-ana-lucia-silva-inicio-a-2026-06-30.pdf\""));
    }

    @Test
    void prontuario_inicioDepoisDoFim_retorna400() throws Exception {
        mvc.perform(get("/pacientes/{id}/prontuario/pdf", paciente.getId())
                        .param("inicio", "2026-07-01")
                        .param("fim", "2026-06-30")
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.PROFISSIONAL)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Período inicial não pode ser maior que o período final"));
    }

    @Test
    void prontuario_pacienteInexistente_retorna404() throws Exception {
        mvc.perform(get("/pacientes/{id}/prontuario/pdf", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.PROFISSIONAL)))
                .andExpect(status().isNotFound());
    }

    @Test
    void prontuarioEEvolucao_papeisSemAcessoClinico_recebem403() throws Exception {
        for (Role role : Set.of(Role.RECEPCAO, Role.FINANCEIRO, Role.PACIENTE)) {
            mvc.perform(get("/pacientes/{id}/prontuario/pdf", paciente.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isForbidden());
            mvc.perform(get("/evolucoes-sessao/{id}/pdf", evolucaoDeMarco.getId())
                            .header(HttpHeaders.AUTHORIZATION, bearer(role)))
                    .andExpect(status().isForbidden());
        }
    }

    @Test
    void evolucao_pdfComPacienteEAssinatura() throws Exception {
        byte[] pdf = mvc.perform(get("/evolucoes-sessao/{id}/pdf", evolucaoDeMarco.getId())
                        .header(HttpHeaders.AUTHORIZATION, bearer(Role.PROFISSIONAL)))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andExpect(header().string(
                                HttpHeaders.CONTENT_DISPOSITION,
                                "attachment; filename=\"evolucao-ana-lucia-silva-2026-03-10.pdf\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        assertThat(texto(pdf))
                .contains("Ana Lúcia Silva")
                .contains("Ponte e alongamento")
                .contains("Fisioterapeuta: Paula Mendes — CREFITO nº 350544-F")
                .doesNotContain("Registro sem CREFITO");
    }

    @Test
    void evolucao_inexistente_retorna404() throws Exception {
        mvc.perform(get("/evolucoes-sessao/{id}/pdf", 999_999L).header(HttpHeaders.AUTHORIZATION, bearer(Role.ADMIN)))
                .andExpect(status().isNotFound());
    }

    private void avaliacao(LocalDate data, String diagnostico) {
        AvaliacaoFisioterapeutica avaliacao = new AvaliacaoFisioterapeutica();
        avaliacao.setPaciente(paciente);
        avaliacao.setDataAvaliacao(data);
        avaliacao.setQueixaFuncional("Dificuldade para agachar");
        avaliacao.setEscalaDor(6);
        avaliacao.setDiagnosticoFisioterapeutico(diagnostico);
        entityManager.persist(avaliacao);
    }

    private void reavaliacao(LocalDate data, String evolucaoDor) {
        Reavaliacao reavaliacao = new Reavaliacao();
        reavaliacao.setPaciente(paciente);
        reavaliacao.setDataReavaliacao(data);
        reavaliacao.setEvolucaoDor(evolucaoDor);
        entityManager.persist(reavaliacao);
    }

    private void plano(LocalDate inicio, LocalDate fimPrevisto, String objetivos) {
        PlanoTratamento plano = new PlanoTratamento();
        plano.setPaciente(paciente);
        plano.setDataInicio(inicio);
        plano.setDataFimPrevista(fimPrevisto);
        plano.setObjetivosTratamento(objetivos);
        entityManager.persist(plano);
    }

    private EvolucaoSessao evolucao(LocalDate data, String crefito, String exercicios) {
        SessaoPilates sessao = new SessaoPilates();
        sessao.setPaciente(paciente);
        sessao.setTipo(TipoSessao.PILATES);
        sessao.setStatus(StatusSessao.REALIZADA);
        sessao.setData(data);
        sessao.setHorario(LocalTime.of(9, 0));
        entityManager.persist(sessao);

        EvolucaoSessao evolucao = new EvolucaoSessao();
        evolucao.setSessao(sessao);
        evolucao.setProfissionalNome("Paula Mendes");
        evolucao.setProfissionalNumeroRegistro(crefito);
        evolucao.setDataHoraRegistro(data.atTime(9, 50));
        evolucao.setExerciciosRealizados(exercicios);
        entityManager.persist(evolucao);
        return evolucao;
    }

    private String bearer(Role role) {
        String email = "prontuario." + role.name().toLowerCase() + "@email.com";
        User user = userRepository.findByEmail(email).orElseGet(() -> {
            User novo = new User();
            novo.setName("Usuário Prontuário");
            novo.setEmail(email);
            novo.setPassword(passwordEncoder.encode("senha1234"));
            novo.setRoles(Set.of(role));
            novo.setAtivo(true);
            return userRepository.save(novo);
        });
        return "Bearer " + jwtService.generateToken(user);
    }

    private String texto(byte[] pdf) throws Exception {
        PdfReader reader = new PdfReader(pdf);
        try {
            PdfTextExtractor extractor = new PdfTextExtractor(reader);
            StringBuilder texto = new StringBuilder();
            for (int pagina = 1; pagina <= reader.getNumberOfPages(); pagina++) {
                texto.append(extractor.getTextFromPage(pagina)).append('\n');
            }
            return texto.toString();
        } finally {
            reader.close();
        }
    }
}
