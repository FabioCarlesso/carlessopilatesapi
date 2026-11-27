package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.enums.OrigemPontoDor;
import java.time.LocalDate;
import java.util.List;

/**
 * Pontos de dor de um registro clínico no histórico do paciente.
 *
 * @param registroId id da avaliação ou da reavaliação, conforme {@code origem}
 * @param data {@code dataAvaliacao} ou {@code dataReavaliacao} do registro
 */
public record PontosDorRegistroDTO(
        OrigemPontoDor origem, Long registroId, LocalDate data, List<PontoDorResponseDTO> pontos) {}
