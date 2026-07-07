package com.carlesso.pilatesapi.service;

import com.carlesso.pilatesapi.entity.Profissional;
import com.carlesso.pilatesapi.entity.enums.TipoContrato;
import com.carlesso.pilatesapi.repository.ProfissionalRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import com.carlesso.pilatesapi.support.PostgresDataJpaTest;
import com.carlesso.pilatesapi.support.PostgresTestcontainerSupport;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@PostgresDataJpaTest
@Import(ProfissionalService.class)
class ProfissionalServiceIntegrationTest extends PostgresTestcontainerSupport {

    @Autowired
    private ProfissionalRepository repository;

    @Autowired
    private ProfissionalService service;

    @BeforeEach
    void setUp() {
        repository.deleteAll();
        repository.save(profissional("Paula Mendes", "paula@email.com", "12345678900", TipoContrato.PJ, "45.00", true));
        repository.save(profissional("Ricardo Souza", "ricardo@email.com", "98765432100", TipoContrato.AUTONOMO, "40.00", true));
        repository.save(profissional("Fernanda Lima", "fernanda@email.com", "11122233344", TipoContrato.CLT, "35.00", false));
    }

    @Test
    void listar_semStatusRetornaAtivosPorPadrao() {
        var resultado = service.listar(null, null, null, null, null, PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .extracting("nome")
                .containsExactlyInAnyOrder("Paula Mendes", "Ricardo Souza");
    }

    @Test
    void listar_filtraPorCamposDaIssueEStatus() {
        var resultado = service.listar(
                "fer",
                "email.com",
                TipoContrato.CLT,
                new BigDecimal("35.00"),
                false,
                PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .singleElement()
                .satisfies(profissional -> {
                    assertThat(profissional.nome()).isEqualTo("Fernanda Lima");
                    assertThat(profissional.ativo()).isFalse();
                });
    }

    @Test
    void listar_filtroNomeEmBranco_deveIgnorarFiltro() {
        var resultado = service.listar("   ", null, null, null, null, PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .extracting("nome")
                .containsExactlyInAnyOrder("Paula Mendes", "Ricardo Souza");
    }

    @Test
    void listar_semAtivoNaoRetornaTodosProfissionais() {
        var ativos = service.listar(null, null, null, null, null, PageRequest.of(0, 10));
        var inativos = service.listar(null, null, null, null, false, PageRequest.of(0, 10));

        assertThat(ativos.getTotalElements()).isEqualTo(2);
        assertThat(inativos.getTotalElements()).isEqualTo(1);
        assertThat(ativos.getTotalElements() + inativos.getTotalElements())
                .isEqualTo(3);
    }

    @Test
    void listar_filtraPorPercentualPagamentoAulaIsolado() {
        var resultado = service.listar(null, null, null, new BigDecimal("40.00"), null, PageRequest.of(0, 10));

        assertThat(resultado.getContent())
                .singleElement()
                .satisfies(profissional -> {
                    assertThat(profissional.nome()).isEqualTo("Ricardo Souza");
                    assertThat(profissional.percentualPagamentoAula()).isEqualByComparingTo("40.00");
                });
    }

    private Profissional profissional(
            String nome,
            String email,
            String cpf,
            TipoContrato tipoContrato,
            String percentualPagamentoAula,
            boolean ativo) {
        Profissional profissional = new Profissional();
        profissional.setNome(nome);
        profissional.setEmail(email);
        profissional.setCpf(cpf);
        profissional.setTelefone("11999999999");
        profissional.setTipoContrato(tipoContrato);
        profissional.setPercentualPagamentoAula(new BigDecimal(percentualPagamentoAula));
        profissional.setDataInicio(LocalDate.of(2024, 1, 15));
        profissional.setAtivo(ativo);
        return profissional;
    }
}
