package com.carlesso.pilatesapi.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import java.time.LocalDate;

public record PacienteUpdateDTO(
        String nome,
        @Email String email,
        String telefone,
        LocalDate dataNascimento,
        @Valid EnderecoDTO endereco,
        @Schema(
                        description =
                                "Versão recebida no GET; divergente da atual retorna 409 concurrent-modification. Ausente: sem checagem (modo legado)",
                        example = "0")
                Long version,
        @Schema(
                        description =
                                "CPF do paciente. Ausente ou vazio mantém o atual; CPF de outro paciente retorna 409."
                                        + " Permite completar o cadastro de quem foi importado sem CPF",
                        example = "11122233344")
                String cpf,
        @Schema(
                        description =
                                "O paciente deseja CPF/CNPJ na NFSE. Ausente: mantém o atual. Desmarcar não desvincula o"
                                        + " tomador: o vínculo só muda por PATCH /pacientes/{id}/tomador",
                        example = "true")
                Boolean cpfNaNota) {}
