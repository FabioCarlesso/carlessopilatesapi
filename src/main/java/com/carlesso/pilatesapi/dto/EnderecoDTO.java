package com.carlesso.pilatesapi.dto;

public record EnderecoDTO(
        String logradouro,
        String numero,
        String bairro,
        String cidade,
        String uf,
        String cep
) {}
