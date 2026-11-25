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
        boolean ativo,
        Long version,
        boolean cpfNaNota,
        Long tomadorId,
        String tomadorNome,
        String tomadorCpfCnpj) {
    public static PacienteResponseDTO from(Paciente p) {
        EnderecoDTO enderecoDTO = null;
        if (p.getEndereco() != null) {
            enderecoDTO = new EnderecoDTO(
                    p.getEndereco().getLogradouro(),
                    p.getEndereco().getNumero(),
                    p.getEndereco().getBairro(),
                    p.getEndereco().getCidade(),
                    p.getEndereco().getUf(),
                    p.getEndereco().getCep());
        }
        return new PacienteResponseDTO(
                p.getId(),
                p.getNome(),
                p.getEmail(),
                p.getCpf(),
                p.getTelefone(),
                p.getDataNascimento(),
                enderecoDTO,
                p.isAtivo(),
                p.getVersion(),
                p.isCpfNaNota(),
                p.getTomador() == null ? null : p.getTomador().getId(),
                p.getTomador() == null ? null : p.getTomador().getNome(),
                p.getTomador() == null ? null : p.getTomador().getCpfCnpj());
    }
}
