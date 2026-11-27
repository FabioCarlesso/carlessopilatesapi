package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.TipoSessao;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;

/** Corpo de {@code POST /aulas/{id}/atendimento} (#246). Todos os campos são opcionais. */
public record AtendimentoRequestDTO(
        @Schema(
                        description =
                                "Profissional que atendeu. Na aula ainda não realizada, sobrescreve o da aula, como em `PATCH /aulas/{id}/realizar`; ausente, vale o da aula")
                Long profissionalId,
        @Schema(description = "Tipo da sessão criada; ausente, `PILATES`. Ignorado se a aula já tem sessão")
                TipoSessao tipo,
        @Schema(description = "Evolução a registrar na sessão; ausente, o atendimento fica sem evolução") @Valid AtendimentoEvolucaoDTO evolucao) {

    /** Corpo ausente equivale a todos os campos vazios. */
    public static final AtendimentoRequestDTO VAZIO = new AtendimentoRequestDTO(null, null, null);
}
