package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.TesteFuncionalRealizado;
import com.carlesso.pilatesapi.entity.enums.ResultadoTesteFuncional;
import com.carlesso.pilatesapi.entity.enums.TesteFuncional;
import io.swagger.v3.oas.annotations.media.Schema;

/** Teste funcional realizado e o seu resultado (#248), na requisição e na resposta. */
public record TesteFuncionalRealizadoDTO(
        @Schema(description = "Teste realizado", implementation = TesteFuncional.class, example = "SCHOBER")
                String teste,
        @Schema(
                        description = "Resultado do teste; obrigatório",
                        implementation = ResultadoTesteFuncional.class,
                        example = "POSITIVO")
                String resultado) {

    public static TesteFuncionalRealizadoDTO from(TesteFuncionalRealizado t) {
        return new TesteFuncionalRealizadoDTO(t.teste(), t.resultado());
    }
}
