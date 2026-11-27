package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Anamnese;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.LocalDateTime;
import java.util.List;

public record AnamneseResponseDTO(
        Long id,
        Long pacienteId,
        String nomePaciente,
        String queixaPrincipal,
        String historicoDoencas,
        String historicoCirurgias,
        String historicoLesoes,
        String medicamentosUso,
        String alergias,
        String nivelAtividadeFisica,
        String restricoesMedicas,
        String objetivos,
        String observacoes,
        List<String> regioesDor,
        String regioesDorComplemento,
        List<String> condicoesSaude,
        String condicoesSaudeComplemento,
        String nivelAtividadeFisicaOpcao,
        String nivelAtividadeFisicaComplemento,
        List<String> objetivosOpcoes,
        String objetivosComplemento,
        LocalDateTime dataCriacao,
        LocalDateTime dataAtualizacao,
        @Schema(description = "E-mail de quem criou o registro; \"system\" para processos automáticos")
                String criadoPor,
        @Schema(description = "E-mail de quem fez a última alteração; nulo enquanto não houver alteração")
                String alteradoPor,
        Long version) {
    public static AnamneseResponseDTO from(Anamnese a) {
        return new AnamneseResponseDTO(
                a.getId(),
                a.getPaciente().getId(),
                a.getPaciente().getNome(),
                a.getQueixaPrincipal(),
                a.getHistoricoDoencas(),
                a.getHistoricoCirurgias(),
                a.getHistoricoLesoes(),
                a.getMedicamentosUso(),
                a.getAlergias(),
                a.getNivelAtividadeFisica(),
                a.getRestricoesMedicas(),
                a.getObjetivos(),
                a.getObservacoes(),
                a.getRegioesDor(),
                a.getRegioesDorComplemento(),
                a.getCondicoesSaude(),
                a.getCondicoesSaudeComplemento(),
                a.getNivelAtividadeFisicaOpcao(),
                a.getNivelAtividadeFisicaComplemento(),
                a.getObjetivosOpcoes(),
                a.getObjetivosComplemento(),
                a.getDataCriacao(),
                a.getDataAtualizacao(),
                a.getCriadoPor(),
                a.getAlteradoPor(),
                a.getVersion());
    }
}
