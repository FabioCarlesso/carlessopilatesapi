package com.carlesso.pilatesapi.dto;

/**
 * Resposta de {@code GET /profissionais/{id}/disponibilidades/verificar}. {@code true} também quando o profissional
 * não tem nenhuma faixa cadastrada: sem configuração não há o que avisar.
 */
public record VerificacaoDisponibilidadeDTO(boolean dentroDaDisponibilidade) {}
