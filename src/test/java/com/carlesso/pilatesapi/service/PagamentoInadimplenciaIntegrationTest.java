package com.carlesso.pilatesapi.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.carlesso.pilatesapi.config.AppProperties;
import com.carlesso.pilatesapi.dto.ContatoCobrancaRequestDTO;
import com.carlesso.pilatesapi.dto.ContatoCobrancaResponseDTO;
import com.carlesso.pilatesapi.dto.PagamentoResponseDTO;
import com.carlesso.pilatesapi.entity.ContatoCobranca;
import com.carlesso.pilatesapi.entity.Paciente;
import com.carlesso.pilatesapi.entity.Pagamento;
import com.carlesso.pilatesapi.entity.Plano;
import com.carlesso.pilatesapi.entity.enums.FrequenciaSemanal;
import com.carlesso.pilatesapi.entity.enums.StatusPagamento;
import com.carlesso.pilatesapi.entity.enums.TipoPagamento;
import com.carlesso.pilatesapi.exception.BusinessException;
import com.carlesso.pilatesapi.repository.ContatoCobrancaRepository;
import com.carlesso.pilatesapi.support.MetricsTestConfig;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.TestPropertySource;

/**
 * Listagem global de pagamentos (#196) e histórico de contatos de cobrança (#197) contra o schema real: os filtros
 * viram SQL de verdade e a V39 é exercitada pelo Flyway.
 */
@PostgresDataJpaTest
@Import({PagamentoService.class, AulaService.class, ContatoCobrancaService.class, MetricsTestConfig.class})
@EnableConfigurationProperties(AppProperties.class)
@TestPropertySource(
        properties = {
            "app.cobranca.cron-vencidos=0 0 6 * * *",
            "app.cobranca.cron-cobrancas-futuras=0 0 7 * * *",
            "app.cobranca.vencimento-dias=10"
        })
class PagamentoInadimplenciaIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    private PagamentoService pagamentoService;

    @Autowired
    private ContatoCobrancaService contatoCobrancaService;

    @Autowired
    private ContatoCobrancaRepository contatoCobrancaRepository;

    @Autowired
    private TestEntityManager entityManager;

    private Paciente ana;
    private Paciente bia;
    private Pagamento anaVencidoJaneiro;
    private Pagamento anaPendenteMarco;
    private Pagamento biaVencidoFevereiro;
    private Pagamento biaPagoJaneiro;

    @BeforeEach
    void setUp() {
        ana = entityManager.persist(paciente("Ana", "ana-inad@email.com", "90000000001"));
        bia = entityManager.persist(paciente("Bia", "bia-inad@email.com", "90000000002"));
        Plano planoAna = entityManager.persist(plano(ana));
        Plano planoBia = entityManager.persist(plano(bia));

        anaVencidoJaneiro = entityManager.persist(
                pagamento(ana, planoAna, StatusPagamento.VENCIDO, LocalDate.of(2026, 1, 10), "250.00"));
        anaPendenteMarco = entityManager.persist(
                pagamento(ana, planoAna, StatusPagamento.PENDENTE, LocalDate.of(2026, 3, 10), "250.00"));
        biaVencidoFevereiro = entityManager.persist(
                pagamento(bia, planoBia, StatusPagamento.VENCIDO, LocalDate.of(2026, 2, 10), "300.00"));
        biaPagoJaneiro = entityManager.persist(
                pagamento(bia, planoBia, StatusPagamento.PAGO, LocalDate.of(2026, 1, 10), "300.00"));
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    void listar_semFiltros_ordenaPorVencimentoEDesempataPorId() {
        var pagina = pagamentoService.listar(null, null, null, null, PageRequest.of(0, 10, Sort.by("dataVencimento")));

        // anaVencidoJaneiro e biaPagoJaneiro vencem no mesmo dia: o id é o desempate, e a ordem tem que ser estável.
        assertThat(pagina.getContent())
                .extracting(PagamentoResponseDTO::id)
                .containsExactly(
                        anaVencidoJaneiro.getId(),
                        biaPagoJaneiro.getId(),
                        biaVencidoFevereiro.getId(),
                        anaPendenteMarco.getId());
        assertThat(pagina.getTotalElements()).isEqualTo(4);
    }

    @Test
    void listar_porMultiplosStatus_retornaApenasOsInformados() {
        var pagina = pagamentoService.listar(
                List.of(StatusPagamento.PENDENTE, StatusPagamento.VENCIDO),
                null,
                null,
                null,
                PageRequest.of(0, 10, Sort.by("dataVencimento")));

        assertThat(pagina.getContent())
                .extracting(PagamentoResponseDTO::id)
                .containsExactly(anaVencidoJaneiro.getId(), biaVencidoFevereiro.getId(), anaPendenteMarco.getId());
    }

    @Test
    void listar_porFaixaDeVencimento_ehInclusivaNasDuasPontas() {
        var pagina = pagamentoService.listar(
                null,
                LocalDate.of(2026, 2, 10),
                LocalDate.of(2026, 3, 10),
                null,
                PageRequest.of(0, 10, Sort.by("dataVencimento")));

        assertThat(pagina.getContent())
                .extracting(PagamentoResponseDTO::id)
                .containsExactly(biaVencidoFevereiro.getId(), anaPendenteMarco.getId());
    }

    @Test
    void listar_comFaixaAbertaEPaciente_combinaOsFiltros() {
        var pagina = pagamentoService.listar(
                List.of(StatusPagamento.VENCIDO),
                null,
                LocalDate.of(2026, 2, 28),
                ana.getId(),
                PageRequest.of(0, 10, Sort.by("dataVencimento")));

        assertThat(pagina.getContent()).extracting(PagamentoResponseDTO::id).containsExactly(anaVencidoJaneiro.getId());
        assertThat(pagina.getContent().getFirst().pacienteNome()).isEqualTo("Ana");
    }

    @Test
    void listar_paginaERespeitaOrdenacaoPorNomeDoPaciente() {
        var primeira = pagamentoService.listar(
                null, null, null, null, PageRequest.of(0, 3, Sort.by(Sort.Direction.DESC, "paciente.nome")));
        var segunda = pagamentoService.listar(
                null, null, null, null, PageRequest.of(1, 3, Sort.by(Sort.Direction.DESC, "paciente.nome")));

        assertThat(primeira.getContent())
                .extracting(PagamentoResponseDTO::pacienteNome)
                .containsExactly("Bia", "Bia", "Ana");
        assertThat(segunda.getContent())
                .extracting(PagamentoResponseDTO::pacienteNome)
                .containsExactly("Ana");
        assertThat(primeira.getTotalPages()).isEqualTo(2);
    }

    @Test
    void registrarContato_persisteComAutorEMomentoDoServidor() {
        // registradoEm é gravado no fuso do estúdio, não no da JVM.
        LocalDateTime antes = LocalDateTime.now(ZoneId.of("America/Sao_Paulo")).minusSeconds(1);

        ContatoCobrancaResponseDTO criado = contatoCobrancaService.registrar(
                biaVencidoFevereiro.getId(),
                new ContatoCobrancaRequestDTO(LocalDate.of(2026, 2, 15), "  Ligação; promete pagar sexta  "),
                "admin@email.com");
        entityManager.flush();
        entityManager.clear();

        ContatoCobranca salvo = contatoCobrancaRepository.findById(criado.id()).orElseThrow();
        assertThat(salvo.getRegistradoPor()).isEqualTo("admin@email.com");
        assertThat(salvo.getRegistradoEm()).isAfter(antes).isBefore(antes.plusMinutes(1));
        assertThat(salvo.getObservacao()).isEqualTo("Ligação; promete pagar sexta");
        assertThat(criado.pagamentoId()).isEqualTo(biaVencidoFevereiro.getId());
    }

    @Test
    void listarContatos_doMaisRecenteParaOMaisAntigo() {
        var antigo = contatoCobrancaService.registrar(
                biaVencidoFevereiro.getId(),
                new ContatoCobrancaRequestDTO(LocalDate.of(2026, 2, 12), "Mensagem"),
                "admin@email.com");
        var recente = contatoCobrancaService.registrar(
                biaVencidoFevereiro.getId(),
                new ContatoCobrancaRequestDTO(LocalDate.of(2026, 2, 20), "Ligação"),
                "admin@email.com");
        // Mesmo dia do mais antigo, registrado depois: desempata pelo momento do registro.
        var mesmoDiaRegistradoDepois = contatoCobrancaService.registrar(
                biaVencidoFevereiro.getId(),
                new ContatoCobrancaRequestDTO(LocalDate.of(2026, 2, 12), "Presencial"),
                "admin@email.com");
        contatoCobrancaService.registrar(
                anaVencidoJaneiro.getId(),
                new ContatoCobrancaRequestDTO(LocalDate.of(2026, 2, 25), "De outro pagamento"),
                "admin@email.com");
        entityManager.flush();
        entityManager.clear();

        assertThat(contatoCobrancaService.listar(biaVencidoFevereiro.getId()))
                .extracting(ContatoCobrancaResponseDTO::id)
                .containsExactly(recente.id(), mesmoDiaRegistradoDepois.id(), antigo.id());
    }

    @Test
    void registrarContato_emPagamentoPago_naoPersiste() {
        assertThatThrownBy(() -> contatoCobrancaService.registrar(
                        biaPagoJaneiro.getId(),
                        new ContatoCobrancaRequestDTO(LocalDate.of(2026, 1, 12), "Cobrança"),
                        "admin@email.com"))
                .isInstanceOf(BusinessException.class);

        assertThat(contatoCobrancaRepository.count()).isZero();
    }

    private Paciente paciente(String nome, String email, String cpf) {
        Paciente paciente = new Paciente();
        paciente.setNome(nome);
        paciente.setEmail(email);
        paciente.setCpf(cpf);
        return paciente;
    }

    private Plano plano(Paciente paciente) {
        Plano plano = new Plano();
        plano.setPaciente(paciente);
        plano.setTipo(TipoPagamento.MENSAL);
        plano.setValor(new BigDecimal("250.00"));
        plano.setFrequenciaSemanal(FrequenciaSemanal.UMA_VEZ);
        plano.setDiasSemana(List.of(DayOfWeek.MONDAY));
        plano.setDataInicio(LocalDate.of(2026, 1, 1));
        return plano;
    }

    private Pagamento pagamento(
            Paciente paciente, Plano plano, StatusPagamento status, LocalDate vencimento, String valor) {
        Pagamento pagamento = new Pagamento();
        pagamento.setPaciente(paciente);
        pagamento.setPlano(plano);
        pagamento.setValor(new BigDecimal(valor));
        pagamento.setStatus(status);
        pagamento.setDataPagamento(status == StatusPagamento.PAGO ? vencimento : null);
        pagamento.setDataVencimento(vencimento);
        pagamento.setPeriodoInicio(vencimento.withDayOfMonth(1));
        pagamento.setPeriodoFim(vencimento.withDayOfMonth(vencimento.lengthOfMonth()));
        return pagamento;
    }
}
