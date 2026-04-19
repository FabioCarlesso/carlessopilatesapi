package com.carlesso.pilatesapi.dto;

import com.carlesso.pilatesapi.entity.Paciente;
import java.time.LocalDate;

public record PacienteResponseDTO(
        Long id,
        String nome,
        String email,
        String cpf,
        String telefone,
        LocalDate dataNascimento,
        EnderecoDTO endereco,
        boolean ativo
) {
    public static PacienteResponseDTO from(Paciente p) {
        EnderecoDTO enderecoDTO = null;
        if (p.getEndereco() != null) {
            enderecoDTO = new EnderecoDTO(
                    p.getEndereco().getLogradouro(),
                    p.getEndereco().getNumero(),
                    p.getEndereco().getBairro(),
                    p.getEndereco().getCidade(),
                    p.getEndereco().getUf(),
                    p.getEndereco().getCep()
            );
        }
        return new PacienteResponseDTO(
                p.getId(),
                p.getNome(),
                p.getEmail(),
                p.getCpf(),
                p.getTelefone(),
                p.getDataNascimento(),
                enderecoDTO,
                p.isAtivo()
        );
    }
}
