package com.carlesso.pilatesapi.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;

class TipoProblemaTest {

    @Test
    void slugs_saoUnicos() {
        assertThat(Arrays.stream(TipoProblema.values()).map(TipoProblema::slug)).doesNotHaveDuplicates();
    }

    @Test
    void doStatus_escolheTipoGenericoDoStatus() {
        assertThat(TipoProblema.doStatus(HttpStatus.BAD_REQUEST)).contains(TipoProblema.REQUISICAO_INVALIDA);
        assertThat(TipoProblema.doStatus(HttpStatus.CONFLICT)).contains(TipoProblema.CONFLITO);
        assertThat(TipoProblema.doStatus(HttpStatus.NOT_FOUND)).contains(TipoProblema.NAO_ENCONTRADO);
    }

    @Test
    void doStatus_qualquer5xx_eErroInterno() {
        assertThat(TipoProblema.doStatus(HttpStatus.SERVICE_UNAVAILABLE)).contains(TipoProblema.ERRO_INTERNO);
        assertThat(TipoProblema.doStatus(HttpStatusCode.valueOf(599))).contains(TipoProblema.ERRO_INTERNO);
    }

    @Test
    void doStatus_statusForaDoCatalogo_ficaVazio() {
        assertThat(TipoProblema.doStatus(HttpStatus.NOT_ACCEPTABLE)).isEmpty();
    }

    @Test
    void uri_usaBaseDoCatalogo() {
        assertThat(TipoProblema.REGRA_NEGOCIO.uri().toString()).isEqualTo(TipoProblema.BASE_URI + "business-rule");
    }
}
