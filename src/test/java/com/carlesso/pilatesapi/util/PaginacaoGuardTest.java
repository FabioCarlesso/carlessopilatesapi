package com.carlesso.pilatesapi.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class PaginacaoGuardTest {

    private static final Set<String> CAMPOS = Set.of("data", "realizada");

    @Test
    void acrescentaIdComoUltimoCriterioNaDirecaoPedida() {
        Pageable pageable = PageRequest.of(2, 5, Sort.by(Sort.Direction.DESC, "data"));

        Pageable resultado = PaginacaoGuard.comDesempatePorId(pageable, CAMPOS, Sort.Direction.DESC);

        assertThat(resultado.getPageNumber()).isEqualTo(2);
        assertThat(resultado.getPageSize()).isEqualTo(5);
        assertThat(resultado.getSort()).isEqualTo(Sort.by(Sort.Direction.DESC, "data", "id"));
    }

    @Test
    void semOrdenacao_ordenaSoPorId() {
        Pageable resultado = PaginacaoGuard.comDesempatePorId(PageRequest.of(0, 10), CAMPOS, Sort.Direction.ASC);

        assertThat(resultado.getSort()).isEqualTo(Sort.by(Sort.Direction.ASC, "id"));
    }

    @Test
    void campoForaDaLista_lancaIllegalArgumentListandoOsAceitos() {
        Pageable pageable = PageRequest.of(0, 10, Sort.by("observacoes"));

        assertThatThrownBy(() -> PaginacaoGuard.comDesempatePorId(pageable, CAMPOS, Sort.Direction.DESC))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Campo de ordenação inválido: observacoes. Aceitos: data, realizada");
    }

    @Test
    void naoPaginado_passaSemDesempate() {
        assertThat(PaginacaoGuard.comDesempatePorId(Pageable.unpaged(), CAMPOS, Sort.Direction.DESC))
                .isEqualTo(Pageable.unpaged());
    }
}
