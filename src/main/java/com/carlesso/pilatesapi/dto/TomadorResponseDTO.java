package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Tomador;

public record TomadorResponseDTO(
        Long id,
        String nome,
        String cpfCnpj,
        String email,
        String telefone,
        String observacoes,
        boolean ativo,
        long quantidadePacientes,
        Long version) {

    public static TomadorResponseDTO from(Tomador t, long quantidadePacientes) {
        return new TomadorResponseDTO(
                t.getId(),
                t.getNome(),
                t.getCpfCnpj(),
                t.getEmail(),
                t.getTelefone(),
                t.getObservacoes(),
                t.isAtivo(),
                quantidadePacientes,
                t.getVersion());
    }
}
