package com.carlesso.pilatesapi.dto;

/**
 * Pendência de um paciente no relatório de pacientes (#230). As quatro primeiras são de cadastro (dado faltando);
 * {@link #SEM_PLANO_ATIVO} só vale para paciente ativo.
 */
public enum PendenciaCadastroPaciente {
    SEM_CPF("Sem CPF"),
    SEM_EMAIL("Sem e-mail"),
    SEM_TELEFONE("Sem telefone"),
    SEM_DATA_NASCIMENTO("Sem data de nascimento"),
    SEM_PLANO_ATIVO("Sem plano ativo");

    private final String descricao;

    PendenciaCadastroPaciente(String descricao) {
        this.descricao = descricao;
    }

    public String getDescricao() {
        return descricao;
    }
}
