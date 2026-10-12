package com.carlesso.pilatesapi.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * Registro de um contato de cobrança. Não há {@code registradoPor} nem {@code registradoEm}: os dois são do servidor, e
 * um cliente capaz de escolhê-los poderia atribuir o contato a outra pessoa.
 */
public record ContatoCobrancaRequestDTO(
        @NotNull(message = "dataContato é obrigatória") @PastOrPresent(message = "dataContato não pode ser uma data futura") LocalDate dataContato,
        @NotBlank(message = "observacao é obrigatória") @Size(max = 500, message = "observacao deve ter no máximo 500 caracteres") String observacao) {}
