package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Aula;
import com.carlesso.pilatesapi.entity.Profissional;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDate;
import java.time.LocalTime;

public record AulaResponseDTO(
        Long id,
        Long pacienteId,
        String pacienteNome,
        Long profissionalId,
        String profissionalNome,
        Long pagamentoId,
        LocalDate data,
        LocalTime horario,
        boolean realizada,
        boolean feriado,
        @Schema(description = "Sessão do atendimento vinculada à aula (#246); nulo enquanto não houver atendimento")
                Long sessaoId,
        @Schema(description = "A sessão vinculada já tem evolução registrada (#246); `false` sem sessão vinculada")
                boolean temEvolucao) {

    /**
     * Monta a resposta a partir da entidade. O vínculo com a sessão vem à parte porque fica do lado da sessão
     * ({@code sessoes_pilates.aula_id}): mapeá-lo na aula como {@code OneToOne} inverso faria o Hibernate buscar a
     * sessão a cada aula carregada, já que o lado inverso não é LAZY sem instrumentação de bytecode.
     */
    public static AulaResponseDTO from(Aula aula, Long sessaoId, boolean temEvolucao) {
        Profissional profissional = aula.getProfissional();
        return new AulaResponseDTO(
                aula.getId(),
                aula.getPaciente().getId(),
                aula.getPaciente().getNome(),
                profissional != null ? profissional.getId() : null,
                profissional != null ? profissional.getNome() : null,
                aula.getPagamento().getId(),
                aula.getData(),
                aula.getHorario(),
                aula.isRealizada(),
                aula.isFeriado(),
                sessaoId,
                temEvolucao);
    }
}
